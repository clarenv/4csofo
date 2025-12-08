package com.example.a4csofo;

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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
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
import java.util.*;

import static android.app.Activity.RESULT_OK;

public class AdminMenuItemsFragment extends Fragment {

    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_PICK_IMAGE = 200;

    private TextInputEditText etName, etPrice, etPrepTime, etDescription,
            etDiscountSenior, etDiscountPWD;
    private Spinner spCategory;
    private SwitchMaterial switchInStock;
    private ExtendedFloatingActionButton fabAddFood;
    private FloatingActionButton fabPickImage;
    private ImageView ivFoodPreview;
    private LinearLayout placeholderLayout;

    private DatabaseReference foodsRef;
    private String base64Image = null;

    // Character limits
    private static final int NAME_LIMIT = 50;
    private static final int DESC_LIMIT = 200;
    private static final int PREP_TIME_MIN = 1;
    private static final int PREP_TIME_MAX = 999;
    private static final double PRICE_MIN = 1;
    private static final double PRICE_MAX = 10000;
    private static final int DISCOUNT_MIN = 0;
    private static final int DISCOUNT_MAX = 50;

    // Smart food suggestions based on category (for autocomplete)
    private static final Map<String, List<String>> FOOD_SUGGESTIONS = new HashMap<String, List<String>>() {{
        put("Main Dish", Arrays.asList(
                "Adobo", "Sinigang na Baboy", "Kare-Kare", "Lechon Kawali",
                "Menudo", "Caldereta", "Pakbet", "Bicol Express",
                "Dinuguan", "Pork Sisig", "Beef Steak", "Chicken Inasal",
                "Pinakbet", "Laing", "Tinolang Manok", "Afritada",
                "Mechado", "Escabeche", "Crispy Pata", "Beef Bulalo",
                "Pork BBQ", "Chicken Curry", "Beef Salpicao", "Paksiw na Isda",
                "Pork Steak", "Chicken Teriyaki", "Beef Kaldereta", "Chop Suey",
                "Beef Pares", "Pork Binagoongan", "Chicken Cordon Bleu"
        ));
        put("Drinks", Arrays.asList(
                "Soda", "Iced Tea", "Fresh Lemonade", "Mango Shake",
                "Buko Juice", "Calamansi Juice", "Fresh Orange Juice",
                "Coke", "Sprite", "Royal", "Pineapple Juice",
                "Watermelon Shake", "Chocolate Shake", "Coffee",
                "Hot Chocolate", "Milk Tea", "Green Tea", "Mineral Water",
                "Buko Shake", "Guyabano Juice", "Dalandan Juice", "Melon Juice",
                "Avocado Shake", "Strawberry Shake", "Iced Coffee", "Milkshake",
                "Four Seasons", "Sago't Gulaman", "Fresh Buko", "Tropical Juice"
        ));
        put("Dessert", Arrays.asList(
                "Halo-Halo", "Leche Flan", "Buko Pandan", "Mango Float",
                "Bibingka", "Puto", "Kutsinta", "Sapin-Sapin",
                "Buko Salad", "Turon", "Banana Cue", "Cassava Cake",
                "Gulaman", "Mais Con Yelo", "Buko Pie", "Silvanas",
                "Ube Halaya", "Maja Blanca", "Espasol", "Pichi-Pichi",
                "Macapuno", "Yema Cake", "Brazo de Mercedes", "Sans Rival",
                "Pastillas", "Polvoron", "Mamon", "Ensaymada",
                "Coconut Macaroons", "Egg Pie"
        ));
        put("Snacks", Arrays.asList(
                "Pancit Canton", "Lumpiang Shanghai", "Siomai", "Fish Ball",
                "Kikiam", "Cheese Sticks", "French Fries", "Chicken Nuggets",
                "Empanada", "Okoy", "Toge", "Camote Cue",
                "Turon", "Banana Cue", "Sandwich", "Pizza",
                "Burger", "Hotdog", "Nachos", "Dynamite",
                "Chicharon", "Peanuts", "Popcorn", "Crackers",
                "Siopao", "Puto Bumbong", "Bibingkang Malagkit", "Taho",
                "Balut", "Penoy", "Isaw", "Betamax"
        ));
    }};

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
        etDiscountSenior = view.findViewById(R.id.etDiscountSenior);
        etDiscountPWD = view.findViewById(R.id.etDiscountPWD);
        switchInStock = view.findViewById(R.id.switchInStock);
        spCategory = view.findViewById(R.id.spCategory);
        fabAddFood = view.findViewById(R.id.fabAddFood);
        fabPickImage = view.findViewById(R.id.fabPickImage);
        ivFoodPreview = view.findViewById(R.id.ivFoodPreview);
        placeholderLayout = view.findViewById(R.id.placeholderLayout);

        // Setup smart input limitations
        setupSmartInputLimitations();

        // Change switch color based on state
        switchInStock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                switchInStock.setTrackTintList(getResources().getColorStateList(R.color.primary));
                switchInStock.setThumbTintList(getResources().getColorStateList(R.color.primary));
            } else {
                switchInStock.setTrackTintList(getResources().getColorStateList(R.color.nav_ripple));
                switchInStock.setThumbTintList(getResources().getColorStateList(R.color.nav_ripple));
            }
        });

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

        // Setup category change listener for smart suggestions
        spCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCategory = spCategory.getSelectedItem().toString();
                setupSmartFiller(selectedCategory);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // Setup food name suggestions (DIALOG APPROACH)
        setupFoodNameSuggestions();

        // Show image source dialog when FAB or placeholder is clicked
        fabPickImage.setOnClickListener(v -> showImageSourceDialog());
        placeholderLayout.setOnClickListener(v -> showImageSourceDialog());

        // Add food listener - shows confirmation dialog
        fabAddFood.setOnClickListener(v -> showAddConfirmationDialog());

        return view;
    }

    private void setupFoodNameSuggestions() {
        // Add a search icon to show suggestions
        if (etName.getParent().getParent() instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) etName.getParent().getParent();
            layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
            layout.setEndIconDrawable(android.R.drawable.ic_menu_search);
            layout.setEndIconOnClickListener(v -> {
                showFoodNameSuggestionsDialog();
            });
        }

        // Also show suggestions when field is clicked and empty
        etName.setOnClickListener(v -> {
            if (etName.getText().toString().isEmpty()) {
                showFoodNameSuggestionsDialog();
            }
        });

        etName.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && etName.getText().toString().isEmpty()) {
                showFoodNameSuggestionsDialog();
            }
        });
    }

    private void setupSmartFiller(String category) {
        // Smart filler for Description based on category and food name
        etDescription.setOnClickListener(v -> {
            if (etDescription.getText().toString().isEmpty()) {
                showDescriptionSmartFillerDialog(category);
            }
        });
    }

    private void showFoodNameSuggestionsDialog() {
        String category = spCategory.getSelectedItem().toString();

        if (!FOOD_SUGGESTIONS.containsKey(category)) {
            Toast.makeText(getContext(), "No suggestions available", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> suggestions = FOOD_SUGGESTIONS.get(category);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Food Name Suggestions");
        builder.setMessage("Choose from " + category + " category");

        builder.setItems(suggestions.toArray(new String[0]), (dialog, which) -> {
            String selectedFood = suggestions.get(which);
            etName.setText(selectedFood);
            showSuccessToast(selectedFood + " selected");
        });

        builder.setNegativeButton("Cancel", null);

        builder.setNeutralButton("All Categories", (dialog, which) -> {
            showAllFoodSuggestionsDialog();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showAllFoodSuggestionsDialog() {
        // Combine all suggestions
        List<String> allSuggestions = new ArrayList<>();
        for (List<String> suggestions : FOOD_SUGGESTIONS.values()) {
            allSuggestions.addAll(suggestions);
        }
        Collections.sort(allSuggestions);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("All Food Suggestions");

        builder.setItems(allSuggestions.toArray(new String[0]), (dialog, which) -> {
            String selectedFood = allSuggestions.get(which);
            etName.setText(selectedFood);
            showSuccessToast(selectedFood + " selected");
        });

        builder.setNegativeButton("Back", (dialog, which) -> {
            showFoodNameSuggestionsDialog();
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showDescriptionSmartFillerDialog(String category) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Smart Filler - Description");
        builder.setMessage("Select a description template");

        String foodName = etName.getText().toString().trim();
        String[] templates = getDescriptionTemplates(category, foodName);

        builder.setItems(templates, (dialog, which) -> {
            String selectedTemplate = templates[which];

            // Limit to DESC_LIMIT characters
            if (selectedTemplate.length() > DESC_LIMIT) {
                selectedTemplate = selectedTemplate.substring(0, DESC_LIMIT);
                showWarningToast("Description trimmed to " + DESC_LIMIT + " characters");
            }

            etDescription.setText(selectedTemplate);
            showSuccessToast("Description filled");
        });

        builder.setNegativeButton("Cancel", null);

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private String[] getDescriptionTemplates(String category, String foodName) {
        switch (category) {
            case "Main Dish":
                return new String[]{
                        "Delicious " + (foodName.isEmpty() ? "dish" : foodName) + " made with fresh ingredients and traditional Filipino spices.",
                        "A hearty meal that's perfect for lunch or dinner. Served with steamed rice.",
                        "Our specialty " + (foodName.isEmpty() ? "main dish" : foodName) + " cooked to perfection with authentic Filipino flavors.",
                        "Tender and flavorful " + (foodName.isEmpty() ? "meat" : foodName) + " simmered in rich sauce with vegetables."
                };
            case "Drinks":
                return new String[]{
                        "Refreshing " + (foodName.isEmpty() ? "drink" : foodName) + " perfect for any time of day.",
                        "Cool and revitalizing beverage made with fresh ingredients.",
                        "Our signature " + (foodName.isEmpty() ? "drink" : foodName) + " that will quench your thirst instantly.",
                        "Perfectly balanced " + (foodName.isEmpty() ? "beverage" : foodName) + " with just the right amount of sweetness."
                };
            case "Dessert":
                return new String[]{
                        "Sweet and delightful " + (foodName.isEmpty() ? "dessert" : foodName) + " that's perfect for ending your meal.",
                        "A Filipino favorite dessert made with quality ingredients.",
                        "Our special " + (foodName.isEmpty() ? "dessert" : foodName) + " that will satisfy your sweet cravings.",
                        "Creamy and delicious " + (foodName.isEmpty() ? "treat" : foodName) + " made with love and care."
                };
            case "Snacks":
                return new String[]{
                        "Perfect snack " + (foodName.isEmpty() ? "item" : foodName) + " for merienda or light bites.",
                        "Crispy and savory " + (foodName.isEmpty() ? "snack" : foodName) + " that's great for sharing.",
                        "Quick and delicious " + (foodName.isEmpty() ? "snack" : foodName) + " ready in minutes.",
                        "Popular Filipino " + (foodName.isEmpty() ? "snack" : foodName) + " that everyone loves."
                };
            default:
                return new String[]{
                        "Delicious item made with quality ingredients.",
                        "Our special recipe that customers love.",
                        "Perfectly prepared for your enjoyment."
                };
        }
    }

    private void setupSmartInputLimitations() {
        // Setup for Food Name
        setupTextLimiter(etName, NAME_LIMIT, "Food Name");

        // Setup for Description
        setupTextLimiter(etDescription, DESC_LIMIT, "Description");

        // Setup for Prep Time (1-999, no decimals, no negative)
        etPrepTime.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        setupNumberLimiter(etPrepTime, PREP_TIME_MIN, PREP_TIME_MAX, "Prep Time (minutes)");

        // Setup for Price (1-10000, 2 decimal places max)
        etPrice.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        setupPriceLimiter(etPrice, PRICE_MIN, PRICE_MAX);

        // Setup for Discounts (0-50, integers only)
        etDiscountSenior.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        setupDiscountLimiter(etDiscountSenior);

        etDiscountPWD.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        setupDiscountLimiter(etDiscountPWD);
    }

    private void setupTextLimiter(TextInputEditText editText, int maxLength, String fieldName) {
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

                // Check length limit - PREVENT typing beyond limit
                if (currentText.length() > maxLength) {
                    editText.removeTextChangedListener(this);
                    editText.setText(lastValidText);
                    editText.setSelection(lastValidText.length());
                    editText.addTextChangedListener(this);

                    // Show error and vibrate
                    showCharacterLimitError(editText, fieldName, maxLength);
                    return;
                }

                // Update last valid text if within limit
                lastValidText = currentText;
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Clear error if text is within limit
                if (s.length() <= maxLength) {
                    clearError(editText);
                }
            }
        });
    }

    private void setupNumberLimiter(TextInputEditText editText, int min, int max, String fieldName) {
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

                // If empty, allow
                if (currentText.isEmpty()) {
                    lastValidText = "";
                    return;
                }

                try {
                    int value = Integer.parseInt(currentText);

                    // Check if exceeds max
                    if (value > max) {
                        editText.removeTextChangedListener(this);
                        editText.setText(String.valueOf(max));
                        editText.setSelection(String.valueOf(max).length());
                        editText.addTextChangedListener(this);
                        showError(editText, fieldName + " maximum is " + max);
                        vibrate();
                    } else if (value < min) {
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
                    // If not a valid number, revert to last valid
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

    private void setupPriceLimiter(TextInputEditText editText, double min, double max) {
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

                // Prevent multiple decimal points
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

                // Validate price range
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
                        // Revert to last valid
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

    private void setupDiscountLimiter(TextInputEditText editText) {
        setupNumberLimiter(editText, DISCOUNT_MIN, DISCOUNT_MAX, "Discount");
    }

    private void showCharacterLimitError(TextInputEditText field, String fieldName, int limit) {
        String message = fieldName + " cannot exceed " + limit + " characters";
        showError(field, message);
        vibrate();
    }

    private void showWarningToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), "⚠️ " + message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showSuccessToast(String message) {
        if (getContext() != null) {
            Toast.makeText(getContext(), "✅ " + message, Toast.LENGTH_SHORT).show();
        }
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
            // Ignore vibration errors
        }
    }

    private void showImageSourceDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Select Image Source");
        builder.setItems(new CharSequence[]{"Take Photo", "Choose from Gallery"}, (dialog, which) -> {
            if (which == 0) {
                takePhoto();
            } else {
                pickImageFromGallery();
            }
        });
        builder.show();
    }

    private void showAddConfirmationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Confirm Add Food");
        builder.setMessage("Are you sure you want to add this food item to the menu?");

        builder.setPositiveButton("Yes, Add", (dialog, which) -> {
            addFoodItem();
        });

        builder.setNegativeButton("No, Cancel", (dialog, which) -> {
            dialog.dismiss();
        });

        AlertDialog dialog = builder.create();
        dialog.show();

        // Style the buttons
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.primary));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.light_gray));
    }

    private void takePhoto() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getActivity().getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        } else {
            Toast.makeText(getContext(), "No camera app found", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickImageFromGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), REQUEST_PICK_IMAGE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            Bitmap bitmap = null;

            if (requestCode == REQUEST_IMAGE_CAPTURE && data != null && data.getExtras() != null) {
                // Photo taken with camera
                bitmap = (Bitmap) data.getExtras().get("data");
            }
            else if (requestCode == REQUEST_PICK_IMAGE && data != null && data.getData() != null) {
                // Image picked from gallery
                try {
                    Uri imageUri = data.getData();
                    if (getActivity() == null) return;

                    InputStream inputStream = getActivity().getContentResolver().openInputStream(imageUri);
                    bitmap = BitmapFactory.decodeStream(inputStream);
                } catch (Exception e) {
                    Toast.makeText(getContext(), "Failed to select image", Toast.LENGTH_SHORT).show();
                }
            }

            if (bitmap != null) {
                processAndSetImage(bitmap);
            }
        }
    }

    private void processAndSetImage(Bitmap bitmap) {
        try {
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
            Toast.makeText(getContext(), "Failed to process image", Toast.LENGTH_SHORT).show();
        }
    }

    private void addFoodItem() {
        String name = etName.getText().toString().trim();
        String priceStr = etPrice.getText().toString().trim();
        String prepTimeStr = etPrepTime.getText().toString().trim();
        String description = etDescription.getText().toString().trim();
        String discountSeniorStr = etDiscountSenior.getText().toString().trim();
        String discountPWDStr = etDiscountPWD.getText().toString().trim();
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

        if (prepTimeStr.isEmpty()) {
            showError(etPrepTime, "Preparation time is required");
            return;
        }

        if (base64Image == null) {
            Toast.makeText(getContext(), "Please select an image", Toast.LENGTH_SHORT).show();
            return;
        }

        double price, discountSenior = 0, discountPWD = 0;
        int prepTime = 0;

        try {
            // Validate price (1 - 10,000 pesos)
            price = Double.parseDouble(priceStr);
            if (price <= 0) {
                showError(etPrice, "Price must be greater than 0");
                return;
            }
            if (price > PRICE_MAX) {
                showError(etPrice, "Price must be ₱" + PRICE_MAX + " or less");
                return;
            }

            // Validate prep time (1 - 999 minutes)
            prepTime = Integer.parseInt(prepTimeStr);
            if (prepTime < PREP_TIME_MIN) {
                showError(etPrepTime, "Prep time must be at least " + PREP_TIME_MIN + " minute");
                return;
            }
            if (prepTime > PREP_TIME_MAX) {
                showError(etPrepTime, "Prep time must be " + PREP_TIME_MAX + " minutes or less");
                return;
            }

            // Validate name length (max 50 characters)
            if (name.length() > NAME_LIMIT) {
                showError(etName, "Food name must be " + NAME_LIMIT + " characters or less");
                return;
            }

            // Validate description length (max 200 characters)
            if (description.length() > DESC_LIMIT) {
                showError(etDescription, "Description must be " + DESC_LIMIT + " characters or less");
                return;
            }

            // Validate senior discount (0-50%)
            if (!discountSeniorStr.isEmpty()) {
                discountSenior = Double.parseDouble(discountSeniorStr);
                if (discountSenior < DISCOUNT_MIN) {
                    showError(etDiscountSenior, "Senior discount cannot be negative");
                    return;
                }
                if (discountSenior > DISCOUNT_MAX) {
                    showError(etDiscountSenior, "Senior discount max is " + DISCOUNT_MAX + "%");
                    return;
                }
            }

            // Validate PWD discount (0-50%)
            if (!discountPWDStr.isEmpty()) {
                discountPWD = Double.parseDouble(discountPWDStr);
                if (discountPWD < DISCOUNT_MIN) {
                    showError(etDiscountPWD, "PWD discount cannot be negative");
                    return;
                }
                if (discountPWD > DISCOUNT_MAX) {
                    showError(etDiscountPWD, "PWD discount max is " + DISCOUNT_MAX + "%");
                    return;
                }
            }
        } catch (NumberFormatException e) {
            Toast.makeText(getContext(), "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            return;
        }

        String key = foodsRef.push().getKey();
        if (key == null) {
            Toast.makeText(getContext(), "Database error: could not generate key", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading state
        fabAddFood.setEnabled(false);
        fabAddFood.setText("Adding...");

        // Create FoodItem with discounts
        FoodItem food = new FoodItem();
        food.id = key;
        food.name = name;
        food.price = price;
        food.discountSenior = discountSenior;
        food.discountPWD = discountPWD;
        food.prepTime = prepTimeStr + " mins";
        food.description = description;
        food.category = category;
        food.base64Image = base64Image;
        food.inStock = inStock;

        foodsRef.child(key).setValue(food)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(getContext(), "✅ Food added successfully!", Toast.LENGTH_SHORT).show();
                    clearFields();
                    fabAddFood.setEnabled(true);
                    fabAddFood.setText("Add Food Item");
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "❌ Failed to add food: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        etDiscountSenior.setText("");
        etDiscountPWD.setText("");
        spCategory.setSelection(0);
        switchInStock.setChecked(true);
        base64Image = null;
        ivFoodPreview.setImageResource(android.R.color.transparent);
        placeholderLayout.setVisibility(View.VISIBLE);

        // Clear errors
        clearError(etName);
        clearError(etPrice);
        clearError(etPrepTime);
        clearError(etDescription);
        clearError(etDiscountSenior);
        clearError(etDiscountPWD);
    }

    private void clearError(TextInputEditText field) {
        if (field.getParent().getParent() instanceof TextInputLayout) {
            TextInputLayout layout = (TextInputLayout) field.getParent().getParent();
            layout.setError(null);
            layout.setErrorEnabled(false);
        }
    }

    // FoodItem Model WITHOUT add-ons
    public static class FoodItem {
        public String id;
        public String name;
        public double price;
        public double discountSenior;
        public double discountPWD;
        public String prepTime;
        public String description;
        public String category;
        public String base64Image;
        public boolean inStock = true;

        // Required empty constructor for Firebase
        public FoodItem() {}

        public FoodItem(String id, String name, double price, double discountSenior, double discountPWD,
                        String prepTime, String description, String category,
                        String base64Image, boolean inStock) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.discountSenior = discountSenior;
            this.discountPWD = discountPWD;
            this.prepTime = prepTime;
            this.description = description;
            this.category = category;
            this.base64Image = base64Image;
            this.inStock = inStock;
        }
    }
}