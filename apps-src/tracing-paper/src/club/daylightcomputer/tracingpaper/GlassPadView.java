package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The sheet of glass: a transparent canvas of notebooks. Strokes are stored
 * normalized (0..1 by canvas width/height, with pen pressure per point) so
 * they survive rotation and export cleanly to PDF at any size.
 *
 * Layers, bottom to top: white wash (0–100% opacity), the notebook's page
 * template, snipped screenshots, highlighter, ink. Highlights sit under the
 * ink so pen lines stay crisp; snips sit under highlights so you can
 * highlight what you clipped. The eraser rubs through all three.
 */
public class GlassPadView extends View {

    static final int TOOL_PEN = 0, TOOL_HIGHLIGHT = 1, TOOL_ERASE = 2;
    static final int KIND_INK = 0, KIND_ERASE = 1, KIND_HIGHLIGHT = 2;

    static final int TPL_BLANK = 0, TPL_LINED = 1, TPL_DOTS = 2, TPL_SCHOOL = 3;
    static final String[] TPL_NAMES = {"BLANK", "LINED", "DOTS", "SCHOOL"};

    /** light gray that still reads on the grayscale panel, ringed in black */
    static final int HI_FILL = 0x59969696;
    static final int HI_EDGE = Color.BLACK;

    static class Stroke {
        int kind;
        float base;              // stroke width as a fraction of canvas width
        float[] pts = new float[64 * 3]; // x, y, pressure triplets
        int n;

        void add(float x, float y, float p) {
            if (n * 3 == pts.length) {
                float[] t = new float[pts.length * 2];
                System.arraycopy(pts, 0, t, 0, pts.length);
                pts = t;
            }
            pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = p;
            n++;
        }
    }

    /** A screenshot clipping pasted on the glass, at its normalized rectangle. */
    static class Snip {
        String file;
        float x, y, w, h;
        Bitmap bmp; // decoded lazily, not serialized
    }

    static class PageData {
        final List<Stroke> strokes = new ArrayList<>();
        final List<Snip> snips = new ArrayList<>();
        boolean isEmpty() { return strokes.isEmpty() && snips.isEmpty(); }
    }

    static class Book {
        String name;
        int template;
        final List<PageData> pages = new ArrayList<>();
    }

    /** One undoable thing: a stroke, a snip, or a page wipe. */
    private static class Op {
        int page;
        Stroke stroke;
        Snip snip;
        PageData cleared;
    }

    interface StateListener { void onPadStateChanged(); }

    private final NoteStore store;
    private final List<Book> books;
    private int book;
    private int page;
    private int tool = TOOL_PEN;
    private boolean penOnly;
    private int opacity; // 0..100 white wash
    private StateListener listener;

    private Bitmap base, hi, ink;          // snips / highlights / pen ink
    private Canvas baseC, hiC, inkC;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rubber = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiClear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tpl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Stroke cur;
    private float lastX, lastY;
    private final ArrayDeque<Op> undo = new ArrayDeque<>();
    private final ArrayDeque<Op> redo = new ArrayDeque<>();
    private final Handler saver = new Handler(Looper.getMainLooper());
    private final Runnable saveNow = this::flushSave;
    private final float baseWidthPx, hiWidthPx, hiEdgePx;
    private long clearArmedAt;
    private WetInk wet;
    private boolean wetActive;

    public GlassPadView(Context c) {
        super(c);
        store = new NoteStore(c);
        NoteStore.Loaded loaded = store.load();
        books = loaded.books;
        book = Math.max(0, Math.min(loaded.cur, books.size() - 1));
        SharedPreferences p = Prefs.get(c);
        penOnly = p.getBoolean(Prefs.K_PEN_ONLY, false);
        opacity = Math.max(0, Math.min(p.getInt(Prefs.K_OPACITY, 30), 100));
        page = Math.max(0, Math.min(p.getInt(Prefs.K_LAST_PAGE, 0), cb().pages.size() - 1));

        float d = getResources().getDisplayMetrics().density;
        baseWidthPx = 3.5f * d;
        hiWidthPx = 16f * d;
        hiEdgePx = 1.5f * d;

        for (Paint pt : new Paint[]{pen, rubber, hiFill, hiEdge, hiClear}) {
            pt.setStyle(Paint.Style.STROKE);
            pt.setStrokeCap(Paint.Cap.ROUND);
            pt.setStrokeJoin(Paint.Join.ROUND);
        }
        pen.setColor(Color.BLACK);
        rubber.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        hiFill.setColor(HI_FILL);
        hiEdge.setColor(HI_EDGE);
        hiClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
    }

    void setStateListener(StateListener l) { listener = l; }

    /** Wet-ink surface for the in-progress stroke; pad falls back to the plain path without it. */
    void setWetInk(WetInk w) { wet = w; }

    private Book cb() { return books.get(book); }
    private PageData pg() { return cb().pages.get(page); }

    private void notifyState() { if (listener != null) listener.onPadStateChanged(); }

    // ------------------------------------------------------------- rendering

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        hi = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ink = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        baseC = new Canvas(base);
        hiC = new Canvas(hi);
        inkC = new Canvas(ink);
        Prefs.get(getContext()).edit()
                .putInt(Prefs.K_CANVAS_W, w).putInt(Prefs.K_CANVAS_H, h).apply();
        rebuild();
    }

    private void rebuild() {
        if (ink == null) return;
        base.eraseColor(Color.TRANSPARENT);
        hi.eraseColor(Color.TRANSPARENT);
        ink.eraseColor(Color.TRANSPARENT);
        for (Snip s : pg().snips) drawSnip(s);
        for (Stroke s : pg().strokes) render(s);
        invalidate();
        notifyState();
    }

    private void drawSnip(Snip s) {
        if (s.bmp == null) s.bmp = BitmapFactory.decodeFile(
                NoteStore.snipFile(getContext(), s.file).getPath());
        if (s.bmp == null) return;
        int w = getWidth(), h = getHeight();
        baseC.drawBitmap(s.bmp, null,
                new RectF(s.x * w, s.y * h, (s.x + s.w) * w, (s.y + s.h) * h), null);
    }

    private void render(Stroke s) {
        int w = getWidth(), h = getHeight();
        switch (s.kind) {
            case KIND_INK: {
                float bw = s.base * w;
                if (s.n == 1) {
                    pen.setStrokeWidth(bw * (0.5f + s.pts[2]));
                    inkC.drawPoint(s.pts[0] * w, s.pts[1] * h, pen);
                    return;
                }
                for (int i = 1; i < s.n; i++) {
                    pen.setStrokeWidth(bw * (0.5f + s.pts[i * 3 + 2]));
                    inkC.drawLine(s.pts[(i - 1) * 3] * w, s.pts[(i - 1) * 3 + 1] * h,
                            s.pts[i * 3] * w, s.pts[i * 3 + 1] * h, pen);
                }
                return;
            }
            case KIND_ERASE: {
                float bw = s.base * w * 4f;
                for (int i = 0; i < s.n; i++) {
                    rubber.setStrokeWidth(bw * (0.5f + s.pts[i * 3 + 2]));
                    float x = s.pts[i * 3] * w, y = s.pts[i * 3 + 1] * h;
                    if (i == 0) { eraseDot(x, y); continue; }
                    float px = s.pts[(i - 1) * 3] * w, py = s.pts[(i - 1) * 3 + 1] * h;
                    inkC.drawLine(px, py, x, y, rubber);
                    hiC.drawLine(px, py, x, y, rubber);
                    baseC.drawLine(px, py, x, y, rubber);
                }
                return;
            }
            case KIND_HIGHLIGHT: {
                // black ring, punched-out core, translucent gray fill — the
                // content underneath stays readable through the middle
                Path path = strokePath(s, w, h);
                float bw = s.base * w;
                hiEdge.setStrokeWidth(bw + hiEdgePx * 2);
                hiC.drawPath(path, hiEdge);
                hiClear.setStrokeWidth(bw);
                hiC.drawPath(path, hiClear);
                hiFill.setStrokeWidth(bw);
                hiC.drawPath(path, hiFill);
            }
        }
    }

    private void eraseDot(float x, float y) {
        inkC.drawPoint(x, y, rubber);
        hiC.drawPoint(x, y, rubber);
        baseC.drawPoint(x, y, rubber);
    }

    private static Path strokePath(Stroke s, int w, int h) {
        Path p = new Path();
        p.moveTo(s.pts[0] * w, s.pts[1] * h);
        if (s.n == 1) p.lineTo(s.pts[0] * w + 0.1f, s.pts[1] * h);
        for (int i = 1; i < s.n; i++) p.lineTo(s.pts[i * 3] * w, s.pts[i * 3 + 1] * h);
        return p;
    }

    /** Draws just the last segment of the in-progress stroke (fallback path). */
    private void renderSegment(float x, float y, float pr) {
        int w = getWidth();
        switch (cur.kind) {
            case KIND_INK:
                pen.setStrokeWidth(cur.base * w * (0.5f + pr));
                if (cur.n == 0) inkC.drawPoint(x, y, pen);
                else inkC.drawLine(lastX, lastY, x, y, pen);
                break;
            case KIND_ERASE:
                rubber.setStrokeWidth(cur.base * w * 4f * (0.5f + pr));
                if (cur.n == 0) { eraseDot(x, y); break; }
                inkC.drawLine(lastX, lastY, x, y, rubber);
                hiC.drawLine(lastX, lastY, x, y, rubber);
                baseC.drawLine(lastX, lastY, x, y, rubber);
                break;
            case KIND_HIGHLIGHT:
                // live preview without the ring; the real two-pass render lands on pen-up
                hiFill.setStrokeWidth(cur.base * w);
                if (cur.n == 0) hiC.drawPoint(x, y, hiFill);
                else hiC.drawLine(lastX, lastY, x, y, hiFill);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.argb(Math.round(opacity / 100f * 255f), 255, 255, 255));
        drawTemplate(canvas, cb().template, getWidth(), getHeight(), tpl, false);
        if (base != null) canvas.drawBitmap(base, 0, 0, null);
        if (hi != null) canvas.drawBitmap(hi, 0, 0, null);
        if (ink != null) canvas.drawBitmap(ink, 0, 0, null);
    }

    /** Shared with the PDF exporter, which passes onPaper=true for full-strength lines. */
    static void drawTemplate(Canvas cv, int template, float w, float h, Paint p, boolean onPaper) {
        if (template == TPL_BLANK || w <= 0 || h <= 0) return;
        p.setStyle(Paint.Style.STROKE);
        float sp = h / 26f;
        float thin = Math.max(1f, h / 900f);
        int line = onPaper ? 0xFFBDBDBD : 0x5A3C3C3C;
        int strong = onPaper ? 0xFF8A8A8A : 0x883C3C3C;
        if (template == TPL_LINED || template == TPL_SCHOOL) {
            p.setColor(line);
            p.setStrokeWidth(thin);
            for (float y = sp * 3; y < h; y += sp) cv.drawLine(0, y, w, y, p);
            if (template == TPL_SCHOOL) {
                p.setColor(strong);
                p.setStrokeWidth(thin * 2);
                cv.drawLine(w * 0.14f, 0, w * 0.14f, h, p);
            }
        } else if (template == TPL_DOTS) {
            p.setColor(onPaper ? 0xFF9E9E9E : 0x663C3C3C);
            p.setStyle(Paint.Style.FILL);
            float g = w / 24f, r = Math.max(1.5f, w / 700f);
            for (float y = g; y < h; y += g)
                for (float x = g / 2; x < w; x += g) cv.drawCircle(x, y, r, p);
            p.setStyle(Paint.Style.STROKE);
        }
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int toolType = e.getToolType(0);
        boolean stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS;
        boolean eraserEnd = toolType == MotionEvent.TOOL_TYPE_ERASER;
        if (penOnly && !stylus && !eraserEnd) return true; // glass eats the finger
        int kind = eraserEnd || tool == TOOL_ERASE ? KIND_ERASE
                : tool == TOOL_HIGHLIGHT ? KIND_HIGHLIGHT : KIND_INK;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // pen events straight from the digitizer, not batched to vsync
                requestUnbufferedDispatch(e);
                cur = new Stroke();
                cur.kind = kind;
                float widthPx = kind == KIND_HIGHLIGHT ? hiWidthPx : baseWidthPx;
                cur.base = widthPx / Math.max(1, getWidth());
                // erasing must show live on the dry layers, so only pen tools go wet
                wetActive = kind != KIND_ERASE && wet != null && wet.isReady();
                if (wetActive) wet.begin(widthPx, kind);
                addPoint(e.getX(), e.getY(), e.getPressure(), e.getEventTime());
                if (wetActive) wet.present();
                break;
            case MotionEvent.ACTION_MOVE:
                if (cur == null) return true;
                for (int i = 0; i < e.getHistorySize(); i++)
                    addPoint(e.getHistoricalX(i), e.getHistoricalY(i),
                            e.getHistoricalPressure(i), e.getHistoricalEventTime(i));
                addPoint(e.getX(), e.getY(), e.getPressure(), e.getEventTime());
                if (wetActive) wet.present();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (cur == null) return true;
                if (wetActive) {
                    render(cur); // dry the finished stroke into its layer
                    invalidate();
                    wet.end();
                    wetActive = false;
                } else if (cur.kind == KIND_HIGHLIGHT) {
                    rebuild(); // replace the live preview with the ringed render
                }
                pg().strokes.add(cur);
                Op op = new Op();
                op.page = page;
                op.stroke = cur;
                undo.push(op);
                trim(undo);
                redo.clear();
                cur = null;
                scheduleSave();
                notifyState();
                break;
        }
        return true;
    }

    private void addPoint(float x, float y, float rawPressure, long tMs) {
        float pr = Math.max(0.15f, Math.min(rawPressure, 1.3f));
        if (wetActive) {
            wet.addPoint(x, y, pr, tMs);
        } else {
            renderSegment(x, y, pr);
            invalidate();
        }
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        cur.add(x / w, y / h, pr);
        lastX = x; lastY = y;
    }

    private static void trim(ArrayDeque<Op> stack) {
        while (stack.size() > 100) stack.pollLast();
    }

    // ----------------------------------------------------------------- tools

    void setTool(int t) { tool = t; notifyState(); }
    int getTool() { return tool; }

    void setPenOnly(boolean v) { penOnly = v; }

    boolean undo() {
        Op o = undo.poll();
        if (o == null) return false;
        switchTo(o.page);
        if (o.stroke != null) pg().strokes.remove(o.stroke);
        else if (o.snip != null) pg().snips.remove(o.snip);
        else { cb().pages.set(o.page, copyOf(o.cleared)); }
        redo.push(o);
        rebuild();
        scheduleSave();
        return true;
    }

    boolean redo() {
        Op o = redo.poll();
        if (o == null) return false;
        switchTo(o.page);
        if (o.stroke != null) pg().strokes.add(o.stroke);
        else if (o.snip != null) pg().snips.add(o.snip);
        else { pg().strokes.clear(); pg().snips.clear(); }
        undo.push(o);
        rebuild();
        scheduleSave();
        return true;
    }

    private static PageData copyOf(PageData p) {
        PageData n = new PageData();
        n.strokes.addAll(p.strokes);
        n.snips.addAll(p.snips);
        return n;
    }

    /** First tap arms it, second tap within 3s clears — no dialog on glass. */
    void clearPage() {
        if (pg().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - clearArmedAt > 3000) {
            clearArmedAt = now;
            Toast.makeText(getContext(), "Tap CLEAR again to wipe this page", Toast.LENGTH_SHORT).show();
            return;
        }
        clearArmedAt = 0;
        Op o = new Op();
        o.page = page;
        o.cleared = copyOf(pg());
        undo.push(o);
        trim(undo);
        redo.clear();
        pg().strokes.clear();
        pg().snips.clear();
        rebuild();
        scheduleSave();
    }

    /** Long-press CLEAR: tear the page out entirely. */
    void deletePage() {
        if (cb().pages.size() <= 1) {
            pg().strokes.clear();
            pg().snips.clear();
        } else {
            cb().pages.remove(page);
            page = Math.min(page, cb().pages.size() - 1);
        }
        undo.clear();
        redo.clear();
        Toast.makeText(getContext(), "Page torn out", Toast.LENGTH_SHORT).show();
        rebuild();
        scheduleSave();
    }

    // ----------------------------------------------------------------- snips

    /** Pastes a screenshot clipping onto the current page, where it was cut from. */
    void addSnip(String file, RectF norm) {
        Snip s = new Snip();
        s.file = file;
        s.x = norm.left; s.y = norm.top;
        s.w = norm.width(); s.h = norm.height();
        pg().snips.add(s);
        drawSnip(s);
        invalidate();
        Op o = new Op();
        o.page = page;
        o.snip = s;
        undo.push(o);
        trim(undo);
        redo.clear();
        scheduleSave();
        notifyState();
    }

    // ----------------------------------------------------------------- pages

    private void switchTo(int p) {
        if (p == page) return;
        page = Math.max(0, Math.min(p, cb().pages.size() - 1));
        rebuild();
    }

    void prevPage() { if (page > 0) { flushSave(); switchTo(page - 1); } }
    void nextPage() { if (page < cb().pages.size() - 1) { flushSave(); switchTo(page + 1); } }

    void newPage() {
        flushSave();
        cb().pages.add(page + 1, new PageData());
        page++;
        undo.clear();
        redo.clear();
        rebuild();
        scheduleSave();
    }

    String pageLabel() { return (page + 1) + "/" + cb().pages.size(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    // ------------------------------------------------------------- notebooks

    List<Book> getBooks() { return books; }
    int curBook() { return book; }
    String bookName() { return cb().name; }

    void switchBook(int i) {
        if (i == book || i < 0 || i >= books.size()) return;
        flushSave();
        book = i;
        page = 0;
        undo.clear();
        redo.clear();
        rebuild();
        scheduleSave();
    }

    void newBook(String name, int template) {
        flushSave();
        Book b = new Book();
        b.name = name;
        b.template = Math.max(0, Math.min(template, TPL_NAMES.length - 1));
        b.pages.add(new PageData());
        books.add(b);
        book = books.size() - 1;
        page = 0;
        undo.clear();
        redo.clear();
        rebuild();
        scheduleSave();
    }

    void deleteBook(int i) {
        if (i < 0 || i >= books.size()) return;
        Book b = books.remove(i);
        for (PageData p : b.pages)
            for (Snip s : p.snips)
                NoteStore.snipFile(getContext(), s.file).delete();
        if (books.isEmpty()) {
            Book fresh = new Book();
            fresh.name = "Notes";
            fresh.template = TPL_BLANK;
            fresh.pages.add(new PageData());
            books.add(fresh);
        }
        if (book >= books.size()) book = books.size() - 1;
        else if (i < book) book--;
        page = Math.min(page, cb().pages.size() - 1);
        undo.clear();
        redo.clear();
        rebuild();
        scheduleSave();
    }

    // ----------------------------------------------------------------- glass

    void setOpacity(int pct) {
        opacity = Math.max(0, Math.min(pct, 100));
        Prefs.get(getContext()).edit().putInt(Prefs.K_OPACITY, opacity).apply();
        invalidate();
    }

    int getOpacity() { return opacity; }

    // ------------------------------------------------------------ persistence

    private void scheduleSave() {
        saver.removeCallbacks(saveNow);
        saver.postDelayed(saveNow, 800);
    }

    /** Structural copy safe to hand to a background thread. */
    List<Book> snapshotBooks() {
        List<Book> out = new ArrayList<>(books.size());
        for (Book b : books) {
            Book nb = new Book();
            nb.name = b.name;
            nb.template = b.template;
            for (PageData p : b.pages) nb.pages.add(copyOf(p));
            out.add(nb);
        }
        return out;
    }

    void flushSave() {
        saver.removeCallbacks(saveNow);
        store.saveAsync(snapshotBooks(), book);
        Prefs.get(getContext()).edit().putInt(Prefs.K_LAST_PAGE, page).apply();
    }
}
