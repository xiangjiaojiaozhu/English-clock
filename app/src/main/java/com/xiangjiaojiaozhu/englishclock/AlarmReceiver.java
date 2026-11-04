package com.xiangjiaojiaozhu.englishclock;
import android.app.*; import android.content.*; import android.os.*;
public class AlarmReceiver extends BroadcastReceiver { public void onReceive(Context c,Intent i){
  String msg=i.getStringExtra("msg"); String ch="study_alarm"; NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);
  if(Build.VERSION.SDK_INT>=26)n.createNotificationChannel(new NotificationChannel(ch,"学习提醒",NotificationManager.IMPORTANCE_HIGH));
  Intent open=new Intent(c,MainActivity.class); PendingIntent p=PendingIntent.getActivity(c,1,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
  Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(c,ch):new Notification.Builder(c);
  b.setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("English Clock 学习提醒").setContentText(msg==null?"该学习英语了":msg).setContentIntent(p).setAutoCancel(true).setPriority(Notification.PRIORITY_HIGH);
  n.notify((int)System.currentTimeMillis(),b.build());
}}
