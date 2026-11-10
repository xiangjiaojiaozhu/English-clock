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
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Sharing via Jianguoyun (WebDAV), same format as the desktop version (v11):
 * every person writes only their own file "ec_xxxx.json" (named after account + nickname)
 * in the shared folder and only reads the other files.
 */
public class ShareSync {
    static final String HOST = "https://dav.jianguoyun.com";
    public static final String ROOT = "/dav/";
    static final String BAD401 = "坚果云账号或应用密码不对（或已失效）。\n\n注意：这里要填坚果云「账户信息 → 安全选项 → 第三方应用管理」里生成的应用密码，不是平时的登录密码。";

    static final OkHttpClient HTTP = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build();

    public static class Item {
        public String name = "", file = "", href = "", modified = "";
        public boolean dir;

        @Override
        public String toString() {
            return name;
        }
    }

    public static class Result {
        public String error, uploaded;
        public boolean checked;
        public Map<String, JSONObject> got = new HashMap<>();
        public Map<String, String> stamps = new HashMap<>();
        public Set<String> present;
    }

    static class Resp {
        int code;
        String body = "";
    }

    // ------------------------------------------------------------ settings
    static SharedPreferences prefs(Context c) {
        return Schedules.prefs(c);
    }

    public static String user(Context c) { return prefs(c).getString("sync_user", ""); }
    public static String nick(Context c) { return prefs(c).getString("sync_nick", ""); }
    public static String folder(Context c) { return prefs(c).getString("sync_folder", ""); }
    public static String folderName(Context c) { return prefs(c).getString("sync_folder_name", ""); }
    public static String pass(Context c) { return unprotect(prefs(c).getString("sync_pass", "")); }

    public static boolean loggedIn(Context c) {
        return user(c).length() > 0 && prefs(c).getString("sync_pass", "").length() > 0;
    }

    public static boolean ready(Context c) {
        return loggedIn(c) && folder(c).length() > 0;
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
    /** same rule as the desktop version: one file per (account, nickname) */
    public static String fileFor(String user, String nick) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] h = md.digest((user.trim().toLowerCase(Locale.ROOT) + "|" + nick.trim()).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("ec_");
            for (int i = 0; i < 6; i++) sb.append(String.format(Locale.ROOT, "%02x", h[i] & 0xff));
            return sb.append(".json").toString();
        } catch (Exception e) {
            return "ec_mobile.json";
        }
    }

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

    // ------------------------------------------------------------ WebDAV
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
            if (r.code >= 200 && r.code < 300) return null;
            if (r.code == 403) return "坚果云拒绝访问（403）。请确认应用密码有效，或稍后再试。";
            return "坚果云返回错误 " + r.code;
        } catch (Exception e) {
            return e.getMessage();
        }
    }

    static String first(Element e, String name) {
        NodeList n = e.getElementsByTagNameNS("DAV:", name);
        return n.getLength() > 0 ? n.item(0).getTextContent().trim() : "";
    }

    public static List<Item> list(String user, String pass, String href) throws Exception {
        Resp r = send("PROPFIND", dir(href), user, pass, propBody(), "text/xml; charset=utf-8", "1");
        if (r.code == 401) throw new Exception(BAD401);
        if (r.code == 404) throw new Exception("找不到共享文件夹。可能已被删除或取消共享，请在「共享」页点「更换文件夹」重新选择。");
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
        if (r.code == 403) throw new Exception("没有写入权限（403）。如果这个文件夹是别人共享给你的，请让对方把你的权限设为「可编辑」。");
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
    public static Result run(String user, String pass, String folder, String mine, String nick, String device,
                             String body, String prevUp, boolean needCheck, Map<String, String> oldStamps) {
        Result r = new Result();
        try {
            if (needCheck) {
                String own = get(user, pass, folder + mine);
                if (own != null) {
                    String d = "";
                    try { d = new JSONObject(own).optString("Device", ""); } catch (Exception ignored) { }
                    if (d.length() > 0 && !d.equals(device))
                        throw new Exception("昵称「" + nick + "」已经被这个坚果云账号下的另一台设备（比如你的电脑）使用了。\n"
                                + "请点「改昵称」，换一个不同的名字，例如「" + nick + "-手机」。");
                }
                r.checked = true;
            }
            if (!body.equals(prevUp)) {
                put(user, pass, folder + mine, body);
                r.uploaded = body;
            }
            Set<String> seen = new HashSet<>();
            for (Item it : list(user, pass, folder)) {
                if (it.dir) continue;
                String fn = it.file;
                String lf = fn.toLowerCase(Locale.ROOT);
                if (!lf.startsWith("ec_") || !lf.endsWith(".json") || fn.equalsIgnoreCase(mine)) continue;
                String old = oldStamps.get(fn);
                boolean unchanged = old != null && !it.modified.isEmpty() && old.equals(it.modified);
                if (!unchanged) {
                    String txt = get(user, pass, it.href);
                    if (txt == null) continue;
                    JSONObject doc;
                    try { doc = new JSONObject(txt); } catch (Exception e) { continue; }
                    if (device.equals(doc.optString("Device", ""))) {   // my own file under an old nickname
                        delete(user, pass, it.href);
                        continue;
                    }
                    r.got.put(fn, doc);
                }
                seen.add(fn);
                r.stamps.put(fn, it.modified);
            }
            r.present = seen;
        } catch (Exception e) {
            r.error = e.getMessage() == null ? e.toString() : e.getMessage();
        }
        return r;
    }

    // ------------------------------------------------------------ friends cache
    static File cacheFile(Context c) {
        return new File(c.getFilesDir(), "friends_cache.json");
    }

    public static LinkedHashMap<String, JSONObject> loadCache(Context c) {
        LinkedHashMap<String, JSONObject> m = new LinkedHashMap<>();
        try (FileInputStream in = new FileInputStream(cacheFile(c))) {
            JSONObject o = new JSONObject(ImportUtil.stream(in));
            Iterator<String> it = o.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONObject v = o.optJSONObject(k);
                if (v != null) m.put(k, v);
            }
        } catch (Exception ignored) {
        }
        return m;
    }

    public static void saveCache(Context c, Map<String, JSONObject> m) {
        try (FileOutputStream out = new FileOutputStream(cacheFile(c))) {
            JSONObject o = new JSONObject();
            for (Map.Entry<String, JSONObject> e : m.entrySet()) o.put(e.getKey(), e.getValue());
            out.write(o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }
}
