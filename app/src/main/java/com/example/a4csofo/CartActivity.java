package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;

public class CartActivity extends AppCompatActivity {

    private ListView listViewCart;
    private Button btnProceedCheckout;
    private ArrayList<String> cartItems;
    private ArrayList<String> cartKeys;

    private FirebaseAuth auth;
    private DatabaseReference cartRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cart);

        listViewCart = findViewById(R.id.listViewCart);
        btnProceedCheckout = findViewById(R.id.btnProceedCheckout);

        // 🔹 Initialize Bottom Navigation
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_cart); // highlight current tab

        bottomNavigation.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull android.view.MenuItem item) {
                int itemId = item.getItemId();

                if (itemId == R.id.nav_home) {
                    startActivity(new Intent(CartActivity.this, MainActivity.class));
                    overridePendingTransition(0, 0);
                    return true;

                } else if (itemId == R.id.nav_cart) {
                    // Already in CartActivity
                    return true;

                } else if (itemId == R.id.nav_orders) {
                    // 🆕 Navigate to OrdersActivity
                    startActivity(new Intent(CartActivity.this, OrdersActivity.class));
                    overridePendingTransition(0, 0);
                    return true;

                } else if (itemId == R.id.nav_profile) {
                    startActivity(new Intent(CartActivity.this, ProfileActivity.class));
                    overridePendingTransition(0, 0);
                    return true;
                }

                return false;
            }
        });

        // 🔹 Firebase Authentication
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 🔹 Load Cart Items
        cartItems = new ArrayList<>();
        cartKeys = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, cartItems);
        listViewCart.setAdapter(adapter);

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(currentUser.getUid());

        cartRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartItems.clear();
                cartKeys.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    AdminMenuItemsFragment.FoodItem food = data.getValue(AdminMenuItemsFragment.FoodItem.class);
                    if (food != null) {
                        cartItems.add(food.name + " - ₱" + food.price);
                        cartKeys.add(data.getKey());
                    }
                }
                adapter.notifyDataSetChanged();

                if (cartItems.isEmpty()) {
                    Toast.makeText(CartActivity.this, "Your cart is empty", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CartActivity.this, "Failed to load cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // 🔹 Long click to remove item
        listViewCart.setOnItemLongClickListener((parent, view, position, id) -> {
            String itemName = cartItems.get(position);
            String key = cartKeys.get(position);

            new AlertDialog.Builder(CartActivity.this)
                    .setTitle("Remove Item")
                    .setMessage("Do you want to remove " + itemName + " from your cart?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        cartRef.child(key).removeValue()
                                .addOnSuccessListener(aVoid -> Toast.makeText(CartActivity.this,
                                        itemName + " removed from cart", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(CartActivity.this,
                                        "Failed to remove item: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("No", null)
                    .show();

            return true;
        });

        // 🔹 Proceed to checkout
        btnProceedCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(CartActivity.this, CheckoutActivity.class);
            startActivity(intent);
        });
    }
}
