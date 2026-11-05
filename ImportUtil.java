package com.xiangjiaojiaozhu.englishclock;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.provider.OpenableColumns;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.rendering.PDFRenderer;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class ImportUtil {

    public static String read(Context c, Uri u) throws Exception {
        String n = name(c, u).toLowerCase(Locale.ROOT);
        String mime = c.getContentResolver().getType(u);
        if (mime == null) mime = "";
        if (n.endsWith(".pdf") || mime.contains("pdf")) return pdf(c, u);
        if (n.endsWith(".docx") || mime.contains("wordprocessingml")) return docx(c, u);
        return stream(c.getContentResolver().openInputStream(u));
    }

    static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toByteArray();
    }

    public static String stream(InputStream in) throws IOException {
        if (in == null) throw new IOException("无法打开文件");
        try {
            return new String(readAll(in), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    public static String name(Context c, Uri u) {
        try (Cursor q = c.getContentResolver().query(u, null, null, null, null)) {
            if (q != null && q.moveToFirst()) {
                int i = q.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0 && q.getString(i) != null) return q.getString(i);
            }
        } catch (Exception ignored) {
        }
        String p = u.getLastPathSegment();
        return p == null ? "article" : p;
    }

    static String docx(Context c, Uri u) throws Exception {
        try (ZipInputStream z = new ZipInputStream(c.getContentResolver().openInputStream(u))) {
            ZipEntry e;
            while ((e = z.getNextEntry()) != null) {
                if (!"word/document.xml".equals(e.getName())) continue;
                byte[] xml = readAll(z);
                Document d = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(xml));
                NodeList ps = d.getElementsByTagName("w:p");
                StringBuilder out = new StringBuilder();
                for (int i = 0; i < ps.getLength(); i++) {
                    NodeList ts = ((Element) ps.item(i)).getElementsByTagName("w:t");
                    StringBuilder line = new StringBuilder();
                    for (int j = 0; j < ts.getLength(); j++) line.append(ts.item(j).getTextContent());
                    if (line.toString().trim().length() > 0) out.append(line).append("\n\n");
                }
                return out.toString();
            }
        }
        throw new Exception("DOCX 里没有找到正文");
    }

    static String pdf(Context c, Uri u) throws Exception {
        PDFBoxResourceLoader.init(c.getApplicationContext());
        File f = File.createTempFile("english_clock_", ".pdf", c.getCacheDir());
        try {
            try (InputStream in = c.getContentResolver().openInputStream(u);
                 OutputStream out = new FileOutputStream(f)) {
                if (in == null) throw new IOException("无法打开 PDF");
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            }
            try (PDDocument d = PDDocument.load(f)) {
                String t = new PDFTextStripper().getText(d);
                if (t.replaceAll("[^A-Za-z]", "").length() >= 30) return tidy(t);
                PDFRenderer r = new PDFRenderer(d);
                TextRecognizer rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
                StringBuilder s = new StringBuilder();
                try {
                    for (int p = 0; p < d.getNumberOfPages(); p++) {
                        Bitmap b = r.renderImageWithDPI(p, 200);
                        com.google.mlkit.vision.text.Text res = Tasks.await(rec.process(InputImage.fromBitmap(b, 0)));
                        s.append(res.getText()).append("\n\n");
                        b.recycle();
                    }
                } finally {
                    rec.close();
                }
                return tidy(s.toString());
            }
        } finally {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    static String tidy(String t) {
        t = t.replace("\r", "");
        t = t.replaceAll("-\\n(?=[a-z])", "");
        t = t.replaceAll("(?<=[.!?\"\u201D])\\n(?=[A-Z\u201C\"])", "\n\n");
        t = t.replaceAll("(?<!\\n)\\n(?!\\n)", " ");
        t = t.replaceAll("[ \\t]{2,}", " ");
        return t.trim();
    }
}
