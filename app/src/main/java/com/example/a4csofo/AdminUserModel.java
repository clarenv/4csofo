package com.example.a4csofo;

public class AdminUserModel {
    private String uid;
    private String name;
    private String email;
    private String role;
    private String password;
    private String ordersSummary;
    private String paymentHistory;
    private String address;
    private String phone;
    private String profileImage;
    private String registrationDate;

    // ONLY online field (no active field)
    private Boolean online;  // Use Boolean for Firebase compatibility

    // -------------------- No-argument constructor --------------------
    public AdminUserModel() {
        this.uid = "";
        this.name = "";
        this.email = "";
        this.role = "";
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.address = "No address";
        this.online = false;  // Default offline
        this.phone = "";
        this.profileImage = "";
        this.registrationDate = "";
    }

    // -------------------- Simple constructor --------------------
    public AdminUserModel(String uid, String name, String email, String role) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.role = role;
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.address = "No address";
        this.online = false;
        this.phone = "";
        this.profileImage = "";
        this.registrationDate = "";
    }

    // -------------------- Full constructor --------------------
    public AdminUserModel(String uid, String name, String email, String phone, String address,
                          String role, String registrationDate, Boolean online) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.address = address;
        this.role = role;
        this.registrationDate = registrationDate;
        this.online = online != null ? online : false;
        this.password = "";
        this.ordersSummary = "No orders";
        this.paymentHistory = "No payment history";
        this.profileImage = "";
    }

    // -------------------- GETTERS --------------------
    public String getUid() { return uid != null ? uid : ""; }
    public String getName() { return name != null ? name : ""; }
    public String getEmail() { return email != null ? email : ""; }
    public String getRole() { return role != null ? role : ""; }
    public String getPassword() { return password != null ? password : ""; }
    public String getOrdersSummary() { return ordersSummary != null ? ordersSummary : ""; }
    public String getPaymentHistory() { return paymentHistory != null ? paymentHistory : ""; }
    public String getAddress() { return address != null ? address : ""; }
    public String getPhone() { return phone != null ? phone : ""; }
    public String getProfileImage() { return profileImage != null ? profileImage : ""; }
    public String getRegistrationDate() { return registrationDate != null ? registrationDate : ""; }

    // IMPORTANT: One getter for online only
    public Boolean getOnline() {
        return online != null ? online : false;
    }

    // -------------------- SETTERS --------------------
    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setRole(String role) { this.role = role; }
    public void setPassword(String password) { this.password = password; }
    public void setOrdersSummary(String ordersSummary) { this.ordersSummary = ordersSummary; }
    public void setPaymentHistory(String paymentHistory) { this.paymentHistory = paymentHistory; }
    public void setAddress(String address) { this.address = address; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setProfileImage(String profileImage) { this.profileImage = profileImage; }
    public void setRegistrationDate(String registrationDate) { this.registrationDate = registrationDate; }
    public void setOnline(Boolean online) { this.online = online; }

    // -------------------- HELPER METHODS --------------------

    // Use this in adapters instead of isOnline()
    public boolean isUserOnline() {
        return getOnline();
    }

    // FIXED: Add the missing isOnline() method
    public boolean isOnline() {
        return getOnline();
    }

    // Get formatted status text
    public String getStatusText() {
        return isUserOnline() ? "Online" : "Offline";
    }

    // Check if user has phone number
    public boolean hasPhone() {
        return phone != null && !phone.trim().isEmpty() && !phone.equals("No phone");
    }

    // Check if user has address
    public boolean hasAddress() {
        return address != null && !address.trim().isEmpty() && !address.equals("No address");
    }

    // Get initials for profile picture
    public String getInitials() {
        if (name == null || name.trim().isEmpty()) {
            return "U";
        }

        String[] names = name.trim().split("\\s+");
        if (names.length == 1) {
            return names[0].substring(0, 1).toUpperCase();
        } else {
            return (names[0].substring(0, 1) + names[names.length - 1].substring(0, 1)).toUpperCase();
        }
    }

    // Get user type (customer, staff, admin)
    public String getUserType() {
        if (role == null || role.trim().isEmpty()) {
            return "Customer";
        }
        return role.substring(0, 1).toUpperCase() + role.substring(1).toLowerCase();
    }

    // Check if user is staff or admin
    public boolean isStaff() {
        return role != null && (role.equalsIgnoreCase("staff") || role.equalsIgnoreCase("admin"));
    }

    // Get formatted user info for display
    public String getFormattedInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Name: ").append(name != null ? name : "N/A").append("\n");
        info.append("Email: ").append(email != null ? email : "N/A").append("\n");
        if (hasPhone()) {
            info.append("Phone: ").append(phone).append("\n");
        }
        if (hasAddress()) {
            info.append("Address: ").append(address).append("\n");
        }
        info.append("Role: ").append(getUserType()).append("\n");
        info.append("Status: ").append(getStatusText());

        return info.toString();
    }

    // Validate required fields
    public boolean isValid() {
        return uid != null && !uid.isEmpty() &&
                name != null && !name.isEmpty() &&
                email != null && !email.isEmpty();
    }

    // Check if user matches search query
    public boolean matchesSearch(String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String lowerQuery = query.toLowerCase();
        return (name != null && name.toLowerCase().contains(lowerQuery)) ||
                (email != null && email.toLowerCase().contains(lowerQuery)) ||
                (phone != null && phone.toLowerCase().contains(lowerQuery)) ||
                (address != null && address.toLowerCase().contains(lowerQuery)) ||
                (role != null && role.toLowerCase().contains(lowerQuery));
    }

    // Get user's first name
    public String getFirstName() {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        String[] names = name.trim().split("\\s+");
        return names[0];
    }
}