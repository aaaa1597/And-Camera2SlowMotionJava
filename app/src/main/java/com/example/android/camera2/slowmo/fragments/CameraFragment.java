package com.example.android.camera2.slowmo.fragments;

import androidx.fragment.app.Fragment;

public class CameraFragment extends Fragment {
    private final String cameraId;
    private final int width;
    private final int height;
    private final int fps;

    public CameraFragment(String cameraId, int width, int height, int fps) {
        this.cameraId = cameraId;
        this.width = width;
        this.height = height;
        this.fps = fps;
    }

    public static Fragment newInstance(String cameraId, int width, int height, int fps) {
        return new CameraFragment(cameraId, width, height, fps);
    }
}
