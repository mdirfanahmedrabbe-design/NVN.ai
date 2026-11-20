package com.archer.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView chat, status;
    private ScrollView scroll;
    private EditText input, keyInput;
    private OrbView orb;
    private SpeechRecognizer recognizer;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean english = false;
    private boolean torchOn = false;
    private String cameraId = null;

    private static final Map<String, String> KNOWN_APPS = new HashMap<>();
    static {
        KNOWN_APPS.put("facebook", "com.facebook.katana");
        KNOWN_APPS.put("ফেসবুক", "com.facebook.katana");
        KNOWN_APPS.put("messenger", "com.facebook.orca");
        KNOWN_APPS.put("youtube", "com.google.android.youtube");
        KNOWN_APPS.put("ইউটিউব", "com.google.android.youtube");
        KNOWN_APPS.put("instagram", "com.instagram.android");
        KNOWN_APPS.put("ইনস্টাগ্রাম", "com.instagram.android");
        KNOWN_APPS.put("whatsapp", "com.whatsapp");
        KNOWN_APPS.put("হোয়াটসঅ্যাপ", "com.whatsapp");
        KNOWN_APPS.put("telegram", "org.telegram.messenger");
        KNOWN_APPS.put("টেলিগ্রাম", "org.telegram.messenger");
        KNOWN_APPS.put("tiktok", "com.zhiliaoapp.musically");
        KNOWN_APPS.put("টিকটক", "com.zhiliaoapp.musically");
        KNOWN_APPS.put("chrome", "com.android.chrome");
        KNOWN_APPS.put("ক্রোম", "com.android.chrome");
        KNOWN_APPS.put("gmail", "com.google.android.gm");
        KNOWN_APPS.put("play store", "com.android.vending");
        KNOWN_APPS.put("প্লে স্টোর", "com.android.vending");
        KNOWN_APPS.put("bkash", "com.bKash.customerapp");
        KNOWN_APPS.put("বিকাশ", "com.bKash.customerapp");
        KNOWN_APPS.put("nagad", "com.konasl.nagad");
        KNOWN_APPS.put("নগদ", "com.konasl.nagad");
        KNOWN_APPS.put("imo", "com.imo.android.imoim");
        KNOWN_APPS.put("ইমো", "com.imo.android.imoim");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chat = findViewById(R.id.chat);
        status = findViewById(R.id.status);
        scroll = findViewById(R.id.scroll);
        input = findViewById(R.id.input);
        keyInput = findViewById(R.id.key);
        orb = findViewById(R.id.orb);
        Button mic = findViewById(R.id.mic);
        Button send = findViewById(R.id.send);
        Button lang = findViewById(R.id.lang);
        Button saveKey = findViewById(R.id.saveKey);
        Button cameraBtn = findViewById(R.id.camera);
        Button newsBtn = findViewById(R.id.news);
        Button accessBtn = findViewById(R.id.access);
        Button liveBtn = findViewById(R.id.live);

        liveBtn.setOnClickListener(v -> startActivity(new Intent(this, LiveActivity.class)));

        cameraBtn.setOnClickListener(v -> takePhoto());
        newsBtn.setOnClickListener(v -> fetchNews());
        accessBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "NVN AI Voice Control চালু করো", Toast.LENGTH_LONG).show();
        });

        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        keyInput.setText(prefs.getString("gemini_key", ""));
        saveKey.setOnClickListener(v -> {
            prefs.edit().putString("gemini_key", keyInput.getText().toString().trim()).apply();
            status.setText("Key সেভ হয়েছে");
        });

        lang.setOnClickListener(v -> {
            english = !english;
            lang.setText(english ? "English" : "বাংলা");
            status.setText(english ? "Language: English" : "ভাষা: বাংলা");
        });

        if (Build.VERSION.SDK_INT >= 23 &&
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 1);
        }

        tts = new TextToSpeech(this, s -> {
            if (s == TextToSpeech.SUCCESS) {
                ttsReady = true;
                tts.setLanguage(new Locale("bn", "BD"));
            }
        });
        tts.setOnUtteranceProgressListener(new android.speech.tts.UtteranceProgressListener() {
            @Override public void onStart(String id) { runOnUiThread(() -> orb.setState(OrbView.SPEAKING)); }
            @Override public void onDone(String id) { runOnUiThread(() -> orb.setState(OrbView.IDLE)); }
            @Override public void onError(String id) { runOnUiThread(() -> orb.setState(OrbView.IDLE)); }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onResults(Bundle results) {
                    ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    orb.setState(OrbView.IDLE);
                    if (list != null && !list.isEmpty()) handle(list.get(0));
                }
                @Override public void onError(int error) {
                    orb.setState(OrbView.IDLE);
                    status.setText("শুনতে পারিনি, আবার চেষ্টা করো");
                }
                @Override public void onReadyForSpeech(Bundle p) { status.setText("শুনছি... কথা বলো"); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float v) {}
                @Override public void onBufferReceived(byte[] b) {}
                @Override public void onEndOfSpeech() { status.setText("ভাবছি..."); }
                @Override public void onPartialResults(Bundle p) {}
                @Override public void onEvent(int t, Bundle p) {}
            });
        }

        mic.setOnClickListener(v -> {
            if (recognizer == null) {
                status.setText("এই ফোনে ভয়েস ইনপুট নেই — লিখে পাঠাও");
                return;
            }
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, english ? "en-US" : "bn-BD");
            orb.setState(OrbView.LISTENING);
            try { recognizer.startListening(i); } catch (Exception e) { orb.setState(OrbView.IDLE); }
        });

        send.setOnClickListener(v -> {
            String q = input.getText().toString();
            input.setText("");
            handle(q);
        });

        addAi("হ্যালো! আমি NVN। মাইক চেপে কথা বলো — অ্যাপ খুলতে, volume/brightness বদলাতে, torch জ্বালাতে পারি।");
    }

    private void addMe(String text) {
        chat.append("\nতুমি: " + text + "\n");
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void addAi(String text) {
        chat.append("NVN: " + text + "\n");
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void reply(String text) {
        addAi(text);
        if (ttsReady) {
            tts.setLanguage(english ? Locale.US : new Locale("bn", "BD"));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "archer");
        }
    }

    private void handle(String q) {
        if (q == null || q.trim().isEmpty()) return;
        addMe(q);
        String s = q.toLowerCase(Locale.ROOT);
        String cmd = runCommand(s);
        if (cmd != null) {
            reply(cmd);
            status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
            return;
        }
        status.setText("ভাবছি...");
        new Thread(() -> {
            String ans = askGemini(q);
            runOnUiThread(() -> {
                reply(ans);
                status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
            });
        }).start();
    }

    private String runCommand(String s) {
        // time / date
        if (s.contains("কয়টা বাজে") || s.contains("time")) {
            return "এখন " + new SimpleDateFormat("h:mm a", Locale.US).format(new Date()) + "।";
        }
        if (s.contains("তারিখ") || s.contains("date")) {
            return "আজ " + new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.US).format(new Date()) + "।";
        }
        if (s.contains("তুমি কে") || s.contains("who are you") || s.contains("introduce")) {
            return "আমি NVN, তোমার AI অ্যাসিস্ট্যান্ট। কথা বলো, আমি কাজ করব।";
        }
        // volume
        AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (s.contains("volume") || s.contains("ভলিউম") || s.contains("সাউন্ড")) {
            if (s.contains("বাড়াও") || s.contains("বাড়া") || s.contains("up") || s.contains("increase")) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                return "Volume বাড়ালাম।";
            }
            if (s.contains("কমাও") || s.contains("কমা") || s.contains("down") || s.contains("decrease")) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return "Volume কমালাম।";
            }
        }
        // brightness
        if (s.contains("brightness") || s.contains("ব্রাইটনেস") || s.contains("আলো")) {
            return changeBrightness(s.contains("বাড়াও") || s.contains("বাড়া") || s.contains("up") || s.contains("increase"));
        }
        // flashlight
        if (s.contains("torch") || s.contains("টর্চ") || s.contains("flashlight") || s.contains("ফ্ল্যাশ")) {
            boolean on = !(s.contains("বন্ধ") || s.contains("off"));
            return setTorch(on);
        }
        // settings
        if (s.contains("settings") || s.contains("সেটিংস")) {
            startActivity(new Intent(Settings.ACTION_SETTINGS));
            return "Settings খুললাম।";
        }
        if (s.contains("camera") || s.contains("ক্যামেরা")) {
            if (s.contains("দেখ") || s.contains("কী") || s.contains("what") || s.contains("see")) {
                takePhoto();
                return "ছবি তোলো, আমি দেখে বলছি।";
            }
            Intent i = new Intent("android.media.action.IMAGE_CAPTURE");
            try { startActivity(i); return "Camera খুললাম।"; } catch (Exception e) { return "Camera খুলতে পারলাম না।"; }
        }
        if (s.contains("লাইভ") || s.contains("live")) {
            startActivity(new Intent(this, LiveActivity.class));
            return "লাইভ স্ক্রিন খুললাম — 🔴 লাইভ শুরু করো চাপো।";
        }
        // news
        if (s.contains("খবর") || s.contains("news") || s.contains("headline")) {
            fetchNews();
            return "খবর আনছি...";
        }
        // accessibility global actions
        if (s.contains("back") || s.contains("পিছনে") || s.contains("পেছনে")) {
            return accessibilityAction("back");
        }
        if (s.contains("home") || s.contains("হোম")) {
            return accessibilityAction("home");
        }
        if (s.contains("recent") || s.contains("রিসেন্ট")) {
            return accessibilityAction("recents");
        }
        if (s.contains("notification") || s.contains("নোটিফিকেশন")) {
            return accessibilityAction("notifications");
        }
        // open app by name
        if (s.contains("খোলো") || s.contains("খুলো") || s.contains("open") || s.contains("চালু")) {
            return openApp(s);
        }
        return null;
    }

    private String openApp(String s) {
        for (Map.Entry<String, String> e : KNOWN_APPS.entrySet()) {
            if (s.contains(e.getKey())) {
                if (launchPackage(e.getValue())) return capitalize(e.getKey()) + " খুললাম।";
                return capitalize(e.getKey()) + " এই ফোনে পাইনি।";
            }
        }
        // search installed apps by label
        PackageManager pm = getPackageManager();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            String label = pm.getApplicationLabel(app).toString().toLowerCase(Locale.ROOT);
            if (!label.isEmpty() && s.contains(label)) {
                if (launchPackage(app.packageName)) return pm.getApplicationLabel(app) + " খুললাম।";
            }
        }
        return "কোন অ্যাপ খুলব বুঝতে পারিনি। অ্যাপের নামটা বলো।";
    }

    private boolean launchPackage(String pkg) {
        Intent i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return false;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        return true;
    }

    private String changeBrightness(boolean up) {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            startActivity(i);
            return "Brightness বদলাতে একবার পারমিশন দাও — স্ক্রিনে Allow করে ফিরে এসো।";
        }
        try {
            int cur = Settings.System.getInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
            int next = Math.max(10, Math.min(255, cur + (up ? 50 : -50)));
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            Settings.System.putInt(getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, next);
            return up ? "Brightness বাড়ালাম।" : "Brightness কমালাম।";
        } catch (Exception e) {
            return "Brightness বদলাতে পারলাম না।";
        }
    }

    private String setTorch(boolean on) {
        try {
            CameraManager cm = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraId == null) {
                for (String id : cm.getCameraIdList()) {
                    Boolean flash = cm.getCameraCharacteristics(id)
                            .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { cameraId = id; break; }
                }
            }
            if (cameraId == null) return "এই ফোনে ফ্ল্যাশ পাইনি।";
            cm.setTorchMode(cameraId, on);
            torchOn = on;
            return on ? "Torch জ্বালালাম।" : "Torch বন্ধ করলাম।";
        } catch (Exception e) {
            return "Torch চালু করতে পারলাম না।";
        }
    }

    private void takePhoto() {
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            startActivityForResult(i, 42);
        } catch (Exception e) {
            reply("Camera খুলতে পারলাম না।");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 42 && resultCode == RESULT_OK && data != null && data.getExtras() != null) {
            Bitmap bmp = (Bitmap) data.getExtras().get("data");
            if (bmp == null) {
                reply("ছবি পাইনি।");
                return;
            }
            addMe("📷 ছবি পাঠানো হয়েছে");
            status.setText("ছবি দেখছি...");
            new Thread(() -> {
                String ans = askGeminiVision(bmp);
                runOnUiThread(() -> {
                    reply(ans);
                    status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
                });
            }).start();
        }
    }

    private String askGeminiVision(Bitmap bmp) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String key = prefs.getString("gemini_key", "");
        if (key.isEmpty()) {
            return "ছবি বুঝতে ফ্রি Gemini Key লাগবে — নিচে বসাও (aistudio.google.com)।";
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            JSONObject textPart = new JSONObject();
            textPart.put("text", "Describe what you see in this photo briefly (2-3 sentences) in Bengali. If there is text, read it.");
            JSONObject inline = new JSONObject();
            inline.put("mime_type", "image/jpeg");
            inline.put("data", b64);
            JSONObject imgPart = new JSONObject();
            imgPart.put("inline_data", inline);
            JSONObject content = new JSONObject();
            content.put("parts", new JSONArray().put(textPart).put(imgPart));
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject res = new JSONObject(sb.toString());
            return res.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text").trim();
        } catch (Exception e) {
            return "ছবি বুঝতে পারলাম না। ইন্টারনেট আর Key দেখো।";
        }
    }

    private void fetchNews() {
        status.setText("খবর আনছি...");
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
                StringBuilder out = new StringBuilder("আজকের খবর (BBC বাংলা):\n");
                int idx = 0, count = 0;
                while (count < 5) {
                    int start = xml.indexOf("<item>", idx);
                    if (start < 0) break;
                    int tStart = xml.indexOf("<title>", start);
                    int tEnd = xml.indexOf("</title>", tStart);
                    if (tStart < 0 || tEnd < 0) break;
                    String title = xml.substring(tStart + 7, tEnd)
                            .replace("<![CDATA[", "").replace("]]>", "").trim();
                    out.append("• ").append(title).append("\n");
                    idx = tEnd;
                    count++;
                }
                result = count > 0 ? out.toString().trim() : "খবর পাইনি।";
            } catch (Exception e) {
                result = "খবর আনতে পারলাম না। ইন্টারনেট দেখো।";
            }
            String finalResult = result;
            runOnUiThread(() -> {
                reply(finalResult);
                status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
            });
        }).start();
    }

    private String accessibilityAction(String action) {
        ArcherAccessibilityService svc = ArcherAccessibilityService.instance;
        if (svc == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return "এই কাজের জন্য ♿ কন্ট্রোল চালু করতে হবে — স্ক্রিনে NVN AI Voice Control চালু করে ফিরে এসো।";
        }
        boolean ok;
        switch (action) {
            case "back": ok = svc.goBack(); break;
            case "home": ok = svc.goHome(); break;
            case "recents": ok = svc.openRecents(); break;
            default: ok = svc.openNotifications(); break;
        }
        return ok ? "হয়ে গেছে।" : "করতে পারলাম না।";
    }

    private String askGemini(String q) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String key = prefs.getString("gemini_key", "");
        if (key.isEmpty()) {
            return "AI উত্তরের জন্য নিচে ফ্রি Gemini Key বসাও (aistudio.google.com)। Key ছাড়া আমি অ্যাপ খুলতে, volume/brightness বদলাতে, torch জ্বালাতে পারি।";
        }
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(30000);
            JSONObject part = new JSONObject();
            part.put("text", "You are Archer, a friendly AI voice assistant. Answer briefly (2-3 sentences) in the same language as the user (Bengali or English). User: " + q);
            JSONObject content = new JSONObject();
            content.put("parts", new JSONArray().put(part));
            JSONObject body = new JSONObject();
            body.put("contents", new JSONArray().put(content));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new InputStreamReader(
                    conn.getResponseCode() < 400 ? conn.getInputStream() : conn.getErrorStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            JSONObject res = new JSONObject(sb.toString());
            return res.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text").trim();
        } catch (Exception e) {
            return "দুঃখিত, উত্তর পাইনি। ইন্টারনেট আর Key ঠিক আছে কিনা দেখো।";
        }
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void onDestroy() {
        if (recognizer != null) recognizer.destroy();
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}
