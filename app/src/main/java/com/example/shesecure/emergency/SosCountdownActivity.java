package com.example.shesecure.emergency;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.auth.SessionManager;
import com.example.shesecure.auth.WelcomeActivity;
import com.example.shesecure.contacts.EmergencyContactsActivity;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.models.EmergencyContact;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.List;

public class SosCountdownActivity extends AppCompatActivity {

    public static final String EXTRA_TRIGGER_SOURCE = "EXTRA_TRIGGER_SOURCE";
    public static final String EXTRA_IS_TEST_MODE = "EXTRA_IS_TEST_MODE";
    public static final String SOURCE_IN_APP = "IN_APP";
    public static final String SOURCE_WIDGET = "WIDGET";
    public static final String SOURCE_LOCK_SCREEN = "LOCK_SCREEN";
    public static final String SOURCE_SHAKE = "SHAKE";
    public static final String SOURCE_TEST = "TEST_SIMULATION";

    private static final long TOTAL_DURATION_MS = 5000;
    private static final long INTERVAL_MS = 100;

    private TextView tvSeconds;
    private TextView tvSubtitle;
    private LinearProgressIndicator progressBar;
    private MaterialButton btnCancel;

    private CountDownTimer timer;
    private Vibrator vibrator;
    private boolean isCancelled = false;
    private boolean isTestMode = false;
    private String triggerSource = SOURCE_IN_APP;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Enable display over lock screen safely without security bypass
        configureLockScreenDisplay();

        // 1. Guard check: Session
        SessionManager sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Please sign in to SheSecure to use emergency features.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        // 2. Guard check: Contacts
        LocalContactCache contactCache = LocalContactCache.getInstance(this);
        List<EmergencyContact> contacts = contactCache.getContacts();
        if (contacts.isEmpty()) {
            Toast.makeText(this, "No emergency contacts configured! Please add trusted contacts first.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(this, EmergencyContactsActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_sos_countdown);

        if (getIntent().hasExtra(EXTRA_TRIGGER_SOURCE)) {
            triggerSource = getIntent().getStringExtra(EXTRA_TRIGGER_SOURCE);
        }

        // Normal SOS flow is unconditionally REAL. Test mode only runs if explicitly passed as true from a separate dedicated test button.
        isTestMode = getIntent().getBooleanExtra(EXTRA_IS_TEST_MODE, false);

        tvSeconds = findViewById(R.id.tv_countdown_seconds);
        tvSubtitle = findViewById(R.id.tv_countdown_subtitle);
        progressBar = findViewById(R.id.countdown_progress);
        btnCancel = findViewById(R.id.btn_cancel_alert);

        if (isTestMode && tvSubtitle != null) {
            tvSubtitle.setText("TEST SOS SIMULATION ACTIVE\nPractice run: No actual SMS will be dispatched to your contacts.");
            tvSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_warning_amber));
        } else if (tvSubtitle != null) {
            tvSubtitle.setText(R.string.countdown_subtitle);
            tvSubtitle.setTextColor(ContextCompat.getColor(this, R.color.emergency_red_dark));
        }

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        btnCancel.setOnClickListener(v -> cancelEmergency());

        startCountdown();
    }

    private void configureLockScreenDisplay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
    }

    private void startCountdown() {
        triggerHapticPulse();

        timer = new CountDownTimer(TOTAL_DURATION_MS, INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (isCancelled) return;

                int secondsLeft = (int) Math.ceil(millisUntilFinished / 1000.0);
                tvSeconds.setText(String.valueOf(secondsLeft));
                progressBar.setProgress((int) millisUntilFinished);

                // Pulse haptic feedback every second
                if (millisUntilFinished % 1000 < 150) {
                    triggerHapticPulse();
                }
            }

            @Override
            public void onFinish() {
                if (isCancelled) return;
                progressBar.setProgress(0);
                tvSeconds.setText("0");
                triggerFinalAlarmVibrate();
                executeEmergencyAlert();
            }
        }.start();
    }

    private void cancelEmergency() {
        isCancelled = true;
        if (timer != null) {
            timer.cancel();
        }
        if (vibrator != null) {
            vibrator.cancel();
        }
        Toast.makeText(this, "Emergency alert cancelled.", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void executeEmergencyAlert() {
        Intent intent = new Intent(this, AlertStatusActivity.class);
        intent.putExtra(EXTRA_TRIGGER_SOURCE, triggerSource);
        intent.putExtra(EXTRA_IS_TEST_MODE, isTestMode);
        startActivity(intent);
        finish();
    }

    private void triggerHapticPulse() {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(120);
            }
        }
    }

    private void triggerFinalAlarmVibrate() {
        if (vibrator != null && vibrator.hasVibrator()) {
            long[] pattern = {0, 300, 150, 300};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                vibrator.vibrate(pattern, -1);
            }
        }
    }

    @Override
    public void onBackPressed() {
        cancelEmergency();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }
}
