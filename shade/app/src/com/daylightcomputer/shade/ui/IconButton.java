package com.daylightcomputer.shade.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** A square bordered button whose glyph is drawn with the canvas, so it
 *  renders identically regardless of which fonts the device ships. */
public class IconButton extends View {

    public enum Glyph { PLAY, PAUSE, PREV, NEXT, BACK15, FWD15, HEART, HEART_OUTLINE, X, BACK, MIC }

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Glyph glyph;
    private boolean active; // inverted (ink background)

    public IconButton(Context c, Glyph g, Runnable onTap) {
        super(c);
        this.glyph = g;
        setClickable(true);
        setOnClickListener(v -> onTap.run());
        stroke.setStyle(Paint.Style.STROKE);
        textPaint.setTypeface(android.graphics.Typeface.SERIF);
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setGlyph(Glyph g) { if (glyph != g) { glyph = g; invalidate(); } }
    public void setActive(boolean a) { if (active != a) { active = a; invalidate(); } }

    @Override protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        int w = getWidth(), h = getHeight();
        int bw = Ui.dp(getContext(), 2);
        int bg = active ? Ui.INK : (isPressed() ? Ui.PRESSED : Ui.PAPER);
        int fg = active ? Ui.PAPER : Ui.INK;

        fill.setColor(bg);
        cv.drawRect(0, 0, w, h, fill);
        stroke.setColor(Ui.INK);
        stroke.setStrokeWidth(bw * 2); // half is clipped; yields a bw border
        cv.drawRect(0, 0, w, h, stroke);

        fill.setColor(fg);
        stroke.setColor(fg);
        float cx = w / 2f, cy = h / 2f, u = Math.min(w, h) / 6f;
        Path p = new Path();
        switch (glyph) {
            case PLAY:
                p.moveTo(cx - u, cy - u * 1.3f); p.lineTo(cx + u * 1.2f, cy); p.lineTo(cx - u, cy + u * 1.3f);
                p.close(); cv.drawPath(p, fill);
                break;
            case PAUSE:
                cv.drawRect(cx - u * 1.1f, cy - u * 1.2f, cx - u * 0.3f, cy + u * 1.2f, fill);
                cv.drawRect(cx + u * 0.3f, cy - u * 1.2f, cx + u * 1.1f, cy + u * 1.2f, fill);
                break;
            case PREV:
                cv.drawRect(cx - u * 1.4f, cy - u, cx - u * 0.9f, cy + u, fill);
                p.moveTo(cx + u * 1.2f, cy - u); p.lineTo(cx - u * 0.5f, cy); p.lineTo(cx + u * 1.2f, cy + u);
                p.close(); cv.drawPath(p, fill);
                break;
            case NEXT:
                p.moveTo(cx - u * 1.2f, cy - u); p.lineTo(cx + u * 0.5f, cy); p.lineTo(cx - u * 1.2f, cy + u);
                p.close(); cv.drawPath(p, fill);
                cv.drawRect(cx + u * 0.9f, cy - u, cx + u * 1.4f, cy + u, fill);
                break;
            case BACK15:
            case FWD15: {
                boolean fwd = glyph == Glyph.FWD15;
                float tx = fwd ? cx + u * 1.5f : cx - u * 1.5f;
                p.moveTo(tx, cy - u * 0.9f);
                p.lineTo(fwd ? tx + u * 0.9f : tx - u * 0.9f, cy);
                p.lineTo(tx, cy + u * 0.9f);
                p.close(); cv.drawPath(p, fill);
                textPaint.setColor(fg);
                textPaint.setTextSize(u * 2.4f);
                textPaint.setFakeBoldText(true);
                float ty = cy - (textPaint.ascent() + textPaint.descent()) / 2f;
                cv.drawText("15", fwd ? cx - u * 0.4f : cx + u * 0.4f, ty, textPaint);
                break;
            }
            case HEART:
            case HEART_OUTLINE: {
                p.moveTo(cx, cy + u * 1.3f);
                p.cubicTo(cx - u * 2.2f, cy - u * 0.4f, cx - u * 1.1f, cy - u * 1.8f, cx, cy - u * 0.5f);
                p.cubicTo(cx + u * 1.1f, cy - u * 1.8f, cx + u * 2.2f, cy - u * 0.4f, cx, cy + u * 1.3f);
                p.close();
                if (glyph == Glyph.HEART) cv.drawPath(p, fill);
                else { stroke.setStrokeWidth(Ui.dp(getContext(), 2)); stroke.setStyle(Paint.Style.STROKE); cv.drawPath(p, stroke); }
                break;
            }
            case X:
                stroke.setStrokeWidth(Ui.dp(getContext(), 2));
                stroke.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx - u, cy - u, cx + u, cy + u, stroke);
                cv.drawLine(cx + u, cy - u, cx - u, cy + u, stroke);
                break;
            case BACK:
                stroke.setStrokeWidth(Ui.dp(getContext(), 2.5f));
                stroke.setStyle(Paint.Style.STROKE);
                cv.drawLine(cx + u * 0.7f, cy - u * 1.1f, cx - u * 0.7f, cy, stroke);
                cv.drawLine(cx - u * 0.7f, cy, cx + u * 0.7f, cy + u * 1.1f, stroke);
                break;
            case MIC: {
                // capsule body, cradle arc, stem, base — a mic in four strokes
                cv.drawRoundRect(cx - u * 0.55f, cy - u * 1.5f, cx + u * 0.55f, cy + u * 0.2f,
                        u * 0.55f, u * 0.55f, fill);
                stroke.setStrokeWidth(Ui.dp(getContext(), 2));
                stroke.setStyle(Paint.Style.STROKE);
                cv.drawArc(cx - u * 1.05f, cy - u * 0.9f, cx + u * 1.05f, cy + u * 0.75f,
                        20, 140, false, stroke);
                cv.drawLine(cx, cy + u * 0.75f, cx, cy + u * 1.3f, stroke);
                cv.drawLine(cx - u * 0.6f, cy + u * 1.3f, cx + u * 0.6f, cy + u * 1.3f, stroke);
                break;
            }
        }
    }
}
