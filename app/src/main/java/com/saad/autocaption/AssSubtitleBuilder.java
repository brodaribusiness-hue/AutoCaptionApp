package com.saad.autocaption;

import java.util.List;
import java.util.Locale;

public class AssSubtitleBuilder {

    // SIGNATURE CHANGE: fontSizeSp -> fontSizePreviewPx (actual rendered px of the preview TextView,
// via TextView.getTextSize(), so density is baked in correctly instead of guessed)
public static String build(
        List<Caption> captions,
        int videoWidth,
        int videoHeight,
        int previewWidthPx,
        int previewHeightPx,
        String fontName,
        float fontSizePreviewPx,
        int highlightColor,
        CaptionStyleOptions.CaptionStyleType style,
        CaptionSlotTransform beforeSlot,
        CaptionSlotTransform activeSlot,
        CaptionSlotTransform afterSlot) {

    // GUARD: container not laid out yet -> avoid divide-by-zero / garbage \pos values
    previewWidthPx = Math.max(previewWidthPx, 1);
    previewHeightPx = Math.max(previewHeightPx, 1);

    StringBuilder sb = new StringBuilder();
    sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: ")
            .append(videoWidth).append("\nPlayResY: ").append(videoHeight).append("\n\n");

    // FIX: font size now scales by the SAME preview->video ratio used for \pos,
    // instead of an arbitrary "assume preview is 400px wide" constant. This keeps
    // caption size visually consistent with what the user saw in the live preview
    // regardless of device density or actual container width.
    float previewToVideoY = videoHeight / (float) previewHeightPx;
    int assFontSize = Math.round(fontSizePreviewPx * previewToVideoY);

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

    float previewToVideoX = videoWidth / (float) previewWidthPx;

    int baseX = videoWidth / 2;
    int baseY = videoHeight - Math.round(videoHeight * 0.06f);

    boolean oneWordPunch = style == CaptionStyleOptions.CaptionStyleType.ONE_WORD_PUNCH;

    for (int i = 0; i < captions.size(); i++) {
        Caption activeCap = captions.get(i);
        String startTime = toAssTime(activeCap.startTime);
        String endTime = toAssTime(activeCap.endTime);

        // duration of this active word's on-screen window, used to time \t() animations
        long durationMs = Math.max(
                Math.round((activeCap.endTime - activeCap.startTime) * 1000f), 1);

        if (!oneWordPunch && i - 1 >= 0) {
            appendWordLine(sb, startTime, endTime, captions.get(i - 1).word,
                    beforeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                    "\\c&HFFFFFF&");
        }

        String activeTag = buildActiveWordOverrideTag(style, highlightAss, durationMs);
        appendWordLine(sb, startTime, endTime, activeCap.word,
                activeSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                activeTag);

        if (!oneWordPunch && i + 1 < captions.size()) {
            appendWordLine(sb, startTime, endTime, captions.get(i + 1).word,
                    afterSlot, baseX, baseY, previewToVideoX, previewToVideoY,
                    "\\c&HFFFFFF&");
        }
    }

    return sb.toString();
}

// ... existing code (appendWordLine unchanged) ...

// REPLACES buildActiveWordColorTag: now returns full override block including
// \t() transform animations so KARAOKE_FLOW/ONE_WORD_PUNCH/BOUNCE/BOX_HIGHLIGHT/GLOW_POP
// actually render distinctly in the exported burn-in, matching their live-preview Span behavior.
private static String buildActiveWordOverrideTag(
        CaptionStyleOptions.CaptionStyleType style, String highlightAss, long durationMs) {
    switch (style) {
        case GREEN_EMPHASIS:
            return "\\c&H0000FF00&\\b1";

        case MINIMAL_CLEAN:
            return "\\c&HFFFFFF&\\b1";

        case GLOW_POP: {
            long half = Math.min(150, durationMs / 2);
            return "\\c" + highlightAss + "\\blur4\\fscx100\\fscy100"
                    + "\\t(0," + half + ",\\fscx115\\fscy115)"
                    + "\\t(" + half + "," + (half * 2) + ",\\fscx100\\fscy100)";
        }

        case KARAOKE_FLOW:
            // approximate progressive fill: white -> highlight color over the word's duration
            return "\\c&HFFFFFF&\\t(0," + durationMs + ",\\c" + highlightAss + ")";

        case ONE_WORD_PUNCH: {
            long popDur = Math.min(200, Math.max(durationMs / 2, 1));
            return "\\c" + highlightAss + "\\b1\\fscx100\\fscy100"
                    + "\\t(0," + popDur + ",\\fscx180\\fscy180)"
                    + "\\t(" + popDur + "," + (popDur * 2) + ",\\fscx100\\fscy100)";
        }

        case BOX_HIGHLIGHT:
            // outline+shadow approximation of a filled background box behind the word
            return "\\c&HFFFFFF&\\3c" + highlightAss + "\\4c" + highlightAss
                    + "\\bord6\\shad0\\b1";

        case BOUNCE: {
            long third = Math.min(120, Math.max(durationMs / 3, 1));
            return "\\c" + highlightAss + "\\b1"
                    + "\\t(0," + third + ",\\fscy130)"
                    + "\\t(" + third + "," + (third * 2) + ",\\fscy85)"
                    + "\\t(" + (third * 2) + "," + (third * 3) + ",\\fscy100)";
        }

        default: // HIGHLIGHT_POP
            return "\\c" + highlightAss + "\\b1";
    }
}

// ... existing code (toAssColor, toAssTime unchanged) ...

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
