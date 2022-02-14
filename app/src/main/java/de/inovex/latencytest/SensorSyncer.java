// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.util.Log;

import java.io.Closeable;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sensor Exposure Syncer
 *
 * This class allows fire a callback when the exposure of a new frame
 * in the camera sensor starts. It syncs the callback invocation based on the
 * reported timestamps from the sensor.
 */
public class SensorSyncer implements Closeable {
    private final static String TAG = "SensorSyncer";
    Thread thread;
    AtomicBoolean running = new AtomicBoolean(true);
    long delay_in_us = 33_000; // default delay
    long last_timestamp_in_us;
    Runnable listener;

    SensorSyncer(Runnable listener) {
        thread = new Thread(mainloop);
        thread.start();
        this.listener = listener;
    }

    Runnable mainloop = () -> {
        last_timestamp_in_us = Clock.clockGetTimeInUs();
        while (running.get()) {
            long current_time_in_us = Clock.clockGetTimeInUs();
            long wait_until_in_us = last_timestamp_in_us;
            while (current_time_in_us > wait_until_in_us)
                wait_until_in_us += delay_in_us;

            Clock.clockNanoSleepUntil(wait_until_in_us);
            listener.run();
        }
    };

    void updateTimestamp(long timestamp_in_ns) {
        delay_in_us = (timestamp_in_ns / 1000) - last_timestamp_in_us;
        last_timestamp_in_us = timestamp_in_ns / 1000;
    }

    @Override
    public void close() {
        running.set(false);
        try {
            thread.join(1_000);
        } catch (InterruptedException e) {
            Log.e(TAG, "not expected here", e);
        }
    }
}
