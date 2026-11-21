package com.archer.ai;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

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

    public String foregroundPackage() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) return root.getPackageName().toString();
        } catch (Exception ignored) {}
        return "";
    }

    public boolean launchIntent(android.content.Intent i) {
        try {
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (Exception e) {
            return false;
        }
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

    public boolean tapText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo node : nodes) {
                AccessibilityNodeInfo target = node;
                while (target != null && !target.isClickable()) target = target.getParent();
                if (target != null && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
                Rect r = new Rect();
                node.getBoundsInScreen(r);
                if (r.width() > 0 && r.height() > 0) return tapAt(r.centerX(), r.centerY());
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean tapAt(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 80))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean typeText(String text) {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused == null) focused = findEditable(root);
            if (focused == null) return false;
            Bundle args = new Bundle();
            args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
        } catch (Exception e) {
            return false;
        }
    }

    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo found = findEditable(node.getChild(i));
            if (found != null) return found;
        }
        return null;
    }

    public boolean pressEnter() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null && android.os.Build.VERSION.SDK_INT >= 30) {
                if (focused.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.getId())) return true;
            }
            String[] labels = {"Send", "পাঠাও", "Search", "খুঁজুন", "Go", "Done", "OK", "ঠিক আছে"};
            for (String label : labels) {
                if (tapText(label)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean scrollDown() {
        return swipe(true);
    }

    public boolean scrollUp() {
        return swipe(false);
    }

    private boolean swipe(boolean down) {
        try {
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = dm.widthPixels;
            int h = dm.heightPixels;
            Path path = new Path();
            if (down) {
                path.moveTo(w / 2f, h * 0.75f);
                path.lineTo(w / 2f, h * 0.3f);
            } else {
                path.moveTo(w / 2f, h * 0.3f);
                path.lineTo(w / 2f, h * 0.75f);
            }
            GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 250))
                    .build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            return false;
        }
    }

    public String readScreen() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null) return "screen empty";
            StringBuilder sb = new StringBuilder();
            collectText(root, sb);
            String out = sb.toString().trim();
            if (out.length() > 3000) out = out.substring(0, 3000);
            return out.isEmpty() ? "screen empty" : out;
        } catch (Exception e) {
            return "screen empty";
        }
    }

    private void collectText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null || sb.length() > 3000) return;
        CharSequence t = node.getText();
        if (t != null && t.length() > 0) sb.append(t).append("\n");
        CharSequence d = node.getContentDescription();
        if (d != null && d.length() > 0) sb.append("[").append(d).append("]\n");
        for (int i = 0; i < node.getChildCount(); i++) {
            collectText(node.getChild(i), sb);
        }
    }
}
