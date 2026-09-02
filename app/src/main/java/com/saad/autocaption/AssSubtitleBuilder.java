package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    public static String build(
            List<Caption> captions,
            int videoWidth,
            int videoHeight,
            int previewWidthPx,
            int previewHeightPx,
            String fontName,
            float fontSizeSp,
            int highlightColor,
            CaptionStyleOptions.CaptionStyleType style,
            CaptionSlotTransform beforeSlot,
            CaptionSlotTransform activeSlot,
            CaptionSlotTransform afterSlot) {

        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ")
                .append(videoWidth).append("\nPlayResY: ").append(videoHeight).append("\n\n");

        float scaleFactor = videoWidth / 400f;
        int assFontSize = Math.round(fontSizeSp * scaleFactor);

        String highlightAss = toAssColor(highlightColor);
        String outlineColor = toAssColor(0xFF000000);

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");
        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&HFFFFFF&,%s,%s,%s,0,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1\n\n",
                fontName, assFontSize, highlightAss, outlineColor, outlineColor));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        // \pos overrides the bottom-center anchor with an exact pixel
        // position, mapped from the preview's on-screen pixels into the
        // exported video's own resolution.
        float previewToVideoX = videoWidth / (float) Math.max(previewWidthPx, 1);
        float previewToVideoY = videoHeight / (float) Math.max(previewHeightPx, 1);

        int baseX = videoWidth / 2;
        int baseY = videoHeight - Math.round(videoHeight * 0.06f);

        boolean oneWordPunch = style == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;

        for (int i = 0; i < captions.size(); i++) {
            Caption activeCap = captions.get(i);
            String startTime = toAssTime(activeCap.startTime);
            String endTime = toAssTime(activeCap.endTime);

            if (!oneWordPunch && i - 1 >= 0) {
                appendWordLine(sb, startTime, endTime, captions.get(i - 1).word,
                        beforeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                        "\\c&HFFFFFF&");
            }

            String activeColorTag = buildActiveWordColorTag(style, highlightAss);
            appendWordLine(sb, startTime, endTime, activeCap.word,
                    activeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                    activeColorTag);

            if (!oneWordPunch && i + 1 < captions.size()) {
                appendWordLine(sb, startTime, endTime, captions.get(i + 1).word,
                        afterSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                        "\\c&HFFFFFF&");
            }
        }

        return sb.toString();
    }

    private static void appendWordLine(
            StringBuilder sb, String startTime, String endTime, String word,
            CaptionSlotTransform slot, int baseX, int baseY,
            float previewToVideoX, float previewToVideoY, String colorTag) {

        int posX = baseX + Math.round(slot.translationX * previewToVideoX);
        int posY = baseY + Math.round(slot.translationY * previewToVideoY);
        int scalePercent = Math.round(slot.scale * 100);

        sb.append("Dialogue: 0,").append(startTime).append(",").append(endTime)
                .append(",Default,,0,0,0,,")
                .append("{\\pos(").append(posX).append(",").append(posY).append(")")
                .append("\\fscx").append(scalePercent).append("\\fscy").append(scalePercent)
                .append(colorTag).append("}")
                .append(word).append("\n");
    }

    private static String buildActiveWordColorTag(
            CaptionStyleOptions.CaptionStyleType style, String highlightAss) {
        switch (style) {
            case GREEN_EMPHASIS:
                return "\\c&H0000FF00&";
            case MINIMAL_CLEAN:
                return "\\c&HFFFFFF&";
            case GLOW_POP:
                return "\\c" + highlightAss + "\\blur4";
            default:
                return "\\c" + highlightAss;
        }
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
