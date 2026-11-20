package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

public class SettingsActivity extends Activity {
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
        EditText addKey = findViewById(R.id.setAddKey);
        TextView keyCount = findViewById(R.id.setKeyCount);
        Button clearKeys = findViewById(R.id.setClearKeys);
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
        keyLabel.setVisibility(vis);
        addKey.setVisibility(vis);
        keyCount.setVisibility(vis);
        clearKeys.setVisibility(vis);
        List<String> keys = NvnCore.getKeys(prefs);
        keyCount.setText("মোট Key: " + keys.size() + "টি (একটার লিমিট শেষ হলে অটো পরেরটায় যাবে)");

        clearKeys.setOnClickListener(v -> {
            prefs.edit().remove("gemini_keys").remove("gemini_key").putInt("key_index", 0).apply();
            key.setText("");
            keyCount.setText("মোট Key: 0টি");
            Toast.makeText(this, "সব Key মুছে গেছে", Toast.LENGTH_SHORT).show();
        });

        save.setOnClickListener(v -> {
            SharedPreferences.Editor ed = prefs.edit()
                    .putString("gemini_key", key.getText().toString().trim())
                    .putFloat("tts_speed", Math.max(0.5f, speed.getProgress() / 10f))
                    .putFloat("tts_pitch", Math.max(0.5f, pitch.getProgress() / 10f))
                    .putBoolean("english", english.isChecked())
                    .putBoolean("main_voice", mainVoice.isChecked())
                    .putString("bkash_number", bkash.getText().toString().trim());
            String extra = addKey.getText().toString().trim();
            if (!extra.isEmpty()) {
                String existing = prefs.getString("gemini_keys", "");
                String single = key.getText().toString().trim();
                StringBuilder sb = new StringBuilder();
                if (!single.isEmpty()) sb.append(single);
                if (!existing.isEmpty()) {
                    if (sb.length() > 0) sb.append("\n");
                    sb.append(existing);
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(extra);
                ed.putString("gemini_keys", sb.toString());
            }
            ed.apply();
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
