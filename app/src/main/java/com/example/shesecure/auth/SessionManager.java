package com.example.shesecure.auth;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.shesecure.widget.SheSecureWidgetProvider;

public class SessionManager {
    private static final String PREF_NAME = "shesecure_session_pref";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_EXPIRES_AT = "expires_at";

    private static SessionManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    private SessionManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SessionManager getInstance(Context context) {
        if (instance == null) {
            instance = new SessionManager(context);
        }
        return instance;
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String email, String displayName, long expiresInSeconds) {
        saveSession(accessToken, refreshToken, userId, email, displayName, "", expiresInSeconds);
    }

    public void saveSession(String accessToken, String refreshToken, String userId, String email, String displayName, String phoneNumber, long expiresInSeconds) {
        long expiresAt = System.currentTimeMillis() + (expiresInSeconds * 1000);
        prefs.edit()
                .putString(KEY_ACCESS_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_USER_EMAIL, email)
                .putString(KEY_USER_PHONE, phoneNumber != null ? phoneNumber : "")
                .putString(KEY_DISPLAY_NAME, displayName != null ? displayName : "User")
                .putLong(KEY_EXPIRES_AT, expiresAt)
                .commit();

        if (context != null) {
            SheSecureWidgetProvider.updateAllWidgets(context);
        }
    }

    public void updateDisplayName(String displayName) {
        prefs.edit().putString(KEY_DISPLAY_NAME, displayName).apply();
    }

    public void updatePhoneNumber(String phoneNumber) {
        prefs.edit().putString(KEY_USER_PHONE, phoneNumber != null ? phoneNumber : "").apply();
    }

    public String getUserPhone() {
        return prefs.getString(KEY_USER_PHONE, "");
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        return token != null && !token.trim().isEmpty();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getRefreshToken() {
        return prefs.getString(KEY_REFRESH_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "");
    }

    public String getDisplayName() {
        return prefs.getString(KEY_DISPLAY_NAME, "User");
    }

    public long getExpiresAt() {
        return prefs.getLong(KEY_EXPIRES_AT, 0);
    }

    public void clearSession() {
        prefs.edit().clear().commit();
        if (context != null) {
            SheSecureWidgetProvider.updateAllWidgets(context);
        }
    }
}
