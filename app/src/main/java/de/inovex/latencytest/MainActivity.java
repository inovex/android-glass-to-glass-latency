// SPDX-License-Identifier: MIT
// SPDX-FileCopyrightText: Copyright (c) 2022 inovex GmbH

package de.inovex.latencytest;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

public class MainActivity extends Activity {
    private static final String TAG = "RequestPermissions";
    final int REQUEST_CAMERA_PERMISSION = 21;
    TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        textView = findViewById(R.id.status);

        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            success();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void success() {
        textView.setText("All necessary permissions granted.");
    }

    private void fail() {
        textView.setText("Permissions are not granted!");
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                fail();
            } else {
                success();
            }
        }
    }

    public void startBWFlip(View view) {
        Intent intent = new Intent(this, BWFlip.class);
        startActivity(intent);
    }

    public void startFlashToggleSpeed(View view) {
        Intent intent = new Intent(this, FlashToggleSpeed.class);
        startActivity(intent);
    }

    public void startJPEGSnapshot(View view) {
        Intent intent = new Intent(this, JPEGSnapshot.class);
        startActivity(intent);
    }

    public void startCameraToDisplay(View view) {
        Intent intent = new Intent(this, CameraToDisplay.class);
        startActivity(intent);
    }
}
