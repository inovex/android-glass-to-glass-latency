// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Toggle the flash of the backfacing camera *faster*
 *
 * The class 'FlashToggleSpeed' uses the Java API of the CameraManager
 * to toggle the torch. This class uses the self-made C library that
 * uses the v4l device directly to toggle the torch. It has much fewer
 * overhead and fewer latency.
 *
 * This class cannot use https://docs.oracle.com/javase/7/docs/api/java/util/Timer.html
 * because it only allows to sleep in the accuracy of milliseconds.
 * Here we need 0.1 ms accuracy.
 *
 * Results: The torch can be toggled on and off in 1.6ms with an latency below
 * 800us. This is fast and precise enough to generate a pattern while the
 * exposure of the sensor is taking place. The exposure takes around 32ms.
 */
public class FlashToggleSpeedFaster extends Activity implements Runnable {
    static {
        System.loadLibrary("latencytest");
    }
    private static final String TAG = "FlashToggleSpeedFaster";
    private Thread thread;
    private final AtomicBoolean stopThread = new AtomicBoolean(false);

    // 800us is the fastest delay. If you reduce it further, you get a lot of
    // "not fast enough" messages. This means you can toggle the flash on and off
    // in 1.6 ms.
    private final static long PERIOD_IN_US = 800;
    int counter = 0;
    PixelTorch pixelTorch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_flash_toogle_speed);
        try {
            pixelTorch = PixelTorch.openPixelTorch();
        } catch (IOException e) {
            Log.e(TAG, "Cannot open torch device", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (pixelTorch != null)
            pixelTorch.close();
        pixelTorch = null;
    }

    private void updateTorch() {
        Trace.beginSection("updateTorch");
        counter = (counter + 1) % 2;
        try {
            pixelTorch.setMode(counter == 0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Trace.endSection();
    }

    @Override
    public void run() {
        Log.d(TAG, "Thread mainloop has started.");
        long timepoint_in_us = Clock.clockGetTimeInUs(); // in micro seconds
        while (true) {
            Trace.beginSection("mainloop");

            if (stopThread.get())
                break;
            updateTorch();

            long sleep_until_timepoint_in_us = timepoint_in_us + PERIOD_IN_US;
            long current_timepoint_in_us = Clock.clockGetTimeInUs();
            if (sleep_until_timepoint_in_us < current_timepoint_in_us) {
                Trace.beginSection("mainloop not fast enough");
                Trace.endSection();
                // We are not fast enough. The target timepoint for the sleep is already in the past
                Log.w(TAG, "Mainloop is not fast enough!");
                sleep_until_timepoint_in_us = current_timepoint_in_us;
            }
            timepoint_in_us = sleep_until_timepoint_in_us;
            Clock.clockNanoSleepUntil(timepoint_in_us);
            Trace.endSection();
        }
        Log.d(TAG, "Thread mainloop is returning.");
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        stopThread.set(false);
        thread = new Thread(this);
        thread.start();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();

        Log.d(TAG, "Stopping thread");
        stopThread.set(true);
        thread.interrupt();
        try {
            thread.join(100);
        } catch (InterruptedException e) {
            Log.e(TAG, "Error while waiting for thread to stop", e);
        }
        thread = null;
        Log.d(TAG, "Thread has stopped");

        // Disable the flash light if it is currently enabled.
        try {
            pixelTorch.setMode(false);
        } catch (IOException e) {
            Log.e(TAG, "cannot set torch mode", e);
        }
    }
}
