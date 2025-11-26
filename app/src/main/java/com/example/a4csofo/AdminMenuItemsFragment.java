package com.example.a4csofo;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class AdminMenuItemsFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 100;

    private EditText etName, etPrice, etPrepTime, etDescription, etDiscountPrice,
            etAddOns, etIngredients, etServingSize, etCalories;
    private Spinner spCategory;
    private Switch switchInStock;
    private Button btnAddFood, btnPickImage;
    private ImageView ivFoodPreview;

    private DatabaseReference foodsRef;
    private String base64Image = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_admin_menu, container, false);

        // Initialize fields
        etName = view.findViewById(R.id.etName);
        etPrice = view.findViewById(R.id.etPrice);
        etPrepTime = view.findViewById(R.id.etPrepTime);
        etDescription = view.findViewById(R.id.etDescription);
        etDiscountPrice = view.findViewById(R.id.etDiscountPrice);
        etAddOns = view.findViewById(R.id.etAddOns);
        etIngredients = view.findViewById(R.id.etIngredients);
        etServingSize = view.findViewById(R.id.etServingSize);
        etCalories = view.findViewById(R.id.etCalories);
        switchInStock = view.findViewById(R.id.switchInStock);
        spCategory = view.findViewById(R.id.spCategory);
        btnAddFood = view.findViewById(R.id.btnAddFood);
        btnPickImage = view.findViewById(R.id.btnPickImage);
        ivFoodPreview = view.findViewById(R.id.ivFoodPreview);

        foodsRef = FirebaseDatabase.getInstance().getReference("foods");

        // Spinner setup
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getContext(),
                android.R.layout.simple_spinner_item,
                new String[]{"Main Dish", "Drinks", "Dessert", "Snacks"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        // Pick image listener
        btnPickImage.setOnClickListener(v -> openImagePicker());

        // Add food listener
        btnAddFood.setOnClickListener(v -> addFoodItem());

        return view;
    }

    private void openImagePicker() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE_REQUEST);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            try {
                Uri imageUri = data.getData();
                InputStream inputStream = getActivity().getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ivFoodPreview.setImageBitmap(bitmap);

                // Convert to Base64
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] bytes = baos.toByteArray();
                base64Image = Base64.encodeToString(bytes, Base64.DEFAULT);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to select image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addFoodItem() {
        // Get field values
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String discountStr = etDiscountPrice.getText().toString().trim();
        String addOnsStr = etAddOns.getText().toString().trim();
        String ingredientsStr = etIngredients.getText().toString().trim();
        String servingSize = etServingSize.getText().toString().trim();
        String caloriesStr = etCalories.getText().toString().trim();
        String category = spCategory.getSelectedItem() != null ? spCategory.getSelectedItem().toString() : "";
        boolean inStock = switchInStock.isChecked();

        // Validate required fields
        if (name.isEmpty() || priceStr.isEmpty() || prepTime.isEmpty()) {
            Toast.makeText(getContext(), "Fill required fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price, discountPrice = 0;
        int calories = 0;
        try {
            price = Double.parseDouble(priceStr);
            if (!discountStr.isEmpty()) discountPrice = Double.parseDouble(discountStr);
            if (!caloriesStr.isEmpty()) calories = Integer.parseInt(caloriesStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid number format", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> addOns = addOnsStr.isEmpty() ? null : Arrays.asList(addOnsStr.split(","));
        List<String> ingredients = ingredientsStr.isEmpty() ? null : Arrays.asList(ingredientsStr.split(","));

        String key = foodsRef.push().getKey();
        if (key == null) {
            Toast.makeText(getContext(), "Database error: could not generate key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Create FoodItem object
        FoodItem food = new FoodItem(key, name, price, discountPrice, prepTime,
                description, category, base64Image, addOns, ingredients, servingSize, calories, inStock);

        // Save to Firebase
        foodsRef.child(key).setValue(food)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Food added successfully!", Toast.LENGTH_SHORT).show();
                    clearFields();
                })
                .addOnFailureListener(e -> Toast.makeText(getContext(), "Database error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void clearFields() {
        etName.setText("");
        etPrice.setText("");
        etPrepTime.setText("");
        etDescription.setText("");
        etDiscountPrice.setText("");
        etAddOns.setText("");
        etIngredients.setText("");
        etServingSize.setText("");
        etCalories.setText("");
        switchInStock.setChecked(true);
        base64Image = null;
        ivFoodPreview.setImageResource(android.R.color.transparent);
    }

    // Full FoodItem model
    public static class FoodItem {
        public String id;
        public String name;
        public double price;
        public double discountPrice;
        public String prepTime;
        public String description;
        public String category;
        public String base64Image;
        public List<String> addOns;
        public List<String> ingredients;
        public String servingSize;
        public int calories;
        public boolean inStock = true;

        public FoodItem() {}

        public FoodItem(String id, String name, double price, double discountPrice, String prepTime,
                        String description, String category, String base64Image,
                        List<String> addOns, List<String> ingredients, String servingSize, int calories,
                        boolean inStock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.discountPrice = discountPrice;
            this.prepTime = prepTime;
            this.description = description;
            this.category = category;
            this.base64Image = base64Image;
            this.addOns = addOns;
            this.ingredients = ingredients;
            this.servingSize = servingSize;
            this.calories = calories;
            this.inStock = inStock;
        }

        public Bitmap getBitmap() {
            if (base64Image == null || base64Image.isEmpty()) return null;
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        }
    }
}
