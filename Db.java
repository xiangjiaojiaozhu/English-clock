package com.xiangjiaojiaozhu.englishclock;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;

public class Db extends SQLiteOpenHelper {
    public Db(Context c) {
        super(c, "english_clock.db", null, 2);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("create table if not exists article(id integer primary key autoincrement,title text,body text,added text)");
        d.execSQL("create table if not exists vocab(id integer primary key autoincrement,word text unique,zh text default '',context text,source text,added text,mastered integer default 0,phonetic text default '')");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
        onCreate(d);
        if (oldV < 2) {
            try {
                d.execSQL("alter table vocab add column phonetic text default ''");
            } catch (Exception ignored) {
                // column already exists
            }
        }
    }

    static String s(Cursor c, int i) {
        String v = c.getString(i);
        return v == null ? "" : v;
    }

    public long addA(String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("added", day());
        return getWritableDatabase().insert("article", null, v);
    }

    public ArrayList<String[]> arts() {
        ArrayList<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("select id,title,body,added from article order by id desc", null);
        while (c.moveToNext()) out.add(new String[]{s(c, 0), s(c, 1), s(c, 2), s(c, 3)});
        c.close();
        return out;
    }

    public void delA(String id) {
        getWritableDatabase().delete("article", "id=?", new String[]{id});
    }

    public boolean has(String w) {
        Cursor c = getReadableDatabase().rawQuery("select 1 from vocab where lower(word)=lower(?)", new String[]{w});
        boolean x = c.moveToFirst();
        c.close();
        return x;
    }

    /** returns true if the word was added, false if removed */
    public boolean toggle(String w, String context, String source) {
        if (has(w)) {
            getWritableDatabase().delete("vocab", "lower(word)=lower(?)", new String[]{w});
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("word", w.toLowerCase(Locale.ROOT));
        v.put("context", context);
        v.put("source", source);
        v.put("added", day());
        getWritableDatabase().insert("vocab", null, v);
        return true;
    }

    /** id, word, zh, context, source, added, mastered, phonetic */
    public ArrayList<String[]> words(String q) {
        ArrayList<String[]> out = new ArrayList<>();
        String like = "%" + q + "%";
        Cursor c = getReadableDatabase().rawQuery(
                "select id,word,zh,context,source,added,mastered,phonetic from vocab where word like ? or zh like ? order by mastered,id desc",
                new String[]{like, like});
        while (c.moveToNext()) {
            out.add(new String[]{s(c, 0), s(c, 1), s(c, 2), s(c, 3), s(c, 4), s(c, 5), s(c, 6), s(c, 7)});
        }
        c.close();
        return out;
    }

    /** words that still have no phonetic */
    public ArrayList<String> missingPhonetic() {
        ArrayList<String> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("select word from vocab where phonetic is null or phonetic=''", null);
        while (c.moveToNext()) out.add(s(c, 0));
        c.close();
        return out;
    }

    public HashSet<String> wordSet() {
        HashSet<String> set = new HashSet<>();
        Cursor c = getReadableDatabase().rawQuery("select lower(word) from vocab", null);
        while (c.moveToNext()) set.add(s(c, 0));
        c.close();
        return set;
    }

    public void zh(String id, String z) {
        ContentValues v = new ContentValues();
        v.put("zh", z);
        getWritableDatabase().update("vocab", v, "id=?", new String[]{id});
    }

    public void zhIfEmpty(String word, String z) {
        ContentValues v = new ContentValues();
        v.put("zh", z);
        getWritableDatabase().update("vocab", v, "lower(word)=lower(?) and (zh is null or zh='')", new String[]{word});
    }

    public void phonetic(String word, String p) {
        ContentValues v = new ContentValues();
        v.put("phonetic", p);
        getWritableDatabase().update("vocab", v, "lower(word)=lower(?)", new String[]{word});
    }

    public void master(String id, boolean m) {
        ContentValues v = new ContentValues();
        v.put("mastered", m ? 1 : 0);
        getWritableDatabase().update("vocab", v, "id=?", new String[]{id});
    }

    public void delW(String id) {
        getWritableDatabase().delete("vocab", "id=?", new String[]{id});
    }

    public static String day() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
