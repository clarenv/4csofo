package com.example.a4csofo;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

        // -------------------- Null-Safe Text --------------------
        holder.txtName.setText(user.getName() != null ? user.getName() : "Unknown");
        holder.txtEmail.setText(user.getEmail() != null ? user.getEmail() : "Unknown");
        holder.txtRole.setText(user.getRole() != null ? user.getRole() : "Unknown");

        // -------------------- Active / Online Status --------------------
        boolean isActive = user.isActive();
        holder.tvOnlineStatus.setText(isActive ? "Online" : "Offline");
        holder.tvOnlineStatus.setTextColor(isActive ? Color.parseColor("#4CAF50")
                : Color.parseColor("#F44336"));

        String uid = user.getUid();

        // -------------------- Buttons --------------------
        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null && uid != null && !uid.isEmpty()) {
                deleteListener.onDelete(uid);
            } else {
                Toast.makeText(holder.itemView.getContext(),
                        "Cannot delete user: UID is missing",
                        Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnViewProfile.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onViewProfile(user);
        });
    }

    @Override
    public int getItemCount() {
        return userList != null ? userList.size() : 0;
    }

    // -------------------- Update List (for Search/Filter) --------------------
    public void updateList(List<AdminUserModel> newList) {
        this.userList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        TextView txtName, txtEmail, txtRole, tvOnlineStatus;
        ImageView btnDelete, btnViewProfile;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            txtName = itemView.findViewById(R.id.txtName);
            txtEmail = itemView.findViewById(R.id.txtEmail);
            txtRole = itemView.findViewById(R.id.txtRole);
            tvOnlineStatus = itemView.findViewById(R.id.tvOnlineStatus);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnViewProfile = itemView.findViewById(R.id.btnViewProfile);
        }
    }
}
