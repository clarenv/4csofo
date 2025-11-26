package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class AdminUsersFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EditText searchBox;
    private AdminUserAdapter userAdapter;
    private ArrayList<AdminUserModel> userList;
    private DatabaseReference usersRef;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_admin_users, container, false);

        recyclerView = view.findViewById(R.id.recyclerUsers);
        progressBar = view.findViewById(R.id.progressBar);
        searchBox = view.findViewById(R.id.editSearch);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        userList = new ArrayList<>();
        userAdapter = new AdminUserAdapter(userList, this::deleteUser);
        recyclerView.setAdapter(userAdapter);

        usersRef = FirebaseDatabase.getInstance().getReference("users");
        loadUsers();

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                userAdapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        return view;
    }

    private void loadUsers() {
        progressBar.setVisibility(ProgressBar.VISIBLE);
        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressBar.setVisibility(ProgressBar.GONE);
                userList.clear();
                for (DataSnapshot userSnap : snapshot.getChildren()) {
                    String uid = userSnap.getKey();
                    String name = userSnap.child("name").getValue(String.class);
                    String email = userSnap.child("email").getValue(String.class);
                    String role = userSnap.child("role").getValue(String.class);
                    if (uid != null) userList.add(new AdminUserModel(uid, name, email, role));
                }
                userAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(ProgressBar.GONE);
                Toast.makeText(getContext(), "Failed to load users: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void deleteUser(String userUid) {
        usersRef.child(userUid).removeValue()
                .addOnSuccessListener(e -> Toast.makeText(getContext(), "User deleted successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Failed to delete user", Toast.LENGTH_SHORT).show());
    }
}
