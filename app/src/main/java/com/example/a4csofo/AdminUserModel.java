package com.example.a4csofo;

public class AdminUserModel {
    private String uid;
    private String name;
    private String email;
    private String role;
    private boolean active;
    private String password;
    private String ordersSummary;
    private String paymentHistory;
    private String address;
    private boolean online; // <-- new field

    // -------------------- No-argument constructor (required by Firebase) --------------------
    public AdminUserModel() {
        this.uid = "";
        this.name = "";
        this.email = "";
        this.role = "";
        this.active = true;
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.address = "No address";
        this.online = false;
    }

    // -------------------- Existing constructors --------------------
    public AdminUserModel(String uid, String name, String email, String role, boolean b) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.active = b;
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.address = "No address";
        this.online = false;
    }

    public AdminUserModel(String uid, String name, String email, String role) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.active = true;
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.address = "No address";
        this.online = false;
    }

    // -------------------- Getters --------------------
    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }
    public String getPassword() { return password; }
    public String getOrdersSummary() { return ordersSummary; }
    public String getPaymentHistory() { return paymentHistory; }
    public String getAddress() { return address; }
    public boolean isOnline() { return online; }

    // -------------------- Setters --------------------
    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }
    public void setActive(boolean active) { this.active = active; }
    public void setPassword(String password) { this.password = password; }
    public void setOrdersSummary(String ordersSummary) { this.ordersSummary = ordersSummary; }
    public void setPaymentHistory(String paymentHistory) { this.paymentHistory = paymentHistory; }
    public void setAddress(String address) { this.address = address; }
    public void setOnline(boolean online) { this.online = online; }
}
