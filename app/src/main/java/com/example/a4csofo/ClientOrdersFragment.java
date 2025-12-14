package com.example.a4csofo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class ClientOrdersFragment extends Fragment {

    private RecyclerView recyclerOrders;
    private OrdersAdapter ordersAdapter;
    private ArrayList<OrderModel> orderList;
    private DatabaseReference ordersRef;
    private ProgressBar progressBar;
    private LinearLayout emptyLayout;
    private TextView txtEmptyMessage;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    // Track old status to detect changes
    private final HashMap<String, String> orderStatusMap = new HashMap<>();

    // Store listener for cleanup
    private ChildEventListener ordersListener;

    public ClientOrdersFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_client_orders, container, false);

        recyclerOrders = view.findViewById(R.id.recyclerOrders);
        progressBar = view.findViewById(R.id.progressBar);
        emptyLayout = view.findViewById(R.id.emptyLayout);
        txtEmptyMessage = view.findViewById(R.id.txtEmptyMessage);

        recyclerOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        orderList = new ArrayList<>();
        ordersAdapter = new OrdersAdapter(requireContext(), orderList);

        // Adapter click listeners
        ordersAdapter.setOnOrderClickListener(new OrdersAdapter.OnOrderClickListener() {
            @Override
            public void onOrderClick(OrderModel order) { }

            @Override
            public void onViewReceiptClick(OrderModel order) {
                showOrderReceipt(order);
            }

            @Override
            public void onRateFoodClick(OrderModel order) {
                if ("completed".equalsIgnoreCase(order.getStatus())) {
                    openRatingScreen(order);
                } else {
                    Toast.makeText(requireContext(), "You can only rate delivered orders", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelOrderClick(OrderModel order) {
                if (order.canUpdateStatus()) {
                    cancelOrder(order);
                } else {
                    Toast.makeText(requireContext(), "This order cannot be cancelled", Toast.LENGTH_SHORT).show();
                }
            }
        });

        recyclerOrders.setAdapter(ordersAdapter);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            emptyLayout.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("Please log in to see your orders.");
            return view;
        }

        ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        loadUserOrders();

        return view;
    }

    private void loadUserOrders() {
        progressBar.setVisibility(View.VISIBLE);
        orderList.clear();

        if (ordersListener != null) {
            ordersRef.removeEventListener(ordersListener);
        }

        ordersListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                handleOrderSnapshot(snapshot, false);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                handleOrderSnapshot(snapshot, true);
            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String key = snapshot.getKey();
                if (key == null) return;
                for (int i = 0; i < orderList.size(); i++) {
                    if (orderList.get(i).getOrderKey().equals(key)) {
                        orderList.remove(i);
                        orderStatusMap.remove(key);
                        ordersAdapter.updateOrders(orderList);
                        break;
                    }
                }
                updateUI();
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Error loading orders", Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.GONE);
            }
        };

        ordersRef.orderByChild("userId")
                .equalTo(currentUser.getUid())
                .addChildEventListener(ordersListener);
    }

    private void handleOrderSnapshot(DataSnapshot snapshot, boolean isUpdate) {
        try {
            OrderModel order = parseOrderManually(snapshot);
            if (order == null) return;

            String orderKey = order.getOrderKey();
            String newStatus = order.getStatus();

            if (isUpdate) {
                String oldStatus = orderStatusMap.get(orderKey);
                if (oldStatus != null && newStatus != null && !oldStatus.equals(newStatus)) {
                    showStatusToast(orderKey, newStatus);
                }

                boolean found = false;
                for (int i = 0; i < orderList.size(); i++) {
                    if (orderList.get(i).getOrderKey().equals(orderKey)) {
                        orderList.set(i, order);
                        found = true;
                        break;
                    }
                }
                if (!found) orderList.add(order);
            } else {
                boolean exists = false;
                for (OrderModel o : orderList) {
                    if (o.getOrderKey().equals(orderKey)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) orderList.add(order);
            }

            orderStatusMap.put(orderKey, newStatus);
            sortAndUpdateOrders();

        } catch (Exception e) {
            Log.e("ClientOrders", "Error processing order: " + e.getMessage());
        }

        updateUI();
    }

    private void sortAndUpdateOrders() {
        Collections.sort(orderList, new Comparator<OrderModel>() {
            @Override
            public int compare(OrderModel o1, OrderModel o2) {
                return Long.compare(o2.getOrderDate(), o1.getOrderDate());
            }
        });
        ordersAdapter.updateOrders(orderList);
    }

    private void updateUI() {
        progressBar.setVisibility(View.GONE);
        if (orderList.isEmpty()) {
            emptyLayout.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("You have no orders yet.");
        } else {
            emptyLayout.setVisibility(View.GONE);
        }
    }

    private void showStatusToast(String orderKey, String newStatus) {
        String shortId = formatOrderId(orderKey);
        Toast.makeText(requireContext(),
                "Order #" + shortId + " is now " + newStatus,
                Toast.LENGTH_LONG).show();
    }

    private String formatOrderId(String orderKey) {
        if (orderKey == null || orderKey.isEmpty()) return "N/A";
        return orderKey.length() > 6 ? orderKey.substring(0, 6).toUpperCase() : orderKey.toUpperCase();
    }

    private OrderModel parseOrderManually(DataSnapshot snapshot) {
        try {
            OrderModel order = new OrderModel();
            String orderKey = snapshot.getKey();
            if (orderKey == null) return null;
            order.setOrderKey(orderKey);

            String userId = getValue(snapshot, "userId", String.class);
            if (userId == null) userId = getValue(snapshot, "customerUid", String.class);
            if (userId == null || !userId.equals(currentUser.getUid())) return null;

            order.setUserId(userId);
            order.setPayment_method(getValue(snapshot, "payment_method", String.class));
            order.setStatus(getValue(snapshot, "status", String.class));
            order.setOrderType(getValue(snapshot, "orderType", String.class));
            order.setDeliveryLocation(getValue(snapshot, "deliveryLocation", String.class));
            order.setPickupBranch(getValue(snapshot, "pickupBranch", String.class));
            order.setPickupTime(getValue(snapshot, "pickupTime", String.class));
            order.setGcashReferenceNumber(getValue(snapshot, "gcashReferenceNumber", String.class));

            if (snapshot.hasChild("items")) {
                List<String> items = new ArrayList<>();
                for (DataSnapshot itemSnap : snapshot.child("items").getChildren()) {
                    String item = itemSnap.getValue(String.class);
                    if (item != null) items.add(item);
                }
                order.setItems(items);
            }

            order.setTotal_price(parseTotalPrice(snapshot));

            Long orderDate = getValue(snapshot, "orderDate", Long.class);
            if (orderDate != null) order.setOrderDate(orderDate);

            return order;

        } catch (Exception e) {
            Log.e("ClientOrders", "Parse error: " + e.getMessage());
            return null;
        }
    }

    private <T> T getValue(DataSnapshot snapshot, String child, Class<T> type) {
        if (snapshot.hasChild(child)) return snapshot.child(child).getValue(type);
        return null;
    }

    private double parseTotalPrice(DataSnapshot snapshot) {
        if (!snapshot.hasChild("total_price")) return 0.0;
        Object totalObj = snapshot.child("total_price").getValue();
        if (totalObj instanceof Double) return (Double) totalObj;
        if (totalObj instanceof Long) return ((Long) totalObj).doubleValue();
        if (totalObj instanceof Integer) return ((Integer) totalObj).doubleValue();
        if (totalObj instanceof String) {
            try {
                return Double.parseDouble(((String) totalObj).replace("₱","").replace(",","").trim());
            } catch (NumberFormatException e) { return 0.0; }
        }
        return 0.0;
    }

    private void showOrderReceipt(OrderModel order) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("🧾 ORDER RECEIPT\n\n");
        receipt.append("Order ID: ").append(formatOrderId(order.getOrderKey())).append("\n");
        receipt.append("Date: ").append(new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a")
                .format(new java.util.Date(order.getOrderDate()))).append("\n");
        receipt.append("Status: ").append(order.getStatus()).append("\n");
        receipt.append("Payment: ").append(order.getPayment_method()).append("\n");
        receipt.append("Type: ").append(order.getOrderType()).append("\n\n");

        receipt.append("Items:\n");
        if (order.getItems() != null) {
            for (String item : order.getItems()) {
                receipt.append("• ").append(item).append("\n");
            }
        }

        receipt.append("\nTotal: ").append(order.getFormattedTotalForDisplay()).append("\n");

        if (order.getDeliveryLocation() != null && !order.getDeliveryLocation().isEmpty()) {
            receipt.append("Location: ").append(order.getDeliveryLocation()).append("\n");
        }

        if (order.isGcash() && order.getGcashReferenceNumber() != null && !order.getGcashReferenceNumber().isEmpty()) {
            receipt.append("GCash Ref: ").append(order.getGcashReferenceNumber()).append("\n");
        }

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Order Receipt")
                .setMessage(receipt.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private void openRatingScreen(OrderModel order) {
        String[] options = {"Bad", "Okay", "Good", "Very Good", "Excellent"};

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Rate your food")
                .setItems(options, (dialog, which) -> {
                    int ratingValue = which + 1; // 0-based index → 1-5
                    saveOrderRating(order, ratingValue);

                    Toast.makeText(requireContext(),
                            "You rated Order #" + formatOrderId(order.getOrderKey()) +
                                    " with rating " + ratingValue,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveOrderRating(OrderModel order, int rating) {

        if (order.getItems() == null || order.getItems().isEmpty()) return;

        DatabaseReference foodsRef =
                FirebaseDatabase.getInstance().getReference("foods");

        for (String itemName : order.getItems()) {

            String cleanName = itemName.replaceAll("x\\d+", "").trim();

            foodsRef.orderByChild("name")
                    .equalTo(cleanName)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {

                            for (DataSnapshot foodSnap : snapshot.getChildren()) {

                                double currentRating =
                                        foodSnap.child("rating").getValue(Double.class) != null
                                                ? foodSnap.child("rating").getValue(Double.class)
                                                : 0;

                                int ratingCount =
                                        foodSnap.child("ratingCount").getValue(Integer.class) != null
                                                ? foodSnap.child("ratingCount").getValue(Integer.class)
                                                : 0;

                                double newRating =
                                        ((currentRating * ratingCount) + rating)
                                                / (ratingCount + 1);

                                foodSnap.getRef().child("rating").setValue(newRating);
                                foodSnap.getRef().child("ratingCount").setValue(ratingCount + 1);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        // Optional: mark order as rated
        FirebaseDatabase.getInstance()
                .getReference("orders")
                .child(order.getOrderKey())
                .child("rated")
                .setValue(true);
    }

    private void cancelOrder(OrderModel order) {
        String key = order.getOrderKey();
        if (key == null) return;

        new android.app.AlertDialog.Builder(requireContext())
                .setTitle("Cancel Order")
                .setMessage("Are you sure you want to cancel Order #" + formatOrderId(key) + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    // Perform cancellation
                    ordersRef.child(key).child("status").setValue("Cancelled")
                            .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(),
                                    "Order #" + formatOrderId(key) + " cancelled", Toast.LENGTH_SHORT).show())
                            .addOnFailureListener(e -> Toast.makeText(requireContext(),
                                    "Failed to cancel order", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (ordersListener != null) {
            ordersRef.removeEventListener(ordersListener);
        }
    }
}
