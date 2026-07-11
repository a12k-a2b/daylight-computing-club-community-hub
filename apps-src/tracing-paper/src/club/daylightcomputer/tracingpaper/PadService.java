package club.daylightcomputer.tracingpaper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.function.Consumer;

/**
 * The keeper of the glass. Watches the DC-1's hardware buttons (top button by
 * default — re-learnable), lays the pad over whatever you're doing, snaps
 * screenshots, and flips the backlight if you ask it to.
 *
 * Button events arrive two ways on a DC-1: as filtered key events (the top
 * button is KEYCODE_F12) and as SolOS's own broadcasts. We listen to both and
 * debounce, so the pad works whichever path a given SolOS build takes.
 */
public class PadService extends AccessibilityService {

    private static final String SOLOS_SINGLE = "com.daylightcomputer.solosserver.ACTION_BUTTON_SINGLE_PRESS";
    private static final String SOLOS_LONG = "com.daylightcomputer.solosserver.ACTION_BUTTON_LONG_PRESS";
    private static final long LONG_PRESS_MS = 500;
    private static final long DEBOUNCE_MS = 600;

    static volatile PadService instance;

    private WindowManager wm;
    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private FrameLayout root;
    private GlassPadView pad;
    private LinearLayout barShell;      // bottom toolbar (border + scroll row)
    private TextView chip;              // "back to writing" pill shown while peeking
    private WindowManager.LayoutParams rootLp;

    private boolean shown, peeking, longFired;
    private long lastKeyHandled;

    private TextView pageBtn, penBtn, eraseBtn, undoBtn, redoBtn, frostBtn, peekBtn;

    private final Runnable longPress = () -> {
        longFired = true;
        if (shown) screenshot();
        else shortPress();
    };

    private final BroadcastReceiver solosButtons = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (SystemClock.uptimeMillis() - lastKeyHandled < DEBOUNCE_MS) return;
            String a = i.getAction();
            if (SOLOS_SINGLE.equals(a)) shortPress();
            else if (SOLOS_LONG.equals(a)) { if (shown) screenshot(); else shortPress(); }
        }
    };

    // ------------------------------------------------------------- lifecycle

    @Override
    protected void onServiceConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        prefs = Prefs.get(this);

        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(SOLOS_SINGLE);
        f.addAction(SOLOS_LONG);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(solosButtons, f, Context.RECEIVER_EXPORTED);
        else registerReceiver(solosButtons, f);
    }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(solosButtons); } catch (Exception ignored) {}
        removeEverything();
        instance = null;
        super.onDestroy();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}

    // ----------------------------------------------------------------- keys

    @Override
    protected boolean onKeyEvent(KeyEvent e) {
        long until = prefs.getLong(Prefs.K_CALIBRATE_UNTIL, 0);
        if (until > 0) {
            if (System.currentTimeMillis() > until) {
                prefs.edit().putLong(Prefs.K_CALIBRATE_UNTIL, 0).apply();
            } else {
                if (e.getAction() == KeyEvent.ACTION_DOWN) {
                    prefs.edit().putInt(Prefs.K_TOGGLE, e.getKeyCode())
                            .putLong(Prefs.K_CALIBRATE_UNTIL, 0).apply();
                    sendBroadcast(new Intent(Prefs.CALIBRATED_ACTION).setPackage(getPackageName()));
                    toast("Pad button set to " + keyName(e.getKeyCode()));
                }
                return true;
            }
        }

        int code = e.getKeyCode();
        if (code == prefs.getInt(Prefs.K_TOGGLE, Prefs.DEFAULT_TOGGLE)) {
            lastKeyHandled = SystemClock.uptimeMillis();
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                if (e.getRepeatCount() == 0) {
                    longFired = false;
                    handler.postDelayed(longPress, LONG_PRESS_MS);
                }
            } else if (e.getAction() == KeyEvent.ACTION_UP) {
                handler.removeCallbacks(longPress);
                if (!longFired) shortPress();
            }
            return true;
        }

        if (code == KeyEvent.KEYCODE_VOLUME_DOWN && shown && !peeking
                && prefs.getBoolean(Prefs.K_SIDE_BACKLIGHT, false)) {
            lastKeyHandled = SystemClock.uptimeMillis();
            if (e.getAction() == KeyEvent.ACTION_UP) toggleBacklight();
            return true;
        }
        return false;
    }

    static String keyName(int code) {
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }

    // --------------------------------------------------------- pad states

    /** hidden -> open; open -> hidden; peeking -> back to writing. */
    private void shortPress() {
        handler.post(() -> {
            if (!shown) showPad();
            else if (peeking) exitPeek();
            else hidePad();
        });
    }

    static boolean togglePad(Context unused) {
        PadService s = instance;
        if (s == null) return false;
        s.shortPress();
        return true;
    }

    static boolean isShown() {
        PadService s = instance;
        return s != null && s.shown;
    }

    private void showPad() {
        if (shown) return;
        if (root == null) buildUi();
        pad.setPenOnly(prefs.getBoolean(Prefs.K_PEN_ONLY, false));
        rootLp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        try {
            wm.addView(root, rootLp);
        } catch (Exception e) {
            toast("Couldn't open the pad");
            return;
        }
        shown = true;
        peeking = false;
        refreshBar();
    }

    private void hidePad() {
        if (!shown) return;
        if (peeking) exitPeek();
        pad.flushSave();
        try { wm.removeView(root); } catch (Exception ignored) {}
        shown = false;
    }

    private void removeEverything() {
        if (chip != null) { try { wm.removeView(chip); } catch (Exception ignored) {} chip = null; }
        if (shown && root != null) {
            if (pad != null) pad.flushSave();
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        shown = false;
        peeking = false;
    }

    /** Glass stays visible but touches fall through to the app beneath. */
    private void peek() {
        if (!shown || peeking) return;
        peeking = true;
        rootLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        wm.updateViewLayout(root, rootLp);
        barShell.setAlpha(0.35f);

        chip = button("✍ WRITE", v -> exitPeek());
        chip.setTextSize(18);
        int pad12 = dp(12);
        chip.setPadding(pad12 * 2, pad12, pad12 * 2, pad12);
        WindowManager.LayoutParams clp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.x = dp(16);
        clp.y = dp(16);
        try { wm.addView(chip, clp); } catch (Exception ignored) {}
    }

    private void exitPeek() {
        if (!peeking) return;
        peeking = false;
        rootLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(root, rootLp); } catch (Exception ignored) {}
        barShell.setAlpha(1f);
        if (chip != null) { try { wm.removeView(chip); } catch (Exception ignored) {} chip = null; }
    }

    // ------------------------------------------------------------ screenshot

    private void screenshot() {
        if (!shown) return;
        handler.post(() -> {
            barShell.setVisibility(View.INVISIBLE);
            if (chip != null) chip.setVisibility(View.INVISIBLE);
            handler.postDelayed(this::reallyScreenshot, 180);
        });
    }

    private void restoreAfterShot() {
        barShell.setVisibility(View.VISIBLE);
        if (chip != null) chip.setVisibility(View.VISIBLE);
    }

    private void reallyScreenshot() {
        takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult res) {
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(
                                    res.getHardwareBuffer(), res.getColorSpace());
                            if (hw == null) throw new IllegalStateException("null bitmap");
                            Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                            res.getHardwareBuffer().close();
                            String name = Exporter.savePng(PadService.this, sw);
                            toast("Snapped — " + name + " in Pictures");
                        } catch (Exception ex) {
                            toast("Screenshot failed");
                        } finally {
                            restoreAfterShot();
                        }
                    }
                    @Override public void onFailure(int code) {
                        restoreAfterShot();
                        toast("Screenshot failed (" + code + ")");
                    }
                });
    }

    // ------------------------------------------------------------- backlight

    private void toggleBacklight() {
        if (!Settings.System.canWrite(this)) {
            toast("Allow “Modify system settings” in the Tracing Paper app first");
            Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:" + getPackageName()));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try { startActivity(i); } catch (Exception ignored) {}
            return;
        }
        try {
            int cur = Settings.System.getInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, 100);
            if (cur > 0) {
                int mode = Settings.System.getInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                prefs.edit().putInt(Prefs.K_LAST_BRIGHT, cur)
                        .putInt(Prefs.K_LAST_BRIGHT_MODE, mode).apply();
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS, 0);
            } else {
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS,
                        prefs.getInt(Prefs.K_LAST_BRIGHT, 96));
                Settings.System.putInt(getContentResolver(),
                        Settings.System.SCREEN_BRIGHTNESS_MODE,
                        prefs.getInt(Prefs.K_LAST_BRIGHT_MODE,
                                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL));
            }
        } catch (Exception e) {
            toast("Couldn't reach the backlight");
        }
    }

    // ------------------------------------------------------------------- ui

    private void buildUi() {
        root = new FrameLayout(this);
        pad = new GlassPadView(this);
        pad.setStateListener(this::refreshBar);
        root.addView(pad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        // toolbar: a 2px rule, then one scrollable row of big square buttons
        barShell = new LinearLayout(this);
        barShell.setOrientation(LinearLayout.VERTICAL);
        View rule = new View(this);
        rule.setBackgroundColor(Color.BLACK);
        barShell.addView(rule, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(2)));

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setBackgroundColor(Color.WHITE);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int p8 = dp(8);
        row.setPadding(p8, p8, p8, p8);
        scroll.addView(row);
        barShell.addView(scroll, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        penBtn = button("PEN", v -> { pad.setTool(GlassPadView.TOOL_PEN); });
        eraseBtn = button("ERASE", v -> { pad.setTool(GlassPadView.TOOL_ERASE); });
        undoBtn = button("UNDO", v -> pad.undo());
        redoBtn = button("REDO", v -> pad.redo());
        TextView clearBtn = button("CLEAR", v -> pad.clearPage());
        clearBtn.setOnLongClickListener(v -> { pad.deletePage(); return true; });
        TextView prevBtn = button("◀", v -> pad.prevPage());
        pageBtn = button("1/1", null);
        TextView nextBtn = button("▶", v -> pad.nextPage());
        TextView plusBtn = button("+PAGE", v -> pad.newPage());
        frostBtn = button("FROST", v -> {
            int f = pad.cycleFrost();
            frostBtn.setText(GlassPadView.FROST_NAMES[f]);
        });
        peekBtn = button("PEEK", v -> peek());
        TextView shotBtn = button("SNAP", v -> screenshot());
        TextView pdfBtn = button("PDF", v -> exportPdf());
        TextView hideBtn = button("✕ HIDE", v -> hidePad());

        TextView[] order = {hideBtn, penBtn, eraseBtn, undoBtn, redoBtn, clearBtn,
                prevBtn, pageBtn, nextBtn, plusBtn, frostBtn, peekBtn, shotBtn, pdfBtn};
        for (TextView b : order) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
            blp.setMarginEnd(p8);
            row.addView(b, blp);
        }

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(barShell, barLp);

        rootLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        refreshBar();
    }

    private void refreshBar() {
        if (pageBtn == null) return;
        pageBtn.setText(pad.pageLabel());
        boolean penSel = pad.getTool() == GlassPadView.TOOL_PEN;
        style(penBtn, penSel);
        style(eraseBtn, !penSel);
        undoBtn.setAlpha(pad.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(pad.canRedo() ? 1f : 0.35f);
        frostBtn.setText(GlassPadView.FROST_NAMES[pad.getFrost()]);
    }

    private void exportPdf() {
        toast("Making the PDF…");
        new Thread(() -> {
            try {
                pad.flushSave();
                float aspect = (float) Math.max(1, pad.getWidth())
                        / Math.max(1, pad.getHeight());
                String name = Exporter.exportPdf(this, pad.pages, aspect);
                handler.post(() -> toast("Saved " + name + " in Download"));
            } catch (Exception e) {
                handler.post(() -> toast("PDF export failed"));
            }
        }).start();
    }

    private TextView button(String label, Consumer<View> onTap) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextSize(16);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(48));
        t.setMinHeight(dp(48));
        int p = dp(10);
        t.setPadding(p, 0, p, 0);
        style(t, false);
        if (onTap != null) t.setOnClickListener(onTap::accept);
        return t;
    }

    private void style(TextView t, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(selected ? Color.BLACK : Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        t.setBackground(g);
        t.setTextColor(selected ? Color.WHITE : Color.BLACK);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        handler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }
}
