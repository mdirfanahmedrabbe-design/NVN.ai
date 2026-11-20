package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView onlinePill;
    private GirlView mainGirl;
    private int tickCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable usageTicker = new Runnable() {
        @Override public void run() {
            refreshUsage();
            checkAnnounce();
            tickCount++;
            if (mainGirl != null) {
                if (tickCount % 3 == 1) mainGirl.dance();
                else if (tickCount % 3 == 2) mainGirl.jump();
            }
            if (tickCount % 2 == 0) new Thread(() -> NvnCore.backupData(MainActivity.this)).start();
            handler.postDelayed(this, 30000);
        }
    };

    private void checkAnnounce() {
        new Thread(() -> {
            String[] ann = NvnCore.checkAnnouncement(this);
            if (ann != null) NvnCore.showAnnouncementNotification(this, ann[0], ann[1]);
        }).start();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        onlinePill = findViewById(R.id.onlinePill);
        mainGirl = findViewById(R.id.mainGirl);
        Button liveBtn = findViewById(R.id.liveBtn);

        findViewById(R.id.menuBtn).setOnClickListener(v -> showFeatureMenu());
        findViewById(R.id.bellBtn).setOnClickListener(v -> showStatusDialog());
        liveBtn.setOnClickListener(v -> openLive("camera"));
        findViewById(R.id.navHome).setOnClickListener(v -> refreshUsage());
        findViewById(R.id.navHistory).setOnClickListener(v -> showHistory());
        findViewById(R.id.navShare).setOnClickListener(v -> shareApp());
        findViewById(R.id.navSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.navSettings).setOnLongClickListener(v -> {
            ownerPinDialog();
            return true;
        });

        NvnCore.restoreData(this);
        fetchServerConfig();
        refreshUsage();
        if (prefs.getString("girl_name", "").isEmpty()) showNameDialog();
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setHint("নাম লিখো");
        new AlertDialog.Builder(this)
                .setTitle("আসসালামু আলাইকুম মাস্টার! 💜")
                .setMessage("আমি আপনার নতুন সঙ্গী। আমাকে একটা সুন্দর নাম দিন তো — এই নামেই আমি সবসময় আপনার পাশে থাকব।")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("নাম দাও", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "NVN";
                    prefs.edit().putString("girl_name", name).apply();
                    if (mainGirl != null) { mainGirl.wave(); mainGirl.dance(); }
                    new Thread(() -> NvnCore.backupData(this)).start();
                    Toast.makeText(this, "ধন্যবাদ মাস্টার! এখন থেকে আমি " + name + " 💜", Toast.LENGTH_LONG).show();
                })
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsage();
        handler.post(usageTicker);
        if (mainGirl != null) mainGirl.wave();
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(usageTicker);
        super.onPause();
    }

    private void refreshUsage() {
        try {
            int used = NvnCore.liveSecondsToday(this) / 60;
            int limit = NvnCore.TIER_LIVE_MIN[NvnCore.tierIndex(this)];
            onlinePill.setText("● Online for " + used + " min  (আজ " + used + "/" + limit + ")");
        } catch (Exception ignored) {}
    }

    private void openLive(String mode) {
        Intent i = new Intent(this, LiveActivity.class);
        i.putExtra("mode", mode);
        i.putExtra("autostart", true);
        startActivity(i);
    }

    private void showFeatureMenu() {
        String[] items = {
                "📹 Live Camera",
                "🖥 Share Screen",
                "👑 Premium",
                "📰 আজকের খবর",
                "✅ কাজের লিস্ট",
                "🕋 কিবলা",
                "📿 তসবিহ",
                "⚙ সেটিংস",
                "🔗 অ্যাপ শেয়ার করো"
        };
        new AlertDialog.Builder(this)
                .setTitle("NVN AI — সব ফিচার")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openLive("camera"); break;
                        case 1: openLive("both"); break;
                        case 2: startActivity(new Intent(this, PremiumActivity.class)); break;
                        case 3: showHeadlines(); break;
                        case 4: showTasks(); break;
                        case 5: startActivity(new Intent(this, QiblaActivity.class)); break;
                        case 6: startActivity(new Intent(this, TasbihActivity.class)); break;
                        case 7: startActivity(new Intent(this, SettingsActivity.class)); break;
                        case 8: shareApp(); break;
                    }
                })
                .show();
    }

    private void showStatusDialog() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        int tier = NvnCore.tierIndex(this);
        long exp = p.getLong("premium_expiry", 0);
        String expText = exp > 0
                ? new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH).format(new Date(exp))
                : "—";
        int usedMin = NvnCore.liveSecondsToday(this) / 60;
        int limitMin = NvnCore.TIER_LIVE_MIN[tier];
        String annTitle = p.getString("last_announce_title", "");
        String annBody = p.getString("last_announce_body", "");
        String annText = annTitle.isEmpty() ? "" : "📢 " + annTitle + "\n" + annBody + "\n\n";
        String msg = annText + "প্যাকেজ: " + NvnCore.TIER_NAMES[tier] + "\n"
                + "মেয়াদ: " + expText + "\n"
                + "আজ Live: " + usedMin + "/" + limitMin + " মিনিট\n"
                + "আজ প্রশ্ন: " + NvnCore.questionsUsedToday(this) + "/" + NvnCore.TIER_Q[tier] + "\n"
                + "মালিক মোড: " + (p.getBoolean("owner_mode", false) ? "চালু" : "বন্ধ");
        new AlertDialog.Builder(this)
                .setTitle("🔔 অবস্থা")
                .setMessage(msg)
                .setPositiveButton("ঠিক আছে", null)
                .show();
    }

    private void showHistory() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String hist = p.getString("chat_history", "");
        new AlertDialog.Builder(this)
                .setTitle("🕐 History")
                .setMessage(hist.isEmpty() ? "কোনো ইতিহাস নেই।" : hist)
                .setPositiveButton("ঠিক আছে", null)
                .setNegativeButton("মুছে ফেলো", (d, w) -> {
                    p.edit().remove("chat_history").apply();
                    Toast.makeText(this, "ইতিহাস মুছে গেছে", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void showHeadlines() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("📰 আজকের খবর")
                .setMessage("খবর লোড হচ্ছে...")
                .setPositiveButton("ঠিক আছে", null)
                .show();
        new Thread(() -> {
            String result;
            try {
                URL url = new URL("https://feeds.bbci.co.uk/bengali/rss.xml");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                String xml = sb.toString();
                StringBuilder out = new StringBuilder();
                int idx = 0, count = 0;
                while (count < 5) {
                    int start = xml.indexOf("<item>", idx);
                    if (start < 0) break;
                    int tStart = xml.indexOf("<title>", start);
                    int tEnd = xml.indexOf("</title>", tStart);
                    if (tStart < 0 || tEnd < 0) break;
                    String title = xml.substring(tStart + 7, tEnd)
                            .replace("<![CDATA[", "").replace("]]>", "").trim();
                    out.append("• ").append(title).append(System.lineSeparator());
                    idx = tEnd;
                    count++;
                }
                result = count > 0 ? out.toString().trim() : "খবর পাইনি।";
            } catch (Exception e) {
                result = "খবর আনতে পারলাম না।";
            }
            String finalResult = result;
            runOnUiThread(() -> dialog.setMessage(finalResult));
        }).start();
    }

    private void showTasks() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String list = p.getString("todo_list", "");
        EditText input = new EditText(this);
        input.setHint("নতুন কাজ লেখো");
        new AlertDialog.Builder(this)
                .setTitle("✅ কাজের লিস্ট")
                .setMessage(list.isEmpty() ? "কোনো কাজ নেই।" : list.trim())
                .setView(input)
                .setPositiveButton("যোগ করো", (d, w) -> {
                    String item = input.getText().toString().trim();
                    if (!item.isEmpty()) {
                        String cur = p.getString("todo_list", "");
                        p.edit().putString("todo_list", cur + "• " + item + System.lineSeparator()).apply();
                        Toast.makeText(this, "যোগ হয়েছে", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNeutralButton("সব মুছো", (d, w) -> {
                    p.edit().putString("todo_list", "").apply();
                    Toast.makeText(this, "সব মুছে গেছে", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("বন্ধ", null)
                .show();
    }

    private void shareApp() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,
                "NVN AI — বাংলা ভয়েস অ্যাসিস্ট্যান্ট অ্যাপ\nডাউনলোড: https://github.com/mdirfanahmedrabbe-design/NVN.ai/releases/latest");
        startActivity(Intent.createChooser(send, "শেয়ার করো"));
    }

    private void ownerPinDialog() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String hash = p.getString("owner_pin_hash", "");
        EditText pinInput = new EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN");
        new AlertDialog.Builder(this)
                .setTitle(hash.isEmpty() ? "মালিকের PIN সেট করো" : "মালিকের PIN দাও")
                .setView(pinInput)
                .setPositiveButton("ঠিক আছে", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (pin.length() < 4) { Toast.makeText(this, "PIN কমপক্ষে ৪ ডিজিট", Toast.LENGTH_SHORT).show(); return; }
                    if (hash.isEmpty()) {
                        p.edit().putString("owner_pin_hash", NvnCore.sha256Hex(pin)).putBoolean("owner_mode", true).apply();
                        Toast.makeText(this, "মালিক মোড চালু — লিমিট নেই", Toast.LENGTH_SHORT).show();
                    } else if (NvnCore.sha256Hex(pin).equals(hash)) {
                        boolean now = !p.getBoolean("owner_mode", false);
                        p.edit().putBoolean("owner_mode", now).apply();
                        Toast.makeText(this, now ? "মালিক মোড চালু" : "মালিক মোড বন্ধ", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "PIN ভুল", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
    }

    private String httpGet(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void fetchServerConfig() {
        new Thread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
                String keysBody = httpGet(NvnCore.SERVER_BASE + "/keys");
                if (keysBody != null && !keysBody.trim().isEmpty()) {
                    String decoded = NvnCore.xorDecode(keysBody.trim());
                    if (decoded.contains("AIza")) {
                        p.edit().putString("gemini_keys", decoded).apply();
                    }
                }
                String cfg = httpGet(NvnCore.SERVER_BASE + "/config");
                if (cfg != null && cfg.contains("bkash_number")) {
                    JSONObject o = new JSONObject(cfg);
                    String num = o.optString("bkash_number", "");
                    if (!num.isEmpty()) p.edit().putString("bkash_number", num).apply();
                }
            } catch (Exception ignored) {}
        }).start();
    }
}
