package com.example.a4csofo;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class OrderModel {

    private String orderKey;          // Firebase key
    private String userId;            // User ID ng nag-order
    private String customer_name;     // Pangalan ng customer
    private List<String> items;       // List ng items sa order
    private double total_price;
    private String payment_method;
    private String status;
    private String transaction_number;
    private String gcashProof;        // <-- Added for GCash proof of payment

    // Default constructor required for Firebase
    public OrderModel() {}

    // Constructor
    public OrderModel(String userId, String customer_name, List<String> items, double total_price,
                      String payment_method, String status, String transaction_number) {
        this.userId = userId;
        this.customer_name = customer_name;
        this.items = items;
        this.total_price = total_price;
        this.payment_method = payment_method;
        this.status = status;
        this.transaction_number = transaction_number;
    }

    // Getters
    public String getOrderKey() { return orderKey; }
    public String getUserId() { return userId; }
    public String getCustomerName() { return customer_name != null ? customer_name : "Unknown"; }
    public List<String> getItems() { return items; }
    public double getTotal_price() { return total_price; }
    public String getPayment_method() { return payment_method; }
    public String getStatus() { return status; }
    public String getTransaction_number() { return transaction_number; }
    public String getGcashProof() { return gcashProof; }  // <-- new getter

    // Legacy getter para sa Adapter compatibility
    public String getCustomer_name() { return getCustomerName(); }

    // Setters
    public void setOrderKey(String orderKey) { this.orderKey = orderKey; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCustomerName(String customer_name) { this.customer_name = customer_name; }
    public void setItems(List<String> items) { this.items = items; }
    public void setTotal_price(double total_price) { this.total_price = total_price; }
    public void setPayment_method(String payment_method) { this.payment_method = payment_method; }
    public void setStatus(String status) { this.status = status; }
    public void setTransaction_number(String transaction_number) { this.transaction_number = transaction_number; }
    public void setGcashProof(String gcashProof) { this.gcashProof = gcashProof; } // <-- new setter

    // Helper method: return items as comma-separated string
    public String getItemsAsString() {
        if (items == null || items.isEmpty()) return "No items";
        return String.join(", ", items);
    }

    // Helper method: formatted total price
    public String getFormattedTotal() {
        return "₱" + String.format("%.2f", total_price);
    }

    // Helper method: formatted payment text
    public String getPaymentText() {
        return "Payment: " + (payment_method != null ? payment_method : "N/A");
    }

    // Adapter-friendly methods
    public String getTotal() {
        return getFormattedTotal();
    }

    public String getPaymentMethod() {
        return payment_method != null ? payment_method : "N/A";
    }

    // Update order status in Firebase
    public void updateStatus(String newStatus) {
        if (orderKey == null || orderKey.isEmpty()) return;

        DatabaseReference ordersRef = FirebaseDatabase.getInstance()
                .getReference("orders")
                .child(orderKey);

        ordersRef.child("status").setValue(newStatus)
                .addOnSuccessListener(aVoid -> this.status = newStatus)
                .addOnFailureListener(e -> System.err.println("Failed to update status: " + e.getMessage()));
    }
}
