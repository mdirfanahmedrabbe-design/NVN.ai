package com.archer.ai;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ConnectWorker {

    public static final String[] SVC_IDS = {"facebook", "instagram", "threads", "youtube", "telegram", "blogger", "wordpress", "tiktok", "x", "pinterest", "whatsapp", "gmail", "website", "custom"};
    public static final String[] SVC_NAMES = {"📘 Facebook Page", "📸 Instagram", "🧵 Threads", "▶️ YouTube", "✈️ Telegram", "📝 Blogger", "📰 WordPress", "🎵 TikTok", "🐦 X (Twitter)", "📌 Pinterest", "💬 WhatsApp", "📧 Gmail", "🌐 ওয়েবসাইট", "🔗 কাস্টম সাইট"};

    public static String svcName(String svc) {
        for (int i = 0; i < SVC_IDS.length; i++) if (SVC_IDS[i].equals(svc)) return SVC_NAMES[i];
        return svc;
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
    }

    public static boolean isConnected(Context ctx, String svc) {
        SharedPreferences p = prefs(ctx);
        if (!p.getString("conn_" + svc + "_token", "").trim().isEmpty()) return true;
        return !p.getString("conn_" + svc + "_user", "").trim().isEmpty()
                && !p.getString("conn_" + svc + "_pass", "").trim().isEmpty();
    }

    // ---------- browser mode (works in the owner's Chrome, already logged in — no token) ----------
    public static boolean isBrowserMode(Context ctx, String svc) {
        return prefs(ctx).getBoolean("conn_" + svc + "_browser", false);
    }

    public static String defaultSiteUrl(String svc) {
        switch (svc) {
            case "facebook": return "https://www.facebook.com";
            case "instagram": return "https://www.instagram.com";
            case "threads": return "https://www.threads.net";
            case "youtube": return "https://www.youtube.com";
            case "telegram": return "https://web.telegram.org";
            case "blogger": return "https://www.blogger.com";
            case "tiktok": return "https://www.tiktok.com";
            case "x": return "https://x.com";
            case "pinterest": return "https://www.pinterest.com";
            case "whatsapp": return "https://web.whatsapp.com";
            case "gmail": return "https://mail.google.com";
            default: return "";
        }
    }

    public static String siteUrl(Context ctx, String svc) {
        String u = prefs(ctx).getString("conn_" + svc + "_site", "").trim();
        if (!u.isEmpty()) return u;
        if ("wordpress".equals(svc) || "website".equals(svc) || "custom".equals(svc)) {
            String id = prefs(ctx).getString("conn_" + svc + "_id", "").trim();
            if (id.startsWith("http")) return id;
        }
        return defaultSiteUrl(svc);
    }

    static String[] composerHints(String svc) {
        switch (svc) {
            case "facebook": return new String[]{"What's on your mind", "কী ভাবছেন", "Create post", "Write something"};
            case "instagram": return new String[]{"New post", "Create", "What's on your mind"};
            case "threads": return new String[]{"Start a thread", "New thread", "What's new"};
            case "x": return new String[]{"What is happening", "What's happening", "Post your reply"};
            case "tiktok": return new String[]{"Upload", "Create", "Post"};
            case "pinterest": return new String[]{"Create Pin", "Create", "Add a title"};
            case "youtube": return new String[]{"Create", "Upload video"};
            case "whatsapp": return new String[]{"Type a message", "মেসেজ লিখুন"};
            case "gmail": return new String[]{"Compose", "লিখুন"};
            case "telegram": return new String[]{"Message", "মেসেজ"};
            default: return new String[]{"Write", "Post", "লিখুন"};
        }
    }

    static String[] postButtonTexts(String svc) {
        switch (svc) {
            case "x": return new String[]{"Post", "Tweet", "পোস্ট"};
            case "threads": return new String[]{"Post", "পোস্ট"};
            case "pinterest": return new String[]{"Publish", "Save", "Post"};
            case "gmail": return new String[]{"Send", "পাঠাও"};
            case "whatsapp": return new String[]{"Send", "পাঠাও"};
            case "telegram": return new String[]{"Send", "পাঠাও"};
            default: return new String[]{"Post", "Share", "Publish", "পোস্ট", "শেয়ার"};
        }
    }

    static void dismissBanners(ArcherAccessibilityService a11y) {
        String[] labels = {"Allow all cookies", "Accept all", "Accept", "Allow", "OK", "ঠিক আছে", "গ্রহণ করুন", "Not now", "পরে", "Close"};
        for (String l : labels) {
            try { a11y.tapText(l); } catch (Exception ignored) {}
        }
        ArcherAccessibilityService.sleep(600);
    }

    // Live work status shown on the browser card inside the chat.
    static void workStatus(Context ctx, String line) {
        try {
            SharedPreferences p = prefs(ctx);
            String old = p.getString("conn_work_status", "");
            String updated = old.isEmpty() ? line : old + "\n" + line;
            String[] lines = updated.split("\n");
            if (lines.length > 12) {
                StringBuilder sb = new StringBuilder();
                for (int i = lines.length - 12; i < lines.length; i++) sb.append(lines[i]).append("\n");
                updated = sb.toString().trim();
            }
            p.edit().putString("conn_work_status", updated).apply();
        } catch (Exception ignored) {}
    }

    static void workShot(Context ctx, ArcherAccessibilityService a11y) {
        try {
            java.io.File f = new java.io.File(ctx.getFilesDir(), "browser_shot.png");
            if (a11y.captureScreenshot(f)) {
                prefs(ctx).edit().putLong("conn_work_shot_ts", System.currentTimeMillis()).apply();
            }
        } catch (Exception ignored) {}
    }

    // Posts by opening the site in the owner's Chrome (already logged in) and tapping/typing
    // through the Accessibility service — the same way the owner works by hand. No token needed.
    // Needs: Accessibility ON, screen on (we wake it), phone unlocked (no PIN stop).
    public static boolean browserPost(Context ctx, String svc, String text) {
        String name = svcName(svc);
        try {
            ArcherAccessibilityService a11y = ArcherAccessibilityService.instance;
            if (a11y == null) {
                logHistory(ctx, name + " — ব্রাউজার মোড: Accessibility বন্ধ আছে, তাই কাজ হয়নি");
                notifyUser(ctx, "NVN ব্রাউজার মোড", name + "-এ কাজ করতে Accessibility চালু লাগবে (Settings → Accessibility → NVN AI Voice Control)", false);
                return false;
            }
            String url = siteUrl(ctx, svc);
            if (url.isEmpty()) {
                logHistory(ctx, name + " — ব্রাউজার মোড: সাইটের ঠিকানা বসানো নেই");
                return false;
            }
            if (!a11y.isScreenOn()) {
                a11y.wakeScreen();
                ArcherAccessibilityService.sleep(2500);
            }
            workStatus(ctx, "🌐 " + name + " — Chrome-এ সাইট খুলছি…");
            if (!a11y.openUrl(url)) {
                logHistory(ctx, name + " — ব্রাউজার মোড: Chrome খোলা যায়নি");
                workStatus(ctx, "❌ Chrome খোলা যায়নি");
                return false;
            }
            ArcherAccessibilityService.sleep(7000);
            workShot(ctx, a11y);
            dismissBanners(a11y);
            // If the phone is locked (PIN screen), the site text never appears.
            boolean opened = false;
            for (String hint : composerHints(svc)) {
                if (a11y.hasText(hint)) { a11y.tapText(hint); opened = true; break; }
            }
            if (!opened) {
                // Some sites open the editor directly; try typing anyway.
                ArcherAccessibilityService.sleep(1500);
            } else {
                ArcherAccessibilityService.sleep(2500);
                dismissBanners(a11y);
            }
            workStatus(ctx, "✍️ পোস্টের লেখা বসাচ্ছি…");
            boolean typed = a11y.typeText(text);
            if (!typed) {
                ArcherAccessibilityService.sleep(1500);
                typed = a11y.typeText(text);
            }
            if (!typed) {
                workStatus(ctx, "❌ লেখার জায়গা পাওয়া যায়নি — ফোন আনলক + Chrome-এ লগইন আছে কিনা দেখো");
                workShot(ctx, a11y);
                logHistory(ctx, name + " — ব্রাউজার মোড: লেখার জায়গা পাওয়া যায়নি (ফোন লক থাকলে বা লগইন না থাকলে এমন হয়)");
                notifyUser(ctx, "NVN ব্রাউজার মোড", name + "-এ লেখা বসানো যায়নি — ফোন আনলক আছে কিনা আর Chrome-এ লগইন আছে কিনা দেখো", false);
                return false;
            }
            ArcherAccessibilityService.sleep(1200);
            for (String btn : postButtonTexts(svc)) {
                if (a11y.hasText(btn)) {
                    a11y.tapText(btn);
                    ArcherAccessibilityService.sleep(3000);
                    workStatus(ctx, "✅ পোস্ট হয়ে গেছে — " + name);
                    workShot(ctx, a11y);
                    logHistory(ctx, name + " — ব্রাউজার মোডে পোস্ট পাঠানো হয়েছে");
                    return true;
                }
            }
            // Typed but no Post button found — leave it typed for the owner to press Post.
            workStatus(ctx, "✍️ লেখা বসানো হয়েছে — Post বাটনে নিজে টিপ দাও");
            workShot(ctx, a11y);
            logHistory(ctx, name + " — ব্রাউজার মোড: লেখা বসানো হয়েছে, Post বাটন পাওয়া যায়নি — নিজে টিপ দাও");
            notifyUser(ctx, "NVN ব্রাউজার মোড", name + "-এ লেখা বসিয়ে দিয়েছি, Post বাটনে নিজে টিপ দাও", false);
            return false;
        } catch (Exception e) {
            logHistory(ctx, name + " — ব্রাউজার মোডে সমস্যা হয়েছে");
            return false;
        }
    }

    // ---------- history ----------
    public static void logHistory(Context ctx, String text) {
        try {
            SharedPreferences p = prefs(ctx);
            String stamp = new SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH).format(new Date());
            String old = p.getString("conn_history", "");
            String updated = stamp + " — " + text + "\n" + old;
            String[] lines = updated.split("\n");
            if (lines.length > 300) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < 300; i++) sb.append(lines[i]).append("\n");
                updated = sb.toString();
            }
            p.edit().putString("conn_history", updated).apply();
        } catch (Exception ignored) {}
    }

    // ---------- approval queue ----------
    public static JSONArray getQueue(Context ctx) {
        try {
            return new JSONArray(prefs(ctx).getString("conn_queue", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    static void saveQueue(Context ctx, JSONArray q) {
        prefs(ctx).edit().putString("conn_queue", q.toString()).apply();
    }

    static void addToQueue(Context ctx, String svc, String kind, String text, String target) {
        try {
            JSONArray q = getQueue(ctx);
            JSONObject o = new JSONObject();
            o.put("svc", svc).put("kind", kind).put("text", text)
                    .put("target", target == null ? "" : target)
                    .put("ts", System.currentTimeMillis());
            q.put(o);
            saveQueue(ctx, q);
        } catch (Exception ignored) {}
    }

    // ---------- notifications ----------
    static void notifyUser(Context ctx, String title, String body, boolean urgent) {
        try {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String chId = urgent ? "nvn_urgent" : "nvn_connect";
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(chId, urgent ? "NVN জরুরি" : "NVN কানেক্ট",
                        urgent ? NotificationManager.IMPORTANCE_HIGH : NotificationManager.IMPORTANCE_DEFAULT);
                nm.createNotificationChannel(ch);
            }
            Intent intent = new Intent(ctx, urgent ? ApprovalActivity.class : ConnectActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, urgent ? 791 : 790, intent,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, chId) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(urgent ? 791 : 790, b.build());
        } catch (Exception ignored) {}
    }

    // ---------- scheduling ----------
    public static void scheduleAll(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, ConnectReceiver.class).setAction("com.archer.ai.CONNECT_RUN");
            PendingIntent pi = PendingIntent.getBroadcast(ctx, 795, i,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT : PendingIntent.FLAG_UPDATE_CURRENT);
            long first = System.currentTimeMillis() + 5 * 60 * 1000L;
            am.setInexactRepeating(AlarmManager.RTC_WAKEUP, first, 3 * 60 * 60 * 1000L, pi);
        } catch (Exception ignored) {}
    }

    public static void runInBackground(final Context ctx, final boolean manual) {
        new Thread(() -> {
            PowerManager.WakeLock wl = null;
            try {
                PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "nvn:connect");
                    wl.acquire(10 * 60 * 1000L);
                }
            } catch (Exception ignored) {}
            try {
                runAll(ctx, manual);
            } catch (Exception ignored) {} finally {
                try { if (wl != null && wl.isHeld()) wl.release(); } catch (Exception ignored) {}
            }
        }).start();
    }

    // ---------- Gemini text ----------
    // Flash-Lite free tier allows ~500 requests/day per project (vs ~20 for Flash),
    // so background Connect work uses Lite; falls back to Flash if Lite ever errors.
    static String geminiText(Context ctx, String prompt) {
        SharedPreferences p = prefs(ctx);
        String model = "gemini-3.5-flash-lite";
        for (int attempt = 0; attempt < 3; attempt++) {
            String key = NvnCore.currentKey(p);
            if (key == null || key.trim().isEmpty()) return null;
            try {
                JSONObject part = new JSONObject().put("text", prompt);
                JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
                JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
                String res = httpPostJson(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key.trim(),
                        body.toString(), null);
                if (res == null) return null;
                if (NvnCore.isQuotaError(res)) {
                    NvnCore.rotateKey(p);
                    continue;
                }
                if (res.contains("\"error\"") && model.equals("gemini-3.5-flash-lite")) {
                    model = "gemini-3.5-flash";
                    continue;
                }
                JSONObject o = new JSONObject(res);
                JSONArray cands = o.optJSONArray("candidates");
                if (cands == null || cands.length() == 0) return null;
                JSONArray parts = cands.getJSONObject(0).optJSONObject("content").optJSONArray("parts");
                if (parts == null || parts.length() == 0) return null;
                String text = parts.getJSONObject(0).optString("text", "").trim();
                return text.isEmpty() ? null : text;
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    // ---------- HTTP helpers ----------
    static String httpGet(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
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

    static String httpPostJson(String urlStr, String json, String bearer) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "application/json");
            if (bearer != null && !bearer.isEmpty()) conn.setRequestProperty("Authorization", "Bearer " + bearer);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            java.io.InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String httpPostJsonAuth(String urlStr, String json, String authHeader) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "application/json");
            if (authHeader != null && !authHeader.isEmpty()) conn.setRequestProperty("Authorization", authHeader);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            java.io.InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String httpPostForm(String urlStr, String form) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(25000);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(form.getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            java.io.InputStream is = conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream();
            if (is == null) return null;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String enc(String s) {
        try { return URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    // Accepts a bare numeric ID or a pasted facebook.com URL and returns the numeric ID.
    static String extractId(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        int i = s.indexOf("id=");
        if (i >= 0) s = s.substring(i + 3);
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < s.length(); j++) {
            char c = s.charAt(j);
            if (Character.isDigit(c)) sb.append(c);
            else if (sb.length() > 0) break;
        }
        return sb.toString();
    }

    // ---------- main run ----------
    public static void runAll(Context ctx, boolean manual) {
        SharedPreferences p = prefs(ctx);
        dailyBrief(ctx, p);
        List<String> report = new ArrayList<>();
        for (String svc : SVC_IDS) {
            if (!p.getBoolean("conn_" + svc + "_enabled", false)) continue;
            String token = p.getString("conn_" + svc + "_token", "").trim();
            boolean connected = isConnected(ctx, svc);
            boolean browser = isBrowserMode(ctx, svc);
            if (!connected && !browser) continue;
            try {
                if (connected && p.getBoolean("conn_" + svc + "_comments", true)) {
                    int n = handleComments(ctx, svc, token);
                    if (n > 0) report.add(svcName(svc) + ": " + n + "টি কমেন্টের কাজ হয়েছে");
                }
                if (p.getBoolean("conn_" + svc + "_post", false)) {
                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
                    boolean doneToday = today.equals(p.getString("conn_" + svc + "_lastpost", ""));
                    int hour = p.getInt("conn_" + svc + "_hour", 9);
                    int minute = p.getInt("conn_" + svc + "_minute", 0);
                    Calendar now = Calendar.getInstance();
                    boolean timeReached = now.get(Calendar.HOUR_OF_DAY) > hour
                            || (now.get(Calendar.HOUR_OF_DAY) == hour && now.get(Calendar.MINUTE) >= minute);
                    if (!doneToday && (manual || timeReached)) {
                        String r = makeDailyPost(ctx, svc, token);
                        if (r != null) {
                            p.edit().putString("conn_" + svc + "_lastpost", today).apply();
                            report.add(svcName(svc) + ": " + r);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        if (!report.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String line : report) sb.append("• ").append(line).append("\n");
            logHistory(ctx, "রান শেষ — " + String.join("; ", report));
            notifyUser(ctx, "NVN কানেক্ট রিপোর্ট", sb.toString().trim(), false);
        } else if (manual) {
            logHistory(ctx, "রান শেষ — নতুন কিছু নেই");
        }
    }

    // Once a day: morning brief — prayer times + connected apps (works while he's away)
    static void dailyBrief(Context ctx, SharedPreferences p) {
        try {
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
            if (today.equals(p.getString("last_brief_date", ""))) return;
            p.edit().putString("last_brief_date", today).apply();
            int connected = 0;
            for (String svc : SVC_IDS) if (isConnected(ctx, svc) || isBrowserMode(ctx, svc)) connected++;
            String prayer = NvnCore.prayerTimesText(ctx);
            String firstLine = prayer.contains("\n") ? prayer.substring(prayer.indexOf('\n') + 1) : prayer;
            String body = "আসসালামু আলাইকুম মাস্টার 💜\n" + firstLine
                    + "\n🔗 কানেক্টেড অ্যাপ: " + connected + "টি — আজকের কাজ নিজে নিজে চলবে।";
            notifyUser(ctx, "🌅 আজকের দিন", body, false);
            logHistory(ctx, "সকালের ব্রিফ পাঠানো হয়েছে");
        } catch (Exception ignored) {}
    }

    // ---------- comments ----------
    static int handleComments(Context ctx, String svc, String token) {
        if ("facebook".equals(svc)) return facebookComments(ctx, token);
        if ("telegram".equals(svc)) return telegramComments(ctx, token);
        return 0;
    }

    static String[] classifyAndReply(Context ctx, String svc, String rules, String comment) {
        String prompt = "You are NVN, a polite assistant working for the owner on " + svcName(svc) + ".\n"
                + "Owner's rules for this site:\n" + (rules == null || rules.trim().isEmpty() ? "(no special rules)" : rules) + "\n\n"
                + "A comment arrived: \"" + comment + "\"\n"
                + "First classify it as exactly one of: QUESTION, COMPLAINT, SPAM, NORMAL.\n"
                + "Then write a short polite reply in the SAME language as the comment.\n"
                + "Hard rules: never share any phone number, address, password, OTP or personal info; "
                + "never promise prices, discounts, income or guarantees; never argue or be rude; "
                + "if COMPLAINT, apologise politely and say the owner will look into it; "
                + "if SPAM, reply with empty text.\n"
                + "Answer in exactly this format:\nTYPE: <type>\nREPLY: <reply text>";
        String out = geminiText(ctx, prompt);
        if (out == null) return null;
        String type = "NORMAL";
        String reply = "";
        for (String line : out.split("\n")) {
            String t = line.trim();
            if (t.toUpperCase(Locale.ROOT).startsWith("TYPE:")) type = t.substring(5).trim().toUpperCase(Locale.ROOT);
            else if (t.toUpperCase(Locale.ROOT).startsWith("REPLY:")) reply = t.substring(6).trim();
        }
        if (reply.isEmpty() && !out.contains("REPLY:")) reply = out.trim();
        return new String[]{type, reply};
    }

    static void processComment(Context ctx, String svc, String token, String commentId, String commentText) {
        SharedPreferences p = prefs(ctx);
        String rules = p.getString("conn_" + svc + "_rules", "");
        String[] cr = classifyAndReply(ctx, svc, rules, commentText);
        if (cr == null) return;
        String type = cr[0];
        String reply = cr[1];
        if ("COMPLAINT".equals(type)) {
            notifyUser(ctx, "⚠️ জরুরি: " + svcName(svc) + "-এ অভিযোগ", "কমেন্ট: " + commentText, true);
            logHistory(ctx, svcName(svc) + " — অভিযোগ এসেছে, তোমাকে জানানো হয়েছে");
        }
        if ("SPAM".equals(type) || reply.isEmpty()) {
            logHistory(ctx, svcName(svc) + " — স্প্যাম কমেন্ট এড়ানো হয়েছে");
            return;
        }
        if (p.getBoolean("conn_" + svc + "_approve", true)) {
            addToQueue(ctx, svc, "reply", reply, commentId);
            logHistory(ctx, svcName(svc) + " — উত্তরের ড্রাফট অনুমোদনের অপেক্ষায়");
        } else {
            boolean ok = sendReply(ctx, svc, token, commentId, reply);
            logHistory(ctx, svcName(svc) + (ok ? " — কমেন্টের উত্তর পাঠানো হয়েছে" : " — উত্তর পাঠানো যায়নি"));
        }
    }

    static int facebookComments(Context ctx, String token) {
        SharedPreferences p = prefs(ctx);
        String pageId = extractId(p.getString("conn_facebook_id", ""));
        if (pageId.isEmpty()) return 0;
        String url = "https://graph.facebook.com/v19.0/" + pageId
                + "/feed?fields=id,message,comments.limit(20){id,message,from,created_time}&limit=5&access_token=" + enc(token);
        String res = httpGet(url);
        if (res == null) return 0;
        int count = 0;
        try {
            JSONObject o = new JSONObject(res);
            JSONArray posts = o.optJSONArray("data");
            if (posts == null) return 0;
            String seen = p.getString("conn_facebook_seen", "");
            List<String> seenList = new ArrayList<>();
            for (String s : seen.split(",")) if (!s.isEmpty()) seenList.add(s);
            for (int i = 0; i < posts.length(); i++) {
                JSONObject post = posts.getJSONObject(i);
                JSONObject comments = post.optJSONObject("comments");
                if (comments == null) continue;
                JSONArray data = comments.optJSONArray("data");
                if (data == null) continue;
                for (int j = 0; j < data.length(); j++) {
                    JSONObject c = data.getJSONObject(j);
                    String cid = c.optString("id", "");
                    String msg = c.optString("message", "");
                    if (cid.isEmpty() || msg.isEmpty() || seenList.contains(cid)) continue;
                    seenList.add(cid);
                    processComment(ctx, "facebook", token, cid, msg);
                    count++;
                }
            }
            while (seenList.size() > 200) seenList.remove(0);
            p.edit().putString("conn_facebook_seen", String.join(",", seenList)).apply();
        } catch (Exception ignored) {}
        return count;
    }

    static int telegramComments(Context ctx, String token) {
        SharedPreferences p = prefs(ctx);
        long offset = p.getLong("conn_telegram_offset", 0);
        String url = "https://api.telegram.org/bot" + token + "/getUpdates?offset=" + offset + "&timeout=0";
        String res = httpGet(url);
        if (res == null) return 0;
        int count = 0;
        try {
            JSONObject o = new JSONObject(res);
            JSONArray result = o.optJSONArray("result");
            if (result == null) return 0;
            long lastId = offset;
            for (int i = 0; i < result.length(); i++) {
                JSONObject u = result.getJSONObject(i);
                long uid = u.optLong("update_id", 0);
                if (uid >= lastId) lastId = uid + 1;
                JSONObject msg = u.optJSONObject("message");
                if (msg == null) continue;
                String text = msg.optString("text", "");
                long mid = msg.optLong("message_id", 0);
                JSONObject chat = msg.optJSONObject("chat");
                long chatId = chat != null ? chat.optLong("id", 0) : 0;
                if (text.isEmpty() || mid == 0 || chatId == 0) continue;
                String target = chatId + ":" + mid;
                processComment(ctx, "telegram", token, target, text);
                count++;
            }
            p.edit().putLong("conn_telegram_offset", lastId).apply();
        } catch (Exception ignored) {}
        return count;
    }

    // ---------- daily post ----------
    static String makeDailyPost(Context ctx, String svc, String token) {
        SharedPreferences p = prefs(ctx);
        String rules = p.getString("conn_" + svc + "_rules", "");
        String prompt = "You are NVN, working for the owner on " + svcName(svc) + ".\n"
                + "Owner's rules for this site:\n" + (rules == null || rules.trim().isEmpty() ? "(no special rules — write a helpful, friendly post)" : rules) + "\n\n"
                + "Write ONE post for today following the rules exactly. "
                + "Language: Bengali unless the rules say otherwise. "
                + "Hard rules: no false claims, no guaranteed income or results, no prices unless the rules include them, "
                + "never share any phone number or personal info. Output only the post text, nothing else.";
        String post = geminiText(ctx, prompt);
        if (post == null || post.isEmpty()) return null;
        // Browser mode without a token: post through the owner's Chrome instead of the API.
        if (!isConnected(ctx, svc) && isBrowserMode(ctx, svc)) {
            if (p.getBoolean("conn_" + svc + "_approve", true)) {
                addToQueue(ctx, svc, "post", post, "");
                logHistory(ctx, svcName(svc) + " — ব্রাউজার মোডের পোস্টের ড্রাফট অনুমোদনের অপেক্ষায়");
                return "পোস্টের ড্রাফট তৈরি (অনুমোদনের অপেক্ষায়)";
            }
            boolean okB = browserPost(ctx, svc, post);
            return okB ? "ব্রাউজার মোডে আজকের পোস্ট পাঠানো হয়েছে" : null;
        }
        boolean draftOnly = "instagram".equals(svc) || "youtube".equals(svc)
                || "tiktok".equals(svc) || "x".equals(svc) || "pinterest".equals(svc)
                || "whatsapp".equals(svc) || "gmail".equals(svc);
        if (draftOnly || p.getBoolean("conn_" + svc + "_approve", true)) {
            addToQueue(ctx, svc, "post", post, "");
            logHistory(ctx, svcName(svc) + " — আজকের পোস্টের ড্রাফট অনুমোদনের অপেক্ষায়");
            return "পোস্টের ড্রাফট তৈরি (অনুমোদনের অপেক্ষায়)";
        }
        boolean ok = sendPost(ctx, svc, token, post);
        logHistory(ctx, svcName(svc) + (ok ? " — আজকের পোস্ট পাঠানো হয়েছে" : " — পোস্ট পাঠানো যায়নি"));
        return ok ? "আজকের পোস্ট পাঠানো হয়েছে" : null;
    }

    // ---------- sending ----------
    public static boolean sendReply(Context ctx, String svc, String token, String target, String text) {
        try {
            if ("facebook".equals(svc)) {
                String res = httpPostForm("https://graph.facebook.com/v19.0/" + target + "/comments",
                        "message=" + enc(text) + "&access_token=" + enc(token));
                return res != null && res.contains("\"id\"");
            }
            if ("telegram".equals(svc)) {
                String[] parts = target.split(":");
                if (parts.length != 2) return false;
                JSONObject o = new JSONObject().put("chat_id", parts[0]).put("text", text)
                        .put("reply_to_message_id", Long.parseLong(parts[1]));
                String res = httpPostJson("https://api.telegram.org/bot" + token + "/sendMessage", o.toString(), null);
                return res != null && res.contains("\"ok\":true");
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean sendPost(Context ctx, String svc, String token, String text) {
        SharedPreferences p = prefs(ctx);
        try {
            if ("facebook".equals(svc)) {
                String pageId = extractId(p.getString("conn_facebook_id", ""));
                if (pageId.isEmpty()) return false;
                String res = httpPostForm("https://graph.facebook.com/v19.0/" + pageId + "/feed",
                        "message=" + enc(text) + "&access_token=" + enc(token));
                return res != null && res.contains("\"id\"");
            }
            if ("telegram".equals(svc)) {
                String chatId = p.getString("conn_telegram_id", "").trim();
                if (chatId.isEmpty()) return false;
                JSONObject o = new JSONObject().put("chat_id", chatId).put("text", text);
                String res = httpPostJson("https://api.telegram.org/bot" + token + "/sendMessage", o.toString(), null);
                return res != null && res.contains("\"ok\":true");
            }
            if ("threads".equals(svc)) {
                String userId = p.getString("conn_threads_id", "").trim();
                if (userId.isEmpty()) return false;
                String create = httpPostForm("https://graph.threads.net/v1.0/" + userId + "/threads",
                        "media_type=TEXT&text=" + enc(text) + "&access_token=" + enc(token));
                if (create == null) return false;
                String creationId = new JSONObject(create).optString("id", "");
                if (creationId.isEmpty()) return false;
                String pub = httpPostForm("https://graph.threads.net/v1.0/" + userId + "/threads_publish",
                        "creation_id=" + enc(creationId) + "&access_token=" + enc(token));
                return pub != null && pub.contains("\"id\"");
            }
            if ("website".equals(svc) || "custom".equals(svc)) {
                String url = p.getString("conn_" + svc + "_id", "").trim();
                if (url.isEmpty()) return false;
                JSONObject o = new JSONObject().put("text", text).put("source", "NVN");
                String res = httpPostJson(url, o.toString(), token);
                return res != null;
            }
            if ("blogger".equals(svc)) {
                String blogId = extractId(p.getString("conn_blogger_id", ""));
                if (blogId.isEmpty()) return false;
                String title = text.length() > 60 ? text.substring(0, 60) : text;
                JSONObject o = new JSONObject().put("kind", "blogger#post")
                        .put("title", title).put("content", text);
                String res = httpPostJson("https://www.googleapis.com/blogger/v3/blogs/" + blogId + "/posts",
                        o.toString(), token);
                return res != null && res.contains("\"id\"");
            }
            if ("wordpress".equals(svc)) {
                String site = p.getString("conn_wordpress_id", "").trim();
                String user = p.getString("conn_wordpress_user", "").trim();
                String pass = p.getString("conn_wordpress_pass", "").trim();
                if (pass.isEmpty()) pass = token;
                if (site.isEmpty() || user.isEmpty() || pass.isEmpty()) return false;
                if (site.endsWith("/")) site = site.substring(0, site.length() - 1);
                String title = text.length() > 60 ? text.substring(0, 60) : text;
                JSONObject o = new JSONObject().put("title", title)
                        .put("content", text).put("status", "publish");
                String basic = "Basic " + android.util.Base64.encodeToString(
                        (user + ":" + pass).getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
                String res = httpPostJsonAuth(site + "/wp-json/wp/v2/posts", o.toString(), basic);
                return res != null && res.contains("\"id\"");
            }
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean sendQueuedItem(Context ctx, JSONObject item) {
        String svc = item.optString("svc", "");
        String kind = item.optString("kind", "");
        String text = item.optString("text", "");
        String target = item.optString("target", "");
        String token = prefs(ctx).getString("conn_" + svc + "_token", "").trim();
        if (text.isEmpty()) return false;
        if (token.isEmpty() && !"wordpress".equals(svc)) {
            // Approved draft for a browser-mode site: post it through Chrome.
            if ("post".equals(kind) && isBrowserMode(ctx, svc)) return browserPost(ctx, svc, text);
            return false;
        }
        if ("reply".equals(kind)) return sendReply(ctx, svc, token, target, text);
        return sendPost(ctx, svc, token, text);
    }
}
