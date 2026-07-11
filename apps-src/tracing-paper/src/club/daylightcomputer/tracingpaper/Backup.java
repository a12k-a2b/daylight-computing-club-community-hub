package club.daylightcomputer.tracingpaper;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Belt for the braces: whole-library backups as one portable JSON file in
 * Download/Tracing Paper/Backups (snips embedded), kept to the last seven.
 * Restore never overwrites — imported notebooks are appended. The reader
 * also understands Glassnote's .gn_2.json notebooks, so old notes can walk
 * straight onto this glass.
 */
final class Backup {

    private static final String DIR = Environment.DIRECTORY_DOWNLOADS + "/Tracing Paper/Backups";
    private static final String PREFIX = "tracing-paper-backup-";
    private static final int KEEP = 7;

    private Backup() {}

    // ------------------------------------------------------------- write

    static String writeBackup(Context c, List<GlassPadView.Book> books) throws Exception {
        String name = PREFIX + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
                .format(new Date()) + ".json";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, DIR);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("MediaStore refused the backup");
        try (OutputStream os = c.getContentResolver().openOutputStream(uri);
             java.io.BufferedWriter w = new java.io.BufferedWriter(
                     new java.io.OutputStreamWriter(os, StandardCharsets.UTF_8), 1 << 16)) {
            if (os == null) throw new IOException("no stream");
            // streamed, snips embedded — bounded memory however big the shelf
            NoteStore.writeLibrary(w, c, books, 0);
        }
        cv.clear();
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
        c.getContentResolver().update(uri, cv, null, null);
        prune(c);
        return name;
    }

    /** Keep the newest KEEP backups we own; timestamped names sort naturally. */
    private static void prune(Context c) {
        try {
            List<String> names = new ArrayList<>();
            List<Long> ids = new ArrayList<>();
            try (Cursor cur = c.getContentResolver().query(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME},
                    MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?",
                    new String[]{PREFIX + "%"},
                    MediaStore.MediaColumns.DISPLAY_NAME + " ASC")) {
                if (cur == null) return;
                while (cur.moveToNext()) {
                    ids.add(cur.getLong(0));
                    names.add(cur.getString(1));
                }
            }
            for (int i = 0; i < ids.size() - KEEP; i++) {
                c.getContentResolver().delete(
                        Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                String.valueOf(ids.get(i))), null, null);
            }
        } catch (Exception ignored) {
        }
    }

    // -------------------------------------------------------------- read

    /** Parses a backup (ours or Glassnote's) into notebooks, writing snip PNGs. */
    static List<GlassPadView.Book> read(Context c, Uri uri) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (InputStream in = c.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("no stream");
            byte[] buf = new byte[65536];
            int r;
            while ((r = in.read(buf)) > 0) bos.write(buf, 0, r);
        }
        JSONObject root = new JSONObject(bos.toString("UTF-8"));
        if (root.has("books")) return readOurs(c, root);
        if (root.has("drawings")) {
            List<GlassPadView.Book> one = new ArrayList<>();
            one.add(readGlassnote(c, root));
            return one;
        }
        throw new IOException("not a backup file this app knows");
    }

    private static List<GlassPadView.Book> readOurs(Context c, JSONObject root) throws Exception {
        List<GlassPadView.Book> out = new ArrayList<>();
        JSONArray bs = root.getJSONArray("books");
        for (int i = 0; i < bs.length(); i++) {
            JSONObject bo = bs.getJSONObject(i);
            GlassPadView.Book b = new GlassPadView.Book();
            b.name = bo.optString("n", "Restored");
            b.template = bo.optInt("t", GlassPadView.TPL_BLANK);
            b.opacity = bo.optInt("o", -1);
            b.createdTime = bo.optLong("c", 0);
            b.lastModified = bo.optLong("m", System.currentTimeMillis());
            JSONArray ps = bo.getJSONArray("pages");
            for (int j = 0; j < ps.length(); j++) {
                JSONObject po = ps.getJSONObject(j);
                GlassPadView.PageData pd = new GlassPadView.PageData();
                JSONArray ss = po.getJSONArray("s");
                for (int k = 0; k < ss.length(); k++) {
                    JSONObject o = ss.getJSONObject(k);
                    GlassPadView.Stroke s = new GlassPadView.Stroke();
                    s.kind = o.optInt("k", 0);
                    s.shade = o.optInt("c", 0);
                    s.base = (float) o.optDouble("w", 0.003);
                    JSONArray a = o.getJSONArray("p");
                    for (int m = 0; m + 2 < a.length(); m += 3)
                        s.add((float) a.getDouble(m), (float) a.getDouble(m + 1),
                                (float) a.getDouble(m + 2));
                    pd.strokes.add(s);
                }
                JSONArray si = po.optJSONArray("i");
                if (si != null) {
                    for (int k = 0; k < si.length(); k++) {
                        JSONObject so = si.getJSONObject(k);
                        GlassPadView.Snip s = new GlassPadView.Snip();
                        s.x = (float) so.getDouble("x");
                        s.y = (float) so.getDouble("y");
                        s.w = (float) so.getDouble("w");
                        s.h = (float) so.getDouble("h");
                        s.r = (float) so.optDouble("r", 0);
                        String data = so.optString("data", "");
                        if (!data.isEmpty()) s.file = writeSnip(c, Base64.decode(data, Base64.NO_WRAP));
                        else continue; // an old backup without pixels: skip the ghost
                        pd.snips.add(s);
                    }
                }
                b.pages.add(pd);
            }
            if (b.pages.isEmpty()) b.pages.add(new GlassPadView.PageData());
            out.add(b);
        }
        return out;
    }

    /**
     * Glassnote kept one tall scrolling canvas per notebook; we slice it into
     * pages at this device's aspect. Its strokes carry no pressure, so they
     * arrive as steady-handed monoline ink.
     */
    private static GlassPadView.Book readGlassnote(Context c, JSONObject root) throws Exception {
        GlassPadView.Book b = new GlassPadView.Book();
        b.name = root.optString("name", "Glassnote");
        String tpl = root.optString("pageTemplate", "BLANK");
        b.template = "LINED".equals(tpl) ? GlassPadView.TPL_LINED
                : "DOTS".equals(tpl) || "DOTTED".equals(tpl) ? GlassPadView.TPL_DOTS
                : "SCHOOL".equals(tpl) ? GlassPadView.TPL_SCHOOL : GlassPadView.TPL_BLANK;
        b.createdTime = root.optLong("createdTime", 0);
        b.lastModified = root.optLong("lastModified", System.currentTimeMillis());

        // canvas width ≈ the device it was written on; height per page from ours
        float canvasW = 0;
        JSONArray ds = root.optJSONArray("drawings");
        JSONArray is = root.optJSONArray("images");
        if (ds != null) for (int i = 0; i < ds.length(); i++) {
            JSONArray p = ds.getJSONObject(i).getJSONArray("pathPoints");
            for (int k = 0; k < p.length(); k++)
                canvasW = Math.max(canvasW, (float) p.getJSONObject(k).getDouble("x"));
        }
        if (is != null) for (int i = 0; i < is.length(); i++) {
            JSONObject im = is.getJSONObject(i);
            canvasW = Math.max(canvasW, (float) (im.getDouble("x") + im.getDouble("width")));
        }
        if (canvasW < 100) canvasW = 1179;
        android.content.SharedPreferences prefs = Prefs.get(c);
        float aspect = (float) prefs.getInt(Prefs.K_CANVAS_H, 1600)
                / Math.max(1, prefs.getInt(Prefs.K_CANVAS_W, 1200));
        float pageHpx = canvasW * aspect;

        java.util.TreeMap<Integer, GlassPadView.PageData> pages = new java.util.TreeMap<>();
        if (ds != null) for (int i = 0; i < ds.length(); i++) {
            JSONObject o = ds.getJSONObject(i);
            JSONArray p = o.getJSONArray("pathPoints");
            if (p.length() == 0) continue;
            float firstY = (float) p.getJSONObject(0).getDouble("y");
            int page = Math.max(0, (int) (firstY / pageHpx));
            GlassPadView.Stroke s = new GlassPadView.Stroke();
            s.kind = GlassPadView.KIND_INK;
            s.base = (float) o.optDouble("strokeWidth", 4) / canvasW;
            for (int k = 0; k < p.length(); k++) {
                JSONObject pt = p.getJSONObject(k);
                float x = (float) pt.getDouble("x") / canvasW;
                float y = ((float) pt.getDouble("y") - page * pageHpx) / pageHpx;
                s.add(Math.max(0, Math.min(x, 1)), Math.max(0, Math.min(y, 1)), 0.65f);
            }
            page(pages, page).strokes.add(s);
        }
        if (is != null) for (int i = 0; i < is.length(); i++) {
            JSONObject im = is.getJSONObject(i);
            String data = im.optString("imageData", "").replace("\n", "");
            if (data.isEmpty()) continue;
            float w = (float) im.getDouble("width") / canvasW;
            float h = (float) im.getDouble("height") / pageHpx;
            float cy = (float) im.getDouble("y") + (float) im.getDouble("height") / 2f;
            int pg = Math.max(0, (int) (cy / pageHpx));
            GlassPadView.Snip s = new GlassPadView.Snip();
            s.file = writeSnip(c, Base64.decode(data, Base64.DEFAULT));
            s.x = (float) im.getDouble("x") / canvasW;
            s.y = ((float) im.getDouble("y") - pg * pageHpx) / pageHpx;
            s.w = w;
            s.h = h;
            s.r = (float) im.optDouble("rotation", 0);
            page(pages, pg).snips.add(s);
        }
        int maxPage = pages.isEmpty() ? 0 : pages.lastKey();
        for (int i = 0; i <= maxPage; i++) b.pages.add(page(pages, i));
        if (b.pages.isEmpty()) b.pages.add(new GlassPadView.PageData());
        return b;
    }

    private static GlassPadView.PageData page(
            java.util.TreeMap<Integer, GlassPadView.PageData> m, int i) {
        GlassPadView.PageData p = m.get(i);
        if (p == null) {
            p = new GlassPadView.PageData();
            m.put(i, p);
        }
        return p;
    }

    private static String writeSnip(Context c, byte[] png) throws IOException {
        String name = "snip-restored-" + System.currentTimeMillis() + "-"
                + Integer.toHexString(java.util.Arrays.hashCode(png)) + ".png";
        File f = NoteStore.snipFile(c, name);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(png);
        }
        return name;
    }
}
