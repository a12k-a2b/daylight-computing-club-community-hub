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
}
