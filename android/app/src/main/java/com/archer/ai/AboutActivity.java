package com.archer.ai;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.TextView;

public class AboutActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);
        TextView version = findViewById(R.id.aboutVersion);
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText("ভার্সন: " + pi.versionName + " (বিল্ড " + pi.versionCode + ")");
        } catch (Exception e) {
            version.setText("");
        }
    }
}
