package com.xiangjiaojiaozhu.englishclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class LockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        final PendingResult r = goAsync();
        final Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                LockWallpaper.update(app);
            } finally {
                r.finish();
            }
        }).start();
    }
}
