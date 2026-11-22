package com.archer.ai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.Locale;

public class ConnectActivity extends Activity {

    // Catalog: id, display name, search keywords
    private static final String[][] CATALOG = {
            {"facebook", "📘 Facebook Page", "facebook fb ফেসবুক পেজ page"},
            {"instagram", "📸 Instagram", "instagram insta ইনস্টাগ্রাম ig"},
            {"threads", "🧵 Threads", "threads থ্রেড"},
            {"youtube", "▶️ YouTube", "youtube ইউটিউব video ভিডিও"},
            {"telegram", "✈️ Telegram", "telegram টেলিগ্রাম tg"},
            {"blogger", "📝 Blogger", "blogger blog ব্লগার ব্লগ blogspot"},
            {"wordpress", "📰 WordPress", "wordpress ওয়ার্ডপ্রেস wp"},
            {"tiktok", "🎵 TikTok", "tiktok টিকটক"},
            {"x", "🐦 X (Twitter)", "x twitter টুইটার এক্স"},
            {"pinterest", "📌 Pinterest", "pinterest পিন্টারেস্ট pin"},
            {"whatsapp", "💬 WhatsApp", "whatsapp হোয়াটসঅ্যাপ wa"},
            {"gmail", "📧 Gmail", "gmail email জিমেইল ইমেইল mail"},
            {"website", "🌐 ওয়েবসাইট", "website ওয়েবসাইট site সাইট web"},
            {"custom", "🔗 কাস্টম সাইট", "custom কাস্টম other অন্য"},
    };

    private String query = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);

        EditText search = findViewById(R.id.connSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                query = s.toString().toLowerCase(Locale.ROOT).trim();
                buildList();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        buildList();

        findViewById(R.id.connRunNow).setOnClickListener(v -> {
            ConnectWorker.runInBackground(getApplicationContext(), true);
            Toast.makeText(this, "কাজ শুরু হয়েছে — শেষ হলে জানাবো", Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.connApprovals).setOnClickListener(v ->
                startActivity(new Intent(this, ApprovalActivity.class)));
        findViewById(R.id.connHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    private void buildList() {
        LinearLayout list = findViewById(R.id.connList);
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (String[] entry : CATALOG) {
            String svc = entry[0];
            String name = entry[1];
            String keywords = entry[2];
            if (!query.isEmpty()
                    && !name.toLowerCase(Locale.ROOT).contains(query)
                    && !keywords.contains(query)
                    && !svc.contains(query)) {
                continue;
            }
            Button b = new Button(this);
            boolean connected = ConnectWorker.isConnected(this, svc);
            boolean browserMode = ConnectWorker.isBrowserMode(this, svc);
            boolean enabled = getSharedPreferences("archer", MODE_PRIVATE)
                    .getBoolean("conn_" + svc + "_enabled", false);
            String status;
            if (connected) {
                status = enabled ? "✅ কানেক্ট আছে, কাজ চালু" : "✅ কানেক্ট আছে, কাজ বন্ধ";
            } else if (browserMode) {
                status = enabled ? "🌐 ব্রাউজার মোড, কাজ চালু" : "🌐 ব্রাউজার মোড, কাজ বন্ধ";
            } else {
                status = "⚠️ Token বসানো বাকি — টিপ দিয়ে কানেক্ট করো";
            }
            if (connected || browserMode) {
                // Composio-style: show which phone + account this connection is on
                android.content.SharedPreferences pp =
                        getSharedPreferences("archer", MODE_PRIVATE);
                String device = pp.getString("conn_" + svc + "_device", "");
                if (device.isEmpty()) device = NvnCore.deviceName();
                String account = pp.getString("conn_" + svc + "_account", "");
                String since = pp.getString("conn_" + svc + "_since", "");
                StringBuilder info = new StringBuilder("📱 " + device);
                if (!account.isEmpty()) info.append(" · ").append(account);
                if (!since.isEmpty()) info.append(" · কানেক্ট: ").append(since);
                status += "\n" + info;
            }
            b.setText(name + "\n" + status);
            b.setTextColor(0xFFE8F4FF);
            b.setBackgroundResource(R.drawable.btn_secondary);
            b.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * density);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                Intent i = new Intent(this, ServiceActivity.class);
                i.putExtra("svc", svc);
                startActivity(i);
            });
            list.addView(b);
        }
        int q = ConnectWorker.getQueue(this).length();
        Button appr = findViewById(R.id.connApprovals);
        appr.setText("📋 অনুমোদনের অপেক্ষায়" + (q > 0 ? " (" + q + ")" : ""));
    }
}
