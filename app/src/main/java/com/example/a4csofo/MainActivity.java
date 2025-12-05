package com.example.a4csofo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.badge.BadgeDrawable;

public class MainActivity extends AppCompatActivity {

    BottomNavigationView bottomNavigation;
    private BadgeDrawable cartBadge; // Add badge reference

    // Fragment instances for better performance
    private ClientHomeFragment homeFragment;
    private ClientCartFragment cartFragment;
    private ClientOrdersFragment ordersFragment;
    private ClientProfileFragment profileFragment;

    // Constants
    private static final int CHECKOUT_REQUEST_CODE = 1001;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_ORDERS = "show_orders_after_checkout";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // FIX: Prevent keyboard from pushing layout
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Initialize badge (compatible with older versions)
        cartBadge = bottomNavigation.getOrCreateBadge(R.id.nav_cart);
        cartBadge.setVisible(false); // Hide initially

        // Default Fragment - Initialize home fragment
        homeFragment = new ClientHomeFragment();
        loadFragment(homeFragment);

        // Check if coming from CheckoutActivity via intent extra
        if (getIntent() != null && getIntent().hasExtra("SHOW_ORDERS_TAB")) {
            // Delay to ensure BottomNavigation is ready
            new Handler().postDelayed(() -> {
                bottomNavigation.setSelectedItemId(R.id.nav_orders);
                // Clear the extra so it doesn't auto-show again on rotation
                getIntent().removeExtra("SHOW_ORDERS_TAB");
            }, 500);
        }

        // Use setOnItemSelectedListener instead of deprecated setOnNavigationItemSelectedListener
        bottomNavigation.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                if (homeFragment == null) {
                    homeFragment = new ClientHomeFragment();
                }
                fragment = homeFragment;
            } else if (id == R.id.nav_cart) {
                if (cartFragment == null) {
                    cartFragment = new ClientCartFragment();
                }
                fragment = cartFragment;
            } else if (id == R.id.nav_orders) {
                if (ordersFragment == null) {
                    ordersFragment = new ClientOrdersFragment();
                }
                fragment = ordersFragment;
            } else if (id == R.id.nav_profile) {
                if (profileFragment == null) {
                    profileFragment = new ClientProfileFragment();
                }
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
            // Clear the flag
            prefs.edit().putBoolean(KEY_SHOW_ORDERS, false).apply();

            // Auto-switch to Orders tab
            new Handler().postDelayed(() -> {
                if (bottomNavigation != null) {
                    bottomNavigation.setSelectedItemId(R.id.nav_orders);

                    // Refresh the orders fragment para makita ang bagong order
                    if (ordersFragment != null) {
                        // Pwede mong lagyan ng refresh method ang ordersFragment dito
                        // ordersFragment.refreshOrders();
                    }
                }
            }, 300);
        }
    }

    /**
     * Method to navigate to CheckoutActivity
     * Call this from CartFragment
     */
    public void navigateToCheckout() {
        Intent intent = new Intent(MainActivity.this, CheckoutActivity.class);
        startActivityForResult(intent, CHECKOUT_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == CHECKOUT_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                // Save flag to show orders tab
                SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                prefs.edit().putBoolean(KEY_SHOW_ORDERS, true).apply();

                // Update cart badge to 0 since cart is cleared after checkout
                if (cartBadge != null) {
                    cartBadge.setVisible(false);
                    cartBadge.clearNumber();
                }

                // Show toast notification
                Toast.makeText(this, "Order placed successfully! Redirecting to My Orders...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .setReorderingAllowed(true) // Add for better performance
                    .commit();
            return true;
        }
        return false;
    }

    /**
     * Helper method to update cart badge count - FIXED VERSION
     */
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

    /**
     * Handle back button press - go to home instead of exiting
     */
    @Override
    public void onBackPressed() {
        // Check current fragment
        Fragment currentFragment = getSupportFragmentManager()
                .findFragmentById(R.id.fragmentContainer);

        if (currentFragment instanceof ClientHomeFragment) {
            // If already on home, exit app
            super.onBackPressed();
        } else {
            // Go back to home
            bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    /**
     * Get cart fragment instance (if needed by other fragments)
     */
    public ClientCartFragment getCartFragment() {
        return cartFragment;
    }

    /**
     * Get home fragment instance (if needed by other fragments)
     */
    public ClientHomeFragment getHomeFragment() {
        return homeFragment;
    }

    /**
     * Clean up fragments to prevent memory leaks
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Optional: Clear fragment references
        homeFragment = null;
        cartFragment = null;
        ordersFragment = null;
        profileFragment = null;
    }
}