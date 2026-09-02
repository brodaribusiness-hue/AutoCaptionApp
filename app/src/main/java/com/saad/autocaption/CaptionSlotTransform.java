package com.saad.autocaption;

/** Snapshot of one caption slot's position (in preview pixels) and
 * pinch-zoom scale at export time. */
public class CaptionSlotTransform {
    public final float translationX;
    public final float translationY;
    public final float scale;

    public CaptionSlotTransform(float translationX, float translationY, float scale) {
        this.translationX = translationX;
        this.translationY = translationY;
        this.scale = scale;
    }
}
