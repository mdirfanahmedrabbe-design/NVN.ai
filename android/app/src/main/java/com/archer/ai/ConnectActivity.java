package com.archer.ai;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

// Simple Connected list (owner's mockup): "Connected" header pill, one row per
// started site (name pill + Connected/Connect status pill), "New connect +"
// at the bottom opens the full catalog to add a site.
public class ConnectActivity extends Activity {
    private LinearLayout list;
    private float d;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        d = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0A0F0D);
        root.setPadding(dp(16), dp(16), dp(16), dp(16));

        TextView back = new TextView(this);
        back.setText("←");
        back.setTextColor(0xFFFFFFFF);
        back.setTextSize(18);
        back.setGravity(Gravity.CENTER);
        back.setBackground(pill(0xFF123B33, 12));
        back.setLayoutParams(new LinearLayout.LayoutParams(dp(40), dp(40)));
        back.setOnClickListener(v -> finish());
        root.addView(back);

        TextView header = new TextView(this);
        header.setText("Connected");
        header.setTextColor(0xFFFFFFFF);
        header.setTextSize(17);
        header.setGravity(Gravity.CENTER);
        header.setBackground(pill(0xFF1B7F8E, 24));
        header.setPadding(dp(16), dp(10), dp(16), dp(10));
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        hLp.topMargin = dp(18);
        hLp.leftMargin = dp(30);
        hLp.rightMargin = dp(30);
        header.setLayoutParams(hLp);
        root.addView(header);

        ScrollView sv = new ScrollView(this);
        LinearLayout.LayoutParams sLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sLp.topMargin = dp(22);
        sv.setLayoutParams(sLp);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv);

        TextView newBtn = new TextView(this);
        newBtn.setText("New connect");
        newBtn.setTextColor(0xFFFFFFFF);
        newBtn.setTextSize(15);
        newBtn.setGravity(Gravity.CENTER);
        newBtn.setBackground(pill(0xFF27AE60, 24));
        newBtn.setPadding(dp(22), dp(9), dp(22), dp(9));
        LinearLayout.LayoutParams nLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nLp.gravity = Gravity.CENTER_HORIZONTAL;
        newBtn.setLayoutParams(nLp);
        newBtn.setOnClickListener(v -> startActivity(new Intent(this, CatalogActivity.class)));
        root.addView(newBtn);

        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextColor(0xFFFFFFFF);
        plus.setTextSize(20);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(pill(0xFF27AE60, 30));
        LinearLayout.LayoutParams pLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        pLp.gravity = Gravity.CENTER_HORIZONTAL;
        pLp.topMargin = dp(6);
        plus.setLayoutParams(pLp);
        plus.setOnClickListener(v -> startActivity(new Intent(this, CatalogActivity.class)));
        root.addView(plus);

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        buildList();
    }

    private int dp(int v) {
        return (int) (v * d);
    }

    private GradientDrawable pill(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private void buildList() {
        list.removeAllViews();
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        boolean any = false;
        for (String svc : ConnectWorker.SVC_IDS) {
            boolean connected = ConnectWorker.isConnected(this, svc);
            boolean browser = ConnectWorker.isBrowserMode(this, svc);
            boolean enabled = p.getBoolean("conn_" + svc + "_enabled", false);
            String token = p.getString("conn_" + svc + "_token", "");
            if (!connected && !browser && !enabled && (token == null || token.isEmpty())) continue;
            any = true;
            addRow(svc, connected || browser);
        }
        if (!any) {
            TextView empty = new TextView(this);
            empty.setText("এখনো কোনো সাইট যুক্ত হয়নি — নিচের New connect দিয়ে যোগ করো");
            empty.setTextColor(0xFF8AA79B);
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(10), dp(24), dp(10), dp(10));
            list.addView(empty);
        }
    }

    private void addRow(String svc, boolean connected) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rLp.bottomMargin = dp(16);
        row.setLayoutParams(rLp);

        TextView name = new TextView(this);
        name.setText(ConnectWorker.svcName(svc));
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(15);
        name.setGravity(Gravity.CENTER);
        name.setBackground(pill(0xFF2B7FB8, 10));
        name.setPadding(dp(18), dp(8), dp(18), dp(8));
        row.addView(name);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(0, 1, 1f));
        row.addView(spacer);

        TextView status = new TextView(this);
        status.setText(connected ? "Connected ✓" : "Connect ✓");
        status.setTextColor(0xFFFFFFFF);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setBackground(pill(0xFF27AE60, 10));
        status.setPadding(dp(14), dp(7), dp(14), dp(7));
        row.addView(status);

        View.OnClickListener open = v -> {
            Intent i = new Intent(this, ServiceActivity.class);
            i.putExtra("svc", svc);
            startActivity(i);
        };
        row.setOnClickListener(open);
        name.setOnClickListener(open);
        status.setOnClickListener(open);
        list.addView(row);
    }
}
