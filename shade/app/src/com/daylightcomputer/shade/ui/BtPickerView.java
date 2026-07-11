package com.daylightcomputer.shade.ui;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.daylightcomputer.shade.control.BtDevices;
import com.daylightcomputer.shade.control.Caps;
import com.daylightcomputer.shade.control.SysApi;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Paired devices with connect/disconnect, plus "find new devices" for
 *  pairing — all drawn in the shade. Pairing works on any install (public
 *  APIs; the code-confirmation dialog stays the system's). Routing audio to
 *  a paired device needs the Sol:OS blessing; without it, tapping hands off
 *  to Bluetooth settings. */
@SuppressWarnings({"deprecation", "MissingPermission"})
public class BtPickerView extends PickerPage {

    private final Runnable handOffToSettings;
    private final TextView status;
    private final LinearLayout list;
    private BroadcastReceiver receiver;
    private BluetoothProfile a2dp, headset;
    private final Map<String, BluetoothDevice> found = new LinkedHashMap<>();
    private boolean discovering;

    public BtPickerView(Context c, Runnable handOffToSettings) {
        super(c);
        this.handOffToSettings = handOffToSettings;
        status = statusLine();
        addView(status);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override public String title() { return "Bluetooth"; }

    @Override public void start() {
        Context c = getContext();
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent i) {
                String a = i.getAction();
                if (BluetoothDevice.ACTION_FOUND.equals(a)) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    if (d != null && d.getBondState() != BluetoothDevice.BOND_BONDED) {
                        found.put(d.getAddress(), d);
                    }
                } else if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(a)) {
                    discovering = true;
                } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(a)) {
                    discovering = false;
                }
                populate();
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(BluetoothDevice.ACTION_FOUND);
        f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        f.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED);
        f.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        c.registerReceiver(receiver, f);

        BluetoothAdapter adapter = BtDevices.adapter(c);
        if (adapter != null && Caps.btConnect(c)) {
            try {
                adapter.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        a2dp = proxy; populate();
                    }
                    @Override public void onServiceDisconnected(int profile) { a2dp = null; }
                }, BluetoothProfile.A2DP);
                adapter.getProfileProxy(c, new BluetoothProfile.ServiceListener() {
                    @Override public void onServiceConnected(int profile, BluetoothProfile proxy) {
                        headset = proxy; populate();
                    }
                    @Override public void onServiceDisconnected(int profile) { headset = null; }
                }, BluetoothProfile.HEADSET);
            } catch (Throwable ignored) {}
        }
        populate();
    }

    @Override public void stop() {
        Context c = getContext();
        if (receiver != null) {
            try { c.unregisterReceiver(receiver); } catch (Throwable ignored) {}
            receiver = null;
        }
        BtDevices.cancelDiscovery(c);
        BluetoothAdapter adapter = BtDevices.adapter(c);
        if (adapter != null) {
            try { if (a2dp != null) adapter.closeProfileProxy(BluetoothProfile.A2DP, a2dp); } catch (Throwable ignored) {}
            try { if (headset != null) adapter.closeProfileProxy(BluetoothProfile.HEADSET, headset); } catch (Throwable ignored) {}
        }
        a2dp = null; headset = null;
    }

    private List<BluetoothDevice> connectedNow() {
        List<BluetoothDevice> out = new ArrayList<>();
        try { if (a2dp != null) out.addAll(a2dp.getConnectedDevices()); } catch (Throwable ignored) {}
        try { if (headset != null) out.addAll(headset.getConnectedDevices()); } catch (Throwable ignored) {}
        return out;
    }

    private void populate() {
        Context c = getContext();
        list.removeAllViews();

        boolean on = BtDevices.isOn(c);
        list.addView(row("Bluetooth is " + (on ? "on" : "off"),
                on ? "tap to turn off" : "tap to turn on", () -> {
                    if (!BtDevices.toggleDirect(c)) handOffToSettings.run();
                    else postDelayed(this::populate, 500);
                }), rowLp());

        if (!on) { status.setText(""); status.setVisibility(GONE); return; }

        List<BluetoothDevice> connected = connectedNow();
        for (BluetoothDevice d : BtDevices.bonded(c)) {
            boolean isConn = false;
            for (BluetoothDevice cd : connected) {
                if (cd.getAddress().equals(d.getAddress())) { isConn = true; break; }
            }
            final boolean conn = isConn;
            final BluetoothDevice dev = d;
            list.addView(row(BtDevices.name(d),
                    conn ? "connected · tap to disconnect" : "paired · tap to connect",
                    () -> {
                        boolean acted = false;
                        if (a2dp != null) acted |= SysApi.btProfileAct(a2dp, dev, !conn);
                        if (headset != null) acted |= SysApi.btProfileAct(headset, dev, !conn);
                        if (!acted) handOffToSettings.run();
                        else postDelayed(this::populate, 800);
                    }), rowLp());
        }

        // pairing: works on any install, dialog is the system's
        list.addView(row(discovering ? "searching…" : "find new devices",
                discovering ? "tap a device below to pair" : "",
                () -> {
                    if (!discovering) {
                        found.clear();
                        if (!BtDevices.startDiscovery(c)) {
                            status.setVisibility(VISIBLE);
                            status.setText("allow \"nearby devices\" in shade setup to search");
                        }
                    }
                    populate();
                }), rowLp());

        for (BluetoothDevice d : found.values()) {
            final BluetoothDevice dev = d;
            list.addView(row(BtDevices.name(d), "found nearby · tap to pair", () -> {
                if (BtDevices.pair(c, dev)) {
                    status.setVisibility(VISIBLE);
                    status.setText("pairing with " + BtDevices.name(dev) + "…");
                } else {
                    handOffToSettings.run();
                }
            }), rowLp());
        }
    }
}
