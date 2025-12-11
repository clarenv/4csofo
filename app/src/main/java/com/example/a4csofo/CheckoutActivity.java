package com.example.a4csofo;

import android.Manifest;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.github.dhaval2404.imagepicker.ImagePicker;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;

public class CheckoutActivity extends AppCompatActivity {

    // UI Components
    private LinearLayout containerOrderItems;
    private TextView tvSubtotal, tvDeliveryFee, tvTotalAmount;
    private RadioGroup rgOrderType, rgPaymentMethod;
    private RadioButton rbPickup, rbDelivery, rbCash, rbGcash;
    private LinearLayout containerPickupDetails, containerDeliveryDetails, containerPaymentSection;
    private Button btnSelectPickupTime, btnUseCurrentLocation, btnTypeManual, btnUseSavedLocation;
    private EditText etDeliveryAddress, etGcashReference;
    private TextView tvLocationValidation, tvCodWarning, tvPickupTimeWarning;
    private LinearLayout containerGcashDetails;
    private Button btnUploadProof, btnViewProof;
    private ImageView ivProofPreview;
    private Button btnPlaceOrder;
    private ProgressDialog progressDialog;

    // Data
    private ArrayList<ClientCartFragment.CartFoodItem> cartItems;
    private ArrayList<String> cartKeys;
    private Map<String, List<ClientCartFragment.AddonSelection>> itemAddonsMap;
    private double subtotal = 0;
    private double deliveryFee = 30.00;
    private double totalAmount = 0;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final int IMAGE_PICKER_REQUEST = 200;

    // Firebase
    private FirebaseAuth auth;
    private FirebaseUser currentUser;
    private DatabaseReference cartRef;
    private DatabaseReference ordersRef;
    private DatabaseReference usersRef;

    // Selected values
    private String selectedOrderType = "delivery";
    private String selectedPaymentMethod = "Cash";
    private String selectedPickupTime = "";
    private String savedAddress = "";
    private byte[] gcashProofBytes = null;
    private String customerName = "";

    // Sta. Cruz, Laguna boundaries
    private static final double STA_CRUZ_MIN_LAT = 14.20;
    private static final double STA_CRUZ_MAX_LAT = 14.35;
    private static final double STA_CRUZ_MIN_LNG = 121.35;
    private static final double STA_CRUZ_MAX_LNG = 121.45;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);
        setTitle("Checkout - Sta. Cruz, Laguna");

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Initialize UI
        initViews();

        // Initialize ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);

        // Load user data
        loadUserData();

        // Load cart data
        loadCartData();

        // Setup listeners
        setupListeners();

        // Initial UI state
        setupInitialUIState();
    }

    private void initViews() {
        containerOrderItems = findViewById(R.id.containerOrderItems);
        tvSubtotal = findViewById(R.id.tvSubtotal);
        tvDeliveryFee = findViewById(R.id.tvDeliveryFee);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);

        // Order Type
        rgOrderType = findViewById(R.id.rgOrderType);
        rbPickup = findViewById(R.id.rbPickup);
        rbDelivery = findViewById(R.id.rbDelivery);
        containerPickupDetails = findViewById(R.id.containerPickupDetails);
        containerDeliveryDetails = findViewById(R.id.containerDeliveryDetails);

        // Payment Section Container
        containerPaymentSection = findViewById(R.id.containerPaymentSection);

        btnSelectPickupTime = findViewById(R.id.btnSelectPickupTime);
        btnUseCurrentLocation = findViewById(R.id.btnUseCurrentLocation);
        btnTypeManual = findViewById(R.id.btnTypeManual);
        btnUseSavedLocation = findViewById(R.id.btnUseSavedLocation);
        etDeliveryAddress = findViewById(R.id.etDeliveryAddress);
        tvLocationValidation = findViewById(R.id.tvLocationValidation);
        tvPickupTimeWarning = findViewById(R.id.tvPickupTimeWarning);

        // Payment Method
        rgPaymentMethod = findViewById(R.id.rgPaymentMethod);
        rbCash = findViewById(R.id.rbCash);
        rbGcash = findViewById(R.id.rbGcash);
        containerGcashDetails = findViewById(R.id.containerGcashDetails);
        tvCodWarning = findViewById(R.id.tvCodWarning);
        etGcashReference = findViewById(R.id.etGcashReference);
        btnUploadProof = findViewById(R.id.btnUploadProof);
        btnViewProof = findViewById(R.id.btnViewProof);
        ivProofPreview = findViewById(R.id.ivProofPreview);

        // Place Order Button
        btnPlaceOrder = findViewById(R.id.btnPlaceOrder);
    }
    private void setupInitialUIState() {
        // Hide sections by default
        containerPickupDetails.setVisibility(View.GONE);
        containerDeliveryDetails.setVisibility(View.GONE);
        containerPaymentSection.setVisibility(View.GONE);
        containerGcashDetails.setVisibility(View.GONE);

        // Disable place order button initially
        btnPlaceOrder.setEnabled(false);
        btnPlaceOrder.setBackgroundColor(getResources().getColor(android.R.color.darker_gray));

        // CHECK IF STORE IS CLOSED (8 PM onwards, before 8 AM)
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);

        boolean isStoreClosed = (currentHour >= 20 || currentHour < 8);

        if (isStoreClosed) {
            // STORE IS CLOSED - CANNOT PLACE ANY ORDER
            btnPlaceOrder.setText("STORE CLOSED (8PM-8AM)");
            btnPlaceOrder.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            btnPlaceOrder.setEnabled(false);

            // Show message
            String timeMessage = (currentHour >= 20) ?
                    "Store is closed (8:00 PM - 8:00 AM). Come back tomorrow." :
                    "Store opens at 8:00 AM.";

            Toast.makeText(this, timeMessage, Toast.LENGTH_LONG).show();
        }

        // Set default order type
        if (!isStoreClosed) {
            rbDelivery.setChecked(true);
            selectedOrderType = "delivery";
            containerDeliveryDetails.setVisibility(View.VISIBLE);
            deliveryFee = 30.00;
            updateTotals();
        }
    }
    private void loadUserData() {
        usersRef.child(currentUser.getUid()).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Get user's name
                    if (snapshot.child("name").exists()) {
                        customerName = snapshot.child("name").getValue(String.class);
                    } else if (snapshot.child("fullName").exists()) {
                        customerName = snapshot.child("fullName").getValue(String.class);
                    } else if (snapshot.child("username").exists()) {
                        customerName = snapshot.child("username").getValue(String.class);
                    } else {
                        customerName = currentUser.getEmail().split("@")[0];
                    }

                    // Get saved address
                    if (snapshot.child("address").exists()) {
                        savedAddress = snapshot.child("address").getValue(String.class);
                        if (savedAddress != null && !savedAddress.isEmpty()) {
                            btnUseSavedLocation.setVisibility(View.VISIBLE);
                        }
                    }
                } else {
                    customerName = currentUser.getEmail().split("@")[0];
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                customerName = currentUser.getEmail().split("@")[0];
            }
        });
    }

    private void loadCartData() {
        cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartItems = new ArrayList<>();
                cartKeys = new ArrayList<>();
                itemAddonsMap = new HashMap<>();
                subtotal = 0;

                if (!snapshot.exists()) {
                    Toast.makeText(CheckoutActivity.this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                for (DataSnapshot data : snapshot.getChildren()) {
                    ClientCartFragment.CartFoodItem item = data.getValue(ClientCartFragment.CartFoodItem.class);
                    if (item != null) {
                        String key = data.getKey();
                        cartItems.add(item);
                        cartKeys.add(key);

                        // Load addons
                        List<ClientCartFragment.AddonSelection> addons = new ArrayList<>();
                        if (data.child("addons").exists()) {
                            for (DataSnapshot addonSnap : data.child("addons").getChildren()) {
                                ClientCartFragment.AddonSelection addon = addonSnap.getValue(ClientCartFragment.AddonSelection.class);
                                if (addon != null) {
                                    addons.add(addon);
                                }
                            }
                        }
                        itemAddonsMap.put(key, addons);

                        // Calculate subtotal
                        double itemTotal = item.price * item.quantity;
                        for (ClientCartFragment.AddonSelection addon : addons) {
                            itemTotal += addon.price * addon.quantity;
                        }
                        subtotal += itemTotal;
                    }
                }

                // Display order items
                displayOrderItems();
                // Update totals
                updateTotals();
                // Check COD item limit
                checkCODItemLimit();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this,
                        "Failed to load cart: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayOrderItems() {
        containerOrderItems.removeAllViews();

        for (int i = 0; i < cartItems.size(); i++) {
            ClientCartFragment.CartFoodItem item = cartItems.get(i);
            String key = cartKeys.get(i);
            List<ClientCartFragment.AddonSelection> addons = itemAddonsMap.get(key);

            View itemView = getLayoutInflater().inflate(R.layout.order_summary_item, containerOrderItems, false);

            TextView tvItemName = itemView.findViewById(R.id.tvItemName);
            TextView tvItemDetails = itemView.findViewById(R.id.tvItemDetails);
            TextView tvItemPrice = itemView.findViewById(R.id.tvItemPrice);

            tvItemName.setText(item.name + " × " + item.quantity);

            // Build details with addons
            StringBuilder details = new StringBuilder();
            double itemTotal = item.price * item.quantity;

            if (addons != null && !addons.isEmpty()) {
                for (ClientCartFragment.AddonSelection addon : addons) {
                    if (details.length() > 0) details.append(", ");
                    details.append(addon.name);
                    if (addon.quantity > 1) details.append("(").append(addon.quantity).append(")");
                    itemTotal += addon.price * addon.quantity;
                }
            }

            if (details.length() > 0) {
                tvItemDetails.setText("Add-ons: " + details.toString());
                tvItemDetails.setVisibility(View.VISIBLE);
            } else {
                tvItemDetails.setVisibility(View.GONE);
            }

            tvItemPrice.setText("₱" + String.format("%.2f", itemTotal));
            containerOrderItems.addView(itemView);
        }
    }

    private void updateTotals() {
        // Update UI
        tvSubtotal.setText("₱" + String.format("%.2f", subtotal));

        // Calculate total based on order type
        if ("delivery".equals(selectedOrderType)) {
            tvDeliveryFee.setText("₱" + String.format("%.2f", deliveryFee));
            totalAmount = subtotal + deliveryFee;
        } else {
            tvDeliveryFee.setText("₱0.00");
            totalAmount = subtotal;
        }

        tvTotalAmount.setText("₱" + String.format("%.2f", totalAmount));
    }

    private void checkCODItemLimit() {
        int totalItems = getTotalCartItems();
        if (totalItems > 10) {
            tvCodWarning.setVisibility(View.VISIBLE);
            tvCodWarning.setText("⚠️ Cash on Delivery is limited to 10 items maximum. You have " + totalItems + " items.");

            if ("Cash".equals(selectedPaymentMethod)) {
                rbCash.setEnabled(false);
                rbGcash.setChecked(true);
                selectedPaymentMethod = "gcash";
                updatePaymentUI();
            }
        } else {
            tvCodWarning.setVisibility(View.GONE);
            rbCash.setEnabled(true);
        }
    }

    private void updatePaymentUI() {
        if ("gcash".equals(selectedPaymentMethod)) {
            containerGcashDetails.setVisibility(View.VISIBLE);
        } else {
            containerGcashDetails.setVisibility(View.GONE);
        }
        checkAndShowPaymentSection();
    }

    private int getTotalCartItems() {
        int total = 0;
        for (ClientCartFragment.CartFoodItem item : cartItems) {
            total += item.quantity;
        }
        return total;
    }

    private void setupListeners() {
        // Order Type Radio Group
        rgOrderType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbPickup) {
                selectedOrderType = "pickup";
                containerPickupDetails.setVisibility(View.VISIBLE);
                containerDeliveryDetails.setVisibility(View.GONE);
                containerPaymentSection.setVisibility(View.GONE); // HIDE PAYMENT FOR PICKUP
                deliveryFee = 0;
                selectedPickupTime = ""; // Reset pickup time
                btnSelectPickupTime.setText("Select Pickup Time");
                tvPickupTimeWarning.setVisibility(View.GONE);
            } else {
                selectedOrderType = "delivery";
                containerPickupDetails.setVisibility(View.GONE);
                containerDeliveryDetails.setVisibility(View.VISIBLE);
                containerPaymentSection.setVisibility(View.GONE); // Hide payment initially
                deliveryFee = 30.00;
            }
            updateTotals();
            updatePlaceOrderButton();
        });

        // Payment Method Radio Group
        rgPaymentMethod.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbCash) {
                selectedPaymentMethod = "Cash";
                containerGcashDetails.setVisibility(View.GONE);
            } else {
                selectedPaymentMethod = "gcash";
                containerGcashDetails.setVisibility(View.VISIBLE);
            }
            updatePlaceOrderButton();
        });

        // Select Pickup Time - TIME ONLY
        btnSelectPickupTime.setOnClickListener(v -> showTimePicker());

        // Use Saved Location
        btnUseSavedLocation.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(savedAddress)) {
                etDeliveryAddress.setText(savedAddress);
                tvLocationValidation.setVisibility(View.GONE);
                Toast.makeText(CheckoutActivity.this, "Saved address loaded", Toast.LENGTH_SHORT).show();
                checkAndShowPaymentSection();
            } else {
                Toast.makeText(CheckoutActivity.this, "No saved address found", Toast.LENGTH_SHORT).show();
            }
        });

        // Use Current Location
        btnUseCurrentLocation.setOnClickListener(v -> getNetworkLocation());

        // Type Manual Address
        btnTypeManual.setOnClickListener(v -> {
            etDeliveryAddress.setText("");
            etDeliveryAddress.requestFocus();
            showAddressFormatHint();
        });

        // Address Text Watcher
        etDeliveryAddress.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                checkAndShowPaymentSection();
            }
        });

        // GCash Reference Text Watcher
        if (etGcashReference != null) {
            etGcashReference.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    updatePlaceOrderButton();
                }
            });
        }

        // Upload Proof of Payment
        btnUploadProof.setOnClickListener(v -> openImagePicker());

        // View Proof
        btnViewProof.setOnClickListener(v -> {
            if (gcashProofBytes != null) {
                showImageDialog();
            }
        });

        // Place Order
        btnPlaceOrder.setOnClickListener(v -> validateAndPlaceOrder());
    }

    private void checkAndShowPaymentSection() {
        // FOR PICKUP: NEVER SHOW PAYMENT SECTION - PAY AT SHOP
        if ("pickup".equals(selectedOrderType)) {
            containerPaymentSection.setVisibility(View.GONE);
            return;
        }

        // FOR DELIVERY ONLY: show payment when address is valid
        String address = etDeliveryAddress.getText().toString().trim();
        boolean shouldShowPayment = !TextUtils.isEmpty(address) && isValidStaCruzAddress(address);

        if (shouldShowPayment) {
            containerPaymentSection.setVisibility(View.VISIBLE);
        } else {
            containerPaymentSection.setVisibility(View.GONE);
        }

        updatePlaceOrderButton();
    }

    private void updatePlaceOrderButton() {
        boolean isReady = false;

        // CHECK IF STORE IS CLOSED (8 PM onwards, before 8 AM)
        Calendar now = Calendar.getInstance();
        int currentHour = now.get(Calendar.HOUR_OF_DAY);
        int currentMinute = now.get(Calendar.MINUTE);

        // Check if store is closed (8 PM to 8 AM)
        boolean isStoreClosed = (currentHour >= 20 || currentHour < 8);

        if (isStoreClosed) {
            // STORE IS CLOSED - CANNOT PLACE ANY ORDER
            isReady = false;

            // Show warning message
            btnPlaceOrder.setText("STORE CLOSED (8PM-8AM)");
            btnPlaceOrder.setBackgroundColor(getResources().getColor(android.R.color.holo_red_dark));
            btnPlaceOrder.setEnabled(false);
            return;
        }

        if ("pickup".equals(selectedOrderType)) {
            // Pickup: need time selected ONLY, no payment needed (pay at shop)
            isReady = !TextUtils.isEmpty(selectedPickupTime);
        } else {
            // Delivery: need address and payment validation
            String address = etDeliveryAddress.getText().toString().trim();
            boolean hasValidAddress = !TextUtils.isEmpty(address) && isValidStaCruzAddress(address);

            if (!hasValidAddress) {
                isReady = false;
            } else if ("Cash".equals(selectedPaymentMethod)) {
                isReady = true;
            } else if ("gcash".equals(selectedPaymentMethod)) {
                String reference = etGcashReference.getText().toString().trim();
                boolean hasValidReference = !TextUtils.isEmpty(reference) && reference.matches("\\d{13}");
                boolean hasProof = gcashProofBytes != null;
                isReady = hasValidReference && hasProof;
            }
        }

        btnPlaceOrder.setEnabled(isReady);
        btnPlaceOrder.setBackgroundColor(isReady ?
                getResources().getColor(android.R.color.holo_green_dark) :
                getResources().getColor(android.R.color.darker_gray));

        // Reset button text if not closed
        if (!isStoreClosed) {
            btnPlaceOrder.setText("PLACE ORDER");
        }
    }
    private void showTimePicker() {
        final Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE, 30); // Minimum 30 minutes from now

        // Check if current time is within business hours (8 AM - 8 PM)
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);
        int currentMinute = calendar.get(Calendar.MINUTE);

        // If current time is after 7:30 PM, set to next day 8:00 AM
        if (currentHour >= 20 || (currentHour >= 19 && currentMinute >= 30)) {
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR_OF_DAY, 8);
            calendar.set(Calendar.MINUTE, 0);
        }
        // If current time is before 8:00 AM, set to 8:00 AM same day
        else if (currentHour < 8) {
            calendar.set(Calendar.HOUR_OF_DAY, 8);
            calendar.set(Calendar.MINUTE, 0);
        }

        TimePickerDialog timePicker = new TimePickerDialog(
                this,
                (view, hourOfDay, minute) -> {
                    Calendar selectedTime = Calendar.getInstance();
                    selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                    selectedTime.set(Calendar.MINUTE, minute);

                    // Check if selected time is at least 30 minutes from now
                    Calendar now = Calendar.getInstance();
                    long timeDifference = selectedTime.getTimeInMillis() - now.getTimeInMillis();
                    long minutesDifference = timeDifference / (60 * 1000);

                    if (minutesDifference < 30) {
                        tvPickupTimeWarning.setVisibility(View.VISIBLE);
                        tvPickupTimeWarning.setText("Pickup time must be at least 30 minutes from now");
                        return;
                    }

                    // Check if within business hours (8 AM - 8 PM)
                    if (hourOfDay < 8 || hourOfDay >= 20) {
                        tvPickupTimeWarning.setVisibility(View.VISIBLE);
                        tvPickupTimeWarning.setText("Pickup time must be between 8:00 AM and 8:00 PM");
                        return;
                    }

                    // Success - set the pickup time
                    tvPickupTimeWarning.setVisibility(View.GONE);

                    // Get current date
                    Calendar today = Calendar.getInstance();
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                    String currentDate = dateFormat.format(today.getTime());

                    // Format time
                    String amPm = (hourOfDay < 12) ? "AM" : "PM";
                    int displayHour = (hourOfDay > 12) ? hourOfDay - 12 : hourOfDay;
                    if (displayHour == 0) displayHour = 12;

                    selectedPickupTime = currentDate + " " + String.format("%02d:%02d", hourOfDay, minute);
                    String displayTime = String.format("%d:%02d %s", displayHour, minute, amPm);

                    SimpleDateFormat displayFormat = new SimpleDateFormat("EEE, MMM dd, yyyy", Locale.getDefault());
                    String displayDate = displayFormat.format(today.getTime());

                    btnSelectPickupTime.setText(displayDate + " at " + displayTime);

                    // Update place order button
                    updatePlaceOrderButton();
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                false
        );

        // Set title
        timePicker.setTitle("Select Pickup Time (8:00 AM - 8:00 PM)");
        timePicker.show();
    }

    private void getNetworkLocation() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        showLoading("Getting address...");
        btnUseCurrentLocation.setEnabled(false);

        // Use NETWORK PRIORITY for battery-friendly location
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        double lat = location.getLatitude();
                        double lng = location.getLongitude();

                        // Check if within Sta. Cruz bounds
                        if (isWithinStaCruz(lat, lng)) {
                            // Call Nominatim API to get address
                            getAddressFromNominatim(lat, lng);
                        } else {
                            hideLoading();
                            btnUseCurrentLocation.setEnabled(true);
                            showLocationError("You are outside Sta. Cruz, Laguna. Please type address manually.");
                        }
                    } else {
                        // Request new location
                        fusedLocationClient.requestLocationUpdates(locationRequest,
                                new LocationCallback() {
                                    @Override
                                    public void onLocationResult(LocationResult locationResult) {
                                        if (locationResult != null) {
                                            Location location = locationResult.getLastLocation();
                                            if (location != null) {
                                                double lat = location.getLatitude();
                                                double lng = location.getLongitude();

                                                if (isWithinStaCruz(lat, lng)) {
                                                    getAddressFromNominatim(lat, lng);
                                                } else {
                                                    hideLoading();
                                                    btnUseCurrentLocation.setEnabled(true);
                                                    showLocationError("You are outside Sta. Cruz, Laguna. Please type address manually.");
                                                }
                                                fusedLocationClient.removeLocationUpdates(this);
                                            }
                                        }
                                    }
                                },
                                null);
                    }
                })
                .addOnFailureListener(e -> {
                    hideLoading();
                    btnUseCurrentLocation.setEnabled(true);
                    showLocationError("Failed to get location: " + e.getMessage());
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getNetworkLocation();
            } else {
                hideLoading();
                btnUseCurrentLocation.setEnabled(true);
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean isWithinStaCruz(double lat, double lng) {
        return lat >= STA_CRUZ_MIN_LAT && lat <= STA_CRUZ_MAX_LAT &&
                lng >= STA_CRUZ_MIN_LNG && lng <= STA_CRUZ_MAX_LNG;
    }

    private void getAddressFromNominatim(double lat, double lng) {
        new Thread(() -> {
            try {
                // Nominatim API URL
                String urlString = "https://nominatim.openstreetmap.org/reverse?format=json&lat=" + lat + "&lon=" + lng;
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                // REQUIRED: Set User-Agent header
                connection.setRequestProperty("User-Agent", "4CSOFO-App/1.0");
                connection.setRequestMethod("GET");

                // Read response
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Parse JSON
                JSONObject json = new JSONObject(response.toString());
                JSONObject address = json.getJSONObject("address");

                // Extract address components
                String street = getAddressComponent(address, "road", "street", "village", "hamlet");
                String barangay = getAddressComponent(address, "village", "suburb", "neighbourhood", "hamlet");

                // Format address
                String formattedAddress = formatAddress(street, barangay);

                runOnUiThread(() -> {
                    if (formattedAddress != null) {
                        etDeliveryAddress.setText(formattedAddress);
                        tvLocationValidation.setVisibility(View.GONE);
                        Toast.makeText(CheckoutActivity.this, "Address generated", Toast.LENGTH_SHORT).show();
                        checkAndShowPaymentSection();
                    } else {
                        showLocationError("Could not generate address. Please type manually.");
                    }
                    hideLoading();
                    btnUseCurrentLocation.setEnabled(true);
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    hideLoading();
                    btnUseCurrentLocation.setEnabled(true);
                    showLocationError("Failed to get address. Please type manually.");
                });
            }
        }).start();
    }

    private String getAddressComponent(JSONObject address, String... keys) {
        for (String key : keys) {
            try {
                if (address.has(key)) {
                    return address.getString(key);
                }
            } catch (Exception e) {
                // Continue to next key
            }
        }
        return null;
    }

    private String formatAddress(String street, String barangay) {
        StringBuilder sb = new StringBuilder();

        if (street != null && !street.isEmpty()) {
            sb.append(street).append(" ");
        }

        if (barangay != null && !barangay.isEmpty()) {
            sb.append(barangay).append(" ");
        }

        // Check if we have at least one component
        if (sb.length() > 0) {
            sb.append("Sta.Cruz Laguna");
            return sb.toString().trim();
        }

        return null;
    }

    private void showLoading(String message) {
        progressDialog.setMessage(message);
        progressDialog.show();
    }

    private void hideLoading() {
        if (progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void showAddressFormatHint() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Address Format")
                .setMessage("Please enter your address in this format:\n\n" +
                        "[Street/Road/Village] [Barangay] Sta.Cruz Laguna\n\n" +
                        "Examples:\n" +
                        "• Bagumbayan Road Bagumbayan Sta.Cruz Laguna\n" +
                        "• Purok 3 Bagumbayan Sta.Cruz Laguna\n" +
                        "• Village Street Bagumbayan Sta.Cruz Laguna\n\n" +
                        "Note: No commas, just spaces between parts.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void showLocationError(String message) {
        tvLocationValidation.setText("⚠️ " + message);
        tvLocationValidation.setVisibility(View.VISIBLE);
    }

    private void openImagePicker() {
        ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(1080, 1080)
                .start(IMAGE_PICKER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == IMAGE_PICKER_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();

            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byteArrayOutputStream.write(buffer, 0, bytesRead);
                }

                gcashProofBytes = byteArrayOutputStream.toByteArray();

                // Show preview
                ivProofPreview.setImageURI(imageUri);
                ivProofPreview.setVisibility(View.VISIBLE);
                btnViewProof.setVisibility(View.VISIBLE);

                Toast.makeText(this, "Proof of payment selected", Toast.LENGTH_SHORT).show();

                // Update place order button state
                updatePlaceOrderButton();

                inputStream.close();
                byteArrayOutputStream.close();

            } catch (Exception e) {
                Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showImageDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Proof of Payment");

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(gcashProofBytes, 0, gcashProofBytes.length));
        imageView.setAdjustViewBounds(true);
        imageView.setMaxHeight(600);
        imageView.setMaxWidth(600);

        builder.setView(imageView)
                .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void validateAndPlaceOrder() {
        // Validate order type
        if ("pickup".equals(selectedOrderType)) {
            if (TextUtils.isEmpty(selectedPickupTime)) {
                Toast.makeText(this, "Please select pickup time", Toast.LENGTH_SHORT).show();
                return;
            }

            if (tvPickupTimeWarning.getVisibility() == View.VISIBLE) {
                Toast.makeText(this, "Please select a valid pickup time", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            // Delivery validation
            String address = etDeliveryAddress.getText().toString().trim();
            if (TextUtils.isEmpty(address)) {
                Toast.makeText(this, "Please enter delivery address", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidStaCruzAddress(address)) {
                showLocationError("Address must be in format: [Street] [Barangay] Sta.Cruz Laguna");
                return;
            }
        }

        // Validate payment method (only for delivery)
        if ("delivery".equals(selectedOrderType) && "gcash".equals(selectedPaymentMethod)) {
            String reference = etGcashReference.getText().toString().trim();
            if (TextUtils.isEmpty(reference)) {
                Toast.makeText(this, "Please enter GCash reference number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!reference.matches("\\d{13}")) {
                Toast.makeText(this, "Reference number must be 13 digits", Toast.LENGTH_SHORT).show();
                return;
            }

            if (gcashProofBytes == null) {
                Toast.makeText(this, "Please upload proof of payment", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if ("delivery".equals(selectedOrderType) && "Cash".equals(selectedPaymentMethod)) {
            int totalItems = getTotalCartItems();
            if (totalItems > 10) {
                new AlertDialog.Builder(this)
                        .setTitle("COD Limit Exceeded")
                        .setMessage("Cash on Delivery is limited to 10 items maximum.\n\n" +
                                "You have " + totalItems + " items in your cart.\n" +
                                "Please switch to GCash payment.")
                        .setPositiveButton("Switch to GCash", (dialog, which) -> {
                            rbGcash.setChecked(true);
                            selectedPaymentMethod = "gcash";
                            updatePaymentUI();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return;
            }
        }

        showOrderConfirmation();
    }

    private boolean isValidStaCruzAddress(String address) {
        if (address == null || address.isEmpty()) return false;

        String lowerAddress = address.toLowerCase();

        if (!lowerAddress.contains("sta") || !lowerAddress.contains("cruz") ||
                !lowerAddress.contains("laguna")) {
            return false;
        }

        String[] parts = address.split(" ");
        if (parts.length < 3) {
            return false;
        }

        return true;
    }

    private void showOrderConfirmation() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        StringBuilder message = new StringBuilder();
        message.append("Please confirm your order:\n\n");

        message.append("Order Type: ").append(selectedOrderType.equals("pickup") ? "Pickup" : "Delivery").append("\n");

        if ("pickup".equals(selectedOrderType)) {
            message.append("Pickup Time: ").append(btnSelectPickupTime.getText()).append("\n");
            message.append("Pickup Location: Bagumbayan Main Branch\n");
            message.append("Payment: Pay at the shop upon pickup\n");
        } else {
            message.append("Delivery Address: ").append(etDeliveryAddress.getText().toString()).append("\n");
            message.append("Payment Method: ").append(selectedPaymentMethod.equals("Cash") ? "Cash on Delivery" : "GCash").append("\n");

            if ("gcash".equals(selectedPaymentMethod)) {
                message.append("Reference: ").append(etGcashReference.getText().toString()).append("\n");
            }
        }

        message.append("\nItems:\n");
        for (int i = 0; i < cartItems.size(); i++) {
            ClientCartFragment.CartFoodItem item = cartItems.get(i);
            List<ClientCartFragment.AddonSelection> addons = itemAddonsMap.get(cartKeys.get(i));

            message.append("• ").append(item.quantity).append("× ").append(item.name);
            if (addons != null && !addons.isEmpty()) {
                message.append(" (");
                for (int j = 0; j < addons.size(); j++) {
                    if (j > 0) message.append(", ");
                    message.append(addons.get(j).name);
                    if (addons.get(j).quantity > 1) {
                        message.append("×").append(addons.get(j).quantity);
                    }
                }
                message.append(")");
            }
            message.append("\n");
        }

        message.append("\nSubtotal: ₱").append(String.format("%.2f", subtotal)).append("\n");
        if ("delivery".equals(selectedOrderType)) {
            message.append("Delivery Fee: ₱").append(String.format("%.2f", deliveryFee)).append("\n");
        }
        message.append("Total: ₱").append(String.format("%.2f", totalAmount)).append("\n\n");

        if ("pickup".equals(selectedOrderType)) {
            message.append("Note: You will pay at the shop when you pick up your order.\n\n");
        }

        message.append("Place this order?");

        builder.setTitle("Confirm Order")
                .setMessage(message.toString())
                .setPositiveButton("Place Order", (dialog, which) -> {
                    placeOrder();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void placeOrder() {
        String orderKey = ordersRef.push().getKey();

        List<String> itemsList = new ArrayList<>();
        for (int i = 0; i < cartItems.size(); i++) {
            ClientCartFragment.CartFoodItem item = cartItems.get(i);
            List<ClientCartFragment.AddonSelection> addons = itemAddonsMap.get(cartKeys.get(i));

            StringBuilder itemString = new StringBuilder();
            itemString.append(item.quantity).append("× ").append(item.name);

            if (addons != null && !addons.isEmpty()) {
                itemString.append(" (");
                for (int j = 0; j < addons.size(); j++) {
                    if (j > 0) itemString.append(", ");
                    itemString.append(addons.get(j).name);
                    if (addons.get(j).quantity > 1) {
                        itemString.append("×").append(addons.get(j).quantity);
                    }
                }
                itemString.append(")");
            }
            itemsList.add(itemString.toString());
        }

        String proofBase64 = "";
        if (gcashProofBytes != null) {
            proofBase64 = Base64.encodeToString(gcashProofBytes, Base64.DEFAULT);
        }

        OrderModel order = new OrderModel();
        order.setOrderKey(orderKey);
        order.setUserId(currentUser.getUid());
        order.setCustomerName(customerName);
        order.setItems(itemsList);
        order.setTotal_price(totalAmount);

        // Set payment method - for pickup, always "cash" (pay at shop)
        if ("pickup".equals(selectedOrderType)) {
            order.setPayment_method("Cash"); // Pay at shop
        } else {
            order.setPayment_method(selectedPaymentMethod.equals("Cash") ? "Cash" : "gcash");
        }

        order.setStatus("Pending");
        order.setOrderType(selectedOrderType);
        order.setOrderDate(System.currentTimeMillis());

        if ("pickup".equals(selectedOrderType)) {
            order.setPickupTime(selectedPickupTime);
            order.setPickupBranch("Bagumbayan Main Branch");
        } else {
            order.setDeliveryLocation(etDeliveryAddress.getText().toString());
        }

        if ("delivery".equals(selectedOrderType) && "gcash".equals(selectedPaymentMethod)) {
            order.setGcashReferenceNumber(etGcashReference.getText().toString());
            order.setGcashProofDownloadUrl(proofBase64);
        }

        ordersRef.child(orderKey).setValue(order)
                .addOnSuccessListener(aVoid -> {
                    cartRef.removeValue();

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setTitle("🎉 Order Placed Successfully!")
                            .setMessage("Your order has been placed!\n\n" +
                                    "Order ID: " + orderKey.substring(0, 8).toUpperCase() + "\n" +
                                    "Total: ₱" + String.format("%.2f", totalAmount) + "\n\n" +
                                    ("pickup".equals(selectedOrderType) ?
                                            "Please pick up at Bagumbayan Main Branch at the selected time." :
                                            "You can track your order in the Orders section."))
                            .setPositiveButton("OK", (dialog, which) -> {
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnPlaceOrder.setText("PLACE ORDER");
                    btnPlaceOrder.setEnabled(true);
                });

        btnPlaceOrder.setText("Processing...");
        btnPlaceOrder.setEnabled(false);
    }
}