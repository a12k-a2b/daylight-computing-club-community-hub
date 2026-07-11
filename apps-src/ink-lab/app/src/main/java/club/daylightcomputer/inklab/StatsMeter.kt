package club.daylightcomputer.inklab

import android.os.SystemClock
import android.widget.TextView

/**
 * Rolling input→submit lag (ms) and event rate. This measures how fast each
 * pipeline hands a stroke segment to its renderer after the touch happened —
 * it can NOT see compositor + scanout (front-buffer rendering bypasses the
 * instrumented path by design). Film 240fps slo-mo for ground truth.
 */
class StatsMeter(private val label: TextView) {
    private val lags = ArrayDeque<Long>()
    private var events = 0
    private var windowStart = SystemClock.uptimeMillis()
    private var lastShown = 0L
    private var rate = 0

    fun sample(eventTimeMs: Long) {
        val now = SystemClock.uptimeMillis()
        lags.addLast(now - eventTimeMs)
        while (lags.size > 200) lags.removeFirst()
        events++
        if (now - windowStart >= 1000) {
            rate = (events * 1000L / (now - windowStart)).toInt()
            events = 0
            windowStart = now
        }
        if (now - lastShown >= 250 && lags.isNotEmpty()) {
            lastShown = now
            val avg = lags.sum().toDouble() / lags.size
            val max = lags.max()
            label.post {
                label.text = "submit lag avg %.1f ms · max %d ms · %d ev/s".format(avg, max, rate)
            }
        }
    }
}
