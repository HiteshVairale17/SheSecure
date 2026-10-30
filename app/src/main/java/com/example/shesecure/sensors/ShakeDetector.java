package com.example.shesecure.sensors;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public class ShakeDetector implements SensorEventListener {

    public interface OnShakeListener {
        void onShake();
    }

    // Default acceleration threshold in m/s^2 (Standard is ~13 m/s^2 above gravity)
    private static final float SHAKE_THRESHOLD_LOW = 18.0f;
    private static final float SHAKE_THRESHOLD_MEDIUM = 14.5f;
    private static final float SHAKE_THRESHOLD_HIGH = 11.5f;

    private static final int SHAKE_SLOP_TIME_MS = 400;
    private static final int SHAKE_COUNT_RESET_TIME_MS = 2500;
    private static final int REQUIRED_SHAKE_COUNT = 3; // deliberate back-and-forth

    private OnShakeListener listener;
    private long lastShakeTimestamp = 0;
    private int shakeCount = 0;
    private float currentThreshold = SHAKE_THRESHOLD_MEDIUM;

    public void setOnShakeListener(OnShakeListener listener) {
        this.listener = listener;
    }

    public void setSensitivity(int sensitivity) {
        if (sensitivity == 0) {
            currentThreshold = SHAKE_THRESHOLD_LOW;
        } else if (sensitivity == 2) {
            currentThreshold = SHAKE_THRESHOLD_HIGH;
        } else {
            currentThreshold = SHAKE_THRESHOLD_MEDIUM;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (listener == null) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        float gX = x / SensorManager.GRAVITY_EARTH;
        float gY = y / SensorManager.GRAVITY_EARTH;
        float gZ = z / SensorManager.GRAVITY_EARTH;

        // gForce is near 1 when device is resting
        float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

        float thresholdG = currentThreshold / SensorManager.GRAVITY_EARTH;

        if (gForce > thresholdG) {
            final long now = System.currentTimeMillis();
            // Ignore shakes too close together (debouncing)
            if (lastShakeTimestamp + SHAKE_SLOP_TIME_MS > now) {
                return;
            }

            // Reset shake count if too much time passed
            if (lastShakeTimestamp + SHAKE_COUNT_RESET_TIME_MS < now) {
                shakeCount = 0;
            }

            lastShakeTimestamp = now;
            shakeCount++;

            if (shakeCount >= REQUIRED_SHAKE_COUNT) {
                shakeCount = 0;
                listener.onShake();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
