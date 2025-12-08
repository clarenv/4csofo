package com.example.a4csofo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
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
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 101;

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

        // Setup notification channel with sound
        createNotificationChannel();

        // Request runtime notification permission for Android 13+
        requestNotificationPermissionIfNeeded();

        // Load orders with ChildEventListener
        loadUserOrders();

        return view;
    }

    // Request POST_NOTIFICATIONS permission on Android 13+
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(requireActivity(),
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST_CODE);
            }
        }
    }

    private void loadUserOrders() {
        progressBar.setVisibility(View.VISIBLE);

        ordersRef.orderByChild("userId").equalTo(currentUser.getUid())
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        OrderModel order = snapshot.getValue(OrderModel.class);
                        if (order != null) {
                            order.setOrderKey(snapshot.getKey());
                            orderList.add(order);

                            // Save initial status (no notification on first load)
                            orderStatusMap.put(order.getOrderKey(), order.getStatus());
                            ordersAdapter.notifyDataSetChanged();
                        }
                        progressBar.setVisibility(View.GONE);
                        emptyLayout.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                        OrderModel updatedOrder = snapshot.getValue(OrderModel.class);
                        if (updatedOrder != null) {
                            updatedOrder.setOrderKey(snapshot.getKey());

                            // Find the order in the list
                            for (int i = 0; i < orderList.size(); i++) {
                                if (orderList.get(i).getOrderKey().equals(updatedOrder.getOrderKey())) {
                                    // Check for status change
                                    String oldStatus = orderStatusMap.get(updatedOrder.getOrderKey());
                                    String newStatus = updatedOrder.getStatus();
                                    if (oldStatus != null && !oldStatus.equals(newStatus)) {
                                        showStatusNotification(updatedOrder.getOrderKey(), newStatus);
                                    }

                                    // Update map and list
                                    orderStatusMap.put(updatedOrder.getOrderKey(), newStatus);
                                    orderList.set(i, updatedOrder);
                                    ordersAdapter.notifyItemChanged(i);
                                    break;
                                }
                            }
                        }
                    }

                    @Override
                    public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                        String key = snapshot.getKey();
                        for (int i = 0; i < orderList.size(); i++) {
                            if (orderList.get(i).getOrderKey().equals(key)) {
                                orderList.remove(i);
                                orderStatusMap.remove(key);
                                ordersAdapter.notifyItemRemoved(i);
                                break;
                            }
                        }
                        emptyLayout.setVisibility(orderList.isEmpty() ? View.VISIBLE : View.GONE);
                    }

                    @Override
                    public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) { }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }

    // 🔔 Notification method with sound
    private void showStatusNotification(String orderKey, String newStatus) {
        Log.d("NOTIFICATION", "Triggering notification for order " + orderKey + " status " + newStatus);

        // Check if we have permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.d("NOTIFICATION", "Permission denied for notifications.");
                return;
            }
        }

        Intent intent = new Intent(requireContext(), ClientOrdersFragment.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(requireContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(requireContext(), CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Order Status Updated")
                .setContentText("Order " + orderKey + " is now " + newStatus)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setSound(soundUri); // sound for pre-Oreo

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(requireContext());
        int notificationId = (orderKey != null) ? orderKey.hashCode() : (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Order Status";
            String description = "Notifications for order status updates";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            channel.setSound(soundUri, audioAttributes); // sound for Oreo+

            NotificationManager notificationManager = requireContext().getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
