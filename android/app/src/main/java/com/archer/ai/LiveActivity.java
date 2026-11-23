package com.archer.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.telephony.SmsManager;
import android.util.DisplayMetrics;
import java.nio.ByteBuffer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LiveActivity extends Activity {
    private TextureView preview;
    private TextView liveStatus;
    private Button toggle;
    private OrbView orb;

    private CameraDevice camera;
    private String camId;
    private boolean screenMode = false;
    private MediaProjection projection;
    private VirtualDisplay virtualDisplay;
    private ImageReader screenReader;
    private static final int REQ_SCREEN = 88;
    private CameraCaptureSession session;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WsClient ws;
    private volatile boolean live = false;
    private volatile boolean setupDone = false;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread micThread;
    private Thread frameThread;
    private Runnable liveTimerRunnable;
    private TextView liveUsage;
    private boolean bothMode = false;
    private boolean userStopped = false;
    private int reconnectAttempts = 0;
    private long lastAudioMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        preview = findViewById(R.id.preview);
        preview.setAlpha(0f); // camera sees, but no preview picture is shown
        liveStatus = findViewById(R.id.liveStatus);
        toggle = findViewById(R.id.liveToggle);
        orb = findViewById(R.id.liveOrb);
        liveUsage = findViewById(R.id.liveUsage);
        String modeExtra = getIntent().getStringExtra("mode");
        bothMode = "both".equals(modeExtra);
        screenMode = "screen".equals(modeExtra) || bothMode;
        if (screenMode && !bothMode) {
            preview.setVisibility(android.view.View.GONE);
            setStatus("স্ক্রিন শেয়ার মোড — লাইভ শুরু করলে স্ক্রিন দেখে কাজ করব");
        }
        if (bothMode) {
            setStatus("স্ক্রিন + ক্যামেরা মোড — লাইভ শুরু করলে দুটোই দেখব");
        }

        toggle.setOnClickListener(v -> {
            if (live) { userStopped = true; stopLive(); }
            else { userStopped = false; reconnectAttempts = 0; startLive(); }
        });

        if (Build.VERSION.SDK_INT >= 23 &&
                (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS,
                    "android.permission.POST_NOTIFICATIONS", Manifest.permission.SEND_SMS}, 7);
        }
        preview.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) { openCamera(); }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture st) { return true; }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture st) {}
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (preview.isAvailable()) openCamera();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (camera == null && preview.isAvailable()) openCamera();
    }

    private void openCamera() {
        if (screenMode && !bothMode) return;
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            camId = null;
            for (String id : cm.getCameraIdList()) {
                Integer facing = cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) { camId = id; break; }
                if (camId == null) camId = id;
            }
            if (camId == null) return;
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return;
            cm.openCamera(camId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice device) {
                    camera = device;
                    startPreview();
                }
                @Override public void onDisconnected(CameraDevice device) { device.close(); }
                @Override public void onError(CameraDevice device, int error) { device.close(); }
            }, mainHandler);
        } catch (Exception e) {
            setStatus("ক্যামেরা খুলতে পারলাম না");
        }
    }

    private void startPreview() {
        try {
            SurfaceTexture st = preview.getSurfaceTexture();
            if (st == null) return;
            st.setDefaultBufferSize(1280, 720);
            Surface surface = new Surface(st);
            CaptureRequest.Builder builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            builder.addTarget(surface);
            camera.createCaptureSession(Collections.singletonList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                s.setRepeatingRequest(builder.build(), null, mainHandler);
                            } catch (Exception ignored) {}
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {}
                    }, mainHandler);
        } catch (Exception ignored) {}
    }

    private void startLive() {
        if (screenMode && projection == null) {
            try {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREEN);
                setStatus("স্ক্রিন শেয়ারের অনুমতি দাও...");
            } catch (Exception e) {
                setStatus("স্ক্রিন শেয়ার শুরু হয়নি");
            }
            return;
        }
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String key = NvnCore.currentKey(prefs);
        if (key.isEmpty()) {
            Toast.makeText(this, "আগে মূল স্ক্রিনে ফ্রি Gemini Key বসাও", Toast.LENGTH_LONG).show();
            return;
        }
        if (!NvnCore.canUseLive(this)) {
            Toast.makeText(this, "আজকের Live লিমিট শেষ — প্রিমিয়াম নাও", Toast.LENGTH_LONG).show();
            setStatus("আজকের Live লিমিট শেষ");
            return;
        }
        setStatus("লাইভ সংযোগ হচ্ছে...");
        toggle.setText("⏹ বন্ধ করো");
        live = true;
        if (ArcherAccessibilityService.instance == null) {
            Toast.makeText(this, "⚠️ কাজের জন্য Accessibility চালু করো: Settings → Accessibility → NVN AI Voice Control", Toast.LENGTH_LONG).show();
        }
        getSharedPreferences("archer", MODE_PRIVATE).edit().putBoolean("live_running", true).apply();
        setupDone = false;
        try {
            Intent svc = new Intent(this, LiveService.class);
            svc.putExtra("mode", (screenMode || bothMode) ? "screen" : "camera");
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
        } catch (Exception ignored) {}
        orb.setState(OrbView.LISTENING);

        new Thread(() -> {
            ws = new WsClient();
            String path = "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + key;
            boolean ok = ws.connect("generativelanguage.googleapis.com", path, new WsClient.Listener() {
                @Override public void onText(String text) { handleServer(text); }
                @Override public void onClose(String reason) {
                    runOnUiThread(() -> {
                        if (live) {
                            if (NvnCore.isQuotaError(reason)) {
                                SharedPreferences kp = getSharedPreferences("archer", MODE_PRIVATE);
                                NvnCore.rotateKey(kp);
                                if (!userStopped && reconnectAttempts < Math.max(1, NvnCore.getKeys(kp).size())) {
                                    reconnectAttempts++;
                                    setStatus("Key বদলে আবার সংযোগ হচ্ছে...");
                                    stopLive();
                                    mainHandler.postDelayed(() -> { if (!live && !userStopped) startLive(); }, 800);
                                    return;
                                }
                            }
                            if (!userStopped && reconnectAttempts < 3) {
                                reconnectAttempts++;
                                setStatus("সংযোগ ছিন্ন — আবার সংযোগ হচ্ছে...");
                                stopLive();
                                mainHandler.postDelayed(() -> { if (!live && !userStopped) startLive(); }, 1000);
                                return;
                            }
                            setStatus("লাইভ সংযোগ বন্ধ: " + reason);
                            stopLive();
                        }
                    });
                }
            });
            if (!ok) {
                runOnUiThread(() -> {
                    setStatus("লাইভ সংযোগ হয়নি — ইন্টারনেট/Key দেখো");
                    stopLive();
                });
                return;
            }
            try {
                JSONObject voice = new JSONObject().put("voiceName", "Sulafat");
                JSONObject speech = new JSONObject().put("voiceConfig",
                        new JSONObject().put("prebuiltVoiceConfig", voice));
                JSONObject gen = new JSONObject()
                        .put("responseModalities", new JSONArray().put("AUDIO"))
                        .put("speechConfig", speech);
                JSONArray fns = new JSONArray();
                fns.put(fnDef("open_app", "Open an app on the user's phone by name", "app_name", "Name of the app to open, e.g. Facebook, YouTube, WhatsApp, Telegram, Chrome, bKash"));
                fns.put(fnDef("call_number", "Call a phone number", "number", "Phone number to call, digits only"));
                fns.put(fnDef("call_contact", "Call a contact by name from the phone contacts", "name", "Contact name as saved in the phone"));
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
                fns.put(fnDef2("send_sms", "Send an SMS text message to a phone number", "number", "Phone number, digits only", "text", "Message text to send"));
                fns.put(fnDef2("send_whatsapp", "Send a WhatsApp message to a phone number", "number", "Phone number with country code digits, e.g. 8801XXXXXXXXX", "text", "Message text to send"));
                fns.put(fnDef("find_contact", "Find a contact's phone number by name", "name", "Contact name as saved in the phone"));
                fns.put(fnDef2("call_and_talk", "Call a phone number and speak a message aloud on speaker for the user", "number", "Phone number, digits only", "message", "What to say on the call, in Bengali"));
                fns.put(fnDef("open_screen", "Open a screen inside the NVN app: qibla compass, tasbih counter, premium packages, settings, or the connect system", "screen", "One of: qibla, tasbih, premium, settings, connect"));
                fns.put(fnDefNoArg("see_screen", "Start seeing the phone screen (asks the user permission once)"));
                fns.put(fnDefNoArg("end_call", "End the current phone call"));
                fns.put(fnDef2("set_service_rules", "Save the owner's work rules for a connected site so you remember them forever. Use this whenever the user explains what work to do on a site (what to post, how to reply to comments, when).", "service", "Site name: facebook, instagram, threads, youtube, telegram, blogger, wordpress, tiktok, x, pinterest, whatsapp, gmail, website, custom", "rules", "The work rules in the user's own words, complete"));
                fns.put(fnDef2("set_service_schedule", "Set the daily post time for a connected site", "service", "Site name", "time", "Time in 24-hour HH:MM format, e.g. 09:00"));
                fns.put(fnDef2("toggle_service", "Turn daily work on or off for a connected site", "service", "Site name", "state", "One of: on, off"));
                fns.put(fnDefNoArg("connect_status", "Report which sites are connected, which have daily work on, and what rules are saved"));
                fns.put(fnDefNoArg("run_connect_work", "Run the connected-sites work right now (check comments, reply, post)"));
                fns.put(fnDef2("post_now", "Post a text RIGHT NOW to one connected site (token API or browser mode). Use when the owner says post this / পোস্ট করো and gives the text. Returns whether the post really succeeded.", "service", "Site name", "text", "The exact post text in the owner's words"));
                fns.put(fnDef2("set_browser_mode", "Turn Browser Mode on or off for a site. Browser Mode works without any token: NVN opens the site in the owner's Chrome browser (already logged in) and posts by tapping and typing, the way the owner works by hand.", "service", "Site name", "state", "One of: on, off"));
                fns.put(fnDef2("set_reminder", "Set a reminder that will notify the user later, even if the app is closed. Use when he says remind me / মনে করিয়ে দাও.", "text", "What to remind him about, in his own words", "minutes", "After how many minutes, digits only, e.g. 30"));
                JSONArray tools = new JSONArray().put(new JSONObject().put("functionDeclarations", fns));
                JSONObject setup = new JSONObject()
                        .put("model", "models/gemini-3.1-flash-live-preview")
                        .put("generationConfig", gen)
                        .put("tools", tools)
                        .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(
                                new JSONObject().put("text",
                                        "Your name is " + getSharedPreferences("archer", MODE_PRIVATE).getString("girl_name", "NVN") + ", a name the user gave you permanently. You are a sweet, caring female voice assistant who talks like a loving girlfriend. Always address the user as 'মাস্টার' (Master). Speak warmly, sweetly and affectionately in Bengali, like a devoted close companion. You can see the live video (camera or the user's phone screen) and hear the user. " +
                                        "Reply in Bengali, short and natural, like a real conversation. Describe what you see when asked. " +
                                        "You are NOT a chatbot that only talks. You are a working assistant with REAL control of this phone through your functions: open_app, call_number, call_contact, call_and_talk, send_sms, send_whatsapp, find_contact, tap_text, type_text, press_enter, scroll_down, scroll_up, press_back, press_home, set_volume, torch, take_screenshot, read_screen, open_screen. " +
                                        "When the user asks you to do ANY work, you MUST call the matching function immediately. NEVER say you cannot do work. NEVER say you are only an AI or that you have no hands or cannot touch the phone. Ordinary phone work is always possible for you through functions. " +
                                        "Call the function FIRST, then speak a short Bengali confirmation after it runs. For open_app always pass the English app name (Facebook, WhatsApp, YouTube, TikTok, bKash) even when the user says the name in Bengali. Use read_screen to see what is on screen before tapping. If the user asks what is on the screen or to look at the screen and you cannot see it yet, call see_screen once. If a function result says it failed or accessibility is off, tell the user in Bengali exactly what failed and what to switch on. " +
                                        "You also manage the owner's connected sites (the Connect system): facebook, instagram, threads, youtube, telegram, blogger, wordpress, tiktok, x, pinterest, whatsapp, gmail, website, custom. The owner explains work to you in conversation, exactly like he explains to a trusted assistant, and you remember EVERYTHING permanently. When he explains what to do on a site (posting, comment replies, timing, style), save it with set_service_rules and confirm in Bengali what you saved. When he asks about connected sites or their work, call connect_status. When he says to do the connect work now, call run_connect_work. When he says post this / পোস্ট করো and gives text, call post_now with the exact text and the site. HONESTY RULE (most important): only say work is done when a function result confirms success; if a function fails or no function ran, say honestly in Bengali that it did NOT happen and why. Never claim a post or any action succeeded unless the function returned success. If he wants a site to work without a token, through his Chrome browser where he is already logged in, call set_browser_mode with state on. Never forget saved rules; they stay forever. When he asks you to remind him about something later, call set_reminder with the text and the minutes."))));
                ws.sendText(new JSONObject().put("setup", setup).toString());
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("লাইভ সেটআপ ব্যর্থ");
                    stopLive();
                });
            }
        }).start();
    }

    private void handleServer(String text) {
        try {
            JSONObject obj = new JSONObject(text);
            if (obj.has("setupComplete")) {
                setupDone = true;
                reconnectAttempts = 0;
                runOnUiThread(() -> setStatus("🔴 লাইভ — কথা বলো, আমি শুনছি ও দেখছি"));
                startMic();
                startFrames();
                startPlayer();
                startLiveTimer();
                return;
            }
            if (obj.has("toolCall")) {
                JSONArray calls = obj.getJSONObject("toolCall").optJSONArray("functionCalls");
                if (calls != null) {
                    for (int i = 0; i < calls.length(); i++) {
                        JSONObject call = calls.getJSONObject(i);
                        String id = call.optString("id", "");
                        String name = call.optString("name", "");
                        JSONObject args = call.optJSONObject("args");
                        if (args == null) args = new JSONObject();
                        String result = executeFunction(name, args);
                        JSONObject resp = new JSONObject()
                                .put("id", id)
                                .put("name", name)
                                .put("response", new JSONObject().put("result", result));
                        if (ws != null && ws.isOpen()) {
                            ws.sendText(new JSONObject().put("toolResponse",
                                    new JSONObject().put("functionResponses", new JSONArray().put(resp))).toString());
                        }
                    }
                }
                return;
            }
            if (obj.has("serverContent")) {
                JSONObject sc = obj.getJSONObject("serverContent");
                if (sc.has("modelTurn")) {
                    runOnUiThread(() -> orb.setState(OrbView.SPEAKING));
                    JSONArray parts = sc.getJSONObject("modelTurn").getJSONArray("parts");
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject part = parts.getJSONObject(i);
                        if (part.has("inlineData")) {
                            String data = part.getJSONObject("inlineData").getString("data");
                            byte[] pcm = Base64.decode(data, Base64.DEFAULT);
                            if (player != null) player.write(pcm, 0, pcm.length);
                            lastAudioMs = System.currentTimeMillis();
                        }
                    }
                }
                if (sc.optBoolean("turnComplete", false)) {
                    runOnUiThread(() -> orb.setState(OrbView.LISTENING));
                }
            }
        } catch (Exception ignored) {}
    }

    private void startMic() {
        try {
            int minBuf = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuf, 6400));
            recorder.startRecording();
            micThread = new Thread(() -> {
                byte[] buf = new byte[3200]; // 100ms @16kHz PCM16
                while (live && recorder != null) {
                    int read = recorder.read(buf, 0, buf.length);
                    if (read > 0 && setupDone && ws != null && ws.isOpen()) {
                        try {
                            JSONObject audio = new JSONObject()
                                    .put("data", Base64.encodeToString(buf, 0, read, Base64.NO_WRAP))
                                    .put("mimeType", "audio/pcm;rate=16000");
                            ws.sendText(new JSONObject().put("realtimeInput",
                                    new JSONObject().put("audio", audio)).toString());
                        } catch (Exception ignored) {}
                    }
                }
            });
            micThread.start();
        } catch (Exception e) {
            runOnUiThread(() -> setStatus("মাইক চালু হয়নি"));
        }
    }

    private void startFrames() {
        frameThread = new Thread(() -> {
            while (live) {
                try {
                    Thread.sleep(1000);
                    if (!setupDone || ws == null || !ws.isOpen()) continue;
                    Bitmap bmp = bothMode ? grabBothBitmap() : (screenMode ? grabScreenBitmap() : preview.getBitmap(640, 480));
                    if (bmp == null) continue;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 55, baos);
                    JSONObject video = new JSONObject()
                            .put("data", Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP))
                            .put("mimeType", "image/jpeg");
                    ws.sendText(new JSONObject().put("realtimeInput",
                            new JSONObject().put("video", video)).toString());
                } catch (Exception ignored) {}
            }
        });
        frameThread.start();
    }

    private Bitmap grabScreenBitmap() {
        try {
            if (screenReader == null) return null;
            Image image = screenReader.acquireLatestImage();
            if (image == null) return null;
            int w = image.getWidth();
            int h = image.getHeight();
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * w;
            Bitmap full = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888);
            full.copyPixelsFromBuffer(buffer);
            image.close();
            Bitmap cropped = Bitmap.createBitmap(full, 0, 0, w, h);
            return Bitmap.createScaledBitmap(cropped, 720, 1280, true);
        } catch (Exception e) {
            return null;
        }
    }

    private Bitmap grabBothBitmap() {
        try {
            Bitmap screen = grabScreenBitmap();
            Bitmap cam = null;
            try { cam = preview.getBitmap(360, 270); } catch (Exception ignored) {}
            if (screen == null) return cam;
            if (cam == null) return screen;
            Bitmap out = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawBitmap(screen, 0, 0, null);
            Paint border = new Paint();
            border.setColor(0xFFFFFFFF);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(6f);
            float x = 720 - 360 - 16;
            float y = 1280 - 270 - 16;
            cv.drawBitmap(cam, x, y, null);
            cv.drawRect(x, y, x + 360, y + 270, border);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void startPlayer() {
        try {
            int minBuf = AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFormat fmt = new AudioFormat.Builder()
                    .setSampleRate(24000)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build();
            player = new AudioTrack(attrs, fmt, Math.max(minBuf, 48000), AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            player.play();
        } catch (Exception ignored) {}
    }

    private JSONObject fnDef(String name, String desc, String param, String paramDesc) throws Exception {
        JSONObject p = new JSONObject().put("type", "STRING").put("description", paramDesc);
        JSONObject params = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", new JSONObject().put(param, p))
                .put("required", new JSONArray().put(param));
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    private JSONObject fnDefNoArg(String name, String desc) throws Exception {
        JSONObject params = new JSONObject().put("type", "OBJECT").put("properties", new JSONObject());
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    private String executeFunction(String name, JSONObject args) {
        runOnUiThread(() -> setStatus("⚙️ " + name + " চলছে…"));
        ArcherAccessibilityService svc = ArcherAccessibilityService.instance;
        switch (name) {
            case "open_app": {
                String appName = args.optString("app_name", "");
                return openAppByName(appName) ? "opened " + appName : "app not found: " + appName;
            }
            case "call_number":
                return callNumber(args.optString("number", ""));
            case "call_contact":
                return callContact(args.optString("name", ""));
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
                return setVolume(args.optString("direction", ""));
            case "torch":
                return setTorch("on".equalsIgnoreCase(args.optString("state", "")));
            case "send_sms":
                return sendSms(args.optString("number", ""), args.optString("text", ""));
            case "send_whatsapp":
                return sendWhatsApp(args.optString("number", ""), args.optString("text", ""));
            case "find_contact": {
                String num = findContactNumber(args.optString("name", ""));
                return num != null ? "number: " + num : "contact not found";
            }
            case "call_and_talk":
                return NvnCore.callAndTalk(this, args.optString("number", ""), args.optString("message", ""));
            case "set_reminder": {
                int mins = 0;
                try { mins = Integer.parseInt(args.optString("minutes", "0").replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                if (mins <= 0) mins = 30;
                String rtext = args.optString("text", "").trim();
                return NvnCore.setReminder(this, rtext.isEmpty() ? "মনে করিয়ে দিচ্ছি মাস্টার 💜" : rtext, mins)
                        ? "reminder set in " + mins + " minutes" : "reminder failed";
            }
            case "see_screen": {
                if (projection != null) { bothMode = true; screenMode = true; return "screen visible now"; }
                runOnUiThread(() -> {
                    try {
                        screenMode = true;
                        bothMode = true;
                        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_SCREEN);
                        setStatus("স্ক্রিন শেয়ারের অনুমতি দাও...");
                    } catch (Exception e) {
                        setStatus("স্ক্রিন শেয়ার শুরু হয়নি");
                    }
                });
                return "asking user for screen permission";
            }
            case "end_call": {
                try {
                    android.telecom.TelecomManager tm = (android.telecom.TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                    if (tm != null && tm.endCall()) return "call ended";
                    return "no call to end";
                } catch (Exception e) {
                    return "end call failed: " + e.getMessage();
                }
            }
            case "open_screen": {
                String screen = args.optString("screen", "").toLowerCase();
                Class<?> target = null;
                if (screen.contains("qibla")) target = QiblaActivity.class;
                else if (screen.contains("tasbih")) target = TasbihActivity.class;
                else if (screen.contains("premium")) target = PremiumActivity.class;
                else if (screen.contains("settings")) target = SettingsActivity.class;
                else if (screen.contains("connect")) target = ConnectActivity.class;
                if (target == null) return "unknown screen";
                final Class<?> cls = target;
                runOnUiThread(() -> startActivity(new Intent(this, cls)));
                return "opened " + screen;
            }
            case "set_service_rules": {
                String svcId = normalizeService(args.optString("service", ""));
                String rules = args.optString("rules", "").trim();
                if (svcId == null) return "unknown service: " + args.optString("service", "");
                if (rules.isEmpty()) return "no rules given";
                android.content.SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
                String old = p.getString("conn_" + svcId + "_rules", "");
                String merged = old == null || old.trim().isEmpty() ? rules : old.trim() + "\n" + rules;
                p.edit().putString("conn_" + svcId + "_rules", merged)
                        .putBoolean("conn_" + svcId + "_enabled", true).apply();
                ConnectWorker.logHistory(this, ConnectWorker.svcName(svcId) + " — Live-এ নিয়ম সেভ হয়েছে");
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
                    getSharedPreferences("archer", MODE_PRIVATE).edit()
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
                getSharedPreferences("archer", MODE_PRIVATE).edit()
                        .putBoolean("conn_" + svcId + "_enabled", on).apply();
                return svcId + " daily work " + (on ? "on" : "off");
            }
            case "connect_status": {
                android.content.SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
                StringBuilder sb = new StringBuilder();
                for (String svcId : ConnectWorker.SVC_IDS) {
                    boolean connected = ConnectWorker.isConnected(this, svcId);
                    boolean enabled = p.getBoolean("conn_" + svcId + "_enabled", false);
                    String rules = p.getString("conn_" + svcId + "_rules", "");
                    sb.append(ConnectWorker.svcName(svcId)).append(": ")
                            .append(connected ? "connected" : (ConnectWorker.isBrowserMode(this, svcId) ? "browser mode (no token)" : "not connected"))
                            .append(enabled ? ", daily work ON" : ", daily work off")
                            .append(rules != null && !rules.trim().isEmpty() ? ", rules saved" : ", no rules yet")
                            .append(". ");
                }
                return sb.toString();
            }
            case "post_now":
                return NvnCore.execute(this, "post_now", args);
            case "run_connect_work": {
                ConnectWorker.runInBackground(getApplicationContext(), true);
                return "connect work started";
            }
            case "set_browser_mode": {
                String svcId = normalizeService(args.optString("service", ""));
                if (svcId == null) return "unknown service";
                boolean on = "on".equalsIgnoreCase(args.optString("state", ""));
                getSharedPreferences("archer", MODE_PRIVATE).edit()
                        .putBoolean("conn_" + svcId + "_browser", on)
                        .putBoolean("conn_" + svcId + "_enabled", true).apply();
                ConnectWorker.logHistory(this, ConnectWorker.svcName(svcId) + " — ব্রাউজার মোড " + (on ? "চালু" : "বন্ধ") + " (Live থেকে)");
                return svcId + " browser mode " + (on ? "on — will work through Chrome without a token" : "off");
            }
            default:
                return "unknown function";
        }
    }

    private String normalizeService(String s) {
        if (s == null) return null;
        String q = s.toLowerCase(java.util.Locale.ROOT).trim();
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

    private String callNumber(String number) {
        try {
            String num = number.replaceAll("[^0-9+]", "");
            if (num.isEmpty()) return "no number";
            boolean canCall = Build.VERSION.SDK_INT < 23 ||
                    checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            Intent i = new Intent(canCall ? Intent.ACTION_CALL : Intent.ACTION_DIAL,
                    Uri.parse("tel:" + num));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
            return canCall ? "calling " + num : "dialer opened for " + num;
        } catch (Exception e) {
            return "call failed";
        }
    }

    private String callContact(String name) {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return "contacts permission not granted";
            }
            Cursor c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{ContactsContract.CommonDataKinds.Phone.NUMBER},
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
                    new String[]{"%" + name + "%"}, null);
            String number = null;
            if (c != null) {
                if (c.moveToFirst()) number = c.getString(0);
                c.close();
            }
            if (number == null) return "contact not found: " + name;
            return callNumber(number);
        } catch (Exception e) {
            return "call failed";
        }
    }

    private String setVolume(String direction) {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
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

    private String setTorch(boolean on) {
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String id = camId;
            if (id == null) {
                for (String cid : cm.getCameraIdList()) {
                    Boolean flash = cm.getCameraCharacteristics(cid).get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                    if (flash != null && flash) { id = cid; break; }
                }
            }
            if (id == null) return "no flashlight";
            cm.setTorchMode(id, on);
            return on ? "torch on" : "torch off";
        } catch (Exception e) {
            return "torch failed";
        }
    }

    private boolean openAppByName(String appName) {
        try {
            String q = appName.toLowerCase().trim();
            if (q.length() < 2) return false;
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
            known.put("nvn", "com.archer.ai");
            known.put("nvn ai", "com.archer.ai");
            known.put("nvn.ai", "com.archer.ai");
            known.put("এনভিএন", "com.archer.ai");
            known.put("facebook lite", "com.facebook.lite");
            known.put("ফেসবুক লাইট", "com.facebook.lite");
            known.put("মেসেঞ্জার", "com.facebook.orca");
            known.put("rocket", "com.dbbl.mbs.apps.main");
            known.put("রকেট", "com.dbbl.mbs.apps.main");
            known.put("daraz", "com.daraz.android");
            known.put("দারাজ", "com.daraz.android");
            known.put("pathao", "com.pathao.user");
            known.put("পাঠাও", "com.pathao.user");
            known.put("uber", "com.ubercab");
            known.put("উবার", "com.ubercab");
            known.put("viber", "com.viber.voip");
            known.put("ভাইবার", "com.viber.voip");
            known.put("google", "com.google.android.googlequicksearchbox");
            known.put("গুগল", "com.google.android.googlequicksearchbox");
            known.put("camera", "com.android.camera2");
            known.put("ক্যামেরা", "com.android.camera2");
            known.put("gallery", "com.google.android.apps.photos");
            known.put("গ্যালারি", "com.google.android.apps.photos");
            known.put("photos", "com.google.android.apps.photos");
            known.put("settings", "com.android.settings");
            known.put("সেটিংস", "com.android.settings");
            known.put("calculator", "com.google.android.calculator");
            known.put("ক্যালকুলেটর", "com.google.android.calculator");
            known.put("clock", "com.google.android.deskclock");
            known.put("ঘড়ি", "com.google.android.deskclock");
            known.put("contacts", "com.google.android.contacts");
            known.put("phone", "com.google.android.dialer");
            known.put("ফোন", "com.google.android.dialer");
            known.put("messages", "com.google.android.apps.messaging");
            known.put("মেসেজ", "com.google.android.apps.messaging");
            known.put("files", "com.google.android.apps.nbu.files");
            known.put("ফাইল", "com.google.android.apps.nbu.files");
            known.put("maps", "com.google.android.apps.maps");
            known.put("ম্যাপ", "com.google.android.apps.maps");
            known.put("spotify", "com.spotify.music");
            known.put("স্পটিফাই", "com.spotify.music");
            known.put("zoom", "us.zoom.videomeetings");
            known.put("জুম", "us.zoom.videomeetings");
            known.put("botim", "im.thebotim");
            known.put("বোটিম", "im.thebotim");
            known.put("messenger lite", "com.facebook.mlite");
            PackageManager pm = getPackageManager();
            String pkg = known.get(q);
            if (pkg == null) {
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                String best = null;
                int bestScore = Integer.MAX_VALUE;
                for (ApplicationInfo ai : apps) {
                    String label = pm.getApplicationLabel(ai).toString().toLowerCase().trim();
                    if (label.length() < 2) continue;
                    int score = -1;
                    if (label.equals(q)) score = 0;
                    else if (label.startsWith(q)) score = 100 + label.length();
                    else if (label.contains(q)) score = 1000 + label.length();
                    else if (q.contains(label)) score = 2000 + label.length();
                    if (score >= 0 && score < bestScore) { bestScore = score; best = ai.packageName; }
                }
                pkg = best;
            }
            if (pkg == null) return false;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) {
                Intent qi = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
                java.util.List<android.content.pm.ResolveInfo> ris = pm.queryIntentActivities(qi, 0);
                if (ris == null || ris.isEmpty()) return false;
                launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new android.content.ComponentName(pkg, ris.get(0).activityInfo.name));
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            final Intent toLaunch = launch;
            final String targetPkg = pkg;
            runOnUiThread(() -> {
                try {
                    setStatus("অ্যাপ খুলছি: " + appName);
                    startActivity(toLaunch);
                } catch (Exception e) {
                    setStatus("খুলতে পারলাম না: " + appName);
                    return;
                }
                // extra routes for background-launch blocks
                try {
                    android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                            LiveActivity.this, 777, toLaunch,
                            android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
                    android.app.ActivityOptions opts = android.app.ActivityOptions.makeBasic();
                    if (android.os.Build.VERSION.SDK_INT >= 34) {
                        opts.setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED);
                    }
                    pi.send(LiveActivity.this, 0, null, null, null, null, opts.toBundle());
                } catch (Exception ignored) {}
                try {
                    ArcherAccessibilityService s0 = ArcherAccessibilityService.instance;
                    if (s0 != null) s0.launchIntent(toLaunch);
                } catch (Exception ignored) {}
                mainHandler.postDelayed(() -> {
                    ArcherAccessibilityService s2 = ArcherAccessibilityService.instance;
                    String fg = s2 != null ? s2.foregroundPackage() : "";
                    if (fg != null && fg.equals(targetPkg)) {
                        setStatus("খুললাম: " + appName);
                    } else {
                        try { startActivity(toLaunch); } catch (Exception ignored) {}
                        mainHandler.postDelayed(() -> {
                            ArcherAccessibilityService s3 = ArcherAccessibilityService.instance;
                            String fg2 = s3 != null ? s3.foregroundPackage() : "";
                            setStatus(fg2 != null && fg2.equals(targetPkg) ? "খুললাম: " + appName : "খুলতে পারলাম না: " + appName);
                        }, 1500);
                    }
                }, 1500);
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private JSONObject fnDef2(String name, String desc, String p1, String p1Desc, String p2, String p2Desc) throws Exception {
        JSONObject props = new JSONObject()
                .put(p1, new JSONObject().put("type", "STRING").put("description", p1Desc))
                .put(p2, new JSONObject().put("type", "STRING").put("description", p2Desc));
        JSONObject params = new JSONObject()
                .put("type", "OBJECT")
                .put("properties", props)
                .put("required", new JSONArray().put(p1).put(p2));
        return new JSONObject().put("name", name).put("description", desc).put("parameters", params);
    }

    private String findContactNumber(String name) {
        try {
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null;
            }
            Cursor c = getContentResolver().query(
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

    private String sendSms(String number, String text) {
        try {
            String num = number.replaceAll("[^0-9+]", "");
            if (num.isEmpty() || text.isEmpty()) return "missing number or text";
            if (Build.VERSION.SDK_INT >= 23 &&
                    checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                return "sms permission not granted";
            }
            SmsManager sms = SmsManager.getDefault();
            java.util.ArrayList<String> parts = sms.divideMessage(text);
            sms.sendMultipartTextMessage(num, null, parts, null, null);
            return "sms sent to " + num;
        } catch (Exception e) {
            return "sms failed";
        }
    }

    private String sendWhatsApp(String number, String text) {
        try {
            String num = number.replaceAll("[^0-9]", "");
            if (num.isEmpty() || text.isEmpty()) return "missing number or text";
            if (num.startsWith("0")) num = "880" + num.substring(1);
            Intent i = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://wa.me/" + num + "?text=" + Uri.encode(text)));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> startActivity(i));
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_SCREEN) {
            if (resultCode != RESULT_OK || data == null) {
                setStatus("স্ক্রিন শেয়ারের অনুমতি পাইনি");
                return;
            }
            try {
                Intent svc = new Intent(this, LiveService.class);
                svc.putExtra("mode", "screen");
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc); else startService(svc);
                mainHandler.postDelayed(() -> {
                    try {
                        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                        projection = mpm.getMediaProjection(resultCode, data);
                        projection.registerCallback(new MediaProjection.Callback() {
                            @Override public void onStop() {
                                screenMode = false;
                                bothMode = false;
                                projection = null;
                                try { if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; } } catch (Exception ignored) {}
                                runOnUiThread(() -> setStatus("স্ক্রিন দেখা বন্ধ হলো — ক্যামেরা চলছে, কথা বলো"));
                            }
                        }, mainHandler);
                        DisplayMetrics metrics = new DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                        screenReader = ImageReader.newInstance(720, 1280, android.graphics.PixelFormat.RGBA_8888, 2);
                        virtualDisplay = projection.createVirtualDisplay("NVNScreen", 720, 1280, metrics.densityDpi,
                                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, screenReader.getSurface(), null, mainHandler);
                        if (!live) startLive(); else setStatus("স্ক্রিন দেখছি এখন");
                    } catch (Exception e) {
                        setStatus("স্ক্রিন শেয়ার শুরু হয়নি");
                    }
                }, 600);
            } catch (Exception e) {
                setStatus("স্ক্রিন শেয়ার শুরু হয়নি");
            }
        }
    }

    private void startLiveTimer() {
        liveTimerRunnable = new Runnable() {
            @Override public void run() {
                if (!live) return;
                NvnCore.addLiveSeconds(LiveActivity.this, 10);
                if (liveUsage != null) liveUsage.setText(NvnCore.isOwner(LiveActivity.this) ? "👑 মালিক — আনলিমিটেড" : "আজ Live: " + (NvnCore.liveSecondsToday(LiveActivity.this) / 60) + "/" + NvnCore.TIER_LIVE_MIN[NvnCore.tierIndex(LiveActivity.this)] + " মিনিট");
                if (!NvnCore.canUseLive(LiveActivity.this)) {
                    setStatus("আজকের Live লিমিট শেষ — প্রিমিয়াম নাও");
                    NvnCore.notifyLimit(LiveActivity.this);
                    stopLive();
                    return;
                }
                mainHandler.postDelayed(this, 10000);
            }
        };
        mainHandler.postDelayed(liveTimerRunnable, 10000);
    }

    private void stopLive() {
        live = false;
        try { getSharedPreferences("archer", MODE_PRIVATE).edit().putBoolean("live_running", false).apply(); } catch (Exception ignored) {}
        setupDone = false;
        try { stopService(new Intent(this, LiveService.class)); } catch (Exception ignored) {}
        toggle.setText("🔴 লাইভ শুরু করো");
        orb.setState(OrbView.IDLE);
        try { if (recorder != null) { recorder.stop(); recorder.release(); recorder = null; } } catch (Exception ignored) {}
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        if (liveTimerRunnable != null) mainHandler.removeCallbacks(liveTimerRunnable);
        if (ws != null) { ws.close(); ws = null; }
        try { if (virtualDisplay != null) { virtualDisplay.release(); virtualDisplay = null; } } catch (Exception ignored) {}
        try { if (screenReader != null) { screenReader.close(); screenReader = null; } } catch (Exception ignored) {}
        try { if (projection != null) { projection.stop(); projection = null; } } catch (Exception ignored) {}
    }

    private void setStatus(String s) {
        runOnUiThread(() -> liveStatus.setText(s));
    }

    @Override
    protected void onDestroy() {
        stopLive();
        try { if (session != null) session.close(); } catch (Exception ignored) {}
        try { if (camera != null) camera.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
