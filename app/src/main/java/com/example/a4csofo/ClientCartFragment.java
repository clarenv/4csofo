package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ClientCartFragment extends Fragment {

    private LinearLayout containerCartItems;
    private LinearLayout emptyCartView;
    private Button btnProceedCheckout;

    private ArrayList<CartFoodItem> cartItems;
    private ArrayList<String> cartKeys;

    private FirebaseAuth auth;
    private DatabaseReference cartRef;

    public ClientCartFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        // Inflate fragment layout
        View view = inflater.inflate(R.layout.fragment_client_cart, container, false);

        // Initialize views
        containerCartItems = view.findViewById(R.id.containerCartItems);
        emptyCartView = view.findViewById(R.id.emptyCartView);
        btnProceedCheckout = view.findViewById(R.id.btnProceedCheckout);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            getActivity().finish();
            return view;
        }

        // Initialize cart lists
        cartItems = new ArrayList<>();
        cartKeys = new ArrayList<>();

        cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        // Load and merge cart items
        cartRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartItems.clear();
                cartKeys.clear();
                containerCartItems.removeAllViews();

                Map<String, Integer> itemQuantityMap = new HashMap<>();
                Map<String, Double> itemPriceMap = new HashMap<>();
                Map<String, String> itemKeysMap = new HashMap<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    CartFoodItem food = data.getValue(CartFoodItem.class);
                    if (food != null) {
                        itemQuantityMap.put(food.name,
                                itemQuantityMap.getOrDefault(food.name, 0) + food.quantity);
                        if (!itemPriceMap.containsKey(food.name)) {
                            itemPriceMap.put(food.name, food.price);
                            itemKeysMap.put(food.name, data.getKey());
                        }
                    }
                }

                if (itemQuantityMap.isEmpty()) {
                    emptyCartView.setVisibility(View.VISIBLE);
                } else {
                    emptyCartView.setVisibility(View.GONE);

                    for (String name : itemQuantityMap.keySet()) {
                        int qty = itemQuantityMap.get(name);
                        double price = itemPriceMap.get(name);
                        String key = itemKeysMap.get(name);

                        CartFoodItem item = new CartFoodItem();
                        item.name = name;
                        item.price = price;
                        item.quantity = qty;
                        cartItems.add(item);
                        cartKeys.add(key);

                        // Create item view dynamically
                        View itemView = LayoutInflater.from(requireContext())
                                .inflate(R.layout.cart_item_view, containerCartItems, false);
                        TextView tvName = itemView.findViewById(R.id.tvItemName);
                        TextView tvPrice = itemView.findViewById(R.id.tvItemPrice);

                        tvName.setText(name + " x" + qty);
                        tvPrice.setText("₱" + (price * qty));

                        // Handle long click to remove
                        itemView.setOnLongClickListener(v -> {
                            new AlertDialog.Builder(requireContext())
                                    .setTitle("Remove Item")
                                    .setMessage("Do you want to remove " + name + " from your cart?")
                                    .setPositiveButton("Yes", (dialog, which) -> {
                                        cartRef.child(key).removeValue()
                                                .addOnSuccessListener(aVoid ->
                                                        Toast.makeText(requireContext(), name + " removed from cart", Toast.LENGTH_SHORT).show())
                                                .addOnFailureListener(e ->
                                                        Toast.makeText(requireContext(), "Failed to remove item: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                                    })
                                    .setNegativeButton("No", null)
                                    .show();
                            return true;
                        });

                        containerCartItems.addView(itemView);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        // Proceed to checkout
        btnProceedCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(requireContext(), "Your cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(requireActivity(), CheckoutActivity.class));
        });

        return view;
    }

    // ---------------------
    // CartFoodItem Model
    // ---------------------
    public static class CartFoodItem {
        public String name;
        public double price;
        public int quantity;

        public CartFoodItem() { }
    }
}
