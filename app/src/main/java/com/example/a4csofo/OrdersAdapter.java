package com.example.a4csofo;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

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

        // Total price - use getFormattedTotalForDisplay() instead of getFormattedTotal()
        holder.txtTotal.setText(order.getFormattedTotalForDisplay());

        // Payment method
        holder.txtPayment.setText(order.getPayment_method());

        // Status
        holder.txtStatus.setText(order.getStatus() != null ? order.getStatus() : "Pending");

        // Items - use getItemsAsStringForDisplay() instead of getItemsAsString()
        holder.txtItems.setText(order.getItemsAsStringForDisplay());

        // Pickup info - use isPickupOrder() instead of isPickup()
        if (order.isPickupOrder()) {
            holder.txtPickupInfo.setVisibility(View.VISIBLE);
            String pickupInfo = "";
            if (order.getPickupBranch() != null && !order.getPickupBranch().isEmpty()) {
                pickupInfo = order.getPickupBranch();
            }
            if (order.getPickupTime() != null && !order.getPickupTime().isEmpty()) {
                if (!pickupInfo.isEmpty()) pickupInfo += " - ";
                pickupInfo += order.getPickupTime();
            }
            holder.txtPickupInfo.setText(pickupInfo);
        } else {
            holder.txtPickupInfo.setVisibility(View.GONE);
        }

        // GCash info - use isGcash() (this method exists)
        if (order.isGcash()) {
            holder.txtGcashInfo.setVisibility(View.VISIBLE);
            String gcashInfo = "";
            if (order.getGcashReferenceNumber() != null && !order.getGcashReferenceNumber().isEmpty()) {
                gcashInfo = "Ref: " + order.getGcashReferenceNumber();
            }
            holder.txtGcashInfo.setText(gcashInfo);
            holder.qrImage.setVisibility(View.VISIBLE);
        } else {
            holder.txtGcashInfo.setVisibility(View.GONE);
            holder.qrImage.setVisibility(View.GONE);
        }

        // Order date
        if (order.getOrderDate() > 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
            String formattedDate = sdf.format(new Date(order.getOrderDate()));
            holder.txtOrderDate.setText(formattedDate);
        } else {
            holder.txtOrderDate.setText("Date not available");
        }

        // Transaction number
        String transactionNum = order.getOrderKey();
        if (transactionNum != null && transactionNum.length() > 8) {
            transactionNum = transactionNum.substring(0, 8);
        }
        holder.txtTransaction.setText("TRX: " + (transactionNum != null ? transactionNum : "N/A"));

        // Delivery location
        if (order.getDeliveryLocation() != null && !order.getDeliveryLocation().isEmpty()) {
            holder.txtDeliveryLocation.setText("📍 " + order.getDeliveryLocation());
            holder.txtDeliveryLocation.setVisibility(View.VISIBLE);
        } else {
            holder.txtDeliveryLocation.setVisibility(View.GONE);
        }

        // Click listener for delivering orders
        holder.itemView.setOnClickListener(v -> {
            if ("delivering".equalsIgnoreCase(order.getStatus()) ||
                    "out for delivery".equalsIgnoreCase(order.getStatus()) ||
                    "on the way".equalsIgnoreCase(order.getStatus())) {

                // Check if we have delivery location coordinates
                if (order.hasDeliveryLocation()) {
                    String geoUri = "geo:" + order.getDeliveryLat() + "," + order.getDeliveryLng() + "?q=" +
                            order.getDeliveryLat() + "," + order.getDeliveryLng() + "(Delivery+Location)";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(geoUri));
                    intent.setPackage("com.google.android.apps.maps");

                    if (intent.resolveActivity(context.getPackageManager()) != null) {
                        context.startActivity(intent);
                    } else {
                        // Fallback to web Google Maps
                        String webUri = "https://www.google.com/maps/search/?api=1&query=" +
                                order.getDeliveryLat() + "," + order.getDeliveryLng();
                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(webUri)));
                    }
                } else {
                    Toast.makeText(context, "Delivery location coordinates not available", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    public void updateOrders(List<OrderModel> newOrders) {
        orderList = newOrders;
        notifyDataSetChanged();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView txtCustomer, txtTotal, txtPayment, txtStatus, txtTransaction,
                txtItems, txtPickupInfo, txtGcashInfo, txtOrderDate, txtDeliveryLocation;
        ImageView qrImage;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            txtCustomer = itemView.findViewById(R.id.txtCustomer);
            txtTotal = itemView.findViewById(R.id.txtTotal);
            txtPayment = itemView.findViewById(R.id.txtPayment);
            txtStatus = itemView.findViewById(R.id.txtStatus);
            txtTransaction = itemView.findViewById(R.id.txtTransaction);
            txtItems = itemView.findViewById(R.id.txtItems);
            txtPickupInfo = itemView.findViewById(R.id.txtPickupInfo);
            txtGcashInfo = itemView.findViewById(R.id.txtGcashInfo);
            qrImage = itemView.findViewById(R.id.qrImage);
            txtOrderDate = itemView.findViewById(R.id.txtOrderDate);

            // Check if txtDeliveryLocation exists in layout
            txtDeliveryLocation = itemView.findViewById(R.id.txtDeliveryLocation);

            // If it doesn't exist, create a placeholder
            if (txtDeliveryLocation == null) {
                // You can create a new TextView programmatically if needed
                // Or just ignore it and don't use it
            }
        }
    }
}