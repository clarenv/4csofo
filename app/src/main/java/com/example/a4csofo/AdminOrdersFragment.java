package com.example.a4csofo;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminOrdersFragment extends Fragment implements AdminManageOrdersAdapter.OrderStatusListener {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private Spinner spinnerFilterStatus;

    private AdminManageOrdersAdapter adapter;
    private final ArrayList<OrderModel> orderList = new ArrayList<>();
    private final ArrayList<OrderModel> filteredOrderList = new ArrayList<>();

    private DatabaseReference ordersRef;
    private DatabaseReference usersRef;

    // Customer name mapping (customerUid -> customerName)
    private Map<String, String> customerNameMap = new HashMap<>();

    private static final String TAG = "AdminOrdersFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_orders, container, false);

        initViews(view);
        setupRecycler();
        setupFilterSpinner();
        initFirebase();

        // LOAD BOTH: users first, then orders
        loadCustomerNames();
        fetchOrders();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerOrders);
        progressBar = view.findViewById(R.id.progressLoadingOrders);
        emptyStateText = view.findViewById(R.id.textNoOrders);
        spinnerFilterStatus = view.findViewById(R.id.spinnerFilterStatus);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminManageOrdersAdapter(getContext(), filteredOrderList, this);

        // Add click listener for order details
        adapter.setOnOrderClickListener(new AdminManageOrdersAdapter.OnOrderClickListener() {
            @Override
            public void onOrderClick(OrderModel order) {
                showOrderDetailsDialog(order);
            }
        });

        recyclerView.setAdapter(adapter);
        Log.d(TAG, "RecyclerView setup complete");
    }

    private void setupFilterSpinner() {
        String[] filterOptions = {
                "All Orders",
                "Pending",
                "Accepted",
                "Preparing",
                "Ready",
                "Delivering",
                "Completed",
                "Cancelled"
        };

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                filterOptions
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterStatus.setAdapter(spinnerAdapter);
        spinnerFilterStatus.setSelection(0);

        spinnerFilterStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedFilter = filterOptions[position];
                Log.d(TAG, "Filter selected: " + selectedFilter);
                filterOrders(selectedFilter);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterOrders("All Orders");
            }
        });

        Log.d(TAG, "Filter spinner setup complete");
    }

    private void initFirebase() {
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        Log.d(TAG, "Firebase initialized");
    }

    // ===== LOAD CUSTOMER NAMES =====
    private void loadCustomerNames() {
        Log.d(TAG, "Loading customer names from Firebase users...");

        usersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                customerNameMap.clear();

                for (DataSnapshot userSnapshot : snapshot.getChildren()) {
                    String uid = userSnapshot.getKey();
                    if (uid == null) continue;

                    try {
                        // Skip boolean values
                        if (userSnapshot.getValue() instanceof Boolean) {
                            continue;
                        }

                        // Try to get name
                        String name = null;
                        if (userSnapshot.child("name").exists()) {
                            name = userSnapshot.child("name").getValue(String.class);
                        } else {
                            try {
                                AdminUserModel user = userSnapshot.getValue(AdminUserModel.class);
                                if (user != null) {
                                    name = user.getName();
                                }
                            } catch (Exception e) {
                                // Ignore
                            }
                        }

                        if (name != null && !name.trim().isEmpty()) {
                            customerNameMap.put(uid, name);
                        } else {
                            customerNameMap.put(uid, "User_" + uid.substring(0, Math.min(6, uid.length())));
                        }

                    } catch (Exception e) {
                        Log.e(TAG, "Error processing user " + uid, e);
                    }
                }

                Log.d(TAG, "Loaded " + customerNameMap.size() + " customer names");

                // Update adapter
                if (adapter != null) {
                    adapter.updateCustomerNames(customerNameMap);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load customer names: " + error.getMessage());
            }
        });
    }

    private void fetchOrders() {
        showLoading(true);
        Log.d(TAG, "Starting to fetch orders from Firebase...");

        ordersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                orderList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                        try {
                            OrderModel order = orderSnapshot.getValue(OrderModel.class);
                            if (order != null) {
                                order.setOrderKey(orderSnapshot.getKey());

                                // Set customerUid if available
                                if (order.getCustomerUid() == null) {
                                    if (orderSnapshot.child("userId").exists()) {
                                        order.setCustomerUid(orderSnapshot.child("userId").getValue(String.class));
                                    }
                                }

                                // Set defaults
                                if (order.getStatus() == null || order.getStatus().isEmpty()) {
                                    order.setStatus("Pending");
                                }
                                if (order.getPayment_method() == null || order.getPayment_method().isEmpty()) {
                                    order.setPayment_method("COD");
                                }
                                if (order.getOrderType() == null || order.getOrderType().isEmpty()) {
                                    order.setOrderType("delivery");
                                }
                                if (order.getOrderDate() == null) {
                                    order.setOrderDate(System.currentTimeMillis());
                                }

                                orderList.add(order);
                            }
                        } catch (Exception e) {
                            Log.e(TAG, "Error parsing order", e);
                        }
                    }

                    // Sort by latest first
                    sortOrdersByLatest();
                    Log.d(TAG, "Loaded " + orderList.size() + " orders");
                }

                // Show all orders by default
                filterOrders("All Orders");
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                Log.e(TAG, "Failed to load orders: " + error.getMessage());
                showToast("Failed to load orders");
            }
        });
    }

    // SORT ORDERS BY LATEST FIRST
    private void sortOrdersByLatest() {
        Collections.sort(orderList, new Comparator<OrderModel>() {
            @Override
            public int compare(OrderModel o1, OrderModel o2) {
                Long date1 = o1.getOrderDate() != null ? o1.getOrderDate() : 0L;
                Long date2 = o2.getOrderDate() != null ? o2.getOrderDate() : 0L;
                return date2.compareTo(date1);
            }
        });
    }

    private void filterOrders(String filter) {
        Log.d(TAG, "Applying filter: " + filter);

        filteredOrderList.clear();

        if (filter.equals("All Orders")) {
            filteredOrderList.addAll(orderList);
        } else {
            String statusToFilter = filter.replace(" Orders", "").trim();
            for (OrderModel order : orderList) {
                String orderStatus = order.getStatus() != null ? order.getStatus() : "Pending";
                if (orderStatus.equalsIgnoreCase(statusToFilter)) {
                    filteredOrderList.add(order);
                }
            }
        }

        // Update adapter
        if (adapter != null) {
            adapter.updateList(filteredOrderList);
        }

        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredOrderList.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            String selectedFilter = spinnerFilterStatus.getSelectedItem() != null
                    ? spinnerFilterStatus.getSelectedItem().toString()
                    : "All Orders";

            if (selectedFilter.equals("All Orders")) {
                emptyStateText.setText("No orders available");
            } else {
                emptyStateText.setText("No '" + selectedFilter + "' orders found");
            }
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showLoading(boolean isLoading) {
        if (progressBar != null) {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        }
        if (isLoading) {
            recyclerView.setVisibility(View.GONE);
            emptyStateText.setVisibility(View.GONE);
        }
    }

    private void showToast(String msg) {
        if (getContext() != null && isAdded()) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    // ===== ORDER DETAILS DIALOG =====
    private void showOrderDetailsDialog(OrderModel order) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();

        // Use the XML layout you provided
        View dialogView = inflater.inflate(R.layout.dialog_order_details, null);
        builder.setView(dialogView);

        // Initialize dialog views
        TextView textOrderId = dialogView.findViewById(R.id.textOrderId);
        TextView textCustomerName = dialogView.findViewById(R.id.textCustomerName);
        TextView textOrderStatus = dialogView.findViewById(R.id.textOrderStatus);
        TextView textTotalPrice = dialogView.findViewById(R.id.textTotalPrice);
        TextView textPaymentMethod = dialogView.findViewById(R.id.textPaymentMethod);
        TextView textOrderDate = dialogView.findViewById(R.id.textOrderDate);
        TextView textOrderType = dialogView.findViewById(R.id.textOrderType);
        LinearLayout containerOrderItems = dialogView.findViewById(R.id.containerOrderItems);
        ImageView imgGcashProof = dialogView.findViewById(R.id.imgGcashProof);
        LinearLayout gcashSection = dialogView.findViewById(R.id.gcashSection);
        Button buttonClose = dialogView.findViewById(R.id.buttonClose);

        // Set order details - SIMPLE ORDER NUMBER
        // Find the position in the filtered list
        int orderNumber = 1;
        for (int i = 0; i < filteredOrderList.size(); i++) {
            if (filteredOrderList.get(i).getOrderKey() != null &&
                    filteredOrderList.get(i).getOrderKey().equals(order.getOrderKey())) {
                orderNumber = i + 1;
                break;
            }
        }
        textOrderId.setText("Order #" + orderNumber);

        textCustomerName.setText(order.getCustomerName() != null ? order.getCustomerName() : "Unknown Customer");
        textOrderStatus.setText(order.getStatus() != null ? order.getStatus().toUpperCase() : "PENDING");

        // Format total price
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        double totalPrice = order.getTotal_price() != null ? order.getTotal_price() : 0.0;
        textTotalPrice.setText(currencyFormat.format(totalPrice));

        textPaymentMethod.setText(order.getPayment_method() != null ? order.getPayment_method().toUpperCase() : "COD");

        // Format order date
        if (order.getOrderDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
            String formattedDate = sdf.format(new Date(order.getOrderDate()));
            textOrderDate.setText(formattedDate);
        } else {
            textOrderDate.setText("Date not available");
        }

        textOrderType.setText(order.getOrderType() != null ? order.getOrderType().toUpperCase() : "DELIVERY");

        // Set status color
        String status = order.getStatus() != null ? order.getStatus().toLowerCase() : "pending";
        switch (status) {
            case "completed":
                textOrderStatus.setTextColor(Color.parseColor("#4CAF50")); // Green
                break;
            case "cancelled":
                textOrderStatus.setTextColor(Color.parseColor("#F44336")); // Red
                break;
            case "pending":
                textOrderStatus.setTextColor(Color.parseColor("#FF9800")); // Orange
                break;
            case "accepted":
                textOrderStatus.setTextColor(Color.parseColor("#2196F3")); // Blue
                break;
            case "preparing":
                textOrderStatus.setTextColor(Color.parseColor("#9C27B0")); // Purple
                break;
            case "ready":
                textOrderStatus.setTextColor(Color.parseColor("#FFC107")); // Yellow
                break;
            case "delivering":
                textOrderStatus.setTextColor(Color.parseColor("#00BCD4")); // Cyan
                break;
            default:
                textOrderStatus.setTextColor(Color.parseColor("#757575")); // Gray
                break;
        }

        // Show GCash proof if payment is GCash
        if (order.isGcash()) {
            gcashSection.setVisibility(View.VISIBLE);
            String gcashProofUrl = order.getGcashProofDownloadUrl();
            if (gcashProofUrl != null && !gcashProofUrl.isEmpty()) {
                // Load GCash proof image using Glide
                Glide.with(requireContext())
                        .load(gcashProofUrl)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_report_image)
                        .into(imgGcashProof);
            } else {
                imgGcashProof.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        } else {
            gcashSection.setVisibility(View.GONE);
        }

        // Clear previous items
        containerOrderItems.removeAllViews();

        // Check if order has items
        List<String> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            // Since items are just List<String>, show them as simple text
            for (String item : items) {
                TextView itemText = new TextView(requireContext());
                itemText.setText("• " + item);
                itemText.setTextSize(14);
                itemText.setPadding(0, 8, 0, 8);
                itemText.setTextColor(Color.parseColor("#212121"));
                containerOrderItems.addView(itemText);
            }

            // Show item count
            TextView itemCountText = new TextView(requireContext());
            itemCountText.setText("Total Items: " + items.size());
            itemCountText.setTextSize(14);
            itemCountText.setTypeface(null, android.graphics.Typeface.BOLD);
            itemCountText.setPadding(0, 16, 0, 8);
            itemCountText.setTextColor(Color.parseColor("#4CAF50"));
            containerOrderItems.addView(itemCountText);
        } else {
            // Show message if no items
            TextView noItemsText = new TextView(requireContext());
            noItemsText.setText("No items found in this order");
            noItemsText.setTextSize(14);
            noItemsText.setPadding(32, 32, 32, 32);
            noItemsText.setGravity(android.view.Gravity.CENTER);
            noItemsText.setTextColor(Color.GRAY);
            containerOrderItems.addView(noItemsText);
        }

        // Create and show dialog
        AlertDialog dialog = builder.create();
        dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        // Close button click
        buttonClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }
    // Implement the OrderStatusListener interface
    @Override
    public void onUpdateStatus(OrderModel order, String newStatus) {
        if (order == null || order.getOrderKey() == null) {
            showToast("Cannot update: Order key is null");
            return;
        }

        Log.d(TAG, "Updating order " + order.getOrderKey() + " to status: " + newStatus);

        ordersRef.child(order.getOrderKey()).child("status").setValue(newStatus)
                .addOnSuccessListener(unused -> {
                    showToast("Status updated to: " + newStatus);

                    // Update in local list
                    for (OrderModel o : orderList) {
                        if (o.getOrderKey() != null && o.getOrderKey().equals(order.getOrderKey())) {
                            o.setStatus(newStatus);
                            Log.d(TAG, "Local order status updated");
                            break;
                        }
                    }

                    // Sort again after update
                    sortOrdersByLatest();

                    // Refresh current filter
                    String selectedFilter = spinnerFilterStatus.getSelectedItem() != null
                            ? spinnerFilterStatus.getSelectedItem().toString()
                            : "All Orders";
                    filterOrders(selectedFilter);
                })
                .addOnFailureListener(e -> {
                    showToast("Update failed: " + e.getMessage());
                    Log.e(TAG, "Status update failed", e);
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up listeners if needed
    }
}