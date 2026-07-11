package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

/** One quick-settings pill: bordered rectangle, title + state line,
 *  inverts to ink-on-paper→paper-on-ink when active. Tap toggles,
 *  long-press opens the relevant full settings page. */
public class TileButton extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String title = "";
    private String sub = "";
    private boolean active;
    private boolean available = true;

    public TileButton(Context c, String title, Runnable onTap, Runnable onLongPress) {
        super(c);
        this.title = title;
        setClickable(true);
        setLongClickable(true);
        setOnClickListener(v -> onTap.run());
        setOnLongClickListener(v -> { onLongPress.run(); return true; });
        setMinimumHeight(Ui.dp(c, 72));
    }

    public void setState(boolean active, String sub, boolean available) {
        this.active = active;
        this.sub = sub == null ? "" : sub;
        this.available = available;
        invalidate();
    }

    /** Stock-QS parity: the Bluetooth pill's title becomes the connected
     *  device's name while exactly one is connected. */
    public void setTitle(String t) {
        this.title = t == null ? "" : t;
        invalidate();
    }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        Context c = getContext();
        int w = getWidth(), h = getHeight();
        int bg = active ? Ui.INK : (isPressed() ? Ui.PRESSED : Ui.PAPER);
        int fg = active ? Ui.PAPER : (available ? Ui.INK : Ui.FAINT);
        int fgSub = active ? Ui.PAPER : (available ? Ui.MID : Ui.FAINT);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(bg);
        cv.drawRect(0, 0, w, h, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(c, available ? 3 : 2) * 2f);
        paint.setColor(available ? Ui.INK : Ui.FAINT);
        cv.drawRect(0, 0, w, h, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Ui.dp(c, 16));
        paint.setColor(fg);
        float mid = h / 2f;
        cv.drawText(title, w / 2f, sub.isEmpty() ? mid + Ui.dp(c, 6) : mid - Ui.dp(c, 2), paint);
        if (!sub.isEmpty()) {
            paint.setTypeface(Typeface.SERIF);
            paint.setTextSize(Ui.dp(c, 12.5f));
            paint.setColor(fgSub);
            cv.drawText(sub, w / 2f, mid + Ui.dp(c, 16), paint);
        }
    }
}
