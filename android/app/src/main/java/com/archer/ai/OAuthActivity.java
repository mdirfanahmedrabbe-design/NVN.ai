package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

// Facebook Login (OAuth) inside the app: the owner logs in on Facebook's official page,
// the Page token is fetched and saved automatically — no copy-paste.
public class OAuthActivity extends Activity {

    public static final String FB_APP_ID = "990853486953690";
    public static final String REDIRECT_URI = "https://hafijurislam777.pythonanywhere.com/nvn/oauth";

    private boolean handled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        WebView web = findViewById(R.id.oauthWeb);
        View progress = findViewById(R.id.oauthProgress);
        WebSettings ws = web.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(REDIRECT_URI)) {
                    handleRedirect(url);
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                if (url != null && url.startsWith(REDIRECT_URI)) handleRedirect(url);
            }
        });

        try {
            String authUrl = "https://www.facebook.com/v19.0/dialog/oauth"
                    + "?client_id=" + FB_APP_ID
                    + "&redirect_uri=" + URLEncoder.encode(REDIRECT_URI, "UTF-8")
                    + "&response_type=token"
                    + "&scope=" + URLEncoder.encode("pages_show_list,pages_read_engagement,pages_manage_posts", "UTF-8");
            web.loadUrl(authUrl);
        } catch (Exception e) {
            Toast.makeText(this, "লগইন পেজ খোলা যায়নি", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void handleRedirect(String url) {
        if (handled) return;
        handled = true;
        String token = null;
        try {
            int hash = url.indexOf('#');
            String frag = hash >= 0 ? url.substring(hash + 1) : url;
            for (String kv : frag.split("&")) {
                if (kv.startsWith("access_token=")) token = kv.substring("access_token=".length());
            }
        } catch (Exception ignored) {}
        if (token == null || token.isEmpty()) {
            Toast.makeText(this, "লগইন বাতিল হয়েছে", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        final String userToken = token;
        new Thread(() -> fetchPages(userToken)).start();
    }

    private void fetchPages(String userToken) {
        try {
            String res = NvnCore.httpGet("https://graph.facebook.com/v19.0/me/accounts?access_token=" + userToken);
            if (res == null) throw new Exception("no response");
            JSONObject o = new JSONObject(res);
            JSONArray data = o.optJSONArray("data");
            if (data == null || data.length() == 0) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "তোমার কোনো Facebook Page পাওয়া যায়নি", Toast.LENGTH_LONG).show();
                    finish();
                });
                return;
            }
            if (data.length() == 1) {
                JSONObject page = data.getJSONObject(0);
                savePage(page.optString("id", ""), page.optString("access_token", ""), page.optString("name", ""));
                return;
            }
            final String[] names = new String[data.length()];
            final String[] ids = new String[data.length()];
            final String[] tokens = new String[data.length()];
            for (int i = 0; i < data.length(); i++) {
                JSONObject page = data.getJSONObject(i);
                names[i] = page.optString("name", "Page " + (i + 1));
                ids[i] = page.optString("id", "");
                tokens[i] = page.optString("access_token", "");
            }
            runOnUiThread(() -> new AlertDialog.Builder(this)
                    .setTitle("কোন Page-টা কানেক্ট করবে?")
                    .setItems(names, (d, which) -> savePage(ids[which], tokens[which], names[which]))
                    .setOnCancelListener(d -> finish())
                    .show());
        } catch (Exception e) {
            runOnUiThread(() -> {
                Toast.makeText(this, "Page-এর তথ্য আনা যায়নি, আবার চেষ্টা করো", Toast.LENGTH_LONG).show();
                finish();
            });
        }
    }

    private void savePage(String pageId, String pageToken, String pageName) {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        p.edit()
                .putString("conn_facebook_token", pageToken)
                .putString("conn_facebook_id", pageId)
                .putBoolean("conn_facebook_enabled", true)
                .apply();
        NvnCore.markConnected(this, "facebook", pageName);
        ConnectWorker.logHistory(getApplicationContext(),
                "📘 Facebook Page — লগইন করে কানেক্ট হয়েছে (" + pageName + ")");
        new Thread(() -> NvnCore.backupData(getApplicationContext())).start();
        runOnUiThread(() -> {
            Toast.makeText(this, "✅ Facebook Page কানেক্ট হয়েছে: " + pageName, Toast.LENGTH_LONG).show();
            finish();
        });
    }
}
