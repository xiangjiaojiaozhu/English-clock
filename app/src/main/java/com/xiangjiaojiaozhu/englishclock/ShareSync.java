package com.xiangjiaojiaozhu.englishclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.xml.parsers.DocumentBuilderFactory;
import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.json.JSONArray;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Account sync via Jianguoyun (WebDAV), same format as the desktop version (v12).
 * The Jianguoyun account is the app account:
 *   /dav/EnglishClock/dev_<device>.json - one file per device; all of them are merged
 *   <friends folder>/ec_<account>.json  - optional read-only copy for friends with their own account
 */
public class ShareSync {
    static final String HOST = "https://dav.jianguoyun.com";
    public static final String ROOT = "/dav/";
    public static final String APP = "/dav/EnglishClock/";
    static final String BAD401 = "坚果云账号或应用密码不对（或已失效）。\n\n注意：这里要填坚果云「账户信息 → 安全选项 → 第三方应用管理」里生成的应用密码，不是平时的登录密码。";

    static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    // ------------------------------------------------------------ document (same JSON as the desktop version)
    public static class W {
        public String word = "", zh = "", phonetic = "", context = "", source = "", added = "", by = "";
        public boolean mastered;
        public long modified;

        W copy() {
            W c = new W();
            c.word = word; c.zh = zh; c.phonetic = phonetic; c.context = context; c.source = source;
            c.added = added; c.by = by; c.mastered = mastered; c.modified = modified;
            return c;
        }
    }

    public static class A {
        public String id = "", title = "", content = "", date = "", by = "";
        public long modified;

        A copy() {
            A c = new A();
            c.id = id; c.title = title; c.content = content; c.date = date; c.by = by; c.modified = modified;
            return c;
        }
    }

    public static class Doc {
        public String owner = "", device = "", account = "";
        public LinkedHashMap<String, W> vocab = new LinkedHashMap<>();
        public ArrayList<A> arts = new ArrayList<>();
        public HashMap<String, Long> delW = new HashMap<>();
        public HashMap<String, Long> delA = new HashMap<>();

        public String json() {
            try {
                JSONObject o = new JSONObject();
                o.put("Owner", owner);
                o.put("Device", device);
                o.put("Account", account);
                JSONObject v = new JSONObject();
                for (Map.Entry<String, W> e : vocab.entrySet()) {
                    W w = e.getValue();
                    JSONObject x = new JSONObject();
                    x.put("Word", w.word); x.put("Zh", w.zh); x.put("Phonetic", w.phonetic); x.put("Context", w.context);
                    x.put("Source", w.source); x.put("Added", w.added); x.put("Mastered", w.mastered);
                    x.put("Modified", w.modified); x.put("AddedBy", w.by);
                    v.put(e.getKey(), x);
                }
                o.put("Vocab", v);
                JSONArray arr = new JSONArray();
                for (A a : arts) {
                    JSONObject x = new JSONObject();
                    x.put("Id", a.id); x.put("Title", a.title); x.put("Content", a.content); x.put("Date", a.date);
                    x.put("Modified", a.modified); x.put("AddedBy", a.by);
                    arr.put(x);
                }
                o.put("Articles", arr);
                JSONObject dw = new JSONObject();
                for (String k : sorted(delW.keySet())) dw.put(k, delW.get(k));
                o.put("DelWords", dw);
                JSONObject da = new JSONObject();
                for (String k : sorted(delA.keySet())) da.put(k, delA.get(k));
                o.put("DelArticles", da);
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        public static Doc parse(String s) {
            Doc d = new Doc();
            if (s == null || s.isEmpty()) return d;
            try {
                JSONObject o = new JSONObject(s);
                d.owner = o.optString("Owner", "");
                d.device = o.optString("Device", "");
                d.account = o.optString("Account", "");
                JSONObject v = o.optJSONObject("Vocab");
                if (v != null) {
                    Iterator<String> it = v.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        JSONObject x = v.optJSONObject(k);
                        if (x == null) continue;
                        W w = new W();
                        w.word = x.optString("Word", k);
                        if (w.word.isEmpty() || "null".equals(w.word)) w.word = k;
                        w.zh = str(x, "Zh"); w.phonetic = str(x, "Phonetic"); w.context = str(x, "Context");
                        w.source = str(x, "Source"); w.added = str(x, "Added"); w.by = str(x, "AddedBy");
                        w.mastered = x.optBoolean("Mastered", false);
                        w.modified = x.optLong("Modified", 0);
                        d.vocab.put(k, w);
                    }
                }
                JSONArray arr = o.optJSONArray("Articles");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject x = arr.optJSONObject(i);
                        if (x == null) continue;
                        A a = new A();
                        a.id = str(x, "Id");
                        if (a.id.isEmpty()) continue;
                        a.title = str(x, "Title"); a.content = str(x, "Content"); a.date = str(x, "Date"); a.by = str(x, "AddedBy");
                        a.modified = x.optLong("Modified", 0);
                        d.arts.add(a);
                    }
                }
                readLongs(o.optJSONObject("DelWords"), d.delW);
                readLongs(o.optJSONObject("DelArticles"), d.delA);
            } catch (Exception ignored) {
            }
            return d;
        }

        static String str(JSONObject x, String k) {
            if (x.isNull(k)) return "";
            return x.optString(k, "");
        }

        static void readLongs(JSONObject o, HashMap<String, Long> m) {
            if (o == null) return;
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, o.optLong(k, 0));
            }
        }
    }

    static ArrayList<String> sorted(Set<String> s) {
        ArrayList<String> l = new ArrayList<>(s);
        Collections.sort(l);
        return l;
    }

    static void max(HashMap<String, Long> d, String k, long v) {
        Long o = d.get(k);
        if (o == null || v > o) d.put(k, v);
    }

    /** newest change wins per word / article; a deletion wins over older changes (same rule as the desktop version) */
    public static Doc merge(Doc a, Doc b) {
        Doc r = new Doc();
        r.owner = a.owner; r.device = a.device; r.account = a.account;
        for (Map.Entry<String, Long> e : a.delW.entrySet()) max(r.delW, e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : b.delW.entrySet()) max(r.delW, e.getKey(), e.getValue());
        HashSet<String> keys = new HashSet<>(a.vocab.keySet());
        keys.addAll(b.vocab.keySet());
        ArrayList<Map.Entry<String, W>> win = new ArrayList<>();
        for (String k : keys) {
            W va = a.vocab.get(k), vb = b.vocab.get(k);
            W w = va == null ? vb : vb == null ? va : (vb.modified > va.modified ? vb : va);
            if (w == null) continue;
            Long del = r.delW.get(k);
            if (del != null) {
                if (del >= w.modified) continue;
                r.delW.remove(k);
            }
            win.add(new java.util.AbstractMap.SimpleEntry<>(k, w.copy()));
        }
        Collections.sort(win, (x, y) -> {
            int c = x.getValue().added.compareTo(y.getValue().added);
            if (c != 0) return c;
            c = Long.compare(x.getValue().modified, y.getValue().modified);
            if (c != 0) return c;
            return x.getKey().compareTo(y.getKey());
        });
        for (Map.Entry<String, W> e : win) r.vocab.put(e.getKey(), e.getValue());

        for (Map.Entry<String, Long> e : a.delA.entrySet()) max(r.delA, e.getKey(), e.getValue());
        for (Map.Entry<String, Long> e : b.delA.entrySet()) max(r.delA, e.getKey(), e.getValue());
        HashMap<String, A> am = new HashMap<>(), bm = new HashMap<>();
        for (A x : a.arts) am.put(x.id, x);
        for (A x : b.arts) bm.put(x.id, x);
        HashSet<String> ids = new HashSet<>(am.keySet());
        ids.addAll(bm.keySet());
        for (String id : ids) {
            A xa = am.get(id), xb = bm.get(id);
            A w = xa == null ? xb : xb == null ? xa : (xb.modified > xa.modified ? xb : xa);
            Long del = r.delA.get(id);
            if (del != null) {
                if (del >= w.modified) continue;
                r.delA.remove(id);
            }
            r.arts.add(w.copy());
        }
        Collections.sort(r.arts, (x, y) -> {
            int c = y.date.compareTo(x.date);
            if (c != 0) return c;
            c = Long.compare(y.modified, x.modified);
            if (c != 0) return c;
            return x.id.compareTo(y.id);
        });
        return r;
    }

    // ------------------------------------------------------------ settings
    static SharedPreferences prefs(Context c) {
        return Schedules.prefs(c);
    }

    public static void migrate(Context c) {
        SharedPreferences p = prefs(c);
        if (p.getBoolean("sync_v09", false)) return;
        SharedPreferences.Editor e = p.edit();
        String old = p.getString("sync_folder", "");
        if (!old.isEmpty() && p.getString("friend_folder", "").isEmpty())
            e.putString("friend_folder", old).putString("friend_folder_name", p.getString("sync_folder_name", ""));
        e.putBoolean("sync_v09", true).apply();
    }

    public static String user(Context c) { return prefs(c).getString("sync_user", ""); }
    public static String nick(Context c) {
        String n = prefs(c).getString("sync_nick", "");
        return n.isEmpty() ? "我的手机" : n;
    }
    public static String friendFolder(Context c) { return prefs(c).getString("friend_folder", ""); }
    public static String friendFolderName(Context c) { return prefs(c).getString("friend_folder_name", ""); }
    public static String pass(Context c) { return unprotect(prefs(c).getString("sync_pass", "")); }

    public static boolean loggedIn(Context c) {
        return user(c).length() > 0 && prefs(c).getString("sync_pass", "").length() > 0;
    }

    public static String device(Context c) {
        SharedPreferences p = prefs(c);
        String d = p.getString("device_id", "");
        if (d.isEmpty()) {
            d = UUID.randomUUID().toString().replace("-", "");
            p.edit().putString("device_id", d).apply();
        }
        return d;
    }

    // ------------------------------------------------------------ password stored with the Android Keystore
    static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias("ec_sync")) {
            KeyGenerator g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder("ec_sync", KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return g.generateKey();
        }
        return (SecretKey) ks.getKey("ec_sync", null);
    }

    public static String protect(String s) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(s.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(ct, Base64.NO_WRAP);
        } catch (Exception e) {
            return "";
        }
    }

    public static String unprotect(String s) {
        try {
            int i = s.indexOf(':');
            if (i < 0) return "";
            byte[] iv = Base64.decode(s.substring(0, i), Base64.NO_WRAP);
            byte[] ct = Base64.decode(s.substring(i + 1), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------ names and paths
    public static String accountId(String user) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest(user.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 6; i++) sb.append(String.format(Locale.ROOT, "%02x", h[i] & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "mobile";
        }
    }

    public static String deviceFile(String device) { return "dev_" + device + ".json"; }
    public static String friendFile(String user) { return "ec_" + accountId(user) + ".json"; }

    public static String dir(String href) {
        return href.endsWith("/") ? href : href + "/";
    }

    static String decode(String h) {
        try {
            return URLDecoder.decode(h.replace("+", "%2B"), "UTF-8");
        } catch (Exception e) {
            return h;
        }
    }

    static String norm(String h) {
        String d = decode(h);
        while (d.endsWith("/")) d = d.substring(0, d.length() - 1);
        return d.toLowerCase(Locale.ROOT);
    }

    public static boolean sameDir(String a, String b) {
        return norm(dir(a)).equals(norm(dir(b)));
    }

    // ------------------------------------------------------------ WebDAV
    public static class Item {
        public String name = "", file = "", href = "", modified = "";
        public boolean dir;

        @Override
        public String toString() {
            return name;
        }
    }

    static class Resp {
        int code;
        String body = "";
    }

    static Resp send(String method, String href, String user, String pass, byte[] body, String type, String depth) throws Exception {
        String url = href.startsWith("http") ? href : HOST + href;
        RequestBody rb = body == null ? null : RequestBody.create(body, MediaType.get(type));
        Request.Builder b = new Request.Builder()
                .url(url)
                .method(method, rb)
                .header("Authorization", Credentials.basic(user, pass, StandardCharsets.UTF_8))
                .header("User-Agent", "EnglishClock");
        if (depth != null) b.header("Depth", depth);
        try (Response r = HTTP.newCall(b.build()).execute()) {
            Resp x = new Resp();
            x.code = r.code();
            ResponseBody body2 = r.body();
            x.body = body2 == null ? "" : body2.string();
            return x;
        } catch (IOException e) {
            throw new Exception("连不上坚果云（可能没联网，或网络拦截了 dav.jianguoyun.com）：" + e.getMessage());
        }
    }

    static byte[] propBody() {
        return ("<?xml version=\"1.0\" encoding=\"utf-8\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>"
                + "<d:displayname/><d:resourcetype/><d:getlastmodified/><d:getcontentlength/></d:prop></d:propfind>")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** null = OK, otherwise a message for the user */
    public static String login(String user, String pass) {
        try {
            Resp r = send("PROPFIND", ROOT, user, pass, propBody(), "text/xml; charset=utf-8", "0");
            if (r.code == 401) return BAD401;
            if (r.code == 403) return "坚果云拒绝访问（403）。请确认应用密码有效，或稍后再试。";
            if (r.code < 200 || r.code >= 300) return "坚果云返回错误 " + r.code;
            send("MKCOL", APP, user, pass, null, null, null);
            return null;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    static String first(Element e, String name) {
        NodeList n = e.getElementsByTagNameNS("DAV:", name);
        return n.getLength() > 0 ? n.item(0).getTextContent().trim() : "";
    }

    /** null if the folder does not exist */
    public static List<Item> list(String user, String pass, String href) throws Exception {
        Resp r = send("PROPFIND", dir(href), user, pass, propBody(), "text/xml; charset=utf-8", "1");
        if (r.code == 401) throw new Exception(BAD401);
        if (r.code == 404) return null;
        if (r.code < 200 || r.code >= 300) throw new Exception("读取文件夹失败（错误 " + r.code + "）");
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        Document doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(r.body.getBytes(StandardCharsets.UTF_8)));
        NodeList rs = doc.getElementsByTagNameNS("DAV:", "response");
        String self = norm(dir(href));
        ArrayList<Item> out = new ArrayList<>();
        for (int i = 0; i < rs.getLength(); i++) {
            Element e = (Element) rs.item(i);
            String h = first(e, "href");
            if (h.isEmpty()) continue;
            if (h.startsWith("http")) {
                try { h = new URL(h).getPath(); } catch (Exception ignored) { }
            }
            if (norm(h).equals(self)) continue;
            Item it = new Item();
            it.dir = e.getElementsByTagNameNS("DAV:", "collection").getLength() > 0;
            it.href = it.dir ? dir(h) : h;
            String last = decode(h);
            while (last.endsWith("/")) last = last.substring(0, last.length() - 1);
            it.file = last.substring(last.lastIndexOf('/') + 1);
            String dn = first(e, "displayname");
            it.name = dn.length() > 0 ? dn : it.file;
            it.modified = first(e, "getlastmodified");
            out.add(it);
        }
        return out;
    }

    public static String makeDir(String user, String pass, String parent, String name) throws Exception {
        String h = dir(parent) + URLEncoder.encode(name, "UTF-8").replace("+", "%20") + "/";
        Resp r = send("MKCOL", h, user, pass, null, null, null);
        if (r.code == 401) throw new Exception(BAD401);
        if (r.code == 201 || r.code == 405 || (r.code >= 200 && r.code < 300)) return h;
        throw new Exception("新建文件夹失败（错误 " + r.code + "）");
    }

    public static void put(String user, String pass, String href, String json) throws Exception {
        Resp r = send("PUT", href, user, pass, json.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8", null);
        if (r.code == 401) throw new Exception(BAD401);
        if (r.code == 403) throw new Exception("没有写入权限（403）。如果好友共享文件夹是别人共享给你的，请让对方把你的权限设为「可编辑」。");
        if (r.code < 200 || r.code >= 300) throw new Exception("上传失败（错误 " + r.code + "）");
    }

    public static String get(String user, String pass, String href) throws Exception {
        Resp r = send("GET", href, user, pass, null, null, null);
        if (r.code == 404) return null;
        if (r.code == 401) throw new Exception(BAD401);
        if (r.code < 200 || r.code >= 300) throw new Exception("下载失败（错误 " + r.code + "）");
        return r.body;
    }

    public static void delete(String user, String pass, String href) {
        try { send("DELETE", href, user, pass, null, null, null); } catch (Exception ignored) { }
    }

    // ------------------------------------------------------------ one sync round (background thread)
    public static class Result {
        public String error, uploadedOwn, uploadedFriend;
        public Doc merged;
        public int devices;
        public Map<String, Doc> gotDevices = new HashMap<>();
        public Map<String, String> deviceStamps = new HashMap<>();
        public Map<String, Doc> gotFriends = new HashMap<>();
        public Map<String, String> friendStamps = new HashMap<>();
        public Set<String> friendPresent;
    }

    public static Result run(String user, String pass, String device, String nick, Doc local, String prevOwn,
                             Map<String, Doc> knownDevices, Map<String, String> devStamps,
                             String friendFolder, String prevFriend, Map<String, String> friendStamps) {
        Result res = new Result();
        try {
            String mine = deviceFile(device);
            List<Item> items = list(user, pass, APP);
            if (items == null) {
                send("MKCOL", APP, user, pass, null, null, null);
                items = new ArrayList<>();
            }

            // 1. other devices of this account
            Map<String, Doc> all = new HashMap<>(knownDevices);
            Set<String> present = new HashSet<>();
            for (Item it : items) {
                if (it.dir) continue;
                String lf = it.file.toLowerCase(Locale.ROOT);
                if (!lf.startsWith("dev_") || !lf.endsWith(".json") || it.file.equalsIgnoreCase(mine)) continue;
                present.add(it.file);
                res.deviceStamps.put(it.file, it.modified);
                String old = devStamps.get(it.file);
                if (all.containsKey(it.file) && old != null && !old.isEmpty() && old.equals(it.modified)) continue;
                String txt = get(user, pass, it.href);
                if (txt == null) continue;
                Doc d = Doc.parse(txt);
                all.put(it.file, d);
                res.gotDevices.put(it.file, d);
            }
            all.keySet().retainAll(present);
            res.devices = present.size() + 1;

            // 2. merge everything
            Doc merged = local;
            for (Doc d : all.values()) merged = merge(merged, d);
            merged.owner = nick; merged.device = device; merged.account = accountId(user);
            res.merged = merged;

            // 3. upload my device file
            String body = merged.json();
            if (!body.equals(prevOwn)) {
                put(user, pass, APP + mine, body);
                res.uploadedOwn = body;
            }

            // 4. friends folder (optional)
            if (friendFolder != null && !friendFolder.isEmpty()) {
                Set<String> myDevs = new HashSet<>();
                for (Doc d : all.values()) if (!d.device.isEmpty()) myDevs.add(d.device);
                myDevs.add(device);
                String acc = accountId(user), ffile = friendFile(user);
                Doc share = new Doc();
                share.owner = nick; share.account = acc;
                for (Map.Entry<String, W> e : merged.vocab.entrySet()) {
                    W c = e.getValue().copy();
                    c.mastered = false;
                    share.vocab.put(e.getKey(), c);
                }
                for (A a : merged.arts) share.arts.add(a.copy());
                String fb = share.json();
                if (!fb.equals(prevFriend)) {
                    put(user, pass, dir(friendFolder) + ffile, fb);
                    res.uploadedFriend = fb;
                }
                List<Item> fl = list(user, pass, friendFolder);
                if (fl == null) throw new Exception("找不到好友共享文件夹。可能已被删除或取消共享，请在「账号」页重新设置。");
                Set<String> seen = new HashSet<>();
                for (Item it : fl) {
                    if (it.dir) continue;
                    String lf = it.file.toLowerCase(Locale.ROOT);
                    if (!lf.startsWith("ec_") || !lf.endsWith(".json") || it.file.equalsIgnoreCase(ffile)) continue;
                    String old = friendStamps.get(it.file);
                    boolean unchanged = old != null && !old.isEmpty() && old.equals(it.modified);
                    if (!unchanged) {
                        String txt = get(user, pass, it.href);
                        if (txt == null) continue;
                        Doc d = Doc.parse(txt);
                        // old files written by my own devices (desktop v10/v11 or phone v0.8): remove
                        if (acc.equals(d.account) || (!d.device.isEmpty() && myDevs.contains(d.device))) {
                            delete(user, pass, it.href);
                            continue;
                        }
                        res.gotFriends.put(it.file, d);
                    }
                    seen.add(it.file);
                    res.friendStamps.put(it.file, it.modified);
                }
                res.friendPresent = seen;
            }
        } catch (Exception e) {
            res.error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return res;
    }

    // ------------------------------------------------------------ friends cache
    static File cacheFile(Context c) {
        return new File(c.getFilesDir(), "friends_cache_v2.json");
    }

    public static LinkedHashMap<String, Doc> loadCache(Context c) {
        LinkedHashMap<String, Doc> m = new LinkedHashMap<>();
        try (FileInputStream in = new FileInputStream(cacheFile(c))) {
            JSONObject o = new JSONObject(ImportUtil.stream(in));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                m.put(k, Doc.parse(o.optString(k, "")));
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    public static void saveCache(Context c, Map<String, Doc> m) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(c))) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, Doc> e : m.entrySet()) o.put(e.getKey(), e.getValue().json());
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
