package com.saad.autocaption;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.style.ReplacementSpan;

public class BackgroundBoxSpan extends ReplacementSpan {
    private final int textColor;
    private final int boxColor;
    private final float horizontalPadding;
    private final float verticalPadding;
    private final float cornerRadius;

    public BackgroundBoxSpan(int textColor, int boxColor, float paddingPx, float cornerRadiusPx) {
        this.textColor = textColor;
        this.boxColor = boxColor;
        this.horizontalPadding = paddingPx;
        this.verticalPadding = paddingPx * 0.55f;
        this.cornerRadius = cornerRadiusPx;
    }

    @Override
    public int getSize(Paint paint, CharSequence text, int start, int end, Paint.FontMetricsInt fm) {
        if (fm != null) {
            int extra = Math.round(verticalPadding);
            fm.ascent -= extra;
            fm.top -= extra;
            fm.descent += extra;
            fm.bottom += extra;
        }
        return Math.round(paint.measureText(text, start, end) + (horizontalPadding * 2f));
    }

    @Override
    public void draw(Canvas canvas, CharSequence text, int start, int end, float x, int top, int y, int bottom, Paint paint) {
        float textWidth = paint.measureText(text, start, end);
        float boxWidth = textWidth + (horizontalPadding * 2f);

        // Calculate bounded rect for background box
        RectF rect = new RectF(x, top, x + boxWidth, bottom);

        int originalColor = paint.getColor();
        Paint.Style originalStyle = paint.getStyle();

        // 1. Draw rounded box
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(boxColor);
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint);

        // 2. Strict baseline vertical and horizontal centering calculation
        float textX = x + horizontalPadding;
        float textY = rect.centerY() - ((paint.descent() + paint.ascent()) / 2f);

        // 3. Subtle shadow depth
        float[] hsv = new float[3];
        Color.colorToHSV(textColor, hsv);
        int depthColor = Color.HSVToColor(new float[]{hsv[0], hsv[1], Math.max(hsv[2] * 0.35f, 0.08f)});
        paint.setColor(depthColor);
        canvas.drawText(text, start, end, textX + 1.5f, textY + 1.5f, paint);

        // 4. Foreground active text centered
        paint.setColor(textColor);
        canvas.drawText(text, start, end, textX, textY, paint);

        paint.setColor(originalColor);
        paint.setStyle(originalStyle);
    }
}
