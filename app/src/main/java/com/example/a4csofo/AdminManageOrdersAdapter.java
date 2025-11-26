package com.example.a4csofo;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
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

        holder.tvOrderId.setText(order.getOrderKey() != null ? "ORD-" + order.getOrderKey() : "ORD-N/A");
        holder.tvCustomer.setText(order.getCustomerName());
        holder.tvTotalPrice.setText(order.getTotal());
        holder.tvPayment.setText("Payment: " + order.getPaymentMethod());
        holder.tvStatus.setText("Status: " + order.getStatus());

        // Setup spinner
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                context,
                R.array.order_status_array,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerStatus.setAdapter(spinnerAdapter);

        // Set spinner to current status
        int spinnerPosition = spinnerAdapter.getPosition(order.getStatus());
        holder.spinnerStatus.setSelection(spinnerPosition);

        // Update button logic
        holder.btnUpdateStatus.setOnClickListener(v -> {
            String selectedStatus = holder.spinnerStatus.getSelectedItem().toString();

            if (order.getOrderKey() != null && !selectedStatus.equals(order.getStatus())) {
                order.setStatus(selectedStatus); // local update
                fragment.updateOrderStatus(order, selectedStatus); // Firebase update
                holder.tvStatus.setText("Status: " + selectedStatus);
                Toast.makeText(context, "Order status updated to " + selectedStatus, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, "No changes to update", Toast.LENGTH_SHORT).show();
            }
        });

        // Optional: item click to open details page
        holder.itemView.setOnClickListener(v -> {
            // TODO: Open OrderDetailsFragment or Activity
            Toast.makeText(context, "Click to view order details", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public int getItemCount() {
        return orderList.size();
    }

    static class OrderViewHolder extends RecyclerView.ViewHolder {

        TextView tvOrderId, tvCustomer, tvTotalPrice, tvPayment, tvStatus;
        Spinner spinnerStatus;
        Button btnUpdateStatus;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
            tvPayment = itemView.findViewById(R.id.tvPayment);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            spinnerStatus = itemView.findViewById(R.id.spinnerStatus);
            btnUpdateStatus = itemView.findViewById(R.id.btnUpdateStatus);
        }
    }
}
