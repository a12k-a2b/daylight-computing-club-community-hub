package com.daylightcomputer.shade;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.media.session.MediaSessionManager;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.WindowManager;

import com.daylightcomputer.shade.control.Caps;
import com.daylightcomputer.shade.control.SysApi;
import com.daylightcomputer.shade.ui.PanelView;
import com.daylightcomputer.shade.ui.Ui;

/** Owns the two overlay windows: an invisible catch strip along the top
 *  edge, and the panel itself. Idle cost is one dormant foreground service —
 *  no timers, no polling; receivers are only registered while the panel is
 *  actually open. */
public class ShadeService extends Service {
    private static final String TAG = "ShadeService";
    public static final String ACTION_SHOW = "com.daylightcomputer.shade.SHOW";
    public static final String ACTION_HIDE = "com.daylightcomputer.shade.HIDE";
    public static final String ACTION_APPLY = "com.daylightcomputer.shade.APPLY";

    // hidden window types, newest-first fallback chain (see README §portability)
    private static final int TYPE_STATUS_BAR_SUB_PANEL = 2017;
    private static final int TYPE_SECURE_SYSTEM_OVERLAY = 2015;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View strip;
    private PanelView panel;
    private boolean stockShadeBlocked;
    private BroadcastReceiver panelReceiver;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        NotificationChannel ch = new NotificationChannel("shade",
                getString(R.string.fgs_channel_name), NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
        Notification n = new Notification.Builder(this, "shade")
                .setSmallIcon(R.drawable.ic_tile)
                .setContentTitle(getString(R.string.fgs_notif_title))
                .setContentText(getString(R.string.fgs_notif_text))
                .build();
        startForeground(1, n);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? ACTION_APPLY : intent.getAction();
        if (strip == null) applyConfig(); // first start via SHOW still arms the strip
        else if (ACTION_APPLY.equals(a)) applyConfig();
        if (ACTION_SHOW.equals(a)) showPanel(false);
        else if (ACTION_HIDE.equals(a)) requestHide();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        removeStrip();
        if (panel != null) { try { wm.removeViewImmediate(panel); } catch (Throwable ignored) {} panel = null; }
        teardownPanelSignals();
        if (stockShadeBlocked) SysApi.setStockShadeBlocked(this, false);
        super.onDestroy();
    }

    // -------------------------------------------------------------- config

    /** Re-reads prefs: gesture strip placement + stock-shade takeover. */
    private void applyConfig() {
        removeStrip();
        String mode = Prefs.stripMode(this);
        if (!Prefs.STRIP_OFF.equals(mode) && Caps.overlay(this)) {
            boolean over = Prefs.STRIP_OVER.equals(mode);
            strip = new StripView(this);
            if (over && Caps.internalWindow(this)) {
                if (!addStrip(strip, TYPE_STATUS_BAR_SUB_PANEL, 0)
                        && !addStrip(strip, TYPE_SECURE_SYSTEM_OVERLAY, 0)) {
                    addStrip(strip, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                            statusBarHeight());
                }
            } else {
                addStrip(strip, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        over ? 0 : statusBarHeight());
            }
        }

        boolean wantBlock = Prefs.takeOver(this) && Caps.statusBar(this);
        if (wantBlock != stockShadeBlocked) {
            if (SysApi.setStockShadeBlocked(this, wantBlock)) stockShadeBlocked = wantBlock;
        }
    }

    private boolean addStrip(View v, int type, int yOffset) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                Math.max(statusBarHeight(), Ui.dp(this, 24)),
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.y = yOffset;
        lp.setFitInsetsTypes(0);
        try {
            wm.addView(v, lp);
            Log.i(TAG, "strip attached, window type " + type + " y=" + yOffset);
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "strip type " + type + " rejected: " + t);
            return false;
        }
    }

    private void removeStrip() {
        if (strip != null) { try { wm.removeViewImmediate(strip); } catch (Throwable ignored) {} strip = null; }
    }

    private int statusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        return id > 0 ? getResources().getDimensionPixelSize(id) : Ui.dp(this, 28);
    }

    // --------------------------------------------------------------- panel

    private final PanelView.Host host = new PanelView.Host() {
        @Override public void requestHide() { ShadeService.this.requestHide(); }
        @Override public void onFullyClosed() { ShadeService.this.removePanel(); }
        @Override public void openShadeSetup() {
            startActivity(new Intent(ShadeService.this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            ShadeService.this.requestHide();
        }
    };

    private void showPanel(boolean fromDrag) {
        if (panel != null) { if (!fromDrag) panel.open(true); return; }
        if (!Caps.overlay(this)) {
            startActivity(new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        panel = new PanelView(this, host);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.setFitInsetsTypes(0);
        try {
            wm.addView(panel, lp);
        } catch (Throwable t) {
            Log.w(TAG, "panel window rejected: " + t);
            panel = null;
            return;
        }
        panel.refreshAll();
        setupPanelSignals();
        if (!fromDrag) panel.open(true);
    }

    private void requestHide() {
        if (panel != null) panel.close();
    }

    private void removePanel() {
        teardownPanelSignals();
        if (panel != null) {
            panel.detachSession();
            try { wm.removeViewImmediate(panel); } catch (Throwable ignored) {}
            panel = null;
        }
    }

    /** Live updates while (and only while) the panel is open. */
    private void setupPanelSignals() {
        if (panelReceiver == null) {
            panelReceiver = new BroadcastReceiver() {
                @Override public void onReceive(Context c, Intent i) {
                    if (panel == null) return;
                    if (Intent.ACTION_TIME_TICK.equals(i.getAction())) panel.refreshAll();
                    else panel.refreshTiles();
                }
            };
            IntentFilter f = new IntentFilter();
            f.addAction(Intent.ACTION_TIME_TICK);
            f.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
            f.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
            f.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            f.addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED);
            f.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
            registerReceiver(panelReceiver, f);
        }
        ShadeNLService.setWatcher(() -> main.post(() -> {
            if (panel != null) { panel.refreshNotifications(); panel.refreshMedia(); }
        }));
        if (Caps.notifListener(this) && sessionsListener == null) {
            try {
                sessionsListener = list -> { if (panel != null) panel.refreshMedia(); };
                getSystemService(MediaSessionManager.class).addOnActiveSessionsChangedListener(
                        sessionsListener, new ComponentName(this, ShadeNLService.class));
            } catch (Throwable t) {
                sessionsListener = null;
            }
        }
    }

    private void teardownPanelSignals() {
        if (panelReceiver != null) {
            try { unregisterReceiver(panelReceiver); } catch (Throwable ignored) {}
            panelReceiver = null;
        }
        ShadeNLService.setWatcher(null);
        if (sessionsListener != null) {
            try {
                getSystemService(MediaSessionManager.class)
                        .removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Throwable ignored) {}
            sessionsListener = null;
        }
    }

    // ------------------------------------------------------- gesture strip

    /** Invisible band along the top edge. A downward drag pulls the panel
     *  with the finger, exactly like the stock shade. */
    private class StripView extends View {
        private float downRawY;
        private boolean dragging;
        private VelocityTracker vt;

        StripView(Context c) { super(c); }

        @Override public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (getSystemService(KeyguardManager.class).isKeyguardLocked()) return false;
                    downRawY = e.getRawY();
                    dragging = false;
                    vt = VelocityTracker.obtain();
                    vt.addMovement(e);
                    return true;
                case MotionEvent.ACTION_MOVE: {
                    if (vt != null) vt.addMovement(e);
                    float dy = e.getRawY() - downRawY;
                    if (!dragging && dy > Ui.dp(getContext(), 10)) {
                        dragging = true;
                        showPanel(true);
                    }
                    if (dragging && panel != null) panel.setDragOffset(dy);
                    return true;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging && panel != null && vt != null) {
                        vt.computeCurrentVelocity(1000);
                        panel.settle(vt.getYVelocity());
                    }
                    if (vt != null) { vt.recycle(); vt = null; }
                    dragging = false;
                    return true;
            }
            return super.onTouchEvent(e);
        }
    }
}
