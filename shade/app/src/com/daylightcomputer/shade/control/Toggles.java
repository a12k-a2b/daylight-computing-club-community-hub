package com.daylightcomputer.shade.control;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.UiModeManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;

/** Every quick toggle in one place. Uniform shape: state(), sub(), toggle().
 *  toggle() returns true when it changed the setting directly and false when
 *  it had to fall back to opening a Settings screen (the panel closes then).
 *  Direct paths need tier-2/3 grants; fallbacks work on any DC-1 today. */
public final class Toggles {
    private static final String TAG = "ShadeToggles";
    private Toggles() {}

    private static void openSettings(Context c, String action) {
        try {
            Intent i = new Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Throwable t) {
            Log.w(TAG, "no settings screen for " + action);
        }
    }

    // ---- wifi / bluetooth ----
    // state + direct flips live in WifiNets / BtDevices; these are the
    // system hand-off surfaces the panel falls back to
    public static boolean wifiOn(Context c) { return WifiNets.isOn(c); }
    public static boolean btOn(Context c) { return BtDevices.isOn(c); }

    /** Straight to the Wi-Fi settings screen. (The Settings.Panel bottom
     *  sheet is deprecated and renders as a bare "Wi-Fi" stub on Sol:OS —
     *  field-tested 2026-07-11: it looked shady and just led here anyway.) */
    public static void openWifiSheet(Context c) {
        openSettings(c, Settings.ACTION_WIFI_SETTINGS);
    }
    /** No compact sheet exists for Bluetooth — full settings it is. */
    public static void openBtSettings(Context c) {
        openSettings(c, Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    // ---- airplane ----
    public static boolean airplaneOn(Context c) {
        return Settings.Global.getInt(c.getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }
    public static boolean airplaneToggle(Context c) {
        boolean target = !airplaneOn(c);
        if (Caps.airplaneDirect(c) && SysApi.setAirplaneMode(c, target)) return true;
        openSettings(c, Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        return false;
    }

    // ---- do not disturb ----
    public static boolean dndOn(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        int f = nm.getCurrentInterruptionFilter();
        return f != NotificationManager.INTERRUPTION_FILTER_ALL
                && f != NotificationManager.INTERRUPTION_FILTER_UNKNOWN;
    }
    public static boolean dndToggle(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (nm.isNotificationPolicyAccessGranted()) {
            nm.setInterruptionFilter(dndOn(c)
                    ? NotificationManager.INTERRUPTION_FILTER_ALL
                    : NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            return true;
        }
        openSettings(c, Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
        return false;
    }

    // ---- dark mode ----
    public static boolean darkOn(Context c) {
        int m = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return m == Configuration.UI_MODE_NIGHT_YES;
    }
    public static boolean darkToggle(Context c) {
        UiModeManager um = c.getSystemService(UiModeManager.class);
        if (Caps.dayNight(c)) {
            try {
                um.setNightMode(darkOn(c)
                        ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
                return true;
            } catch (Throwable t) { Log.w(TAG, "setNightMode: " + t); }
        }
        openSettings(c, Settings.ACTION_DISPLAY_SETTINGS);
        return false;
    }

    // ---- rotation lock (works today: plain WRITE_SETTINGS) ----
    public static boolean rotationLocked(Context c) {
        return Settings.System.getInt(c.getContentResolver(),
                Settings.System.ACCELEROMETER_ROTATION, 1) == 0;
    }
    public static boolean rotationToggle(Context c) {
        if (Caps.writeSettings(c)) {
            Settings.System.putInt(c.getContentResolver(),
                    Settings.System.ACCELEROMETER_ROTATION, rotationLocked(c) ? 1 : 0);
            return true;
        }
        openSettings(c, Settings.ACTION_MANAGE_WRITE_SETTINGS);
        return false;
    }

    // ---- header info ----
    public static int batteryPercent(Context c) {
        BatteryManager bm = c.getSystemService(BatteryManager.class);
        return bm == null ? -1 : bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }
    public static boolean batteryCharging(Context c) {
        BatteryManager bm = c.getSystemService(BatteryManager.class);
        return bm != null && bm.isCharging();
    }
    public static String nextAlarmText(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        AlarmManager.AlarmClockInfo info = am == null ? null : am.getNextAlarmClock();
        if (info == null) return "";
        return "alarm " + DateFormat.getTimeFormat(c).format(
                new java.util.Date(info.getTriggerTime()));
    }
}
