package com.daylightcomputer.shade.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.daylightcomputer.shade.control.WifiNets;

import java.util.List;

/** Networks, drawn our way: current network on top, then everything the
 *  radio can hear, strongest first. Tap a saved network to hop to it
 *  (blessed installs); unknown or password-needing networks hand off to
 *  the compact system sheet. */
public class WifiPickerView extends PickerPage {

    private final Runnable handOffToSystemSheet;
    private final TextView status;
    private final LinearLayout list;
    private BroadcastReceiver receiver;
    private String joining = "";

    public WifiPickerView(Context c, Runnable handOffToSystemSheet) {
        super(c);
        this.handOffToSystemSheet = handOffToSystemSheet;
        status = statusLine();
        addView(status);
        list = new LinearLayout(c);
        list.setOrientation(VERTICAL);
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    @Override public String title() { return "Wi-Fi"; }

    @Override public void start() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) { populate(); }
        };
        IntentFilter f = new IntentFilter();
        f.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        f.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        getContext().registerReceiver(receiver, f);
        if (WifiNets.canListNetworks(getContext())) WifiNets.scan(getContext());
        populate();
    }

    @Override public void stop() {
        if (receiver != null) {
            try { getContext().unregisterReceiver(receiver); } catch (Throwable ignored) {}
            receiver = null;
        }
    }

    private void populate() {
        Context c = getContext();
        list.removeAllViews();

        boolean on = WifiNets.isOn(c);
        list.addView(row("Wi-Fi is " + (on ? "on" : "off"),
                on ? "tap to turn off" : "tap to turn on", () -> {
                    if (!WifiNets.toggleDirect(c)) handOffToSystemSheet.run();
                    else postDelayed(this::populate, 400);
                }), rowLp());

        if (!on) { status.setText("turn Wi-Fi on to look around"); return; }

        if (!WifiNets.canListNetworks(c)) {
            // Android shows scan results only to system installs (or with
            // device location on + fine location, which this app refuses
            // to ask for) — say so instead of listening forever
            status.setText("");
            status.setVisibility(GONE);
            list.addView(row("the network list arrives with the Sol:OS blessing",
                    "until then, tap here to pick from the system list",
                    handOffToSystemSheet), rowLp());
            return;
        }

        String current = WifiNets.currentSsid(c);
        if (!current.isEmpty()) {
            list.addView(netRow(current, "connected", 3, null), rowLp());
        }

        List<ScanResult> results = WifiNets.results(c);
        int shown = 0;
        for (ScanResult r : results) {
            if (r.SSID.equals(current)) continue;
            if (shown >= 10) break;
            final String ssid = r.SSID;
            String sub = (WifiNets.secured(r) ? "secured" : "open")
                    + (ssid.equals(joining) ? " · joining…" : "");
            list.addView(netRow(ssid, sub, WifiNets.level(c, r), () -> {
                if (WifiNets.connectSaved(c, ssid)) {
                    joining = ssid;
                    populate();
                } else {
                    // unknown network (password needed) or unblessed install
                    handOffToSystemSheet.run();
                }
            }), rowLp());
            shown++;
        }

        status.setText(shown == 0 && current.isEmpty()
                ? "listening for networks…" : "");
        status.setVisibility(status.getText().length() == 0 ? GONE : VISIBLE);
    }

    /** A network row: signal bars + name + state line. */
    private View netRow(String ssid, String sub, int level, Runnable onTap) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(c, 14), Ui.dp(c, 10), Ui.dp(c, 14), Ui.dp(c, 10));
        r.setBackground(Ui.box(c, 2, Ui.PAPER, Ui.INK));
        r.setMinimumHeight(Ui.dp(c, 56));

        SignalBars bars = new SignalBars(c, level);
        r.addView(bars, new LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 22)));

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(VERTICAL);
        body.setPadding(Ui.dp(c, 12), 0, 0, 0);
        TextView t = Ui.text(c, 16, Ui.INK, true, true);
        t.setText(ssid);
        t.setSingleLine();
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        body.addView(t);
        TextView s = Ui.text(c, 13.5f, Ui.MID, true, false);
        s.setText(sub);
        body.addView(s);
        r.addView(body, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        if (onTap != null) {
            r.setClickable(true);
            r.setOnClickListener(v -> onTap.run());
        }
        return r;
    }

    /** Four ascending bars, filled by signal level. */
    private static class SignalBars extends View {
        private final Paint paint = new Paint();
        private final int level;

        SignalBars(Context c, int level) { super(c); this.level = level; }

        @Override protected void onDraw(Canvas cv) {
            int w = getWidth(), h = getHeight();
            float bw = w / 5.5f;
            for (int i = 0; i < 4; i++) {
                paint.setColor(i <= level ? Ui.INK : Ui.FAINT);
                float x = i * (bw * 1.4f);
                float bh = h * (0.35f + 0.2f * i);
                cv.drawRect(x, h - bh, x + bw, h, paint);
            }
        }
    }
}
