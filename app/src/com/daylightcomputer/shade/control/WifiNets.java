package com.daylightcomputer.shade.control;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The Wi-Fi radio, defensively. Reads work with the user-grantable
 *  NEARBY_WIFI_DEVICES (declared neverForLocation — the shade sees radios,
 *  never places). Direct writes (toggle, join saved network) light up with
 *  the Sol:OS blessing; everything degrades to `false` so callers can hand
 *  off to the system sheet. Uses only public APIs — the deprecated ones are
 *  exactly the ones system apps still use. */
@SuppressWarnings("deprecation")
public final class WifiNets {
    private static final String TAG = "ShadeWifiNets";
    private WifiNets() {}

    private static WifiManager wm(Context c) {
        return c.getApplicationContext().getSystemService(WifiManager.class);
    }

    public static boolean isOn(Context c) {
        try { return wm(c) != null && wm(c).isWifiEnabled(); }
        catch (Throwable t) { return false; }
    }

    /** Flip the radio without any fallback UI. True = it worked. */
    public static boolean toggleDirect(Context c) {
        if (!Caps.networkSettings(c)) return false;
        try { return wm(c).setWifiEnabled(!isOn(c)); }
        catch (Throwable t) { Log.w(TAG, "setWifiEnabled: " + t); return false; }
    }

    public static void scan(Context c) {
        try { wm(c).startScan(); } catch (Throwable t) { Log.w(TAG, "startScan: " + t); }
    }

    /** Cached scan results, deduped per SSID (strongest kept), best first. */
    public static List<ScanResult> results(Context c) {
        List<ScanResult> out = new ArrayList<>();
        try {
            Map<String, ScanResult> best = new HashMap<>();
            for (ScanResult r : wm(c).getScanResults()) {
                String ssid = r.SSID;
                if (ssid == null || ssid.isEmpty()) continue;
                ScanResult prev = best.get(ssid);
                if (prev == null || r.level > prev.level) best.put(ssid, r);
            }
            out.addAll(best.values());
            out.sort((a, b) -> Integer.compare(b.level, a.level));
        } catch (Throwable t) {
            Log.w(TAG, "getScanResults: " + t);
        }
        return out;
    }

    /** 0..3 for the little bars. */
    public static int level(Context c, ScanResult r) {
        try { return wm(c).calculateSignalLevel(r.level, 4); }
        catch (Throwable t) { return 2; }
    }

    public static boolean secured(ScanResult r) {
        String cap = r.capabilities == null ? "" : r.capabilities;
        return cap.contains("WPA") || cap.contains("WEP") || cap.contains("SAE")
                || cap.contains("EAP");
    }

    /** The SSID we're on right now, or "". */
    public static String currentSsid(Context c) {
        try {
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network n = cm.getActiveNetwork();
            NetworkCapabilities caps = n == null ? null : cm.getNetworkCapabilities(n);
            if (caps == null || !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "";
            Object info = caps.getTransportInfo();
            if (!(info instanceof WifiInfo)) return "";
            String ssid = ((WifiInfo) info).getSSID();
            if (ssid == null || ssid.contains("unknown ssid")) return "";
            return ssid.replace("\"", "");
        } catch (Throwable t) {
            return "";
        }
    }

    /** Join a network we already have credentials for. True = request sent.
     *  Returns false for unknown networks (password needed → system sheet)
     *  and on unblessed installs (getConfiguredNetworks comes back empty). */
    public static boolean connectSaved(Context c, String ssid) {
        try {
            List<WifiConfiguration> cfgs = wm(c).getConfiguredNetworks();
            if (cfgs == null) return false;
            String quoted = "\"" + ssid + "\"";
            for (WifiConfiguration cfg : cfgs) {
                if (quoted.equals(cfg.SSID) || ssid.equals(cfg.SSID)) {
                    return wm(c).enableNetwork(cfg.networkId, true);
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "connectSaved: " + t);
        }
        return false;
    }
}
