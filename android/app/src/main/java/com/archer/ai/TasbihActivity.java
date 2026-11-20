package com.archer.ai;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class TasbihActivity extends Activity {
    private int count = 0;
    private int round = 0;
    private TextView countText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasbih);
        countText = findViewById(R.id.tasbihCount);
        Button tap = findViewById(R.id.tasbihTap);
        Button reset = findViewById(R.id.tasbihReset);
        update();
        tap.setOnClickListener(v -> {
            count++;
            if (count > 33) {
                count = 1;
                round++;
            }
            update();
        });
        reset.setOnClickListener(v -> {
            count = 0;
            round = 0;
            update();
        });
    }

    private void update() {
        countText.setText(count + " / 33\nরাউন্ড: " + round);
    }
}
