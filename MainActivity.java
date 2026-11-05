package com.xiangjiaojiaozhu.englishclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int PICK = 9;
    static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['\u2019-][A-Za-z]+)*");

    final int NAVY = Color.rgb(26, 38, 58);
    final int MUTED = Color.rgb(108, 117, 132);
    final int BLUE = Color.rgb(36, 107, 206);
    final int BG = Color.rgb(247, 249, 252);
    final int MARK = 0xFFFFE7A3;

    Db db;
    LinearLayout body;
    ScrollView scroll;
    Button[] tabs = new Button[4];
    int tab = 0;
    final Handler h = new Handler(Looper.getMainLooper());

    TextView clock;
    Button startBtn;
    int focusMin = 25;
    long end = 0, pausedLeft = -1;
    boolean run = false;
    final Runnable tickR = this::tick;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        db = new Db(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        }
        shell();
        go(0);
        share(getIntent());
        Schedules.scheduleLock(this);
        if (Schedules.prefs(this).getBoolean("card_on", false)) LockService.start(this);
        Phonetic.backfill(this, () -> runOnUiThread(() -> {
            WordWidget.refresh(this);
            if (tab == 1) words();
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == 1 && body != null) words();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        share(i);
    }

    int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    TextView text(String s, float sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0, 1.2f);
        return t;
    }

    TextView hint(String s) {
        TextView t = text(s, 14, MUTED);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        return t;
    }

    Button soft(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(NAVY);
        b.setBackgroundResource(R.drawable.button_soft);
        b.setStateListAnimator(null);
        b.setPadding(dp(8), 0, dp(8), 0);
        b.setOnClickListener(l);
        return b;
    }

    Button primary(String s, View.OnClickListener l) {
        Button b = soft(s, l);
        b.setTextColor(Color.WHITE);
        b.setTypeface(null, Typeface.BOLD);
        b.setBackgroundResource(R.drawable.button_primary);
        return b;
    }

    LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(46), w);
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundResource(R.drawable.card);
        c.setPadding(dp(18), dp(16), dp(18), dp(16));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(6), 0, dp(6));
        c.setLayoutParams(p);
        return c;
    }

    EditText input(String hintText) {
        EditText e = new EditText(this);
        e.setHint(hintText);
        e.setTextSize(15);
        e.setTextColor(NAVY);
        return e;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void shell() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setFitsSystemWindows(true);

        TextView brand = text("English Clock", 19, NAVY);
        brand.setTypeface(null, Typeface.BOLD);
        brand.setPadding(dp(20), dp(14), dp(20), dp(2));
        root.addView(brand);

        scroll = new ScrollView(this);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = row();
        nav.setBackgroundResource(R.drawable.nav_bg);
        nav.setPadding(dp(8), dp(2), dp(8), dp(2));
        String[] names = {"阅读", "生词", "专注", "提醒"};
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(15);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setStateListAnimator(null);
            b.setOnClickListener(v -> go(k));
            tabs[i] = b;
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    void go(int k) {
        tab = k;
        for (int i = 0; i < 4; i++) {
            tabs[i].setTextColor(i == k ? BLUE : MUTED);
            tabs[i].setTypeface(null, i == k ? Typeface.BOLD : Typeface.NORMAL);
        }
        if (k == 0) home();
        if (k == 1) words();
        if (k == 2) focus();
        if (k == 3) alarm();
    }

    void head(String title, String sub) {
        body.removeAllViews();
        scroll.scrollTo(0, 0);
        TextView t = text(title, 28, NAVY);
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(dp(4), dp(14), dp(4), dp(2));
        body.addView(t);
        TextView s = text(sub, 14, MUTED);
        s.setPadding(dp(4), 0, dp(4), dp(12));
        body.addView(s);
    }

    void home() {
        head("今日阅读", "把文章读完，也把不认识的词留下来。");
        LinearLayout r = row();
        r.addView(primary("粘贴文章", v -> paste()), weight(1));
        r.addView(soft("导入文件", v -> pick()), weight(1));
        body.addView(r);
        body.addView(hint("支持 TXT / DOCX / PDF，扫描版 PDF 会自动识别文字。也可以在微信、浏览器里分享到 English Clock。"));

        ArrayList<String[]> arts = db.arts();
        if (arts.isEmpty()) body.addView(hint("还没有文章。"));
        for (String[] a : arts) {
            LinearLayout c = card();
            TextView t = text(a[1], 17, NAVY);
            t.setTypeface(null, Typeface.BOLD);
            c.addView(t);
            String preview = a[2].replaceAll("\\s+", " ").trim();
            if (preview.length() > 90) preview = preview.substring(0, 90) + "…";
            TextView p = text(preview, 14, MUTED);
            p.setPadding(0, dp(6), 0, dp(6));
            c.addView(p);
            c.addView(text(a[3], 12, MUTED));
            c.setOnClickListener(v -> read(a));
            body.addView(c);
        }
    }

    void paste() {
        EditText title = input("标题（可不填）");
        title.setSingleLine(true);
        EditText txt = input("在这里粘贴英文文章");
        txt.setMinLines(8);
        txt.setMaxLines(14);
        txt.setGravity(Gravity.TOP);
        txt.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(20), dp(8), dp(20), 0);
        p.addView(title);
        p.addView(txt);
        new AlertDialog.Builder(this)
                .setTitle("新文章")
                .setView(p)
                .setPositiveButton("保存", (d, w) -> {
                    String body = txt.getText().toString().trim();
                    if (body.isEmpty()) return;
                    String t = title.getText().toString().trim();
                    db.addA(t.isEmpty() ? "文章 " + Db.day() : t, body);
                    go(0);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    void pick() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/plain", "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"});
        startActivityForResult(i, PICK);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == PICK && res == RESULT_OK && data != null && data.getData() != null) importUri(data.getData());
    }

    void importUri(Uri u) {
        Toast.makeText(this, "正在导入…扫描版 PDF 需要多等一会儿", Toast.LENGTH_LONG).show();
        new Thread(() -> {
            try {
                String name = ImportUtil.name(this, u);
                String title = name.replaceAll("\\.[A-Za-z0-9]+$", "");
                String content = ImportUtil.read(this, u).trim();
                if (content.isEmpty()) throw new Exception("没有识别到文字");
                runOnUiThread(() -> {
                    db.addA(title, content);
                    go(0);
                    toast("导入完成");
                });
            } catch (Throwable e) {
                String m = e.getMessage() == null ? e.toString() : e.getMessage();
                runOnUiThread(() -> Toast.makeText(this, "导入失败：" + m, Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @SuppressWarnings("deprecation")
    void share(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String x = i.getStringExtra(Intent.EXTRA_TEXT);
        if (x != null && x.trim().length() > 0) {
            String sub = i.getStringExtra(Intent.EXTRA_SUBJECT);
            db.addA(sub == null || sub.trim().isEmpty() ? "分享的文章 " + Db.day() : sub.trim(), x.trim());
            go(0);
            toast("已导入");
        } else {
            Uri u = i.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) importUri(u);
        }
        i.setAction(null);
    }

    void read(String[] a) {
        head(a[1], "轻触单词加入生词本，再点一次取消。黄色表示已收藏。");
        LinearLayout c = card();
        c.setPadding(dp(20), dp(18), dp(20), dp(22));
        TextView tv = text("", 19, NAVY);
        tv.setTypeface(Typeface.SERIF);
        tv.setLineSpacing(0, 1.45f);
        tv.setHighlightColor(Color.TRANSPARENT);
        SpannableString sp = new SpannableString(a[2]);
        Matcher m = WORD.matcher(a[2]);
        while (m.find()) {
            final String w = m.group();
            sp.setSpan(new ClickableSpan() {
                @Override
                public void onClick(View v) {
                    boolean added = db.toggle(w, sentence(a[2], w), a[1]);
                    if (added) translate(w);
                    paint(sp, a[2]);
                    tv.setText(sp);
                    WordWidget.refresh(MainActivity.this);
                    toast(added ? "已加入：" + w : "已取消：" + w);
                }

                @Override
                public void updateDrawState(TextPaint p) {
                    p.setColor(NAVY);
                    p.setUnderlineText(false);
                }
            }, m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        paint(sp, a[2]);
        tv.setText(sp);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        c.addView(tv);
        body.addView(c);

        Button del = soft("删除这篇文章", v -> new AlertDialog.Builder(this)
                .setMessage("删除这篇文章？生词会保留。")
                .setPositiveButton("删除", (d, w) -> { db.delA(a[0]); go(0); })
                .setNegativeButton("取消", null)
                .show());
        del.setTextColor(Color.rgb(196, 60, 60));
        LinearLayout r = row();
        r.addView(soft("返回", v -> go(0)), weight(1));
        r.addView(del, weight(1));
        body.addView(r);
    }

    void paint(SpannableString sp, String src) {
        for (BackgroundColorSpan s : sp.getSpans(0, sp.length(), BackgroundColorSpan.class)) sp.removeSpan(s);
        HashSet<String> set = db.wordSet();
        Matcher m = WORD.matcher(src);
        while (m.find()) {
            if (set.contains(m.group().toLowerCase(Locale.ROOT))) {
                sp.setSpan(new BackgroundColorSpan(MARK), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
    }

    String sentence(String t, String w) {
        Matcher m = Pattern.compile("(?i)(?<![A-Za-z])" + Pattern.quote(w) + "(?![A-Za-z])").matcher(t);
        if (!m.find()) return "";
        int s = m.start(), e = m.end();
        String stops = ".!?\n";
        while (s > 0 && stops.indexOf(t.charAt(s - 1)) < 0) s--;
        while (e < t.length() && stops.indexOf(t.charAt(e)) < 0) e++;
        if (e < t.length() && t.charAt(e) != '\n') e++;
        String r = t.substring(s, e).trim().replaceAll("\\s+", " ");
        return r.length() > 240 ? r.substring(0, 240) + "…" : r;
    }

    /** looks up Chinese meaning and phonetic for a newly added word */
    void translate(String word) {
        new Thread(() -> {
            try {
                URL u = new URL("https://api.mymemory.translated.net/get?langpair=en%7Czh-CN&q=" + URLEncoder.encode(word, "UTF-8"));
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(8000);
                String raw = ImportUtil.stream(c.getInputStream());
                String zh = new JSONObject(raw).getJSONObject("responseData").optString("translatedText", "").trim();
                if (!zh.isEmpty() && !zh.equalsIgnoreCase(word)) db.zhIfEmpty(word, zh);
            } catch (Exception ignored) {
            }
            String ph = Phonetic.lookup(word);
            if (!ph.isEmpty()) db.phonetic(word, ph);
            runOnUiThread(() -> WordWidget.refresh(this));
        }).start();
    }

    void words() {
        ArrayList<String[]> all = db.words("");
        int left = 0;
        for (String[] v : all) if (!"1".equals(v[6])) left++;
        head("生词本", "共 " + all.size() + " 个 · 待掌握 " + left + " 个");
        lockCard();

        EditText q = input("搜索单词或中文");
        body.addView(q, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list);

        Runnable draw = () -> {
            list.removeAllViews();
            ArrayList<String[]> ws = db.words(q.getText().toString().trim());
            if (ws.isEmpty()) list.addView(hint("还没有生词。打开一篇文章，点一下不认识的单词就会加到这里。"));
            for (String[] v : ws) {
                boolean done = "1".equals(v[6]);
                LinearLayout c = card();
                LinearLayout top = row();
                top.setGravity(Gravity.BOTTOM);
                TextView wv = text(v[1], 20, done ? MUTED : NAVY);
                wv.setTypeface(null, Typeface.BOLD);
                top.addView(wv);
                TextView ph = text(v[7].isEmpty() ? "" : "  " + v[7], 14, MUTED);
                ph.setPadding(0, 0, 0, dp(2));
                top.addView(ph, new LinearLayout.LayoutParams(0, -2, 1));
                if (done) top.addView(text("已掌握", 12, BLUE));
                c.addView(top);
                boolean noZh = v[2].isEmpty();
                c.addView(text(noZh ? "点这里填写中文释义" : v[2], 16, noZh ? BLUE : NAVY));
                if (!v[3].isEmpty()) {
                    TextView ctx = text(v[3], 13, MUTED);
                    ctx.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
                    ctx.setPadding(0, dp(6), 0, 0);
                    c.addView(ctx);
                }
                c.setOnClickListener(x -> edit(v));
                list.addView(c);
            }
        };
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { draw.run(); }
            public void afterTextChanged(Editable e) { }
        });
        draw.run();
    }

    void lockCard() {
        SharedPreferences p = Schedules.prefs(this);
        boolean on = p.getBoolean("card_on", false);
        boolean hide = p.getBoolean("card_hide", false);
        boolean overlay = Settings.canDrawOverlays(this);
        LinearLayout c = card();
        TextView t = text("锁屏单词", 17, NAVY);
        t.setTypeface(null, Typeface.BOLD);
        c.addView(t);
        c.addView(text(on ? "已开启 · 亮屏先看到生词卡片，左右滑动换词，上滑关闭"
                : "开启后，每次亮屏会先出现一组生词卡片，上滑关闭后再正常解锁", 14, MUTED));
        if (!overlay) {
            TextView warn = text("还差一步：需要允许「显示在其他应用的上层」", 14, Color.rgb(196, 60, 60));
            warn.setPadding(0, dp(6), 0, 0);
            c.addView(warn);
        }
        LinearLayout r1 = row();
        r1.addView(on ? soft("关闭", v -> setCard(false)) : primary("开启", v -> setCard(true)), weight(1));
        r1.addView(soft("预览", v -> preview()), weight(1));
        c.addView(r1);
        LinearLayout r2 = row();
        r2.addView(soft(hide ? "中文：遮住" : "中文：显示", v -> {
            p.edit().putBoolean("card_hide", !hide).apply();
            words();
        }), weight(1));
        r2.addView(soft(overlay ? "其他权限" : "去授权", v -> openPerms(overlay)), weight(1));
        c.addView(r2);
        TextView tip = text("华为手机还需要：设置 → 应用和服务 → 应用管理 → English Clock → 权限，打开「锁屏显示」和「后台弹出界面」；"
                + "并在「应用启动管理」里改为手动管理，允许后台活动。", 12, MUTED);
        tip.setPadding(0, dp(6), 0, 0);
        c.addView(tip);
        body.addView(c);
    }

    void setCard(boolean on) {
        Schedules.prefs(this).edit().putBoolean("card_on", on).apply();
        if (on) {
            LockService.start(this);
            if (!Settings.canDrawOverlays(this)) openPerms(false);
            else Toast.makeText(this, "已开启，锁屏后再亮屏看看", Toast.LENGTH_LONG).show();
        } else {
            LockService.stop(this);
            toast("已关闭");
        }
        words();
    }

    void preview() {
        if (db.words("").isEmpty()) {
            toast("生词本还是空的，先在文章里点几个生词");
            return;
        }
        startActivity(new Intent(this, LockActivity.class));
    }

    void openPerms(boolean overlayGranted) {
        Uri pkg = Uri.parse("package:" + getPackageName());
        try {
            if (!overlayGranted) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkg));
                Toast.makeText(this, "打开「允许显示在其他应用上层」后返回", Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
                Toast.makeText(this, "在「权限」里打开「锁屏显示」和「后台弹出界面」", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg));
        }
    }

    void edit(String[] v) {
        EditText ph = input("音标，例如 /rɪˈzɪliəns/");
        ph.setText(v[7]);
        ph.setSingleLine(true);
        EditText z = input("中文释义");
        z.setText(v[2]);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(20), 0, dp(20), 0);
        p.addView(ph, new LinearLayout.LayoutParams(-1, -2));
        p.addView(z, new LinearLayout.LayoutParams(-1, -2));
        boolean done = "1".equals(v[6]);
        new AlertDialog.Builder(this)
                .setTitle(v[1])
                .setMessage(v[3].isEmpty() ? "来源：" + v[4] : v[3] + "\n\n来源：" + v[4])
                .setView(p)
                .setPositiveButton("保存", (d, w) -> {
                    db.zh(v[0], z.getText().toString().trim());
                    db.phonetic(v[1], ph.getText().toString().trim());
                    WordWidget.refresh(this);
                    words();
                })
                .setNeutralButton(done ? "设为未掌握" : "已掌握", (d, w) -> { db.master(v[0], !done); words(); })
                .setNegativeButton("删除", (d, w) -> { db.delW(v[0]); WordWidget.refresh(this); words(); })
                .show();
    }

    String fmt(long ms) {
        long s = Math.max(0, (ms + 999) / 1000);
        return String.format(Locale.ROOT, "%02d:%02d", s / 60, s % 60);
    }

    void focus() {
        head("专注", "只做一件事。到点后再停下来。");
        LinearLayout c = card();
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        long show = run ? end - System.currentTimeMillis() : (pausedLeft > 0 ? pausedLeft : focusMin * 60000L);
        clock = text(fmt(show), 60, NAVY);
        clock.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        clock.setGravity(Gravity.CENTER);
        clock.setPadding(0, dp(16), 0, dp(16));
        c.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout presets = row();
        for (int m : new int[]{15, 25, 45, 60}) {
            presets.addView(soft(m + " 分", v -> {
                if (run) return;
                focusMin = m;
                pausedLeft = -1;
                clock.setText(fmt(m * 60000L));
                startBtn.setText("开始");
            }), weight(1));
        }
        c.addView(presets, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ctrl = row();
        startBtn = primary(run ? "暂停" : (pausedLeft > 0 ? "继续" : "开始"), v -> toggleTimer());
        ctrl.addView(startBtn, weight(2));
        ctrl.addView(soft("重置", v -> resetTimer()), weight(1));
        c.addView(ctrl, new LinearLayout.LayoutParams(-1, -2));
        body.addView(c);
        if (run) tick();
    }

    void toggleTimer() {
        if (run) {
            run = false;
            pausedLeft = end - System.currentTimeMillis();
            h.removeCallbacks(tickR);
            startBtn.setText("继续");
        } else {
            long len = pausedLeft > 0 ? pausedLeft : focusMin * 60000L;
            end = System.currentTimeMillis() + len;
            pausedLeft = -1;
            run = true;
            startBtn.setText("暂停");
            tick();
        }
    }

    void resetTimer() {
        run = false;
        pausedLeft = -1;
        h.removeCallbacks(tickR);
        clock.setText(fmt(focusMin * 60000L));
        startBtn.setText("开始");
    }

    void tick() {
        h.removeCallbacks(tickR);
        if (!run) return;
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            run = false;
            pausedLeft = -1;
            if (clock != null) clock.setText("00:00");
            if (startBtn != null) startBtn.setText("开始");
            Notify.post(this, 7, "focus", "专注计时", NotificationManager.IMPORTANCE_HIGH, "专注时间到", "休息一下吧");
            return;
        }
        if (clock != null) clock.setText(fmt(left));
        h.postDelayed(tickR, 500);
    }

    void alarm() {
        head("学习提醒", "给每天的英语留一个固定时间。");
        SharedPreferences p = Schedules.prefs(this);
        boolean on = p.getBoolean("alarm_on", false);
        LinearLayout c = card();
        c.addView(text(on ? String.format(Locale.ROOT, "每天 %02d:%02d · %s", p.getInt("alarm_h", 20), p.getInt("alarm_m", 0),
                p.getString("alarm_msg", "背单词")) : "还没有设置提醒", 15, on ? BLUE : MUTED));
        TimePicker tp = new TimePicker(this);
        tp.setIs24HourView(true);
        tp.setHour(p.getInt("alarm_h", 20));
        tp.setMinute(p.getInt("alarm_m", 0));
        c.addView(tp, new LinearLayout.LayoutParams(-1, -2));
        EditText msg = input("提醒内容");
        msg.setText(p.getString("alarm_msg", "背单词"));
        c.addView(msg, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout r = row();
        r.addView(primary("保存", v -> {
            String m = msg.getText().toString().trim();
            p.edit().putBoolean("alarm_on", true).putInt("alarm_h", tp.getHour()).putInt("alarm_m", tp.getMinute())
                    .putString("alarm_msg", m.isEmpty() ? "背单词" : m).apply();
            Schedules.scheduleDaily(this);
            toast("已设置每天提醒");
            alarm();
        }), weight(2));
        r.addView(soft("关闭", v -> {
            p.edit().putBoolean("alarm_on", false).apply();
            Schedules.scheduleDaily(this);
            toast("已关闭");
            alarm();
        }), weight(1));
        c.addView(r, new LinearLayout.LayoutParams(-1, -2));
        body.addView(c);
        body.addView(hint("华为手机建议：设置 → 应用和服务 → 应用启动管理 → English Clock 改为手动管理，并打开允许后台活动，否则提醒可能延迟。"));
    }
}
