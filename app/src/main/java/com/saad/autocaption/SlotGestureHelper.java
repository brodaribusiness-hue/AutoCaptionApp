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
                        currentScale *= detector.getScaleFactor();
                        currentScale = Math.max(0.4f, Math.min(currentScale, 4.0f));
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

                    float newX = view.getTranslationX() + dx;
                    float newY = view.getTranslationY() + dy;

                    // Allow unrestricted movement inside preview frame
                    View container = findPreviewContainer(view);
                    if (container != null && container.getWidth() > 0 && container.getHeight() > 0) {
                        float limitX = container.getWidth() * 0.95f;
                        float limitY = container.getHeight() * 0.95f;

                        newX = Math.max(-limitX, Math.min(newX, limitX));
                        newY = Math.max(-limitY, Math.min(newY, limitY));
                    }

                    view.setTranslationX(newX);
                    view.setTranslationY(newY);

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

            default:
                break;
        }

        view.setScaleX(currentScale);
        view.setScaleY(currentScale);
        return true;
    }

    private View findPreviewContainer(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            View parentView = (View) parent;
            if (parentView instanceof AspectRatioFrameLayout) {
                return parentView;
            }
            parent = parent.getParent();
        }
        return (view.getParent() instanceof View) ? (View) view.getParent() : null;
    }

    private void requestParentDisallowIntercept(View view, boolean disallow) {
        if (view.getParent() != null) {
            view.getParent().requestDisallowInterceptTouchEvent(disallow);
        }
    }

    public float getScale() {
        return currentScale;
    }

    public boolean isPositionDragged() {
        return positionDragged;
    }
}
