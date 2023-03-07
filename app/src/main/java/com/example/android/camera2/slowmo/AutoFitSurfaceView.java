package com.example.android.camera2.slowmo;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceView;

public class AutoFitSurfaceView extends SurfaceView {
    private float aspectRatio = 0;

    public AutoFitSurfaceView(Context context) {
        super(context);
    }
    public AutoFitSurfaceView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public AutoFitSurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * Sets the aspect ratio for this view. The size of the view will be
     * measured based on the ratio calculated from the parameters.
     *
     * @param width  Camera resolution horizontal size
     * @param height Camera resolution vertical size
     */
    public void setAspectRatio(int width, int height) {
        if(width <= 0 || height <= 0)
            throw new RuntimeException(String.format("Illigal argument!! Size cannot be negative %dx%d", width, height));
        aspectRatio = ((float)width) / height;
        getHolder().setFixedSize(width, height);
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height);
        }
        else {
            // Performs center-crop transformation of the camera frames
            int newWidth;
            int newHeight;
            float actualRatio = (width > height) ? aspectRatio : 1f / aspectRatio;
            if(width < height * actualRatio) {
                newHeight = height;
                newWidth = (int)(height * actualRatio);
            }
            else {
                newWidth = width;
                newHeight = (int)(width / actualRatio);
            }

            Log.d("aaaaa", String.format("Measured dimensions set: %d x %d", newWidth, newHeight));
            setMeasuredDimension(newWidth, newHeight);
        }
    }
}
