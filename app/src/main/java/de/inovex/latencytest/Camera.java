// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.OutputConfiguration;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.os.Trace;
import android.util.Log;
import android.util.Range;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.io.Closeable;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

// Doku:
// https://developer.android.com/training/camera2/capture-sessions-requests
public class Camera implements Closeable {
    private final static String TAG = "Camera";
    private final CameraManager manager;
    private final HandlerThread handlerThread;
    private final Handler handler;
    private OnCaptureCompleted listener;
    private Surface surface;

    Camera(CameraManager manager) {
        this.manager = manager;
        handlerThread = new HandlerThread("camera");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void close() {
        Log.d(TAG, "Closing...");
        stop();
        deconfigure();
        handlerThread.quitSafely();
        Log.d(TAG, "Closed.");
    }

    CameraDevice cameraDevice;
    CameraCaptureSession session;

    void configure(String cameraId, Surface surface) {
        AtomicLatchAndValue<CameraDevice> cameraDeviceLatch = new AtomicLatchAndValue<>();

        CameraDevice.StateCallback cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice cameraDevice) {
                cameraDeviceLatch.ok(cameraDevice);
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice cameraDevice) {
                Log.e(TAG, "onDisconnected()");
                cameraDeviceLatch.error();
            }

            @Override
            public void onError(@NonNull CameraDevice cameraDevice, int i) {
                cameraDeviceLatch.error();
            }
        };

        try {
            manager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (CameraAccessException | SecurityException e) {
            Log.e(TAG, "failure", e);
            throw new RuntimeException(e);
        }

        Optional<CameraDevice> cameraDeviceOptional = cameraDeviceLatch.get();
        if (!cameraDeviceOptional.isPresent()) {
            throw new RuntimeException("Cannot create camera device");
        }
        cameraDevice = cameraDeviceOptional.get();

        AtomicLatchAndValue<CameraCaptureSession> captureSessionAtomicLatch = new AtomicLatchAndValue<>();
        CameraCaptureSession.StateCallback sessionStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                Log.d(TAG, "onConfigured");
                captureSessionAtomicLatch.ok(session);
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                Log.e(TAG, "onConfigureFailed");
                captureSessionAtomicLatch.error();
            }
        };

        this.surface = surface;
        List<OutputConfiguration> outputConfigurations = Arrays.asList(
                new OutputConfiguration(surface)
        );
        try {
            cameraDevice.createCaptureSessionByOutputConfigurations(outputConfigurations, sessionStateCallback, handler);
        } catch (CameraAccessException | IllegalArgumentException e) {
            Log.e(TAG, "cannot create request", e);
            throw new RuntimeException(e);
        }

        Optional<CameraCaptureSession> captureSessionOptional = captureSessionAtomicLatch.get();
        if (!captureSessionOptional.isPresent()) {
            throw new RuntimeException("Cannot create capture session");
        }
        session = captureSessionOptional.get();
        Log.d(TAG, "camera finished");
    }

    void deconfigure() {
        if (cameraDevice == null || session == null) {
            return;
        }

        stop(); // if not already done

        session.close();
        session = null;

        cameraDevice.close();
        cameraDevice = null;
    }

    int log_counter = 0;
    FrameRater captureRate = new FrameRater(TAG, "camera");
    CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            Trace.beginSection("onCaptureStarted timestamp=" + timestamp);
            //Log.d(TAG, "onCaptureStarted: SENSOR_TIMESTAMP=" + timestamp + " frameNumber=" + frameNumber);
            Trace.endSection();
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            //long timestamp = partialResult.get(CaptureResult.SENSOR_TIMESTAMP); // Does not work!
            Trace.beginSection("onCaptureProgressed");
            Trace.endSection();
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            long timestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
            long sensor_skew_time = result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW);
            long current_timestamp = SystemClock.elapsedRealtimeNanos();
            float delta_in_ms = (float) (current_timestamp - timestamp) / 1000 / 1000;
            float exposure_end_delta_ms = ((float) current_timestamp - (timestamp + sensor_skew_time)) / 1000 / 1000;

            Trace.beginSection("onCaptureCompleted timestamp=" + timestamp +
                    " current_timestamp=" + current_timestamp +
                    " d=" + delta_in_ms + "ms" +
                    " e_end_d=" + exposure_end_delta_ms + "ms");
            captureRate.tick();
            if (log_counter == 0) {
                Log.d(TAG, "onCaptureCompleted: SENSOR_TIMESTAMP=" + timestamp);
                //Log.d(TAG, "onCaptureCompleted: REQUEST_PIPELINE_DEPTH=" + result.get(CaptureResult.REQUEST_PIPELINE_DEPTH));
                // REQUEST_PIPELINE_DEPTH=3
                // REQUEST_PIPELINE_DEPTH=4
                // REQUEST_PIPELINE_MAX_DEPTH=8
                Log.d(TAG, "onCaptureCompleted: SENSOR_ROLLING_SHUTTER_SKEW=" + result.get(CaptureResult.SENSOR_ROLLING_SHUTTER_SKEW));
                Log.d(TAG, "onCaptureCompleted: SENSOR_EXPOSURE_TIME=" + result.get(CaptureResult.SENSOR_EXPOSURE_TIME));
                Log.d(TAG, "onCaptureCompleted: SENSOR_FRAME_DURATION=" + result.get(CaptureResult.SENSOR_FRAME_DURATION));
                Log.d(TAG, "onCaptureCompleted: current_time - timestamp= " + delta_in_ms + "ms");
            }
            log_counter = (log_counter + 1) % 30;

            listener.onCaptureCompleted(result);

            Trace.endSection();
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            Log.e(TAG, "onCaptureFailed");
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            Log.d(TAG, "onCaptureSequenceCompleted");
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            Log.e(TAG, "onCaptureSequenceAborted");
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            Log.e(TAG, "onCaptureBufferLost");
        }
    };

    void start(int fps) {
        start(fps, -1);
    }

    void start(int fps, long exposure_time_in_ns) {
        if (cameraDevice == null || session == null) {
            throw new IllegalStateException("device and session are not configured");
        }

        try {
            CaptureRequest.Builder request = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_VIDEO_SNAPSHOT);
            request.addTarget(surface);

            request.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range.create(fps, fps));
            if (exposure_time_in_ns >= 0) {
                request.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
                request.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exposure_time_in_ns);
            }
            //request.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);
            //request.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            //request.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_OFF);
            //request.set(CaptureRequest.SENSOR_TEST_PATTERN_MODE, CameraMetadata.SENSOR_TEST_PATTERN_MODE_COLOR_BARS); // works
            session.setRepeatingRequest(request.build(), captureCallback, handler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera", e);
            throw new RuntimeException(e);
        }
    }

    void stop() {
        if (cameraDevice == null || session == null) {
            throw new IllegalStateException("device and session are not configured");
        }

        try {
            session.stopRepeating();
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot access camera", e);
        }
    }

    // TODO Add option for Handler
    void setOnCaptureCompletedListener(OnCaptureCompleted listener) {
        this.listener = listener;
    }

    public interface OnCaptureCompleted {
        void onCaptureCompleted(@NonNull TotalCaptureResult result);
    }
}
