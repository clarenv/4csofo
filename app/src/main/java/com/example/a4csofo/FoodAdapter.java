package com.example.a4csofo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class FoodAdapter extends RecyclerView.Adapter<FoodAdapter.FoodViewHolder> {

    private Context context;
    private List<MenuItemsActivity.FoodItem> foodList;

    public FoodAdapter(Context context, List<MenuItemsActivity.FoodItem> foodList) {
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
        MenuItemsActivity.FoodItem food = foodList.get(position);

        holder.tvName.setText(food.name + " - ₱" + food.price);
        holder.tvDesc.setText(food.description + " (" + food.prepTime + " mins, " + food.category + ")");

        // Load image using Glide
        if (food.imageUrl != null && !food.imageUrl.isEmpty()) {
            Glide.with(context)
                    .load(food.imageUrl)
                    .placeholder(R.drawable.ic_placeholder) // Add a placeholder drawable
                    .into(holder.ivFoodImage);
        } else {
            holder.ivFoodImage.setImageResource(R.drawable.ic_placeholder);
        }

        holder.btnAddCart.setOnClickListener(v -> {
            Toast.makeText(context, food.name + " added to cart!", Toast.LENGTH_SHORT).show();
            // TODO: Add actual cart functionality
        });
    }

    @Override
    public int getItemCount() {
        return foodList.size();
    }

    public static class FoodViewHolder extends RecyclerView.ViewHolder {

        ImageView ivFoodImage;
        TextView tvName, tvDesc;
        Button btnAddCart;

        public FoodViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFoodImage = itemView.findViewById(R.id.ivFoodImage);
            tvName = itemView.findViewById(R.id.tvFoodName);
            tvDesc = itemView.findViewById(R.id.tvFoodDesc);
            btnAddCart = itemView.findViewById(R.id.btnAddCart);
        }
    }
}
