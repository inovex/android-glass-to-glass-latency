package de.inovex.latencytest;

import java.io.IOException;

public class PixelTorch implements AutoCloseable {
    static {
        System.loadLibrary("latencytest");
    }

    int fd;

    private PixelTorch(int fd) {
        this.fd = fd;

    }

    public static PixelTorch openPixelTorch() throws IOException {
        int ret = init();
        // If this fails, look in the logcat buffer for the native error message.
        // For example:
        //     E/PixelTorch: Cannot open torch device: Permission denied
        // Then you need to grant the needed permissions:
        //     $ adb root
        //     $ adb shell chmod o+rw /dev/v4l-subdev12
        //     $ adb shell setenforce permissive   # disable seLinux
        if (ret < 0)
            throw new IOException("Cannot open torch device: " + ret);

        return new PixelTorch(ret);
    }

    public void setMode(boolean on) throws IOException {
        int ret = setMode(fd, on);
        if (ret != 0)
            throw new IOException("Failed to set mode: " + ret);
    }

    @Override
    public void close() {
        if (fd >= 0) {
            deinit(fd);
            fd = -1;
        }
    }

    private native static int init();

    private native int setMode(int fd, boolean on);

    private native void deinit(int fd);
}
