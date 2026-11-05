package com.xiangjiaojiaozhu.englishclock;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Kept only so alarms scheduled by v0.5 do nothing. */
public class LockReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
    }
}
