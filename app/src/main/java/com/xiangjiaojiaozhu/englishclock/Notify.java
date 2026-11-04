package com.xiangjiaojiaozhu.englishclock;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class Notify {
    public static void post(Context c, int id, String channel, String channelName, int importance, String title, String text) {
        NotificationManager n = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        n.createNotificationChannel(new NotificationChannel(channel, channelName, importance));
        PendingIntent pi = PendingIntent.getActivity(c, id, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification nt = new Notification.Builder(c, channel)
                .setSmallIcon(R.drawable.ic_notify)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        n.notify(id, nt);
    }
}
