package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

/**
 * Builds a libass-compatible .ass subtitle file from the recognized
 * captions, using the same 4-5 word rolling window and per-word
 * highlight the on-screen preview uses.
 */
public class AssSubtitleBuilder {

    public static String build(
            List<Caption> captions,
            int videoWidth,
            int videoHeight,
            String fontName,
            float fontSizeSp,
            int highlightColor,
            int gravity,
            CaptionStyleOptions.CaptionStyleType style) {

        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ")
                .append(videoWidth).append("\nPlayResY: ").append(videoHeight).append("\n\n");

        float scaleFactor = videoWidth / 400f;
        int assFontSize = Math.round(fontSizeSp * scaleFactor);

        int alignment = alignmentForGravity(gravity);
        int marginV = Math.round(videoHeight * 0.06f);

        String primaryColor = toAssColor(0xFFCCCCCC);
        String highlightAss = toAssColor(highlightColor);
        String outlineColor = toAssColor(0xFF000000);

        // UPDATED: mapped to the new 8 style names.
        int borderStyle = (style == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT
                || style == CaptionStyleOptions.CaptionStyleType.KARAOKE_FLOW) ? 3 : 1;
        int outlineWidth = (style == CaptionStyleOptions.CaptionStyleType.BOUNCE
                || style == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH) ? 4 : 2;

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");
        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,%s,%s,%s,%s,0,0,0,0,100,100,0,0,%d,%d,0,%d,20,20,%d,1\n\n",
                fontName, assFontSize, primaryColor, highlightAss, outlineColor,
                outlineColor, borderStyle, outlineWidth, alignment, marginV));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        int wordsBefore = 2;
        int wordsAfter = 2;

        for (int matched = 0; matched < captions.size(); matched++) {
            Caption activeCap = captions.get(matched);
            int startIdx = Math.max(0, matched - wordsBefore);
            int endIdx = Math.min(captions.size() - 1, matched + wordsAfter);

            StringBuilder line = new StringBuilder();

            // NEW: One Word Punch only shows the active word in export too.
            if (style == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH) {
                line.append(buildActiveWordTag(style, highlightAss))
                        .append(activeCap.word).append("{\\r}");
            } else {
                for (int i = startIdx; i <= endIdx; i++) {
                    Caption cap = captions.get(i);
                    if (i == matched) {
                        line.append(buildActiveWordTag(style, highlightAss))
                                .append(cap.word).append("{\\r}");
                    } else {
                        line.append(cap.word);
                    }
                    if (i != endIdx) line.append(" ");
                }
            }

            sb.append("Dialogue: 0,")
                    .append(toAssTime(activeCap.startTime)).append(",")
                    .append(toAssTime(activeCap.endTime)).append(",")
                    .append("Default,,0,0,0,,").append(line).append("\n");
        }

        return sb.toString();
    }

    // UPDATED: mapped to the new 8 style names.
    private static String buildActiveWordTag(
            CaptionStyleOptions.CaptionStyleType style, String highlightAss) {
        switch (style) {
            case ONE_WORD_PUNCH:
                return "{\\fscx180\\fscy180\\c" + highlightAss + "}";
            case KARAOKE_FLOW:
            case BOX_HIGHLIGHT:
                return "{\\c" + highlightAss + "}";
            case BOUNCE:
                return "{\\fscx125\\fscy125\\c" + highlightAss + "}";
            case GLOW_POP:
                return "{\\fscx125\\fscy125\\c" + highlightAss + "\\blur4}";
            case GREEN_EMPHASIS:
                return "{\\c&H0000FF00&}"; // solid green
            case MINIMAL_CLEAN:
                return "{\\c&HFFFFFF&}"; // solid white, no emphasis
            case HIGHLIGHT_POP:
            default:
                return "{\\c" + highlightAss + "}";
        }
    }

    private static int alignmentForGravity(int gravity) {
        if (gravity == android.view.Gravity.TOP) return 8;
        if (gravity == android.view.Gravity.CENTER_VERTICAL) return 5;
        return 2; // bottom
    }

    private static String toAssColor(int argb) {
        int a = 255 - ((argb >> 24) & 0xFF);
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return String.format(Locale.US, "&H%02X%02X%02X%02X", a, b, g, r);
    }

    private static String toAssTime(float seconds) {
        int totalCs = Math.round(seconds * 100);
        int h = totalCs / 360000;
        int m = (totalCs / 6000) % 60;
        int s = (totalCs / 100) % 60;
        int cs = totalCs % 100;
        return String.format(Locale.US, "%d:%02d:%02d.%02d", h, m, s, cs);
    }
}
