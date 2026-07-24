package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notebooks live in one JSON file in app-private storage, snips as PNGs in a
 * private folder next to it — nothing ever leaves the tablet unless you
 * export it yourself.
 *
 * v2 format: {v:2, cur, books:[{n,t,pages:[{s:[{k,w,p:[x,y,pr,...]}], i:[{f,x,y,w,h}]}]}]}
 * v1 files (a bare page list) are migrated into a single "Notes" book.
 */
class NoteStore {

    static class Loaded {
        final List<GlassPadView.Book> books = new ArrayList<>();
        int cur;
    }

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final File file;
    private final File tmp;
    private final File bak;
    private final Context ctx;

    /** Decoded snips, size-capped so a page of clippings can't bloat memory. */
    private static final android.util.LruCache<String, android.graphics.Bitmap> SNIPS =
            new android.util.LruCache<String, android.graphics.Bitmap>(48 * 1024 * 1024) {
                @Override protected int sizeOf(String k, android.graphics.Bitmap b) {
                    return b.getByteCount();
                }
            };

    NoteStore(Context c) {
        ctx = c.getApplicationContext();
        file = new File(c.getFilesDir(), "notes.json");
        tmp = new File(c.getFilesDir(), "notes.json.tmp");
        bak = new File(c.getFilesDir(), "notes.json.bak");
    }

    /** Bounds-checked, downsampled, cached snip decode. */
    static android.graphics.Bitmap snipBitmap(Context c, String name) {
        android.graphics.Bitmap b = SNIPS.get(name);
        if (b != null) return b;
        File f = snipFile(c, name);
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        int sample = 1;
        while (o.outWidth / sample > 2048 || o.outHeight / sample > 2048) sample *= 2;
        o.inJustDecodeBounds = false;
        o.inSampleSize = sample;
        o.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565; // half the RAM; fine for text
        b = BitmapFactory.decodeFile(f.getPath(), o);
        if (b != null) SNIPS.put(name, b);
        return b;
    }

    /** Raw stored bytes of a snip file, for embedding in backups without re-encoding. */
    static byte[] snipBytes(Context c, String name) {
        try {
            File f = snipFile(c, name);
            byte[] buf = new byte[(int) f.length()];
            try (FileInputStream in = new FileInputStream(f)) {
                int off = 0, r;
                while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
            }
            return buf;
        } catch (Exception e) {
            return null;
        }
    }

    static File snipDir(Context c) {
        File d = new File(c.getFilesDir(), "snips");
        d.mkdirs();
        return d;
    }

    static File snipFile(Context c, String name) {
        return new File(snipDir(c), name);
    }

    /**
     * Saves a snip bitmap and returns its stored file name. Lossy WebP at
     * q87: several times smaller than PNG on disk, text still crisp enough
     * to read — the quality floor is legibility, not photography.
     */
    static String saveSnip(Context c, Bitmap b) throws Exception {
        String name = "snip-" + new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US)
                .format(new Date()) + ".webp";
        try (FileOutputStream out = new FileOutputStream(snipFile(c, name))) {
            b.compress(Bitmap.CompressFormat.WEBP_LOSSY, 87, out);
        }
        return name;
    }

    Loaded load() {
        // Never let a corrupt file quietly become a fresh empty pad: quarantine
        // it, fall back to the previous save, and only then start blank.
        Loaded out = tryLoad(file);
        if (out == null) {
            if (file.exists()) {
                file.renameTo(new File(file.getParent(),
                        "notes.json.corrupt-" + System.currentTimeMillis()));
            }
            out = tryLoad(bak);
        }
        if (out == null) out = new Loaded();
        if (out.books.isEmpty()) {
            GlassPadView.Book b = new GlassPadView.Book();
            b.name = "Notes";
            b.template = GlassPadView.TPL_BLANK;
            b.createdTime = b.lastModified = System.currentTimeMillis();
            b.pages.add(new GlassPadView.PageData());
            out.books.add(b);
            out.cur = 0;
        }
        sweepOrphanSnips(out);
        return out;
    }

    /** Old snip files nothing references any more; age-gated so undo stays safe. */
    private void sweepOrphanSnips(Loaded loaded) {
        try {
            java.util.HashSet<String> live = new java.util.HashSet<>();
            for (GlassPadView.Book b : loaded.books)
                for (GlassPadView.PageData p : b.pages)
                    for (GlassPadView.Snip s : p.snips) live.add(s.file);
            long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
            File[] all = snipDir(ctx).listFiles();
            if (all == null) return;
            for (File f : all)
                if (!live.contains(f.getName()) && f.lastModified() < cutoff) f.delete();
        } catch (Exception ignored) {
        }
    }

    private Loaded tryLoad(File src) {
        Loaded out = new Loaded();
        try {
            if (src.exists()) {
                byte[] buf = new byte[(int) src.length()];
                try (FileInputStream in = new FileInputStream(src)) {
                    int off = 0, r;
                    while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
                }
                JSONObject root = new JSONObject(new String(buf, StandardCharsets.UTF_8));
                if (root.has("books")) {
                    out.cur = root.optInt("cur", 0);
                    JSONArray bs = root.getJSONArray("books");
                    for (int i = 0; i < bs.length(); i++) {
                        JSONObject bo = bs.getJSONObject(i);
                        GlassPadView.Book b = new GlassPadView.Book();
                        b.name = bo.optString("n", "Notes");
                        b.template = bo.optInt("t", GlassPadView.TPL_BLANK);
                        b.opacity = bo.optInt("o", -1);
                        b.createdTime = bo.optLong("c", 0);
                        b.lastModified = bo.optLong("m", 0);
                        JSONArray ps = bo.getJSONArray("pages");
                        for (int j = 0; j < ps.length(); j++) b.pages.add(readPage(ps.getJSONObject(j)));
                        if (b.pages.isEmpty()) b.pages.add(new GlassPadView.PageData());
                        out.books.add(b);
                    }
                } else if (root.has("pages")) {
                    // v1: one implicit notebook of bare stroke lists
                    GlassPadView.Book b = new GlassPadView.Book();
                    b.name = "Notes";
                    b.template = GlassPadView.TPL_BLANK;
                    JSONArray ps = root.getJSONArray("pages");
                    for (int i = 0; i < ps.length(); i++) {
                        GlassPadView.PageData pd = new GlassPadView.PageData();
                        readStrokes(ps.getJSONArray(i), pd.strokes);
                        b.pages.add(pd);
                    }
                    if (b.pages.isEmpty()) b.pages.add(new GlassPadView.PageData());
                    out.books.add(b);
                }
            }
        } catch (Exception broken) {
            return null; // caller quarantines and falls back to the previous save
        }
        return out;
    }

    private static GlassPadView.PageData readPage(JSONObject po) throws Exception {
        GlassPadView.PageData pd = new GlassPadView.PageData();
        readStrokes(po.getJSONArray("s"), pd.strokes);
        JSONArray si = po.optJSONArray("i");
        if (si != null) {
            for (int k = 0; k < si.length(); k++) {
                JSONObject so = si.getJSONObject(k);
                GlassPadView.Snip s = new GlassPadView.Snip();
                s.file = so.getString("f");
                s.x = (float) so.getDouble("x");
                s.y = (float) so.getDouble("y");
                s.w = (float) so.getDouble("w");
                s.h = (float) so.getDouble("h");
                s.r = (float) so.optDouble("r", 0);
                pd.snips.add(s);
            }
        }
        return pd;
    }

    private static void readStrokes(JSONArray ss, List<GlassPadView.Stroke> into) throws Exception {
        for (int j = 0; j < ss.length(); j++) {
            JSONObject o = ss.getJSONObject(j);
            GlassPadView.Stroke s = new GlassPadView.Stroke();
            if (o.has("k")) s.kind = o.getInt("k");
            else s.kind = o.optBoolean("e", false)
                    ? GlassPadView.KIND_ERASE : GlassPadView.KIND_INK; // v1
            s.shade = o.optInt("c", 0);
            s.base = (float) o.optDouble("w", 0.003);
            JSONArray a = o.getJSONArray("p");
            for (int k = 0; k + 2 < a.length(); k += 3)
                s.add((float) a.getDouble(k), (float) a.getDouble(k + 1),
                        (float) a.getDouble(k + 2));
            into.add(s);
        }
    }

    void saveAsync(List<GlassPadView.Book> snapshot, int cur) {
        IO.execute(() -> {
            try {
                // stream, don't build: a 30,000-stroke shelf serializes in
                // bounded memory (the chaos monkey OOM'd the one-big-string
                // version), fsync'd before the atomic swap as before
                FileOutputStream fos = new FileOutputStream(tmp);
                java.io.BufferedWriter w = new java.io.BufferedWriter(
                        new java.io.OutputStreamWriter(fos, StandardCharsets.UTF_8), 1 << 16);
                writeLibrary(w, null, snapshot, cur);
                w.flush();
                fos.getFD().sync();
                w.close();
                if (file.exists()) {
                    bak.delete();
                    file.renameTo(bak);
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete();
                }
            } catch (Throwable ignored) {
                // a failed save must never take the pad down with it —
                // the previous generation is still intact on disk
            }
        });
    }

    /**
     * Streams the library as JSON.
     * @param embed non-null embeds snip pixels base64 (portable backups);
     *              null writes snip file references (the on-device store)
     */
    static void writeLibrary(java.io.Writer w, Context embed,
            List<GlassPadView.Book> books, int cur) throws java.io.IOException {
        w.write("{\"v\":2,");
        if (embed != null) w.write("\"app\":\"tracing-paper\",");
        w.write("\"cur\":");
        w.write(Integer.toString(cur));
        w.write(",\"books\":[");
        for (int bi = 0; bi < books.size(); bi++) {
            GlassPadView.Book b = books.get(bi);
            if (bi > 0) w.write(',');
            w.write("{\"n\":");
            w.write(JSONObject.quote(b.name == null ? "Notes" : b.name));
            w.write(",\"t\":");
            w.write(Integer.toString(b.template));
            if (b.opacity >= 0) { w.write(",\"o\":"); w.write(Integer.toString(b.opacity)); }
            w.write(",\"c\":");
            w.write(Long.toString(b.createdTime));
            w.write(",\"m\":");
            w.write(Long.toString(b.lastModified));
            w.write(",\"pages\":[");
            for (int pi = 0; pi < b.pages.size(); pi++) {
                GlassPadView.PageData pd = b.pages.get(pi);
                if (pi > 0) w.write(',');
                w.write("{\"s\":[");
                for (int si = 0; si < pd.strokes.size(); si++) {
                    GlassPadView.Stroke s = pd.strokes.get(si);
                    if (si > 0) w.write(',');
                    w.write("{\"k\":");
                    w.write(Integer.toString(s.kind));
                    if (s.shade != 0) { w.write(",\"c\":"); w.write(Integer.toString(s.shade)); }
                    w.write(",\"w\":");
                    w.write(Double.toString(round(s.base)));
                    w.write(",\"p\":[");
                    for (int k = 0; k < s.n * 3; k++) {
                        if (k > 0) w.write(',');
                        w.write(Double.toString(round(s.pts[k])));
                    }
                    w.write("]}");
                }
                w.write(']');
                if (!pd.snips.isEmpty()) {
                    w.write(",\"i\":[");
                    for (int k = 0; k < pd.snips.size(); k++) {
                        GlassPadView.Snip s = pd.snips.get(k);
                        if (k > 0) w.write(',');
                        w.write("{\"x\":");
                        w.write(Double.toString(round(s.x)));
                        w.write(",\"y\":");
                        w.write(Double.toString(round(s.y)));
                        w.write(",\"w\":");
                        w.write(Double.toString(round(s.w)));
                        w.write(",\"h\":");
                        w.write(Double.toString(round(s.h)));
                        if (s.r != 0) { w.write(",\"r\":"); w.write(Double.toString(round(s.r))); }
                        if (embed == null) {
                            w.write(",\"f\":");
                            w.write(JSONObject.quote(s.file));
                        } else {
                            byte[] raw = snipBytes(embed, s.file);
                            if (raw != null) {
                                w.write(",\"data\":\"");
                                w.write(android.util.Base64.encodeToString(raw,
                                        android.util.Base64.NO_WRAP));
                                w.write('\"');
                            }
                        }
                        w.write('}');
                    }
                    w.write(']');
                }
                w.write('}');
            }
            w.write("]}");
        }
        w.write("]}");
    }

    private static double round(float v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
