package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class ServiceActivity extends Activity {

    private String svc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_service);
        svc = getIntent().getStringExtra("svc");
        if (svc == null) svc = "facebook";

        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        TextView title = findViewById(R.id.svcTitle);
        TextView info = findViewById(R.id.svcInfo);
        EditText token = findViewById(R.id.svcToken);
        EditText userField = findViewById(R.id.svcUser);
        EditText passField = findViewById(R.id.svcPass);
        EditText idField = findViewById(R.id.svcId);
        EditText customName = findViewById(R.id.svcCustomName);
        EditText rules = findViewById(R.id.svcRules);
        EditText hour = findViewById(R.id.svcHour);
        EditText minute = findViewById(R.id.svcMinute);
        CheckBox enabled = findViewById(R.id.svcEnabled);
        CheckBox comments = findViewById(R.id.svcComments);
        CheckBox post = findViewById(R.id.svcPost);
        CheckBox approve = findViewById(R.id.svcApprove);
        CheckBox browser = findViewById(R.id.svcBrowser);
        EditText siteField = findViewById(R.id.svcSite);

        title.setText(ConnectWorker.svcName(svc));
        token.setText(prefs.getString("conn_" + svc + "_token", ""));
        userField.setText(prefs.getString("conn_" + svc + "_user", ""));
        passField.setText(prefs.getString("conn_" + svc + "_pass", ""));
        idField.setText(prefs.getString("conn_" + svc + "_id", ""));
        rules.setText(prefs.getString("conn_" + svc + "_rules", ""));
        hour.setText(String.valueOf(prefs.getInt("conn_" + svc + "_hour", 9)));
        minute.setText(String.valueOf(prefs.getInt("conn_" + svc + "_minute", 0)));
        enabled.setChecked(prefs.getBoolean("conn_" + svc + "_enabled", false));
        comments.setChecked(prefs.getBoolean("conn_" + svc + "_comments", true));
        post.setChecked(prefs.getBoolean("conn_" + svc + "_post", false));
        approve.setChecked(prefs.getBoolean("conn_" + svc + "_approve", true));
        browser.setChecked(prefs.getBoolean("conn_" + svc + "_browser", false));
        String savedSite = prefs.getString("conn_" + svc + "_site", "");
        siteField.setText(savedSite.isEmpty() ? ConnectWorker.defaultSiteUrl(svc) : savedSite);

        boolean isCustom = "custom".equals(svc);
        findViewById(R.id.svcCustomNameWrap).setVisibility(isCustom ? View.VISIBLE : View.GONE);
        if (isCustom) {
            String savedName = prefs.getString("conn_custom_name", "");
            String prefill = getIntent().getStringExtra("prefill_name");
            customName.setText(!savedName.isEmpty() ? savedName : (prefill == null ? "" : prefill));
            if (!savedName.isEmpty() && prefill != null && !prefill.trim().isEmpty()
                    && !savedName.equalsIgnoreCase(prefill.trim())) {
                customName.setText(prefill.trim());
            }
        }

        info.setText(infoFor(svc));

        // One-tap connect (browser mode): no token needed — she works through
        // the in-app browser where the user is already logged in.
        try {
            android.widget.ScrollView scroll = (android.widget.ScrollView)
                    ((android.view.ViewGroup) findViewById(android.R.id.content)).getChildAt(0);
            android.widget.LinearLayout root = (android.widget.LinearLayout) scroll.getChildAt(0);
            android.widget.Button oneTap = new android.widget.Button(this);
            boolean alreadyBrowser = prefs.getBoolean("conn_" + svc + "_browser", false);
            oneTap.setText(alreadyBrowser
                    ? "✅ ব্রাউজার মোডে যুক্ত আছে — টোকেন লাগবে না"
                    : "🌐 এক চাপে যুক্ত করুন (ব্রাউজার মোড — টোকেন লাগবে না)");
            oneTap.setTextColor(0xFFFFFFFF);
            oneTap.setTextSize(15);
            oneTap.setBackgroundResource(R.drawable.btn_primary);
            float d = getResources().getDisplayMetrics().density;
            android.widget.LinearLayout.LayoutParams otpLp = new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
            otpLp.topMargin = (int) (12 * d);
            oneTap.setLayoutParams(otpLp);
            oneTap.setOnClickListener(v -> {
                prefs.edit()
                        .putBoolean("conn_" + svc + "_browser", true)
                        .putBoolean("conn_" + svc + "_enabled", true)
                        .apply();
                NvnCore.markConnected(this, svc, null);
                ConnectWorker.scheduleAll(getApplicationContext());
                Toast.makeText(this, "যুক্ত হয়েছে — ব্রাউজার মোডে কাজ করবে", Toast.LENGTH_LONG).show();
                finish();
            });
            root.addView(oneTap, 1);
        } catch (Exception ignored) {}

        String tokenUrl = tokenUrlFor(svc);
        android.view.View tokenBtn = findViewById(R.id.svcTokenBtn);
        if ("facebook".equals(svc)) {
            // One-tap Facebook login: token saves itself (same as chat dialog).
            ((android.widget.Button) tokenBtn).setText("🔐 Facebook দিয়ে লগইন করো — Token নিজে বসবে");
            tokenBtn.setOnClickListener(v ->
                    startActivity(new android.content.Intent(this, OAuthActivity.class)));
        } else if (tokenUrl == null) {
            tokenBtn.setVisibility(View.GONE);
        } else {
            tokenBtn.setOnClickListener(v -> {
                try {
                    startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(tokenUrl)));
                } catch (Exception e) {
                    Toast.makeText(this, "পেজ খোলা যায়নি", Toast.LENGTH_SHORT).show();
                }
            });
        }

        findViewById(R.id.svcSave).setOnClickListener(v -> {
            int h = parseInt(hour.getText().toString(), 9);
            int m = parseInt(minute.getText().toString(), 0);
            if (h < 0 || h > 23) h = 9;
            if (m < 0 || m > 59) m = 0;
            SharedPreferences.Editor ed = prefs.edit()
                    .putString("conn_" + svc + "_token", token.getText().toString().trim())
                    .putString("conn_" + svc + "_user", userField.getText().toString().trim())
                    .putString("conn_" + svc + "_pass", passField.getText().toString().trim())
                    .putString("conn_" + svc + "_id", idField.getText().toString().trim())
                    .putString("conn_" + svc + "_rules", rules.getText().toString())
                    .putInt("conn_" + svc + "_hour", h)
                    .putInt("conn_" + svc + "_minute", m)
                    .putBoolean("conn_" + svc + "_enabled", enabled.isChecked())
                    .putBoolean("conn_" + svc + "_comments", comments.isChecked())
                    .putBoolean("conn_" + svc + "_post", post.isChecked())
                    .putBoolean("conn_" + svc + "_approve", approve.isChecked())
                    .putBoolean("conn_" + svc + "_browser", browser.isChecked())
                    .putString("conn_" + svc + "_site", siteField.getText().toString().trim());
            if (isCustom) ed.putString("conn_custom_name", customName.getText().toString().trim());
            ed.apply();
            if (!token.getText().toString().trim().isEmpty()
                    || !userField.getText().toString().trim().isEmpty()
                    || browser.isChecked())
                NvnCore.markConnected(this, svc, null);
            new Thread(() -> NvnCore.backupData(getApplicationContext())).start();
            ConnectWorker.scheduleAll(getApplicationContext());
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private int parseInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private String infoFor(String svc) {
        switch (svc) {
            case "facebook":
                return "Token: তোমার Page-এর Access Token। ID: Page ID। কমেন্টের উত্তর আর পোস্ট — দুটোই অটো চলবে।";
            case "instagram":
                return "Token: Instagram Graph API Token। ID: Instagram User ID। Token ছাড়া চালাতে চাইলে নিচে ব্রাউজার মোড চালু করো — Chrome-এ লগইন থাকলেই সে নিজে গিয়ে পোস্ট করবে।";
            case "threads":
                return "Token: Threads API Token। ID: Threads User ID। টেক্সট পোস্ট অটো চলবে।";
            case "youtube":
                return "Token: YouTube API Key। ID: Channel ID। Token ছাড়া চালাতে নিচে ব্রাউজার মোড চালু করো — Chrome-এ লগইন থাকলেই সে নিজে গিয়ে কাজ করবে।";
            case "telegram":
                return "Token: BotFather থেকে বটের Token। ID: চ্যানেল/গ্রুপের ID (যেমন @mychannel বা -1001234567890)। বটকে চ্যানেল/গ্রুপে অ্যাডমিন করতে হবে।";
            case "website":
                return "ID-এর ঘরে তোমার ওয়েবসাইটের পোস্ট নেওয়ার URL বসাও। Token থাকলে বসাও, না থাকলে খালি রাখো। পোস্ট JSON আকারে যাবে।";
            case "custom":
                return "যেকোনো সাইট: নাম দাও, ID-এর ঘরে সেই সাইটের পোস্ট নেওয়ার URL, Token থাকলে বসাও।";
            case "blogger":
                return "Token: Google OAuth Access Token (OAuth Playground থেকে)। ID: Blog ID (Blogger সেটিংসে পাবে)। পোস্ট অটো চলবে, তবে Token-এর মেয়াদ শেষ হলে আবার বসাতে হবে।";
            case "wordpress":
                return "ID: সাইটের ঠিকানা (যেমন https://example.com)। Username: তোমার WordPress username। Password: Application Password (সাইটের প্রোফাইল পেজ থেকে বানাতে হয়, আসল পাসওয়ার্ড না)। পোস্ট অটো চলবে।";
            case "tiktok":
                return "TikTok-এর API অনুমোদন কঠিন। Token ছাড়া চালাতে নিচে ব্রাউজার মোড চালু করো — Chrome-এ TikTok লগইন থাকলেই সে নিজে গিয়ে কাজ করবে।";
            case "x":
                return "X-এর API-র জন্য Developer অ্যাকাউন্ট লাগে। Token ছাড়া চালাতে নিচে ব্রাউজার মোড চালু করো — Chrome-এ X লগইন থাকলেই সে নিজে গিয়ে পোস্ট করবে।";
            case "pinterest":
                return "Token: Pinterest API Token। ID: Board ID। Token ছাড়া চালাতে নিচে ব্রাউজার মোড চালু করো — Chrome-এ লগইন থাকলেই সে নিজে গিয়ে কাজ করবে।";
            case "whatsapp":
                return "WhatsApp-এর অফিসিয়াল অটো উপায় নেই। ব্রাউজার মোড চালু করলে সে WhatsApp Web-এ গিয়ে কাজ করবে (Chrome-এ লগইন/QR স্ক্যান থাকতে হবে)।";
            case "gmail":
                return "Token: Google OAuth Access Token। Token ছাড়া চালাতে নিচে ব্রাউজার মোড চালু করো — Chrome-এ Gmail লগইন থাকলেই সে নিজে গিয়ে কাজ করবে।";
            default:
                return "";
        }
    }

    private String tokenUrlFor(String svc) {
        switch (svc) {
            case "facebook":
            case "instagram":
            case "threads":
                return "https://developers.facebook.com/tools/explorer/";
            case "youtube":
                return "https://console.cloud.google.com/apis/credentials";
            case "telegram":
                return "https://t.me/BotFather";
            case "blogger":
            case "gmail":
                return "https://developers.google.com/oauthplayground/";
            case "wordpress":
                return "https://wordpress.org/support/article/application-passwords/";
            case "tiktok":
                return "https://developers.tiktok.com/";
            case "x":
                return "https://developer.x.com/en/portal/dashboard";
            case "pinterest":
                return "https://developers.pinterest.com/apps/";
            default:
                return null;
        }
    }
}
