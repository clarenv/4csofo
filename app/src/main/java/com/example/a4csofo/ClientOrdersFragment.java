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

    private final HashMap<String, String> orderStatusMap = new HashMap<>();

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

        // Setup RecyclerView
        recyclerOrders.setLayoutManager(new LinearLayoutManager(requireContext()));
        orderList = new ArrayList<>();
        ordersAdapter = new OrdersAdapter(requireContext(), orderList);

        // Set click listeners
        ordersAdapter.setOnOrderClickListener(new OrdersAdapter.OnOrderClickListener() {
            @Override
            public void onOrderClick(OrderModel order) {
                // Expand/collapse handled in adapter
            }
            @Override
            public void onViewReceiptClick(OrderModel order) {
                showOrderReceipt(order);
            }
        });

        recyclerOrders.setAdapter(ordersAdapter);

        // Firebase
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

        ordersRef.orderByChild("userId").equalTo(currentUser.getUid())
                .addChildEventListener(new ChildEventListener() {
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
                });
    }

    private void handleOrderSnapshot(DataSnapshot snapshot, boolean isUpdate) {
        try {
            OrderModel order = parseOrderManually(snapshot);
            if (order == null) return;

            if (isUpdate) {
                // Check for status change
                String oldStatus = orderStatusMap.get(order.getOrderKey());
                String newStatus = order.getStatus();
                if (oldStatus != null && !oldStatus.equals(newStatus)) {
                    showStatusToast(order.getOrderKey(), newStatus);
                }

                // Update existing order
                boolean found = false;
                for (int i = 0; i < orderList.size(); i++) {
                    if (orderList.get(i).getOrderKey().equals(order.getOrderKey())) {
                        orderList.set(i, order);
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    orderList.add(order);
                }
            } else {
                // Add new order
                orderList.add(order);
            }

            // Save status and update
            orderStatusMap.put(order.getOrderKey(), order.getStatus());
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
        emptyLayout.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void showStatusToast(String orderKey, String newStatus) {
        // Display only first 6 characters of order ID
        String shortId = formatOrderId(orderKey);
        Toast.makeText(requireContext(),
                "Order #" + shortId + " is now " + newStatus,
                Toast.LENGTH_LONG).show();
    }

    // Format order ID to show only first 6 characters
    private String formatOrderId(String orderKey) {
        if (orderKey == null || orderKey.isEmpty()) {
            return "N/A";
        }
        // If order key is longer than 6 characters, show first 6
        if (orderKey.length() > 6) {
            return orderKey.substring(0, 6).toUpperCase();
        }
        return orderKey.toUpperCase();
    }

    private OrderModel parseOrderManually(DataSnapshot snapshot) {
        try {
            OrderModel order = new OrderModel();

            String orderKey = snapshot.getKey();
            if (orderKey == null) return null;
            order.setOrderKey(orderKey);

            // Check user
            String userId = getValue(snapshot, "userId", String.class);
            if (userId == null) {
                userId = getValue(snapshot, "customerUid", String.class);
            }

            if (userId == null || !userId.equals(currentUser.getUid())) {
                return null;
            }

            order.setUserId(userId);

            // REMOVED: setCustomerName - di na kailangan
            order.setPayment_method(getValue(snapshot, "payment_method", String.class));
            order.setStatus(getValue(snapshot, "status", String.class));
            order.setOrderType(getValue(snapshot, "orderType", String.class));
            order.setDeliveryLocation(getValue(snapshot, "deliveryLocation", String.class));
            order.setPickupBranch(getValue(snapshot, "pickupBranch", String.class));
            order.setPickupTime(getValue(snapshot, "pickupTime", String.class));
            order.setGcashReferenceNumber(getValue(snapshot, "gcashReferenceNumber", String.class));

            // Parse items
            if (snapshot.hasChild("items")) {
                List<String> items = new ArrayList<>();
                for (DataSnapshot itemSnap : snapshot.child("items").getChildren()) {
                    String item = itemSnap.getValue(String.class);
                    if (item != null) items.add(item);
                }
                order.setItems(items);
            }

            // Parse total price
            order.setTotal_price(parseTotalPrice(snapshot));

            // Parse order date
            Long orderDate = getValue(snapshot, "orderDate", Long.class);
            if (orderDate != null) {
                order.setOrderDate(orderDate);
            }

            return order;

        } catch (Exception e) {
            Log.e("ClientOrders", "Parse error: " + e.getMessage());
            return null;
        }
    }

    private <T> T getValue(DataSnapshot snapshot, String child, Class<T> type) {
        if (snapshot.hasChild(child)) {
            return snapshot.child(child).getValue(type);
        }
        return null;
    }

    private double parseTotalPrice(DataSnapshot snapshot) {
        if (!snapshot.hasChild("total_price")) return 0.0;

        Object totalObj = snapshot.child("total_price").getValue();
        if (totalObj instanceof Double) {
            return (Double) totalObj;
        } else if (totalObj instanceof Long) {
            return ((Long) totalObj).doubleValue();
        } else if (totalObj instanceof Integer) {
            return ((Integer) totalObj).doubleValue();
        } else if (totalObj instanceof String) {
            try {
                String str = (String) totalObj;
                return Double.parseDouble(str.replace("₱", "").replace(",", "").trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private void showOrderReceipt(OrderModel order) {
        StringBuilder receipt = new StringBuilder();
        receipt.append("🧾 ORDER RECEIPT\n\n");
        receipt.append("Order ID: ").append(formatOrderId(order.getOrderKey())).append("\n");
        // REMOVED: Customer name line
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
}