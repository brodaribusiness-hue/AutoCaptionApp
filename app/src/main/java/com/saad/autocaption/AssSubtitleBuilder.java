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

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");

        // Alignment 5 is direct Center
        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,1,0,0,0,100,100,0,0,1,2,0,5,20,20,20,1\n\n",
                configActive.fontOption.exportFamilyName, assFontSize, outlineColor, outlineColor));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        float previewToVideoX = videoWidth / (float) Math.max(previewWidthPx, 1);
        float previewToVideoY = videoHeight / (float) Math.max(previewHeightPx, 1);

        int baseX = videoWidth / 2;
        int baseY = videoHeight / 2;

        boolean oneWordPunch = configActive.styleType == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;
        boolean cumulativeBuildUp = configActive.styleType == CaptionStyleOptions.CaptionStyleType.CUMULATIVE_BUILD_UP;
        List<CaptionGrouper.Group> groups = CaptionGrouper.group(captions, GROUP_SIZE);

        for (CaptionGrouper.Group group : groups) {
            List<Caption> words = group.words;
            if (words == null || words.isEmpty()) continue;

            float groupEndTime = group.endTime;

            if (cumulativeBuildUp) {
                // Typewriter / Cumulative Word Build-up: Each spoken word enters and STAYS until group end
                for (int pos = 0; pos < words.size(); pos++) {
                    Caption cap = words.get(pos);
                    String startTime = toAssTime(cap.startTime);
                    String endTime = toAssTime(groupEndTime);

                    CaptionSlotTransform slot = (pos == 0) ? beforeSlot : (pos == 1 ? activeSlot : afterSlot);
                    SlotStyleConfig cfg = (pos == 1) ? configActive : (pos == 0 ? configBefore : configAfter);

                    int wordCol = cap.resolveColor(cfg.textColor);
                    String wordColAss = toAssColor(wordCol);
                    String wordTag = buildWordStyleTag(cfg.styleType, wordColAss, cfg.fontOption.exportFamilyName, true);

                    appendWordLine(sb, startTime, endTime, cap.word,
                            slot, baseX, baseY, previewToVideoX, previewToVideoY, wordTag);
                }
            } else if (oneWordPunch) {
                for (int j = 0; j < words.size(); j++) {
                    Caption activeWord = words.get(j);
                    String startTime = toAssTime(activeWord.startTime);
                    String endTime = toAssTime(activeWord.endTime);

                    int col = activeWord.resolveColor(configActive.textColor);
                    String colAss = toAssColor(col);
                    String tag = buildWordStyleTag(configActive.styleType, colAss, configActive.fontOption.exportFamilyName, true);

                    appendWordLine(sb, startTime, endTime, activeWord.word,
                            activeSlot, baseX, baseY, previewToVideoX, previewToVideoY, tag);
                }
            } else {
                // Sequential Glowing Flow: All 3 words stay on screen; speaking word receives active tag
                for (int j = 0; j < words.size(); j++) {
                    Caption activeWord = words.get(j);
                    String startTime = toAssTime(activeWord.startTime);
                    String endTime = toAssTime(activeWord.endTime);

                    for (int pos = 0; pos < words.size(); pos++) {
                        Caption cap = words.get(pos);
                        boolean isSpeaking = (pos == j);

                        SlotStyleConfig cfg = isSpeaking ? configActive : (pos < j ? configBefore : configAfter);
                        CaptionSlotTransform slot = (pos == 0) ? beforeSlot : (pos == 1 ? activeSlot : afterSlot);

                        int wordCol = cap.resolveColor(cfg.textColor);
                        String wordColAss = toAssColor(wordCol);
                        String wordTag = buildWordStyleTag(cfg.styleType, wordColAss, cfg.fontOption.exportFamilyName, isSpeaking);

                        appendWordLine(sb, startTime, endTime, cap.word,
                                slot, baseX, baseY, previewToVideoX, previewToVideoY, wordTag);
                    }
                }
            }
        }

        return sb.toString();
    }

    private static void appendWordLine(
            StringBuilder sb, String startTime, String endTime, String word,
            CaptionSlotTransform slot, int baseX, int baseY,
            float previewToVideoX, float previewToVideoY, String styleTag) {

        int posX = baseX + Math.round(slot.translationX * previewToVideoX);
        int posY = baseY + Math.round(slot.translationY * previewToVideoY);
        int scalePercent = Math.round(slot.scale * 100);

        sb.append("Dialogue: 0,").append(startTime).append(",").append(endTime)
                .append(",Default,,0,0,0,,")
                .append("{\\pos(").append(posX).append(",").append(posY).append(")")
                .append("\\fscx").append(scalePercent).append("\\fscy").append(scalePercent)
                .append(styleTag).append("}")
                .append(word).append("\n");
    }

    private static String buildWordStyleTag(
            CaptionStyleOptions.CaptionStyleType style, String colorAss, String fontName, boolean isSpeaking) {
        String base = "\\fn" + fontName;

        if (!isSpeaking) {
            return base + "\\c" + colorAss + "\\bord2\\shad0";
        }

        switch (style) {
            case GLOW_POP:
                return base + "\\c" + colorAss + "\\bord4\\shad0\\blur8\\3c" + colorAss + "\\fscx112\\fscy112\\b1";
            case HIGHLIGHT_POP:
                return base + "\\c" + colorAss + "\\fscx118\\fscy118\\b1";
            case GREEN_EMPHASIS:
                return base + "\\c" + toAssColor(GREEN_EMPHASIS_COLOR) + "\\fscx110\\fscy110\\b1";
            case BOUNCE:
                return base + "\\c" + colorAss + "\\t(0,200,\\fscx120\\fscy120)\\t(200,400,\\fscx100\\fscy100)";
            case ONE_WORD_PUNCH:
                return base + "\\c" + colorAss + "\\fscx160\\fscy160\\b1";
            case CUMULATIVE_BUILD_UP:
                return base + "\\c" + colorAss + "\\fscx108\\fscy108\\b1";
            case MINIMAL_CLEAN:
            default:
                return base + "\\c" + colorAss + "\\b1";
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
