package com.archer.ai;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConnectReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            ConnectWorker.scheduleAll(ctx);
        } else if ("com.archer.ai.CONNECT_RUN".equals(action)) {
            ConnectWorker.runInBackground(ctx.getApplicationContext(), false);
        }
    }
}
