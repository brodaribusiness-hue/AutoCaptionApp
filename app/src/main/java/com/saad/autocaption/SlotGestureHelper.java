package com.saad.autocaption;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;

public class SlotGestureHelper implements View.OnTouchListener {

    private final ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private float lastTouchY;
    private float currentScale = 1f;
    private boolean positionDragged = false;

    public SlotGestureHelper(Context context) {
        scaleDetector = new ScaleGestureDetector(context,
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        currentScale *= scaleFactor;
                        currentScale = Math.max(0.5f, Math.min(currentScale, 3.5f));
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
                requestParentDisallowIntercept(view, true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getRawX() - lastTouchX;
                    float dy = event.getRawY() - lastTouchY;

                    view.setTranslationX(view.getTranslationX() + dx);
                    view.setTranslationY(view.getTranslationY() + dy);

                    lastTouchX = event.getRawX();
                    lastTouchY = event.getRawY();
                    positionDragged = true;
                }
                requestParentDisallowIntercept(view, true);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                requestParentDisallowIntercept(view, false);
                break;
        }

        view.setScaleX(currentScale);
        view.setScaleY(currentScale);
        return true;
    }

    private void requestParentDisallowIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    public float getScale() {
        return currentScale;
    }

    public boolean isPositionDragged() {
        return positionDragged;
    }
}
