package com.example.a4csofo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class ClientHomeFragment extends Fragment {

    private TextView tvWelcome;
    private ImageView ivLogo;
    private RecyclerView recyclerMain;
    private LinearLayout menuOrdersLayout; // KEEP THIS BUT DON'T USE IT
    private AppBarLayout appBarLayout;
    private EditText searchEditText;

    // NEW: Category buttons
    private LinearLayout btnMainDish, btnDrinks, btnSnacks, btnDesserts;
    private String currentCategory = "All"; // Track current category

    private FirebaseAuth auth;
    private List<FoodItem> foodList = new ArrayList<>();
    private List<FoodItem> filteredFoodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    public ClientHomeFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_client_home, container, false);

        // Initialize views
        auth = FirebaseAuth.getInstance();
        tvWelcome = view.findViewById(R.id.tvWelcome);
        ivLogo = view.findViewById(R.id.ivLogo);
        recyclerMain = view.findViewById(R.id.recyclerMain);
        menuOrdersLayout = view.findViewById(R.id.menuOrdersLayout); // KEEP BUT COMMENT OUT LATER
        searchEditText = view.findViewById(R.id.etSearch);
        appBarLayout = view.findViewById(R.id.appBarLayout);

        // NEW: Initialize category buttons
        btnMainDish = view.findViewById(R.id.btnMainDish);
        btnDrinks = view.findViewById(R.id.btnDrinks);
        btnSnacks = view.findViewById(R.id.btnSnacks);
        btnDesserts = view.findViewById(R.id.btnDesserts);

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
        recyclerMain.setLayoutManager(new LinearLayoutManager(requireContext()));
        foodAdapter = new FoodAdapter(filteredFoodList);
        recyclerMain.setAdapter(foodAdapter);

        loadFoodItemsFromFirebase();

        // Shopee-style scroll effect - FIXED VERSION
        recyclerMain.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                // SIMPLIFIED VERSION - AppBar effect only
                float appBarHeight = appBarLayout.getHeight();
                float newAppBarY = appBarLayout.getTranslationY() - dy;
                newAppBarY = Math.max(-appBarHeight, Math.min(0, newAppBarY));
                appBarLayout.setTranslationY(newAppBarY);

                // REMOVED ALL menuOrdersLayout REFERENCES
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

        // NEW: Category button click listeners
        setupCategoryButtons();

        return view;
    }

    // NEW: Setup category button listeners
    private void setupCategoryButtons() {
        // Main Dish button
        btnMainDish.setOnClickListener(v -> filterByCategory("Main Dish"));

        // Drinks button
        btnDrinks.setOnClickListener(v -> filterByCategory("Drinks"));

        // Snacks button
        btnSnacks.setOnClickListener(v -> filterByCategory("Snacks"));

        // Desserts button
        btnDesserts.setOnClickListener(v -> filterByCategory("Desserts"));
    }

    // NEW: Filter by category
    private void filterByCategory(String category) {
        currentCategory = category;

        // Reset all button appearances
        resetCategoryButtons();

        // Highlight selected button
        highlightCategoryButton(category);

        // Apply category filter
        String searchQuery = searchEditText.getText().toString();
        applyFilters(searchQuery, category);
    }

    // NEW: Reset all category buttons to default appearance
    private void resetCategoryButtons() {
        // Reset all buttons (you can customize the appearance)
        btnMainDish.setAlpha(0.8f);
        btnDrinks.setAlpha(0.8f);
        btnSnacks.setAlpha(0.8f);
        btnDesserts.setAlpha(0.8f);
    }

    // NEW: Highlight selected category button
    private void highlightCategoryButton(String category) {
        switch (category) {
            case "Main Dish":
                btnMainDish.setAlpha(1.0f);
                break;
            case "Drinks":
                btnDrinks.setAlpha(1.0f);
                break;
            case "Snacks":
                btnSnacks.setAlpha(1.0f);
                break;
            case "Desserts":
                btnDesserts.setAlpha(1.0f);
                break;
        }
    }

    // NEW: Apply both search and category filters
    private void applyFilters(String searchQuery, String category) {
        filteredFoodList.clear();

        for (FoodItem food : foodList) {
            boolean matchesSearch = searchQuery.isEmpty() ||
                    (food.name != null && food.name.toLowerCase().contains(searchQuery.toLowerCase()));

            boolean matchesCategory = category.equals("All") ||
                    (food.category != null && food.category.equals(category));

            if (matchesSearch && matchesCategory) {
                filteredFoodList.add(food);
            }
        }

        foodAdapter.updateList(filteredFoodList);

        // Show message if no items found
        if (filteredFoodList.isEmpty()) {
            Toast.makeText(requireContext(),
                    "No items found for '" + searchQuery + "' in " + category,
                    Toast.LENGTH_SHORT).show();
        }
    }

    // Filter foods by search (updated to include category)
    private void filterFood(String query) {
        applyFilters(query, currentCategory);
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
                Toast.makeText(requireContext(), "Failed to load menu items: " + e.getMessage(), Toast.LENGTH_SHORT).show()
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

    // RecyclerView Adapter (no changes needed)
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private final List<FoodItem> foods;

        public FoodAdapter(List<FoodItem> foods) {
            this.foods = foods;
        }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
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
                Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
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
                                Toast.makeText(requireContext(), food.name + " quantity updated in cart!", Toast.LENGTH_SHORT).show();
                            } else {
                                CartFoodItem newItem = new CartFoodItem(food.name, food.price, 1);
                                cartRef.push().setValue(newItem)
                                        .addOnSuccessListener(aVoid ->
                                                Toast.makeText(requireContext(), food.name + " added to cart!", Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e ->
                                                Toast.makeText(requireContext(), "Failed to add to cart: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {
                            Toast.makeText(requireContext(), "Failed to access cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFoodImage;
            TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime;
            android.widget.Button btnAddCart;

            public FoodViewHolder(@NonNull View itemView) {
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

    // Data models (no changes needed)
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