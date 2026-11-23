package com.archer.ai;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.telephony.SmsManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NvnCore {
    public static final String SERVER_BASE = "https://hafijurislam777.pythonanywhere.com/nvn";
    private static final byte[] XOR_KEY = "NVN2026".getBytes(StandardCharsets.UTF_8);

    public static final String[] TIER_IDS = {"free", "m1", "m3", "m6", "m9", "m12", "life"};
    public static final String[] TIER_NAMES = {"ফ্রি", "১ মাস", "৩ মাস", "৬ মাস", "৯ মাস", "১২ মাস", "লাইফটাইম"};
    public static final int[] TIER_PRICE = {0, 199, 499, 899, 1299, 1599, 2999};
    public static final int[] TIER_DAYS = {0, 30, 90, 180, 270, 365, 0};
    public static final int[] TIER_Q = {20, 150, 300, 500, 700, 1000, 1500};
    public static final int[] TIER_LIVE_MIN = {5, 30, 60, 120, 180, 240, 360};

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
    }

    public static boolean isOwner(Context ctx) {
        return prefs(ctx).getBoolean("owner_mode", false);
    }

    public static int tierIndex(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String tier = p.getString("premium_tier", "free");
        long expiry = p.getLong("premium_expiry", 0);
        int idx = 0;
        for (int i = 0; i < TIER_IDS.length; i++) if (TIER_IDS[i].equals(tier)) idx = i;
        if (idx > 0 && !"life".equals(tier) && expiry > 0 && System.currentTimeMillis() > expiry) return 0;
        return idx;
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    public static int questionsUsedToday(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!today().equals(p.getString("q_date", ""))) return 0;
        return p.getInt("q_count", 0);
    }

    public static boolean tryCountQuestion(Context ctx) {
        if (isOwner(ctx)) return true;
        SharedPreferences p = prefs(ctx);
        String t = today();
        int count = t.equals(p.getString("q_date", "")) ? p.getInt("q_count", 0) : 0;
        if (count >= TIER_Q[tierIndex(ctx)]) return false;
        p.edit().putString("q_date", t).putInt("q_count", count + 1)
                .putInt("q_hist_" + t, p.getInt("q_hist_" + t, 0) + 1).apply();
        return true;
    }

    public static int liveSecondsToday(Context ctx) {
        SharedPreferences p = prefs(ctx);
        if (!today().equals(p.getString("live_date", ""))) return 0;
        return p.getInt("live_seconds", 0);
    }

    public static boolean canUseLive(Context ctx) {
        if (isOwner(ctx)) return true;
        return liveSecondsToday(ctx) < TIER_LIVE_MIN[tierIndex(ctx)] * 60;
    }

    public static void addLiveSeconds(Context ctx, int sec) {
        SharedPreferences p = prefs(ctx);
        String t = today();
        int cur = t.equals(p.getString("live_date", "")) ? p.getInt("live_seconds", 0) : 0;
        p.edit().putString("live_date", t).putInt("live_seconds", cur + sec).apply();
    }

    public static List<String> getKeys(SharedPreferences p) {
        List<String> out = new ArrayList<>();
        String multi = p.getString("gemini_keys", "");
        if (multi != null) {
            for (String k : multi.split("\n")) {
                String t = k.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        if (out.isEmpty()) {
            String single = p.getString("gemini_key", "").trim();
            if (!single.isEmpty()) out.add(single);
        }
        return out;
    }

    public static String currentKey(SharedPreferences p) {
        List<String> keys = getKeys(p);
        if (keys.isEmpty()) return "";
        int idx = p.getInt("key_index", 0);
        return keys.get(Math.abs(idx) % keys.size());
    }

    public static void rotateKey(SharedPreferences p) {
        try {
            String cur = currentKey(p);
            if (cur != null && !cur.isEmpty()) {
                String h = sha256Hex(cur);
                String stored = p.getString("exhausted_keys", "");
                String todayStr = today();
                java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
                if (stored.startsWith(todayStr + "|")) {
                    for (String x : stored.substring(todayStr.length() + 1).split(",")) if (!x.isEmpty()) set.add(x);
                }
                set.add(h);
                p.edit().putString("exhausted_keys", todayStr + "|" + String.join(",", set)).apply();
            }
        } catch (Exception ignored) {}
        p.edit().putInt("key_index", p.getInt("key_index", 0) + 1).apply();
    }

    public static boolean isKeyExhausted(Context ctx, String key) {
        try {
            if (key == null || key.trim().isEmpty()) return false;
            String stored = prefs(ctx).getString("exhausted_keys", "");
            String todayStr = today();
            if (!stored.startsWith(todayStr + "|")) return false;
            String h = sha256Hex(key.trim());
            for (String x : stored.substring(todayStr.length() + 1).split(",")) if (x.equals(h)) return true;
        } catch (Exception ignored) {}
        return false;
    }

    public static void notifyLimit(Context ctx) {
        try {
            if (isOwner(ctx)) return;
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "nvn_announce";
            if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(new android.app.NotificationChannel(channelId, "NVN", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            }
            android.app.Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(ctx, channelId)
                    : new android.app.Notification.Builder(ctx);
            b.setContentTitle("NVN AI").setContentText("আজকের Live লিমিট শেষ — প্রিমিয়াম নিলে বেশি পাবেন")
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);
            nm.notify(778, b.build());
        } catch (Exception ignored) {}
    }

    public static boolean isQuotaError(String s) {
        if (s == null) return false;
        String l = s.toLowerCase(Locale.ROOT);
        return l.contains("429") || l.contains("resource_exhausted") || l.contains("quota") || l.contains("rate limit");
    }

    public static String xorDecode(String b64) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            byte[] out = new byte[data.length];
            for (int i = 0; i < data.length; i++) out[i] = (byte) (data[i] ^ XOR_KEY[i % XOR_KEY.length]);
            return new String(out, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    public static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static JSONObject fnDef(String name, String desc, String param, String paramDesc) throws Exception {
        JSONObject p = new JSONObject().put("type", "STRING").put("description", paramDesc);
        JSONObject params = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject().put(param, p))
                .put("required", new JSONArray().put(param));
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    public static JSONObject fnDef2(String name, String desc, String p1, String p1Desc, String p2, String p2Desc) throws Exception {
        JSONObject props = new JSONObject()
                .put(p1, new JSONObject().put("type", "STRING").put("description", p1Desc))
                .put(p2, new JSONObject().put("type", "STRING").put("description", p2Desc));
        JSONObject params = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", new JSONArray().put(p1).put(p2));
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    public static JSONObject fnDefNoArg(String name, String desc) throws Exception {
        JSONObject params = new JSONObject().put("type", "OBJECT").put("properties", new JSONObject());
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    public static JSONArray fnDeclarations() throws Exception {
        JSONArray fns = new JSONArray();
        fns.put(fnDef("open_app", "Open an app on the user's phone by name", "app_name", "Name of the app to open, e.g. Facebook, YouTube, WhatsApp, Telegram, Chrome, bKash"));
        fns.put(fnDef("call_number", "Call a phone number", "number", "Phone number to call, digits only"));
        fns.put(fnDef("call_contact", "Call a contact by name from the phone contacts", "name", "Contact name as saved in the phone"));
        fns.put(fnDef("find_contact", "Find a contact's phone number by name", "name", "Contact name as saved in the phone"));
        fns.put(fnDef2("call_and_talk", "Call a phone number and speak a message aloud on speaker for the user", "number", "Phone number, digits only", "message", "What to say on the call, in Bengali"));
        fns.put(fnDef2("send_sms", "Send an SMS text message to a phone number", "number", "Phone number, digits only", "text", "Message text to send"));
        fns.put(fnDef2("send_whatsapp", "Send a WhatsApp message to a phone number", "number", "Phone number with country code digits, e.g. 8801XXXXXXXXX", "text", "Message text to send"));
        fns.put(fnDef("tap_text", "Tap a button or item on the screen by its visible text", "text", "Exact visible text of the button or item"));
        fns.put(fnDef("type_text", "Type text into the currently focused text field on screen", "text", "Text to type"));
        fns.put(fnDef("set_volume", "Change media volume", "direction", "One of: up, down, max, min"));
        fns.put(fnDef("torch", "Turn the phone flashlight on or off", "state", "One of: on, off"));
        fns.put(fnDefNoArg("press_back", "Press the Back button"));
        fns.put(fnDefNoArg("press_home", "Go to the home screen"));
        fns.put(fnDefNoArg("scroll_down", "Scroll the screen down"));
        fns.put(fnDefNoArg("scroll_up", "Scroll the screen up"));
        fns.put(fnDefNoArg("press_enter", "Press Enter or Send or Search on the keyboard"));
        fns.put(fnDefNoArg("read_screen", "Read all visible text on the current screen"));
        fns.put(fnDefNoArg("take_screenshot", "Take a screenshot"));
        fns.put(fnDef("open_screen", "Open a screen inside the NVN app: qibla compass, tasbih counter, premium packages, settings, connect system, or chat", "screen", "One of: qibla, tasbih, premium, settings, connect, chat"));
        fns.put(fnDefNoArg("end_call", "End the current phone call"));
        fns.put(fnDef("remember_fact", "Remember a fact about the user forever (name, likes, work, family, anything he says to remember)", "fact", "The fact to remember, in the user's own words"));
        fns.put(fnDef2("set_reminder", "Set a reminder that will notify the user later, even if the app is closed. Use when he says remind me / মনে করিয়ে দাও.", "text", "What to remind him about, in his own words", "minutes", "After how many minutes, digits only, e.g. 30"));
        fns.put(fnDef2("set_service_rules", "Save the owner's work rules for a connected site so you remember them forever. Use this whenever the user explains what work to do on a site (what to post, how to reply to comments, when).", "service", "Site name: facebook, instagram, threads, youtube, telegram, blogger, wordpress, tiktok, x, pinterest, whatsapp, gmail, website, custom", "rules", "The work rules in the user's own words, complete"));
        fns.put(fnDef2("set_service_schedule", "Set the daily post time for a connected site", "service", "Site name", "time", "Time in 24-hour HH:MM format, e.g. 09:00"));
        fns.put(fnDef2("toggle_service", "Turn daily work on or off for a connected site", "service", "Site name", "state", "One of: on, off"));
        fns.put(fnDef2("set_browser_mode", "Turn Browser Mode on or off for a site. Browser Mode works without any token: NVN opens the site in the owner's Chrome browser (already logged in) and posts by tapping and typing, the way the owner works by hand.", "service", "Site name", "state", "One of: on, off"));
        fns.put(fnDef("open_browser", "Open NVN built-in browser so the user can log in to a website inside the app, or to show a website", "url", "Website URL to open, e.g. https://www.facebook.com (can be empty)"));
        fns.put(fnDefNoArg("connect_status", "Report which sites are connected, which have daily work on, and what rules are saved"));
        fns.put(fnDefNoArg("run_connect_work", "Run the connected-sites work right now (check comments, reply, post)"));
        fns.put(fnDef("connect_site", "Connect a site: opens the secure connect box in this chat (login/token/password) so the owner can connect it without leaving the chat", "service", "Site name"));
        return fns;
    }

    public static String execute(Context ctx, String name, JSONObject args) {
        ArcherAccessibilityService svc = ArcherAccessibilityService.instance;
        switch (name) {
            case "open_app": {
                String appName = args.optString("app_name", "");
                return openAppByName(ctx, appName) ? "opened " + appName : "app not found: " + appName;
            }
            case "call_number":
                return callNumber(ctx, args.optString("number", ""));
            case "call_contact": {
                String num = findContactNumber(ctx, args.optString("name", ""));
                return num != null ? callNumber(ctx, num) : "contact not found";
            }
            case "find_contact": {
                String num = findContactNumber(ctx, args.optString("name", ""));
                return num != null ? "number: " + num : "contact not found";
            }
            case "call_and_talk":
                return callAndTalk(ctx, args.optString("number", ""), args.optString("message", ""));
            case "send_sms":
                return sendSms(ctx, args.optString("number", ""), args.optString("text", ""));
            case "send_whatsapp":
                return sendWhatsApp(ctx, args.optString("number", ""), args.optString("text", ""));
            case "press_back":
                return svc != null && svc.goBack() ? "back pressed" : "accessibility off";
            case "press_home":
                return svc != null && svc.goHome() ? "home pressed" : "accessibility off";
            case "scroll_down":
                return svc != null && svc.scrollDown() ? "scrolled down" : "accessibility off";
            case "scroll_up":
                return svc != null && svc.scrollUp() ? "scrolled up" : "accessibility off";
            case "tap_text": {
                String t = args.optString("text", "");
                return svc != null && svc.tapText(t) ? "tapped " + t : "not found: " + t;
            }
            case "type_text": {
                String t = args.optString("text", "");
                return svc != null && svc.typeText(t) ? "typed" : "no text field";
            }
            case "press_enter":
                return svc != null && svc.pressEnter() ? "enter pressed" : "failed";
            case "read_screen":
                return svc != null ? svc.readScreen() : "accessibility off";
            case "take_screenshot":
                return svc != null && svc.takeScreenshot() ? "screenshot taken" : "failed";
            case "set_volume":
                return setVolume(ctx, args.optString("direction", ""));
            case "torch":
                return setTorch(ctx, "on".equalsIgnoreCase(args.optString("state", "")));
            case "open_screen": {
                String screen = args.optString("screen", "").toLowerCase(Locale.ROOT);
                Class<?> target = null;
                if (screen.contains("qibla")) target = QiblaActivity.class;
                else if (screen.contains("tasbih")) target = TasbihActivity.class;
                else if (screen.contains("premium")) target = PremiumActivity.class;
                else if (screen.contains("settings")) target = SettingsActivity.class;
                else if (screen.contains("connect")) target = ConnectActivity.class;
                else if (screen.contains("chat")) target = ChatActivity.class;
                if (target == null) return "unknown screen";
                try {
                    Intent i = new Intent(ctx, target);
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    ctx.startActivity(i);
                    return "opened " + screen;
                } catch (Exception e) {
                    return "could not open " + screen;
                }
            }
            case "end_call": {
                try {
                    android.telecom.TelecomManager tm = (android.telecom.TelecomManager) ctx.getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && Build.VERSION.SDK_INT >= 28) {
                        return tm.endCall() ? "call ended" : "no call to end";
                    }
                    return "end call not supported";
                } catch (Exception e) {
                    return "end call failed";
                }
            }
            case "remember_fact": {
                String fact = args.optString("fact", "").trim();
                if (fact.isEmpty()) return "no fact given";
                SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
                String old = p.getString("memory_facts", "");
                String merged = old == null || old.trim().isEmpty() ? fact : old.trim() + "; " + fact;
                p.edit().putString("memory_facts", merged).apply();
                backupData(ctx);
                return "remembered forever: " + fact;
            }
            case "set_reminder": {
                String text = args.optString("text", "").trim();
                int mins = 0;
                try { mins = Integer.parseInt(args.optString("minutes", "0").replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                if (text.isEmpty()) return "no reminder text given";
                if (mins <= 0) mins = 30;
                return setReminder(ctx, text, mins) ? "reminder set in " + mins + " minutes: " + text : "reminder failed";
            }
            case "set_service_rules": {
                String svcId = normalizeService(args.optString("service", ""));
                String rules = args.optString("rules", "").trim();
                if (svcId == null) return "unknown service: " + args.optString("service", "");
                if (rules.isEmpty()) return "no rules given";
                SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
                String old = p.getString("conn_" + svcId + "_rules", "");
                String merged = old == null || old.trim().isEmpty() ? rules : old.trim() + "\n" + rules;
                p.edit().putString("conn_" + svcId + "_rules", merged)
                        .putBoolean("conn_" + svcId + "_enabled", true).apply();
                ConnectWorker.logHistory(ctx, ConnectWorker.svcName(svcId) + " — চ্যাটে নিয়ম সেভ হয়েছে");
                return "rules saved for " + svcId + " and remembered forever";
            }
            case "set_service_schedule": {
                String svcId = normalizeService(args.optString("service", ""));
                String time = args.optString("time", "").trim();
                if (svcId == null) return "unknown service";
                try {
                    String[] parts = time.split(":");
                    int h = Integer.parseInt(parts[0].trim());
                    int m = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
                    if (h < 0 || h > 23 || m < 0 || m > 59) return "bad time";
                    ctx.getSharedPreferences("archer", Context.MODE_PRIVATE).edit()
                            .putInt("conn_" + svcId + "_hour", h).putInt("conn_" + svcId + "_minute", m).apply();
                    return "schedule set for " + svcId + " at " + time;
                } catch (Exception e) {
                    return "bad time format";
                }
            }
            case "toggle_service": {
                String svcId = normalizeService(args.optString("service", ""));
                if (svcId == null) return "unknown service";
                boolean on = "on".equalsIgnoreCase(args.optString("state", ""));
                ctx.getSharedPreferences("archer", Context.MODE_PRIVATE).edit()
                        .putBoolean("conn_" + svcId + "_enabled", on).apply();
                return svcId + " daily work " + (on ? "on" : "off");
            }
            case "set_browser_mode": {
                String svcId = normalizeService(args.optString("service", ""));
                if (svcId == null) return "unknown service";
                boolean on = "on".equalsIgnoreCase(args.optString("state", ""));
                ctx.getSharedPreferences("archer", Context.MODE_PRIVATE).edit()
                        .putBoolean("conn_" + svcId + "_browser", on)
                        .putBoolean("conn_" + svcId + "_enabled", true).apply();
                ConnectWorker.logHistory(ctx, ConnectWorker.svcName(svcId) + " — ব্রাউজার মোড " + (on ? "চালু" : "বন্ধ") + " (চ্যাট থেকে)");
                return svcId + " browser mode " + (on ? "on — will work through Chrome without a token" : "off");
            }
            case "open_browser": {
                String u = args.optString("url", "").trim();
                BrowserActivity.open(ctx, u);
                return "NVN built-in browser opened" + (u.isEmpty() ? "" : " at " + u);
            }
            case "connect_status": {
                SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
                StringBuilder sb = new StringBuilder();
                for (String svcId : ConnectWorker.SVC_IDS) {
                    boolean connected = ConnectWorker.isConnected(ctx, svcId);
                    boolean enabled = p.getBoolean("conn_" + svcId + "_enabled", false);
                    String rules = p.getString("conn_" + svcId + "_rules", "");
                    sb.append(ConnectWorker.svcName(svcId)).append(": ")
                            .append(connected ? "connected" : (ConnectWorker.isBrowserMode(ctx, svcId) ? "browser mode (no token)" : "not connected"))
                            .append(enabled ? ", daily work ON" : ", daily work off")
                            .append(rules != null && !rules.trim().isEmpty() ? ", rules saved" : ", no rules yet")
                            .append(". ");
                }
                return sb.toString();
            }
            case "run_connect_work": {
                ConnectWorker.runInBackground(ctx.getApplicationContext(), true);
                return "connect work started";
            }
            case "connect_site": {
                String svcId = normalizeService(args.optString("service", ""));
                if (svcId == null) return "unknown service";
                return "CONNECT_DIALOG:" + svcId;
            }
            default:
                return "unknown function";
        }
    }

    public static String normalizeService(String s) {
        if (s == null) return null;
        String q = s.toLowerCase(Locale.ROOT).trim();
        if (q.contains("facebook") || q.contains("ফেসবুক") || q.contains("fb")) return "facebook";
        if (q.contains("instagram") || q.contains("ইনস্টাগ্রাম") || q.contains("insta")) return "instagram";
        if (q.contains("threads") || q.contains("থ্রেড")) return "threads";
        if (q.contains("youtube") || q.contains("ইউটিউব")) return "youtube";
        if (q.contains("telegram") || q.contains("টেলিগ্রাম")) return "telegram";
        if (q.contains("blogger") || q.contains("ব্লগার")) return "blogger";
        if (q.contains("wordpress") || q.contains("ওয়ার্ডপ্রেস")) return "wordpress";
        if (q.contains("tiktok") || q.contains("টিকটক")) return "tiktok";
        if (q.contains("pinterest") || q.contains("পিন্টারেস্ট")) return "pinterest";
        if (q.contains("whatsapp") || q.contains("হোয়াটসঅ্যাপ")) return "whatsapp";
        if (q.contains("gmail") || q.contains("জিমেইল") || q.contains("email") || q.contains("ইমেইল")) return "gmail";
        if (q.contains("twitter") || q.contains("টুইটার") || q.equals("x") || q.contains("এক্স")) return "x";
        if (q.contains("website") || q.contains("ওয়েবসাইট") || q.contains("site") || q.contains("সাইট")) return "website";
        if (q.contains("custom") || q.contains("কাস্টম")) return "custom";
        for (String id : ConnectWorker.SVC_IDS) if (id.equals(q)) return id;
        return null;
    }

    public static boolean openAppByName(Context ctx, String appName) {
        try {
            String q = appName.toLowerCase(Locale.ROOT).trim();
            Map<String, String> known = new HashMap<>();
            known.put("facebook", "com.facebook.katana");
            known.put("ফেসবুক", "com.facebook.katana");
            known.put("messenger", "com.facebook.orca");
            known.put("youtube", "com.google.android.youtube");
            known.put("ইউটিউব", "com.google.android.youtube");
            known.put("instagram", "com.instagram.android");
            known.put("ইনস্টাগ্রাম", "com.instagram.android");
            known.put("whatsapp", "com.whatsapp");
            known.put("হোয়াটসঅ্যাপ", "com.whatsapp");
            known.put("telegram", "org.telegram.messenger");
            known.put("টেলিগ্রাম", "org.telegram.messenger");
            known.put("tiktok", "com.zhiliaoapp.musically");
            known.put("টিকটক", "com.zhiliaoapp.musically");
            known.put("chrome", "com.android.chrome");
            known.put("ক্রোম", "com.android.chrome");
            known.put("gmail", "com.google.android.gm");
            known.put("play store", "com.android.vending");
            known.put("প্লে স্টোর", "com.android.vending");
            known.put("bkash", "com.bKash.customerapp");
            known.put("বিকাশ", "com.bKash.customerapp");
            known.put("nagad", "com.konasl.nagad");
            known.put("নগদ", "com.konasl.nagad");
            known.put("imo", "com.imo.android.imoim");
            known.put("ইমো", "com.imo.android.imoim");
            known.put("facebook lite", "com.facebook.lite");
            known.put("messenger lite", "com.facebook.mlite");
            PackageManager pm = ctx.getPackageManager();
            String pkg = known.get(q);
            if (pkg == null) {
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (ApplicationInfo ai : apps) {
                    String label = pm.getApplicationLabel(ai).toString().toLowerCase(Locale.ROOT);
                    if (label.equals(q) || label.contains(q) || q.contains(label)) { pkg = ai.packageName; break; }
                }
            }
            if (pkg == null) return false;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(launch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String callNumber(Context ctx, String number) {
        try {
            String num = number.replaceAll("[^0-9+]", "");
            if (num.isEmpty()) return "no number";
            boolean canCall = Build.VERSION.SDK_INT < 23 ||
                    ctx.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            Intent i = new Intent(canCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + num));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            return canCall ? "calling " + num : "dialer opened for " + num;
        } catch (Exception e) {
            return "call failed";
        }
    }

    private static TextToSpeech callTts;

    public static String callAndTalk(Context ctx, String number, String message) {
        String res = callNumber(ctx, number);
        if (!res.startsWith("calling")) return res;
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            am.setMode(AudioManager.MODE_IN_CALL);
            am.setSpeakerphoneOn(true);
        } catch (Exception ignored) {}
        Handler h = new Handler(Looper.getMainLooper());
        h.postDelayed(() -> {
            try {
                if (callTts == null) {
                    callTts = new TextToSpeech(ctx.getApplicationContext(), status -> {
                        if (status == TextToSpeech.SUCCESS) {
                            try { callTts.setLanguage(new Locale("bn", "BD")); } catch (Exception ignored) {}
                            callTts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nvn-call");
                        }
                    });
                } else {
                    callTts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "nvn-call");
                }
            } catch (Exception ignored) {}
        }, 5000);
        return "calling and will speak: " + message;
    }

    public static String findContactNumber(Context ctx, String name) {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    ctx.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Cursor c = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            String number = null;
            if (c != null) {
                if (c.moveToFirst()) number = c.getString(0);
                c.close();
            }
            return number;
        } catch (Exception e) {
            return null;
        }
    }

    public static String sendSms(Context ctx, String number, String text) {
        try {
            String num = number.replaceAll("[^0-9+]", "");
            if (num.isEmpty() || text.isEmpty()) return "missing number or text";
            if (Build.VERSION.SDK_INT >= 23 &&
                    ctx.checkSelfPermission(android.Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                return "sms permission not granted";
            }
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(text);
            sms.sendMultipartTextMessage(num, null, parts, null, null);
            return "sms sent to " + num;
        } catch (Exception e) {
            return "sms failed";
        }
    }

    public static String sendWhatsApp(Context ctx, String number, String text) {
        try {
            String num = number.replaceAll("[^0-9]", "");
            if (num.isEmpty() || text.isEmpty()) return "missing number or text";
            if (num.startsWith("0")) num = "880" + num.substring(1);
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(text)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                ArcherAccessibilityService svc = ArcherAccessibilityService.instance;
                if (svc != null) {
                    if (!svc.tapText("Send")) svc.tapText("পাঠান");
                }
            }, 3000);
            return "whatsapp opened for " + num + ", message sending";
        } catch (Exception e) {
            return "whatsapp failed";
        }
    }

    public static String setVolume(Context ctx, String direction) {
        try {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int cur = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            int target = cur;
            if ("up".equals(direction)) target = Math.min(max, cur + 2);
            else if ("down".equals(direction)) target = Math.max(0, cur - 2);
            else if ("max".equals(direction)) target = max;
            else if ("min".equals(direction)) target = 0;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI);
            return "volume " + target + "/" + max;
        } catch (Exception e) {
            return "volume failed";
        }
    }

    public static String setTorch(Context ctx, boolean on) {
        try {
            CameraManager cm = (CameraManager) ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                Boolean flash = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (flash != null && flash) { id = cid; break; }
            }
            if (id == null) return "no flashlight";
            cm.setTorchMode(id, on);
            return on ? "torch on" : "torch off";
        } catch (Exception e) {
            return "torch failed";
        }
    }

    public static String httpGet(String urlStr) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String httpPost(String urlStr, String json) {
        try {
            java.net.URL url = new java.net.URL(urlStr);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(20000);
            conn.setRequestProperty("Content-Type", "application/json");
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(conn.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String[] checkAnnouncement(Context ctx) {
        try {
            String body = httpGet(SERVER_BASE + "/announce");
            if (body == null || body.trim().isEmpty()) return null;
            JSONObject o = new JSONObject(body);
            int id = o.optInt("id", 0);
            String title = o.optString("title", "");
            String text = o.optString("body", "");
            if (id == 0 || title.isEmpty()) return null;
            SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
            if (p.getInt("last_announce_id", 0) == id) return null;
            p.edit().putInt("last_announce_id", id)
                    .putString("last_announce_title", title)
                    .putString("last_announce_body", text).apply();
            return new String[]{title, text};
        } catch (Exception e) {
            return null;
        }
    }

    public static void showAnnouncementNotification(Context ctx, String title, String body) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            String channelId = "nvn_announce";
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                android.app.NotificationChannel ch = new android.app.NotificationChannel(channelId, "NVN ঘোষণা", android.app.NotificationManager.IMPORTANCE_HIGH);
                nm.createNotificationChannel(ch);
            }
            Intent intent = new Intent(ctx, MainActivity.class);
            android.app.PendingIntent pi = android.app.PendingIntent.getActivity(ctx, 0, intent,
                    android.os.Build.VERSION.SDK_INT >= 23 ? android.app.PendingIntent.FLAG_IMMUTABLE : 0);
            android.app.Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(ctx, channelId)
                    : new android.app.Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new android.app.Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(777, b.build());
        } catch (Exception ignored) {}
    }

    public static String postAnnouncement(Context ctx, String token, String title, String body) {
        try {
            JSONObject o = new JSONObject().put("token", token).put("title", title).put("body", body);
            String res = httpPost(SERVER_BASE + "/announce", o.toString());
            return res != null && res.contains("ok") ? "sent" : "failed";
        } catch (Exception e) {
            return "failed";
        }
    }

    private static final String[] BACKUP_STRING_KEYS = {
            "girl_name", "user_name", "memory_facts", "todo_list", "chat_history",
            "premium_tier", "gemini_keys", "gemini_key", "bkash_number", "owner_pin_hash",
            "personality", "voice_lang", "last_announce_title", "last_announce_body",
            "conn_queue", "conn_history", "conn_custom_name",
            "conn_facebook_token", "conn_facebook_id", "conn_facebook_rules",
            "conn_instagram_token", "conn_instagram_id", "conn_instagram_rules",
            "conn_threads_token", "conn_threads_id", "conn_threads_rules",
            "conn_youtube_token", "conn_youtube_id", "conn_youtube_rules",
            "conn_telegram_token", "conn_telegram_id", "conn_telegram_rules",
            "conn_website_token", "conn_website_id", "conn_website_rules",
            "conn_custom_token", "conn_custom_id", "conn_custom_rules",
            "conn_blogger_token", "conn_blogger_id", "conn_blogger_rules",
            "conn_wordpress_token", "conn_wordpress_id", "conn_wordpress_rules",
            "conn_tiktok_token", "conn_tiktok_id", "conn_tiktok_rules",
            "conn_x_token", "conn_x_id", "conn_x_rules",
            "conn_pinterest_token", "conn_pinterest_id", "conn_pinterest_rules",
            "conn_whatsapp_token", "conn_whatsapp_id", "conn_whatsapp_rules",
            "conn_gmail_token", "conn_gmail_id", "conn_gmail_rules",
            "conn_facebook_user", "conn_facebook_pass",
            "conn_instagram_user", "conn_instagram_pass",
            "conn_threads_user", "conn_threads_pass",
            "conn_youtube_user", "conn_youtube_pass",
            "conn_telegram_user", "conn_telegram_pass",
            "conn_blogger_user", "conn_blogger_pass",
            "conn_wordpress_user", "conn_wordpress_pass",
            "conn_tiktok_user", "conn_tiktok_pass",
            "conn_x_user", "conn_x_pass",
            "conn_pinterest_user", "conn_pinterest_pass",
            "conn_whatsapp_user", "conn_whatsapp_pass",
            "conn_gmail_user", "conn_gmail_pass",
            "conn_website_user", "conn_website_pass",
            "conn_custom_user", "conn_custom_pass",
            "conn_facebook_site", "conn_instagram_site", "conn_threads_site",
            "conn_youtube_site", "conn_telegram_site", "conn_blogger_site",
            "conn_wordpress_site", "conn_tiktok_site", "conn_x_site",
            "conn_pinterest_site", "conn_whatsapp_site", "conn_gmail_site",
            "conn_website_site", "conn_custom_site",
            "conn_facebook_account", "conn_facebook_device", "conn_facebook_since",
            "conn_instagram_account", "conn_instagram_device", "conn_instagram_since",
            "conn_threads_account", "conn_threads_device", "conn_threads_since",
            "conn_youtube_account", "conn_youtube_device", "conn_youtube_since",
            "conn_telegram_account", "conn_telegram_device", "conn_telegram_since",
            "conn_blogger_account", "conn_blogger_device", "conn_blogger_since",
            "conn_wordpress_account", "conn_wordpress_device", "conn_wordpress_since",
            "conn_tiktok_account", "conn_tiktok_device", "conn_tiktok_since",
            "conn_x_account", "conn_x_device", "conn_x_since",
            "conn_pinterest_account", "conn_pinterest_device", "conn_pinterest_since",
            "conn_whatsapp_account", "conn_whatsapp_device", "conn_whatsapp_since",
            "conn_gmail_account", "conn_gmail_device", "conn_gmail_since",
            "conn_website_account", "conn_website_device", "conn_website_since",
            "conn_custom_account", "conn_custom_device", "conn_custom_since",
            "orb_color", "qa_cache", "prayer_cache", "prayer_cache_date", "last_brief_date"
    };

    private static final String[] BACKUP_BOOL_KEYS = {
            "conn_facebook_browser", "conn_instagram_browser", "conn_threads_browser",
            "conn_youtube_browser", "conn_telegram_browser", "conn_blogger_browser",
            "conn_wordpress_browser", "conn_tiktok_browser", "conn_x_browser",
            "conn_pinterest_browser", "conn_whatsapp_browser", "conn_gmail_browser",
            "conn_website_browser", "conn_custom_browser", "fast_mode"
    };

    // This phone's name, e.g. "Infinix SMART 9 HD"
    public static String deviceName() {
        String m = (Build.MANUFACTURER == null ? "" : Build.MANUFACTURER.trim());
        String model = (Build.MODEL == null ? "" : Build.MODEL.trim());
        String name = (m + " " + model).trim();
        return name.isEmpty() ? "এই ফোন" : name;
    }

    // Remember WHICH phone + account a service was connected on, and when (Composio-style)
    public static void markConnected(Context ctx, String svc, String account) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
            SharedPreferences.Editor ed = p.edit();
            ed.putString("conn_" + svc + "_device", deviceName());
            if (account != null && !account.trim().isEmpty())
                ed.putString("conn_" + svc + "_account", account.trim());
            if (p.getString("conn_" + svc + "_since", "").isEmpty())
                ed.putString("conn_" + svc + "_since",
                        new java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.ENGLISH)
                                .format(new java.util.Date()));
            ed.apply();
        } catch (Exception ignored) {
        }
        notifyConnected(ctx, svc, account);
    }

    // "✅ Connected Success" notification whenever a service connects
    public static void notifyConnected(Context ctx, String svc, String account) {
        try {
            android.app.NotificationManager nm = (android.app.NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String channelId = "nvn_connect";
            if (android.os.Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(new android.app.NotificationChannel(
                        channelId, "NVN Connect", android.app.NotificationManager.IMPORTANCE_DEFAULT));
            }
            android.app.Notification.Builder b = android.os.Build.VERSION.SDK_INT >= 26
                    ? new android.app.Notification.Builder(ctx, channelId)
                    : new android.app.Notification.Builder(ctx);
            String text = ConnectWorker.svcName(svc) + " কানেক্ট হয়েছে — " + deviceName();
            if (account != null && !account.trim().isEmpty()) text += " · " + account.trim();
            b.setContentTitle("✅ Connected Success").setContentText(text)
                    .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true);
            nm.notify(780 + Math.abs(svc.hashCode() % 100), b.build());
        } catch (Exception ignored) {
        }
    }

    // Reminder that fires later even if the app is closed (works while he's away)
    public static boolean setReminder(Context ctx, String text, int minutes) {
        try {
            android.app.AlarmManager am = (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return false;
            android.content.Intent i = new android.content.Intent(ctx, ReminderReceiver.class);
            i.putExtra("text", text);
            int code = (int) (System.currentTimeMillis() % 100000);
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(ctx, code, i,
                    android.os.Build.VERSION.SDK_INT >= 23
                            ? android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT
                            : android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            long at = System.currentTimeMillis() + minutes * 60_000L;
            if (android.os.Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, at, pi);
            else am.set(android.app.AlarmManager.RTC_WAKEUP, at, pi);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Today's prayer times (Dhamrai) — fetched once, then served from cache offline
    public static String prayerTimesText(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(new java.util.Date());
        String cached = p.getString("prayer_cache", "");
        if (today.equals(p.getString("prayer_cache_date", "")) && !cached.isEmpty()) return cached;
        try {
            String res = httpGet("https://api.aladhan.com/v1/timings?latitude=23.92&longitude=90.21&method=1");
            if (res != null) {
                org.json.JSONObject t = new org.json.JSONObject(res).getJSONObject("data").getJSONObject("timings");
                String out = "🕌 আজকের নামাজের সময় (ধামরাই):\nফজর " + t.optString("Fajr")
                        + "\nযোহর " + t.optString("Dhuhr") + "\nআসর " + t.optString("Asr")
                        + "\nমাগরিব " + t.optString("Maghrib") + "\nএশা " + t.optString("Isha");
                p.edit().putString("prayer_cache", out).putString("prayer_cache_date", today).apply();
                return out;
            }
        } catch (Exception ignored) {}
        return cached.isEmpty() ? "নামাজের সময় এখন আনা যাচ্ছে না মাস্টার — ইন্টারনেট এলে আবার বলুন।" : cached;
    }

    public static void backupData(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
            JSONObject o = new JSONObject();
            for (String k : BACKUP_STRING_KEYS) {
                String v = p.getString(k, null);
                if (v != null) o.put(k, v);
            }
            for (String k : BACKUP_BOOL_KEYS) o.put(k, p.getBoolean(k, false));
            o.put("premium_expiry", p.getLong("premium_expiry", 0));
            o.put("owner_mode", p.getBoolean("owner_mode", false));
            o.put("last_announce_id", p.getInt("last_announce_id", 0));
            String json = o.toString();
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver cr = ctx.getContentResolver();
                String rel = android.os.Environment.DIRECTORY_DOCUMENTS + "/NVNAI";
                try (android.database.Cursor c = cr.query(android.provider.MediaStore.Files.getContentUri("external"),
                        new String[]{android.provider.MediaStore.MediaColumns._ID},
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{rel, "nvn_backup.json"}, null)) {
                    if (c != null) {
                        while (c.moveToNext()) {
                            long id = c.getLong(0);
                            cr.delete(android.content.ContentUris.withAppendedId(android.provider.MediaStore.Files.getContentUri("external"), id), null, null);
                        }
                    }
                }
                android.content.ContentValues cv = new android.content.ContentValues();
                cv.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "nvn_backup.json");
                cv.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/json");
                cv.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel);
                android.net.Uri uri = cr.insert(android.provider.MediaStore.Files.getContentUri("external"), cv);
                if (uri != null) {
                    try (java.io.OutputStream os = cr.openOutputStream(uri)) {
                        os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    }
                }
            } else {
                java.io.File dir = new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "NVNAI");
                dir.mkdirs();
                try (java.io.FileWriter fw = new java.io.FileWriter(new java.io.File(dir, "nvn_backup.json"))) {
                    fw.write(json);
                }
            }
        } catch (Exception ignored) {}
    }

    // If the Gemini keys (or connect tokens) are missing — e.g. after a reinstall where the
    // name was re-entered so restoreData() skipped — pull ONLY those back from the backup file.
    public static void restoreKeysIfMissing(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
            if (!getKeys(p).isEmpty()) return;
            String json = readBackupJson(ctx);
            if (json == null || json.trim().isEmpty()) return;
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = p.edit();
            String[] keys = {"gemini_keys", "gemini_key"};
            for (String k : keys) if (o.has(k)) e.putString(k, o.optString(k, ""));
            for (String k : BACKUP_STRING_KEYS) {
                if ((k.startsWith("conn_") && (k.endsWith("_token") || k.endsWith("_id")
                        || k.endsWith("_user") || k.endsWith("_pass") || k.endsWith("_site")))
                        && o.has(k) && p.getString(k, "").isEmpty()) {
                    e.putString(k, o.optString(k, ""));
                }
            }
            e.apply();
        } catch (Exception ignored) {}
    }

    private static String readBackupJson(Context ctx) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver cr = ctx.getContentResolver();
                String rel = android.os.Environment.DIRECTORY_DOCUMENTS + "/NVNAI";
                try (android.database.Cursor c = cr.query(android.provider.MediaStore.Files.getContentUri("external"),
                        new String[]{android.provider.MediaStore.MediaColumns._ID},
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{rel, "nvn_backup.json"}, null)) {
                    if (c != null && c.moveToFirst()) {
                        long id = c.getLong(0);
                        android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Files.getContentUri("external"), id);
                        try (java.io.InputStream is = cr.openInputStream(uri);
                             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            return sb.toString();
                        }
                    }
                }
            } else {
                java.io.File f = new java.io.File(new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "NVNAI"), "nvn_backup.json");
                if (f.exists()) {
                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static void restoreData(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("archer", Context.MODE_PRIVATE);
            if (!p.getString("girl_name", "").isEmpty()) return;
            String json = null;
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                android.content.ContentResolver cr = ctx.getContentResolver();
                String rel = android.os.Environment.DIRECTORY_DOCUMENTS + "/NVNAI";
                try (android.database.Cursor c = cr.query(android.provider.MediaStore.Files.getContentUri("external"),
                        new String[]{android.provider.MediaStore.MediaColumns._ID},
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                        new String[]{rel, "nvn_backup.json"}, null)) {
                    if (c != null && c.moveToFirst()) {
                        long id = c.getLong(0);
                        android.net.Uri uri = android.content.ContentUris.withAppendedId(android.provider.MediaStore.Files.getContentUri("external"), id);
                        try (java.io.InputStream is = cr.openInputStream(uri);
                             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is, java.nio.charset.StandardCharsets.UTF_8))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line);
                            json = sb.toString();
                        }
                    }
                }
            } else {
                java.io.File f = new java.io.File(new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS), "NVNAI"), "nvn_backup.json");
                if (f.exists()) {
                    StringBuilder sb = new StringBuilder();
                    try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    json = sb.toString();
                }
            }
            if (json == null || json.trim().isEmpty()) return;
            JSONObject o = new JSONObject(json);
            SharedPreferences.Editor e = p.edit();
            for (String k : BACKUP_STRING_KEYS) {
                if (o.has(k)) e.putString(k, o.optString(k, ""));
            }
            for (String k : BACKUP_BOOL_KEYS) if (o.has(k)) e.putBoolean(k, o.optBoolean(k, false));
            e.putLong("premium_expiry", o.optLong("premium_expiry", 0));
            e.putBoolean("owner_mode", o.optBoolean("owner_mode", false));
            e.putInt("last_announce_id", o.optInt("last_announce_id", 0));
            e.apply();
        } catch (Exception ignored) {}
    }
}
