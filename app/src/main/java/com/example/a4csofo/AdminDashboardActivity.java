package com.example.a4csofo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    private AdminUsersFragment usersFragment;
    private AdminOrdersFragment ordersFragment;
    private AdminCategoriesFragment categoriesFragment;
    private AdminMenuItemsFragment menuItemsFragment;
    private AdminFamousFragment famousFragment; // NEW

    private String currentFragmentTag = "USERS";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Load initial fragment
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
            else if (id == R.id.nav_famous) newTag = "FAMOUS"; // NEW

            if (currentFragmentTag.equals(newTag)) return true;

            switchFragment(newTag);
            return true;
        });

        bottomNavigation.setSelectedItemId(R.id.nav_users);
    }

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
            case "FAMOUS": title = "Famous Items"; break;
        }
        setTitle(title);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        usersFragment = null;
        ordersFragment = null;
        categoriesFragment = null;
        menuItemsFragment = null;
        famousFragment = null;
    }
}
