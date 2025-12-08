package com.example.a4csofo;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class OrdersAdapter extends RecyclerView.Adapter<OrdersAdapter.OrderViewHolder> {

    private Context context;
    private List<OrderModel> orderList;
    private OnOrderClickListener listener;
    private int expandedPosition = -1;

    public interface OnOrderClickListener {
        void onOrderClick(OrderModel order);
        void onViewReceiptClick(OrderModel order);
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.listener = listener;
    }

    public OrdersAdapter(Context context, List<OrderModel> orderList) {
        this.context = context;
        this.orderList = new ArrayList<>(orderList);
        sortByLatest();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, @SuppressLint("RecyclerView") int position) {
        OrderModel order = orderList.get(position);
        if (order == null) return;

        // Calculate order number (reverse position since sorted by latest)
        int orderNumber = getItemCount() - position;
        holder.bind(order, orderNumber);

        // Expand/collapse
        boolean isExpanded = position == expandedPosition;
        holder.layoutExpandContent.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        if (holder.imgExpandArrow != null) {
            holder.imgExpandArrow.setRotation(isExpanded ? 180f : 0f);
        }

        // Click to expand/collapse
        holder.itemView.setOnClickListener(v -> {
            int previous = expandedPosition;

            if (isExpanded) {
                expandedPosition = -1;
            } else {
                expandedPosition = position;
            }

            if (previous != -1) {
                notifyItemChanged(previous);
            }
            notifyItemChanged(position);

            if (listener != null) {
                listener.onOrderClick(order);
            }
        });

        // View Receipt button
        if (holder.btnViewReceipt != null) {
            holder.btnViewReceipt.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onViewReceiptClick(order);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    public void sortByLatest() {
        Collections.sort(orderList, new Comparator<OrderModel>() {
            @Override
            public int compare(OrderModel o1, OrderModel o2) {
                return Long.compare(o2.getOrderDate(), o1.getOrderDate());
            }
        });
        notifyDataSetChanged();
    }

    public void updateOrders(List<OrderModel> newOrders) {
        this.orderList = new ArrayList<>(newOrders);
        sortByLatest();
        expandedPosition = -1;
        notifyDataSetChanged();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        // Header views
        TextView txtOrderNumber, txtOrderDate, txtStatus, txtTotal;

        // Expandable content views
        LinearLayout layoutExpandContent;
        TextView txtPayment, txtItems;
        ImageView imgExpandArrow;
        Button btnViewReceipt;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);

            // Header views
            txtOrderNumber = itemView.findViewById(R.id.txtOrderId); // Now shows Order #1, #2, etc.
            txtTotal = itemView.findViewById(R.id.txtTotal);
            txtOrderDate = itemView.findViewById(R.id.txtOrderDate);
            txtStatus = itemView.findViewById(R.id.txtStatus);

            // Expandable content
            layoutExpandContent = itemView.findViewById(R.id.layoutExpandContent);
            txtPayment = itemView.findViewById(R.id.txtPayment);
            txtItems = itemView.findViewById(R.id.txtItems);

            // Optional views
            try {
                imgExpandArrow = itemView.findViewById(R.id.imgExpandArrow);
            } catch (Exception e) {
                imgExpandArrow = null;
            }

            try {
                btnViewReceipt = itemView.findViewById(R.id.btnViewReceipt);
            } catch (Exception e) {
                btnViewReceipt = null;
            }
        }

        public void bind(OrderModel order, int orderNumber) {
            // Display Order #1, #2, #3, etc. (latest order = highest number)
            txtOrderNumber.setText(String.format(Locale.getDefault(), "Order #%d", orderNumber));

            // Total price
            txtTotal.setText(order.getFormattedTotalForDisplay());

            // Status with color
            String status = order.getStatus() != null ? order.getStatus() : "Pending";
            txtStatus.setText(status);
            txtStatus.setTextColor(getStatusColor(status));

            // Order date
            if (order.getOrderDate() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                String date = sdf.format(new Date(order.getOrderDate()));
                txtOrderDate.setText(date);
            } else {
                txtOrderDate.setText("Date N/A");
            }

            // Expandable content
            txtPayment.setText(order.getPayment_method());
            txtItems.setText(order.getItemsAsStringForDisplay());
        }

        private int getStatusColor(String status) {
            switch (status.toLowerCase()) {
                case "pending": return 0xFFFF9800; // Orange
                case "accepted": case "verifying": return 0xFF2196F3; // Blue
                case "preparing": return 0xFF9C27B0; // Purple
                case "ready": return 0xFF4CAF50; // Green
                case "delivering": return 0xFF00BCD4; // Cyan
                case "completed": return 0xFF8BC34A; // Light Green
                case "cancelled": return 0xFFF44336; // Red
                default: return 0xFF757575; // Gray
            }
        }
    }
}