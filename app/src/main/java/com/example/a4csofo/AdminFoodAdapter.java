package com.example.a4csofo;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AdminFoodAdapter extends RecyclerView.Adapter<AdminFoodAdapter.FoodViewHolder> {

    private final Context context;
    private final List<AdminMenuItemsFragment.FoodItem> foodList;

    public AdminFoodAdapter(Context context, List<AdminMenuItemsFragment.FoodItem> foodList) {
        this.context = context;
        this.foodList = foodList;
    }

    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_food, parent, false);
        return new FoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        AdminMenuItemsFragment.FoodItem food = foodList.get(position);

        holder.tvName.setText(food.name);

        // Show discount price if available
        if (food.discountPrice > 0) {
            holder.tvPrice.setText("₱" + String.format("%.2f", food.discountPrice)
                    + " (Orig: ₱" + String.format("%.2f", food.price) + ")");
        } else {
            holder.tvPrice.setText("₱" + String.format("%.2f", food.price));
        }

        holder.tvDesc.setText(food.description);
        holder.tvCategory.setText(food.category);
        holder.tvPrepTime.setText(food.prepTime + " mins");

        // Optional: Show additional info
        StringBuilder extras = new StringBuilder();
        if (food.addOns != null && !food.addOns.isEmpty()) {
            extras.append("Add-ons: ").append(String.join(", ", food.addOns)).append("\n");
        }
        if (food.ingredients != null && !food.ingredients.isEmpty()) {
            extras.append("Ingredients: ").append(String.join(", ", food.ingredients)).append("\n");
        }
        if (food.servingSize != null && !food.servingSize.isEmpty()) {
            extras.append("Serving: ").append(food.servingSize).append("\n");
        }
        if (food.calories > 0) {
            extras.append("Calories: ").append(food.calories).append(" kcal\n");
        }
        extras.append(food.inStock ? "In Stock" : "Out of Stock");
        holder.tvExtras.setText(extras.toString());

        // Set image
        Bitmap bitmap = food.getBitmap();
        if (bitmap != null) {
            holder.ivFoodImage.setImageBitmap(bitmap);
        } else {
            holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
        }

        // Optional click action
        holder.btnAddCart.setOnClickListener(v ->
                Toast.makeText(context, food.name + " added to cart!", Toast.LENGTH_SHORT).show()
        );
    }

    @Override
    public int getItemCount() {
        return foodList.size();
    }

    public static class FoodViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFoodImage;
        TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime, tvExtras;
        Button btnAddCart;

        public FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
            tvName = itemView.findViewById(R.id.tvFoodName);
            tvPrice = itemView.findViewById(R.id.tvFoodPrice);
            tvDesc = itemView.findViewById(R.id.tvFoodDesc);
            tvCategory = itemView.findViewById(R.id.tvFoodCategory);
            tvPrepTime = itemView.findViewById(R.id.tvFoodPrepTime);
            tvExtras = itemView.findViewById(R.id.tvFoodExtras); // new TextView for extra fields
            btnAddCart = itemView.findViewById(R.id.btnAddCart);
        }
    }
}
