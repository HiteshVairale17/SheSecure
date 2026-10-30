package com.example.shesecure.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.shesecure.dashboard.DashboardActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(this::checkSessionAndRoute, 1000);
    }

    private void checkSessionAndRoute() {
        if (isFinishing()) return;

        SessionManager sessionManager = SessionManager.getInstance(this);
        Intent intent;
        if (sessionManager.isLoggedIn()) {
            intent = new Intent(this, DashboardActivity.class);
        } else {
            intent = new Intent(this, WelcomeActivity.class);
        }
        startActivity(intent);
        finish();
    }
}
