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

    init {
        setBackgroundColor(Color.WHITE)
        addView(finished, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(ipsv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        ipsv.addFinishedStrokesListener(this)
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
