package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

public class BackgroundBoxSpan extends ReplacementSpan {
    private final int textColor;
    private final int boxColor;
    private final float cornerRadius;
    private final float paddingPx;

    public BackgroundBoxSpan(int textColor, int boxColor, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.cornerRadius = cornerRadiusPx;
        this.paddingPx = 18f;
    }

    public BackgroundBoxSpan(int textColor, int boxColor, float paddingPx, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.paddingPx = paddingPx;
        this.cornerRadius = cornerRadiusPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float horizontalPad = paddingPx * 1.2f;
        float verticalPad = paddingPx * 0.6f;

        if (fm != null) {
            int extra = Math.round(verticalPad);
            fm.ascent -= extra;
            fm.top -= extra;
            fm.descent += extra;
            fm.bottom += extra;
        }
        return Math.round(paint.measureText(text, start, end) + (horizontalPad * 2f));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float textWidth = paint.measureText(text, start, end);
        float hPad = paddingPx * 1.2f;
        float vPad = paddingPx * 0.6f;

        Paint.FontMetrics fm = paint.getFontMetrics();
        float boxTop = y + fm.ascent - vPad;
        float boxBottom = y + fm.descent + vPad;
        float boxLeft = x;
        float boxRight = x + textWidth + (hPad * 2f);

        RectF rect = new RectF(boxLeft, boxTop, boxRight, boxBottom);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // 1. Draw rounded box with exact boundary
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 2. Center Text inside the box
        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + hPad, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
