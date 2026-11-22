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
        CheckBox fastMode = findViewById(R.id.setFastMode);
        fastMode.setChecked(prefs.getBoolean("fast_mode", true));
        Button orbBtn = findViewById(R.id.setOrbColorBtn);
        String[] orbNames = {"অটো (রং বদলায়)", "বেগুনি", "নীল", "সবুজ", "সোনালি", "গোলাপি", "লাল"};
        int orbIdx = 0;
        try { orbIdx = Integer.parseInt(prefs.getString("orb_color", "-1")) + 1; } catch (Exception ignored) {}
        if (orbIdx < 0 || orbIdx >= orbNames.length) orbIdx = 0;
        orbBtn.setText("🎨 বলের রং: " + orbNames[orbIdx]);
        final int[] orbCur = {orbIdx};
        orbBtn.setOnClickListener(v -> {
            orbCur[0] = (orbCur[0] + 1) % orbNames.length;
            orbBtn.setText("🎨 বলের রং: " + orbNames[orbCur[0]]);
            prefs.edit().putString("orb_color", String.valueOf(orbCur[0] - 1)).apply();
        });
        bkash.setText(prefs.getString("bkash_number", ""));
        info.setText("Gemini Key ফ্রি পাবে: aistudio.google.com\nKey শুধু তোমার ফোনেই থাকে।");

        boolean owner = prefs.getBoolean("owner_mode", false);
        int vis = owner ? View.VISIBLE : View.GONE;
        findViewById(R.id.setAnnounceBtn).setVisibility(vis);

        findViewById(R.id.setStatusBtn).setOnClickListener(v -> showStatusDialog());
        android.widget.Button ownerBtn = findViewById(R.id.setOwnerBtn);
        ownerBtn.setText(prefs.getBoolean("owner_mode", false) ? "👑 মালিক মোড: চালু (আনলিমিটেড)" : "👑 মালিক মোড: বন্ধ — চালু করতে টিপ দাও");
        ownerBtn.setOnClickListener(v -> ownerPinDialog());
        findViewById(R.id.setKeysBtn).setOnClickListener(v ->
                startActivity(new Intent(this, KeysActivity.class)));
        findViewById(R.id.setConnectBtn).setOnClickListener(v ->
                startActivity(new Intent(this, ConnectActivity.class)));
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
                    .putBoolean("fast_mode", fastMode.isChecked())
                    .putString("bkash_number", bkash.getText().toString().trim());
            ed.apply();
            new Thread(() -> NvnCore.backupData(getApplicationContext())).start();
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void ownerPinDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);
        String hash = prefs.getString("owner_pin_hash", "");
        android.widget.EditText pinInput = new android.widget.EditText(this);
        pinInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setHint("PIN (কমপক্ষে ৪ ডিজিট)");
        new AlertDialog.Builder(this)
                .setTitle(hash.isEmpty() ? "মালিকের PIN সেট করো" : "মালিকের PIN দাও")
                .setView(pinInput)
                .setPositiveButton("ঠিক আছে", (d, w) -> {
                    String pin = pinInput.getText().toString().trim();
                    if (pin.length() < 4) { Toast.makeText(this, "PIN কমপক্ষে ৪ ডিজিট", Toast.LENGTH_SHORT).show(); return; }
                    if (hash.isEmpty()) {
                        prefs.edit().putString("owner_pin_hash", NvnCore.sha256Hex(pin)).putBoolean("owner_mode", true).apply();
                        Toast.makeText(this, "মালিক মোড চালু — লিমিট নেই", Toast.LENGTH_LONG).show();
                        recreate();
                    } else if (NvnCore.sha256Hex(pin).equals(hash)) {
                        boolean now = !prefs.getBoolean("owner_mode", false);
                        prefs.edit().putBoolean("owner_mode", now).apply();
                        Toast.makeText(this, now ? "মালিক মোড চালু — লিমিট নেই" : "মালিক মোড বন্ধ", Toast.LENGTH_LONG).show();
                        recreate();
                    } else {
                        Toast.makeText(this, "PIN ভুল", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("বাতিল", null)
                .show();
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
        boolean owner = prefs.getBoolean("owner_mode", false);
        String msg = owner
                ? annText + "👑 আপনি মালিক — সব আনলিমিটেড\nআজ Live: " + usedMin + " মিনিট (লিমিট নেই)"
                : annText + "প্যাকেজ: " + NvnCore.TIER_NAMES[tier] + "\n"
                + "মেয়াদ: " + expText + "\n"
                + "আজ Live: " + usedMin + "/" + limitMin + " মিনিট\n"
                + "আজ প্রশ্ন: " + NvnCore.questionsUsedToday(this) + "/" + NvnCore.TIER_Q[tier] + "\n"
                + "মালিক মোড: বন্ধ";
        AlertDialog.Builder bld = new AlertDialog.Builder(this)
                .setTitle("🔔 নোটিফিকেশন ও অবস্থা")
                .setMessage(msg)
                .setPositiveButton("ঠিক আছে", null);
        if (!owner) bld.setNeutralButton("👑 Premium কিনুন", (dlg, w) ->
                startActivity(new Intent(this, PremiumActivity.class)));
        AlertDialog d = bld.create();
        d.show();
        try {
            if (d.getWindow() != null) d.getWindow().setBackgroundDrawableResource(R.drawable.bg_card);
            android.widget.TextView m = d.findViewById(android.R.id.message);
            if (m != null) m.setTextColor(0xFFF3E9C8);
            android.widget.TextView tt = d.findViewById(android.R.id.title);
            if (tt != null) tt.setTextColor(0xFFE7C873);
            android.widget.Button pos = d.getButton(AlertDialog.BUTTON_POSITIVE);
            if (pos != null) pos.setTextColor(0xFFE7C873);
            android.widget.Button neu = d.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neu != null) neu.setTextColor(0xFFFACC15);
        } catch (Exception ignored) {}
    }
}
