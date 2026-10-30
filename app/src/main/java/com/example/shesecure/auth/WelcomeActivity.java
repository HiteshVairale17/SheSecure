package com.example.shesecure.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.shesecure.dashboard.DashboardActivity;
import com.google.android.material.button.MaterialButton;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Guard: If user is already logged in, go straight to Dashboard
        if (SessionManager.getInstance(this).isLoggedIn()) {
            startActivity(new Intent(this, DashboardActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_welcome);

        MaterialButton btnGetStarted = findViewById(R.id.btn_get_started);
        MaterialButton btnLogin = findViewById(R.id.btn_login);

        btnGetStarted.setOnClickListener(v -> {
            Intent intent = new Intent(this, SignupActivity.class);
            startActivity(intent);
        });

        btnLogin.setOnClickListener(v -> {
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        });

        android.widget.ImageView ivLogo = findViewById(R.id.iv_welcome_logo);
        if (ivLogo != null) {
            final int[] tapCount = {0};
            ivLogo.setOnClickListener(v -> {
                tapCount[0]++;
                if (tapCount[0] >= 5) {
                    tapCount[0] = 0;
                    android.widget.Toast.makeText(this, "Developer Options: Supabase Server Diagnostics", android.widget.Toast.LENGTH_SHORT).show();
                    SupabaseServerConfigDialog.show(this, null);
                }
            });
        }
    }
}
