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

    public static final String[] SVC_IDS = {"facebook", "instagram", "threads", "youtube", "telegram", "website", "custom"};
    public static final String[] SVC_NAMES = {"📘 Facebook Page", "📸 Instagram", "🧵 Threads", "▶️ YouTube", "✈️ Telegram", "🌐 ওয়েবসাইট", "🔗 কাস্টম সাইট"};

    public static String svcName(String svc) {
        for (int i = 0; i < SVC_IDS.length; i++) if (SVC_IDS[i].equals(svc)) return SVC_NAMES[i];
        return svc;
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
    }

    public static boolean isConnected(Context ctx, String svc) {
        return !prefs(ctx).getString("conn_" + svc + "_token", "").trim().isEmpty();
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
    static String geminiText(Context ctx, String prompt) {
        SharedPreferences p = prefs(ctx);
        for (int attempt = 0; attempt < 2; attempt++) {
            String key = NvnCore.currentKey(p);
            if (key == null || key.trim().isEmpty()) return null;
            try {
                JSONObject part = new JSONObject().put("text", prompt);
                JSONObject content = new JSONObject().put("parts", new JSONArray().put(part));
                JSONObject body = new JSONObject().put("contents", new JSONArray().put(content));
                String res = httpPostJson(
                        "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + key.trim(),
                        body.toString(), null);
                if (res == null) return null;
                if (NvnCore.isQuotaError(res)) {
                    NvnCore.rotateKey(p);
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
        List<String> report = new ArrayList<>();
        for (String svc : SVC_IDS) {
            if (!p.getBoolean("conn_" + svc + "_enabled", false)) continue;
            String token = p.getString("conn_" + svc + "_token", "").trim();
            if (token.isEmpty()) continue;
            try {
                if (p.getBoolean("conn_" + svc + "_comments", true)) {
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
        boolean draftOnly = "instagram".equals(svc) || "youtube".equals(svc);
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
        } catch (Exception ignored) {}
        return false;
    }

    public static boolean sendQueuedItem(Context ctx, JSONObject item) {
        String svc = item.optString("svc", "");
        String kind = item.optString("kind", "");
        String text = item.optString("text", "");
        String target = item.optString("target", "");
        String token = prefs(ctx).getString("conn_" + svc + "_token", "").trim();
        if (token.isEmpty() || text.isEmpty()) return false;
        if ("reply".equals(kind)) return sendReply(ctx, svc, token, target, text);
        return sendPost(ctx, svc, token, text);
    }
}
