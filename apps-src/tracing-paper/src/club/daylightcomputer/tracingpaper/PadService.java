package club.daylightcomputer.tracingpaper;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.view.Display;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.function.Consumer;

/**
 * The keeper of the glass. Watches the DC-1's top button (re-learnable), lays
 * the pad over whatever you're doing, snips clippings onto it, and snaps
 * pictures of your notes over the page.
 *
 * Button events arrive two ways on a DC-1: as filtered key events (the top
 * button is KEYCODE_F12) and as SolOS's own broadcasts. We listen to both and
 * debounce, so the pad works whichever path a given SolOS build takes. The
 * volume keys are left alone on purpose — Daylight Keys owns those.
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
    private WetInk wet;
    private LinearLayout barShell;      // bottom toolbar (border + scroll row)
    private TextView chip;              // "back to writing" pill shown while peeking
    private WindowManager.LayoutParams rootLp;

    private boolean shown, peeking, longFired, snipMode;
    private long lastKeyHandled;

    private SnipVeil veil;
    private View panel;                 // notebooks / new-notebook card
    private View toolPopup;             // pen/hilite/eraser size card
    private View hintCard;              // one-time mental-model hint
    private int armedBookDelete = -1;
    private long armedBookAt;

    private TextView pageBtn, penBtn, hiBtn, eraseBtn, pickBtn, undoBtn, redoBtn, bookBtn;
    private SeekBar glassSeek;

    private final Runnable longPress = () -> {
        longFired = true;
        if (shown) screenshot();
        else shortPress();
    };

    /**
     * Exported so SolOS's button broadcasts can reach us — which also means any
     * local app could spoof one. So this path may only ever toggle the pad;
     * screenshots stay exclusive to the un-spoofable key path and the SNAP button.
     */
    private final BroadcastReceiver solosButtons = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (SystemClock.uptimeMillis() - lastKeyHandled < DEBOUNCE_MS) return;
            String a = i.getAction();
            if (SOLOS_SINGLE.equals(a)) shortPress();
            else if (SOLOS_LONG.equals(a)) { if (!shown) shortPress(); }
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

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // "Annotate the World" beta: keep the roll in step with the app beneath.
        // Only the scroll *amount* is used; window content is never read.
        if (event.getEventType() != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;
        if (!shown || peeking || snipMode || pad == null) return;
        if (!prefs.getBoolean(Prefs.K_BETA_FOLLOW, false)) return;
        CharSequence from = event.getPackageName();
        if (from == null || getPackageName().contentEquals(from)) return;
        final int dy = Build.VERSION.SDK_INT >= 28 ? event.getScrollDeltaY() : 0;
        if (dy != 0) handler.post(() -> { if (shown && pad != null) pad.followWorld(dy); });
    }

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

        if (e.getKeyCode() == prefs.getInt(Prefs.K_TOGGLE, Prefs.DEFAULT_TOGGLE)) {
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
        return false;
    }

    static String keyName(int code) {
        return KeyEvent.keyCodeToString(code).replace("KEYCODE_", "");
    }

    // --------------------------------------------------------- pad states

    /** hidden -> open; snipping -> back to the pad; peeking -> back to writing; open -> hidden. */
    private void shortPress() {
        handler.post(() -> {
            if (!shown) showPad();
            else if (snipMode) cancelSnip();
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
        pad.ensureBitmaps(); // rebuilt after the memory handed back on close
        pad.setPenOnly(prefs.getBoolean(Prefs.K_PEN_ONLY, false));
        pad.setBetaFlick(prefs.getBoolean(Prefs.K_BETA_FLICK, false));
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
        // the notebook slides down over the page — a layer, not a place-change.
        // 160ms on open only; closing stays instant so going back costs nothing.
        root.animate().cancel();
        root.setTranslationY(-getResources().getDisplayMetrics().heightPixels);
        root.animate().translationY(0).setDuration(160)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .start();
        maybeShowHint();
        refreshBar();
    }

    private static final String[][] WALKTHROUGH = {
            {"THIS IS TRACING PAPER", "A layer over your page — you never left it. Write with "
                    + "the pen (its other end erases); press the top button and you're right "
                    + "back where you were, place kept."},
            {"THE GLASS SLIDER", "It sets how much of the world shows through: 0% is clear "
                    + "glass for tracing, 100% is opaque paper. You're at 80% — enough paper "
                    + "to write on, enough world to remember where you are. Each notebook "
                    + "remembers its own setting."},
            {"REACH THROUGH", "PEEK lets you scroll the page beneath while your ink stays "
                    + "put. SNIP cuts a piece of it onto the glass — drag, resize, tilt it. "
                    + "And the Annotate the World betas in the app let flicks and taps pass "
                    + "through the glass entirely."}};

    private int hintStep;

    /** Once, the first time the glass appears: teach the mental model in three breaths. */
    private void maybeShowHint() {
        if (prefs.getInt(Prefs.K_HINTS, 0) != 0 || hintCard != null) return;
        hintStep = 0;
        showHintStep();
    }

    private void showHintStep() {
        if (hintCard != null) { root.removeView(hintCard); hintCard = null; }
        if (hintStep >= WALKTHROUGH.length) {
            prefs.edit().putInt(Prefs.K_HINTS, 1).apply();
            return;
        }
        LinearLayout card = card(WALKTHROUGH[hintStep][0]
                + "  ·  " + (hintStep + 1) + "/" + WALKTHROUGH.length);
        TextView t = new TextView(this);
        t.setText(WALKTHROUGH[hintStep][1]);
        t.setTextColor(Color.BLACK);
        t.setTextSize(16);
        card.addView(t, rowLp());
        card.addView(button(hintStep < WALKTHROUGH.length - 1 ? "NEXT →" : "GOT IT", v -> {
            hintStep++;
            showHintStep();
        }), rowLp());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(380), FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL | Gravity.TOP;
        lp.topMargin = dp(48);
        hintCard = card;
        root.addView(card, lp);
    }

    private void hidePad() {
        if (!shown) return;
        root.animate().cancel();
        root.setTranslationY(0);
        cancelSnip();
        closePanel();
        closeToolPopup();
        if (hintCard != null) { root.removeView(hintCard); hintCard = null; }
        if (peeking) exitPeek();
        pad.flushSave();
        maybeAutoBackup();
        try { wm.removeView(root); } catch (Exception ignored) {}
        shown = false;
        pad.releaseBitmaps(); // the pad away = its memory handed back
    }

    /** Once a day, on pad close: the whole library into Download, quietly. */
    private void maybeAutoBackup() {
        long last = prefs.getLong(Prefs.K_LAST_BACKUP, 0);
        if (System.currentTimeMillis() - last < 24L * 3600 * 1000) return;
        final List<GlassPadView.Book> snap = pad.snapshotBooks();
        new Thread(() -> {
            try {
                Backup.writeBackup(this, snap);
                prefs.edit().putLong(Prefs.K_LAST_BACKUP, System.currentTimeMillis()).apply();
                toast("Notebooks backed up to Download/Tracing Paper/Backups");
            } catch (Throwable ignored) {
                // never let a failed backup interrupt putting the glass away
            }
        }).start();
    }

    /** After a restore wrote new notebooks to disk, drop the cached pad so the next open reloads. */
    static void dropPadIfHidden() {
        PadService s = instance;
        if (s == null) return;
        s.handler.post(() -> {
            if (!s.shown) {
                s.root = null;
                s.pad = null;
            }
        });
    }

    private void removeEverything() {
        if (chip != null) { try { wm.removeView(chip); } catch (Exception ignored) {} chip = null; }
        if (shown && root != null) {
            if (pad != null) pad.flushSave();
            try { wm.removeView(root); } catch (Exception ignored) {}
        }
        shown = false;
        peeking = false;
        snipMode = false;
    }

    /** Glass stays visible but touches fall through to the app beneath. */
    private void peek() {
        if (!shown || peeking || snipMode) return;
        closePanel();
        closeToolPopup();
        pad.resetZoom();
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

    // ------------------------------------------------------------------ snip

    /** Drag a box over the page below; the clipping is pasted onto the glass. */
    private void startSnip() {
        if (!shown || snipMode) return;
        closePanel();
        closeToolPopup();
        if (hintCard != null) { root.removeView(hintCard); hintCard = null; }
        if (peeking) exitPeek();
        snipMode = true;
        pad.resetZoom(); // snips are cut from the page at true size
        pad.setVisibility(View.INVISIBLE);
        barShell.setVisibility(View.INVISIBLE);
        veil = new SnipVeil(this, this::finishSnip, this::cancelSnip);
        root.addView(veil, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void cancelSnip() {
        if (!snipMode) return;
        snipMode = false;
        if (veil != null) { root.removeView(veil); veil = null; }
        pad.setVisibility(View.VISIBLE);
        barShell.setVisibility(View.VISIBLE);
    }

    private void finishSnip(Rect sel) {
        if (veil != null) { root.removeView(veil); veil = null; }
        // the pad is already invisible; give the frame a beat to settle, then shoot
        handler.postDelayed(() -> takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(),
                new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult res) {
                        try {
                            Bitmap hw = Bitmap.wrapHardwareBuffer(
                                    res.getHardwareBuffer(), res.getColorSpace());
                            if (hw == null) throw new IllegalStateException("null bitmap");
                            Bitmap sw = hw.copy(Bitmap.Config.ARGB_8888, false);
                            res.getHardwareBuffer().close();
                            int[] loc = new int[2];
                            root.getLocationOnScreen(loc);
                            int x = Math.max(0, sel.left + loc[0]);
                            int y = Math.max(0, sel.top + loc[1]);
                            int w = Math.min(sel.width(), sw.getWidth() - x);
                            int h = Math.min(sel.height(), sw.getHeight() - y);
                            if (w < 4 || h < 4) throw new IllegalStateException("empty crop");
                            Bitmap crop = Bitmap.createBitmap(sw, x, y, w, h);
                            String file = NoteStore.saveSnip(PadService.this, crop);
                            float pw = Math.max(1, pad.getWidth()), ph = Math.max(1, pad.getHeight());
                            pad.addSnip(file, new RectF(sel.left / pw, sel.top / ph,
                                    sel.right / pw, sel.bottom / ph));
                            toast("Snipped onto the glass");
                        } catch (Exception ex) {
                            toast("Snip failed");
                        } finally {
                            snipMode = false;
                            pad.setVisibility(View.VISIBLE);
                            barShell.setVisibility(View.VISIBLE);
                        }
                    }
                    @Override public void onFailure(int code) {
                        snipMode = false;
                        pad.setVisibility(View.VISIBLE);
                        barShell.setVisibility(View.VISIBLE);
                        toast("Snip failed (" + code + ")");
                    }
                }), 180);
    }

    // -------------------------------------------- "Annotate the World" (beta)

    /**
     * Replay a recorded finger gesture beneath the glass. The pad window goes
     * untouchable for the moment of the replay so the injected touches reach
     * the app below instead of bouncing off our own glass.
     */
    private void replayFlick(float[] xs, float[] ys, long[] ts, int n) {
        if (!shown || peeking || snipMode || n < 2) return;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(clampX(xs[0]), clampY(ys[0]));
        int step = Math.max(1, n / 20);
        for (int i = step; i < n; i += step) p.lineTo(clampX(xs[i]), clampY(ys[i]));
        p.lineTo(clampX(xs[n - 1]), clampY(ys[n - 1]));
        long dur = Math.max(80, Math.min(ts[n - 1] - ts[0], 400));
        try {
            inject(new android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(new android.accessibilityservice.GestureDescription
                            .StrokeDescription(p, 0, dur))
                    .build(), dur + 300);
        } catch (IllegalArgumentException ignored) {
            // an unreplayable gesture is a skipped page-turn, not a crash
        }
    }

    private void replayTap(float x, float y) {
        if (!shown || peeking || snipMode) return;
        android.graphics.Path p = new android.graphics.Path();
        p.moveTo(clampX(x), clampY(y));
        try {
            inject(new android.accessibilityservice.GestureDescription.Builder()
                    .addStroke(new android.accessibilityservice.GestureDescription
                            .StrokeDescription(p, 0, 60))
                    .build(), 360);
        } catch (IllegalArgumentException ignored) {
        }
    }

    // A finger can roll past the screen's edge mid-flick (the digitizer reports
    // slightly negative coordinates); GestureDescription refuses paths outside
    // the display, so replayed points are pinned to it.
    private float clampX(float x) {
        return Math.max(0, Math.min(x, getResources().getDisplayMetrics().widthPixels - 1));
    }

    private float clampY(float y) {
        return Math.max(0, Math.min(y, getResources().getDisplayMetrics().heightPixels - 1));
    }

    private void inject(android.accessibilityservice.GestureDescription g, long safetyMs) {
        rootLp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(root, rootLp); } catch (Exception ignored) {}
        boolean ok = dispatchGesture(g, new GestureResultCallback() {
            @Override public void onCompleted(android.accessibilityservice.GestureDescription gd) {
                restoreTouchable();
            }
            @Override public void onCancelled(android.accessibilityservice.GestureDescription gd) {
                restoreTouchable();
            }
        }, handler);
        if (!ok) restoreTouchable();
        handler.postDelayed(this::restoreTouchable, safetyMs);
    }

    private void restoreTouchable() {
        if (!shown || peeking) return; // peek owns the untouchable flag while it lasts
        rootLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        try { wm.updateViewLayout(root, rootLp); } catch (Exception ignored) {}
    }

    // ------------------------------------------------------------ screenshot

    private void screenshot() {
        if (!shown) return;
        if (snipMode) { cancelSnip(); return; }
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

    // ------------------------------------------------------------- notebooks

    private void closePanel() {
        if (panel != null) {
            root.removeView(panel);
            panel = null;
            setWindowFocusable(false);
        }
    }

    private void setWindowFocusable(boolean f) {
        if (!shown) return;
        if (f) rootLp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        else rootLp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        try { wm.updateViewLayout(root, rootLp); } catch (Exception ignored) {}
    }

    private LinearLayout card(String title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        col.setPadding(p, p, p, p);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        col.setBackground(g);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(20);
        t.setTextColor(Color.BLACK);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, 0, 0, dp(10));
        col.addView(t);
        return col;
    }

    private void addPanel(View card) {
        ScrollView sc = new ScrollView(this);
        sc.addView(card);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(400), FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER;
        panel = sc;
        root.addView(sc, lp);
    }

    private void showBooks() {
        if (!shown || snipMode) return;
        closePanel();
        closeToolPopup();
        LinearLayout col = card("NOTEBOOKS");
        List<GlassPadView.Book> books = pad.getBooks();
        for (int i = 0; i < books.size(); i++) {
            final int idx = i;
            GlassPadView.Book b = books.get(i);
            String when = b.lastModified > 0 ? "  ·  "
                    + new java.text.SimpleDateFormat("MMM d", java.util.Locale.US)
                            .format(new java.util.Date(b.lastModified)) : "";
            TextView row = button((idx == pad.curBook() ? "▸ " : "") + b.name
                            + "  ·  " + b.pages.size() + " pg  ·  "
                            + GlassPadView.TPL_NAMES[b.template] + when,
                    v -> { pad.switchBook(idx); closePanel(); refreshBar(); });
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setOnLongClickListener(v -> {
                long now = System.currentTimeMillis();
                if (armedBookDelete == idx && now - armedBookAt < 3000) {
                    pad.deleteBook(idx);
                    armedBookDelete = -1;
                    closePanel();
                    showBooks();
                    refreshBar();
                } else {
                    armedBookDelete = idx;
                    armedBookAt = now;
                    toast("Long-press again to delete “" + b.name + "”");
                }
                return true;
            });
            TextView renameBtn = button("✎", v -> showRenameBook(idx));
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLpIn = new LinearLayout.LayoutParams(0, dp(52), 1f);
            line.addView(row, rowLpIn);
            LinearLayout.LayoutParams penLp = new LinearLayout.LayoutParams(dp(52), dp(52));
            penLp.setMarginStart(dp(8));
            line.addView(renameBtn, penLp);
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            rlp.bottomMargin = dp(8);
            col.addView(line, rlp);
        }
        col.addView(button("+ NEW NOTEBOOK", v -> showNewBook()), rowLp());
        col.addView(button("CLOSE", v -> closePanel()), rowLp());
        addPanel(col);
    }

    private void showRenameBook(int idx) {
        closePanel();
        LinearLayout col = card("RENAME NOTEBOOK");
        EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText(pad.getBooks().get(idx).name);
        name.setSelectAllOnFocus(true);
        name.setTextColor(Color.BLACK);
        name.setTextSize(18);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        name.setBackground(g);
        int p8 = dp(8);
        name.setPadding(p8 * 2, p8, p8 * 2, p8);
        col.addView(name, rowLp());
        col.addView(button("SAVE", v -> {
            pad.renameBook(idx, name.getText().toString());
            closePanel();
            showBooks();
            refreshBar();
        }), rowLp());
        col.addView(button("CANCEL", v -> { closePanel(); showBooks(); }), rowLp());
        addPanel(col);
        setWindowFocusable(true); // so the name field can take the keyboard
    }

    private void showNewBook() {
        closePanel();
        LinearLayout col = card("NEW NOTEBOOK");

        EditText name = new EditText(this);
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setText("Notebook " + (pad.getBooks().size() + 1));
        name.setSelectAllOnFocus(true);
        name.setTextColor(Color.BLACK);
        name.setTextSize(18);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        name.setBackground(g);
        int p8 = dp(8);
        name.setPadding(p8 * 2, p8, p8 * 2, p8);
        col.addView(name, rowLp());

        TextView lbl = new TextView(this);
        lbl.setText("Paper:");
        lbl.setTextColor(Color.BLACK);
        lbl.setTextSize(16);
        lbl.setPadding(0, dp(10), 0, dp(6));
        col.addView(lbl);

        LinearLayout tplRow = new LinearLayout(this);
        tplRow.setOrientation(LinearLayout.HORIZONTAL);
        final int[] sel = {GlassPadView.TPL_BLANK};
        final TextView[] tiles = new TextView[GlassPadView.TPL_NAMES.length];
        for (int t = 0; t < GlassPadView.TPL_NAMES.length; t++) {
            final int tv = t;
            tiles[t] = button(GlassPadView.TPL_NAMES[t], v -> {
                sel[0] = tv;
                for (int k = 0; k < tiles.length; k++) style(tiles[k], k == tv);
            });
            tiles[t].setTextSize(13);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (t > 0) tlp.setMarginStart(p8);
            tplRow.addView(tiles[t], tlp);
        }
        style(tiles[0], true);
        col.addView(tplRow, rowLp());

        col.addView(button("CREATE", v -> {
            String n = name.getText().toString().trim();
            if (n.isEmpty()) n = "Notebook " + (pad.getBooks().size() + 1);
            pad.newBook(n, sel[0]);
            closePanel();
            refreshBar();
        }), rowLp());
        col.addView(button("CANCEL", v -> closePanel()), rowLp());

        addPanel(col);
        setWindowFocusable(true); // so the name field can take the keyboard
    }

    private LinearLayout.LayoutParams rowLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        return lp;
    }

    // ------------------------------------------------------------------- ui

    private void buildUi() {
        root = new FrameLayout(this);
        pad = new GlassPadView(this);
        pad.setStateListener(this::refreshBar);
        root.addView(pad, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        wet = new WetInk(this);
        pad.setWetInk(wet);
        pad.setWorldGestures(new GlassPadView.WorldGestures() {
            @Override public void flick(float[] xs, float[] ys, long[] ts, int n) {
                replayFlick(xs, ys, ts, n);
            }
            @Override public void tap(float x, float y) {
                replayTap(x, y);
            }
        });
        root.addView(wet, new FrameLayout.LayoutParams(
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

        // tapping the tool you're already holding opens its size card
        penBtn = button("PEN", v -> toolTap(GlassPadView.TOOL_PEN));
        hiBtn = button("HILITE", v -> toolTap(GlassPadView.TOOL_HIGHLIGHT));
        eraseBtn = button("ERASE", v -> toolTap(GlassPadView.TOOL_ERASE));
        pickBtn = button("PICK", v -> { pad.setTool(GlassPadView.TOOL_PICK); closeToolPopup(); });
        undoBtn = button("UNDO", v -> pad.undo());
        redoBtn = button("REDO", v -> pad.redo());
        TextView clearBtn = button("CLEAR", v -> pad.clearPage());
        clearBtn.setOnLongClickListener(v -> { pad.deletePage(); return true; });
        TextView prevBtn = button("◀", v -> pad.prevPage());
        pageBtn = button("1/1", null);
        TextView nextBtn = button("▶", v -> pad.nextPage());
        TextView plusBtn = button("+PAGE", v -> pad.newPage());
        bookBtn = button("NOTES ▾", v -> showBooks());
        TextView snipBtn = button("SNIP", v -> startSnip());
        View glassBox = buildGlassSlider();
        TextView peekBtn = button("PEEK", v -> peek());
        TextView shotBtn = button("SNAP", v -> screenshot());
        TextView pdfBtn = button("PDF", v -> exportPdf());
        TextView hideBtn = button("✕ HIDE", v -> hidePad());

        View[] order = {hideBtn, penBtn, hiBtn, eraseBtn, pickBtn, undoBtn, redoBtn, clearBtn,
                prevBtn, pageBtn, nextBtn, plusBtn, bookBtn, snipBtn, glassBox,
                peekBtn, shotBtn, pdfBtn};
        for (View b : order) {
            LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                    b == glassBox ? dp(220) : LinearLayout.LayoutParams.WRAP_CONTENT, dp(48));
            blp.setMarginEnd(p8);
            row.addView(b, blp);
        }

        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        barLp.gravity = Gravity.BOTTOM;
        root.addView(barShell, barLp);
        // the wet layer composes above the whole window — keep it off the toolbar
        barShell.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or2, ob) -> wet.setClipBottom(t));

        rootLp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);

        refreshBar();
    }

    /** GLASS ▁▁▂▄ — the 0–100% white-wash slider, right in the bar. */
    private View buildGlassSlider() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.WHITE);
        g.setStroke(dp(2), Color.BLACK);
        box.setBackground(g);
        int p8 = dp(8);
        box.setPadding(p8, 0, p8, 0);

        TextView lbl = new TextView(this);
        lbl.setText("GLASS");
        lbl.setTextSize(13);
        lbl.setTypeface(Typeface.DEFAULT_BOLD);
        lbl.setTextColor(Color.BLACK);
        box.addView(lbl);

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(pad.getOpacity());
        sb.setThumbTintList(ColorStateList.valueOf(Color.BLACK));
        sb.setProgressTintList(ColorStateList.valueOf(Color.BLACK));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0xFF888888));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean fromUser) {
                if (!fromUser) return;
                // a soft detent at the sweet spot: near 80 means 80
                int vv = Math.abs(v - 80) <= 3 ? 80 : v;
                if (vv != v) s.setProgress(vv);
                pad.setOpacity(vv);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                s.getParent().requestDisallowInterceptTouchEvent(true);
            }
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        glassSeek = sb;
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        slp.setMarginStart(p8);
        box.addView(sb, slp);
        return box;
    }

    private void toolTap(int tool) {
        if (pad.getTool() == tool && toolPopup == null) showToolPopup(tool);
        else { pad.setTool(tool); closeToolPopup(); }
    }

    private void closeToolPopup() {
        if (toolPopup != null) { root.removeView(toolPopup); toolPopup = null; }
    }

    /** A small card above the bar: five sizes, and for the eraser its two natures. */
    private void showToolPopup(int tool) {
        closeToolPopup();
        String sizeKey = tool == GlassPadView.TOOL_HIGHLIGHT ? Prefs.K_HI_SIZE
                : tool == GlassPadView.TOOL_ERASE ? Prefs.K_ERASE_SIZE : Prefs.K_PEN_SIZE;
        String title = tool == GlassPadView.TOOL_HIGHLIGHT ? "HILITE SIZE"
                : tool == GlassPadView.TOOL_ERASE ? "ERASER" : "PEN SIZE";

        LinearLayout col = card(title);
        LinearLayout sizes = new LinearLayout(this);
        sizes.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] tiles = new TextView[5];
        int cur = prefs.getInt(sizeKey, 2);
        for (int i = 1; i <= 5; i++) {
            final int size = i;
            tiles[i - 1] = button(String.valueOf(i), v -> {
                prefs.edit().putInt(sizeKey, size).apply();
                for (int k = 0; k < 5; k++) style(tiles[k], k == size - 1);
            });
            style(tiles[i - 1], i == cur);
            LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, dp(48), 1f);
            if (i > 1) tlp.setMarginStart(dp(8));
            sizes.addView(tiles[i - 1], tlp);
        }
        col.addView(sizes, rowLp());

        if (tool == GlassPadView.TOOL_PEN) {
            LinearLayout shades = new LinearLayout(this);
            shades.setOrientation(LinearLayout.HORIZONTAL);
            final TextView[] sts = new TextView[GlassPadView.SHADE_NAMES.length];
            int curShade = prefs.getInt(Prefs.K_PEN_SHADE, 0);
            for (int i = 0; i < sts.length; i++) {
                final int shade = i;
                sts[i] = button(GlassPadView.SHADE_NAMES[i], v -> {
                    prefs.edit().putInt(Prefs.K_PEN_SHADE, shade).apply();
                    for (int k = 0; k < sts.length; k++) style(sts[k], k == shade);
                });
                sts[i].setTextSize(13);
                style(sts[i], i == curShade);
                LinearLayout.LayoutParams slp2 = new LinearLayout.LayoutParams(0, dp(48), 1f);
                if (i > 0) slp2.setMarginStart(dp(8));
                shades.addView(sts[i], slp2);
            }
            col.addView(shades, rowLp());
        }

        if (tool == GlassPadView.TOOL_ERASE) {
            LinearLayout modes = new LinearLayout(this);
            modes.setOrientation(LinearLayout.HORIZONTAL);
            final TextView[] mts = new TextView[2];
            boolean pixel = prefs.getBoolean(Prefs.K_ERASE_PIXEL, false);
            String[] labels = {"STROKE", "PIXEL"};
            for (int i = 0; i < 2; i++) {
                final boolean px = i == 1;
                mts[i] = button(labels[i], v -> {
                    prefs.edit().putBoolean(Prefs.K_ERASE_PIXEL, px).apply();
                    style(mts[0], !px);
                    style(mts[1], px);
                });
                style(mts[i], px == pixel);
                LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(0, dp(48), 1f);
                if (i > 0) mlp.setMarginStart(dp(8));
                modes.addView(mts[i], mlp);
            }
            col.addView(modes, rowLp());
            TextView note = new TextView(this);
            note.setText("STROKE grazes whole lines and erases them when you lift the pen. "
                    + "PIXEL rubs ink out exactly where you touch.");
            note.setTextColor(0xFF333333);
            note.setTextSize(13);
            col.addView(note, rowLp());
        }

        col.addView(button("DONE", v -> closeToolPopup()), rowLp());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                dp(340), FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = barShell.getHeight() + dp(8);
        toolPopup = col;
        root.addView(col, lp);
    }

    private void refreshBar() {
        if (pageBtn == null) return;
        pageBtn.setText(pad.pageLabel());
        int tool = pad.getTool();
        style(penBtn, tool == GlassPadView.TOOL_PEN);
        style(hiBtn, tool == GlassPadView.TOOL_HIGHLIGHT);
        style(eraseBtn, tool == GlassPadView.TOOL_ERASE);
        style(pickBtn, tool == GlassPadView.TOOL_PICK);
        undoBtn.setAlpha(pad.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(pad.canRedo() ? 1f : 0.35f);
        String n = pad.bookName();
        if (n.length() > 10) n = n.substring(0, 9) + "…";
        bookBtn.setText(n.toUpperCase(java.util.Locale.US) + " ▾");
        // each notebook remembers its own GLASS — keep the slider honest
        if (glassSeek != null && glassSeek.getProgress() != pad.getOpacity())
            glassSeek.setProgress(pad.getOpacity());
    }

    private void exportPdf() {
        toast("Making the PDF…");
        pad.flushSave();
        final List<GlassPadView.Book> snap = pad.snapshotBooks();
        final float aspect = (float) Math.max(1, pad.getWidth()) / Math.max(1, pad.getHeight());
        new Thread(() -> {
            try {
                String name = Exporter.exportPdf(this, snap, aspect);
                handler.post(() -> toast("Saved " + name + " in Download"));
            } catch (Throwable e) {
                // Throwable on purpose: a giant library should fail with a
                // toast, not take the process (and the pad) down with it
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
