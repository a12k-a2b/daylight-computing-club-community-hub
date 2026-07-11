package com.daylightcomputer.shade.control;

import android.content.Context;
import android.net.ConnectivityManager;
import android.util.Log;

import java.lang.reflect.Method;

/** The only file that touches hidden system APIs, always by reflection and
 *  always with a graceful false on failure. On a normal install these calls
 *  simply fail (SecurityException / blocklist) and callers fall back; on a
 *  Sol:OS priv-app install they succeed. Keeping every such call here makes
 *  the AOSP 16/17 port a one-file audit. */
public final class SysApi {
    private static final String TAG = "ShadeSysApi";
    private SysApi() {}

    /** StatusBarManager.DISABLE_EXPAND — stops the stock shade from opening
     *  on swipe, which is what hands the gesture to us. */
    private static final int DISABLE_EXPAND = 0x00010000;
    private static final int DISABLE_NONE = 0;

    public static boolean setStockShadeBlocked(Context c, boolean blocked) {
        try {
            Object sbm = c.getSystemService("statusbar");
            if (sbm == null) return false;
            Method m = sbm.getClass().getMethod("disable", int.class);
            m.invoke(sbm, blocked ? DISABLE_EXPAND : DISABLE_NONE);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "statusbar disable unavailable: " + t);
            return false;
        }
    }

    /** Opens the stock notification shade (used by the pass-through row).
     *  Caller must un-block first if it blocked the shade. */
    public static boolean expandStockNotifications(Context c) {
        try {
            Object sbm = c.getSystemService("statusbar");
            if (sbm == null) return false;
            Method m = sbm.getClass().getMethod("expandNotificationsPanel");
            m.invoke(sbm);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "expandNotificationsPanel unavailable: " + t);
            return false;
        }
    }

    /** BluetoothProfile (A2DP / headset) connect()/disconnect() — hidden,
     *  needs BLUETOOTH_PRIVILEGED (in the priv-app allowlist). This is what
     *  makes tapping a paired device in the shade actually route audio. */
    public static boolean btProfileAct(Object profileProxy,
                                       android.bluetooth.BluetoothDevice d,
                                       boolean connect) {
        try {
            Method m = profileProxy.getClass().getMethod(
                    connect ? "connect" : "disconnect",
                    android.bluetooth.BluetoothDevice.class);
            Object r = m.invoke(profileProxy, d);
            return !(r instanceof Boolean) || (Boolean) r;
        } catch (Throwable t) {
            Log.w(TAG, "bt profile " + (connect ? "connect" : "disconnect")
                    + " unavailable: " + t);
            return false;
        }
    }

    /** ConnectivityManager#setAirplaneMode — @SystemApi, works only with
     *  NETWORK_AIRPLANE_MODE / NETWORK_SETTINGS. */
    public static boolean setAirplaneMode(Context c, boolean on) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Method m = ConnectivityManager.class.getMethod("setAirplaneMode", boolean.class);
            m.invoke(cm, on);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "setAirplaneMode unavailable: " + t);
            return false;
        }
    }
}
