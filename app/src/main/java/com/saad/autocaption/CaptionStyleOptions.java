package com.saad.autocaption;

import android.content.Context;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class CaptionStyleOptions {

    public static class FontOption {
        public final String label;
        public final String assetFileName;
        public final Typeface builtInTypeface;
        public final String exportFamilyName;
        public final String systemFontFile;

        FontOption(String label, String assetFileName, Typeface builtInTypeface, String exportFamilyName, String systemFontFile) {
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

    public enum CaptionStyleType {
        GLOW_POP,
        HIGHLIGHT_POP,
        GREEN_EMPHASIS,
        CUMULATIVE_BUILD_UP,
        BOUNCE,
        ONE_WORD_PUNCH,
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
                new FontOption("Modern (Roboto)", null, Typeface.SANS_SERIF, "Roboto", "Roboto-Regular.ttf"),
                new FontOption("Serif Classic", null, Typeface.SERIF, "Noto Serif", "NotoSerif-Regular.ttf"),
                new FontOption("Abril Fatface", "fonts/AbrilFatface-Regular.ttf", Typeface.SERIF, "Abril Fatface", null),
                new FontOption("Blaka Ink", "fonts/BlakaInk-Regular.ttf", Typeface.SANS_SERIF, "Blaka Ink", null),
                new FontOption("Chau Philomene Regular", "fonts/ChauPhilomeneOne-Regular.ttf", Typeface.SANS_SERIF, "Chau Philomene One", null),
                new FontOption("Chau Philomene Italic", "fonts/ChauPhilomeneOne-Italic.ttf", Typeface.SANS_SERIF, "Chau Philomene One", null),
                new FontOption("Comic Relief Bold", "fonts/ComicRelief-Bold.ttf", Typeface.SANS_SERIF, "Comic Relief", null),
                new FontOption("Comic Relief Regular", "fonts/ComicRelief-Regular.ttf", Typeface.SANS_SERIF, "Comic Relief", null),
                new FontOption("Jost Bold", "fonts/Jost-Bold.ttf", Typeface.SANS_SERIF, "Jost", null),
                new FontOption("Jost ExtraLight Italic", "fonts/Jost-ExtraLightItalic.ttf", Typeface.SANS_SERIF, "Jost", null),
                new FontOption("Lilita One", "fonts/LilitaOne-Regular.ttf", Typeface.SANS_SERIF, "Lilita One", null),
                new FontOption("Lugrasimo", "fonts/Lugrasimo-Regular.ttf", Typeface.SERIF, "Lugrasimo", null),
                new FontOption("Montserrat Alt ExtraBold", "fonts/MontserratAlternates-ExtraBold.ttf", Typeface.SANS_SERIF, "Montserrat Alternates", null),
                new FontOption("Montserrat Alt BlackItalic", "fonts/MontserratAlternates-BlackItalic.ttf", Typeface.SANS_SERIF, "Montserrat Alternates", null),
                new FontOption("Petit Formal Script", "fonts/PetitFormalScript-Regular.ttf", Typeface.SERIF, "Petit Formal Script", null),
                new FontOption("Playfair Display Regular", "fonts/PlayfairDisplay-Regular.ttf", Typeface.SERIF, "Playfair Display", null),
                new FontOption("Playfair Display Bold", "fonts/PlayfairDisplay-Bold.ttf", Typeface.SERIF, "Playfair Display", null),
                new FontOption("Playfair Display Black", "fonts/PlayfairDisplay-Black.ttf", Typeface.SERIF, "Playfair Display", null),
                new FontOption("Playfair Display Italic", "fonts/PlayfairDisplay-Italic.ttf", Typeface.SERIF, "Playfair Display", null),
                new FontOption("Playfair Display SemiBold Italic", "fonts/PlayfairDisplay-SemiBoldItalic.ttf", Typeface.SERIF, "Playfair Display", null),
                new FontOption("Reddit Mono Bold", "fonts/RedditMono-Bold.ttf", Typeface.MONOSPACE, "Reddit Mono", null),
                new FontOption("Reddit Mono Regular", "fonts/RedditMono-Regular.ttf", Typeface.MONOSPACE, "Reddit Mono", null),
                new FontOption("Open Sans Regular", "fonts/OpenSans-Regular.ttf", Typeface.SANS_SERIF, "Open Sans", null),
                new FontOption("Open Sans Italic", "fonts/OpenSans-Italic.ttf", Typeface.SANS_SERIF, "Open Sans", null)
        };
    }

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

    public static StyleOption[] getStyleOptions() {
        return new StyleOption[]{
                new StyleOption("Glow Pop (Neon)", CaptionStyleType.GLOW_POP),
                new StyleOption("Word Build-Up (Typewriter)", CaptionStyleType.CUMULATIVE_BUILD_UP),
                new StyleOption("Highlight Pop", CaptionStyleType.HIGHLIGHT_POP),
                new StyleOption("Green Emphasis", CaptionStyleType.GREEN_EMPHASIS),
                new StyleOption("Bounce Caption", CaptionStyleType.BOUNCE),
                new StyleOption("One Word Punch", CaptionStyleType.ONE_WORD_PUNCH),
                new StyleOption("Minimal Clean", CaptionStyleType.MINIMAL_CLEAN),
        };
    }

    public static Typeface resolveTypeface(Context context, FontOption option) {
        if (option != null && option.assetFileName != null) {
            try {
                return Typeface.createFromAsset(context.getAssets(), option.assetFileName);
            } catch (Exception e) {
                return option.builtInTypeface != null ? option.builtInTypeface : Typeface.DEFAULT;
            }
        }
        return (option != null && option.builtInTypeface != null) ? option.builtInTypeface : Typeface.DEFAULT;
    }

    public static String prepareExportFont(Context context, FontOption option, File targetDir) throws Exception {
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        if (option != null && option.assetFileName != null) {
            String baseName = option.assetFileName.substring(option.assetFileName.lastIndexOf('/') + 1);
            File dest = new File(targetDir, baseName);
            try (InputStream in = context.getAssets().open(option.assetFileName);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
                return option.exportFamilyName;
            } catch (Exception ignored) {}
        } else if (option != null && option.systemFontFile != null) {
            File systemFile = new File("/system/fonts/" + option.systemFontFile);
            if (systemFile.exists()) {
                File dest = new File(targetDir, option.systemFontFile);
                try (InputStream in = new java.io.FileInputStream(systemFile);
                     OutputStream out = new FileOutputStream(dest)) {
                    copyStream(in, out);
                    return option.exportFamilyName;
                } catch (Exception ignored) {}
            }
        }

        File robotoSrc = new File("/system/fonts/Roboto-Regular.ttf");
        if (robotoSrc.exists()) {
            File dest = new File(targetDir, "Roboto-Regular.ttf");
            try (InputStream in = new java.io.FileInputStream(robotoSrc);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
            } catch (Exception ignored) {}
        }
        return "Roboto";
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int len;
        while ((len = in.read(buffer)) != -1) {
            out.write(buffer, 0, len);
        }
        out.flush();
    }
}
