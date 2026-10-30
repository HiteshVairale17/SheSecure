package com.example.shesecure.data.remote;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.BuildConfig;

public class SupabaseConfig {
    private static final String PREF_NAME = "supabase_runtime_config";
    private static final String KEY_CUSTOM_URL = "custom_supabase_url";
    private static final String KEY_CUSTOM_ANON_KEY = "custom_supabase_anon_key";

    private static SupabaseConfig instance;
    private final SharedPreferences prefs;

    private SupabaseConfig(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized SupabaseConfig getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseConfig(context);
        }
        return instance;
    }

    public String getUrl() {
        String custom = prefs.getString(KEY_CUSTOM_URL, null);
        if (custom != null && !custom.trim().isEmpty()) {
            return cleanUrl(custom.trim());
        }
        try {
            String buildVal = BuildConfig.SUPABASE_URL;
            if (buildVal != null && !buildVal.trim().isEmpty()) {
                return cleanUrl(buildVal.trim());
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String getAnonKey() {
        String custom = prefs.getString(KEY_CUSTOM_ANON_KEY, null);
        if (custom != null && !custom.trim().isEmpty()) {
            return custom.trim();
        }
        try {
            String buildVal = BuildConfig.SUPABASE_ANON_KEY;
            if (buildVal != null && !buildVal.trim().isEmpty()) {
                return buildVal.trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public void setCustomCredentials(String url, String anonKey) {
        prefs.edit()
                .putString(KEY_CUSTOM_URL, url != null ? cleanUrl(url.trim()) : "")
                .putString(KEY_CUSTOM_ANON_KEY, anonKey != null ? anonKey.trim() : "")
                .apply();
    }

    public void clearCustomCredentials() {
        prefs.edit().clear().apply();
    }

    public boolean isCustomConfigured() {
        String custom = prefs.getString(KEY_CUSTOM_URL, null);
        return custom != null && !custom.trim().isEmpty();
    }

    public String getDefaultUrl() {
        try {
            String buildVal = BuildConfig.SUPABASE_URL;
            if (buildVal != null && !buildVal.trim().isEmpty()) {
                return cleanUrl(buildVal.trim());
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String getDefaultAnonKey() {
        try {
            String buildVal = BuildConfig.SUPABASE_ANON_KEY;
            if (buildVal != null && !buildVal.trim().isEmpty()) {
                return buildVal.trim();
            }
        } catch (Exception ignored) {}
        return "";
    }

    public String getProjectRef() {
        String u = getUrl();
        if (u != null && u.startsWith("https://") && u.contains(".supabase.co")) {
            try {
                return u.substring("https://".length(), u.indexOf(".supabase.co"));
            } catch (Exception ignored) {}
        }
        return "";
    }

    public boolean isConfigured() {
        String u = getUrl();
        String k = getAnonKey();
        return !u.isEmpty() && !u.contains("your-project-id") &&
               !k.isEmpty() && !k.contains("your-supabase-anon-key");
    }

    private String cleanUrl(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/rest/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/rest/v1".length());
        } else if (trimmed.endsWith("/auth/v1")) {
            trimmed = trimmed.substring(0, trimmed.length() - "/auth/v1".length());
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
