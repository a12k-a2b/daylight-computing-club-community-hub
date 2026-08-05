package com.daylightcomputer.shade.control;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/** The amber↔white slider. On the DC-1 the stock slider writes
 *  Settings.System "screen_brightness_amber_rate" = 256 + amber, where
 *  amber is 0..255 (256 = paper white, 511 = full amber; the +256 is a
 *  sentinel the MTK display stack expects — never write below it).
 *  Discovered on-glass 2026-07-11 by diffing `settings list system`
 *  around the stock slider.
 *
 *  A write to the key is only half the job — the backlight does not move
 *  until a brightness write follows it. See {@link #pokeBacklight}.
 *
 *  Backend chain:
 *   1. SYSTEM-TABLE KEY — the real amber drive. Present on every DC-1;
 *      writable only from an install that lives on a system partition
 *      (the Sol:OS blessing, or a hand-placed system app on a rooted
 *      unit). The gate is the settings provider's system-table rule,
 *      which turns on whether the caller is a system app — no grantable
 *      permission reaches it, so `pm grant` cannot open this door and
 *      neither can an in-app `su` call on a stock userdebug build (that
 *      su refuses every caller that is not root or shell).
 *   2. NIGHT DISPLAY — AOSP night light, ONLY on hardware that has no
 *      real key at all (emulators, dev devices). On a DC-1 a software
 *      tint over a hardware-amber backlight looks broken, not warm —
 *      field-tested 2026-07-11 — so there the slider sits disabled with
 *      an honest hint until the blessing instead of faking it.
 *   3. none — slider shows the hint. */
public final class Warmth {
    private static final String TAG = "ShadeWarmth";
    private static final String PREFS = "shade";
    private static final String PREF_KEY = "warmth_key";
    private static final String PREF_MIN = "warmth_min";
    private static final String PREF_MAX = "warmth_max";

    /** The DC-1's real key first, then likely names for other builds;
     *  verified on-device at first run. Probing a missing key is free. */
    private static final String[] DEFAULT_KEYS = {
            "screen_brightness_amber_rate",
            "screen_warmth", "daylight_warmth", "warmth", "amber_brightness",
            "backlight_warmth", "screen_amber", "reading_light_warmth",
    };

    /** Raw range for a key: value = min + v·(max−min). The amber key keeps
     *  its +256 sentinel offset; everything else defaults to 0..255. */
    private static int defMin(String k) {
        return "screen_brightness_amber_rate".equals(k) ? 256 : 0;
    }
    private static int defMax(String k) {
        return "screen_brightness_amber_rate".equals(k) ? 511 : 255;
    }

    // AOSP night display range (config_nightDisplayColorTemperatureMin/Max defaults)
    private static final int NIGHT_TEMP_WARM = 2596;
    private static final int NIGHT_TEMP_COOL = 4082;

    private Warmth() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The Settings.System key present on this device, or null. */
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

    /** AOSP refuses unknown system-table keys from non-system apps
     *  ("You cannot keep your settings in the secure settings"), no matter
     *  which write grants are held. A Sol:OS (system-image) install passes;
     *  a sideload must fall back. Probed once with a same-value write. */
    private static volatile int sKeyWritable = -1; // -1 unprobed, 0 no, 1 yes

    private static String usableKey(Context c) {
        String k = systemKey(c);
        if (k == null || !Caps.writeSettings(c)) return null;
        if (sKeyWritable == -1) {
            try {
                int raw = Settings.System.getInt(c.getContentResolver(), k, defMin(k));
                Settings.System.putInt(c.getContentResolver(), k, raw);
                sKeyWritable = 1;
            } catch (Throwable t) {
                Log.w(TAG, "system key not writable from this install: " + t);
                sKeyWritable = 0;
            }
        }
        return sKeyWritable == 1 ? k : null;
    }

    public static void configure(Context c, String key, int min, int max) {
        prefs(c).edit().putString(PREF_KEY, key)
                .putInt(PREF_MIN, min).putInt(PREF_MAX, max).apply();
    }

    private static int keyMin(Context c, String k) {
        return prefs(c).contains(PREF_KEY) ? prefs(c).getInt(PREF_MIN, 0) : defMin(k);
    }
    private static int keyMax(Context c, String k) {
        return prefs(c).contains(PREF_KEY)
                ? Math.max(keyMin(c, k) + 1, prefs(c).getInt(PREF_MAX, 255))
                : defMax(k);
    }

    public static String backendName(Context c) {
        String k = usableKey(c);
        if (k != null) return "system key \"" + k + "\"";
        // the stand-in exists for key-less dev hardware only — on a real
        // DC-1 (key present, write refused) honesty beats a fake tint
        if (systemKey(c) == null && Caps.secureSettings(c))
            return "night display (stand-in)";
        return "";
    }

    public static boolean available(Context c) {
        return !backendName(c).isEmpty();
    }

    /** 0 = paper white, 1 = full amber. */
    public static float get(Context c) {
        String k = usableKey(c);
        if (k != null) {
            int min = keyMin(c, k), max = keyMax(c, k);
            int raw = Settings.System.getInt(c.getContentResolver(), k, min);
            return Math.max(0f, Math.min(1f, (raw - min) / (float) (max - min)));
        }
        if (systemKey(c) == null && Caps.secureSettings(c)) {
            if (Settings.Secure.getInt(c.getContentResolver(), "night_display_activated", 0) == 0)
                return 0f;
            int t = Settings.Secure.getInt(c.getContentResolver(),
                    "night_display_color_temperature", NIGHT_TEMP_COOL);
            return (NIGHT_TEMP_COOL - t) / (float) (NIGHT_TEMP_COOL - NIGHT_TEMP_WARM);
        }
        return 0f;
    }

    /** Writing the amber key changes nothing on its own.
     *
     *  Verified on glass 2026-08-05 (JP4R01422, root shell reading the
     *  driver directly): with the key set to 300 and left alone, both
     *  `/sys/class/leds/lcd-backlight-amber/brightness` and its white
     *  sibling stayed exactly where they were. The key is a passive side
     *  channel — LightsService re-reads it only while servicing a
     *  *brightness* write (`amber = getInt(...)` inside setBrightness),
     *  which is why stock SystemUI "re-pokes the current brightness"
     *  after every warmth change. Rewriting brightness to the value it
     *  already holds does NOT count: the settings provider dedupes it and
     *  the strings never move (measured — same 27/1 split before and
     *  after). It takes a real change.
     *
     *  So: nudge one step and come straight back. Two facts from
     *  {@link Brightness} constrain which way — raw 1 is backlight OFF
     *  and raw 2 is a dead rung that emits nothing — so anything at or
     *  below 3 nudges UP, or the "poke" would flash the panel dark. At
     *  raw ≤ 1 the backlight is off, amber is moot (LightsService pins it
     *  to 255 when the brightness byte is 0), and we skip entirely.
     *
     *  Costs only WRITE_SETTINGS, which the brightness slider already
     *  needs — so this half works today, on any DC-1, blessed or not. */
    private static void pokeBacklight(Context c) {
        if (!Caps.writeSettings(c)) return;
        try {
            int b = Settings.System.getInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, -1);
            if (b <= 1) return;
            int nudge = b > 3 ? b - 1 : b + 1;
            Settings.System.putInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, nudge);
            Settings.System.putInt(c.getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, b);
        } catch (Throwable t) {
            Log.w(TAG, "backlight poke failed: " + t);
        }
    }

    public static void set(Context c, float v) {
        v = Math.max(0f, Math.min(1f, v));
        String k = usableKey(c);
        if (k != null) {
            int min = keyMin(c, k), max = keyMax(c, k);
            try {
                Settings.System.putInt(c.getContentResolver(), k,
                        min + Math.round(v * (max - min)));
                pokeBacklight(c);
                return;
            } catch (Throwable t) {
                Log.w(TAG, "system key write failed: " + t);
            }
        }
        if (systemKey(c) == null && Caps.secureSettings(c)) {
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
