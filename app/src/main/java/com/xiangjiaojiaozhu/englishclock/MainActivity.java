package com.xiangjiaojiaozhu.englishclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
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
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONObject;

public class MainActivity extends Activity {
    static final int PICK = 9;
    static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:['\u2019-][A-Za-z]+)*");

    final int NAVY = Color.rgb(26, 38, 58);
    final int MUTED = Color.rgb(108, 117, 132);
    final int BLUE = Color.rgb(36, 107, 206);
    final int RED = Color.rgb(196, 60, 60);
    final int BG = Color.rgb(247, 249, 252);
    final int MARK = 0xFFFFE7A3;

    Db db;
    LinearLayout body;
    ScrollView scroll;
    Button[] tabs = new Button[5];
    int tab = 0;
    final Handler h = new Handler(Looper.getMainLooper());

    // fast-scroll handle on the right edge
    View thumb;
    boolean dragging;
    float dragStartY;
    int dragStartScroll;

    TextView clock;
    Button startBtn;
    int focusMin = 25;
    long end = 0, pausedLeft = -1;
    boolean run = false;
    final Runnable tickR = this::tick;

    // account sync
    boolean syncing, choosingFolder;
    String lastSyncMsg = "", lastOwn = "", lastFriend = "";
    long syncedVersion = -1, nextPull = 0;
    int deviceCount = 0;
    Map<String, ShareSync.Doc> devices = new HashMap<>();
    Map<String, String> devStamps = new HashMap<>();
    LinkedHashMap<String, ShareSync.Doc> friends = new LinkedHashMap<>();
    Map<String, String> friendStamps = new HashMap<>();
    TextView syncStatus;
    LinearLayout friendList;
    View friendArtHeader;
    String curFriendKey = null, friendQuery = "";
    String[] openArticle = null;
    final Runnable syncTickR = this::syncTick;

    @Override
    protected void onCreate(Bundle s) {
        super.onCreate(s);
        db = new Db(this);
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 3);
        }
        ShareSync.migrate(this);
        friends = ShareSync.loadCache(this);
        shell();
        go(0);
        share(getIntent());
        Schedules.scheduleLock(this);
        if (Schedules.prefs(this).getBoolean("card_on", false)) LockService.start(this);
        backfill();
    }

    void backfill() {
        Phonetic.backfill(this, () -> runOnUiThread(() -> {
            WordWidget.refresh(this);
            if (tab == 1) words();
        }));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tab == 1 && body != null) words();
        nextPull = 0;
        h.removeCallbacks(syncTickR);
        h.postDelayed(syncTickR, 1500);
    }

    @Override
    protected void onPause() {
        super.onPause();
        h.removeCallbacks(syncTickR);
        if (ShareSync.loggedIn(this) && !syncing && Db.version != syncedVersion) startSync(false);
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

    LinearLayout.LayoutParams full() {
        return new LinearLayout.LayoutParams(-1, -2);
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

    void alert(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("知道了", null).show();
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

        FrameLayout wrap = new FrameLayout(this);
        scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(body);
        wrap.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        setupFastScroll(wrap);
        root.addView(wrap, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout nav = row();
        nav.setBackgroundResource(R.drawable.nav_bg);
        nav.setPadding(dp(4), dp(2), dp(4), dp(2));
        String[] names = {"阅读", "生词", "专注", "提醒", "账号"};
        for (int i = 0; i < names.length; i++) {
            final int k = i;
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(14);
            b.setBackgroundColor(Color.TRANSPARENT);
            b.setStateListAnimator(null);
            b.setPadding(0, 0, 0, 0);
            b.setOnClickListener(v -> { choosingFolder = false; go(k); });
            tabs[i] = b;
            nav.addView(b, new LinearLayout.LayoutParams(0, dp(54), 1));
        }
        root.addView(nav);
        setContentView(root);
    }

    // ---------------------------------------------------------------- fast scroll
    void setupFastScroll(FrameLayout wrap) {
        GradientDrawable bar = new GradientDrawable();
        bar.setColor(0xFF7F8BA0);
        bar.setCornerRadius(dp(4));
        thumb = new View(this);
        thumb.setBackground(new InsetDrawable(bar, dp(21), dp(2), dp(2), dp(2)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(30), dp(64), Gravity.END | Gravity.TOP);
        wrap.addView(thumb, lp);
        thumb.setVisibility(View.GONE);
        thumb.setAlpha(0.6f);

        thumb.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragging = true;
                    dragStartY = e.getRawY();
                    dragStartScroll = scroll.getScrollY();
                    thumb.setAlpha(1f);
                    v.getParent().requestDisallowInterceptTouchEvent(true);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    int track = scroll.getHeight() - thumb.getHeight();
                    int range = body.getHeight() - scroll.getHeight();
                    if (track > 0 && range > 0) {
                        float dy = e.getRawY() - dragStartY;
                        int y = Math.round(dragStartScroll + dy * range / track);
                        scroll.scrollTo(0, Math.max(0, Math.min(range, y)));
                    }
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    thumb.setAlpha(0.6f);
                    return true;
            }
            return false;
        });
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> updateThumb());
        body.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> thumb.post(this::updateThumb));
    }

    void updateThumb() {
        if (thumb == null) return;
        int view = scroll.getHeight(), content = body.getHeight();
        if (view <= 0 || content < view * 1.6f) {
            if (thumb.getVisibility() != View.GONE) thumb.setVisibility(View.GONE);
            return;
        }
        if (thumb.getVisibility() != View.VISIBLE) thumb.setVisibility(View.VISIBLE);
        int track = view - thumb.getHeight();
        int range = content - view;
        float y = range <= 0 ? 0 : (float) scroll.getScrollY() * track / range;
        thumb.setTranslationY(Math.max(0, Math.min(track, y)));
    }

    void scrollToView(View v) {
        int y = 0;
        View c = v;
        while (c != null && c != body) {
            y += c.getTop();
            if (!(c.getParent() instanceof View)) break;
            c = (View) c.getParent();
        }
        scroll.smoothScrollTo(0, Math.max(0, y - dp(8)));
    }

    void go(int k) {
        tab = k;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == k ? BLUE : MUTED);
            tabs[i].setTypeface(null, i == k ? Typeface.BOLD : Typeface.NORMAL);
        }
        syncStatus = null;
        friendList = null;
        friendArtHeader = null;
        openArticle = null;
        if (k == 0) home();
        if (k == 1) words();
        if (k == 2) focus();
        if (k == 3) alarm();
        if (k == 4) showAccount();
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
        openArticle = a;
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
                    boolean added = db.toggle(w, sentence(a[2], w), a[1], "");
                    if (added) lookupNew(w);
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
                .setMessage("删除这篇文章？生词会保留。已登录账号时，其他设备上也会删除。")
                .setPositiveButton("删除", (d, w) -> { db.delA(a[0]); go(0); })
                .setNegativeButton("取消", null)
                .show());
        del.setTextColor(RED);
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

    void lookupNew(String word) {
        new Thread(() -> {
            String ph = Phonetic.lookup(this, word);
            if (!ph.isEmpty()) db.phonetic(word, ph);
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
            runOnUiThread(() -> {
                WordWidget.refresh(this);
                if (tab == 1) words();
            });
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
                TextView ph = text(v[7].isEmpty() ? "" : "  " + v[7], 15, MUTED);
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
                : "开启后，每次亮屏会先出现生词卡片，上滑关闭后再正常解锁", 14, MUTED));
        if (!overlay) {
            TextView warn = text("还差一步：需要允许「显示在其他应用的上层」", 14, RED);
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
                    String nz = z.getText().toString().trim(), np = ph.getText().toString().trim();
                    if (!nz.equals(v[2])) db.zh(v[0], nz);
                    if (!np.equals(v[7])) db.phonetic(v[1], np);
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

    // ================================================================ account tab
    void showAccount() {
        syncStatus = null;
        friendList = null;
        friendArtHeader = null;
        if (!ShareSync.loggedIn(this)) accountLogin();
        else if (choosingFolder) chooseFriendFolder();
        else accountMain();
    }

    void accountLogin() {
        head("账号", "登录后，手机和电脑的生词本自动保持一致。");
        LinearLayout c = card();
        c.addView(text("用坚果云账号登录。电脑、手机登录同一个账号后，生词、文章、中文释义、音标、掌握状态都会自动同步，删除也会同步。\n"
                + "坚果云要求第三方 App 使用「应用密码」，不是平时的登录密码。", 13, MUTED));
        EditText u = input("坚果云账号（邮箱 / 手机号）");
        u.setSingleLine(true);
        u.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        u.setText(ShareSync.user(this));
        EditText p = input("应用密码");
        p.setSingleLine(true);
        p.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        c.addView(u, full());
        c.addView(p, full());
        LinearLayout r = row();
        Button login = primary("登录", null);
        login.setOnClickListener(v -> doLogin(u.getText().toString().trim(), p.getText().toString().trim(), login));
        r.addView(login, weight(1));
        r.addView(soft("获取应用密码", v -> passHelp()), weight(1));
        c.addView(r, full());
        body.addView(c);
        body.addView(hint("还没有坚果云账号？到 jianguoyun.com 免费注册一个。不登录也能正常使用，只是不同步。"));
    }

    void passHelp() {
        alert("怎么获取应用密码",
                "1. 用浏览器登录 jianguoyun.com\n"
                        + "2. 右上角头像 → 账户信息 → 安全选项\n"
                        + "3. 在「第三方应用管理」里点「添加应用」，名称填 EnglishClock\n"
                        + "4. 点「生成密码」，把生成的那串字符填到这里\n\n"
                        + "同一个账号的电脑和手机都填这同一个应用密码。\n"
                        + "不想用了，在坚果云里删掉这个应用密码即可，不影响账号本身。");
    }

    void doLogin(String user, String pass, Button btn) {
        if (user.isEmpty() || pass.isEmpty()) { toast("请填写坚果云账号和应用密码"); return; }
        btn.setEnabled(false);
        btn.setText("正在连接…");
        new Thread(() -> {
            String err = ShareSync.login(user, pass);
            runOnUiThread(() -> {
                if (err != null) {
                    btn.setEnabled(true);
                    btn.setText("登录");
                    alert("登录失败", err);
                    return;
                }
                String enc = ShareSync.protect(pass);
                if (enc.isEmpty()) {
                    btn.setEnabled(true);
                    btn.setText("登录");
                    alert("登录失败", "无法在手机上保存密码。");
                    return;
                }
                SharedPreferences.Editor e = Schedules.prefs(this).edit();
                if (!user.equalsIgnoreCase(ShareSync.user(this))) {
                    e.putString("friend_folder", "").putString("friend_folder_name", "");
                    clearFriends();
                }
                e.putString("sync_user", user).putString("sync_pass", enc).apply();
                devices.clear();
                devStamps.clear();
                lastOwn = "";
                lastFriend = "";
                lastSyncMsg = "";
                go(4);
                startSync(true);
            });
        }).start();
    }

    void logout() {
        new AlertDialog.Builder(this)
                .setMessage("退出登录后不再同步，手机上的文章和生词都会保留。确定退出？")
                .setPositiveButton("退出", (d, w) -> {
                    Schedules.prefs(this).edit().putString("sync_pass", "").apply();
                    devices.clear();
                    devStamps.clear();
                    deviceCount = 0;
                    lastOwn = "";
                    lastSyncMsg = "";
                    clearFriends();
                    go(4);
                })
                .setNegativeButton("取消", null).show();
    }

    void clearFriends() {
        friends.clear();
        friendStamps.clear();
        lastFriend = "";
        curFriendKey = null;
        ShareSync.saveCache(this, friends);
    }

    void accountMain() {
        head("账号", "已登录：" + ShareSync.user(this));
        syncStatus = text("", 14, BLUE);
        syncStatus.setTypeface(null, Typeface.BOLD);
        syncStatus.setPadding(dp(4), 0, dp(4), dp(6));
        body.addView(syncStatus);
        updateStatus();
        body.addView(hint("电脑和手机登录这个账号后，生词、文章、掌握状态都会自动同步；在任何一台设备上删除，其他设备也会删除。"));
        LinearLayout r1 = row();
        r1.addView(primary("立即同步", v -> startSync(true)), weight(1));
        r1.addView(soft("退出登录", v -> logout()), weight(1));
        body.addView(r1, full());

        TextView ft = text("和好友互相看生词本（可选）", 18, NAVY);
        ft.setTypeface(null, Typeface.BOLD);
        ft.setPadding(dp(4), dp(18), dp(4), dp(4));
        body.addView(ft);

        if (ShareSync.friendFolder(this).isEmpty()) {
            body.addView(hint("好友用他自己的坚果云账号登录。你们共用一个坚果云文件夹（在坚果云网页上邀请对方，权限选「可编辑」），"
                    + "就能互相看到对方的生词本，把有用的词加到自己的生词本里。各自的数据不会混在一起。"));
            LinearLayout r2 = row();
            r2.addView(soft("设置好友共享文件夹", v -> { choosingFolder = true; go(4); }), weight(1));
            body.addView(r2, full());
            return;
        }

        body.addView(hint("我的名字：" + ShareSync.nick(this) + " · 文件夹：" + ShareSync.friendFolderName(this)));
        LinearLayout r3 = row();
        r3.addView(soft("改名字", v -> changeNick()), weight(1));
        r3.addView(soft("更换文件夹", v -> { choosingFolder = true; go(4); }), weight(1));
        r3.addView(soft("关闭", v -> {
            Schedules.prefs(this).edit().putString("friend_folder", "").putString("friend_folder_name", "").apply();
            clearFriends();
            go(4);
        }), weight(1));
        body.addView(r3, full());

        if (friends.isEmpty()) {
            body.addView(hint("还没有收到好友的数据。好友登录他自己的账号、设置同一个文件夹并同步一次后，这里就会出现他的生词本。"));
            return;
        }

        final ArrayList<String> keys = new ArrayList<>(friends.keySet());
        Collections.sort(keys, (a, b) -> friends.get(a).owner.compareTo(friends.get(b).owner));
        ArrayList<String> labels = new ArrayList<>();
        int sel = 0;
        for (int i = 0; i < keys.size(); i++) {
            ShareSync.Doc f = friends.get(keys.get(i));
            labels.add((f.owner.isEmpty() ? "（未命名）" : f.owner) + " · " + f.vocab.size() + " 词 · " + f.arts.size() + " 篇");
            if (keys.get(i).equals(curFriendKey)) sel = i;
        }
        curFriendKey = keys.get(sel);
        Spinner sp = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(ad);
        sp.setSelection(sel);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String k = keys.get(pos);
                if (!k.equals(curFriendKey)) {
                    curFriendKey = k;
                    renderFriend();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        body.addView(text("好友", 13, MUTED));
        body.addView(sp, full());

        EditText q = input("搜索好友的生词");
        q.setSingleLine(true);
        q.setText(friendQuery);
        q.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { friendQuery = s.toString().trim(); renderFriend(); }
            public void afterTextChanged(Editable e) { }
        });
        body.addView(q, full());
        LinearLayout r4 = row();
        r4.addView(primary("全部加入生词本", v -> addAllFriend()), weight(1));
        r4.addView(soft("跳到好友文章 ↓", v -> {
            if (friendArtHeader != null) scrollToView(friendArtHeader);
            else toast("这位好友还没有文章");
        }), weight(1));
        body.addView(r4, full());

        friendList = new LinearLayout(this);
        friendList.setOrientation(LinearLayout.VERTICAL);
        body.addView(friendList, full());
        renderFriend();
    }

    void chooseFriendFolder() {
        head("好友共享文件夹", "选一个你和好友共用的坚果云文件夹。");
        body.addView(hint("第一个人：点「新建」，再到坚果云网页把它共享给好友，权限选「可编辑」。\n好友：在坚果云里接受邀请后点「刷新」，选同一个文件夹。"));
        LinearLayout r = row();
        r.addView(primary("新建", v -> newFolder()), weight(1));
        r.addView(soft("刷新", v -> go(4)), weight(1));
        r.addView(soft("返回", v -> { choosingFolder = false; go(4); }), weight(1));
        body.addView(r, full());
        TextView st = hint("正在读取坚果云里的文件夹…");
        body.addView(st);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        body.addView(list, full());

        String user = ShareSync.user(this), pass = ShareSync.pass(this);
        new Thread(() -> {
            List<ShareSync.Item> dirs = new ArrayList<>();
            String err = null;
            try {
                List<ShareSync.Item> all = ShareSync.list(user, pass, ShareSync.ROOT);
                if (all != null) for (ShareSync.Item it : all) if (it.dir && !ShareSync.sameDir(it.href, ShareSync.APP)) dirs.add(it);
                Collections.sort(dirs, (a, b) -> a.name.compareToIgnoreCase(b.name));
            } catch (Exception e) {
                err = e.getMessage();
            }
            final String fe = err;
            runOnUiThread(() -> {
                if (tab != 4 || !choosingFolder) return;
                if (fe != null) {
                    st.setText("读取失败：" + fe);
                    st.setTextColor(RED);
                    return;
                }
                st.setText(dirs.isEmpty() ? "还没有文件夹，点「新建」。" : "点一个文件夹即可使用：");
                for (ShareSync.Item it : dirs) {
                    LinearLayout c = card();
                    TextView t = text(it.name, 17, NAVY);
                    t.setTypeface(null, Typeface.BOLD);
                    c.addView(t);
                    c.setOnClickListener(v -> useFolder(it.href, it.name));
                    list.addView(c);
                }
            });
        }).start();
    }

    void newFolder() {
        EditText e = input("文件夹名称");
        e.setText("EnglishClock好友");
        e.setSingleLine(true);
        LinearLayout p = new LinearLayout(this);
        p.setPadding(dp(20), 0, dp(20), 0);
        p.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("新建好友共享文件夹").setView(p)
                .setPositiveButton("新建", (d, w) -> {
                    String name = e.getText().toString().trim();
                    if (name.isEmpty()) return;
                    String user = ShareSync.user(this), pass = ShareSync.pass(this);
                    new Thread(() -> {
                        String href = null, err = null;
                        try { href = ShareSync.makeDir(user, pass, ShareSync.ROOT, name); } catch (Exception ex) { err = ex.getMessage(); }
                        final String fh = href, fe = err;
                        runOnUiThread(() -> {
                            if (fe != null) { alert("新建失败", fe); return; }
                            alert("新建成功", "已新建「" + name + "」。\n\n下一步：用浏览器打开坚果云网页 → 找到这个文件夹 → 共享 / 邀请成员 → 输入好友的坚果云账号，权限选「可编辑」。");
                            useFolder(fh, name);
                        });
                    }).start();
                })
                .setNegativeButton("取消", null).show();
    }

    void useFolder(String href, String name) {
        Schedules.prefs(this).edit().putString("friend_folder", ShareSync.dir(href)).putString("friend_folder_name", name).apply();
        clearFriends();
        choosingFolder = false;
        go(4);
        startSync(true);
    }

    void changeNick() {
        String old = ShareSync.nick(this);
        EditText e = input("好友看到的名字");
        e.setText(old);
        e.setSingleLine(true);
        LinearLayout p = new LinearLayout(this);
        p.setPadding(dp(20), 0, dp(20), 0);
        p.addView(e, new LinearLayout.LayoutParams(-1, -2));
        new AlertDialog.Builder(this).setTitle("改名字").setView(p)
                .setPositiveButton("保存", (d, w) -> {
                    String n = e.getText().toString().trim();
                    if (n.isEmpty() || n.equals(old)) return;
                    Schedules.prefs(this).edit().putString("sync_nick", n).apply();
                    go(4);
                    startSync(false);
                })
                .setNegativeButton("取消", null).show();
    }

    String owner(ShareSync.Doc f) {
        return f.owner.isEmpty() ? "好友" : f.owner;
    }

    /** key, word, zh, phonetic, context, added */
    ArrayList<String[]> friendWords() {
        ArrayList<String[]> out = new ArrayList<>();
        ShareSync.Doc f = curFriendKey == null ? null : friends.get(curFriendKey);
        if (f == null) return out;
        for (Map.Entry<String, ShareSync.W> e : f.vocab.entrySet()) {
            ShareSync.W w = e.getValue();
            out.add(new String[]{e.getKey(), w.word.isEmpty() ? e.getKey() : w.word, w.zh, w.phonetic, w.context, w.added});
        }
        Collections.reverse(out);
        Collections.sort(out, (a, b) -> b[5].compareTo(a[5]));
        String q = friendQuery.toLowerCase(Locale.ROOT);
        if (!q.isEmpty()) {
            ArrayList<String[]> starts = new ArrayList<>(), rest = new ArrayList<>();
            for (String[] r : out) {
                boolean hit = false;
                for (int i = 0; i < 5; i++) if (r[i].toLowerCase(Locale.ROOT).contains(q)) hit = true;
                if (!hit) continue;
                if (r[1].toLowerCase(Locale.ROOT).startsWith(q)) starts.add(r); else rest.add(r);
            }
            starts.addAll(rest);
            out = starts;
        }
        return out;
    }

    void renderFriend() {
        if (friendList == null) return;
        friendList.removeAllViews();
        friendArtHeader = null;
        ShareSync.Doc f = curFriendKey == null ? null : friends.get(curFriendKey);
        if (f == null) return;
        String who = owner(f);
        ArrayList<String[]> ws = friendWords();
        HashSet<String> mine = db.wordSet();
        int have = 0;
        for (String[] w : ws) if (mine.contains(w[0].toLowerCase(Locale.ROOT))) have++;
        friendList.addView(hint(who + " 的生词：显示 " + ws.size() + " 个，其中 " + have + " 个你已经有了。按住右边的灰色滑块上下拖，可以快速翻页。"));
        int shown = 0;
        for (String[] w : ws) {
            if (++shown > 400) { friendList.addView(hint("只显示前 400 个，可以用搜索找其他词。")); break; }
            boolean has = mine.contains(w[0].toLowerCase(Locale.ROOT));
            LinearLayout c = card();
            LinearLayout top = row();
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout left = new LinearLayout(this);
            left.setOrientation(LinearLayout.VERTICAL);
            TextView wv = text(w[1] + (w[3].isEmpty() ? "" : "  " + w[3]), 18, has ? MUTED : NAVY);
            wv.setTypeface(null, Typeface.BOLD);
            left.addView(wv);
            if (!w[2].isEmpty()) left.addView(text(w[2], 15, has ? MUTED : NAVY));
            top.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            if (has) top.addView(text("已有", 13, MUTED));
            else {
                Button add = soft("加入", v -> addFriendWord(w, who));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(72), dp(40));
                lp.setMargins(dp(8), 0, dp(20), 0);
                top.addView(add, lp);
            }
            c.addView(top);
            if (!w[4].isEmpty()) {
                TextView ctx = text(w[4], 13, MUTED);
                ctx.setTypeface(Typeface.create(Typeface.SERIF, Typeface.ITALIC));
                ctx.setPadding(0, dp(6), 0, 0);
                c.addView(ctx);
            }
            friendList.addView(c);
        }

        if (!f.arts.isEmpty()) {
            TextView t = text(who + " 的文章（" + f.arts.size() + " 篇）", 18, NAVY);
            t.setTypeface(null, Typeface.BOLD);
            t.setPadding(dp(4), dp(16), dp(4), dp(4));
            friendList.addView(t);
            friendArtHeader = t;
            for (ShareSync.A a : f.arts) {
                String title = a.title.isEmpty() ? "文章" : a.title, content = a.content;
                LinearLayout c = card();
                TextView tt = text(title, 16, NAVY);
                tt.setTypeface(null, Typeface.BOLD);
                c.addView(tt);
                c.addView(text(a.date, 12, MUTED));
                LinearLayout r = row();
                r.addView(soft("复制到我的文章", v -> {
                    db.addA(title + "（来自 " + who + "）", content);
                    toast("已复制到「阅读」页");
                }), weight(1));
                c.addView(r, full());
                friendList.addView(c);
            }
            LinearLayout r = row();
            r.addView(soft("回到顶部 ↑", v -> scroll.smoothScrollTo(0, 0)), weight(1));
            friendList.addView(r, full());
        }
    }

    void addFriendWord(String[] w, String who) {
        int y = scroll.getScrollY();
        if (db.addFriendWord(w[1], w[2], w[3], w[4], "来自 " + who)) {
            toast("已加入：" + w[1]);
            WordWidget.refresh(this);
        } else toast("你已经有这个词了");
        renderFriend();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    void addAllFriend() {
        ShareSync.Doc f = curFriendKey == null ? null : friends.get(curFriendKey);
        if (f == null) return;
        ArrayList<String[]> ws = friendWords();
        String who = owner(f);
        new AlertDialog.Builder(this)
                .setMessage("把列表里的 " + ws.size() + " 个词全部加入你的生词本？（你已有的会跳过）")
                .setPositiveButton("加入", (d, x) -> {
                    int n = 0;
                    for (String[] w : ws) if (db.addFriendWord(w[1], w[2], w[3], w[4], "来自 " + who)) n++;
                    toast("已加入 " + n + " 个词");
                    WordWidget.refresh(this);
                    renderFriend();
                })
                .setNegativeButton("取消", null).show();
    }

    String statusText() {
        if (syncing) return "正在同步…";
        if (!lastSyncMsg.isEmpty()) return lastSyncMsg;
        long t = Schedules.prefs(this).getLong("last_sync", 0);
        if (t > 0) return "已同步 · 上次 " + new SimpleDateFormat("MM-dd HH:mm:ss", Locale.ROOT).format(new Date(t))
                + (deviceCount > 0 ? " · 共 " + deviceCount + " 台设备" : "")
                + (ShareSync.friendFolder(this).isEmpty() ? "" : " · 好友 " + friends.size() + " 人");
        return "还没有同步";
    }

    void updateStatus() {
        if (syncStatus == null) return;
        syncStatus.setText(statusText());
        syncStatus.setTextColor(lastSyncMsg.isEmpty() ? BLUE : RED);
    }

    ShareSync.Doc localDoc() {
        ShareSync.Doc d = db.export();
        d.owner = ShareSync.nick(this);
        d.device = ShareSync.device(this);
        d.account = ShareSync.accountId(ShareSync.user(this));
        return ShareSync.merge(d, new ShareSync.Doc());   // same ordering as merged results, so equal data gives equal JSON
    }

    void startSync(boolean manual) {
        if (!ShareSync.loggedIn(this) || syncing) return;
        final String user = ShareSync.user(this), pass = ShareSync.pass(this);
        if (pass.isEmpty()) {
            lastSyncMsg = "保存的密码读取失败，请退出登录后重新登录。";
            updateStatus();
            return;
        }
        final String snap = localDoc().json();
        if (snap.isEmpty()) return;
        syncing = true;
        final long ver = Db.version;
        final ShareSync.Doc local = ShareSync.Doc.parse(snap);
        final String prevOwn = lastOwn, prevFriend = lastFriend;
        final String nick = ShareSync.nick(this), dev = ShareSync.device(this), ff = ShareSync.friendFolder(this);
        final Map<String, ShareSync.Doc> known = new HashMap<>(devices);
        final Map<String, String> ds = new HashMap<>(devStamps), fs = new HashMap<>(friendStamps);
        updateStatus();
        new Thread(() -> {
            ShareSync.Result r = ShareSync.run(user, pass, dev, nick, local, prevOwn, known, ds, ff, prevFriend, fs);
            runOnUiThread(() -> finishSync(r, snap, ver, manual));
        }).start();
    }

    void finishSync(ShareSync.Result r, String snap, long ver, boolean manual) {
        syncing = false;
        long now = System.currentTimeMillis();
        if (r.uploadedOwn != null) lastOwn = r.uploadedOwn;
        if (r.error != null || r.merged == null) {
            lastSyncMsg = "同步失败：" + (r.error == null ? "未知错误" : r.error);
            nextPull = now + 60000;
            updateStatus();
            if (manual) alert("同步失败", r.error == null ? "未知错误" : r.error);
            return;
        }
        devices.putAll(r.gotDevices);
        devices.keySet().retainAll(r.deviceStamps.keySet());
        devStamps = r.deviceStamps;
        deviceCount = r.devices;
        if (r.uploadedFriend != null) lastFriend = r.uploadedFriend;

        boolean dataChanged = false;
        String mergedJson = r.merged.json();
        if (!mergedJson.equals(snap)) {
            ShareSync.Doc liveDoc = localDoc();
            String live = liveDoc.json();
            ShareSync.Doc m = live.equals(snap) ? r.merged : ShareSync.merge(liveDoc, r.merged);
            db.apply(m);
            dataChanged = true;
            syncedVersion = live.equals(snap) ? Db.version : ver;
        } else {
            syncedVersion = ver;
        }
        nextPull = now + 120000;
        lastSyncMsg = "";

        boolean fchanged = !r.gotFriends.isEmpty();
        if (r.friendPresent != null) {
            for (String k : new ArrayList<>(friends.keySet())) {
                if (!r.friendPresent.contains(k)) { friends.remove(k); fchanged = true; }
            }
            friends.putAll(r.gotFriends);
            friendStamps = r.friendStamps;
            if (fchanged) ShareSync.saveCache(this, friends);
        }
        Schedules.prefs(this).edit().putLong("last_sync", now).apply();

        if (dataChanged) WordWidget.refresh(this);
        refreshAfterSync(dataChanged, fchanged);
        if (manual && tab == 4) toast("同步完成");
    }

    /** redraw the current page after a sync, keeping the scroll position */
    void refreshAfterSync(boolean dataChanged, boolean friendsChanged) {
        if (dragging) { updateStatus(); return; }
        int y = scroll.getScrollY();
        boolean redraw = false;
        if (tab == 4 && (friendsChanged || dataChanged) && !choosingFolder) { go(4); redraw = true; }
        else if (dataChanged && tab == 0 && openArticle == null) { home(); redraw = true; }
        else if (dataChanged && tab == 1) { words(); redraw = true; }
        if (redraw) scroll.post(() -> scroll.scrollTo(0, y));
        else updateStatus();
    }

    void syncTick() {
        h.removeCallbacks(syncTickR);
        if (ShareSync.loggedIn(this) && !syncing && !dragging) {
            long now = System.currentTimeMillis();
            boolean dirty = Db.version != syncedVersion && now - Db.changedAt > 4000 && lastSyncMsg.isEmpty();
            if (dirty || now > nextPull) startSync(false);
        }
        h.postDelayed(syncTickR, 3000);
    }
}
