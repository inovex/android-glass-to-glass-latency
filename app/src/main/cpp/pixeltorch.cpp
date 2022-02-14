// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

#define LOG_TAG "PixelTorch"

#include <log/log.h>
#include <jni.h>
#include <pixeltorch/pixeltorch.h>
#include <errno.h>
#include <string.h>

extern "C"
JNIEXPORT jint JNICALL
Java_de_inovex_latencytest_PixelTorch_init(JNIEnv*, jclass) {
    int ret = pixeltorch_init(pixeltorch_device);
    if (ret != 0) {
        ALOGE("Cannot open torch device: %s", strerror(errno));
    }
    return ret;
}

extern "C"
JNIEXPORT jint JNICALL
Java_de_inovex_latencytest_PixelTorch_setMode(JNIEnv*, jobject, jint fd, jboolean on) {
    return pixeltorch_setmode(fd, on);
}

extern "C"
JNIEXPORT void JNICALL
Java_de_inovex_latencytest_PixelTorch_deinit(JNIEnv*, jobject, jint fd) {
    pixeltorch_deinit(fd);
}
