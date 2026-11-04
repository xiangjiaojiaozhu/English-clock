package com.xiangjiaojiaozhu.englishclock;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        int pad = (int)(24 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(pad,pad,pad,pad);
        root.setBackgroundColor(Color.rgb(248,250,252));

        TextView title = text("English Clock", 30, Color.rgb(21,101,192));
        TextView word = text("resilience", 38, Color.rgb(25,25,25));
        TextView zh = text("韧性；恢复力", 22, Color.rgb(70,70,70));
        TextView info = text("APK 编译通道测试版\n\n如果你能在 HarmonyOS 4 手机上看到这个页面，说明 GitHub Actions → APK → 手机安装这条链路已经打通。", 17, Color.rgb(90,90,90));
        info.setPadding(0,pad,0,0);
        root.addView(title); root.addView(word); root.addView(zh); root.addView(info);
        setContentView(root);
    }
    private TextView text(String s, float sp, int color) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        v.setGravity(Gravity.CENTER); v.setPadding(0,12,0,12); return v;
    }
}
