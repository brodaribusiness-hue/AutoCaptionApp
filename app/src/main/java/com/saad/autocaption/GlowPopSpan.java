package com.saad.autocaption;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Glow + enlarged scale combined on the active word. */
public class GlowPopSpan extends ReplacementSpan {

    private final int color;
    private final float scale;
    private final float blurRadius;

    public GlowPopSpan(int color, float scale, float blurRadius) {
        this.color = color;
        this.scale = scale;
        this.blurRadius = blurRadius;
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
        MaskFilter originalMask = paint.getMaskFilter();

        paint.setTextSize(originalSize * scale);
        paint.setColor(color);
        paint.setMaskFilter(new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(text, start, end, x, y, paint);

        paint.setMaskFilter(originalMask);
        paint.setColor(originalColor);
        paint.setTextSize(originalSize);
    }
}
