package com.saad.autocaption;

import android.content.Context;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;

public class SlotGestureHelper implements View.OnTouchListener {

    private final ScaleGestureDetector scaleDetector;
    private float lastTouchX;
    private float lastTouchY;
    private float currentScale = 1f;

    // Tracks whether the user has manually dragged this slot's
    // position — once true, auto-spacing stops touching translationX/Y.
    private boolean positionDragged = false;

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
                // FIX: the root layout is a ScrollView. Without this,
                // ScrollView steals touch events mid-gesture for its own
                // vertical scroll, which is what made dragging feel
                // laggy/interrupted instead of smooth.
                requestParentDisallowIntercept(view, true);
                break;

            case MotionEvent.ACTION_MOVE:
                if (event.getPointerCount() == 1 && !scaleDetector.isInProgress()) {
                    float dx = event.getRawX() - lastTouchX;
                    float dy = event.getRawY() - lastTouchY;

                    float newX = view.getTranslationX() + dx;
                    float newY = view.getTranslationY() + dy;

                    if (view.getParent() instanceof ViewGroup) {
                        ViewGroup parent = (ViewGroup) view.getParent();
                        int parentWidth = parent.getWidth();
                        int parentHeight = parent.getHeight();

                        if (parentWidth > 0 && parentHeight > 0) {
                            float halfW = (view.getWidth() * currentScale) / 2f;
                            float halfH = (view.getHeight() * currentScale) / 2f;
                            float centerX = view.getLeft() + view.getWidth() / 2f;
                            float centerY = view.getTop() + view.getHeight() / 2f;

                            float minX = -centerX + halfW;
                            float maxX = parentWidth - centerX - halfW;
                            float minY = -centerY + halfH;
                            float maxY = parentHeight - centerY - halfH;

                            if (minX <= maxX) {
                                newX = Math.max(minX, Math.min(newX, maxX));
                            }
                            if (minY <= maxY) {
                                newY = Math.max(minY, Math.min(newY, maxY));
                            }
                        }
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
