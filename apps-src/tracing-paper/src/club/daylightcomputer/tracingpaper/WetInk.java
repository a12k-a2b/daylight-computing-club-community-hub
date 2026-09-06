package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The wet-ink layer. The stroke being written right now is drawn straight to
 * a hardware surface the moment each (unbuffered) pen event arrives — no
 * waiting for the next UI-thread frame — with a ~20ms predicted tail carried
 * forward on the pen's velocity. This is the same wet/dry split the platform's
 * low-latency ink stack (androidx.ink / front-buffered rendering) uses, done
 * with plain platform APIs so the club's no-Gradle build stays plain Java.
 * On pen-up the stroke is committed to the dry layers underneath and this
 * surface is wiped; if the surface isn't available the pad falls back to the
 * ordinary path, so nothing breaks on an odd SolOS build.
 */
class WetInk extends SurfaceView implements SurfaceHolder.Callback {

    private static final float PREDICT_MS = 20f;
    private static final float PREDICT_CAP_PX = 56f; // don't overshoot on a flick

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hiClear = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float hiEdgePx;
    private volatile boolean ready;
    private int clipBottom = Integer.MAX_VALUE;

    // the current stroke, in screen space
    private float[] xs = new float[256], ys = new float[256], ps = new float[256];
    private int n;
    private float baseWidth;
    private int kind;
    private long lastT, prevT;

    WetInk(Context c) {
        super(c);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
        setFocusable(false);
        setClickable(false);
        for (Paint p : new Paint[]{paint, hiFill, hiEdge, hiClear}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        paint.setColor(Color.BLACK);
        hiFill.setColor(GlassPadView.HI_FILL);
        hiEdge.setColor(GlassPadView.HI_EDGE);
        hiClear.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        hiEdgePx = 1.5f * getResources().getDisplayMetrics().density;
    }

    boolean isReady() { return ready; }

    /** Keeps wet ink from painting over the toolbar while the pen crosses it. */
    void setClipBottom(int px) { clipBottom = px; }

    void begin(float baseWidthPx, int strokeKind, int inkColor) {
        n = 0;
        baseWidth = baseWidthPx;
        kind = strokeKind;
        paint.setColor(inkColor);
    }

    void addPoint(float x, float y, float p, long tMs) {
        if (n == xs.length) { xs = grow(xs); ys = grow(ys); ps = grow(ps); }
        xs[n] = x; ys[n] = y; ps[n] = p; n++;
        prevT = lastT;
        lastT = tMs;
    }

    private static float[] grow(float[] a) {
        float[] b = new float[a.length * 2];
        System.arraycopy(a, 0, b, 0, a.length);
        return b;
    }

    /** Redraws the wet stroke plus the predicted tail. Call once per event batch. */
    void present() {
        if (!ready || n == 0) return;
        Canvas cv = lock();
        if (cv == null) return;
        try {
            cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
            cv.clipRect(0, 0, cv.getWidth(), Math.min(clipBottom, cv.getHeight()));
            if (kind == GlassPadView.KIND_HIGHLIGHT) presentHighlight(cv);
            else presentInk(cv);
        } finally {
            unlock(cv);
        }
    }

    private void presentInk(Canvas cv) {
        if (n == 1) {
            paint.setStrokeWidth(baseWidth * (0.5f + ps[0]));
            cv.drawPoint(xs[0], ys[0], paint);
            return;
        }
        for (int i = 1; i < n; i++) {
            paint.setStrokeWidth(baseWidth * (0.5f + ps[i]));
            cv.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint);
        }
        float[] tail = predict();
        if (tail != null) {
            paint.setStrokeWidth(baseWidth * (0.5f + ps[n - 1]));
            cv.drawLine(xs[n - 1], ys[n - 1], tail[0], tail[1], paint);
        }
    }

    private void presentHighlight(Canvas cv) {
        Path path = new Path();
        path.moveTo(xs[0], ys[0]);
        if (n == 1) path.lineTo(xs[0] + 0.1f, ys[0]);
        for (int i = 1; i < n; i++) path.lineTo(xs[i], ys[i]);
        float[] tail = predict();
        if (tail != null) path.lineTo(tail[0], tail[1]);
        hiEdge.setStrokeWidth(baseWidth + hiEdgePx * 2);
        cv.drawPath(path, hiEdge);
        hiClear.setStrokeWidth(baseWidth);
        cv.drawPath(path, hiClear);
        hiFill.setStrokeWidth(baseWidth);
        cv.drawPath(path, hiFill);
    }

    /** ~20ms of the pen's velocity carried forward, capped so flicks don't overshoot. */
    private float[] predict() {
        if (n < 2 || lastT <= prevT) return null;
        float dt = lastT - prevT;
        float dx = (xs[n - 1] - xs[n - 2]) / dt * PREDICT_MS;
        float dy = (ys[n - 1] - ys[n - 2]) / dt * PREDICT_MS;
        float len = (float) Math.hypot(dx, dy);
        if (len > PREDICT_CAP_PX) {
            dx *= PREDICT_CAP_PX / len;
            dy *= PREDICT_CAP_PX / len;
        }
        return new float[]{xs[n - 1] + dx, ys[n - 1] + dy};
    }

    /** Pen-up: the stroke has been dried into the layers below — wipe the glass. */
    void end() {
        n = 0;
        if (!ready) return;
        Canvas cv = lock();
        if (cv == null) return;
        try {
            cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
        } finally {
            unlock(cv);
        }
    }

    private Canvas lock() {
        try {
            return getHolder().lockHardwareCanvas();
        } catch (Exception e) {
            try { return getHolder().lockCanvas(); } catch (Exception e2) { return null; }
        }
    }

    private void unlock(Canvas cv) {
        try { getHolder().unlockCanvasAndPost(cv); } catch (Exception ignored) {}
    }

    @Override public void surfaceCreated(SurfaceHolder h) { ready = true; }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {}
    @Override public void surfaceDestroyed(SurfaceHolder h) { ready = false; }
}
