// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.app.Activity;
import android.content.Context;
import android.database.CursorJoiner;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Measure the camera to display latency
 * <p>
 * Features:
 * <p>
 * You can use the TextureView or the SurfaceView to check the position of a LED
 * before the camera. Both options display the camera image on the display.
 */
public class CameraToDisplay extends Activity implements Choreographer.FrameCallback {
    private final static String TAG = "MainActivity";
    //private final static int WIDTH = 480; final static int HEIGHT = 360; // only on pixel
    //private final static int WIDTH = 352; final static int HEIGHT = 288; // only on emulator
    //private final static int WIDTH = 640; final static int HEIGHT = 480;
    private final static int WIDTH = 1920;
    private final static int HEIGHT = 1080;
    private final static USE use = USE.TEXTURE_VIEW;
    // the backfacing camera on the pixel2 supports 60hz!
    private final static int FPS = 30; // NOTE 60 fps only works when AE mode is not manual. Must be auto.
    private final static long EXPOSURE_TIME_IN_NS = 1_000_000;
    //private final static long EXPOSURE_TIME_IN_NS = -1;  // Use auto exposure
    ConstraintLayout layout;
    HandlerThread handlerThreadImageReader;
    Handler handlerImageReader;
    HandlerThread handlerThreadTorch;
    Handler handlerTorch;
    TextureView textureView;
    SurfaceView surfaceView;
    ImageReader imageReader;
    Surface surface;
    CameraManager manager;
    String cameraIdFront;
    String cameraIdBack;
    Camera camera;
    FrameRater surfaceRate = new FrameRater(TAG, "surface");
    FrameRater vsyncRate = new FrameRater(TAG, "vsync");
    PixelTorch pixelTorch;
    SensorSyncer sensorSyncer;
    Camera.OnCaptureCompleted onCaptureCompleted = (result) -> {
        long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (sensorSyncer != null)
            sensorSyncer.updateTimestamp(timestamp);
    };
    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            Log.d(TAG, "onSurfaceTextureAvailable");
            surfaceTexture.setDefaultBufferSize(WIDTH, HEIGHT);
            surface = new Surface(surfaceTexture);
            getMainExecutor().execute(CameraToDisplay.this::configureAndStartCamera);
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int i, int i1) {
            Log.d(TAG, "onSurfaceTextureSizeChanged");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surfaceTexture) {
            // TODO is this timestamp correct?
            // call here https://cs.android.com/android/platform/superproject/+/master:frameworks/base/core/java/android/view/TextureView.java;l=473?q=TextureView&ss=android

            // function: https://cs.android.com/android/platform/superproject/+/master:frameworks/base/graphics/java/android/graphics/SurfaceTexture.java;l=346?q=SurfaceTexture&ss=android
            long timestamp = surfaceTexture.getTimestamp();
            Trace.beginSection("onSurfaceTextureUpdated timestamp=" + timestamp);
            surfaceRate.tick();
            //Log.d(TAG, "surfaceTexture.getTimestamp=" +  surfaceTexture.getTimestamp());
            Trace.endSection();
        }
    };
    private final SurfaceHolder.Callback surfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder surfaceHolder) {
            Log.d(TAG, "surfaceCreated");
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder surfaceHolder, int format, int width, int height) {
            Log.d(TAG, "surfaceChanged format=" + format + " width=" + width + " height=" + height);
            surface = surfaceHolder.getSurface();
            getMainExecutor().execute(CameraToDisplay.this::configureAndStartCamera);
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder surfaceHolder) {
            Log.d(TAG, "surfaceDestroyed");
        }
    };
    int counter = 1;
    Runnable onSensorExposureStarts = () -> {
        counter = (counter + 1) % 10;
        if (counter == 0) {
            //handlerTorch.postDelayed(this::drawTorchPattern, 6); // set torch at once
            //handlerTorch.postDelayed(this::drawTorchPattern, 10);
        }
    };
    boolean draw_vsync_marker = false;
    ImageReader.OnImageAvailableListener callback = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {

            Image image = imageReader.acquireLatestImage();
            if (image != null) {
                long timestamp = image.getTimestamp();
                Trace.beginSection("onImageAvailable timestamp=" + timestamp);

                try (image) {
                    assert (image.getWidth() == WIDTH);
                    assert (image.getHeight() == HEIGHT);
                    assert (image.getFormat() == ImageFormat.YUV_420_888);
                    assert (image.getPlanes().length == 3);
                    //Log.d(TAG, "getRowStride "+ image.getPlanes()[0].getRowStride() + " getPixelStride " + image.getPlanes()[0].getPixelStride());
                    // getRowStride 1920 getPixelStride 1

                    int rowStride = image.getPlanes()[0].getRowStride();
                    int pixelStride = image.getPlanes()[0].getPixelStride();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    int count = countWhiteLinesInPlane(buffer, WIDTH, HEIGHT, rowStride, pixelStride);

                    boolean is_white = count >= 100;
                    if (is_white) {
                        Log.d(TAG, "average " + count + " is_white");
                        drawMarkerWithTorch("image-callback-white", 800);
                        handlerTorch.postDelayed(() -> {
                            draw_vsync_marker = true;
                        }, 30);
                    }
                    String is_white_str = is_white ? "white" : "black";
                    Trace.beginSection(is_white_str);
                    Trace.endSection();

                    layout.setBackgroundColor(is_white ? 0xFFFFFFFF : 0xFF000000);

                    surfaceRate.tick();
                }
            }
            Trace.endSection();
        }
    };

    private static List<String> mapControlAeAvailableModes(int[] modes) {
        return Arrays.stream(modes).mapToObj(
                (mode) -> {
                    switch (mode) {
                        case CameraCharacteristics.CONTROL_AE_MODE_OFF:
                            return "CONTROL_AE_MODE_OFF";
                        case CameraCharacteristics.CONTROL_AE_MODE_ON:
                            return "CONTROL_AE_MODE_ON";
                        case CameraCharacteristics.CONTROL_AE_MODE_ON_EXTERNAL_FLASH:
                            return "CONTROL_AE_MODE_ON_EXTERNAL_FLASH";
                        case CameraCharacteristics.CONTROL_AE_MODE_ON_ALWAYS_FLASH:
                            return "CONTROL_AE_MODE_ON_ALWAYS_FLASH";
                        case CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE:
                            return "CONTROL_AE_MODE_ON_AUTO_FLASH_REDEYE";
                        case CameraCharacteristics.CONTROL_AE_MODE_ON_AUTO_FLASH:
                            return "CONTROL_AE_MODE_ON_AUTO_FLASH";
                        default:
                            return "CONTROL_AE_MODE_UNKNOWN";
                    }
                }
        ).collect(Collectors.toList());
    }

    static int countWhiteLinesInPlane(ByteBuffer buffer, int width, int height, int rowStride, int pixelStride) {
        // Note: The vertical position greatly depends on the physical placement of the LEDs in the test setup.
        final int verticalLinePosition = 700;
        assert (verticalLinePosition < width);
        int count = 0;
        for (int y = 0; y < height; y++) {
            int pos = y * rowStride + verticalLinePosition * pixelStride;
            int value = buffer.get(pos) & 0xFF;
            if (value >= 50) {
                count++;
            }
        }
        return count;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        // Using three different layouts:
        switch (use) {
            case TEXTURE_VIEW:
                setContentView(R.layout.activity_camera_to_display_textureview);
                textureView = findViewById(R.id.textureView);
                textureView.setSurfaceTextureListener(surfaceTextureListener);
                break;
            case SURFACE_VIEW:
                setContentView(R.layout.activity_camera_to_display_surfaceview);
                surfaceView = findViewById(R.id.surfaceView);
                surfaceView.getHolder().setFixedSize(WIDTH, HEIGHT);
                surfaceView.getHolder().addCallback(surfaceHolderCallback);
                break;
            case IMAGE_READER:
                imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 3);
                imageReader.setOnImageAvailableListener(callback, null);
                surface = imageReader.getSurface();
                setContentView(R.layout.activity_camera_to_display_imagereader);
                configureAndStartCamera();
                break;
            default:
                throw new RuntimeException("Missing code here");
        }
        layout = findViewById(R.id.background);

        handlerThreadImageReader = new HandlerThread("imagereader");
        handlerThreadImageReader.start();
        handlerImageReader = new Handler(handlerThreadImageReader.getLooper());

        handlerThreadTorch = new HandlerThread("torch");
        handlerThreadTorch.start();
        handlerTorch = new Handler(handlerThreadTorch.getLooper());

        try {
            pixelTorch = PixelTorch.openPixelTorch();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        if (camera != null) {
            Choreographer.getInstance().postFrameCallback(this);
            camera.start(FPS, EXPOSURE_TIME_IN_NS);
        }
        sensorSyncer = new SensorSyncer(onSensorExposureStarts);
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        Choreographer.getInstance().removeFrameCallback(this);
        setTorch(false);
        if (camera != null) {
            camera.stop();
        }
        sensorSyncer.close();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.close();
        }
        if (imageReader != null) {
            imageReader.close();
        }
        handlerThreadImageReader.quitSafely();

        if (pixelTorch != null)
            pixelTorch.close();
    }

    void logCameraInfo(String id) {
        try {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Log.d(TAG, "REQUEST_PIPELINE_MAX_DEPTH=" + c.get(CameraCharacteristics.REQUEST_PIPELINE_MAX_DEPTH));
            Log.d(TAG, "CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES=" + Arrays.toString(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)));
            Log.d(TAG, "CONTROL_AE_AVAILABLE_MODES=" + mapControlAeAvailableModes(c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_MODES)));
            Log.d(TAG, "SENSOR_INFO_TIMESTAMP_SOURCE=" + c.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) + " (1==realtime)");
            Log.d(TAG, "SENSOR_INFO_EXPOSURE_TIME_RANGE=" + c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE));
        } catch (CameraAccessException e) {
            Log.e(TAG, "error", e);
        }
    }

    void configureAndStartCamera() {
        cameraIdBack = Utils.getBackfacingCameraId(manager);
        cameraIdFront = Utils.getFrontfacingCameraId(manager);

        logCameraInfo(cameraIdFront);

        camera = new Camera(manager);
        camera.setOnCaptureCompletedListener(onCaptureCompleted);
        camera.configure(cameraIdBack, surface);
        // This function is called mostly after 'onResume()' was executed.
        camera.start(FPS, EXPOSURE_TIME_IN_NS);
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void doFrame(long l) {
        vsyncRate.tick();

        if (draw_vsync_marker) {
            drawMarkerWithTorch("vsync", 1600);
            draw_vsync_marker = false;
        }

        //handler.postDelayed(() -> setTorch(is_white), 20); // Set torch with a delay
        Choreographer.getInstance().postFrameCallback(this);
    }

    void drawTorchPattern() {
        Trace.beginSection("drawTorchPattern");
        try {
            long timepoint_in_us = Clock.clockGetTimeInUs();
            pixelTorch.setMode(true);

            Clock.clockNanoSleepUntil(timepoint_in_us + 800);

            pixelTorch.setMode(false);

            Clock.clockNanoSleepUntil(timepoint_in_us + 1_600);

            pixelTorch.setMode(true);

            Clock.clockNanoSleepUntil(timepoint_in_us + 21_600);

            pixelTorch.setMode(false);

            Clock.clockNanoSleepUntil(timepoint_in_us + 22_400);

            pixelTorch.setMode(true);

            Clock.clockNanoSleepUntil(timepoint_in_us + 23_200);

            pixelTorch.setMode(false);

        } catch (IOException e) {
            Log.e(TAG, "Cannot set torch", e);
        } finally {
            Trace.endSection();
        }
    }

    void drawMarkerWithTorch(String name, long delay_in_us) {
        Trace.beginSection("drawMarkerWithTorch-" + name);
        try {
            long timepoint_in_us = Clock.clockGetTimeInUs();
            pixelTorch.setMode(true);
            Clock.clockNanoSleepUntil(timepoint_in_us + delay_in_us);
            pixelTorch.setMode(false);
        } catch (IOException e) {
            Log.e(TAG, "Cannot set torch", e);
        } finally {
            Trace.endSection();
        }
    }

    void setTorch(boolean value) {
        Trace.beginSection(value ? "setTorch-to-white" : "setTorch-to-black");
        try {
            pixelTorch.setMode(value);
        } catch (IOException e) {
            Log.e(TAG, "Cannot set torch", e);
        } finally {
            Trace.endSection();
        }
    }

    enum USE {
        IMAGE_READER, // Toggles the view background color if the image is black or white
        TEXTURE_VIEW,
        SURFACE_VIEW,
    }
}
