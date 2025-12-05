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
import java.util.HashMap;
import java.util.Map;

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

        // ---------------- Bottom Navigation ----------------
        BottomNavigationView bottomNavigation = findViewById(R.id.bottomNavigation);
        bottomNavigation.setSelectedItemId(R.id.nav_cart);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_home) {
                startActivity(new Intent(CartActivity.this, MainActivity.class));
                overridePendingTransition(0, 0);
            } else if (itemId == R.id.nav_orders) {
                startActivity(new Intent(CartActivity.this, OrdersActivity.class));
                overridePendingTransition(0, 0);
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(CartActivity.this, ProfileActivity.class));
                overridePendingTransition(0, 0);
            }
            // Return true to indicate item selection handled
            return true;
        });

        // ---------------- Firebase Authentication ----------------
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // ---------------- Initialize Cart ----------------
        cartItems = new ArrayList<>();
        cartKeys = new ArrayList<>();
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, cartItems);
        listViewCart.setAdapter(adapter);

        cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        // Load and merge cart items
        cartRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartItems.clear();
                cartKeys.clear();

                // Merge items by name
                Map<String, Integer> itemQuantityMap = new HashMap<>();
                Map<String, Double> itemPriceMap = new HashMap<>();
                Map<String, String> itemKeysMap = new HashMap<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    MainActivity.CartFoodItem food = data.getValue(MainActivity.CartFoodItem.class);
                    if (food != null) {
                        // Merge quantity if item exists
                        itemQuantityMap.put(food.name,
                                itemQuantityMap.getOrDefault(food.name, 0) + food.quantity);
                        if (!itemPriceMap.containsKey(food.name)) {
                            itemPriceMap.put(food.name, food.price);
                            itemKeysMap.put(food.name, data.getKey());
                        }
                    }
                }

                // Populate cartItems and cartKeys
                for (String name : itemQuantityMap.keySet()) {
                    int qty = itemQuantityMap.get(name);
                    double price = itemPriceMap.get(name);
                    cartItems.add(name + " x" + qty + " - ₱" + (price * qty));
                    cartKeys.add(itemKeysMap.get(name));
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

        // ---------------- Remove Item on Long Click ----------------
        listViewCart.setOnItemLongClickListener((parent, view, position, id) -> {
            String itemName = cartItems.get(position);
            String key = cartKeys.get(position);

            new AlertDialog.Builder(CartActivity.this)
                    .setTitle("Remove Item")
                    .setMessage("Do you want to remove " + itemName + " from your cart?")
                    .setPositiveButton("Yes", (dialog, which) -> cartRef.child(key).removeValue()
                            .addOnSuccessListener(aVoid ->
                                    Toast.makeText(CartActivity.this, itemName + " removed from cart", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e ->
                                    Toast.makeText(CartActivity.this, "Failed to remove item: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                    .setNegativeButton("No", null)
                    .show();

            return true;
        });

        // ---------------- Proceed to Checkout ----------------
        btnProceedCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(this, "Your cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(CartActivity.this, CheckoutActivity.class));
        });
    }
}
