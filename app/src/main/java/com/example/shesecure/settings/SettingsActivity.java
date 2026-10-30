package com.example.shesecure.settings;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.emergency.LockScreenAccessManager;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsActivity extends AppCompatActivity {

    private static final int NOTIF_PERMISSION_REQ_CODE = 301;

    private SwitchMaterial switchShake;
    private SwitchMaterial switchLockScreen;
    private RadioGroup rgSensitivity;
    private MaterialCardView cardPermissions;
    private MaterialCardView cardGuide;
    private LocalContactCache cache;
    private LockScreenAccessManager lockScreenManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        cache = LocalContactCache.getInstance(this);
        lockScreenManager = LockScreenAccessManager.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        switchShake = findViewById(R.id.switch_shake_master);
        switchLockScreen = findViewById(R.id.switch_lockscreen_access);
        rgSensitivity = findViewById(R.id.rg_sensitivity);
        cardPermissions = findViewById(R.id.card_goto_permissions);
        cardGuide = findViewById(R.id.card_goto_guide);

        // Shake Master Toggle
        switchShake.setChecked(cache.isShakeEnabled());
        switchShake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            cache.setShakeEnabled(isChecked);
            Toast.makeText(this, isChecked ? "Shake detection enabled" : "Shake detection disabled", Toast.LENGTH_SHORT).show();
        });

        // Sensitivity RadioGroup
        int currentSens = cache.getShakeSensitivity();
        if (currentSens == 0) {
            rgSensitivity.check(R.id.rb_sens_low);
        } else if (currentSens == 2) {
            rgSensitivity.check(R.id.rb_sens_high);
        } else {
            rgSensitivity.check(R.id.rb_sens_medium);
        }

        rgSensitivity.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_sens_low) {
                cache.setShakeSensitivity(0);
            } else if (checkedId == R.id.rb_sens_high) {
                cache.setShakeSensitivity(2);
            } else {
                cache.setShakeSensitivity(1);
            }
            Toast.makeText(this, "Sensitivity updated", Toast.LENGTH_SHORT).show();
        });

        // Lock-Screen Fast Access Switch
        switchLockScreen.setChecked(lockScreenManager.isLockScreenAccessEnabled());
        switchLockScreen.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERMISSION_REQ_CODE);
                        return;
                    }
                }
                lockScreenManager.setLockScreenAccessEnabled(true);
                Toast.makeText(this, "Lock-screen fast access active", Toast.LENGTH_SHORT).show();
            } else {
                lockScreenManager.setLockScreenAccessEnabled(false);
                Toast.makeText(this, "Lock-screen fast access disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // Navigation
        cardPermissions.setOnClickListener(v -> startActivity(new Intent(this, PermissionGuidanceActivity.class)));
        cardGuide.setOnClickListener(v -> startActivity(new Intent(this, AppGuideActivity.class)));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIF_PERMISSION_REQ_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                lockScreenManager.setLockScreenAccessEnabled(true);
                switchLockScreen.setChecked(true);
                Toast.makeText(this, "Lock-screen fast access active", Toast.LENGTH_SHORT).show();
            } else {
                switchLockScreen.setChecked(false);
                Toast.makeText(this, "Notification permission is required for lock-screen access", Toast.LENGTH_LONG).show();
            }
        }
    }
}
