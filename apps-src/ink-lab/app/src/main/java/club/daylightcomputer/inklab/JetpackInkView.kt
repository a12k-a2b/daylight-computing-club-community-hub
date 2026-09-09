package club.daylightcomputer.inklab

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import androidx.input.motionprediction.MotionEventPredictor

/**
 * The JETPACK pane: androidx.ink's InProgressStrokesView (front-buffered
 * low-latency wet rendering where the GPU driver supports it) fed with
 * unbuffered input and androidx motion prediction — the real "latest &
 * greatest" stack, wired per its documented usage.
 */
class JetpackInkView(c: Context) : FrameLayout(c), InProgressStrokesFinishedListener {

    var stats: StatsMeter? = null

    private val finished = FinishedView(c)
    private val ipsv = InProgressStrokesView(c)
    private val predictor = MotionEventPredictor.newInstance(this)
    private val brush = Brush.createWithColorIntArgb(
        family = StockBrushes.pressurePen(),
        colorIntArgb = Color.BLACK,
        size = 3.5f * resources.displayMetrics.density,
        epsilon = 0.1f
    )
    private var strokeId: InProgressStrokeId? = null
    private var pointerId = -1

    private val latSamples = java.util.ArrayDeque<Float>()
    private var latCount = 0
    private var latLabel = "nib→?"
    private var rawDumps = 0
    private var lastDrawStamp = 0L
    private var drawCount = 0
    private var eventCount = 0
    private val splitA = java.util.ArrayDeque<Float>()
    private val splitB = java.util.ArrayDeque<Float>()

    init {
        setBackgroundColor(Color.WHITE)
        addView(finished, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(ipsv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ipsv.addFinishedStrokesListener(this)
        installLatencyProbe()
    }

    /** The BENCHMARK instrument — byte-for-byte the same measurement Note
     *  Overlay takes (JetInk.kt setLatencyDataCallback + logLatency), so the
     *  two apps' numbers are comparable. Same fallback ladder, same
     *  percentiles, same 200-sample cadence; only the log tag differs. */
    @OptIn(androidx.ink.authoring.ExperimentalLatencyDataApi::class)
    private fun installLatencyProbe() {
        ipsv.setLatencyDataCallback { d ->
            if (rawDumps < 3) { rawDumps++; android.util.Log.i("InkLab", "LatencyData raw: $d") }
            val end = when {
                d.estimatedPixelPresentationTime > 0 -> {
                    latLabel = "nib→pixel"; d.estimatedPixelPresentationTime
                }
                d.strokesViewFinishesDrawCalls > 0 -> {
                    latLabel = "nib→draw"; d.strokesViewFinishesDrawCalls
                }
                d.canvasFrontBufferStrokesRenderHelperData.finishesDrawCalls > 0 -> {
                    latLabel = "nib→fbdraw"
                    d.canvasFrontBufferStrokesRenderHelperData.finishesDrawCalls
                }
                else -> return@setLatencyDataCallback
            }
            if (!d.isOsDetectsEventSet) return@setLatencyDataCallback
            val fb = d.canvasFrontBufferStrokesRenderHelperData.finishesDrawCalls
            if (fb > 0) { if (fb != lastDrawStamp) { lastDrawStamp = fb; drawCount++ }; eventCount++ }
            val gets = d.strokesViewGetsAction
            if (gets > 0) {
                splitA.addLast((gets - d.osDetectsEvent) / 1e6f)
                splitB.addLast((end - gets) / 1e6f)
                while (splitA.size > 600) splitA.pollFirst()
                while (splitB.size > 600) splitB.pollFirst()
            }
            val ms = (end - d.osDetectsEvent) / 1e6f
            if (d.strokeAction == androidx.ink.authoring.latency.LatencyData.StrokeAction.START) {
                android.util.Log.i("InkLab", String.format("stroke-start %s: %.1fms", latLabel, ms))
            }
            if (ms > 0 && ms < 500) {
                latSamples.addLast(ms)
                while (latSamples.size > 600) latSamples.pollFirst()
                if (++latCount % 200 == 0) logLatency()
            }
        }
    }

    private fun logLatency() {
        val a = latSamples.toFloatArray().sortedArray()
        if (a.size < 20) return
        android.util.Log.i("InkLab", String.format(
            "%s latency: p50 %.1fms  p90 %.1fms  max %.1fms  (n=%d)",
            latLabel, a[a.size / 2], a[(a.size * 9) / 10], a.last(), a.size))
        val x = splitA.toFloatArray().sortedArray()
        val y = splitB.toFloatArray().sortedArray()
        if (x.size >= 20 && y.size >= 20) android.util.Log.i("InkLab", String.format(
            "SPLIT dispatch+app p50 %.1fms p90 %.1fms | androidx-render p50 %.1fms p90 %.1fms",
            x[x.size / 2], x[(x.size * 9) / 10], y[y.size / 2], y[(y.size * 9) / 10]) +
            String.format(" | draws=%d events=%d (%.1f events/draw)", drawCount, eventCount,
                if (drawCount > 0) eventCount.toFloat() / drawCount else 0f))
    }

    fun clearInk() {
        finished.clear()
    }

    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
        finished.add(strokes.values)
        ipsv.removeFinishedStrokes(strokes.keys)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        predictor.record(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestUnbufferedDispatch(e)
                pointerId = e.getPointerId(0)
                strokeId = ipsv.startStroke(e, pointerId, brush)
                stats?.sample(e.eventTime)
            }
            MotionEvent.ACTION_MOVE -> {
                val id = strokeId ?: return true
                ipsv.addToStroke(e, pointerId, id, predictor.predict())
                stats?.sample(e.eventTime)
            }
            MotionEvent.ACTION_UP -> {
                val id = strokeId ?: return true
                ipsv.finishStroke(e, pointerId, id)
                strokeId = null
            }
            MotionEvent.ACTION_CANCEL -> {
                val id = strokeId ?: return true
                ipsv.cancelStroke(id, e)
                strokeId = null
            }
        }
        return true
    }

    /** Dry layer for finished androidx.ink strokes. */
    private class FinishedView(c: Context) : View(c) {
        private val renderer = CanvasStrokeRenderer.create()
        private val strokes = ArrayList<Stroke>()
        private val transform = Matrix()

        fun add(s: Collection<Stroke>) {
            strokes.addAll(s)
            invalidate()
        }

        fun clear() {
            strokes.clear()
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            for (s in strokes) renderer.draw(canvas, s, transform)
        }
    }
}
