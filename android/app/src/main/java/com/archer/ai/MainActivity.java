package com.archer.ai;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.RingtoneManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.StatFs;
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Base64;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
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
    private String lastAnswer = "";
    private boolean wakeWordOn = false;
    private MediaPlayer mediaPlayer;
    private MediaRecorder voiceRecorder;
    private boolean recording = false;
    private String voiceNotePath = "";

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
        KNOWN_APPS.put("imo", "com.imo.android.imoim"); KNOWN_APPS.put("facebook lite", "com.facebook.lite"); KNOWN_APPS.put("messenger lite", "com.facebook.mlite");
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
        Button settingsBtn = findViewById(R.id.settings);
        Button shareBtn = findViewById(R.id.share);
        Button shareAppBtn = findViewById(R.id.shareApp);

        settingsBtn.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        shareBtn.setOnClickListener(v -> shareText(lastAnswer.isEmpty() ? "NVN AI — আমার ভয়েস অ্যাসিস্ট্যান্ট" : lastAnswer));
        shareAppBtn.setOnClickListener(v -> shareText(
                "NVN AI অ্যাপ ডাউনলোড করো: https://github.com/mdirfanahmedrabbe-design/NVN.ai/releases/latest")); 

        liveBtn.setOnClickListener(v -> startActivity(new Intent(this, LiveActivity.class)));

        cameraBtn.setOnClickListener(v -> takePhoto());
        newsBtn.setOnClickListener(v -> fetchNews());
        accessBtn.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this, "NVN AI Voice Control চালু করো", Toast.LENGTH_LONG).show();
        });

        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        keyInput.setText(prefs.getString("gemini_key", "").trim());
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
                tts.setLanguage(new Locale("bn", "BD")); try { android.speech.tts.Voice best = null; for (android.speech.tts.Voice v : tts.getVoices()) { if (v.getLocale().getLanguage().equals("bn") && (best == null || v.getQuality() > best.getQuality())) best = v; } if (best != null) tts.setVoice(best); } catch (Exception ignored) {}
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
                    status.setText("ভয়েস ডায়ালগ খুলছি..."); openSpeechDialog();
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
                openSpeechDialog();
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

    private void openSpeechDialog() { try { Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, english ? "en-US" : "bn-BD"); i.putExtra(RecognizerIntent.EXTRA_PROMPT, "কথা বলো..."); startActivityForResult(i, 55); } catch (Exception e) { status.setText("এই ফোনে ভয়েস ইনপুট নেই — লিখে পাঠাও"); } } private void addMe(String text) {
        chat.append("\nতুমি: " + text + "\n");
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void addAi(String text) {
        chat.append("NVN: " + text + "\n");
        scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void reply(String text) {
        lastAnswer = text;
        addAi(text);
        if (ttsReady) {
            SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
            tts.setSpeechRate(prefs.getFloat("tts_speed", 1.0f));
            tts.setPitch(prefs.getFloat("tts_pitch", 1.0f));
            tts.setLanguage(english ? Locale.US : new Locale("bn", "BD"));
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "archer");
        }
    }

    private void shareText(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, text);
        startActivity(Intent.createChooser(i, "শেয়ার করো"));
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
        // prayer times
        if (s.contains("নামাজ") || s.contains("prayer") || s.contains("আজান")) {
            fetchPrayerTimes();
            return "নামাজের সময় আনছি...";
        }
        // alarm / timer
        if (s.contains("অ্যালার্ম") || s.contains("alarm")) {
            return setAlarm(s);
        }
        if (s.contains("টাইমার") || s.contains("timer")) {
            return setTimer(s);
        }
        // call / sms
        if (s.contains("কল করো") || s.contains("call")) {
            return makeCall(s);
        }
        if (s.contains("sms") || s.contains("মেসেজ পাঠাও")) {
            return sendSms(s);
        }
        // ===== daily tasks =====
        if (s.contains("আবহাওয়া") || s.contains("weather")) { fetchWeather(); return "আবহাওয়া আনছি..."; }
        if (s.contains("ডলার") || s.contains("dollar") || s.contains("রেট")) { fetchDollarRate(); return "ডলারের রেট আনছি..."; }
        if (s.contains("ক্রিকেট") || s.contains("cricket") || s.contains("স্কোর")) { fetchCricket(); return "ক্রিকেট স্কোর আনছি..."; }
        if (s.contains("সকালের ব্রিফ") || s.contains("morning brief") || s.contains("ব্রিফ")) { morningBrief(); return "ব্রিফ তৈরি করছি..."; }
        if (s.contains("হিসাব") && (s.contains("+") || s.contains("-") || s.contains("×") || s.contains("*") || s.contains("÷") || s.contains("/"))) {
            return calculate(s);
        }
        if (s.contains("অনুবাদ") || s.contains("translate")) {
            askGeminiAsync("Translate to " + (english ? "Bengali" : "English") + ", only the translation: " + s);
            return "অনুবাদ করছি...";
        }
        if (s.contains("টুডু") || s.contains("todo") || s.contains("কাজের লিস্ট")) return todoCommand(s);
        if (s.contains("মনে রাখো") || s.contains("remember")) return rememberCommand(s);
        if (s.contains("আমার নাম") && s.contains("কী")) {
            String n = getSharedPreferences("archer", MODE_PRIVATE).getString("user_name", "");
            return n.isEmpty() ? "তোমার নাম এখনো বলোনি। বলো: আমার নাম ... মনে রাখো" : "তোমার নাম " + n + "।";
        }
        // ===== phone control =====
        if (s.contains("ব্যাটারি") || s.contains("battery")) return batteryInfo();
        if (s.contains("স্টোরেজ") || s.contains("storage") || s.contains("ram") || s.contains("র‌্যাম")) return storageInfo();
        if (s.contains("wifi") || s.contains("ওয়াইফাই")) return wifiCommand(s);
        if (s.contains("ব্লুটুথ") || s.contains("bluetooth")) return bluetoothCommand(s);
        if (s.contains("ডিসটার্ব") || s.contains("dnd") || s.contains("do not disturb")) return dndCommand(s);
        if (s.contains("রোটেশন") || s.contains("rotation")) return rotationCommand(s);
        if (s.contains("স্ক্রিনশট") || s.contains("screenshot")) return accessibilityAction("screenshot");
        if (s.contains("স্ক্রিন পড়") || s.contains("স্ক্রিনে কী") || s.contains("read screen")) return readScreen();
        if (s.contains("ফোন বাজাও") || s.contains("ring") || s.contains("হারালে")) { ringPhone(); return "ফোন বাজাচ্ছি ৩০ সেকেন্ড..."; }
        if (s.contains("কপি করো") || s.contains("copy")) {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("NVN", lastAnswer));
            return "শেষ উত্তর কপি করলাম।";
        }
        // ===== smart / conversation =====
        if (s.contains("ব্যক্তিত্ব") || s.contains("personality")) return personalityCommand(s);
        if (s.contains("গল্প শোনাও") || s.contains("story")) { askGeminiAsync("Tell a short interesting story in Bengali (5-6 sentences)."); return "গল্প শোনাচ্ছি..."; }
        if (s.contains("কৌতুক") || s.contains("joke") || s.contains("হাসাও")) { askGeminiAsync("Tell a funny clean joke in Bengali."); return "কৌতুক শোনাচ্ছি..."; }
        if (s.contains("বাচ্চাদের") || s.contains("kids mode")) { askGeminiAsync("Talk like a friendly kids teacher in simple Bengali, fun and easy. Greet the child and offer to teach counting or rhymes."); return "বাচ্চাদের মোড চালু!"; }
        if (s.contains("ইংরেজি অনুশীলন") || s.contains("english practice")) { askGeminiAsync("Start English speaking practice with a Bengali learner. Ask one simple question in English, wait for answer, correct gently."); return "English practice শুরু!"; }
        if (s.contains("সারাংশ") || s.contains("summarize") || s.contains("summary")) { askGeminiAsync("Summarize in 2-3 Bengali sentences: " + s); return "সারাংশ করছি..."; }
        if (s.contains("ওয়েক") || s.contains("wake word") || s.contains("হে nvn") || s.contains("হে আর্চার")) {
            wakeWordOn = !wakeWordOn;
            return wakeWordOn ? "ওয়েক ওয়ার্ড চালু — অ্যাপ খোলা থাকলে \"হে NVN\" বললেই শুনব।" : "ওয়েক ওয়ার্ড বন্ধ।";
        }
        // ===== islamic =====
        if (s.contains("কুরআন") || s.contains("quran") || s.contains("তিলাওয়াত")) { quranCommand(s); return "কুরআন আনছি..."; }
        if (s.contains("দোয়া") || s.contains("dua")) return duaCommand();
        if (s.contains("হাদিস") || s.contains("hadith")) { askGeminiAsync("Share one authentic short hadith with Bengali translation and source."); return "হাদিস শোনাচ্ছি..."; }
        if (s.contains("কিবলা") || s.contains("qibla")) { startActivity(new Intent(this, QiblaActivity.class)); return "কিবলা কম্পাস খুললাম।"; }
        if (s.contains("তসবিহ") || s.contains("tasbih")) { startActivity(new Intent(this, TasbihActivity.class)); return "তসবিহ খুললাম।"; }
        if (s.contains("সাহরি") || s.contains("ইফতার") || s.contains("sehri") || s.contains("iftar")) { fetchPrayerTimes(); return "সাহরি/ইফতারের সময় আনছি..."; }
        // ===== office & accounts =====
        if (s.contains("ভয়েস নোট") || s.contains("voice note")) return voiceNoteCommand(s);
        if (s.contains("খরচ")) return expenseCommand(s);
        if (s.contains("ওভারটাইম")) return overtimeCalc(s);
        // ===== share / export / history =====
        if (s.contains("চ্যাট এক্সপোর্ট") || s.contains("export")) return exportChat();
        if (s.contains("হিস্ট্রি মুছ") || s.contains("clear history")) { chat.setText(""); return "চ্যাট মুছে দিলাম।"; }
        if (s.contains("শেয়ার করো") || s.contains("share")) { shareText(lastAnswer); return "শেয়ার স্ক্রিন খুললাম।"; }
        if (s.contains("আপডেট") || s.contains("update")) { checkUpdate(); return "আপডেট দেখছি..."; }
        // open app by name
        if (s.contains("খোলো") || s.contains("খুলো") || s.contains("open") || s.contains("চালু") || s.contains("চালাও")) {
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
            if (!label.isEmpty() && (s.contains(label) || label.contains(s.replace("খোলো", "").replace("open", "").trim()))) {
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
        super.onActivityResult(requestCode, resultCode, data); if (requestCode == 55 && resultCode == RESULT_OK && data != null) { ArrayList<String> list = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS); if (list != null && !list.isEmpty()) handle(list.get(0)); return; }
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
        String key = prefs.getString("gemini_key", "").trim();
        if (key.isEmpty()) {
            return "ছবি বুঝতে ফ্রি Gemini Key লাগবে — নিচে বসাও (aistudio.google.com)।";
        }
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
            String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
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
            if (res.has("error")) { return "Gemini error: " + res.getJSONObject("error").optString("message", "unknown"); } return res.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text").trim();
        } catch (Exception e) {
            return "ছবি বুঝতে পারলাম না। (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
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
            case "screenshot": ok = svc.takeScreenshot(); break;
            default: ok = svc.openNotifications(); break;
        }
        return ok ? "হয়ে গেছে।" : "করতে পারলাম না।";
    }

    private String extractNumber(String s) {
        StringBuilder num = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c) || c == '+') num.append(c);
            else if (c >= '০' && c <= '৯') num.append((char) ('0' + (c - '০')));
        }
        return num.toString();
    }

    private String makeCall(String s) {
        String num = extractNumber(s);
        if (num.length() < 5) return "নম্বরটা বলো — যেমন: কল করো 01XXXXXXXXX";
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + num)));
            } else {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + num)));
            }
            return num + " নম্বরে কল করছি।";
        } catch (Exception e) {
            return "কল করতে পারলাম না।";
        }
    }

    private String sendSms(String s) {
        String num = extractNumber(s);
        Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + num));
        startActivity(i);
        return "SMS স্ক্রিন খুললাম — লিখে পাঠাও।";
    }

    private int parseHour(String s) {
        String digits = extractNumber(s);
        if (digits.isEmpty()) return -1;
        int h = Integer.parseInt(digits.substring(0, Math.min(2, digits.length())));
        if ((s.contains("বিকাল") || s.contains("সন্ধ্যা") || s.contains("রাত") || s.contains("pm")) && h < 12) h += 12;
        if ((s.contains("দুপুর") || s.contains("noon")) && h < 12) h += 12;
        return h % 24;
    }

    private String setAlarm(String s) {
        int h = parseHour(s);
        if (h < 0) return "কয়টায় অ্যালার্ম দেব বলো — যেমন: সকাল ৭টায় অ্যালার্ম দাও।";
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
            i.putExtra(AlarmClock.EXTRA_HOUR, h);
            i.putExtra(AlarmClock.EXTRA_MINUTES, 0);
            i.putExtra(AlarmClock.EXTRA_MESSAGE, "NVN Alarm");
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            startActivity(i);
            return h + ":00-এ অ্যালার্ম দিলাম।";
        } catch (Exception e) {
            return "অ্যালার্ম দিতে পারলাম না।";
        }
    }

    private String setTimer(String s) {
        String digits = extractNumber(s);
        if (digits.isEmpty()) return "কত মিনিটের টাইমার বলো।";
        int mins = Integer.parseInt(digits);
        try {
            Intent i = new Intent(AlarmClock.ACTION_SET_TIMER);
            i.putExtra(AlarmClock.EXTRA_LENGTH, mins * 60);
            i.putExtra(AlarmClock.EXTRA_MESSAGE, "NVN Timer");
            i.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
            startActivity(i);
            return mins + " মিনিটের টাইমার দিলাম।";
        } catch (Exception e) {
            return "টাইমার দিতে পারলাম না।";
        }
    }

    private void fetchPrayerTimes() {
        status.setText("নামাজের সময় আনছি...");
        new Thread(() -> {
            String result;
            try {
                // Dhamrai coordinates, method 1 (Karachi)
                URL url = new URL("https://api.aladhan.com/v1/timings?latitude=23.92&longitude=90.21&method=1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(20000);
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject timings = new JSONObject(sb.toString())
                        .getJSONObject("data").getJSONObject("timings");
                result = "আজকের নামাজের সময় (ধামরাই):\n"
                        + "ফজর: " + timings.getString("Fajr") + "\n"
                        + "যোহর: " + timings.getString("Dhuhr") + "\n"
                        + "আসর: " + timings.getString("Asr") + "\n"
                        + "মাগরিব: " + timings.getString("Maghrib") + "\n"
                        + "এশা: " + timings.getString("Isha");
            } catch (Exception e) {
                result = "নামাজের সময় আনতে পারলাম না। ইন্টারনেট দেখো।";
            }
            String finalResult = result;
            runOnUiThread(() -> {
                reply(finalResult);
                status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
            });
        }).start();
    }

    private void askGeminiAsync(String prompt) {
        status.setText("ভাবছি...");
        new Thread(() -> {
            String ans = askGemini(prompt);
            runOnUiThread(() -> {
                reply(ans);
                status.setText("মাইক চেপে কথা বলো, বা লিখে পাঠাও");
            });
        }).start();
    }

    private String httpGet(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(20000);
        conn.setRequestProperty("User-Agent", "NVN-AI/1.0");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    private void fetchWeather() {
        new Thread(() -> {
            String result;
            try {
                String json = httpGet("https://api.open-meteo.com/v1/forecast?latitude=23.92&longitude=90.21&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FDhaka&forecast_days=1");
                JSONObject root = new JSONObject(json);
                JSONObject cur = root.getJSONObject("current");
                JSONObject daily = root.getJSONObject("daily");
                result = "ধামরাইয়ের আবহাওয়া এখন:\n"
                        + "তাপমাত্রা: " + cur.getDouble("temperature_2m") + "°C\n"
                        + "আর্দ্রতা: " + cur.getInt("relative_humidity_2m") + "%\n"
                        + "বাতাস: " + cur.getDouble("wind_speed_10m") + " km/h\n"
                        + "আজ সর্বোচ্চ: " + daily.getJSONArray("temperature_2m_max").getDouble(0) + "°C, সর্বনিম্ন: "
                        + daily.getJSONArray("temperature_2m_min").getDouble(0) + "°C\n"
                        + "বৃষ্টির সম্ভাবনা: " + daily.getJSONArray("precipitation_probability_max").optInt(0, 0) + "%";
            } catch (Exception e) {
                result = "আবহাওয়া আনতে পারলাম না। ইন্টারনেট দেখো।";
            }
            String fr = result;
            runOnUiThread(() -> reply(fr));
        }).start();
    }

    private void fetchDollarRate() {
        new Thread(() -> {
            String result;
            try {
                String json = httpGet("https://open.er-api.com/v6/latest/USD");
                double bdt = new JSONObject(json).getJSONObject("rates").getDouble("BDT");
                result = "আজ ১ ডলার = " + String.format(Locale.US, "%.2f", bdt) + " টাকা।";
            } catch (Exception e) {
                result = "ডলারের রেট আনতে পারলাম না।";
            }
            String fr = result;
            runOnUiThread(() -> reply(fr));
        }).start();
    }

    private void fetchCricket() {
        new Thread(() -> {
            String result;
            try {
                String xml = httpGet("https://www.espncricinfo.com/rss/content/story/feeds/0.xml");
                StringBuilder out = new StringBuilder("ক্রিকেট খবর:\n");
                int idx = 0, count = 0;
                while (count < 3) {
                    int start = xml.indexOf("<item>", idx);
                    if (start < 0) break;
                    int tStart = xml.indexOf("<title>", start);
                    int tEnd = xml.indexOf("</title>", tStart);
                    if (tStart < 0 || tEnd < 0) break;
                    out.append("• ").append(xml.substring(tStart + 7, tEnd).replace("<![CDATA[", "").replace("]]>", "").trim()).append("\n");
                    idx = tEnd;
                    count++;
                }
                result = count > 0 ? out.toString().trim() : "লাইভ স্কোর এখন পাচ্ছি না।";
            } catch (Exception e) {
                result = "ক্রিকেট স্কোর আনতে পারলাম না।";
            }
            String fr = result;
            runOnUiThread(() -> reply(fr));
        }).start();
    }

    private void morningBrief() {
        reply("সকালের ব্রিফ তৈরি করছি — আবহাওয়া, খবর আর নামাজের সময় একসাথে দিচ্ছি।");
        fetchWeather();
        fetchNews();
        fetchPrayerTimes();
    }

    private String calculate(String s) {
        try {
            String expr = s.replaceAll("[^0-9+\\-*/×÷(). ]", "").replace("×", "*").replace("÷", "/").trim();
            if (expr.isEmpty()) return "হিসাবটা বলো — যেমন: হিসাব 25+40";
            double val = new Object() {
                int pos = -1, ch;
                void next() { ch = (++pos < expr.length()) ? expr.charAt(pos) : -1; }
                boolean eat(int c) { while (ch == ' ') next(); if (ch == c) { next(); return true; } return false; }
                double parse() { next(); double v = parseExpr(); return v; }
                double parseExpr() { double v = parseTerm(); for (;;) { if (eat('+')) v += parseTerm(); else if (eat('-')) v -= parseTerm(); else return v; } }
                double parseTerm() { double v = parseFactor(); for (;;) { if (eat('*')) v *= parseFactor(); else if (eat('/')) v /= parseFactor(); else return v; } }
                double parseFactor() {
                    if (eat('+')) return parseFactor();
                    if (eat('-')) return -parseFactor();
                    double v; int start = pos;
                    if (eat('(')) { v = parseExpr(); eat(')'); }
                    else { while ((ch >= '0' && ch <= '9') || ch == '.') next(); v = Double.parseDouble(expr.substring(start, pos)); }
                    return v;
                }
            }.parse();
            return expr + " = " + (val == Math.floor(val) ? String.valueOf((long) val) : String.valueOf(val));
        } catch (Exception e) {
            return "হিসাবটা বুঝতে পারিনি।";
        }
    }

    private String todoCommand(String s) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String list = prefs.getString("todo_list", "");
        if (s.contains("যোগ") || s.contains("add")) {
            String item = s.replace("টুডু", "").replace("যোগ", "").replace("করো", "").trim();
            if (item.isEmpty()) return "কী যোগ করব বলো।";
            prefs.edit().putString("todo_list", list + "• " + item + "\n").apply();
            return "টুডুতে যোগ করলাম: " + item;
        }
        if (s.contains("মুছ") || s.contains("clear") || s.contains("শেষ")) {
            prefs.edit().putString("todo_list", "").apply();
            return "টুডু লিস্ট মুছে দিলাম।";
        }
        return list.isEmpty() ? "টুডু লিস্ট খালি। বলো: টুডু যোগ করো ..." : "তোমার টুডু লিস্ট:\n" + list.trim();
    }

    private String rememberCommand(String q) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String lower = q.toLowerCase(Locale.ROOT);
        if (lower.contains("নাম")) {
            String name = q.replaceAll("(?i)আমার নাম|মনে রাখো|remember|my name is", "").trim();
            if (!name.isEmpty()) {
                prefs.edit().putString("user_name", name).apply();
                return "মনে রাখলাম — তোমার নাম " + name + "।";
            }
        }
        String fact = q.replaceAll("(?i)মনে রাখো|remember", "").trim();
        String mem = prefs.getString("memory_facts", "");
        prefs.edit().putString("memory_facts", mem + fact + "; ").apply();
        return "মনে রাখলাম।";
    }

    private String batteryInfo() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = registerReceiver(null, filter);
        int level = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
        int scale = battery != null ? battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100) : 100;
        int pct = scale > 0 ? (level * 100 / scale) : -1;
        return "ব্যাটারি আছে " + pct + "%।";
    }

    private String storageInfo() {
        try {
            StatFs stat = new StatFs(Environment.getDataDirectory().getPath());
            long total = stat.getTotalBytes() / (1024 * 1024 * 1024);
            long free = stat.getAvailableBytes() / (1024 * 1024 * 1024);
            ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(mi);
            long ramTotal = mi.totalMem / (1024 * 1024);
            long ramFree = mi.availMem / (1024 * 1024);
            return "স্টোরেজ: মোট " + total + " GB, খালি " + free + " GB। RAM: মোট " + ramTotal + " MB, খালি " + ramFree + " MB।";
        } catch (Exception e) {
            return "তথ্য পাইনি।";
        }
    }

    private String wifiCommand(String s) {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (Build.VERSION.SDK_INT >= 29) {
                startActivity(new Intent(Settings.Panel.ACTION_WIFI));
                return "Wi-Fi প্যানেল খুললাম — সেখান থেকে চালু/বন্ধ করো।";
            }
            boolean on = !(s.contains("বন্ধ") || s.contains("off"));
            wm.setWifiEnabled(on);
            return on ? "Wi-Fi চালু করলাম।" : "Wi-Fi বন্ধ করলাম।";
        } catch (Exception e) {
            return "Wi-Fi বদলাতে পারলাম না।";
        }
    }

    private String bluetoothCommand(String s) {
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt == null) return "এই ফোনে ব্লুটুথ পাইনি।";
            boolean on = !(s.contains("বন্ধ") || s.contains("off"));
            if (on) {
                startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
                return "ব্লুটুথ চালু করতে Allow চাপো।";
            }
            bt.disable();
            return "ব্লুটুথ বন্ধ করলাম।";
        } catch (Exception e) {
            return "ব্লুটুথ বদলাতে পারলাম না।";
        }
    }

    private String dndCommand(String s) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (!nm.isNotificationPolicyAccessGranted()) {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                return "DND-র জন্য একবার পারমিশন দাও — স্ক্রিনে Allow করে ফিরে এসো।";
            }
            boolean on = !(s.contains("বন্ধ") || s.contains("off"));
            nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_NONE : NotificationManager.INTERRUPTION_FILTER_ALL);
            return on ? "ডিসটার্ব না করুন চালু।" : "ডিসটার্ব না করুন বন্ধ।";
        } catch (Exception e) {
            return "DND বদলাতে পারলাম না।";
        }
    }

    private String rotationCommand(String s) {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.System.canWrite(this)) {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                return "রোটেশনের জন্য একবার পারমিশন দাও।";
            }
            boolean on = !(s.contains("বন্ধ") || s.contains("off") || s.contains("লক"));
            Settings.System.putInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, on ? 1 : 0);
            return on ? "অটো রোটেশন চালু।" : "রোটেশন লক করলাম।";
        } catch (Exception e) {
            return "রোটেশন বদলাতে পারলাম না।";
        }
    }

    private String readScreen() {
        ArcherAccessibilityService svc = ArcherAccessibilityService.instance;
        if (svc == null) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            return "স্ক্রিন পড়তে ♿ কন্ট্রোল চালু করতে হবে।";
        }
        AccessibilityNodeInfo root = svc.getRootInActiveWindow();
        if (root == null) return "স্ক্রিনে কিছু পাইনি।";
        StringBuilder sb = new StringBuilder();
        collectText(root, sb);
        String text = sb.toString().trim();
        return text.isEmpty() ? "স্ক্রিনে লেখা পাইনি।" : "স্ক্রিনে আছে: " + (text.length() > 500 ? text.substring(0, 500) : text);
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) sb.append(node.getText()).append(" ");
        for (int i = 0; i < node.getChildCount(); i++) collectText(node.getChild(i), sb);
    }

    private void ringPhone() {
        try {
            AudioManager am = (AudioManager) getSystemService(AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, uri);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();
            new android.os.Handler().postDelayed(() -> {
                try { if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.release(); mediaPlayer = null; } } catch (Exception ignored) {}
            }, 30000);
        } catch (Exception ignored) {}
    }

    private String personalityCommand(String s) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String p = "বন্ধু";
        if (s.contains("শিক্ষক")) p = "শিক্ষক";
        else if (s.contains("গল্পকার")) p = "গল্পকার";
        else if (s.contains("ইসলামিক")) p = "ইসলামিক সহায়ক";
        prefs.edit().putString("personality", p).apply();
        return "ঠিক আছে — এখন থেকে আমি তোমার " + p + " হিসেবে কথা বলব।";
    }

    private void quranCommand(String s) {
        new Thread(() -> {
            String result;
            try {
                String digits = extractNumber(s);
                int ayah = digits.isEmpty() ? 1 : Integer.parseInt(digits);
                String json = httpGet("https://api.alquran.cloud/v1/ayah/" + ayah + "/editions/quran-uthmani,bn.bengali");
                JSONArray data = new JSONObject(json).getJSONArray("data");
                String ar = data.getJSONObject(0).getString("text");
                String bn = data.getJSONObject(1).getString("text");
                result = "আয়াত " + ayah + ":\n" + ar + "\nঅনুবাদ: " + bn;
            } catch (Exception e) {
                result = "কুরআনের আয়াত আনতে পারলাম না। নম্বর বলো — যেমন: কুরআন 255";
            }
            String fr = result;
            runOnUiThread(() -> reply(fr));
        }).start();
    }

    private String duaCommand() {
        String[] duas = {
            "আল্লাহুম্মা ইন্নি আসআলুকাল জান্নাতা ওয়া আউযুবিকা মিনান নার। অর্থ: হে আল্লাহ, আমি আপনার কাছে জান্নাত চাই এবং জাহান্নাম থেকে আশ্রয় চাই।",
            "রব্বানা আতিনা ফিদ্দুনিয়া হাসানাতাওঁ ওয়া ফিল আখিরাতি হাসানাতাওঁ ওয়া কিনা আযাবান নার। অর্থ: হে আমাদের রব, আমাদের দুনিয়ায় কল্যাণ দিন, আখিরাতেও কল্যাণ দিন, আর জাহান্নামের আজাব থেকে বাঁচান।",
            "আল্লাহুম্মা আফিনি ফি বাদানি, আল্লাহুম্মা আফিনি ফি সাময়ি, আল্লাহুম্মা আফিনি ফি বাসারি। অর্থ: হে আল্লাহ, আমার শরীরে, শ্রবণে ও দৃষ্টিতে সুস্থতা দিন।"
        };
        return duas[(int) (System.currentTimeMillis() / 1000 % duas.length)];
    }

    private String voiceNoteCommand(String s) {
        try {
            if (!recording) {
                voiceNotePath = getExternalFilesDir(null) + "/voicenote.m4a";
                voiceRecorder = new MediaRecorder();
                voiceRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
                voiceRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
                voiceRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
                voiceRecorder.setOutputFile(voiceNotePath);
                voiceRecorder.prepare();
                voiceRecorder.start();
                recording = true;
                return "ভয়েস নোট রেকর্ড হচ্ছে... থামাতে আবার বলো: ভয়েস নোট";
            }
            voiceRecorder.stop();
            voiceRecorder.release();
            voiceRecorder = null;
            recording = false;
            return "ভয়েস নোট সেভ হয়েছে। শুনতে বলো: ভয়েস নোট শোনাও";
        } catch (Exception e) {
            recording = false;
            return "ভয়েস নোট হয়নি।";
        }
    }

    private String expenseCommand(String s) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        if (s.contains("যোগ") || s.contains("খরচ করলাম")) {
            String digits = extractNumber(s);
            if (digits.isEmpty()) return "কত টাকা খরচ বলো।";
            int amount = Integer.parseInt(digits);
            int total = prefs.getInt("expense_total", 0) + amount;
            prefs.edit().putInt("expense_total", total).apply();
            return amount + " টাকা যোগ হলো। মোট খরচ: " + total + " টাকা।";
        }
        if (s.contains("মুছ") || s.contains("রিসেট")) {
            prefs.edit().putInt("expense_total", 0).apply();
            return "খরচের হিসাব মুছে দিলাম।";
        }
        return "মোট খরচ: " + prefs.getInt("expense_total", 0) + " টাকা। যোগ করতে বলো: খরচ যোগ করো 100";
    }

    private String overtimeCalc(String s) {
        String digits = extractNumber(s);
        if (digits.isEmpty()) return "ওভারটাইমের ঘণ্টা বলো — যেমন: ওভারটাইম 10 ঘণ্টা";
        int hours = Integer.parseInt(digits);
        int rate = getSharedPreferences("archer", MODE_PRIVATE).getInt("overtime_rate", 100);
        return hours + " ঘণ্টা ওভারটাইম × " + rate + " টাকা = " + (hours * rate) + " টাকা। (রেট বদলাতে Settings-এ যাও)";
    }

    private String exportChat() {
        try {
            File f = new File(getExternalFilesDir(null), "nvn-chat.txt");
            FileWriter w = new FileWriter(f);
            w.write(chat.getText().toString());
            w.close();
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, chat.getText().toString());
            startActivity(Intent.createChooser(i, "চ্যাট শেয়ার করো"));
            return "চ্যাট এক্সপোর্ট করে শেয়ার স্ক্রিন খুললাম।";
        } catch (Exception e) {
            return "এক্সপোর্ট হয়নি।";
        }
    }

    private void checkUpdate() {
        new Thread(() -> {
            String result;
            try {
                String json = httpGet("https://api.github.com/repos/mdirfanahmedrabbe-design/NVN.ai/releases/latest");
                String tag = new JSONObject(json).optString("tag_name", "");
                result = tag.isEmpty() ? "আপডেটের খবর পাইনি।"
                        : "সর্বশেষ ভার্সন: " + tag + "। ডাউনলোড: github.com/mdirfanahmedrabbe-design/NVN.ai/releases";
            } catch (Exception e) {
                result = "আপডেট দেখতে পারলাম না।";
            }
            String fr = result;
            runOnUiThread(() -> reply(fr));
        }).start();
    }

    private String askGemini(String q) {
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String key = prefs.getString("gemini_key", "").trim();
        if (key.isEmpty()) {
            return "AI উত্তরের জন্য নিচে ফ্রি Gemini Key বসাও (aistudio.google.com)। Key ছাড়া আমি অ্যাপ খুলতে, volume/brightness বদলাতে, torch জ্বালাতে পারি।";
        }
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + key);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            SharedPreferences mprefs = getSharedPreferences("archer", MODE_PRIVATE);
            String personality = mprefs.getString("personality", "বন্ধু");
            String userName = mprefs.getString("user_name", "");
            String memFacts = mprefs.getString("memory_facts", "");
            String sys = "You are NVN, a friendly AI voice assistant with personality: " + personality
                    + ". Answer briefly (2-3 sentences) in the same language as the user (Bengali, English, or mixed Banglish — understand mixed). "
                    + (userName.isEmpty() ? "" : "User's name: " + userName + ". ")
                    + (memFacts.isEmpty() ? "" : "Things to remember about user: " + memFacts + " ")
                    + "Be warm and match the user's emotion. User: ";
            JSONObject part = new JSONObject();
            part.put("text", sys + q);
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
            if (res.has("error")) { return "Gemini error: " + res.getJSONObject("error").optString("message", "unknown"); } return res.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                    .getString("text").trim();
        } catch (Exception e) {
            return "দুঃখিত, উত্তর পাইনি। (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")";
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
