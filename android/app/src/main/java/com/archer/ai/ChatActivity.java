package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

// Text chat with NVN — the owner writes like he writes to his trusted assistant,
// and she does the work: phone tasks, site connects (secure dialog inside chat),
// rules, browser mode — everything, remembered permanently.
public class ChatActivity extends Activity {

    private LinearLayout msgBox;
    private ScrollView scroll;
    private EditText input;
    private LinearLayout workCard;
    private TextView workText;
    private ImageView workShot;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastStatus = "";
    private long lastShotTs = 0;
    private boolean busy = false;
    private boolean polling = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        msgBox = findViewById(R.id.msgBox);
        scroll = findViewById(R.id.chatScroll);
        input = findViewById(R.id.chatInput);
        workCard = findViewById(R.id.workCard);
        workText = findViewById(R.id.workText);
        workShot = findViewById(R.id.workShot);

        loadHistory();

        // App init (chat is now the launcher screen)
        try { ConnectWorker.scheduleAll(getApplicationContext()); } catch (Exception ignored) {}
        try {
            if (getSharedPreferences("archer", MODE_PRIVATE).getBoolean("bubble_on", false)) {
                startService(new Intent(this, BubbleService.class));
            }
        } catch (Exception ignored) {}
        fetchServerConfig();
        if (getSharedPreferences("archer", MODE_PRIVATE).getString("girl_name", "").isEmpty()) showNameDialog();

        // WhatsApp-style header: ball (orb) on top only, ⋮ menu
        findViewById(R.id.chatBack).setOnClickListener(v -> finish());
        findViewById(R.id.chatMenu).setOnClickListener(this::showChatMenu);
        findViewById(R.id.chatCamera).setOnClickListener(v -> openLive("camera"));
        findViewById(R.id.chatEmoji).setOnClickListener(v -> {
            input.append("😊");
            input.requestFocus();
        });
        findViewById(R.id.chatAttach).setOnClickListener(this::showAttachMenu);

        // Mic/send button: 🎤 when empty (opens Live voice), ➤ when text present
        TextView sendBtn = findViewById(R.id.chatSend);
        sendBtn.setOnClickListener(v -> {
            if (input.getText().toString().trim().isEmpty()) openLive("voice");
            else sendMsg();
        });
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {
                sendBtn.setText(s.toString().trim().isEmpty() ? "🎤" : "➤");
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(workPoll);
    }

    @Override
    protected void onPause() {
        polling = false;
        handler.removeCallbacks(workPoll);
        super.onPause();
    }

    // ---------- browser card (live work view) ----------
    private final Runnable workPoll = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            try {
                SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
                String status = p.getString("conn_work_status", "");
                long ts = p.getLong("conn_work_shot_ts", 0);
                if (!status.isEmpty() && !status.equals(lastStatus)) {
                    lastStatus = status;
                    workCard.setVisibility(View.VISIBLE);
                    workText.setText(status);
                }
                if (ts > lastShotTs) {
                    lastShotTs = ts;
                    File f = new File(getFilesDir(), "browser_shot.png");
                    if (f.exists()) {
                        workCard.setVisibility(View.VISIBLE);
                        workShot.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
                        workShot.setVisibility(View.VISIBLE);
                    }
                }
            } catch (Exception ignored) {}
            handler.postDelayed(this, 2000);
        }
    };

    // ---------- history ----------
    private JSONArray msgs() {
        try {
            return new JSONArray(getSharedPreferences("archer", MODE_PRIVATE).getString("chat_msgs", "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void saveMsg(String role, String text) {
        try {
            JSONArray a = msgs();
            a.put(new JSONObject().put("r", role).put("t", text));
            while (a.length() > 200) a.remove(0);
            SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
            String hist = p.getString("chat_history", "");
            String line = ("user".equals(role) ? "তুমি: " : "NVN: ") + text + "\n";
            hist = hist + line;
            if (hist.length() > 8000) hist = hist.substring(hist.length() - 8000);
            p.edit().putString("chat_msgs", a.toString()).putString("chat_history", hist).apply();
        } catch (Exception ignored) {}
    }

    private void loadHistory() {
        JSONArray a = msgs();
        if (a.length() == 0) {
            String girl = getSharedPreferences("archer", MODE_PRIVATE).getString("girl_name", "NVN");
            addBubble(false, "আসসালামু আলাইকুম মাস্টার! 💜 আমি " + girl + "। এখানে লিখে যা বলবেন, আমি তাই করবো — অ্যাপ খোলা, ফোন, মেসেজ, সাইট কানেক্ট করা, পোস্ট করা — সবকিছু।");
            return;
        }
        for (int i = 0; i < a.length(); i++) {
            JSONObject m = a.optJSONObject(i);
            if (m == null) continue;
            addBubble("user".equals(m.optString("r")), m.optString("t", ""));
        }
    }

    private void addBubble(boolean mine, String text) {
        TextView tv = new TextView(this);
        String time = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(new java.util.Date());
        String stamp = "  " + time + (mine ? " ✓✓" : "");
        android.text.SpannableString ss = new android.text.SpannableString(text + stamp);
        ss.setSpan(new android.text.style.RelativeSizeSpan(0.72f), text.length(), text.length() + stamp.length(), 0);
        ss.setSpan(new android.text.style.ForegroundColorSpan(mine ? 0xFF9FD8C9 : 0xFF8696A0),
                text.length(), text.length() + stamp.length(), 0);
        tv.setText(ss);
        tv.setTextSize(15);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int) (12 * d), (int) (9 * d), (int) (12 * d), (int) (9 * d));
        if (mine) {
            tv.setBackgroundResource(R.drawable.bubble_mine);
            tv.setTextColor(0xFFFFFFFF);
        } else {
            tv.setBackgroundResource(R.drawable.bubble_her);
            tv.setTextColor(0xFFF3E9C8);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.bottomMargin = (int) (8 * d);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78);
        tv.setMaxWidth(maxW);
        tv.setLayoutParams(lp);
        msgBox.addView(tv);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private void openLive(String mode) {
        Intent i = new Intent(this, LiveActivity.class);
        i.putExtra("mode", mode);
        i.putExtra("autostart", true);
        startActivity(i);
    }

    // WhatsApp-style ⋮ menu: everything else lives here
    private void showChatMenu(View anchor) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add(0, 8, 0, "🎙 Live (ভয়েস)");
        pm.getMenu().add(0, 9, 1, "📹 Live (ক্যামেরা)");
        pm.getMenu().add(0, 10, 2, "🏠 ড্যাশবোর্ড");
        pm.getMenu().add(0, 1, 3, "⚙️ Settings");
        pm.getMenu().add(0, 2, 4, "🔗 সাইট কানেক্ট");
        pm.getMenu().add(0, 3, 5, "🔑 API Keys");
        pm.getMenu().add(0, 4, 6, "👑 Premium");
        pm.getMenu().add(0, 5, 7, "🌐 ব্রাউজার");
        pm.getMenu().add(0, 6, 8, "🗑 চ্যাট মুছুন");
        pm.getMenu().add(0, 7, 9, "ℹ️ About");
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 8: openLive("voice"); return true;
                case 9: openLive("camera"); return true;
                case 10: startActivity(new Intent(this, MainActivity.class)); return true;
                case 1: startActivity(new Intent(this, SettingsActivity.class)); return true;
                case 2: startActivity(new Intent(this, ConnectActivity.class)); return true;
                case 3: startActivity(new Intent(this, KeysActivity.class)); return true;
                case 4: startActivity(new Intent(this, PremiumActivity.class)); return true;
                case 5: BrowserActivity.open(this, ""); return true;
                case 6:
                    getSharedPreferences("archer", MODE_PRIVATE).edit()
                            .remove("chat_msgs").remove("chat_history").apply();
                    msgBox.removeAllViews();
                    loadHistory();
                    return true;
                case 7: startActivity(new Intent(this, AboutActivity.class)); return true;
            }
            return false;
        });
        pm.show();
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

    private String httpGet(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(c.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- attach: photo / video / file (WhatsApp-style) ----------
    private static final int REQ_IMAGE = 7001;
    private static final int REQ_VIDEO = 7002;
    private static final int REQ_FILE = 7003;

    private void showAttachMenu(View anchor) {
        android.widget.PopupMenu pm = new android.widget.PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "🖼 ছবি (Gallery)");
        pm.getMenu().add(0, 2, 1, "🎬 ভিডিও");
        pm.getMenu().add(0, 3, 2, "📄 ফাইল");
        pm.setOnMenuItemClickListener(item -> {
            Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            switch (item.getItemId()) {
                case 1: i.setType("image/*"); startActivityForResult(i, REQ_IMAGE); return true;
                case 2: i.setType("video/*"); startActivityForResult(i, REQ_VIDEO); return true;
                case 3: i.setType("*/*"); startActivityForResult(i, REQ_FILE); return true;
            }
            return false;
        });
        pm.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String caption = input.getText().toString().trim();
        input.setText("");
        new Thread(() -> handleAttachment(uri, caption)).start();
    }

    private void handleAttachment(Uri uri, String caption) {
        try {
            String mime0 = getContentResolver().getType(uri);
            final String mime = mime0 == null ? "application/octet-stream" : mime0;
            final String name = queryFileName(uri);
            if (mime.startsWith("image/")) {
                android.graphics.Bitmap bmp = decodeDownscaled(uri, 1024);
                if (bmp == null) { postBubble(false, "মাস্টার, ছবিটা খুলতে পারলাম না।"); return; }
                runOnUiThread(() -> addImageBubble(bmp));
                saveMsg("user", "[ছবি পাঠানো হয়েছে]" + (caption.isEmpty() ? "" : " " + caption));
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, bos);
                String b64 = android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP);
                String prompt = caption.isEmpty()
                        ? "এই ছবিটা দেখে বাংলায় বলো ছবিতে কী আছে।"
                        : caption;
                String reply = analyzeMedia("image/jpeg", b64, prompt);
                postBubble(false, reply);
                saveMsg("model", reply);
            } else if (mime.startsWith("video/") || mime.equals("application/pdf")) {
                byte[] bytes = readUriBytes(uri, 15 * 1024 * 1024);
                runOnUiThread(() -> addBubble(true, (mime.startsWith("video/") ? "🎬 " : "📄 ") + name));
                if (bytes == null) {
                    postBubble(false, "মাস্টার, এটা অনেক বড় (15MB-এর বেশি) — ছোটটা দাও, দেখে বলছি।");
                    return;
                }
                saveMsg("user", "[" + name + "]" + (caption.isEmpty() ? "" : " " + caption));
                String b64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
                String prompt = caption.isEmpty()
                        ? (mime.startsWith("video/") ? "এই ভিডিওটা দেখে বাংলায় বলো কী আছে।" : "এই ফাইলটা পড়ে বাংলায় সারসংক্ষেপ বলো।")
                        : caption;
                String reply = analyzeMedia(mime, b64, prompt);
                postBubble(false, reply);
                saveMsg("model", reply);
            } else if (mime.startsWith("text/")) {
                byte[] bytes = readUriBytes(uri, 200 * 1024);
                runOnUiThread(() -> addBubble(true, "📄 " + name));
                if (bytes == null) { postBubble(false, "মাস্টার, ফাইলটা বড় — ছোটটা দাও।"); return; }
                String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                saveMsg("user", "[" + name + "]");
                String reply = analyzeMedia(null, null,
                        "এই লেখাটা পড়ে বাংলায় বলো কী আছে:\n" + text + (caption.isEmpty() ? "" : "\n\nপ্রশ্ন: " + caption));
                postBubble(false, reply);
                saveMsg("model", reply);
            } else {
                runOnUiThread(() -> addBubble(true, "📎 " + name));
                postBubble(false, "মাস্টার, এই ধরনের ফাইল (" + mime + ") আমি পড়তে পারি না — ছবি, ভিডিও, PDF বা লেখা ফাইল দাও।");
            }
        } catch (Exception e) {
            postBubble(false, "মাস্টার, ফাইলটা নিতে সমস্যা হলো — আবার চেষ্টা করো।");
        }
    }

    private void postBubble(boolean mine, String text) {
        runOnUiThread(() -> addBubble(mine, text));
    }

    private void addImageBubble(android.graphics.Bitmap bmp) {
        ImageView iv = new ImageView(this);
        iv.setImageBitmap(bmp);
        iv.setAdjustViewBounds(true);
        float d = getResources().getDisplayMetrics().density;
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.7);
        iv.setMaxWidth(maxW);
        iv.setBackgroundResource(R.drawable.bubble_mine);
        iv.setPadding((int) (4 * d), (int) (4 * d), (int) (4 * d), (int) (4 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.END;
        lp.bottomMargin = (int) (8 * d);
        iv.setLayoutParams(lp);
        msgBox.addView(iv);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private android.graphics.Bitmap decodeDownscaled(Uri uri, int maxDim) {
        try {
            android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            java.io.InputStream in1 = getContentResolver().openInputStream(uri);
            android.graphics.BitmapFactory.decodeStream(in1, null, bounds);
            if (in1 != null) in1.close();
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2;
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inSampleSize = sample;
            java.io.InputStream in2 = getContentResolver().openInputStream(uri);
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in2, null, opts);
            if (in2 != null) in2.close();
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] readUriBytes(Uri uri, int maxBytes) {
        try {
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) return null;
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
                if (bos.size() > maxBytes) { in.close(); return null; }
            }
            in.close();
            return bos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private String queryFileName(Uri uri) {
        String name = "ফাইল";
        try {
            android.database.Cursor c = getContentResolver().query(uri, null, null, null, null);
            if (c != null) {
                int idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0 && c.moveToFirst()) name = c.getString(idx);
                c.close();
            }
        } catch (Exception ignored) {}
        return name;
    }

    // Send media (or text) to Gemini and return her reply.
    private String analyzeMedia(String mime, String base64, String prompt) {
        try {
            JSONArray parts = new JSONArray();
            if (mime != null && base64 != null) {
                parts.put(new JSONObject().put("inline_data",
                        new JSONObject().put("mime_type", mime).put("data", base64)));
            }
            parts.put(new JSONObject().put("text", prompt));
            JSONObject body = new JSONObject().put("contents",
                    new JSONArray().put(new JSONObject().put("role", "user").put("parts", parts)));
            String model = "gemini-3.5-flash";
            boolean switched = false;
            for (int attempt = 0; attempt < 3; attempt++) {
                String key = NvnCore.currentKey(getSharedPreferences("archer", MODE_PRIVATE));
                if (key == null || key.trim().isEmpty()) return "মাস্টার, Gemini Key বসানো নেই — ⋮ মেনু থেকে API Keys-এ বসাও।";
                String res = NvnCore.httpPost(
                        "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key.trim(),
                        body.toString());
                if (res != null && NvnCore.isQuotaError(res)) {
                    NvnCore.rotateKey(getSharedPreferences("archer", MODE_PRIVATE));
                    continue;
                }
                if (res != null && res.contains("\"error\"") && !switched) {
                    model = "gemini-3.5-flash-lite";
                    switched = true;
                    continue;
                }
                if (res == null) return "দুঃখিত মাস্টার, এখন দেখতে পারছি না — একটু পরে আবার দাও।";
                JSONObject o = new JSONObject(res);
                JSONArray cands = o.optJSONArray("candidates");
                if (cands == null || cands.length() == 0) return "দুঃখিত মাস্টার, উত্তর পাইনি।";
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray rparts = content != null ? content.optJSONArray("parts") : null;
                if (rparts == null) return "দুঃখিত মাস্টার, উত্তর পাইনি।";
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rparts.length(); i++) {
                    JSONObject part = rparts.getJSONObject(i);
                    if (part.has("text")) sb.append(part.optString("text"));
                }
                String t = sb.toString().trim();
                return t.isEmpty() ? "দেখলাম মাস্টার 💜" : t;
            }
            return "দুঃখিত মাস্টার, এখন দেখতে পারছি না — একটু পরে আবার দাও।";
        } catch (Exception e) {
            return "দুঃখিত মাস্টার, এখন দেখতে পারছি না — একটু পরে আবার দাও।";
        }
    }

    private void showNameDialog() {
        EditText et = new EditText(this);
        et.setHint("যেমন: ফারিহা");
        new AlertDialog.Builder(this)
                .setTitle("আমার নাম দাও")
                .setMessage("তুমি আমাকে কী নামে ডাকবে? এই নামটা সবসময় থাকবে।")
                .setView(et)
                .setPositiveButton("ঠিক আছে", (d, w) -> {
                    String name = et.getText().toString().trim();
                    if (!name.isEmpty()) {
                        getSharedPreferences("archer", MODE_PRIVATE).edit().putString("girl_name", name).apply();
                    }
                })
                .setCancelable(false)
                .show();
    }

    // ---------- sending ----------
    private void sendMsg() {
        if (busy) return;
        String text = input.getText().toString().trim();
        if (text.isEmpty()) return;
        input.setText("");
        addBubble(true, text);
        saveMsg("user", text);
        busy = true;
        new Thread(() -> {
            String reply = chatLoop(text);
            runOnUiThread(() -> {
                busy = false;
                addBubble(false, reply);
                saveMsg("model", reply);
            });
        }).start();
    }

    private String persona() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String girl = p.getString("girl_name", "NVN");
        String userName = p.getString("user_name", "");
        String facts = p.getString("memory_facts", "");
        return "Your name is " + girl + ", a name the user gave you permanently. "
                + "You are a sweet, caring female assistant who talks like a loving girlfriend. "
                + "Always address the user as 'মাস্টার' (Master). Reply in Bengali, short and natural. "
                + (userName.isEmpty() ? "" : "The user's name is " + userName + ". ")
                + (facts.isEmpty() ? "" : "Things you remember about the user forever: " + facts + " ")
                + "The owner writes to you in this chat exactly like he writes to his trusted assistant, "
                + "and you DO the work with your functions — you are NOT a chatbot that only talks. "
                + "When he asks for any work, call the matching function immediately, then confirm in Bengali. "
                + "Never say you cannot do work. Never say you are only an AI. "
                + "For open_app always pass the English app name. "
                + "You manage his connected sites (facebook, instagram, threads, youtube, telegram, blogger, wordpress, tiktok, x, pinterest, whatsapp, gmail, website, custom). "
                + "When he explains what work to do on a site, save it with set_service_rules and confirm what you saved. "
                + "When he asks to connect a site, call connect_site — a secure connect box will open in this chat. "
                + "When he wants a site to work without a token through his Chrome browser (already logged in), call set_browser_mode with state on. "
                + "When he asks about connected sites, call connect_status. When he says do the connect work now, call run_connect_work. "
                + "When he tells you something to remember, call remember_fact. "
                + "When he asks you to remind him about something later, call set_reminder with the text and the minutes. "
                + "Hard rules: never share any phone number, password, OTP or personal info with anyone; "
                + "never promise prices, income or guarantees; never argue. "
                + "Everything he tells you, you remember permanently.";
    }

    private String chatLoop(String userText) {
        try {
            // 1) Offline local commands — instant, free, no internet, no key
            String local = localCommand(userText);
            if (local != null) return local;
            // 2) Answer cache — same question again = instant answer, no key spent
            String cached = cacheGet(userText);
            if (cached != null) return cached;
            // 3) Offline notice for questions that truly need the AI
            if (!isOnline()) {
                return "ইন্টারনেট নেই মাস্টার — টর্চ, সময়, তারিখ, হিসাব, ব্যাটারি, নামাজের সময়, অ্যাপ খোলা আর রিমাইন্ডার এসব অফলাইনেও করতে পারি।";
            }
            NvnCore.restoreKeysIfMissing(this);
            if (!NvnCore.tryCountQuestion(this)) {
                return "আজকের ফ্রি প্রশ্ন শেষ হয়ে গেছে মাস্টার 💜 প্রিমিয়াম নিলে প্রতিদিন অনেক বেশি পাবেন।";
            }
            JSONArray contents = new JSONArray();
            JSONArray hist = msgs();
            int start = Math.max(0, hist.length() - 16);
            for (int i = start; i < hist.length(); i++) {
                JSONObject m = hist.optJSONObject(i);
                if (m == null) continue;
                contents.put(new JSONObject()
                        .put("role", "user".equals(m.optString("r")) ? "user" : "model")
                        .put("parts", new JSONArray().put(new JSONObject().put("text", m.optString("t", "")))));
            }

            for (int round = 0; round < 5; round++) {
                JSONObject body = new JSONObject()
                        .put("systemInstruction", new JSONObject().put("parts",
                                new JSONArray().put(new JSONObject().put("text", persona()))))
                        .put("contents", contents)
                        .put("tools", new JSONArray().put(new JSONObject()
                                .put("functionDeclarations", NvnCore.fnDeclarations())));

                String res = null;
                // Fast mode (Settings, default ON): Flash-Lite — ~500 requests/day free,
                // faster replies. Falls back to the other model automatically on error.
                boolean fast = getSharedPreferences("archer", MODE_PRIVATE).getBoolean("fast_mode", true);
                String model = fast ? "gemini-3.5-flash-lite" : "gemini-3.5-flash";
                boolean switched = false;
                for (int attempt = 0; attempt < 3; attempt++) {
                    String key = NvnCore.currentKey(getSharedPreferences("archer", MODE_PRIVATE));
                    if (key == null || key.trim().isEmpty()) {
                        runOnUiThread(() -> startActivity(new Intent(this, KeysActivity.class)));
                        return "মাস্টার, Gemini Key বসানো নেই — Key-ঘরটা খুলে দিয়েছি, Key বসিয়ে সেভ করে আবার বলুন।";
                    }
                    res = NvnCore.httpPost(
                            "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key.trim(),
                            body.toString());
                    if (res != null && NvnCore.isQuotaError(res)) {
                        NvnCore.rotateKey(getSharedPreferences("archer", MODE_PRIVATE));
                        res = null;
                        continue;
                    }
                    if (res != null && res.contains("\"error\"") && !switched) {
                        model = model.equals("gemini-3.5-flash-lite") ? "gemini-3.5-flash" : "gemini-3.5-flash-lite";
                        switched = true;
                        res = null;
                        continue;
                    }
                    break;
                }
                if (res == null) return "দুঃখিত মাস্টার, এখন উত্তর আনতে পারছি না — একটু পরে আবার বলুন।";

                JSONObject o = new JSONObject(res);
                JSONArray cands = o.optJSONArray("candidates");
                if (cands == null || cands.length() == 0) return "দুঃখিত মাস্টার, উত্তর পাইনি — আবার বলুন।";
                JSONObject content = cands.getJSONObject(0).optJSONObject("content");
                JSONArray parts = content != null ? content.optJSONArray("parts") : null;
                if (parts == null) return "দুঃখিত মাস্টার, উত্তর পাইনি — আবার বলুন।";

                StringBuilder textOut = new StringBuilder();
                JSONArray fnCalls = new JSONArray();
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.getJSONObject(i);
                    if (part.has("text")) textOut.append(part.optString("text"));
                    if (part.has("functionCall")) fnCalls.put(part.getJSONObject("functionCall"));
                }

                if (fnCalls.length() == 0) {
                    String t = textOut.toString().trim();
                    if (!t.isEmpty()) cachePut(userText, t);
                    return t.isEmpty() ? "ঠিক আছে মাস্টার 💜" : t;
                }

                // Model turn with function calls, then our function responses.
                contents.put(content);
                JSONArray respParts = new JSONArray();
                for (int i = 0; i < fnCalls.length(); i++) {
                    JSONObject fc = fnCalls.getJSONObject(i);
                    String name = fc.optString("name", "");
                    JSONObject args = fc.optJSONObject("args");
                    if (args == null) args = new JSONObject();
                    String result = executeFunction(name, args);
                    respParts.put(new JSONObject().put("functionResponse", new JSONObject()
                            .put("name", name)
                            .put("response", new JSONObject().put("result", result))));
                }
                contents.put(new JSONObject().put("role", "user").put("parts", respParts));
            }
            return "কাজটা করে দিয়েছি মাস্টার 💜";
        } catch (Exception e) {
            return "দুঃখিত মাস্টার, একটা সমস্যা হয়েছে — আবার বলুন।";
        }
    }

    // ---------- offline local commands + answer cache (fast, free, no internet) ----------
    private boolean isOnline() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    getSystemService(CONNECTIVITY_SERVICE);
            if (cm == null) return true; // cannot tell -> try the call anyway
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                android.net.Network n = cm.getActiveNetwork();
                if (n == null) return false;
                android.net.NetworkCapabilities cap = cm.getNetworkCapabilities(n);
                return cap != null && cap.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            android.net.NetworkInfo ni = cm.getActiveNetworkInfo();
            return ni != null && ni.isConnected();
        } catch (Exception e) {
            return true; // on any doubt, try the call
        }
    }

    private static String bnToEn(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c >= '০' && c <= '৯') sb.append((char) ('0' + (c - '০')));
            else sb.append(c);
        }
        return sb.toString();
    }

    private String localCommand(String raw) {
        try {
            String t = raw.trim();
            String low = bnToEn(t).toLowerCase(java.util.Locale.ROOT);

            // "ফেসবুক কি তোমার সাথে যুক্ত আছে" — answer from saved state, no internet needed
            if (low.contains("যুক্ত") || low.contains("কানেক্ট") || low.contains("connected") || low.contains("সংযোগ")) {
                String svc = NvnCore.normalizeService(t);
                if (svc != null) {
                    android.content.SharedPreferences pp = getSharedPreferences("archer", MODE_PRIVATE);
                    String tok = pp.getString("conn_" + svc + "_token", "");
                    String usr = pp.getString("conn_" + svc + "_user", "");
                    boolean browser = pp.getBoolean("conn_" + svc + "_browser", false);
                    String acc = pp.getString("conn_" + svc + "_account", "");
                    String nm = ConnectWorker.svcName(svc);
                    if ((tok != null && !tok.isEmpty()) || (usr != null && !usr.isEmpty())) {
                        return "হ্যাঁ মাস্টার, " + nm + " আমার সাথে যুক্ত আছে" + (acc != null && !acc.isEmpty() ? " (" + acc + ")" : "") + "।";
                    }
                    if (browser) {
                        return "হ্যাঁ মাস্টার, " + nm + " ব্রাউজার মোডে যুক্ত আছে — অ্যাপের ব্রাউজারে লগইন থাকলে আমি কাজ করতে পারব।";
                    }
                    return "না মাস্টার, " + nm + " এখনো যুক্ত নেই। চ্যাটে লিখুন '" + nm + " কানেক্ট করো' — আমি লগইন বাক্স খুলে দেব।";
                }
            }

            // "ব্রাউজার এ গিয়ে ইউটিউব লগইন করো" — open NVN built-in browser at that site
            if (low.contains("ব্রাউজার") || low.contains("browser")) {
                String u = "";
                if (low.contains("ইউটিউব") || low.contains("youtube")) u = "https://www.youtube.com";
                else if (low.contains("ফেসবুক") || low.contains("facebook")) u = "https://www.facebook.com";
                else if (low.contains("টিকটক") || low.contains("tiktok")) u = "https://www.tiktok.com";
                else if (low.contains("ইনস্টাগ্রাম") || low.contains("instagram")) u = "https://www.instagram.com";
                else if (low.contains("থ্রেডস") || low.contains("threads")) u = "https://www.threads.net";
                else if (low.contains("টেলিগ্রাম") || low.contains("telegram")) u = "https://web.telegram.org";
                else if (low.contains("টুইটার") || low.contains("twitter") || low.contains("এক্স")) u = "https://x.com";
                else if (low.contains("পিন্টারেস্ট") || low.contains("pinterest")) u = "https://www.pinterest.com";
                else if (low.contains("হোয়াটসঅ্যাপ") || low.contains("whatsapp")) u = "https://web.whatsapp.com";
                else if (low.contains("জিমেইল") || low.contains("gmail")) u = "https://mail.google.com";
                BrowserActivity.open(this, u);
                return "মাস্টার, অ্যাপের ব্রাউজার খুলে দিয়েছি" + (u.isEmpty() ? "।" : " — " + u + " এ লগইন করে রাখুন, তারপর আমি সেখানেই কাজ করব।");
            }

            // reminder: "৩০ মিনিট পর মনে করিয়ে দাও ..."
            if (low.contains("মনে করিয়ে") || low.contains("remind")) {
                int mins = 30;
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(low);
                if (m.find()) mins = Integer.parseInt(m.group(1));
                String text = t.replaceAll("[০-৯0-9]+\\s*মিনিট\\s*(পর|পরে)?", "")
                        .replace("মনে করিয়ে দাও", "").replace("মনে করিয়ে দিও", "")
                        .replace("remind me", "").trim();
                if (text.isEmpty()) text = "মনে করিয়ে দিচ্ছি মাস্টার 💜";
                boolean ok = NvnCore.setReminder(this, text, mins);
                return ok ? "ঠিক আছে মাস্টার, " + mins + " মিনিট পর মনে করিয়ে দেবো: " + text
                        : "রিমাইন্ডার সেট হয়নি মাস্টার, আবার বলুন।";
            }
            // prayer times (cached after first fetch)
            if (low.contains("নামাজ")) return NvnCore.prayerTimesText(this);
            // torch
            if (low.contains("টর্চ") || low.contains("torch") || (low.contains("লাইট") && !low.contains("লাইটার"))) {
                boolean on = !(low.contains("বন্ধ") || low.contains("off") || low.contains("নিভ"));
                try {
                    JSONObject a = new JSONObject().put("state", on ? "on" : "off");
                    NvnCore.execute(getApplicationContext(), "torch", a);
                } catch (Exception ignored) {}
                return on ? "টর্চ জ্বালিয়ে দিয়েছি মাস্টার 🔦" : "টর্চ বন্ধ করে দিয়েছি মাস্টার।";
            }
            // volume
            if (low.contains("ভলিউম") || low.contains("volume")) {
                String dir = (low.contains("কম") || low.contains("down")) ? "down"
                        : (low.contains("সর্বোচ্চ") || low.contains("max") || low.contains("ফুল")) ? "max" : "up";
                try {
                    JSONObject a = new JSONObject().put("direction", dir);
                    NvnCore.execute(getApplicationContext(), "set_volume", a);
                } catch (Exception ignored) {}
                return "ভলিউম ঠিক করে দিয়েছি মাস্টার 🔊";
            }
            // battery
            if (low.contains("ব্যাটারি") || low.contains("battery")) {
                android.os.BatteryManager bm = (android.os.BatteryManager) getSystemService(BATTERY_SERVICE);
                int pct = bm != null ? bm.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) : -1;
                return pct >= 0 ? "ব্যাটারি " + pct + "% আছে মাস্টার 🔋" : "ব্যাটারির হিসাব পাইনি মাস্টার।";
            }
            // time
            if (low.contains("কয়টা বাজে") || low.contains("কয়টা বাজে") || low.contains("সময় কত") || low.contains("what time")) {
                String now = new java.text.SimpleDateFormat("h:mm a", java.util.Locale.US).format(new java.util.Date());
                return "এখন বাজে " + now + " মাস্টার 🕐";
            }
            // date
            if (low.contains("তারিখ") || low.contains("কী বার") || low.contains("কি বার") || low.contains("what date")) {
                String d = new java.text.SimpleDateFormat("EEEE, d MMMM yyyy", new java.util.Locale("bn")).format(new java.util.Date());
                return "আজ " + d + " মাস্টার 📅";
            }
            // calculator: "250 + 175", "৫০০-২০০"
            String calc = calcEval(low);
            if (calc != null) return calc;
            // open app: "YouTube খোলো" / "open YouTube"
            if (low.endsWith("খোলো") || low.endsWith("খুলো") || low.startsWith("open ")) {
                String app = t.replace("খোলো", "").replace("খুলো", "")
                        .replaceFirst("(?i)^open\\s+", "").trim();
                if (!app.isEmpty()) {
                    try {
                        JSONObject a = new JSONObject().put("app_name", app);
                        String r = NvnCore.execute(getApplicationContext(), "open_app", a);
                        if (r != null && r.startsWith("opened")) return app + " খুলে দিয়েছি মাস্টার 💜";
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String calcEval(String low) {
        try {
            String s = low.replace("×", "*").replace("x", "*").replace("÷", "/").replace(" ", "");
            if (!s.matches("^[0-9+\\-*/.]+$") || !s.matches(".*[+\\-*/].*")) return null;
            java.util.List<Double> nums = new java.util.ArrayList<>();
            java.util.List<Character> ops = new java.util.ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (char c : s.toCharArray()) {
                if ((c >= '0' && c <= '9') || c == '.') cur.append(c);
                else { nums.add(Double.parseDouble(cur.toString())); cur.setLength(0); ops.add(c); }
            }
            nums.add(Double.parseDouble(cur.toString()));
            if (nums.size() < 2 || ops.size() != nums.size() - 1) return null;
            double r = nums.get(0);
            for (int i = 0; i < ops.size(); i++) {
                double n = nums.get(i + 1);
                switch (ops.get(i)) {
                    case '+': r += n; break;
                    case '-': r -= n; break;
                    case '*': r *= n; break;
                    case '/': if (n == 0) return "শূন্য দিয়ে ভাগ করা যায় না মাস্টার।"; r /= n; break;
                }
            }
            String out = (r == Math.floor(r)) ? String.valueOf((long) r) : String.valueOf(Math.round(r * 100) / 100.0);
            return "হিসাব: " + out + " মাস্টার 🧮";
        } catch (Exception e) {
            return null;
        }
    }

    private String cacheKey(String q) {
        return q.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String cacheGet(String q) {
        try {
            SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
            JSONObject c = new JSONObject(p.getString("qa_cache", "{}"));
            String v = c.optString(cacheKey(q), "");
            return v.isEmpty() ? null : v;
        } catch (Exception e) {
            return null;
        }
    }

    private void cachePut(String q, String a) {
        try {
            String low = q.toLowerCase(java.util.Locale.ROOT);
            // don't cache action requests — their answers change
            if (low.contains("করো") || low.contains("খোলো") || low.contains("পাঠাও")
                    || low.contains("বানাও") || low.contains("কানেক্ট") || low.contains("connect")) return;
            if (a.length() > 600) return;
            SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
            JSONObject c = new JSONObject(p.getString("qa_cache", "{}"));
            if (c.length() >= 100) c.remove(c.keys().next());
            c.put(cacheKey(q), a);
            p.edit().putString("qa_cache", c.toString()).apply();
        } catch (Exception ignored) {}
    }

    private String executeFunction(String name, JSONObject args) {
        if ("connect_site".equals(name)) {
            String svcId = NvnCore.normalizeService(args.optString("service", ""));
            if (svcId == null) return "unknown service: " + args.optString("service", "");
            return showConnectDialog(svcId);
        }
        return NvnCore.execute(getApplicationContext(), name, args);
    }

    // ---------- secure connect dialog (inside chat) ----------
    private String showConnectDialog(String svc) {
        final String[] result = {"cancelled"};
        final CountDownLatch latch = new CountDownLatch(1);
        runOnUiThread(() -> {
            try {
                SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
                float d = getResources().getDisplayMetrics().density;
                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding((int) (16 * d), (int) (12 * d), (int) (16 * d), (int) (12 * d));

                TextView note = new TextView(this);
                note.setTextColor(0xFFA8C3B2);
                note.setTextSize(13);
                box.addView(note);

                final EditText tokenF = field(box, "Token / Key (শুধু তোমার ফোনেই থাকবে)", true,
                        p.getString("conn_" + svc + "_token", ""));
                final EditText idF = field(box, "ID / URL", false, p.getString("conn_" + svc + "_id", ""));
                final EditText userF = field(box, "Username (যদি লাগে)", false, p.getString("conn_" + svc + "_user", ""));
                final EditText passF = field(box, "Password / App Password (যদি লাগে)", true, p.getString("conn_" + svc + "_pass", ""));

                if ("facebook".equals(svc)) {
                    note.setText("সবচেয়ে সহজ: নিচের বাটনে Facebook দিয়ে লগইন করো — Token নিজে নিজে বসে যাবে।");
                    Button loginBtn = new Button(this);
                    loginBtn.setText("🔐 Facebook দিয়ে লগইন করো");
                    loginBtn.setBackgroundResource(R.drawable.btn_primary);
                    loginBtn.setTextColor(0xFFFFFFFF);
                    loginBtn.setOnClickListener(v -> {
                        startActivity(new Intent(this, OAuthActivity.class));
                        result[0] = "facebook login opened — after login the page token saves itself";
                        latch.countDown();
                    });
                    box.addView(loginBtn);
                } else if ("wordpress".equals(svc) || "website".equals(svc) || "custom".equals(svc)) {
                    note.setText("সাইটের ঠিকানা, Username আর Password/App Password বসাও।");
                } else if ("telegram".equals(svc)) {
                    note.setText("BotFather থেকে বটের Token আর চ্যানেল/গ্রুপের ID বসাও।");
                } else {
                    note.setText("Token বসাও, অথবা নিচে ব্রাউজার মোড চালু করো (Chrome-এ লগইন থাকলেই হবে)।");
                }

                String tokenUrl = tokenUrlFor(svc);
                if (tokenUrl != null && !"facebook".equals(svc)) {
                    Button tb = new Button(this);
                    tb.setText("🔐 লগইন করে Token নাও (অফিসিয়াল পেজ)");
                    tb.setBackgroundResource(R.drawable.btn_secondary);
                    tb.setTextColor(0xFFF3E9C8);
                    tb.setOnClickListener(v -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl))); }
                        catch (Exception e) { Toast.makeText(this, "পেজ খোলা যায়নি", Toast.LENGTH_SHORT).show(); }
                    });
                    box.addView(tb);
                }

                Button browserBtn = new Button(this);
                browserBtn.setText("🌐 ব্রাউজার মোড চালু করো (Token লাগবে না)");
                browserBtn.setBackgroundResource(R.drawable.btn_secondary);
                browserBtn.setTextColor(0xFFF3E9C8);
                browserBtn.setOnClickListener(v -> {
                    p.edit().putBoolean("conn_" + svc + "_browser", true)
                            .putBoolean("conn_" + svc + "_enabled", true).apply();
                    ConnectWorker.logHistory(getApplicationContext(),
                            ConnectWorker.svcName(svc) + " — ব্রাউজার মোড চালু (চ্যাট থেকে)");
                    result[0] = "browser mode on for " + svc;
                    latch.countDown();
                });
                box.addView(browserBtn);

                new AlertDialog.Builder(this)
                        .setTitle(ConnectWorker.svcName(svc) + " — কানেক্ট করো")
                        .setView(box)
                        .setPositiveButton("সেভ করো", (dialog, w) -> {
                            p.edit()
                                    .putString("conn_" + svc + "_token", tokenF.getText().toString().trim())
                                    .putString("conn_" + svc + "_id", idF.getText().toString().trim())
                                    .putString("conn_" + svc + "_user", userF.getText().toString().trim())
                                    .putString("conn_" + svc + "_pass", passF.getText().toString().trim())
                                    .putBoolean("conn_" + svc + "_enabled", true)
                                    .apply();
                            NvnCore.markConnected(this, svc, null);
                            ConnectWorker.logHistory(getApplicationContext(),
                                    ConnectWorker.svcName(svc) + " — চ্যাট থেকে কানেক্ট হয়েছে");
                            new Thread(() -> NvnCore.backupData(getApplicationContext())).start();
                            result[0] = "connected " + svc;
                            latch.countDown();
                        })
                        .setNegativeButton("বাতিল", (dialog, w) -> latch.countDown())
                        .setOnCancelListener(dialog -> latch.countDown())
                        .show();
            } catch (Exception e) {
                latch.countDown();
            }
        });
        try { latch.await(10, TimeUnit.MINUTES); } catch (Exception ignored) {}
        return result[0];
    }

    private EditText field(LinearLayout box, String label, boolean password, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(0xFFEDE6D6);
        tv.setTextSize(13);
        box.addView(tv);
        EditText et = new EditText(this);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setTextColor(0xFFF3E9C8);
        et.setPadding(24, 18, 24, 18);
        if (password) et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setText(value == null ? "" : value);
        box.addView(et);
        return et;
    }

    private String tokenUrlFor(String svc) {
        switch (svc) {
            case "facebook":
            case "instagram":
            case "threads":
                return "https://developers.facebook.com/tools/explorer/";
            case "youtube":
                return "https://console.cloud.google.com/apis/credentials";
            case "telegram":
                return "https://t.me/BotFather";
            case "blogger":
            case "gmail":
                return "https://developers.google.com/oauthplayground/";
            case "wordpress":
                return "https://wordpress.org/support/article/application-passwords/";
            case "tiktok":
                return "https://developers.tiktok.com/";
            case "x":
                return "https://developer.x.com/en/portal/dashboard";
            case "pinterest":
                return "https://developers.pinterest.com/apps/";
            default:
                return null;
        }
    }
}
