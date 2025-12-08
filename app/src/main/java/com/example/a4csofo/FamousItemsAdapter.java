package com.example.a4csofo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;

public class FamousItemsAdapter extends RecyclerView.Adapter<FamousItemsAdapter.FamousViewHolder> {

    private final Context context;
    private final ArrayList<FamousItem> famousList;

    public FamousItemsAdapter(Context context, ArrayList<FamousItem> famousList) {
        this.context = context;
        this.famousList = famousList;
    }

    @NonNull
    @Override
    public FamousViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_famous, parent, false);
        return new FamousViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FamousViewHolder holder, int position) {
        FamousItem item = famousList.get(position);
        holder.tvName.setText(item.name);
        holder.tvDesc.setText(item.description);
        holder.tvPrice.setText("₱" + String.format("%.2f", item.price));
        holder.tvSales.setText("Sold Today: " + item.sales);

        // Optional: Card click listener
        holder.cardFamous.setOnClickListener(v -> {
            // Example: show Toast
            android.widget.Toast.makeText(context, item.name + " clicked!", android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return famousList.size();
    }

    static class FamousViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardFamous;
        TextView tvName, tvDesc, tvPrice, tvSales;

        public FamousViewHolder(@NonNull View itemView) {
            super(itemView);
            cardFamous = itemView.findViewById(R.id.cardFamous);
            tvName = itemView.findViewById(R.id.tvFamousName);
            tvDesc = itemView.findViewById(R.id.tvFamousDesc);
            tvPrice = itemView.findViewById(R.id.tvFamousPrice);
            tvSales = itemView.findViewById(R.id.tvFamousSales);
        }
    }
}
