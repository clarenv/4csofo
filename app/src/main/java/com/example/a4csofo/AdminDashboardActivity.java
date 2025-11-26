package com.example.a4csofo;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class AdminDashboardActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard); // Contains FrameLayout + BottomNavigationView

        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Load default fragment (UsersFragment)
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, new AdminUsersFragment())
                .commit();

        // Bottom navigation click listener
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_users) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new AdminUsersFragment())
                        .commit();
            } else if (id == R.id.nav_orders) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new AdminOrdersFragment())
                        .commit();
            } else if (id == R.id.nav_categories) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new AdminCategoriesFragment())
                        .commit();
            } else if (id == R.id.nav_menu_items) {
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragmentContainer, new AdminMenuItemsFragment())
                        .commit();
            }

            return true; // Indicate the click was handled
        });

        // Set default selected item to "Users"
        bottomNavigation.setSelectedItemId(R.id.nav_users);
    }
}
