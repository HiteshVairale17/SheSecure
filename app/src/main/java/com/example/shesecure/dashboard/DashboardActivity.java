package com.example.shesecure.dashboard;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.auth.SessionManager;
import com.example.shesecure.auth.WelcomeActivity;
import com.example.shesecure.contacts.EmergencyContactsActivity;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.emergency.SosCountdownActivity;
import com.example.shesecure.models.EmergencyContact;
import com.example.shesecure.profile.ProfileActivity;
import com.example.shesecure.sensors.ShakeDetector;
import com.example.shesecure.settings.PermissionGuidanceActivity;
import com.example.shesecure.settings.SettingsActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.List;

public class DashboardActivity extends AppCompatActivity {

    private static final long HOLD_DURATION_MS = 2500;
    private static final long UPDATE_INTERVAL_MS = 25;
    private static final int REQ_CODE_EMERGENCY_PERMS = 101;

    private TextView tvGreeting;
    private TextView tvProtectionBadge;
    private TextView tvShakeStatus;
    private TextView tvContactsCount;
    private TextView tvSmsStatus;
    private TextView tvLocationStatus;
    private SwitchMaterial switchShakeQuick;

    private View btnSosTrigger;
    private CircularProgressIndicator sosProgressRing;

    private MaterialCardView cardPermissionWarning;
    private MaterialButton btnGrantPermissions;
    private MaterialCardView cardTestSos;
    private MaterialCardView cardContacts;
    private MaterialCardView cardShake;
    private MaterialCardView cardPermissions;
    private MaterialCardView cardAppGuide;
    private ImageView btnSettings;
    private ImageView btnProfile;

    private LocalContactCache cache;
    private Vibrator vibrator;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ShakeDetector shakeDetector;

    private final Handler holdHandler = new Handler(Looper.getMainLooper());
    private long holdStartTime = 0;
    private boolean isSosTriggered = false;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            if (holdStartTime == 0) return;

            long elapsed = System.currentTimeMillis() - holdStartTime;
            int progress = (int) ((elapsed / (float) HOLD_DURATION_MS) * 100);

            if (progress >= 100) {
                sosProgressRing.setProgress(100);
                triggerSosAlert();
            } else {
                sosProgressRing.setProgress(progress);
                holdHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        }
    };

    private SessionManager sessionManager;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = SessionManager.getInstance(this);
        if (!sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_dashboard);

        cache = LocalContactCache.getInstance(this);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // UI references
        tvGreeting = findViewById(R.id.tv_greeting);
        tvProtectionBadge = findViewById(R.id.tv_protection_badge);
        tvShakeStatus = findViewById(R.id.tv_shake_status);
        tvContactsCount = findViewById(R.id.tv_contacts_count);
        tvSmsStatus = findViewById(R.id.tv_sms_status);
        tvLocationStatus = findViewById(R.id.tv_location_status);
        switchShakeQuick = findViewById(R.id.switch_shake_quick);

        btnSosTrigger = findViewById(R.id.btn_sos_trigger);
        sosProgressRing = findViewById(R.id.sos_progress_ring);

        cardPermissionWarning = findViewById(R.id.card_permission_warning);
        btnGrantPermissions = findViewById(R.id.btn_grant_permissions);
        cardTestSos = findViewById(R.id.card_test_sos);
        cardContacts = findViewById(R.id.card_manage_contacts);
        cardShake = findViewById(R.id.card_shake_settings);
        cardPermissions = findViewById(R.id.card_permissions);
        cardAppGuide = findViewById(R.id.card_app_guide);
        btnSettings = findViewById(R.id.btn_settings);
        btnProfile = findViewById(R.id.btn_profile);

        // Navigation listeners
        if (cardTestSos != null) {
            cardTestSos.setOnClickListener(v -> triggerTestSos());
        }
        if (btnGrantPermissions != null) {
            btnGrantPermissions.setOnClickListener(v -> requestEmergencyPermissionsIfMissing());
        }
        cardContacts.setOnClickListener(v -> startActivity(new Intent(this, EmergencyContactsActivity.class)));
        cardShake.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        cardPermissions.setOnClickListener(v -> startActivity(new Intent(this, PermissionGuidanceActivity.class)));
        cardAppGuide.setOnClickListener(v -> startActivity(new Intent(this, com.example.shesecure.settings.AppGuideActivity.class)));
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        // Shake quick switch
        switchShakeQuick.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cache.setShakeEnabled(isChecked);
            updateStatusUi();
        });

        // Intentional Press and Hold SOS listener
        btnSosTrigger.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    isSosTriggered = false;
                    holdStartTime = System.currentTimeMillis();
                    holdHandler.post(progressRunnable);
                    vibrateTick(50);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacks(progressRunnable);
                    if (!isSosTriggered) {
                        holdStartTime = 0;
                        sosProgressRing.setProgress(0);
                        Toast.makeText(this, "Hold SOS button for 2.5s to trigger alert.", Toast.LENGTH_SHORT).show();
                    }
                    return true;
            }
            return false;
        });

        // Setup Shake Detector
        initShakeDetector();

        // Background sync contacts and profile from Supabase
        syncDataFromSupabase();

        // Request emergency permissions upfront so user is ready when crisis occurs
        requestEmergencyPermissionsIfMissing();
    }

    private void requestEmergencyPermissionsIfMissing() {
        List<String> missing = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.SEND_SMS);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            missing.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (!missing.isEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_CODE_EMERGENCY_PERMS);
        }
    }

    private void triggerTestSos() {
        Intent intent = new Intent(this, SosCountdownActivity.class);
        intent.putExtra(SosCountdownActivity.EXTRA_TRIGGER_SOURCE, SosCountdownActivity.SOURCE_TEST);
        intent.putExtra(SosCountdownActivity.EXTRA_IS_TEST_MODE, true);
        startActivity(intent);
    }

    private void syncDataFromSupabase() {
        com.example.shesecure.data.remote.SupabaseDataService dataService =
                com.example.shesecure.data.remote.SupabaseDataService.getInstance(this);

        dataService.fetchContacts(new com.example.shesecure.data.remote.SupabaseDataService.ContactsCallback() {
            @Override
            public void onSuccess(List<EmergencyContact> remoteList) {
                if (isFinishing() || isDestroyed()) return;
                if (!remoteList.isEmpty()) {
                    cache.saveContacts(remoteList);
                    updateStatusUi();
                } else {
                    List<EmergencyContact> local = cache.getContacts();
                    for (EmergencyContact c : local) {
                        dataService.addContact(c, new com.example.shesecure.data.remote.SupabaseDataService.ContactActionCallback() {
                            @Override public void onSuccess(EmergencyContact contact) {}
                            @Override public void onError(String errorMessage) {}
                        });
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {}
        });

        dataService.fetchFullProfile(new com.example.shesecure.data.remote.SupabaseDataService.FullProfileCallback() {
            @Override
            public void onSuccess(String remoteName, String remotePhone) {
                if (isFinishing() || isDestroyed()) return;
                if (remoteName != null && !remoteName.trim().isEmpty() && !remoteName.equals("User")) {
                    sessionManager.updateDisplayName(remoteName);
                    cache.setUserName(remoteName);
                }
                if (remotePhone != null && !remotePhone.trim().isEmpty()) {
                    sessionManager.updatePhoneNumber(remotePhone);
                    cache.setUserPhone(remotePhone);
                }
                updateStatusUi();
            }

            @Override
            public void onError(String errorMessage) {}
        });
    }

    private void initShakeDetector() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            shakeDetector = new ShakeDetector();
            shakeDetector.setOnShakeListener(() -> {
                if (cache.isShakeEnabled()) {
                    triggerSosAlert();
                }
            });
        }
    }

    private void triggerSosAlert() {
        isSosTriggered = true;
        holdStartTime = 0;
        sosProgressRing.setProgress(0);
        vibrateTick(150);

        Intent intent = new Intent(this, SosCountdownActivity.class);
        intent.putExtra(SosCountdownActivity.EXTRA_TRIGGER_SOURCE, SosCountdownActivity.SOURCE_IN_APP);
        intent.putExtra(SosCountdownActivity.EXTRA_IS_TEST_MODE, false);
        startActivity(intent);
    }

    private void vibrateTick(long ms) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(ms);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sessionManager == null || !sessionManager.isLoggedIn()) {
            Intent intent = new Intent(this, WelcomeActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        updateStatusUi();

        // Register accelerometer listener if shake is enabled
        if (sensorManager != null && accelerometer != null && cache.isShakeEnabled()) {
            shakeDetector.setSensitivity(cache.getShakeSensitivity());
            sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null && shakeDetector != null) {
            sensorManager.unregisterListener(shakeDetector);
        }
        holdHandler.removeCallbacks(progressRunnable);
        sosProgressRing.setProgress(0);
        holdStartTime = 0;
    }

    private void updateStatusUi() {
        // Greeting from authenticated Supabase session
        String name = sessionManager != null ? sessionManager.getDisplayName() : "User";
        tvGreeting.setText("Hello, " + name);

        // Contacts count
        List<EmergencyContact> contacts = cache.getContacts();
        tvContactsCount.setText(contacts.size() + " of 5 contacts configured");

        // Shake status
        boolean shakeActive = cache.isShakeEnabled();
        switchShakeQuick.setChecked(shakeActive);
        tvShakeStatus.setText(shakeActive ? R.string.shake_detection_active : R.string.shake_detection_inactive);

        // Permissions check
        boolean hasSms = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED;
        if (tvSmsStatus != null) {
            tvSmsStatus.setText(hasSms ? "Automatic Direct SMS Ready" : "SMS Permission Required");
            tvSmsStatus.setTextColor(ContextCompat.getColor(this, hasSms ? R.color.text_secondary : R.color.emergency_red));
        }

        boolean hasLocation = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (tvLocationStatus != null) {
            tvLocationStatus.setText(hasLocation ? "GPS Location Available" : "Location Permission Required");
            tvLocationStatus.setTextColor(ContextCompat.getColor(this, hasLocation ? R.color.text_secondary : R.color.emergency_red));
        }

        if (cardPermissionWarning != null) {
            cardPermissionWarning.setVisibility((!hasSms || !hasLocation) ? View.VISIBLE : View.GONE);
        }

        // Overall badge
        if (contacts.isEmpty() || !hasSms || !hasLocation) {
            tvProtectionBadge.setText(R.string.status_attention_needed);
            tvProtectionBadge.setBackgroundResource(R.drawable.bg_badge_warning);
            tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.status_warning_amber));
        } else {
            tvProtectionBadge.setText(R.string.status_protected);
            tvProtectionBadge.setBackgroundResource(R.drawable.bg_badge_active);
            tvProtectionBadge.setTextColor(ContextCompat.getColor(this, R.color.status_active_green));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_EMERGENCY_PERMS) {
            updateStatusUi();
        }
    }
}
