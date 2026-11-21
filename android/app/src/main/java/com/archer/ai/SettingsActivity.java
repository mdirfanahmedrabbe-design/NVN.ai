package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class SettingsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        SeekBar speed = findViewById(R.id.setSpeed);
        SeekBar pitch = findViewById(R.id.setPitch);
        CheckBox english = findViewById(R.id.setEnglish);
        CheckBox mainVoice = findViewById(R.id.setMainVoice);
        EditText bkash = findViewById(R.id.setBkash);
        Button save = findViewById(R.id.setSave);
        TextView info = findViewById(R.id.setInfo);

        speed.setProgress((int) (prefs.getFloat("tts_speed", 1.0f) * 10));
        pitch.setProgress((int) (prefs.getFloat("tts_pitch", 1.0f) * 10));
        english.setChecked(prefs.getBoolean("english", false));
        mainVoice.setChecked(prefs.getBoolean("main_voice", false));
        bkash.setText(prefs.getString("bkash_number", ""));
        info.setText("Gemini Key ফ্রি পাবে: aistudio.google.com\nKey শুধু তোমার ফোনেই থাকে।");

        boolean owner = prefs.getBoolean("owner_mode", false);
        int vis = owner ? View.VISIBLE : View.GONE;
        findViewById(R.id.setAnnounceBtn).setVisibility(vis);

        findViewById(R.id.setStatusBtn).setOnClickListener(v -> showStatusDialog());
        findViewById(R.id.setKeysBtn).setOnClickListener(v ->
                startActivity(new Intent(this, KeysActivity.class)));
        findViewById(R.id.setAnnounceBtn).setOnClickListener(v ->
                startActivity(new Intent(this, AnnounceActivity.class)));
        findViewById(R.id.setAboutBtn).setOnClickListener(v ->
                startActivity(new Intent(this, AboutActivity.class)));

        save.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit()
                    .putFloat("tts_speed", Math.max(0.5f, speed.getProgress() / 10f))
                    .putFloat("tts_pitch", Math.max(0.5f, pitch.getProgress() / 10f))
                    .putBoolean("english", english.isChecked())
                    .putBoolean("main_voice", mainVoice.isChecked())
                    .putString("bkash_number", bkash.getText().toString().trim());
            ed.apply();
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void showStatusDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
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
