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
    private Brightness() {}

    public static boolean available(Context c) { return Caps.writeSettings(c); }

    /** Slider position 0..1 on the perceptual dial. */
    public static float get(Context c) {
        int raw = Settings.System.getInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, MAX / 2);
        if (raw <= 1) return 0f;
        return (float) Math.min(1, Math.log(raw) / Math.log(MAX));
    }

    public static void set(Context c, float v) {
        if (!available(c)) return;
        int raw = (int) Math.round(Math.pow(MAX, Math.max(0f, Math.min(1f, v))));
        if (raw == 2) raw = 3; // dead rung
        raw = Math.max(1, Math.min(MAX, raw));
        // manual brightness while the user is driving the slider
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, raw);
    }
}
