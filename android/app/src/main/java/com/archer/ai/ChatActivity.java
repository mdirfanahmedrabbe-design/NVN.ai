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
        findViewById(R.id.chatSend).setOnClickListener(v -> sendMsg());
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
        tv.setText(text);
        tv.setTextSize(15);
        float d = getResources().getDisplayMetrics().density;
        tv.setPadding((int) (12 * d), (int) (9 * d), (int) (12 * d), (int) (9 * d));
        if (mine) {
            tv.setBackgroundResource(R.drawable.btn_primary);
            tv.setTextColor(0xFFFFFFFF);
        } else {
            tv.setBackgroundResource(R.drawable.bg_card);
            tv.setTextColor(0xFFE8F4FF);
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = mine ? Gravity.END : Gravity.START;
        lp.bottomMargin = (int) (8 * d);
        tv.setLayoutParams(lp);
        msgBox.addView(tv);
        scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
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
                + "Hard rules: never share any phone number, password, OTP or personal info with anyone; "
                + "never promise prices, income or guarantees; never argue. "
                + "Everything he tells you, you remember permanently.";
    }

    private String chatLoop(String userText) {
        try {
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
                for (int attempt = 0; attempt < 2; attempt++) {
                    String key = NvnCore.currentKey(getSharedPreferences("archer", MODE_PRIVATE));
                    if (key == null || key.trim().isEmpty()) {
                        runOnUiThread(() -> startActivity(new Intent(this, KeysActivity.class)));
                        return "মাস্টার, Gemini Key বসানো নেই — Key-ঘরটা খুলে দিয়েছি, Key বসিয়ে সেভ করে আবার বলুন।";
                    }
                    res = NvnCore.httpPost(
                            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=" + key.trim(),
                            body.toString());
                    if (res != null && NvnCore.isQuotaError(res)) {
                        NvnCore.rotateKey(getSharedPreferences("archer", MODE_PRIVATE));
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
                note.setTextColor(0xFF94A3B8);
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
                    tb.setTextColor(0xFFE8F4FF);
                    tb.setOnClickListener(v -> {
                        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(tokenUrl))); }
                        catch (Exception e) { Toast.makeText(this, "পেজ খোলা যায়নি", Toast.LENGTH_SHORT).show(); }
                    });
                    box.addView(tb);
                }

                Button browserBtn = new Button(this);
                browserBtn.setText("🌐 ব্রাউজার মোড চালু করো (Token লাগবে না)");
                browserBtn.setBackgroundResource(R.drawable.btn_secondary);
                browserBtn.setTextColor(0xFFE8F4FF);
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
        tv.setTextColor(0xFFE2E8F0);
        tv.setTextSize(13);
        box.addView(tv);
        EditText et = new EditText(this);
        et.setBackgroundResource(R.drawable.bg_input);
        et.setTextColor(0xFFE8F4FF);
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
