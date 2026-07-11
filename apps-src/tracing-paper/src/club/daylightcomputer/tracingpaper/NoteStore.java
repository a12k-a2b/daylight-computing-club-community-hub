package club.daylightcomputer.tracingpaper;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notes live in one JSON file in app-private storage — nothing ever leaves
 * the tablet unless you export it yourself.
 */
class NoteStore {

    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private final File file;
    private final File tmp;

    NoteStore(Context c) {
        file = new File(c.getFilesDir(), "notes.json");
        tmp = new File(c.getFilesDir(), "notes.json.tmp");
    }

    List<List<GlassPadView.Stroke>> load() {
        List<List<GlassPadView.Stroke>> pages = new ArrayList<>();
        try {
            if (file.exists()) {
                byte[] buf = new byte[(int) file.length()];
                try (FileInputStream in = new FileInputStream(file)) {
                    int off = 0, r;
                    while (off < buf.length && (r = in.read(buf, off, buf.length - off)) > 0) off += r;
                }
                JSONObject root = new JSONObject(new String(buf, StandardCharsets.UTF_8));
                JSONArray ps = root.getJSONArray("pages");
                for (int i = 0; i < ps.length(); i++) {
                    List<GlassPadView.Stroke> pg = new ArrayList<>();
                    JSONArray ss = ps.getJSONArray(i);
                    for (int j = 0; j < ss.length(); j++) {
                        JSONObject o = ss.getJSONObject(j);
                        GlassPadView.Stroke s = new GlassPadView.Stroke();
                        s.eraser = o.optBoolean("e", false);
                        s.base = (float) o.optDouble("w", 0.003);
                        JSONArray a = o.getJSONArray("p");
                        for (int k = 0; k + 2 < a.length(); k += 3)
                            s.add((float) a.getDouble(k), (float) a.getDouble(k + 1),
                                    (float) a.getDouble(k + 2));
                        pg.add(s);
                    }
                    pages.add(pg);
                }
            }
        } catch (Exception ignored) {
            // A corrupt file just means a fresh pad; the old file is overwritten on next save.
        }
        if (pages.isEmpty()) pages.add(new ArrayList<>());
        return pages;
    }

    void saveAsync(List<List<GlassPadView.Stroke>> snapshot) {
        IO.execute(() -> {
            try {
                JSONArray ps = new JSONArray();
                for (List<GlassPadView.Stroke> pg : snapshot) {
                    JSONArray ss = new JSONArray();
                    for (GlassPadView.Stroke s : pg) {
                        JSONObject o = new JSONObject();
                        o.put("e", s.eraser);
                        o.put("w", round(s.base));
                        JSONArray a = new JSONArray();
                        for (int k = 0; k < s.n * 3; k++) a.put(round(s.pts[k]));
                        o.put("p", a);
                        ss.put(o);
                    }
                    ps.put(ss);
                }
                JSONObject root = new JSONObject();
                root.put("v", 1);
                root.put("pages", ps);
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(root.toString().getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(file)) {
                    tmp.delete();
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static double round(float v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
