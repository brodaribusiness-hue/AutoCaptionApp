package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    private static final int GROUP_SIZE = CaptionGrouper.DEFAULT_GROUP_SIZE; //[span_7](start_span)[span_7](end_span)[span_8](start_span)[span_8](end_span)
    private static final int GREEN_EMPHASIS_COLOR = 0xFF00E676; //[span_9](start_span)[span_9](end_span)

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

        // Crisp Vector Font Scaling based on 720p base standard
        float scaleFactor = (float) videoHeight / 720f;
        int assFontSize = Math.round(fontSizeSp * 2.2f * scaleFactor);

        String outlineColor = toAssColor(0xFF000000);

        sb.append("[V4+ Styles]\n");
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, "
                + "OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, "
                + "ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n");

        // Alignment 2 = Bottom-Center (Matches Android bottom-gravity layout perfectly)
        int marginV = Math.round(videoHeight * 0.08f);
        sb.append(String.format(Locale.US,
                "Style: Default,%s,%d,&HFFFFFF&,&HFFFFFF&,%s,%s,1,0,0,0,100,100,0,0,1,3,0,2,20,20,%d,1\n\n",
                configActive.fontOption.exportFamilyName, assFontSize, outlineColor, outlineColor, marginV));

        sb.append("[Events]\n");
        sb.append("Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n");

        float previewToVideoX = videoWidth / (float) Math.max(previewWidthPx, 1);
        float previewToVideoY = videoHeight / (float) Math.max(previewHeightPx, 1);

        // Map base coordinates: X to center, Y to bottom margin baseline
        int baseX = videoWidth / 2;
        int baseY = videoHeight - marginV;

        boolean oneWordPunch = configActive.styleType == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;
        boolean cumulativeBuildUp = configActive.styleType == CaptionStyleOptions.CaptionStyleType.CUMULATIVE_BUILD_UP;
        List<CaptionGrouper.Group> groups = CaptionGrouper.group(captions, GROUP_SIZE); //[span_10](start_span)[span_10](end_span)

        for (CaptionGrouper.Group group : groups) {
            List<Caption> words = group.words;
            if (words == null || words.isEmpty()) continue;

            float groupEndTime = group.endTime;

            if (cumulativeBuildUp) {
                for (int pos = 0; pos < words.size(); pos++) {
                    Caption cap = words.get(pos);
                    String startTime = toAssTime(cap.startTime);
                    String endTime = toAssTime(groupEndTime);

                    CaptionSlotTransform slot = (pos == 0) ? beforeSlot : (pos == 1 ? activeSlot : afterSlot);
                    SlotStyleConfig cfg = (pos == 1) ? configActive : (pos == 0 ? configBefore : configAfter);

                    int wordCol = cap.resolveColor(cfg.textColor);
                    String wordColAss = toAssColor(wordCol);
                    String wordTag = buildWordStyleTag(cfg.styleType, wordColAss, cfg.fontOption.exportFamilyName, true, slot.scale);

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
                    String tag = buildWordStyleTag(configActive.styleType, colAss, configActive.fontOption.exportFamilyName, true, activeSlot.scale);

                    appendWordLine(sb, startTime, endTime, activeWord.word,
                            activeSlot, baseX, baseY, previewToVideoX, previewToVideoY, tag);
                }
            } else {
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
                        String wordTag = buildWordStyleTag(cfg.styleType, wordColAss, cfg.fontOption.exportFamilyName, isSpeaking, slot.scale);

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

        sb.append("Dialogue: 0,").append(startTime).append(",").append(endTime)
                .append(",Default,,0,0,0,,")
                .append("{\\pos(").append(posX).append(",").append(posY).append(")")
                .append(styleTag).append("}")
                .append(word).append("\n");
    }

    private static String buildWordStyleTag(
            CaptionStyleOptions.CaptionStyleType style, String colorAss, String fontName, boolean isSpeaking, float userScale) {
        String base = "\\fn" + fontName;
        int baseScalePercent = Math.round(userScale * 100f);

        if (!isSpeaking) {
            return base + "\\c" + colorAss + "\\bord2\\shad0\\fscx" + baseScalePercent + "\\fscy" + baseScalePercent;
        }

        // Active Speaking Word effects with scale multiplier applied directly once
        switch (style) {
            case GLOW_POP:
                int glowScale = Math.round(baseScalePercent * 1.12f);
                return base + "\\c" + colorAss + "\\bord4\\shad0\\blur8\\3c" + colorAss 
                        + "\\fscx" + glowScale + "\\fscy" + glowScale + "\\b1";
            case HIGHLIGHT_POP:
                int popScale = Math.round(baseScalePercent * 1.18f);
                return base + "\\c" + colorAss + "\\fscx" + popScale + "\\fscy" + popScale + "\\b1";
            case GREEN_EMPHASIS:
                int greenScale = Math.round(baseScalePercent * 1.10f);
                return base + "\\c" + toAssColor(GREEN_EMPHASIS_COLOR) 
                        + "\\fscx" + greenScale + "\\fscy" + greenScale + "\\b1";
            case BOUNCE:
                int bounceMax = Math.round(baseScalePercent * 1.20f);
                return base + "\\c" + colorAss 
                        + "\\t(0,200,\\fscx" + bounceMax + "\\fscy" + bounceMax + ")"
                        + "\\t(200,400,\\fscx" + baseScalePercent + "\\fscy" + baseScalePercent + ")";
            case ONE_WORD_PUNCH:
                int punchScale = Math.round(baseScalePercent * 1.50f);
                return base + "\\c" + colorAss + "\\fscx" + punchScale + "\\fscy" + punchScale + "\\b1";
            case CUMULATIVE_BUILD_UP:
                int cumScale = Math.round(baseScalePercent * 1.08f);
                return base + "\\c" + colorAss + "\\fscx" + cumScale + "\\fscy" + cumScale + "\\b1";
            case MINIMAL_CLEAN:
            default:
                return base + "\\c" + colorAss + "\\fscx" + baseScalePercent + "\\fscy" + baseScalePercent + "\\b1";
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
