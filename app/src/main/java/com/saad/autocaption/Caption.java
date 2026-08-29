package com.saad.autocaption;

public class Caption {
    public String word;
    public float startTime;  // in seconds
    public float endTime;    // in seconds
    public float confidence;

    public Caption(String word, float startTime, float endTime, float confidence) {
        this.word = word;
        this.startTime = startTime;
        this.endTime = endTime;
        this.confidence = confidence;
    }
}
