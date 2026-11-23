package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// Browser settings (Chrome-style): search engine, text size, JavaScript,
// images, cookies, zoom controls, desktop default, homepage.
public class BrowserSettingsActivity extends Activity {

    private SharedPreferences bp;
    private float d;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        bp = getSharedPreferences("nvn_browser", MODE_PRIVATE);
        d = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_main);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("⚙️ ব্রাউজার সেটিংস");
        title.setTextColor(0xFFE7C873);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        // Search engine (tap to change)
        addCycleRow(root, "🔎 সার্চ ইঞ্জিন", "search_engine",
                new String[]{"google", "bing", "ddg"},
                new String[]{"Google", "Bing", "DuckDuckGo"}, "google");

        // Text size (tap to change)
        addCycleRow(root, "🔤 লেখার আকার", "text_zoom",
                new String[]{"85", "100", "115", "130"},
                new String[]{"ছোট", "সাধারণ", "বড়", "খুব বড়"}, "100");

        addToggleRow(root, "JavaScript", "js_on", true);
        addToggleRow(root, "ছবি দেখাও", "images_on", true);
        addToggleRow(root, "কুকি (লগইন মনে রাখে)", "cookies_on", true);
        addToggleRow(root, "জুম বোতাম", "zoom_controls", true);
        addToggleRow(root, "ডেস্কটপ সাইট (ডিফল্ট)", "desktop_default", false);

        // Homepage
        TextView homeLabel = new TextView(this);
        homeLabel.setText("🏠 হোমপেজ (খালি রাখলে স্টার্ট পেজ)");
        homeLabel.setTextColor(0xFFF3E9C8);
        homeLabel.setTextSize(15);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hl.topMargin = (int) (14 * d);
        homeLabel.setLayoutParams(hl);
        root.addView(homeLabel);

        EditText homeInput = new EditText(this);
        homeInput.setHint("https://…");
        homeInput.setText(bp.getString("homepage", ""));
        homeInput.setTextColor(0xFFF3E9C8);
        homeInput.setHintTextColor(0xFF8AA79B);
        homeInput.setBackgroundResource(R.drawable.bg_input);
        homeInput.setPadding((int) (12 * d), (int) (10 * d), (int) (12 * d), (int) (10 * d));
        root.addView(homeInput);

        Button saveHome = new Button(this);
        saveHome.setText("হোমপেজ সেভ করুন");
        saveHome.setBackgroundResource(R.drawable.btn_primary);
        saveHome.setTextColor(0xFFFFFFFF);
        saveHome.setOnClickListener(v -> {
            bp.edit().putString("homepage", homeInput.getText().toString().trim()).apply();
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
        });
        root.addView(saveHome);

        Button clearData = new Button(this);
        clearData.setText("🗑 ব্রাউজিং ডেটা মুছুন");
        clearData.setBackgroundResource(R.drawable.btn_secondary);
        clearData.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams cdLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cdLp.topMargin = (int) (14 * d);
        clearData.setLayoutParams(cdLp);
        clearData.setOnClickListener(v -> {
            CookieManager.getInstance().removeAllCookies(null);
            bp.edit().remove("history").apply();
            Toast.makeText(this, "ডেটা মোছা হয়েছে", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearData);

        TextView note = new TextView(this);
        note.setText("সেটিংস ব্রাউজারে ফিরলেই কাজ করবে।");
        note.setTextColor(0xFFA8C3B2);
        note.setTextSize(12);
        note.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.topMargin = (int) (12 * d);
        note.setLayoutParams(nLp);
        root.addView(note);

        setContentView(scroll);
    }

    private void addCycleRow(LinearLayout root, String label, String key,
                             String[] values, String[] labels, String def) {
        TextView row = new TextView(this);
        row.setTextColor(0xFFF3E9C8);
        row.setTextSize(15);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding((int) (14 * d), (int) (13 * d), (int) (14 * d), (int) (13 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * d);
        row.setLayoutParams(lp);
        Runnable refresh = () -> {
            String cur = bp.getString(key, def);
            int idx = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) idx = i;
            row.setText(label + "  —  " + labels[idx]);
        };
        refresh.run();
        row.setOnClickListener(v -> {
            String cur = bp.getString(key, def);
            int idx = 0;
            for (int i = 0; i < values.length; i++) if (values[i].equals(cur)) idx = i;
            bp.edit().putString(key, values[(idx + 1) % values.length]).apply();
            refresh.run();
        });
        root.addView(row);
    }

    private void addToggleRow(LinearLayout root, String label, String key, boolean def) {
        TextView row = new TextView(this);
        row.setTextColor(0xFFF3E9C8);
        row.setTextSize(15);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding((int) (14 * d), (int) (13 * d), (int) (14 * d), (int) (13 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = (int) (10 * d);
        row.setLayoutParams(lp);
        Runnable refresh = () -> row.setText(label + "  —  " + (bp.getBoolean(key, def) ? "চালু ✓" : "বন্ধ"));
        refresh.run();
        row.setOnClickListener(v -> {
            bp.edit().putBoolean(key, !bp.getBoolean(key, def)).apply();
            refresh.run();
        });
        root.addView(row);
    }
}
