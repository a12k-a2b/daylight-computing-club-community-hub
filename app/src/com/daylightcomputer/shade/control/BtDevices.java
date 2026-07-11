package com.daylightcomputer.shade.control;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/** The Bluetooth radio, defensively. Listing paired devices, discovery and
 *  pairing are public APIs (BLUETOOTH_CONNECT / BLUETOOTH_SCAN, both
 *  user-grantable, both declared neverForLocation). Routing a paired device's
 *  audio (profile connect/disconnect) is the one hidden piece — that lives
 *  in SysApi and lights up with the Sol:OS blessing. */
@SuppressWarnings({"deprecation", "MissingPermission"})
public final class BtDevices {
    private static final String TAG = "ShadeBtDevices";
    private BtDevices() {}

    public static BluetoothAdapter adapter(Context c) {
        BluetoothManager bm = c.getSystemService(BluetoothManager.class);
        return bm == null ? null : bm.getAdapter();
    }

    public static boolean isOn(Context c) {
        try {
            BluetoothAdapter a = adapter(c);
            return a != null && a.isEnabled();
        } catch (Throwable t) { return false; }
    }

    /** Flip the radio without any fallback UI. True = it worked. */
    public static boolean toggleDirect(Context c) {
        if (!Caps.btConnect(c)) return false;
        try {
            BluetoothAdapter a = adapter(c);
            if (a == null) return false;
            return a.isEnabled() ? a.disable() : a.enable();
        } catch (Throwable t) { Log.w(TAG, "toggle: " + t); return false; }
    }

    public static List<BluetoothDevice> bonded(Context c) {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            BluetoothAdapter a = adapter(c);
            if (a != null && Caps.btConnect(c)) out.addAll(a.getBondedDevices());
        } catch (Throwable t) { Log.w(TAG, "bonded: " + t); }
        return out;
    }

    public static String name(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n == null || n.isEmpty() ? d.getAddress() : n;
        } catch (Throwable t) { return "device"; }
    }

    public static boolean startDiscovery(Context c) {
        try {
            BluetoothAdapter a = adapter(c);
            return a != null && a.startDiscovery();
        } catch (Throwable t) { Log.w(TAG, "discovery: " + t); return false; }
    }

    public static void cancelDiscovery(Context c) {
        try {
            BluetoothAdapter a = adapter(c);
            if (a != null) a.cancelDiscovery();
        } catch (Throwable ignored) {}
    }

    /** Pair. The confirmation dialog is the system's (by design). */
    public static boolean pair(Context c, BluetoothDevice d) {
        try {
            cancelDiscovery(c);
            return d.createBond();
        } catch (Throwable t) { Log.w(TAG, "createBond: " + t); return false; }
    }
}
