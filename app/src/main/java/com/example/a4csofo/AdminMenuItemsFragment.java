package com.example.a4csofo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static android.app.Activity.RESULT_OK;

public class AdminMenuItemsFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 100;

    private TextInputEditText etName, etPrice, etPrepTime, etDescription, etDiscountPrice,
            etAddOns, etIngredients, etServingSize, etCalories;
    private Spinner spCategory;
    private SwitchMaterial switchInStock;
    private ExtendedFloatingActionButton fabAddFood;
    private FloatingActionButton fabPickImage;
    private ImageView ivFoodPreview;
    private LinearLayout placeholderLayout;

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
        fabAddFood = view.findViewById(R.id.fabAddFood);
        fabPickImage = view.findViewById(R.id.fabPickImage);
        ivFoodPreview = view.findViewById(R.id.ivFoodPreview);
        placeholderLayout = view.findViewById(R.id.placeholderLayout);

        foodsRef = FirebaseDatabase.getInstance().getReference("foods");

        // Category Spinner setup
        String[] categories = new String[]{
                "Main Dish",
                "Drinks",
                "Dessert",
                "Snacks",
        };

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                categories
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(adapter);

        // Set default selection (first item)
        spCategory.setSelection(0);

        // Pick image listener
        fabPickImage.setOnClickListener(v -> openImagePicker());
        placeholderLayout.setOnClickListener(v -> openImagePicker());

        // Add food listener
        fabAddFood.setOnClickListener(v -> addFoodItem());

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
                if (getActivity() == null) return;

                InputStream inputStream = getActivity().getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                if (bitmap == null) return;

                // Resize/Compress for Firebase
                Bitmap compressed = Bitmap.createScaledBitmap(bitmap, 800, 800, true);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                compressed.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                byte[] bytes = baos.toByteArray();
                base64Image = Base64.encodeToString(bytes, Base64.DEFAULT);

                ivFoodPreview.setImageBitmap(compressed);
                placeholderLayout.setVisibility(View.GONE);

                Toast.makeText(getContext(), "Image selected successfully", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(getContext(), "Failed to select image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addFoodItem() {
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String discountStr = etDiscountPrice.getText().toString().trim();
        String addOnsStr = etAddOns.getText().toString().trim();
        String ingredientsStr = etIngredients.getText().toString().trim();
        String servingSize = etServingSize.getText().toString().trim();
        String caloriesStr = etCalories.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();
        boolean inStock = switchInStock.isChecked();

        // Validate required fields
        if (name.isEmpty()) {
            showError(etName, "Food name is required");
            return;
        }

        if (priceStr.isEmpty()) {
            showError(etPrice, "Price is required");
            return;
        }

        if (prepTime.isEmpty()) {
            showError(etPrepTime, "Preparation time is required");
            return;
        }

        if (base64Image == null) {
            Toast.makeText(getContext(), "Please select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        double price, discountPrice = 0;
        int calories = 0;
        try {
            price = Double.parseDouble(priceStr);
            if (price <= 0) {
                showError(etPrice, "Price must be greater than 0");
                return;
            }

            if (!discountStr.isEmpty()) {
                discountPrice = Double.parseDouble(discountStr);
                if (discountPrice >= price) {
                    showError(etDiscountPrice, "Discount price must be less than regular price");
                    return;
                }
            }

            if (!caloriesStr.isEmpty()) {
                calories = Integer.parseInt(caloriesStr);
                if (calories < 0) {
                    showError(etCalories, "Calories cannot be negative");
                    return;
                }
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> addOns = addOnsStr.isEmpty() ? null : Arrays.asList(addOnsStr.split("\\s*,\\s*"));
        List<String> ingredients = ingredientsStr.isEmpty() ? null : Arrays.asList(ingredientsStr.split("\\s*,\\s*"));

        String key = foodsRef.push().getKey();
        if (key == null) {
            Toast.makeText(getContext(), "Database error: could not generate key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        fabAddFood.setEnabled(false);
        fabAddFood.setText("Adding...");

        // Create FoodItem without getBitmap() method
        FoodItem food = new FoodItem();
        food.id = key;
        food.name = name;
        food.price = price;
        food.discountPrice = discountPrice;
        food.prepTime = prepTime;
        food.description = description;
        food.category = category;
        food.base64Image = base64Image;
        food.addOns = addOns;
        food.ingredients = ingredients;
        food.servingSize = servingSize.isEmpty() ? null : servingSize;
        food.calories = calories;
        food.inStock = inStock;

        foodsRef.child(key).setValue(food)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "Food added successfully!", Toast.LENGTH_SHORT).show();
                    clearFields();
                    fabAddFood.setEnabled(true);
                    fabAddFood.setText("Add Food Item");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to add food: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    fabAddFood.setEnabled(true);
                    fabAddFood.setText("Add Food Item");
                });
    }

    private void showError(TextInputEditText field, String message) {
        if (field.getParent().getParent() instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) field.getParent().getParent();
            layout.setError(message);
            field.requestFocus();
        }
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
        spCategory.setSelection(0);
        switchInStock.setChecked(true);
        base64Image = null;
        ivFoodPreview.setImageResource(android.R.color.transparent);
        placeholderLayout.setVisibility(View.VISIBLE);

        // Clear errors
        clearError(etName);
        clearError(etPrice);
        clearError(etPrepTime);
    }

    private void clearError(TextInputEditText field) {
        if (field.getParent().getParent() instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) field.getParent().getParent();
            layout.setError(null);
            layout.setErrorEnabled(false);
        }
    }

    // Simplified FoodItem Model without getBitmap() method
    public static class FoodItem {
        public String id;
        public String name;
        public double price;
        public double discountPrice;
        public String prepTime;
        public String description;
        public String category;
        public String base64Image;
        public List<String> addOns;       // optional
        public List<String> ingredients;  // optional
        public String servingSize;        // optional
        public int calories;              // optional, 0 = not set
        public boolean inStock = true;

        // Required empty constructor for Firebase
        public FoodItem() {}

        // Optional: You can keep this constructor or remove it
        public FoodItem(String id, String name, double price, double discountPrice, String prepTime,
                        String description, String category, String base64Image,
                        List<String> addOns, List<String> ingredients, String servingSize,
                        int calories, boolean inStock) {
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
    }
}