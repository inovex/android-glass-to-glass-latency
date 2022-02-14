// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

#pragma once

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

extern const char *pixeltorch_device; // Default device path

int pixeltorch_init(const char *path);
int pixeltorch_setmode(int fd, bool on);
void pixeltorch_deinit(int fd);

#ifdef __cplusplus
}
#endif
