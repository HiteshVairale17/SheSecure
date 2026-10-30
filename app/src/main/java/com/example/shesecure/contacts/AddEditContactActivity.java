package com.example.shesecure.contacts;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.R;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.models.EmergencyContact;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;

public class AddEditContactActivity extends AppCompatActivity {

    private TextInputLayout tilName;
    private TextInputLayout tilPhone;
    private TextInputEditText etName;
    private TextInputEditText etPhone;
    private MaterialButton btnSave;
    private MaterialButton btnDelete;
    private TextView tvTitle;

    private LocalContactCache cache;
    private EmergencyContact existingContact;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_edit_contact);

        cache = LocalContactCache.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tvTitle = findViewById(R.id.tv_title);
        tilName = findViewById(R.id.til_contact_name);
        tilPhone = findViewById(R.id.til_contact_phone);
        etName = findViewById(R.id.et_contact_name);
        etPhone = findViewById(R.id.et_contact_phone);
        btnSave = findViewById(R.id.btn_save_contact);
        btnDelete = findViewById(R.id.btn_delete_contact);

        if (getIntent().hasExtra("contact_extra")) {
            existingContact = (EmergencyContact) getIntent().getSerializableExtra("contact_extra");
        }

        if (existingContact != null) {
            tvTitle.setText(R.string.title_edit_contact);
            etName.setText(existingContact.getContactName());
            etPhone.setText(existingContact.getPhoneNumber());
            btnDelete.setVisibility(View.VISIBLE);
            btnDelete.setOnClickListener(v -> confirmDelete());
        } else {
            tvTitle.setText(R.string.title_add_contact);
            btnDelete.setVisibility(View.GONE);
        }

        btnSave.setOnClickListener(v -> saveContact());
    }

    private void saveContact() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        tilName.setError(null);
        tilPhone.setError(null);

        if (TextUtils.isEmpty(name)) {
            tilName.setError("Contact name is required");
            etName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(phone)) {
            tilPhone.setError("Phone number is required");
            etPhone.requestFocus();
            return;
        }

        // Clean phone digits for validation
        String digitsOnly = phone.replaceAll("[^0-9]", "");
        if (digitsOnly.length() < 7 || digitsOnly.length() > 15) {
            tilPhone.setError("Please enter a valid phone number (7-15 digits)");
            etPhone.requestFocus();
            return;
        }

        // Duplicate check
        List<EmergencyContact> all = cache.getContacts();
        for (EmergencyContact c : all) {
            if (existingContact != null && c.getId().equals(existingContact.getId())) {
                continue; // skip self
            }
            if (c.getPhoneNumber().replaceAll("[^0-9]", "").equals(digitsOnly)) {
                tilPhone.setError("This phone number is already in your emergency contacts");
                etPhone.requestFocus();
                return;
            }
        }

        com.example.shesecure.data.remote.SupabaseDataService dataService =
                com.example.shesecure.data.remote.SupabaseDataService.getInstance(this);

        if (existingContact != null) {
            existingContact.setContactName(name);
            existingContact.setPhoneNumber(phone);
            cache.updateContact(existingContact);
            dataService.updateContact(existingContact, new com.example.shesecure.data.remote.SupabaseDataService.ContactActionCallback() {
                @Override public void onSuccess(EmergencyContact contact) {}
                @Override public void onError(String errorMessage) {}
            });
            Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
        } else {
            EmergencyContact newContact = new EmergencyContact(name, phone);
            boolean added = cache.addContact(newContact);
            if (!added) {
                Toast.makeText(this, "Cannot add: 5 contact limit reached", Toast.LENGTH_SHORT).show();
                return;
            }
            dataService.addContact(newContact, new com.example.shesecure.data.remote.SupabaseDataService.ContactActionCallback() {
                @Override public void onSuccess(EmergencyContact contact) {}
                @Override public void onError(String errorMessage) {}
            });
            Toast.makeText(this, "Emergency contact saved", Toast.LENGTH_SHORT).show();
        }

        setResult(RESULT_OK);
        finish();
    }

    private void confirmDelete() {
        if (existingContact == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage(getString(R.string.confirm_delete_contact, existingContact.getContactName()))
                .setPositiveButton("Remove", (dialog, which) -> {
                    cache.deleteContact(existingContact.getId());
                    com.example.shesecure.data.remote.SupabaseDataService.getInstance(this)
                            .deleteContact(existingContact.getId(), new com.example.shesecure.data.remote.SupabaseDataService.SimpleCallback() {
                                @Override public void onSuccess() {}
                                @Override public void onError(String errorMessage) {}
                            });
                    Toast.makeText(this, "Contact removed", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
