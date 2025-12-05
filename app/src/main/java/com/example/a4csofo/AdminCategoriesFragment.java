package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.*;

import java.util.*;

public class AdminCategoriesFragment extends Fragment {

    private RecyclerView recyclerViewFoods;
    private DatabaseReference foodsRef;
    private List<ItemAdminCategory> foodList = new ArrayList<>();
    private List<ItemAdminCategory> filteredList = new ArrayList<>();
    private FoodAdapter foodAdapter;
    private Spinner spinnerCategories;
    private TextView tvEmptyState;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_categories, container, false);

        recyclerViewFoods = view.findViewById(R.id.recyclerViewFoods);
        spinnerCategories = view.findViewById(R.id.spinnerCategories);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        recyclerViewFoods.setLayoutManager(new LinearLayoutManager(getContext()));
        foodAdapter = new FoodAdapter(filteredList);
        recyclerViewFoods.setAdapter(foodAdapter);

        foodsRef = FirebaseDatabase.getInstance().getReference("foods");
        loadFoods();

        // Set spinner listener
        spinnerCategories.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = parent.getItemAtPosition(position).toString();
                filterFoodsByCategory(selectedCategory);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                filterFoodsByCategory("All");
            }
        });

        return view;
    }

    private void loadFoods() {
        foodsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();

                // Collect unique categories for spinner
                Set<String> categoriesSet = new HashSet<>();
                categoriesSet.add("All");

                for (DataSnapshot item : snapshot.getChildren()) {
                    ItemAdminCategory food = item.getValue(ItemAdminCategory.class);
                    if (food != null) {
                        food.setKey(item.getKey());
                        foodList.add(food);

                        // Add category to set
                        if (food.getCategory() != null && !food.getCategory().isEmpty()) {
                            categoriesSet.add(food.getCategory());
                        }
                    }
                }

                // Update spinner with categories
                List<String> categoriesList = new ArrayList<>(categoriesSet);
                Collections.sort(categoriesList);

                ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                        getContext(),
                        android.R.layout.simple_spinner_item,
                        categoriesList
                );
                spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCategories.setAdapter(spinnerAdapter);

                // Initial filter
                filterFoodsByCategory("All");

                // Show/hide empty state
                if (filteredList.isEmpty()) {
                    tvEmptyState.setVisibility(View.VISIBLE);
                } else {
                    tvEmptyState.setVisibility(View.GONE);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed to load foods: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterFoodsByCategory(String category) {
        filteredList.clear();

        if (category.equals("All")) {
            filteredList.addAll(foodList);
        } else {
            for (ItemAdminCategory food : foodList) {
                if (food.getCategory() != null && food.getCategory().equals(category)) {
                    filteredList.add(food);
                }
            }
        }

        foodAdapter.notifyDataSetChanged();

        // Update empty state
        if (filteredList.isEmpty()) {
            tvEmptyState.setText(category.equals("All") ?
                    "No food items found" : "No items in " + category + " category");
            tvEmptyState.setVisibility(View.VISIBLE);
        } else {
            tvEmptyState.setVisibility(View.GONE);
        }
    }

    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

        private final List<ItemAdminCategory> foods;

        public FoodAdapter(List<ItemAdminCategory> foods) {
            this.foods = foods;
        }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_admin_category, parent, false);
            return new FoodViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
            ItemAdminCategory food = foods.get(position);

            holder.tvName.setText(food.getName());
            holder.tvCategory.setText(food.getCategory());
            holder.tvPrice.setText("₱" + String.format("%.2f", food.getPrice()));
            holder.tvPrepTime.setText(food.getPrepTime() + " mins");
            holder.tvDesc.setText(food.getDescription());

            String extrasText = "";
            if (food.getAddOns() != null) extrasText += "Add-ons: " + String.join(", ", food.getAddOns()) + "\n";
            if (food.getIngredients() != null) extrasText += "Ingredients: " + String.join(", ", food.getIngredients()) + "\n";
            if (food.getServingSize() != null) extrasText += "Serving Size: " + food.getServingSize() + "\n";
            extrasText += "Calories: " + food.getCalories();
            holder.tvExtras.setText(extrasText);

            if (food.getImageBase64() != null && !food.getImageBase64().isEmpty()) {
                Bitmap bmp = base64ToBitmap(food.getImageBase64());
                holder.ivFoodImage.setImageBitmap(bmp);
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            holder.switchAvailable.setChecked(food.isAvailable());
            holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                food.setAvailable(isChecked);
                foodsRef.child(food.getKey()).child("available").setValue(isChecked)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(getContext(), "Availability updated", Toast.LENGTH_SHORT).show();
                        });
            });

            // Update button click - Shows update dialog
            holder.btnUpdate.setOnClickListener(v -> showAdminUpdateFoodDialog(food, position));

            // Delete button click
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Food")
                        .setMessage("Are you sure you want to delete " + food.getName() + "?")
                        .setPositiveButton("Yes", (dialog, which) ->
                                foodsRef.child(food.getKey()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            Toast.makeText(getContext(), food.getName() + " deleted", Toast.LENGTH_SHORT).show();
                                        })
                                        .addOnFailureListener(e -> {
                                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                                        }))
                        .setNegativeButton("No", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() { return foods.size(); }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFoodImage;
            TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime, tvExtras;
            Button btnUpdate, btnDelete;
            Switch switchAvailable;

            public FoodViewHolder(@NonNull View itemView) {
                super(itemView);
                ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
                tvName = itemView.findViewById(R.id.tvFoodName);
                tvPrice = itemView.findViewById(R.id.tvFoodPrice);
                tvDesc = itemView.findViewById(R.id.tvFoodDesc);
                tvCategory = itemView.findViewById(R.id.tvFoodCategory);
                tvPrepTime = itemView.findViewById(R.id.tvFoodPrepTime);
                tvExtras = itemView.findViewById(R.id.tvFoodExtras);
                btnUpdate = itemView.findViewById(R.id.btnUpdate);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                switchAvailable = itemView.findViewById(R.id.switchAvailable);
            }
        }
    }

    private void showAdminUpdateFoodDialog(ItemAdminCategory food, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Update " + food.getName());

        // Inflate the update dialog layout
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_admin_update_food, null);
        builder.setView(dialogView);

        // Initialize all form fields
        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etPrepTime = dialogView.findViewById(R.id.etPrepTime);
        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        EditText etAddOns = dialogView.findViewById(R.id.etAddOns);
        EditText etIngredients = dialogView.findViewById(R.id.etIngredients);
        EditText etServingSize = dialogView.findViewById(R.id.etServingSize);
        EditText etCalories = dialogView.findViewById(R.id.etCalories);
        Switch switchAvailable = dialogView.findViewById(R.id.switchAvailable);

        // Set current values
        etName.setText(food.getName() != null ? food.getName() : "");
        etPrice.setText(String.valueOf(food.getPrice()));
        etDescription.setText(food.getDescription() != null ? food.getDescription() : "");
        etPrepTime.setText(food.getPrepTime() != null ? food.getPrepTime() : "");

        // Set up category spinner with available categories
        List<String> categories = new ArrayList<>();
        categories.add("Main Dish");
        categories.add("Drinks");
        categories.add("Dessert");
        categories.add("Snacks");
        categories.add("Appetizer");

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                categories
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        // Set selected category
        if (food.getCategory() != null) {
            for (int i = 0; i < categories.size(); i++) {
                if (categories.get(i).equals(food.getCategory())) {
                    spCategory.setSelection(i);
                    break;
                }
            }
        }

        // Set other fields
        if (food.getAddOns() != null && !food.getAddOns().isEmpty()) {
            etAddOns.setText(String.join(", ", food.getAddOns()));
        }

        if (food.getIngredients() != null && !food.getIngredients().isEmpty()) {
            etIngredients.setText(String.join(", ", food.getIngredients()));
        }

        etServingSize.setText(food.getServingSize() != null ? food.getServingSize() : "");
        etCalories.setText(String.valueOf(food.getCalories()));
        switchAvailable.setChecked(food.isAvailable());

        // Set up the Update button
        builder.setPositiveButton("Update", (dialog, which) -> {
            // Get updated values
            String name = etName.getText().toString().trim();
            String priceStr = etPrice.getText().toString().trim();
            String description = etDescription.getText().toString().trim();
            String prepTime = etPrepTime.getText().toString().trim();
            String category = spCategory.getSelectedItem().toString();
            String addOnsStr = etAddOns.getText().toString().trim();
            String ingredientsStr = etIngredients.getText().toString().trim();
            String servingSize = etServingSize.getText().toString().trim();
            String caloriesStr = etCalories.getText().toString().trim();
            boolean available = switchAvailable.isChecked();

            // Validate inputs
            if (name.isEmpty()) {
                Toast.makeText(getContext(), "Please enter food name", Toast.LENGTH_SHORT).show();
                return;
            }

            if (priceStr.isEmpty()) {
                Toast.makeText(getContext(), "Please enter price", Toast.LENGTH_SHORT).show();
                return;
            }

            double price;
            try {
                price = Double.parseDouble(priceStr);
            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Invalid price format", Toast.LENGTH_SHORT).show();
                return;
            }

            int calories = 0;
            if (!caloriesStr.isEmpty()) {
                try {
                    calories = Integer.parseInt(caloriesStr);
                } catch (NumberFormatException e) {
                    Toast.makeText(getContext(), "Invalid calories format", Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            // Convert comma-separated strings to lists
            List<String> addOns = null;
            if (!addOnsStr.isEmpty()) {
                addOns = Arrays.asList(addOnsStr.split("\\s*,\\s*"));
            }

            List<String> ingredients = null;
            if (!ingredientsStr.isEmpty()) {
                ingredients = Arrays.asList(ingredientsStr.split("\\s*,\\s*"));
            }

            // Update the food object
            food.setName(name);
            food.setPrice(price);
            food.setDescription(description);
            food.setPrepTime(prepTime);
            food.setCategory(category);
            food.setAddOns(addOns);
            food.setIngredients(ingredients);
            food.setServingSize(servingSize);
            food.setCalories(calories);
            food.setAvailable(available);

            // Update in Firebase
            foodsRef.child(food.getKey()).setValue(food)
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(getContext(), food.getName() + " updated successfully", Toast.LENGTH_SHORT).show();
                        // Refresh the adapter
                        foodAdapter.notifyItemChanged(position);
                    })
                    .addOnFailureListener(e -> {
                        Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });

        // Set up the Cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (Exception e) {
            Log.e("AdminCategories", "Error converting base64 to bitmap: " + e.getMessage());
            return null;
        }
    }

    public static class ItemAdminCategory {
        private String key, category, description, name, prepTime, servingSize;
        private double price;
        private int calories;
        private List<String> addOns, ingredients;
        private String imageBase64;
        private boolean available = true;

        public ItemAdminCategory() {}

        // Getters and setters
        public String getKey() { return key; }
        public void setKey(String key) { this.key = key; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPrepTime() { return prepTime; }
        public void setPrepTime(String prepTime) { this.prepTime = prepTime; }

        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }

        public String getImageBase64() { return imageBase64; }
        public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }

        public List<String> getAddOns() { return addOns; }
        public void setAddOns(List<String> addOns) { this.addOns = addOns; }

        public List<String> getIngredients() { return ingredients; }
        public void setIngredients(List<String> ingredients) { this.ingredients = ingredients; }

        public String getServingSize() { return servingSize; }
        public void setServingSize(String servingSize) { this.servingSize = servingSize; }

        public int getCalories() { return calories; }
        public void setCalories(int calories) { this.calories = calories; }
    }
}