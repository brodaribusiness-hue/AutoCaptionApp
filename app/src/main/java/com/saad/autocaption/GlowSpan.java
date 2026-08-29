package com.saad.autocaption;

import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.text.style.UpdateAppearance;

public class GlowSpan extends CharacterStyle implements UpdateAppearance {

    private final int textColor;
    private final int glowColor;
    private final float glowRadius;

    public GlowSpan(int textColor, int glowColor, float glowRadius) {
        this.textColor = textColor;
        this.glowColor = glowColor;
        this.glowRadius = glowRadius;
    }

    @Override
    public void updateDrawState(TextPaint tp) {
        tp.setColor(textColor);
        tp.setShadowLayer(glowRadius, 0, 0, glowColor);
    }
}
