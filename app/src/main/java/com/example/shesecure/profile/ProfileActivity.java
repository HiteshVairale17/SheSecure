package com.example.shesecure.profile;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.shesecure.auth.SessionManager;
import com.example.shesecure.auth.SupabaseAuthService;
import com.example.shesecure.auth.WelcomeActivity;
import com.example.shesecure.data.local.LocalContactCache;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class ProfileActivity extends AppCompatActivity {

    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private TextInputLayout tilName;
    private TextInputLayout tilPhone;
    private TextInputEditText etEmail;
    private TextView tvInitial;
    private MaterialButton btnUpdate;
    private MaterialButton btnLogout;
    private SessionManager sessionManager;
    private SupabaseAuthService authService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        sessionManager = SessionManager.getInstance(this);
        authService = SupabaseAuthService.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tilName = findViewById(R.id.til_profile_name);
        tilPhone = findViewById(R.id.til_profile_phone);
        etName = findViewById(R.id.et_profile_name);
        etPhone = findViewById(R.id.et_profile_phone);
        etEmail = findViewById(R.id.et_profile_email);
        tvInitial = findViewById(R.id.tv_profile_initial);
        btnUpdate = findViewById(R.id.btn_update_profile);
        btnLogout = findViewById(R.id.btn_logout);

        String currentName = sessionManager.getDisplayName();
        String currentEmail = sessionManager.getUserEmail();
        String currentPhone = sessionManager.getUserPhone();

        etName.setText(currentName);
        etEmail.setText(currentEmail);
        populatePhoneField(currentPhone);
        updateInitial(currentName);

        com.example.shesecure.data.remote.SupabaseDataService dataService =
                com.example.shesecure.data.remote.SupabaseDataService.getInstance(this);

        dataService.fetchFullProfile(new com.example.shesecure.data.remote.SupabaseDataService.FullProfileCallback() {
            @Override
            public void onSuccess(String remoteName, String remotePhone) {
                if (isFinishing() || isDestroyed()) return;
                if (remoteName != null && !remoteName.trim().isEmpty() && !remoteName.equals("User")) {
                    sessionManager.updateDisplayName(remoteName);
                    LocalContactCache.getInstance(ProfileActivity.this).setUserName(remoteName);
                    etName.setText(remoteName);
                    updateInitial(remoteName);
                }
                if (remotePhone != null && !remotePhone.trim().isEmpty()) {
                    sessionManager.updatePhoneNumber(remotePhone);
                    LocalContactCache.getInstance(ProfileActivity.this).setUserPhone(remotePhone);
                    populatePhoneField(remotePhone);
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Silently keep local display name and phone
            }
        });

        btnUpdate.setOnClickListener(v -> {
            String newName = etName.getText() != null ? etName.getText().toString().trim() : "";
            String rawPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

            tilName.setError(null);
            tilPhone.setError(null);

            if (TextUtils.isEmpty(newName)) {
                tilName.setError("Name cannot be empty");
                etName.requestFocus();
                return;
            }

            // Indian mobile validation
            String digitsOnly = rawPhone.replaceAll("[^0-9]", "");
            String tenDigitNumber = "";

            if (digitsOnly.length() == 12 && digitsOnly.startsWith("91")) {
                tenDigitNumber = digitsOnly.substring(2);
            } else if (digitsOnly.length() == 11 && digitsOnly.startsWith("0")) {
                tenDigitNumber = digitsOnly.substring(1);
            } else if (digitsOnly.length() == 10) {
                tenDigitNumber = digitsOnly;
            }

            if (TextUtils.isEmpty(tenDigitNumber) || !tenDigitNumber.matches("^[6-9][0-9]{9}$")) {
                tilPhone.setError("Enter a valid 10-digit Indian mobile number (e.g. 9876543210)");
                etPhone.requestFocus();
                return;
            }

            String formattedPhone = "+91" + tenDigitNumber;

            sessionManager.updateDisplayName(newName);
            sessionManager.updatePhoneNumber(formattedPhone);
            LocalContactCache.getInstance(this).setUserName(newName);
            LocalContactCache.getInstance(this).setUserPhone(formattedPhone);
            updateInitial(newName);

            dataService.updateProfile(newName, formattedPhone, new com.example.shesecure.data.remote.SupabaseDataService.SimpleCallback() {
                @Override public void onSuccess() {}
                @Override public void onError(String errorMessage) {}
            });

            Toast.makeText(this, "Profile and mobile number updated", Toast.LENGTH_SHORT).show();
        });

        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Log Out")
                    .setMessage("Are you sure you want to log out of SheSecure? You will need to sign in again to manage contacts.")
                    .setPositiveButton("Log Out", (dialog, which) -> {
                        authService.signOut(new SupabaseAuthService.SimpleCallback() {
                            @Override
                            public void onSuccess() {
                                Toast.makeText(ProfileActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();
                                Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }

                            @Override
                            public void onError(String errorMessage) {
                                // Session is cleared regardless
                                Intent intent = new Intent(ProfileActivity.this, WelcomeActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void updateInitial(String name) {
        if (name != null && !name.trim().isEmpty()) {
            tvInitial.setText(name.trim().substring(0, 1).toUpperCase());
        } else {
            tvInitial.setText("U");
        }
    }

    private void populatePhoneField(String phone) {
        if (etPhone == null || phone == null) return;
        String clean = phone.trim();
        if (clean.startsWith("+91")) {
            clean = clean.substring(3).trim();
        }
        etPhone.setText(clean);
    }
}
