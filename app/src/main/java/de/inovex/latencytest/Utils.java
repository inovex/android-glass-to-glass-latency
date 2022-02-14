// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

public class Utils {
    public static String getBackfacingCameraId(CameraManager cameraManager) {
        return getCameraId(cameraManager, CameraCharacteristics.LENS_FACING_BACK);
    }

    public static String getFrontfacingCameraId(CameraManager cameraManager) {
        return getCameraId(cameraManager, CameraCharacteristics.LENS_FACING_FRONT);
    }

    private static String getCameraId(CameraManager cameraManager, int facing) {
        try {
            String[] ids = cameraManager.getCameraIdList();
            for (String id : ids) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                int cameraDirection = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cameraDirection == facing) {
                    return id;
                }
            }
            throw new RuntimeException("Camera not found");
        } catch (CameraAccessException ex) {
            throw new RuntimeException(ex);
        }
    }
}
