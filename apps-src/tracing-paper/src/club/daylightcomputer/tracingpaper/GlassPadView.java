package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The sheet of glass: a transparent ink canvas. Strokes are stored normalized
 * (0..1 by canvas width/height, with pen pressure per point) so they survive
 * rotation and export cleanly to PDF at any size.
 */
public class GlassPadView extends View {

    static final int TOOL_PEN = 0, TOOL_ERASE = 1;
    /** clear glass / frosted glass / opaque paper */
    static final int[] FROSTS = {0x2EFFFFFF, 0x80FFFFFF, 0xF4FFFFFF};
    static final String[] FROST_NAMES = {"CLEAR", "FROST", "PAPER"};

    static class Stroke {
        boolean eraser;
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

    /** One undoable thing: either a stroke was added, or a page was cleared. */
    private static class Op {
        int page;
        Stroke added;
        List<Stroke> cleared;
    }

    interface StateListener { void onPadStateChanged(); }

    private final NoteStore store;
    final List<List<Stroke>> pages;
    private int page;
    private int tool = TOOL_PEN;
    private boolean penOnly;
    private int frost;
    private StateListener listener;

    private Bitmap ink;
    private Canvas inkCanvas;
    private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rubber = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Stroke cur;
    private float lastX, lastY;
    private final ArrayDeque<Op> undo = new ArrayDeque<>();
    private final ArrayDeque<Op> redo = new ArrayDeque<>();
    private final Handler saver = new Handler(Looper.getMainLooper());
    private final Runnable saveNow = this::flushSave;
    private final float baseWidthPx;
    private long clearArmedAt;

    public GlassPadView(Context c) {
        super(c);
        store = new NoteStore(c);
        pages = store.load();
        SharedPreferences p = Prefs.get(c);
        penOnly = p.getBoolean(Prefs.K_PEN_ONLY, false);
        frost = clampFrost(p.getInt(Prefs.K_FROST, 1));
        page = Math.max(0, Math.min(p.getInt(Prefs.K_LAST_PAGE, 0), pages.size() - 1));

        pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        pen.setColor(Color.BLACK);
        rubber.set(pen);
        rubber.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        baseWidthPx = 3.5f * getResources().getDisplayMetrics().density;
    }

    void setStateListener(StateListener l) { listener = l; }

    private static int clampFrost(int f) { return Math.max(0, Math.min(f, FROSTS.length - 1)); }

    private void notifyState() { if (listener != null) listener.onPadStateChanged(); }

    // ------------------------------------------------------------- rendering

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        ink = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        inkCanvas = new Canvas(ink);
        Prefs.get(getContext()).edit()
                .putInt(Prefs.K_CANVAS_W, w).putInt(Prefs.K_CANVAS_H, h).apply();
        rebuild();
    }

    private void rebuild() {
        if (ink == null) return;
        ink.eraseColor(Color.TRANSPARENT);
        for (Stroke s : pages.get(page)) render(s);
        invalidate();
        notifyState();
    }

    private void render(Stroke s) {
        int w = getWidth(), h = getHeight();
        Paint p = s.eraser ? rubber : pen;
        float bw = s.base * w * (s.eraser ? 4f : 1f);
        if (s.n == 1) {
            p.setStrokeWidth(bw * (0.5f + s.pts[2]));
            inkCanvas.drawPoint(s.pts[0] * w, s.pts[1] * h, p);
            return;
        }
        for (int i = 1; i < s.n; i++) {
            p.setStrokeWidth(bw * (0.5f + s.pts[i * 3 + 2]));
            inkCanvas.drawLine(s.pts[(i - 1) * 3] * w, s.pts[(i - 1) * 3 + 1] * h,
                    s.pts[i * 3] * w, s.pts[i * 3 + 1] * h, p);
        }
    }

    /** Draws just the last segment of the in-progress stroke straight onto the ink. */
    private void renderSegment(float x, float y, float pr) {
        int w = getWidth(), h = getHeight();
        Paint p = cur.eraser ? rubber : pen;
        float bw = cur.base * w * (cur.eraser ? 4f : 1f);
        p.setStrokeWidth(bw * (0.5f + pr));
        if (cur.n == 0) inkCanvas.drawPoint(x, y, p);
        else inkCanvas.drawLine(lastX, lastY, x, y, p);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(FROSTS[frost]);
        if (ink != null) canvas.drawBitmap(ink, 0, 0, null);
    }

    // ----------------------------------------------------------------- touch

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int toolType = e.getToolType(0);
        boolean stylus = toolType == MotionEvent.TOOL_TYPE_STYLUS;
        boolean eraserEnd = toolType == MotionEvent.TOOL_TYPE_ERASER;
        if (penOnly && !stylus && !eraserEnd) return true; // glass eats the finger
        boolean erase = eraserEnd || tool == TOOL_ERASE;

        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cur = new Stroke();
                cur.eraser = erase;
                cur.base = baseWidthPx / Math.max(1, getWidth());
                addPoint(e.getX(), e.getY(), e.getPressure());
                break;
            case MotionEvent.ACTION_MOVE:
                if (cur == null) return true;
                for (int i = 0; i < e.getHistorySize(); i++)
                    addPoint(e.getHistoricalX(i), e.getHistoricalY(i), e.getHistoricalPressure(i));
                addPoint(e.getX(), e.getY(), e.getPressure());
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (cur == null) return true;
                pages.get(page).add(cur);
                Op op = new Op();
                op.page = page;
                op.added = cur;
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

    private void addPoint(float x, float y, float rawPressure) {
        float pr = Math.max(0.15f, Math.min(rawPressure, 1.3f));
        renderSegment(x, y, pr);
        int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
        cur.add(x / w, y / h, pr);
        lastX = x; lastY = y;
        invalidate();
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
        if (o.added != null) pages.get(o.page).remove(o.added);
        else pages.set(o.page, new ArrayList<>(o.cleared));
        redo.push(o);
        rebuild();
        scheduleSave();
        return true;
    }

    boolean redo() {
        Op o = redo.poll();
        if (o == null) return false;
        switchTo(o.page);
        if (o.added != null) pages.get(o.page).add(o.added);
        else pages.get(o.page).clear();
        undo.push(o);
        rebuild();
        scheduleSave();
        return true;
    }

    /** First tap arms it, second tap within 3s clears — no dialog on glass. */
    void clearPage() {
        if (pages.get(page).isEmpty()) return;
        long now = System.currentTimeMillis();
        if (now - clearArmedAt > 3000) {
            clearArmedAt = now;
            Toast.makeText(getContext(), "Tap CLEAR again to wipe this page", Toast.LENGTH_SHORT).show();
            return;
        }
        clearArmedAt = 0;
        Op o = new Op();
        o.page = page;
        o.cleared = new ArrayList<>(pages.get(page));
        undo.push(o);
        trim(undo);
        redo.clear();
        pages.get(page).clear();
        rebuild();
        scheduleSave();
    }

    /** Long-press CLEAR: tear the page out entirely. */
    void deletePage() {
        if (pages.size() <= 1) {
            pages.get(0).clear();
        } else {
            pages.remove(page);
            page = Math.min(page, pages.size() - 1);
        }
        undo.clear();
        redo.clear();
        Toast.makeText(getContext(), "Page torn out", Toast.LENGTH_SHORT).show();
        rebuild();
        scheduleSave();
    }

    // ----------------------------------------------------------------- pages

    private void switchTo(int p) {
        if (p == page) return;
        page = Math.max(0, Math.min(p, pages.size() - 1));
        rebuild();
    }

    void prevPage() { if (page > 0) { flushSave(); switchTo(page - 1); } }
    void nextPage() { if (page < pages.size() - 1) { flushSave(); switchTo(page + 1); } }

    void newPage() {
        flushSave();
        pages.add(page + 1, new ArrayList<>());
        page++;
        undo.clear();
        redo.clear();
        rebuild();
        scheduleSave();
    }

    String pageLabel() { return (page + 1) + "/" + pages.size(); }
    boolean canUndo() { return !undo.isEmpty(); }
    boolean canRedo() { return !redo.isEmpty(); }

    // ----------------------------------------------------------------- frost

    int cycleFrost() {
        frost = (frost + 1) % FROSTS.length;
        Prefs.get(getContext()).edit().putInt(Prefs.K_FROST, frost).apply();
        invalidate();
        return frost;
    }

    int getFrost() { return frost; }

    // ------------------------------------------------------------ persistence

    private void scheduleSave() {
        saver.removeCallbacks(saveNow);
        saver.postDelayed(saveNow, 800);
    }

    void flushSave() {
        saver.removeCallbacks(saveNow);
        List<List<Stroke>> copy = new ArrayList<>(pages.size());
        for (List<Stroke> pg : pages) copy.add(new ArrayList<>(pg));
        store.saveAsync(copy);
        Prefs.get(getContext()).edit().putInt(Prefs.K_LAST_PAGE, page).apply();
    }
}
