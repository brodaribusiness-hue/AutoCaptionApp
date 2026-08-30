package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** TikTok/CapCut-style solid rounded "pill" background behind the word. */
public class BackgroundBoxSpan extends ReplacementSpan {

    private final int textColor;
    private final int boxColor;
    private final float paddingPx;
    private final float cornerRadiusPx;

    public BackgroundBoxSpan(int textColor, int boxColor, float paddingPx, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.paddingPx = paddingPx;
        this.cornerRadiusPx = cornerRadiusPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end) + paddingPx * 2);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        float width = paint.measureText(text, start, end);
        RectF rect = new RectF(x, top, x + width + paddingPx * 2, bottom);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);

        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + paddingPx, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
