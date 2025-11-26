package com.example.a4csofo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
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

    private String deliveryLocation = ""; // ✅ store address here

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

        // ✅ Show GCash QR when selected or show location input for COD
        paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = findViewById(checkedId);
            if (selected != null) {
                String paymentText = selected.getText().toString();
                if (paymentText.equalsIgnoreCase("GCash")) {
                    showGcashPopup();
                } else if (paymentText.equalsIgnoreCase("Cash on Delivery")) {
                    showDeliveryLocationDialog();
                }
            }
        });
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
                    AdminMenuItemsActivity.FoodItem food = itemSnapshot.getValue(AdminMenuItemsActivity.FoodItem.class);
                    if (food != null) {
                        cartList.add(new CartItem(food.name, 1, food.price));
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

        vat = subtotal * 0.12;
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

        if (paymentMethod.equalsIgnoreCase("Cash on Delivery") && deliveryLocation.isEmpty()) {
            Toast.makeText(this, "Please enter delivery location", Toast.LENGTH_SHORT).show();
            showDeliveryLocationDialog();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        List<String> itemsList = new ArrayList<>();
        for (CartItem item : cartList) {
            itemsList.add(item.getName() + " x" + item.getQuantity());
        }

        OrderModel order = new OrderModel(
                currentUser.getUid(),
                "Customer Name",
                itemsList,
                total,
                paymentMethod,
                "Pending",
                deliveryLocation // ✅ Include location if delivery
        );

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        ordersRef.push().setValue(order)
                .addOnSuccessListener(aVoid -> {
                    cartRef.removeValue(); // clear cart
                    showReceiptPopup(itemsList, total, paymentMethod);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(CheckoutActivity.this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ✅ Dialog to input delivery address
    private void showDeliveryLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Enter Delivery Location");

        final EditText input = new EditText(this);
        input.setHint("e.g. 123 Mabini St., Manila");
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("Save", (dialog, which) -> {
            String location = input.getText().toString().trim();
            if (location.isEmpty()) {
                Toast.makeText(this, "Location cannot be empty", Toast.LENGTH_SHORT).show();
            } else {
                deliveryLocation = location;
                Toast.makeText(this, "Delivery location saved!", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        builder.show();
    }

    // ✅ GCash QR Popup
    private void showGcashPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_gcash_qr, null);

        ImageView qrImage = dialogView.findViewById(R.id.qrImage);
        TextView txtInfo = dialogView.findViewById(R.id.txtInfo);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ✅ RECEIPT POPUP
    private void showReceiptPopup(List<String> items, double total, String paymentMethod) {
        double vat = total * 0.12 / 1.12;
        double subtotal = total - vat - 30; // assuming ₱30 delivery fee

        StringBuilder receiptBuilder = new StringBuilder();

        receiptBuilder.append("🧾  OFFICIAL RECEIPT\n\n");
        receiptBuilder.append("A4CSOFO FOOD SERVICES\n");
        receiptBuilder.append("123 Mabini St., Manila City\n");
        receiptBuilder.append("VAT Reg TIN: 123-456-789-000\n");
        receiptBuilder.append("Tel No: (02) 123-4567\n");
        receiptBuilder.append("--------------------------------------------\n");

        receiptBuilder.append("ITEMS\n");
        for (String item : items) {
            receiptBuilder.append("• ").append(item).append("\n");
        }

        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("Subtotal:        ₱%.2f\n", subtotal));
        receiptBuilder.append(String.format("VAT (12%%):       ₱%.2f\n", vat));
        receiptBuilder.append(String.format("Delivery Fee:    ₱%.2f\n", 30.00));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("TOTAL DUE:       ₱%.2f\n", total));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("Payment Method: " + paymentMethod + "\n");
        if (!deliveryLocation.isEmpty())
            receiptBuilder.append("Delivery Address: " + deliveryLocation + "\n");
        receiptBuilder.append("Cashier: SYSTEM AUTO\n");
        receiptBuilder.append("Date/Time: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n");
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("✅ THANK YOU FOR YOUR PURCHASE!\n");

        new AlertDialog.Builder(this)
                .setTitle("Order Confirmed")
                .setMessage(receiptBuilder.toString())
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    Intent intent = new Intent(CheckoutActivity.this, OrdersActivity.class);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    // ✅ CART ITEM MODEL
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
    }
}
