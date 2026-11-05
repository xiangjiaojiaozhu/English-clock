package com.xiangjiaojiaozhu.englishclock;

import android.content.Context;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * IPA phonetics. Uses the built-in offline dictionary (assets/phonetic.txt, ~126k words, US pronunciation)
 * and only goes online for words that are not in it.
 */
public class Phonetic {
    static HashMap<String, String> dict;

    static synchronized HashMap<String, String> dict(Context c) {
        if (dict != null) return dict;
        HashMap<String, String> m = new HashMap<>(180000);
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                c.getApplicationContext().getAssets().open("phonetic.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int t = line.indexOf('\t');
                if (t > 0) m.put(line.substring(0, t), line.substring(t + 1));
            }
        } catch (Exception ignored) {
        }
        dict = m;
        return dict;
    }

    static String one(HashMap<String, String> d, String w) {
        w = w.toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        String p = d.get(w);
        if (p != null) return p;
        if (w.endsWith("'s")) {
            p = d.get(w.substring(0, w.length() - 2));
            if (p != null) return p + "z";
        }
        return null;
    }

    /** offline lookup; works for single words and short phrases. "" if not found */
    public static String offline(Context c, String word) {
        HashMap<String, String> d = dict(c);
        String[] parts = word.trim().split("[\\s\\-]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            String p = one(d, part);
            if (p == null) return "";
            if (sb.length() > 0) sb.append(' ');
            sb.append(p);
        }
        return sb.length() == 0 ? "" : "/" + sb + "/";
    }

    static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0");
        return ImportUtil.stream(c.getInputStream());
    }

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

    static String online(String word) {
        try {
            String q = URLEncoder.encode(word, "UTF-8");
            Object j = new JSONArray(get("https://api.dictionaryapi.dev/api/v2/entries/en/" + q));
            String p = find(j, "phonetic");
            if (p == null) p = find(j, "text");
            if (p != null) return slash(p);
        } catch (Exception ignored) {
        }
        return "";
    }

    /** offline first, then online. Must not be called on the UI thread. */
    public static String lookup(Context c, String word) {
        String p = offline(c, word);
        return p.isEmpty() ? online(word) : p;
    }

    static volatile boolean running = false;

    /** fill phonetics for all words that are still missing them; background thread */
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
                    String p = lookup(app, w);
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
