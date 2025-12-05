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

    private static final ArrayList<String> STATUS_FLOW = new ArrayList<String>() {{
        add("Pending");
        add("Preparing");
        add("Delivering");
        add("Delivered");
    }};
    private static final String STATUS_CANCELLED = "Cancelled";

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

        // Generate spinner options for this order
        ArrayList<String> spinnerOptions = getNextStatusOptions(order.getStatus());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, spinnerOptions);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        holder.spinnerStatus.setAdapter(spinnerAdapter);

        // Set spinner selection to first option by default
        holder.spinnerStatus.setSelection(0);

        holder.btnUpdateStatus.setOnClickListener(v -> {
            Object selectedItem = holder.spinnerStatus.getSelectedItem();
            if (selectedItem == null) {
                Toast.makeText(context, "Please select a status", Toast.LENGTH_SHORT).show();
                return;
            }

            String selectedStatus = selectedItem.toString();

            // Prevent updating if already Delivered or Cancelled
            if ("Delivered".equals(order.getStatus()) || "Cancelled".equals(order.getStatus())) {
                Toast.makeText(context, "Order is already " + order.getStatus(), Toast.LENGTH_SHORT).show();
                return;
            }

            if (!selectedStatus.equals(order.getStatus())) {
                // Update via fragment method; UI will refresh after Firebase success
                fragment.updateOrderStatus(order, selectedStatus);
            } else {
                Toast.makeText(context, "No changes to update", Toast.LENGTH_SHORT).show();
            }
        });

        holder.itemView.setOnClickListener(v -> {
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

    /**
     * Generate next valid status options for the spinner.
     * Shows all remaining forward statuses + Cancel option.
     * Delivered/Cancelled orders show only the current status.
     */
    private ArrayList<String> getNextStatusOptions(String currentStatus) {
        ArrayList<String> options = new ArrayList<>();

        if ("Delivered".equalsIgnoreCase(currentStatus) || "Cancelled".equalsIgnoreCase(currentStatus)) {
            options.add(currentStatus);
            return options;
        }

        int currentIndex = STATUS_FLOW.indexOf(currentStatus);
        if (currentIndex == -1) currentIndex = 0;

        // Add all remaining statuses forward
        for (int i = currentIndex + 1; i < STATUS_FLOW.size(); i++) {
            options.add(STATUS_FLOW.get(i));
        }

        // Add Cancel option
        options.add(STATUS_CANCELLED);

        return options;
    }
}
