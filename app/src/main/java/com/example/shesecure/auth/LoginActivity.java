package com.example.shesecure.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
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

public class LoginActivity extends AppCompatActivity {

    private TextInputLayout tilEmail;
    private TextInputLayout tilPassword;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private MaterialButton btnSubmit;
    private TextView tvForgot;
    private TextView tvGotoSignup;

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

        setContentView(R.layout.activity_login);
        authService = SupabaseAuthService.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tilEmail = findViewById(R.id.til_email);
        tilPassword = findViewById(R.id.til_password);
        etEmail = findViewById(R.id.et_email);
        etPassword = findViewById(R.id.et_password);
        btnSubmit = findViewById(R.id.btn_login_submit);
        tvForgot = findViewById(R.id.tv_forgot_password);
        tvGotoSignup = findViewById(R.id.tv_goto_signup);

        tvGotoSignup.setOnClickListener(v -> {
            startActivity(new Intent(this, SignupActivity.class));
            finish();
        });

        tvForgot.setOnClickListener(v -> showForgotPasswordDialog());

        btnSubmit.setOnClickListener(v -> attemptLogin());
    }

    private void attemptLogin() {
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String password = etPassword.getText() != null ? etPassword.getText().toString().trim() : "";

        tilEmail.setError(null);
        tilPassword.setError(null);

        if (TextUtils.isEmpty(email) || !email.contains("@")) {
            tilEmail.setError("Enter a valid email address");
            etEmail.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            tilPassword.setError("Password must be at least 6 characters");
            etPassword.requestFocus();
            return;
        }

        setLoading(true);

        authService.signIn(email, password, new SupabaseAuthService.AuthCallback() {
            @Override
            public void onSuccess(String userId, String userEmail, String displayName, boolean emailConfirmationRequired) {
                setLoading(false);
                LocalContactCache cache = LocalContactCache.getInstance(LoginActivity.this);
                cache.setUserEmail(userEmail);
                if (displayName != null && !displayName.isEmpty()) {
                    cache.setUserName(displayName);
                }
                String phone = sessionManager.getUserPhone();
                if (phone != null && !phone.isEmpty()) {
                    cache.setUserPhone(phone);
                }

                // Asynchronously synchronize profile and emergency contacts from Supabase
                com.example.shesecure.data.remote.SupabaseDataService dataService =
                        com.example.shesecure.data.remote.SupabaseDataService.getInstance(LoginActivity.this);

                dataService.fetchFullProfile(new com.example.shesecure.data.remote.SupabaseDataService.FullProfileCallback() {
                    @Override
                    public void onSuccess(String remoteName, String remotePhone) {
                        if (remoteName != null && !remoteName.trim().isEmpty() && !remoteName.equals("User")) {
                            sessionManager.updateDisplayName(remoteName);
                            cache.setUserName(remoteName);
                        }
                        if (remotePhone != null && !remotePhone.trim().isEmpty()) {
                            sessionManager.updatePhoneNumber(remotePhone);
                            cache.setUserPhone(remotePhone);
                        } else {
                            String localPhone = sessionManager.getUserPhone();
                            if (localPhone != null && !localPhone.trim().isEmpty()) {
                                dataService.updateProfile(sessionManager.getDisplayName(), localPhone, new com.example.shesecure.data.remote.SupabaseDataService.SimpleCallback() {
                                    @Override public void onSuccess() {}
                                    @Override public void onError(String errorMessage) {}
                                });
                            }
                        }
                    }

                    @Override
                    public void onError(String errorMessage) {}
                });

                dataService.fetchContacts(new com.example.shesecure.data.remote.SupabaseDataService.ContactsCallback() {
                    @Override
                    public void onSuccess(java.util.List<com.example.shesecure.models.EmergencyContact> contacts) {
                        cache.saveContacts(contacts);
                    }

                    @Override
                    public void onError(String errorMessage) {}
                });

                Toast.makeText(LoginActivity.this, "Welcome back, " + displayName + "!", Toast.LENGTH_SHORT).show();

                Intent intent = new Intent(LoginActivity.this, DashboardActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }

            @Override
            public void onError(String errorMessage) {
                setLoading(false);
                String title = errorMessage != null && errorMessage.contains("disabled in your Supabase")
                        ? "Supabase Setup Required"
                        : "Sign In Failed";
                showErrorDialog(title, errorMessage);
            }

        });
    }

    private void showForgotPasswordDialog() {
        final EditText input = new EditText(this);
        input.setHint("name@example.com");
        String currentEmail = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        if (!currentEmail.isEmpty()) {
            input.setText(currentEmail);
        }

        new AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setMessage("Enter your registered email address to receive a password reset link.")
                .setView(input)
                .setPositiveButton("Send Reset Link", (dialog, which) -> {
                    String email = input.getText().toString().trim();
                    if (TextUtils.isEmpty(email) || !email.contains("@")) {
                        Toast.makeText(this, "Please enter a valid email address.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    authService.resetPassword(email, new SupabaseAuthService.SimpleCallback() {
                        @Override
                        public void onSuccess() {
                            new AlertDialog.Builder(LoginActivity.this)
                                    .setTitle("Check Your Email")
                                    .setMessage("A password recovery link has been sent to " + email + ". Follow the instructions in the email to reset your password.")
                                    .setPositiveButton("OK", null)
                                    .show();
                        }

                        @Override
                        public void onError(String errorMessage) {
                            showErrorDialog("Password Reset Failed", errorMessage);
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setLoading(boolean loading) {
        btnSubmit.setEnabled(!loading);
        btnSubmit.setText(loading ? "Signing in..." : getString(R.string.btn_login));
        etEmail.setEnabled(!loading);
        etPassword.setEnabled(!loading);
    }

    private void showErrorDialog(String title, String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("OK", null);

        if (message != null && (message.contains("disabled") || message.contains("Supabase") || message.contains("Provider"))) {
            builder.setNeutralButton("Server Diagnostics", (dialog, which) -> {
                SupabaseServerConfigDialog.show(this, null);
            });
        }

        builder.show();
    }
}
