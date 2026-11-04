package com.xiangjiaojiaozhu.englishclock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Random;

public class WordWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        for (int id : ids) update(c, m, id);
    }

    static void update(Context c, AppWidgetManager m, int id) {
        ArrayList<String[]> a = new Db(c).words("");
        String w = "English Clock";
        String z = "点一个生词，它会出现在这里";
        if (!a.isEmpty()) {
            String[] v = a.get(new Random().nextInt(a.size()));
            w = v[1];
            z = v[2].length() > 0 ? v[2] : v[3];
        }
        RemoteViews r = new RemoteViews(c.getPackageName(), R.layout.word_widget);
        r.setTextViewText(R.id.widget_word, w);
        r.setTextViewText(R.id.widget_zh, z);
        PendingIntent pi = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        r.setOnClickPendingIntent(R.id.widget_root, pi);
        m.updateAppWidget(id, r);
    }

    public static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, WordWidget.class))) update(c, m, id);
    }
}
