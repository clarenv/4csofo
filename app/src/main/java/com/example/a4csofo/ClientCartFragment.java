package com.example.a4csofo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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

    public ClientCartFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_client_cart, container, false);

        containerCartItems = view.findViewById(R.id.containerCartItems);
        emptyCartView = view.findViewById(R.id.emptyCartView);
        btnProceedCheckout = view.findViewById(R.id.btnProceedCheckout);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) getActivity().finish();
            return view;
        }

        cartItems = new ArrayList<>();
        cartKeys = new ArrayList<>();

        cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        // LOAD CART ITEMS
        cartRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                if (!isAdded()) return;

                cartItems.clear();
                cartKeys.clear();
                containerCartItems.removeAllViews();

                Map<String, Integer> qtyMap = new HashMap<>();
                Map<String, Double> priceMap = new HashMap<>();
                Map<String, String> imageMap = new HashMap<>();
                Map<String, String> keyMap = new HashMap<>();

                for (DataSnapshot data : snapshot.getChildren()) {
                    CartFoodItem food = data.getValue(CartFoodItem.class);
                    if (food != null) {

                        qtyMap.put(food.name, qtyMap.getOrDefault(food.name, 0) + food.quantity);

                        if (!priceMap.containsKey(food.name)) {
                            priceMap.put(food.name, food.price);
                            keyMap.put(food.name, data.getKey());
                            imageMap.put(food.name, food.base64Image);
                        }
                    }
                }

                if (qtyMap.isEmpty()) {
                    emptyCartView.setVisibility(View.VISIBLE);
                } else {
                    emptyCartView.setVisibility(View.GONE);

                    for (String name : qtyMap.keySet()) {

                        int qty = qtyMap.get(name);
                        double price = priceMap.get(name);
                        String key = keyMap.get(name);
                        String base64 = imageMap.get(name);

                        CartFoodItem item = new CartFoodItem(name, price, qty, base64);
                        cartItems.add(item);
                        cartKeys.add(key);

                        View itemView = LayoutInflater.from(getContext())
                                .inflate(R.layout.cart_item_view, containerCartItems, false);

                        ImageView imgFood = itemView.findViewById(R.id.imgItem);
                        TextView tvName = itemView.findViewById(R.id.tvItemName);
                        TextView tvPrice = itemView.findViewById(R.id.tvItemPrice);

                        tvName.setText(name + " x" + qty);
                        tvPrice.setText("₱" + String.format("%.2f", price * qty));

                        // DISPLAY BASE64 IMAGE
                        if (base64 != null && !base64.isEmpty()) {
                            Bitmap bmp = base64ToBitmap(base64);
                            if (bmp != null) imgFood.setImageBitmap(bmp);
                        }

                        // 👉 INSERTED QUANTITY UI CODE HERE
                        Button btnMinus = itemView.findViewById(R.id.btnMinus);
                        Button btnAdd = itemView.findViewById(R.id.btnAdd);
                        TextView tvQty = itemView.findViewById(R.id.tvQty);

                        tvQty.setText(String.valueOf(qty));

                        // ADD BUTTON
                        btnAdd.setOnClickListener(v -> {
                            int currentQty = Integer.parseInt(tvQty.getText().toString());
                            currentQty++;

                            tvQty.setText(String.valueOf(currentQty));
                            tvName.setText(name + " x" + currentQty);
                            tvPrice.setText("₱" + String.format("%.2f", price * currentQty));

                            cartRef.child(key).child("quantity").setValue(currentQty);
                        });

                        // MINUS BUTTON
                        btnMinus.setOnClickListener(v -> {
                            int currentQty = Integer.parseInt(tvQty.getText().toString());

                            if (currentQty > 1) {
                                currentQty--;

                                tvQty.setText(String.valueOf(currentQty));
                                tvName.setText(name + " x" + currentQty);
                                tvPrice.setText("₱" + String.format("%.2f", price * currentQty));

                                cartRef.child(key).child("quantity").setValue(currentQty);

                            } else {
                                new AlertDialog.Builder(requireActivity())
                                        .setTitle("Remove Item")
                                        .setMessage("Remove " + name + " from cart?")
                                        .setPositiveButton("Yes", (dialog, which) -> {
                                            cartRef.child(key).removeValue();
                                        })
                                        .setNegativeButton("No", null)
                                        .show();
                            }
                        });
                        // END OF INSERTED CODE

                        containerCartItems.addView(itemView);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                Toast.makeText(getContext(),
                        "Failed to load cart: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        btnProceedCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(getContext(), "Your cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }

            startActivity(new Intent(requireActivity(), CheckoutActivity.class));
        });

        return view;
    }

    private Bitmap base64ToBitmap(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    public static class CartFoodItem {
        public String name;
        public double price;
        public int quantity;
        public String base64Image;

        public CartFoodItem() {}

        public CartFoodItem(String name, double price, int quantity, String base64Image) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.base64Image = base64Image;
        }
    }
}
