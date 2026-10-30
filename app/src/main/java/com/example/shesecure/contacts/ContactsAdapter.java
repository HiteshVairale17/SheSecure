package com.example.shesecure.contacts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.R;
import com.example.shesecure.models.EmergencyContact;

import java.util.ArrayList;
import java.util.List;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.ContactViewHolder> {

    public interface OnContactActionListener {
        void onEdit(EmergencyContact contact);
        void onDelete(EmergencyContact contact);
    }

    private List<EmergencyContact> contacts = new ArrayList<>();
    private final OnContactActionListener listener;

    public ContactsAdapter(OnContactActionListener listener) {
        this.listener = listener;
    }

    public void setContacts(List<EmergencyContact> contacts) {
        this.contacts = contacts != null ? contacts : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_emergency_contact, parent, false);
        return new ContactViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        EmergencyContact contact = contacts.get(position);
        holder.bind(contact, listener);
    }

    @Override
    public int getItemCount() {
        return contacts.size();
    }

    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvInitial;
        private final TextView tvName;
        private final TextView tvPhone;
        private final ImageView btnEdit;
        private final ImageView btnDelete;

        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInitial = itemView.findViewById(R.id.tv_contact_initial);
            tvName = itemView.findViewById(R.id.tv_contact_name);
            tvPhone = itemView.findViewById(R.id.tv_contact_phone);
            btnEdit = itemView.findViewById(R.id.btn_edit_contact);
            btnDelete = itemView.findViewById(R.id.btn_delete_contact);
        }

        public void bind(final EmergencyContact contact, final OnContactActionListener listener) {
            tvName.setText(contact.getContactName());
            tvPhone.setText(contact.getPhoneNumber());

            String initial = "C";
            if (contact.getContactName() != null && !contact.getContactName().trim().isEmpty()) {
                initial = contact.getContactName().trim().substring(0, 1).toUpperCase();
            }
            tvInitial.setText(initial);

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onEdit(contact);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onDelete(contact);
            });
        }
    }
}
