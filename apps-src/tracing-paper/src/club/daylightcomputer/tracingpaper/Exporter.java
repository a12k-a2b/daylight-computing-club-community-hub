package club.daylightcomputer.tracingpaper;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Screenshots go to Pictures, PDFs go to Download — both via MediaStore, no storage permission. */
final class Exporter {

    private static final String FOLDER = "Tracing Paper";

    private Exporter() {}

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
    }

    static String savePng(Context c, Bitmap b) throws IOException {
        String name = "tracing-paper-" + stamp() + ".png";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        cv.put(MediaStore.MediaColumns.MIME_TYPE, "image/png");
        cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + FOLDER);
        cv.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = c.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
        if (uri == null) throw new IOException("MediaStore refused the image");
        try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
            if (os == null) throw new IOException("no stream");
            b.compress(Bitmap.CompressFormat.PNG, 100, os);
        }
        cv.clear();
        cv.put(MediaStore.MediaColumns.IS_PENDING, 0);
        c.getContentResolver().update(uri, cv, null, null);
        return name;
    }

    /**
     * Every page of the pad as one PDF, ink drawn as vectors.
     * @param aspect canvas width / height, so pages keep their shape.
     */
    static String exportPdf(Context c, List<List<GlassPadView.Stroke>> pages, float aspect)
            throws IOException {
        int pw = 612, ph = 792; // US Letter, points
        float margin = 28f;

        PdfDocument doc = new PdfDocument();
        Paint ink = new Paint(Paint.ANTI_ALIAS_FLAG);
        ink.setStyle(Paint.Style.STROKE);
        ink.setStrokeCap(Paint.Cap.ROUND);
        ink.setStrokeJoin(Paint.Join.ROUND);
        ink.setColor(Color.BLACK);
        Paint rubber = new Paint(ink);
        rubber.setColor(Color.WHITE); // PDF pages are white; erasing = painting white
        Paint frame = new Paint();
        frame.setStyle(Paint.Style.STROKE);
        frame.setStrokeWidth(0.75f);
        frame.setColor(0xFF888888);
        Paint footer = new Paint(Paint.ANTI_ALIAS_FLAG);
        footer.setColor(0xFF666666);
        footer.setTextSize(9f);
        footer.setTextAlign(Paint.Align.CENTER);

        try {
            for (int i = 0; i < pages.size(); i++) {
                PdfDocument.PageInfo info =
                        new PdfDocument.PageInfo.Builder(pw, ph, i + 1).create();
                PdfDocument.Page page = doc.startPage(info);
                Canvas cv = page.getCanvas();

                float bw = pw - 2 * margin, bh = ph - 2 * margin;
                float cw, ch;
                if (bw / bh > aspect) { ch = bh; cw = ch * aspect; }
                else { cw = bw; ch = cw / aspect; }
                float ox = (pw - cw) / 2f, oy = (ph - ch) / 2f;

                cv.drawRect(ox, oy, ox + cw, oy + ch, frame);
                for (GlassPadView.Stroke s : pages.get(i)) {
                    Paint p = s.eraser ? rubber : ink;
                    float base = s.base * cw * (s.eraser ? 4f : 1f);
                    if (s.n == 1) {
                        p.setStrokeWidth(base * (0.5f + s.pts[2]));
                        cv.drawPoint(ox + s.pts[0] * cw, oy + s.pts[1] * ch, p);
                        continue;
                    }
                    for (int k = 1; k < s.n; k++) {
                        p.setStrokeWidth(base * (0.5f + s.pts[k * 3 + 2]));
                        cv.drawLine(ox + s.pts[(k - 1) * 3] * cw, oy + s.pts[(k - 1) * 3 + 1] * ch,
                                ox + s.pts[k * 3] * cw, oy + s.pts[k * 3 + 1] * ch, p);
                    }
                }
                cv.drawText((i + 1) + " / " + pages.size(), pw / 2f, ph - 14f, footer);
                doc.finishPage(page);
            }

            String name = "tracing-paper-" + stamp() + ".pdf";
            ContentValues cv2 = new ContentValues();
            cv2.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv2.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            cv2.put(MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + FOLDER);
            cv2.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv2);
            if (uri == null) throw new IOException("MediaStore refused the PDF");
            try (OutputStream os = c.getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("no stream");
                doc.writeTo(os);
            }
            cv2.clear();
            cv2.put(MediaStore.MediaColumns.IS_PENDING, 0);
            c.getContentResolver().update(uri, cv2, null, null);
            return name;
        } finally {
            doc.close();
        }
    }
}
