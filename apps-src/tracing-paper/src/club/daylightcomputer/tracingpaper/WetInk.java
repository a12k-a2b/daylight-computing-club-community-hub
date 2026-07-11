package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * The wet-ink layer. The stroke being written right now is drawn straight to
 * a hardware surface the moment each (unbuffered) pen event arrives — no
 * waiting for the next UI-thread frame — with a ~12ms predicted tail carried
 * forward on the pen's velocity. This is the same wet/dry split the platform's
 * low-latency ink stack (androidx.ink / front-buffered rendering) uses, done
 * with plain platform APIs so the club's no-Gradle build stays plain Java.
 * On pen-up the stroke is committed to the dry bitmap underneath and this
 * layer is wiped; if the surface isn't available the pad falls back to the
 * ordinary path, so nothing breaks on an odd SolOS build.
 */
class WetInk extends SurfaceView implements SurfaceHolder.Callback {

    private static final float PREDICT_MS = 12f;
    private static final float PREDICT_CAP_PX = 48f; // don't overshoot on a flick

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private volatile boolean ready;
    private int clipBottom = Integer.MAX_VALUE;

    // the current stroke, in screen space
    private float[] xs = new float[256], ys = new float[256], ps = new float[256];
    private int n;
    private float baseWidth;
    private long lastT, prevT;

    WetInk(Context c) {
        super(c);
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        getHolder().addCallback(this);
        setFocusable(false);
        setClickable(false);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.BLACK);
    }

    boolean isReady() { return ready; }

    /** Keeps wet ink from painting over the toolbar while the pen crosses it. */
    void setClipBottom(int px) { clipBottom = px; }

    void begin(float baseWidthPx) {
        n = 0;
        baseWidth = baseWidthPx;
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
            if (n == 1) {
                paint.setStrokeWidth(baseWidth * (0.5f + ps[0]));
                cv.drawPoint(xs[0], ys[0], paint);
            } else {
                for (int i = 1; i < n; i++) {
                    paint.setStrokeWidth(baseWidth * (0.5f + ps[i]));
                    cv.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint);
                }
                if (lastT > prevT) {
                    float dt = lastT - prevT;
                    float dx = (xs[n - 1] - xs[n - 2]) / dt * PREDICT_MS;
                    float dy = (ys[n - 1] - ys[n - 2]) / dt * PREDICT_MS;
                    float len = (float) Math.hypot(dx, dy);
                    if (len > PREDICT_CAP_PX) {
                        dx *= PREDICT_CAP_PX / len;
                        dy *= PREDICT_CAP_PX / len;
                    }
                    paint.setStrokeWidth(baseWidth * (0.5f + ps[n - 1]));
                    cv.drawLine(xs[n - 1], ys[n - 1], xs[n - 1] + dx, ys[n - 1] + dy, paint);
                }
            }
        } finally {
            unlock(cv);
        }
    }

    /** Pen-up: the stroke has been dried into the bitmap below — wipe the glass. */
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
