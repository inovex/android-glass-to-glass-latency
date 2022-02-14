# Pixel2 torch control

This directory contains a static C library to control the Pixel2 torch.  It's
the flash of the backfacing camera. It also contains a program to toggle the
torch as fast as possible.

Traditionally the torch can be controlled via the Java API of the Camera2
interface:

    https://developer.android.com/reference/android/hardware/camera2/CameraManager#setTorchMode(java.lang.String,%20boolean)

But this requires a significant runtime overhead and has a high latency. This
is totally acceptable for normal usage as a torch or as a camera flash light.
Nevertheless I had wanted to use the torch for something different.


# New requirements

I want to use the torch as a external synchronization signal. E.g. to mark time
points on the oscilloscope capture, or to exposure specific rows in the camera
sensor or correlate systrace traces with oscilloscope captures.

Therefore I want a minimal latency and minimal overhead method to control an
external signal.

Normally you would use a GPIO pin, but the Pixel2 phone is no development
board. I don't know of any available GPIO pins.


# Development notes

This code was developed with normal debugging techniques:


## How to find out the video for linux device

Start an app that toggles the torch.

    $ adb root
    $ adb shell lshal | grep camera
    FM    Y android.frameworks.cameraservice.service@2.0::ICameraService/default   0/4 943    582
    DM,FC Y android.hardware.camera.provider@2.4::ICameraProvider/legacy/0  0/3 785    943 582
    FC    ? android.hardware.camera.provider@2.4::ICameraProvider/legacy/0  N/A 785    785
    FC    ? android.hardware.camera.provider@2.4::I*/* (/vendor/lib/hw/)   N/A N/A    785

The pid is 785. Then start strace and thread all threads

    $ adb shell strace -fp 785 -e openat
    [...]
    [pid  5697] openat(AT_FDCWD, "/dev/v4l-subdev12", O_RDWR|O_NONBLOCK|O_LARGEFILE) = 26
    [pid  5697] openat(AT_FDCWD, "/dev/v4l-subdev12", O_RDWR|O_NONBLOCK|O_LARGEFILE) = 26
    [...]

The video for linux device is opened and close for every on-off cycle.

It seems that the device number is stable across reboots. It's always
'v4l-subdev12'.

The device file is read/write for root and the group 'camera'.

    1|walleye:/ $ ls -lh /dev/v4l-subdev12
    crw-rw---- 1 system camera 81, 140 1972-10-18 14:09 /dev/v4l-subdev12


## syscalls to set the torch on and off

Setting torch on:

    , 0xffdaad68) = 0
    [pid   792]      1.983605 futex(0xe9a2a090, FUTEX_WAKE_PRIVATE, 2147483647) = 0
    [pid   792]      0.000266 openat(AT_FDCWD, "/dev/v4l-subdev12", O_RDWR|O_NONBLOCK|O_LARGEFILE) = 26
    [pid   792]      0.000287 ioctl(26, _IOC(_IOC_READ|_IOC_WRITE, 0x56, 0xcd, 0x20), 0xffdaa9e8) = 0
    [pid   792]      0.000189 nanosleep({tv_sec=21474836480000000, tv_nsec=1839096282},
    NULL) = 0
    [pid   792]      0.005335 ioctl(26, _IOC(_IOC_READ|_IOC_WRITE, 0x56, 0xcd, 0x20), 0xffdaaa60) = 0
    [pid   792]      0.000343 ioctl(6, BINDER_WRITE_READ, 0xffdaa7f8) = 0
    [pid   792]      0.004044 ioctl(6, BINDER_WRITE_READ, 0xffdaaa28) = 0
    [pid   792]      0.000715 futex(0xe9a2a090, FUTEX_WAKE_PRIVATE, 2147483647) = 0
    [pid   792]      0.000221 ioctl(6, BINDER_WRITE_READ

Setting torch off:

    , 0xffdaad68) = 0
    [pid   792]      1.003354 futex(0xe9a2a090, FUTEX_WAKE_PRIVATE, 2147483647) = 0
    [pid   792]      0.002258 ioctl(26, _IOC(_IOC_READ|_IOC_WRITE, 0x56, 0xcd, 0x20), 0xffdaaa60) = 0
    [pid   792]      0.003053 ioctl(26, _IOC(_IOC_READ|_IOC_WRITE, 0x56, 0xcd, 0x20), 0xffdaaa68) = 0
    [pid   792]      0.001223 close(26)     = 0
    [pid   792]      0.001395 ioctl(6, BINDER_WRITE_READ, 0xffdaa7f8) = 0
    [pid   792]      0.006341 ioctl(6, BINDER_WRITE_READ, 0xffdaaa28) = 0
    [pid   792]      0.002080 futex(0xe9a2a090, FUTEX_WAKE_PRIVATE, 2147483647) = 0
    [pid   792]      0.000808 ioctl(6, BINDER_WRITE_READ


## HAL implementation

But the camera HAL implementation is

    $ $HOME/Android/Sdk/ndk-bundle/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android-objdump -CT camera.msm8998.so | grep -i flashMode
    000f45f0 g    DF .text	00000040  Base        qcamera::QCameraParametersIntf::updateFlashMode(cam_flash_mode_t)
    000eb074 g    DF .text	00000184  Base        qcamera::QCameraParameters::updateFlashMode(cam_flash_mode_t)
    000764b0 g    DF .text	0000014c  Base        qcamera::QCameraFlash::setFlashMode(int, bool)

The source code is at

    https://cs.android.com/android/platform/superproject/+/android-10.0.0_r1:hardware/qcom/camera/msm8998/QCamera2/util/QCameraFlash.cpp
