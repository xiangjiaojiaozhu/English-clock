package com.xiangjiaojiaozhu.englishclock;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.util.ArrayList;
import java.util.Random;

/** Draws the current word directly onto the lock-screen wallpaper. */
public class LockWallpaper {

    /** @return null on success, otherwise a message for the user */
    public static String update(Context c) {
        ArrayList<String[]> all = new Db(c).words("");
        if (all.isEmpty()) return "生词本还是空的，先在文章里点几个生词";
        ArrayList<String[]> pool = new ArrayList<>();
        for (String[] v : all) if (!"1".equals(v[6])) pool.add(v);
        if (pool.isEmpty()) pool = all;
        String[] v = pool.get(new Random().nextInt(pool.size()));
        try {
            Bitmap b = render(c, v[1], v[2], v[3]);
            WallpaperManager.getInstance(c).setBitmap(b, null, true, WallpaperManager.FLAG_LOCK);
            b.recycle();
            return null;
        } catch (Exception e) {
            return "设置锁屏失败：" + e.getMessage();
        }
    }

    @SuppressWarnings("deprecation")
    static Bitmap render(Context c, String word, String zh, String ctx) {
        DisplayMetrics dm = new DisplayMetrics();
        ((WindowManager) c.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRealMetrics(dm);
        int w = Math.max(720, dm.widthPixels);
        int h = Math.max(1280, dm.heightPixels);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);

        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0F1B2D, 0xFF15294A, 0xFF1E3D6E}, null, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, bg);
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setShader(new RadialGradient(w * 0.5f, h * 0.58f, w * 0.8f, 0x332F7BFF, 0x002F7BFF, Shader.TileMode.CLAMP));
        cv.drawRect(0, 0, w, h, glow);

        float cx = w / 2f;
        float y = h * 0.46f;   // leave the top for the system clock

        Paint label = center(0xFF8FA6C8, w * 0.03f, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
        label.setLetterSpacing(0.3f);
        cv.drawText("TODAY'S WORD", cx, y, label);

        y += w * 0.15f;
        Paint wp = center(0xFFFFFFFF, w * 0.13f, Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        while (wp.measureText(word) > w * 0.86f && wp.getTextSize() > w * 0.05f) wp.setTextSize(wp.getTextSize() * 0.92f);
        cv.drawText(word, cx, y, wp);

        y += w * 0.06f;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(0x665B8DEF);
        line.setStrokeWidth(w * 0.004f);
        line.setStrokeCap(Paint.Cap.ROUND);
        cv.drawLine(cx - w * 0.05f, y, cx + w * 0.05f, y, line);
        y += w * 0.06f;

        if (zh != null && zh.trim().length() > 0) {
            TextPaint zp = text(0xFFDCE7FF, w * 0.055f, Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL));
            y = block(cv, zh.trim(), zp, w, y, 3) + w * 0.05f;
        }
        if (ctx != null && ctx.trim().length() > 0) {
            TextPaint tp = text(0xFF9DB0CC, w * 0.036f, Typeface.create(Typeface.SERIF, Typeface.ITALIC));
            block(cv, ctx.trim(), tp, w, y, 4);
        }
        return b;
    }

    static Paint center(int color, float size, Typeface tf) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(tf);
        p.setTextAlign(Paint.Align.CENTER);
        return p;
    }

    static TextPaint text(int color, float size, Typeface tf) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setTextSize(size);
        p.setTypeface(tf);
        return p;
    }

    static float block(Canvas cv, String s, TextPaint tp, int w, float y, int maxLines) {
        int width = (int) (w * 0.84f);
        StaticLayout l = StaticLayout.Builder.obtain(s, 0, s.length(), tp, width)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setLineSpacing(0f, 1.15f)
                .setMaxLines(maxLines)
                .setEllipsize(TextUtils.TruncateAt.END)
                .build();
        cv.save();
        cv.translate((w - width) / 2f, y);
        l.draw(cv);
        cv.restore();
        return y + l.getHeight();
    }
}
