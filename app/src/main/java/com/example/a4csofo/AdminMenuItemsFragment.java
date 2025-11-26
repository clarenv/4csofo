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
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import static android.app.Activity.RESULT_OK;

public class AdminMenuItemsFragment extends Fragment {

    private static final int PICK_IMAGE_REQUEST = 100;

    private EditText etName, etPrice, etPrepTime, etDescription;
    private Spinner spCategory;
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

        etName = view.findViewById(R.id.etName);
        etPrice = view.findViewById(R.id.etPrice);
        etPrepTime = view.findViewById(R.id.etPrepTime);
        etDescription = view.findViewById(R.id.etDescription);
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

        // Image picker
        btnPickImage.setOnClickListener(v -> openImagePicker());

        // Add food
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
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String category = spCategory.getSelectedItem() != null ? spCategory.getSelectedItem().toString() : "";

        if (name.isEmpty() || priceStr.isEmpty() || prepTime.isEmpty()) {
            Toast.makeText(getContext(), "Fill all fields", Toast.LENGTH_SHORT).show();
            return;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Invalid price", Toast.LENGTH_SHORT).show();
            return;
        }

        String key = foodsRef.push().getKey();
        if (key == null) {
            Toast.makeText(getContext(), "Database error: could not generate key", Toast.LENGTH_SHORT).show();
            return;
        }

        FoodItem food = new FoodItem(name, price, prepTime, description, category, base64Image);

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
        base64Image = null;
        ivFoodPreview.setImageResource(android.R.color.transparent);
    }

    public static class FoodItem {
        public String name;
        public double price;
        public String prepTime;
        public String description;
        public String category;
        public String base64Image;

        public FoodItem() {}
        public FoodItem(String name, double price, String prepTime, String description, String category, String base64Image) {
            this.name = name;
            this.price = price;
            this.prepTime = prepTime;
            this.description = description;
            this.category = category;
            this.base64Image = base64Image;
        }
    }
}
