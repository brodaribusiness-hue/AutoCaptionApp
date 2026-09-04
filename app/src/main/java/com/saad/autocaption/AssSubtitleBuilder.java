package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    private static final int GROUP_SIZE = CaptionGrouper.DEFAULT_GROUP_SIZE;
    private static final int HIGHLIGHT_POP_COLOR = 0xFFFF3D00;
    private static final int GREEN_EMPHASIS_COLOR = 0xFF00E676;

    public static String build(
            List<Caption> captions,
            int videoWidth,
            int videoHeight,
            int previewWidthPx,
            int previewHeightPx,
            String fontName,
            float fontSizeSp,
            int highlightColor,
            int boxBackgroundColor,
            CaptionStyleOptions.CaptionStyleType style,
            CaptionSlotTransform beforeSlot,
            CaptionSlotTransform activeSlot,
            CaptionSlotTransform afterSlot) {

        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ")
                .append(videoWidth).append("\nPlayResY: ").append(videoHeight).append("\n\n");

        float scaleFactor = videoWidth / 400f;
        int assFontSize = Math.round(fontSizeSp * scaleFactor);

        String outlineColor = toAssColor(0xFF000000);
        String boxAss = toAssColor(boxBackgroundColor);

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");

        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1\n",
                fontName, assFontSize, outlineColor, outlineColor));

        sb.append(String.format(Locale.US,
                "Style: BoxHighlight,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,3,14,0,2,20,20,20,1\n\n",
                fontName, assFontSize, outlineColor, boxAss));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        float previewToVideoX = videoWidth / (float) Math.max(previewWidthPx, 1);
        float previewToVideoY = videoHeight / (float) Math.max(previewHeightPx, 1);

        int baseX = videoWidth / 2;
        int baseY = videoHeight - Math.round(videoHeight * 0.06f);

        boolean oneWordPunch = style == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;
        boolean boxHighlight = style == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT;

        List<CaptionGrouper.Group> groups = CaptionGrouper.group(captions, GROUP_SIZE);

        for (CaptionGrouper.Group group : groups) {
            List<Caption> words = group.words;

            for (int j = 0; j < words.size(); j++) {
                Caption activeCap = words.get(j);
                String startTime = toAssTime(activeCap.startTime);
                String endTime = toAssTime(activeCap.endTime);

                if (!oneWordPunch && j - 1 >= 0) {
                    appendWordLine(sb, startTime, endTime, words.get(j - 1).word,
                            beforeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                            "\\c&HCCCCCC&", "Default");
                }

                String wordHighlightAss = toAssColor(activeCap.resolveColor(highlightColor));
                String activeColorTag = buildActiveWordColorTag(style, wordHighlightAss);
                String activeStyleName = boxHighlight ? "BoxHighlight" : "Default";

                appendWordLine(sb, startTime, endTime, activeCap.word,
                        activeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                        activeColorTag, activeStyleName);

                if (!oneWordPunch && j + 1 < words.size()) {
                    appendWordLine(sb, startTime, endTime, words.get(j + 1).word,
                            afterSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                            "\\c&HCCCCCC&", "Default");
                }
            }
        }

        return sb.toString();
    }

    private static void appendWordLine(
            StringBuilder sb, String startTime, String endTime, String word,
            CaptionSlotTransform slot, int baseX, int baseY,
            float previewToVideoX, float previewToVideoY, String colorTag,
            String styleName) {

        int posX = baseX + Math.round(slot.translationX * previewToVideoX);
        int posY = baseY + Math.round(slot.translationY * previewToVideoY);
        int scalePercent = Math.round(slot.scale * 100);

        sb.append("Dialogue: 0,").append(startTime).append(",").append(endTime)
                .append(",").append(styleName).append(",,0,0,0,,")
                .append("{\\pos(").append(posX).append(",").append(posY).append(")")
                .append("\\fscx").append(scalePercent).append("\\fscy").append(scalePercent)
                .append(colorTag).append("}")
                .append(word).append("\n");
    }

    private static String buildActiveWordColorTag(
            CaptionStyleOptions.CaptionStyleType style, String highlightAss) {
        switch (style) {
            case GREEN_EMPHASIS:
                return "\\c" + toAssColor(GREEN_EMPHASIS_COLOR) + "\\b1";
            case HIGHLIGHT_POP:
                return "\\c" + toAssColor(HIGHLIGHT_POP_COLOR) + "\\b1";
            case MINIMAL_CLEAN:
                return "\\c&HFFFFFF&\\b1";
            case GLOW_POP:
                return "\\c" + highlightAss + "\\blur6";
            case BOX_HIGHLIGHT:
                return "\\c" + highlightAss + "\\bord3\\shad4";
            case BOUNCE:
                return "\\c" + highlightAss + "\\t(0,250,\\fscx120\\fscy120)\\t(250,500,\\fscx100\\fscy100)";
            case KARAOKE_FLOW:
                return "\\c" + highlightAss + "\\k50";
            case ONE_WORD_PUNCH:
                return "\\c" + highlightAss + "\\fscx160\\fscy160\\b1";
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
