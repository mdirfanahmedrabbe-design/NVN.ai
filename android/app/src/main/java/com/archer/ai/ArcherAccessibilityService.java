package com.archer.ai;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public class ArcherAccessibilityService extends AccessibilityService {
    public static ArcherAccessibilityService instance;

    @Override
    public void onServiceConnected() {
        instance = this;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
    }

    public boolean goBack() {
        return performGlobalAction(GLOBAL_ACTION_BACK);
    }

    public boolean goHome() {
        return performGlobalAction(GLOBAL_ACTION_HOME);
    }

    public boolean openRecents() {
        return performGlobalAction(GLOBAL_ACTION_RECENTS);
    }

    public boolean openNotifications() {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS);
    }

    public boolean takeScreenshot() {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            return performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT);
        }
        return false;
    }
}
