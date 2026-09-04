package com.saad.autocaption;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
public class CaptionStyleOptions {
    public static class FontOption {
        public final String label;
        public final String assetFileName; // null = built-in system font
        public final Typeface builtInTypeface;
        public final String exportFamilyName; // family name written into the .ass file
        public final String systemFontFile;   // filename under /system/fonts (null for custom fonts)
        FontOption(String label, String assetFileName, Typeface builtInTypeface,
                   String exportFamilyName, String systemFontFile) {
            this.label = label;
            this.assetFileName = assetFileName;
            this.builtInTypeface = builtInTypeface;
            this.exportFamilyName = exportFamilyName;
            this.systemFontFile = systemFontFile;
        }
        @Override
        public String toString() {
            return label;
        }
    }
    public static class ColorOption {
        public final String label;
        public final int color;
        ColorOption(String label, int color) {
            this.label = label;
            this.color = color;
        }
        @Override
        public String toString() {
            return label;
        }
    }
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
    public enum CaptionStyleType {
        HIGHLIGHT_POP,
        GREEN_EMPHASIS,
        KARAOKE_FLOW,
        ONE_WORD_PUNCH,
        BOX_HIGHLIGHT,
        BOUNCE,
        GLOW_POP,
        MINIMAL_CLEAN
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
                new FontOption("Modern", null, Typeface.SANS_SERIF,
                        "Roboto", "Roboto-Regular.ttf"),
                new FontOption("Vintage", null, Typeface.SERIF,
                        "Noto Serif", "NotoSerif-Regular.ttf"),
                new FontOption("Swanky", null,
                        Typeface.create("cursive", Typeface.NORMAL),
                        "Roboto", "Roboto-Regular.ttf"),
                new FontOption("Aesthetic", null,
                        Typeface.create("sans-serif-light", Typeface.NORMAL),
                        "Roboto Light", "Roboto-Light.ttf"),
                new FontOption("Blissful Script",
                        "fonts/blissful_script.ttf", Typeface.SERIF,
                        "Blissful Script", null),
                new FontOption("Blafhy Glibs",
                        "fonts/blafhy_glibs.ttf", Typeface.SANS_SERIF,
                        "Blafhy Glibs", null),
        };
    }
    // NEW: all preset swatches bumped to fully-saturated "Material
    // Accent" shades so every preset reads as sharp/bright on video,
    // matching the hue-only custom picker's guarantee of full
    // saturation + full brightness.
    public static ColorOption[] getColorOptions() {
        return new ColorOption[]{
                new ColorOption("Yellow", 0xFFFFEA00),
                new ColorOption("White", 0xFFFFFFFF),
                new ColorOption("Red", 0xFFFF1744),
                new ColorOption("Green", 0xFF00E676),
                new ColorOption("Cyan", 0xFF00E5FF),
                new ColorOption("Pink", 0xFFF50057),
                new ColorOption("Orange", 0xFFFF6D00),
                new ColorOption("Custom...", 0),
        };
    }
    public static FontSizeOption[] getFontSizeOptions() {
        return new FontSizeOption[]{
                new FontSizeOption("Small", 18f),
                new FontSizeOption("Medium", 22f),
                new FontSizeOption("Large", 26f),
                new FontSizeOption("X-Large", 30f),
                new FontSizeOption("Huge", 36f),
        };
    }
    public static PositionOption[] getPositionOptions() {
        return new PositionOption[]{
                new PositionOption("Top", Gravity.TOP),
                new PositionOption("Middle", Gravity.CENTER_VERTICAL),
                new PositionOption("Bottom", Gravity.BOTTOM),
        };
    }
    public static StyleOption[] getStyleOptions() {
        return new StyleOption[]{
                new StyleOption("Highlight Pop", CaptionStyleType.HIGHLIGHT_POP),
                new StyleOption("Green Emphasis", CaptionStyleType.GREEN_EMPHASIS),
                new StyleOption("Karaoke Flow", CaptionStyleType.KARAOKE_FLOW),
                new StyleOption("One Word Punch", CaptionStyleType.ONE_WORD_PUNCH),
                new StyleOption("Box Highlight", CaptionStyleType.BOX_HIGHLIGHT),
                new StyleOption("Bounce Caption", CaptionStyleType.BOUNCE),
                new StyleOption("Glow Pop", CaptionStyleType.GLOW_POP),
                new StyleOption("Minimal Clean", CaptionStyleType.MINIMAL_CLEAN),
        };
    }
    public static Typeface resolveTypeface(Context context, FontOption option) {
        if (option.assetFileName != null) {
            try {
                return Typeface.createFromAsset(
                        context.getAssets(), option.assetFileName);
            } catch (Exception e) {
                return option.builtInTypeface;
            }
        }
        return option.builtInTypeface;
    }
    /** Copies whichever font file the export needs (asset or system font)
     * into targetDir so ffmpeg's subtitle renderer can find it, and
     * returns the font family name to use in the .ass file. Falls back
     * to Roboto if the requested font can't be found. */
    public static String prepareExportFont(Context context, FontOption option, File targetDir)
            throws Exception {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        if (option.assetFileName != null) {
            String baseName = option.assetFileName.substring(
                    option.assetFileName.lastIndexOf('/') + 1);
            File dest = new File(targetDir, baseName);
            try (InputStream in = context.getAssets().open(option.assetFileName);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
                return option.exportFamilyName;
            } catch (Exception e) {
                // Asset not added yet — fall through to Roboto fallback below.
            }
        } else if (option.systemFontFile != null) {
            File systemFile = new File("/system/fonts/" + option.systemFontFile);
            if (systemFile.exists()) {
                File dest = new File(targetDir, option.systemFontFile);
                try (InputStream in = new java.io.FileInputStream(systemFile);
                     OutputStream out = new FileOutputStream(dest)) {
                    copyStream(in, out);
                    return option.exportFamilyName;
                }
            }
        }

        File robotoSrc = new File("/system/fonts/Roboto-Regular.ttf");
        if (robotoSrc.exists()) {
            File dest = new File(targetDir, "Roboto-Regular.ttf");
            try (InputStream in = new java.io.FileInputStream(robotoSrc);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
            }
        }
        return "Roboto";
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
    }
}
