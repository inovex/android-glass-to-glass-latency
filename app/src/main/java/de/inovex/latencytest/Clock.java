// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

public final class Clock {
    static {
        System.loadLibrary("latencytest");
    }

    public static native int clockNanoSleepUntil(long timepoint_in_us);
    public static native long clockGetTimeInUs();
}
