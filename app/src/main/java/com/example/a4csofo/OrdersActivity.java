package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;

public class OrdersActivity extends AppCompatActivity {

    private RecyclerView recyclerOrders;
    private OrdersAdapter ordersAdapter;
    private ArrayList<OrderModel> orderList;
    private DatabaseReference ordersRef;
    private ProgressBar progressBar;
    private LinearLayout emptyLayout;
    private TextView txtEmptyMessage;

    private FirebaseAuth auth;
    private FirebaseUser currentUser;

    private BottomNavigationView bottomNavigation;
    private ImageView ivCartIcon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order);

        recyclerOrders = findViewById(R.id.recyclerOrders);
        progressBar = findViewById(R.id.progressBar);
        emptyLayout = findViewById(R.id.emptyLayout);
        txtEmptyMessage = findViewById(R.id.txtEmptyMessage);

        bottomNavigation = findViewById(R.id.bottomNavigation);


        recyclerOrders.setLayoutManager(new LinearLayoutManager(this));
        orderList = new ArrayList<>();
        ordersAdapter = new OrdersAdapter(this, orderList);
        recyclerOrders.setAdapter(ordersAdapter);

        auth = FirebaseAuth.getInstance();
        currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            emptyLayout.setVisibility(View.VISIBLE);
            txtEmptyMessage.setText("Please log in to see your orders.");
            return;
        }

        ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        loadUserOrders();
        setupNavigation();
    }

    private void setupNavigation() {

        bottomNavigation.setSelectedItemId(R.id.nav_orders);

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                startActivity(new Intent(OrdersActivity.this, MainActivity.class));
                return true;
            } else if (itemId == R.id.nav_cart) {
                startActivity(new Intent(OrdersActivity.this, CartActivity.class));
                return true;
            } else if (itemId == R.id.nav_orders) {
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(OrdersActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });

        ivCartIcon.setOnClickListener(v ->
                startActivity(new Intent(OrdersActivity.this, CartActivity.class))
        );
    }


    private void loadUserOrders() {
        progressBar.setVisibility(View.VISIBLE);

        ordersRef.orderByChild("userId").equalTo(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        orderList.clear();

                        for (DataSnapshot orderSnap : snapshot.getChildren()) {
                            OrderModel order = orderSnap.getValue(OrderModel.class);
                            if (order != null) {
                                order.setOrderKey(orderSnap.getKey());
                                orderList.add(order);
                            }
                        }

                        progressBar.setVisibility(View.GONE);

                        if (orderList.isEmpty()) {
                            emptyLayout.setVisibility(View.VISIBLE);
                            txtEmptyMessage.setText("You have no orders yet.");
                        } else {
                            emptyLayout.setVisibility(View.GONE);
                        }

                        ordersAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        progressBar.setVisibility(View.GONE);
                        emptyLayout.setVisibility(View.VISIBLE);
                        txtEmptyMessage.setText("Failed to load orders: " + error.getMessage());
                    }
                });
    }
}
