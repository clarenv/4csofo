package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.HashMap;
import java.util.Map;

public class ClientProfileFragment extends Fragment {

    private EditText edtName, edtEmail, edtAddress, edtPhone;
    private Button btnSave, btnEdit, btnLogout;
    private boolean isEditing = false;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference usersRef;

    public ClientProfileFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_client_profile, container, false);

        // Initialize views
        edtName = view.findViewById(R.id.edtName);
        edtEmail = view.findViewById(R.id.edtEmail);
        edtAddress = view.findViewById(R.id.edtAddress);
        edtPhone = view.findViewById(R.id.edtPhone);

        btnSave = view.findViewById(R.id.btnSave);
        btnEdit = view.findViewById(R.id.btnEdit);
        btnLogout = view.findViewById(R.id.btnLogout);

        // Firebase
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        if (currentUser == null) {
            Toast.makeText(requireContext(), "No user logged in", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireActivity(), LoginActivity.class));
            getActivity().finish();
            return view;
        }

        // Load user data
        loadUserData();
        setEditingEnabled(false);

        // Edit / Cancel button
        btnEdit.setOnClickListener(v -> {
            isEditing = !isEditing;
            setEditingEnabled(isEditing);
            btnEdit.setText(isEditing ? "Cancel" : "Edit");
        });

        // Save button
        btnSave.setOnClickListener(v -> {
            saveUserData();
            setEditingEnabled(false);
            btnEdit.setText("Edit");
            isEditing = false;
        });

        // Logout button
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Toast.makeText(requireContext(), "Logged out successfully", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(requireActivity(), LoginActivity.class));
            getActivity().finish();
        });

        return view;
    }

    private void loadUserData() {
        String uid = currentUser.getUid();

        usersRef.child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    edtName.setText(snapshot.child("name").getValue(String.class));
                    edtEmail.setText(snapshot.child("email").getValue(String.class));
                    edtAddress.setText(snapshot.child("address").getValue(String.class));
                    edtPhone.setText(snapshot.child("phone").getValue(String.class));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void saveUserData() {
        String uid = currentUser.getUid();

        Map<String, Object> data = new HashMap<>();
        data.put("name", edtName.getText().toString().trim());
        data.put("email", edtEmail.getText().toString().trim());
        data.put("address", edtAddress.getText().toString().trim());
        data.put("phone", edtPhone.getText().toString().trim());

        usersRef.child(uid).updateChildren(data)
                .addOnSuccessListener(aVoid ->
                        Toast.makeText(requireContext(), "Profile updated successfully", Toast.LENGTH_SHORT).show()
                )
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
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
