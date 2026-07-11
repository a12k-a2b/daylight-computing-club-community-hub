package club.daylightcomputer.inklab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout

/**
 * The CLUB pane: a faithful port of Tracing Paper's ink engine.
 * Unbuffered stylus input; the wet stroke drawn straight to a hardware
 * SurfaceView canvas the moment each event arrives, with a ~20ms
 * velocity-predicted tail; dried into a bitmap on pen-up.
 */
class ClubInkView(c: Context) : FrameLayout(c) {

    var stats: StatsMeter? = null

    private val dry = DryView(c)
    private val wet = WetSurface(c)
    private val baseWidthPx = 3.5f * resources.displayMetrics.density

    // current stroke, screen space (x, y, pressure triplets)
    private val pts = ArrayList<Float>(1024)

    init {
        setBackgroundColor(Color.WHITE)
        addView(dry, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(wet, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun clearInk() {
        dry.clear()
        wet.wipe()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                pts.clear()
                wet.begin(baseWidthPx)
                addPoint(e.x, e.y, e.pressure, e.eventTime)
                wet.present()
                stats?.sample(e.eventTime)
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until e.historySize)
                    addPoint(e.getHistoricalX(i), e.getHistoricalY(i),
                        e.getHistoricalPressure(i), e.getHistoricalEventTime(i))
                addPoint(e.x, e.y, e.pressure, e.eventTime)
                wet.present()
                stats?.sample(e.eventTime)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dry.commit(pts, baseWidthPx)
                pts.clear()
                wet.end()
            }
        }
        return true
    }

    private fun addPoint(x: Float, y: Float, rawPressure: Float, t: Long) {
        val p = rawPressure.coerceIn(0.15f, 1.3f)
        pts.add(x); pts.add(y); pts.add(p)
        wet.addPoint(x, y, p, t)
    }

    /** Dry layer: committed strokes in a bitmap, same rendering as the club app. */
    private class DryView(c: Context) : View(c) {
        private var bmp: Bitmap? = null
        private var cv: Canvas? = null
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }

        override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
            if (w <= 0 || h <= 0) return
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            cv = Canvas(bmp!!)
        }

        fun commit(pts: List<Float>, base: Float) {
            val c = cv ?: return
            val n = pts.size / 3
            if (n == 0) return
            if (n == 1) {
                paint.strokeWidth = base * (0.5f + pts[2])
                c.drawPoint(pts[0], pts[1], paint)
            } else {
                for (i in 1 until n) {
                    paint.strokeWidth = base * (0.5f + pts[i * 3 + 2])
                    c.drawLine(pts[(i - 1) * 3], pts[(i - 1) * 3 + 1],
                        pts[i * 3], pts[i * 3 + 1], paint)
                }
            }
            invalidate()
        }

        fun clear() {
            bmp?.eraseColor(Color.TRANSPARENT)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            bmp?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        }
    }

    /** Wet layer: Tracing Paper's WetInk, ink-only. */
    private class WetSurface(c: Context) : SurfaceView(c), SurfaceHolder.Callback {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = Color.BLACK
        }
        @Volatile private var ready = false
        private var xs = FloatArray(256); private var ys = FloatArray(256); private var ps = FloatArray(256)
        private var n = 0
        private var baseWidth = 0f
        private var lastT = 0L; private var prevT = 0L

        init {
            setZOrderOnTop(true)
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(this)
            isFocusable = false
            isClickable = false
        }

        fun begin(b: Float) { n = 0; baseWidth = b }

        fun addPoint(x: Float, y: Float, p: Float, t: Long) {
            if (n == xs.size) { xs = xs.copyOf(n * 2); ys = ys.copyOf(n * 2); ps = ps.copyOf(n * 2) }
            xs[n] = x; ys[n] = y; ps[n] = p; n++
            prevT = lastT; lastT = t
        }

        fun present() {
            if (!ready || n == 0) return
            val cv = lock() ?: return
            try {
                cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                if (n == 1) {
                    paint.strokeWidth = baseWidth * (0.5f + ps[0])
                    cv.drawPoint(xs[0], ys[0], paint)
                } else {
                    for (i in 1 until n) {
                        paint.strokeWidth = baseWidth * (0.5f + ps[i])
                        cv.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i], paint)
                    }
                    if (lastT > prevT) {
                        val dt = (lastT - prevT).toFloat()
                        var dx = (xs[n - 1] - xs[n - 2]) / dt * PREDICT_MS
                        var dy = (ys[n - 1] - ys[n - 2]) / dt * PREDICT_MS
                        val len = Math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                        if (len > PREDICT_CAP_PX) { dx *= PREDICT_CAP_PX / len; dy *= PREDICT_CAP_PX / len }
                        paint.strokeWidth = baseWidth * (0.5f + ps[n - 1])
                        cv.drawLine(xs[n - 1], ys[n - 1], xs[n - 1] + dx, ys[n - 1] + dy, paint)
                    }
                }
            } finally {
                unlock(cv)
            }
        }

        fun end() { n = 0; wipe() }

        fun wipe() {
            if (!ready) return
            val cv = lock() ?: return
            try { cv.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) } finally { unlock(cv) }
        }

        private fun lock(): Canvas? = try {
            holder.lockHardwareCanvas()
        } catch (e: Exception) {
            try { holder.lockCanvas() } catch (e2: Exception) { null }
        }

        private fun unlock(cv: Canvas) {
            try { holder.unlockCanvasAndPost(cv) } catch (ignored: Exception) {}
        }

        override fun surfaceCreated(h: SurfaceHolder) { ready = true }
        override fun surfaceChanged(h: SurfaceHolder, f: Int, w: Int, hh: Int) {}
        override fun surfaceDestroyed(h: SurfaceHolder) { ready = false }

        companion object {
            const val PREDICT_MS = 20f
            const val PREDICT_CAP_PX = 56f
        }
    }
}
