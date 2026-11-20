package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
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
        EditText key = findViewById(R.id.setKey);
        SeekBar speed = findViewById(R.id.setSpeed);
        SeekBar pitch = findViewById(R.id.setPitch);
        CheckBox english = findViewById(R.id.setEnglish);
        Button save = findViewById(R.id.setSave);
        TextView info = findViewById(R.id.setInfo);

        key.setText(prefs.getString("gemini_key", ""));
        speed.setProgress((int) (prefs.getFloat("tts_speed", 1.0f) * 10));
        pitch.setProgress((int) (prefs.getFloat("tts_pitch", 1.0f) * 10));
        english.setChecked(prefs.getBoolean("english", false));
        info.setText("Gemini Key ফ্রি পাবে: aistudio.google.com\nKey শুধু তোমার ফোনেই থাকে।");

        save.setOnClickListener(v -> {
            prefs.edit()
                    .putString("gemini_key", key.getText().toString().trim())
                    .putFloat("tts_speed", Math.max(0.5f, speed.getProgress() / 10f))
                    .putFloat("tts_pitch", Math.max(0.5f, pitch.getProgress() / 10f))
                    .putBoolean("english", english.isChecked())
                    .apply();
            Toast.makeText(this, "সেভ হয়েছে", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
