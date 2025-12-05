package com.example.a4csofo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class AdminOrdersFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView emptyStateText;
    private Spinner spinnerFilterStatus;

    private AdminManageOrdersAdapter adapter;
    private final ArrayList<OrderModel> orderList = new ArrayList<>();
    private final ArrayList<OrderModel> filteredList = new ArrayList<>();
    private DatabaseReference ordersRef;

    private static final String TAG = "AdminOrdersFragment";

    // Sequential status update logic
    private static final ArrayList<String> STATUS_FLOW = new ArrayList<String>() {{
        add("Pending");
        add("Preparing");
        add("Delivering");
        add("Delivered");
    }};
    private static final String STATUS_CANCELLED = "Cancelled";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_orders, container, false);

        initViews(view);
        setupRecycler();
        setupStatusFilter();
        initFirebase();
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
        adapter = new AdminManageOrdersAdapter(getContext(), filteredList, this);
        recyclerView.setAdapter(adapter);
    }

    private void setupStatusFilter() {
        ArrayAdapter<CharSequence> filterAdapter = ArrayAdapter.createFromResource(
                getContext(),
                R.array.order_status_array_filter,
                android.R.layout.simple_spinner_item
        );
        filterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilterStatus.setAdapter(filterAdapter);
        spinnerFilterStatus.setSelection(0); // default to "All"

        spinnerFilterStatus.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = spinnerFilterStatus.getSelectedItem() != null
                        ? spinnerFilterStatus.getSelectedItem().toString()
                        : "All";
                applyFilter(selected);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                applyFilter("All");
            }
        });
    }

    private void initFirebase() {
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");
    }

    private void fetchOrders() {
        showLoading(true);

        ordersRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {

                orderList.clear();

                if (snapshot.exists()) {
                    parseOrders(snapshot);
                }

                String currentFilter = spinnerFilterStatus.getSelectedItem() != null
                        ? spinnerFilterStatus.getSelectedItem().toString()
                        : "All";
                applyFilter(currentFilter);
                showLoading(false);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                showLoading(false);
                showToast("Failed to load orders: " + error.getMessage());
                Log.e(TAG, "Firebase error: ", error.toException());
            }
        });
    }

    private void parseOrders(DataSnapshot snapshot) {
        for (DataSnapshot snap : snapshot.getChildren()) {
            try {
                OrderModel order = snap.getValue(OrderModel.class);
                if (order != null) {
                    order.setOrderKey(snap.getKey());
                    if (order.getStatus() == null || order.getStatus().isEmpty()) order.setStatus("Pending");
                    if (order.getPaymentMethod() == null || order.getPaymentMethod().isEmpty()) order.setPaymentMethod("N/A");
                    if (order.getCustomerName() == null || order.getCustomerName().isEmpty()) order.setCustomerName("Unknown");

                    orderList.add(order);
                } else {
                    Log.w(TAG, "Null OrderModel at key: " + snap.getKey());
                }
            } catch (Exception e) {
                Log.e(TAG, "Data parsing error at key: " + snap.getKey(), e);
            }
        }
    }

    private void applyFilter(String status) {
        if (status == null) status = "All";
        filteredList.clear();

        for (OrderModel order : orderList) {
            if ("All".equalsIgnoreCase(status) || (order.getStatus() != null && order.getStatus().equalsIgnoreCase(status))) {
                filteredList.add(order);
            }
        }

        updateUI();
    }

    private void updateUI() {
        if (adapter == null) return; // safety check

        if (filteredList.isEmpty()) {
            emptyStateText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateText.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
    }

    private void showLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }

    private void showToast(String msg) {
        if (getContext() != null) {
            Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Update order status sequentially: Pending -> Preparing -> Delivering -> Delivered
     * Cancelling allowed except for delivered orders.
     */
    public void updateOrderStatus(OrderModel order, String newStatus) {
        if (order == null || order.getOrderKey() == null || newStatus == null) return;

        String currentStatus = order.getStatus() != null ? order.getStatus().trim() : "Pending";
        newStatus = newStatus.trim();

        // Handle cancel
        if (STATUS_CANCELLED.equalsIgnoreCase(newStatus)) {
            if ("Delivered".equalsIgnoreCase(currentStatus)) {
                showToast("Cannot cancel delivered order");
                return;
            }
            performStatusUpdate(order, STATUS_CANCELLED);
            return;
        }

        int currentIndex = STATUS_FLOW.indexOf(currentStatus);
        int newIndex = STATUS_FLOW.indexOf(newStatus);

        if (currentIndex == -1) currentIndex = 0;

        if (newIndex == currentIndex + 1) {
            performStatusUpdate(order, newStatus);
        } else if (newIndex <= currentIndex) {
            showToast("Cannot move order backward");
        } else {
            showToast("Invalid status update");
        }
    }

    private void performStatusUpdate(OrderModel order, String newStatus) {
        if (order == null || order.getOrderKey() == null) return;

        ordersRef.child(order.getOrderKey()).child("status").setValue(newStatus)
                .addOnSuccessListener(unused -> {
                    order.setStatus(newStatus);

                    // Force spinner to "All" to keep the order visible after status change
                    spinnerFilterStatus.setSelection(0);

                    applyFilter("All"); // refresh filtered list
                    showToast("Status updated to " + newStatus);
                })
                .addOnFailureListener(e -> showToast("Update failed: " + e.getMessage()));
    }
}
