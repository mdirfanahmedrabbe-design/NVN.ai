package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

// NVN built-in browser — Chrome-style: home button, omnibox, new-tab button,
// tab counter + switcher, Chrome menu (new tab, incognito, history, delete
// data, downloads, bookmarks, share, find in page, desktop site), start page,
// error page with retry, file upload, downloads.
// Sites logged in here stay logged in (persistent cookies), so NVN can work
// inside them without Chrome.
public class BrowserActivity extends Activity {

    private static class Tab {
        WebView web;
        boolean incognito;
        String title = "";
    }

    private final List<Tab> tabs = new ArrayList<>();
    private int current = 0;
    private FrameLayout holder;
    private EditText addr;
    private ProgressBar bar;
    private TextView tabCount;
    private LinearLayout findBar;
    private EditText findText;
    private ValueCallback<Uri[]> fileCallback;
    private boolean desktopMode = false;
    private SharedPreferences bp;
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int REQ_FILE = 77;

    public static void open(Context ctx, String url) {
        Intent i = new Intent(ctx, BrowserActivity.class);
        i.putExtra("url", url == null ? "" : url);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private Tab cur() {
        return tabs.isEmpty() ? null : tabs.get(Math.min(current, tabs.size() - 1));
    }

    private TextView iconBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#F3E9C8"));
        b.setTextSize(20);
        b.setGravity(Gravity.CENTER);
        b.setPadding(16, 10, 16, 10);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bp = getSharedPreferences("nvn_browser", MODE_PRIVATE);
        desktopMode = bp.getBoolean("desktop_default", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#04170F"));

        // ---- Chrome-style top bar: home, omnibox, +, tab count, menu ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.parseColor("#07271C"));
        top.setPadding(8, 10, 8, 10);

        TextView home = iconBtn("⌂");
        home.setOnClickListener(v -> {
            Tab t = cur();
            if (t == null) return;
            String hp = bp.getString("homepage", "");
            if (hp != null && !hp.trim().isEmpty()) {
                t.web.loadUrl(hp.trim().startsWith("http") ? hp.trim() : "https://" + hp.trim());
            } else {
                showStart(t);
            }
        });

        addr = new EditText(this);
        addr.setHint("Google-এ সার্চ করুন বা ঠিকানা লিখুন");
        addr.setHintTextColor(Color.parseColor("#7FBF9E"));
        addr.setTextColor(Color.parseColor("#F3E9C8"));
        addr.setTextSize(15);
        addr.setSingleLine(true);
        addr.setImeOptions(EditorInfo.IME_ACTION_GO);
        addr.setBackgroundResource(R.drawable.bg_input);
        addr.setPadding(28, 16, 28, 16);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ap.setMargins(6, 0, 6, 0);
        addr.setLayoutParams(ap);
        addr.setOnEditorActionListener((v, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { goTo(addr.getText().toString()); return true; }
            return false;
        });
        addr.setOnFocusChangeListener((v, hasFocus) -> {
            Tab t = cur();
            if (t == null) return;
            String u = t.web.getUrl();
            if (hasFocus) {
                if (u != null && u.startsWith("http") && !u.contains("nvn.local")) {
                    addr.setText(u);
                    addr.selectAll();
                }
            } else {
                showHost(u);
            }
        });

        TextView plus = iconBtn("+");
        plus.setOnClickListener(v -> newTab(false, ""));

        tabCount = iconBtn("▣ 1");
        tabCount.setTextSize(15);
        tabCount.setOnClickListener(v -> showTabs());

        TextView menu = iconBtn("⋮");
        menu.setOnClickListener(this::showMenu);

        top.addView(home);
        top.addView(addr);
        top.addView(plus);
        top.addView(tabCount);
        top.addView(menu);
        root.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E7C873")));
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 6));

        // find-in-page bar (hidden until used)
        findBar = new LinearLayout(this);
        findBar.setOrientation(LinearLayout.HORIZONTAL);
        findBar.setGravity(Gravity.CENTER_VERTICAL);
        findBar.setBackgroundColor(Color.parseColor("#07271C"));
        findBar.setPadding(10, 6, 10, 6);
        findBar.setVisibility(View.GONE);
        findText = new EditText(this);
        findText.setHint("পেজে খুঁজুন");
        findText.setHintTextColor(Color.parseColor("#7FBF9E"));
        findText.setTextColor(Color.parseColor("#F3E9C8"));
        findText.setSingleLine(true);
        findText.setBackgroundResource(R.drawable.bg_input);
        findText.setPadding(20, 10, 20, 10);
        findText.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        findText.setOnEditorActionListener((v, a, e) -> {
            Tab t = cur();
            if (t != null) t.web.findAllAsync(findText.getText().toString());
            return true;
        });
        TextView fUp = iconBtn("▲");
        fUp.setOnClickListener(v -> { Tab t = cur(); if (t != null) t.web.findNext(false); });
        TextView fDown = iconBtn("▼");
        fDown.setOnClickListener(v -> { Tab t = cur(); if (t != null) t.web.findNext(true); });
        TextView fClose = iconBtn("✕");
        fClose.setOnClickListener(v -> {
            Tab t = cur();
            if (t != null) t.web.clearMatches();
            findBar.setVisibility(View.GONE);
        });
        findBar.addView(findText);
        findBar.addView(fUp);
        findBar.addView(fDown);
        findBar.addView(fClose);
        root.addView(findBar);

        holder = new FrameLayout(this);
        root.addView(holder, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("এখানে সাইটে লগইন করে রাখলে সে অ্যাপের ভিতর থেকেই কাজ করবে।");
        hint.setTextColor(Color.parseColor("#A7E8C8"));
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(12, 8, 12, 8);
        root.addView(hint);

        setContentView(root);

        String url = getIntent() != null ? getIntent().getStringExtra("url") : null;
        newTab(false, url == null ? "" : url.trim());
    }

    private WebView makeWebView(Tab tab) {
        WebView web = new WebView(this);
        WebSettings s = web.getSettings();
        applyBrowserSettings(s);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(desktopMode ? UA_DESKTOP : UA_MOBILE);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        // speed: hardware rendering, cache on, no safe-browsing/form overhead
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        s.setCacheMode(tab.incognito ? WebSettings.LOAD_NO_CACHE : WebSettings.LOAD_DEFAULT);
        s.setSaveFormData(false);
        s.setGeolocationEnabled(false);
        if (android.os.Build.VERSION.SDK_INT >= 26) s.setSafeBrowsingEnabled(false);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(bp.getBoolean("cookies_on", true));
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (cur() != null && cur().web == view) {
                    bar.setProgress(progress);
                    bar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                tab.title = title == null ? "" : title;
            }

            @Override
            public boolean onShowFileChooser(WebView wv, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = cb;
                Intent i = params.createIntent();
                try {
                    startActivityForResult(i, REQ_FILE);
                    return true;
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (cur() != null && cur().web == view && !addr.hasFocus()) showHost(url);
                CookieManager.getInstance().flush();
                if (!tab.incognito && url != null && url.startsWith("http") && !url.contains("nvn.local")) {
                    addHistory(url, tab.title);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    String failed = req.getUrl() != null ? req.getUrl().toString() : "";
                    showError(tab, failed);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                String u = req.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception ignored) {}
                return true;
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimeType, long contentLength) {
                try {
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mimeType);
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) r.addRequestHeader("Cookie", cookies);
                    r.addRequestHeader("User-Agent", userAgent);
                    r.setTitle(URLUtil.guessFileName(url, contentDisposition, mimeType));
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,
                            URLUtil.guessFileName(url, contentDisposition, mimeType));
                    DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                    dm.enqueue(r);
                    Toast.makeText(BrowserActivity.this, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(BrowserActivity.this, "ডাউনলোড করা যায়নি", Toast.LENGTH_SHORT).show();
                }
            }
        });
        return web;
    }

    private void newTab(boolean incognito, String url) {
        Tab t = new Tab();
        t.incognito = incognito;
        t.web = makeWebView(t);
        tabs.add(t);
        current = tabs.size() - 1;
        showTab(current);
        if (url != null && !url.isEmpty()) t.web.loadUrl(url);
        else showStart(t);
        updateTabCount();
    }

    private void showTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        current = idx;
        holder.removeAllViews();
        Tab t = tabs.get(idx);
        if (t.web.getParent() != null) ((android.view.ViewGroup) t.web.getParent()).removeView(t.web);
        holder.addView(t.web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        showHost(t.web.getUrl());
        updateTabCount();
    }

    private void closeTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) return;
        Tab t = tabs.remove(idx);
        // remember closed tabs (Chrome "Recent tabs")
        try {
            String u = t.web.getUrl();
            if (!t.incognito && u != null && u.startsWith("http") && !u.contains("nvn.local")) {
                String closed = bp.getString("closed_tabs", "");
                String line = u + "\t" + (t.title == null ? "" : t.title) + "\n";
                String merged = line + closed;
                String[] lines = merged.split("\n");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(lines.length, 10); i++) {
                    if (!lines[i].trim().isEmpty()) sb.append(lines[i]).append("\n");
                }
                bp.edit().putString("closed_tabs", sb.toString()).apply();
            }
        } catch (Exception ignored) {}
        if (t.incognito) t.web.clearCache(true);
        t.web.destroy();
        if (tabs.isEmpty()) {
            newTab(false, "");
            return;
        }
        showTab(Math.min(idx, tabs.size() - 1));
    }

    private void updateTabCount() {
        tabCount.setText("▣ " + tabs.size());
    }

    private void showTabs() {
        String[] items = new String[tabs.size() + 1];
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            String label = t.title != null && !t.title.isEmpty() ? t.title : hostOf(t.web.getUrl());
            if (label.isEmpty()) label = "নতুন ট্যাব";
            items[i] = (i == current ? "● " : "○ ") + label + (t.incognito ? "  (ইনকগনিটো)" : "");
        }
        items[tabs.size()] = "+ নতুন ট্যাব";
        new AlertDialog.Builder(this)
                .setTitle("ট্যাবসমূহ")
                .setItems(items, (d, w) -> {
                    if (w == tabs.size()) newTab(false, "");
                    else showTab(w);
                })
                .setNeutralButton("বর্তমান ট্যাব বন্ধ করুন", (d, w) -> closeTab(current))
                .setNegativeButton("বন্ধ", null)
                .show();
    }

    private String hostOf(String url) {
        if (url == null || url.isEmpty() || url.contains("nvn.local")) return "";
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? url : h;
        } catch (Exception e) {
            return url;
        }
    }

    private void showHost(String url) {
        addr.setText(hostOf(url));
    }

    private void goTo(String text) {
        String q = text == null ? "" : text.trim();
        Tab t = cur();
        if (q.isEmpty() || t == null) return;
        if (q.startsWith("http://") || q.startsWith("https://")) {
            t.web.loadUrl(q);
        } else if (q.contains(".") && !q.contains(" ")) {
            t.web.loadUrl("https://" + q);
        } else {
            t.web.loadUrl(searchUrl(q));
        }
        addr.clearFocus();
    }

    private void showStart(Tab t) {
        if (t == null) return;
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#04170F;color:#F3E9C8;font-family:sans-serif;text-align:center;padding:24px}"
                + "h2{color:#E7C873}a{display:inline-block;margin:8px;padding:14px 20px;background:#07271C;color:#A7E8C8;"
                + "border:2px solid #C9A227;border-radius:24px;text-decoration:none;font-size:16px}</style></head><body>"
                + "<h2>NVN ব্রাউজার</h2><p>সাইটে লগইন করে রাখুন — সে এখান থেকেই কাজ করবে</p>"
                + "<a href='https://www.facebook.com'>Facebook</a>"
                + "<a href='https://www.youtube.com'>YouTube</a>"
                + "<a href='https://www.tiktok.com'>TikTok</a>"
                + "<a href='https://www.instagram.com'>Instagram</a>"
                + "<a href='https://www.threads.net'>Threads</a>"
                + "<a href='https://web.telegram.org'>Telegram</a>"
                + "<a href='https://web.whatsapp.com'>WhatsApp</a>"
                + "<a href='https://www.google.com'>Google</a>"
                + "</body></html>";
        t.web.loadDataWithBaseURL("https://nvn.local/", html, "text/html", "UTF-8", null);
        t.title = "NVN ব্রাউজার";
        if (t == cur()) addr.setText("");
    }

    private void showError(Tab t, String failedUrl) {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#04170F;color:#F3E9C8;font-family:sans-serif;text-align:center;padding:32px}"
                + "h3{color:#E7C873}a{display:inline-block;margin-top:16px;padding:14px 28px;background:#065F46;color:#fff;"
                + "border-radius:24px;text-decoration:none;font-size:16px}</style></head><body>"
                + "<h3>পেজটা লোড হয়নি</h3><p>ইন্টারনেট চালু আছে কিনা দেখুন, তারপর আবার চেষ্টা করুন।</p>"
                + "<a href='" + (failedUrl == null ? "" : failedUrl) + "'>আবার চেষ্টা করুন</a></body></html>";
        t.web.loadDataWithBaseURL("https://nvn.local/", html, "text/html", "UTF-8", null);
    }

    // ---- history & bookmarks (stored as "url\ttitle" lines) ----
    private void addHistory(String url, String title) {
        try {
            String old = bp.getString("history", "");
            String entry = url + "\t" + (title == null ? "" : title.replace("\n", " "));
            StringBuilder sb = new StringBuilder(entry);
            int n = 1;
            for (String line : old.split("\n")) {
                if (line.isEmpty() || line.startsWith(url + "\t")) continue;
                sb.append("\n").append(line);
                if (++n >= 100) break;
            }
            bp.edit().putString("history", sb.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void showList(String key, String title) {
        String data = bp.getString(key, "");
        List<String> labels = new ArrayList<>();
        List<String> urls = new ArrayList<>();
        for (String line : data.split("\n")) {
            if (line.isEmpty()) continue;
            String[] parts = line.split("\t", 2);
            urls.add(parts[0]);
            labels.add(parts.length > 1 && !parts[1].isEmpty() ? parts[1] : hostOf(parts[0]));
        }
        if (labels.isEmpty()) {
            Toast.makeText(this, "এখানে কিছু নেই", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setItems(labels.toArray(new String[0]), (d, w) -> {
                    Tab t = cur();
                    if (t != null) t.web.loadUrl(urls.get(w));
                })
                .setNegativeButton("বন্ধ", null)
                .show();
    }

    private void addBookmark() {
        Tab t = cur();
        if (t == null) return;
        String u = t.web.getUrl();
        if (u == null || !u.startsWith("http") || u.contains("nvn.local")) {
            Toast.makeText(this, "এই পেজ বুকমার্ক করা যাবে না", Toast.LENGTH_SHORT).show();
            return;
        }
        String old = bp.getString("bookmarks", "");
        String entry = u + "\t" + (t.title == null ? "" : t.title.replace("\n", " "));
        if (!old.contains(u + "\t")) {
            bp.edit().putString("bookmarks", entry + (old.isEmpty() ? "" : "\n" + old)).apply();
        }
        Toast.makeText(this, "বুকমার্ক করা হয়েছে", Toast.LENGTH_SHORT).show();
    }

    // ---------- Chrome-style settings ----------
    private void applyBrowserSettings(WebSettings s) {
        s.setJavaScriptEnabled(bp.getBoolean("js_on", true));
        s.setBlockNetworkImage(!bp.getBoolean("images_on", true));
        s.setBuiltInZoomControls(bp.getBoolean("zoom_controls", true));
        try {
            s.setTextZoom(Integer.parseInt(bp.getString("text_zoom", "100")));
        } catch (Exception ignored) {
            s.setTextZoom(100);
        }
    }

    private String searchUrl(String q) {
        String engine = bp.getString("search_engine", "google");
        String enc = Uri.encode(q);
        if ("bing".equals(engine)) return "https://www.bing.com/search?q=" + enc;
        if ("ddg".equals(engine)) return "https://duckduckgo.com/?q=" + enc;
        return "https://www.google.com/search?q=" + enc;
    }

    private void showRecentTabs() {
        String closed = bp.getString("closed_tabs", "");
        if (closed == null || closed.trim().isEmpty()) {
            Toast.makeText(this, "সাম্প্রতিক বন্ধ ট্যাব নেই", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] lines = closed.trim().split("\n");
        String[] labels = new String[lines.length];
        String[] urls = new String[lines.length];
        for (int i = 0; i < lines.length; i++) {
            String[] parts = lines[i].split("\t", 2);
            urls[i] = parts[0];
            labels[i] = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1] : hostOf(parts[0]);
        }
        new AlertDialog.Builder(this)
                .setTitle("সাম্প্রতিক ট্যাব")
                .setItems(labels, (d, w) -> newTab(false, urls[w]))
                .show();
    }

    private void printPage() {
        Tab t = cur();
        if (t == null) return;
        try {
            android.print.PrintManager pm = (android.print.PrintManager) getSystemService(PRINT_SERVICE);
            String job = t.title != null && !t.title.isEmpty() ? t.title : "NVN পেজ";
            pm.print(job, t.web.createPrintDocumentAdapter(job), null);
        } catch (Exception e) {
            Toast.makeText(this, "প্রিন্ট হয়নি", Toast.LENGTH_SHORT).show();
        }
    }

    private void addHomeShortcut() {
        Tab t = cur();
        String u = t != null ? t.web.getUrl() : null;
        if (u == null || !u.startsWith("http")) {
            Toast.makeText(this, "আগে একটা পেজ খোলো", Toast.LENGTH_SHORT).show();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "এই ফোনে শর্টকাট হবে না", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            android.content.pm.ShortcutManager sm = getSystemService(android.content.pm.ShortcutManager.class);
            if (sm != null && sm.isRequestPinShortcutSupported()) {
                Intent target = new Intent(this, BrowserActivity.class);
                target.setAction(Intent.ACTION_VIEW);
                target.putExtra("url", u);
                String label = t.title != null && !t.title.isEmpty() ? t.title : hostOf(u);
                android.content.pm.ShortcutInfo info = new android.content.pm.ShortcutInfo.Builder(this, "nvn_page_" + System.currentTimeMillis())
                        .setShortLabel(label)
                        .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.mipmap.ic_launcher))
                        .setIntent(target)
                        .build();
                sm.requestPinShortcut(info, null);
            } else {
                Toast.makeText(this, "এই ফোনে শর্টকাট হবে না", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "শর্টকাট হয়নি", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Re-apply browser settings after returning from the settings page.
        try {
            CookieManager.getInstance().setAcceptCookie(bp.getBoolean("cookies_on", true));
            for (Tab tb : tabs) applyBrowserSettings(tb.web.getSettings());
        } catch (Exception ignored) {}
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "নতুন ট্যাব");
        pm.getMenu().add(0, 2, 1, "ইনকগনিটো ট্যাব");
        pm.getMenu().add(0, 3, 2, "ইতিহাস");
        pm.getMenu().add(0, 4, 3, "ব্রাউজিং ডেটা মুছুন");
        pm.getMenu().add(0, 5, 4, "ডাউনলোড");
        pm.getMenu().add(0, 6, 5, "বুকমার্ক");
        pm.getMenu().add(0, 7, 6, "এই পেজ বুকমার্ক করুন");
        pm.getMenu().add(0, 8, 7, "শেয়ার করুন");
        pm.getMenu().add(0, 9, 8, "পেজে খুঁজুন");
        pm.getMenu().add(0, 10, 9, desktopMode ? "ডেস্কটপ সাইট ✓" : "ডেস্কটপ সাইট");
        pm.getMenu().add(0, 12, 10, "সাম্প্রতিক ট্যাব");
        pm.getMenu().add(0, 13, 11, "অনুবাদ করুন");
        pm.getMenu().add(0, 14, 12, "প্রিন্ট করুন");
        pm.getMenu().add(0, 15, 13, "হোম স্ক্রিনে যোগ করুন");
        pm.getMenu().add(0, 16, 14, "সেটিংস");
        pm.getMenu().add(0, 11, 15, "এই ট্যাব বন্ধ করুন");
        pm.setOnMenuItemClickListener(item -> {
            Tab t = cur();
            switch (item.getItemId()) {
                case 1:
                    newTab(false, "");
                    return true;
                case 2:
                    newTab(true, "");
                    Toast.makeText(this, "ইনকগনিটো ট্যাব — ইতিহাস জমা হবে না", Toast.LENGTH_SHORT).show();
                    return true;
                case 3:
                    showList("history", "ইতিহাস");
                    return true;
                case 4:
                    new AlertDialog.Builder(this)
                            .setTitle("ব্রাউজিং ডেটা মুছুন")
                            .setMessage("লগইন, কুকি, ইতিহাস সব মুছে যাবে।")
                            .setPositiveButton("মুছুন", (d, w) -> {
                                CookieManager.getInstance().removeAllCookies(null);
                                bp.edit().remove("history").apply();
                                for (Tab tb : tabs) {
                                    tb.web.clearCache(true);
                                    tb.web.clearHistory();
                                }
                                Toast.makeText(this, "ডেটা মোছা হয়েছে", Toast.LENGTH_SHORT).show();
                                if (t != null) showStart(t);
                            })
                            .setNegativeButton("বাতিল", null)
                            .show();
                    return true;
                case 5:
                    try {
                        startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
                    } catch (Exception e) {
                        Toast.makeText(this, "ডাউনলোড ফোল্ডার খোলা যায়নি", Toast.LENGTH_SHORT).show();
                    }
                    return true;
                case 6:
                    showList("bookmarks", "বুকমার্ক");
                    return true;
                case 7:
                    addBookmark();
                    return true;
                case 8: {
                    String u = t != null ? t.web.getUrl() : null;
                    if (u != null) {
                        Intent sh = new Intent(Intent.ACTION_SEND);
                        sh.setType("text/plain");
                        sh.putExtra(Intent.EXTRA_TEXT, u);
                        startActivity(Intent.createChooser(sh, "শেয়ার করুন"));
                    }
                    return true;
                }
                case 9:
                    findBar.setVisibility(View.VISIBLE);
                    findText.requestFocus();
                    return true;
                case 10:
                    desktopMode = !desktopMode;
                    for (Tab tb : tabs) {
                        tb.web.getSettings().setUserAgentString(desktopMode ? UA_DESKTOP : UA_MOBILE);
                    }
                    if (t != null) t.web.reload();
                    return true;
                case 11:
                    closeTab(current);
                    return true;
                case 12:
                    showRecentTabs();
                    return true;
                case 13: {
                    String u = t != null ? t.web.getUrl() : null;
                    if (u != null && u.startsWith("http")) {
                        newTab(false, "https://translate.google.com/translate?sl=auto&tl=bn&u=" + Uri.encode(u));
                    }
                    return true;
                }
                case 14:
                    printPage();
                    return true;
                case 15:
                    addHomeShortcut();
                    return true;
                case 16:
                    startActivity(new Intent(this, BrowserSettingsActivity.class));
                    return true;
            }
            return false;
        });
        pm.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null) {
                if (data.getData() != null) {
                    result = new Uri[]{data.getData()};
                } else if (data.getClipData() != null) {
                    int n = data.getClipData().getItemCount();
                    result = new Uri[n];
                    for (int i = 0; i < n; i++) result[i] = data.getClipData().getItemAt(i).getUri();
                }
            }
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intent.getStringExtra("url");
        if (url != null && !url.trim().isEmpty()) newTab(false, url.trim());
    }

    @Override
    public void onBackPressed() {
        Tab t = cur();
        String u = t != null ? t.web.getUrl() : null;
        boolean onStart = u == null || u.contains("nvn.local");
        if (findBar.getVisibility() == View.VISIBLE) {
            if (t != null) t.web.clearMatches();
            findBar.setVisibility(View.GONE);
        } else if (!onStart && t != null && t.web.canGoBack()) {
            t.web.goBack();
        } else {
            // On the start page (or nowhere to go back): leave the browser.
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        for (Tab t : tabs) {
            if (t.incognito) t.web.clearCache(true);
            t.web.destroy();
        }
        tabs.clear();
        super.onDestroy();
    }
}
