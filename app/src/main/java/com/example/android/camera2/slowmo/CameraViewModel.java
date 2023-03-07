package com.example.android.camera2.slowmo;

import androidx.lifecycle.ViewModel;

public class CameraViewModel extends ViewModel {
    private String cameraId;
    public String getCameraId() {
        return cameraId;
    }
    public void setCameraId(String cameraId) {
        this.cameraId = cameraId;
    }

    private int width;
    public int getWidth() {
        return width;
    }
    public void setWidth(int width) {
        this.width = width;
    }

    private int height;
    public int getHeight() {
        return height;
    }
    public void setHeight(int height) {
        this.height = height;
    }

    private int fps;
    public int getFps() {
        return fps;
    }
    public void setFps(int fps) {
        this.fps = fps;
    }
}
