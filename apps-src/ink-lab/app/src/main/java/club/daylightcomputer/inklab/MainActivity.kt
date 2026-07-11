package club.daylightcomputer.inklab

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.HardwareBuffer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Ink Lab — throwaway A/B rig. Same pen, two pipelines: the club's
 * Tracing Paper engine on one side, androidx.ink on the other. Scribble
 * fast circles on both; for ground truth film the pen tip at 240fps and
 * count frames between the nib and the ink edge on each side.
 */
class MainActivity : Activity() {

    private lateinit var row: LinearLayout
    private lateinit var clubPane: View
    private lateinit var jetpackPane: View
    private lateinit var club: ClubInkView
    private lateinit var jetpack: JetpackInkView

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }

        val fb = frontBufferSupported()
        root.addView(TextView(this).apply {
            text = "INK LAB (throwaway) — front-buffer usage supported here: " +
                (if (fb) "YES" else "NO") +
                ". Lag numbers are input→submit only; the compositor and scanout are " +
                "invisible to software. Ground truth: 240fps slo-mo of nib vs ink."
            setTextColor(Color.BLACK)
            textSize = 13f
            setPadding(dp(12), dp(8), dp(12), dp(4))
        })

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), 0, dp(12), dp(4))
        }
        controls.addView(button("CLEAR BOTH") { club.clearInk(); jetpack.clearInk() })
        controls.addView(button("SWAP SIDES") { swap() })
        root.addView(controls)

        club = ClubInkView(this)
        jetpack = JetpackInkView(this)
        clubPane = labeled("CLUB — Tracing Paper engine", club) { club.stats = it }
        jetpackPane = labeled("JETPACK — androidx.ink 1.0.0", jetpack) { jetpack.stats = it }

        row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(clubPane, paneLp())
        row.addView(jetpackPane, paneLp())
        root.addView(row, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)
    }

    private fun paneLp() = LinearLayout.LayoutParams(
        0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
        val m = (4 * resources.displayMetrics.density).toInt()
        setMargins(m, m, m, m)
    }

    private fun swap() {
        row.removeAllViews()
        val tmp = clubPane
        clubPane = jetpackPane
        jetpackPane = tmp
        row.addView(clubPane, paneLp())
        row.addView(jetpackPane, paneLp())
    }

    private fun labeled(title: String, ink: View, bind: (StatsMeter) -> Unit): View {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
            }
        }
        col.addView(TextView(this).apply {
            text = title
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(4), 0, dp(4))
        })
        val statsView = TextView(this).apply {
            text = "draw to measure"
            setTextColor(Color.BLACK)
            textSize = 12f
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        bind(StatsMeter(statsView))
        col.addView(statsView)
        col.addView(ink, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        return col
    }

    private fun button(label: String, tap: () -> Unit): TextView {
        val d = resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()
        return TextView(this).apply {
            text = label
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.BLACK)
            textSize = 15f
            gravity = Gravity.CENTER
            minHeight = dp(44)
            minWidth = dp(120)
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
            }
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { tap() }
            (layoutParams as? LinearLayout.LayoutParams)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(8)
            layoutParams = lp
        }
    }

    /** Can this GPU driver even do front-buffered rendering? (androidx.graphics falls back if not.) */
    private fun frontBufferSupported(): Boolean = try {
        HardwareBuffer.isSupported(
            1, 1, HardwareBuffer.RGBA_8888, 1,
            HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                    or HardwareBuffer.USAGE_COMPOSER_OVERLAY
                    or HardwareBuffer.USAGE_FRONT_BUFFER)
    } catch (t: Throwable) {
        false
    }
}
