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
import java.util.Map;
import java.util.UUID;

public class Db extends SQLiteOpenHelper {
    /** increases on every user change; used to know when to sync */
    public static volatile long version = 0;
    public static volatile long changedAt = 0;

    static void bump() {
        version++;
        changedAt = System.currentTimeMillis();
    }

    /** same time unit as the desktop version (.NET UTC ticks) */
    public static long now() {
        return System.currentTimeMillis() * 10000L + 621355968000000000L;
    }

    static String newUid() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    public Db(Context c) {
        super(c, "english_clock.db", null, 3);
    }

    @Override
    public void onCreate(SQLiteDatabase d) {
        d.execSQL("create table if not exists article(id integer primary key autoincrement,title text,body text,added text,uid text,modified integer default 0)");
        d.execSQL("create table if not exists vocab(id integer primary key autoincrement,word text unique,zh text default '',context text,source text,added text,mastered integer default 0,phonetic text default '',modified integer default 0)");
        d.execSQL("create table if not exists tomb(kind text,k text,at integer,primary key(kind,k))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase d, int oldV, int newV) {
        onCreate(d);
        migrate(d);
    }

    @Override
    public void onOpen(SQLiteDatabase d) {
        super.onOpen(d);
        if (!d.isReadOnly()) migrate(d);
    }

    static boolean hasColumn(SQLiteDatabase d, String table, String col) {
        Cursor c = d.rawQuery("pragma table_info(" + table + ")", null);
        boolean has = false;
        while (c.moveToNext()) if (col.equals(c.getString(1))) has = true;
        c.close();
        return has;
    }

    static void migrate(SQLiteDatabase d) {
        d.execSQL("create table if not exists tomb(kind text,k text,at integer,primary key(kind,k))");
        if (!hasColumn(d, "vocab", "phonetic")) d.execSQL("alter table vocab add column phonetic text default ''");
        if (!hasColumn(d, "vocab", "modified")) d.execSQL("alter table vocab add column modified integer default 0");
        if (!hasColumn(d, "article", "uid")) d.execSQL("alter table article add column uid text");
        if (!hasColumn(d, "article", "modified")) d.execSQL("alter table article add column modified integer default 0");
        long t = now() - 1;
        d.execSQL("update vocab set modified=" + t + " where modified is null or modified=0");
        d.execSQL("update article set modified=" + t + " where modified is null or modified=0");
        Cursor c = d.rawQuery("select id from article where uid is null or uid=''", null);
        ArrayList<String> ids = new ArrayList<>();
        while (c.moveToNext()) ids.add(c.getString(0));
        c.close();
        for (String id : ids) {
            ContentValues v = new ContentValues();
            v.put("uid", newUid());
            d.update("article", v, "id=?", new String[]{id});
        }
    }

    static String s(Cursor c, int i) {
        String v = c.getString(i);
        return v == null ? "" : v;
    }

    void tomb(String kind, String k, long at) {
        ContentValues v = new ContentValues();
        v.put("kind", kind);
        v.put("k", k);
        v.put("at", at);
        getWritableDatabase().insertWithOnConflict("tomb", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    void untomb(String kind, String k) {
        getWritableDatabase().delete("tomb", "kind=? and k=?", new String[]{kind, k});
    }

    // ------------------------------------------------------------ articles
    public long addA(String title, String body) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("body", body);
        v.put("added", day());
        v.put("uid", newUid());
        v.put("modified", now());
        long id = getWritableDatabase().insert("article", null, v);
        bump();
        return id;
    }

    /** id, title, body, added */
    public ArrayList<String[]> arts() {
        ArrayList<String[]> out = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery("select id,title,body,added from article order by added desc,id desc", null);
        while (c.moveToNext()) out.add(new String[]{s(c, 0), s(c, 1), s(c, 2), s(c, 3)});
        c.close();
        return out;
    }

    public void delA(String id) {
        Cursor c = getReadableDatabase().rawQuery("select uid from article where id=?", new String[]{id});
        String uid = c.moveToFirst() ? s(c, 0) : "";
        c.close();
        getWritableDatabase().delete("article", "id=?", new String[]{id});
        if (!uid.isEmpty()) tomb("a", uid, now());
        bump();
    }

    // ------------------------------------------------------------ words
    public boolean has(String w) {
        Cursor c = getReadableDatabase().rawQuery("select 1 from vocab where lower(word)=lower(?)", new String[]{w});
        boolean x = c.moveToFirst();
        c.close();
        return x;
    }

    /** returns true if the word was added, false if removed */
    public boolean toggle(String w, String context, String source, String phonetic) {
        String key = w.toLowerCase(Locale.ROOT);
        if (has(w)) {
            getWritableDatabase().delete("vocab", "lower(word)=lower(?)", new String[]{w});
            tomb("w", key, now());
            bump();
            return false;
        }
        ContentValues v = new ContentValues();
        v.put("word", key);
        v.put("context", context);
        v.put("source", source);
        v.put("added", day());
        v.put("phonetic", phonetic == null ? "" : phonetic);
        v.put("modified", now());
        getWritableDatabase().insert("vocab", null, v);
        untomb("w", key);
        bump();
        return true;
    }

    /** copy a word from a friend's book; false if I already have it */
    public boolean addFriendWord(String word, String zh, String phonetic, String context, String source) {
        String key = word.toLowerCase(Locale.ROOT);
        if (has(key)) return false;
        ContentValues v = new ContentValues();
        v.put("word", key);
        v.put("zh", zh == null ? "" : zh);
        v.put("phonetic", phonetic == null ? "" : phonetic);
        v.put("context", context == null ? "" : context);
        v.put("source", source == null ? "" : source);
        v.put("added", day());
        v.put("modified", now());
        long id = getWritableDatabase().insertWithOnConflict("vocab", null, v, SQLiteDatabase.CONFLICT_IGNORE);
        if (id > 0) {
            untomb("w", key);
            bump();
            return true;
        }
        return false;
    }

    /** id, word, zh, context, source, added, mastered, phonetic */
    public ArrayList<String[]> words(String q) {
        ArrayList<String[]> out = new ArrayList<>();
        String like = "%" + q + "%";
        Cursor c = getReadableDatabase().rawQuery(
                "select id,word,zh,context,source,added,mastered,phonetic from vocab where word like ? or zh like ? order by mastered,added desc,id desc",
                new String[]{like, like});
        while (c.moveToNext()) {
            out.add(new String[]{s(c, 0), s(c, 1), s(c, 2), s(c, 3), s(c, 4), s(c, 5), s(c, 6), s(c, 7)});
        }
        c.close();
        return out;
    }

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
        v.put("modified", now());
        getWritableDatabase().update("vocab", v, "id=?", new String[]{id});
        bump();
    }

    public void zhIfEmpty(String word, String z) {
        ContentValues v = new ContentValues();
        v.put("zh", z);
        v.put("modified", now());
        int n = getWritableDatabase().update("vocab", v, "lower(word)=lower(?) and (zh is null or zh='')", new String[]{word});
        if (n > 0) bump();
    }

    public void phonetic(String word, String p) {
        ContentValues v = new ContentValues();
        v.put("phonetic", p);
        v.put("modified", now());
        int n = getWritableDatabase().update("vocab", v, "lower(word)=lower(?) and ifnull(phonetic,'')<>?", new String[]{word, p});
        if (n > 0) bump();
    }

    public void master(String id, boolean m) {
        ContentValues v = new ContentValues();
        v.put("mastered", m ? 1 : 0);
        v.put("modified", now());
        getWritableDatabase().update("vocab", v, "id=?", new String[]{id});
        bump();
    }

    public void delW(String id) {
        Cursor c = getReadableDatabase().rawQuery("select lower(word) from vocab where id=?", new String[]{id});
        String key = c.moveToFirst() ? s(c, 0) : "";
        c.close();
        getWritableDatabase().delete("vocab", "id=?", new String[]{id});
        if (!key.isEmpty()) tomb("w", key, now());
        bump();
    }

    // ------------------------------------------------------------ sync
    /** everything I have, as a sync document */
    public ShareSync.Doc export() {
        ShareSync.Doc d = new ShareSync.Doc();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("select word,zh,phonetic,context,source,added,mastered,modified from vocab order by id", null);
        while (c.moveToNext()) {
            ShareSync.W w = new ShareSync.W();
            w.word = s(c, 0); w.zh = s(c, 1); w.phonetic = s(c, 2); w.context = s(c, 3); w.source = s(c, 4);
            w.added = s(c, 5); w.mastered = c.getInt(6) == 1; w.modified = c.getLong(7);
            d.vocab.put(w.word.toLowerCase(Locale.ROOT), w);
        }
        c.close();
        c = db.rawQuery("select uid,title,body,added,modified from article order by id", null);
        while (c.moveToNext()) {
            ShareSync.A a = new ShareSync.A();
            a.id = s(c, 0); a.title = s(c, 1); a.content = s(c, 2); a.date = s(c, 3); a.modified = c.getLong(4);
            if (!a.id.isEmpty()) d.arts.add(a);
        }
        c.close();
        c = db.rawQuery("select kind,k,at from tomb", null);
        while (c.moveToNext()) {
            if ("w".equals(s(c, 0))) d.delW.put(s(c, 1), c.getLong(2));
            else d.delA.put(s(c, 1), c.getLong(2));
        }
        c.close();
        return d;
    }

    /** replace local data with the merged result (does not count as a user change) */
    public void apply(ShareSync.Doc m) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // words
            HashSet<String> keep = new HashSet<>(m.vocab.keySet());
            Cursor c = db.rawQuery("select id,lower(word) from vocab", null);
            ArrayList<String> drop = new ArrayList<>();
            HashSet<String> have = new HashSet<>();
            while (c.moveToNext()) {
                if (keep.contains(s(c, 1))) have.add(s(c, 1));
                else drop.add(s(c, 0));
            }
            c.close();
            for (String id : drop) db.delete("vocab", "id=?", new String[]{id});
            for (Map.Entry<String, ShareSync.W> e : m.vocab.entrySet()) {
                ShareSync.W w = e.getValue();
                ContentValues v = new ContentValues();
                v.put("zh", w.zh);
                v.put("phonetic", w.phonetic);
                v.put("context", w.context);
                v.put("source", w.source);
                v.put("added", w.added);
                v.put("mastered", w.mastered ? 1 : 0);
                v.put("modified", w.modified);
                if (have.contains(e.getKey())) db.update("vocab", v, "lower(word)=?", new String[]{e.getKey()});
                else {
                    v.put("word", e.getKey());
                    db.insertWithOnConflict("vocab", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            // articles
            HashSet<String> keepA = new HashSet<>();
            for (ShareSync.A a : m.arts) keepA.add(a.id);
            c = db.rawQuery("select id,uid from article", null);
            ArrayList<String> dropA = new ArrayList<>();
            HashSet<String> haveA = new HashSet<>();
            while (c.moveToNext()) {
                if (keepA.contains(s(c, 1))) haveA.add(s(c, 1));
                else dropA.add(s(c, 0));
            }
            c.close();
            for (String id : dropA) db.delete("article", "id=?", new String[]{id});
            for (ShareSync.A a : m.arts) {
                ContentValues v = new ContentValues();
                v.put("title", a.title);
                v.put("body", a.content);
                v.put("added", a.date);
                v.put("modified", a.modified);
                if (haveA.contains(a.id)) db.update("article", v, "uid=?", new String[]{a.id});
                else {
                    v.put("uid", a.id);
                    db.insert("article", null, v);
                    haveA.add(a.id);
                }
            }
            // deletion records
            db.delete("tomb", null, null);
            for (Map.Entry<String, Long> e : m.delW.entrySet()) {
                ContentValues v = new ContentValues();
                v.put("kind", "w"); v.put("k", e.getKey()); v.put("at", e.getValue());
                db.insertWithOnConflict("tomb", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            for (Map.Entry<String, Long> e : m.delA.entrySet()) {
                ContentValues v = new ContentValues();
                v.put("kind", "a"); v.put("k", e.getKey()); v.put("at", e.getValue());
                db.insertWithOnConflict("tomb", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public static String day() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }
}
