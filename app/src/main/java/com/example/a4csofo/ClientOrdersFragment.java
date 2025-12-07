package com.example.a4csofo;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.HashMap;

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

    // Store previous status to detect changes
    private final HashMap<String, String> orderStatusMap = new HashMap<>();
    private static final String CHANNEL_ID = "order_status_channel";

    public ClientOrdersFragment() { }

    @Nullable

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

        // Setup notification channel
        createNotificationChannel();

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

                                // --- Notification logic: status change ---
                                String previousStatus = orderStatusMap.get(order.getOrderKey());
                                String currentStatus = order.getStatus();
                                if (previousStatus != null && !previousStatus.equals(currentStatus)) {
                                    showStatusNotification(order.getOrderKey(), currentStatus);
                                }
                                orderStatusMap.put(order.getOrderKey(), currentStatus);

                                // --- Add to list ---
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

    // 🔔 Notification method
    private void showStatusNotification(String orderKey, String newStatus) {
        Intent intent = new Intent(requireContext(), ClientOrdersFragment.class); // puwede rin ibang activity
        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info) // built-in icon para walang error
                .setContentTitle("Order Status Updated")
                .setContentText("Order " + orderKey + " is now " + newStatus)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        notificationManager.notify(orderKey.hashCode(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Order Status";
            String description = "Notifications for order status updates";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
