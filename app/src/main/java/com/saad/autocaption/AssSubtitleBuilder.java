package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    private static final int GROUP_SIZE = CaptionGrouper.DEFAULT_GROUP_SIZE;
    private static final int GREEN_EMPHASIS_COLOR = 0xFF00E676;

    public static String build(
            List<Caption> captions,
            int videoWidth,
            int videoHeight,
            int previewWidthPx,
            int previewHeightPx,
            float fontSizeSp,
            SlotStyleConfig configBefore,
            SlotStyleConfig configActive,
            SlotStyleConfig configAfter,
            CaptionSlotTransform beforeSlot,
            CaptionSlotTransform activeSlot,
            CaptionSlotTransform afterSlot) {

        StringBuilder sb = new StringBuilder();
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ")
                .append(videoWidth).append("\nPlayResY: ").append(videoHeight).append("\n\n");

        float scaleFactor = videoWidth / 400f;
        int assFontSize = Math.round(fontSizeSp * scaleFactor);

        String outlineColor = toAssColor(0xFF000000);
        String beforeBoxAss = toAssColor(configBefore.boxColor);
        String activeBoxAss = toAssColor(configActive.boxColor);
        String afterBoxAss = toAssColor(configAfter.boxColor);

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");

        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,1,2,0,2,20,20,20,1\n",
                configActive.fontOption.exportFamilyName, assFontSize, outlineColor, outlineColor));

        sb.append(String.format(Locale.US,
                "Style: BoxBefore,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,3,14,0,2,20,20,20,1\n",
                configBefore.fontOption.exportFamilyName, assFontSize, outlineColor, beforeBoxAss));

        sb.append(String.format(Locale.US,
                "Style: BoxActive,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,3,14,0,2,20,20,20,1\n",
                configActive.fontOption.exportFamilyName, assFontSize, outlineColor, activeBoxAss));

        sb.append(String.format(Locale.US,
                "Style: BoxAfter,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,0,0,0,0,100,100,0,0,3,14,0,2,20,20,20,1\n\n",
                configAfter.fontOption.exportFamilyName, assFontSize, outlineColor, afterBoxAss));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        float previewToVideoX = videoWidth / (float) Math.max(previewWidthPx, 1);
        float previewToVideoY = videoHeight / (float) Math.max(previewHeightPx, 1);

        int baseX = videoWidth / 2;
        int baseY = videoHeight - Math.round(videoHeight * 0.12f);

        boolean oneWordPunch = configActive.styleType == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;
        List<CaptionGrouper.Group> groups = CaptionGrouper.group(captions, GROUP_SIZE);

        for (CaptionGrouper.Group group : groups) {
            List<Caption> words = group.words;

            for (int j = 0; j < words.size(); j++) {
                Caption activeCap = words.get(j);
                String startTime = toAssTime(activeCap.startTime);
                String endTime = toAssTime(activeCap.endTime);

                if (!oneWordPunch && j - 1 >= 0) {
                    Caption beforeCap = words.get(j - 1);
                    int beforeColor = beforeCap.resolveColor(configBefore.textColor);
                    String beforeColorAss = toAssColor(beforeColor);
                    String beforeTag = buildWordStyleTag(configBefore.styleType, beforeColorAss, configBefore.fontOption.exportFamilyName);
                    String beforeStyle = (configBefore.styleType == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT) ? "BoxBefore" : "Default";

                    appendWordLine(sb, startTime, endTime, beforeCap.word,
                            beforeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                            beforeTag, beforeStyle);
                }

                int activeColor = activeCap.resolveColor(configActive.textColor);
                String activeColorAss = toAssColor(activeColor);
                String activeTag = buildWordStyleTag(configActive.styleType, activeColorAss, configActive.fontOption.exportFamilyName);
                String activeStyle = (configActive.styleType == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT) ? "BoxActive" : "Default";

                appendWordLine(sb, startTime, endTime, activeCap.word,
                        activeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                        activeTag, activeStyle);

                if (!oneWordPunch && j + 1 < words.size()) {
                    Caption afterCap = words.get(j + 1);
                    int afterColor = afterCap.resolveColor(configAfter.textColor);
                    String afterColorAss = toAssColor(afterColor);
                    String afterTag = buildWordStyleTag(configAfter.styleType, afterColorAss, configAfter.fontOption.exportFamilyName);
                    String afterStyle = (configAfter.styleType == CaptionStyleOptions.CaptionStyleType.BOX_HIGHLIGHT) ? "BoxAfter" : "Default";

                    appendWordLine(sb, startTime, endTime, afterCap.word,
                            afterSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                            afterTag, afterStyle);
                }
            }
        }

        return sb.toString();
    }

    private static void appendWordLine(
            StringBuilder sb, String startTime, String endTime, String word,
            CaptionSlotTransform slot, int baseX, int baseY,
            float previewToVideoX, float previewToVideoY, String styleTag,
            String styleName) {

        int posX = baseX + Math.round(slot.translationX * previewToVideoX);
        int posY = baseY + Math.round(slot.translationY * previewToVideoY);
        int scalePercent = Math.round(slot.scale * 100);

        sb.append("Dialogue: 0,").append(startTime).append(",").append(endTime)
                .append(",").append(styleName).append(",,0,0,0,,")
                .append("{\\pos(").append(posX).append(",").append(posY).append(")")
                .append("\\fscx").append(scalePercent).append("\\fscy").append(scalePercent)
                .append(styleTag).append("}")
                .append(word).append("\n");
    }

    private static String buildWordStyleTag(
            CaptionStyleOptions.CaptionStyleType style, String colorAss, String fontName) {
        String base = "\\fn" + fontName;
        switch (style) {
            case GREEN_EMPHASIS:
                return base + "\\c" + toAssColor(GREEN_EMPHASIS_COLOR) + "\\b1";
            case HIGHLIGHT_POP:
                return base + "\\c" + colorAss + "\\fscx115\\fscy115\\b1";
            case MINIMAL_CLEAN:
                return base + "\\c" + colorAss + "\\b1";
            case GLOW_POP:
                return base + "\\c" + colorAss + "\\blur6";
            case BOX_HIGHLIGHT:
                return base + "\\c" + colorAss + "\\bord3\\shad4";
            case BOUNCE:
                return base + "\\c" + colorAss + "\\t(0,250,\\fscx120\\fscy120)\\t(250,500,\\fscx100\\fscy100)";
            case KARAOKE_FLOW:
                return base + "\\c" + colorAss + "\\k50";
            case ONE_WORD_PUNCH:
                return base + "\\c" + colorAss + "\\fscx160\\fscy160\\b1";
            default:
                return base + "\\c" + colorAss;
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
