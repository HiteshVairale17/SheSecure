package com.example.shesecure.auth;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.R;
import com.example.shesecure.data.remote.SupabaseConfig;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

public class SupabaseServerConfigDialog {

    public interface OnConfigChangeListener {
        void onConfigChanged();
    }

    public static void show(Activity activity, OnConfigChangeListener listener) {
        if (activity == null || activity.isFinishing()) return;

        LayoutInflater inflater = LayoutInflater.from(activity);
        View dialogView = inflater.inflate(R.layout.dialog_supabase_config, null);

        TextView tvStatusTitle = dialogView.findViewById(R.id.tv_status_title);
        ProgressBar pbLoading = dialogView.findViewById(R.id.pb_status_loading);
        TextView tvStatusDetails = dialogView.findViewById(R.id.tv_status_details);
        TextView tvDashboardLink = dialogView.findViewById(R.id.tv_dashboard_link);
        MaterialButton btnTestLive = dialogView.findViewById(R.id.btn_test_live_status);

        TextInputEditText etUrl = dialogView.findViewById(R.id.et_config_url);
        TextInputEditText etAnonKey = dialogView.findViewById(R.id.et_config_anon_key);
        MaterialButton btnSave = dialogView.findViewById(R.id.btn_save_config);
        MaterialButton btnReset = dialogView.findViewById(R.id.btn_reset_defaults);
        MaterialButton btnClose = dialogView.findViewById(R.id.btn_close_dialog);

        SupabaseConfig config = SupabaseConfig.getInstance(activity);
        SupabaseAuthService authService = SupabaseAuthService.getInstance(activity);

        String currentUrl = config.getUrl();
        String currentKey = config.getAnonKey();

        etUrl.setText(currentUrl);
        etAnonKey.setText(currentKey);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        Runnable runTest = () -> {
            String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            String key = etAnonKey.getText() != null ? etAnonKey.getText().toString().trim() : "";

            pbLoading.setVisibility(View.VISIBLE);
            tvStatusTitle.setText("Testing connection...");
            tvStatusTitle.setTextColor(activity.getResources().getColor(R.color.primary_navy));
            tvStatusDetails.setText("Contacting /auth/v1/settings...");

            authService.checkServerSettings(url, key, (reachable, emailEnabled, signupsAllowed, autoconfirmEnabled, projectRef, message) -> {
                pbLoading.setVisibility(View.GONE);
                String displayRef = projectRef.isEmpty() ? "Unknown" : projectRef;
                tvDashboardLink.setText("Dashboard URL: https://supabase.com/dashboard/project/" + displayRef + "/auth/providers");

                if (reachable) {
                    if (emailEnabled) {
                        tvStatusTitle.setText("✅ Connected & Email Provider ENABLED");
                        tvStatusTitle.setTextColor(activity.getResources().getColor(R.color.status_active_green));
                        StringBuilder sb = new StringBuilder();
                        sb.append("Project Ref: ").append(displayRef).append("\n");
                        sb.append("• Email Provider: ENABLED\n");
                        sb.append("• New Signups: ").append(signupsAllowed ? "ALLOWED" : "BLOCKED in dashboard").append("\n");
                        sb.append("• Confirm Email: ").append(autoconfirmEnabled ? "DISABLED (instant login)" : "ENABLED (requires email confirmation link)");
                        tvStatusDetails.setText(sb.toString());
                    } else {
                        tvStatusTitle.setText("⚠️ Server Connected, BUT Email Provider is DISABLED!");
                        tvStatusTitle.setTextColor(activity.getResources().getColor(R.color.emergency_red));
                        StringBuilder sb = new StringBuilder();
                        sb.append("Project Ref: ").append(displayRef).append("\n\n");
                        sb.append("The Supabase server reports that Email authentication is OFF.\n");
                        sb.append("Fix in Supabase Dashboard:\n");
                        sb.append("1. Go to Authentication -> Providers -> Email\n");
                        sb.append("2. Toggle 'Enable Email provider' to ON\n");
                        sb.append("3. Scroll to the bottom and click the 'Save' button\n");
                        sb.append("4. Tap 'Re-check Server Live Status' above to verify.");
                        tvStatusDetails.setText(sb.toString());
                    }
                } else {
                    tvStatusTitle.setText("❌ Connection Failed");
                    tvStatusTitle.setTextColor(activity.getResources().getColor(R.color.emergency_red));
                    tvStatusDetails.setText(message + "\n\nPlease check your Project URL and Anon Key.");
                }
            });
        };

        btnTestLive.setOnClickListener(v -> runTest.run());

        btnSave.setOnClickListener(v -> {
            String newUrl = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            String newKey = etAnonKey.getText() != null ? etAnonKey.getText().toString().trim() : "";

            if (newUrl.isEmpty() || !newUrl.startsWith("https://")) {
                etUrl.setError("Enter a valid https:// URL");
                etUrl.requestFocus();
                return;
            }
            if (newKey.isEmpty() || newKey.length() < 10) {
                etAnonKey.setError("Enter a valid Supabase anon key");
                etAnonKey.requestFocus();
                return;
            }

            config.setCustomCredentials(newUrl, newKey);
            authService.reloadConfiguration();
            Toast.makeText(activity, "Supabase credentials saved!", Toast.LENGTH_SHORT).show();
            runTest.run();
            if (listener != null) listener.onConfigChanged();
        });

        btnReset.setOnClickListener(v -> {
            config.clearCustomCredentials();
            authService.reloadConfiguration();
            etUrl.setText(config.getUrl());
            etAnonKey.setText(config.getAnonKey());
            Toast.makeText(activity, "Reset to build defaults.", Toast.LENGTH_SHORT).show();
            runTest.run();
            if (listener != null) listener.onConfigChanged();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        // Run initial check
        runTest.run();
    }
}
