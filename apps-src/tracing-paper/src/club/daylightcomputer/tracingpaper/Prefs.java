package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.content.SharedPreferences;

/** One tiny bag of settings shared by the activity, the service and the pad. */
final class Prefs {
    static final String CALIBRATED_ACTION = "club.daylightcomputer.tracingpaper.CALIBRATED";

    static final String K_TOGGLE = "toggle_key";
    static final String K_CALIBRATE_UNTIL = "calibrate_until";
    static final String K_PEN_ONLY = "pen_only";
    static final String K_OPACITY = "opacity_pct";
    static final String K_LAST_PAGE = "last_page";
    static final String K_CANVAS_W = "canvas_w";
    static final String K_CANVAS_H = "canvas_h";
    static final String K_PEN_SIZE = "pen_size";
    static final String K_HI_SIZE = "hi_size";
    static final String K_ERASE_SIZE = "erase_size";
    static final String K_ERASE_PIXEL = "erase_pixel";
    static final String K_HINTS = "hints_shown";
    static final String K_LAST_BACKUP = "last_backup";
    static final String K_BETA_FLICK = "beta_world_flick";
    static final String K_BETA_FOLLOW = "beta_world_follow";

    /**
     * Eight parts paper, two parts world: enough opacity that the pad is a
     * place, enough transparency that you never feel you left the page.
     */
    static final int DEFAULT_OPACITY = 80;

    /** KEYCODE_F12 — what the DC-1's orange top button sends. Re-learnable in the app. */
    static final int DEFAULT_TOGGLE = 142;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getSharedPreferences("pad", Context.MODE_PRIVATE);
    }
}
