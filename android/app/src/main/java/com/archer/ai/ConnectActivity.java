package com.archer.ai;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

public class ConnectActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connect);
        buildList();

        findViewById(R.id.connRunNow).setOnClickListener(v -> {
            ConnectWorker.runInBackground(getApplicationContext(), true);
            Toast.makeText(this, "কাজ শুরু হয়েছে — শেষ হলে জানাবো", Toast.LENGTH_LONG).show();
        });
        findViewById(R.id.connApprovals).setOnClickListener(v ->
                startActivity(new Intent(this, ApprovalActivity.class)));
        findViewById(R.id.connHistory).setOnClickListener(v ->
                startActivity(new Intent(this, HistoryActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    private void buildList() {
        LinearLayout list = findViewById(R.id.connList);
        list.removeAllViews();
        float density = getResources().getDisplayMetrics().density;
        for (String svc : ConnectWorker.SVC_IDS) {
            Button b = new Button(this);
            boolean connected = ConnectWorker.isConnected(this, svc);
            boolean enabled = getSharedPreferences("archer", MODE_PRIVATE)
                    .getBoolean("conn_" + svc + "_enabled", false);
            String status = connected ? (enabled ? "✅ কানেক্ট আছে, কাজ চালু" : "✅ কানেক্ট আছে, কাজ বন্ধ") : "❌ কানেক্ট নাই";
            b.setText(ConnectWorker.svcName(svc) + "\n" + status);
            b.setTextColor(0xFFE8F4FF);
            b.setBackgroundResource(R.drawable.btn_secondary);
            b.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = (int) (10 * density);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                Intent i = new Intent(this, ServiceActivity.class);
                i.putExtra("svc", svc);
                startActivity(i);
            });
            list.addView(b);
        }
        int q = ConnectWorker.getQueue(this).length();
        Button appr = findViewById(R.id.connApprovals);
        appr.setText("📋 অনুমোদনের অপেক্ষায়" + (q > 0 ? " (" + q + ")" : ""));
    }
}
