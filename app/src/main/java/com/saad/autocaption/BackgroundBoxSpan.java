package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

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
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            int extra = Math.round(paddingPx);
            fm.ascent -= extra;
            fm.top -= extra;
            fm.descent += extra;
            fm.bottom += extra;
        }
        return Math.round(paint.measureText(text, start, end) + paddingPx * 2);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float width = paint.measureText(text, start, end);
        RectF rect = new RectF(x, top - (paddingPx / 2f), x + width + (paddingPx * 2f), bottom + (paddingPx / 2f));

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // 1. Draw rounded box background
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);

        // 2. Text depth shadow
        float[] hsv = new float[3];
        Color.colorToHSV(textColor, hsv);
        int depthColor = Color.HSVToColor(new float[]{hsv[0], hsv[1], Math.max(hsv[2] * 0.35f, 0.08f)});
        paint.setColor(depthColor);
        canvas.drawText(text, start, end, x + paddingPx + 2f, y + 2f, paint);

        // 3. Foreground active text
        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + paddingPx, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
