package com.xiangjiaojiaozhu.englishclock;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Calendar;

public class Schedules {
    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static PendingIntent dailyPi(Context c) {
        return PendingIntent.getBroadcast(c, 8, new Intent(c, AlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent lockPi(Context c) {
        return PendingIntent.getBroadcast(c, 9, new Intent(c, LockReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static void scheduleDaily(Context c) {
        SharedPreferences p = prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(dailyPi(c));
        if (!p.getBoolean("alarm_on", false)) return;
        Calendar x = Calendar.getInstance();
        x.set(Calendar.HOUR_OF_DAY, p.getInt("alarm_h", 20));
        x.set(Calendar.MINUTE, p.getInt("alarm_m", 0));
        x.set(Calendar.SECOND, 0);
        x.set(Calendar.MILLISECOND, 0);
        if (!x.after(Calendar.getInstance())) x.add(Calendar.DAY_OF_YEAR, 1);
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, x.getTimeInMillis(), AlarmManager.INTERVAL_DAY, dailyPi(c));
    }

    public static void scheduleLock(Context c) {
        SharedPreferences p = prefs(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        am.cancel(lockPi(c));
        if (!p.getBoolean("lock_on", false)) return;
        long ms = p.getInt("lock_min", 30) * 60000L;
        // RTC (not wakeup): changes the word the next time the phone is awake, saves battery
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + ms, ms, lockPi(c));
    }
}
