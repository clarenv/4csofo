package com.example.a4csofo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

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
        holder.txtCustomer.setText(order.getCustomerName());

        // Total price
        holder.txtTotal.setText(order.getFormattedTotal());

        // Payment method
        holder.txtPayment.setText(order.getPaymentText());

        // Status
        holder.txtStatus.setText(order.getStatus() != null ? order.getStatus() : "Pending");

        // Transaction number
        holder.txtTransaction.setText(order.getTransactionNumber());

        // Items
        holder.txtItems.setText("Items: " + order.getItemsAsString());

        // Pickup info
        if ("pickup".equals(order.getOrderType())) {
            holder.txtPickupInfo.setVisibility(View.VISIBLE);
            holder.txtPickupInfo.setText("Pickup: " + order.getPickupBranch() + " at " + order.getPickupTime());
        } else {
            holder.txtPickupInfo.setVisibility(View.GONE);
        }

        // GCash info
        if ("GCash".equalsIgnoreCase(order.getPaymentMethod())) {
            holder.txtGcashInfo.setVisibility(View.VISIBLE);
            holder.txtGcashInfo.setText("GCash Ref: " + order.getGcashReferenceNumber());
        } else {
            holder.txtGcashInfo.setVisibility(View.GONE);
        }

        // 🔥 CLICK LISTENER FOR DELIVERING ORDERS TO OPEN MAP
        holder.itemView.setOnClickListener(v -> {
            if ("delivering".equalsIgnoreCase(order.getStatus())) {
                double lat = order.getDeliveryLat();
                double lng = order.getDeliveryLng();
                if (lat != 0 && lng != 0) {
                    String geoUri = "geo:" + lat + "," + lng + "?q=" + lat + "," + lng + "(Your+Order)";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
                    intent.setPackage("com.google.android.apps.maps");
                    context.startActivity(intent);
                } else {
                    Toast.makeText(context, "Delivery location not available", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView txtCustomer, txtTotal, txtPayment, txtStatus, txtTransaction, txtItems, txtPickupInfo, txtGcashInfo;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCustomer = itemView.findViewById(R.id.txtCustomer);
            txtTotal = itemView.findViewById(R.id.txtTotal);
            txtPayment = itemView.findViewById(R.id.txtPayment);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtTransaction = itemView.findViewById(R.id.txtTransaction);
            txtItems = itemView.findViewById(R.id.txtItems);

            // NEW FIELDS
            txtPickupInfo = itemView.findViewById(R.id.txtPickupInfo);
            txtGcashInfo = itemView.findViewById(R.id.txtGcashInfo);
        }
    }
}
