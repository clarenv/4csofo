package com.example.a4csofo;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.UserViewHolder> {

    public interface OnUserActionListener {
        void onViewProfile(AdminUserModel user);
    }

    public interface DeleteListener {
        void onDelete(String uid);
    }

    private List<AdminUserModel> userList;
    private final DeleteListener deleteListener;
    private final OnUserActionListener actionListener;

    public AdminUserAdapter(List<AdminUserModel> userList,
                            DeleteListener deleteListener,
                            OnUserActionListener actionListener) {
        this.userList = userList != null ? userList : new ArrayList<>();
        this.deleteListener = deleteListener;
        this.actionListener = actionListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_user, parent, false);
        return new UserViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        AdminUserModel user = userList.get(position);

        // Set User Data
        holder.txtName.setText(user.getName() != null ? user.getName() : "Unknown");
        holder.txtEmail.setText(user.getEmail() != null ? user.getEmail() : "No email");

        // Role with proper formatting
        String role = user.getRole();
        if (role != null && !role.trim().isEmpty()) {
            String formattedRole = role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
            holder.txtRole.setText(formattedRole);

            // Set role color based on role type
            switch (role.toLowerCase()) {
                case "admin":
                    holder.txtRole.setBackgroundColor(Color.parseColor("#FF5252")); // Red
                    break;
                case "staff":
                    holder.txtRole.setBackgroundColor(Color.parseColor("#FF9800")); // Orange
                    break;
                default: // customer
                    holder.txtRole.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
                    break;
            }
        } else {
            holder.txtRole.setText("Customer");
            holder.txtRole.setBackgroundColor(Color.parseColor("#4CAF50")); // Green
        }

        // Phone
        if (user.getPhone() != null && !user.getPhone().isEmpty() && !user.getPhone().equals("No phone")) {
            holder.txtPhone.setText("📱 " + user.getPhone());
            holder.txtPhone.setVisibility(View.VISIBLE);
        } else {
            holder.txtPhone.setVisibility(View.GONE);
        }

        // Address
        if (user.getAddress() != null && !user.getAddress().isEmpty() && !user.getAddress().equals("No address")) {
            holder.txtAddress.setText("📍 " + user.getAddress());
            holder.txtAddress.setVisibility(View.VISIBLE);
        } else {
            holder.txtAddress.setVisibility(View.GONE);
        }

        // Status - FIXED: Use isOnline() instead of isUserOnline()
        if (user.isOnline()) {
            holder.tvStatus.setText("🟢 Online");
            holder.tvStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
        } else {
            holder.tvStatus.setText("🔴 Offline");
            holder.tvStatus.setTextColor(Color.parseColor("#757575")); // Gray
        }

        String uid = user.getUid();

        // Delete Button
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null && uid != null && !uid.isEmpty()) {
                    showDeleteConfirmation(holder.itemView.getContext(), user);
                }
            });
        }

        // View Profile Button
        if (holder.btnViewProfile != null) {
            holder.btnViewProfile.setOnClickListener(v -> {
                if (actionListener != null) {
                    actionListener.onViewProfile(user);
                }
            });
        }

        // Whole Item Click
        holder.itemView.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onViewProfile(user);
            }
        });

        // Long Press for Quick Delete
        holder.itemView.setOnLongClickListener(v -> {
            showDeleteConfirmation(holder.itemView.getContext(), user);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    // Update List (for Search/Filter)
    public void updateList(List<AdminUserModel> newList) {
        this.userList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    // Delete Confirmation
    private void showDeleteConfirmation(android.content.Context context, AdminUserModel user) {
        new AlertDialog.Builder(context)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete " + user.getName() + "?\n\nThis action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    if (deleteListener != null && user.getUid() != null) {
                        deleteListener.onDelete(user.getUid());
                    }
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // ViewHolder - CHANGED: TextView instead of ImageView
    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtEmail, txtRole, tvStatus, txtPhone, txtAddress;
        TextView btnDelete, btnViewProfile; // CHANGED to TextView

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtEmail = itemView.findViewById(R.id.txtEmail);
            txtRole = itemView.findViewById(R.id.txtRole);

            // CHANGED: These are TextViews in your XML
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnViewProfile = itemView.findViewById(R.id.btnViewProfile);

            // Status field (check if exists in your layout)
            tvStatus = itemView.findViewById(R.id.tvStatus);

            // Optional fields
            txtPhone = itemView.findViewById(R.id.txtPhone);
            txtAddress = itemView.findViewById(R.id.txtAddress);
        }
    }
}