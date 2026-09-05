package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Background highlight box dynamically scaled to text size and font metrics. */
public class BackgroundBoxSpan extends ReplacementSpan {
    private final int textColor;
    private final int boxColor;
    private final float cornerRadius;

    public BackgroundBoxSpan(int textColor, int boxColor, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.cornerRadius = cornerRadiusPx;
    }

    private float getHorizontalPadding(Paint paint) {
        return paint.getTextSize() * 0.30f;
    }

    private float getVerticalPadding(Paint paint) {
        return paint.getTextSize() * 0.18f;
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
        float textCenterY = y + (fm.ascent + fm.descent) / 2f;
        float boxHeight = (fm.descent - fm.ascent) + (vPad * 2f);

        RectF rect = new RectF(
                x,
                textCenterY - (boxHeight / 2f),
                x + textWidth + (hPad * 2f),
                textCenterY + (boxHeight / 2f)
        );

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // 1. Rounded highlight background box
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 2. Exact Centered Text placement
        float textX = x + hPad;
        float textY = y;

        // Subtle drop shadow for depth
        float[] hsv = new float[3];
        Color.colorToHSV(textColor, hsv);
        int depthColor = Color.HSVToColor(160, new float[]{hsv[0], hsv[1], Math.max(hsv[2] * 0.30f, 0.05f)});
        paint.setColor(depthColor);
        canvas.drawText(text, start, end, textX + 1.5f, textY + 1.5f, paint);

        // Foreground text
        paint.setColor(textColor);
        canvas.drawText(text, start, end, textX, textY, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
