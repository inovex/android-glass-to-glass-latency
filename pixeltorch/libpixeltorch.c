// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

#include "pixeltorch/pixeltorch.h"

#include <fcntl.h>
#include <stdio.h>
#include <unistd.h>

// Kernel headers:
#include "msm_cam_sensor.h"

// Needed global define. Original code
// https://cs.android.com/android/platform/superproject/+/android-10.0.0_r1:hardware/qcom/camera/msm8998/QCamera2/util/QCameraFlash.h;l=42
#define QCAMERA_TORCH_CURRENT_VALUE 200

const char *pixeltorch_device = "/dev/v4l-subdev12";

// Original code:
// https://cs.android.com/android/platform/superproject/+/android-10.0.0_r1:hardware/qcom/camera/msm8998/QCamera2/util/QCameraFlash.cpp;l=138
int pixeltorch_init(const char *path) {
	int fd;
	int ret;
	struct msm_flash_cfg_data_t cfg = {};
	struct msm_flash_init_info_t init_info = {};

	fd = open(path, O_RDWR);
	if (fd < 0) {
		perror("open");
		return fd;
	}

	init_info.flash_driver_type = FLASH_DRIVER_DEFAULT;
	cfg.cfg.flash_init_info = &init_info;
	cfg.cfg_type = CFG_FLASH_INIT;

	ret = ioctl(fd, VIDIOC_MSM_FLASH_CFG, &cfg);
	if (ret != 0) {
		perror("init");
		close(fd);
		return -1;
	}

	usleep(5000); // Same as original code

	return fd;
}

// Original code:
// https://cs.android.com/android/platform/superproject/+/android-10.0.0_r1:hardware/qcom/camera/msm8998/QCamera2/util/QCameraFlash.cpp;l=219
int pixeltorch_setmode(int fd, bool on) {
	int ret;
	struct msm_flash_cfg_data_t cfg = {};

	// TODO: why is this needed?
	for (int i = 0; i < MAX_LED_TRIGGERS; i++)
		cfg.flash_current[i] = QCAMERA_TORCH_CURRENT_VALUE;

	cfg.cfg_type = on ? CFG_FLASH_LOW : CFG_FLASH_OFF;

	ret = ioctl(fd, VIDIOC_MSM_FLASH_CFG, &cfg);
	if (ret != 0) {
		perror("setmode");
		return ret;
	}

	return ret;
}

// Original code:
// https://cs.android.com/android/platform/superproject/+/android-10.0.0_r1:hardware/qcom/camera/msm8998/QCamera2/util/QCameraFlash.cpp;l=269
void pixeltorch_deinit(int fd) {
	int ret;
	struct msm_flash_cfg_data_t cfg = {};

	pixeltorch_setmode(fd, false);

	cfg.cfg_type = CFG_FLASH_RELEASE;
	ret = ioctl(fd, VIDIOC_MSM_FLASH_CFG, &cfg);
	if (ret != 0) {
		perror("deinit");
	}

	close(fd);
}
