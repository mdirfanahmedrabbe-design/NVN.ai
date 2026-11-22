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

        boolean isCustom = "custom".equals(svc);
        findViewById(R.id.svcCustomNameWrap).setVisibility(isCustom ? View.VISIBLE : View.GONE);
        if (isCustom) customName.setText(prefs.getString("conn_custom_name", ""));

        info.setText(infoFor(svc));

        String tokenUrl = tokenUrlFor(svc);
        android.view.View tokenBtn = findViewById(R.id.svcTokenBtn);
        if (tokenUrl == null) {
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
                    .putBoolean("conn_" + svc + "_approve", approve.isChecked());
            if (isCustom) ed.putString("conn_custom_name", customName.getText().toString().trim());
            ed.apply();
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
                return "Token: Instagram Graph API Token। ID: Instagram User ID। এই ভার্সনে পোস্টের ড্রাফট তৈরি হবে, অনুমোদনের পর অ্যাপে গিয়ে পোস্ট করতে হবে।";
            case "threads":
                return "Token: Threads API Token। ID: Threads User ID। টেক্সট পোস্ট অটো চলবে।";
            case "youtube":
                return "Token: YouTube API Key। ID: Channel ID। এই ভার্সনে ভিডিওর টাইটেল/বর্ণনার ড্রাফট তৈরি হবে।";
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
                return "TikTok-এ অটো পোস্টের জন্য TikTok-এর অনুমোদিত অ্যাপ লাগে। এই ভার্সনে ভিডিওর ক্যাপশন/ড্রাফট তৈরি হবে।";
            case "x":
                return "X (Twitter)-এ অটো পোস্টের জন্য X Developer অ্যাকাউন্ট লাগে। এই ভার্সনে পোস্টের ড্রাফট তৈরি হবে।";
            case "pinterest":
                return "Token: Pinterest API Token। ID: Board ID। এই ভার্সনে পিনের ড্রাফট তৈরি হবে।";
            case "whatsapp":
                return "WhatsApp-এ ব্যক্তিগত অ্যাকাউন্টে অটো পোস্টের অফিসিয়াল উপায় নেই। এই ভার্সনে মেসেজের ড্রাফট তৈরি হবে।";
            case "gmail":
                return "Token: Google OAuth Access Token। এই ভার্সনে ইমেইলের ড্রাফট তৈরি হবে।";
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
