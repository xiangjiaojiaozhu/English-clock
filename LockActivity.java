package com.xiangjiaojiaozhu.englishclock;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;

/** Lock-screen word card: one word at a time, swipe left/right to change, swipe up to close. */
public class LockActivity extends Activity {

    Db db;
    boolean hide;
    GestureDetector gd;
    ArrayList<String[]> words = new ArrayList<>();
    int idx = 0;
    boolean animating = false;

    LinearLayout card;
    TextView counter, wordV, phV, zhV, ctxV;
    Button hideBtn;

    final BroadcastReceiver unlocked = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            finish();
        }
    };

    public static void launch(Context c) {
        AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            int m = am.getMode();
            if (m == AudioManager.MODE_IN_CALL || m == AudioManager.MODE_IN_COMMUNICATION || m == AudioManager.MODE_RINGTONE) return;
        }
        if (new Db(c).words("").isEmpty()) return;
        Intent i = new Intent(c, LockActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        try {
            c.startActivity(i);
        } catch (Exception ignored) {
        }
    }

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(false);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        db = new Db(this);
        gd = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent a, MotionEvent b, float vx, float vy) {
                if (a == null || b == null) return false;
                float dx = b.getX() - a.getX(), dy = b.getY() - a.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > dp(60) && Math.abs(vx) > 400) {
                    move(dx < 0 ? 1 : -1);
                    return true;
                }
                if (dy < -dp(90) && Math.abs(dy) > Math.abs(dx) && vy < -600) {
                    close();
                    return true;
                }
                return false;
            }
        });
        IntentFilter f = new IntentFilter(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(unlocked, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(unlocked, f);
        load();
        build();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        load();
        show();
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(unlocked);
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        if (gd != null && gd.onTouchEvent(e)) {
            MotionEvent cancel = MotionEvent.obtain(e);
            cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel);
            cancel.recycle();
            return true;
        }
        return super.dispatchTouchEvent(e);
    }

    void close() {
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    void load() {
        ArrayList<String[]> all = db.words("");
        ArrayList<String[]> pool = new ArrayList<>();
        for (String[] v : all) if (!"1".equals(v[6])) pool.add(v);
        if (pool.isEmpty()) pool = all;
        Collections.shuffle(pool);
        words = pool;
        idx = 0;
    }

    GradientDrawable round(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(0x26FFFFFF, 14));
        b.setStateListAnimator(null);
        b.setOnClickListener(l);
        return b;
    }

    LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
        p.setMargins(dp(4), 0, dp(4), 0);
        return p;
    }

    void build() {
        hide = Schedules.prefs(this).getBoolean("card_hide", false);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0F1B2D, 0xFF15294A, 0xFF1E3D6E}));
        root.setPadding(dp(22), dp(52), dp(22), dp(26));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("hh:mm");
        time.setTextSize(64);
        time.setTextColor(Color.WHITE);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setGravity(Gravity.CENTER);
        root.addView(time, new LinearLayout.LayoutParams(-1, -2));

        TextClock date = new TextClock(this);
        date.setFormat24Hour("M月d日 EEEE");
        date.setFormat12Hour("M月d日 EEEE");
        date.setTextSize(15);
        date.setTextColor(0xFFB8C7E0);
        date.setGravity(Gravity.CENTER);
        root.addView(date, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout middle = new LinearLayout(this);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setGravity(Gravity.CENTER);
        root.addView(middle, new LinearLayout.LayoutParams(-1, 0, 1));

        counter = text("", 12, 0xFF8FA6C8);
        counter.setLetterSpacing(0.2f);
        counter.setGravity(Gravity.CENTER);
        counter.setPadding(0, 0, 0, dp(10));
        middle.addView(counter, new LinearLayout.LayoutParams(-1, -2));

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(round(0x1AFFFFFF, 24));
        card.setPadding(dp(22), dp(30), dp(22), dp(30));
        card.setMinimumHeight(dp(260));

        wordV = text("", 38, Color.WHITE);
        wordV.setTypeface(null, Typeface.BOLD);
        wordV.setGravity(Gravity.CENTER);
        card.addView(wordV, new LinearLayout.LayoutParams(-1, -2));

        phV = text("", 17, 0xFFB8C7E0);
        phV.setGravity(Gravity.CENTER);
        phV.setPadding(0, dp(6), 0, 0);
        card.addView(phV, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackground(round(0x665B8DEF, 2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(36), dp(3));
        lp.setMargins(0, dp(16), 0, dp(16));
        card.addView(line, lp);

        zhV = text("", 20, 0xFFDCE7FF);
        zhV.setGravity(Gravity.CENTER);
        card.addView(zhV, new LinearLayout.LayoutParams(-1, -2));

        ctxV = text("", 14, 0xFF9DB0CC);
        ctxV.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
        ctxV.setGravity(Gravity.CENTER);
        ctxV.setLineSpacing(0, 1.2f);
        ctxV.setPadding(0, dp(16), 0, 0);
        card.addView(ctxV, new LinearLayout.LayoutParams(-1, -2));

        card.setOnClickListener(x -> reveal());
        card.setOnLongClickListener(x -> {
            if (words.isEmpty()) return true;
            String[] v = words.get(idx);
            db.master(v[0], true);
            Toast.makeText(this, "已掌握：" + v[1], Toast.LENGTH_SHORT).show();
            words.remove(idx);
            if (words.isEmpty()) load();
            if (idx >= words.size()) idx = 0;
            show();
            return true;
        });
        middle.addView(card, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(0, dp(12), 0, 0);
        hideBtn = button(hide ? "显示中文" : "遮住中文", x -> {
            hide = !hide;
            Schedules.prefs(this).edit().putBoolean("card_hide", hide).apply();
            hideBtn.setText(hide ? "显示中文" : "遮住中文");
            show();
        });
        row.addView(hideBtn, weight());
        row.addView(button("打开 App", x -> openApp()), weight());
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));

        TextView tip = text("左右滑动换词 · 轻点显示释义 · 长按已掌握 · 上滑关闭", 12, 0x88FFFFFF);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(14), 0, 0);
        root.addView(tip, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        show();
    }

    void show() {
        if (wordV == null) return;
        if (words.isEmpty()) {
            counter.setText("");
            wordV.setText("生词本还是空的");
            phV.setText("");
            zhV.setText("");
            ctxV.setText("");
            return;
        }
        String[] v = words.get(idx);
        counter.setText((idx + 1) + " / " + words.size());
        wordV.setText(v[1]);
        String ph = v[7];
        phV.setText(ph);
        phV.setVisibility(ph.isEmpty() ? View.GONE : View.VISIBLE);
        String zh = v[2].isEmpty() ? "（还没有中文释义）" : v[2];
        if (hide) {
            zhV.setText("轻点显示释义");
            zhV.setTextColor(0x77FFFFFF);
            ctxV.setVisibility(View.GONE);
        } else {
            zhV.setText(zh);
            zhV.setTextColor(0xFFDCE7FF);
            ctxV.setVisibility(v[3].isEmpty() ? View.GONE : View.VISIBLE);
        }
        ctxV.setText(v[3]);
    }

    void reveal() {
        if (words.isEmpty()) return;
        String[] v = words.get(idx);
        zhV.setText(v[2].isEmpty() ? "（还没有中文释义）" : v[2]);
        zhV.setTextColor(0xFFDCE7FF);
        ctxV.setVisibility(v[3].isEmpty() ? View.GONE : View.VISIBLE);
    }

    void move(int dir) {
        if (animating || words.size() < 2) return;
        animating = true;
        final float w = card.getWidth() > 0 ? card.getWidth() : dp(320);
        card.animate().translationX(-dir * w).alpha(0f).setDuration(160).withEndAction(() -> {
            idx = (idx + dir + words.size()) % words.size();
            show();
            card.setTranslationX(dir * w);
            card.animate().translationX(0f).alpha(1f).setDuration(180).withEndAction(() -> animating = false).start();
        }).start();
    }

    void openApp() {
        final Context app = getApplicationContext();
        final Intent main = new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && km.isKeyguardLocked()) {
            km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                @Override
                public void onDismissSucceeded() {
                    app.startActivity(main);
                    finish();
                }
            });
        } else {
            app.startActivity(main);
            finish();
        }
    }
}
