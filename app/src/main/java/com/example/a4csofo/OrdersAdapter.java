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

    private Context Context;
    private List<OrderModel> OrderList;
    private OnOrderClickListener Listener;
    private int ExpandedPosition = -1;

    // Listener interface
    public interface OnOrderClickListener {
        void onOrderClick(OrderModel order);
        void onViewReceiptClick(OrderModel order);
        void onRateFoodClick(OrderModel order);
        void onCancelOrderClick(OrderModel order);
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.Listener = listener;
    }

    public OrdersAdapter(Context context, List<OrderModel> orderList) {
        this.Context = context;
        this.OrderList = new ArrayList<>(orderList);
        sortByLatest();
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(Context).inflate(R.layout.item_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, @SuppressLint("RecyclerView") int position) {
        OrderModel Order = OrderList.get(position);
        if (Order == null) return;

        int OrderNumber = getItemCount() - position;
        holder.bind(Order, OrderNumber);

        boolean IsExpanded = position == ExpandedPosition;
        holder.LayoutExpandContent.setVisibility(IsExpanded ? View.VISIBLE : View.GONE);
        if (holder.ImgExpandArrow != null) {
            holder.ImgExpandArrow.setRotation(IsExpanded ? 180f : 0f);
        }

        // Expand/collapse click
        holder.itemView.setOnClickListener(v -> {
            int Previous = ExpandedPosition;
            if (IsExpanded) {
                ExpandedPosition = -1;
            } else {
                ExpandedPosition = position;
            }
            if (Previous != -1) notifyItemChanged(Previous);
            notifyItemChanged(position);

            if (Listener != null) Listener.onOrderClick(Order);
        });

        // View Receipt button
        if (holder.BtnViewReceipt != null) {
            holder.BtnViewReceipt.setOnClickListener(v -> {
                if (Listener != null) Listener.onViewReceiptClick(Order);
            });
        }

        // Rate Food button - only visible if order is completed
        if (holder.BtnRateFood != null) {
            if ("completed".equalsIgnoreCase(Order.getStatus()) &&
                    Order.getItems() != null && !Order.getItems().isEmpty()) {
                holder.BtnRateFood.setVisibility(View.VISIBLE);
            } else {
                holder.BtnRateFood.setVisibility(View.GONE);
            }
            holder.BtnRateFood.setOnClickListener(v -> {
                if (Listener != null) Listener.onRateFoodClick(Order);
            });
        }

        // Cancel Order button - only visible if Pending
        if (holder.BtnCancelOrder != null) {
            if ("pending".equalsIgnoreCase(Order.getStatus())) {
                holder.BtnCancelOrder.setVisibility(View.VISIBLE);
            } else {
                holder.BtnCancelOrder.setVisibility(View.GONE);
            }
            holder.BtnCancelOrder.setOnClickListener(v -> {
                if (Listener != null) Listener.onCancelOrderClick(Order);
            });
        }
    }

    @Override
    public int getItemCount() {
        return OrderList != null ? OrderList.size() : 0;
    }

    public void sortByLatest() {
        Collections.sort(OrderList, (O1, O2) -> Long.compare(O2.getOrderDate(), O1.getOrderDate()));
        notifyDataSetChanged();
    }

    public void updateOrders(List<OrderModel> NewOrders) {
        this.OrderList = new ArrayList<>(NewOrders);
        sortByLatest();
        ExpandedPosition = -1;
        notifyDataSetChanged();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView TxtOrderNumber, TxtOrderDate, TxtStatus, TxtTotal;
        LinearLayout LayoutExpandContent;
        TextView TxtPayment, TxtItems;
        ImageView ImgExpandArrow;
        Button BtnViewReceipt, BtnRateFood, BtnCancelOrder;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);

            TxtOrderNumber = itemView.findViewById(R.id.txtOrderId);
            TxtTotal = itemView.findViewById(R.id.txtTotal);
            TxtOrderDate = itemView.findViewById(R.id.txtOrderDate);
            TxtStatus = itemView.findViewById(R.id.txtStatus);

            LayoutExpandContent = itemView.findViewById(R.id.layoutExpandContent);
            TxtPayment = itemView.findViewById(R.id.txtPayment);
            TxtItems = itemView.findViewById(R.id.txtItems);

            ImgExpandArrow = itemView.findViewById(R.id.imgExpandArrow);
            BtnViewReceipt = itemView.findViewById(R.id.btnViewReceipt);
            BtnRateFood = itemView.findViewById(R.id.btnRateFood);
            BtnCancelOrder = itemView.findViewById(R.id.btnCancelOrder);
        }

        public void bind(OrderModel order, int orderNumber) {
            TxtOrderNumber.setText(String.format(Locale.getDefault(), "Order #%d", orderNumber));
            TxtTotal.setText(order.getFormattedTotalForDisplay());

            String Status = order.getStatus() != null ? order.getStatus() : "Pending";
            TxtStatus.setText(Status);
            TxtStatus.setTextColor(getStatusColor(Status));

            if (order.getOrderDate() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                TxtOrderDate.setText(sdf.format(new Date(order.getOrderDate())));
            } else {
                TxtOrderDate.setText("Date N/A");
            }

            TxtPayment.setText(order.getPayment_method());
            TxtItems.setText(order.getItemsAsStringForDisplay());
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
