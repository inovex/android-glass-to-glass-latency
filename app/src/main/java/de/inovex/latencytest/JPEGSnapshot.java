// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Trace;
import android.util.Log;
import android.view.Surface;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

// TODO Combine camera implementation with MainActiviy
// The Advantage of using the JPEG Pixel format is that the HAL/camera_server already
// adds the EXIF data to the image.
public class JPEGSnapshot extends Activity {
    private final static String TAG = "JPEGSnapshot";
    //final static int WIDTH = 480; final static int HEIGHT = 360; // only on pixel
    //final static int WIDTH = 352; final static int HEIGHT = 288; // only on emulator
    //final static int WIDTH = 640; final static int HEIGHT = 480;
    final static int WIDTH = 1920;
    final static int HEIGHT = 1080;
    // the backfacing camera on the pixel2 supports 60hz!
    final int FPS = 30; // NOTE 60 fps only works when AE mode is not manual. Must be auto.
    CameraManager manager;
    String cameraId;
    ImageReader imageReader;
    Surface surface;
    Camera camera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_jpeg_snapshot);

        imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.JPEG, 3);
        // This runs on the main/UI thread on purpose!
        imageReader.setOnImageAvailableListener(callback, null);
        surface = imageReader.getSurface();

        manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        cameraId = Utils.getBackfacingCameraId(manager);

        camera = new Camera(manager);
        camera.configure(cameraId, surface);
        camera.start(30);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        if (camera != null) {
            camera.close();
        }
        surface = null;
        if (imageReader != null)
            imageReader.close();
    }

    ImageReader.OnImageAvailableListener callback = (ImageReader imager) -> {
        Image image = imageReader.acquireLatestImage();
        if (image != null) {
            long timestamp = image.getTimestamp();
            Trace.beginSection("onImageAvailable timestamp=" + timestamp);
            try(image) {
                saveImage(image);
            }
            Trace.endSection();
        }
    };

    @Override
    protected  void onPause()  {
        Log.d(TAG, "onPause");
        super.onPause();
        if (camera != null) {
            camera.stop();
        }
    }

    @Override
    protected  void onResume()  {
        Log.d(TAG, "onResume");
        super.onResume();
        if (camera != null) {
            camera.start(30);
        }
    }

    // get image wit
    // adb shell run-as de.inovex.latencytest cat /data/user/0/de.inovex.latencytest/files/image0.jpeg > image0.jpeg
    int counter = 0;
    void saveImage(Image image) {
        assert(image.getWidth() == WIDTH);
        assert(image.getHeight() == HEIGHT);
        assert(image.getFormat() == ImageFormat.JPEG);
        assert(image.getPlanes().length == 1);

        if (counter != 0) {
            return;
        }

        String path = getFilesDir().getPath() + "/" + "image" + counter + ".jpeg";
        Log.d(TAG, "Writing file to path: " + path);
        try {
            try(FileOutputStream stream = new FileOutputStream(path)) {
                ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                byte[] buf = new byte[buffer.remaining()];
                buffer.get(buf);
                stream.write(buf);
            }
        } catch (IOException e) {
            Log.e(TAG, "Cannot write to file", e);
        }

        counter += 1;
    }
}
