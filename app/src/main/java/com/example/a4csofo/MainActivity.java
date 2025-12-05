package com.example.a4csofo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
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
    private ImageView ivLogo;
    private RecyclerView recyclerMain;
    private BottomNavigationView bottomNavigation;
    private LinearLayout menuOrdersLayout;
    private AppBarLayout appBarLayout;

    private FirebaseAuth auth;
    private List<FoodItem> foodList = new ArrayList<>();
    private List<FoodItem> filteredFoodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    private EditText searchEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        auth = FirebaseAuth.getInstance();
        tvWelcome = findViewById(R.id.tvWelcome);
        recyclerMain = findViewById(R.id.recyclerMain);
        bottomNavigation = findViewById(R.id.bottomNavigation);
        ivLogo = findViewById(R.id.ivLogo);
        menuOrdersLayout = findViewById(R.id.menuOrdersLayout);
        appBarLayout = findViewById(R.id.appBarLayout);
        searchEditText = findViewById(R.id.etSearch);

        ivLogo.setClickable(false);
        ivLogo.setFocusable(false);

        // Welcome message
        FirebaseUser user = auth.getCurrentUser();
        String userName = "Customer";
        if (user != null) {
            if (user.getDisplayName() != null && !user.getDisplayName().isEmpty()) {
                userName = user.getDisplayName();
            } else if (user.getEmail() != null) {
                userName = user.getEmail().split("@")[0];
            }
        }
        tvWelcome.setText("Welcome, " + userName + "!");

        // RecyclerView setup
        recyclerMain.setLayoutManager(new LinearLayoutManager(this));
        foodAdapter = new FoodAdapter(filteredFoodList);
        recyclerMain.setAdapter(foodAdapter);

        loadFoodItemsFromFirebase();

        // Bottom navigation
        bottomNavigation.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_home) return true;
            if (item.getItemId() == R.id.nav_cart)
                startActivity(new Intent(MainActivity.this, CartActivity.class));
            else if (item.getItemId() == R.id.nav_orders)
                startActivity(new Intent(MainActivity.this, OrdersActivity.class));
            else if (item.getItemId() == R.id.nav_profile)
                startActivity(new Intent(MainActivity.this, ProfileActivity.class));
            return true;
        });

        // Shopee-style scroll
        recyclerMain.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                float appBarHeight = appBarLayout.getHeight();
                float menuHeight = menuOrdersLayout.getHeight();
                float bottomHeight = bottomNavigation.getHeight();

                float newAppBarY = appBarLayout.getTranslationY() - dy;
                float newMenuY = menuOrdersLayout.getTranslationY() - dy;
                float newBottomY = bottomNavigation.getTranslationY() + dy;

                newAppBarY = Math.max(-appBarHeight, Math.min(0, newAppBarY));
                newMenuY = Math.max(-menuHeight, Math.min(0, newMenuY));
                newBottomY = Math.max(0, Math.min(bottomHeight, newBottomY));

                appBarLayout.setTranslationY(newAppBarY);
                menuOrdersLayout.setTranslationY(newMenuY);
                bottomNavigation.setTranslationY(newBottomY);
            }
        });

        // Search functionality
        searchEditText.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFood(s.toString());
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
    }

    // Filter foods
    private void filterFood(String query) {
        filteredFoodList.clear();
        if (query.isEmpty()) {
            filteredFoodList.addAll(foodList);
        } else {
            String lowerQuery = query.toLowerCase();
            for (FoodItem food : foodList) {
                if (food.name != null && food.name.toLowerCase().contains(lowerQuery)) {
                    filteredFoodList.add(food);
                }
            }
        }
        foodAdapter.updateList(filteredFoodList);
    }

    // Load food items from Firebase
    private void loadFoodItemsFromFirebase() {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("foods");
        foodRef.get().addOnSuccessListener(snapshot -> {
            foodList.clear();
            for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                FoodItem food = itemSnapshot.getValue(FoodItem.class);
                if (food != null) foodList.add(food);
            }
            filteredFoodList.clear();
            filteredFoodList.addAll(foodList);
            foodAdapter.notifyDataSetChanged();
        }).addOnFailureListener(e ->
                Toast.makeText(MainActivity.this, "Failed to load menu items: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    // Convert Base64 to Bitmap
    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }

    // RecyclerView Adapter
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private final List<FoodItem> foods;

        public FoodAdapter(List<FoodItem> foods) {
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

            holder.itemView.setAlpha(food.available ? 1.0f : 0.5f);
            holder.btnAddCart.setEnabled(food.available);

            holder.btnAddCart.setOnClickListener(v -> addToCart(food));
        }

        @Override
        public int getItemCount() {
            return foods.size();
        }

        public void updateList(List<FoodItem> newList) {
            foods.clear();
            foods.addAll(newList);
            notifyDataSetChanged();
        }

        private void addToCart(FoodItem food) {
            FirebaseUser currentUser = auth.getCurrentUser();
            if (currentUser == null) {
                Toast.makeText(MainActivity.this, "Please log in first", Toast.LENGTH_SHORT).show();
                return;
            }

            DatabaseReference cartRef = FirebaseDatabase.getInstance()
                    .getReference("carts")
                    .child(currentUser.getUid());

            cartRef.orderByChild("name").equalTo(food.name)
                    .addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            if (snapshot.exists()) {
                                for (DataSnapshot child : snapshot.getChildren()) {
                                    CartFoodItem existingItem = child.getValue(CartFoodItem.class);
                                    if (existingItem != null) {
                                        existingItem.quantity += 1;
                                        child.getRef().setValue(existingItem);
                                    }
                                }
                                Toast.makeText(MainActivity.this, food.name + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                            } else {
                                CartFoodItem newItem = new CartFoodItem(food.name, food.price, 1);
                                cartRef.push().setValue(newItem)
                                        .addOnSuccessListener(aVoid ->
                                                Toast.makeText(MainActivity.this, food.name + " added to cart!", Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e ->
                                                Toast.makeText(MainActivity.this, "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                            Toast.makeText(MainActivity.this, "Failed to access cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
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

    public static class CartFoodItem {
        public String name;
        public double price;
        public int quantity;

        public CartFoodItem() {}
        public CartFoodItem(String name, double price, int quantity) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }
    }
}
