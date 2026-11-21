package com.archer.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public class LiveService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String channelId = "nvn_live";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(new NotificationChannel(
                        channelId, "NVN Live", NotificationManager.IMPORTANCE_LOW));
            }
        }
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, channelId)
                : new Notification.Builder(this);
        Notification notification = builder
                .setContentTitle("NVN AI")
                .setContentText("লাইভ চালু আছে — কথা বলো, আমি শুনছি")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .build();
        String mode = intent != null ? intent.getStringExtra("mode") : null;
        boolean screen = "screen".equals(mode) || "both".equals(mode);
        if (Build.VERSION.SDK_INT >= 29) {
            int type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            if (screen) type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
            startForeground(1, notification, type);
        } else {
            startForeground(1, notification);
        }
        return START_STICKY;
    }
}
