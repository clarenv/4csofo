package com.example.a4csofo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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
    private List<FoodItem> foodList = new ArrayList<>();
    private FoodAdapter foodAdapter;
    private DatabaseReference usersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        auth = FirebaseAuth.getInstance();
        tvWelcome = findViewById(R.id.tvWelcome);
        recyclerViewFood = findViewById(R.id.recyclerViewFood);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        ivCartIcon = findViewById(R.id.ivCartIcon);
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Welcome message
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

        // Bottom navigation
        bottomNavigation.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) return true;
            else if (id == R.id.nav_cart) startActivity(new Intent(this, CartActivity.class));
            else if (id == R.id.nav_orders) startActivity(new Intent(this, OrdersActivity.class));
            else if (id == R.id.nav_profile) startActivity(new Intent(this, ProfileActivity.class));
            return true;
        });

        ivCartIcon.setOnClickListener(v -> startActivity(new Intent(this, CartActivity.class)));
    }

    /** ------------------ USER ONLINE/OFFLINE ------------------ **/
    @Override
    protected void onStart() {
        super.onStart();
        setUserOnlineStatus(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        setUserOnlineStatus(false);
    }

    private void setUserOnlineStatus(boolean isOnline) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            DatabaseReference statusRef = usersRef.child(currentUser.getUid()).child("online");
            if (isOnline) {
                statusRef.setValue(true);
                statusRef.onDisconnect().setValue(false);
            } else {
                statusRef.setValue(false);
            }
        }
    }

    /** ------------------ FOOD DASHBOARD ------------------ **/
    private void loadFoodItemsFromFirebase() {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("foods");
        foodRef.get().addOnSuccessListener(snapshot -> {
            foodList.clear();
            for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                FoodItem food = itemSnapshot.getValue(FoodItem.class);
                if (food != null) foodList.add(food);
            }
            foodAdapter.notifyDataSetChanged();
        }).addOnFailureListener(e ->
                Toast.makeText(this, "Failed to load menu items: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }

    /** ------------------ FOOD ADAPTER ------------------ **/
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private final List<FoodItem> foods;

        public FoodAdapter(List<FoodItem> foods) { this.foods = foods; }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_food, parent, false);
            return new FoodViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
            FoodItem food = foods.get(position);
            holder.tvName.setText(food.name);
            holder.tvPrice.setText("₱" + String.format("%.2f", food.price));
            holder.tvDesc.setText(food.description);
            holder.tvCategory.setText(food.category);
            holder.tvPrepTime.setText(food.prepTime + " mins");

            if (food.base64Image != null && !food.base64Image.isEmpty()) {
                Bitmap bitmap = base64ToBitmap(food.base64Image);
                if (bitmap != null) holder.ivFoodImage.setImageBitmap(bitmap);
                else holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            holder.itemView.setAlpha(food.available ? 1f : 0.5f);
            holder.btnAddCart.setEnabled(food.available);
            holder.btnAddCart.setOnClickListener(v -> addToCart(food));
        }

        @Override
        public int getItemCount() { return foods.size(); }

        private void addToCart(FoodItem food) {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(MainActivity.this, "Please log in first", Toast.LENGTH_SHORT).show();
                return;
            }
            DatabaseReference cartRef = FirebaseDatabase.getInstance()
                    .getReference("carts")
                    .child(currentUser.getUid())
                    .push();
            cartRef.setValue(food).addOnSuccessListener(aVoid ->
                            Toast.makeText(MainActivity.this, food.name + " added to cart!", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e ->
                            Toast.makeText(MainActivity.this, "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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

    /** ------------------ FOOD MODEL ------------------ **/
    public static class FoodItem {
        public String name;
        public String description;
        public String category;
        public String prepTime;
        public double price;
        public String base64Image;
        public boolean available = true;

        public FoodItem() {}
    }
}
