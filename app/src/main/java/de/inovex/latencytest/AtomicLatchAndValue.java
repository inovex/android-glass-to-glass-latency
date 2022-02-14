package de.inovex.latencytest;

import android.util.Log;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

class AtomicLatchAndValue<X> {
    private static final String TAG = "AtomicLatchAndValue";
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<X> value = new AtomicReference<>(null);
    // TODO add throwable as error value
    AtomicBoolean error = new AtomicBoolean(false);

    AtomicLatchAndValue() {
    }

    public void error() {
        error.set(true);
        value.set(null);
        latch.countDown();
    }

    public void ok(X value) {
        this.value.set(value);
        latch.countDown();
    }

    public Optional<X> get() {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Log.e(TAG, "interrupted", e);
        }
        X value = this.value.get();
        if (error.get()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }
}
