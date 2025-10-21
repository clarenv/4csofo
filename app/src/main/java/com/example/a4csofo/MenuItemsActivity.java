package com.example.a4csofo;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

public class MenuItemsActivity extends AppCompatActivity {

    private EditText etName, etPrice, etPrepTime, etDescription;
    private Spinner spCategory;
    private Button btnAddFood;
    private RecyclerView rvFoods;

    private DatabaseReference foodsRef;

    private List<FoodItem> foodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu_items);

        etName = findViewById(R.id.etName);
        etPrice = findViewById(R.id.etPrice);
        etPrepTime = findViewById(R.id.etPrepTime);
        etDescription = findViewById(R.id.etDescription);
        spCategory = findViewById(R.id.spCategory);
        btnAddFood = findViewById(R.id.btnAddFood);
        rvFoods = findViewById(R.id.rvFoods);

        // Firebase reference
        foodsRef = FirebaseDatabase.getInstance().getReference("foods");

        // Spinner setup
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{"Main Dish", "Drinks", "Dessert", "Snacks"}
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        // RecyclerView setup
        rvFoods.setLayoutManager(new LinearLayoutManager(this));
        foodAdapter = new FoodAdapter(foodList);
        rvFoods.setAdapter(foodAdapter);

        loadFoods();

        btnAddFood.setOnClickListener(v -> addFoodItem());
    }

    private void loadFoods() {
        foodsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    FoodItem food = data.getValue(FoodItem.class);
                    if (food != null) foodList.add(food);
                }
                foodAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MenuItemsActivity.this, "Failed to load foods: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addFoodItem() {
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String category = spCategory.getSelectedItem() != null ? spCategory.getSelectedItem().toString() : "";

        if (name.isEmpty() || priceStr.isEmpty() || prepTime.isEmpty()) {
            Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price", Toast.LENGTH_SHORT).show();
            return;
        }

        btnAddFood.setEnabled(false);
        btnAddFood.setText("Adding...");

        String key = foodsRef.push().getKey();
        if (key == null) {
            Toast.makeText(this, "Database error: could not generate key", Toast.LENGTH_SHORT).show();
            btnAddFood.setEnabled(true);
            btnAddFood.setText("Add Food Item");
            return;
        }

        FoodItem food = new FoodItem(name, price, prepTime, description, category, null);

        foodsRef.child(key).setValue(food)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(MenuItemsActivity.this, "Food added successfully!", Toast.LENGTH_SHORT).show();
                    clearFields();
                    btnAddFood.setEnabled(true);
                    btnAddFood.setText("Add Food Item");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(MenuItemsActivity.this, "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnAddFood.setEnabled(true);
                    btnAddFood.setText("Add Food Item");
                });
    }

    private void clearFields() {
        etName.setText("");
        etPrice.setText("");
        etPrepTime.setText("");
        etDescription.setText("");
    }

    // Model class
    public static class FoodItem {
        public String name;
        public double price;
        public String prepTime;
        public String description;
        public String category;
        public String imageUrl; // null for now

        public FoodItem() {}
        public FoodItem(String name, double price, String prepTime, String description, String category, String imageUrl) {
            this.name = name;
            this.price = price;
            this.prepTime = prepTime;
            this.description = description;
            this.category = category;
            this.imageUrl = imageUrl;
        }
    }

    // RecyclerView Adapter
    public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {
        private final List<FoodItem> foods;

        public FoodAdapter(List<FoodItem> foods) { this.foods = foods; }

        @NonNull
        @Override
        public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_food, parent, false);
            return new FoodViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
            FoodItem food = foods.get(position);
            holder.tvName.setText(food.name + " - ₱" + food.price);
            holder.tvDesc.setText(food.description + " (" + food.prepTime + " mins, " + food.category + ")");
        }

        @Override
        public int getItemCount() { return foods.size(); }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvDesc;

            public FoodViewHolder(@NonNull View itemView) {
                super(itemView);
                tvName = itemView.findViewById(R.id.tvFoodName);
                tvDesc = itemView.findViewById(R.id.tvFoodDesc);
            }
        }
    }
}
