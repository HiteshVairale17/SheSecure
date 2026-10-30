package com.example.shesecure.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.shesecure.models.EmergencyContact;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class LocalContactCache {
    private static final String PREF_NAME = "shesecure_local_cache";
    private static final String KEY_CONTACTS = "emergency_contacts";
    private static final String KEY_USER_NAME = "user_display_name";
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_PHONE = "user_phone";
    private static final String KEY_SHAKE_ENABLED = "shake_enabled";
    private static final String KEY_SHAKE_SENSITIVITY = "shake_sensitivity"; // 0: low, 1: medium, 2: high
    public static final int MAX_CONTACTS = 5;

    private static LocalContactCache instance;
    private final Context context;
    private final SharedPreferences prefs;
    private final Gson gson;

    private LocalContactCache(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        gson = new Gson();
        initDefaultDataIfEmpty();
    }

    public static synchronized LocalContactCache getInstance(Context context) {
        if (instance == null) {
            instance = new LocalContactCache(context);
        }
        return instance;
    }

    private void initDefaultDataIfEmpty() {
        if (!prefs.contains(KEY_CONTACTS)) {
            saveContacts(new ArrayList<>());
        }
    }

    public List<EmergencyContact> getContacts() {
        String json = prefs.getString(KEY_CONTACTS, null);
        if (json == null) {
            return new ArrayList<>();
        }
        Type type = new TypeToken<ArrayList<EmergencyContact>>() {}.getType();
        List<EmergencyContact> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public boolean saveContacts(List<EmergencyContact> contacts) {
        if (contacts.size() > MAX_CONTACTS) {
            return false;
        }
        String json = gson.toJson(contacts);
        boolean saved = prefs.edit().putString(KEY_CONTACTS, json).commit();
        if (saved && context != null) {
            com.example.shesecure.widget.SheSecureWidgetProvider.updateAllWidgets(context);
        }
        return saved;
    }

    public boolean addContact(EmergencyContact contact) {
        List<EmergencyContact> list = getContacts();
        if (list.size() >= MAX_CONTACTS) {
            return false;
        }
        list.add(contact);
        return saveContacts(list);
    }

    public boolean updateContact(EmergencyContact updated) {
        List<EmergencyContact> list = getContacts();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(updated.getId())) {
                list.set(i, updated);
                return saveContacts(list);
            }
        }
        return false;
    }

    public boolean deleteContact(String contactId) {
        List<EmergencyContact> list = getContacts();
        boolean removed = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(contactId)) {
                list.remove(i);
                removed = true;
                break;
            }
        }
        if (removed) {
            return saveContacts(list);
        }
        return false;
    }

    public String getUserName() {
        return prefs.getString(KEY_USER_NAME, "User");
    }

    public void setUserName(String name) {
        prefs.edit().putString(KEY_USER_NAME, name).apply();
    }

    public String getUserEmail() {
        return prefs.getString(KEY_USER_EMAIL, "user@example.com");
    }

    public void setUserEmail(String email) {
        prefs.edit().putString(KEY_USER_EMAIL, email).apply();
    }

    public String getUserPhone() {
        return prefs.getString(KEY_USER_PHONE, "");
    }

    public void setUserPhone(String phone) {
        prefs.edit().putString(KEY_USER_PHONE, phone != null ? phone : "").apply();
    }

    public boolean isShakeEnabled() {
        return prefs.getBoolean(KEY_SHAKE_ENABLED, true);
    }

    public void setShakeEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_SHAKE_ENABLED, enabled).apply();
    }

    public int getShakeSensitivity() {
        return prefs.getInt(KEY_SHAKE_SENSITIVITY, 1); // 1 = Medium
    }

    public void setShakeSensitivity(int sensitivity) {
        prefs.edit().putInt(KEY_SHAKE_SENSITIVITY, sensitivity).apply();
    }
}
