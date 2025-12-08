package com.example.a4csofo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
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
import java.text.SimpleDateFormat;
import java.util.*;

public class CheckoutActivity extends AppCompatActivity {

    // UI Components
    private LinearLayout cartContainer;
    private TextView txtSubtotal, txtVat, txtDelivery, txtTotal;
    private RadioGroup paymentGroup;
    private Button btnConfirm;

    // Data
    private ArrayList<CartItem> cartList = new ArrayList<>();
    private HashMap<String, List<ClientCartFragment.AddonSelection>> cartItemAddOns = new HashMap<>();
    private double subtotal = 0, vat = 0, deliveryFee = 30, total = 0;

    // Services
    private FirebaseAuth auth;
    private DatabaseReference cartRef;
    private PaymentManager paymentManager;
    private FusedLocationProviderClient fusedLocationClient;

    // Order Details
    private String deliveryLocation = "";
    private String gcashReferenceNumber = "";
    private String gcashProofDownloadUrl = "";
    private String pickupBranch = "";
    private String pickupTime = "";
    private String orderType = "delivery";
    private Uri gcashImageUri = null;

    // Track selected payment method
    private String selectedPaymentMethod = "";

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

    // Permissions
    private static final int REQ_CODE_PICK_IMAGE = 2001;
    private static final int LOCATION_PERMISSION_REQUEST = 1001;
    private static final String TAG = "CheckoutActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        Log.d(TAG, "onCreate started");

        // Initialize
        auth = FirebaseAuth.getInstance();
        paymentManager = new PaymentManager(this, auth);
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Setup UI
        setupUI();

        // Load cart
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

        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);

        btnConfirm.setOnClickListener(v -> confirmOrder());

        // FIXED: Use the CORRECT IDs from your layout
        paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = group.findViewById(checkedId);
            if (selected != null) {
                selectedPaymentMethod = selected.getText().toString();
                btnConfirm.setEnabled(false);
                btnConfirm.setAlpha(0.5f);

                String paymentText = selected.getText().toString().toLowerCase();

                if (paymentText.contains("gcash")) {
                    showGcashDialog();
                } else if (paymentText.contains("cash") || paymentText.contains("delivery")) {
                    showDeliveryDialog();
                } else if (paymentText.contains("pick")) {
                    showPickupDialog();
                }
            }
        });
    }

    // ====================== DIALOG METHODS ======================
    private void showDeliveryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Delivery Location");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_delivery_options, null);

        // Find views with EXISTING IDs from dialog_delivery_options.xml
        RadioButton rbCurrent = dialogView.findViewById(R.id.rbCurrentLocation);
        RadioButton rbSaved = dialogView.findViewById(R.id.rbSavedAddress);
        RadioButton rbManual = dialogView.findViewById(R.id.rbManualInput);
        LinearLayout manualInputLayout = dialogView.findViewById(R.id.manualInputLayout);
        EditText edtStreet = dialogView.findViewById(R.id.edtStreetDetails);
        Spinner spinnerBarangay = dialogView.findViewById(R.id.spinnerBarangay);

        // Setup spinner
        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, staCruzBarangays);
        spinnerBarangay.setAdapter(barangayAdapter);

        // Hide manual input by default
        if (manualInputLayout != null) {
            manualInputLayout.setVisibility(View.GONE);
        }

        // Radio button listeners
        if (rbCurrent != null) {
            rbCurrent.setOnClickListener(v -> {
                if (manualInputLayout != null) manualInputLayout.setVisibility(View.GONE);
            });
        }

        if (rbSaved != null) {
            rbSaved.setOnClickListener(v -> {
                if (manualInputLayout != null) manualInputLayout.setVisibility(View.GONE);
            });
        }

        if (rbManual != null) {
            rbManual.setOnClickListener(v -> {
                if (manualInputLayout != null) manualInputLayout.setVisibility(View.VISIBLE);
            });
        }

        builder.setView(dialogView);

        builder.setPositiveButton("Save", (dialog, which) -> {
            if (rbCurrent != null && rbCurrent.isChecked()) {
                // Use Current Location
                getCurrentLocation();
            } else if (rbSaved != null && rbSaved.isChecked()) {
                // Use Saved Address
                getSavedAddress();
            } else if (rbManual != null && rbManual.isChecked() && edtStreet != null && spinnerBarangay != null) {
                // Manual Input
                String street = edtStreet.getText().toString().trim();
                String barangay = spinnerBarangay.getSelectedItem() != null ?
                        spinnerBarangay.getSelectedItem().toString() : "";

                if (!street.isEmpty() && !barangay.isEmpty()) {
                    validateManualAddress(street, barangay);
                } else {
                    Toast.makeText(this, "Please enter both street and barangay", Toast.LENGTH_SHORT).show();
                }
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            paymentGroup.clearCheck();
        });

        builder.show();
    }

    // ====================== LOCATION METHODS ======================
    private void getCurrentLocation() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);
            return;
        }

        // Get current location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            // Convert location to address (simplified)
                            double lat = location.getLatitude();
                            double lng = location.getLongitude();

                            // For now, just use a generic address since we're in Sta. Cruz, Laguna only
                            deliveryLocation = "Current Location (GPS: " +
                                    String.format("%.6f", lat) + ", " +
                                    String.format("%.6f", lng) + "), Sta. Cruz, Laguna";

                            // Check COD limits
                            checkCODLimitsWithAddress(deliveryLocation);
                        } else {
                            Toast.makeText(CheckoutActivity.this,
                                    "Unable to get current location. Please use manual input.",
                                    Toast.LENGTH_LONG).show();
                            paymentGroup.clearCheck();
                        }
                    }
                });
    }

    private void getSavedAddress() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_SHORT).show();
            paymentGroup.clearCheck();
            return;
        }

        // Check if user has saved address in Firebase
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users")
                .child(currentUser.getUid()).child("savedAddress");

        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String savedAddress = snapshot.getValue(String.class);
                    if (savedAddress != null && !savedAddress.isEmpty()) {
                        deliveryLocation = savedAddress + ", Sta. Cruz, Laguna";
                        checkCODLimitsWithAddress(deliveryLocation);
                    } else {
                        Toast.makeText(CheckoutActivity.this,
                                "No saved address found. Please add one first.",
                                Toast.LENGTH_LONG).show();
                        paymentGroup.clearCheck();
                    }
                } else {
                    Toast.makeText(CheckoutActivity.this,
                            "No saved address found. Please use manual input.",
                            Toast.LENGTH_LONG).show();
                    paymentGroup.clearCheck();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this,
                        "Error loading saved address",
                        Toast.LENGTH_SHORT).show();
                paymentGroup.clearCheck();
            }
        });
    }

    private void validateManualAddress(String street, String barangay) {
        // SIMPLE VALIDATION: Just check if barangay is in Sta. Cruz list
        if (staCruzBarangays.contains(barangay.toUpperCase())) {
            String fullAddress = street + ", " + barangay + ", Sta. Cruz, Laguna";
            deliveryLocation = fullAddress;
            checkCODLimitsWithAddress(fullAddress);
        } else {
            Toast.makeText(this,
                    "Please select a valid barangay in Sta. Cruz, Laguna",
                    Toast.LENGTH_LONG).show();
            paymentGroup.clearCheck();
        }
    }

    private void checkCODLimitsWithAddress(String fullAddress) {
        paymentManager.checkCOD(total, deliveryLocation, new PaymentManager.PaymentCallback() {
            @Override
            public void onAllowed() {
                runOnUiThread(() -> {
                    btnConfirm.setEnabled(true);
                    btnConfirm.setAlpha(1f);
                    Toast.makeText(CheckoutActivity.this, "✓ Delivery location saved!", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onBlocked(String reason) {
                runOnUiThread(() -> {
                    new AlertDialog.Builder(CheckoutActivity.this)
                            .setTitle("COD Not Available")
                            .setMessage(reason)
                            .setPositiveButton("OK", (dialog, which1) -> {
                                paymentGroup.clearCheck();
                                btnConfirm.setEnabled(false);
                                btnConfirm.setAlpha(0.5f);
                            })
                            .show();
                });
            }
        });
    }

    // ====================== GCASH DIALOG ======================
    private void showGcashDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GCash Payment");

        View view = getLayoutInflater().inflate(R.layout.dialog_gcash_combined, null);
        EditText edtRefNumber = view.findViewById(R.id.edtReferenceNumber);
        Button btnUploadProof = view.findViewById(R.id.btnUploadProof);
        EditText edtStreet = view.findViewById(R.id.edtStreet);
        Spinner spinnerBarangay = view.findViewById(R.id.spinnerBarangay);

        ArrayAdapter<String> barangayAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, staCruzBarangays);
        spinnerBarangay.setAdapter(barangayAdapter);

        builder.setView(view);

        btnUploadProof.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            startActivityForResult(intent, REQ_CODE_PICK_IMAGE);
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            String refNumber = edtRefNumber.getText().toString().trim();
            String street = edtStreet.getText().toString().trim();
            String barangay = spinnerBarangay.getSelectedItem() != null ?
                    spinnerBarangay.getSelectedItem().toString() : "";

            if (refNumber.isEmpty() || street.isEmpty() || barangay.isEmpty()) {
                Toast.makeText(this, "Please complete all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            // Simple validation
            if (staCruzBarangays.contains(barangay.toUpperCase())) {
                deliveryLocation = street + ", " + barangay + ", Sta. Cruz, Laguna";
                gcashReferenceNumber = refNumber;

                // Now check GCash limits WITH address
                paymentManager.checkGCash(total, deliveryLocation, new PaymentManager.PaymentCallback() {
                    @Override
                    public void onAllowed() {
                        runOnUiThread(() -> {
                            btnConfirm.setEnabled(true);
                            btnConfirm.setAlpha(1f);
                            Toast.makeText(CheckoutActivity.this, "✓ GCash details saved!", Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onBlocked(String reason) {
                        runOnUiThread(() -> {
                            new AlertDialog.Builder(CheckoutActivity.this)
                                    .setTitle("GCash Not Available")
                                    .setMessage(reason)
                                    .setPositiveButton("OK", (dialog1, which1) -> {
                                        paymentGroup.clearCheck();
                                        btnConfirm.setEnabled(false);
                                        btnConfirm.setAlpha(0.5f);
                                    })
                                    .show();
                        });
                    }
                });
            } else {
                Toast.makeText(this, "Please select a valid barangay in Sta. Cruz, Laguna", Toast.LENGTH_LONG).show();
            }
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            paymentGroup.clearCheck();
        });

        builder.show();
    }

    // ====================== PICKUP DIALOG ======================
    private void showPickupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick-Up Options");

        View view = getLayoutInflater().inflate(R.layout.dialog_pickup_options, null);
        Spinner branchSpinner = view.findViewById(R.id.spinnerBranch);
        EditText edtPickupTime = view.findViewById(R.id.edtPickupTime);

        List<String> branches = Arrays.asList("Bubukal Sta Cruz Laguna");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, branches);
        branchSpinner.setAdapter(adapter);

        builder.setView(view);

        // Time picker
        edtPickupTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            new TimePickerDialog(this, (view1, hourOfDay, minute1) -> {
                edtPickupTime.setText(String.format("%02d:%02d", hourOfDay, minute1));
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false).show();
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            String selectedBranch = branchSpinner.getSelectedItem() != null ?
                    branchSpinner.getSelectedItem().toString() : "Main Branch";
            String selectedTime = edtPickupTime.getText().toString().trim();
            if (selectedTime.isEmpty()) selectedTime = "ASAP";

            pickupBranch = selectedBranch;
            pickupTime = selectedTime;
            deliveryLocation = "Pick-up at " + selectedBranch + " (" + selectedTime + ")";

            // Check pickup limits
            paymentManager.checkPickup(total, new PaymentManager.PaymentCallback() {
                @Override
                public void onAllowed() {
                    runOnUiThread(() -> {
                        btnConfirm.setEnabled(true);
                        btnConfirm.setAlpha(1f);
                        showQRCode(deliveryLocation);
                    });
                }

                @Override
                public void onBlocked(String reason) {
                    runOnUiThread(() -> {
                        new AlertDialog.Builder(CheckoutActivity.this)
                                .setTitle("Pickup Not Available")
                                .setMessage(reason)
                                .setPositiveButton("OK", (dialog1, which1) -> {
                                    paymentGroup.clearCheck();
                                    btnConfirm.setEnabled(false);
                                    btnConfirm.setAlpha(0.5f);
                                })
                                .show();
                    });
                }
            });
        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            paymentGroup.clearCheck();
        });

        builder.show();
    }

    // ====================== PERMISSION HANDLING ======================
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, get location
                getCurrentLocation();
            } else {
                // Permission denied
                Toast.makeText(this, "Location permission denied. Please use manual input.", Toast.LENGTH_LONG).show();
                paymentGroup.clearCheck();
            }
        }
    }

    // ====================== REST OF THE METHODS ======================
    private void loadCartFromFirebase() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(currentUser.getUid());
        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartList.clear();
                cartItemAddOns.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    String name = itemSnapshot.child("name").getValue(String.class);
                    Double price = itemSnapshot.child("price").getValue(Double.class);
                    Long qty = itemSnapshot.child("quantity").getValue(Long.class);

                    // Load add-ons
                    List<ClientCartFragment.AddonSelection> addOns = new ArrayList<>();
                    if (itemSnapshot.hasChild("addons")) {
                        for (DataSnapshot addonSnapshot : itemSnapshot.child("addons").getChildren()) {
                            ClientCartFragment.AddonSelection addon = addonSnapshot.getValue(ClientCartFragment.AddonSelection.class);
                            if (addon != null) addOns.add(addon);
                        }
                    }

                    if (name != null && price != null && qty != null) {
                        CartItem item = new CartItem(name, qty.intValue(), price);
                        cartList.add(item);

                        String itemKey = itemSnapshot.getKey();
                        if (itemKey != null && !addOns.isEmpty()) {
                            cartItemAddOns.put(itemKey, addOns);
                        }
                    }
                }

                if (cartList.isEmpty()) {
                    Toast.makeText(CheckoutActivity.this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                displayCartItems();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this, "Failed to load cart", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayCartItems() {
        cartContainer.removeAllViews();
        subtotal = 0;

        LayoutInflater inflater = LayoutInflater.from(this);
        List<String> itemKeys = new ArrayList<>(cartItemAddOns.keySet());

        for (int i = 0; i < cartList.size(); i++) {
            CartItem item = cartList.get(i);
            View itemView = inflater.inflate(R.layout.item_cart_checkout, cartContainer, false);

            TextView txtName = itemView.findViewById(R.id.txtItemName);
            TextView txtDescription = itemView.findViewById(R.id.txtItemDescription);
            TextView txtQty = itemView.findViewById(R.id.txtItemQty);
            TextView txtPrice = itemView.findViewById(R.id.txtItemPrice);

            txtName.setText(item.getName());

            // Get add-ons
            List<ClientCartFragment.AddonSelection> addOns = null;
            if (i < itemKeys.size()) {
                String itemKey = itemKeys.get(i);
                addOns = cartItemAddOns.get(itemKey);
            }

            // Display with add-ons
            if (addOns != null && !addOns.isEmpty()) {
                StringBuilder addOnsText = new StringBuilder();
                double itemAddonsTotal = 0;

                for (ClientCartFragment.AddonSelection addon : addOns) {
                    double addonTotal = addon.price * addon.quantity;
                    itemAddonsTotal += addonTotal;
                    addOnsText.append("+ ").append(addon.name);
                    if (addon.quantity > 1) addOnsText.append(" (x").append(addon.quantity).append(")");
                    addOnsText.append(" - ₱").append(String.format("%.2f", addonTotal)).append("\n");
                }

                txtDescription.setText(addOnsText.toString());
                txtDescription.setVisibility(View.VISIBLE);

                double itemBaseTotal = item.getQuantity() * item.getPrice();
                double itemTotal = itemBaseTotal + itemAddonsTotal;

                txtQty.setText("x" + item.getQuantity());
                txtPrice.setText("₱" + String.format("%.2f", itemTotal));
                subtotal += itemTotal;
            } else {
                txtDescription.setVisibility(View.GONE);
                txtQty.setText("x" + item.getQuantity());
                txtPrice.setText("₱" + String.format("%.2f", item.getQuantity() * item.getPrice()));
                subtotal += item.getQuantity() * item.getPrice();
            }

            cartContainer.addView(itemView);
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

    private void showQRCode(String data) {
        try {
            int size = 400;
            BitMatrix bitMatrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            ImageView qrView = new ImageView(this);
            qrView.setImageBitmap(bitmap);

            new AlertDialog.Builder(this)
                    .setTitle("Your Pickup QR Code")
                    .setView(qrView)
                    .setPositiveButton("OK", null)
                    .show();

        } catch (WriterException e) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            gcashImageUri = data.getData();
            Toast.makeText(this, "Payment proof selected", Toast.LENGTH_SHORT).show();
        }
    }

    // ====================== CONFIRM ORDER ======================
    private void confirmOrder() {
        if (selectedPaymentMethod.isEmpty()) {
            Toast.makeText(this, "Select payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate fields based on payment method
        if (selectedPaymentMethod.toLowerCase().contains("gcash")) {
            if (gcashReferenceNumber.isEmpty() || deliveryLocation.isEmpty() || gcashImageUri == null) {
                Toast.makeText(this, "Complete GCash details and upload proof", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (selectedPaymentMethod.toLowerCase().contains("cash")) {
            if (deliveryLocation.isEmpty()) {
                Toast.makeText(this, "Select delivery location", Toast.LENGTH_SHORT).show();
                return;
            }
        } else if (selectedPaymentMethod.toLowerCase().contains("pick")) {
            if (deliveryLocation.isEmpty()) {
                Toast.makeText(this, "Select pickup details", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Prepare order
        List<String> itemsList = new ArrayList<>();
        for (int i = 0; i < cartList.size(); i++) {
            CartItem item = cartList.get(i);
            itemsList.add(item.getName() + " x" + item.getQuantity() +
                    " - ₱" + String.format("%.2f", item.getQuantity() * item.getPrice()));
        }

        // Create order in Firebase
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login", Toast.LENGTH_SHORT).show();
            return;
        }

        OrderModel order = new OrderModel();
        order.setUserId(currentUser.getUid());
        order.setCustomerName(currentUser.getDisplayName() != null ?
                currentUser.getDisplayName() : currentUser.getEmail());
        order.setItems(itemsList);
        order.setTotal_price(total);
        order.setPayment_method(selectedPaymentMethod);
        order.setStatus("Pending");
        order.setOrderType(selectedPaymentMethod.toLowerCase().contains("pick") ? "pickup" : "delivery");
        order.setOrderDate(System.currentTimeMillis());
        order.setDeliveryLocation(deliveryLocation);

        if (selectedPaymentMethod.toLowerCase().contains("gcash")) {
            order.setGcashReferenceNumber(gcashReferenceNumber);
        }

        if (selectedPaymentMethod.toLowerCase().contains("pick")) {
            order.setPickupBranch(pickupBranch);
            order.setPickupTime(pickupTime);
        }

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        String key = ordersRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Error creating order", Toast.LENGTH_SHORT).show();
            return;
        }

        order.setOrderKey(key);
        ordersRef.child(key).setValue(order).addOnSuccessListener(aVoid -> {
            // Clear cart
            if (cartRef != null) {
                cartRef.removeValue();
            }

            // Show receipt
            showReceipt(itemsList, total, selectedPaymentMethod);
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Order failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void showReceipt(List<String> items, double total, String paymentMethod) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("🧾 ORDER RECEIPT\n\n");
        receipt.append("Items:\n");
        for (String item : items) {
            receipt.append("• ").append(item).append("\n");
        }
        receipt.append("\nTotal: ₱").append(String.format("%.2f", total)).append("\n");
        receipt.append("Payment: ").append(paymentMethod).append("\n");
        receipt.append("Location: ").append(deliveryLocation).append("\n");

        if (paymentMethod.toLowerCase().contains("gcash") && !gcashReferenceNumber.isEmpty()) {
            receipt.append("GCash Ref: ").append(gcashReferenceNumber).append("\n");
        }

        new AlertDialog.Builder(this)
                .setTitle("✅ Order Confirmed!")
                .setMessage(receipt.toString())
                .setPositiveButton("OK", (dialog, which) -> finish())
                .show();
    }

    // CartItem inner class
    public class CartItem {
        private String name;
        private int quantity;
        private double price;

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