// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

#include <jni.h>
#include <time.h>

// Notes:
// - using CLOCK_BOOTTIME instead of CLOCK_MONOTONIC
//   to match Androids Java API
//   https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos()
extern "C"
JNIEXPORT jlong JNICALL
Java_de_inovex_latencytest_Clock_clockGetTimeInUs(JNIEnv*, jclass) {
	struct timespec ts;
	clock_gettime(CLOCK_BOOTTIME, &ts);
	return ((jlong) ts.tv_sec) * 1000000 + ts.tv_nsec / 1000;
}

extern "C"
JNIEXPORT jint JNICALL
Java_de_inovex_latencytest_Clock_clockNanoSleepUntil(JNIEnv*, jclass , jlong jtimepoint_us) {
	long timepoint_us = (long) jtimepoint_us;
	struct timespec ts = { timepoint_us / 1000000, (timepoint_us % 1000000) * 1000};
	clock_nanosleep(CLOCK_BOOTTIME, TIMER_ABSTIME, &ts, nullptr);
	return 42;
}
