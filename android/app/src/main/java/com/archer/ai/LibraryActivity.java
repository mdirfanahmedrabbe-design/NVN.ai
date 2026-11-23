package com.archer.ai;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;

// Library — files/photos/videos sent in chat are saved here.
public class LibraryActivity extends Activity {

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

        TextView title = new TextView(this);
        title.setText("🗂 Library");
        title.setTextColor(0xFFE7C873);
        title.setTextSize(22);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        File dir = getExternalFilesDir("library");
        File[] files = dir != null ? dir.listFiles() : null;
        if (files == null || files.length == 0) {
            TextView empty = new TextView(this);
            empty.setText("এখনো কোনো ফাইল নেই।\nচ্যাটে 📎 দিয়ে ছবি/ভিডিও/ফাইল পাঠালে এখানে জমা হবে।");
            empty.setTextColor(0xFFA8C3B2);
            empty.setTextSize(14);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (16 * d);
            empty.setLayoutParams(lp);
            root.addView(empty);
            setContentView(scroll);
            return;
        }

        Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
        for (File f : files) {
            String name = f.getName();
            String icon = name.endsWith(".jpg") || name.endsWith(".png") ? "🖼 "
                    : name.endsWith(".mp4") ? "🎬 "
                    : name.endsWith(".pdf") ? "📄 " : "📎 ";
            TextView row = new TextView(this);
            row.setText(icon + name + "  (" + humanSize(f.length()) + ")");
            row.setTextColor(0xFFF3E9C8);
            row.setTextSize(14);
            row.setBackgroundResource(R.drawable.bg_card);
            row.setPadding((int) (14 * d), (int) (12 * d), (int) (14 * d), (int) (12 * d));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = (int) (10 * d);
            row.setLayoutParams(lp);
            row.setOnClickListener(v -> {
                if (name.endsWith(".jpg") || name.endsWith(".png")) {
                    ImageView iv = new ImageView(this);
                    iv.setImageBitmap(BitmapFactory.decodeFile(f.getAbsolutePath()));
                    iv.setAdjustViewBounds(true);
                    new AlertDialog.Builder(this).setView(iv).setPositiveButton("বন্ধ", null).show();
                }
            });
            root.addView(row);
        }

        setContentView(scroll);
    }

    private String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        return String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
