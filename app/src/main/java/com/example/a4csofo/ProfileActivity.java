package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private EditText edtName, edtEmail, edtAddress, edtPhone;
    private Button btnSave, btnEdit, btnLogout;
    private boolean isEditing = false;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference usersRef;

    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtAddress = findViewById(R.id.edtAddress);
        edtPhone = findViewById(R.id.edtPhone);

        btnSave = findViewById(R.id.btnSave);
        btnEdit = findViewById(R.id.btnEdit);
        btnLogout = findViewById(R.id.btnLogout);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        if (currentUser == null) {
            Toast.makeText(this, "No user logged in", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
            return;
        }

        loadUserData();
        setEditingEnabled(false);

        btnEdit.setOnClickListener(v -> {
            isEditing = !isEditing;
            setEditingEnabled(isEditing);

            btnEdit.setText(isEditing ? "Cancel" : "Edit");
        });

        btnSave.setOnClickListener(v -> {
            saveUserData();
            setEditingEnabled(false);
            btnEdit.setText("Edit");
            isEditing = false;
        });

        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        });

        setupNavigation();
    }

    private void setupNavigation() {
        bottomNavigation.setSelectedItemId(R.id.nav_profile);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                startActivity(new Intent(ProfileActivity.this, MainActivity.class));
                return true;
            } else if (id == R.id.nav_cart) {
                startActivity(new Intent(ProfileActivity.this, CartActivity.class));
                return true;
            } else if (id == R.id.nav_orders) {
                startActivity(new Intent(ProfileActivity.this, OrdersActivity.class));
                return true;
            } else if (id == R.id.nav_profile) {
                return true;
            }
            return false;
        });
    }

    // -------------------------
    // LOAD USER DATA
    // -------------------------
    private void loadUserData() {
        String uid = currentUser.getUid();

        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    edtName.setText(snapshot.child("name").getValue(String.class));
                    edtEmail.setText(snapshot.child("email").getValue(String.class));
                    edtAddress.setText(snapshot.child("address").getValue(String.class));
                    edtPhone.setText(snapshot.child("phone").getValue(String.class));
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // -------------------------
    // SAVE USER DATA
    // -------------------------
    private void saveUserData() {
        String uid = currentUser.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("name", edtName.getText().toString().trim());
        data.put("email", edtEmail.getText().toString().trim());
        data.put("address", edtAddress.getText().toString().trim());
        data.put("phone", edtPhone.getText().toString().trim());

        usersRef.child(uid).updateChildren(data)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(ProfileActivity.this, "Profile updated successfully", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(ProfileActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    private void setEditingEnabled(boolean enabled) {
        edtName.setEnabled(enabled);
        edtEmail.setEnabled(enabled);
        edtAddress.setEnabled(enabled);
        edtPhone.setEnabled(enabled);
        btnSave.setEnabled(enabled);
    }
}
