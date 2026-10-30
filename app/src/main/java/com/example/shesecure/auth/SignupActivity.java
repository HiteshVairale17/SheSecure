package com.example.shesecure.auth;

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
import com.example.shesecure.dashboard.DashboardActivity;
import com.example.shesecure.data.local.LocalContactCache;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class SignupActivity extends AppCompatActivity {

    private TextInputLayout tilName;
    private TextInputLayout tilEmail;
    private TextInputLayout tilPhone;
    private TextInputLayout tilPassword;
    private TextInputLayout tilConfirm;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirm;
    private MaterialButton btnSubmit;
    private TextView tvGotoLogin;

    private SupabaseAuthService authService;
    private SessionManager sessionManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sessionManager = SessionManager.getInstance(this);
        if (sessionManager.isLoggedIn()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_signup);
        authService = SupabaseAuthService.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tilName = findViewById(R.id.til_display_name);
        tilEmail = findViewById(R.id.til_email);
        tilPhone = findViewById(R.id.til_user_phone);
        tilPassword = findViewById(R.id.til_password);
        tilConfirm = findViewById(R.id.til_confirm_password);

        etName = findViewById(R.id.et_display_name);
        etEmail = findViewById(R.id.et_email);
        etPhone = findViewById(R.id.et_user_phone);
        etPassword = findViewById(R.id.et_password);
        etConfirm = findViewById(R.id.et_confirm_password);
        btnSubmit = findViewById(R.id.btn_signup_submit);
        tvGotoLogin = findViewById(R.id.tv_goto_login);

        tvGotoLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        });

        btnSubmit.setOnClickListener(v -> attemptSignup());
    }

    private void attemptSignup() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String rawPhone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";
        String confirm = etConfirm.getText() != null ? etConfirm.getText().toString().trim() : "";

        tilName.setError(null);
        tilEmail.setError(null);
        tilPhone.setError(null);
        tilPassword.setError(null);
        tilConfirm.setError(null);

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Full name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            tilEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        // Indian mobile number validation (10 digits starting with 6, 7, 8, or 9)
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

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        if (!password.equals(confirm)) {
            tilConfirm.setError("Passwords do not match");
            etConfirm.requestFocus();
            return;
        }

        setLoading(true);

        authService.signUp(email, password, name, formattedPhone, new SupabaseAuthService.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail, String displayName, boolean emailConfirmationRequired) {
                setLoading(false);
                LocalContactCache.getInstance(SignupActivity.this).setUserName(displayName);
                LocalContactCache.getInstance(SignupActivity.this).setUserEmail(userEmail);
                LocalContactCache.getInstance(SignupActivity.this).setUserPhone(formattedPhone);

                // Synchronize profile details into public.profiles
                com.example.shesecure.data.remote.SupabaseDataService.getInstance(SignupActivity.this)
                        .updateProfile(displayName, formattedPhone, new com.example.shesecure.data.remote.SupabaseDataService.SimpleCallback() {
                            @Override public void onSuccess() {}
                            @Override public void onError(String errorMessage) {}
                        });

                if (emailConfirmationRequired) {
                    new AlertDialog.Builder(SignupActivity.this)
                            .setTitle("Verify Your Email")
                            .setMessage("Account created! A confirmation email has been sent to " + userEmail + ". Please verify your email before logging in.")
                            .setPositiveButton("Go to Login", (dialog, which) -> {
                                Intent intent = new Intent(SignupActivity.this, LoginActivity.class);
                                intent.putExtra("prefill_email", userEmail);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                } else {
                    Toast.makeText(SignupActivity.this, "Welcome to SheSecure, " + displayName + "!", Toast.LENGTH_SHORT).show();
                    Intent intent = new Intent(SignupActivity.this, DashboardActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                }
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                String title = errorMessage != null && (errorMessage.contains("disabled") || errorMessage.contains("Supabase"))
                        ? "Supabase Setup Required"
                        : "Registration Failed";

                AlertDialog.Builder builder = new AlertDialog.Builder(SignupActivity.this)
                        .setTitle(title)
                        .setMessage(errorMessage)
                        .setPositiveButton("OK", null);

                if (errorMessage != null && (errorMessage.contains("disabled") || errorMessage.contains("Supabase") || errorMessage.contains("Provider"))) {
                    builder.setNeutralButton("Server Diagnostics", (dialog, which) -> {
                        SupabaseServerConfigDialog.show(SignupActivity.this, null);
                    });
                }

                builder.show();
            }

        });
    }

    private void setLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);
        btnSubmit.setText(loading ? "Creating Account..." : getString(R.string.btn_signup));
        etName.setEnabled(!loading);
        etEmail.setEnabled(!loading);
        etPhone.setEnabled(!loading);
        etPassword.setEnabled(!loading);
        etConfirm.setEnabled(!loading);
    }
}
