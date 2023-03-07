package com.example.android.camera2.slowmo.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraConstrainedHighSpeedCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodec;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.android.camera2.slowmo.AutoFitSurfaceView;
import com.example.android.camera2.slowmo.BuildConfig;
import com.example.android.camera2.slowmo.CameraActivity;
import com.example.android.camera2.slowmo.CameraViewModel;
import com.example.android.camera2.slowmo.OrientationLiveData;
import com.example.android.camera2.slowmo.R;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.Dispatchers;

public class CameraFragment extends Fragment {
    private CameraViewModel mViewModel;
    private CameraManager cameraManager;
    private CameraCharacteristics characteristics;
    /** [HandlerThread] where all camera operations run */
    private HandlerThread cameraThread;
    /** [Handler] corresponding to [cameraThread] */
    private Handler cameraHandler;
    /** The [CameraDevice] that will be opened in this fragment */
    private CameraDevice camera = null;
    /** Readers used as buffers for camera still shots */
    private Surface recorderSurface;
    /** Saves the video recording */
    private MediaRecorder recorder;
    private File outputFile;
    private CameraConstrainedHighSpeedCaptureSession session;
    /** Requests used for preview only in the [CameraConstrainedHighSpeedCaptureSession] */
    private List<CaptureRequest> previewRequestList;
    private List<CaptureRequest> recordRequestList;
    /** Live data listener for changes in the device orientation relative to the camera */
    private OrientationLiveData relativeOrientation;
    private Long recordingStartMillis = 0L;
    private Runnable animationTask;

    public static Fragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mViewModel = new ViewModelProvider(requireActivity()).get(CameraViewModel.class);

        Context context = requireContext().getApplicationContext();
        cameraManager = (CameraManager)context.getSystemService(Context.CAMERA_SERVICE);

        try {
            characteristics = cameraManager.getCameraCharacteristics(mViewModel.getCameraId());
        }
        catch(CameraAccessException e) {
            throw new RuntimeException(e);
        }

        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());

        /* Creates a [File] named with the current date and time */
        Function2<Context, String, File> createFile = (context1, extension) -> {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss_SSS", Locale.US);
            return new File(context.getFilesDir(), String.format("VID_%s.%s", sdf.format(new Date()), extension));
        };
        outputFile = createFile.invoke(requireContext(), "mp4");
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        final AutoFitSurfaceView viewFinder = (AutoFitSurfaceView)view.findViewById(R.id.view_finder);
        viewFinder.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                // Selects appropriate preview size and configures view finder
                Size previewSize = getConstrainedPreviewOutputSize(viewFinder.getDisplay(), characteristics, SurfaceHolder.class, null);
                Log.d("aaaaa", String.format("View finder size: %d x %d", viewFinder.getWidth(), viewFinder.getHeight()));
                Log.d("aaaaa", String.format("Selected preview size: %s", previewSize));
                viewFinder.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());

                // To ensure that size is set, initialize camera in the view's thread
                viewFinder.post(() -> {
                    initializeCamera(view);
                });
            }
            @Override public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(@NonNull SurfaceHolder holder) {}
        });

        recorderSurface = MediaCodec.createPersistentInputSurface();
        MediaRecorder mr = new MediaRecorder();
        mr.setAudioSource(MediaRecorder.AudioSource.MIC);
        mr.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mr.setOutputFile(outputFile.getAbsolutePath());
        mr.setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE);
        mr.setVideoFrameRate(mViewModel.getFps());
        mr.setVideoSize(mViewModel.getWidth(), mViewModel.getHeight());
        mr.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mr.setInputSurface(recorderSurface);
        try { mr.prepare(); }
        catch(IOException e) { throw new RuntimeException(e); }
        mr.release();

        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        recorder.setOutputFile(outputFile.getAbsolutePath());
        recorder.setVideoEncodingBitRate(RECORDER_VIDEO_BITRATE);
        recorder.setVideoFrameRate(mViewModel.getFps());
        recorder.setVideoSize(mViewModel.getWidth(), mViewModel.getHeight());
        recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setInputSurface(recorderSurface);

        relativeOrientation = new OrientationLiveData(requireContext(), characteristics);
        relativeOrientation.observe(getViewLifecycleOwner(), orientation -> {
            Log.d("aaaaa", String.format("Orientation changed: %d", orientation) );
        });

        animationTask = () -> {
            // Flash white animation
            view.findViewById(R.id.overlay).setForeground( new ColorDrawable(Color.argb(150, 255, 255, 255)));
            // Wait for ANIMATION_FAST_MILLIS
            view.findViewById(R.id.overlay).postDelayed((Runnable)() -> {
                                                // Remove white flash animation
                                                view.findViewById(R.id.overlay).setForeground(null);
                                                // Restart animation recursively
                                                view.findViewById(R.id.overlay).postDelayed(animationTask, CameraActivity.ANIMATION_FAST_MILLIS);
                                            }, CameraActivity.ANIMATION_FAST_MILLIS);
        };
    }

    /**
     * Preview size is subject to the same rules compared to a normal capture session with the
     * additional constraint that the selected size must also be available as one of possible
     * constrained high-speed session sizes.
     */
    private <T> Size getConstrainedPreviewOutputSize(Display display, CameraCharacteristics characteristics, Class<T> targetClass, Integer format) {
        // Find which is smaller: screen or 1080p
        SmartSize screenSize = getDisplaySmartSize(display);
        boolean hdScreen = screenSize.mlong >= SIZE_1080P.mlong || screenSize.mshort >= SIZE_1080P.mshort;
        SmartSize maxSize = (hdScreen) ? SIZE_1080P : screenSize;

        // If image format is provided, use it to determine supported sizes; else use target class
        StreamConfigurationMap config = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (format == null)
            assert(StreamConfigurationMap.isOutputSupportedFor(targetClass));
        else
            assert(config.isOutputSupportedFor(format));

        Size[] allSizes = (format == null) ? config.getOutputSizes(targetClass) : config.getOutputSizes(format);

        // Get a list of potential high speed video sizes for the selected FPS
        Size[] highSpeedSizes = config.getHighSpeedVideoSizesFor(new Range<>(mViewModel.getFps(), mViewModel.getFps()));

        // Filter sizes which are part of the high speed constrained session
        // Get available sizes and sort them by area from largest to smallest
        List<Size> validSizes = Arrays.stream(allSizes)
                .filter(size -> { return Arrays.asList(highSpeedSizes).contains(size); })
                .sorted((o1, o2) -> { return (o2.getWidth()*o2.getHeight()) - (o1.getWidth()*o1.getHeight()); })
                .collect(Collectors.toList());

        // Then, get the largest output size that is smaller or equal than our max size
        return validSizes.stream().filter(it -> {
                                                    SmartSize smartSize = new SmartSize(it);
                                                    return smartSize.mlong <= maxSize.mlong && smartSize.mshort <= maxSize.mshort;
                                                })
                                  .findFirst().get();
    }

    /**
     * Begin all camera operations in a coroutine in the main thread. This function:
     * - Opens the camera
     * - Configures the camera session
     * - Starts the preview by dispatching a repeating burst request
     */
    @SuppressLint("ClickableViewAccessibility")
    private void initializeCamera(@NonNull View view) {
        // Open the selected camera
        openCamera(cameraManager, mViewModel.getCameraId(), cameraHandler);
        while(camera == null) {
            try { Thread.sleep(10); }
            catch(InterruptedException e) { }
        }

        // Creates list of Surfaces where the camera will output frames
        final AutoFitSurfaceView viewFinder = (AutoFitSurfaceView)view.findViewById(R.id.view_finder);
        List<Surface> targets = Arrays.asList(viewFinder.getHolder().getSurface(), recorderSurface);

        // Start a capture session using our open camera and list of Surfaces where frames will go
        createCaptureSession(camera, targets, cameraHandler);
        while(session == null) {
            try { Thread.sleep(10); }
            catch(InterruptedException e) { }
        }

        CaptureRequest.Builder builderforpreview = null;
        try { builderforpreview = session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW); }
        catch(CameraAccessException e) { throw new RuntimeException(e); }
        builderforpreview.addTarget(viewFinder.getHolder().getSurface());
        builderforpreview.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(FPS_PREVIEW_ONLY, mViewModel.getFps()));

        try { previewRequestList = session.createHighSpeedRequestList(builderforpreview.build()); }
        catch(CameraAccessException e) { throw new RuntimeException(e);}

        CaptureRequest.Builder builderforrecord = null;
        try { builderforrecord = session.getDevice().createCaptureRequest(CameraDevice.TEMPLATE_RECORD); }
        catch(CameraAccessException e) { throw new RuntimeException(e); }
        builderforrecord.addTarget(viewFinder.getHolder().getSurface());
        builderforrecord.addTarget(recorderSurface);
        builderforrecord.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range(mViewModel.getFps(), mViewModel.getFps()));

        try { recordRequestList = session.createHighSpeedRequestList(builderforrecord.build()); }
        catch(CameraAccessException e) { throw new RuntimeException(e); }

        // Ensures the requested size and FPS are compatible with this camera
        Range<Integer> fpsRange = new Range<>(mViewModel.getFps(), mViewModel.getFps());
        assert(true == Arrays.asList(characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                .getHighSpeedVideoFpsRangesFor(new Size(mViewModel.getWidth(), mViewModel.getHeight()))).contains(fpsRange));

        // Sends the capture request as frequently as possible until the session is torn down or
        // session.stopRepeating() is called
        try { session.setRepeatingBurst(previewRequestList, null, cameraHandler); }
        catch(CameraAccessException e) { throw new RuntimeException(e); }

        // Listen to the capture button
        view.findViewById(R.id.capture_button).setOnTouchListener((v, event) -> {
            switch(event.getAction()) {
                case MotionEvent.ACTION_DOWN: {
                    // Prevents screen rotation during the video recording
                    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);

                    // Stops preview requests, and start record requests
                    try { session.stopRepeating(); }
                    catch(CameraAccessException e) { throw new RuntimeException(e); }

                    try { session.setRepeatingBurst(recordRequestList, null, cameraHandler); }
                    catch(CameraAccessException e) { throw new RuntimeException(e); }

                    // Sets output orientation based on current sensor value at start time
                    recorder.setOrientationHint(relativeOrientation.getValue());
                    // Finalizes recorder setup and starts recording
                    try { recorder.prepare(); }
                    catch(IOException e) { throw new RuntimeException(e); }
                    recorder.start();

                    recordingStartMillis = System.currentTimeMillis();
                    Log.d("aaaaa", "Recording started");

                        // Starts recording animation
                    view.findViewById(R.id.overlay).post(animationTask);
                }
                    break;
                case MotionEvent.ACTION_UP: {

                    // Unlocks screen rotation after recording finished
                    requireActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                    // Requires recording of at least MIN_REQUIRED_RECORDING_TIME_MILLIS
                    long elapsedTimeMillis = System.currentTimeMillis() - recordingStartMillis;
                    if(elapsedTimeMillis < MIN_REQUIRED_RECORDING_TIME_MILLIS) {
                        try {
                            Thread.sleep(MIN_REQUIRED_RECORDING_TIME_MILLIS - elapsedTimeMillis);
                        }
                        catch(InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    Log.d("aaaaa", "Recording stopped. Output file: $outputFile");
                    //                    recorder.stop()

                    // Removes recording animation
                    view.findViewById(R.id.overlay).removeCallbacks(animationTask);

                    // Broadcasts the media file to the rest of the system
                    MediaScannerConnection.scanFile(view.getContext(), new String[]{outputFile.getAbsolutePath()}, null, null);

                    // Launch external activity via intent to play video recorded using our provider
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_VIEW);
                    String mimetype = MimeTypeMap.getSingleton().getMimeTypeFromExtension(outputFile.getAbsolutePath().substring(outputFile.getAbsolutePath().lastIndexOf(".") + 1));
                    String authority = BuildConfig.APPLICATION_ID + ".provider";
                    Log.d("aaaaa", "aaaaa authority=" + authority);
                    Uri uri = FileProvider.getUriForFile(view.getContext(), authority, outputFile);
                    intent.setDataAndType(uri, mimetype);
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);

                    // Finishes our current camera screen
                    try {
                        Thread.sleep(CameraActivity.ANIMATION_SLOW_MILLIS);
                    }
                    catch(InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    /* 戻るボタン押下を疑似発行 */
                    try { Runtime.getRuntime().exec("input keyevent " + KeyEvent.KEYCODE_BACK); }
                    catch(IOException e) { throw new RuntimeException(e); }
//                    getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
//                    getActivity().dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK));
                }
                break;
            }
            return true;
        });
    }

    /** Opens the camera and returns the opened device (as the result of the suspend coroutine) */
    private void openCamera(CameraManager manager, String cameraId, Handler handler) {
        if(ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            throw new RuntimeException("error!! camera permission deneied!!");

        try {
            manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                public void onOpened(@NonNull CameraDevice acamera) {
                    camera = acamera;
                }

                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.w("aaaaa", String.format("Camera %s has been disconnected", cameraId));
                    requireActivity().finish();
                }

                public void onError(@NonNull CameraDevice camera, int error) {
                    Function1<Integer, String> getMessage = (what) -> {
                        switch(what) {
                            case ERROR_CAMERA_DEVICE:      return "Fatal (device)";
                            case ERROR_CAMERA_DISABLED:    return "Device policy";
                            case ERROR_CAMERA_IN_USE:      return "Camera in use";
                            case ERROR_CAMERA_SERVICE:     return "Fatal (service)";
                            case ERROR_MAX_CAMERAS_IN_USE: return "Maximum cameras in use";
                            default: return "Unknown";
                        }
                    };
                    String msg = getMessage.invoke(error);
                    RuntimeException exc = new RuntimeException(String.format(Locale.getDefault(), "Camera %s error: (%d) %s", cameraId, error, msg));
                    Log.e("aaaaa", exc.getMessage(), exc);
                    getActivity().finish();
                }
            }, handler);
        }
        catch(CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates a [CameraCaptureSession] and returns the configured session (as the result of the
     * suspend coroutine)
     */
    private void createCaptureSession(CameraDevice device, List<Surface> targets, Handler handler) {
        // Creates a capture session using the predefined targets, and defines a session state
        // callback which resumes the coroutine once the session is configured
        try {
            device.createConstrainedHighSpeedCaptureSession(targets, new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession asession) {
                            session = (CameraConstrainedHighSpeedCaptureSession)asession;
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            RuntimeException exc = new RuntimeException(String.format(Locale.JAPAN, "Camera %s session configuration failed", device.getId()));
                            Log.e("aaaaa", exc.getMessage(), exc);
                            getActivity().finish();
                        }
                    }, handler);
        }
        catch(CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        camera.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        cameraThread.quitSafely();
        recorder.release();
        recorderSurface.release();
    }

    /** Maximum number of images that will be held in the reader's buffer */
    private static final int RECORDER_VIDEO_BITRATE = 10000000;
    private static final long MIN_REQUIRED_RECORDING_TIME_MILLIS = 1000L;
    /** FPS rate for preview-only requests, 30 is *guaranteed* by framework. See: [StreamConfigurationMap.getHighSpeedVideoFpsRanges] */
    private static final int  FPS_PREVIEW_ONLY= 30;

    /** Returns a [SmartSize] object for the given [Display] */
    private SmartSize getDisplaySmartSize(Display display) {
        Point outPoint = new Point();
        display.getRealSize(outPoint);
        return new SmartSize(outPoint.x, outPoint.y);
    }

    /** Standard High Definition size for pictures and video */
    SmartSize SIZE_1080P = new SmartSize(1920, 1080);

    /** Helper class used to pre-compute shortest and longest sides of a [Size] */
    static class SmartSize {
        Size size;
        int mlong;
        int mshort;

        public SmartSize(int width, int height) {
            size = new Size(width, height);
            mlong = Math.max(size.getWidth(), size.getHeight());
            mshort = Math.min(size.getWidth(), size.getHeight());
        }
        public SmartSize(Size lsize) {
            size = lsize;
            mlong = Math.max(size.getWidth(), size.getHeight());
            mshort = Math.min(size.getWidth(), size.getHeight());
        }
        @NonNull @Override
        public String toString() {
            return String.format(Locale.JAPAN, "SmartSize(%dx%d)", mlong, mshort);
        }
    }
}
