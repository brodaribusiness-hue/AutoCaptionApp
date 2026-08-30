package com.saad.autocaption;

import android.content.Context;
import android.graphics.Typeface;

public class CaptionStyleOptions {

    public static class FontOption {
        public final String label;
        public final String assetFileName; // null = use builtInTypeface
        public final Typeface builtInTypeface;

        FontOption(String label, String assetFileName, Typeface builtInTypeface) {
            this.label = label;
            this.assetFileName = assetFileName;
            this.builtInTypeface = builtInTypeface;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static class ColorOption {
        public final String label;
        public final int color; // 0 means "Custom" (color picked by user)

        ColorOption(String label, int color) {
            this.label = label;
            this.color = color;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static FontOption[] getFontOptions() {
        return new FontOption[]{
                new FontOption("Modern", null, Typeface.SANS_SERIF),
                new FontOption("Vintage", null, Typeface.SERIF),
                new FontOption("Swanky", null,
                        Typeface.create("cursive", Typeface.NORMAL)),
                new FontOption("Aesthetic", null,
                        Typeface.create("sans-serif-light", Typeface.NORMAL)),
                new FontOption("Blissful Script",
                        "fonts/blissful_script.ttf", Typeface.SERIF),
                new FontOption("Blafhy Glibs",
                        "fonts/blafhy_glibs.ttf", Typeface.SANS_SERIF),
        };
    }

    public static ColorOption[] getColorOptions() {
        return new ColorOption[]{
                new ColorOption("Yellow", 0xFFFFEB3B),
                new ColorOption("White", 0xFFFFFFFF),
                new ColorOption("Red", 0xFFFF5252),
                new ColorOption("Green", 0xFF4CAF50),
                new ColorOption("Cyan", 0xFF00E5FF),
                new ColorOption("Pink", 0xFFFF4081),
                new ColorOption("Orange", 0xFFFF9800),
                new ColorOption("Custom...", 0),
        };
    }

    /** Loads a custom font from assets, falling back to a built-in
     * typeface if the file isn't present yet. Never throws. */
    public static Typeface resolveTypeface(Context context, FontOption option) {
        if (option.assetFileName != null) {
            try {
                return Typeface.createFromAsset(
                        context.getAssets(), option.assetFileName);
            } catch (Exception e) {
                // Font file not added yet — fall back silently.
                return option.builtInTypeface;
            }
        }
        return option.builtInTypeface;
    }
}
