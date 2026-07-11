package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The Daylight design system, on-device edition: pure grayscale, real
 *  borders instead of shadows, serif type, no gradients, generous targets.
 *
 *  The palette follows the system theme: day = ink on paper, night = the
 *  same page inverted (paper type on ink). Call applyTheme(context) before
 *  building any view tree — the panel and the control room both rebuild
 *  from scratch on every open/resume, so a swap is always clean. */
public final class Ui {
    // day palette by default; applyTheme() flips these (not final on purpose —
    // final ints would be inlined at compile time and never flip)
    public static int INK = Color.BLACK;
    public static int PAPER = Color.WHITE;
    public static int MID = 0xFF555555;
    public static int FAINT = 0xFF999999;
    public static int PRESSED = 0xFFDDDDDD;
    public static final int SCRIM = 0x66000000; // over the app behind us, both themes

    private Ui() {}

    public static boolean isNight(Context c) {
        int m = c.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    public static void applyTheme(Context c) {
        boolean n = isNight(c);
        INK = n ? 0xFFFFFFFF : 0xFF000000;
        PAPER = n ? 0xFF000000 : 0xFFFFFFFF;
        MID = n ? 0xFFAAAAAA : 0xFF555555;
        FAINT = n ? 0xFF777777 : 0xFF999999;
        PRESSED = n ? 0xFF333333 : 0xFFDDDDDD;
    }

    public static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }

    /** A flat rectangle with a real border. (GradientDrawable never gets a
     *  gradient here — it is just the cheapest bordered-rect drawable.) */
    public static GradientDrawable box(Context c, int strokeDp, int fill, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(dp(c, strokeDp), stroke);
        return d;
    }

    public static TextView text(Context c, float sizeSp, int color, boolean serif, boolean bold) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        t.setTextColor(color);
        t.setTypeface(serif ? Typeface.SERIF : Typeface.SANS_SERIF,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** A big bordered text button, ≥48dp tall, ink on paper. */
    public static TextView button(Context c, String label, Runnable onTap) {
        final TextView t = text(c, 17, INK, true, false);
        t.setText(label);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight(dp(c, 52));
        t.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8));
        t.setBackground(box(c, 2, PAPER, INK));
        t.setClickable(true);
        t.setOnClickListener(v -> onTap.run());
        t.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    t.setBackground(box(c, 2, PRESSED, INK)); break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    t.setBackground(box(c, 2, PAPER, INK)); break;
            }
            return false;
        });
        return t;
    }

    public static View hr(Context c) {
        View v = new View(c);
        v.setBackgroundColor(INK);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(c, 2)));
        return v;
    }

    public static LinearLayout.LayoutParams lp(Context c, int wDp, int hDp) {
        return new LinearLayout.LayoutParams(
                wDp < 0 ? wDp : dp(c, wDp), hDp < 0 ? hDp : dp(c, hDp));
    }
}
