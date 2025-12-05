package com.example.a4csofo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    // Fragment instances
    private AdminUsersFragment usersFragment;
    private AdminOrdersFragment ordersFragment;
    private AdminCategoriesFragment categoriesFragment;
    private AdminMenuItemsFragment menuItemsFragment;

    // Current fragment tracking
    private String currentFragmentTag = "USERS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Initialize fragments ONCE
        usersFragment = new AdminUsersFragment();
        ordersFragment = new AdminOrdersFragment();
        categoriesFragment = new AdminCategoriesFragment();
        menuItemsFragment = new AdminMenuItemsFragment();

        // Set initial title
        setTitle("Admin Dashboard");

        // Load ALL fragments initially (Show/Hide method)
        FragmentTransaction initialTransaction = getSupportFragmentManager().beginTransaction();
        initialTransaction.add(R.id.fragmentContainer, usersFragment, "USERS");
        initialTransaction.add(R.id.fragmentContainer, ordersFragment, "ORDERS");
        initialTransaction.add(R.id.fragmentContainer, categoriesFragment, "CATEGORIES");
        initialTransaction.add(R.id.fragmentContainer, menuItemsFragment, "MENU_ITEMS");

        // Show only users, hide others
        initialTransaction.hide(ordersFragment);
        initialTransaction.hide(categoriesFragment);
        initialTransaction.hide(menuItemsFragment);

        initialTransaction.commit();

        // Bottom navigation click listener
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // Prevent rapid taps
            bottomNavigation.setEnabled(false);

            FragmentTransaction transaction = getSupportFragmentManager()
                    .beginTransaction()
                    .setReorderingAllowed(true); // Performance

            if (id == R.id.nav_users && !currentFragmentTag.equals("USERS")) {
                transaction.hide(getCurrentFragment())
                        .show(usersFragment);
                setTitle("User Management");
                currentFragmentTag = "USERS";
            }
            else if (id == R.id.nav_orders && !currentFragmentTag.equals("ORDERS")) {
                transaction.hide(getCurrentFragment())
                        .show(ordersFragment);
                setTitle("Order Management");
                currentFragmentTag = "ORDERS";
            }
            else if (id == R.id.nav_categories && !currentFragmentTag.equals("CATEGORIES")) {
                transaction.hide(getCurrentFragment())
                        .show(categoriesFragment);
                setTitle("Categories");
                currentFragmentTag = "CATEGORIES";
            }
            else if (id == R.id.nav_menu_items && !currentFragmentTag.equals("MENU_ITEMS")) {
                transaction.hide(getCurrentFragment())
                        .show(menuItemsFragment);
                setTitle("Menu Items");
                currentFragmentTag = "MENU_ITEMS";
            }

            transaction.commit();

            // Re-enable after 200ms
            bottomNavigation.postDelayed(() -> bottomNavigation.setEnabled(true), 200);

            return true;
        });

        // Set default selected item
        bottomNavigation.setSelectedItemId(R.id.nav_users);
    }

    /**
     * Get current visible fragment
     */
    private Fragment getCurrentFragment() { // CHANGED THIS LINE
        switch (currentFragmentTag) {
            case "USERS": return usersFragment;
            case "ORDERS": return ordersFragment;
            case "CATEGORIES": return categoriesFragment;
            case "MENU_ITEMS": return menuItemsFragment;
            default: return usersFragment;
        }
    }

    /**
     * Update subtitle
     */
    public void updateSubtitle(String subtitle) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }
}