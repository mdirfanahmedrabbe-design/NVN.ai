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
    private TextView dashText;
    private Button liveBtn;
    private boolean torchOn = false;
    private SharedPreferences prefs;
    private int tickCount = 0;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable usageTicker = new Runnable() {
        @Override public void run() {
            refreshUsage();
            checkAnnounce();
            tickCount++;
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
        prefs = getSharedPreferences("archer", MODE_PRIVATE);

        // Emerald Palace design: ball defaults to emerald+gold (palette 2); migrate old defaults once
        if (!prefs.getBoolean("emerald_design_applied", false)) {
            prefs.edit().putString("orb_color", "2").putBoolean("emerald_design_applied", true).apply();
        }
        if (!prefs.getBoolean("emerald2_applied", false)) {
            prefs.edit().putString("orb_color", "2").putBoolean("emerald2_applied", true).apply();
        }
        if (prefs.getBoolean("bubble_on", false)
                && (android.os.Build.VERSION.SDK_INT < 23 || android.provider.Settings.canDrawOverlays(this))) {
            try { startService(new Intent(this, BubbleService.class)); } catch (Exception ignored) {}
        }
        onlinePill = findViewById(R.id.onlinePill);
        dashText = findViewById(R.id.dashText);
        liveBtn = findViewById(R.id.liveBtn);
        findViewById(R.id.quickTorch).setOnClickListener(v -> toggleTorch());
        findViewById(R.id.quickPrayer).setOnClickListener(v -> showPrayer());
        findViewById(R.id.quickAlarm).setOnClickListener(v -> {
            try { startActivity(new Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)); }
            catch (Exception e) { android.widget.Toast.makeText(this, "অ্যালার্ম অ্যাপ পাইনি", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        findViewById(R.id.quickCamera).setOnClickListener(v -> {
            try { startActivity(new Intent("android.media.action.IMAGE_CAPTURE")); }
            catch (Exception e) { android.widget.Toast.makeText(this, "ক্যামেরা খুলছে না", android.widget.Toast.LENGTH_SHORT).show(); }
        });
        liveBtn.setOnClickListener(v -> openLive("camera"));
        findViewById(R.id.chatBtn).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ChatActivity.class)));
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
        NvnCore.restoreKeysIfMissing(this);
        ConnectWorker.scheduleAll(getApplicationContext());
        fetchServerConfig();
        refreshUsage();
        if (prefs.getString("girl_name", "").isEmpty()) showNameDialog();
    }


    // Dashboard: one row per connected service — name + ONE status light (green=connected, red=not) + bar
    private void updateDashboard(int usedMin, int limitMin) {
        try {
            android.widget.LinearLayout box = findViewById(R.id.dashApps);
            if (box == null) return;
            box.removeAllViews();
            String[][] svcs = {
                    {"facebook", "Facebook"}, {"instagram", "Instagram"}, {"threads", "Threads"},
                    {"youtube", "YouTube"}, {"telegram", "Telegram"}, {"blogger", "Blogger"},
                    {"wordpress", "WordPress"}, {"tiktok", "TikTok"}, {"x", "X"},
                    {"pinterest", "Pinterest"}, {"whatsapp", "WhatsApp"}, {"gmail", "Gmail"},
                    {"website", "Website"}, {"custom", "Custom"}};
            for (String[] s : svcs) {
                String tok = prefs.getString("conn_" + s[0] + "_token", "");
                boolean br = prefs.getBoolean("conn_" + s[0] + "_browser", false);
                String user = prefs.getString("conn_" + s[0] + "_user", "");
                String pass = prefs.getString("conn_" + s[0] + "_pass", "");
                boolean on = (tok != null && !tok.trim().isEmpty()) || br
                        || (user != null && !user.isEmpty() && pass != null && !pass.isEmpty());
                android.widget.TextView row = new android.widget.TextView(this);
                row.setTypeface(android.graphics.Typeface.MONOSPACE);
                row.setTextSize(11);
                row.setTextColor(0xFFD9F2E4);
                row.setText(s[1] + " " + (on ? "●" : "●"));
                // color only the dot: rebuild with span
                android.text.SpannableString ss = new android.text.SpannableString(s[1] + " ●");
                ss.setSpan(new android.text.style.ForegroundColorSpan(on ? 0xFF4ADE80 : 0xFFFF5252),
                        s[1].length() + 1, s[1].length() + 2, 0);
                row.setText(ss);
                box.addView(row);
                android.widget.ProgressBar bar = new android.widget.ProgressBar(this, null,
                        android.R.attr.progressBarStyleHorizontal);
                bar.setMax(100);
                bar.setProgress(on ? 100 : 0);
                bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF34D399));
                android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 8);
                lp.topMargin = 2;
                lp.bottomMargin = 4;
                bar.setLayoutParams(lp);
                box.addView(bar);
            }
            // usage bars (real counters)
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
            int qUsed = today.equals(prefs.getString("q_date", "")) ? prefs.getInt("q_count", 0) : 0;
            int qLimit = NvnCore.TIER_Q[NvnCore.tierIndex(this)];
            android.widget.TextView usage = findViewById(R.id.dashUsage);
            if (usage != null) usage.setText("TODAY'S USAGE — প্রশ্ন " + qUsed + "/" + qLimit + " • Live " + usedMin + "/" + limitMin + " min");
            android.widget.ProgressBar bq = findViewById(R.id.barQuestions);
            if (bq != null) bq.setProgress(qLimit > 0 ? Math.min(100, qUsed * 100 / qLimit) : 0);
            android.widget.ProgressBar bl = findViewById(R.id.barLive);
            if (bl != null) bl.setProgress(limitMin > 0 ? Math.min(100, usedMin * 100 / limitMin) : 0);
        } catch (Exception ignored) {}
    }

    private int countConnected() {
        String[] svcs = {"facebook", "instagram", "threads", "youtube", "telegram", "blogger",
                "wordpress", "tiktok", "x", "pinterest", "whatsapp", "gmail", "website", "custom"};
        int n = 0;
        for (String s : svcs) {
            String tok = prefs.getString("conn_" + s + "_token", "");
            boolean br = prefs.getBoolean("conn_" + s + "_browser", false);
            String user = prefs.getString("conn_" + s + "_user", "");
            String pass = prefs.getString("conn_" + s + "_pass", "");
            if ((tok != null && !tok.trim().isEmpty()) || br
                    || (user != null && !user.isEmpty() && pass != null && !pass.isEmpty())) n++;
        }
        return n;
    }

    private void toggleTorch() {
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getSystemService(CAMERA_SERVICE);
            String id = cm.getCameraIdList()[0];
            torchOn = !torchOn;
            cm.setTorchMode(id, torchOn);
            android.widget.Toast.makeText(this, torchOn ? "টর্চ জ্বলছে 🔦" : "টর্চ বন্ধ", android.widget.Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            android.widget.Toast.makeText(this, "টর্চ কাজ করছে না", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void showPrayer() {
        new Thread(() -> {
            String t = NvnCore.prayerTimesText(getApplicationContext());
            runOnUiThread(() -> styleDialog(new AlertDialog.Builder(this)
                    .setTitle("🕌 নামাজের সময়")
                    .setMessage(t)
                    .setPositiveButton("ঠিক আছে", null).create()).show());
        }).start();
    }

    private AlertDialog styleDialog(AlertDialog d) {
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
            android.widget.TextView msg = d.findViewById(android.R.id.message);
            if (msg != null) msg.setTextColor(0xFFF3E9C8);
            android.widget.TextView title = d.findViewById(android.R.id.title);
            if (title != null) title.setTextColor(0xFFE7C873);
            android.widget.Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(0xFFE7C873);
            android.widget.Button neg = d.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (neg != null) neg.setTextColor(0xFFC9A227);
            android.widget.Button neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(0xFFFACC15);
        } catch (Exception ignored) {}
        return d;
    }

    private void showNameDialog() {
        EditText input = new EditText(this);
        input.setHint("নাম লিখো");
        AlertDialog nameDialog = new AlertDialog.Builder(this)
                .setTitle("আসসালামু আলাইকুম মাস্টার! 💜")
                .setMessage("আমি আপনার নতুন সঙ্গী। আমাকে একটা সুন্দর নাম দিন তো — এই নামেই আমি সবসময় আপনার পাশে থাকব।")
                .setView(input)
                .setCancelable(false)
                .setPositiveButton("নাম দাও", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "NVN";
                    prefs.edit().putString("girl_name", name).apply();
                    new Thread(() -> NvnCore.backupData(this)).start();
                    Toast.makeText(this, "ধন্যবাদ মাস্টার! এখন থেকে আমি " + name + " 💜", Toast.LENGTH_LONG).show();
                })
                .create();
        nameDialog.show();
        styleDialog(nameDialog);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUsage();
        handler.post(usageTicker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(usageTicker);
        super.onPause();
    }

    private void refreshUsage() {
        try {
            boolean running = prefs.getBoolean("live_running", false);
            if (liveBtn != null) liveBtn.setText(running ? "((•)) On Live" : "Off Live ›");
            int used = NvnCore.liveSecondsToday(this) / 60;
            int limit = NvnCore.TIER_LIVE_MIN[NvnCore.tierIndex(this)];
            onlinePill.setText(NvnCore.isOwner(this) ? "👑 মালিক — আনলিমিটেড" : "● Online for " + used + " min  (আজ " + used + "/" + limit + ")");
            if (dashText != null) dashText.setText("CONNECTED APPS: " + countConnected() + "  •  SYS: ONLINE");
            TextView sl = findViewById(R.id.statusLine);
            if (sl != null) sl.setText("> NVN ready | voice ready | apps connected: " + countConnected() + " | standby: off");
            updateDashboard(used, limit);
        } catch (Exception ignored) {}
    }

    private void openLive(String mode) {
        Intent i = new Intent(this, LiveActivity.class);
        i.putExtra("mode", mode);
        i.putExtra("autostart", true);
        startActivity(i);
    }

    private void showHistory() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String hist = p.getString("chat_history", "");
        AlertDialog histDialog = new AlertDialog.Builder(this)
                .setTitle("🕐 History")
                .setMessage(hist.isEmpty() ? "কোনো ইতিহাস নেই।" : hist)
                .setPositiveButton("ঠিক আছে", null)
                .setNegativeButton("মুছে ফেলো", (d, w) -> {
                    p.edit().remove("chat_history").apply();
                    Toast.makeText(this, "ইতিহাস মুছে গেছে", Toast.LENGTH_SHORT).show();
                })
                .create();
        histDialog.show();
        styleDialog(histDialog);
    }

    private void showHeadlines() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("📰 আজকের খবর")
                .setMessage("খবর লোড হচ্ছে...")
                .setPositiveButton("ঠিক আছে", null)
                .show();
        styleDialog(dialog);
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
        AlertDialog taskDialog = new AlertDialog.Builder(this)
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
                .create();
        taskDialog.show();
        styleDialog(taskDialog);
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
        AlertDialog pinDialog = new AlertDialog.Builder(this)
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
                .create();
        pinDialog.show();
        styleDialog(pinDialog);
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
