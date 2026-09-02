package com.saad.autocaption;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

/** Handles drag (1 finger) + pinch-to-zoom (2 fingers) for a single
 * caption word slot, so it can be moved and resized independently. */
public class SlotGestureHelper implements View.OnTouchListener {

    private final ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private float lastTouchY;
    private float currentScale = 1f;

    public SlotGestureHelper(Context context) {
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        currentScale *= detector.getScaleFactor();
                        currentScale = Math.max(0.4f, Math.min(currentScale, 4f));
                        return true;
                    }
                });
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        scaleDetector.onTouchEvent(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastTouchX = event.getRawX();
                lastTouchY = event.getRawY();
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getRawX() - lastTouchX;
                    float dy = event.getRawY() - lastTouchY;
                    view.setTranslationX(view.getTranslationX() + dx);
                    view.setTranslationY(view.getTranslationY() + dy);
                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                }
                break;
        }

        view.setScaleX(currentScale);
        view.setScaleY(currentScale);
        return true;
    }

    public float getScale() {
        return currentScale;
    }
}
