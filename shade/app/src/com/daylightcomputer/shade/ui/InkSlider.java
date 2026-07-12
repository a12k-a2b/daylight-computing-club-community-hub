package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;

/** A chunky grayscale slider: 4dp ink track, big square thumb, sun glyphs
 *  drawn on the canvas. Whole row is one ≥56dp touch target. */
public class InkSlider extends View {

    public interface Listener { void onValue(float v, boolean fromUser); }

    /** Optional legend: names the current position ("paper-like", …),
     *  drawn above the thumb and updated live as the finger moves. */
    public interface Labeler { String labelFor(float v); }

    public enum EndGlyphs { BRIGHTNESS, WARMTH }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final EndGlyphs glyphs;
    private float value = 0.5f;
    private boolean enabled = true;
    private String disabledHint = "";
    private Listener listener;
    private Labeler labeler;

    public InkSlider(Context c, EndGlyphs g) {
        super(c);
        glyphs = g;
        setMinimumHeight(Ui.dp(c, 60));
    }

    public void setListener(Listener l) { listener = l; }
    public void setLabeler(Labeler l) { labeler = l; invalidate(); }
    public void setValue(float v) { value = Math.max(0f, Math.min(1f, v)); invalidate(); }
    public float getValue() { return value; }
    public void setSliderEnabled(boolean e, String hint) {
        enabled = e; disabledHint = hint == null ? "" : hint; invalidate();
    }

    private float pad() { return Ui.dp(getContext(), 44); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (!enabled) return false;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                ViewParent p = getParent();
                if (p != null) p.requestDisallowInterceptTouchEvent(true);
                // fall through
            }
            case MotionEvent.ACTION_MOVE: {
                float track = getWidth() - pad() * 2;
                float v = (e.getX() - pad()) / Math.max(1f, track);
                setValue(v);
                if (listener != null) listener.onValue(value, true);
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override protected void onDraw(Canvas cv) {
        Context c = getContext();
        float cy = getHeight() / 2f;
        float left = pad(), right = getWidth() - pad();
        int ink = enabled ? Ui.INK : Ui.FAINT;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(c, 4));
        paint.setColor(ink);
        cv.drawLine(left, cy, right, cy, paint);

        drawEndGlyph(cv, left / 2f, cy, false, ink);
        drawEndGlyph(cv, getWidth() - left / 2f, cy, true, ink);

        if (!enabled) {
            if (!disabledHint.isEmpty()) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTypeface(android.graphics.Typeface.SERIF);
                paint.setTextSize(Ui.dp(c, 13));
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(Ui.MID);
                cv.drawText(disabledHint, getWidth() / 2f, cy - Ui.dp(c, 12), paint);
            }
            return;
        }

        // square paper thumb with a real border
        float x = left + (right - left) * value;
        float half = Ui.dp(c, 15);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Ui.PAPER);
        cv.drawRect(x - half, cy - half, x + half, cy + half, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(c, 3));
        paint.setColor(Ui.INK);
        cv.drawRect(x - half, cy - half, x + half, cy + half, paint);

        // the legend: a quiet word above the thumb naming this light
        String zone = labeler == null ? null : labeler.labelFor(value);
        if (zone != null && !zone.isEmpty()) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTypeface(android.graphics.Typeface.create(
                    android.graphics.Typeface.SERIF, android.graphics.Typeface.ITALIC));
            paint.setTextSize(Ui.dp(c, 12.5f));
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setColor(Ui.MID);
            float tw = paint.measureText(zone) / 2f;
            float tx = Math.max(left + tw, Math.min(right - tw, x));
            cv.drawText(zone, tx, cy - half - Ui.dp(c, 6), paint);
        }
    }

    /** Left/right end glyphs: dim sun → bright sun, or warm sun → open sun
     *  (warmth runs amber→white rightward, matching the stock shade). */
    private void drawEndGlyph(Canvas cv, float cx, float cy, boolean rightEnd, int ink) {
        Context c = getContext();
        boolean warmEnd = glyphs == EndGlyphs.WARMTH && !rightEnd;
        float r = Ui.dp(c, rightEnd || warmEnd ? 7 : 5);
        paint.setColor(ink);
        boolean filled = glyphs == EndGlyphs.WARMTH ? warmEnd : true;
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE);
        paint.setStrokeWidth(Ui.dp(c, 2));
        cv.drawCircle(cx, cy, r, paint);
        if (glyphs == EndGlyphs.BRIGHTNESS || warmEnd) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.dp(c, 2));
            int rays = 8;
            float inner = r + Ui.dp(c, 2.5f),
                    outer = r + Ui.dp(c, rightEnd || warmEnd ? 6 : 4.5f);
            for (int i = 0; i < rays; i++) {
                double a = Math.PI * 2 * i / rays;
                cv.drawLine((float) (cx + inner * Math.cos(a)), (float) (cy + inner * Math.sin(a)),
                        (float) (cx + outer * Math.cos(a)), (float) (cy + outer * Math.sin(a)), paint);
            }
        }
    }
}
