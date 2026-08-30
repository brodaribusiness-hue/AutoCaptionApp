package com.saad.autocaption;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;

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

    // NEW: 5 font sizes
    public static class FontSizeOption {
        public final String label;
        public final float sizeSp;

        FontSizeOption(String label, float sizeSp) {
            this.label = label;
            this.sizeSp = sizeSp;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // NEW: Top / Middle / Bottom position
    public static class PositionOption {
        public final String label;
        public final int gravity;

        PositionOption(String label, int gravity) {
            this.label = label;
            this.gravity = gravity;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // NEW: 5 caption styles (Glow + 4 new)
    public enum CaptionStyleType {
        GLOW, OUTLINE, BACKGROUND_BOX, KARAOKE_FILL, POP_SCALE
    }

    public static class StyleOption {
        public final String label;
        public final CaptionStyleType type;

        StyleOption(String label, CaptionStyleType type) {
            this.label = label;
            this.type = type;
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

    // NEW
    public static FontSizeOption[] getFontSizeOptions() {
        return new FontSizeOption[]{
                new FontSizeOption("Small", 18f),
                new FontSizeOption("Medium", 22f),
                new FontSizeOption("Large", 26f),
                new FontSizeOption("X-Large", 30f),
                new FontSizeOption("Huge", 36f),
        };
    }

    // NEW
    public static PositionOption[] getPositionOptions() {
        return new PositionOption[]{
                new PositionOption("Top", Gravity.TOP),
                new PositionOption("Middle", Gravity.CENTER_VERTICAL),
                new PositionOption("Bottom", Gravity.BOTTOM),
        };
    }

    // NEW
    public static StyleOption[] getStyleOptions() {
        return new StyleOption[]{
                new StyleOption("Glow", CaptionStyleType.GLOW),
                new StyleOption("Outline", CaptionStyleType.OUTLINE),
                new StyleOption("Background Box", CaptionStyleType.BACKGROUND_BOX),
                new StyleOption("Karaoke Fill", CaptionStyleType.KARAOKE_FILL),
                new StyleOption("Pop / Scale", CaptionStyleType.POP_SCALE),
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
