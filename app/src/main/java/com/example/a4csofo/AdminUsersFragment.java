package com.example.a4csofo;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.*;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EditText searchBox;
    private FloatingActionButton fabAddUser;
    private AdminUserAdapter userAdapter;
    private List<AdminUserModel> userList = new ArrayList<>();
    private DatabaseReference usersRef;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);

        recyclerView = view.findViewById(R.id.recyclerUsers);
        progressBar = view.findViewById(R.id.progressBar);
        searchBox = view.findViewById(R.id.editSearch);
        fabAddUser = view.findViewById(R.id.fabAddUser);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userAdapter = new AdminUserAdapter(userList,
                this::deleteUser,
                this::showUserProfile);

        recyclerView.setAdapter(userAdapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        loadUsers();

        // -------------------- Search & Filter --------------------
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                List<AdminUserModel> filtered = new ArrayList<>();
                for (AdminUserModel user : userList) {
                    if (user.getName().toLowerCase().contains(s.toString().toLowerCase()) ||
                            user.getEmail().toLowerCase().contains(s.toString().toLowerCase()) ||
                            user.getRole().toLowerCase().contains(s.toString().toLowerCase())) {
                        filtered.add(user);
                    }
                }
                userAdapter.updateList(filtered);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // -------------------- Add New User --------------------
        fabAddUser.setOnClickListener(v -> showAddUserDialog());

        return view;
    }

    // -------------------- Load Users --------------------
    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                userList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    try {
                        AdminUserModel user = snap.getValue(AdminUserModel.class);
                        if (user != null) userList.add(user);
                    } catch (Exception e) {
                        e.printStackTrace(); // Skip invalid nodes
                    }
                }
                userAdapter.updateList(userList);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(getContext(), "Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------- Delete User --------------------
    private void deleteUser(String uid) {
        new AlertDialog.Builder(getContext())
                .setTitle("Confirm Delete")
                .setMessage("Are you sure you want to delete this user?")
                .setPositiveButton("Delete", (dialog, which) -> usersRef.child(uid).removeValue())
                .setNegativeButton("Cancel", null)
                .show();
    }

    // -------------------- Show User Profile --------------------
    private void showUserProfile(AdminUserModel user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(user.getName() + " Profile");

        StringBuilder details = new StringBuilder();
        details.append("Email: ").append(user.getEmail()).append("\n")
                .append("Role: ").append(user.getRole()).append("\n")
                .append("Active: ").append(user.isActive() ? "Yes" : "No").append("\n")
                .append("Address: ").append(user.getAddress() != null ? user.getAddress() : "N/A").append("\n")
                .append("Orders: ").append(user.getOrdersSummary() != null ? user.getOrdersSummary() : "No orders").append("\n")
                .append("Payment History: ").append(user.getPaymentHistory() != null ? user.getPaymentHistory() : "No payment history");

        builder.setMessage(details.toString());

        // -------------------- Actions --------------------
        builder.setNeutralButton("Reset Password", (dialog, which) -> resetUserPassword(user));
        builder.setNegativeButton("Close", null);

        builder.show();
    }

    // -------------------- Reset User Password --------------------
    private void resetUserPassword(AdminUserModel user) {
        Toast.makeText(getContext(), "Password reset link sent to " + user.getEmail(), Toast.LENGTH_SHORT).show();
    }

    // -------------------- Add New User Dialog --------------------
    private void showAddUserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Add New User");

        View view = getLayoutInflater().inflate(R.layout.admin_dialog_add_user, null);
        builder.setView(view);

        EditText edtName = view.findViewById(R.id.etUserName);
        EditText edtEmail = view.findViewById(R.id.etUserEmail);
        Spinner roleSpinner = view.findViewById(R.id.spRole);

        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(getContext(),
                R.array.roles_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(adapter);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String name = edtName.getText().toString().trim();
            String email = edtEmail.getText().toString().trim();
            String role = roleSpinner.getSelectedItem().toString();

            if (!name.isEmpty() && !email.isEmpty()) {
                String uid = usersRef.push().getKey();
                if (uid != null) {
                    AdminUserModel newUser = new AdminUserModel(uid, name, email, role, true);
                    usersRef.child(uid).setValue(newUser);
                    Toast.makeText(getContext(), "User added successfully", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getContext(), "Failed to generate user ID", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(getContext(), "Name & Email cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}
