package com.example.shesecure.auth;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.BuildConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseAuthService {

    public interface AuthCallback {
        void onSuccess(String userId, String email, String displayName, boolean emailConfirmationRequired);
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static SupabaseAuthService instance;
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final SessionManager sessionManager;
    private final Handler mainHandler;

    private String supabaseUrl;
    private String supabaseAnonKey;

    private SupabaseAuthService(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = SessionManager.getInstance(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        loadConfiguration();
    }

    public static synchronized SupabaseAuthService getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseAuthService(context);
        }
        return instance;
    }

    private void loadConfiguration() {
        com.example.shesecure.data.remote.SupabaseConfig cfg = com.example.shesecure.data.remote.SupabaseConfig.getInstance(context);
        this.supabaseUrl = cfg.getUrl();
        this.supabaseAnonKey = cfg.getAnonKey();
    }

    public interface ServerStatusCallback {
        void onResult(boolean reachable, boolean emailEnabled, boolean signupsAllowed, boolean autoconfirmEnabled, String projectRef, String message);
    }

    public void reloadConfiguration() {
        loadConfiguration();
    }

    public void checkServerSettings(String optionalUrl, String optionalKey, ServerStatusCallback callback) {
        String testUrl = (optionalUrl != null && !optionalUrl.trim().isEmpty())
                ? optionalUrl.trim()
                : supabaseUrl;
        String testKey = (optionalKey != null && !optionalKey.trim().isEmpty())
                ? optionalKey.trim()
                : supabaseAnonKey;

        while (testUrl.endsWith("/")) {
            testUrl = testUrl.substring(0, testUrl.length() - 1);
        }
        if (testUrl.endsWith("/rest/v1")) {
            testUrl = testUrl.substring(0, testUrl.length() - "/rest/v1".length());
        } else if (testUrl.endsWith("/auth/v1")) {
            testUrl = testUrl.substring(0, testUrl.length() - "/auth/v1".length());
        }

        String projectRef = "";
        if (testUrl.startsWith("https://") && testUrl.contains(".supabase.co")) {
            try {
                projectRef = testUrl.substring("https://".length(), testUrl.indexOf(".supabase.co"));
            } catch (Exception ignored) {}
        }

        final String finalRef = projectRef;
        final String finalUrl = testUrl;
        if (testUrl.isEmpty() || testKey.isEmpty() || testUrl.contains("your-project-id")) {
            mainHandler.post(() -> callback.onResult(false, false, false, false, finalRef, "Supabase credentials are missing or placeholder."));
            return;
        }

        Request request = new Request.Builder()
                .url(testUrl + "/auth/v1/settings")
                .addHeader("apikey", testKey)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onResult(false, false, false, false, finalRef, "Cannot connect to " + finalUrl + ": " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                        boolean emailEnabled = false;
                        if (json.has("external")) {
                            JsonObject ext = json.getAsJsonObject("external");
                            if (ext.has("email")) {
                                emailEnabled = ext.get("email").getAsBoolean();
                            }
                        }
                        boolean disableSignup = json.has("disable_signup") && json.get("disable_signup").getAsBoolean();
                        boolean mailerAutoconfirm = json.has("mailer_autoconfirm") && json.get("mailer_autoconfirm").getAsBoolean();

                        boolean finalEmail = emailEnabled;
                        mainHandler.post(() -> callback.onResult(true, finalEmail, !disableSignup, mailerAutoconfirm, finalRef, "Server reachable and authenticated."));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onResult(false, false, false, false, finalRef, "Failed to parse server response: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onResult(false, false, false, false, finalRef, "Server returned HTTP " + response.code() + ": " + respStr));
                }
            }
        });
    }

    public void checkCurrentServerSettings(ServerStatusCallback callback) {
        loadConfiguration();
        checkServerSettings(supabaseUrl, supabaseAnonKey, callback);
    }

    public boolean isConfigured() {
        loadConfiguration();
        return com.example.shesecure.data.remote.SupabaseConfig.getInstance(context).isConfigured();
    }

    public String getConfigurationStatusMessage() {
        if (!isConfigured()) {
            return "Supabase credentials not configured. Please add your real SUPABASE_URL and SUPABASE_ANON_KEY in .env or the Secrets panel.";
        }
        return "Configured (" + supabaseUrl + ")";
    }

    public void signUp(String email, String password, String displayName, AuthCallback callback) {
        signUp(email, password, displayName, "", callback);
    }

    public void signUp(String email, String password, String displayName, String phoneNumber, AuthCallback callback) {
        if (!isConfigured()) {
            notifyError(callback, getConfigurationStatusMessage());
            return;
        }

        JsonObject userMeta = new JsonObject();
        userMeta.addProperty("display_name", displayName);
        if (phoneNumber != null && !phoneNumber.trim().isEmpty()) {
            userMeta.addProperty("phone", phoneNumber.trim());
        }

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);
        body.add("data", userMeta);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/signup")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError(callback, "Network error: Unable to reach Supabase. " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                        String accessToken = json.has("access_token") ? json.get("access_token").getAsString() : null;
                        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : null;
                        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;

                        JsonObject userObj = json.has("user") ? json.getAsJsonObject("user") : json;
                        String userId = userObj.has("id") ? userObj.get("id").getAsString() : "";
                        String userEmail = userObj.has("email") ? userObj.get("email").getAsString() : email;

                        String resolvedName = displayName;
                        String resolvedPhone = phoneNumber != null ? phoneNumber : "";
                        if (userObj.has("user_metadata")) {
                            JsonObject meta = userObj.getAsJsonObject("user_metadata");
                            if (meta.has("display_name")) {
                                resolvedName = meta.get("display_name").getAsString();
                            }
                            if (meta.has("phone")) {
                                resolvedPhone = meta.get("phone").getAsString();
                            }
                        }

                        if (accessToken != null && !accessToken.isEmpty()) {
                            sessionManager.saveSession(accessToken, refreshToken, userId, userEmail, resolvedName, resolvedPhone, expiresIn);
                            notifySuccess(callback, userId, userEmail, resolvedName, false);
                        } else {
                            // Email confirmation required
                            sessionManager.updatePhoneNumber(resolvedPhone);
                            notifySuccess(callback, userId, userEmail, resolvedName, true);
                        }
                    } catch (Exception e) {
                        notifyError(callback, "Error parsing signup response: " + e.getMessage());
                    }
                } else {
                    String errorMsg = parseErrorMessage(respStr, "Signup failed (HTTP " + response.code() + ")");
                    notifyError(callback, errorMsg);
                }
            }
        });
    }

    public void signIn(String email, String password, AuthCallback callback) {
        if (!isConfigured()) {
            notifyError(callback, getConfigurationStatusMessage());
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("email", email);
        body.addProperty("password", password);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/token?grant_type=password")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifyError(callback, "Network connection error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonObject json = JsonParser.parseString(respStr).getAsJsonObject();
                        String accessToken = json.get("access_token").getAsString();
                        String refreshToken = json.has("refresh_token") ? json.get("refresh_token").getAsString() : "";
                        long expiresIn = json.has("expires_in") ? json.get("expires_in").getAsLong() : 3600;

                        JsonObject userObj = json.getAsJsonObject("user");
                        String userId = userObj.get("id").getAsString();
                        String userEmail = userObj.get("email").getAsString();

                        String resolvedName = "User";
                        String resolvedPhone = "";
                        if (userObj.has("user_metadata")) {
                            JsonObject meta = userObj.getAsJsonObject("user_metadata");
                            if (meta.has("display_name")) {
                                resolvedName = meta.get("display_name").getAsString();
                            }
                            if (meta.has("phone")) {
                                resolvedPhone = meta.get("phone").getAsString();
                            }
                        }

                        sessionManager.saveSession(accessToken, refreshToken, userId, userEmail, resolvedName, resolvedPhone, expiresIn);
                        notifySuccess(callback, userId, userEmail, resolvedName, false);
                    } catch (Exception e) {
                        notifyError(callback, "Failed to parse authentication response: " + e.getMessage());
                    }
                } else {
                    String errorMsg = parseErrorMessage(respStr, "Authentication failed (HTTP " + response.code() + ")");
                    notifyError(callback, errorMsg);
                }
            }
        });
    }

    public void resetPassword(String email, SimpleCallback callback) {
        if (!isConfigured()) {
            notifySimpleError(callback, getConfigurationStatusMessage());
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("email", email);

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/recover")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                notifySimpleError(callback, "Network error: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String respStr = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    String errorMsg = parseErrorMessage(respStr, "Password reset request failed.");
                    notifySimpleError(callback, errorMsg);
                }
            }
        });
    }

    public void signOut(SimpleCallback callback) {
        String token = sessionManager.getAccessToken();
        sessionManager.clearSession();

        if (!isConfigured() || token == null) {
            mainHandler.post(callback::onSuccess);
            return;
        }

        Request request = new Request.Builder()
                .url(supabaseUrl + "/auth/v1/logout")
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .post(RequestBody.create("{}", JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // Local session is already cleared
                mainHandler.post(callback::onSuccess);
            }

            @Override
            public void onResponse(Call call, Response response) {
                mainHandler.post(callback::onSuccess);
            }
        });
    }

    private String parseErrorMessage(String responseBody, String defaultMsg) {
        String rawMsg = "";
        try {
            JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
            if (json.has("msg")) {
                rawMsg = json.get("msg").getAsString();
            } else if (json.has("error_description")) {
                rawMsg = json.get("error_description").getAsString();
            } else if (json.has("message")) {
                rawMsg = json.get("message").getAsString();
            } else if (json.has("error")) {
                rawMsg = json.get("error").getAsString();
            }
        } catch (Exception ignored) {}

        if (rawMsg == null || rawMsg.trim().isEmpty()) {
            return defaultMsg;
        }

        String lower = rawMsg.toLowerCase(Locale.ROOT);
        String projectRef = com.example.shesecure.data.remote.SupabaseConfig.getInstance(context).getProjectRef();
        String displayRef = projectRef.isEmpty() ? supabaseUrl : projectRef;
        String dashboardUrl = "https://supabase.com/dashboard/project/" + (projectRef.isEmpty() ? "default" : projectRef) + "/auth/providers";

        if (lower.contains("email signups are disabled") || lower.contains("signup is disabled") || lower.contains("email_provider_disabled")) {
            return "Email signups are currently disabled on the connected Supabase server.\n\n" +
                    "Active Project ID: " + displayRef + "\n\n" +
                    "Why this happens:\n" +
                    "1. Unsaved Changes: In Supabase Dashboard, you may have toggled the Email switch, but did NOT click the blue/green 'Save' button at the bottom of the Email section.\n" +
                    "2. Different Project: You may have configured a different project in your browser than '" + displayRef + "'.\n\n" +
                    "How to fix:\n" +
                    "• Open: " + dashboardUrl + "\n" +
                    "• Under 'Email', ensure 'Enable Email provider' is ON\n" +
                    "• Ensure 'Allow new users to sign up' is ON\n" +
                    "• Scroll down and click SAVE\n" +
                    "• Or tap 'Server Config' to paste your custom Supabase Project URL & Anon Key.";
        }

        if (lower.contains("email logins are disabled") || lower.contains("email provider is disabled")) {
            return "Email authentication is disabled on the connected Supabase server.\n\n" +
                    "Active Project ID: " + displayRef + "\n\n" +
                    "Why this happens:\n" +
                    "The Email provider toggle is OFF on server '" + displayRef + "'.\n\n" +
                    "How to fix:\n" +
                    "1. Open: " + dashboardUrl + "\n" +
                    "2. Turn ON 'Enable Email provider' and click SAVE\n" +
                    "3. If your project in Supabase is different, tap 'Server Config' to update your credentials.";
        }

        if (lower.contains("invalid login credentials")) {
            return "Invalid email address or password. Please verify your credentials or tap 'Forgot password?' to receive a reset link.";
        }

        if (lower.contains("user already registered")) {
            return "An account with this email address already exists. Please tap 'Already have an account? Sign in'.";
        }

        if (lower.contains("email not confirmed")) {
            return "Your email address has not been confirmed yet. Please check your inbox for the confirmation link sent by Supabase, or disable 'Confirm email' in Supabase Dashboard (Authentication -> Providers -> Email).";
        }

        if (lower.contains("signup requires a valid password") || lower.contains("password should be at least")) {
            return "Password does not meet security requirements. Please use at least 6 characters.";
        }

        return rawMsg;
    }


    private void notifySuccess(AuthCallback cb, String uid, String email, String name, boolean emailConfirm) {
        mainHandler.post(() -> cb.onSuccess(uid, email, name, emailConfirm));
    }

    private void notifyError(AuthCallback cb, String error) {
        mainHandler.post(() -> cb.onError(error));
    }

    private void notifySimpleError(SimpleCallback cb, String error) {
        mainHandler.post(() -> cb.onError(error));
    }
}
