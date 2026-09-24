package com.daylightcomputer.shade.control;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /** Nameless finds are neighborhood BLE noise (randomized MACs);
     *  anything actually in pairing mode broadcasts a name. */
    public static boolean hasName(BluetoothDevice d) {
        try {
            String n = d.getName();
            return n != null && !n.trim().isEmpty();
        } catch (Throwable t) { return false; }
    }

    // ---- connected-device names for the pill (stock-QS parity) ----
    // A small profile-proxy cache that lives only while the panel is open:
    // ShadeService opens it with the other panel signals, closes it on
    // teardown. Proxies arrive async, so onChange re-paints the pills.

    private static BluetoothProfile sA2dp, sHeadset;

    public static void openProxies(Context c, Runnable onChange) {
        BluetoothAdapter a = adapter(c);
        if (a == null || !Caps.btConnect(c)) return;
        try {
            a.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int p, BluetoothProfile proxy) {
                    sA2dp = proxy;
                    if (onChange != null) onChange.run();
                }
                @Override public void onServiceDisconnected(int p) { sA2dp = null; }
            }, BluetoothProfile.A2DP);
            a.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                @Override public void onServiceConnected(int p, BluetoothProfile proxy) {
                    sHeadset = proxy;
                    if (onChange != null) onChange.run();
                }
                @Override public void onServiceDisconnected(int p) { sHeadset = null; }
            }, BluetoothProfile.HEADSET);
        } catch (Throwable t) { Log.w(TAG, "profile proxies: " + t); }
    }

    public static void closeProxies(Context c) {
        BluetoothAdapter a = adapter(c);
        if (a != null) {
            try { if (sA2dp != null) a.closeProfileProxy(BluetoothProfile.A2DP, sA2dp); } catch (Throwable ignored) {}
            try { if (sHeadset != null) a.closeProfileProxy(BluetoothProfile.HEADSET, sHeadset); } catch (Throwable ignored) {}
        }
        sA2dp = null; sHeadset = null;
    }

    /** Devices with a live audio/headset connection, deduped by address. */
    public static List<BluetoothDevice> connected(Context c) {
        Map<String, BluetoothDevice> out = new LinkedHashMap<>();
        try { if (sA2dp != null) for (BluetoothDevice d : sA2dp.getConnectedDevices()) out.put(d.getAddress(), d); } catch (Throwable ignored) {}
        try { if (sHeadset != null) for (BluetoothDevice d : sHeadset.getConnectedDevices()) out.put(d.getAddress(), d); } catch (Throwable ignored) {}
        return new ArrayList<>(out.values());
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
