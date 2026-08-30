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

        // Preview text size is tuned against a ~400dp-wide screen; scale
        // proportionally to the real export resolution.
        float scaleFactor = videoWidth / 400f;
        int assFontSize = Math.round(fontSizeSp * scaleFactor);

        int alignment = alignmentForGravity(gravity);
        int marginV = Math.round(videoHeight * 0.06f);

        String primaryColor = toAssColor(0xFFCCCCCC);
        String highlightAss = toAssColor(highlightColor);
        String outlineColor = toAssColor(0xFF000000);

        int borderStyle = (style == CaptionStyleOptions.CaptionStyleType.BACKGROUND_BOX
                || style == CaptionStyleOptions.CaptionStyleType.KARAOKE_FILL) ? 3 : 1;
        int outlineWidth = (style == CaptionStyleOptions.CaptionStyleType.OUTLINE) ? 4 : 2;

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

            sb.append("Dialogue: 0,")
                    .append(toAssTime(activeCap.startTime)).append(",")
                    .append(toAssTime(activeCap.endTime)).append(",")
                    .append("Default,,0,0,0,,").append(line).append("\n");
        }

        return sb.toString();
    }

    private static String buildActiveWordTag(
            CaptionStyleOptions.CaptionStyleType style, String highlightAss) {
        switch (style) {
            case POP_SCALE:
                return "{\\fscx135\\fscy135\\c" + highlightAss + "}";
            case KARAOKE_FILL:
            case BACKGROUND_BOX:
                return "{\\c" + highlightAss + "}";
            case OUTLINE:
                return "{\\c" + highlightAss + "\\bord4}";
            case GLOW:
            default:
                return "{\\c" + highlightAss + "\\blur4}";
        }
    }

    private static int alignmentForGravity(int gravity) {
        if (gravity == android.view.Gravity.TOP) return 8;
        if (gravity == android.view.Gravity.CENTER_VERTICAL) return 5;
        return 2; // bottom
    }

    // ASS uses &HAABBGGRR — alpha inverted (00 = fully opaque).
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
