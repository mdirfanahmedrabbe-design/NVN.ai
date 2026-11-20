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
        p.edit().putString("q_date", t).putInt("q_count", count + 1).apply();
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
        p.edit().putInt("key_index", p.getInt("key_index", 0) + 1).apply();
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
            default:
                return "unknown function";
        }
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
}
