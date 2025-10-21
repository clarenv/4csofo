package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class AdminDashboardActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EditText searchBox;
    private UserAdapter userAdapter;
    private ArrayList<UserModel> userList;
    private DatabaseReference usersRef;

    // Admin buttons
    private Button btnOrders, btnUsers, btnCategories, btnMenuItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Find views
        recyclerView = findViewById(R.id.recyclerUsers);
        progressBar = findViewById(R.id.progressBar);
        searchBox = findViewById(R.id.editSearch);

        btnOrders = findViewById(R.id.btnOrders);
        btnUsers = findViewById(R.id.btnUsers);
        btnCategories = findViewById(R.id.btnCategories);
        btnMenuItems = findViewById(R.id.btnMenuItems);

        // RecyclerView setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        userList = new ArrayList<>();
        userAdapter = new UserAdapter(userList, this::deleteUser);
        recyclerView.setAdapter(userAdapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");

        loadUsersFromFirebase();

        // Search functionality
        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                userAdapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Button click listeners
        btnOrders.setOnClickListener(v -> startActivity(new Intent(this, ManageOrdersActivity.class)));
        btnUsers.setOnClickListener(v -> startActivity(new Intent(this, UsersActivity.class)));
        btnCategories.setOnClickListener(v -> startActivity(new Intent(this, CategoriesActivity.class)));
        btnMenuItems.setOnClickListener(v -> startActivity(new Intent(this, MenuItemsActivity.class)));
    }

    private void loadUsersFromFirebase() {
        progressBar.setVisibility(ProgressBar.VISIBLE);

        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                progressBar.setVisibility(ProgressBar.GONE);
                userList.clear();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String name = userSnapshot.child("name").getValue(String.class);
                    String email = userSnapshot.child("email").getValue(String.class);
                    String role = userSnapshot.child("role").getValue(String.class);
                    String uid = userSnapshot.getKey();

                    int id = uid != null ? uid.hashCode() : 0;
                    userList.add(new UserModel(id, name, email, role));
                }
                userAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                progressBar.setVisibility(ProgressBar.GONE);
                Toast.makeText(AdminDashboardActivity.this,
                        "Failed to load users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteUser(int userId) {
        new AlertDialog.Builder(this)
                .setTitle("Delete User")
                .setMessage("Are you sure you want to delete this user?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    for (UserModel user : userList) {
                        if (user.getId() == userId) {
                            usersRef.child(String.valueOf(userId)).removeValue()
                                    .addOnSuccessListener(aVoid -> Toast.makeText(this, "User deleted!", Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e -> Toast.makeText(this, "Failed to delete user!", Toast.LENGTH_SHORT).show());
                            break;
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
