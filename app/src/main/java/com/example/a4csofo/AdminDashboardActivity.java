package com.example.a4csofo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    // Fragment instances - LAZY INITIALIZATION
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

        // FIX: Add this to prevent keyboard from pushing layout
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Set initial title
        setTitle("Admin Dashboard");

        // Load initial fragment (LAZY loading)
        usersFragment = new AdminUsersFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragmentContainer, usersFragment, "USERS")
                .commit();

        currentFragmentTag = "USERS";

        // Bottom navigation click listener
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            String newTag = "";

            // Determine which fragment to show
            if (id == R.id.nav_users) {
                newTag = "USERS";
            } else if (id == R.id.nav_orders) {
                newTag = "ORDERS";
            } else if (id == R.id.nav_categories) {
                newTag = "CATEGORIES";
            } else if (id == R.id.nav_menu_items) {
                newTag = "MENU_ITEMS";
            }

            // If already on this fragment, do nothing
            if (currentFragmentTag.equals(newTag)) {
                return true;
            }

            // Switch fragments
            switchFragment(newTag);

            return true;
        });

        // Set default selected item
        bottomNavigation.setSelectedItemId(R.id.nav_users);
    }

    /**
     * Switch between fragments
     */
    private void switchFragment(String newTag) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Hide current fragment
        Fragment currentFragment = getCurrentFragment();
        if (currentFragment != null) {
            transaction.hide(currentFragment);
        }

        // Show or add the new fragment
        Fragment newFragment = getSupportFragmentManager().findFragmentByTag(newTag);
        if (newFragment == null) {
            // Fragment doesn't exist yet, create it
            newFragment = createFragment(newTag);
            transaction.add(R.id.fragmentContainer, newFragment, newTag);
        } else {
            // Fragment exists, just show it
            transaction.show(newFragment);
        }

        transaction.setReorderingAllowed(true)
                .commitAllowingStateLoss();

        // Update title based on fragment
        updateTitle(newTag);

        currentFragmentTag = newTag;
    }

    /**
     * Create fragment instance based on tag
     */
    private Fragment createFragment(String tag) {
        switch (tag) {
            case "USERS":
                if (usersFragment == null) {
                    usersFragment = new AdminUsersFragment();
                }
                return usersFragment;

            case "ORDERS":
                if (ordersFragment == null) {
                    ordersFragment = new AdminOrdersFragment();
                }
                return ordersFragment;

            case "CATEGORIES":
                if (categoriesFragment == null) {
                    categoriesFragment = new AdminCategoriesFragment();
                }
                return categoriesFragment;

            case "MENU_ITEMS":
                if (menuItemsFragment == null) {
                    menuItemsFragment = new AdminMenuItemsFragment();
                }
                return menuItemsFragment;

            default:
                return new AdminUsersFragment();
        }
    }

    /**
     * Get current visible fragment
     */
    private Fragment getCurrentFragment() {
        return getSupportFragmentManager().findFragmentByTag(currentFragmentTag);
    }

    /**
     * Update title based on fragment
     */
    private void updateTitle(String fragmentTag) {
        String title = "Admin Dashboard";

        switch (fragmentTag) {
            case "USERS":
                title = "User Management";
                break;
            case "ORDERS":
                title = "Order Management";
                break;
            case "CATEGORIES":
                title = "Categories";
                break;
            case "MENU_ITEMS":
                title = "Menu Items";
                break;
        }

        setTitle(title);
    }

    /**
     * Update subtitle
     */
    public void updateSubtitle(String subtitle) {
        if (getSupportActionBar() != null) {
            getSupportActionBar().setSubtitle(subtitle);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up fragments to prevent memory leaks
        usersFragment = null;
        ordersFragment = null;
        categoriesFragment = null;
        menuItemsFragment = null;
    }
}