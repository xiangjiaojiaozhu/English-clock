package com.xiangjiaojiaozhu.englishclock;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String msg = Schedules.prefs(c).getString("alarm_msg", "背单词");
        Notify.post(c, 707, "study_alarm", "学习提醒", NotificationManager.IMPORTANCE_HIGH, "English Clock", msg);
    }
}
