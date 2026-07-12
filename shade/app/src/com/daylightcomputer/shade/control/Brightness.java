package com.daylightcomputer.shade.control;

import android.content.Context;
import android.provider.Settings;

/** Backlight level via Settings.System.SCREEN_BRIGHTNESS (1–255).
 *  Needs only the user-grantable WRITE_SETTINGS appop — works today.
 *
 *  The slider runs on the same perceptual (logarithmic) dial as dc1-keys:
 *  position = ln(raw)/ln(255). A linear dial wastes the whole nighttime
 *  range in the first sliver of travel; on this dial each inch of slider
 *  is a similar perceived step (Weber–Fechner). Two DC-1 facts baked in
 *  (verified in the dc1-backlight project): raw 1 means backlight OFF on
 *  this reflective panel (far left = sun muted, a feature), and raw 2 is
 *  a dead rung (1/255 PWM emits no visible light) — skipped. */
public final class Brightness {
    private static final int MAX = 255;

    /** The dial's ceiling. "100%" on the shade is deliberately NOT the
     *  panel's true maximum: slamming the slider right is the #1 way
     *  people end up with a too-bright Daylight — sore eyes, drained
     *  battery — on a reflective screen that rarely needs backlight at
     *  all. The dial tops out at ~60% duty; the true 255 stays reachable
     *  in Android's own Settings for whoever goes there on purpose. */
    private static final int CAP = 153;

    private Brightness() {}

    public static boolean available(Context c) { return Caps.writeSettings(c); }

    /** Slider position 0..1 on the perceptual dial (0 = off, 1 = CAP).
     *  A raw value above CAP (set elsewhere) pins the thumb right. */
    public static float get(Context c) {
        int raw = Settings.System.getInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, MAX / 2);
        if (raw <= 1) return 0f;
        return (float) Math.min(1, Math.log(raw) / Math.log(CAP));
    }

    public static void set(Context c, float v) {
        if (!available(c)) return;
        int raw = (int) Math.round(Math.pow(CAP, Math.max(0f, Math.min(1f, v))));
        if (raw == 2) raw = 3; // dead rung
        raw = Math.max(1, Math.min(CAP, raw));
        // manual brightness while the user is driving the slider
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, raw);
    }

    /** The little legend above the slider: name the light, guide the
     *  hand. "candlelit" is the dimmest half of the calm range (night
     *  reading — paper in a dark room isn't "paper-like", it's lit by a
     *  candle); "paper-like" is the home zone (backlight just improving
     *  contrast); "screen-like" starts where the panel begins to look
     *  emissive. That last boundary is a matter of eyes, not math, so
     *  it's tunable in shade setup (Prefs.paperZoneEnd, default 30%) —
     *  candlelit scales with it as its lower half, one knob for all. */
    public static String zoneLabel(Context c, float p) {
        if (p <= 0.001f) return "pure reflective";
        float paperEnd = com.daylightcomputer.shade.Prefs.paperZoneEnd(c);
        if (p <= paperEnd / 2f) return "candlelit";
        if (p <= paperEnd) return "paper-like";
        return "screen-like";
    }
}
