package com.example.shesecure.emergency;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.R;
import com.example.shesecure.dashboard.DashboardActivity;

public class LockScreenAccessManager {

    private static final String PREF_NAME = "shesecure_lockscreen_pref";
    private static final String KEY_LOCK_SCREEN_ENABLED = "lock_screen_access_enabled";

    public static final String CHANNEL_ID = "shesecure_fast_access_channel";
    public static final int NOTIFICATION_ID = 9110;

    private static LockScreenAccessManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private LockScreenAccessManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        createNotificationChannel();
    }

    public static synchronized LockScreenAccessManager getInstance(Context context) {
        if (instance == null) {
            instance = new LockScreenAccessManager(context);
        }
        return instance;
    }

    public boolean isLockScreenAccessEnabled() {
        return prefs.getBoolean(KEY_LOCK_SCREEN_ENABLED, false);
    }

    public void setLockScreenAccessEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCK_SCREEN_ENABLED, enabled).apply();
        if (enabled) {
            showLockScreenNotification();
        } else {
            dismissLockScreenNotification();
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.lockscreen_notification_channel_name),
                        NotificationManager.IMPORTANCE_HIGH
                );
                channel.setDescription(context.getString(R.string.lockscreen_notification_channel_desc));
                channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);
                channel.setShowBadge(false);
                channel.enableVibration(true);
                nm.createNotificationChannel(channel);
            }
        }
    }

    public void showLockScreenNotification() {
        // Android 13+ permission check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        // Intent for main notification body tap -> opens Dashboard
        Intent appIntent = new Intent(context, DashboardActivity.class);
        appIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentPendingIntent = PendingIntent.getActivity(
                context,
                0,
                appIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Intent for "TRIGGER SOS" action button -> opens SosCountdownActivity over lock screen
        Intent sosIntent = new Intent(context, SosCountdownActivity.class);
        sosIntent.putExtra(SosCountdownActivity.EXTRA_TRIGGER_SOURCE, SosCountdownActivity.SOURCE_LOCK_SCREEN);
        sosIntent.putExtra(SosCountdownActivity.EXTRA_IS_TEST_MODE, false);
        sosIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent sosPendingIntent = PendingIntent.getActivity(
                context,
                1,
                sosIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield_alert)
                .setContentTitle(context.getString(R.string.lockscreen_notification_title))
                .setContentText(context.getString(R.string.lockscreen_notification_text))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC) // Visible on lock screen
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(contentPendingIntent)
                .addAction(
                        R.drawable.ic_shield_alert,
                        context.getString(R.string.lockscreen_action_trigger_sos),
                        sosPendingIntent
                );

        nm.notify(NOTIFICATION_ID, builder.build());
    }

    public void dismissLockScreenNotification() {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.cancel(NOTIFICATION_ID);
        }
    }

    public void syncState() {
        if (isLockScreenAccessEnabled()) {
            showLockScreenNotification();
        } else {
            dismissLockScreenNotification();
        }
    }
}
