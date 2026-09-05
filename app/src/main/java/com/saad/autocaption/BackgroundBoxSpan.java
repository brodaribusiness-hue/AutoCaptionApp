package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Proportional rounded box that fully encloses text without cutting ascending/descending glyphs. */
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
        return paint.getTextSize() * 0.35f;
    }

    private float getVerticalPadding(Paint paint) {
        return paint.getTextSize() * 0.25f;
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
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
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

        // 1. Draw rounded box with full text boundary
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 2. Text drop shadow for contrast
        float textX = x + hPad;
        float[] hsv = new float[3];
        Color.colorToHSV(textColor, hsv);
        int depthColor = Color.HSVToColor(160, new float[]{hsv[0], hsv[1], Math.max(hsv[2] * 0.30f, 0.05f)});
        paint.setColor(depthColor);
        canvas.drawText(text, start, end, textX + 1.5f, y + 1.5f, paint);

        // 3. Crisp Foreground Text
        paint.setColor(textColor);
        canvas.drawText(text, start, end, textX, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
