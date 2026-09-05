package com.saad.autocaption;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class AspectRatioFrameLayout extends FrameLayout {

    private float ratioWidth = 9f;
    private float ratioHeight = 16f;

    public AspectRatioFrameLayout(Context context) {
        super(context);
    }

    public AspectRatioFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setAspectRatio(float width, float height) {
        if (width > 0 && height > 0) {
            this.ratioWidth = width;
            this.ratioHeight = height;
            requestLayout();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);

        if (ratioWidth == 0 || ratioHeight == 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        int calculatedHeight = Math.round(widthSize * (ratioHeight / ratioWidth));
        int newHeightSpec = MeasureSpec.makeMeasureSpec(calculatedHeight, MeasureSpec.EXACTLY);

        super.onMeasure(widthMeasureSpec, newHeightSpec);
    }
}
