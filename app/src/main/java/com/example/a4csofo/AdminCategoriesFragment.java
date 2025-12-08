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
    private static final int ADDONS_LIMIT = 100;
    private static final int PREP_TIME_MIN = 1;
    private static final int PREP_TIME_MAX = 999;
    private static final double PRICE_MIN = 1;
    private static final double PRICE_MAX = 10000;

    // ActivityResultLaunchers for image picking
    private ActivityResultLauncher<Intent> pickImageLauncher;
    private ActivityResultLauncher<Intent> takePhotoLauncher;
    private Bitmap selectedImageBitmap;
    private String selectedImageBase64;

    // References to current dialog views
    private ImageView currentDialogImageView;
    private LinearLayout currentDialogPlaceholderLayout;

    // Smart add-ons suggestions
    private static final Map<String, List<String>> ADDONS_SUGGESTIONS = new HashMap<>();

    static {
        ADDONS_SUGGESTIONS.put("Main Dish", Arrays.asList(
                "Extra Rice", "Extra Sauce", "Add Egg", "Add Cheese",
                "Extra Meat", "Add Vegetables", "Spicy Level Up"
        ));
        ADDONS_SUGGESTIONS.put("Drinks", Arrays.asList(
                "Add Pearls", "Add Nata de Coco", "Extra Sugar",
                "Less Ice", "No Ice", "Add Whipped Cream"
        ));
        ADDONS_SUGGESTIONS.put("Dessert", Arrays.asList(
                "Extra Ice Cream", "Add Leche Flan", "Extra Condensed Milk",
                "Add Fruits", "Extra Ube", "Add Cheese"
        ));
        ADDONS_SUGGESTIONS.put("Snacks", Arrays.asList(
                "Extra Dip", "Add Cheese Dip", "Add Vinegar Dip",
                "Extra Sauce", "Add Chili Sauce", "Side Ketchup"
        ));
        ADDONS_SUGGESTIONS.put("Appetizer", Arrays.asList(
                "Extra Dip", "Add Sauce", "Extra Cheese",
                "Add Sour Cream", "Extra Guacamole"
        ));
    }

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

                            // Update the image in dialog immediately
                            if (currentDialogImageView != null && currentDialogPlaceholderLayout != null) {
                                updateImageInDialog(currentDialogImageView, currentDialogPlaceholderLayout, selectedImageBase64);
                            }
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

                            // Update the image in dialog immediately
                            if (currentDialogImageView != null && currentDialogPlaceholderLayout != null) {
                                updateImageInDialog(currentDialogImageView, currentDialogPlaceholderLayout, selectedImageBase64);
                            }
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

            // Build extras text
            StringBuilder extrasBuilder = new StringBuilder();
            if (food.getAddOns() != null && !food.getAddOns().isEmpty()) {
                extrasBuilder.append("Add-ons: ").append(String.join(", ", food.getAddOns()));
            } else {
                extrasBuilder.append("Add-ons: None");
            }
            holder.tvExtras.setText(extrasBuilder.toString());

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
            holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {
                food.setAvailable(isChecked);
                foodsRef.child(food.getKey()).child("available").setValue(isChecked)
                        .addOnSuccessListener(aVoid -> {
                            showSuccessToast("Availability updated");
                        });
            });

            // Update button click
            holder.btnUpdate.setOnClickListener(v -> showSmartUpdateFoodDialog(food, position));

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

    private void showSmartUpdateFoodDialog(ItemAdminCategory food, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Update " + food.getName());

        // Inflate the update dialog layout
        View dialogView = LayoutInflater.from(getContext()).inflate(R.layout.dialog_admin_update_food, null);
        builder.setView(dialogView);

        // Initialize form fields
        ImageView ivCurrentFoodImage = dialogView.findViewById(R.id.ivCurrentFoodImage);
        LinearLayout placeholderLayout = dialogView.findViewById(R.id.placeholderLayout);
        FloatingActionButton fabPickImage = dialogView.findViewById(R.id.fabPickImage);

        EditText etName = dialogView.findViewById(R.id.etName);
        EditText etPrice = dialogView.findViewById(R.id.etPrice);
        EditText etDescription = dialogView.findViewById(R.id.etDescription);
        EditText etPrepTime = dialogView.findViewById(R.id.etPrepTime);
        Spinner spCategory = dialogView.findViewById(R.id.spCategory);
        EditText etAddOns = dialogView.findViewById(R.id.etAddOns);

        SwitchMaterial switchAvailable = dialogView.findViewById(R.id.switchAvailable);
        Button btnSmartAddons = dialogView.findViewById(R.id.btnSmartAddons);

        // Store original image
        selectedImageBase64 = food.getBase64Image();
        selectedImageBitmap = null;

        // Load current image and show/hide placeholder
        updateImageInDialog(ivCurrentFoodImage, placeholderLayout, food.getBase64Image());

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

        // Set add-ons field
        if (food.getAddOns() != null && !food.getAddOns().isEmpty()) {
            etAddOns.setText(String.join(", ", food.getAddOns()));
        }

        switchAvailable.setChecked(food.isAvailable());

        // Setup image selection - Store references to current dialog views
        fabPickImage.setOnClickListener(v -> {
            // Store references to current dialog views
            currentDialogImageView = ivCurrentFoodImage;
            currentDialogPlaceholderLayout = placeholderLayout;

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

        // Setup smart add-ons button
        btnSmartAddons.setOnClickListener(v -> {
            String category = spCategory.getSelectedItem().toString();
            showSmartAddonsDialog(category, etAddOns);
        });

        // Also allow click on the field itself
        etAddOns.setOnClickListener(v -> {
            String category = spCategory.getSelectedItem().toString();
            showSmartAddonsDialog(category, etAddOns);
        });

        // Setup smart input limitations
        setupSmartInputLimitationsForDialog(etName, etPrice, etPrepTime, etDescription, etAddOns);

        // Set up the Update button
        builder.setPositiveButton("Update", (dialog, which) -> {
            if (validateAndUpdateFood(food, position, etName, etPrice, etDescription,
                    etPrepTime, spCategory, etAddOns, switchAvailable)) {
                dialog.dismiss();
            }
        });

        // Set up the Cancel button
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());

        // Show the dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Helper method to update image in dialog
    private void updateImageInDialog(ImageView imageView, LinearLayout placeholderLayout, String base64Image) {
        if (base64Image != null && !base64Image.isEmpty()) {
            Bitmap bmp = base64ToBitmap(base64Image);
            if (bmp != null) {
                imageView.setImageBitmap(bmp);
                placeholderLayout.setVisibility(View.GONE);
            } else {
                imageView.setImageResource(R.drawable.ic_placeholder);
                placeholderLayout.setVisibility(View.VISIBLE);
            }
        } else {
            imageView.setImageResource(R.drawable.ic_placeholder);
            placeholderLayout.setVisibility(View.VISIBLE);
        }
    }

    private void showSmartAddonsDialog(String category, EditText etAddOns) {
        // Get suggestions for the selected category
        List<String> suggestions = ADDONS_SUGGESTIONS.get(category);

        if (suggestions == null || suggestions.isEmpty()) {
            Toast.makeText(getContext(), "No add-ons suggestions for " + category, Toast.LENGTH_SHORT).show();
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Smart Add-ons for " + category);

        // Convert to array for checkbox
        final String[] items = suggestions.toArray(new String[0]);
        final boolean[] checkedItems = new boolean[items.length];

        // Get current add-ons from EditText
        final String currentText = etAddOns.getText().toString().trim();
        final List<String> currentAddonsList = new ArrayList<>();
        if (!currentText.isEmpty()) {
            currentAddonsList.addAll(Arrays.asList(currentText.split("\\s*,\\s*")));
        }

        // Pre-check items that are already selected
        for (int i = 0; i < items.length; i++) {
            checkedItems[i] = currentAddonsList.contains(items[i]);
        }

        builder.setMultiChoiceItems(items, checkedItems, (dialog, which, isChecked) -> {
            checkedItems[which] = isChecked;
        });

        builder.setPositiveButton("Apply", (dialog, which) -> {
            // Build selected add-ons list
            List<String> selectedAddons = new ArrayList<>();
            for (int i = 0; i < items.length; i++) {
                if (checkedItems[i]) {
                    selectedAddons.add(items[i]);
                }
            }

            // Keep existing custom add-ons
            for (String existing : currentAddonsList) {
                if (!suggestions.contains(existing) && !selectedAddons.contains(existing)) {
                    selectedAddons.add(existing);
                }
            }

            // Update EditText
            String addonsText = String.join(", ", selectedAddons);
            if (addonsText.length() > ADDONS_LIMIT) {
                addonsText = addonsText.substring(0, ADDONS_LIMIT);
                showWarningToast("Add-ons trimmed to " + ADDONS_LIMIT + " characters");
            }

            etAddOns.setText(addonsText);
            showSuccessToast(selectedAddons.size() + " add-ons selected");
        });

        builder.setNegativeButton("Cancel", null);
        builder.setNeutralButton("Clear All", (dialog, which) -> {
            etAddOns.setText("");
            showSuccessToast("All add-ons cleared");
        });

        builder.show();
    }

    private void setupSmartInputLimitationsForDialog(EditText etName, EditText etPrice,
                                                     EditText etPrepTime, EditText etDescription,
                                                     EditText etAddOns) {
        // Food Name limit
        setupTextLimiter(etName, NAME_LIMIT, "Food Name");

        // Description limit
        setupTextLimiter(etDescription, DESC_LIMIT, "Description");

        // Add-ons limit
        setupTextLimiter(etAddOns, ADDONS_LIMIT, "Add-ons");

        // Prep Time (1-999)
        etPrepTime.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        setupNumberLimiter(etPrepTime, PREP_TIME_MIN, PREP_TIME_MAX, "Prep Time");

        // Price (1-10000, 2 decimals)
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        setupPriceLimiter(etPrice, PRICE_MIN, PRICE_MAX);
    }

    private void setupTextLimiter(EditText editText, int maxLength, String fieldName) {
        editText.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s != null) {
                    lastValidText = s.toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentText = s != null ? s.toString() : "";

                if (currentText.length() > maxLength) {
                    editText.removeTextChangedListener(this);
                    editText.setText(lastValidText);
                    editText.setSelection(lastValidText.length());
                    editText.addTextChangedListener(this);
                    showCharacterLimitError(editText, fieldName, maxLength);
                } else {
                    lastValidText = currentText;
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupNumberLimiter(EditText editText, int min, int max, String fieldName) {
        editText.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s != null) {
                    lastValidText = s.toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentText = s != null ? s.toString() : "";

                if (currentText.isEmpty()) {
                    lastValidText = "";
                    return;
                }

                try {
                    int value = Integer.parseInt(currentText);

                    if (value > max) {
                        editText.removeTextChangedListener(this);
                        editText.setText(String.valueOf(max));
                        editText.setSelection(String.valueOf(max).length());
                        editText.addTextChangedListener(this);
                        showError(editText, fieldName + " maximum is " + max);
                        vibrate();
                    } else if (value < min && !currentText.isEmpty()) {
                        editText.removeTextChangedListener(this);
                        editText.setText(String.valueOf(min));
                        editText.setSelection(String.valueOf(min).length());
                        editText.addTextChangedListener(this);
                        showError(editText, fieldName + " minimum is " + min);
                        vibrate();
                    } else {
                        lastValidText = currentText;
                    }
                } catch (NumberFormatException e) {
                    editText.removeTextChangedListener(this);
                    editText.setText(lastValidText);
                    editText.setSelection(lastValidText.length());
                    editText.addTextChangedListener(this);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void setupPriceLimiter(EditText editText, double min, double max) {
        editText.addTextChangedListener(new TextWatcher() {
            private String lastValidText = "";

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                if (s != null) {
                    lastValidText = s.toString();
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String currentText = s != null ? s.toString() : "";

                // Prevent multiple decimals
                int decimalCount = 0;
                for (char c : currentText.toCharArray()) {
                    if (c == '.') decimalCount++;
                }
                if (decimalCount > 1) {
                    editText.removeTextChangedListener(this);
                    editText.setText(lastValidText);
                    editText.setSelection(lastValidText.length());
                    editText.addTextChangedListener(this);
                    return;
                }

                // Limit to 2 decimal places
                if (currentText.contains(".")) {
                    String[] parts = currentText.split("\\.");
                    if (parts.length > 1 && parts[1].length() > 2) {
                        editText.removeTextChangedListener(this);
                        String formatted = parts[0] + "." + parts[1].substring(0, 2);
                        editText.setText(formatted);
                        editText.setSelection(formatted.length());
                        editText.addTextChangedListener(this);
                        return;
                    }
                }

                // Validate range
                if (!currentText.isEmpty() && !currentText.equals(".")) {
                    try {
                        double value = Double.parseDouble(currentText);
                        if (value > max) {
                            editText.removeTextChangedListener(this);
                            editText.setText(String.valueOf(max));
                            editText.setSelection(String.valueOf(max).length());
                            editText.addTextChangedListener(this);
                            showError(editText, "Price maximum is ₱" + max);
                            vibrate();
                        } else if (value < min) {
                            editText.removeTextChangedListener(this);
                            editText.setText(String.valueOf(min));
                            editText.setSelection(String.valueOf(min).length());
                            editText.addTextChangedListener(this);
                            showError(editText, "Price minimum is ₱" + min);
                            vibrate();
                        } else {
                            lastValidText = currentText;
                        }
                    } catch (NumberFormatException e) {
                        editText.removeTextChangedListener(this);
                        editText.setText(lastValidText);
                        editText.setSelection(lastValidText.length());
                        editText.addTextChangedListener(this);
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private boolean validateAndUpdateFood(ItemAdminCategory food, int position,
                                          EditText etName, EditText etPrice, EditText etDescription,
                                          EditText etPrepTime, Spinner spCategory, EditText etAddOns,
                                          SwitchMaterial switchAvailable) {

        // Get values
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String prepTime = etPrepTime.getText().toString().trim();
        String category = spCategory.getSelectedItem().toString();
        String addOnsStr = etAddOns.getText().toString().trim();
        boolean available = switchAvailable.isChecked();

        // Validate
        if (name.isEmpty()) {
            showError(etName, "Food name is required");
            return false;
        }

        if (priceStr.isEmpty()) {
            showError(etPrice, "Price is required");
            return false;
        }

        if (prepTime.isEmpty()) {
            showError(etPrepTime, "Prep time is required");
            return false;
        }

        // Validate limits
        if (name.length() > NAME_LIMIT) {
            showError(etName, "Max " + NAME_LIMIT + " characters");
            return false;
        }

        if (description.length() > DESC_LIMIT) {
            showError(etDescription, "Max " + DESC_LIMIT + " characters");
            return false;
        }

        if (addOnsStr.length() > ADDONS_LIMIT) {
            showError(etAddOns, "Max " + ADDONS_LIMIT + " characters");
            return false;
        }

        double price;
        try {
            price = Double.parseDouble(priceStr);
            if (price < PRICE_MIN || price > PRICE_MAX) {
                showError(etPrice, "Price must be ₱" + PRICE_MIN + " - ₱" + PRICE_MAX);
                return false;
            }
        } catch (NumberFormatException e) {
            showError(etPrice, "Invalid price");
            return false;
        }

        try {
            int prepTimeVal = Integer.parseInt(prepTime);
            if (prepTimeVal < PREP_TIME_MIN || prepTimeVal > PREP_TIME_MAX) {
                showError(etPrepTime, "Prep time must be " + PREP_TIME_MIN + "-" + PREP_TIME_MAX + " mins");
                return false;
            }
        } catch (NumberFormatException e) {
            showError(etPrepTime, "Invalid prep time");
            return false;
        }

        // Convert strings to lists
        List<String> addOns = null;
        if (!addOnsStr.isEmpty()) {
            addOns = Arrays.asList(addOnsStr.split("\\s*,\\s*"));
        }

        // Update food object
        food.setName(name);
        food.setPrice(price);
        food.setDescription(description);
        food.setPrepTime(prepTime);
        food.setCategory(category);
        food.setAddOns(addOns);
        food.setAvailable(available);

        // Update image if new one was selected
        if (selectedImageBase64 != null) {
            food.setBase64Image(selectedImageBase64);
        }

        // Update in Firebase
        foodsRef.child(food.getKey()).setValue(food)
                .addOnSuccessListener(aVoid -> {
                    showSuccessToast(food.getName() + " updated");
                    foodAdapter.notifyItemChanged(position);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });

        return true;
    }

    private void showCharacterLimitError(EditText field, String fieldName, int limit) {
        showError(field, fieldName + " cannot exceed " + limit + " characters");
        vibrate();
    }

    private void showError(EditText field, String message) {
        field.setError(message);
        field.requestFocus();
        vibrate();
    }

    private void showWarningToast(String message) {
        Toast.makeText(getContext(), "⚠️ " + message, Toast.LENGTH_SHORT).show();
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
        private List<String> addOns;
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

        public List<String> getAddOns() { return addOns; }
        public void setAddOns(List<String> addOns) { this.addOns = addOns; }
    }
}