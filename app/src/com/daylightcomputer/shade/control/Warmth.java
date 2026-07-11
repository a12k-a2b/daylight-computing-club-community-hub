package com.daylightcomputer.shade.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/** The amber↔white slider. The DC-1's warm backlight is Daylight's own
 *  hardware, so the one fact we need from the platform team is: which
 *  setting does the stock slider write? (Find it with:
 *    adb shell settings list system > before.txt
 *    …move the stock warmth slider…
 *    adb shell settings list system > after.txt ; diff before.txt after.txt
 *  then set that key + max in Daylight Shade's control room, or bake it
 *  into DEFAULT_KEYS below.)
 *
 *  Backend chain, first available wins:
 *   1. SYSTEM-TABLE KEY — a configurable Settings.System key (candidate
 *      names probed automatically). Needs only WRITE_SETTINGS.
 *   2. NIGHT DISPLAY — AOSP night light as a stand-in warmth control.
 *      Needs WRITE_SECURE_SETTINGS (adb- or priv-grantable).
 *   3. none — slider shows a "needs Sol:OS hookup" hint. */
public final class Warmth {
    private static final String TAG = "ShadeWarmth";
    private static final String PREFS = "shade";
    private static final String PREF_KEY = "warmth_key";
    private static final String PREF_MAX = "warmth_max";

    /** Likely names for Daylight's own warmth setting; verified on-device
     *  at first run. Extend freely — probing a missing key is free. */
    private static final String[] DEFAULT_KEYS = {
            "screen_warmth", "daylight_warmth", "warmth", "amber_brightness",
            "backlight_warmth", "screen_amber", "reading_light_warmth",
    };

    // AOSP night display range (config_nightDisplayColorTemperatureMin/Max defaults)
    private static final int NIGHT_TEMP_WARM = 2596;
    private static final int NIGHT_TEMP_COOL = 4082;

    private Warmth() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The Settings.System key in use, or null when falling back / absent. */
    public static String systemKey(Context c) {
        String configured = prefs(c).getString(PREF_KEY, null);
        if (configured != null && !configured.isEmpty()) {
            return Settings.System.getString(c.getContentResolver(), configured) != null
                    ? configured : null;
        }
        for (String k : DEFAULT_KEYS) {
            if (Settings.System.getString(c.getContentResolver(), k) != null) return k;
        }
        return null;
    }

    public static void configure(Context c, String key, int max) {
        prefs(c).edit().putString(PREF_KEY, key).putInt(PREF_MAX, max).apply();
    }

    public static String backendName(Context c) {
        String k = systemKey(c);
        if (k != null && Caps.writeSettings(c)) return "system key \"" + k + "\"";
        if (Caps.secureSettings(c)) return "night display (stand-in)";
        return "";
    }

    public static boolean available(Context c) {
        return !backendName(c).isEmpty();
    }

    /** 0 = paper white, 1 = full amber. */
    public static float get(Context c) {
        String k = systemKey(c);
        if (k != null) {
            int max = Math.max(1, prefs(c).getInt(PREF_MAX, 255));
            return Settings.System.getInt(c.getContentResolver(), k, 0) / (float) max;
        }
        if (Caps.secureSettings(c)) {
            if (Settings.Secure.getInt(c.getContentResolver(), "night_display_activated", 0) == 0)
                return 0f;
            int t = Settings.Secure.getInt(c.getContentResolver(),
                    "night_display_color_temperature", NIGHT_TEMP_COOL);
            return (NIGHT_TEMP_COOL - t) / (float) (NIGHT_TEMP_COOL - NIGHT_TEMP_WARM);
        }
        return 0f;
    }

    public static void set(Context c, float v) {
        v = Math.max(0f, Math.min(1f, v));
        String k = systemKey(c);
        if (k != null && Caps.writeSettings(c)) {
            int max = Math.max(1, prefs(c).getInt(PREF_MAX, 255));
            Settings.System.putInt(c.getContentResolver(), k, Math.round(v * max));
            return;
        }
        if (Caps.secureSettings(c)) {
            try {
                Settings.Secure.putInt(c.getContentResolver(),
                        "night_display_activated", v > 0.02f ? 1 : 0);
                Settings.Secure.putInt(c.getContentResolver(),
                        "night_display_color_temperature",
                        Math.round(NIGHT_TEMP_COOL - v * (NIGHT_TEMP_COOL - NIGHT_TEMP_WARM)));
            } catch (Throwable t) {
                Log.w(TAG, "night display write failed: " + t);
            }
        }
    }
}
