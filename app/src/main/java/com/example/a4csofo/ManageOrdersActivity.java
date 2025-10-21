package com.example.a4csofo;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;

public class ManageOrdersActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ManageOrdersAdapter adapter;
    private ArrayList<OrderModel> orderList;
    private DatabaseReference ordersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_orders);

        recyclerView = findViewById(R.id.recyclerOrders);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        orderList = new ArrayList<>();
        adapter = new ManageOrdersAdapter(this, orderList);
        recyclerView.setAdapter(adapter);

        // Firebase reference
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        loadOrdersFromFirebase();
    }

    private void loadOrdersFromFirebase() {
        if (ordersRef == null) {
            Toast.makeText(this, "Firebase reference not available", Toast.LENGTH_SHORT).show();
            return;
        }

        ordersRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                orderList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot orderSnap : snapshot.getChildren()) {
                        try {
                            OrderModel order = orderSnap.getValue(OrderModel.class);

                            if (order != null) {
                                // Save Firebase key
                                order.setOrderKey(orderSnap.getKey());

                                // Ensure default values if fields are null
                                if (order.getStatus() == null) order.setStatus("Pending");
                                if (order.getPayment_method() == null) order.setPayment_method("N/A");
                                if (order.getCustomerName() == null) order.setCustomerName("Unknown");

                                orderList.add(order);
                            }
                        } catch (Exception e) {
                            e.printStackTrace(); // Log error but continue
                        }
                    }
                }

                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManageOrdersActivity.this,
                        "Failed to load orders: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Optional helper method to update status directly from adapter
    public void updateOrderStatus(OrderModel order, String newStatus) {
        if (order == null || order.getOrderKey() == null) return;

        order.updateStatus(newStatus);
        adapter.notifyDataSetChanged(); // Refresh UI
        Toast.makeText(this, "Order status updated to " + newStatus, Toast.LENGTH_SHORT).show();
    }
}
