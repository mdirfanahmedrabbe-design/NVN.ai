package com.archer.ai;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

// Floating ball (like a chat head) that stays on top of the screen.
// Tap = open NVN. Drag = move it anywhere. Needs "Display over other apps".
public class BubbleService extends Service {

    private WindowManager wm;
    private OrbView orb;
    private WindowManager.LayoutParams params;
    private float downX, downY;
    private int startX, startY;
    private long downTime;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int size = (int) (72 * getResources().getDisplayMetrics().density);
        orb = new OrbView(this);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 24;
        params.y = 320;

        orb.setOnTouchListener((v, e) -> {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = params.x; startY = params.y;
                    downTime = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    params.x = startX + (int) (e.getRawX() - downX);
                    params.y = startY + (int) (e.getRawY() - downY);
                    try { wm.updateViewLayout(orb, params); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    float moved = Math.abs(e.getRawX() - downX) + Math.abs(e.getRawY() - downY);
                    if (moved < 24 && System.currentTimeMillis() - downTime < 400) {
                        Intent i = new Intent(this, MainActivity.class);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(i);
                    }
                    return true;
            }
            return false;
        });

        try {
            wm.addView(orb, params);
        } catch (Exception ex) {
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        if (orb != null && wm != null) {
            try { wm.removeView(orb); } catch (Exception ignored) {}
            orb = null;
        }
        super.onDestroy();
    }
}
