package com.archer.ai;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

public class ApprovalActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_approval);
        buildList();
    }

    private void buildList() {
        LinearLayout list = findViewById(R.id.apprList);
        TextView empty = findViewById(R.id.apprEmpty);
        list.removeAllViews();
        JSONArray q = ConnectWorker.getQueue(this);
        empty.setVisibility(q.length() == 0 ? View.VISIBLE : View.GONE);
        float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i < q.length(); i++) {
            final int idx = i;
            JSONObject item = q.optJSONObject(i);
            if (item == null) continue;
            String svc = item.optString("svc", "");
            String kind = item.optString("kind", "");
            String text = item.optString("text", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            card.setPadding((int) (14 * density), (int) (12 * density), (int) (14 * density), (int) (12 * density));
            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            clp.bottomMargin = (int) (12 * density);
            card.setLayoutParams(clp);

            TextView head = new TextView(this);
            head.setText(ConnectWorker.svcName(svc) + " — " + ("reply".equals(kind) ? "কমেন্টের উত্তর" : "পোস্ট"));
            head.setTextColor(0xFFC084FC);
            head.setTextSize(14);
            head.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(head);

            TextView body = new TextView(this);
            body.setText(text);
            body.setTextColor(0xFFE8F4FF);
            body.setTextSize(14);
            body.setPadding(0, (int) (6 * density), 0, (int) (6 * density));
            card.addView(body);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);

            Button send = new Button(this);
            send.setText("✅ পাঠাও");
            send.setTextColor(0xFFFFFFFF);
            send.setBackgroundResource(R.drawable.btn_primary);
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            blp.rightMargin = (int) (8 * density);
            send.setLayoutParams(blp);
            send.setOnClickListener(v -> {
                send.setEnabled(false);
                new Thread(() -> {
                    boolean ok = ConnectWorker.sendQueuedItem(getApplicationContext(), item);
                    runOnUiThread(() -> {
                        if (ok) {
                            removeItem(idx);
                            ConnectWorker.logHistory(getApplicationContext(),
                                    ConnectWorker.svcName(svc) + " — অনুমোদনের পর পাঠানো হয়েছে");
                            Toast.makeText(this, "পাঠানো হয়েছে", Toast.LENGTH_SHORT).show();
                        } else {
                            send.setEnabled(true);
                            Toast.makeText(this, "পাঠানো যায়নি — Token/ID ঠিক আছে কিনা দেখো", Toast.LENGTH_LONG).show();
                        }
                    });
                }).start();
            });
            row.addView(send);

            Button drop = new Button(this);
            drop.setText("❌ বাদ দাও");
            drop.setTextColor(0xFFE8F4FF);
            drop.setBackgroundResource(R.drawable.btn_secondary);
            drop.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            drop.setOnClickListener(v -> {
                removeItem(idx);
                ConnectWorker.logHistory(getApplicationContext(),
                        ConnectWorker.svcName(svc) + " — ড্রাফট বাদ দেওয়া হয়েছে");
                Toast.makeText(this, "বাদ দেওয়া হয়েছে", Toast.LENGTH_SHORT).show();
            });
            row.addView(drop);

            card.addView(row);
            list.addView(card);
        }
    }

    private void removeItem(int idx) {
        JSONArray q = ConnectWorker.getQueue(this);
        JSONArray nq = new JSONArray();
        for (int i = 0; i < q.length(); i++) {
            if (i != idx) nq.put(q.optJSONObject(i));
        }
        ConnectWorker.saveQueue(this, nq);
        buildList();
    }
}
