package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class KeysActivity extends Activity {
    private LinearLayout keysList;
    private TextView keyCount;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_keys);
        prefs = getSharedPreferences("archer", MODE_PRIVATE);
        keysList = findViewById(R.id.keysList);
        keyCount = findViewById(R.id.keysCount);

        for (String k : NvnCore.getKeys(prefs)) addKeyRow(k);
        addKeyRow("");
        updateCount();

        findViewById(R.id.keysSave).setOnClickListener(v -> saveKeys());
        findViewById(R.id.keysClear).setOnClickListener(v -> {
            prefs.edit().remove("gemini_keys").remove("gemini_key").putInt("key_index", 0).apply();
            keysList.removeAllViews();
            addKeyRow("");
            updateCount();
            Toast.makeText(this, "সব Key মুছে গেছে", Toast.LENGTH_SHORT).show();
        });
    }

    // 🟢 green = limit left, 🔴 red = today's limit finished (turns green again next day)
    private int keyColor(String value) {
        if (value == null || value.trim().isEmpty()) return 0xFFA8C3B2;
        return NvnCore.isKeyExhausted(this, value) ? 0xFFFF5252 : 0xFF4ADE80;
    }

    private void addKeyRow(String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 5, 0, 5);
        TextView dot = new TextView(this);
        dot.setText("●");
        dot.setTextSize(18);
        dot.setPadding(8, 18, 10, 0);
        dot.setTextColor(keyColor(value));
        EditText et = new EditText(this);
        et.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        et.setBackgroundResource(R.drawable.bg_input);
        et.setHint("Gemini Key বসাও");
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setPadding(24, 20, 24, 20);
        et.setTextColor(keyColor(value));
        et.setHintTextColor(0xFFA8C3B2);
        et.setText(value);
        et.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                int c = keyColor(s.toString());
                et.setTextColor(c);
                dot.setTextColor(c);
                updateCount();
                // last box filled -> a new empty box opens below automatically
                if (!s.toString().trim().isEmpty() && keysList.getChildAt(keysList.getChildCount() - 1) == row) {
                    addKeyRow("");
                }
            }
        });
        Button del = new Button(this);
        del.setText("✕");
        del.setBackgroundResource(R.drawable.btn_secondary);
        del.setTextColor(0xFFF3E9C8);
        del.setOnClickListener(v -> {
            keysList.removeView(row);
            if (keysList.getChildCount() == 0) addKeyRow("");
            updateCount();
        });
        row.addView(dot);
        row.addView(et);
        row.addView(del);
        keysList.addView(row);
    }

    private List<String> collect() {
        List<String> all = new ArrayList<>();
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
        return all;
    }

    private void saveKeys() {
        List<String> all = collect();
        prefs.edit()
                .putString("gemini_key", all.isEmpty() ? "" : all.get(0))
                .putString("gemini_keys", String.join("\n", all))
                .putInt("key_index", 0)
                .apply();
        new Thread(() -> NvnCore.backupData(getApplicationContext())).start();
        Toast.makeText(this, "সেভ হয়েছে — মোট " + all.size() + "টি Key", Toast.LENGTH_SHORT).show();
        // after save, a fresh empty box opens below
        boolean lastEmpty = false;
        if (keysList.getChildCount() > 0) {
            View last = keysList.getChildAt(keysList.getChildCount() - 1);
            if (last instanceof LinearLayout) {
                LinearLayout ll = (LinearLayout) last;
                for (int j = 0; j < ll.getChildCount(); j++) {
                    View child = ll.getChildAt(j);
                    if (child instanceof EditText && ((EditText) child).getText().toString().trim().isEmpty()) lastEmpty = true;
                }
            }
        }
        if (!lastEmpty) addKeyRow("");
        updateCount();
    }

    private void updateCount() {
        int count = collect().size();
        keyCount.setText("মোট Key: " + count + "টি — 🟢 লিমিট আছে · 🔴 আজকের লিমিট শেষ (কাল আবার সবুজ হবে)। একটার লিমিট শেষ হলে অটোমেটিক পরেরটায় চলে যাবে");
    }
}
