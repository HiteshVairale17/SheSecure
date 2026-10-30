package com.example.shesecure.contacts;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.shesecure.data.local.LocalContactCache;
import com.example.shesecure.models.EmergencyContact;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class EmergencyContactsActivity extends AppCompatActivity implements ContactsAdapter.OnContactActionListener {

    private static final int REQUEST_ADD_EDIT = 101;

    private RecyclerView rvContacts;
    private LinearLayout layoutEmpty;
    private TextView tvCounter;
    private MaterialButton btnAddContact;
    private ContactsAdapter adapter;
    private LocalContactCache cache;
    private com.example.shesecure.data.remote.SupabaseDataService dataService;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_emergency_contacts);

        cache = LocalContactCache.getInstance(this);
        dataService = com.example.shesecure.data.remote.SupabaseDataService.getInstance(this);

        ImageView btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(v -> finish());

        tvCounter = findViewById(R.id.tv_contacts_counter_header);
        rvContacts = findViewById(R.id.rv_contacts);
        layoutEmpty = findViewById(R.id.layout_empty_contacts);
        btnAddContact = findViewById(R.id.btn_add_contact_action);

        rvContacts.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ContactsAdapter(this);
        rvContacts.setAdapter(adapter);

        btnAddContact.setOnClickListener(v -> {
            List<EmergencyContact> current = cache.getContacts();
            if (current.size() >= LocalContactCache.MAX_CONTACTS) {
                Toast.makeText(this, "Maximum limit of 5 contacts reached.", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, AddEditContactActivity.class);
            startActivityForResult(intent, REQUEST_ADD_EDIT);
        });

        loadContacts();
        syncWithSupabase();
    }

    private void syncWithSupabase() {
        dataService.fetchContacts(new com.example.shesecure.data.remote.SupabaseDataService.ContactsCallback() {
            @Override
            public void onSuccess(List<EmergencyContact> remoteContacts) {
                if (isFinishing() || isDestroyed()) return;
                if (!remoteContacts.isEmpty()) {
                    cache.saveContacts(remoteContacts);
                    loadContacts();
                } else {
                    List<EmergencyContact> local = cache.getContacts();
                    for (EmergencyContact c : local) {
                        dataService.addContact(c, new com.example.shesecure.data.remote.SupabaseDataService.ContactActionCallback() {
                            @Override public void onSuccess(EmergencyContact contact) {}
                            @Override public void onError(String errorMessage) {}
                        });
                    }
                }
            }

            @Override
            public void onError(String errorMessage) {
                // Offline safe: continue using local cache without interruption
            }
        });
    }

    private void loadContacts() {
        List<EmergencyContact> list = cache.getContacts();
        tvCounter.setText(list.size() + "/" + LocalContactCache.MAX_CONTACTS);

        if (list.isEmpty()) {
            rvContacts.setVisibility(View.GONE);
            layoutEmpty.setVisibility(View.VISIBLE);
        } else {
            rvContacts.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
            adapter.setContacts(list);
        }

        if (list.size() >= LocalContactCache.MAX_CONTACTS) {
            btnAddContact.setEnabled(false);
            btnAddContact.setText("Limit Reached (5/5)");
        } else {
            btnAddContact.setEnabled(true);
            btnAddContact.setText(R.string.btn_add_contact);
        }
    }

    @Override
    public void onEdit(EmergencyContact contact) {
        Intent intent = new Intent(this, AddEditContactActivity.class);
        intent.putExtra("contact_extra", contact);
        startActivityForResult(intent, REQUEST_ADD_EDIT);
    }

    @Override
    public void onDelete(EmergencyContact contact) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Contact")
                .setMessage(getString(R.string.confirm_delete_contact, contact.getContactName()))
                .setPositiveButton("Remove", (dialog, which) -> {
                    cache.deleteContact(contact.getId());
                    Toast.makeText(this, contact.getContactName() + " removed", Toast.LENGTH_SHORT).show();
                    loadContacts();

                    dataService.deleteContact(contact.getId(), new com.example.shesecure.data.remote.SupabaseDataService.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onError(String errorMessage) {}
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ADD_EDIT && resultCode == RESULT_OK) {
            loadContacts();
        }
    }
}
