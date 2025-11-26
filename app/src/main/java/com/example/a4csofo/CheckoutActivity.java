package com.example.a4csofo;

import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;

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
    private static final String SHOP_LOCATION = "Bubukal, Sta. Cruz, Laguna";
    private int nextPickUpNumber = 1;

    // Allowed barangays
    private static final String[] ALLOWED_BARANGAYS = {
            "Patimbao", "Labuin", "San Juan", "Gatid", "Bagumbayan"
    };

    // GCash
    private static final int PICK_IMAGE_REQUEST = 101;
    private String gcashProofBase64 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_checkout);

        auth = FirebaseAuth.getInstance();

        cartContainer = findViewById(R.id.cartContainer);
        txtSubtotal = findViewById(R.id.txtSubtotal);
        txtVat = findViewById(R.id.txtVat);
        txtDelivery = findViewById(R.id.txtDelivery);
        txtTotal = findViewById(R.id.txtTotal);
        paymentGroup = findViewById(R.id.paymentGroup);
        btnConfirm = findViewById(R.id.btnConfirm);

        loadCartFromFirebase();
        prefetchPickUpNumber();

        paymentGroup.setOnCheckedChangeListener((group, checkedId) -> {
            RadioButton selected = findViewById(checkedId);
            if (selected != null) {
                String paymentText = selected.getText().toString();
                if (paymentText.equalsIgnoreCase("GCash")) {
                    showGcashPopup();
                } else if (paymentText.equalsIgnoreCase("Cash on Delivery")) {
                    showCODPopup();
                } else if (paymentText.equalsIgnoreCase("Pick Up")) {
                    showPickUpDialog();
                }
            }
        });

        btnConfirm.setOnClickListener(v -> confirmOrder());
    }

    private void prefetchPickUpNumber() {
        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        ordersRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                nextPickUpNumber = (int) snapshot.getChildrenCount() + 1;
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    // ------------------ COD POPUP ---------------------

    private void showCODPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cod_location, null);

        EditText inputHouse = dialogView.findViewById(R.id.inputHouse);
        EditText inputBarangay = dialogView.findViewById(R.id.inputBarangay);
        TextView txtError = dialogView.findViewById(R.id.txtCodError);
        Button btnConfirmCOD = dialogView.findViewById(R.id.btnCodConfirm);

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();

        inputBarangay.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String barangay = s.toString().trim();
                boolean allowed = false;
                for (String b : ALLOWED_BARANGAYS) {
                    if (b.equalsIgnoreCase(barangay)) { allowed = true; break; }
                }
                if (allowed) {
                    txtError.setText("");
                    btnConfirmCOD.setEnabled(true);
                } else {
                    txtError.setText("COD not available in this barangay.");
                    btnConfirmCOD.setEnabled(false);
                }
            }
            @Override public void afterTextChanged(android.text.Editable s) { }
        });

        btnConfirmCOD.setOnClickListener(v -> {
            String house = inputHouse.getText().toString().trim();
            String barangay = inputBarangay.getText().toString().trim();

            if (house.isEmpty() || barangay.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            deliveryLocation = house + ", " + barangay + ", Santa Cruz, Laguna";
            Toast.makeText(this, "Delivery location saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ------------------ CART LOADING ------------------------

    private void loadCartFromFirebase() {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please log in to view your cart", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cartRef = FirebaseDatabase.getInstance().getReference("carts").child(currentUser.getUid());
        cartRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cartList.clear();
                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    AdminMenuItemsActivity.FoodItem food = itemSnapshot.getValue(AdminMenuItemsActivity.FoodItem.class);
                    if (food != null) {
                        cartList.add(new CartItem(food.name, 1, food.price));
                    }
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
            txtQty.setText("x" + item.getQuantity());
            txtPrice.setText("₱" + String.format("%.2f", item.getPrice() * item.getQuantity()));

            subtotal += item.getPrice() * item.getQuantity();
            cartContainer.addView(itemView);
        }

        vat = subtotal * 0.12;
        total = subtotal + vat + deliveryFee;

        txtSubtotal.setText("₱" + String.format("%.2f", subtotal));
        txtVat.setText("₱" + String.format("%.2f", vat));
        txtDelivery.setText("₱" + String.format("%.2f", deliveryFee));
        txtTotal.setText("₱" + String.format("%.2f", total));
    }

    // ----------------- ORDER CONFIRMATION ------------------------

    private void confirmOrder() {
        int selectedId = paymentGroup.getCheckedRadioButtonId();
        if (selectedId == -1) {
            Toast.makeText(this, "Please select a payment method", Toast.LENGTH_SHORT).show();
            return;
        }

        RadioButton selected = findViewById(selectedId);
        String paymentMethod = selected.getText().toString();

        if (paymentMethod.equalsIgnoreCase("Cash on Delivery") && deliveryLocation.isEmpty()) {
            Toast.makeText(this, "Please enter delivery location", Toast.LENGTH_SHORT).show();
            showCODPopup();
            return;
        }

        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) return;

        List<String> itemsList = new ArrayList<>();
        for (CartItem item : cartList) {
            itemsList.add(item.getName() + " x" + item.getQuantity());
        }

        DatabaseReference ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        OrderModel order = new OrderModel(
                currentUser.getUid(),
                "Customer Name",
                itemsList,
                total,
                paymentMethod,
                "Pending",
                deliveryLocation
        );

        if(paymentMethod.equalsIgnoreCase("GCash")) {
            order.setGcashProof(gcashProofBase64);
        }

        ordersRef.push().setValue(order)
                .addOnSuccessListener(aVoid -> {
                    cartRef.removeValue();

                    if (paymentMethod.equalsIgnoreCase("Pick Up")) {
                        showPickUpDialog();
                    } else {
                        showReceiptPopup(itemsList, total, paymentMethod);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(CheckoutActivity.this, "Failed to place order: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                );
    }

    // ----------------- GCASH ----------------------------

    private void showGcashPopup() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_gcash_payment, null);

        EditText edtGcashNumber = dialogView.findViewById(R.id.edtGcashNumber);
        EditText edtReference = dialogView.findViewById(R.id.edtReference);
        Button btnUpload = dialogView.findViewById(R.id.btnUpload);
        Button btnConfirmGCash = dialogView.findViewById(R.id.btnConfirmGCash);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        btnConfirmGCash.setEnabled(false); // initially disabled

        builder.setView(dialogView);
        AlertDialog dialog = builder.create();
        dialog.show();

        // Enable confirm button only if GCash number valid + (ref OR screenshot)
        android.text.TextWatcher watcher = new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { validateGCash(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
            private void validateGCash() {
                String number = edtGcashNumber.getText().toString().trim();
                String ref = edtReference.getText().toString().trim();
                boolean numberValid = number.matches("^09\\d{9}$"); // PH 11-digit
                boolean refValid = ref.isEmpty() || ref.matches("\\d{13}");
                boolean proofProvided = !gcashProofBase64.isEmpty() || !ref.isEmpty();

                btnConfirmGCash.setEnabled(numberValid && proofProvided && refValid);
            }
        };

        edtGcashNumber.addTextChangedListener(watcher);
        edtReference.addTextChangedListener(watcher);

        btnUpload.setOnClickListener(v -> openImagePicker());

        btnConfirmGCash.setOnClickListener(v -> {
            String gcashNumber = edtGcashNumber.getText().toString().trim();
            String reference = edtReference.getText().toString().trim();

            if (!gcashNumber.matches("^09\\d{9}$")) {
                Toast.makeText(this, "Enter valid PH GCash number", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!reference.isEmpty() && !reference.matches("\\d{13}")) {
                Toast.makeText(this, "Reference number must be 13 digits", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save reference number as proof if no screenshot
            if (!reference.isEmpty()) gcashProofBase64 = reference;

            Toast.makeText(this, "GCash payment info saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
    }

    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        try {
            startActivityForResult(Intent.createChooser(intent, "Select Proof of Payment"), PICK_IMAGE_REQUEST);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "No app found to pick image", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                gcashProofBase64 = encodeImageToBase64(bitmap);
                Toast.makeText(this, "Proof of payment uploaded successfully", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to upload image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String encodeImageToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
        return Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT);
    }

    // ---------------- PICK UP DIALOG WITH QR --------------------

    private void showPickUpDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_pickup, null);
        builder.setView(view);

        Spinner spinnerBranches = view.findViewById(R.id.spinnerBranches);
        RadioGroup radioGroupTime = view.findViewById(R.id.radioGroupTime);
        RadioButton radioExactTime = view.findViewById(R.id.radioExactTime);
        Button btnPickTime = view.findViewById(R.id.btnPickTime);
        Button btnConfirmPickup = view.findViewById(R.id.btnConfirmPickup);

        String[] branches = {"Main Branch", "Downtown Branch", "Mall Branch"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, branches);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBranches.setAdapter(adapter);

        final Calendar selectedTime = Calendar.getInstance();

        radioGroupTime.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.radioExactTime) {
                btnPickTime.setVisibility(View.VISIBLE);
            } else {
                btnPickTime.setVisibility(View.GONE);
            }
        });

        btnPickTime.setOnClickListener(v -> {
            int hour = selectedTime.get(Calendar.HOUR_OF_DAY);
            int minute = selectedTime.get(Calendar.MINUTE);
            TimePickerDialog timePickerDialog = new TimePickerDialog(this, (view1, hourOfDay, minute1) -> {
                selectedTime.set(Calendar.HOUR_OF_DAY, hourOfDay);
                selectedTime.set(Calendar.MINUTE, minute1);
                Toast.makeText(this, "Selected time: " + hourOfDay + ":" + String.format("%02d", minute1), Toast.LENGTH_SHORT).show();
            }, hour, minute, false);
            timePickerDialog.show();
        });

        AlertDialog dialog = builder.create();

        btnConfirmPickup.setOnClickListener(v -> {
            String branch = spinnerBranches.getSelectedItem().toString();
            String time = radioExactTime.isChecked()
                    ? String.format("%02d:%02d", selectedTime.get(Calendar.HOUR_OF_DAY), selectedTime.get(Calendar.MINUTE))
                    : "ASAP";

            int orderNumber = nextPickUpNumber;
            nextPickUpNumber++;

            showPickUpQRCode(orderNumber, branch, time);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showPickUpQRCode(int orderNumber, String branch, String time) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_pickup_qr, null);

        ImageView qrImage = view.findViewById(R.id.qrImage);
        TextView txtInfo = view.findViewById(R.id.txtPickupInfo);
        Button btnDone = view.findViewById(R.id.btnDone);

        txtInfo.setText("Order Number: #" + orderNumber + "\nBranch: " + branch + "\nPick-Up Time: " + time);

        try {
            com.google.zxing.Writer writer = new com.google.zxing.qrcode.QRCodeWriter();
            com.google.zxing.common.BitMatrix bitMatrix = writer.encode(
                    "Order#" + orderNumber + "|" + branch + "|" + time,
                    com.google.zxing.BarcodeFormat.QR_CODE,
                    512, 512
            );

            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565);
            for (int x = 0; x < 512; x++) {
                for (int y = 0; y < 512; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
                }
            }
            qrImage.setImageBitmap(bitmap);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show();
        }

        AlertDialog dialog = builder.setView(view).create();
        btnDone.setOnClickListener(v -> {
            dialog.dismiss();
            Intent intent = new Intent(CheckoutActivity.this, OrdersActivity.class);
            startActivity(intent);
            finish();
        });

        dialog.show();
    }

    // ---------------- RECEIPT -----------------------

    private void showReceiptPopup(List<String> items, double total, String paymentMethod) {
        double vat = total * 0.12 / 1.12;
        double subtotal = total - vat - 30;

        StringBuilder receiptBuilder = new StringBuilder();
        receiptBuilder.append("🧾  OFFICIAL RECEIPT\n\n");
        receiptBuilder.append("A4CSOFO FOOD SERVICES\n");
        receiptBuilder.append(SHOP_LOCATION + "\n");
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("ITEMS\n");

        for (String item : items) {
            receiptBuilder.append("• ").append(item).append("\n");
        }

        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("Subtotal:        ₱%.2f\n", subtotal));
        receiptBuilder.append(String.format("VAT (12%%):       ₱%.2f\n", vat));
        receiptBuilder.append(String.format("Delivery Fee:    ₱%.2f\n", 30.00));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append(String.format("TOTAL DUE:       ₱%.2f\n", total));
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("Payment Method: " + paymentMethod + "\n");

        if (!deliveryLocation.isEmpty())
            receiptBuilder.append("Delivery Address: " + deliveryLocation + "\n");

        receiptBuilder.append("Cashier: SYSTEM AUTO\n");
        receiptBuilder.append("Date/Time: " + java.text.DateFormat.getDateTimeInstance().format(new java.util.Date()) + "\n");
        receiptBuilder.append("--------------------------------------------\n");
        receiptBuilder.append("✅ THANK YOU FOR YOUR PURCHASE!\n");

        new AlertDialog.Builder(this)
                .setTitle("Order Confirmed")
                .setMessage(receiptBuilder.toString())
                .setCancelable(false)
                .setPositiveButton("OK", (dialog, which) -> {
                    Intent intent = new Intent(CheckoutActivity.this, OrdersActivity.class);
                    startActivity(intent);
                    finish();
                })
                .show();
    }

    // ---------------- CART ITEM CLASS ----------------------

    public static class CartItem {
        private String name;
        private int quantity;
        private double price;

        public CartItem() { }

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
