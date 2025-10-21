package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class CheckoutActivity extends AppCompatActivity {

    private LinearLayout cartContainer;
    private TextView txtSubtotal, txtVat, txtDelivery, txtTotal;
    private RadioGroup paymentGroup;
    private Button btnConfirm;

    private ArrayList<CartItem> cartList = new ArrayList<>();
    private double subtotal = 0, vat = 0, deliveryFee = 30, total = 0;

    private FirebaseAuth auth;
    private DatabaseReference cartRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        auth = FirebaseAuth.getInstance();

        cartContainer = findViewById(R.id.cartContainer);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtVat = findViewById(R.id.txtVat);
        txtDelivery = findViewById(R.id.txtDelivery);
        txtTotal = findViewById(R.id.txtTotal);
        paymentGroup = findViewById(R.id.paymentGroup);
        btnConfirm = findViewById(R.id.btnConfirm);

        loadCartFromFirebase();

        btnConfirm.setOnClickListener(v -> confirmOrder());
    }

    private void loadCartFromFirebase() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view your cart", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(currentUser.getUid());
        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartList.clear();
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    MenuItemsActivity.FoodItem food = itemSnapshot.getValue(MenuItemsActivity.FoodItem.class);
                    if (food != null) {
                        cartList.add(new CartItem(food.name, 1, food.price)); // quantity=1 by default
                    }
                }
                loadCart();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this, "Failed to load cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCart() {
        cartContainer.removeAllViews();
        subtotal = 0;

        LayoutInflater inflater = LayoutInflater.from(this);
        for (CartItem item : cartList) {
            View itemView = inflater.inflate(R.layout.item_cart_checkout, cartContainer, false);

            TextView txtName = itemView.findViewById(R.id.txtItemName);
            TextView txtQty = itemView.findViewById(R.id.txtItemQty);
            TextView txtPrice = itemView.findViewById(R.id.txtItemPrice);

            txtName.setText(item.getName());
            txtQty.setText("x" + item.getQuantity());
            txtPrice.setText("₱" + String.format("%.2f", item.getPrice() * item.getQuantity()));

            subtotal += item.getPrice() * item.getQuantity();
            cartContainer.addView(itemView);
        }

        vat = subtotal * 0.12; // VAT 12%
        total = subtotal + vat + deliveryFee;

        txtSubtotal.setText("₱" + String.format("%.2f", subtotal));
        txtVat.setText("₱" + String.format("%.2f", vat));
        txtDelivery.setText("₱" + String.format("%.2f", deliveryFee));
        txtTotal.setText("₱" + String.format("%.2f", total));
    }

    private void confirmOrder() {
        int selectedId = paymentGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selected = findViewById(selectedId);
        String paymentMethod = selected.getText().toString();

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        // Convert cart items to List<String>
        List<String> itemsList = new ArrayList<>();
        for (CartItem item : cartList) {
            itemsList.add(item.getName() + " x" + item.getQuantity());
        }

        // Create OrderModel object
        OrderModel order = new OrderModel(
                currentUser.getUid(),   // userId
                "Customer Name",        // customer_name (optional, can be dynamic)
                itemsList,              // list of items
                total,                  // total_price
                paymentMethod,          // payment method
                "Pending",              // status
                ""                      // transaction_number
        );

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        ordersRef.push().setValue(order)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(CheckoutActivity.this, "Order placed successfully!", Toast.LENGTH_SHORT).show();
                    // Clear cart
                    cartRef.removeValue();
                    startActivity(new Intent(CheckoutActivity.this, OrdersActivity.class));
                    finish();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(CheckoutActivity.this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // Cart item model
    public static class CartItem {
        private String name;
        private int quantity;
        private double price;

        public CartItem() {}

        public CartItem(String name, int quantity, double price) {
            this.name = name;
            this.quantity = quantity;
            this.price = price;
        }

        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }

        public void setName(String name) { this.name = name; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public void setPrice(double price) { this.price = price; }
    }
}
