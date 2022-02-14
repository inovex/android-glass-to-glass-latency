// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Trace;
import android.util.Log;
import android.view.Choreographer;

import androidx.constraintlayout.widget.ConstraintLayout;

/*
 * Switch background and torch between white/on and off/black.
 *
 * Activity can be used to test
 * - Scanout duration of a display. Two probes. One at the top and one at the bottom of the display.
 * - Flash to Display Latency:
 *   The flash is set to on and the background color of the activity is set to white at the same time.
 *   But the activity change needs some to to propagate, trough the RenderThread, GPU, Surfaceflinger
 *   until it's display at the next vsync event.
 */
public class BWFlip extends Activity implements Choreographer.FrameCallback {
    final static String TAG = "BWFlip";
    ConstraintLayout layout;
    FrameRater vsyncRate = new FrameRater(TAG,"vsync");
    int counter = 0;
    CameraManager manager;
    private String cameraId;
    HandlerThread handlerThread;
    Handler handler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        manager = getSystemService(CameraManager.class);
        cameraId = Utils.getBackfacingCameraId(manager);
        setContentView(R.layout.activity_bwflip);
        layout = findViewById(R.id.background);
        handlerThread = new HandlerThread("torch");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public void doFrame(long l) {
        vsyncRate.tick();
        Choreographer.getInstance().postFrameCallback(this);
        switchBackgroundAndTorch();
    }

    void switchBackgroundAndTorch() {
        counter = (counter + 1) % 2;
        boolean is_white = counter == 0;
        int color = is_white ? 0xFFFFFFFF : 0xFF000000;

        Trace.beginSection(is_white ? "switchBackground-to-white" : "switchBackground-to-black");

        layout.setBackgroundColor(color);

        if (is_white) {
            // Fire of the flash a bit later. With a delay of 23 ms
            // The flash fires nearly at the same time as the pixels on the display
            // light up.
            handler.postDelayed(() -> {
                setTorch(true);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                setTorch(false);
            }, 25);
        }
        Trace.endSection();
    }

    void setTorch(boolean value) {
        Trace.beginSection(value ? "setTorch-to-on" : "setTorch-to-off");
        try {
            manager.setTorchMode(cameraId, value);
        } catch (CameraAccessException e) {
            Log.e(TAG, "error", e);
        } finally {
            Trace.endSection();
        }
    }

    @Override
    protected  void onPause()  {
        Log.d(TAG, "onPause");
        super.onPause();
        Choreographer.getInstance().removeFrameCallback(this);
        layout.setBackgroundColor(0xFFFFFFFF); // set to white
        try {
            manager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot switch of torch", e);
        }
    }

    @Override
    protected  void onResume()  {
        Log.d(TAG, "onResume");
        super.onResume();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    public void onDestroy()  {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        handlerThread.quitSafely();
    }
}
