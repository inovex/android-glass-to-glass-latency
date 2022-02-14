
.PHONY: compile
compile:
	cd .. && ./gradlew assembleDebug

.PHONY: reformat
reformat:
	clang-format -i flasher.c libpixeltorch.c includes/pixeltorch/pixeltorch.h

.PHONY: run
run:
	# The folder /data/loacl/tmp/flasher is also writeable for the 'shell' user
	adb push ../app/build/intermediates/cmake/debug/obj/arm64-v8a/flasher /data/local/tmp/flasher
	# If adb runs as non-root, the programm cannot open the v4l device
	adb shell /data/local/tmp/flasher
