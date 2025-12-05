package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;

public class RegisterActivity extends AppCompatActivity {

    EditText name, email, phone, address, password, confirmPassword;
    Button btnRegister;
    TextView loginLink;
    FirebaseAuth auth;
    DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        name = findViewById(R.id.etName);
        email = findViewById(R.id.etEmail);
        phone = findViewById(R.id.etPhone);
        address = findViewById(R.id.etAddress);
        password = findViewById(R.id.etPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        loginLink = findViewById(R.id.loginLink);

        btnRegister.setOnClickListener(v -> registerUser());
        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
        });
    }

    private void registerUser() {
        String fullName = name.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String userPhone = phone.getText().toString().trim();
        String userAddress = address.getText().toString().trim();
        String userPassword = password.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();

        // -----------------------
        // VALIDATIONS
        // -----------------------
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(userEmail)
                || TextUtils.isEmpty(userPhone) || TextUtils.isEmpty(userAddress)
                || TextUtils.isEmpty(userPassword) || TextUtils.isEmpty(confirmPass)) {

            Toast.makeText(this, "⚠️ All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            Toast.makeText(this, "❌ Invalid email format.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.PHONE.matcher(userPhone).matches() || userPhone.length() < 10) {
            Toast.makeText(this, "❌ Invalid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userPassword.length() < 6) {
            Toast.makeText(this, "❌ Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!userPassword.equals(confirmPass)) {
            Toast.makeText(this, "❌ Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        // -----------------------
        // CREATE USER
        // -----------------------
        btnRegister.setEnabled(false);
        btnRegister.setText("Registering...");

        auth.createUserWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(task -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");

                    if (task.isSuccessful()) {

                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {

                            HashMap<String, Object> userData = new HashMap<>();
                            userData.put("name", fullName);
                            userData.put("email", userEmail);
                            userData.put("phone", userPhone);
                            userData.put("address", userAddress);
                            userData.put("role", "customer");

                            usersRef.child(user.getUid()).setValue(userData)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            user.sendEmailVerification();
                                            Toast.makeText(RegisterActivity.this,
                                                    "✅ Registration successful! Please verify your email.",
                                                    Toast.LENGTH_LONG).show();
                                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                            finish();
                                        } else {
                                            Toast.makeText(RegisterActivity.this,
                                                    "❌ Failed to save user info.",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }

                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "❌ Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
