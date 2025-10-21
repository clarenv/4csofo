package com.example.a4csofo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {

    private Context context;
    private List<OrderModel> orderList;

    public OrdersAdapter(Context context, List<OrderModel> orderList) {
        this.context = context;
        this.orderList = orderList;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        OrderModel order = orderList.get(position);
        if (order == null) return;

        // Customer name
        holder.txtCustomer.setText(order.getCustomer_name() != null ? order.getCustomer_name() : "Unknown");

        // Total price
        holder.txtTotal.setText("₱" + String.format("%.2f", order.getTotal_price()));

        // Payment method
        holder.txtPayment.setText(order.getPayment_method() != null ? order.getPayment_method() : "N/A");

        // Status
        holder.txtStatus.setText(order.getStatus() != null ? order.getStatus() : "Pending");

        // Transaction number
        holder.txtTransaction.setText(order.getTransaction_number() != null ? order.getTransaction_number() : "N/A");

        // Items
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            holder.txtItems.setText("Items: " + String.join(", ", order.getItems()));
        } else {
            holder.txtItems.setText("Items: N/A");
        }
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView txtCustomer, txtTotal, txtPayment, txtStatus, txtTransaction, txtItems;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCustomer = itemView.findViewById(R.id.txtCustomer);
            txtTotal = itemView.findViewById(R.id.txtTotal);
            txtPayment = itemView.findViewById(R.id.txtPayment);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtTransaction = itemView.findViewById(R.id.txtTransaction);
            txtItems = itemView.findViewById(R.id.txtItems);
        }
    }
}
