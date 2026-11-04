package com.xiangjiaojiaozhu.englishclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Schedules.scheduleDaily(c);
        Schedules.scheduleLock(c);
        if (Schedules.prefs(c).getBoolean("card_on", false)) LockService.start(c);
    }
}
