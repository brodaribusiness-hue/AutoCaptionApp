package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

public class PopScaleSpan extends ReplacementSpan {

    private final int textColor;
    private final float scale;

    public PopScaleSpan(int textColor, float scale) {
        this.textColor = textColor;
        this.scale = scale;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
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
        float originalSize = paint.getTextSize();
        int originalColor = paint.getColor();

        paint.setTextSize(originalSize * scale);
        paint.setColor(textColor);
        canvas.drawText(text, start, end, x, y, paint);

        paint.setTextSize(originalSize);
        paint.setColor(originalColor);
    }
}
