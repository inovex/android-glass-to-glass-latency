package de.inovex.latencytest;

import android.app.Activity;
import android.os.Bundle;
import android.view.Choreographer;
import android.widget.TextView;

/**
 * Simple activity showing the a duration with milliseconds precision
 *
 * This activity tries to provide a millisecond clock at best as technically possible.
 * For every display refresh cycle, the current duration is set. It provides
 * three decimal digits for the seconds value. This is an improvement compared to the standard
 * Android clock App.
 *
 * Note that the display refresh rate is 30 or 60 Hz, which means it takes 33 or 16 milliseconds
 * between every display redraw. The clock on the display can never be more precise or update
 * faster.
 */
public class MilliSecondsClock extends Activity implements Choreographer.FrameCallback {
    private TextView textViewClock;
    private long startTimeMilliseconds = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_milli_seconds_clock);
        textViewClock = findViewById(R.id.textViewClock);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Choreographer.getInstance().postFrameCallback(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Choreographer.getInstance().removeFrameCallback(this);
        startTimeMilliseconds = -1;
    }

    @Override
    public void doFrame(long l) {
        long currentTimeMilliseconds = System.currentTimeMillis();
        if (startTimeMilliseconds < 0) {
            startTimeMilliseconds = currentTimeMilliseconds;
        }
        long durationMilliseconds = currentTimeMilliseconds - startTimeMilliseconds;
        setTimeInClock(durationMilliseconds);
        Choreographer.getInstance().postFrameCallback(this);
    }

    private void setTimeInClock(long durationMilliseconds) {
        float seconds = (durationMilliseconds % 60_000) / 1000.0F;
        long minutes = durationMilliseconds / 60_000;

        textViewClock.setText(getString(R.string.clockMilliseconds, minutes, seconds));
    }
}