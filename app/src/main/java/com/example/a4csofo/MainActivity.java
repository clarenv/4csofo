package com.example.a4csofo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextView tvWelcome;
    private Button btnCart, btnOrders, btnProfile, btnLogout;
    private RecyclerView recyclerViewFood;
    private FirebaseAuth auth;
    private List<MenuItemsActivity.FoodItem> foodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        tvWelcome = findViewById(R.id.tvWelcome);
        recyclerViewFood = findViewById(R.id.recyclerViewFood);
        btnCart = findViewById(R.id.btnCart);
        btnOrders = findViewById(R.id.btnOrders);
        btnProfile = findViewById(R.id.btnProfile);
        btnLogout = findViewById(R.id.btnLogout);

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

        // Bottom buttons
        btnCart.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, CartActivity.class)));
        btnOrders.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, OrdersActivity.class)));
        btnProfile.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, ProfileActivity.class)));
        btnLogout.setOnClickListener(v -> {
            auth.signOut();
            Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, LoginActivity.class));
            finish();
        });
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
            holder.tvName.setText(food.name + " - ₱" + food.price);
            holder.tvDesc.setText(food.description + " (" + food.prepTime + " mins, " + food.category + ")");
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
            TextView tvName, tvDesc;
            Button btnAddCart;

            public FoodViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
                tvName = itemView.findViewById(R.id.tvFoodName);
                tvDesc = itemView.findViewById(R.id.tvFoodDesc);
                btnAddCart = itemView.findViewById(R.id.btnAddCart);
            }
        }
    }
}
