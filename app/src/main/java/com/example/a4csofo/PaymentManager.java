package com.example.a4csofo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class PaymentManager {

    // Payment limits
    private static final int MAX_COD_ORDERS = 2;
    private static final double MAX_COD_VALUE = 5000.00;
    private static final int MAX_GCASH_ORDERS = 5;
    private static final double MAX_GCASH_VALUE = 10000.00;
    private static final int MAX_PICKUP_ORDERS = 3;
    private static final double MAX_PICKUP_SINGLE_ORDER = 3000.00;

    private FirebaseAuth auth;
    private DatabaseReference ordersRef;
    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private Geocoder geocoder;

    private static final String TAG = "PaymentManager";
    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    // Barangays list
    private final List<String> staCruzBarangays = Arrays.asList(
            "BABAYAN", "BAGUMBAYAN", "BUBUKAL", "CALIOS", "GATID",
            "ILAYANG BUKAL", "ILAYANG PALSABANGON", "ILAYANG PULONG BATO",
            "IPAG", "KANLURANG BUKAL", "LABUIN", "LAGUNA", "MALINAO",
            "PATIM", "SAN JOSE", "SANTIAGO", "SANTO ANGEL CENTRAL",
            "SANTO ANGEL NORTE", "SANTO ANGEL SUR", "TALANGAN",
            "UGONG", "POBLACION UNO", "POBLACION DOS", "POBLACION TRES",
            "POBLACION CUATRO"
    );

    public interface PaymentCallback {
        void onAllowed();
        void onBlocked(String reason);
    }

    public interface LocationCallback {
        void onLocationSuccess(String fullAddress);
        void onLocationError(String reason);
    }

    public interface AddressValidationCallback {
        void onValidAddress(String fullAddress);
        void onInvalidAddress(String reason);
    }

    public PaymentManager(Context context, FirebaseAuth auth) {
        this.context = context;
        this.auth = auth;
        this.ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        try {
            this.geocoder = new Geocoder(context, Locale.getDefault());
        } catch (Exception e) {
            Log.e(TAG, "Geocoder init failed: " + e.getMessage());
        }
    }

    // ====================== GET CURRENT LOCATION ======================
    public void getCurrentLocation(final LocationCallback callback) {
        // Check location permission
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            if (context instanceof Activity) {
                ActivityCompat.requestPermissions((Activity) context,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        LOCATION_PERMISSION_REQUEST);
            }
            callback.onLocationError("Location permission required");
            return;
        }

        // Get current location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();

                            // For Sta. Cruz, Laguna delivery only - use simplified address
                            String fullAddress = "Current Location (GPS: " +
                                    String.format("%.6f", lat) + ", " +
                                    String.format("%.6f", lng) + "), Sta. Cruz, Laguna";
                            callback.onLocationSuccess(fullAddress);
                        } else {
                            callback.onLocationError("Unable to get current location. Please use manual input.");
                        }
                    }
                });
    }

    // ====================== GET SAVED ADDRESS ======================
    public void getSavedAddress(final LocationCallback callback) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            callback.onLocationError("Please login first");
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid()).child("savedAddress");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String savedAddress = snapshot.getValue(String.class);
                    if (savedAddress != null && !savedAddress.isEmpty()) {
                        // Add Sta. Cruz, Laguna to the address
                        if (!savedAddress.toLowerCase().contains("sta. cruz") &&
                                !savedAddress.toLowerCase().contains("laguna")) {
                            savedAddress += ", Sta. Cruz, Laguna";
                        }
                        callback.onLocationSuccess(savedAddress);
                    } else {
                        callback.onLocationError("No saved address found");
                    }
                } else {
                    callback.onLocationError("No saved address found");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                callback.onLocationError("Error loading saved address");
            }
        });
    }

    // ====================== VALIDATE MANUAL ADDRESS ======================
    public void validateManualAddress(String street, String barangay, AddressValidationCallback callback) {
        if (street == null || street.trim().isEmpty()) {
            callback.onInvalidAddress("Please enter street details");
            return;
        }

        if (barangay == null || barangay.isEmpty()) {
            callback.onInvalidAddress("Please select barangay");
            return;
        }

        // Check if barangay is in Sta. Cruz list
        if (staCruzBarangays.contains(barangay.toUpperCase())) {
            String fullAddress = street + ", " + barangay + ", Sta. Cruz, Laguna";
            callback.onValidAddress(fullAddress);
        } else {
            callback.onInvalidAddress("Please select a valid barangay in Sta. Cruz, Laguna");
        }
    }

    // ====================== COD CHECK ======================
    public void checkCOD(double currentOrderTotal, String deliveryAddress, PaymentCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onBlocked("Please login first");
            return;
        }

        // Simple address validation for Sta. Cruz, Laguna
        if (!isValidStaCruzAddress(deliveryAddress)) {
            callback.onBlocked("Delivery is only available in Sta. Cruz, Laguna");
            return;
        }

        // Check COD limits
        checkCODLimits(currentOrderTotal, user.getUid(), callback);
    }

    private void checkCODLimits(double currentOrderTotal, String userId, PaymentCallback callback) {
        String today = getTodayDate();
        ordersRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int codCount = 0;
                        double codTotal = 0;

                        for (DataSnapshot orderSnap : snapshot.getChildren()) {
                            try {
                                String paymentMethod = getPaymentMethod(orderSnap);
                                double orderTotal = getOrderTotal(orderSnap);
                                String orderDate = getOrderDate(orderSnap);

                                if (isCOD(paymentMethod) && orderDate.equals(today)) {
                                    codCount++;
                                    codTotal += orderTotal;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing COD order", e);
                            }
                        }

                        // Check limits
                        if (codCount >= MAX_COD_ORDERS) {
                            callback.onBlocked("Max " + MAX_COD_ORDERS + " COD orders/day\nUsed: " + codCount + "/" + MAX_COD_ORDERS);
                            return;
                        }

                        if ((codTotal + currentOrderTotal) > MAX_COD_VALUE) {
                            callback.onBlocked("COD limit ₱" + String.format("%.2f", MAX_COD_VALUE) + "/day\nUsed: ₱" + String.format("%.2f", codTotal));
                            return;
                        }

                        callback.onAllowed();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onBlocked("Error checking COD limits");
                    }
                });
    }

    // ====================== GCASH CHECK ======================
    public void checkGCash(double currentOrderTotal, String deliveryAddress, PaymentCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onBlocked("Please login first");
            return;
        }

        // Address validation for delivery
        if (deliveryAddress != null && !deliveryAddress.isEmpty()) {
            if (!isValidStaCruzAddress(deliveryAddress)) {
                callback.onBlocked("Delivery is only available in Sta. Cruz, Laguna");
                return;
            }
        }

        // Check order total limit
        if (currentOrderTotal > MAX_PICKUP_SINGLE_ORDER) {
            callback.onBlocked("Max single order: ₱" + String.format("%.2f", MAX_PICKUP_SINGLE_ORDER));
            return;
        }

        // Check GCash limits
        checkGCashLimits(currentOrderTotal, user.getUid(), callback);
    }

    private void checkGCashLimits(double currentOrderTotal, String userId, PaymentCallback callback) {
        String today = getTodayDate();
        ordersRef.orderByChild("userId").equalTo(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int gcashCount = 0;
                        double gcashTotal = 0;

                        for (DataSnapshot orderSnap : snapshot.getChildren()) {
                            try {
                                String paymentMethod = getPaymentMethod(orderSnap);
                                double orderTotal = getOrderTotal(orderSnap);
                                String orderDate = getOrderDate(orderSnap);

                                if (isGCash(paymentMethod) && orderDate.equals(today)) {
                                    gcashCount++;
                                    gcashTotal += orderTotal;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing GCash order", e);
                            }
                        }

                        // Check limits
                        if (gcashCount >= MAX_GCASH_ORDERS) {
                            callback.onBlocked("Max " + MAX_GCASH_ORDERS + " GCash orders/day\nUsed: " + gcashCount + "/" + MAX_GCASH_ORDERS);
                            return;
                        }

                        if ((gcashTotal + currentOrderTotal) > MAX_GCASH_VALUE) {
                            callback.onBlocked("GCash limit ₱" + String.format("%.2f", MAX_GCASH_VALUE) + "/day\nUsed: ₱" + String.format("%.2f", gcashTotal));
                            return;
                        }

                        callback.onAllowed();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onBlocked("Error checking GCash limits");
                    }
                });
    }

    // ====================== GCASH CHECK WITHOUT ADDRESS ======================
    public void checkGCash(double currentOrderTotal, PaymentCallback callback) {
        // For pickup orders
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onBlocked("Please login first");
            return;
        }

        // Check order total limit
        if (currentOrderTotal > MAX_PICKUP_SINGLE_ORDER) {
            callback.onBlocked("Max single order: ₱" + String.format("%.2f", MAX_PICKUP_SINGLE_ORDER));
            return;
        }

        checkGCashLimits(currentOrderTotal, user.getUid(), callback);
    }

    // ====================== PICKUP CHECK ======================
    public void checkPickup(double currentOrderTotal, PaymentCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onBlocked("Please login first");
            return;
        }

        String today = getTodayDate();
        ordersRef.orderByChild("userId").equalTo(user.getUid())
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        int pickupCount = 0;

                        for (DataSnapshot orderSnap : snapshot.getChildren()) {
                            try {
                                String orderType = getOrderType(orderSnap);
                                String orderDate = getOrderDate(orderSnap);

                                if (isPickup(orderType) && orderDate.equals(today)) {
                                    pickupCount++;
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing Pickup order", e);
                            }
                        }

                        // Check limits
                        if (pickupCount >= MAX_PICKUP_ORDERS) {
                            callback.onBlocked("Max " + MAX_PICKUP_ORDERS + " Pickup orders/day\nUsed: " + pickupCount + "/" + MAX_PICKUP_ORDERS);
                            return;
                        }

                        if (currentOrderTotal > MAX_PICKUP_SINGLE_ORDER) {
                            callback.onBlocked("Max pickup order: ₱" + String.format("%.2f", MAX_PICKUP_SINGLE_ORDER));
                            return;
                        }

                        callback.onAllowed();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        callback.onBlocked("Error checking Pickup limits");
                    }
                });
    }

    // ====================== HELPER METHODS ======================
    private boolean isValidStaCruzAddress(String address) {
        if (address == null || address.trim().isEmpty()) {
            return false;
        }

        String addressLower = address.toLowerCase();

        // Check for Sta. Cruz or Santa Cruz variations
        boolean hasStaCruz = addressLower.contains("sta. cruz") || addressLower.contains("santa cruz");
        boolean hasLaguna = addressLower.contains("laguna");

        return hasStaCruz && hasLaguna;
    }

    private String getTodayDate() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    private String getPaymentMethod(DataSnapshot snapshot) {
        if (snapshot.hasChild("payment_method")) {
            return snapshot.child("payment_method").getValue(String.class);
        }
        return "";
    }

    private String getOrderType(DataSnapshot snapshot) {
        if (snapshot.hasChild("orderType")) {
            return snapshot.child("orderType").getValue(String.class);
        }
        return "";
    }

    private double getOrderTotal(DataSnapshot snapshot) {
        try {
            if (snapshot.hasChild("total_price")) {
                Object totalObj = snapshot.child("total_price").getValue();
                if (totalObj instanceof Double) return (Double) totalObj;
                if (totalObj instanceof Long) return ((Long) totalObj).doubleValue();
                if (totalObj instanceof Integer) return ((Integer) totalObj).doubleValue();
                if (totalObj instanceof String) {
                    String str = (String) totalObj;
                    return Double.parseDouble(str.replace("₱", "").replace(",", "").trim());
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing order total", e);
        }
        return 0;
    }

    private String getOrderDate(DataSnapshot snapshot) {
        try {
            if (snapshot.hasChild("orderDate")) {
                Object dateObj = snapshot.child("orderDate").getValue();
                if (dateObj instanceof Long) {
                    long timestamp = (Long) dateObj;
                    return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            .format(new Date(timestamp));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing order date", e);
        }
        return "";
    }

    private boolean isCOD(String paymentMethod) {
        if (paymentMethod == null) return false;
        String lower = paymentMethod.toLowerCase();
        return lower.contains("cash") || lower.contains("cod");
    }

    private boolean isGCash(String paymentMethod) {
        if (paymentMethod == null) return false;
        return paymentMethod.toLowerCase().contains("gcash");
    }

    private boolean isPickup(String orderType) {
        if (orderType == null) return false;
        return orderType.toLowerCase().contains("pickup");
    }
}