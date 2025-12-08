package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AdminUsersFragment extends Fragment {

    // UI Components
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EditText searchBox;
    private TextView tvEmptyState, tvUserCount;
    private SwipeRefreshLayout swipeRefresh;
    private MaterialButton btnSort, btnFilterRole, btnExport;

    // Adapters & Data
    private AdminUserAdapter userAdapter;
    private List<AdminUserModel> userList = new ArrayList<>();
    private List<AdminUserModel> filteredList = new ArrayList<>();

    // Firebase
    private DatabaseReference usersRef;
    private ValueEventListener usersListener;

    // Handlers
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    // State management
    private String currentSort = "name_asc";
    private String currentRoleFilter = "all";
    private String lastSearchQuery = "";

    // Security
    private FirebaseAuth auth;
    private String currentAdminId;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);

        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentAdminId = auth.getCurrentUser().getUid();
        }

        // Initialize views
        initializeViews(view);
        setupRecyclerView();
        setupFirebase();

        // Load users
        loadUsers();

        // Setup event listeners
        setupSearch();
        setupButtons();
        setupSwipeRefresh();

        return view;
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerUsers);
        progressBar = view.findViewById(R.id.progressBar);
        searchBox = view.findViewById(R.id.editSearch);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        tvUserCount = view.findViewById(R.id.tvUserCount);
        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        btnSort = view.findViewById(R.id.btnSort);
        btnFilterRole = view.findViewById(R.id.btnFilterRole);
        btnExport = view.findViewById(R.id.btnExport);
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userAdapter = new AdminUserAdapter(filteredList,
                this::deleteUser,
                this::showUserProfile);

        recyclerView.setAdapter(userAdapter);
    }

    private void setupFirebase() {
        usersRef = FirebaseDatabase.getInstance().getReference("users");
    }

    // ==================== DATA LOADING ====================

    private void loadUsers() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmptyState.setVisibility(View.GONE);

        // Remove previous listener if exists
        if (usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                userList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    try {
                        AdminUserModel user = parseUserFromSnapshot(snap);
                        if (user != null) {
                            userList.add(user);
                        }
                    } catch (Exception e) {
                        Log.e("AdminUsersFragment", "Error parsing user: " + e.getMessage());
                    }
                }

                // Update filtered list
                applyFiltersAndUpdateUI();
                updateUserCount();
                updateEmptyState();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                tvEmptyState.setText("Failed to load users");
                tvEmptyState.setVisibility(View.VISIBLE);

                Toast.makeText(getContext(),
                        "Failed to load users: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        };

        usersRef.addValueEventListener(usersListener);
    }

    private AdminUserModel parseUserFromSnapshot(DataSnapshot snap) {
        String uid = snap.getKey();
        if (uid == null) return null;

        AdminUserModel user = new AdminUserModel();
        user.setUid(uid);

        // Parse fields safely
        user.setName(getStringValue(snap, "name", "Unnamed User"));
        user.setEmail(getStringValue(snap, "email", "No email"));
        user.setRole(getStringValue(snap, "role", "customer"));
        user.setPhone(getStringValue(snap, "phone", ""));
        user.setAddress(getStringValue(snap, "address", ""));

        // Online status
        if (snap.child("online").exists()) {
            Object onlineValue = snap.child("online").getValue();
            if (onlineValue instanceof Boolean) {
                user.setOnline((Boolean) onlineValue);
            } else if (onlineValue instanceof String) {
                user.setOnline(Boolean.parseBoolean((String) onlineValue));
            } else if (onlineValue instanceof Long) {
                user.setOnline(((Long) onlineValue) == 1);
            }
        }

        return user;
    }

    private String getStringValue(DataSnapshot snap, String key, String defaultValue) {
        if (!snap.child(key).exists()) return defaultValue;
        Object value = snap.child(key).getValue();
        return (value instanceof String && !((String) value).isEmpty()) ? (String) value : defaultValue;
    }

    // ==================== FILTER & SEARCH ====================

    private void setupSearch() {
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> performSearch(s.toString());
                searchHandler.postDelayed(searchRunnable, 400);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performSearch(String query) {
        lastSearchQuery = query;
        applyFiltersAndUpdateUI();
    }

    private void applyFiltersAndUpdateUI() {
        filteredList.clear();

        String searchQuery = lastSearchQuery.toLowerCase().trim();

        for (AdminUserModel user : userList) {
            // Skip current admin from list
            if (currentAdminId != null && user.getUid().equals(currentAdminId)) continue;

            boolean matchesSearch = searchQuery.isEmpty() ||
                    (user.getName() != null && user.getName().toLowerCase().contains(searchQuery)) ||
                    (user.getEmail() != null && user.getEmail().toLowerCase().contains(searchQuery)) ||
                    (user.getPhone() != null && user.getPhone().contains(searchQuery)) ||
                    (user.getUid() != null && user.getUid().toLowerCase().contains(searchQuery));

            boolean matchesRole = currentRoleFilter.equals("all") ||
                    (user.getRole() != null && user.getRole().equalsIgnoreCase(currentRoleFilter));

            if (matchesSearch && matchesRole) {
                filteredList.add(user);
            }
        }

        applySorting();
        userAdapter.updateList(filteredList);
        updateEmptyState();
        updateUserCount();
    }

    // ==================== SORTING ====================

    private void applySorting() {
        Collections.sort(filteredList, new Comparator<AdminUserModel>() {
            @Override
            public int compare(AdminUserModel u1, AdminUserModel u2) {
                switch (currentSort) {
                    case "name_desc":
                        return safeCompare(u2.getName(), u1.getName());
                    case "role":
                        int roleCompare = safeCompare(u1.getRole(), u2.getRole());
                        return roleCompare != 0 ? roleCompare : safeCompare(u1.getName(), u2.getName());
                    default: // name_asc
                        return safeCompare(u1.getName(), u2.getName());
                }
            }
        });
    }

    private int safeCompare(String str1, String str2) {
        String s1 = str1 != null ? str1 : "";
        String s2 = str2 != null ? str2 : "";
        return s1.compareToIgnoreCase(s2);
    }

    private void showSortOptions() {
        String[] options = {"Name (A-Z)", "Name (Z-A)", "By Role"};

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Sort Users")
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: currentSort = "name_asc"; break;
                        case 1: currentSort = "name_desc"; break;
                        case 2: currentSort = "role"; break;
                    }
                    applyFiltersAndUpdateUI();
                })
                .setNegativeButton("Cancel", null)
                .create();

        dialog.show();
    }

    // ==================== FILTER BY ROLE ====================

    private void showRoleFilterOptions() {
        String[] options = {"All Users", "Customers Only", "Staff Only", "Admins Only"};

        new AlertDialog.Builder(getContext())
                .setTitle("Filter by Role")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: currentRoleFilter = "all"; break;
                        case 1: currentRoleFilter = "customer"; break;
                        case 2: currentRoleFilter = "staff"; break;
                        case 3: currentRoleFilter = "admin"; break;
                    }
                    applyFiltersAndUpdateUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ==================== USER OPERATIONS ====================

    private void deleteUser(String uid) {
        // Prevent self-deletion
        if (currentAdminId != null && uid.equals(currentAdminId)) {
            Toast.makeText(getContext(), "You cannot delete your own account", Toast.LENGTH_LONG).show();
            return;
        }

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("⚠️ Delete User")
                .setMessage("This will permanently delete user data. This action cannot be undone!")
                .setPositiveButton("DELETE", (d, which) -> confirmUserDeletion(uid))
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .create();

        dialog.show();
    }

    private void confirmUserDeletion(String uid) {
        AlertDialog progressDialog = new AlertDialog.Builder(getContext())
                .setTitle("Deleting...")
                .setMessage("Removing user data...")
                .setCancelable(false)
                .create();
        progressDialog.show();

        // Delete user from database
        usersRef.child(uid).removeValue()
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), "User deleted successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(getContext(), "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ==================== USER PROFILE ====================

    private void showUserProfile(AdminUserModel user) {
        // Create custom dialog layout
        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_user_profile, null);

        // Initialize dialog views
        TextView tvUserName = dialogView.findViewById(R.id.tvUserName);
        TextView tvUserEmail = dialogView.findViewById(R.id.tvUserEmail);
        TextView tvUserRole = dialogView.findViewById(R.id.tvUserRole);
        TextView tvUserPhone = dialogView.findViewById(R.id.tvUserPhone);
        TextView tvUserAddress = dialogView.findViewById(R.id.tvUserAddress);
        TextView tvUserSince = dialogView.findViewById(R.id.tvUserSince);
        TextView tvUserStatus = dialogView.findViewById(R.id.tvUserStatus);
        ProgressBar profileProgress = dialogView.findViewById(R.id.profileProgress);
        TextView tvOrderStats = dialogView.findViewById(R.id.tvOrderStats);

        // Set basic user info
        tvUserName.setText(user.getName());
        tvUserEmail.setText(user.getEmail());
        tvUserRole.setText("Role: " + (user.getRole() != null ? user.getRole().toUpperCase() : "CUSTOMER"));

        // Phone
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            tvUserPhone.setText("📱 Phone: " + user.getPhone());
        } else {
            tvUserPhone.setText("📱 Phone: Not provided");
        }

        // Address
        if (user.getAddress() != null && !user.getAddress().isEmpty()) {
            tvUserAddress.setText("📍 Address: " + user.getAddress());
        } else {
            tvUserAddress.setText("📍 Address: Not provided");
        }

        // Status
        if (user.isOnline()) {
            tvUserStatus.setText("🟢 Online");
            tvUserStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else {
            tvUserStatus.setText("🔴 Offline");
            tvUserStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
        }

        // Since we don't have createdAt
        tvUserSince.setText("📅 Member: N/A");

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .setPositiveButton("Close", null)
                .setNeutralButton("Edit Role", (d, which) -> editUserRole(user))
                .create();

        dialog.show();

        // Load order statistics - USE SAFE METHOD
        loadUserOrderStatsSafe(user.getUid(), tvOrderStats, profileProgress);
    }

    // ==================== SAFE ORDER STATS METHOD ====================

    private void loadUserOrderStatsSafe(String userId, TextView statsView, ProgressBar progressBar) {
        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        ordersRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        progressBar.setVisibility(View.GONE);

                        int total = 0, completed = 0, pending = 0, cancelled = 0;
                        double totalSpent = 0;

                        for (DataSnapshot orderSnap : snapshot.getChildren()) {
                            try {
                                total++;

                                // SAFE PARSING - manual instead of getValue(OrderModel.class)
                                String status = getStringValue(orderSnap, "status", "");
                                String paymentMethod = getStringValue(orderSnap, "payment_method", "");

                                // Try to get total_price safely
                                double orderTotal = 0;
                                if (orderSnap.child("total_price").exists()) {
                                    Object totalValue = orderSnap.child("total_price").getValue();
                                    if (totalValue instanceof Double) {
                                        orderTotal = (Double) totalValue;
                                    } else if (totalValue instanceof Long) {
                                        orderTotal = ((Long) totalValue).doubleValue();
                                    } else if (totalValue instanceof Integer) {
                                        orderTotal = ((Integer) totalValue).doubleValue();
                                    } else if (totalValue instanceof String) {
                                        try {
                                            orderTotal = Double.parseDouble((String) totalValue);
                                        } catch (NumberFormatException e) {
                                            orderTotal = 0;
                                        }
                                    }
                                }

                                if ("completed".equalsIgnoreCase(status)) {
                                    completed++;
                                    totalSpent += orderTotal;
                                } else if ("pending".equalsIgnoreCase(status)) {
                                    pending++;
                                } else if ("cancelled".equalsIgnoreCase(status)) {
                                    cancelled++;
                                }

                            } catch (Exception e) {
                                Log.e("AdminUsersFragment", "Error parsing order: " + e.getMessage());
                            }
                        }

                        String statsText = "📊 ORDER STATISTICS\n\n" +
                                "• Total Orders: " + total + "\n" +
                                "• Completed: " + completed + "\n" +
                                "• Pending: " + pending + "\n" +
                                "• Cancelled: " + cancelled + "\n";

                        if (completed > 0) {
                            statsText += "• Total Spent: ₱" + String.format("%.2f", totalSpent) + "\n" +
                                    "• Average: ₱" + String.format("%.2f", totalSpent / completed);
                        }

                        if (total == 0) {
                            statsText = "📊 ORDER STATISTICS\n\nNo orders found for this user.";
                        }

                        statsView.setText(statsText);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        statsView.setText("Failed to load order statistics");
                    }
                });
    }

    private void editUserRole(AdminUserModel user) {
        if (currentAdminId != null && user.getUid().equals(currentAdminId)) {
            Toast.makeText(getContext(), "You cannot change your own role", Toast.LENGTH_LONG).show();
            return;
        }

        String currentRole = user.getRole() != null ? user.getRole() : "customer";

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("Change Role")
                .setMessage("Current: " + currentRole.toUpperCase() + "\n\nSelect new role:")
                .setPositiveButton("CUSTOMER", (d, which) -> updateRole(user.getUid(), "customer"))
                .setNeutralButton("STAFF", (d, which) -> updateRole(user.getUid(), "staff"))
                .setNegativeButton("ADMIN", (d, which) -> updateRole(user.getUid(), "admin"))
                .create();

        dialog.show();
    }

    private void updateRole(String uid, String newRole) {
        usersRef.child(uid).child("role").setValue(newRole)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Role updated to " + newRole.toUpperCase(), Toast.LENGTH_SHORT).show();

                    // Update local data
                    for (AdminUserModel user : userList) {
                        if (user.getUid().equals(uid)) {
                            user.setRole(newRole);
                            break;
                        }
                    }
                    applyFiltersAndUpdateUI();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    // ==================== HELPER METHODS ====================

    private void updateUserCount() {
        if (tvUserCount != null) {
            String text = String.format(Locale.getDefault(),
                    "👥 Users: %d total | %d filtered",
                    userList.size(), filteredList.size());
            tvUserCount.setText(text);
        }
    }

    private void updateEmptyState() {
        if (filteredList.isEmpty()) {
            if (userList.isEmpty()) {
                tvEmptyState.setText("No users found in database");
            } else if (!lastSearchQuery.isEmpty()) {
                tvEmptyState.setText("No users match '" + lastSearchQuery + "'");
            } else if (!currentRoleFilter.equals("all")) {
                tvEmptyState.setText("No " + currentRoleFilter + " users found");
            }
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    // ==================== SETUP METHODS ====================

    private void setupButtons() {
        btnSort.setOnClickListener(v -> showSortOptions());
        btnFilterRole.setOnClickListener(v -> showRoleFilterOptions());

        btnExport.setOnClickListener(v -> {
            if (filteredList.isEmpty()) {
                Toast.makeText(getContext(), "No users to export", Toast.LENGTH_SHORT).show();
                return;
            }

            AlertDialog exportDialog = new AlertDialog.Builder(getContext())
                    .setTitle("Export Users")
                    .setMessage("Export " + filteredList.size() + " users as:")
                    .setPositiveButton("CSV", (d, which) -> exportAsCSV())
                    .setNegativeButton("Cancel", null)
                    .create();
            exportDialog.show();
        });
    }

    private void exportAsCSV() {
        StringBuilder csv = new StringBuilder();
        csv.append("Name,Email,Role,Phone,Status\n");

        for (AdminUserModel user : filteredList) {
            csv.append(user.getName()).append(",")
                    .append(user.getEmail()).append(",")
                    .append(user.getRole()).append(",")
                    .append(user.getPhone()).append(",")
                    .append(user.isOnline() ? "Online" : "Offline").append("\n");
        }

        Toast.makeText(getContext(),
                "Exported " + filteredList.size() + " users to CSV",
                Toast.LENGTH_SHORT).show();
    }

    private void setupSwipeRefresh() {
        swipeRefresh.setOnRefreshListener(() -> {
            loadUsers();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                swipeRefresh.setRefreshing(false);
            }, 1000);
        });
    }

    // ==================== CLEANUP ====================

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (searchBox != null) {
            searchBox.clearFocus();
        }
    }
}