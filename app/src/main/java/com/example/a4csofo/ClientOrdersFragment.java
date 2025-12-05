package com.example.a4csofo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;

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

        // Load orders
        loadUserOrders();

        return view;
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
