package com.example.a4csofo;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.List;

public class OrderModel {

    private String orderKey;
    private String userId;
    private String customer_name;
    private List<String> items;
    private double total_price;
    private String payment_method;
    private String status;
    private String transaction_number;
    private String deliveryLocation; // format: "lat,lng"
    private String gcashProof;

    // PICKUP INFO
    private String orderType;      // delivery or pickup
    private String pickupTime;
    private String pickupBranch;

    // GCASH FIELDS
    private String gcashReferenceNumber;
    private String gcashProofDownloadUrl;

    // lat/lng extracted
    private double deliveryLat;
    private double deliveryLng;

    // Required empty constructor for Firebase
    public OrderModel() {}

    public OrderModel(String userId, String customer_name, List<String> items, double total_price,
                      String payment_method, String status, String deliveryLocation) {

        this.userId = userId;
        this.customer_name = customer_name;
        this.items = items;
        this.total_price = total_price;
        this.payment_method = payment_method;
        this.status = status;
        this.transaction_number = "N/A";

        this.orderType = "delivery";
        this.pickupTime = "";
        this.pickupBranch = "";

        this.gcashReferenceNumber = "";
        this.gcashProofDownloadUrl = "";
        this.gcashProof = "";

        this.deliveryLocation = deliveryLocation;

        // Extract lat/lng
        if (deliveryLocation != null && !deliveryLocation.isEmpty()) {
            try {
                String[] coords = deliveryLocation.split(",");
                if (coords.length == 2) {
                    this.deliveryLat = Double.parseDouble(coords[0].trim());
                    this.deliveryLng = Double.parseDouble(coords[1].trim());
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    // GETTERS
    public String getOrderKey() { return orderKey; }
    public String getUserId() { return userId; }
    public String getCustomerName() { return customer_name != null ? customer_name : "Unknown"; }
    public List<String> getItems() { return items; }
    public double getTotal_price() { return total_price; }
    public String getPayment_method() { return payment_method; }
    public String getStatus() { return status; }
    public String getTransactionNumber() { return transaction_number; }
    public String getDeliveryLocation() { return deliveryLocation != null ? deliveryLocation : "N/A"; }
    public double getDeliveryLat() { return deliveryLat; }
    public double getDeliveryLng() { return deliveryLng; }
    public String getGcashProof() { return gcashProof; }
    public String getGcashReferenceNumber() { return gcashReferenceNumber != null ? gcashReferenceNumber : ""; }
    public String getGcashProofDownloadUrl() { return gcashProofDownloadUrl != null ? gcashProofDownloadUrl : ""; }
    public String getOrderType() { return orderType; }
    public String getPickupTime() { return pickupTime; }
    public String getPickupBranch() { return pickupBranch; }

    // Extra getters for display
    public String getTotal() {
        return "₱" + String.format("%.2f", total_price);
    }

    public String getFormattedTotal() {
        return "₱" + String.format("%.2f", total_price);
    }

    public String getPaymentMethod() {
        return payment_method != null ? payment_method : "N/A";
    }

    public String getPaymentText() {
        return "Payment: " + getPaymentMethod();
    }

    public String getItemsAsString() {
        if (items == null || items.isEmpty()) return "No items";
        return String.join(", ", items);
    }

    // SETTERS
    public void setOrderKey(String orderKey) { this.orderKey = orderKey; }
    public void setUserId(String userId) { this.userId = userId; }
    public void setCustomerName(String customer_name) { this.customer_name = customer_name; }
    public void setItems(List<String> items) { this.items = items; }
    public void setTotal_price(double total_price) { this.total_price = total_price; }
    public void setPayment_method(String payment_method) { this.payment_method = payment_method; }
    public void setStatus(String status) { this.status = status; }
    public void setTransaction_number(String transaction_number) { this.transaction_number = transaction_number; }

    public void setDeliveryLocation(String deliveryLocation) {
        this.deliveryLocation = deliveryLocation;

        if (deliveryLocation != null && !deliveryLocation.isEmpty()) {
            try {
                String[] coords = deliveryLocation.split(",");
                if (coords.length == 2) {
                    this.deliveryLat = Double.parseDouble(coords[0].trim());
                    this.deliveryLng = Double.parseDouble(coords[1].trim());
                }
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
    }

    public void setGcashProof(String gcashProof) { this.gcashProof = gcashProof; }
    public void setOrderType(String orderType) { this.orderType = orderType; }
    public void setPickupTime(String pickupTime) { this.pickupTime = pickupTime; }
    public void setPickupBranch(String pickupBranch) { this.pickupBranch = pickupBranch; }
    public void setGcashReferenceNumber(String gcashReferenceNumber) { this.gcashReferenceNumber = gcashReferenceNumber; }
    public void setGcashProofDownloadUrl(String gcashProofDownloadUrl) { this.gcashProofDownloadUrl = gcashProofDownloadUrl; }

    // Firebase Status Update
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
