package club.daylightcomputer.tracingpaper;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;

import java.util.function.Consumer;

/** Snip mode: a dim veil you drag a box on. The box becomes a clipping on the glass. */
class SnipVeil extends View {

    private final Consumer<Rect> onDone;
    private final Runnable onCancel;
    private final Paint dim = new Paint();
    private final Paint box = new Paint();
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintBg = new Paint();
    private final float d;
    private float sx, sy, cx, cy;
    private boolean dragging;

    SnipVeil(Context c, Consumer<Rect> done, Runnable cancel) {
        super(c);
        onDone = done;
        onCancel = cancel;
        d = getResources().getDisplayMetrics().density;
        dim.setColor(0x2E000000);
        box.setStyle(Paint.Style.STROKE);
        box.setStrokeWidth(2 * d);
        box.setColor(Color.BLACK);
        hint.setColor(Color.BLACK);
        hint.setTextSize(16 * d);
        hint.setTextAlign(Paint.Align.CENTER);
        hintBg.setColor(Color.WHITE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                sx = cx = e.getX();
                sy = cy = e.getY();
                dragging = true;
                invalidate();
                return true;
            case MotionEvent.ACTION_MOVE:
                cx = e.getX();
                cy = e.getY();
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                Rect r = selection();
                if (r.width() < 24 * d || r.height() < 24 * d) onCancel.run();
                else onDone.accept(r);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                onCancel.run();
                return true;
        }
        return true;
    }

    private Rect selection() {
        return new Rect(Math.round(Math.min(sx, cx)), Math.round(Math.min(sy, cy)),
                Math.round(Math.max(sx, cx)), Math.round(Math.max(sy, cy)));
    }

    @Override
    protected void onDraw(Canvas cv) {
        Rect r = selection();
        if (dragging && r.width() > 0 && r.height() > 0) {
            cv.save();
            cv.clipOutRect(r);
            cv.drawPaint(dim);
            cv.restore();
            cv.drawRect(r, box);
        } else {
            cv.drawPaint(dim);
        }
        String msg = "Drag a box — it lands on the glass. A tiny tap cancels.";
        float tw = hint.measureText(msg);
        float y = 28 * d;
        cv.drawRect(getWidth() / 2f - tw / 2 - 12 * d, y - 22 * d,
                getWidth() / 2f + tw / 2 + 12 * d, y + 10 * d, hintBg);
        cv.drawText(msg, getWidth() / 2f, y, hint);
    }
}
