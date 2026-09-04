package com.saad.autocaption;

import android.graphics.Typeface;

public class SlotStyleConfig {
    public CaptionStyleOptions.FontOption fontOption;
    public Typeface typeface;
    public int textColor;
    public CaptionStyleOptions.CaptionStyleType styleType;
    public int boxColor;

    public SlotStyleConfig(CaptionStyleOptions.FontOption fontOption,
                            Typeface typeface,
                            int textColor,
                            CaptionStyleOptions.CaptionStyleType styleType,
                            int boxColor) {
        this.fontOption = fontOption;
        this.typeface = typeface;
        this.textColor = textColor;
        this.styleType = styleType;
        this.boxColor = boxColor;
    }
}
