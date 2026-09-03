package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** Karaoke-style solid color highlight block behind the active word
 * (full glyph height, not just a thin underline bar), with text drawn
 * in a contrasting color on top so the effect is unmistakable. */
public class KaraokeFillSpan extends ReplacementSpan {

    private final int textColor;
    private final int fillColor;
    private final float extraPaddingPx;

    public KaraokeFillSpan(int textColor, int fillColor, float extraPaddingPx) {
        this.textColor = textColor;
        this.fillColor = fillColor;
        this.extraPaddingPx = extraPaddingPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end) + extraPaddingPx);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        float width = paint.measureText(text, start, end);
        // FIX: was a thin barHeightPx-tall bar at the very bottom, which
        // read as a barely-visible underline. Now fills the word's full
        // vertical extent so it reads as a clear karaoke highlight.
        RectF block = new RectF(
                x, top - extraPaddingPx / 2f,
                x + width + extraPaddingPx, bottom + extraPaddingPx / 2f);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(fillColor);
        canvas.drawRoundRect(block, 10f, 10f, paint);

        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + extraPaddingPx / 2f, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
