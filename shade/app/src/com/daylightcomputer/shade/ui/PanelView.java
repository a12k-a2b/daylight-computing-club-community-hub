package com.daylightcomputer.shade.ui;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.os.SystemClock;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.daylightcomputer.shade.Prefs;
import com.daylightcomputer.shade.ShadeNLService;
import com.daylightcomputer.shade.control.Brightness;
import com.daylightcomputer.shade.control.BtDevices;
import com.daylightcomputer.shade.control.Media;
import com.daylightcomputer.shade.control.Caps;
import com.daylightcomputer.shade.control.Toggles;
import com.daylightcomputer.shade.control.Warmth;
import com.daylightcomputer.shade.control.WifiNets;

import java.util.Date;
import java.util.List;

/** The shade: header, two sliders, six pills, media card, notification
 *  list, footer. Pure grayscale, real borders, serif type, one screen. */
public class PanelView extends FrameLayout {

    public interface Host {
        void requestHide();          // animate closed, then remove
        void onFullyClosed();        // sheet finished closing
        void openShadeSetup();       // MainActivity
    }

    private final Host host;
    private FrameLayout pageHost;
    private LinearLayout sheet;
    private PickerPage picker;
    private LinearLayout pickerWrap;
    private TextView clock, dateLine, battery;
    private InkSlider brightness, warmth;
    private TileButton tWifi, tBt, tAir, tDnd, tDark, tRot;
    private LinearLayout mediaCard, extraControls, notifList;
    private TextView notifCount, clearAll;
    private TextView mediaTitle, mediaArtist, mediaApp;
    private IconButton bPlay, bHeart;
    private MediaController session;
    private final MediaController.Callback mediaCb = new MediaController.Callback() {
        @Override public void onMetadataChanged(android.media.MediaMetadata m) { refreshMedia(); }
        @Override public void onPlaybackStateChanged(android.media.session.PlaybackState s) { refreshMedia(); }
    };

    private long lastSliderWrite;
    private boolean closing;
    private float pendingDrag = -1;

    public PanelView(Context c, Host host) {
        super(c);
        this.host = host;
        setBackgroundColor(Ui.SCRIM);
        setOnClickListener(v -> host.requestHide());
        setFocusableInTouchMode(true); // so the BACK key reaches us

        int screenW = c.getResources().getDisplayMetrics().widthPixels;
        int sheetW = Math.min(screenW, Ui.dp(c, 660));

        // the sheet scrolls if it ever outgrows the screen
        ScrollView scroller = new ScrollView(c);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.setClickable(true); // keep taps off the scrim
        LayoutParams slp = new LayoutParams(sheetW, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addView(scroller, slp);

        // the scroller's one child hosts the main sheet and, when open,
        // a picker page (Wi-Fi networks / Bluetooth devices) in its place
        pageHost = new FrameLayout(c);
        scroller.addView(pageHost, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        sheet = new LinearLayout(c);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setBackgroundColor(Ui.PAPER);
        pageHost.addView(sheet, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        buildHeader(c);
        sheet.addView(Ui.hr(c));
        buildSliders(c);
        sheet.addView(Ui.hr(c));
        buildTiles(c);
        buildMedia(c);
        sheet.addView(Ui.hr(c));
        buildNotifications(c);
        sheet.addView(Ui.hr(c));
        buildFooter(c);

        View bottomRule = new View(c);
        bottomRule.setBackgroundColor(Ui.INK);
        sheet.addView(bottomRule, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 4)));

        this.scroller = scroller;
    }

    private ScrollView scroller;

    // ------------------------------------------------------------- sections

    private LinearLayout pad(Context c, int orientation) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(orientation);
        int p = Ui.dp(c, 20);
        l.setPadding(p, Ui.dp(c, 14), p, Ui.dp(c, 14));
        return l;
    }

    private void buildHeader(Context c) {
        LinearLayout col = pad(c, LinearLayout.VERTICAL);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        clock = Ui.text(c, 40, Ui.INK, true, true);
        row.addView(clock);
        View spacer = new View(c);
        row.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        battery = Ui.text(c, 17, Ui.INK, true, false);
        row.addView(battery);
        col.addView(row);

        dateLine = Ui.text(c, 15, Ui.MID, true, false);
        dateLine.setPadding(0, Ui.dp(c, 2), 0, 0);
        col.addView(dateLine);
        sheet.addView(col);
    }

    private void buildSliders(Context c) {
        LinearLayout col = pad(c, LinearLayout.VERTICAL);
        brightness = new InkSlider(c, InkSlider.EndGlyphs.BRIGHTNESS);
        brightness.setListener((v, fromUser) -> {
            if (fromUser && throttleOk()) Brightness.set(c, v);
        });
        // the legend above the thumb ("pure reflective / paper-like /
        // screen-like") wants a little headroom — hence the taller row
        brightness.setLabeler(v -> Brightness.zoneLabel(c, v));
        col.addView(brightness, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 72)));

        // slider runs amber→white left-to-right, matching the stock shade
        // (and "right = more light" on both sliders), so position = 1 − warmth
        warmth = new InkSlider(c, InkSlider.EndGlyphs.WARMTH);
        warmth.setListener((v, fromUser) -> {
            if (fromUser && throttleOk()) Warmth.set(c, 1f - v);
        });
        col.addView(warmth, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 60)));
        sheet.addView(col);
    }

    private boolean throttleOk() {
        long now = SystemClock.uptimeMillis();
        if (now - lastSliderWrite < 40) return false;
        lastSliderWrite = now;
        return true;
    }

    private void buildTiles(Context c) {
        LinearLayout col = pad(c, LinearLayout.VERTICAL);
        // wifi/bt: tap = direct flip when blessed, else our picker page
        // (or the system surface when pickers are switched off);
        // long-press = always go deeper
        tWifi = new TileButton(c, "Wi-Fi", () -> {
            if (WifiNets.toggleDirect(c)) postDelayed(this::refreshTiles, 300);
            else openWifiSurface();
        }, this::openWifiSurface);
        tBt = new TileButton(c, "Bluetooth", () -> {
            if (BtDevices.toggleDirect(c)) postDelayed(this::refreshTiles, 400);
            else openBtSurface();
        }, this::openBtSurface);
        // pill names are Apple's, on purpose: our customers arrive from
        // iPhone/iPad, and these concepts aren't new — only the light is.
        // (Founder ruling 2026-07-12: familiar where familiar, novel where
        // novel — see shade/decisions/2026-07-12-qs-redesign-ideation.)
        tAir = tile(c, "Airplane Mode", () -> Toggles.airplaneToggle(c), Settings.ACTION_AIRPLANE_MODE_SETTINGS);
        tDnd = tile(c, "Do Not Disturb", () -> Toggles.dndToggle(c), Settings.ACTION_SOUND_SETTINGS);
        tDark = tile(c, "Dark Mode", () -> Toggles.darkToggle(c), Settings.ACTION_DISPLAY_SETTINGS);
        tRot = tile(c, "Rotation Lock", () -> Toggles.rotationToggle(c), Settings.ACTION_DISPLAY_SETTINGS);
        col.addView(tileRow(c, tWifi, tBt, tAir));
        View gap = new View(c);
        col.addView(gap, new LinearLayout.LayoutParams(1, Ui.dp(c, 10)));
        col.addView(tileRow(c, tDnd, tDark, tRot));
        sheet.addView(col);
    }

    private interface ToggleAction { boolean run(); }

    private TileButton tile(Context c, String name, ToggleAction action, String settingsAction) {
        final TileButton[] ref = new TileButton[1];
        TileButton t = new TileButton(c, name,
                () -> {
                    boolean direct = action.run();
                    if (!direct) { host.requestHide(); return; }
                    ref[0].postDelayed(this::refreshTiles, 300);
                },
                () -> {
                    try {
                        getContext().startActivity(new Intent(settingsAction)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Throwable ignored) {}
                    host.requestHide();
                });
        ref[0] = t;
        return t;
    }

    private LinearLayout tileRow(Context c, TileButton a, TileButton b, TileButton d) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int gap = Ui.dp(c, 10);
        LinearLayout.LayoutParams lp1 = new LinearLayout.LayoutParams(0, Ui.dp(c, 72), 1f);
        LinearLayout.LayoutParams lp2 = new LinearLayout.LayoutParams(0, Ui.dp(c, 72), 1f);
        LinearLayout.LayoutParams lp3 = new LinearLayout.LayoutParams(0, Ui.dp(c, 72), 1f);
        lp2.leftMargin = gap; lp3.leftMargin = gap;
        row.addView(a, lp1); row.addView(b, lp2); row.addView(d, lp3);
        return row;
    }

    private void buildMedia(Context c) {
        LinearLayout wrap = pad(c, LinearLayout.VERTICAL);
        wrap.setPadding(Ui.dp(c, 20), 0, Ui.dp(c, 20), Ui.dp(c, 14));
        mediaCard = new LinearLayout(c);
        mediaCard.setOrientation(LinearLayout.VERTICAL);
        mediaCard.setBackground(Ui.box(c, 3, Ui.PAPER, Ui.INK));
        int p = Ui.dp(c, 14);
        mediaCard.setPadding(p, p, p, p);

        mediaApp = Ui.text(c, 12.5f, Ui.FAINT, false, false);
        mediaApp.setLetterSpacing(0.09f);
        mediaApp.setAllCaps(true);
        mediaCard.addView(mediaApp);
        mediaTitle = Ui.text(c, 18, Ui.INK, true, true);
        mediaTitle.setSingleLine(); mediaTitle.setEllipsize(TextUtils.TruncateAt.END);
        mediaTitle.setPadding(0, Ui.dp(c, 3), 0, 0);
        mediaCard.addView(mediaTitle);
        mediaArtist = Ui.text(c, 15, Ui.MID, true, false);
        mediaArtist.setSingleLine(); mediaArtist.setEllipsize(TextUtils.TruncateAt.END);
        mediaCard.addView(mediaArtist);

        LinearLayout controls = new LinearLayout(c);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_HORIZONTAL);
        controls.setPadding(0, Ui.dp(c, 12), 0, 0);
        int sz = Ui.dp(c, 52), gap = Ui.dp(c, 8);
        addControl(controls, new IconButton(c, IconButton.Glyph.BACK15,
                () -> Media.seekBy(session, -15)), sz, gap);
        addControl(controls, new IconButton(c, IconButton.Glyph.PREV,
                () -> Media.prev(session)), sz, gap);
        bPlay = addControl(controls, new IconButton(c, IconButton.Glyph.PLAY,
                () -> { Media.playPause(session); postDelayed(this::refreshMedia, 250); }), sz, gap);
        addControl(controls, new IconButton(c, IconButton.Glyph.NEXT,
                () -> Media.next(session)), sz, gap);
        addControl(controls, new IconButton(c, IconButton.Glyph.FWD15,
                () -> Media.seekBy(session, 15)), sz, gap);
        bHeart = addControl(controls, new IconButton(c, IconButton.Glyph.HEART_OUTLINE,
                () -> {
                    // standard rating API when the app implements it;
                    // otherwise its like/favorite custom action (Spotify)
                    if (Media.supportsHeart(session)) {
                        Media.toggleHeart(session);
                    } else {
                        Media.sendCustom(session, Media.likeAction(session));
                    }
                    postDelayed(this::refreshMedia, 300);
                }), sz, gap);
        mediaCard.addView(controls);

        // the app's own extras (shuffle, repeat, sleep timer…), re-inked
        extraControls = new LinearLayout(c);
        extraControls.setOrientation(LinearLayout.HORIZONTAL);
        extraControls.setGravity(Gravity.CENTER_HORIZONTAL);
        extraControls.setPadding(0, Ui.dp(c, 10), 0, 0);
        extraControls.setVisibility(GONE);
        mediaCard.addView(extraControls);

        wrap.addView(mediaCard, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        mediaCard.setVisibility(GONE);
        sheet.addView(wrap);
    }

    private IconButton addControl(LinearLayout row, IconButton b, int sz, int gap) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(sz, sz);
        if (row.getChildCount() > 0) lp.leftMargin = gap;
        row.addView(b, lp);
        return b;
    }

    private void buildNotifications(Context c) {
        LinearLayout col = pad(c, LinearLayout.VERTICAL);
        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        notifCount = Ui.text(c, 13, Ui.MID, false, false);
        notifCount.setLetterSpacing(0.09f);
        notifCount.setAllCaps(true);
        head.addView(notifCount);
        View spacer = new View(c);
        head.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        clearAll = Ui.text(c, 14, Ui.INK, true, false);
        clearAll.setText("clear all");
        clearAll.setPadding(Ui.dp(c, 12), Ui.dp(c, 8), 0, Ui.dp(c, 8));
        clearAll.setClickable(true);
        clearAll.setOnClickListener(v -> {
            ShadeNLService.dismissAll();
            postDelayed(this::refreshNotifications, 200);
        });
        head.addView(clearAll);
        col.addView(head);

        notifList = new LinearLayout(c);
        notifList.setOrientation(LinearLayout.VERTICAL);
        col.addView(notifList, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        sheet.addView(col);
    }

    private void buildFooter(Context c) {
        LinearLayout row = pad(c, LinearLayout.HORIZONTAL);
        TextView settings = Ui.button(c, "all settings", () -> {
            try {
                getContext().startActivity(new Intent(Settings.ACTION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Throwable ignored) {}
            host.requestHide();
        });
        TextView setup = Ui.button(c, "shade setup", host::openShadeSetup);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, Ui.dp(c, 52), 1f);
        l2.leftMargin = Ui.dp(c, 10);
        row.addView(settings, l1);
        row.addView(setup, l2);
        sheet.addView(row);
    }

    // ------------------------------------------------------------ pickers

    private void openWifiSurface() {
        Context c = getContext();
        if (Prefs.pickers(c)) {
            showPicker(new WifiPickerView(c, () -> {
                Toggles.openWifiSheet(c);
                host.requestHide();
            }));
        } else {
            Toggles.openWifiSheet(c);
            host.requestHide();
        }
    }

    private void openBtSurface() {
        Context c = getContext();
        if (Prefs.pickers(c)) {
            showPicker(new BtPickerView(c, () -> {
                Toggles.openBtSettings(c);
                host.requestHide();
            }));
        } else {
            Toggles.openBtSettings(c);
            host.requestHide();
        }
    }

    private void showPicker(PickerPage p) {
        Context c = getContext();
        closePicker();
        picker = p;
        pickerWrap = new LinearLayout(c);
        pickerWrap.setOrientation(LinearLayout.VERTICAL);
        pickerWrap.setBackgroundColor(Ui.PAPER);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        int hp = Ui.dp(c, 14);
        head.setPadding(hp, hp, hp, hp);
        IconButton back = new IconButton(c, IconButton.Glyph.BACK, this::closePicker);
        head.addView(back, new LinearLayout.LayoutParams(Ui.dp(c, 48), Ui.dp(c, 48)));
        TextView title = Ui.text(c, 20, Ui.INK, true, true);
        title.setText(p.title());
        title.setPadding(Ui.dp(c, 14), 0, 0, 0);
        head.addView(title);
        pickerWrap.addView(head);
        pickerWrap.addView(Ui.hr(c));
        pickerWrap.addView(p, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        View bottomRule = new View(c);
        bottomRule.setBackgroundColor(Ui.INK);
        pickerWrap.addView(bottomRule, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 4)));

        sheet.setVisibility(GONE);
        pageHost.addView(pickerWrap, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        p.start();
    }

    private void closePicker() {
        if (picker == null) return;
        try { picker.stop(); } catch (Throwable ignored) {}
        if (pickerWrap != null) pageHost.removeView(pickerWrap);
        picker = null;
        pickerWrap = null;
        sheet.setVisibility(VISIBLE);
        refreshTiles();
    }

    // ------------------------------------------------------------ refresh

    public void refreshAll() {
        Context c = getContext();
        clock.setText(DateFormat.getTimeFormat(c).format(new Date()));
        String alarm = Toggles.nextAlarmText(c);
        dateLine.setText(DateFormat.format("EEEE, MMMM d", new Date())
                + (alarm.isEmpty() ? "" : "   ·   " + alarm));
        int pct = Toggles.batteryPercent(c);
        battery.setText(pct < 0 ? ""
                : pct + "%" + (Toggles.batteryCharging(c) ? " charging" : ""));

        brightness.setSliderEnabled(Brightness.available(c),
                "brightness needs the \"modify settings\" grant — see shade setup");
        if (Brightness.available(c)) brightness.setValue(Brightness.get(c));
        warmth.setSliderEnabled(Warmth.available(c),
                "the amber backlight unlocks with the Sol:OS blessing");
        if (Warmth.available(c)) warmth.setValue(1f - Warmth.get(c));

        refreshTiles();
        refreshMedia();
        refreshNotifications();
    }

    public void refreshTiles() {
        Context c = getContext();
        // stock-QS parity: SSID under Wi-Fi (arrives with the blessing —
        // Android redacts it for sideloads), and the connected Bluetooth
        // device's name as the pill title (one device) or a count (more)
        boolean wifi = Toggles.wifiOn(c);
        String ssid = WifiNets.currentSsid(c);
        tWifi.setState(wifi, !wifi ? "off" : ssid.isEmpty() ? "on" : ssid, true);

        boolean bt = Toggles.btOn(c);
        List<android.bluetooth.BluetoothDevice> btDevs =
                bt ? BtDevices.connected(c) : java.util.Collections.emptyList();
        tBt.setTitle(btDevs.size() == 1 ? BtDevices.name(btDevs.get(0)) : "Bluetooth");
        tBt.setState(bt, !bt ? "off"
                : btDevs.isEmpty() ? "on"
                : btDevs.size() == 1 ? "connected"
                : btDevs.size() + " devices", true);
        tAir.setState(Toggles.airplaneOn(c), Toggles.airplaneOn(c) ? "on" : "off", true);
        tDnd.setState(Toggles.dndOn(c), Toggles.dndOn(c) ? "on" : "off", Caps.dnd(c));
        tDark.setState(Toggles.darkOn(c), Toggles.darkOn(c) ? "on" : "off", true);
        // Apple's frame: the LOCK is the feature — on when the page holds still
        tRot.setState(Toggles.rotationLocked(c), Toggles.rotationLocked(c) ? "on" : "off",
                Caps.writeSettings(c));
    }

    public void refreshMedia() {
        Context c = getContext();
        MediaController fresh = Media.current(c);
        if (session != null && (fresh == null
                || !session.getPackageName().equals(fresh.getPackageName()))) {
            try { session.unregisterCallback(mediaCb); } catch (Throwable ignored) {}
            session = null;
        }
        if (fresh == null) { mediaCard.setVisibility(GONE); return; }
        if (session == null) {
            session = fresh;
            try { session.registerCallback(mediaCb); } catch (Throwable ignored) {}
        }
        mediaCard.setVisibility(VISIBLE);
        String t = Media.title(session);
        mediaTitle.setText(t.isEmpty() ? "now playing" : t);
        mediaArtist.setText(Media.artist(session));
        mediaArtist.setVisibility(Media.artist(session).isEmpty() ? GONE : VISIBLE);
        mediaApp.setText(Media.appLabel(c, session));
        bPlay.setGlyph(Media.isPlaying(session)
                ? IconButton.Glyph.PAUSE : IconButton.Glyph.PLAY);

        // heart: rating API where implemented, like custom action elsewhere
        android.media.session.PlaybackState.CustomAction like = Media.likeAction(session);
        boolean ratingHeart = Media.supportsHeart(session);
        bHeart.setVisibility(ratingHeart || like != null ? VISIBLE : GONE);
        boolean filled = ratingHeart ? Media.hearted(session) : Media.likeShowsLiked(like);
        bHeart.setGlyph(filled ? IconButton.Glyph.HEART : IconButton.Glyph.HEART_OUTLINE);

        // second row: the app's remaining custom actions, tinted our ink
        extraControls.removeAllViews();
        for (android.media.session.PlaybackState.CustomAction a : Media.extraActions(session)) {
            android.graphics.drawable.Drawable icon = Media.customIcon(c, session, a, Ui.INK);
            if (icon == null) continue; // no icon, no button — calm over complete
            android.widget.ImageView btn = new android.widget.ImageView(c);
            btn.setImageDrawable(icon);
            int pad = Ui.dp(c, 10);
            btn.setPadding(pad, pad, pad, pad);
            btn.setBackground(Ui.box(c, 2, Ui.PAPER, Ui.INK));
            btn.setContentDescription(a.getName());
            btn.setTooltipText(a.getName());
            btn.setClickable(true);
            btn.setOnClickListener(v -> {
                Media.sendCustom(session, a);
                postDelayed(this::refreshMedia, 300);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    Ui.dp(c, 44), Ui.dp(c, 44));
            if (extraControls.getChildCount() > 0) lp.leftMargin = Ui.dp(c, 8);
            extraControls.addView(btn, lp);
        }
        extraControls.setVisibility(extraControls.getChildCount() > 0 ? VISIBLE : GONE);
    }

    public void refreshNotifications() {
        Context c = getContext();
        notifList.removeAllViews();
        if (!Caps.notifListener(c)) {
            notifCount.setText("notifications");
            clearAll.setVisibility(GONE);
            TextView grant = Ui.button(c, "turn on notification access", () -> {
                try {
                    c.startActivity(new Intent(
                            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (Throwable ignored) {}
                host.requestHide();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(c, 52));
            lp.topMargin = Ui.dp(c, 6);
            notifList.addView(grant, lp);
            return;
        }

        List<StatusBarNotification> all = ShadeNLService.current();
        int shown = 0, listed = 0; boolean anyClearable = false;
        for (StatusBarNotification sbn : all) {
            // the shade's own plumbing ("standing by") is not news
            if (c.getPackageName().equals(sbn.getPackageName())) continue;
            CharSequence title = sbn.getNotification().extras
                    .getCharSequence(Notification.EXTRA_TITLE);
            CharSequence text = sbn.getNotification().extras
                    .getCharSequence(Notification.EXTRA_TEXT);
            if (title == null && text == null) continue;
            listed++;
            if (sbn.isClearable()) anyClearable = true;
            if (shown >= 6) continue; // keep counting, stop adding rows
            notifList.addView(notifRow(c, sbn, title, text));
            shown++;
        }
        notifCount.setText(listed == 0 ? "all quiet"
                : "notifications · " + listed);
        clearAll.setVisibility(anyClearable ? VISIBLE : GONE);
    }

    private View notifRow(Context c, StatusBarNotification sbn,
                          CharSequence title, CharSequence text) {
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, Ui.dp(c, 8), 0, Ui.dp(c, 8));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            PendingIntent pi = sbn.getNotification().contentIntent;
            if (pi != null) { try { pi.send(); } catch (Throwable ignored) {} }
            host.requestHide();
        });

        View bar = new View(c);
        bar.setBackgroundColor(Ui.INK);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                Ui.dp(c, 4), Ui.dp(c, 36));
        row.addView(bar, barLp);

        LinearLayout body = new LinearLayout(c);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(c, 12), 0, Ui.dp(c, 8), 0);
        TextView tt = Ui.text(c, 15, Ui.INK, true, true);
        tt.setSingleLine(); tt.setEllipsize(TextUtils.TruncateAt.END);
        tt.setText(title != null ? title : text);
        body.addView(tt);
        if (title != null && text != null) {
            TextView tx = Ui.text(c, 14, Ui.MID, true, false);
            tx.setSingleLine(); tx.setEllipsize(TextUtils.TruncateAt.END);
            tx.setText(text);
            body.addView(tx);
        }
        row.addView(body, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        if (sbn.isClearable()) {
            IconButton x = new IconButton(c, IconButton.Glyph.X, () -> {
                ShadeNLService.dismiss(sbn.getKey());
                postDelayed(this::refreshNotifications, 150);
            });
            row.addView(x, new LinearLayout.LayoutParams(Ui.dp(c, 44), Ui.dp(c, 44)));
        }
        return row;
    }

    // ------------------------------------------------------- open / close

    /** Drag from the gesture strip: dy is finger travel in px. */
    public void setDragOffset(float dy) {
        pendingDrag = dy;
        int h = scroller.getHeight();
        if (h == 0) { scroller.setTranslationY(-Ui.dp(getContext(), 800)); return; }
        scroller.setTranslationY(Math.min(0, -h + dy));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        // the first drag can arrive before the sheet is measured — re-apply
        if (pendingDrag >= 0 && scroller.getHeight() > 0) {
            scroller.setTranslationY(Math.min(0, -scroller.getHeight() + pendingDrag));
        }
        requestFocus();
    }

    public void open(boolean animate) {
        pendingDrag = -1;
        int h = scroller.getHeight();
        if (h == 0) { scroller.setTranslationY(-2000); post(() -> open(animate)); return; }
        if (!animate) { scroller.setTranslationY(0); return; }
        scroller.animate().translationY(0).setDuration(130)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Settle after a drag: open fully or close, by position + velocity. */
    public void settle(float velocityY) {
        int h = Math.max(1, scroller.getHeight());
        float shown = h + scroller.getTranslationY();
        boolean shouldOpen = velocityY > 400 || (velocityY > -400 && shown > h * 0.4f);
        if (shouldOpen) open(true); else close();
    }

    public void close() {
        if (closing) return;
        closing = true;
        pendingDrag = -1;
        int h = Math.max(1, scroller.getHeight());
        scroller.animate().translationY(-h).setDuration(110)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> { detachSession(); host.onFullyClosed(); })
                .start();
    }

    public void detachSession() {
        if (session != null) {
            try { session.unregisterCallback(mediaCb); } catch (Throwable ignored) {}
            session = null;
        }
        if (picker != null) {
            try { picker.stop(); } catch (Throwable ignored) {}
            picker = null;
        }
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getAction() == KeyEvent.ACTION_UP) {
                if (picker != null) closePicker(); else host.requestHide();
            }
            return true;
        }
        return super.dispatchKeyEvent(e);
    }
}
