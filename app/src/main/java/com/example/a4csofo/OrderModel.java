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
    private String gcashProof;        // GCash proof of payment

    // Additional fields to match Firebase keys
    private String paymentText;
    private String gcashProofDownloadUrl;
    private String formattedTotal;
    private String pickupBranch;
    private String orderType;
    private String deliveryLocation;
    private Double deliveryLat;
    private Double deliveryLng;
    private String pickupTime;
    private String gcashReferenceNumber;
    private String itemsAsString;

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
    public String getGcashProof() { return gcashProof; }

    // Additional Firebase fields
    public String getPaymentText() { return paymentText; }
    public void setPaymentText(String paymentText) { this.paymentText = paymentText; }

    public String getGcashProofDownloadUrl() { return gcashProofDownloadUrl; }
    public void setGcashProofDownloadUrl(String gcashProofDownloadUrl) { this.gcashProofDownloadUrl = gcashProofDownloadUrl; }

    public String getFormattedTotal() { return formattedTotal; }
    public void setFormattedTotal(String formattedTotal) { this.formattedTotal = formattedTotal; }

    public String getPickupBranch() { return pickupBranch; }
    public void setPickupBranch(String pickupBranch) { this.pickupBranch = pickupBranch; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getDeliveryLocation() { return deliveryLocation; }
    public void setDeliveryLocation(String deliveryLocation) { this.deliveryLocation = deliveryLocation; }

    public Double getDeliveryLat() { return deliveryLat; }
    public void setDeliveryLat(Double deliveryLat) { this.deliveryLat = deliveryLat; }

    public Double getDeliveryLng() { return deliveryLng; }
    public void setDeliveryLng(Double deliveryLng) { this.deliveryLng = deliveryLng; }

    public String getPickupTime() { return pickupTime; }
    public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }

    public String getGcashReferenceNumber() { return gcashReferenceNumber; }
    public void setGcashReferenceNumber(String gcashReferenceNumber) { this.gcashReferenceNumber = gcashReferenceNumber; }

    public String getItemsAsString() {
        if (items == null || items.isEmpty()) return "No items";
        return String.join(", ", items);
    }
    public void setItemsAsString(String itemsAsString) { this.itemsAsString = itemsAsString; }

    // Adapter-friendly getters
    public String getCustomer_name() { return getCustomerName(); } // Legacy
    public String getTotal() { return getFormattedTotal(); }
    public String getPaymentMethod() { return payment_method != null ? payment_method : "N/A"; }

    // Setters
    public void setOrderKey(String orderKey) { this.orderKey = orderKey; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCustomerName(String customer_name) { this.customer_name = customer_name; }
    public void setItems(List<String> items) { this.items = items; }
    public void setTotal_price(double total_price) { this.total_price = total_price; }
    public void setPayment_method(String payment_method) { this.payment_method = payment_method; }
    public void setStatus(String status) { this.status = status; }
    public void setTransaction_number(String transaction_number) { this.transaction_number = transaction_number; }
    public void setGcashProof(String gcashProof) { this.gcashProof = gcashProof; }

    // Adapter-friendly setters (legacy)
    public void setCustomer_name(String customerName) { this.customer_name = customerName; }
    public void setTotal(String total) { /* no-op, keep for Adapter compatibility */ }
    public void setPaymentMethod(String paymentMethod) { this.payment_method = paymentMethod; }

    // Helper method: formatted total price
    public String getFormattedTotalPrice() {
        return "₱" + String.format("%.2f", total_price);
    }

    // Helper method: formatted payment text
    public String getFormattedPaymentText() {
        return "Payment: " + (payment_method != null ? payment_method : "N/A");
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
