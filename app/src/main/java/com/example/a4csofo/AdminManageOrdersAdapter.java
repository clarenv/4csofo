package com.example.a4csofo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;

public class AdminManageOrdersAdapter extends RecyclerView.Adapter<AdminManageOrdersAdapter.OrderViewHolder> {

    private final Context context;
    private final ArrayList<OrderModel> orderList;
    private final AdminOrdersFragment fragment;

    public AdminManageOrdersAdapter(Context context, ArrayList<OrderModel> orderList, AdminOrdersFragment fragment) {
        this.context = context;
        this.orderList = orderList;
        this.fragment = fragment;
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manage_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        OrderModel order = orderList.get(position);

        // Basic info
        holder.tvOrderId.setText(order.getOrderKey() != null ? "ORD-" + order.getOrderKey() : "ORD-N/A");
        holder.tvCustomer.setText(order.getCustomerName());
        holder.tvTotalPrice.setText(order.getTotal());
        holder.tvPayment.setText("Payment: " + order.getPayment_method());

        // Status and progress dots
        holder.tvStatus.setText("Status: " + order.getStatus());
        holder.tvProgress.setText(getProgressDots(order));

        // Update button
        holder.btnUpdateStatus.setOnClickListener(v -> {
            String nextStatus = getNextStatus(order);
            if (nextStatus != null) {
                order.setStatus(nextStatus);
                fragment.updateOrderStatus(order, nextStatus);

                // Update UI
                holder.tvStatus.setText("Status: " + nextStatus);
                holder.tvProgress.setText(getProgressDots(order));
                Toast.makeText(context, "Order updated to " + nextStatus, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "Order already completed", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    // Determine next status
    private String getNextStatus(OrderModel order) {
        String[] flow = getStatusFlow(order);
        for (int i = 0; i < flow.length; i++) {
            if (flow[i].equalsIgnoreCase(order.getStatus()) && i < flow.length - 1) {
                return flow[i + 1];
            }
        }
        return null;
    }

    // Status flow based on type & payment
    private String[] getStatusFlow(OrderModel order) {
        if ("pickup".equalsIgnoreCase(order.getOrderType())) {
            return new String[]{"Pending", "Preparing", "Ready for Pickup", "Completed"};
        } else {
            if ("GCash".equalsIgnoreCase(order.getPayment_method())) {
                return new String[]{"Pending", "Verifying Payment", "Preparing", "Out for Delivery", "Completed"};
            } else {
                // COD delivery
                return new String[]{"Pending", "Preparing", "Out for Delivery", "Completed"};
            }
        }
    }

    // Convert status to progress dots
    private String getProgressDots(OrderModel order) {
        String[] flow = getStatusFlow(order);
        StringBuilder sb = new StringBuilder();
        boolean filled = true;
        for (String status : flow) {
            if (status.equalsIgnoreCase(order.getStatus())) {
                sb.append("●"); // Current
                filled = false;
            } else if (filled) {
                sb.append("●"); // Completed
            } else {
                sb.append("○"); // Pending
            }
        }
        return sb.toString();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvCustomer, tvTotalPrice, tvPayment, tvStatus, tvProgress;
        Button btnUpdateStatus;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
            tvPayment = itemView.findViewById(R.id.tvPayment);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            btnUpdateStatus = itemView.findViewById(R.id.btnUpdateStatus);
        }
    }
}
