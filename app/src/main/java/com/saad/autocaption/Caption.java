package com.saad.autocaption;

public class Caption {
    public String word;
    public float startTime;
    public float endTime;
    public float confidence;
    public Integer customColor;

    public Caption(String word, float startTime, float endTime, float confidence) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
        this.confidence = confidence;
        this.customColor = null;
    }

    public int resolveColor(int fallbackColor) {
        return (customColor != null && customColor != 0) ? customColor : fallbackColor;
    }
}
