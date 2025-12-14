package com.example.a4csofo;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ClientHomeFragment extends Fragment {

    private TextView tvWelcome;
    private ImageView ivLogo;
    private RecyclerView recyclerMain;
    private LinearLayout menuOrdersLayout;
    private AppBarLayout appBarLayout;
    private EditText searchEditText;

    private LinearLayout btnAll, btnMainDish, btnDrinks, btnSnacks, btnDesserts;
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

        btnAll = view.findViewById(R.id.btnAll);
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

        filteredFoodList = new ArrayList<>();
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
        btnAll.setOnClickListener(v -> filterByCategory("all"));
        btnMainDish.setOnClickListener(v -> filterByCategory("main dish"));
        btnDrinks.setOnClickListener(v -> filterByCategory("drinks"));
        btnSnacks.setOnClickListener(v -> filterByCategory("snacks"));
        btnDesserts.setOnClickListener(v -> filterByCategory("dessert"));

        resetCategoryButtons();
        highlightCategoryButton("all");
    }

    private void setupSearchBar() {
        searchEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterFood(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void filterByCategory(String category) {
        currentCategory = category.toLowerCase();

        if (currentCategory.contains("dessert")) {
            currentCategory = "dessert";
        }

        resetCategoryButtons();
        highlightCategoryButton(currentCategory);

        if (currentCategory.equals("all")) {
            filteredFoodList.clear();
            filteredFoodList.addAll(foodList);
            String query = searchEditText.getText().toString();
            if (!query.isEmpty()) applyFilters(query, "all");
            else foodAdapter.updateList(filteredFoodList);
            return;
        }

        String query = searchEditText.getText().toString();
        applyFilters(query, currentCategory);
    }

    private void resetCategoryButtons() {
        btnAll.setAlpha(0.8f);
        btnMainDish.setAlpha(0.8f);
        btnDrinks.setAlpha(0.8f);
        btnSnacks.setAlpha(0.8f);
        btnDesserts.setAlpha(0.8f);

        btnAll.setScaleX(1f); btnAll.setScaleY(1f);
        btnMainDish.setScaleX(1f); btnMainDish.setScaleY(1f);
        btnDrinks.setScaleX(1f); btnDrinks.setScaleY(1f);
        btnSnacks.setScaleX(1f); btnSnacks.setScaleY(1f);
        btnDesserts.setScaleX(1f); btnDesserts.setScaleY(1f);
    }

    private void highlightCategoryButton(String category) {
        switch (category) {
            case "all":
                btnAll.setAlpha(1f);
                btnAll.setScaleX(1.08f);
                btnAll.setScaleY(1.08f);
                break;
            case "main dish":
                btnMainDish.setAlpha(1f);
                btnMainDish.setScaleX(1.08f);
                btnMainDish.setScaleY(1.08f);
                break;
            case "drinks":
                btnDrinks.setAlpha(1f);
                btnDrinks.setScaleX(1.08f);
                btnDrinks.setScaleY(1.08f);
                break;
            case "snacks":
                btnSnacks.setAlpha(1f);
                btnSnacks.setScaleX(1.08f);
                btnSnacks.setScaleY(1.08f);
                break;
            case "dessert":
                btnDesserts.setAlpha(1f);
                btnDesserts.setScaleX(1.08f);
                btnDesserts.setScaleY(1.08f);
                break;
            default:
                break;
        }
    }

    private void filterFood(String query) {
        applyFilters(query, currentCategory);
    }

    private void applyFilters(String searchQuery, String category) {
        List<FoodItem> tempFilteredList = new ArrayList<>();

        String query = (searchQuery == null ? "" : searchQuery.toLowerCase().trim());
        String cat = (category == null ? "all" : category.toLowerCase().trim());

        for (FoodItem food : foodList) {
            if (food == null || food.name == null) continue;

            String foodName = food.name.toLowerCase().trim();
            String foodCategory = (food.category == null ? "" : food.category.toLowerCase().trim());

            boolean matchesSearch = query.isEmpty() || foodName.contains(query);
            boolean matchesCategory = cat.equals("all") || foodCategory.equals(cat);

            if (matchesSearch && matchesCategory) tempFilteredList.add(food);
        }

        filteredFoodList.clear();
        filteredFoodList.addAll(tempFilteredList);
        foodAdapter.updateList(filteredFoodList);

        if (filteredFoodList.isEmpty() && (!query.isEmpty() || !cat.equals("all"))) {
            Toast.makeText(getContext(), "No items found", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadFoodItemsFromFirebase() {
        DatabaseReference foodRef = FirebaseDatabase.getInstance().getReference("foods");
        foodRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();

                for (DataSnapshot itemSnapshot : snapshot.getChildren()) {
                    FoodItem food = itemSnapshot.getValue(FoodItem.class);
                    if (food != null) {
                        // Load discounts
                        if (itemSnapshot.child("discountSenior").exists()) {
                            Object seniorDiscount = itemSnapshot.child("discountSenior").getValue();
                            if (seniorDiscount != null) {
                                try {
                                    food.discountSenior = Double.parseDouble(seniorDiscount.toString());
                                } catch (NumberFormatException e) { food.discountSenior = 0; }
                            }
                        }

                        if (itemSnapshot.child("discountPWD").exists()) {
                            Object pwdDiscount = itemSnapshot.child("discountPWD").getValue();
                            if (pwdDiscount != null) {
                                try {
                                    food.discountPWD = Double.parseDouble(pwdDiscount.toString());
                                } catch (NumberFormatException e) { food.discountPWD = 0; }
                            }
                        }

                        // Load ratings
                        if (itemSnapshot.child("rating").exists()) {
                            Object ratingObj = itemSnapshot.child("rating").getValue();
                            if (ratingObj != null) {
                                try { food.rating = Double.parseDouble(ratingObj.toString()); }
                                catch (NumberFormatException e) { food.rating = 0; }
                            }
                        }
                        if (itemSnapshot.child("ratingCount").exists()) {
                            Object ratingCountObj = itemSnapshot.child("ratingCount").getValue();
                            if (ratingCountObj != null) {
                                try { food.ratingCount = Integer.parseInt(ratingCountObj.toString()); }
                                catch (NumberFormatException e) { food.ratingCount = 0; }
                            }
                        }

                        foodList.add(food);
                    }
                }

                filteredFoodList.clear();
                filteredFoodList.addAll(foodList);
                foodAdapter.updateList(filteredFoodList);

                if (foodList.isEmpty()) {
                    Toast.makeText(getContext(), "No food items available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(requireContext(), "Failed to load menu items", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ===== MODAL DIALOG FOR FOOD DETAILS =====
    private void showFoodDetailsModal(FoodItem food) {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme);
        View dialogView = getLayoutInflater().inflate(R.layout.modal_food_details, null);
        dialog.setContentView(dialogView);

        // Initialize modal views
        ImageView ivFoodImage = dialogView.findViewById(R.id.ivFoodImageModal);
        TextView tvFoodName = dialogView.findViewById(R.id.tvFoodNameModal);
        TextView tvFoodPrice = dialogView.findViewById(R.id.tvFoodPriceModal);
        TextView tvFoodCategory = dialogView.findViewById(R.id.tvFoodCategoryModal);
        TextView tvFoodDescription = dialogView.findViewById(R.id.tvFoodDescriptionModal);
        TextView tvPrepTime = dialogView.findViewById(R.id.tvPrepTimeModal);
        TextView tvDiscountSenior = dialogView.findViewById(R.id.tvDiscountSenior);
        TextView tvDiscountPWD = dialogView.findViewById(R.id.tvDiscountPWD);
        LinearLayout discountSeniorLayout = dialogView.findViewById(R.id.discountSeniorLayout);
        LinearLayout discountPWDLayout = dialogView.findViewById(R.id.discountPWDLayout);
        Button btnAddToCart = dialogView.findViewById(R.id.btnAddToCartModal);
        Button btnClose = dialogView.findViewById(R.id.btnCloseModal);
        RatingBar ratingBar = dialogView.findViewById(R.id.ratingBarModal);
        TextView tvRatingText = dialogView.findViewById(R.id.tvFoodRatingModal);

        // Set food data
        tvFoodName.setText(food.name);
        tvFoodPrice.setText(formatPrice(food.price));
        tvFoodCategory.setText(food.category != null ? food.category : "Uncategorized");
        tvFoodDescription.setText(food.description != null ? food.description : "No description available");
        tvPrepTime.setText("Prep Time: " + (food.prepTime != null ? food.prepTime : "0 mins"));

        // Load image
        if (food.base64Image != null && !food.base64Image.isEmpty()) {
            Bitmap bitmap = base64ToBitmap(food.base64Image);
            if (bitmap != null) ivFoodImage.setImageBitmap(bitmap);
            else ivFoodImage.setImageResource(R.drawable.ic_placeholder);
        } else {
            ivFoodImage.setImageResource(R.drawable.ic_placeholder);
        }

        // Show discounts if available
        if (food.discountSenior > 0) {
            discountSeniorLayout.setVisibility(View.VISIBLE);
            tvDiscountSenior.setText(String.format("%.0f%%", food.discountSenior) + " Senior Discount");
        } else {
            discountSeniorLayout.setVisibility(View.GONE);
        }

        if (food.discountPWD > 0) {
            discountPWDLayout.setVisibility(View.VISIBLE);
            tvDiscountPWD.setText(String.format("%.0f%%", food.discountPWD) + " PWD Discount");
        } else {
            discountPWDLayout.setVisibility(View.GONE);
        }

        // Show rating
        ratingBar.setRating((float) food.rating);
        if (food.ratingCount > 0) tvRatingText.setText(String.format("%.1f/5 (%d ratings)", food.rating, food.ratingCount));
        else tvRatingText.setText("Not yet rated");

        // Add to cart button
        btnAddToCart.setOnClickListener(v -> {
            addToCartFromModal(food);
            dialog.dismiss();
        });

        // Close button
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void addToCartFromModal(FoodItem food) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(requireContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        cartRef.orderByChild("name").equalTo(food.name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
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
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(requireContext(), "Failed to access cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private String formatPrice(double price) {
        NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        return format.format(price);
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ===== FOOD ADAPTER =====
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private List<FoodItem> foods;

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
            if (foods == null || foods.isEmpty() || position >= foods.size()) return;

            FoodItem food = foods.get(position);
            if (food == null) return;

            holder.tvName.setText(food.name != null ? food.name : "No Name");
            holder.tvPrice.setText("₱" + String.format("%.2f", food.price));
            holder.tvDesc.setText(food.description != null ? food.description : "No description");
            holder.tvCategory.setText(food.category != null ? food.category : "Uncategorized");
            holder.tvPrepTime.setText((food.prepTime != null ? food.prepTime : "0") + " mins");

            if (food.base64Image != null && !food.base64Image.isEmpty()) {
                Bitmap bitmap = base64ToBitmap(food.base64Image);
                if (bitmap != null) holder.ivFoodImage.setImageBitmap(bitmap);
                else holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            holder.itemView.setAlpha(food.available ? 1f : 0.5f);
            holder.btnAddCart.setEnabled(food.available);

            if (food.available) {
                holder.btnAddCart.setOnClickListener(v -> addToCart(food));
                holder.btnAddCart.setText("Add to Cart");
            } else {
                holder.btnAddCart.setOnClickListener(null);
                holder.btnAddCart.setText("Unavailable");
            }

            // ===== RATING DISPLAY IN ITEM =====
            if (food.ratingCount > 0) {
                holder.ratingBar.setRating((float) food.rating);
                holder.tvRating.setText(String.format("%.1f/5 (%d ratings)", food.rating, food.ratingCount));
            } else {
                holder.ratingBar.setRating(0);
                holder.tvRating.setText("Not yet rated");
            }

            holder.itemView.setOnClickListener(v -> showFoodDetailsModal(food));
            holder.itemView.setOnLongClickListener(v -> {
                Toast.makeText(v.getContext(), food.name + " - " + formatPrice(food.price), Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return foods != null ? foods.size() : 0;
        }

        public void updateList(List<FoodItem> newList) {
            this.foods = newList;
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
                    .addListenerForSingleValueEvent(new ValueEventListener() {
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
                        public void onCancelled(@NonNull DatabaseError error) {
                            Toast.makeText(requireContext(), "Failed to access cart: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    });
        }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFoodImage;
            TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime, tvRating;
            RatingBar ratingBar;
            Button btnAddCart;

            public FoodViewHolder(@NonNull View itemView) {
                super(itemView);
                ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
                tvName = itemView.findViewById(R.id.tvFoodName);
                tvPrice = itemView.findViewById(R.id.tvFoodPrice);
                tvDesc = itemView.findViewById(R.id.tvFoodDesc);
                tvCategory = itemView.findViewById(R.id.tvFoodCategory);
                tvPrepTime = itemView.findViewById(R.id.tvFoodPrepTime);
                tvRating = itemView.findViewById(R.id.tvFoodRating); // add TextView in layout
                ratingBar = itemView.findViewById(R.id.ratingBar);    // add RatingBar in layout
                btnAddCart = itemView.findViewById(R.id.btnAddCart);
            }
        }
    }

    // ===== FOOD ITEM CLASS =====
    public static class FoodItem {
        public String name;
        public String description;
        public String category;
        public String prepTime;
        public double price;
        public String base64Image;
        public boolean available = true;
        public double discountSenior = 0;
        public double discountPWD = 0;

        public double rating = 0;
        public int ratingCount = 0;

        public FoodItem() {}
    }

    // ===== CART FOOD ITEM CLASS (WITHOUT ADD-ONS) =====
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
