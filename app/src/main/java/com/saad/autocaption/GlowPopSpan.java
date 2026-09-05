package com.saad.autocaption;

import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.MaskFilter;
import android.graphics.Paint;
import android.text.style.ReplacementSpan;

/** Neon Glow effect: draws multi-stage diffused glow halos behind crisp bright core text. */
public class GlowPopSpan extends ReplacementSpan {

    private final int color;
    private final int glowColor;
    private final float scale;
    private final float blurRadius;

    public GlowPopSpan(int color, int glowColor, float scale, float blurRadius) {
        this.color = color;
        this.glowColor = glowColor;
        this.scale = scale;
        this.blurRadius = Math.max(blurRadius, 8f);
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        float originalSize = paint.getTextSize();
        paint.setTextSize(originalSize * scale);
        int width = Math.round(paint.measureText(text, start, end) + (blurRadius * 2f));
        if (fm != null) {
            Paint.FontMetricsInt scaledFm = new Paint.FontMetricsInt();
            paint.getFontMetricsInt(scaledFm);
            int extraPad = Math.round(blurRadius);
            fm.ascent = scaledFm.ascent - extraPad;
            fm.descent = scaledFm.descent + extraPad;
            fm.top = scaledFm.top - extraPad;
            fm.bottom = scaledFm.bottom + extraPad;
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
        Paint.Style originalStyle = paint.getStyle();

        paint.setTextSize(originalSize * scale);
        float drawX = x + blurRadius;

        // Stage 1: Outer diffused wide neon bloom
        int outerGlow = Color.argb(160, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor));
        paint.setColor(outerGlow);
        paint.setMaskFilter(new BlurMaskFilter(blurRadius * 1.6f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(text, start, end, drawX, y, paint);

        // Stage 2: Intense inner neon glow core
        int innerGlow = Color.argb(240, Color.red(glowColor), Color.green(glowColor), Color.blue(glowColor));
        paint.setColor(innerGlow);
        paint.setMaskFilter(new BlurMaskFilter(blurRadius * 0.7f, BlurMaskFilter.Blur.NORMAL));
        canvas.drawText(text, start, end, drawX, y, paint);

        // Stage 3: Super-bright foreground text
        paint.setMaskFilter(null);
        paint.setColor(color);
        canvas.drawText(text, start, end, drawX, y, paint);

        // Reset Paint State
        paint.setMaskFilter(originalMask);
        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
        paint.setTextSize(originalSize);
    }
}
