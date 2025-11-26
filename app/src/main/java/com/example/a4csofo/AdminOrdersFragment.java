package com.example.a4csofo;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
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

    private AdminManageOrdersAdapter adapter;
    private ArrayList<OrderModel> orderList = new ArrayList<>();
    private DatabaseReference ordersRef;

    private static final String TAG = "AdminOrdersFragment";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_orders, container, false);

        initViews(view);
        setupRecycler();
        initFirebase();
        fetchOrders();

        return view;
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerOrders);
        progressBar = view.findViewById(R.id.progressLoadingOrders);
        emptyStateText = view.findViewById(R.id.textNoOrders);
    }

    private void setupRecycler() {
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new AdminManageOrdersAdapter(getContext(), orderList);
        recyclerView.setAdapter(adapter);
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

                updateUI();
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

                    // default fallback values
                    if (order.getStatus() == null) order.setStatus("Pending");
                    if (order.getPayment_method() == null) order.setPayment_method("N/A");
                    if (order.getCustomerName() == null) order.setCustomerName("Unknown");

                    orderList.add(order);
                }

            } catch (Exception e) {
                Log.e(TAG, "Data parsing error", e);
            }
        }
    }

    private void updateUI() {
        showLoading(false);

        if (orderList.isEmpty()) {
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

    // OPTIONAL — ready for status change feature
    public void updateOrderStatus(OrderModel order, String newStatus) {
        if (order == null || order.getOrderKey() == null) return;

        ordersRef.child(order.getOrderKey()).child("status").setValue(newStatus)
                .addOnSuccessListener(unused -> showToast("Status updated"))
                .addOnFailureListener(e -> showToast("Update failed"));
    }
}
