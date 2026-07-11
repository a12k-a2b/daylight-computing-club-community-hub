package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.OverScroller;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The sheet of glass: a roll of discrete pages you scroll through in one
 * continuous motion — seams between pages, but never a page-turn gesture.
 * Strokes are stored normalized per page (0..1 by page width/height, with
 * pen pressure per point) so they survive rotation and export cleanly.
 *
 * Layers per page, bottom to top: white wash (0–100% opacity), the
 * notebook's template, snips, highlighter, ink. Snips are objects — tap
 * with PICK (or right after snipping) to move, resize, and tilt them, with
 * a gentle snap to straight when you get close to a right angle.
 */
public class GlassPadView extends View {

    static final int TOOL_PEN = 0, TOOL_HIGHLIGHT = 1, TOOL_ERASE = 2, TOOL_PICK = 3;
    static final int KIND_INK = 0, KIND_ERASE = 1, KIND_HIGHLIGHT = 2;

    static final int TPL_BLANK = 0, TPL_LINED = 1, TPL_DOTS = 2, TPL_SCHOOL = 3;
    static final String[] TPL_NAMES = {"BLANK", "LINED", "DOTS", "SCHOOL"};

    /** light gray that still reads on the grayscale panel, ringed in black */
    static final int HI_FILL = 0x59969696;
    static final int HI_EDGE = Color.BLACK;

    private static final float SNAP_DEG = 4f;

    /** Tool size steps, 1..5, as multiples of the base width. */
    static final float[] SIZE_MULS = {0.6f, 1f, 1.5f, 2.2f, 3.2f};

    static float sizeMul(int idx) {
        return SIZE_MULS[Math.max(0, Math.min(idx - 1, SIZE_MULS.length - 1))];
    }

    static class Stroke {
        int kind;
        float base;              // stroke width as a fraction of page width
        float[] pts = new float[64 * 3]; // x, y, pressure triplets
        int n;
        private float bMinX = Float.NaN, bMinY, bMaxX, bMaxY; // lazy bounds

        void add(float x, float y, float p) {
            if (n * 3 == pts.length) {
                float[] t = new float[pts.length * 2];
                System.arraycopy(pts, 0, t, 0, pts.length);
                pts = t;
            }
            pts[n * 3] = x; pts[n * 3 + 1] = y; pts[n * 3 + 2] = p;
            n++;
            bMinX = Float.NaN;
        }

        boolean nearAny(float x, float y, float radius) {
            if (n == 0) return false;
            if (Float.isNaN(bMinX)) {
                bMinX = bMaxX = pts[0];
                bMinY = bMaxY = pts[1];
                for (int i = 1; i < n; i++) {
                    bMinX = Math.min(bMinX, pts[i * 3]); bMaxX = Math.max(bMaxX, pts[i * 3]);
                    bMinY = Math.min(bMinY, pts[i * 3 + 1]); bMaxY = Math.max(bMaxY, pts[i * 3 + 1]);
                }
            }
            if (x < bMinX - radius || x > bMaxX + radius
                    || y < bMinY - radius || y > bMaxY + radius) return false;
            for (int i = 0; i < n; i++) {
                float dx = pts[i * 3] - x, dy = pts[i * 3 + 1] - y;
                if (dx * dx + dy * dy < radius * radius) return true;
            }
            return false;
        }
    }

    /** A screenshot clipping pasted on the glass — a movable, tiltable object. */
    static class Snip {
        String file;
        float x, y, w, h;        // normalized within its page
        float r;                 // degrees about the center
        Bitmap bmp;              // decoded lazily, not serialized
    }

    static class PageData {
        final List<Stroke> strokes = new ArrayList<>();
        final List<Snip> snips = new ArrayList<>();
        boolean isEmpty() { return strokes.isEmpty() && snips.isEmpty(); }
    }

    static class Book {
        String name;
        int template;
        long createdTime, lastModified;
        final List<PageData> pages = new ArrayList<>();
    }

    /** One undoable thing. */
    private static class Op {
        static final int STROKE = 0, SNIP_ADD = 1, SNIP_EDIT = 2, SNIP_DELETE = 3, CLEAR = 4,
                STROKE_ERASE = 5;
        int type, page;
        Stroke stroke;
        Snip snip;
        PageData cleared;
        float ox, oy, ow, oh, orr, nx, ny, nw, nh, nr;
        int[] erasedIdx;
        Stroke[] erased;
    }

    interface StateListener { void onPadStateChanged(); }

    private final NoteStore store;
    private final List<Book> books;
    private int book;
    private int tool = TOOL_PEN;
    private boolean penOnly;
    private int opacity;
    private StateListener listener;

    // scroll state
    private float scrollY;
    private final int gapPx;
    private final OverScroller scroller;
    private VelocityTracker velocity;

    // layers for the page under the pen, plus scratch trio and composites
    private int activePage;
    private Bitmap base, hi, ink;
    private Canvas baseC, hiC, inkC;
    private Bitmap sBase, sHi, sInk;
    private Canvas sBaseC, sHiC, sInkC;
    private final LruCache<Integer, Bitmap> composites = new LruCache<>(3);

    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rubber = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiClear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tpl = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seam = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seamText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleLine = new Paint(Paint.ANTI_ALIAS_FLAG);

    // in-progress stroke
    private Stroke cur;
    private int curPage = -1;
    private float lastX, lastY;
    private boolean wetActive;
    private WetInk wet;

    // stroke eraser: graze on pass, erase on lift
    private boolean strokeErasing;
    private float eraseRadiusPx;
    private final java.util.HashSet<Stroke> grazed = new java.util.HashSet<>();

    // selection
    private Snip sel;
    private int selPage = -1;

    // gesture state
    private static final int M_NONE = 0, M_DRAW = 1, M_SCROLL = 2,
            M_MOVE = 3, M_RESIZE = 4, M_ROTATE = 5;
    private int mode = M_NONE;
    private float grabX, grabY, grabScroll;
    private float grabSnipX, grabSnipY, grabSnipW, grabSnipH, grabSnipR;
    private float editStartX, editStartY, editStartW, editStartH, editStartR;

    private final ArrayDeque<Op> undo = new ArrayDeque<>();
    private final ArrayDeque<Op> redo = new ArrayDeque<>();
    private final Handler saver = new Handler(Looper.getMainLooper());
    private final Runnable saveNow = this::flushSave;
    private final float baseWidthPx, hiWidthPx, hiEdgePx, density;
    private long clearArmedAt;

    public GlassPadView(Context c) {
        super(c);
        store = new NoteStore(c);
        NoteStore.Loaded loaded = store.load();
        books = loaded.books;
        book = Math.max(0, Math.min(loaded.cur, books.size() - 1));
        SharedPreferences p = Prefs.get(c);
        penOnly = p.getBoolean(Prefs.K_PEN_ONLY, false);
        opacity = Math.max(0, Math.min(p.getInt(Prefs.K_OPACITY, Prefs.DEFAULT_OPACITY), 100));
        activePage = Math.max(0, Math.min(p.getInt(Prefs.K_LAST_PAGE, 0), cb().pages.size() - 1));

        density = getResources().getDisplayMetrics().density;
        baseWidthPx = 3.5f * density;
        hiWidthPx = 16f * density;
        hiEdgePx = 1.5f * density;
        gapPx = Math.round(14 * density);
        scroller = new OverScroller(c);

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
        seam.setColor(Color.BLACK);
        seam.setStrokeWidth(2 * density);
        seamText.setColor(0xFF555555);
        seamText.setTextSize(10 * density);
        seamText.setTextAlign(Paint.Align.RIGHT);
        handleFill.setColor(Color.WHITE);
        handleLine.setColor(Color.BLACK);
        handleLine.setStyle(Paint.Style.STROKE);
        handleLine.setStrokeWidth(2 * density);
    }

    void setStateListener(StateListener l) { listener = l; }
    void setWetInk(WetInk w) { wet = w; }
    void setPenOnly(boolean v) { penOnly = v; }

    private Book cb() { return books.get(book); }
    private PageData pg(int i) { return cb().pages.get(i); }

    private void notifyState() { if (listener != null) listener.onPadStateChanged(); }

    private void markChanged() {
        cb().lastModified = System.currentTimeMillis();
        saver.removeCallbacks(saveNow);
        saver.postDelayed(saveNow, 800);
    }

    // -------------------------------------------------------------- geometry

    private int pageH() { return Math.max(1, getHeight()); }
    private float pageTop(int i) { return i * (float) (pageH() + gapPx); }
    private float docHeight() { return cb().pages.size() * (float) (pageH() + gapPx) - gapPx; }
    private float maxScroll() { return Math.max(0, docHeight() - pageH()); }

    /** Which page owns this document-space y, or -1 in a seam. */
    private int pageAtDoc(float docY) {
        int i = (int) Math.floor(docY / (pageH() + gapPx));
        if (i < 0 || i >= cb().pages.size()) return -1;
        return docY - pageTop(i) < pageH() ? i : -1;
    }

    /** The page filling most of the screen — what page ops act on. */
    int dominantPage() {
        int i = pageAtDoc(scrollY + pageH() / 2f);
        if (i >= 0) return i;
        return Math.max(0, Math.min((int) ((scrollY + pageH() / 2f) / (pageH() + gapPx)),
                cb().pages.size() - 1));
    }

    private void clampScroll() { scrollY = Math.max(0, Math.min(scrollY, maxScroll())); }

    void scrollToPage(int i) {
        i = Math.max(0, Math.min(i, cb().pages.size() - 1));
        scroller.abortAnimation();
        scrollY = Math.min(pageTop(i), maxScroll());
        invalidate();
        notifyState();
    }

    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollY = scroller.getCurrY();
            clampScroll();
            postInvalidateOnAnimation();
            notifyState();
        }
    }

    // ------------------------------------------------------------- rendering

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        base = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        hi = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ink = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        sBase = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        sHi = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        sInk = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        baseC = new Canvas(base); hiC = new Canvas(hi); inkC = new Canvas(ink);
        sBaseC = new Canvas(sBase); sHiC = new Canvas(sHi); sInkC = new Canvas(sInk);
        composites.evictAll();
        Prefs.get(getContext()).edit()
                .putInt(Prefs.K_CANVAS_W, w).putInt(Prefs.K_CANVAS_H, h).apply();
        scrollY = Math.min(pageTop(activePage), maxScroll());
        rebuildActive();
    }

    private void rebuildActive() {
        if (ink == null) return;
        renderPage(activePage, baseC, hiC, inkC, base, hi, ink, sel != null && selPage == activePage ? sel : null);
        invalidate();
        notifyState();
    }

    private void renderPage(int page, Canvas bC, Canvas hC, Canvas iC,
                            Bitmap bB, Bitmap hB, Bitmap iB, Snip skip) {
        bB.eraseColor(Color.TRANSPARENT);
        hB.eraseColor(Color.TRANSPARENT);
        iB.eraseColor(Color.TRANSPARENT);
        for (Snip s : pg(page).snips)
            if (s != skip) drawSnip(bC, s, 0);
        for (Stroke s : pg(page).strokes) renderStroke(s, hC, iC);
    }

    /** Merge one page's layers into a cached composite for scroll display. */
    private Bitmap composite(int i) {
        Bitmap cached = composites.get(i);
        if (cached != null) return cached;
        Bitmap out;
        if (i == activePage) {
            out = Bitmap.createBitmap(getWidth(), pageH(), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawBitmap(base, 0, 0, null);
            cv.drawBitmap(hi, 0, 0, null);
            cv.drawBitmap(ink, 0, 0, null);
        } else {
            renderPage(i, sBaseC, sHiC, sInkC, sBase, sHi, sInk, null);
            out = Bitmap.createBitmap(getWidth(), pageH(), Bitmap.Config.ARGB_8888);
            Canvas cv = new Canvas(out);
            cv.drawBitmap(sBase, 0, 0, null);
            cv.drawBitmap(sHi, 0, 0, null);
            cv.drawBitmap(sInk, 0, 0, null);
        }
        composites.put(i, out);
        return out;
    }

    private void pageChanged(int i) {
        composites.remove(i);
        if (i == activePage) rebuildActive();
        else invalidate();
        markChanged();
        notifyState();
    }

    private void structuralChange() {
        composites.evictAll();
        activePage = Math.max(0, Math.min(activePage, cb().pages.size() - 1));
        undo.clear();
        redo.clear();
        rebuildActive();
        markChanged();
    }

    private void drawSnip(Canvas cv, Snip s, float topOffset) {
        if (s.bmp == null) s.bmp = BitmapFactory.decodeFile(
                NoteStore.snipFile(getContext(), s.file).getPath());
        if (s.bmp == null) return;
        int w = getWidth(), h = pageH();
        RectF r = new RectF(s.x * w, topOffset + s.y * h,
                (s.x + s.w) * w, topOffset + (s.y + s.h) * h);
        cv.save();
        if (s.r != 0) cv.rotate(s.r, r.centerX(), r.centerY());
        cv.drawBitmap(s.bmp, null, r, null);
        cv.restore();
    }

    private void renderStroke(Stroke s, Canvas hC, Canvas iC) {
        int w = getWidth(), h = pageH();
        boolean gz = grazed.contains(s); // half-faded: the eraser has it, lift to finish
        switch (s.kind) {
            case KIND_INK: {
                pen.setAlpha(gz ? 70 : 255);
                float bw = s.base * w;
                if (s.n == 1) {
                    pen.setStrokeWidth(bw * (0.5f + s.pts[2]));
                    iC.drawPoint(s.pts[0] * w, s.pts[1] * h, pen);
                    return;
                }
                for (int i = 1; i < s.n; i++) {
                    pen.setStrokeWidth(bw * (0.5f + s.pts[i * 3 + 2]));
                    iC.drawLine(s.pts[(i - 1) * 3] * w, s.pts[(i - 1) * 3 + 1] * h,
                            s.pts[i * 3] * w, s.pts[i * 3 + 1] * h, pen);
                }
                return;
            }
            case KIND_ERASE: {
                float bw = s.base * w * 4f;
                for (int i = 0; i < s.n; i++) {
                    rubber.setStrokeWidth(bw * (0.5f + s.pts[i * 3 + 2]));
                    float x = s.pts[i * 3] * w, y = s.pts[i * 3 + 1] * h;
                    if (i == 0) { iC.drawPoint(x, y, rubber); hC.drawPoint(x, y, rubber); continue; }
                    float px = s.pts[(i - 1) * 3] * w, py = s.pts[(i - 1) * 3 + 1] * h;
                    iC.drawLine(px, py, x, y, rubber);
                    hC.drawLine(px, py, x, y, rubber);
                }
                return;
            }
            case KIND_HIGHLIGHT: {
                hiEdge.setAlpha(gz ? 60 : 255);
                hiFill.setAlpha(gz ? 30 : Color.alpha(HI_FILL));
                Path path = strokePath(s, w, h);
                float bw = s.base * w;
                hiEdge.setStrokeWidth(bw + hiEdgePx * 2);
                hC.drawPath(path, hiEdge);
                hiClear.setStrokeWidth(bw);
                hC.drawPath(path, hiClear);
                hiFill.setStrokeWidth(bw);
                hC.drawPath(path, hiFill);
            }
        }
    }

    private static Path strokePath(Stroke s, int w, int h) {
        Path p = new Path();
        p.moveTo(s.pts[0] * w, s.pts[1] * h);
        if (s.n == 1) p.lineTo(s.pts[0] * w + 0.1f, s.pts[1] * h);
        for (int i = 1; i < s.n; i++) p.lineTo(s.pts[i * 3] * w, s.pts[i * 3 + 1] * h);
        return p;
    }

    /** Live segment for the fallback (non-wet) path, into the active page layers. */
    private void renderSegment(float sx, float sy, float pr) {
        int w = getWidth();
        float top = pageTop(curPage) - scrollY;
        float x = sx, y = sy - top;
        float lx = lastX, ly = lastY - top;
        switch (cur.kind) {
            case KIND_INK:
                pen.setAlpha(255);
                pen.setStrokeWidth(cur.base * w * (0.5f + pr));
                if (cur.n == 0) inkC.drawPoint(x, y, pen);
                else inkC.drawLine(lx, ly, x, y, pen);
                break;
            case KIND_ERASE:
                rubber.setStrokeWidth(cur.base * w * 4f * (0.5f + pr));
                if (cur.n == 0) { inkC.drawPoint(x, y, rubber); hiC.drawPoint(x, y, rubber); break; }
                inkC.drawLine(lx, ly, x, y, rubber);
                hiC.drawLine(lx, ly, x, y, rubber);
                break;
            case KIND_HIGHLIGHT:
                hiFill.setStrokeWidth(cur.base * w);
                if (cur.n == 0) hiC.drawPoint(x, y, hiFill);
                else hiC.drawLine(lx, ly, x, y, hiFill);
                break;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.argb(Math.round(opacity / 100f * 255f), 255, 255, 255));
        if (ink == null) return;
        int w = getWidth(), h = pageH(), n = cb().pages.size();
        int first = Math.max(0, (int) (scrollY / (h + gapPx)));
        int last = Math.min(n - 1, (int) ((scrollY + h) / (h + gapPx)));
        for (int i = first; i <= last; i++) {
            float top = pageTop(i) - scrollY;
            canvas.save();
            canvas.clipRect(0, top, w, top + h);
            canvas.translate(0, top);
            drawTemplate(canvas, cb().template, w, h, tpl, false);
            if (i == activePage) {
                canvas.drawBitmap(base, 0, 0, null);
                canvas.drawBitmap(hi, 0, 0, null);
                canvas.drawBitmap(ink, 0, 0, null);
            } else {
                canvas.drawBitmap(composite(i), 0, 0, null);
            }
            canvas.restore();
            // the seam under this page
            if (i < n - 1) {
                float seamY = top + h + gapPx / 2f;
                if (seamY > 0 && seamY < getHeight()) {
                    canvas.drawLine(0, seamY, w, seamY, seam);
                    canvas.drawText((i + 2) + " / " + n, w - 8 * density,
                            seamY - 4 * density, seamText);
                }
            }
        }
        drawSelection(canvas);
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

    // ------------------------------------------------------------- selection

    private RectF selScreenRect() {
        int w = getWidth(), h = pageH();
        float top = pageTop(selPage) - scrollY;
        return new RectF(sel.x * w, top + sel.y * h,
                (sel.x + sel.w) * w, top + (sel.y + sel.h) * h);
    }

    private void drawSelection(Canvas canvas) {
        if (sel == null) return;
        RectF r = selScreenRect();
        canvas.save();
        if (sel.r != 0) canvas.rotate(sel.r, r.centerX(), r.centerY());
        drawSnipInto(canvas, r);
        canvas.drawRect(r, handleLine);
        float hs = 7 * density;
        float[][] corners = {{r.left, r.top}, {r.right, r.top}, {r.left, r.bottom}, {r.right, r.bottom}};
        for (float[] cnr : corners) {
            canvas.drawRect(cnr[0] - hs, cnr[1] - hs, cnr[0] + hs, cnr[1] + hs, handleFill);
            canvas.drawRect(cnr[0] - hs, cnr[1] - hs, cnr[0] + hs, cnr[1] + hs, handleLine);
        }
        // rotate handle above the top edge, delete above the top-right corner
        float rx = r.centerX(), ry = r.top - 34 * density;
        canvas.drawLine(rx, r.top, rx, ry, handleLine);
        canvas.drawCircle(rx, ry, 12 * density, handleFill);
        canvas.drawCircle(rx, ry, 12 * density, handleLine);
        canvas.drawText("⟳", rx - 5 * density, ry + 5 * density, handleLine);
        float dx = r.right + 20 * density, dy = r.top - 20 * density;
        canvas.drawCircle(dx, dy, 12 * density, handleFill);
        canvas.drawCircle(dx, dy, 12 * density, handleLine);
        canvas.drawLine(dx - 5 * density, dy - 5 * density, dx + 5 * density, dy + 5 * density, handleLine);
        canvas.drawLine(dx - 5 * density, dy + 5 * density, dx + 5 * density, dy - 5 * density, handleLine);
        canvas.restore();
    }

    private void drawSnipInto(Canvas cv, RectF r) {
        if (sel.bmp == null) sel.bmp = BitmapFactory.decodeFile(
                NoteStore.snipFile(getContext(), sel.file).getPath());
        if (sel.bmp != null) cv.drawBitmap(sel.bmp, null, r, null);
    }

    /** Point mapped into the selection's un-rotated frame. */
    private float[] unrotate(float x, float y) {
        RectF r = selScreenRect();
        Matrix m = new Matrix();
        m.setRotate(-sel.r, r.centerX(), r.centerY());
        float[] pt = {x, y};
        m.mapPoints(pt);
        return pt;
    }

    private static final int H_NONE = 0, H_INSIDE = 1, H_CORNER = 2, H_ROTATE = 3, H_DELETE = 4;

    private int hitSelection(float x, float y) {
        if (sel == null) return H_NONE;
        float[] p = unrotate(x, y);
        RectF r = selScreenRect();
        float grab = 22 * density;
        float rx = r.centerX(), ry = r.top - 34 * density;
        if (Math.hypot(p[0] - rx, p[1] - ry) < grab) return H_ROTATE;
        float dx = r.right + 20 * density, dy = r.top - 20 * density;
        if (Math.hypot(p[0] - dx, p[1] - dy) < grab) return H_DELETE;
        float[][] corners = {{r.left, r.top}, {r.right, r.top}, {r.left, r.bottom}, {r.right, r.bottom}};
        for (float[] cnr : corners)
            if (Math.hypot(p[0] - cnr[0], p[1] - cnr[1]) < grab) return H_CORNER;
        if (r.contains(p[0], p[1])) return H_INSIDE;
        return H_NONE;
    }

    private void select(Snip s, int page) {
        deselect();
        sel = s;
        selPage = page;
        if (page == activePage) rebuildActive(); // lift it out of the baked layer
        else { activatePage(page); }
        invalidate();
    }

    void deselect() {
        if (sel == null) return;
        Snip s = sel;
        int p = selPage;
        sel = null;
        selPage = -1;
        composites.remove(p);
        if (p == activePage) rebuildActive();
        invalidate();
    }

    private void deleteSelected() {
        if (sel == null) return;
        Op o = new Op();
        o.type = Op.SNIP_DELETE;
        o.page = selPage;
        o.snip = sel;
        pushOp(o);
        pg(selPage).snips.remove(sel);
        int p = selPage;
        sel = null;
        selPage = -1;
        pageChanged(p);
        Toast.makeText(getContext(), "Snip removed", Toast.LENGTH_SHORT).show();
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int toolType = e.getToolType(0);
        boolean stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS
                || toolType == MotionEvent.TOOL_TYPE_ERASER;
        boolean finger = !stylus;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                scroller.abortAnimation();
                // a tap on the selection's handles wins over everything
                int hit = hitSelection(e.getX(), e.getY());
                if (hit == H_DELETE) { deleteSelected(); mode = M_NONE; return true; }
                if (hit != H_NONE) { beginSnipEdit(hit, e.getX(), e.getY()); return true; }
                if (sel != null) deselect();
                if (tool == TOOL_PICK) {
                    if (pickSnipAt(e.getX(), e.getY())) { beginSnipEdit(H_INSIDE, e.getX(), e.getY()); return true; }
                    beginScroll(e);
                    return true;
                }
                if (finger && penOnly) { beginScroll(e); return true; }
                beginDraw(e);
                return true;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
                // second finger: this gesture is a scroll, not a stroke
                if (mode == M_DRAW && cur != null && finger) {
                    abortStroke();
                    beginScroll(e);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                switch (mode) {
                    case M_DRAW: moveDraw(e); break;
                    case M_SCROLL: moveScroll(e); break;
                    case M_MOVE: case M_RESIZE: case M_ROTATE: moveSnipEdit(e); break;
                }
                return true;
            case MotionEvent.ACTION_UP:
                switch (mode) {
                    case M_DRAW: finishDraw(); break;
                    case M_SCROLL: finishScroll(e); break;
                    case M_MOVE: case M_RESIZE: case M_ROTATE: finishSnipEdit(); break;
                }
                mode = M_NONE;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (mode == M_DRAW) finishDraw();
                if (velocity != null) { velocity.recycle(); velocity = null; }
                mode = M_NONE;
                return true;
        }
        return true;
    }

    // --- drawing

    private void beginDraw(MotionEvent e) {
        int page = pageAtDoc(e.getY() + scrollY);
        if (page < 0) { beginScroll(e); return; }
        activatePage(page);
        curPage = page;
        int toolType = e.getToolType(0);
        boolean eraserEnd = toolType == MotionEvent.TOOL_TYPE_ERASER;
        if (penOnly && toolType != MotionEvent.TOOL_TYPE_STYLUS && !eraserEnd) { mode = M_NONE; return; }
        int kind = eraserEnd || tool == TOOL_ERASE ? KIND_ERASE
                : tool == TOOL_HIGHLIGHT ? KIND_HIGHLIGHT : KIND_INK;
        requestUnbufferedDispatch(e);
        SharedPreferences p = Prefs.get(getContext());
        if (kind == KIND_ERASE && !p.getBoolean(Prefs.K_ERASE_PIXEL, false)) {
            // stroke eraser: graze whole strokes on the way, erase them on lift
            strokeErasing = true;
            grazed.clear();
            eraseRadiusPx = baseWidthPx * 2.5f * sizeMul(p.getInt(Prefs.K_ERASE_SIZE, 2));
            grazeAt(e.getX(), e.getY());
            mode = M_DRAW;
            return;
        }
        cur = new Stroke();
        cur.kind = kind;
        float mul = kind == KIND_HIGHLIGHT ? sizeMul(p.getInt(Prefs.K_HI_SIZE, 2))
                : kind == KIND_ERASE ? sizeMul(p.getInt(Prefs.K_ERASE_SIZE, 2))
                : sizeMul(p.getInt(Prefs.K_PEN_SIZE, 2));
        float widthPx = (kind == KIND_HIGHLIGHT ? hiWidthPx : baseWidthPx) * mul;
        cur.base = widthPx / Math.max(1, getWidth());
        wetActive = kind != KIND_ERASE && wet != null && wet.isReady();
        if (wetActive) wet.begin(widthPx, kind);
        addPoint(e.getX(), e.getY(), e.getPressure(), e.getEventTime());
        if (wetActive) wet.present();
        mode = M_DRAW;
    }

    private void moveDraw(MotionEvent e) {
        if (strokeErasing) {
            for (int i = 0; i < e.getHistorySize(); i++)
                grazeAt(e.getHistoricalX(i), e.getHistoricalY(i));
            grazeAt(e.getX(), e.getY());
            return;
        }
        if (cur == null) return;
        for (int i = 0; i < e.getHistorySize(); i++)
            addPoint(e.getHistoricalX(i), e.getHistoricalY(i),
                    e.getHistoricalPressure(i), e.getHistoricalEventTime(i));
        addPoint(e.getX(), e.getY(), e.getPressure(), e.getEventTime());
        if (wetActive) wet.present();
    }

    private void grazeAt(float sx, float sy) {
        float top = pageTop(curPage) - scrollY;
        int w = Math.max(1, getWidth()), h = pageH();
        float nx = sx / w;
        float ny = Math.max(0, Math.min(sy - top, h - 1)) / h;
        float nr = eraseRadiusPx / w; // radius in normalized-x units; pages are wider than tall-ish
        boolean newGraze = false;
        for (Stroke s : pg(curPage).strokes) {
            if (s.kind == KIND_ERASE || grazed.contains(s)) continue;
            if (s.nearAny(nx, ny, nr)) {
                grazed.add(s);
                newGraze = true;
            }
        }
        if (newGraze) rebuildActive();
    }

    private void addPoint(float x, float y, float rawPressure, long tMs) {
        float pr = Math.max(0.15f, Math.min(rawPressure, 1.3f));
        // clamp into the stroke's page — ink stops at the paper's edge
        float top = pageTop(curPage) - scrollY;
        float cy = Math.max(top, Math.min(y, top + pageH() - 1));
        if (wetActive) {
            wet.addPoint(x, cy, pr, tMs);
        } else {
            renderSegment(x, cy, pr);
            invalidate();
        }
        int w = Math.max(1, getWidth());
        cur.add(x / w, (cy - top) / pageH(), pr);
        lastX = x;
        lastY = cy;
    }

    private void finishDraw() {
        if (strokeErasing) {
            strokeErasing = false;
            if (!grazed.isEmpty()) {
                List<Stroke> list = pg(curPage).strokes;
                int m = grazed.size();
                int[] idxs = new int[m];
                Stroke[] removed = new Stroke[m];
                int k = 0;
                for (int i = 0; i < list.size(); i++)
                    if (grazed.contains(list.get(i))) { idxs[k] = i; removed[k] = list.get(i); k++; }
                list.removeAll(grazed);
                grazed.clear();
                Op o = new Op();
                o.type = Op.STROKE_ERASE;
                o.page = curPage;
                o.erasedIdx = idxs;
                o.erased = removed;
                pushOp(o);
                pageChanged(curPage);
            }
            return;
        }
        if (cur == null) return;
        if (wetActive) {
            renderStroke(cur, hiC, inkC);
            invalidate();
            wet.end();
            wetActive = false;
        } else if (cur.kind == KIND_HIGHLIGHT) {
            rebuildActive(); // replace the live preview with the ringed render
        }
        pg(curPage).strokes.add(cur);
        Op o = new Op();
        o.type = Op.STROKE;
        o.page = curPage;
        o.stroke = cur;
        pushOp(o);
        cur = null;
        composites.remove(curPage);
        markChanged();
        notifyState();
    }

    private void abortStroke() {
        if (wetActive) { wet.end(); wetActive = false; }
        cur = null;
        strokeErasing = false;
        grazed.clear();
        if (curPage >= 0 && curPage == activePage) rebuildActive();
    }

    /** Make this page the one with live layers under the pen. */
    private void activatePage(int page) {
        if (page == activePage) return;
        // snapshot the outgoing page so scrolling stays seamless
        composites.remove(activePage);
        composite(activePage);
        activePage = page;
        rebuildActive();
    }

    // --- scrolling

    private void beginScroll(MotionEvent e) {
        mode = M_SCROLL;
        grabY = e.getY();
        grabScroll = scrollY;
        if (velocity != null) velocity.recycle();
        velocity = VelocityTracker.obtain();
        velocity.addMovement(e);
    }

    private void moveScroll(MotionEvent e) {
        if (velocity != null) velocity.addMovement(e);
        scrollY = grabScroll + (grabY - e.getY());
        clampScroll();
        invalidate();
        notifyState();
    }

    private void finishScroll(MotionEvent e) {
        if (velocity == null) return;
        velocity.addMovement(e);
        velocity.computeCurrentVelocity(1000);
        float vy = velocity.getYVelocity();
        velocity.recycle();
        velocity = null;
        if (Math.abs(vy) > 400) {
            scroller.fling(0, (int) scrollY, 0, (int) -vy, 0, 0, 0, (int) maxScroll());
            postInvalidateOnAnimation();
        }
        Prefs.get(getContext()).edit().putInt(Prefs.K_LAST_PAGE, dominantPage()).apply();
    }

    // --- snip manipulation

    private boolean pickSnipAt(float x, float y) {
        int page = pageAtDoc(y + scrollY);
        if (page < 0) return false;
        int w = getWidth(), h = pageH();
        float top = pageTop(page) - scrollY;
        List<Snip> snips = pg(page).snips;
        for (int i = snips.size() - 1; i >= 0; i--) {
            Snip s = snips.get(i);
            RectF r = new RectF(s.x * w, top + s.y * h, (s.x + s.w) * w, top + (s.y + s.h) * h);
            Matrix m = new Matrix();
            m.setRotate(-s.r, r.centerX(), r.centerY());
            float[] p = {x, y};
            m.mapPoints(p);
            if (r.contains(p[0], p[1])) {
                select(s, page);
                return true;
            }
        }
        return false;
    }

    private void beginSnipEdit(int hit, float x, float y) {
        mode = hit == H_ROTATE ? M_ROTATE : hit == H_CORNER ? M_RESIZE : M_MOVE;
        grabX = x;
        grabY = y;
        grabSnipX = editStartX = sel.x;
        grabSnipY = editStartY = sel.y;
        grabSnipW = editStartW = sel.w;
        grabSnipH = editStartH = sel.h;
        grabSnipR = editStartR = sel.r;
    }

    private void moveSnipEdit(MotionEvent e) {
        if (sel == null) return;
        int w = getWidth(), h = pageH();
        RectF r0 = new RectF(grabSnipX * w, pageTop(selPage) - scrollY + grabSnipY * h,
                (grabSnipX + grabSnipW) * w, pageTop(selPage) - scrollY + (grabSnipY + grabSnipH) * h);
        float cx = r0.centerX(), cy = r0.centerY();
        switch (mode) {
            case M_MOVE: {
                sel.x = grabSnipX + (e.getX() - grabX) / w;
                sel.y = grabSnipY + (e.getY() - grabY) / h;
                break;
            }
            case M_RESIZE: {
                float d0 = (float) Math.hypot(grabX - cx, grabY - cy);
                float d1 = (float) Math.hypot(e.getX() - cx, e.getY() - cy);
                float scale = d0 > 1 ? d1 / d0 : 1f;
                float minW = 32 * density / w;
                float nw = Math.max(minW, Math.min(grabSnipW * scale, 3f));
                float s = nw / grabSnipW;
                sel.w = grabSnipW * s;
                sel.h = grabSnipH * s;
                sel.x = grabSnipX + (grabSnipW - sel.w) / 2f;
                sel.y = grabSnipY + (grabSnipH - sel.h) / 2f;
                break;
            }
            case M_ROTATE: {
                float a0 = (float) Math.toDegrees(Math.atan2(grabY - cy, grabX - cx));
                float a1 = (float) Math.toDegrees(Math.atan2(e.getY() - cy, e.getX() - cx));
                float r = grabSnipR + (a1 - a0);
                while (r > 180) r -= 360;
                while (r < -180) r += 360;
                // the snap Glassnote never had: near-straight means straight
                float nearest = Math.round(r / 90f) * 90f;
                if (Math.abs(r - nearest) <= SNAP_DEG) r = nearest;
                if (r == 180 || r == -180) r = 180;
                sel.r = r == -0f ? 0f : r;
                break;
            }
        }
        invalidate();
    }

    private void finishSnipEdit() {
        if (sel == null) return;
        if (sel.x != editStartX || sel.y != editStartY || sel.w != editStartW
                || sel.h != editStartH || sel.r != editStartR) {
            Op o = new Op();
            o.type = Op.SNIP_EDIT;
            o.page = selPage;
            o.snip = sel;
            o.ox = editStartX; o.oy = editStartY; o.ow = editStartW; o.oh = editStartH; o.orr = editStartR;
            o.nx = sel.x; o.ny = sel.y; o.nw = sel.w; o.nh = sel.h; o.nr = sel.r;
            pushOp(o);
            composites.remove(selPage);
            markChanged();
            notifyState();
        }
    }

    // ----------------------------------------------------------------- tools

    void setTool(int t) {
        tool = t;
        if (t != TOOL_PICK) deselect();
        notifyState();
    }

    int getTool() { return tool; }

    private void pushOp(Op o) {
        undo.push(o);
        while (undo.size() > 100) undo.pollLast();
        redo.clear();
    }

    boolean undo() { return applyOp(undo, redo, true); }
    boolean redo() { return applyOp(redo, undo, false); }

    private boolean applyOp(ArrayDeque<Op> from, ArrayDeque<Op> to, boolean isUndo) {
        Op o = from.poll();
        if (o == null) return false;
        deselect();
        switch (o.type) {
            case Op.STROKE:
                if (isUndo) pg(o.page).strokes.remove(o.stroke);
                else pg(o.page).strokes.add(o.stroke);
                break;
            case Op.SNIP_ADD:
                if (isUndo) pg(o.page).snips.remove(o.snip);
                else pg(o.page).snips.add(o.snip);
                break;
            case Op.SNIP_DELETE:
                if (isUndo) pg(o.page).snips.add(o.snip);
                else pg(o.page).snips.remove(o.snip);
                break;
            case Op.SNIP_EDIT:
                if (isUndo) { o.snip.x = o.ox; o.snip.y = o.oy; o.snip.w = o.ow; o.snip.h = o.oh; o.snip.r = o.orr; }
                else { o.snip.x = o.nx; o.snip.y = o.ny; o.snip.w = o.nw; o.snip.h = o.nh; o.snip.r = o.nr; }
                break;
            case Op.CLEAR:
                if (isUndo) cb().pages.set(o.page, copyOf(o.cleared));
                else { pg(o.page).strokes.clear(); pg(o.page).snips.clear(); }
                break;
            case Op.STROKE_ERASE:
                if (isUndo) {
                    List<Stroke> list = pg(o.page).strokes;
                    for (int i = 0; i < o.erased.length; i++)
                        list.add(Math.min(o.erasedIdx[i], list.size()), o.erased[i]);
                } else {
                    for (Stroke s : o.erased) pg(o.page).strokes.remove(s);
                }
                break;
        }
        to.push(o);
        scrollToPage(o.page);
        composites.remove(o.page);
        if (o.page == activePage) rebuildActive();
        markChanged();
        notifyState();
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
        int page = dominantPage();
        if (pg(page).isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - clearArmedAt > 3000) {
            clearArmedAt = now;
            Toast.makeText(getContext(), "Tap CLEAR again to wipe this page", Toast.LENGTH_SHORT).show();
            return;
        }
        clearArmedAt = 0;
        deselect();
        Op o = new Op();
        o.type = Op.CLEAR;
        o.page = page;
        o.cleared = copyOf(pg(page));
        pushOp(o);
        pg(page).strokes.clear();
        pg(page).snips.clear();
        pageChanged(page);
    }

    /** Long-press CLEAR: tear the page out entirely. */
    void deletePage() {
        deselect();
        int page = dominantPage();
        if (cb().pages.size() <= 1) {
            pg(0).strokes.clear();
            pg(0).snips.clear();
        } else {
            cb().pages.remove(page);
        }
        Toast.makeText(getContext(), "Page torn out", Toast.LENGTH_SHORT).show();
        structuralChange();
        clampScroll();
        invalidate();
    }

    // ----------------------------------------------------------------- snips

    /** Pastes a screenshot clipping onto the page under it, already selected. */
    void addSnip(String file, RectF screenRect) {
        int page = pageAtDoc(screenRect.centerY() + scrollY);
        if (page < 0) page = dominantPage();
        int w = getWidth(), h = pageH();
        float top = pageTop(page) - scrollY;
        Snip s = new Snip();
        s.file = file;
        s.w = screenRect.width() / w;
        s.h = screenRect.height() / h;
        s.x = screenRect.left / w;
        s.y = Math.max(0, Math.min((screenRect.top - top) / h, 1f - s.h));
        s.r = 0;
        pg(page).snips.add(s);
        Op o = new Op();
        o.type = Op.SNIP_ADD;
        o.page = page;
        o.snip = s;
        pushOp(o);
        composites.remove(page);
        markChanged();
        select(s, page); // land selected: resize it a bit, hit the button, back to reading
        notifyState();
    }

    // ----------------------------------------------------------------- pages

    void prevPage() { flushSave(); scrollToPage(dominantPage() - 1); }
    void nextPage() { flushSave(); scrollToPage(dominantPage() + 1); }

    void newPage() {
        flushSave();
        deselect();
        int at = dominantPage() + 1;
        cb().pages.add(at, new PageData());
        structuralChange();
        scrollToPage(at);
    }

    String pageLabel() { return (dominantPage() + 1) + "/" + cb().pages.size(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    // ------------------------------------------------------------- notebooks

    List<Book> getBooks() { return books; }
    int curBook() { return book; }
    String bookName() { return cb().name; }

    void switchBook(int i) {
        if (i == book || i < 0 || i >= books.size()) return;
        flushSave();
        deselect();
        book = i;
        activePage = 0;
        scrollY = 0;
        structuralChange();
    }

    void newBook(String name, int template) {
        flushSave();
        deselect();
        Book b = new Book();
        b.name = name;
        b.template = Math.max(0, Math.min(template, TPL_NAMES.length - 1));
        b.createdTime = b.lastModified = System.currentTimeMillis();
        b.pages.add(new PageData());
        books.add(b);
        book = books.size() - 1;
        activePage = 0;
        scrollY = 0;
        structuralChange();
    }

    void deleteBook(int i) {
        if (i < 0 || i >= books.size()) return;
        deselect();
        Book b = books.remove(i);
        for (PageData p : b.pages)
            for (Snip s : p.snips)
                NoteStore.snipFile(getContext(), s.file).delete();
        if (books.isEmpty()) {
            Book fresh = new Book();
            fresh.name = "Notes";
            fresh.template = TPL_BLANK;
            fresh.createdTime = fresh.lastModified = System.currentTimeMillis();
            fresh.pages.add(new PageData());
            books.add(fresh);
        }
        if (book >= books.size()) book = books.size() - 1;
        else if (i < book) book--;
        activePage = 0;
        scrollY = 0;
        structuralChange();
    }

    // ----------------------------------------------------------------- glass

    void setOpacity(int pct) {
        opacity = Math.max(0, Math.min(pct, 100));
        Prefs.get(getContext()).edit().putInt(Prefs.K_OPACITY, opacity).apply();
        invalidate();
    }

    int getOpacity() { return opacity; }

    // ------------------------------------------------------------ persistence

    /** Structural copy safe to hand to a background thread. */
    List<Book> snapshotBooks() {
        List<Book> out = new ArrayList<>(books.size());
        for (Book b : books) {
            Book nb = new Book();
            nb.name = b.name;
            nb.template = b.template;
            nb.createdTime = b.createdTime;
            nb.lastModified = b.lastModified;
            for (PageData p : b.pages) nb.pages.add(copyOf(p));
            out.add(nb);
        }
        return out;
    }

    void flushSave() {
        saver.removeCallbacks(saveNow);
        store.saveAsync(snapshotBooks(), book);
        Prefs.get(getContext()).edit().putInt(Prefs.K_LAST_PAGE, dominantPage()).apply();
    }
}
