package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class AnnounceActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_announce);
        SharedPreferences prefs = getSharedPreferences("archer", MODE_PRIVATE);

        EditText title = findViewById(R.id.annTitle);
        EditText body = findViewById(R.id.annBody);
        EditText token = findViewById(R.id.annToken);
        TextView current = findViewById(R.id.annCurrent);

        token.setText(prefs.getString("server_token", ""));
        String curT = prefs.getString("last_announce_title", "");
        String curB = prefs.getString("last_announce_body", "");
        current.setText(curT.isEmpty() ? "এখনো কোনো ঘোষণা পোস্ট হয়নি।"
                : "সর্বশেষ ঘোষণা:\n" + curT + "\n" + curB);

        findViewById(R.id.annSend).setOnClickListener(v -> {
            String t = title.getText().toString().trim();
            String b = body.getText().toString().trim();
            String tk = token.getText().toString().trim();
            if (t.isEmpty() || tk.isEmpty()) {
                Toast.makeText(this, "শিরোনাম আর টোকেন লাগবে", Toast.LENGTH_SHORT).show();
                return;
            }
            prefs.edit().putString("server_token", tk).apply();
            Toast.makeText(this, "পাঠানো হচ্ছে...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String res = NvnCore.postAnnouncement(this, tk, t, b);
                runOnUiThread(() -> {
                    if ("sent".equals(res)) {
                        Toast.makeText(this, "ঘোষণা পোস্ট হয়েছে — সবাই দেখতে পাবে", Toast.LENGTH_LONG).show();
                        current.setText("সর্বশেষ ঘোষণা:\n" + t + "\n" + b);
                    } else {
                        Toast.makeText(this, "পাঠানো যায়নি — সার্ভার/টোকেন দেখো", Toast.LENGTH_LONG).show();
                    }
                });
            }).start();
        });
    }
}
