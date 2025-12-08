package com.example.a4csofo;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.*;

import java.util.*;

public class ClientCartFragment extends Fragment {

    private LinearLayout containerCartItems;
    private LinearLayout emptyCartView;
    private Button btnProceedCheckout;
    private TextView tvTotalAmount;
    private TextView tvCartCount;

    private ArrayList<CartFoodItem> cartItems;
    private ArrayList<String> cartKeys;
    private Map<String, List<AddonSelection>> itemAddonsMap;

    private FirebaseAuth auth;
    private DatabaseReference cartRef;
    private DatabaseReference foodsRef;

    // Quantity limitations
    private static final int MAX_QUANTITY_PER_ITEM = 10;
    private static final int MAX_TOTAL_ITEMS = 50;
    private static final double MAX_TOTAL_AMOUNT = 10000.00;

    // HARD-CODED ADD-ONS BY CATEGORY
    private static final Map<String, List<AddonItem>> CATEGORY_ADDONS = new HashMap<String, List<AddonItem>>() {{
        // MAIN DISH ADD-ONS
        put("main dish", Arrays.asList(
                new AddonItem("extra_rice", "Extra Rice", 15.00, 3),
                new AddonItem("extra_patty", "Extra Patty", 30.00, 2),
                new AddonItem("extra_cheese", "Extra Cheese", 10.00, 5),
                new AddonItem("extra_sauce", "Extra Sauce/Gravy", 8.00, 3),
                new AddonItem("add_egg", "Add Egg", 12.00, 2),
                new AddonItem("extra_veggies", "Extra Vegetables", 5.00, 3)
        ));

        // DRINKS ADD-ONS
        put("drinks", Arrays.asList(
                new AddonItem("large_size", "Large Size", 20.00, 1),
                new AddonItem("extra_ice", "Extra Ice", 5.00, 3),
                new AddonItem("extra_straw", "Extra Straw", 2.00, 5),
                new AddonItem("less_sugar", "Less Sugar", 0.00, 1),
                new AddonItem("no_ice", "No Ice", 0.00, 1),
                new AddonItem("extra_syrup", "Extra Syrup", 8.00, 2)
        ));

        // DESSERT ADD-ONS
        put("dessert", Arrays.asList(
                new AddonItem("extra_cream", "Extra Cream", 5.00, 3),
                new AddonItem("extra_toppings", "Extra Toppings", 10.00, 2),
                new AddonItem("chocolate_syrup", "Chocolate Syrup", 8.00, 2),
                new AddonItem("caramel_syrup", "Caramel Syrup", 8.00, 2),
                new AddonItem("whipped_cream", "Whipped Cream", 5.00, 3),
                new AddonItem("sprinkles", "Sprinkles", 3.00, 5)
        ));

        // SNACKS ADD-ONS
        put("snacks", Arrays.asList(
                new AddonItem("extra_dip", "Extra Dip", 8.00, 2),
                new AddonItem("extra_sauce", "Extra Sauce", 5.00, 3),
                new AddonItem("cheese_dip", "Cheese Dip", 10.00, 2),
                new AddonItem("chili_flakes", "Chili Flakes", 2.00, 5),
                new AddonItem("extra_garlic", "Extra Garlic", 3.00, 3),
                new AddonItem("vinegar_dip", "Vinegar Dip", 0.00, 2)
        ));

        // DEFAULT (if category not found)
        put("default", Arrays.asList(
                new AddonItem("extra_serving", "Extra Serving", 10.00, 2),
                new AddonItem("add_ons", "Add Ons", 5.00, 3)
        ));
    }};

    public ClientCartFragment() {}

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_client_cart, container, false);

        containerCartItems = view.findViewById(R.id.containerCartItems);
        emptyCartView = view.findViewById(R.id.emptyCartView);
        btnProceedCheckout = view.findViewById(R.id.btnProceedCheckout);
        tvTotalAmount = view.findViewById(R.id.tvTotalAmount);
        tvCartCount = view.findViewById(R.id.tvCartCount);

        auth = FirebaseAuth.getInstance();
        FirebaseUser currentUser = auth.getCurrentUser();

        if (currentUser == null) {
            Toast.makeText(getContext(), "Please log in first", Toast.LENGTH_SHORT).show();
            if (getActivity() != null) getActivity().finish();
            return view;
        }

        cartItems = new ArrayList<>();
        cartKeys = new ArrayList<>();
        itemAddonsMap = new HashMap<>();

        cartRef = FirebaseDatabase.getInstance()
                .getReference("carts")
                .child(currentUser.getUid());

        foodsRef = FirebaseDatabase.getInstance()
                .getReference("foods");

        // LOAD CART ITEMS
        cartRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!isAdded()) return;

                cartItems.clear();
                cartKeys.clear();
                containerCartItems.removeAllViews();
                itemAddonsMap.clear();

                if (!snapshot.exists()) {
                    emptyCartView.setVisibility(View.VISIBLE);
                    tvCartCount.setVisibility(View.GONE);
                    updateTotalAmount();
                    return;
                }

                // Group items by their Firebase key (unique for each cart entry)
                for (DataSnapshot data : snapshot.getChildren()) {
                    CartFoodItem food = data.getValue(CartFoodItem.class);
                    if (food != null) {
                        String key = data.getKey();
                        cartItems.add(food);
                        cartKeys.add(key);

                        // Load saved addons for this item
                        List<AddonSelection> addons = new ArrayList<>();
                        if (data.child("addons").exists()) {
                            for (DataSnapshot addonSnap : data.child("addons").getChildren()) {
                                AddonSelection addon = addonSnap.getValue(AddonSelection.class);
                                if (addon != null) {
                                    addons.add(addon);
                                }
                            }
                        }
                        itemAddonsMap.put(key, addons);
                    }
                }

                if (cartItems.isEmpty()) {
                    emptyCartView.setVisibility(View.VISIBLE);
                    tvCartCount.setVisibility(View.GONE);
                } else {
                    emptyCartView.setVisibility(View.GONE);
                    tvCartCount.setVisibility(View.VISIBLE);

                    int totalItems = getTotalCartItems();

                    // Display warning if cart exceeds limit
                    if (totalItems > MAX_TOTAL_ITEMS) {
                        showExceedLimitWarning(totalItems);
                    }

                    tvCartCount.setText(totalItems + " item" + (totalItems != 1 ? "s" : ""));

                    // Display each cart item
                    for (int i = 0; i < cartItems.size(); i++) {
                        CartFoodItem item = cartItems.get(i);
                        String key = cartKeys.get(i);
                        List<AddonSelection> addons = itemAddonsMap.get(key);

                        addCartItemView(item, key, addons);
                    }

                    updateTotalAmount();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                Toast.makeText(getContext(),
                        "Failed to load cart: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Proceed to Checkout Button
        btnProceedCheckout.setOnClickListener(v -> {
            if (cartItems.isEmpty()) {
                Toast.makeText(getContext(), "Your cart is empty!", Toast.LENGTH_SHORT).show();
                return;
            }

            // Check total items limit
            int totalItems = getTotalCartItems();
            if (totalItems > MAX_TOTAL_ITEMS) {
                showExceedLimitWarning(totalItems);
                return;
            }

            double total = calculateTotal();
            if (total > MAX_TOTAL_AMOUNT) {
                Toast.makeText(getContext(),
                        "Order total exceeds maximum allowed amount of ₱" +
                                String.format("%.2f", MAX_TOTAL_AMOUNT),
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Start CheckoutActivity
            Intent intent = new Intent(requireActivity(), CheckoutActivity.class);
            startActivity(intent);
        });

        return view;
    }

    private void showExceedLimitWarning(int currentTotal) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle("Order Limit Exceeded")
                .setMessage("You have " + currentTotal + " items in your cart, but the maximum allowed is " + MAX_TOTAL_ITEMS + " items total.\n\n" +
                        "Please remove some items before proceeding to checkout.")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void addCartItemView(CartFoodItem item, String key, List<AddonSelection> addons) {
        View itemView = LayoutInflater.from(getContext())
                .inflate(R.layout.cart_item_view, containerCartItems, false);

        ImageView imgFood = itemView.findViewById(R.id.imgItem);
        TextView tvName = itemView.findViewById(R.id.tvItemName);
        TextView tvPrice = itemView.findViewById(R.id.tvItemPrice);
        TextView tvQty = itemView.findViewById(R.id.tvQty);
        Button btnMinus = itemView.findViewById(R.id.btnMinus);
        Button btnAdd = itemView.findViewById(R.id.btnAdd);
        TextView tvAddonsIndicator = itemView.findViewById(R.id.tvAddonsIndicator);

        tvName.setText(item.name);

        // Calculate total with addons
        double addonsTotal = calculateAddonsTotal(addons);
        double itemTotal = (item.price * item.quantity) + addonsTotal;

        tvPrice.setText("₱" + String.format("%.2f", itemTotal));
        tvQty.setText(String.valueOf(item.quantity));

        // Display addons indicator
        if (addons != null && !addons.isEmpty()) {
            tvAddonsIndicator.setVisibility(View.VISIBLE);
            StringBuilder addonText = new StringBuilder("+ ");
            for (int i = 0; i < Math.min(addons.size(), 2); i++) {
                if (i > 0) addonText.append(", ");
                addonText.append(addons.get(i).name);
                if (addons.get(i).quantity > 1) {
                    addonText.append("(").append(addons.get(i).quantity).append(")");
                }
            }
            if (addons.size() > 2) {
                addonText.append("...");
            }
            tvAddonsIndicator.setText(addonText.toString());
        } else {
            tvAddonsIndicator.setVisibility(View.GONE);
        }

        // Display base64 image
        if (item.base64Image != null && !item.base64Image.isEmpty()) {
            Bitmap bmp = base64ToBitmap(item.base64Image);
            if (bmp != null) imgFood.setImageBitmap(bmp);
        }

        // Add button with quantity limit check
        btnAdd.setOnClickListener(v -> {
            int currentQty = Integer.parseInt(tvQty.getText().toString());

            // Check per item limit
            if (currentQty >= MAX_QUANTITY_PER_ITEM) {
                Toast.makeText(getContext(),
                        "Maximum " + MAX_QUANTITY_PER_ITEM + " of this item allowed",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Check total cart items limit
            int totalCartItems = getTotalCartItems();
            if (totalCartItems >= MAX_TOTAL_ITEMS) {
                Toast.makeText(getContext(),
                        "Maximum " + MAX_TOTAL_ITEMS + " total items allowed in cart",
                        Toast.LENGTH_LONG).show();
                return;
            }

            if (totalCartItems + 1 > MAX_TOTAL_ITEMS) {
                Toast.makeText(getContext(),
                        "Cannot add more items. Maximum " + MAX_TOTAL_ITEMS + " total items allowed. " +
                                "Current items: " + totalCartItems,
                        Toast.LENGTH_LONG).show();
                return;
            }

            currentQty++;
            updateCartItemQuantity(key, currentQty, tvQty, tvPrice, item, addons);
        });

        // Minus button
        btnMinus.setOnClickListener(v -> {
            int currentQty = Integer.parseInt(tvQty.getText().toString());

            if (currentQty > 1) {
                currentQty--;
                updateCartItemQuantity(key, currentQty, tvQty, tvPrice, item, addons);
            } else {
                new AlertDialog.Builder(requireActivity())
                        .setTitle("Remove Item")
                        .setMessage("Remove " + item.name + " from cart?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            cartRef.child(key).removeValue();
                        })
                        .setNegativeButton("No", null)
                        .show();
            }
        });

        // Click on item to add addons
        itemView.setOnClickListener(v -> {
            getFoodCategoryAndShowAddonsDialog(item, key, addons, tvAddonsIndicator, tvPrice);
        });

        // Click on addons indicator to add addons
        if (tvAddonsIndicator.getVisibility() == View.VISIBLE) {
            tvAddonsIndicator.setOnClickListener(v -> {
                getFoodCategoryAndShowAddonsDialog(item, key, addons, tvAddonsIndicator, tvPrice);
            });
        }

        containerCartItems.addView(itemView);
    }

    private void getFoodCategoryAndShowAddonsDialog(CartFoodItem item, String key,
                                                    List<AddonSelection> currentAddons,
                                                    TextView tvAddonsIndicator, TextView tvItemPrice) {
        // First, get the food category from Firebase
        foodsRef.orderByChild("name").equalTo(item.name)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String foodCategory = "default"; // default category

                        for (DataSnapshot foodSnap : snapshot.getChildren()) {
                            ClientHomeFragment.FoodItem foodItem = foodSnap.getValue(ClientHomeFragment.FoodItem.class);
                            if (foodItem != null && foodItem.category != null) {
                                String category = foodItem.category.toLowerCase().trim();
                                // Check if category exists in our hard-coded addons
                                if (CATEGORY_ADDONS.containsKey(category)) {
                                    foodCategory = category;
                                }
                                break;
                            }
                        }

                        // Show addons dialog with hard-coded addons
                        showHardCodedAddonsDialog(item, key, foodCategory, currentAddons,
                                tvAddonsIndicator, tvItemPrice);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(getContext(), "Failed to load food category", Toast.LENGTH_SHORT).show();
                        // Show default addons if error
                        showHardCodedAddonsDialog(item, key, "default", currentAddons,
                                tvAddonsIndicator, tvItemPrice);
                    }
                });
    }

    private void showHardCodedAddonsDialog(CartFoodItem item, String key, final String foodCategory,
                                           List<AddonSelection> currentAddons,
                                           final TextView tvAddonsIndicator, final TextView tvItemPrice) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        String title = "Add-ons for " + item.name;

        // Add category to title if available
        if (!foodCategory.equals("default")) {
            String formattedCategory = foodCategory.substring(0, 1).toUpperCase() + foodCategory.substring(1);
            title += " (" + formattedCategory + ")";
        }
        builder.setTitle(title);

        View dialogView = LayoutInflater.from(getContext())
                .inflate(R.layout.dialog_addons, null);
        builder.setView(dialogView);

        final LinearLayout containerAddons = dialogView.findViewById(R.id.containerAddons);
        final TextView tvAddonsTotal = dialogView.findViewById(R.id.tvAddonsTotal);
        final TextView tvCurrentItemTotal = dialogView.findViewById(R.id.tvCurrentItemTotal);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        AlertDialog dialog = builder.create();

        // Get item quantity from cart
        final int itemQuantity = item.quantity;
        final double baseItemTotal = item.price * itemQuantity;
        double currentAddonsTotal = calculateAddonsTotal(currentAddons);
        double currentTotal = baseItemTotal + currentAddonsTotal;

        tvCurrentItemTotal.setText("Item Total: ₱" + String.format("%.2f", currentTotal));
        tvAddonsTotal.setText("Add-ons: ₱" + String.format("%.2f", currentAddonsTotal));

        // Get addons for this category
        final List<AddonItem> categoryAddons = CATEGORY_ADDONS.get(foodCategory);
        if (categoryAddons == null) {
            final List<AddonItem> defaultAddons = CATEGORY_ADDONS.get("default");
            setupAddonsDialog(containerAddons, tvAddonsTotal, tvCurrentItemTotal,
                    currentAddons, defaultAddons, baseItemTotal,
                    dialog, item, key, tvAddonsIndicator, tvItemPrice, btnSave);
        } else {
            setupAddonsDialog(containerAddons, tvAddonsTotal, tvCurrentItemTotal,
                    currentAddons, categoryAddons, baseItemTotal,
                    dialog, item, key, tvAddonsIndicator, tvItemPrice, btnSave);
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void setupAddonsDialog(final LinearLayout containerAddons,
                                   final TextView tvAddonsTotal,
                                   final TextView tvCurrentItemTotal,
                                   List<AddonSelection> currentAddons,
                                   final List<AddonItem> categoryAddons,
                                   final double baseItemTotal,
                                   final AlertDialog dialog,
                                   final CartFoodItem item,
                                   final String key,
                                   final TextView tvAddonsIndicator,
                                   final TextView tvItemPrice,
                                   Button btnSave) {

        containerAddons.removeAllViews();
        final Map<String, Integer> selectedAddonCounts = new HashMap<>();

        // Initialize selected counts from current addons
        if (currentAddons != null) {
            for (AddonSelection addon : currentAddons) {
                if (addon.addonId != null) {
                    selectedAddonCounts.put(addon.addonId, addon.quantity);
                }
            }
        }

        // Display each addon
        for (final AddonItem addon : categoryAddons) {
            View addonView = LayoutInflater.from(getContext())
                    .inflate(R.layout.item_addon, containerAddons, false);

            CheckBox cbAddon = addonView.findViewById(R.id.cbAddon);
            TextView tvName = addonView.findViewById(R.id.tvAddonName);
            TextView tvPriceView = addonView.findViewById(R.id.tvAddonPrice);
            TextView tvLimit = addonView.findViewById(R.id.tvAddonLimit);
            Button btnMinus = addonView.findViewById(R.id.btnAddonMinus);
            Button btnAddBtn = addonView.findViewById(R.id.btnAddonAdd);
            final TextView tvQty = addonView.findViewById(R.id.tvAddonQty);
            final LinearLayout quantityControls = addonView.findViewById(R.id.quantityControls);

            tvName.setText(addon.name);
            if (addon.price > 0) {
                tvPriceView.setText("₱" + String.format("%.2f", addon.price));
            } else {
                tvPriceView.setText("FREE");
            }

            if (addon.limit > 0) {
                tvLimit.setText("Max: " + addon.limit);
                tvLimit.setVisibility(View.VISIBLE);
            }

            // Check if this addon is currently selected
            boolean isSelected = addon.id != null && selectedAddonCounts.containsKey(addon.id);
            cbAddon.setChecked(isSelected);
            quantityControls.setVisibility(isSelected ? View.VISIBLE : View.GONE);

            if (isSelected && addon.id != null) {
                int currentQty = selectedAddonCounts.get(addon.id);
                tvQty.setText(String.valueOf(currentQty));
            }

            // Addon checkbox listener
            cbAddon.setOnCheckedChangeListener((buttonView, isChecked) -> {
                quantityControls.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (isChecked && addon.id != null) {
                    selectedAddonCounts.put(addon.id, 1);
                    tvQty.setText("1");
                } else if (addon.id != null) {
                    selectedAddonCounts.remove(addon.id);
                }
                updateHardCodedAddonsTotal(selectedAddonCounts, categoryAddons, tvAddonsTotal, baseItemTotal, tvCurrentItemTotal);
            });

            // Quantity controls
            btnAddBtn.setOnClickListener(v -> {
                int currentQty = Integer.parseInt(tvQty.getText().toString());
                if (addon.limit > 0 && currentQty >= addon.limit) {
                    Toast.makeText(getContext(),
                            "Maximum " + addon.limit + " of " + addon.name + " allowed",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                currentQty++;
                tvQty.setText(String.valueOf(currentQty));
                if (addon.id != null) {
                    selectedAddonCounts.put(addon.id, currentQty);
                }
                updateHardCodedAddonsTotal(selectedAddonCounts, categoryAddons, tvAddonsTotal, baseItemTotal, tvCurrentItemTotal);
            });

            btnMinus.setOnClickListener(v -> {
                int currentQty = Integer.parseInt(tvQty.getText().toString());
                if (currentQty > 1) {
                    currentQty--;
                    tvQty.setText(String.valueOf(currentQty));
                    if (addon.id != null) {
                        selectedAddonCounts.put(addon.id, currentQty);
                    }
                } else {
                    cbAddon.setChecked(false);
                }
                updateHardCodedAddonsTotal(selectedAddonCounts, categoryAddons, tvAddonsTotal, baseItemTotal, tvCurrentItemTotal);
            });

            containerAddons.addView(addonView);
        }

        updateHardCodedAddonsTotal(selectedAddonCounts, categoryAddons, tvAddonsTotal, baseItemTotal, tvCurrentItemTotal);

        btnSave.setOnClickListener(v -> {
            // Collect selected addons
            List<AddonSelection> newAddons = new ArrayList<>();

            // Iterate through all addons in the container
            for (int i = 0; i < containerAddons.getChildCount(); i++) {
                View addonView = containerAddons.getChildAt(i);
                CheckBox cb = addonView.findViewById(R.id.cbAddon);
                TextView tvQty = addonView.findViewById(R.id.tvAddonQty);
                TextView tvName = addonView.findViewById(R.id.tvAddonName);

                if (cb.isChecked()) {
                    // Find the addon by name
                    String addonName = tvName.getText().toString();
                    for (AddonItem addon : categoryAddons) {
                        if (addon.name.equals(addonName)) {
                            int quantity = Integer.parseInt(tvQty.getText().toString());
                            newAddons.add(new AddonSelection(addon.id, addon.name, addon.price, quantity));
                            break;
                        }
                    }
                }
            }

            // Save to Firebase
            DatabaseReference itemRef = cartRef.child(key);
            itemRef.child("addons").setValue(newAddons);

            // Update local map
            itemAddonsMap.put(key, newAddons);

            // Update UI
            if (!newAddons.isEmpty()) {
                tvAddonsIndicator.setVisibility(View.VISIBLE);
                StringBuilder addonText = new StringBuilder("+ ");
                for (int i = 0; i < Math.min(newAddons.size(), 2); i++) {
                    if (i > 0) addonText.append(", ");
                    addonText.append(newAddons.get(i).name);
                    if (newAddons.get(i).quantity > 1) {
                        addonText.append("(").append(newAddons.get(i).quantity).append(")");
                    }
                }
                if (newAddons.size() > 2) {
                    addonText.append("...");
                }
                tvAddonsIndicator.setText(addonText.toString());
            } else {
                tvAddonsIndicator.setVisibility(View.GONE);
            }

            // Update item price display
            double addonsTotal = calculateAddonsTotal(newAddons);
            double itemTotal = (item.price * item.quantity) + addonsTotal;
            tvItemPrice.setText("₱" + String.format("%.2f", itemTotal));

            updateTotalAmount();
            Toast.makeText(getContext(), "Add-ons saved for " + item.name, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    private void updateHardCodedAddonsTotal(Map<String, Integer> selectedAddonCounts,
                                            List<AddonItem> categoryAddons,
                                            TextView tvAddonsTotal,
                                            double baseItemTotal,
                                            TextView tvCurrentItemTotal) {
        double total = 0;
        for (Map.Entry<String, Integer> entry : selectedAddonCounts.entrySet()) {
            String addonId = entry.getKey();
            int quantity = entry.getValue();

            // Find addon by ID
            for (AddonItem addon : categoryAddons) {
                if (addon.id.equals(addonId)) {
                    total += addon.price * quantity;
                    break;
                }
            }
        }

        tvAddonsTotal.setText("Add-ons: ₱" + String.format("%.2f", total));
        tvCurrentItemTotal.setText("Item Total: ₱" + String.format("%.2f", baseItemTotal + total));
    }

    private double calculateAddonsTotal(List<AddonSelection> addons) {
        double total = 0;
        if (addons != null) {
            for (AddonSelection addon : addons) {
                total += addon.price * addon.quantity;
            }
        }
        return total;
    }

    private void updateCartItemQuantity(String key, int newQuantity,
                                        TextView tvQty, TextView tvPrice,
                                        CartFoodItem item, List<AddonSelection> addons) {
        tvQty.setText(String.valueOf(newQuantity));

        // Update quantity in Firebase
        cartRef.child(key).child("quantity").setValue(newQuantity);

        // Update local item
        for (int i = 0; i < cartItems.size(); i++) {
            if (cartKeys.get(i).equals(key)) {
                cartItems.get(i).quantity = newQuantity;
                break;
            }
        }

        // Update price display
        double addonsTotal = calculateAddonsTotal(addons);
        double itemTotal = (item.price * newQuantity) + addonsTotal;
        tvPrice.setText("₱" + String.format("%.2f", itemTotal));

        updateTotalAmount();
    }

    private void updateTotalAmount() {
        double total = calculateTotal();
        tvTotalAmount.setText("₱" + String.format("%.2f", total));
    }

    private double calculateTotal() {
        double total = 0;

        for (int i = 0; i < cartItems.size(); i++) {
            CartFoodItem item = cartItems.get(i);
            String key = cartKeys.get(i);

            double itemTotal = item.price * item.quantity;

            // Add addons for this item
            List<AddonSelection> addons = itemAddonsMap.get(key);
            if (addons != null) {
                itemTotal += calculateAddonsTotal(addons);
            }

            total += itemTotal;
        }

        return total;
    }

    private int getTotalCartItems() {
        int total = 0;
        for (CartFoodItem item : cartItems) {
            total += item.quantity;
        }
        return total;
    }

    private Bitmap base64ToBitmap(String base64) {
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    // ===== INNER CLASSES =====

    // AddonItem class (NO LONGER NEEDS CATEGORY FIELD)
    public static class AddonItem {
        public String id;
        public String name;
        public double price;
        public int limit; // 0 means no limit

        public AddonItem() {}

        public AddonItem(String id, String name, double price, int limit) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.limit = limit;
        }
    }

    public static class AddonSelection {
        public String addonId;
        public String name;
        public double price;
        public int quantity;

        public AddonSelection() {}

        public AddonSelection(String addonId, String name, double price, int quantity) {
            this.addonId = addonId;
            this.name = name;
            this.price = price;
            this.quantity = quantity;
        }
    }

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