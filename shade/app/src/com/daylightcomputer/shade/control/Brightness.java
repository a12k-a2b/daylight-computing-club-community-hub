package com.daylightcomputer.shade.control;

import android.content.Context;
import android.provider.Settings;

/** Backlight level via Settings.System.SCREEN_BRIGHTNESS (0–255).
 *  Needs only the user-grantable WRITE_SETTINGS appop — works today.
 *  Note for the Sol:OS port: if the DC-1 exposes a wider or non-linear
 *  backlight range, swap MAX and the mapping here, nowhere else. */
public final class Brightness {
    private static final int MAX = 255;
    private Brightness() {}

    public static boolean available(Context c) { return Caps.writeSettings(c); }

    public static float get(Context c) {
        int v = Settings.System.getInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, MAX / 2);
        return v / (float) MAX;
    }

    public static void set(Context c, float v) {
        if (!available(c)) return;
        // manual brightness while the user is driving the slider
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
        Settings.System.putInt(c.getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS,
                Math.max(1, Math.round(v * MAX)));
    }
}
