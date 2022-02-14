// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

#include <pixeltorch/pixeltorch.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdio.h>
#include <time.h>

// Toggle the Pixel2 torch as fast as possible.

void sleep_nanoseconds(long ns) {
	struct timespec ts = {0, ns};
	clock_nanosleep(CLOCK_BOOTTIME, 0, &ts, NULL);
}

uint64_t get_time() {
	struct timespec ts;
	clock_gettime(CLOCK_BOOTTIME, &ts);
	return ((uint64_t)ts.tv_sec) * 1000000000 + ts.tv_nsec;
}

void sleep_until(uint64_t time_in_ns) {
	struct timespec ts = {time_in_ns / 1000000000, time_in_ns % 1000000000};
	clock_nanosleep(CLOCK_BOOTTIME, TIMER_ABSTIME, &ts, NULL);
}

atomic_bool stop_loop = false;
void sig_handler(int signum) {
	(void)signum;

	stop_loop = true;
}

int main(int argc, char **argv) {
	(void)argc, (void)argv;
	int fd;
	int ret;

	// TODO to get less jitter, this process should get a lower nice value
	// and a real time priority/scheduling policy.

	signal(SIGINT, sig_handler); // Register signal handler

	fd = pixeltorch_init(pixeltorch_device);
	if (fd < 0) {
		return 1;
	}

	printf("Successfully opened the v4l device.\n");
	printf("Running the mainloop...\n");
	fflush(stdout);

	uint64_t wait_until_in_ns = get_time();
	while (!stop_loop) {
		ret = pixeltorch_setmode(fd, true);
		if (ret != 0) {
			goto err;
		}

		wait_until_in_ns += 350000;
		sleep_until(wait_until_in_ns);

		ret = pixeltorch_setmode(fd, false);
		if (ret != 0) {
			goto err;
		}

		// Setting this to a lower value does not make the signal go
		// faster! So the code until this point needs around
		// ~50us=50000ns
		wait_until_in_ns += 50000;
		sleep_until(wait_until_in_ns);
	}

err:
	pixeltorch_deinit(fd);

	printf("Successfully closed the v4l device.\n");
	fflush(stdout);

	return 0;
}
