package com.archer.ai;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
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
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

// NVN built-in browser — Chrome-style: omnibox, tabs button, menu, start page,
// error page with retry, desktop mode, file upload, downloads.
// Sites logged in here stay logged in (persistent cookies), so NVN can work
// inside them without Chrome.
public class BrowserActivity extends Activity {

    private WebView web;
    private EditText addr;
    private ProgressBar bar;
    private TextView tabCount;
    private ValueCallback<Uri[]> fileCallback;
    private boolean desktopMode = false;
    private static final String UA_MOBILE = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final String UA_DESKTOP = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    private static final int REQ_FILE = 77;

    public static void open(Context ctx, String url) {
        Intent i = new Intent(ctx, BrowserActivity.class);
        i.putExtra("url", url == null ? "" : url);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    private TextView iconBtn(String text) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.parseColor("#F3E9C8"));
        b.setTextSize(20);
        b.setGravity(Gravity.CENTER);
        b.setPadding(18, 10, 18, 10);
        return b;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#04170F"));

        // ---- Chrome-style top bar ----
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.parseColor("#07271C"));
        top.setPadding(8, 10, 8, 10);

        TextView back = iconBtn("◀");
        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        TextView fwd = iconBtn("▶");
        fwd.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });

        addr = new EditText(this);
        addr.setHint("সার্চ করুন বা ঠিকানা লিখুন");
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

        TextView reload = iconBtn("↻");
        reload.setOnClickListener(v -> web.reload());

        tabCount = iconBtn("▣ 1");
        tabCount.setTextSize(15);
        tabCount.setOnClickListener(v -> Toast.makeText(this, "এখন ১টি ট্যাব খোলা আছে", Toast.LENGTH_SHORT).show());

        TextView menu = iconBtn("⋮");
        menu.setOnClickListener(this::showMenu);

        top.addView(back);
        top.addView(fwd);
        top.addView(addr);
        top.addView(reload);
        top.addView(tabCount);
        top.addView(menu);
        root.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#E7C873")));
        root.addView(bar, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 6));

        web = new WebView(this);
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        TextView hint = new TextView(this);
        hint.setText("এখানে সাইটে লগইন করে রাখলে সে অ্যাপের ভিতর থেকেই কাজ করবে।");
        hint.setTextColor(Color.parseColor("#A7E8C8"));
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(12, 8, 12, 8);
        root.addView(hint);

        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString(UA_MOBILE);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                bar.setProgress(progress);
                bar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
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
                if (url != null && !url.startsWith("data:") && !url.equals("about:blank")) {
                    addr.setText(url);
                }
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest req, WebResourceError err) {
                if (req != null && req.isForMainFrame()) {
                    String failed = req.getUrl() != null ? req.getUrl().toString() : "";
                    showError(failed);
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

        String url = getIntent() != null ? getIntent().getStringExtra("url") : null;
        if (url != null && !url.trim().isEmpty()) {
            web.loadUrl(url.trim());
        } else {
            showStart();
        }
    }

    private void goTo(String text) {
        String q = text == null ? "" : text.trim();
        if (q.isEmpty()) return;
        if (q.startsWith("http://") || q.startsWith("https://")) {
            web.loadUrl(q);
        } else if (q.contains(".") && !q.contains(" ")) {
            web.loadUrl("https://" + q);
        } else {
            web.loadUrl("https://www.google.com/search?q=" + Uri.encode(q));
        }
    }

    private void showStart() {
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
        web.loadDataWithBaseURL("https://nvn.local/", html, "text/html", "UTF-8", null);
        addr.setText("");
    }

    private void showError(String failedUrl) {
        String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<style>body{background:#04170F;color:#F3E9C8;font-family:sans-serif;text-align:center;padding:32px}"
                + "h3{color:#E7C873}a{display:inline-block;margin-top:16px;padding:14px 28px;background:#065F46;color:#fff;"
                + "border-radius:24px;text-decoration:none;font-size:16px}</style></head><body>"
                + "<h3>পেজটা লোড হয়নি</h3><p>ইন্টারনেট চালু আছে কিনা দেখুন, তারপর আবার চেষ্টা করুন।</p>"
                + "<a href='" + (failedUrl == null ? "" : failedUrl) + "'>আবার চেষ্টা করুন</a></body></html>";
        web.loadDataWithBaseURL("https://nvn.local/", html, "text/html", "UTF-8", null);
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "নতুন ট্যাব (শুরুর পেজ)");
        pm.getMenu().add(0, 2, 1, desktopMode ? "মোবাইল সাইট" : "ডেস্কটপ সাইট");
        pm.getMenu().add(0, 3, 2, "পেজ শেয়ার করুন");
        pm.getMenu().add(0, 4, 3, "লগইন ডেটা মুছুন");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showStart();
                    return true;
                case 2:
                    desktopMode = !desktopMode;
                    web.getSettings().setUserAgentString(desktopMode ? UA_DESKTOP : UA_MOBILE);
                    web.reload();
                    return true;
                case 3: {
                    String u = web.getUrl();
                    if (u != null) {
                        Intent sh = new Intent(Intent.ACTION_SEND);
                        sh.setType("text/plain");
                        sh.putExtra(Intent.EXTRA_TEXT, u);
                        startActivity(Intent.createChooser(sh, "শেয়ার করুন"));
                    }
                    return true;
                }
                case 4:
                    CookieManager.getInstance().removeAllCookies(null);
                    web.clearCache(true);
                    web.clearHistory();
                    Toast.makeText(this, "লগইন ডেটা মোছা হয়েছে", Toast.LENGTH_SHORT).show();
                    showStart();
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
        if (url != null && !url.trim().isEmpty()) web.loadUrl(url.trim());
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
