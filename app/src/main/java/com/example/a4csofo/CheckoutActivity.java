package com.example.a4csofo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.util.*;

public class CheckoutActivity extends AppCompatActivity {

    // UI Components
    private LinearLayout cartContainer;
    private TextView txtSubtotal, txtVat, txtDelivery, txtTotal;
    private RadioGroup paymentGroup;
    private Button btnConfirm, btnSelectLocation;

    // Data
    private ArrayList<CartItem> cartList = new ArrayList<>();
    private HashMap<String, List<ClientCartFragment.AddonSelection>> cartItemAddOns = new HashMap<>();
    private double subtotal = 0, vat = 0, deliveryFee = 30, total = 0;

    // Services
    private FirebaseAuth auth;
    private DatabaseReference cartRef;
    private FusedLocationProviderClient fusedLocationClient;

    // Order Details
    private String deliveryLocation = "";
    private String gcashReferenceNumber = "";
    private String gcashProofDownloadUrl = "";
    private String pickupBranch = "";
    private String pickupTime = "";
    private Uri gcashImageUri = null;

    // Track selected payment method
    private String selectedPaymentMethod = "";

    // Barangays
    private final List<String> staCruzBarangays = Arrays.asList(
            "BABAYAN","BAGUMBAYAN","BUBUKAL","CALIOS","GATID",
            "ILAYANG BUKAL","ILAYANG PALSABANGON","ILAYANG PULONG BATO",
            "IPAG","KANLURANG BUKAL","LABUIN","LAGUNA","MALINAO",
            "PATIM","SAN JOSE","SANTIAGO","SANTO ANGEL CENTRAL",
            "SANTO ANGEL NORTE","SANTO ANGEL SUR","TALANGAN",
            "UGONG","POBLACION UNO","POBLACION DOS","POBLACION TRES","POBLACION CUATRO"
    );

    // Permissions
    private static final int REQ_CODE_PICK_IMAGE = 2001;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CheckoutActivity";

    // Callback interface for location
    private interface LocationCallback {
        void onLocationSuccess();
        void onLocationFailed(String error);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        Log.d(TAG, "onCreate started");

        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupUI();
        loadCartFromFirebase();
    }

    private void setupUI() {
        cartContainer = findViewById(R.id.cartContainer);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtVat = findViewById(R.id.txtVat);
        txtDelivery = findViewById(R.id.txtDelivery);
        txtTotal = findViewById(R.id.txtTotal);
        paymentGroup = findViewById(R.id.paymentGroup);
        btnConfirm = findViewById(R.id.btnConfirm);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);

        // Initial Payment Method State
        for (int i = 0; i < paymentGroup.getChildCount(); i++) {
            View child = paymentGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton rb = (RadioButton) child;
                String text = rb.getText().toString().toLowerCase();
                if (text.contains("pick")) {
                    rb.setEnabled(true); // Pick Up always enabled
                } else {
                    rb.setEnabled(false); // COD & GCash disabled initially
                }
            }
        }

        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);

        btnSelectLocation.setOnClickListener(v -> showDeliveryDialog());
        btnConfirm.setOnClickListener(v -> confirmOrder());

        paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == -1) return;

            RadioButton selected = group.findViewById(checkedId);
            selectedPaymentMethod = selected.getText().toString().toLowerCase();

            btnConfirm.setEnabled(false);
            btnConfirm.setAlpha(0.5f);

            if (selectedPaymentMethod.contains("gcash")) {
                if (deliveryLocation.isEmpty()) {
                    Toast.makeText(this, "Please select location first", Toast.LENGTH_SHORT).show();
                    paymentGroup.clearCheck();
                    return;
                }
                showGcashDialog();
            }
            else if (selectedPaymentMethod.contains("cash")) {
                if (deliveryLocation.isEmpty()) {
                    Toast.makeText(this, "Please select location first", Toast.LENGTH_SHORT).show();
                    paymentGroup.clearCheck();
                    return;
                }
                btnConfirm.setEnabled(true);
                btnConfirm.setAlpha(1f);
            }
            else if (selectedPaymentMethod.contains("pick")) {
                showPickupDialog();
            }
        });
    }

    private void enablePaymentMethods() {
        for (int i = 0; i < paymentGroup.getChildCount(); i++) {
            View child = paymentGroup.getChildAt(i);
            if (child instanceof RadioButton) {
                RadioButton rb = (RadioButton) child;
                String text = rb.getText().toString().toLowerCase();
                if (!text.contains("pick")) {
                    rb.setEnabled(true); // COD & GCash enabled after location selected
                }
            }
        }
    }

    // ------------------------------------
    // LOCATION SELECTION
    // ------------------------------------
    private void showDeliveryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Delivery Location");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delivery_options, null);

        RadioButton rbCurrent = dialogView.findViewById(R.id.rbCurrentLocation);
        RadioButton rbSaved = dialogView.findViewById(R.id.rbSavedAddress);
        RadioButton rbManual = dialogView.findViewById(R.id.rbManualInput);

        LinearLayout manualInputLayout = dialogView.findViewById(R.id.manualInputLayout);
        EditText edtStreet = dialogView.findViewById(R.id.edtStreetDetails);
        Spinner spinnerBarangay = dialogView.findViewById(R.id.spinnerBarangay);

        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, staCruzBarangays);
        spinnerBarangay.setAdapter(barangayAdapter);

        manualInputLayout.setVisibility(View.GONE);

        rbManual.setOnClickListener(v -> manualInputLayout.setVisibility(View.VISIBLE));
        rbCurrent.setOnClickListener(v -> manualInputLayout.setVisibility(View.GONE));
        rbSaved.setOnClickListener(v -> manualInputLayout.setVisibility(View.GONE));

        builder.setView(dialogView);
        builder.setPositiveButton("Save", null); // We'll override later
        builder.setNegativeButton("Cancel", null);
        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (rbCurrent.isChecked()) {
                getCurrentLocation(new LocationCallback() {
                    @Override
                    public void onLocationSuccess() {
                        enablePaymentMethods();
                        btnConfirm.setEnabled(true);
                        btnConfirm.setAlpha(1f);
                        dialog.dismiss();
                        Toast.makeText(CheckoutActivity.this,
                                "Current location saved: " + deliveryLocation, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onLocationFailed(String error) {
                        Toast.makeText(CheckoutActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
            else if (rbSaved.isChecked()) {
                getSavedAddress(new LocationCallback() {
                    @Override
                    public void onLocationSuccess() {
                        enablePaymentMethods();
                        btnConfirm.setEnabled(true);
                        btnConfirm.setAlpha(1f);
                        dialog.dismiss();
                        Toast.makeText(CheckoutActivity.this,
                                "Saved address loaded: " + deliveryLocation, Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onLocationFailed(String error) {
                        Toast.makeText(CheckoutActivity.this, error, Toast.LENGTH_LONG).show();
                    }
                });
            }
            else if (rbManual.isChecked()) {
                String street = edtStreet.getText().toString().trim();
                String barangay = spinnerBarangay.getSelectedItem().toString();

                if (street.isEmpty()) {
                    Toast.makeText(this, "Enter street details", Toast.LENGTH_SHORT).show();
                    return;
                }

                deliveryLocation = street + ", " + barangay + ", Sta. Cruz, Laguna";
                enablePaymentMethods();
                btnConfirm.setEnabled(true);
                btnConfirm.setAlpha(1f);
                Toast.makeText(this, "Location saved: " + deliveryLocation, Toast.LENGTH_LONG).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Please select an option", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ------------------------------------
// CHECK IF LOCATION IS WITHIN STA. CRUZ
// ------------------------------------
    private boolean isWithinStaCruzArea(double latitude, double longitude) {
        // Sta. Cruz approximate boundaries
        double minLat = 14.20;  // Southern boundary
        double maxLat = 14.35;  // Northern boundary
        double minLng = 121.35; // Western boundary
        double maxLng = 121.50; // Eastern boundary

        return latitude >= minLat && latitude <= maxLat &&
                longitude >= minLng && longitude <= maxLng;
    }
    // ------------------------------------
// GET ADDRESS FROM LOCATION
// ------------------------------------
    private void getAddressFromLocation(android.location.Location location,
                                        LocationCallback callback,
                                        ProgressDialog progressDialog) {
        // If progressDialog is null, just ignore
        if (progressDialog != null) progressDialog.setMessage("Fetching address...");

        new Thread(() -> {
            String addressStr = "";
            try {
                if (Geocoder.isPresent()) {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(
                            location.getLatitude(),
                            location.getLongitude(),
                            1
                    );
                    if (addresses != null && !addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        addressStr = formatAddress(address);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (addressStr.isEmpty()) {
                // fallback to coordinates
                addressStr = String.format(Locale.getDefault(),
                        "Near %.6f°N, %.6f°E, Sta. Cruz, Laguna",
                        location.getLatitude(),
                        location.getLongitude());
            }

            String finalAddressStr = addressStr;
            runOnUiThread(() -> {
                if (progressDialog != null) progressDialog.dismiss();
                deliveryLocation = finalAddressStr;
                callback.onLocationSuccess();
                Toast.makeText(CheckoutActivity.this,
                        "Location saved: " + deliveryLocation,
                        Toast.LENGTH_LONG).show();
            });
        }).start();
    }

    // ------------------------------------
// FORMAT ADDRESS
// ------------------------------------
    private String formatAddress(Address address) {
        StringBuilder sb = new StringBuilder();

        String thoroughfare = address.getThoroughfare();
        String subThoroughfare = address.getSubThoroughfare();
        String subLocality = address.getSubLocality();
        String featureName = address.getFeatureName();

        if (subThoroughfare != null && !subThoroughfare.isEmpty()) sb.append(subThoroughfare).append(" ");
        if (thoroughfare != null && !thoroughfare.isEmpty()) sb.append(thoroughfare);
        if (sb.length() == 0 && featureName != null) sb.append(featureName);
        if (subLocality != null && !subLocality.isEmpty()) sb.append(", ").append(subLocality);

        sb.append(", Sta. Cruz, Laguna");
        return sb.toString();
    }


    // GET CURRENT LOCATION (FIXED)
    private void getCurrentLocation(LocationCallback callback) {
        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            callback.onLocationFailed("Location permission needed. Please grant permission and try again.");
            return;
        }

        // Show loading
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Getting your current location...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        try {
            // Create a high-accuracy single update request
            com.google.android.gms.location.LocationRequest locationRequest =
                    com.google.android.gms.location.LocationRequest.create();
            locationRequest.setPriority(com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY);
            locationRequest.setNumUpdates(1);
            locationRequest.setInterval(0);
            locationRequest.setFastestInterval(0);

            com.google.android.gms.location.LocationCallback locationCallback =
                    new com.google.android.gms.location.LocationCallback() {
                        @Override
                        public void onLocationResult(com.google.android.gms.location.LocationResult locationResult) {
                            progressDialog.dismiss();

                            if (locationResult != null && locationResult.getLastLocation() != null) {
                                android.location.Location location = locationResult.getLastLocation();

                                if (isWithinStaCruzArea(location.getLatitude(), location.getLongitude())) {
                                    // Convert to address
                                    getAddressFromLocation(location, callback, progressDialog);
                                } else {
                                    callback.onLocationFailed("You are not within Sta. Cruz, Laguna delivery area. Please use manual input.");
                                }
                            } else {
                                callback.onLocationFailed("Unable to get current location. Please try manual input.");
                            }

                            fusedLocationClient.removeLocationUpdates(this);
                        }
                    };

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        callback.onLocationFailed("Failed to get location: " + e.getMessage());
                    });

        } catch (SecurityException e) {
            progressDialog.dismiss();
            callback.onLocationFailed("Location permission error.");
        }
    }

    // GET SAVED ADDRESS (FIXED)
    // ------------------------------------
    private void getSavedAddress(LocationCallback callback) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            callback.onLocationFailed("User not logged in");
            return;
        }

        DatabaseReference userRef = FirebaseDatabase.getInstance()
                .getReference("users")
                .child(user.getUid());

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    callback.onLocationFailed("User data not found");
                    return;
                }

                // Try different possible locations for saved address
                String savedAddress = null;

                // Check root level
                if (snapshot.child("savedAddress").exists()) {
                    savedAddress = snapshot.child("savedAddress").getValue(String.class);
                }
                // Check address field
                else if (snapshot.child("address").exists()) {
                    savedAddress = snapshot.child("address").getValue(String.class);
                }
                // Check profile/address
                else if (snapshot.child("profile").child("address").exists()) {
                    savedAddress = snapshot.child("profile").child("address").getValue(String.class);
                }

                if (savedAddress == null || savedAddress.isEmpty()) {
                    callback.onLocationFailed("No saved address found. Please use manual input.");
                    return;
                }

                // Ensure the address includes Sta. Cruz, Laguna
                deliveryLocation = savedAddress;
                if (!deliveryLocation.toLowerCase().contains("sta. cruz") &&
                        !deliveryLocation.toLowerCase().contains("sta cruz")) {
                    deliveryLocation += ", Sta. Cruz, Laguna";
                }

                callback.onLocationSuccess();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Database error: " + error.getMessage());
                callback.onLocationFailed("Failed to load saved address: " + error.getMessage());
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission granted. Please select current location again.",
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Location permission denied. Please use manual input.",
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------
    // GCASH DIALOG
    // ------------------------------------
    private void showGcashDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GCash Payment");

        View view = getLayoutInflater().inflate(R.layout.dialog_gcash_combined, null);
        EditText edtRefNumber = view.findViewById(R.id.edtReferenceNumber);
        Button btnUpload = view.findViewById(R.id.btnUploadProof);

        builder.setView(view);

        btnUpload.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_CODE_PICK_IMAGE);
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            gcashReferenceNumber = edtRefNumber.getText().toString().trim();

            if (gcashReferenceNumber.isEmpty() || gcashImageUri == null) {
                Toast.makeText(this, "Please complete GCash payment details", Toast.LENGTH_SHORT).show();
                paymentGroup.clearCheck();
                return;
            }

            btnConfirm.setEnabled(true);
            btnConfirm.setAlpha(1f);

            Toast.makeText(this, "GCash details saved!", Toast.LENGTH_SHORT).show();
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> paymentGroup.clearCheck());
        builder.show();
    }

    // ------------------------------------
    // PICKUP DIALOG
    // ------------------------------------
    private void showPickupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pickup Options");

        View view = getLayoutInflater().inflate(R.layout.dialog_pickup_options, null);
        Spinner spinnerBranch = view.findViewById(R.id.spinnerBranch);
        EditText edtPickupTime = view.findViewById(R.id.edtPickupTime);

        List<String> branches = Arrays.asList("Bubukal Sta Cruz Laguna");
        spinnerBranch.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, branches));

        edtPickupTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new TimePickerDialog(this, (dialog, hour, minute) ->
                    edtPickupTime.setText(String.format("%02d:%02d", hour, minute)),
                    now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
        });

        builder.setView(view);

        builder.setPositiveButton("Save", (dialog, which) -> {
            pickupBranch = spinnerBranch.getSelectedItem().toString();
            pickupTime = edtPickupTime.getText().toString();

            if (pickupTime.isEmpty()) {
                Toast.makeText(this, "Please select pickup time", Toast.LENGTH_SHORT).show();
                return;
            }

            deliveryLocation = "Pickup at " + pickupBranch + " (" + pickupTime + ")";

            btnConfirm.setEnabled(true);
            btnConfirm.setAlpha(1f);

            showQRCode(deliveryLocation);
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> paymentGroup.clearCheck());
        builder.show();
    }

    // ------------------------------------
    // QR CODE GENERATION
    // ------------------------------------
    private void showQRCode(String data) {
        try {
            int size = 400;
            BitMatrix matrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++)
                for (int y = 0; y < size; y++)
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);

            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(bitmap);

            new AlertDialog.Builder(this)
                    .setTitle("Pickup QR Code")
                    .setView(qrView)
                    .setPositiveButton("OK", null)
                    .show();

        } catch (WriterException e) {
            Toast.makeText(this, "QR generation failed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int result, @Nullable Intent data) {
        super.onActivityResult(req, result, data);
        if (req == REQ_CODE_PICK_IMAGE && result == RESULT_OK && data != null) {
            gcashImageUri = data.getData();
            Toast.makeText(this, "GCash proof image selected", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------
    // CONFIRM ORDER
    // ------------------------------------
    private void confirmOrder() {
        if (deliveryLocation.isEmpty()) {
            Toast.makeText(this, "Please select delivery location first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedPaymentMethod.contains("gcash")) {
            if (gcashReferenceNumber.isEmpty() || gcashImageUri == null) {
                Toast.makeText(this, "Please complete GCash payment details", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> items = new ArrayList<>();
        for (CartItem item : cartList)
            items.add(item.getName() + " x" + item.getQuantity());

        OrderModel order = new OrderModel();
        order.setUserId(user.getUid());
        order.setItems(items);
        order.setDeliveryLocation(deliveryLocation);
        order.setPayment_method(selectedPaymentMethod);
        order.setTotal_price(total);
        order.setStatus("Pending");
        order.setOrderDate(System.currentTimeMillis());

        if (selectedPaymentMethod.contains("gcash"))
            order.setGcashReferenceNumber(gcashReferenceNumber);

        if (selectedPaymentMethod.contains("pick")) {
            order.setPickupBranch(pickupBranch);
            order.setPickupTime(pickupTime);
        }

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        String key = ordersRef.push().getKey();
        order.setOrderKey(key);

        ordersRef.child(key).setValue(order).addOnSuccessListener(a -> {
            if (cartRef != null) cartRef.removeValue();
            showReceipt(items, total, selectedPaymentMethod);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void showReceipt(List<String> items, double total, String method) {
        StringBuilder sb = new StringBuilder();
        sb.append("🧾 ORDER RECEIPT\n\nItems:\n");
        for (String i : items) sb.append("• ").append(i).append("\n");
        sb.append("\nTotal: ₱").append(String.format("%.2f", total)).append("\n");
        sb.append("Payment: ").append(method).append("\n");
        sb.append("Location: ").append(deliveryLocation);

        new AlertDialog.Builder(this)
                .setTitle("Order Confirmed!")
                .setMessage(sb.toString())
                .setPositiveButton("OK", (d, w) -> {
                    Intent intent = new Intent(CheckoutActivity.this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    // ------------------------------------
    // CART METHODS
    // ------------------------------------
    private void loadCartFromFirebase() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(user.getUid());
        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartList.clear();
                cartItemAddOns.clear();

                if (!snapshot.exists() || snapshot.getChildrenCount() == 0) {
                    Toast.makeText(CheckoutActivity.this, "Your cart is empty", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    String name = itemSnapshot.child("name").getValue(String.class);
                    Double price = itemSnapshot.child("price").getValue(Double.class);
                    Long qty = itemSnapshot.child("quantity").getValue(Long.class);

                    if (name != null && price != null && qty != null)
                        cartList.add(new CartItem(name, qty.intValue(), price));
                }

                displayCartItems();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this, "Failed to load cart: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayCartItems() {
        cartContainer.removeAllViews();
        subtotal = 0;

        LayoutInflater inflater = LayoutInflater.from(this);

        for (CartItem item : cartList) {
            View view = inflater.inflate(R.layout.item_cart_checkout, cartContainer, false);

            TextView txtName = view.findViewById(R.id.txtItemName);
            TextView txtQty = view.findViewById(R.id.txtItemQty);
            TextView txtPrice = view.findViewById(R.id.txtItemPrice);

            txtName.setText(item.getName());
            txtQty.setText("x" + item.getQuantity());
            txtPrice.setText("₱" + String.format("%.2f", item.getQuantity() * item.getPrice()));

            subtotal += item.getQuantity() * item.getPrice();
            cartContainer.addView(view);
        }

        updateTotals();
    }

    private void updateTotals() {
        vat = subtotal * 0.12;
        total = subtotal + vat + deliveryFee;

        txtSubtotal.setText("₱" + String.format("%.2f", subtotal));
        txtVat.setText("₱" + String.format("%.2f", vat));
        txtDelivery.setText("₱" + String.format("%.2f", deliveryFee));
        txtTotal.setText("₱" + String.format("%.2f", total));
    }

    public class CartItem {
        private String name;
        private int quantity;
        private double price;

        public CartItem(String n, int q, double p) {
            name = n;
            quantity = q;
            price = p;
        }

        public String getName() { return name; }
        public int getQuantity() { return quantity; }
        public double getPrice() { return price; }
    }
}