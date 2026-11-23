package com.archer.ai;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Composio-style connect catalog: search + category chips + 2-column card grid.
// Tap a card (or its Connect button) -> per-service page (ServiceActivity).
public class ConnectActivity extends Activity {

    // Catalog: id, display name, search keywords, category, brand color
    private static final String[][] CATALOG = {
            {"facebook", "📘 Facebook Page", "facebook fb ফেসবুক পেজ page", "social", "#1877F2"},
            {"instagram", "📸 Instagram", "instagram insta ইনস্টাগ্রাম ig", "social", "#E1306C"},
            {"threads", "🧵 Threads", "threads থ্রেড", "social", "#555555"},
            {"youtube", "▶️ YouTube", "youtube ইউটিউব video ভিডিও", "social", "#FF0000"},
            {"telegram", "✈️ Telegram", "telegram টেলিগ্রাম tg", "messaging", "#229ED9"},
            {"blogger", "📝 Blogger", "blogger blog ব্লগার ব্লগ blogspot", "blogging", "#FF5722"},
            {"wordpress", "📰 WordPress", "wordpress ওয়ার্ডপ্রেস wp", "blogging", "#21759B"},
            {"tiktok", "🎵 TikTok", "tiktok টিকটক", "social", "#555555"},
            {"x", "🐦 X (Twitter)", "x twitter টুইটার এক্স", "social", "#555555"},
            {"pinterest", "📌 Pinterest", "pinterest পিন্টারেস্ট pin", "social", "#E60023"},
            {"whatsapp", "💬 WhatsApp", "whatsapp হোয়াটসঅ্যাপ wa", "messaging", "#25D366"},
            {"gmail", "📧 Gmail", "gmail email জিমেইল ইমেইল mail", "email", "#EA4335"},
            {"website", "🌐 ওয়েবসাইট", "website ওয়েবসাইট site সাইট web", "website", "#2EE59D"},
            {"custom", "🔗 কাস্টম সাইট", "custom কাস্টম other অন্য", "website", "#D4AF37"},
            {"linkedin", "💼 LinkedIn", "linkedin লিংকডইন job চাকরি", "social", "#0A66C2"},
            {"snapchat", "👻 Snapchat", "snapchat স্ন্যাপচ্যাট snap", "social", "#E6C200"},
            {"reddit", "🤖 Reddit", "reddit রেডিট", "social", "#FF4500"},
            {"discord", "🎮 Discord", "discord ডিসকর্ড", "messaging", "#5865F2"},
            {"medium", "✍️ Medium", "medium মিডিয়াম লেখা", "blogging", "#555555"},
            {"substack", "📬 Substack", "substack সাবস্ট্যাক newsletter", "blogging", "#FF6719"},
            {"shopify", "🛍️ Shopify", "shopify শপিফাই shop দোকান store", "website", "#96BF48"},
            {"slack", "💬 Slack", "slack স্ল্যাক", "messaging", "#611F69"},
            {"spotify", "🎧 Spotify", "spotify স্পটিফাই music গান", "social", "#1DB954"},
            {"github", "🐙 GitHub", "github গিটহাব code কোড", "website", "#555555"},
            {"googledrive", "📁 Google Drive", "drive google drive ড্রাইভ ফাইল", "website", "#4285F4"},
            {"notion", "📓 Notion", "notion নোশন note নোট", "website", "#555555"},
            {"canva", "🎨 Canva", "canva ক্যানভা design ডিজাইন", "website", "#00C4CC"},
            {"quora", "❓ Quora", "quora কোরা প্রশ্ন", "social", "#B92B27"},
            {"twitch", "🟣 Twitch", "twitch টুইচ live stream", "social", "#9146FF"},
            {"viber", "📞 Viber", "viber ভাইবার", "messaging", "#7360F2"},
            {"imo", "📱 IMO", "imo ইমো", "messaging", "#00A0E9"},
            {"likee", "🎶 Likee", "likee লাইকি video ভিডিও", "social", "#FF0050"},
            {"tumblr", "📓 Tumblr", "tumblr টাম্বলার blog ব্লগ", "blogging", "#35465C"},
            {"mastodon", "🐘 Mastodon", "mastodon মাস্টোডন", "social", "#6364FF"},
    };

    private static final String[][] CATEGORIES = {
            {"all", "সব"},
            {"social", "সোশ্যাল"},
            {"messaging", "মেসেজিং"},
            {"blogging", "ব্লগিং"},
            {"email", "ইমেইল"},
            {"website", "ওয়েবসাইট"},
    };

    private String query = "";
    private String category = "all";

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
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    private void buildList() {
        LinearLayout list = findViewById(R.id.connList);
        list.removeAllViews();
        float d = getResources().getDisplayMetrics().density;

        // ---- category chips ----
        HorizontalScrollView chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chipRow = new LinearLayout(this);
        chipRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String[] cat : CATEGORIES) {
            TextView chip = new TextView(this);
            chip.setText(cat[1]);
            chip.setTextSize(13);
            chip.setPadding((int) (14 * d), (int) (7 * d), (int) (14 * d), (int) (7 * d));
            boolean sel = cat[0].equals(category);
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(20 * d);
            bg.setColor(sel ? Color.parseColor("#D4AF37") : Color.parseColor("#0A3D2E"));
            bg.setStroke((int) (1 * d), Color.parseColor("#D4AF37"));
            chip.setBackground(bg);
            chip.setTextColor(sel ? Color.parseColor("#04170F") : Color.parseColor("#F3E9C8"));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.rightMargin = (int) (8 * d);
            chip.setLayoutParams(clp);
            final String catId = cat[0];
            chip.setOnClickListener(v -> {
                category = catId;
                buildList();
            });
            chipRow.addView(chip);
        }
        chipScroll.addView(chipRow);
        LinearLayout.LayoutParams csLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        csLp.bottomMargin = (int) (12 * d);
        chipScroll.setLayoutParams(csLp);
        list.addView(chipScroll);

        // ---- filtered entries ----
        List<String[]> shown = new ArrayList<>();
        for (String[] entry : CATALOG) {
            if (!"all".equals(category) && !entry[3].equals(category)) continue;
            if (!query.isEmpty()
                    && !entry[1].toLowerCase(Locale.ROOT).contains(query)
                    && !entry[2].contains(query)
                    && !entry[0].contains(query)) {
                continue;
            }
            shown.add(entry);
        }

        // Any searched site/app should come up: if the query matches no catalog
        // entry exactly, add a "new site" card that opens the custom connect page.
        if (!query.isEmpty()) {
            boolean exact = false;
            for (String[] entry : CATALOG) {
                if (entry[0].equals(query)
                        || entry[1].toLowerCase(Locale.ROOT).endsWith(query)) {
                    exact = true;
                    break;
                }
            }
            if (!exact) {
                String title = query.substring(0, 1).toUpperCase(Locale.ROOT) + query.substring(1);
                shown.add(new String[]{"custom", "🔗 " + title, "", "all", "#D4AF37", title});
            }
        }

        // ---- 2-column grid of cards ----
        for (int i = 0; i < shown.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rLp.bottomMargin = (int) (10 * d);
            row.setLayoutParams(rLp);
            row.addView(makeCard(shown.get(i), d, true));
            if (i + 1 < shown.size()) {
                row.addView(makeCard(shown.get(i + 1), d, false));
            } else {
                // spacer to keep half width
                android.view.View spacer = new android.view.View(this);
                spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
                row.addView(spacer);
            }
            list.addView(row);
        }

    }

    private LinearLayout makeCard(String[] entry, float d, boolean left) {
        String svc = entry[0];
        String fullName = entry[1];
        // split emoji + name
        String emoji = fullName.substring(0, fullName.indexOf(' '));
        String name = fullName.substring(fullName.indexOf(' ') + 1);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_card);
        card.setPadding((int) (10 * d), (int) (14 * d), (int) (10 * d), (int) (12 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        if (left) lp.rightMargin = (int) (5 * d); else lp.leftMargin = (int) (5 * d);
        card.setLayoutParams(lp);

        // round brand icon
        TextView icon = new TextView(this);
        icon.setText(emoji);
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.parseColor(entry[4]));
        icon.setBackground(circle);
        icon.setLayoutParams(new LinearLayout.LayoutParams((int) (52 * d), (int) (52 * d)));
        card.addView(icon);

        TextView nameTv = new TextView(this);
        nameTv.setText(name);
        nameTv.setTextColor(0xFFF3E9C8);
        nameTv.setTextSize(15);
        nameTv.setTypeface(null, android.graphics.Typeface.BOLD);
        nameTv.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.topMargin = (int) (8 * d);
        nameTv.setLayoutParams(nLp);
        card.addView(nameTv);

        boolean connected = ConnectWorker.isConnected(this, svc);
        boolean browserMode = ConnectWorker.isBrowserMode(this, svc);
        boolean enabled = getSharedPreferences("archer", MODE_PRIVATE)
                .getBoolean("conn_" + svc + "_enabled", false);

        TextView status = new TextView(this);
        status.setTextSize(11);
        status.setGravity(Gravity.CENTER);
        if (connected) {
            status.setText(enabled ? "✅ যুক্ত আছে · কাজ চালু" : "✅ যুক্ত আছে · কাজ বন্ধ");
            status.setTextColor(0xFF2EE59D);
        } else if (browserMode) {
            status.setText(enabled ? "🌐 ব্রাউজার মোড · চালু" : "🌐 ব্রাউজার মোড · বন্ধ");
            status.setTextColor(0xFFE7C873);
        } else {
            status.setText("যুক্ত করা বাকি");
            status.setTextColor(0xFFA8C3B2);
        }
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sLp.topMargin = (int) (4 * d);
        status.setLayoutParams(sLp);
        card.addView(status);

        // device + account line when connected (Composio-style)
        if (connected || browserMode) {
            android.content.SharedPreferences pp = getSharedPreferences("archer", MODE_PRIVATE);
            String device = pp.getString("conn_" + svc + "_device", "");
            if (device.isEmpty()) device = NvnCore.deviceName();
            String account = pp.getString("conn_" + svc + "_account", "");
            TextView info = new TextView(this);
            info.setText("📱 " + device + (account.isEmpty() ? "" : " · " + account));
            info.setTextSize(10);
            info.setTextColor(0xFFA8C3B2);
            info.setGravity(Gravity.CENTER);
            card.addView(info);
        }

        Button btn = new Button(this);
        btn.setText(connected || browserMode ? "খুলুন" : "যুক্ত করুন");
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(13);
        btn.setBackgroundResource(connected || browserMode ? R.drawable.btn_secondary : R.drawable.btn_primary);
        LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bLp.topMargin = (int) (10 * d);
        btn.setLayoutParams(bLp);
        View.OnClickListener open = v -> {
            Intent i = new Intent(this, ServiceActivity.class);
            i.putExtra("svc", svc);
            if (entry.length > 5) i.putExtra("prefill_name", entry[5]);
            startActivity(i);
        };
        btn.setOnClickListener(open);
        card.setOnClickListener(open);
        card.addView(btn);

        return card;
    }
}
