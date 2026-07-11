package com.daylightcomputer.shade;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.daylightcomputer.shade.control.Caps;
import com.daylightcomputer.shade.control.Warmth;
import com.daylightcomputer.shade.ui.Ui;

import java.util.ArrayList;
import java.util.List;

/** The control room: see what the shade can do on this device, grant what
 *  it still needs, choose how it opens. Written for a non-technical friend —
 *  every row is a plain sentence and a single tap. */
public class MainActivity extends Activity {

    private LinearLayout page;
    private ScrollView rootScroll;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sc = new ScrollView(this);
        rootScroll = sc;
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 22);
        page.setPadding(p, p, p, Ui.dp(this, 60));
        sc.addView(page);
        setContentView(sc);

        List<String> ask = new ArrayList<>();
        if (!Caps.postNotifs(this)) ask.add("android.permission.POST_NOTIFICATIONS");
        if (!Caps.btConnect(this)) ask.add("android.permission.BLUETOOTH_CONNECT");
        if (!Caps.btScan(this)) ask.add("android.permission.BLUETOOTH_SCAN");
        if (!Caps.nearbyWifi(this)) ask.add("android.permission.NEARBY_WIFI_DEVICES");
        if (!ask.isEmpty()) requestPermissions(ask.toArray(new String[0]), 1);
    }

    @Override protected void onResume() {
        super.onResume();
        startForegroundService(new Intent(this, ShadeService.class)
                .setAction(ShadeService.ACTION_APPLY));
        rebuild();
    }

    private void rebuild() {
        Ui.applyTheme(this);
        rootScroll.setBackgroundColor(Ui.PAPER);
        page.removeAllViews();

        TextView h = Ui.text(this, 30, Ui.INK, true, true);
        h.setText("Daylight Shade");
        page.addView(h);

        if (Prefs.safeTripped(this)) {
            TextView warn = Ui.text(this, 15, Ui.INK, true, false);
            warn.setBackground(Ui.box(this, 3, Ui.PAPER, Ui.INK));
            int wp = Ui.dp(this, 14);
            warn.setPadding(wp, wp, wp, wp);
            warn.setText("The shade hit trouble a few times in a row, so it stepped "
                    + "aside: swipe takeover and the catch strip are off and the stock "
                    + "shade is back. Everything else still works. Tap here when you've "
                    + "read this.");
            warn.setClickable(true);
            warn.setOnClickListener(v -> { Prefs.clearSafeTripped(this); rebuild(); });
            page.addView(warn, lpFull(-2, 12));
        }
        TextView tag = Ui.text(this, 16, Ui.MID, true, false);
        tag.setText("your own pull-down panel — calm, grayscale, ours to shape");
        tag.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 18));
        page.addView(tag);

        page.addView(Ui.button(this, "open the panel now", () ->
                startForegroundService(new Intent(this, ShadeService.class)
                        .setAction(ShadeService.ACTION_SHOW))),
                lpFull(56, 0));

        // ---- mode banner ----
        boolean full = Caps.fullMode(this);
        TextView mode = Ui.text(this, 15, Ui.INK, true, false);
        mode.setBackground(Ui.box(this, 2, Ui.PAPER, Ui.INK));
        int pp = Ui.dp(this, 14);
        mode.setPadding(pp, pp, pp, pp);
        mode.setText(full
                ? "FULL MODE — Sol:OS has blessed this app. The swipe-down can be entirely ours."
                : "PREVIEW MODE — the stock shade still owns the swipe from the very top. "
                + "Everything below works today; full takeover arrives with the Sol:OS build "
                + "change (see the README in the repo).");
        page.addView(mode, lpFull(-2, 16));

        // ---- how it opens ----
        section("how the panel opens");
        row(Prefs.STRIP_OFF.equals(Prefs.stripMode(this)),
                "no swipe zone (use the tile or this button)",
                () -> { Prefs.setStripMode(this, Prefs.STRIP_OFF); apply(); });
        row(Prefs.STRIP_BELOW.equals(Prefs.stripMode(this)),
                "swipe zone just below the status bar",
                () -> { Prefs.setStripMode(this, Prefs.STRIP_BELOW); apply(); });
        if (full) {
            row(Prefs.STRIP_OVER.equals(Prefs.stripMode(this)),
                    "swipe the status bar itself (like stock)",
                    () -> { Prefs.setStripMode(this, Prefs.STRIP_OVER); apply(); });
            row(Prefs.takeOver(this),
                    "silence the stock shade (ours replaces it)",
                    () -> { Prefs.setTakeOver(this, !Prefs.takeOver(this)); apply(); });
        }
        note("Tip: there is also a “Daylight panel” tile you can add to the stock "
                + "quick settings — a stepping stone while both shades coexist.");

        // ---- pickers ----
        section("network & device pickers");
        row(Prefs.pickers(this),
                Prefs.pickers(this)
                        ? "in-shade Wi-Fi + Bluetooth pickers (young — tap to disable)"
                        : "in-shade Wi-Fi + Bluetooth pickers are off (tap to enable)",
                () -> { Prefs.setPickers(this, !Prefs.pickers(this)); rebuild(); });
        note("When off, the pills hand off to the system surfaces instead "
                + "(the compact Wi-Fi sheet, Bluetooth settings).");

        // ---- grants ----
        section("what this device allows so far");
        grant(Caps.overlay(this), "draw the panel over other apps (required)",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()))));
        grant(Caps.writeSettings(this), "brightness + rotation (modify settings)",
                () -> startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName()))));
        grant(Caps.notifListener(this), "notifications + media controls (listener access)",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        grant(Caps.dnd(this), "do-not-disturb toggle",
                () -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        grant(Caps.secureSettings(this), "warmth stand-in + more (secure settings, via adb)",
                this::showAdbHelp);
        grant(Caps.statusBar(this), "silence the stock shade — arrives with Sol:OS blessing", null);
        grant(Caps.internalWindow(this), "own the status-bar swipe — arrives with Sol:OS blessing", null);

        // ---- warmth backend ----
        section("warmth slider hookup");
        String backend = Warmth.backendName(this);
        note(backend.isEmpty()
                ? "Not hooked up yet. The amber backlight lives behind the "
                + "screen_brightness_amber_rate setting, which Android only lets "
                + "system apps write — the Sol:OS build drives it directly. On a "
                + "sideload, granting secure settings (above) enables a night-light "
                + "stand-in."
                : "Active backend: " + backend + ".");
    }

    private void apply() {
        startForegroundService(new Intent(this, ShadeService.class)
                .setAction(ShadeService.ACTION_APPLY));
        rebuild();
    }

    private LinearLayout.LayoutParams lpFull(int hDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                hDp <= 0 ? LinearLayout.LayoutParams.WRAP_CONTENT : Ui.dp(this, hDp));
        lp.bottomMargin = Ui.dp(this, bottomDp > 0 ? bottomDp : 10);
        return lp;
    }

    private void section(String label) {
        TextView t = Ui.text(this, 13, Ui.MID, false, true);
        t.setText(label);
        t.setAllCaps(true);
        t.setLetterSpacing(0.09f);
        t.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 8));
        page.addView(t);
    }

    private void note(String s) {
        TextView t = Ui.text(this, 14, Ui.MID, true, false);
        t.setText(s);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        page.addView(t);
    }

    /** A pick-one row: filled square = selected. */
    private void row(boolean selected, String label, Runnable onTap) {
        TextView t = Ui.text(this, 16, Ui.INK, true, selected);
        t.setText((selected ? "■  " : "□  ") + label);
        t.setMinHeight(Ui.dp(this, 52));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        t.setBackground(Ui.box(this, selected ? 3 : 2, Ui.PAPER, selected ? Ui.INK : Ui.MID));
        t.setClickable(true);
        t.setOnClickListener(v -> onTap.run());
        page.addView(t, lpFull(-2, 8));
    }

    /** A grant row: ● granted / ○ tap to grant. */
    private void grant(boolean ok, String label, Runnable onTap) {
        TextView t = Ui.text(this, 16, ok ? Ui.INK : (onTap == null ? Ui.FAINT : Ui.INK),
                true, false);
        t.setText((ok ? "●  " : "○  ") + label + (ok ? "" : (onTap == null ? "" : "  — tap to grant")));
        t.setMinHeight(Ui.dp(this, 52));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 14), Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6));
        t.setBackground(Ui.box(this, 2, Ui.PAPER, ok ? Ui.INK : Ui.FAINT));
        if (!ok && onTap != null) {
            t.setClickable(true);
            t.setOnClickListener(v -> onTap.run());
        }
        page.addView(t, lpFull(-2, 8));
    }

    private void showAdbHelp() {
        TextView msg = Ui.text(this, 14, Ui.INK, false, false);
        msg.setTextIsSelectable(true);
        int p = Ui.dp(this, 18);
        msg.setPadding(p, p, p, p);
        msg.setText("From a computer with adb:\n\n"
                + "adb shell pm grant " + getPackageName()
                + " android.permission.WRITE_SECURE_SETTINGS\n\n"
                + "(One-time, survives reboots. On a Sol:OS build this grant "
                + "ships automatically.)");
        new AlertDialog.Builder(this)
                .setTitle("grant via adb")
                .setView(msg)
                .setPositiveButton("done", null)
                .show();
    }

    @Override public void onRequestPermissionsResult(int c, String[] p, int[] r) {
        rebuild();
    }
}
