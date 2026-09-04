package com.saad.autocaption;

public class Caption {
    public String word;
    public float startTime;  // in seconds
    public float endTime;    // in seconds
    public float confidence;

    // NEW: per-word highlight color override. Null = use the globally
    // selected caption color. Lets each word get its own highlight
    // color independently, set via the "Edit Word Colors" dialog.
    public Integer customColor;

    public Caption(String word, float startTime, float endTime, float confidence) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
        this.confidence = confidence;
    }

    /** Returns customColor if the user set one for this specific word,
     * otherwise falls back to the app-wide selected highlight color. */
    public int resolveColor(int fallbackColor) {
        return customColor != null ? customColor : fallbackColor;
    }
}
