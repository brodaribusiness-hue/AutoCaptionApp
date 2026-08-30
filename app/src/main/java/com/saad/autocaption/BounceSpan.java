package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Active word gently pulses/bounces in size. Caption text already
 * redraws every ~100ms (see MainActivity's caption update loop), so
 * this just picks the scale for "right now" each time it's drawn —
 * no separate animation timer needed. */
public class BounceSpan extends ReplacementSpan {

    private final int color;

    public BounceSpan(int color) {
        this.color = color;
    }

    private float currentScale() {
        long t = System.currentTimeMillis() % 600;
        double phase = (t / 600.0) * Math.PI * 2;
        return 1f + 0.25f * (float) Math.abs(Math.sin(phase));
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float scale = currentScale();
        float originalSize = paint.getTextSize();
        paint.setTextSize(originalSize * scale);
        int width = Math.round(paint.measureText(text, start, end));
        if (fm != null) {
            Paint.FontMetricsInt scaledFm = new Paint.FontMetricsInt();
            paint.getFontMetricsInt(scaledFm);
            fm.ascent = scaledFm.ascent;
            fm.descent = scaledFm.descent;
            fm.top = scaledFm.top;
            fm.bottom = scaledFm.bottom;
        }
        paint.setTextSize(originalSize);
        return width;
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        float scale = currentScale();
        float originalSize = paint.getTextSize();
        int originalColor = paint.getColor();

        paint.setTextSize(originalSize * scale);
        paint.setColor(color);
        canvas.drawText(text, start, end, x, y, paint);

        paint.setTextSize(originalSize);
        paint.setColor(originalColor);
    }
}
