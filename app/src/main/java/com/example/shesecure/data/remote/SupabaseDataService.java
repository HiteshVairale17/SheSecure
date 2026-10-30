package com.example.shesecure.data.remote;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.shesecure.auth.SessionManager;
import com.example.shesecure.models.EmergencyContact;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseDataService {

    public interface ContactsCallback {
        void onSuccess(List<EmergencyContact> contacts);
        void onError(String errorMessage);
    }

    public interface ContactActionCallback {
        void onSuccess(EmergencyContact contact);
        void onError(String errorMessage);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String errorMessage);
    }

    public interface ProfileCallback {
        void onSuccess(String displayName);
        void onError(String errorMessage);
    }

    public interface FullProfileCallback {
        void onSuccess(String displayName, String phoneNumber);
        void onError(String errorMessage);
    }

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json; charset=utf-8");

    private static SupabaseDataService instance;
    private final Context context;
    private final OkHttpClient httpClient;
    private final Gson gson;
    private final SessionManager sessionManager;
    private final SupabaseConfig config;
    private final Handler mainHandler;

    private SupabaseDataService(Context context) {
        this.context = context.getApplicationContext();
        this.sessionManager = SessionManager.getInstance(this.context);
        this.config = SupabaseConfig.getInstance(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.gson = new Gson();

        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();
    }

    public static synchronized SupabaseDataService getInstance(Context context) {
        if (instance == null) {
            instance = new SupabaseDataService(context);
        }
        return instance;
    }

    private boolean isAuthReady() {
        return config.isConfigured() && sessionManager.isLoggedIn();
    }

    private Request.Builder createAuthenticatedRequestBuilder(String pathAndQuery) {
        String baseUrl = config.getUrl();
        String anonKey = config.getAnonKey();
        String token = sessionManager.getAccessToken();

        return new Request.Builder()
                .url(baseUrl + "/rest/v1/" + pathAndQuery)
                .addHeader("apikey", anonKey)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json");
    }

    /**
     * Fetch all emergency contacts for current authenticated user.
     */
    public void fetchContacts(ContactsCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        String userId = sessionManager.getUserId();
        String path = "emergency_contacts?user_id=eq." + userId + "&select=*&order=created_at.asc";

        Request request = createAuthenticatedRequestBuilder(path)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error fetching contacts: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        Type listType = new TypeToken<ArrayList<EmergencyContact>>() {}.getType();
                        List<EmergencyContact> list = gson.fromJson(resp, listType);
                        if (list == null) list = new ArrayList<>();
                        List<EmergencyContact> finalList = list;
                        mainHandler.post(() -> callback.onSuccess(finalList));
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Error parsing contacts: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Failed to fetch contacts (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    /**
     * Create an emergency contact in Supabase.
     */
    public void addContact(EmergencyContact contact, ContactActionCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        String userId = sessionManager.getUserId();
        contact.setUserId(userId);
        if (contact.getCreatedAt() == null || contact.getCreatedAt().isEmpty()) {
            contact.setCreatedAt(getCurrentIsoTimestamp());
        }

        JsonObject body = new JsonObject();
        body.addProperty("id", contact.getId());
        body.addProperty("user_id", userId);
        body.addProperty("contact_name", contact.getContactName());
        body.addProperty("phone_number", contact.getPhoneNumber());
        body.addProperty("created_at", contact.getCreatedAt());

        Request request = createAuthenticatedRequestBuilder("emergency_contacts")
                .addHeader("Prefer", "return=representation")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error creating contact: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();
                        if (arr.size() > 0) {
                            EmergencyContact created = gson.fromJson(arr.get(0), EmergencyContact.class);
                            mainHandler.post(() -> callback.onSuccess(created));
                        } else {
                            mainHandler.post(() -> callback.onSuccess(contact));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onSuccess(contact));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Failed to add contact (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    /**
     * Update an existing emergency contact in Supabase.
     */
    public void updateContact(EmergencyContact contact, ContactActionCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        JsonObject body = new JsonObject();
        body.addProperty("contact_name", contact.getContactName());
        body.addProperty("phone_number", contact.getPhoneNumber());
        body.addProperty("updated_at", getCurrentIsoTimestamp());

        String path = "emergency_contacts?id=eq." + contact.getId();
        Request request = createAuthenticatedRequestBuilder(path)
                .addHeader("Prefer", "return=representation")
                .patch(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error updating contact: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    mainHandler.post(() -> callback.onSuccess(contact));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to update contact (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    /**
     * Delete an emergency contact from Supabase.
     */
    public void deleteContact(String contactId, SimpleCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        String path = "emergency_contacts?id=eq." + contactId;
        Request request = createAuthenticatedRequestBuilder(path)
                .delete()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error deleting contact: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    String resp = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Failed to delete contact (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    /**
     * Fetch user profile from Supabase profiles table.
     */
    public void fetchProfile(ProfileCallback callback) {
        fetchFullProfile(new FullProfileCallback() {
            @Override
            public void onSuccess(String displayName, String phoneNumber) {
                callback.onSuccess(displayName);
            }

            @Override
            public void onError(String errorMessage) {
                callback.onError(errorMessage);
            }
        });
    }

    /**
     * Fetch user profile (both display_name and phone_number) from Supabase profiles table.
     */
    public void fetchFullProfile(FullProfileCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        String userId = sessionManager.getUserId();
        String path = "profiles?id=eq." + userId + "&select=*";

        Request request = createAuthenticatedRequestBuilder(path)
                .get()
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error fetching profile: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try {
                        JsonArray arr = JsonParser.parseString(resp).getAsJsonArray();
                        if (arr.size() > 0) {
                            JsonObject p = arr.get(0).getAsJsonObject();
                            String name = p.has("display_name") && !p.get("display_name").isJsonNull() ? p.get("display_name").getAsString() : "";
                            String phone = p.has("phone_number") && !p.get("phone_number").isJsonNull() ? p.get("phone_number").getAsString() : "";
                            mainHandler.post(() -> callback.onSuccess(name, phone));
                        } else {
                            mainHandler.post(() -> callback.onSuccess(sessionManager.getDisplayName(), sessionManager.getUserPhone()));
                        }
                    } catch (Exception e) {
                        mainHandler.post(() -> callback.onError("Error parsing profile: " + e.getMessage()));
                    }
                } else {
                    mainHandler.post(() -> callback.onError("Failed to fetch profile (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    /**
     * Update user display name in Supabase profiles table.
     */
    public void updateProfileName(String newDisplayName, SimpleCallback callback) {
        updateProfile(newDisplayName, sessionManager.getUserPhone(), callback);
    }

    /**
     * Upsert user profile (display name and phone number) in Supabase profiles table.
     */
    public void updateProfile(String newDisplayName, String newPhoneNumber, SimpleCallback callback) {
        if (!isAuthReady()) {
            mainHandler.post(() -> callback.onError("Not authenticated with Supabase."));
            return;
        }

        String userId = sessionManager.getUserId();
        JsonObject body = new JsonObject();
        body.addProperty("id", userId);
        body.addProperty("display_name", newDisplayName);
        if (newPhoneNumber != null && !newPhoneNumber.trim().isEmpty()) {
            body.addProperty("phone_number", newPhoneNumber.trim());
        }
        body.addProperty("updated_at", getCurrentIsoTimestamp());

        // Use upsert with merge-duplicates and on_conflict=id so it creates or updates safely
        Request request = createAuthenticatedRequestBuilder("profiles?on_conflict=id")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(RequestBody.create(body.toString(), JSON_MEDIA_TYPE))
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                mainHandler.post(() -> callback.onError("Network error updating profile: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    mainHandler.post(callback::onSuccess);
                } else {
                    String resp = response.body() != null ? response.body().string() : "";
                    mainHandler.post(() -> callback.onError("Failed to update profile (HTTP " + response.code() + "): " + resp));
                }
            }
        });
    }

    private String getCurrentIsoTimestamp() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
