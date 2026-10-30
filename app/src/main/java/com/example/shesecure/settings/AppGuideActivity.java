package com.example.shesecure.settings;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.shesecure.widget.SheSecureWidgetProvider;
import com.google.android.material.button.MaterialButton;

public class AppGuideActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_guide);

        ImageView btnBack = findViewById(R.id.btn_back);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }

        MaterialButton btnPinWidget = findViewById(R.id.btn_pin_widget);
        if (btnPinWidget != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                AppWidgetManager appWidgetManager = getSystemService(AppWidgetManager.class);
                if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported()) {
                    btnPinWidget.setVisibility(View.VISIBLE);
                    btnPinWidget.setOnClickListener(v -> {
                        ComponentName provider = new ComponentName(this, SheSecureWidgetProvider.class);
                        boolean success = appWidgetManager.requestPinAppWidget(provider, null, null);
                        if (success) {
                            Toast.makeText(this, "Prompting launcher to pin widget...", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Could not pin widget automatically. Please use manual drag-and-drop.", Toast.LENGTH_LONG).show();
                        }
                    });
                } else {
                    btnPinWidget.setOnClickListener(v ->
                            Toast.makeText(this, "Automatic pinning not supported on this launcher. Please long-press your home screen and select Widgets.", Toast.LENGTH_LONG).show()
                    );
                }
            } else {
                btnPinWidget.setOnClickListener(v ->
                        Toast.makeText(this, "Please long-press your home screen and select Widgets to add SheSecure.", Toast.LENGTH_LONG).show()
                );
            }
        }
    }
}
