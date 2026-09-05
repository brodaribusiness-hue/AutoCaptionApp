package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Dynamic highlight box that automatically expands and scales with font size and metrics. */
public class BackgroundBoxSpan extends ReplacementSpan {
    private final int textColor;
    private final int boxColor;
    private final float cornerRadius;

    public BackgroundBoxSpan(int textColor, int boxColor, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.cornerRadius = cornerRadiusPx;
    }

    public BackgroundBoxSpan(int textColor, int boxColor, float paddingPx, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.cornerRadius = cornerRadiusPx;
    }

    private float getHorizontalPadding(Paint paint) {
        return paint.getTextSize() * 0.32f;
    }

    private float getVerticalPadding(Paint paint) {
        return paint.getTextSize() * 0.22f;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float hPad = getHorizontalPadding(paint);
        float vPad = getVerticalPadding(paint);

        if (fm != null) {
            int extra = Math.round(vPad);
            fm.ascent -= extra;
            fm.top -= extra;
            fm.descent += extra;
            fm.bottom += extra;
        }
        return Math.round(paint.measureText(text, start, end) + (hPad * 2f));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float textWidth = paint.measureText(text, start, end);
        float hPad = getHorizontalPadding(paint);
        float vPad = getVerticalPadding(paint);

        Paint.FontMetrics fm = paint.getFontMetrics();
        float boxTop = y + fm.ascent - vPad;
        float boxBottom = y + fm.descent + vPad;
        float boxLeft = x;
        float boxRight = x + textWidth + (hPad * 2f);

        RectF rect = new RectF(boxLeft, boxTop, boxRight, boxBottom);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // 1. Draw rounded box with exact font proportions
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 2. Draw active text centered inside the box
        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + hPad, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
