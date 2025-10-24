package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private ImageView ivCartIcon;
    private RecyclerView recyclerViewFood;
    private BottomNavigationView bottomNavigation;
    private FirebaseAuth auth;
    private List<MenuItemsActivity.FoodItem> foodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        tvWelcome = findViewById(R.id.tvWelcome);
        ivCartIcon = findViewById(R.id.ivCartIcon);
        recyclerViewFood = findViewById(R.id.recyclerViewFood);
        bottomNavigation = findViewById(R.id.bottomNavigation);

        // Set welcome message
        FirebaseUser user = auth.getCurrentUser();
        String userName = "Customer";
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
            userName = user.getDisplayName();
        } else if (user != null && user.getEmail() != null) {
            userName = user.getEmail().split("@")[0];
        }
        tvWelcome.setText("Welcome, " + userName + "!");

        // RecyclerView setup
        recyclerViewFood.setLayoutManager(new LinearLayoutManager(this));
        foodAdapter = new FoodAdapter(foodList);
        recyclerViewFood.setAdapter(foodAdapter);

        loadFoodItemsFromFirebase();

        // Bottom navigation setup
        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                // Already on home
                return true;
            } else if (itemId == R.id.nav_cart) {
                startActivity(new Intent(MainActivity.this, CartActivity.class));
                return true;
            } else if (itemId == R.id.nav_orders) {
                startActivity(new Intent(MainActivity.this, OrdersActivity.class));
                return true;
            } else if (itemId == R.id.nav_profile) {
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
                return true;
            }
            return false;
        });

        // Cart icon click
        ivCartIcon.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CartActivity.class)));
    }

    private void loadFoodItemsFromFirebase() {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("foods");
        foodRef.get().addOnSuccessListener(snapshot -> {
            foodList.clear();
            for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                MenuItemsActivity.FoodItem food = itemSnapshot.getValue(MenuItemsActivity.FoodItem.class);
                if (food != null) foodList.add(food);
            }
            foodAdapter.notifyDataSetChanged();
        }).addOnFailureListener(e ->
                Toast.makeText(MainActivity.this, "Failed to load menu items: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    // Adapter for food items
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

        private final List<MenuItemsActivity.FoodItem> foods;

        public FoodAdapter(List<MenuItemsActivity.FoodItem> foods) {
            this.foods = foods;
        }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_food, parent, false);
            return new FoodViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
            MenuItemsActivity.FoodItem food = foods.get(position);
            holder.tvName.setText(food.name);
            holder.tvPrice.setText("₱" + String.format("%.2f", food.price));
            holder.tvDesc.setText(food.description);
            holder.tvCategory.setText(food.category);
            holder.tvPrepTime.setText(food.prepTime + " mins");
            
            if (food.imageUrl != null && !food.imageUrl.isEmpty()) {
                Glide.with(MainActivity.this)
                        .load(food.imageUrl)
                        .placeholder(R.drawable.ic_placeholder)
                        .into(holder.ivFoodImage);
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            // Add to Cart button
            holder.btnAddCart.setOnClickListener(v -> addToCart(food));
        }

        @Override
        public int getItemCount() {
            return foods.size();
        }

        private void addToCart(MenuItemsActivity.FoodItem food) {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(MainActivity.this, "Please log in first", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference cartRef = FirebaseDatabase.getInstance()
                    .getReference("carts")
                    .child(currentUser.getUid())
                    .push();

            cartRef.setValue(food)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(MainActivity.this, food.name + " added to cart!", Toast.LENGTH_SHORT).show();
                        startActivity(new Intent(MainActivity.this, CartActivity.class));
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                    );
        }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFoodImage;
            TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime;
            Button btnAddCart;

            public FoodViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
                tvName = itemView.findViewById(R.id.tvFoodName);
                tvPrice = itemView.findViewById(R.id.tvFoodPrice);
                tvDesc = itemView.findViewById(R.id.tvFoodDesc);
                tvCategory = itemView.findViewById(R.id.tvFoodCategory);
                tvPrepTime = itemView.findViewById(R.id.tvFoodPrepTime);
                btnAddCart = itemView.findViewById(R.id.btnAddCart);
            }
        }
    }
}
