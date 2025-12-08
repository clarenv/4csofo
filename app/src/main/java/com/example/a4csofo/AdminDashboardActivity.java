package com.example.a4csofo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashSet;
import java.util.Set;

public class AdminDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    private AdminUsersFragment usersFragment;
    private AdminOrdersFragment ordersFragment;
    private AdminCategoriesFragment categoriesFragment;
    private AdminMenuItemsFragment menuItemsFragment;
    private AdminFamousFragment famousFragment;

    private String currentFragmentTag = "USERS";

    // Notification variables
    private static final String TAG = "AdminDashboard";
    private static final int NOTIFICATION_PERMISSION_CODE = 1001;
    private static final String NOTIFICATION_CHANNEL_ID = "order_notifications";
    private static final String NOTIFICATION_CHANNEL_NAME = "New Order Alerts";

    private DatabaseReference ordersRef;
    private Set<String> existingOrderIds = new HashSet<>();
    private boolean isFirstLoad = true;
    private boolean notificationPermissionChecked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // 1. Setup notification channel (required for Android 8.0+)
        createNotificationChannel();

        // 2. Load initial fragment
        usersFragment = new AdminUsersFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, usersFragment, "USERS")
                .commit();

        currentFragmentTag = "USERS";

        // Bottom navigation listener
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            String newTag = "";

            if (id == R.id.nav_users) newTag = "USERS";
            else if (id == R.id.nav_orders) newTag = "ORDERS";
            else if (id == R.id.nav_categories) newTag = "CATEGORIES";
            else if (id == R.id.nav_menu_items) newTag = "MENU_ITEMS";
            else if (id == R.id.nav_famous) newTag = "FAMOUS";

            if (currentFragmentTag.equals(newTag)) return true;

            switchFragment(newTag);
            return true;
        });

        bottomNavigation.setSelectedItemId(R.id.nav_users);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // 3. Check notification permission when activity resumes
        if (!notificationPermissionChecked) {
            checkAndRequestNotificationPermission();
            notificationPermissionChecked = true;
        }

        // Check if we should open orders tab from notification
        if (getIntent().hasExtra("OPEN_ORDERS_TAB")) {
            bottomNavigation.setSelectedItemId(R.id.nav_orders);
            getIntent().removeExtra("OPEN_ORDERS_TAB");
        }
    }

    // ===== NOTIFICATION PERMISSION METHODS =====

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Get notified when new orders arrive");
            channel.enableLights(true);
            channel.setLightColor(android.graphics.Color.GREEN);
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 300, 200, 300});

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
    }

    private void checkAndRequestNotificationPermission() {
        // For Android 13+ (API 33), need POST_NOTIFICATIONS permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {

                // Permission already granted - start listening for orders
                Log.d(TAG, "Notification permission already granted");
                Toast.makeText(this, "✅ Notifications enabled", Toast.LENGTH_SHORT).show();
                setupOrderListener();

            } else {
                // Show custom dialog first, then request system permission
                showNotificationPermissionDialog();
            }
        } else {
            // For Android 12 and below, no permission needed
            Log.d(TAG, "Android version below 13, no permission needed");
            Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
            setupOrderListener();
        }
    }

    private void showNotificationPermissionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🔔 Allow Order Notifications");
        builder.setMessage("Allow A4CSOFO to send you notifications when customers place new orders?\n\nYou'll get instant alerts for:");
        builder.setMessage("📱 Allow A4CSOFO to send you notifications?\n\n" +
                "You'll get instant alerts when:\n" +
                "• New orders arrive\n" +
                "• Orders need preparation\n" +
                "• Updates on delivery status\n\n" +
                "This helps you manage orders faster!");

        builder.setPositiveButton("Allow Notifications", (dialog, which) -> {
            // Request the SYSTEM notification permission dialog
            requestSystemNotificationPermission();
        });

        builder.setNegativeButton("Not Now", (dialog, which) -> {
            Toast.makeText(this, "You can enable notifications in Settings", Toast.LENGTH_LONG).show();
            // Still setup listener but won't show notifications
            setupOrderListener();
        });

        builder.setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void requestSystemNotificationPermission() {
        // THIS SHOWS THE SYSTEM "ALLOW NOTIFICATIONS" DIALOG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // PERMISSION GRANTED BY USER
                Log.d(TAG, "User granted notification permission");
                Toast.makeText(this, "✅ Notifications enabled! You'll get alerts for new orders.",
                        Toast.LENGTH_LONG).show();

                // Start listening for orders
                setupOrderListener();

            } else {
                // PERMISSION DENIED BY USER
                Log.d(TAG, "User denied notification permission");
                Toast.makeText(this, "❌ Notifications disabled. You won't get order alerts.",
                        Toast.LENGTH_LONG).show();

                // Still setup listener but won't show notifications
                setupOrderListener();

                // Optional: Show how to enable later
                showHowToEnableNotification();
            }
        }
    }

    private void showHowToEnableNotification() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Want to enable notifications later?");
        builder.setMessage("You can enable notifications anytime:\n\n" +
                "1. Go to Phone Settings\n" +
                "2. Tap 'Apps' or 'Applications'\n" +
                "3. Find 'A4CSOFO'\n" +
                "4. Tap 'Notifications'\n" +
                "5. Turn on 'Allow Notifications'");

        builder.setPositiveButton("Open Settings", (dialog, which) -> {
            openAppNotificationSettings();
        });

        builder.setNegativeButton("Maybe Later", null);
        builder.show();
    }

    private void openAppNotificationSettings() {
        try {
            // Try to open app-specific notification settings
            android.content.Intent intent = new android.content.Intent();
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());
            startActivity(intent);
        } catch (Exception e) {
            // Fallback to general app settings
            try {
                android.content.Intent intent = new android.content.Intent();
                intent.setAction(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Please enable notifications in Settings", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ===== FIREBASE ORDER LISTENER =====

    private void setupOrderListener() {
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        ordersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    String orderId = orderSnapshot.getKey();

                    if (orderId != null && !existingOrderIds.contains(orderId)) {
                        // This is a new order (not in our existing set)
                        existingOrderIds.add(orderId);

                        if (!isFirstLoad) {
                            // Show notification for new order
                            showNewOrderNotification(orderSnapshot);
                        }
                    }
                }

                if (isFirstLoad) {
                    isFirstLoad = false;
                    Log.d(TAG, "First load complete, tracking " + existingOrderIds.size() + " existing orders");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load orders: " + error.getMessage());
            }
        });

        Log.d(TAG, "Firebase order listener started");
    }

    private void showNewOrderNotification(DataSnapshot orderSnapshot) {
        // Check if permission is granted (for Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return; // Don't show notification if permission not granted
            }
        }

        try {
            // Extract order data
            String orderId = orderSnapshot.getKey();
            String customerName = "Customer";
            double total = 0.0;
            String status = "Pending";

            // Get customer name
            if (orderSnapshot.child("customer_name").exists()) {
                customerName = orderSnapshot.child("customer_name").getValue(String.class);
            } else if (orderSnapshot.child("customerName").exists()) {
                customerName = orderSnapshot.child("customerName").getValue(String.class);
            }

            // Get total price
            if (orderSnapshot.child("total_price").exists()) {
                Object totalObj = orderSnapshot.child("total_price").getValue();
                if (totalObj instanceof Double) {
                    total = (Double) totalObj;
                } else if (totalObj instanceof Long) {
                    total = ((Long) totalObj).doubleValue();
                }
            } else if (orderSnapshot.child("total").exists()) {
                Object totalObj = orderSnapshot.child("total").getValue();
                if (totalObj instanceof Double) {
                    total = (Double) totalObj;
                } else if (totalObj instanceof Long) {
                    total = ((Long) totalObj).doubleValue();
                }
            }

            // Get status
            if (orderSnapshot.child("status").exists()) {
                status = orderSnapshot.child("status").getValue(String.class);
            }

            // Only notify for pending orders
            if ("pending".equalsIgnoreCase(status) || "Pending".equals(status)) {
                sendNotification(orderId, customerName, total);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error processing order notification: " + e.getMessage());
        }
    }

    private void sendNotification(String orderId, String customerName, double total) {
        try {
            // Create notification
            android.app.Notification.Builder builder;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder = new android.app.Notification.Builder(this, NOTIFICATION_CHANNEL_ID);
            } else {
                builder = new android.app.Notification.Builder(this);
            }

            // Set notification content
            builder.setContentTitle("🛍️ New Order!")
                    .setContentText(customerName + " - ₱" + String.format("%.2f", total))
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setAutoCancel(true)
                    .setPriority(android.app.Notification.PRIORITY_HIGH);

            // Create intent to open orders fragment
            android.content.Intent intent = new android.content.Intent(this, AdminDashboardActivity.class);
            intent.putExtra("OPEN_ORDERS_TAB", true);
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK |
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);

            android.app.PendingIntent pendingIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE);
            } else {
                pendingIntent = android.app.PendingIntent.getActivity(this, 0, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT);
            }

            builder.setContentIntent(pendingIntent);

            // Show notification
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                int notificationId = (int) System.currentTimeMillis();
                manager.notify(notificationId, builder.build());

                Log.d(TAG, "Notification sent: " + customerName + " - ₱" + total);

                // Show toast on screen
                runOnUiThread(() -> {
                    Toast.makeText(AdminDashboardActivity.this,
                            "📱 New order from " + customerName, Toast.LENGTH_SHORT).show();
                });
            }

        } catch (Exception e) {
            Log.e(TAG, "Failed to send notification: " + e.getMessage());
        }
    }

    // ===== EXISTING FRAGMENT METHODS =====

    private void switchFragment(String newTag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null) transaction.hide(currentFragment);

        Fragment newFragment = getSupportFragmentManager().findFragmentByTag(newTag);
        if (newFragment == null) {
            newFragment = createFragment(newTag);
            transaction.add(R.id.fragmentContainer, newFragment, newTag);
        } else {
            transaction.show(newFragment);
        }

        transaction.setReorderingAllowed(true)
                .commitAllowingStateLoss();

        updateTitle(newTag);
        currentFragmentTag = newTag;
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case "USERS":
                if (usersFragment == null) usersFragment = new AdminUsersFragment();
                return usersFragment;
            case "ORDERS":
                if (ordersFragment == null) ordersFragment = new AdminOrdersFragment();
                return ordersFragment;
            case "CATEGORIES":
                if (categoriesFragment == null) categoriesFragment = new AdminCategoriesFragment();
                return categoriesFragment;
            case "MENU_ITEMS":
                if (menuItemsFragment == null) menuItemsFragment = new AdminMenuItemsFragment();
                return menuItemsFragment;
            case "FAMOUS":
                if (famousFragment == null) famousFragment = new AdminFamousFragment();
                return famousFragment;
            default:
                return new AdminUsersFragment();
        }
    }

    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentByTag(currentFragmentTag);
    }

    private void updateTitle(String fragmentTag) {
        String title = "Admin Dashboard";
        switch (fragmentTag) {
            case "USERS": title = "User Management"; break;
            case "ORDERS": title = "Order Management"; break;
            case "CATEGORIES": title = "Categories"; break;
            case "MENU_ITEMS": title = "Menu Items"; break;
            case "FAMOUS": title = "Sales"; break;
        }
        setTitle(title);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Cleanup
        if (ordersRef != null) {
            ordersRef.removeEventListener((ValueEventListener) ordersRef);
        }

        usersFragment = null;
        ordersFragment = null;
        categoriesFragment = null;
        menuItemsFragment = null;
        famousFragment = null;
    }
}