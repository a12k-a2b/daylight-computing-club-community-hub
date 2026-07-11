package com.daylightcomputer.shade.control;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.app.NotificationManager;
import android.provider.Settings;

import com.daylightcomputer.shade.ShadeNLService;

/** The permission matrix, evaluated live. Preview mode needs only tier-1
 *  grants; full mode lights up when Sol:OS ships us as a priv-app (or
 *  platform-signs us) and the tier-3 permissions arrive. */
public final class Caps {
    private Caps() {}

    private static boolean has(Context c, String perm) {
        return c.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    // tier 1 — user-grantable on any DC-1 today
    public static boolean overlay(Context c) { return Settings.canDrawOverlays(c); }
    public static boolean writeSettings(Context c) { return Settings.System.canWrite(c); }
    public static boolean dnd(Context c) {
        return c.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted();
    }
    public static boolean notifListener(Context c) {
        return c.getSystemService(NotificationManager.class)
                .isNotificationListenerAccessGranted(new ComponentName(c, ShadeNLService.class));
    }
    public static boolean btConnect(Context c) { return has(c, "android.permission.BLUETOOTH_CONNECT"); }
    public static boolean postNotifs(Context c) { return has(c, "android.permission.POST_NOTIFICATIONS"); }

    // tier 2 — adb-grantable for development
    public static boolean secureSettings(Context c) { return has(c, "android.permission.WRITE_SECURE_SETTINGS"); }

    // tier 3 — arrives with the Sol:OS build change
    public static boolean statusBar(Context c) { return has(c, "android.permission.STATUS_BAR"); }
    public static boolean internalWindow(Context c) { return has(c, "android.permission.INTERNAL_SYSTEM_WINDOW"); }
    public static boolean networkSettings(Context c) { return has(c, "android.permission.NETWORK_SETTINGS"); }
    public static boolean airplaneDirect(Context c) {
        return has(c, "android.permission.NETWORK_AIRPLANE_MODE") || networkSettings(c);
    }
    public static boolean dayNight(Context c) { return has(c, "android.permission.MODIFY_DAY_NIGHT_MODE"); }

    /** Full mode = we may take the swipe away from the stock shade. */
    public static boolean fullMode(Context c) { return statusBar(c) && internalWindow(c); }
}
