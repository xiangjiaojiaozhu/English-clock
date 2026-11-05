package com.xiangjiaojiaozhu.englishclock;

import android.content.Context;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

/** Looks up IPA phonetics: Youdao first (works in China), dictionaryapi.dev as fallback. */
public class Phonetic {

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        return ImportUtil.stream(c.getInputStream());
    }

    /** depth-first search for the first non-empty string value of one of the keys */
    static String find(Object o, String key) {
        if (o instanceof JSONObject) {
            JSONObject j = (JSONObject) o;
            Object v = j.opt(key);
            if (v instanceof String && ((String) v).trim().length() > 0) return ((String) v).trim();
            JSONArray names = j.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String r = find(j.opt(names.optString(i)), key);
                    if (r != null) return r;
                }
            }
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) {
                String r = find(a.opt(i), key);
                if (r != null) return r;
            }
        }
        return null;
    }

    static String slash(String p) {
        p = p.trim();
        if (p.isEmpty()) return "";
        if (!p.startsWith("/") && !p.startsWith("[")) p = "/" + p;
        if (!p.endsWith("/") && !p.endsWith("]")) p = p + "/";
        return p;
    }

    /** returns "" when nothing found or offline */
    public static String lookup(String word) {
        String q;
        try {
            q = URLEncoder.encode(word, "UTF-8");
        } catch (Exception e) {
            return "";
        }
        try {
            Object j = new JSONObject(get("https://dict.youdao.com/jsonapi?q=" + q));
            String p = find(j, "usphone");
            if (p == null) p = find(j, "ukphone");
            if (p == null) p = find(j, "phone");
            if (p != null) return slash(p);
        } catch (Exception ignored) {
        }
        try {
            Object j = new JSONArray(get("https://api.dictionaryapi.dev/api/v2/entries/en/" + q));
            String p = find(j, "phonetic");
            if (p == null) p = find(j, "text");
            if (p != null) return slash(p);
        } catch (Exception ignored) {
        }
        return "";
    }

    /** fill phonetics for all words that are still missing them; runs on a background thread */
    static volatile boolean running = false;

    public static void backfill(Context c, Runnable done) {
        if (running) return;
        running = true;
        final Context app = c.getApplicationContext();
        new Thread(() -> {
          try {
            Db db = new Db(app);
            ArrayList<String> ws = db.missingPhonetic();
            boolean any = false;
            for (String w : ws) {
                String p = lookup(w);
                if (!p.isEmpty()) {
                    db.phonetic(w, p);
                    any = true;
                }
            }
            if (any && done != null) done.run();
          } finally {
            running = false;
          }
        }).start();
    }
}
