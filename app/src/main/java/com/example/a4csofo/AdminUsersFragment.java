package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdminUsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EditText searchBox;
    private TextView tvEmptyState;
    private SwipeRefreshLayout swipeRefresh;
    private AdminUserAdapter userAdapter;
    private List<AdminUserModel> userList = new ArrayList<>();
    private List<AdminUserModel> filteredList = new ArrayList<>();
    private DatabaseReference usersRef;
    private ValueEventListener usersListener;

    private Handler searchHandler = new Handler();
    private Runnable searchRunnable;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerUsers);
        progressBar = view.findViewById(R.id.progressBar);
        searchBox = view.findViewById(R.id.editSearch);

        // Check if tvEmptyState exists in layout
        tvEmptyState = view.findViewById(R.id.tvEmptyState);
        if (tvEmptyState == null) {
            // Create a TextView programmatically if not in layout
            tvEmptyState = new TextView(getContext());
            tvEmptyState.setText("No users found");
            tvEmptyState.setTextSize(16);
            tvEmptyState.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvEmptyState.setGravity(View.TEXT_ALIGNMENT_CENTER);
            tvEmptyState.setVisibility(View.GONE);
        }

        swipeRefresh = view.findViewById(R.id.swipeRefresh);

        // Setup RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userAdapter = new AdminUserAdapter(filteredList,
                this::deleteUser,
                this::showUserProfile);

        recyclerView.setAdapter(userAdapter);

        // Initialize Firebase
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Load users
        loadUsers();

        // -------------------- Search with Debounce --------------------
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Remove previous callbacks
                if (searchHandler != null && searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }

                // Create new search runnable
                searchRunnable = () -> filterUsers(s.toString());

                // Delay search by 300ms for better performance
                if (searchHandler != null) {
                    searchHandler.postDelayed(searchRunnable, 300);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Clear search focus when scrolling
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    searchBox.clearFocus();
                }
            }
        });

        // -------------------- Pull to Refresh --------------------
        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(() -> {
                loadUsers();
                new Handler().postDelayed(() -> {
                    if (swipeRefresh != null) {
                        swipeRefresh.setRefreshing(false);
                    }
                }, 1000);
            });
        }

        return view;
    }

    // -------------------- Load Users --------------------
    private void loadUsers() {
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        if (tvEmptyState != null) {
            tvEmptyState.setVisibility(View.GONE);
        }

        // Remove previous listener if exists
        if (usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }

        usersListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }

                userList.clear();
                for (DataSnapshot snap : snapshot.getChildren()) {
                    try {
                        AdminUserModel user = snap.getValue(AdminUserModel.class);
                        if (user != null) {
                            user.setUid(snap.getKey()); // Set UID from Firebase key
                            userList.add(user);
                        }
                    } catch (Exception e) {
                        // Skip invalid entries
                    }
                }

                // Update filtered list with current search
                String searchText = searchBox != null ? searchBox.getText().toString() : "";
                filterUsers(searchText);

                // Show empty state if no users
                if (tvEmptyState != null) {
                    if (userList.isEmpty()) {
                        tvEmptyState.setText("No users found");
                        tvEmptyState.setVisibility(View.VISIBLE);
                    } else if (filteredList.isEmpty() && searchBox != null &&
                            !searchBox.getText().toString().isEmpty()) {
                        tvEmptyState.setText("No users match your search");
                        tvEmptyState.setVisibility(View.VISIBLE);
                    } else {
                        tvEmptyState.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }

                if (swipeRefresh != null) {
                    swipeRefresh.setRefreshing(false);
                }

                if (tvEmptyState != null) {
                    tvEmptyState.setText("Failed to load users");
                    tvEmptyState.setVisibility(View.VISIBLE);
                }

                Toast.makeText(getContext(), "Failed to load users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };

        usersRef.addValueEventListener(usersListener);
    }

    // -------------------- Filter Users --------------------
    private void filterUsers(String query) {
        filteredList.clear();

        if (query == null || query.isEmpty()) {
            // Show all users
            filteredList.addAll(userList);
        } else {
            // Filter users based on search query
            String lowerQuery = query.toLowerCase();
            for (AdminUserModel user : userList) {
                if ((user.getName() != null && user.getName().toLowerCase().contains(lowerQuery)) ||
                        (user.getEmail() != null && user.getEmail().toLowerCase().contains(lowerQuery)) ||
                        (user.getRole() != null && user.getRole().toLowerCase().contains(lowerQuery))) {
                    filteredList.add(user);
                }
            }
        }

        userAdapter.updateList(filteredList);

        // Update empty state
        if (tvEmptyState != null) {
            if (filteredList.isEmpty() && !userList.isEmpty() &&
                    searchBox != null && !searchBox.getText().toString().isEmpty()) {
                tvEmptyState.setText("No users match your search");
                tvEmptyState.setVisibility(View.VISIBLE);
            } else if (filteredList.isEmpty() && userList.isEmpty()) {
                tvEmptyState.setText("No users found");
                tvEmptyState.setVisibility(View.VISIBLE);
            } else if (!filteredList.isEmpty()) {
                tvEmptyState.setVisibility(View.GONE);
            }
        }
    }

    // -------------------- Delete User --------------------
    private void deleteUser(String uid) {
        new AlertDialog.Builder(getContext())
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete this user? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> {
                    usersRef.child(uid).removeValue()
                            .addOnSuccessListener(aVoid -> {
                                Toast.makeText(getContext(), "User deleted successfully", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                Toast.makeText(getContext(), "Failed to delete user: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            });
                })
                .setNegativeButton("Cancel", null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    // -------------------- Show User Profile --------------------
    private void showUserProfile(AdminUserModel user) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(user.getName());

        StringBuilder details = new StringBuilder();
        details.append("📧 Email: ").append(user.getEmail()).append("\n\n")
                .append("👤 Role: ").append(user.getRole()).append("\n\n")
                .append("📡 Online: ").append(user.isOnline() ? "Yes" : "No").append("\n\n");

        // Check if address exists and is not default
        if (user.getAddress() != null && !user.getAddress().isEmpty() &&
                !user.getAddress().equals("No address") && !user.getAddress().equals("N/A")) {
            details.append("📍 Address: ").append(user.getAddress()).append("\n\n");
        }

        // Check if orders summary exists and is not default
        if (user.getOrdersSummary() != null && !user.getOrdersSummary().isEmpty() &&
                !user.getOrdersSummary().equals("No orders") && !user.getOrdersSummary().equals("N/A")) {
            details.append("📦 Orders: ").append(user.getOrdersSummary()).append("\n\n");
        }

        // Check if payment history exists and is not default
        if (user.getPaymentHistory() != null && !user.getPaymentHistory().isEmpty() &&
                !user.getPaymentHistory().equals("No payment history") && !user.getPaymentHistory().equals("N/A")) {
            details.append("💳 Payment History: ").append(user.getPaymentHistory()).append("\n\n");
        }

        builder.setMessage(details.toString());

        // Only show close button
        builder.setNegativeButton("Close", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // -------------------- Cleanup --------------------
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Remove search handler callbacks
        if (searchHandler != null && searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }

        // Remove Firebase listener
        if (usersRef != null && usersListener != null) {
            usersRef.removeEventListener(usersListener);
        }
    }
}