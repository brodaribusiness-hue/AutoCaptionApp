package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

/** TikTok/CapCut-style rounded "pill" background behind the active
 * word — the box color is fully user-controlled (boxColor) — with the
 * text itself rendered as pseudo-3D: several darkened, offset copies
 * stacked behind the glyph to fake depth/extrusion, topped with the
 * bright user-chosen highlight color on the face. */
public class BackgroundBoxSpan extends ReplacementSpan {

    private final int textColor;
    private final int boxColor;
    private final float paddingPx;
    private final float cornerRadiusPx;

    private static final int DEPTH_LAYERS = 5;
    private static final float DEPTH_OFFSET_PX = 1.4f;

    public BackgroundBoxSpan(int textColor, int boxColor, float paddingPx, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.paddingPx = paddingPx;
        this.cornerRadiusPx = cornerRadiusPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end,
                        Paint.FontMetricsInt fm) {
        return Math.round(paint.measureText(text, start, end) + paddingPx * 2
                + DEPTH_LAYERS * DEPTH_OFFSET_PX);
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end,
                      float x, int top, int y, int bottom, Paint paint) {
        float width = paint.measureText(text, start, end);
        RectF rect = new RectF(x, top,
                x + width + paddingPx * 2 + DEPTH_LAYERS * DEPTH_OFFSET_PX, bottom);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // Box background — entirely user-controlled, independent of
        // the highlight/text color.
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadiusPx, cornerRadiusPx, paint);

        // Pseudo-3D extrusion: stack darkened copies of the text
        // receding down-and-right, then draw the bright face color on
        // top last so the letters stay crisp and readable.
        float[] hsv = new float[3];
        Color.colorToHSV(textColor, hsv);
        float shadeValue = Math.max(hsv[2] * 0.35f, 0.08f);
        int depthColor = Color.HSVToColor(new float[]{hsv[0], hsv[1], shadeValue});

        for (int layer = DEPTH_LAYERS; layer >= 1; layer--) {
            paint.setColor(depthColor);
            canvas.drawText(text, start, end,
                    x + paddingPx + layer * DEPTH_OFFSET_PX,
                    y + layer * DEPTH_OFFSET_PX, paint);
        }

        paint.setColor(textColor);
        canvas.drawText(text, start, end, x + paddingPx, y, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
