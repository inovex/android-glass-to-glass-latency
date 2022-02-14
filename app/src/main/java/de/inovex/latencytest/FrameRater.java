// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.os.SystemClock;
import android.util.Log;

public class FrameRater {
    final String tag;
    final String name;
    int frames = 0;
    long last_time = -1;

    FrameRater(String tag, String name) {
        this.tag = tag;
        this.name = name;
    }

    public void tick() {
        frames += 1;

        long t = SystemClock.elapsedRealtimeNanos();
        if (last_time == -1) {
            last_time = t;
            frames = 0;
        } else {
            long delta = t - last_time;
            if (delta > 1_000_000_000) {
                Log.d(tag, name + ": frame rate " + frames + " fps");
                last_time = t;
                frames = 0;
            }
        }
    }
}
