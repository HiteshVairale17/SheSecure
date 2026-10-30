package com.example.shesecure.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.UUID;

public class EmergencyContact implements Serializable {
    @SerializedName("id")
    private String id;

    @SerializedName("user_id")
    private String userId;

    @SerializedName("contact_name")
    private String contactName;

    @SerializedName("phone_number")
    private String phoneNumber;

    @SerializedName("created_at")
    private String createdAt;

    public EmergencyContact() {
        this.id = UUID.randomUUID().toString();
    }

    public EmergencyContact(String contactName, String phoneNumber) {
        this.id = UUID.randomUUID().toString();
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
    }

    public EmergencyContact(String id, String userId, String contactName, String phoneNumber, String createdAt) {
        this.id = id;
        this.userId = userId;
        this.contactName = contactName;
        this.phoneNumber = phoneNumber;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
