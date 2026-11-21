package com.archer.ai;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PremiumActivity extends Activity {
    private TextView status, bkashInfo;
    private EditText trxidInput;
    private int selected = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_premium);

        status = findViewById(R.id.premStatus);
        bkashInfo = findViewById(R.id.premBkash);
        trxidInput = findViewById(R.id.premTrxid);
        LinearLayout list = findViewById(R.id.premList);
        Button verify = findViewById(R.id.premVerify);

        for (int i = 1; i < NvnCore.TIER_IDS.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, 10, 0, 10);
            TextView tv = new TextView(this);
            tv.setTextColor(0xFFE2E8F0);
            tv.setTextSize(14);
            tv.setText(NvnCore.TIER_NAMES[i] + " — ৳" + NvnCore.TIER_PRICE[i]
                    + " | Live " + NvnCore.TIER_LIVE_MIN[i] + " মিনিট/দিন, প্রশ্ন " + NvnCore.TIER_Q[i] + "/দিন");
            tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button buy = new Button(this);
            buy.setText("কিনুন");
            buy.setBackgroundResource(R.drawable.btn_primary);
            buy.setTextColor(0xFFFFFFFF);
            buy.setTextSize(15);
            buy.setTypeface(null, android.graphics.Typeface.BOLD);
            buy.setOnClickListener(v -> selectTier(idx));
            row.addView(tv);
            row.addView(buy);
            list.addView(row);
        }

        verify.setOnClickListener(v -> verifyPayment());
        refreshStatus();
        selectTier(1);
    }

    private void selectTier(int idx) {
        selected = idx;
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        String num = p.getString("bkash_number", "");
        bkashInfo.setText("প্যাকেজ: " + NvnCore.TIER_NAMES[idx] + " (৳" + NvnCore.TIER_PRICE[idx] + ")\n"
                + "bKash নম্বর: " + (num.isEmpty() ? "শীঘ্রই আসছে" : num)
                + "\nএই নম্বরে Send Money করে TrxID নিচে দাও।");
    }

    private void refreshStatus() {
        SharedPreferences p = getSharedPreferences("archer", MODE_PRIVATE);
        int idx = NvnCore.tierIndex(this);
        long expiry = p.getLong("premium_expiry", 0);
        String expText;
        if (idx == 0) expText = "ফ্রি প্যাকেজ চলছে";
        else if ("life".equals(NvnCore.TIER_IDS[idx])) expText = "লাইফটাইম";
        else expText = "মেয়াদ: " + new SimpleDateFormat("d MMM yyyy", Locale.US).format(new Date(expiry));
        int qUsed = NvnCore.questionsUsedToday(this);
        int liveMinUsed = NvnCore.liveSecondsToday(this) / 60;
        status.setText("বর্তমান প্যাকেজ: " + NvnCore.TIER_NAMES[idx] + " (" + expText + ")\n"
                + "আজকের ব্যবহার: প্রশ্ন " + qUsed + "/" + NvnCore.TIER_Q[idx]
                + ", Live " + liveMinUsed + "/" + NvnCore.TIER_LIVE_MIN[idx] + " মিনিট");
    }

    private void verifyPayment() {
        String trxid = trxidInput.getText().toString().trim().toUpperCase(Locale.ROOT);
        if (trxid.isEmpty()) {
            Toast.makeText(this, "TrxID দাও", Toast.LENGTH_SHORT).show();
            return;
        }
        bkashInfo.setText("যাচাই হচ্ছে... একটু অপেক্ষা করো।");
        new Thread(() -> {
            boolean done = false;
            for (int attempt = 0; attempt < 24 && !done; attempt++) {
                try {
                    String urlStr = NvnCore.SERVER_BASE + "/check?trxid=" + URLEncoder.encode(trxid, "UTF-8")
                            + "&amount=" + NvnCore.TIER_PRICE[selected];
                    URL url = new URL(urlStr);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONObject res = new JSONObject(sb.toString());
                    if (res.optBoolean("valid", false)) {
                        String tier = res.optString("tier", NvnCore.TIER_IDS[selected]);
                        int days = res.optInt("days", NvnCore.TIER_DAYS[selected]);
                        long expiry = days > 0 ? System.currentTimeMillis() + days * 86400000L : 4102444800000L;
                        getSharedPreferences("archer", MODE_PRIVATE).edit()
                                .putString("premium_tier", tier)
                                .putLong("premium_expiry", expiry)
                                .apply();
                        done = true;
                        runOnUiThread(() -> {
                            Toast.makeText(this, "প্রিমিয়াম চালু হয়েছে!", Toast.LENGTH_LONG).show();
                            refreshStatus();
                            bkashInfo.setText("প্রিমিয়াম চালু হয়েছে। ধন্যবাদ!");
                        });
                        break;
                    }
                } catch (Exception ignored) {}
                try { Thread.sleep(5000); } catch (Exception ignored) {}
            }
            if (!done) {
                runOnUiThread(() -> bkashInfo.setText("এখনো মিলেনি। টাকা পাঠিয়ে থাকলে ১ মিনিট পর আবার যাচাই চাপো। TrxID ঠিক আছে কিনা দেখো।"));
            }
        }).start();
    }
}
