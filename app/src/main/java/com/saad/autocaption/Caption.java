package com.saad.autocaption;

public class Caption {
    public String word;
    public float startTime;
    public float endTime;
    public float confidence;

    // NEW: per-word highlight color override. Null = use the global
    // selected color. Set by user tapping an individual word in the
    // caption editor (Phase 2 UI) or via ASS export.
    public Integer customColor;

    public Caption(String word, float startTime, float endTime, float confidence) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
        this.confidence = confidence;
    }

    public int resolveColor(int fallbackColor) {
        return customColor != null ? customColor : fallbackColor;
    }
}
