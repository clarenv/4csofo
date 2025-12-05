package com.example.a4csofo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AdminFoodAdapter extends RecyclerView.Adapter<AdminFoodAdapter.FoodViewHolder> {

    private final Context context;
    private final List<AdminCategoriesFragment.ItemAdminCategory> foodList;

    public AdminFoodAdapter(Context context, List<AdminCategoriesFragment.ItemAdminCategory> foodList) {
        this.context = context;
        this.foodList = foodList;
    }

    @NonNull
    @Override
    public FoodViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_admin_category, parent, false);
        return new FoodViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FoodViewHolder holder, int position) {
        AdminCategoriesFragment.ItemAdminCategory food = foodList.get(position);

        holder.tvName.setText(food.getName() != null ? food.getName() : "Unknown");
        holder.tvCategory.setText(food.getCategory() != null ? food.getCategory() : "Uncategorized");
        holder.tvPrice.setText("₱" + String.format("%.2f", food.getPrice()));
        holder.tvPrepTime.setText(food.getPrepTime() != null ? food.getPrepTime() + " mins" : "N/A");
        holder.tvDesc.setText(food.getDescription() != null ? food.getDescription() : "No description");

        StringBuilder extrasBuilder = new StringBuilder();
        if (food.getAddOns() != null && !food.getAddOns().isEmpty()) {
            extrasBuilder.append("Add-ons: ").append(String.join(", ", food.getAddOns())).append("\n");
        }
        if (food.getIngredients() != null && !food.getIngredients().isEmpty()) {
            extrasBuilder.append("Ingredients: ").append(String.join(", ", food.getIngredients())).append("\n");
        }
        if (food.getServingSize() != null && !food.getServingSize().isEmpty()) {
            extrasBuilder.append("Serving Size: ").append(food.getServingSize()).append("\n");
        }
        extrasBuilder.append("Calories: ").append(food.getCalories());
        holder.tvExtras.setText(extrasBuilder.toString());

        if (food.getImageBase64() != null && !food.getImageBase64().isEmpty()) {
            try {
                byte[] decodedBytes = Base64.decode(food.getImageBase64(), Base64.DEFAULT);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
                holder.ivFoodImage.setImageBitmap(bitmap);
            } catch (Exception e) {
                holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
            }
        } else {
            holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
        }

        holder.switchAvailable.setChecked(food.isAvailable());
        holder.switchAvailable.setOnCheckedChangeListener((buttonView, isChecked) -> {
            food.setAvailable(isChecked);
        });
    }

    @Override
    public int getItemCount() {
        return foodList != null ? foodList.size() : 0;
    }

    public static class FoodViewHolder extends RecyclerView.ViewHolder {
        ImageView ivFoodImage;
        TextView tvName, tvPrice, tvDesc, tvCategory, tvPrepTime, tvExtras;
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
            switchAvailable = itemView.findViewById(R.id.switchAvailable);
        }
    }
}