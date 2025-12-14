package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

public class AdminCategoriesFragment extends Fragment {

    private RecyclerView recyclerViewFoods;
    private DatabaseReference foodsRef;
    private List<ItemAdminCategory> foodList = new ArrayList<>();
    private List<ItemAdminCategory> filteredList = new ArrayList<>();
    private FoodAdapter foodAdapter;
    private Spinner spinnerCategories;
    private TextView tvEmptyState;

    // SMART FEATURES
    private static final int NAME_LIMIT = 50;
    private static final int DESC_LIMIT = 200;
    private static final int PREP_TIME_MIN = 1;
    private static final int PREP_TIME_MAX = 999;
    private static final double PRICE_MIN = 1;
    private static final double PRICE_MAX = 10000;

    // ActivityResultLaunchers for image picking
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private Bitmap selectedImageBitmap;
    private String selectedImageBase64;

    // Store current food being updated
    private ItemAdminCategory currentUpdatingFood;
    private int currentUpdatePosition = -1;

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

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize ActivityResultLaunchers
        initializeImageLaunchers();
    }

    private void initializeImageLaunchers() {
        // For gallery image picker
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            selectedImageBitmap = MediaStore.Images.Media.getBitmap(requireActivity().getContentResolver(), imageUri);
                            selectedImageBase64 = bitmapToBase64(selectedImageBitmap);
                            showSuccessToast("Image selected");
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(getContext(), "Failed to load image", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );

        // For camera photo
        takePhotoLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Bundle extras = result.getData().getExtras();
                        if (extras != null) {
                            selectedImageBitmap = (Bitmap) extras.get("data");
                            selectedImageBase64 = bitmapToBase64(selectedImageBitmap);
                            showSuccessToast("Photo taken");
                        }
                    }
                }
        );
    }

    private void loadFoods() {
        foodsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                foodList.clear();
                filteredList.clear();

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

            // Load image
            if (food.getBase64Image() != null && !food.getBase64Image().isEmpty()) {
                Bitmap bmp = base64ToBitmap(food.getBase64Image());
                if (bmp != null) {
                    holder.ivFoodImage.setImageBitmap(bmp);
                } else {
                    holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
                }
            } else {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }

            holder.switchAvailable.setChecked(food.isAvailable());
            holder.switchAvailable.setOnCheckedChangeListener(null); // Clear previous listener
            holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                food.setAvailable(isChecked);
                foodsRef.child(food.getKey()).child("available").setValue(isChecked)
                        .addOnSuccessListener(aVoid -> {
                            showSuccessToast("Availability updated");
                        })
                        .addOnFailureListener(e -> {
                            Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            holder.switchAvailable.setChecked(!isChecked); // Revert if failed
                        });
            });

            // Update button click
            holder.btnUpdate.setOnClickListener(v -> {
                currentUpdatingFood = food;
                currentUpdatePosition = holder.getAdapterPosition();
                showSmartUpdateFoodDialog(food);
            });

            // Delete button click
            holder.btnDelete.setOnClickListener(v -> {
                new AlertDialog.Builder(getContext())
                        .setTitle("Delete Food")
                        .setMessage("Are you sure you want to delete " + food.getName() + "?")
                        .setPositiveButton("Yes", (dialog, which) ->
                                foodsRef.child(food.getKey()).removeValue()
                                        .addOnSuccessListener(aVoid -> {
                                            showSuccessToast(food.getName() + " deleted");
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

    private void showSmartUpdateFoodDialog(ItemAdminCategory food) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Update " + food.getName());

        // Inflate the update dialog layout
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_admin_update_food, null);
        builder.setView(dialogView);

        // Initialize form fields
        ImageView ivCurrentFoodImage = dialogView.findViewById(R.id.ivCurrentFoodImage);
        FloatingActionButton fabPickImage = dialogView.findViewById(R.id.fabPickImage);

        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etPrepTime = dialogView.findViewById(R.id.etPrepTime);
        Spinner spCategory = dialogView.findViewById(R.id.spCategory);

        SwitchMaterial switchAvailable = dialogView.findViewById(R.id.switchAvailable);

        // Reset selected image
        selectedImageBase64 = null;
        selectedImageBitmap = null;

        // Load current image
        if (food.getBase64Image() != null && !food.getBase64Image().isEmpty()) {
            Bitmap bmp = base64ToBitmap(food.getBase64Image());
            if (bmp != null) {
                ivCurrentFoodImage.setImageBitmap(bmp);
            } else {
                ivCurrentFoodImage.setImageResource(R.drawable.ic_placeholder);
            }
        } else {
            ivCurrentFoodImage.setImageResource(R.drawable.ic_placeholder);
        }

        // Set current values
        etName.setText(food.getName() != null ? food.getName() : "");
        etPrice.setText(String.valueOf(food.getPrice()));
        etDescription.setText(food.getDescription() != null ? food.getDescription() : "");
        etPrepTime.setText(food.getPrepTime() != null ? food.getPrepTime() : "");

        // Set up category spinner
        List<String> categories = Arrays.asList("Main Dish", "Drinks", "Dessert", "Snacks", "Appetizer");
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(
                getContext(),
                android.R.layout.simple_spinner_item,
                categories
        );
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spCategory.setAdapter(categoryAdapter);

        // Set selected category
        if (food.getCategory() != null) {
            int pos = categories.indexOf(food.getCategory());
            if (pos >= 0) spCategory.setSelection(pos);
        }

        switchAvailable.setChecked(food.isAvailable());

        // Setup image selection
        fabPickImage.setOnClickListener(v -> {
            // Create options dialog for image source
            String[] options = {"Take Photo", "Choose from Gallery", "Cancel"};
            AlertDialog.Builder imageDialog = new AlertDialog.Builder(getContext());
            imageDialog.setTitle("Change Food Image");
            imageDialog.setItems(options, (dialog, which) -> {
                if (which == 0) {
                    // Take Photo
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    if (intent.resolveActivity(getActivity().getPackageManager()) != null) {
                        takePhotoLauncher.launch(intent);
                    } else {
                        Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
                    }
                } else if (which == 1) {
                    // Choose from Gallery
                    Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                    pickImageLauncher.launch(intent);
                }
                // which == 2 is Cancel, do nothing
            });
            imageDialog.show();
        });

        // Setup smart input limitations
        setupSmartInputLimitationsForDialog(etName, etPrice, etPrepTime, etDescription);

        // Create the dialog
        AlertDialog dialog = builder.create();

        // Set up the Update button with custom click listener
        dialog.setButton(AlertDialog.BUTTON_POSITIVE, "Update", (dialogInterface, which) -> {
            // Do nothing here, we'll handle it below
        });

        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel", (dialogInterface, which) -> {
            dialog.dismiss();
        });

        // Show the dialog
        dialog.show();

        // Override the positive button click
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (validateAndUpdateFood(food, etName, etPrice, etDescription,
                    etPrepTime, spCategory, switchAvailable)) {
                dialog.dismiss();
            }
        });
    }

    private void setupSmartInputLimitationsForDialog(EditText etName, EditText etPrice,
                                                     EditText etPrepTime, EditText etDescription) {
        // Food Name limit
        etName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > NAME_LIMIT) {
                    etName.setError("Max " + NAME_LIMIT + " characters");
                    vibrate();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Description limit
        etDescription.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > DESC_LIMIT) {
                    etDescription.setError("Max " + DESC_LIMIT + " characters");
                    vibrate();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Prep Time (1-999)
        etPrepTime.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etPrepTime.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty()) {
                    try {
                        int value = Integer.parseInt(s.toString());
                        if (value < PREP_TIME_MIN || value > PREP_TIME_MAX) {
                            etPrepTime.setError("Must be " + PREP_TIME_MIN + "-" + PREP_TIME_MAX + " mins");
                            vibrate();
                        }
                    } catch (NumberFormatException e) {
                        etPrepTime.setError("Invalid number");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Price (1-10000, 2 decimals)
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etPrice.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!s.toString().isEmpty() && !s.toString().equals(".")) {
                    try {
                        double value = Double.parseDouble(s.toString());
                        if (value < PRICE_MIN || value > PRICE_MAX) {
                            etPrice.setError("Must be ₱" + PRICE_MIN + "-₱" + PRICE_MAX);
                            vibrate();
                        }

                        // Check decimal places
                        String[] parts = s.toString().split("\\.");
                        if (parts.length > 1 && parts[1].length() > 2) {
                            etPrice.setError("Max 2 decimal places");
                            vibrate();
                        }
                    } catch (NumberFormatException e) {
                        etPrice.setError("Invalid price");
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private boolean validateAndUpdateFood(ItemAdminCategory food,
                                          EditText etName, EditText etPrice, EditText etDescription,
                                          EditText etPrepTime, Spinner spCategory,
                                          SwitchMaterial switchAvailable) {

        // Get values
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();
        boolean available = switchAvailable.isChecked();

        // Validate
        boolean isValid = true;

        if (name.isEmpty()) {
            etName.setError("Food name is required");
            etName.requestFocus();
            vibrate();
            isValid = false;
        } else if (name.length() > NAME_LIMIT) {
            etName.setError("Max " + NAME_LIMIT + " characters");
            etName.requestFocus();
            vibrate();
            isValid = false;
        }

        if (priceStr.isEmpty()) {
            etPrice.setError("Price is required");
            if (isValid) {
                etPrice.requestFocus();
                vibrate();
            }
            isValid = false;
        } else {
            try {
                double price = Double.parseDouble(priceStr);
                if (price < PRICE_MIN || price > PRICE_MAX) {
                    etPrice.setError("Must be ₱" + PRICE_MIN + "-₱" + PRICE_MAX);
                    if (isValid) {
                        etPrice.requestFocus();
                        vibrate();
                    }
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                etPrice.setError("Invalid price");
                if (isValid) {
                    etPrice.requestFocus();
                    vibrate();
                }
                isValid = false;
            }
        }

        if (prepTime.isEmpty()) {
            etPrepTime.setError("Prep time is required");
            if (isValid) {
                etPrepTime.requestFocus();
                vibrate();
            }
            isValid = false;
        } else {
            try {
                int prepTimeVal = Integer.parseInt(prepTime);
                if (prepTimeVal < PREP_TIME_MIN || prepTimeVal > PREP_TIME_MAX) {
                    etPrepTime.setError("Must be " + PREP_TIME_MIN + "-" + PREP_TIME_MAX + " mins");
                    if (isValid) {
                        etPrepTime.requestFocus();
                        vibrate();
                    }
                    isValid = false;
                }
            } catch (NumberFormatException e) {
                etPrepTime.setError("Invalid prep time");
                if (isValid) {
                    etPrepTime.requestFocus();
                    vibrate();
                }
                isValid = false;
            }
        }

        if (description.length() > DESC_LIMIT) {
            etDescription.setError("Max " + DESC_LIMIT + " characters");
            if (isValid) {
                etDescription.requestFocus();
                vibrate();
            }
            isValid = false;
        }

        if (!isValid) {
            return false;
        }

        // Prepare update data
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("name", name);
        updateData.put("price", Double.parseDouble(priceStr));
        updateData.put("description", description);
        updateData.put("prepTime", prepTime);
        updateData.put("category", category);
        updateData.put("available", available);

        // Update image if new one was selected
        if (selectedImageBase64 != null) {
            updateData.put("base64Image", selectedImageBase64);
        }

        // Update in Firebase
        foodsRef.child(food.getKey()).updateChildren(updateData)
                .addOnSuccessListener(aVoid -> {
                    showSuccessToast(food.getName() + " updated");

                    // Update local data
                    food.setName(name);
                    food.setPrice(Double.parseDouble(priceStr));
                    food.setDescription(description);
                    food.setPrepTime(prepTime);
                    food.setCategory(category);
                    food.setAvailable(available);
                    if (selectedImageBase64 != null) {
                        food.setBase64Image(selectedImageBase64);
                    }

                    // Update RecyclerView
                    if (currentUpdatePosition >= 0 && currentUpdatePosition < filteredList.size()) {
                        foodAdapter.notifyItemChanged(currentUpdatePosition);
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        return true;
    }

    private void showSuccessToast(String message) {
        Toast.makeText(getContext(), "✅ " + message, Toast.LENGTH_SHORT).show();
    }

    private void vibrate() {
        try {
            if (getActivity() != null) {
                Vibrator vibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
                    } else {
                        vibrator.vibrate(50);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    public static Bitmap base64ToBitmap(String base64Str) {
        if (base64Str == null || base64Str.isEmpty()) {
            return null;
        }

        try {
            // Remove data URL prefix if present
            if (base64Str.startsWith("data:image")) {
                base64Str = base64Str.substring(base64Str.indexOf(",") + 1);
            }

            byte[] decoded = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
        } catch (Exception e) {
            Log.e("AdminCategories", "Error converting base64 to bitmap: " + e.getMessage());
            return null;
        }
    }

    private String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return null;

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    public static class ItemAdminCategory {
        private String key, category, description, name, prepTime;
        private double price;
        private String base64Image;
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

        public String getBase64Image() { return base64Image; }
        public void setBase64Image(String base64Image) { this.base64Image = base64Image; }

        public boolean isAvailable() { return available; }
        public void setAvailable(boolean available) { this.available = available; }
    }
}