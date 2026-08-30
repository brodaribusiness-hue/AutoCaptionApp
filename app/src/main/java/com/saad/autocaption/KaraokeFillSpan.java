package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Karaoke-style solid color "fill bar" underline beneath the active
 * word, text drawn in a contrasting color on top of it. */
public class KaraokeFillSpan extends ReplacementSpan {

    private final int textColor;
    private final int fillColor;
    private final float barHeightPx;

    public KaraokeFillSpan(int textColor, int fillColor, float barHeightPx) {
        this.textColor = textColor;
        this.fillColor = fillColor;
        this.barHeightPx = barHeightPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        float width = paint.measureText(text, start, end);
        RectF bar = new RectF(x, bottom - barHeightPx, x + width, bottom + 2f);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawRoundRect(bar, barHeightPx / 2f, barHeightPx / 2f, paint);

        paint.setColor(textColor);
        canvas.drawText(text, start, end, x, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
