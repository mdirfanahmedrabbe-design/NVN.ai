package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

// Profile page (like the Muse profile in WhatsApp): connectors, wallet,
// secure store, library, notifications, clear/export chat, add shortcut.
public class ProfileActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        float d = getResources().getDisplayMetrics().density;

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundResource(R.drawable.bg_main);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        scroll.addView(root);

        OrbView orb = new OrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams((int) (100 * d), (int) (100 * d));
        orbLp.gravity = Gravity.CENTER_HORIZONTAL;
        orb.setLayoutParams(orbLp);
        root.addView(orb);

        String girl = getSharedPreferences("archer", MODE_PRIVATE).getString("girl_name", "NVN");
        TextView name = new TextView(this);
        name.setText(girl);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(20);
        name.setTypeface(null, android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(name);

        TextView sub = new TextView(this);
        sub.setText("NVN AI");
        sub.setTextColor(0xFFA8C3B2);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER_HORIZONTAL);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.bottomMargin = (int) (14 * d);
        sub.setLayoutParams(subLp);
        root.addView(sub);

        addRow(root, d, "🔗 Connectors", v -> startActivity(new Intent(this, ConnectActivity.class)));
        addRow(root, d, "👑 Wallet / Premium", v -> startActivity(new Intent(this, PremiumActivity.class)));
        addRow(root, d, "🔐 Secure store (API Keys)", v -> startActivity(new Intent(this, KeysActivity.class)));
        addRow(root, d, "🗂 Library", v -> startActivity(new Intent(this, LibraryActivity.class)));
        addRow(root, d, "🔔 Notifications / Settings", v -> startActivity(new Intent(this, SettingsActivity.class)));
        addRow(root, d, "📤 Export chat", v -> exportChat());
        addRow(root, d, "📌 Add shortcut", v -> addShortcut());
        addRow(root, d, "🗑 Clear chat", v -> confirmClear());

        setContentView(scroll);
    }

    private void addRow(LinearLayout root, float d, String text, android.view.View.OnClickListener click) {
        TextView row = new TextView(this);
        row.setText(text);
        row.setTextColor(0xFFF3E9C8);
        row.setTextSize(15);
        row.setBackgroundResource(R.drawable.bg_card);
        row.setPadding((int) (14 * d), (int) (13 * d), (int) (14 * d), (int) (13 * d));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (10 * d);
        row.setLayoutParams(lp);
        row.setOnClickListener(click);
        root.addView(row);
    }

    private void exportChat() {
        String hist = getSharedPreferences("archer", MODE_PRIVATE).getString("chat_history", "");
        if (hist == null || hist.trim().isEmpty()) {
            Toast.makeText(this, "চ্যাট খালি", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, "NVN চ্যাট");
        share.putExtra(Intent.EXTRA_TEXT, hist);
        startActivity(Intent.createChooser(share, "চ্যাট পাঠাও"));
    }

    private void addShortcut() {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, "এই ফোনে শর্টকাট হবে না", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            ShortcutManager sm = getSystemService(ShortcutManager.class);
            if (sm != null && sm.isRequestPinShortcutSupported()) {
                Intent target = new Intent(this, ChatActivity.class);
                target.setAction(Intent.ACTION_MAIN);
                ShortcutInfo info = new ShortcutInfo.Builder(this, "nvn_chat")
                        .setShortLabel("NVN AI")
                        .setIcon(Icon.createWithResource(this, R.mipmap.ic_launcher))
                        .setIntent(target)
                        .build();
                sm.requestPinShortcut(info, null);
            } else {
                Toast.makeText(this, "এই ফোনে শর্টকাট হবে না", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "শর্টকাট হয়নি", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("চ্যাট মুছবে?")
                .setMessage("সব চ্যাট মুছে যাবে।")
                .setPositiveButton("মুছো", (dlg, w) -> {
                    getSharedPreferences("archer", MODE_PRIVATE).edit()
                            .remove("chat_msgs").remove("chat_history").apply();
                    Toast.makeText(this, "চ্যাট মোছা হয়েছে", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("না", null)
                .show();
    }
}
