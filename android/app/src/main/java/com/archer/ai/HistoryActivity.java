package com.archer.ai;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class HistoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        TextView t = findViewById(R.id.histText);
        String h = getSharedPreferences("archer", MODE_PRIVATE).getString("conn_history", "");
        t.setText(h == null || h.trim().isEmpty() ? "এখনো কোনো কাজের ইতিহাস নেই।" : h);
    }
}
