package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
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

import java.util.ArrayList;
import java.util.List;

public class AdminCategoriesFragment extends Fragment {

    private RecyclerView recyclerViewFoods;
    private DatabaseReference foodsRef;
    private List<ItemAdminCategory> foodList = new ArrayList<>();
    private FoodAdapter foodAdapter;

    @SuppressLint("MissingInflatedId")
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_categories, container, false);

        recyclerViewFoods = view.findViewById(R.id.recyclerViewFoods);
        recyclerViewFoods.setLayoutManager(new LinearLayoutManager(getContext()));
        foodAdapter = new FoodAdapter(foodList);
        recyclerViewFoods.setAdapter(foodAdapter);

        foodsRef = FirebaseDatabase.getInstance().getReference("foods");

        loadFoods(); // Load all foods from Firebase

        return view;
    }

    private void loadFoods() {
        foodsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();
                for (DataSnapshot item : snapshot.getChildren()) {
                    ItemAdminCategory food = item.getValue(ItemAdminCategory.class);
                    if (food != null) {
                        food.setKey(item.getKey());
                        foodList.add(food);
                    }
                }
                foodAdapter.notifyDataSetChanged();

                if (foodList.isEmpty()) {
                    Toast.makeText(getContext(), "No foods found.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(getContext(), "Failed: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
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

            if (food.getImageBase64() != null && !food.getImageBase64().isEmpty()) {
                Bitmap bmp = base64ToBitmap(food.getImageBase64());
                holder.ivFoodImage.setImageBitmap(bmp);
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            holder.switchAvailable.setChecked(food.isAvailable());
            holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                food.setAvailable(isChecked);
                foodsRef.child(food.getKey()).child("available").setValue(isChecked);
            });

            holder.btnUpdate.setOnClickListener(v -> showAdminUpdateFoodDialog(food));

            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Food")
                        .setMessage("Are you sure you want to delete " + food.getName() + "?")
                        .setPositiveButton("Yes", (dialog, which) ->
                                foodsRef.child(food.getKey()).removeValue()
                                        .addOnSuccessListener(aVoid ->
                                                Toast.makeText(getContext(), "Deleted " + food.getName(), Toast.LENGTH_SHORT).show())
                                        .addOnFailureListener(e ->
                                                Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                        .setNegativeButton("No", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return foods.size();
        }

        class FoodViewHolder extends RecyclerView.ViewHolder {
            ImageView ivFoodImage;
            TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime;
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
                btnUpdate = itemView.findViewById(R.id.btnUpdate);
                btnDelete = itemView.findViewById(R.id.btnDelete);
                switchAvailable = itemView.findViewById(R.id.switchAvailable);
            }
        }
    }

    private void showAdminUpdateFoodDialog(ItemAdminCategory food) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Admin: Update Food");

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_admin_update_food, null);
        builder.setView(dialogView);

        ImageView ivFood = dialogView.findViewById(R.id.ivAdminFoodImage);
        EditText etName = dialogView.findViewById(R.id.etAdminFoodName);
        EditText etPrice = dialogView.findViewById(R.id.etAdminFoodPrice);
        EditText etPrepTime = dialogView.findViewById(R.id.etAdminFoodPrepTime);
        EditText etDesc = dialogView.findViewById(R.id.etAdminFoodDesc);
        Spinner spCategory = dialogView.findViewById(R.id.spAdminFoodCategory);

        etName.setText(food.getName());
        etPrice.setText(String.valueOf(food.getPrice()));
        etPrepTime.setText(food.getPrepTime());
        etDesc.setText(food.getDescription());

        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Main Dish", "Drinks", "Dessert", "Snacks"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);
        if (food.getCategory() != null) {
            int pos = adapter.getPosition(food.getCategory());
            if (pos >= 0) spCategory.setSelection(pos);
        }

        if (food.getImageBase64() != null && !food.getImageBase64().isEmpty()) {
            Bitmap bmp = base64ToBitmap(food.getImageBase64());
            if (bmp != null) ivFood.setImageBitmap(bmp);
        }

        builder.setPositiveButton("Update", (dialog, which) -> {
            new AlertDialog.Builder(getContext())
                    .setTitle("Confirm Admin Update")
                    .setMessage("Are you sure you want to update " + food.getName() + "?")
                    .setPositiveButton("Yes", (d, w) -> {
                        food.setName(etName.getText().toString().trim());
                        food.setPrice(Double.parseDouble(etPrice.getText().toString().trim()));
                        food.setPrepTime(etPrepTime.getText().toString().trim());
                        food.setDescription(etDesc.getText().toString().trim());
                        food.setCategory(spCategory.getSelectedItem().toString());

                        foodsRef.child(food.getKey()).setValue(food)
                                .addOnSuccessListener(aVoid -> Toast.makeText(getContext(), "Updated successfully", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(getContext(), "Update failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    })
                    .setNegativeButton("No", null)
                    .show();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (Exception e) {
            return null;
        }
    }

    public static class ItemAdminCategory {
        private String key, category, description, name, prepTime;
        private double price;
        private String imageBase64;
        private boolean available = true;

        public ItemAdminCategory() {}

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
    }
}
