package com.example.android.camera2.slowmo.fragments;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.android.camera2.slowmo.R;
import com.google.common.primitives.Ints;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class SelectorFragment extends Fragment {
    List<CameraInfo> cameraList;

    public static Fragment newInstance() {
        return new SelectorFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView v = (RecyclerView)view.findViewById(R.id.rvw_selector);
        v.setLayoutManager(new LinearLayoutManager(getContext()));

        CameraManager cameraManager = (CameraManager)requireContext().getSystemService(Context.CAMERA_SERVICE);
        cameraList = enumerateHighSpeedCameras(cameraManager);

        v.setAdapter(new SelectorAdapter());
    }

    private List<CameraInfo> enumerateHighSpeedCameras(CameraManager cameraManager) {
        List<CameraInfo> availableCameras =  new ArrayList<>();

        String[] cameraIdList = null;
        try { cameraIdList = cameraManager.getCameraIdList(); }
        catch(CameraAccessException e) { throw new RuntimeException(e); }

        // Iterate over the list of cameras and add those with high speed video recording
        //  capability to our output. This function only returns those cameras that declare
        //  constrained high speed video recording, but some cameras may be capable of doing
        //  unconstrained video recording with high enough FPS for some use cases and they will
        //  not necessarily declare constrained high speed video capability.
        Arrays.stream(cameraIdList)
                .filter(id -> {
                    try {
                        CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                        int[] capabilities = characteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
                        boolean is = Ints.asList(capabilities).contains(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO);
                        return is;
                    }
                    catch(CameraAccessException e) { throw new RuntimeException(e); }
                })
                .forEach(id -> {
                    CameraCharacteristics characteristics = null;
                    try { characteristics = cameraManager.getCameraCharacteristics(id); }
                    catch(CameraAccessException e) { throw new RuntimeException(e); }

                    String orientation = lensOrientationString(characteristics.get(CameraCharacteristics.LENS_FACING));

                    StreamConfigurationMap cameraConfig = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    for(Size lpsize : cameraConfig.getHighSpeedVideoSizes()) {
                        for(Range<Integer> lpfps : cameraConfig.getHighSpeedVideoFpsRangesFor(lpsize)) {
                            Log.d("aaaaa", String.format("aaaaa HighSpeedVideoSize=%s FpsRangesFor=%s(%d-%d)", lpsize, lpfps, lpfps.getLower(), lpfps.getUpper()));
                        }
                    }

                    Arrays.stream(cameraConfig.getHighSpeedVideoSizes()).forEach(size -> {
                        Arrays.stream(cameraConfig.getHighSpeedVideoFpsRangesFor(size)).forEach(fpsRange -> {
                            int fps = fpsRange.getUpper();
                            CameraInfo info = new CameraInfo(String.format(Locale.JAPAN, "%s (%s) %s %d FPS", orientation, id, size, fps), id, size, fps);
                            if(!availableCameras.contains(info)) availableCameras.add(info);
                        });
                    });
                });

        return availableCameras;
    }

    /** Converts a lens orientation enum into a human-readable string */
    private String lensOrientationString(int value) {
        switch(value) {
            case CameraCharacteristics.LENS_FACING_BACK:     return "Back";
            case CameraCharacteristics.LENS_FACING_FRONT:    return "Front";
            case CameraCharacteristics.LENS_FACING_EXTERNAL: return "External";
            default: return "Unknown";
        }
    }

    static class CameraInfo {
        String title;
        String cameraId;
        Size size;
        int fps;

        public CameraInfo(String t, String c, Size s, int f) {
            title = t;
            cameraId = c;
            size = s;
            fps = f;
        }

        @Override
        public boolean equals(@Nullable Object obj) {
            if(!(obj instanceof CameraInfo))
                return false;
            CameraInfo info = (CameraInfo)obj;
            if(info.title.equals(this.title) && info.cameraId.equals(this.cameraId) && info.fps == this.fps && info.size.getWidth() == this.size.getWidth() && info.size.getHeight() == this.size.getHeight())
                return true;
            return false;
        }
    }

    private class SelectorAdapter extends RecyclerView.Adapter<SelectorAdapter.ViewHolder> {
        class ViewHolder extends RecyclerView.ViewHolder {
            TextView mTxtItem;
            public ViewHolder(@NonNull View v) {
                super(v);
                mTxtItem = v.findViewById(R.id.txt_item);
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_selector, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CameraInfo item = cameraList.get(position);
            holder.mTxtItem.setText(item.title);
            holder.mTxtItem.setOnClickListener(v1 -> {
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, CameraFragment.newInstance(item.cameraId, item.size.getWidth(), item.size.getHeight(), item.fps)).commit();
            });
        }

        @Override
        public int getItemCount() {
            return cameraList.size();
        }
    }
}
