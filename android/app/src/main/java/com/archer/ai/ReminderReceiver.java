package com.archer.ai;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

// Fires a reminder notification at the set time — works even if the app is closed.
public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            String text = intent.getStringExtra("text");
            if (text == null || text.trim().isEmpty()) text = "মনে করিয়ে দিচ্ছি মাস্টার 💜";
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            String chId = "nvn_remind";
            if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel(chId) == null) {
                nm.createNotificationChannel(new NotificationChannel(chId, "NVN রিমাইন্ডার",
                        NotificationManager.IMPORTANCE_HIGH));
            }
            Intent open = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 900, open,
                    Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(ctx, chId) : new Notification.Builder(ctx);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("⏰ মনে করিয়ে দিচ্ছি")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setContentIntent(pi)
                    .setAutoCancel(true);
            nm.notify(900 + (int) (System.currentTimeMillis() % 500), b.build());
        } catch (Exception ignored) {}
    }
}
