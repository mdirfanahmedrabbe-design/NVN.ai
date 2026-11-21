package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class SettingsActivity extends Activity {
    private LinearLayout keysList;
    private TextView keyCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        EditText key = findViewById(R.id.setKey);
        SeekBar speed = findViewById(R.id.setSpeed);
        SeekBar pitch = findViewById(R.id.setPitch);
        CheckBox english = findViewById(R.id.setEnglish);
        CheckBox mainVoice = findViewById(R.id.setMainVoice);
        EditText bkash = findViewById(R.id.setBkash);
        TextView keyLabel = findViewById(R.id.setKeyLabel);
        keysList = findViewById(R.id.setKeysList);
        Button addRow = findViewById(R.id.setAddKeyRow);
        keyCount = findViewById(R.id.setKeyCount);
        Button clearKeys = findViewById(R.id.setClearKeys);
        EditText announceTitle = findViewById(R.id.setAnnounceTitle);
        EditText announceBody = findViewById(R.id.setAnnounceBody);
        EditText serverToken = findViewById(R.id.setServerToken);
        Button sendAnnounce = findViewById(R.id.setSendAnnounce);
        Button save = findViewById(R.id.setSave);
        TextView info = findViewById(R.id.setInfo);

        key.setText(prefs.getString("gemini_key", ""));
        speed.setProgress((int) (prefs.getFloat("tts_speed", 1.0f) * 10));
        pitch.setProgress((int) (prefs.getFloat("tts_pitch", 1.0f) * 10));
        english.setChecked(prefs.getBoolean("english", false));
        mainVoice.setChecked(prefs.getBoolean("main_voice", false));
        bkash.setText(prefs.getString("bkash_number", ""));
        info.setText("Gemini Key ফ্রি পাবে: aistudio.google.com\nKey শুধু তোমার ফোনেই থাকে।");

        boolean owner = prefs.getBoolean("owner_mode", false);
        int vis = owner ? View.VISIBLE : View.GONE;
        keyLabel.setVisibility(View.VISIBLE);
        keysList.setVisibility(View.VISIBLE);
        addRow.setVisibility(View.VISIBLE);
        keyCount.setVisibility(View.VISIBLE);
        clearKeys.setVisibility(View.VISIBLE);
        announceTitle.setVisibility(vis);
        announceBody.setVisibility(vis);
        serverToken.setVisibility(vis);
        sendAnnounce.setVisibility(vis);
        serverToken.setText(prefs.getString("server_token", ""));

        List<String> keys = NvnCore.getKeys(prefs);
        for (String k : keys) addKeyRow(k);
        addKeyRow("");
        updateKeyCount();

        addRow.setOnClickListener(v -> addKeyRow(""));

        clearKeys.setOnClickListener(v -> {
            prefs.edit().remove("gemini_keys").remove("gemini_key").putInt("key_index", 0).apply();
            key.setText("");
            keysList.removeAllViews();
            addKeyRow("");
            updateKeyCount();
            Toast.makeText(this, "সব Key মুছে গেছে", Toast.LENGTH_SHORT).show();
        });

        sendAnnounce.setOnClickListener(v -> {
            String title = announceTitle.getText().toString().trim();
            String body = announceBody.getText().toString().trim();
            String token = serverToken.getText().toString().trim();
            if (title.isEmpty() || token.isEmpty()) {
                Toast.makeText(this, "শিরোনাম আর টোকেন লাগবে", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("server_token", token).apply();
            Toast.makeText(this, "পাঠানো হচ্ছে...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String res = NvnCore.postAnnouncement(this, token, title, body);
                runOnUiThread(() -> Toast.makeText(this,
                        "sent".equals(res) ? "ঘোষণা পাঠানো হয়েছে" : "পাঠানো যায়নি — সার্ভার/টোকেন দেখো",
                        Toast.LENGTH_LONG).show());
            }).start();
        });

        findViewById(R.id.setStatusBtn).setOnClickListener(v -> showStatusDialog());

        save.setOnClickListener(v -> {
            List<String> all = new ArrayList<>();
            String single = key.getText().toString().trim();
            if (!single.isEmpty()) all.add(single);
            for (int i = 0; i < keysList.getChildCount(); i++) {
                View row = keysList.getChildAt(i);
                if (row instanceof LinearLayout) {
                    LinearLayout ll = (LinearLayout) row;
                    for (int j = 0; j < ll.getChildCount(); j++) {
                        View child = ll.getChildAt(j);
                        if (child instanceof EditText) {
                            String val = ((EditText) child).getText().toString().trim();
                            if (!val.isEmpty() && !all.contains(val)) all.add(val);
                        }
                    }
                }
            }
            SharedPreferences.Editor ed = prefs.edit()
                    .putString("gemini_key", all.isEmpty() ? "" : all.get(0))
                    .putString("gemini_keys", String.join("\n", all))
                    .putFloat("tts_speed", Math.max(0.5f, speed.getProgress() / 10f))
                    .putFloat("tts_pitch", Math.max(0.5f, pitch.getProgress() / 10f))
                    .putBoolean("english", english.isChecked())
                    .putBoolean("main_voice", mainVoice.isChecked())
                    .putString("bkash_number", bkash.getText().toString().trim());
            ed.apply();
            Toast.makeText(this, "সেভ হয়েছে — মোট " + all.size() + "টি Key", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void addKeyRow(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 4, 0, 4);
        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        et.setBackgroundResource(R.drawable.bg_input);
        et.setHint("নতুন Key বসাও");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setPadding(24, 18, 24, 18);
        et.setTextColor(0xFFE8F4FF);
        et.setHintTextColor(0xFF64748B);
        et.setText(value);
        Button del = new Button(this);
        del.setText("✕");
        del.setOnClickListener(v -> {
            keysList.removeView(row);
            updateKeyCount();
        });
        row.addView(et);
        row.addView(del);
        keysList.addView(row);
        updateKeyCount();
    }

    private void updateKeyCount() {
        int count = 0;
        for (int i = 0; i < keysList.getChildCount(); i++) {
            View row = keysList.getChildAt(i);
            if (row instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) row;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof EditText && !((EditText) child).getText().toString().trim().isEmpty()) count++;
                }
            }
        }
        keyCount.setText("মোট Key: " + count + "টি (একটার লিমিট শেষ হলে অটো পরেরটায় যাবে)");
    }

    private void showStatusDialog() {
        int tier = NvnCore.tierIndex(this);
        long exp = prefs.getLong("premium_expiry", 0);
        String expText = exp > 0
                ? new java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.ENGLISH).format(new java.util.Date(exp))
                : "—";
        int usedMin = NvnCore.liveSecondsToday(this) / 60;
        int limitMin = NvnCore.TIER_LIVE_MIN[tier];
        String annTitle = prefs.getString("last_announce_title", "");
        String annBody = prefs.getString("last_announce_body", "");
        String annText = annTitle.isEmpty() ? "" : "📢 " + annTitle + "\n" + annBody + "\n\n";
        String msg = annText + "প্যাকেজ: " + NvnCore.TIER_NAMES[tier] + "\n"
                + "মেয়াদ: " + expText + "\n"
                + "আজ Live: " + usedMin + "/" + limitMin + " মিনিট\n"
                + "আজ প্রশ্ন: " + NvnCore.questionsUsedToday(this) + "/" + NvnCore.TIER_Q[tier] + "\n"
                + "মালিক মোড: " + (prefs.getBoolean("owner_mode", false) ? "চালু" : "বন্ধ");
        AlertDialog d = new AlertDialog.Builder(this)
                .setTitle("🔔 নোটিফিকেশন ও অবস্থা")
                .setMessage(msg)
                .setPositiveButton("ঠিক আছে", null)
                .setNeutralButton("👑 Premium কিনুন", (dlg, w) ->
                        startActivity(new Intent(this, PremiumActivity.class)))
                .create();
        d.show();
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
            android.widget.TextView m = d.findViewById(android.R.id.message);
            if (m != null) m.setTextColor(0xFFE8F4FF);
            android.widget.TextView tt = d.findViewById(android.R.id.title);
            if (tt != null) tt.setTextColor(0xFFC084FC);
            android.widget.Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(0xFFC084FC);
            android.widget.Button neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(0xFFFACC15);
        } catch (Exception ignored) {}
    }
}
