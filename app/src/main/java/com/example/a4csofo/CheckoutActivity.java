package com.example.a4csofo;

import android.Manifest;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.*;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.util.*;
import androidx.annotation.Nullable;

public class CheckoutActivity extends AppCompatActivity {

    private LinearLayout cartContainer;
    private TextView txtSubtotal, txtVat, txtDelivery, txtTotal;
    private RadioGroup paymentGroup;
    private Button btnConfirm;

    private ArrayList<CartItem> cartList = new ArrayList<>();
    private double subtotal = 0, vat = 0, deliveryFee = 30, total = 0;

    private FirebaseAuth auth;
    private DatabaseReference cartRef;

    private String deliveryLocation = "";
    private FusedLocationProviderClient fusedLocationClient;

    private final List<String> allowedBarangays = Arrays.asList(
            "bubukal",
            "calios",
            "bagumbayan",
            "gatid"
    );

    private final int LOCATION_REQUEST_CODE = 1001;

    private static final int REQ_CODE_PICK_IMAGE = 2001;
    private Uri gcashImageUri = null;
    private String gcashReferenceNumber = "";
    private String gcashProofDownloadUrl = "";

    private String pickupBranch = "";
    private String pickupTime = "";
    private String orderType = "delivery";
    private ImageView imgPreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        auth = FirebaseAuth.getInstance();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        cartContainer = findViewById(R.id.cartContainer);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtVat = findViewById(R.id.txtVat);
        txtDelivery = findViewById(R.id.txtDelivery);
        txtTotal = findViewById(R.id.txtTotal);
        paymentGroup = findViewById(R.id.paymentGroup);
        btnConfirm = findViewById(R.id.btnConfirm);

        // DEBUG: Check auth status
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User is not logged in", Toast.LENGTH_LONG).show();
            // Option: Redirect to login or use test mode
        } else {
            Toast.makeText(this, "Logged in as: " + currentUser.getEmail(), Toast.LENGTH_SHORT).show();
        }

        loadCartFromFirebase();

        btnConfirm.setEnabled(false);
        btnConfirm.setAlpha(0.5f);

        btnConfirm.setOnClickListener(v -> confirmOrder());

        paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = findViewById(checkedId);

            if (selected != null) {
                String paymentText = selected.getText().toString().toLowerCase().trim();

                if (paymentText.contains("gcash")) {
                    showGcashCombinedDialog();
                    btnConfirm.setEnabled(false);
                    btnConfirm.setAlpha(0.5f);
                } else if (paymentText.contains("cash")) {
                    showDeliveryLocationDialog();
                } else if (paymentText.contains("pick")) {
                    showPickupOptionsDialog();
                }
            }
        });

    }

    private void loadCartFromFirebase() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login first", Toast.LENGTH_LONG).show();
            finish(); // Or redirect to login
            return;
        }

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(currentUser.getUid());

        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartList.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    String name = itemSnapshot.child("name").getValue(String.class);
                    Double price = itemSnapshot.child("price").getValue(Double.class);
                    Long qty = itemSnapshot.child("quantity").getValue(Long.class);

                    if (name != null && price != null && qty != null) {
                        cartList.add(new CartItem(name, qty.intValue(), price));
                    }
                }

                if (cartList.isEmpty()) {
                    Toast.makeText(CheckoutActivity.this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                loadCart();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(CheckoutActivity.this, "Failed to load cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadCart() {
        cartContainer.removeAllViews();
        subtotal = 0;

        LayoutInflater inflater = LayoutInflater.from(this);

        for (CartItem item : cartList) {
            View itemView = inflater.inflate(R.layout.item_cart_checkout, cartContainer, false);

            TextView txtName = itemView.findViewById(R.id.txtItemName);
            TextView txtQty = itemView.findViewById(R.id.txtItemQty);
            TextView txtPrice = itemView.findViewById(R.id.txtItemPrice);

            txtName.setText(item.getName());
            txtQty.setText(String.valueOf(item.getQuantity()));
            txtPrice.setText("₱" + String.format("%.2f", item.getQuantity() * item.getPrice()));

            cartContainer.addView(itemView);

            subtotal += item.getQuantity() * item.getPrice();
        }

        updateTotals();
    }

    private void updateTotals() {
        subtotal = 0;
        for (CartItem item : cartList) {
            subtotal += item.getQuantity() * item.getPrice();
        }
        vat = subtotal * 0.12;
        total = subtotal + vat + deliveryFee;

        txtSubtotal.setText("₱" + String.format("%.2f", subtotal));
        txtVat.setText("₱" + String.format("%.2f", vat));
        txtDelivery.setText("₱" + String.format("%.2f", deliveryFee));
        txtTotal.setText("₱" + String.format("%.2f", total));
    }

    // Helper method to check if payment is pickup
    private boolean isPickupPayment(String paymentMethod) {
        if (paymentMethod == null) return false;
        String lower = paymentMethod.toLowerCase();
        return lower.contains("pick") || lower.contains("pickup") || lower.contains("pick-up");
    }

    // Helper method to get customer name - SIMPLIFIED FOR TESTING
    private String getCustomerName(FirebaseUser user) {
        // TEMPORARY: Always return "Customer" for testing
        return "Customer";
    }

    private void confirmOrder() {
        int selectedId = paymentGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selected = findViewById(selectedId);
        String paymentMethod = selected.getText().toString();

        // DEBUG: Show what payment method was selected
        Toast.makeText(this, "Payment: " + paymentMethod, Toast.LENGTH_SHORT).show();

        // For COD and GCash, check if location is allowed
        if ((paymentMethod.equalsIgnoreCase("cash on delivery") || paymentMethod.equalsIgnoreCase("gcash"))
                && !isValidBarangay(deliveryLocation)) {
            Toast.makeText(this, "Your location is not allowed to order.", Toast.LENGTH_LONG).show();
            return;
        }

        // For GCash, check if all fields are filled
        if (paymentMethod.equalsIgnoreCase("gcash")) {
            if (gcashReferenceNumber.trim().isEmpty() || gcashProofDownloadUrl.isEmpty() || deliveryLocation.isEmpty()) {
                Toast.makeText(this, "Complete all GCash payment fields before confirming.", Toast.LENGTH_LONG).show();
                return;
            }
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not logged in. Cannot place order.", Toast.LENGTH_LONG).show();
            return;
        }

        List<String> itemsList = new ArrayList<>();
        for (CartItem item : cartList) {
            itemsList.add(item.getName() + " x" + item.getQuantity());
        }

        long orderDate = System.currentTimeMillis();

        // Determine order type
        boolean isPickup = isPickupPayment(paymentMethod);
        String finalOrderType = isPickup ? "pickup" : "delivery";

        // DEBUG: Show order type
        Toast.makeText(this, "Order Type: " + finalOrderType, Toast.LENGTH_SHORT).show();

        // Get customer name - TEMPORARY FIX
        String customerName = getCustomerName(currentUser);

        // Use actual user ID
        String userId = currentUser.getUid();

        // IMPORTANT: Create OrderModel with correct constructor parameters
        // MUST match OrderModel constructor exactly
        OrderModel order = new OrderModel(
                userId,                      // String userId
                customerName,                // String customer_name
                itemsList,                   // List<String> items
                total,                       // double total_price
                paymentMethod,               // String payment_method
                "Pending",                   // String status
                finalOrderType,              // String orderType
                orderDate                    // long orderDate
        );

        // Set additional fields based on order type
        if (isPickup) {
            // For pickup orders
            order.setPickupBranch(pickupBranch != null ? pickupBranch : "");
            order.setPickupTime(pickupTime != null ? pickupTime : "");
            order.setDeliveryLocation("Pick-up at " + pickupBranch + " (" + pickupTime + ")");

            // DEBUG: Show pickup details
            Toast.makeText(this,
                    "Pickup Details Set:\n" +
                            "Branch: " + pickupBranch + "\n" +
                            "Time: " + pickupTime,
                    Toast.LENGTH_LONG).show();
        } else {
            // For delivery orders
            order.setDeliveryLocation(deliveryLocation);
        }

        // GCash fields (if applicable)
        if (paymentMethod.equalsIgnoreCase("gcash")) {
            order.setGcashReferenceNumber(gcashReferenceNumber != null ? gcashReferenceNumber : "");
            order.setGcashProofDownloadUrl(gcashProofDownloadUrl != null ? gcashProofDownloadUrl : "");
        }

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        String key = ordersRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Failed to generate order key.", Toast.LENGTH_SHORT).show();
            return;
        }
        order.setOrderKey(key);

        // DEBUG: Show order details before saving
        Toast.makeText(this,
                "Saving Order to Firebase...\n" +
                        "Order ID: " + key + "\n" +
                        "Customer: " + customerName + "\n" +
                        "Total: ₱" + total,
                Toast.LENGTH_LONG).show();

        // Save to Firebase
        ordersRef.child(key).setValue(order)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "✅ Order placed successfully!", Toast.LENGTH_LONG).show();

                    // Clear cart
                    if (cartRef != null) {
                        cartRef.removeValue()
                                .addOnSuccessListener(aVoid1 -> {
                                    Toast.makeText(CheckoutActivity.this, "Cart cleared", Toast.LENGTH_SHORT).show();
                                });
                    }

                    // Show receipt
                    showReceiptPopup(itemsList, this.total, paymentMethod);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(CheckoutActivity.this, "❌ Failed to place order: " + e.getMessage(), Toast.LENGTH_LONG).show();

                    // DEBUG: More detailed error
                    if (e.getMessage().contains("permission") || e.getMessage().contains("security")) {
                        Toast.makeText(CheckoutActivity.this,
                                "Firebase Security Rules Issue!\n" +
                                        "Check Firebase Database Rules",
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private String cleanText(String text) {
        if (text == null) return "";
        String s = text.toLowerCase()
                .replaceAll("[\\.,]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        s = s.replace("barangay ", "")
                .replace("barangay", "")
                .replace("brgy ", "")
                .replace("brgy", "")
                .replace("bgy ", "")
                .replace("bgy", "")
                .replace("purok ", "")
                .replace("purok", "")
                .replace("sitio ", "")
                .replace("sitio", "")
                .trim();
        s = s.replaceAll("\\s+", " ").trim();
        return s;
    }

    private boolean isValidBarangay(String location) {
        if (location == null || location.isEmpty()) return false;
        String cleaned = cleanText(location);
        boolean containsSantaCruz = cleaned.contains("santa cruz") || cleaned.contains("sta cruz") || cleaned.contains("santa-cruz");
        if (!containsSantaCruz) return false;
        for (String barangay : allowedBarangays) {
            if (cleaned.contains(barangay.toLowerCase())) return true;
        }
        return false;
    }

    private void validateDeliveryLocation(String location) {
        boolean valid = isValidBarangay(location);
        btnConfirm.setEnabled(valid);
        btnConfirm.setAlpha(valid ? 1f : 0.5f);
    }

    private void showDeliveryLocationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Delivery Location");

        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_delivery_options, null);

        RadioButton rbCurrent = dialogView.findViewById(R.id.rbCurrentLocation);
        RadioButton rbSaved = dialogView.findViewById(R.id.rbSavedAddress);
        RadioButton rbManual = dialogView.findViewById(R.id.rbManualInput);
        EditText edtManual = dialogView.findViewById(R.id.edtManualAddress);
        edtManual.setEnabled(false);

        builder.setView(dialogView);

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users")
                    .child(user.getUid()).child("address");
            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String saved = snapshot.getValue(String.class);
                    if (saved != null && !saved.isEmpty())
                        rbSaved.setText("Saved Address: " + saved);
                    else rbSaved.setEnabled(false);
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }

        rbCurrent.setOnClickListener(v -> {
            edtManual.setEnabled(false);
            orderType = "delivery";
            requestCurrentLocation();
        });

        rbSaved.setOnClickListener(v -> {
            edtManual.setEnabled(false);
            orderType = "delivery";
            deliveryLocation = rbSaved.getText().toString().replace("Saved Address: ", "");
            validateDeliveryLocation(deliveryLocation);
        });

        rbManual.setOnClickListener(v -> {
            edtManual.setEnabled(true);
            edtManual.requestFocus();
            orderType = "delivery";
            deliveryLocation = edtManual.getText().toString();
            validateDeliveryLocation(deliveryLocation);
        });

        edtManual.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (rbManual.isChecked()) {
                    deliveryLocation = s.toString();
                    validateDeliveryLocation(deliveryLocation);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        builder.setPositiveButton("Save", (dialog, which) -> {
            if (!deliveryLocation.isEmpty())
                Toast.makeText(this, "Delivery location saved!", Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dlg = builder.create();
        dlg.setCanceledOnTouchOutside(true);
        dlg.show();
    }

    private void requestCurrentLocation() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_REQUEST_CODE);
            return;
        }

        try {
            CancellationTokenSource cts = new CancellationTokenSource();
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                    .addOnSuccessListener(location -> {
                        if (location == null) {
                            Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Geocoder geocoder = new Geocoder(this, Locale.getDefault());

                        try {
                            List<Address> list = geocoder.getFromLocation(
                                    location.getLatitude(),
                                    location.getLongitude(),
                                    3
                            );

                            if (list == null || list.isEmpty()) {
                                Toast.makeText(this, "Unable to read address.", Toast.LENGTH_SHORT).show();
                                return;
                            }

                            Address a = list.get(0);

                            String barangay = safeTrim(a.getSubLocality());
                            String town = safeTrim(a.getLocality());
                            String province = safeTrim(a.getAdminArea());
                            String feature = safeTrim(a.getFeatureName());
                            String addressLine = "";
                            try {
                                addressLine = a.getAddressLine(0);
                            } catch (Exception ignored) {
                            }

                            if (isEmpty(barangay)) {
                                if (!isEmpty(feature)) barangay = feature;
                                else {
                                    String lower = (addressLine != null ? addressLine.toLowerCase() : "");
                                    for (String b : allowedBarangays) {
                                        if (lower.contains(b.toLowerCase())) {
                                            barangay = b;
                                            break;
                                        }
                                    }
                                }
                            }

                            StringBuilder finalAddress = new StringBuilder();
                            if (!isEmpty(barangay)) finalAddress.append(barangay).append(" ");
                            if (!isEmpty(town)) finalAddress.append(town).append(" ");
                            if (!isEmpty(province)) finalAddress.append(province);

                            deliveryLocation = finalAddress.toString().trim();
                            if (deliveryLocation.isEmpty()) {
                                deliveryLocation = (addressLine != null ? addressLine : "");
                            }

                            validateDeliveryLocation(deliveryLocation);

                            if (!btnConfirm.isEnabled()) {
                                Toast.makeText(
                                        this,
                                        "Your current location (" + deliveryLocation + ") is NOT allowed.\n\n" +
                                                "Allowed Barangays:\n" + allowedBarangays,
                                        Toast.LENGTH_LONG
                                ).show();
                            } else {
                                Toast.makeText(this, "Location Detected: " + deliveryLocation, Toast.LENGTH_SHORT).show();
                            }

                        } catch (IOException e) {
                            Toast.makeText(this, "Failed to decode location.", Toast.LENGTH_SHORT).show();
                        }

                    }).addOnFailureListener(e -> {
                        Toast.makeText(this, "Unable to get current location.", Toast.LENGTH_SHORT).show();
                    });
        } catch (Exception e) {
            Toast.makeText(this, "Location error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private String safeTrim(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                requestCurrentLocation();
            else Toast.makeText(this, "Location permission denied.", Toast.LENGTH_SHORT).show();
        }
    }

    private void showPickupOptionsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Pick-Up Options");

        View view = getLayoutInflater().inflate(R.layout.dialog_pickup_options, null);

        Spinner branchSpinner = view.findViewById(R.id.spinnerBranch);
        EditText edtPickupTime = view.findViewById(R.id.edtPickupTime);
        TextView txtInstructions = view.findViewById(R.id.txtPickupInstructions);

        List<String> branches = Arrays.asList("Bubukal Sta Cruz Laguna");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, branches);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        branchSpinner.setAdapter(adapter);

        txtInstructions.setText("Bring your order reference or QR code when collecting your order at the selected branch.");

        builder.setView(view);

        builder.setPositiveButton("Save", null);
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.setCanceledOnTouchOutside(true);

        edtPickupTime.setFocusable(false);
        edtPickupTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            int hour = now.get(Calendar.HOUR_OF_DAY);
            int minute = now.get(Calendar.MINUTE);
            TimePickerDialog tpd = new TimePickerDialog(this, (view1, hourOfDay, minute1) -> {
                String hh = String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute1);
                edtPickupTime.setText(hh);
            }, hour, minute, false);
            tpd.show();
        });

        dialog.setOnShowListener(d -> {
            Button b = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            b.setOnClickListener(v -> {
                String selectedBranch = branchSpinner.getSelectedItem() != null ? branchSpinner.getSelectedItem().toString() : "Main Branch";
                String selectedPickupTime = edtPickupTime.getText().toString().trim();
                if (selectedPickupTime.isEmpty()) selectedPickupTime = "ASAP";

                pickupBranch = selectedBranch;
                pickupTime = selectedPickupTime;
                orderType = "pickup";  // Set order type to pickup
                deliveryLocation = "Pick-up at " + selectedBranch + " (" + selectedPickupTime + ")";

                Toast.makeText(this, "✅ Pick-Up Selected:\n" + deliveryLocation, Toast.LENGTH_LONG).show();

                btnConfirm.setEnabled(true);
                btnConfirm.setAlpha(1f);

                dialog.dismiss();

                // Show QR code for pickup
                showQRCodeDialog(deliveryLocation);
            });
        });

        dialog.show();
    }

    private void showQRCodeDialog(String data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Your Pickup QR Code");

        ImageView qrView = new ImageView(this);

        try {
            int size = 400; // Reduced size for better performance
            BitMatrix bitMatrix = new MultiFormatWriter().encode(data, BarcodeFormat.QR_CODE, size, size);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);

            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }

            qrView.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
            return;
        }

        TextView instructions = new TextView(this);
        instructions.setText("Screenshot this QR code and bring it to the branch to collect your order.");
        instructions.setPadding(20, 20, 20, 20);
        instructions.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);
        layout.addView(qrView);
        layout.addView(instructions);

        builder.setView(layout);
        builder.setPositiveButton("OK", (d, which) -> {
            // After QR code shown, user can click confirm
            Toast.makeText(this, "Now click the CONFIRM ORDER button", Toast.LENGTH_SHORT).show();
            d.dismiss();
        });
        builder.show();
    }

    private void showGcashCombinedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("GCash Payment");

        View view = getLayoutInflater().inflate(R.layout.dialog_gcash_combined, null);

        ImageView qrImage = view.findViewById(R.id.qrImage);
        RadioButton rbCurrent = view.findViewById(R.id.rbCurrentLocation);
        RadioButton rbSaved = view.findViewById(R.id.rbSavedAddress);
        RadioButton rbManual = view.findViewById(R.id.rbManualInput);
        EditText edtManual = view.findViewById(R.id.edtManualAddress);

        imgPreview = view.findViewById(R.id.imgPreview);
        EditText edtRef = view.findViewById(R.id.edtReferenceNumber);

        Button btnSelect = view.findViewById(R.id.btnChooseImage);
        Button btnSave = view.findViewById(R.id.btnSavePayment);

        edtManual.setEnabled(false);
        builder.setView(view);

        AlertDialog dialog = builder.create();

        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(user.getUid())
                    .child("address");

            userRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    String saved = snapshot.getValue(String.class);
                    if (saved != null && !saved.isEmpty()) {
                        rbSaved.setText("Saved Address: " + saved);
                        rbSaved.setEnabled(true);
                    } else {
                        rbSaved.setEnabled(false);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });
        }

        rbCurrent.setOnClickListener(v -> {
            edtManual.setEnabled(false);
            orderType = "delivery";
            requestCurrentLocation();
        });

        rbSaved.setOnClickListener(v -> {
            edtManual.setEnabled(false);
            orderType = "delivery";

            deliveryLocation = rbSaved.getText().toString().replace("Saved Address: ", "");
            validateDeliveryLocation(deliveryLocation);
        });

        rbManual.setOnClickListener(v -> {
            edtManual.setEnabled(true);
            edtManual.requestFocus();
            orderType = "delivery";

            deliveryLocation = edtManual.getText().toString();
            validateDeliveryLocation(deliveryLocation);
        });

        edtManual.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (rbManual.isChecked()) {
                    deliveryLocation = s.toString();
                    validateDeliveryLocation(deliveryLocation);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        qrImage.setOnClickListener(v -> {
            AlertDialog.Builder builderZoom = new AlertDialog.Builder(CheckoutActivity.this);
            ImageView zoomImg = new ImageView(CheckoutActivity.this);
            zoomImg.setImageDrawable(qrImage.getDrawable());

            builderZoom.setView(zoomImg);
            builderZoom.setPositiveButton("Close", (d, w) -> d.dismiss());
            builderZoom.show();
        });

        btnSelect.setOnClickListener(v -> {
            Intent pickIntent = new Intent(Intent.ACTION_PICK);
            pickIntent.setType("image/*");
            startActivityForResult(pickIntent, REQ_CODE_PICK_IMAGE);
        });

        if (gcashImageUri != null) {
            imgPreview.setImageURI(gcashImageUri);
            imgPreview.setVisibility(View.VISIBLE);
        }

        btnSave.setOnClickListener(v -> {

            if (deliveryLocation == null || deliveryLocation.isEmpty()) {
                Toast.makeText(this, "Please select delivery location.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!isValidBarangay(deliveryLocation)) {
                Toast.makeText(this, "Delivery not allowed for this barangay.", Toast.LENGTH_SHORT).show();
                return;
            }

            gcashReferenceNumber = edtRef.getText().toString().trim();
            if (gcashReferenceNumber.isEmpty()) {
                Toast.makeText(this, "Enter reference number.", Toast.LENGTH_SHORT).show();
                return;
            }

            if (gcashImageUri == null) {
                Toast.makeText(this, "Please upload payment screenshot.", Toast.LENGTH_SHORT).show();
                return;
            }

            StorageReference ref = FirebaseStorage.getInstance()
                    .getReference("gcash_proofs/" + System.currentTimeMillis() + ".jpg");

            ref.putFile(gcashImageUri)
                    .addOnSuccessListener(taskSnapshot ->
                            ref.getDownloadUrl().addOnSuccessListener(uri -> {

                                gcashProofDownloadUrl = uri.toString();

                                Toast.makeText(this, "✅ GCash payment saved!", Toast.LENGTH_LONG).show();

                                btnConfirm.setEnabled(true);
                                btnConfirm.setAlpha(1f);

                                dialog.dismiss();
                            })
                    )
                    .addOnFailureListener(e ->
                            Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        });

        dialog.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            gcashImageUri = data.getData();

            if (imgPreview != null) {
                imgPreview.setImageURI(gcashImageUri);
                imgPreview.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showReceiptPopup(List<String> items, double total, String paymentMethod) {
        if (items == null) items = new ArrayList<>();
        if (paymentMethod == null) paymentMethod = "N/A";
        if (deliveryLocation == null) deliveryLocation = "";
        if (gcashReferenceNumber == null) gcashReferenceNumber = "";
        if (gcashProofDownloadUrl == null) gcashProofDownloadUrl = "";

        double localSubtotal = this.subtotal;
        double localVat = this.vat;

        StringBuilder receiptBuilder = new StringBuilder();
        receiptBuilder.append("🧾  OFFICIAL RECEIPT\n\n");
        receiptBuilder.append("A4CSOFO FOOD SERVICES\n");
        receiptBuilder.append("Bubukal Sta Cruz Laguna\n");
        receiptBuilder.append("VAT Reg TIN: 123-456-789-000\n");
        receiptBuilder.append("--------------------------------------------\n");

        receiptBuilder.append("ITEMS\n");
        if (items.isEmpty()) {
            receiptBuilder.append("• No items\n");
        } else {
            for (String item : items) {
                receiptBuilder.append("• ").append(item).append("\n");
            }
        }

        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("Subtotal:        ₱%.2f\n", localSubtotal));
        receiptBuilder.append(String.format("VAT (12%%):       ₱%.2f\n", localVat));
        receiptBuilder.append(String.format("Delivery Fee:    ₱%.2f\n", deliveryFee));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("TOTAL DUE:       ₱%.2f\n", total));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("Payment Method: " + paymentMethod + "\n");

        if (!deliveryLocation.isEmpty())
            receiptBuilder.append("Delivery Address: " + deliveryLocation + "\n");
        if (!gcashReferenceNumber.isEmpty())
            receiptBuilder.append("GCash Ref#: " + gcashReferenceNumber + "\n");
        if (!gcashProofDownloadUrl.isEmpty())
            receiptBuilder.append("GCash Proof URL: " + gcashProofDownloadUrl + "\n");

        receiptBuilder.append("Cashier: SYSTEM AUTO\n");
        receiptBuilder.append("Date/Time: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n");
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("✅ THANK YOU FOR YOUR PURCHASE!\n");
        receiptBuilder.append("Order saved to Firebase!\n");

        new AlertDialog.Builder(this)
                .setTitle("✅ Order Confirmed!")
                .setMessage(receiptBuilder.toString())
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    setResult(RESULT_OK);
                    finish();
                })
                .show();
    }

    public class CartItem {
        private String name;
        private int quantity;
        private double price;

        public CartItem() {
        }

        public CartItem(String name, int quantity, double price) {
            this.name = name;
            this.quantity = quantity;
            this.price = price;
        }

        public String getName() {
            return name;
        }

        public int getQuantity() {
            return quantity;
        }

        public double getPrice() {
            return price;
        }

        public void setQuantity(int quantity) {
            this.quantity = quantity;
        }
    }
}