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
    private int armedBookDelete = -1;
    private long armedBookAt;

    private TextView pageBtn, penBtn, hiBtn, eraseBtn, undoBtn, redoBtn, bookBtn;

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
        cancelSnip();
        closePanel();
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
        snipMode = false;
    }

    /** Glass stays visible but touches fall through to the app beneath. */
    private void peek() {
        if (!shown || peeking || snipMode) return;
        closePanel();
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
        if (peeking) exitPeek();
        snipMode = true;
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
        LinearLayout col = card("NOTEBOOKS");
        List<GlassPadView.Book> books = pad.getBooks();
        for (int i = 0; i < books.size(); i++) {
            final int idx = i;
            GlassPadView.Book b = books.get(i);
            TextView row = button((idx == pad.curBook() ? "▸ " : "") + b.name
                            + "  ·  " + b.pages.size() + " pg  ·  "
                            + GlassPadView.TPL_NAMES[b.template],
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
            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(52));
            rlp.bottomMargin = dp(8);
            col.addView(row, rlp);
        }
        col.addView(button("+ NEW NOTEBOOK", v -> showNewBook()), rowLp());
        col.addView(button("CLOSE", v -> closePanel()), rowLp());
        addPanel(col);
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

        penBtn = button("PEN", v -> pad.setTool(GlassPadView.TOOL_PEN));
        hiBtn = button("HILITE", v -> pad.setTool(GlassPadView.TOOL_HIGHLIGHT));
        eraseBtn = button("ERASE", v -> pad.setTool(GlassPadView.TOOL_ERASE));
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

        View[] order = {hideBtn, penBtn, hiBtn, eraseBtn, undoBtn, redoBtn, clearBtn,
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
                if (fromUser) pad.setOpacity(v);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {
                s.getParent().requestDisallowInterceptTouchEvent(true);
            }
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        slp.setMarginStart(p8);
        box.addView(sb, slp);
        return box;
    }

    private void refreshBar() {
        if (pageBtn == null) return;
        pageBtn.setText(pad.pageLabel());
        int tool = pad.getTool();
        style(penBtn, tool == GlassPadView.TOOL_PEN);
        style(hiBtn, tool == GlassPadView.TOOL_HIGHLIGHT);
        style(eraseBtn, tool == GlassPadView.TOOL_ERASE);
        undoBtn.setAlpha(pad.canUndo() ? 1f : 0.35f);
        redoBtn.setAlpha(pad.canRedo() ? 1f : 0.35f);
        String n = pad.bookName();
        if (n.length() > 10) n = n.substring(0, 9) + "…";
        bookBtn.setText(n.toUpperCase(java.util.Locale.US) + " ▾");
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
