package com.example.a4csofo;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity implements LocationListener {

    BottomNavigationView bottomNavigation;
    private BadgeDrawable cartBadge;

    // Fragment instances for better performance
    private ClientHomeFragment homeFragment;
    private ClientCartFragment cartFragment;
    private ClientOrdersFragment ordersFragment;
    private ClientProfileFragment profileFragment;

    // Constants
    private static final int CHECKOUT_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_ORDERS = "show_orders_after_checkout";

    // Notification & Location Constants
    private static final String TAG = "MainActivity";
    private static final int LOCATION_PERMISSION_CODE = 2001;
    private static final int NOTIFICATION_PERMISSION_CODE = 2002;
    private static final String NOTIFICATION_CHANNEL_ID = "client_notifications";
    private static final String NOTIFICATION_CHANNEL_NAME = "Order Updates";

    // Location specific constants
    private static final long LOCATION_UPDATE_INTERVAL = 30000; // 30 seconds
    private static final float LOCATION_UPDATE_DISTANCE = 10; // 10 meters
    private static final String KEY_LAST_LATITUDE = "last_latitude";
    private static final String KEY_LAST_LONGITUDE = "last_longitude";
    private static final String KEY_LOCATION_TIMESTAMP = "location_timestamp";

    // Location variables
    private LocationManager locationManager;
    private DatabaseReference ordersRef;
    private ValueEventListener orderStatusListener;
    private String currentUserId;
    private Set<String> trackedOrderIds = new HashSet<>();
    private boolean notificationPermissionChecked = false;

    // Current location
    private double currentLatitude = 0.0;
    private double currentLongitude = 0.0;
    private boolean isLocationEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Prevent keyboard from pushing layout
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Initialize badge (compatible with older versions)
        cartBadge = bottomNavigation.getOrCreateBadge(R.id.nav_cart);
        cartBadge.setVisible(false); // Hide initially

        // 1. Setup notification channel
        createNotificationChannel();

        // 2. Initialize location manager
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 3. Get current user ID (you need to implement this based on your auth system)
        // currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        currentUserId = "user123"; // Temporary - replace with actual user ID

        // 4. Load saved location
        loadSavedLocation();

        // Default Fragment - Initialize home fragment
        homeFragment = new ClientHomeFragment();
        loadFragment(homeFragment);

        // Check if coming from CheckoutActivity via intent extra
        if (getIntent() != null && getIntent().hasExtra("SHOW_ORDERS_TAB")) {
            // Delay to ensure BottomNavigation is ready
            new Handler().postDelayed(() -> {
                bottomNavigation.setSelectedItemId(R.id.nav_orders);
                getIntent().removeExtra("SHOW_ORDERS_TAB");
            }, 500);
        }

        // Check for notification data from intent
        if (getIntent() != null && getIntent().hasExtra("ORDER_UPDATE")) {
            String orderId = getIntent().getStringExtra("ORDER_UPDATE");
            String status = getIntent().getStringExtra("ORDER_STATUS");
            if (orderId != null && status != null) {
                showOrderUpdateNotification(orderId, status);
            }
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                if (homeFragment == null) homeFragment = new ClientHomeFragment();
                fragment = homeFragment;
            } else if (id == R.id.nav_cart) {
                if (cartFragment == null) cartFragment = new ClientCartFragment();
                fragment = cartFragment;
            } else if (id == R.id.nav_orders) {
                if (ordersFragment == null) ordersFragment = new ClientOrdersFragment();
                fragment = ordersFragment;
            } else if (id == R.id.nav_profile) {
                if (profileFragment == null) profileFragment = new ClientProfileFragment();
                fragment = profileFragment;
            }

            return loadFragment(fragment);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Check SharedPreferences if need to show orders after checkout
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean showOrders = prefs.getBoolean(KEY_SHOW_ORDERS, false);

        if (showOrders) {
            prefs.edit().putBoolean(KEY_SHOW_ORDERS, false).apply();
            new Handler().postDelayed(() -> {
                if (bottomNavigation != null) {
                    bottomNavigation.setSelectedItemId(R.id.nav_orders);
                    if (ordersFragment != null) {
                        // Refresh orders fragment if needed
                    }
                }
            }, 300);
        }

        // Check notification permission
        if (!notificationPermissionChecked) {
            checkAndRequestPermissions();
            notificationPermissionChecked = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop location updates to save battery
        stopLocationUpdates();
        // Stop listening to order updates
        if (ordersRef != null && orderStatusListener != null) {
            ordersRef.removeEventListener(orderStatusListener);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Handle notification when app is opened from notification
        if (intent != null && intent.hasExtra("ORDER_UPDATE")) {
            String orderId = intent.getStringExtra("ORDER_UPDATE");
            String status = intent.getStringExtra("ORDER_STATUS");
            if (orderId != null && status != null) {
                navigateToOrderDetails(orderId, status);
            }
        }
    }

    // ===== PERMISSION METHODS (SYSTEM DIALOGS ONLY) =====

    private void checkAndRequestPermissions() {
        // Step 1: Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Request SYSTEM notification permission dialog
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_CODE);
                return; // Wait for user response
            }
        }

        // Step 2: Check location permissions
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Request SYSTEM location permission dialog
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_CODE);
        } else {
            // All permissions already granted
            setupOrderStatusListener();
            startLocationUpdates();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Notifications enabled", Toast.LENGTH_SHORT).show();
                // Now check location permission
                checkLocationPermissionAfterNotification();
            } else {
                Toast.makeText(this, "Notifications disabled", Toast.LENGTH_LONG).show();
                // Still check location permission
                checkLocationPermissionAfterNotification();
            }

        } else if (requestCode == LOCATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location access granted", Toast.LENGTH_SHORT).show();
                setupOrderStatusListener();
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Location access denied", Toast.LENGTH_LONG).show();
                // Start app without location
                setupOrderStatusListener();
            }
        }
    }

    private void checkLocationPermissionAfterNotification() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_CODE);
        } else {
            setupOrderStatusListener();
            startLocationUpdates();
        }
    }

    // ===== LOCATION METHODS =====

    private void loadSavedLocation() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentLatitude = Double.longBitsToDouble(prefs.getLong(KEY_LAST_LATITUDE, Double.doubleToLongBits(0.0)));
        currentLongitude = Double.longBitsToDouble(prefs.getLong(KEY_LAST_LONGITUDE, Double.doubleToLongBits(0.0)));

        long lastUpdate = prefs.getLong(KEY_LOCATION_TIMESTAMP, 0);
        if (lastUpdate > 0) {
            Log.d(TAG, "Loaded saved location: " + currentLatitude + ", " + currentLongitude);
        }
    }

    private void saveLocation(double latitude, double longitude) {
        currentLatitude = latitude;
        currentLongitude = longitude;

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit()
                .putLong(KEY_LAST_LATITUDE, Double.doubleToLongBits(latitude))
                .putLong(KEY_LAST_LONGITUDE, Double.doubleToLongBits(longitude))
                .putLong(KEY_LOCATION_TIMESTAMP, System.currentTimeMillis())
                .apply();
    }

    private void startLocationUpdates() {
        if (locationManager == null) {
            locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        }

        // Check if location services are enabled
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "Please enable location services", Toast.LENGTH_LONG).show();
            isLocationEnabled = false;
            return;
        }

        // Check permissions
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Request location updates from GPS provider
            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_UPDATE_DISTANCE,
                    this
            );

            // Also request from network provider as fallback
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATE_INTERVAL,
                    LOCATION_UPDATE_DISTANCE,
                    this
            );

            isLocationEnabled = true;
            Log.d(TAG, "Location updates started");

            // Try to get last known location immediately
            Location lastLocation = getLastKnownLocation();
            if (lastLocation != null) {
                onLocationChanged(lastLocation);
            }

            Toast.makeText(this, "Location tracking started", Toast.LENGTH_SHORT).show();

        } catch (SecurityException e) {
            Log.e(TAG, "Security Exception: " + e.getMessage());
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Log.e(TAG, "Error starting location updates: " + e.getMessage());
            Toast.makeText(this, "Error starting location service", Toast.LENGTH_LONG).show();
        }
    }

    private void stopLocationUpdates() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(this);
                isLocationEnabled = false;
                Log.d(TAG, "Location updates stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping location updates: " + e.getMessage());
            }
        }
    }

    private Location getLastKnownLocation() {
        if (locationManager == null) return null;

        try {
            if (ActivityCompat.checkSelfPermission(this,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ActivityCompat.checkSelfPermission(this,
                            Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

                Location locationGPS = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                Location locationNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

                // Return the most recent location
                if (locationGPS != null && locationNetwork != null) {
                    if (locationGPS.getTime() > locationNetwork.getTime()) {
                        return locationGPS;
                    } else {
                        return locationNetwork;
                    }
                } else if (locationGPS != null) {
                    return locationGPS;
                } else if (locationNetwork != null) {
                    return locationNetwork;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting last known location: " + e.getMessage());
        }

        return null;
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        Log.d(TAG, "New location: " + latitude + ", " + longitude +
                " Accuracy: " + location.getAccuracy() + "m");

        // Save the location
        saveLocation(latitude, longitude);

        // Broadcast location update to fragments
        broadcastLocationUpdate(latitude, longitude);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, "Location provider status changed: " + provider + " status: " + status);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
        Toast.makeText(this, "Location provider enabled: " + provider, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Provider enabled: " + provider);
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
        Toast.makeText(this, "Location provider disabled: " + provider, Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Provider disabled: " + provider);
    }

    private void broadcastLocationUpdate(double latitude, double longitude) {
        Intent intent = new Intent("LOCATION_UPDATE");
        intent.putExtra("latitude", latitude);
        intent.putExtra("longitude", longitude);
        sendBroadcast(intent);
    }

    // ===== NOTIFICATION METHODS =====

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Get notified about your order status");
            channel.enableLights(true);
            channel.setLightColor(getResources().getColor(R.color.primary));
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 100, 200});

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created");
            }
        }
    }

    private void setupOrderStatusListener() {
        ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        orderStatusListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    String orderId = orderSnapshot.getKey();

                    // Check if this order belongs to current user
                    String userId = null;
                    if (orderSnapshot.child("userId").exists()) {
                        userId = orderSnapshot.child("userId").getValue(String.class);
                    } else if (orderSnapshot.child("customerUid").exists()) {
                        userId = orderSnapshot.child("customerUid").getValue(String.class);
                    }

                    if (userId != null && userId.equals(currentUserId)) {
                        String status = "Pending";
                        if (orderSnapshot.child("status").exists()) {
                            status = orderSnapshot.child("status").getValue(String.class);
                        }

                        // Check if status changed
                        String previousStatus = getStoredOrderStatus(orderId);
                        if (!status.equals(previousStatus) && !"pending".equalsIgnoreCase(status)) {
                            // Status changed - show notification
                            showOrderUpdateNotification(orderId, status);
                            storeOrderStatus(orderId, status);
                        }

                        // Add to tracked orders
                        trackedOrderIds.add(orderId);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load orders: " + error.getMessage());
            }
        };

        ordersRef.addValueEventListener(orderStatusListener);
        Log.d(TAG, "Order status listener started");
    }

    private void showOrderUpdateNotification(String orderId, String status) {
        if (!areNotificationsEnabled()) return;

        String title = "Order Update";
        String message = getStatusMessage(status);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.app_logo)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        // Create intent to open orders fragment
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("ORDER_UPDATE", orderId);
        intent.putExtra("ORDER_STATUS", status);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE :
                android.app.PendingIntent.FLAG_UPDATE_CURRENT;

        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                this, 0, intent, flags);

        builder.setContentIntent(pendingIntent);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            int notificationId = (int) System.currentTimeMillis();
            notificationManager.notify(notificationId, builder.build());

            // Also show toast
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show());
        }
    }

    private String getStatusMessage(String status) {
        switch (status.toLowerCase()) {
            case "accepted":
                return "Your order has been accepted!";
            case "preparing":
                return "Your order is being prepared!";
            case "ready":
                return "Your order is ready for pickup!";
            case "delivering":
                return "Your order is on the way!";
            case "completed":
                return "Your order has been delivered!";
            case "cancelled":
                return "Your order has been cancelled";
            default:
                return "Order status: " + status;
        }
    }

    private boolean areNotificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // For Android 12 and below
    }

    private void storeOrderStatus(String orderId, String status) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().putString("order_status_" + orderId, status).apply();
    }

    private String getStoredOrderStatus(String orderId) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return prefs.getString("order_status_" + orderId, "pending");
    }

    private void navigateToOrderDetails(String orderId, String status) {
        // Navigate to orders tab
        if (bottomNavigation != null) {
            bottomNavigation.setSelectedItemId(R.id.nav_orders);

            // You can pass data to orders fragment
            if (ordersFragment != null) {
                // ordersFragment.showOrderDetails(orderId);
            }

            Toast.makeText(this, "Order " + orderId + " is now " + status,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ===== EXISTING METHODS (unchanged) =====

    public void navigateToCheckout() {
        Intent intent = new Intent(MainActivity.this, CheckoutActivity.class);
        startActivityForResult(intent, CHECKOUT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHECKOUT_REQUEST_CODE && resultCode == RESULT_OK) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit().putBoolean(KEY_SHOW_ORDERS, true).apply();

            if (cartBadge != null) {
                cartBadge.setVisible(false);
                cartBadge.clearNumber();
            }

            Toast.makeText(this, "Order placed successfully! Redirecting to My Orders...",
                    Toast.LENGTH_SHORT).show();

            // Show order confirmation notification
            showOrderUpdateNotification("new_order", "pending");
        }
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .setReorderingAllowed(true)
                    .commit();
            return true;
        }
        return false;
    }

    public void updateCartBadge(int count) {
        try {
            if (count > 0) {
                cartBadge.setNumber(count);
                cartBadge.setVisible(true);
            } else {
                cartBadge.setVisible(false);
                cartBadge.clearNumber();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onBackPressed() {
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer);

        if (currentFragment instanceof ClientHomeFragment) {
            super.onBackPressed();
        } else {
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    public ClientCartFragment getCartFragment() {
        return cartFragment;
    }

    public ClientHomeFragment getHomeFragment() {
        return homeFragment;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
        if (ordersRef != null && orderStatusListener != null) {
            ordersRef.removeEventListener(orderStatusListener);
        }
        homeFragment = null;
        cartFragment = null;
        ordersFragment = null;
        profileFragment = null;
        locationManager = null;
    }
}