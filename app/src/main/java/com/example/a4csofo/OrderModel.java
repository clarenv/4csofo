package com.example.a4csofo;

import java.util.List;

public class OrderModel {

    // USE THE OLD FIELD NAMES WITH UNDERSCORES
    private String orderKey;
    private String userId;
    private String customer_name;      // with underscore
    private List<String> items;
    private Double total_price;        // with underscore
    private String payment_method;     // with underscore
    private String status;
    private String orderType;
    private Long orderDate;

    private String deliveryLocation;
    private String pickupBranch;
    private String pickupTime;
    private String gcashReferenceNumber;   // long name
    private String gcashProofDownloadUrl;  // long name
    private Double deliveryLat;
    private Double deliveryLng;

    // For customer name mapping
    private String customerUid;  // Added this field

    // Empty constructor (required by Firebase)
    public OrderModel() {
    }

    // Constructor
    public OrderModel(String userId, String customer_name, List<String> items,
                      double total_price, String payment_method, String status,
                      String orderType, long orderDate) {
        this.userId = userId;
        this.customer_name = customer_name;
        this.items = items;
        this.total_price = total_price;
        this.payment_method = payment_method;
        this.status = status;
        this.orderType = orderType;
        this.orderDate = orderDate;
        this.customerUid = userId; // Set customerUid as well
    }

    // ===== GETTERS =====
    public String getOrderKey() { return orderKey; }

    // For backward compatibility - use both userId and customerUid
    public String getUserId() { return userId != null ? userId : ""; }
    public String getCustomerUid() {
        if (customerUid != null && !customerUid.isEmpty()) {
            return customerUid;
        }
        // Fallback to userId
        return userId != null ? userId : "";
    }

    public String getCustomerName() {
        return customer_name != null ? customer_name : "Customer";
    }

    public List<String> getItems() { return items; }

    public Double getTotal_price() {
        return total_price != null ? total_price : 0.0;
    }

    // For compatibility with other code
    public Double getTotal() {
        return getTotal_price();
    }

    public String getPayment_method() {
        return payment_method != null ? payment_method : "Cash";
    }

    public String getStatus() {
        return status != null ? status : "Pending";
    }

    public String getOrderType() {
        return orderType != null ? orderType : "delivery";
    }

    public Long getOrderDate() {
        return orderDate != null ? orderDate : System.currentTimeMillis();
    }

    public String getDeliveryLocation() {
        return deliveryLocation != null ? deliveryLocation : "";
    }

    public String getPickupBranch() {
        return pickupBranch != null ? pickupBranch : "";
    }

    public String getPickupTime() {
        return pickupTime != null ? pickupTime : "";
    }

    public String getGcashReferenceNumber() {
        return gcashReferenceNumber != null ? gcashReferenceNumber : "";
    }

    public String getGcashProofDownloadUrl() {
        return gcashProofDownloadUrl != null ? gcashProofDownloadUrl : "";
    }

    public Double getDeliveryLat() {
        return deliveryLat != null ? deliveryLat : 0.0;
    }

    public Double getDeliveryLng() {
        return deliveryLng != null ? deliveryLng : 0.0;
    }

    // ===== SETTERS =====
    public void setOrderKey(String orderKey) {
        this.orderKey = orderKey;
    }

    public void setUserId(String userId) {
        this.userId = userId;
        // Also set customerUid if not already set
        if (this.customerUid == null || this.customerUid.isEmpty()) {
            this.customerUid = userId;
        }
    }

    public void setCustomerUid(String customerUid) {
        this.customerUid = customerUid;
        // Also set userId for backward compatibility
        if (this.userId == null || this.userId.isEmpty()) {
            this.userId = customerUid;
        }
    }

    public void setCustomerName(String customer_name) {
        this.customer_name = customer_name;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public void setTotal_price(Double total_price) {
        this.total_price = total_price;
    }

    // For compatibility
    public void setTotal(Double total) {
        setTotal_price(total);
    }

    public void setPayment_method(String payment_method) {
        this.payment_method = payment_method;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setOrderType(String orderType) {
        this.orderType = orderType;
    }

    public void setOrderDate(Long orderDate) {
        this.orderDate = orderDate;
    }

    public void setDeliveryLocation(String deliveryLocation) {
        this.deliveryLocation = deliveryLocation;
    }

    public void setPickupBranch(String pickupBranch) {
        this.pickupBranch = pickupBranch;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public void setGcashReferenceNumber(String gcashReferenceNumber) {
        this.gcashReferenceNumber = gcashReferenceNumber;
    }

    public void setGcashProofDownloadUrl(String gcashProofDownloadUrl) {
        this.gcashProofDownloadUrl = gcashProofDownloadUrl;
    }

    public void setDeliveryLat(Double deliveryLat) {
        this.deliveryLat = deliveryLat;
    }

    public void setDeliveryLng(Double deliveryLng) {
        this.deliveryLng = deliveryLng;
    }

    // ===== HELPER METHODS =====
    public boolean isPickupOrder() {
        return "pickup".equalsIgnoreCase(getOrderType());
    }

    public boolean isPickup() {
        return isPickupOrder();
    }

    public boolean isGcash() {
        return "gcash".equalsIgnoreCase(getPayment_method());
    }

    public boolean isCashOnDelivery() {
        String payment = getPayment_method().toLowerCase();
        return payment.contains("cash") || payment.contains("cod");
    }

    public boolean isDeliveryOrder() {
        return "delivery".equalsIgnoreCase(getOrderType());
    }

    public boolean hasDeliveryLocation() {
        return deliveryLat != null && deliveryLng != null &&
                deliveryLat != 0.0 && deliveryLng != 0.0;
    }

    public String getFormattedTotalForDisplay() {
        return "₱" + String.format("%.2f", getTotal_price());
    }

    public String getFormattedTotal() {
        return getFormattedTotalForDisplay();
    }

    public String getItemsAsStringForDisplay() {
        if (getItems() == null || getItems().isEmpty()) {
            return "No items";
        }
        return String.join(", ", getItems());
    }

    // Check if order is in a specific status
    public boolean isStatus(String statusToCheck) {
        return getStatus().equalsIgnoreCase(statusToCheck);
    }

    // Check if order can be updated
    public boolean canUpdateStatus() {
        String status = getStatus().toLowerCase();
        return !"completed".equals(status) && !"cancelled".equals(status);
    }

    // Get next status (for admin panel)
    public String getNextStatus() {
        String current = getStatus().toLowerCase();
        String orderType = getOrderType().toLowerCase();
        String paymentMethod = getPayment_method().toLowerCase();

        if ("pickup".equals(orderType)) {
            switch (current) {
                case "pending": return "Accepted";
                case "accepted": return "Preparing";
                case "preparing": return "Ready";
                case "ready": return "Completed";
                default: return null;
            }
        } else {
            if ("gcash".equals(paymentMethod)) {
                switch (current) {
                    case "pending": return "Verifying";
                    case "verifying": return "Preparing";
                    case "preparing": return "Delivering";
                    case "delivering": return "Completed";
                    default: return null;
                }
            } else {
                switch (current) {
                    case "pending": return "Accepted";
                    case "accepted": return "Preparing";
                    case "preparing": return "Delivering";
                    case "delivering": return "Completed";
                    default: return null;
                }
            }
        }
    }

    @Override
    public String toString() {
        return "Order #" + (orderKey != null && orderKey.length() > 8 ?
                orderKey.substring(0, 8) : orderKey != null ? orderKey : "???") +
                " - " + getCustomerName() +
                " - " + getFormattedTotal() +
                " - " + getStatus();
    }

}