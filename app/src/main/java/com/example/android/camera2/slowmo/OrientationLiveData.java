package com.example.android.camera2.slowmo;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.view.OrientationEventListener;
import android.view.Surface;

import androidx.lifecycle.LiveData;

import kotlin.jvm.functions.Function1;

public class OrientationLiveData extends LiveData<Integer> {
    Context context;
    CameraCharacteristics characteristics;
    private final OrientationEventListener listener;

    public OrientationLiveData(Context context, CameraCharacteristics characteristics) {
        this.context = context;
        this.characteristics = characteristics;
        setValue(computeRelativeRotation(characteristics, 180));
        this.listener = new OrientationEventListener(context.getApplicationContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                Function1<Integer, Integer> getRotation = (aorientation) -> {
                    if(aorientation <= 45) return Surface.ROTATION_0;
                    else if(aorientation <= 135) return Surface.ROTATION_90;
                    else if(aorientation <= 225) return Surface.ROTATION_180;
                    else if(aorientation <= 315) return Surface.ROTATION_270;
                    else return  Surface.ROTATION_0;
                };
                int rotation = getRotation.invoke(orientation);
                Integer relative = computeRelativeRotation(characteristics, rotation);
                if( !relative.equals(getValue()) ) postValue(relative);
            }
        };
    }

    private Integer computeRelativeRotation(CameraCharacteristics characteristics, int surfaceRotation) {
        Integer sensorOrientationDegrees = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);

        Function1<Integer, Integer> getDeviceOrientationDegrees = (rotation) -> {
            switch(rotation) {
                case Surface.ROTATION_0  : return 0;
                case Surface.ROTATION_90 : return 90;
                case Surface.ROTATION_180: return 180;
                case Surface.ROTATION_270: return 270;
                default: return 0;
            }
        };
        int deviceOrientationDegrees = getDeviceOrientationDegrees.invoke(surfaceRotation);

        // Reverse device orientation for front-facing cameras
        int sign = (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) ? 1 : -1;

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        return (sensorOrientationDegrees - (deviceOrientationDegrees * sign) + 360) % 360;
    }

    @Override
    protected void onActive() {
        super.onActive();
        listener.enable();
    }

    @Override
    protected void onInactive() {
        super.onInactive();
        listener.disable();
    }
}
