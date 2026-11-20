package com.archer.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
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
    private CameraCaptureSession session;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private WsClient ws;
    private volatile boolean live = false;
    private volatile boolean setupDone = false;
    private AudioRecord recorder;
    private AudioTrack player;
    private Thread micThread;
    private Thread frameThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_live);
        preview = findViewById(R.id.preview);
        liveStatus = findViewById(R.id.liveStatus);
        toggle = findViewById(R.id.liveToggle);
        orb = findViewById(R.id.liveOrb);

        toggle.setOnClickListener(v -> {
            if (live) stopLive();
            else startLive();
        });

        if (Build.VERSION.SDK_INT >= 23 &&
                (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                 checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, 7);
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
        try {
            CameraManager cm = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String camId = null;
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
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String key = prefs.getString("gemini_key", "");
        if (key.isEmpty()) {
            Toast.makeText(this, "আগে মূল স্ক্রিনে ফ্রি Gemini Key বসাও", Toast.LENGTH_LONG).show();
            return;
        }
        setStatus("লাইভ সংযোগ হচ্ছে...");
        toggle.setText("⏹ বন্ধ করো");
        live = true;
        setupDone = false;
        orb.setState(OrbView.LISTENING);

        new Thread(() -> {
            ws = new WsClient();
            String path = "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=" + key;
            boolean ok = ws.connect("generativelanguage.googleapis.com", path, new WsClient.Listener() {
                @Override public void onText(String text) { handleServer(text); }
                @Override public void onClose(String reason) {
                    runOnUiThread(() -> {
                        if (live) {
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
                JSONObject appParam = new JSONObject()
                        .put("type", "STRING")
                        .put("description", "Name of the app to open, e.g. Facebook, YouTube, WhatsApp, Telegram, Chrome, bKash");
                JSONObject params = new JSONObject()
                        .put("type", "OBJECT")
                        .put("properties", new JSONObject().put("app_name", appParam))
                        .put("required", new JSONArray().put("app_name"));
                JSONObject fn = new JSONObject()
                        .put("name", "open_app")
                        .put("description", "Open an app on the user's phone by name")
                        .put("parameters", params);
                JSONArray tools = new JSONArray().put(new JSONObject()
                        .put("functionDeclarations", new JSONArray().put(fn)));
                JSONObject setup = new JSONObject()
                        .put("model", "models/gemini-3.1-flash-live-preview")
                        .put("generationConfig", gen)
                        .put("tools", tools)
                        .put("systemInstruction", new JSONObject().put("parts", new JSONArray().put(
                                new JSONObject().put("text",
                                        "You are NVN, a friendly voice assistant. You can see the live camera video and hear the user. " +
                                        "Reply in Bengali, short and natural, like a real conversation. Describe what you see when asked. " +
                                        "When the user asks you to open an app, call the open_app function with the app name."))));
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
                runOnUiThread(() -> setStatus("🔴 লাইভ — কথা বলো, আমি শুনছি ও দেখছি"));
                startMic();
                startFrames();
                startPlayer();
                return;
            }
            if (obj.has("toolCall")) {
                JSONArray calls = obj.getJSONObject("toolCall").optJSONArray("functionCalls");
                if (calls != null) {
                    for (int i = 0; i < calls.length(); i++) {
                        JSONObject call = calls.getJSONObject(i);
                        String id = call.optString("id", "");
                        String name = call.optString("name", "");
                        String result = "unknown function";
                        if ("open_app".equals(name)) {
                            String appName = call.optJSONObject("args") != null
                                    ? call.getJSONObject("args").optString("app_name", "") : "";
                            result = openAppByName(appName) ? "opened " + appName : "app not found: " + appName;
                        }
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
                    Bitmap bmp = preview.getBitmap(640, 480);
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

    private boolean openAppByName(String appName) {
        try {
            String q = appName.toLowerCase().trim();
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
            PackageManager pm = getPackageManager();
            String pkg = known.get(q);
            if (pkg == null) {
                List<ApplicationInfo> apps = pm.getInstalledApplications(0);
                for (ApplicationInfo ai : apps) {
                    String label = pm.getApplicationLabel(ai).toString().toLowerCase();
                    if (label.equals(q) || label.contains(q) || q.contains(label)) { pkg = ai.packageName; break; }
                }
            }
            if (pkg == null) return false;
            Intent launch = pm.getLaunchIntentForPackage(pkg);
            if (launch == null) return false;
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            runOnUiThread(() -> {
                setStatus("অ্যাপ খুলছি: " + appName);
                startActivity(launch);
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void stopLive() {
        live = false;
        setupDone = false;
        toggle.setText("🔴 লাইভ শুরু করো");
        orb.setState(OrbView.IDLE);
        try { if (recorder != null) { recorder.stop(); recorder.release(); recorder = null; } } catch (Exception ignored) {}
        try { if (player != null) { player.stop(); player.release(); player = null; } } catch (Exception ignored) {}
        if (ws != null) { ws.close(); ws = null; }
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
