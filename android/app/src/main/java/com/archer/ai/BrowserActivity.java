package com.archer.ai;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

// NVN's own built-in browser: log in to sites once here; browser-mode work then
// runs inside the app (no Chrome needed). Cookies persist across restarts.
public class BrowserActivity extends Activity {

    private WebView web;
    private EditText addr;
    private ProgressBar bar;

    public static void open(Context ctx, String url) {
        Intent i = new Intent(ctx, BrowserActivity.class);
        if (url != null && !url.trim().isEmpty()) i.putExtra("url", url.trim());
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF04170F);

        // Top bar: back / forward / reload / address / go
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setPadding(10, 10, 10, 10);
        top.setBackgroundColor(0xFF07271C);

        Button back = navBtn("◀");
        Button fwd = navBtn("▶");
        Button reload = navBtn("↻");
        addr = new EditText(this);
        addr.setHint("ওয়েবসাইটের ঠিকানা লিখুন");
        addr.setTextColor(0xFFF3E9C8);
        addr.setHintTextColor(0xFFA8C3B2);
        addr.setSingleLine(true);
        addr.setImeOptions(EditorInfo.IME_ACTION_GO);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        ap.setMargins(8, 0, 8, 0);
        addr.setLayoutParams(ap);
        Button go = navBtn("যাও");
        go.setTextColor(0xFFE7C873);

        top.addView(back);
        top.addView(fwd);
        top.addView(reload);
        top.addView(addr);
        top.addView(go);
        root.addView(top);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        root.addView(bar);

        web = new WebView(this);
        web.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(true);
        ws.setDisplayZoomControls(false);
        ws.setUserAgentString("Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Mobile Safari/537.36");
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                addr.setText(url);
                CookieManager.getInstance().flush();
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int progress) {
                bar.setProgress(progress);
                bar.setVisibility(progress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
        root.addView(web);

        TextView hint = new TextView(this);
        hint.setText("এখানে সাইটে লগইন করে রাখলে সে অ্যাপের ভিতর থেকেই কাজ করবে।");
        hint.setTextColor(0xFFA8C3B2);
        hint.setTextSize(11);
        hint.setPadding(14, 6, 14, 8);
        root.addView(hint);

        setContentView(root);

        back.setOnClickListener(v -> { if (web.canGoBack()) web.goBack(); });
        fwd.setOnClickListener(v -> { if (web.canGoForward()) web.goForward(); });
        reload.setOnClickListener(v -> web.reload());
        go.setOnClickListener(v -> loadTyped());
        addr.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_GO) { loadTyped(); return true; }
            return false;
        });

        String url = getIntent().getStringExtra("url");
        if (url == null || url.trim().isEmpty()) url = "https://www.google.com";
        web.loadUrl(url);
    }

    private Button navBtn(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(0xFFF3E9C8);
        b.setBackgroundColor(0xFF065F46);
        b.setPadding(18, 8, 18, 8);
        return b;
    }

    private void loadTyped() {
        String u = addr.getText().toString().trim();
        if (u.isEmpty()) return;
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            if (u.contains(".") && !u.contains(" ")) u = "https://" + u;
            else u = "https://www.google.com/search?q=" + u.replace(" ", "+");
        }
        web.loadUrl(u);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String url = intent.getStringExtra("url");
        if (url != null && !url.trim().isEmpty() && web != null) web.loadUrl(url.trim());
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) { web.destroy(); web = null; }
        super.onDestroy();
    }
}
