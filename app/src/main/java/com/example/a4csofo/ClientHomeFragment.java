package com.example.a4csofo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
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
    private LinearLayout menuOrdersLayout;
    private AppBarLayout appBarLayout;
    private EditText searchEditText;

    private LinearLayout btnMainDish, btnDrinks, btnSnacks, btnDesserts;
    private String currentCategory = "all";

    private FirebaseAuth auth;
    private List<FoodItem> foodList = new ArrayList<>();
    private List<FoodItem> filteredFoodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    public ClientHomeFragment() {}

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_client_home, container, false);

        auth = FirebaseAuth.getInstance();
        tvWelcome = view.findViewById(R.id.tvWelcome);
        ivLogo = view.findViewById(R.id.ivLogo);
        recyclerMain = view.findViewById(R.id.recyclerMain);
        menuOrdersLayout = view.findViewById(R.id.menuOrdersLayout);
        searchEditText = view.findViewById(R.id.etSearch);
        appBarLayout = view.findViewById(R.id.appBarLayout);

        btnMainDish = view.findViewById(R.id.btnMainDish);
        btnDrinks = view.findViewById(R.id.btnDrinks);
        btnSnacks = view.findViewById(R.id.btnSnacks);
        btnDesserts = view.findViewById(R.id.btnDesserts);

        ivLogo.setClickable(false);
        ivLogo.setFocusable(false);

        setupWelcomeMessage();
        setupRecyclerView();
        setupCategoryButtons();
        setupSearchBar();

        return view;
    }

    private void setupWelcomeMessage() {
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
    }

    private void setupRecyclerView() {
        recyclerMain.setLayoutManager(new LinearLayoutManager(requireContext()));
        foodAdapter = new FoodAdapter(filteredFoodList);
        recyclerMain.setAdapter(foodAdapter);

        loadFoodItemsFromFirebase();

        recyclerMain.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                float appBarHeight = appBarLayout.getHeight();
                float newAppBarY = appBarLayout.getTranslationY() - dy;
                newAppBarY = Math.max(-appBarHeight, Math.min(0, newAppBarY));
                appBarLayout.setTranslationY(newAppBarY);
            }
        });
    }

    private void setupCategoryButtons() {
        btnMainDish.setOnClickListener(v -> filterByCategory("Main Dish"));
        btnDrinks.setOnClickListener(v -> filterByCategory("Drinks"));
        btnSnacks.setOnClickListener(v -> filterByCategory("Snacks"));
        btnDesserts.setOnClickListener(v -> filterByCategory("Dessert"));

        resetCategoryButtons();
        highlightCategoryButton("all");
    }

    private void setupSearchBar() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFood(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterByCategory(String category) {
        currentCategory = category.toLowerCase().trim();
        resetCategoryButtons();
        highlightCategoryButton(currentCategory);
        String query = searchEditText.getText().toString();
        applyFilters(query, currentCategory);
    }

    private void resetCategoryButtons() {
        btnMainDish.setAlpha(0.8f); btnDrinks.setAlpha(0.8f);
        btnSnacks.setAlpha(0.8f); btnDesserts.setAlpha(0.8f);

        btnMainDish.setScaleX(1f); btnMainDish.setScaleY(1f);
        btnDrinks.setScaleX(1f); btnDrinks.setScaleY(1f);
        btnSnacks.setScaleX(1f); btnSnacks.setScaleY(1f);
        btnDesserts.setScaleX(1f); btnDesserts.setScaleY(1f);
    }

    private void highlightCategoryButton(String category) {
        switch (category) {
            case "main dish":
                btnMainDish.setAlpha(1f); btnMainDish.setScaleX(1.08f); btnMainDish.setScaleY(1.08f);
                break;
            case "drinks":
                btnDrinks.setAlpha(1f); btnDrinks.setScaleX(1.08f); btnDrinks.setScaleY(1.08f);
                break;
            case "snacks":
                btnSnacks.setAlpha(1f); btnSnacks.setScaleX(1.08f); btnSnacks.setScaleY(1.08f);
                break;
            case "dessert":
            case "desserts":
                btnDesserts.setAlpha(1f); btnDesserts.setScaleX(1.08f); btnDesserts.setScaleY(1.08f);
                break;
            default: break;
        }
    }

    private void filterFood(String query) {
        applyFilters(query, currentCategory);
    }

    private void applyFilters(String searchQuery, String category) {
        filteredFoodList.clear();

        String query = (searchQuery == null ? "" : searchQuery.toLowerCase().trim());
        String cat = (category == null ? "all" : category.toLowerCase().trim());

        for (FoodItem food : foodList) {
            if (food == null) continue;

            String foodName = (food.name == null ? "" : food.name.toLowerCase().trim());
            String foodCategory = (food.category == null ? "" : food.category.toLowerCase().trim());

            boolean matchesSearch = query.isEmpty() || foodName.contains(query);
            boolean matchesCategory = cat.equals("all") || foodCategory.equals(cat);

            if (matchesSearch && matchesCategory) filteredFoodList.add(food);
        }

        foodAdapter.updateList(filteredFoodList);

        Log.d("SearchDebug", "Query: '" + query + "' | Category: '" + cat + "' | Matches: " + filteredFoodList.size());
    }

    private void loadFoodItemsFromFirebase() {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("foods");
        foodRef.get().addOnSuccessListener(snapshot -> {
            foodList.clear();
            for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                FoodItem food = itemSnapshot.getValue(FoodItem.class);
                if (food != null) {
                    foodList.add(food);
                    Log.d("FoodLoaded", "Name: " + food.name + ", Category: " + food.category);
                }
            }
            filteredFoodList.clear();
            filteredFoodList.addAll(foodList);
            foodAdapter.notifyDataSetChanged();
        }).addOnFailureListener(e ->
                Toast.makeText(requireContext(), "Failed to load menu items: " + e.getMessage(), Toast.LENGTH_SHORT).show()
        );
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return null;
        }
    }

    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private final List<FoodItem> foods;

        public FoodAdapter(List<FoodItem> foods) { this.foods = foods; }

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
            } else holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);

            holder.itemView.setAlpha(food.available ? 1f : 0.5f);
            holder.btnAddCart.setEnabled(food.available);
            holder.btnAddCart.setOnClickListener(v -> addToCart(food));
        }

        @Override
        public int getItemCount() { return foods.size(); }

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
                                CartFoodItem newItem = new CartFoodItem(food.name, food.price, 1, food.base64Image);
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
        public String base64Image;

        public CartFoodItem() {}

        public CartFoodItem(String name, double price, int quantity, String base64Image) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.base64Image = base64Image;
        }
    }
}
