// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.app.Activity;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;

/*
 * Toggle the flash of the backfacing camera
 *
 * Simple activity to measure and test the possible maximum precision of the
 * backlight.
 *
 * It uses native code to use the C/POSIX function clock_nanosleep(). It allows
 * precise wakeups at specific timepoints. It greatly reduces the jitter and
 * improves the precision compared to a naive Thread.sleep() implementation.
 *
 * The activity can also use a implementation based on java.util.Timer. It
 * performs nearly the same, as the thread implementation. It sees that it
 * also uses clock_nanosleep() under the hood. Wakeups precision and jitter
 * is comparable.
 *
 * Results: Wakup every 10 ms is possible. The torch on time is only 5 ms, not 10 ms.
 * It seems that switching the light on (or off) needs around ~5 ms.
 * There are nearly no "not fast enough messages".
 *
 * Using a period/wakeup time of 5ms is not possible. There are a lot of "not fast" enough
 * messages and the on time of the torch is only 2 ms with a lot of jitter.
 */
public class FlashToggleSpeed extends Activity implements Runnable {
    private static final String TAG = "FlashToogleSpeed";
    private CameraManager manager;
    private Thread thread;
    private final AtomicBoolean stopThread = new AtomicBoolean(false);
    Timer timer;
    private final static boolean USE_TIMER = false; // instead of Thread
    private final static int PERIOD_IN_MS = 5;
    int counter = 0;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        manager = getSystemService(CameraManager.class);
        cameraId = Utils.getBackfacingCameraId(manager);
        setContentView(R.layout.activity_flash_toogle_speed);
    }

    private void updateTorch() {
        Trace.beginSection("updateTorch");
        counter = (counter + 1) % 2;
        try {
            manager.setTorchMode(cameraId, counter == 0);
        } catch (CameraAccessException e) {
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

            long sleep_until_timepoint_in_us = timepoint_in_us + PERIOD_IN_MS * 1000;
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
        if (USE_TIMER) {
            timer = new Timer();
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    updateTorch();
                }
            }, PERIOD_IN_MS, PERIOD_IN_MS);
        } else {
            stopThread.set(false);
            thread = new Thread(this);
            thread.start();
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (USE_TIMER) {
            timer.cancel();
            timer = null;
        } else {
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
        }
        // Disable the flash light if it is currently enabled.
        try {
            manager.setTorchMode(cameraId, false);
        } catch (CameraAccessException e) {
            Log.e(TAG, "cannot set torch mode", e);
        }
    }
}
