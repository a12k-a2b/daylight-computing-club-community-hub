package com.daylightcomputer.shade;

import android.content.Context;
import android.content.SharedPreferences;

/** Tiny prefs wrapper shared by the service and the control room. */
public final class Prefs {
    public static final String STRIP_OFF = "off";
    public static final String STRIP_BELOW = "below";
    public static final String STRIP_OVER = "over";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("shade", Context.MODE_PRIVATE);
    }

    /** Where the swipe-down catch zone lives: off / below the status bar /
     *  over the status bar (full mode only). */
    public static String stripMode(Context c) { return p(c).getString("strip_mode", STRIP_OFF); }
    public static void setStripMode(Context c, String m) { p(c).edit().putString("strip_mode", m).apply(); }

    /** Block the stock shade from expanding (full mode only). */
    public static boolean takeOver(Context c) { return p(c).getBoolean("take_over", false); }
    public static void setTakeOver(Context c, boolean b) { p(c).edit().putBoolean("take_over", b).apply(); }

    /** In-shade Wi-Fi / Bluetooth pickers (v2, young). Off = classic
     *  hand-off to the system surfaces. */
    public static boolean pickers(Context c) { return p(c).getBoolean("pickers", true); }
    public static void setPickers(Context c, boolean b) { p(c).edit().putBoolean("pickers", b).apply(); }

    /** Where "paper-like" ends on the brightness dial (fraction of
     *  travel). Past this point the backlight starts to out-glow the
     *  room and the legend says "screen-like". Tunable in shade setup —
     *  Anjan's field guess is ~30%; adjust by eye, not by math. */
    public static float paperZoneEnd(Context c) { return p(c).getFloat("paper_zone_end", 0.30f); }
    public static void setPaperZoneEnd(Context c, float v) {
        p(c).edit().putFloat("paper_zone_end", Math.max(0.05f, Math.min(0.95f, v))).apply();
    }

    /** Where "candlelit" ends (fraction of travel) — its own knob, not a
     *  proportion of the paper zone. If set past the paper boundary the
     *  legend just clamps to it. Default: 15%. */
    public static float candleZoneEnd(Context c) { return p(c).getFloat("candle_zone_end", 0.15f); }
    public static void setCandleZoneEnd(Context c, float v) {
        p(c).edit().putFloat("candle_zone_end", Math.max(0.02f, Math.min(0.95f, v))).apply();
    }

    // ---- crash bookkeeping (the crash-loop breaker) ----
    private static final long CRASH_WINDOW_MS = 15 * 60_000L;
    private static final int CRASH_LIMIT = 3;

    /** Called from the uncaught-exception handler — commit(), not apply():
     *  the process is about to die and this write must land. */
    public static void noteCrash(Context c) {
        SharedPreferences sp = p(c);
        long now = System.currentTimeMillis();
        long first = sp.getLong("crash_first", 0);
        int n = sp.getInt("crash_count", 0);
        if (now - first > CRASH_WINDOW_MS) { first = now; n = 0; }
        sp.edit().putLong("crash_first", first).putInt("crash_count", n + 1).commit();
    }

    public static boolean shouldTripSafeMode(Context c) {
        SharedPreferences sp = p(c);
        return sp.getInt("crash_count", 0) >= CRASH_LIMIT
                && System.currentTimeMillis() - sp.getLong("crash_first", 0) <= CRASH_WINDOW_MS;
    }

    /** Step aside: gesture surfaces off, stock shade back, tell the user. */
    public static void tripSafeMode(Context c) {
        p(c).edit().putString("strip_mode", STRIP_OFF)
                .putBoolean("take_over", false)
                .putBoolean("safe_tripped", true)
                .putInt("crash_count", 0)
                .apply();
    }

    public static boolean safeTripped(Context c) { return p(c).getBoolean("safe_tripped", false); }
    public static void clearSafeTripped(Context c) { p(c).edit().putBoolean("safe_tripped", false).apply(); }
}
