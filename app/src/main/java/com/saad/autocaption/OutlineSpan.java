package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Classic "meme caption" look: solid fill color text with a dark
 * stroke outline, readable on any background regardless of video. */
public class OutlineSpan extends ReplacementSpan {

    private final int fillColor;
    private final int outlineColor;
    private final float strokeWidth;

    public OutlineSpan(int fillColor, int outlineColor, float strokeWidth) {
        this.fillColor = fillColor;
        this.outlineColor = outlineColor;
        this.strokeWidth = strokeWidth;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();
        float originalStrokeWidth = paint.getStrokeWidth();

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(strokeWidth);
        paint.setColor(outlineColor);
        canvas.drawText(text, start, end, x, y, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawText(text, start, end, x, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
        paint.setStrokeWidth(originalStrokeWidth);
    }
}
