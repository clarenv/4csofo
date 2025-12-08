package com.example.a4csofo;

import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminManageOrdersAdapter extends RecyclerView.Adapter<AdminManageOrdersAdapter.OrderViewHolder> {

    private final Context context;
    private List<OrderModel> orderList;
    private final OrderStatusListener statusListener;
    private Map<String, String> customerNameMap = new HashMap<>();
    private OnOrderClickListener orderClickListener;

    private static final String TAG = "AdminOrdersAdapter";

    // Status Colors
    private static final int COLOR_PENDING = Color.parseColor("#FF9800");      // Orange
    private static final int COLOR_ACCEPTED = Color.parseColor("#2196F3");     // Blue
    private static final int COLOR_PREPARING = Color.parseColor("#9C27B0");    // Purple
    private static final int COLOR_READY = Color.parseColor("#4CAF50");        // Green
    private static final int COLOR_DELIVERING = Color.parseColor("#03A9F4");   // Light Blue
    private static final int COLOR_VERIFYING = Color.parseColor("#FFC107");    // Yellow
    private static final int COLOR_COMPLETED = Color.parseColor("#388E3C");    // Dark Green
    private static final int COLOR_CANCELLED = Color.parseColor("#F44336");    // Red

    // Order Type Colors
    private static final int COLOR_DELIVERY = Color.parseColor("#10A048");     // Green
    private static final int COLOR_PICKUP = Color.parseColor("#9C27B0");       // Purple

    public interface OnOrderClickListener {
        void onOrderClick(OrderModel order);
    }

    public interface OrderStatusListener {
        void onUpdateStatus(OrderModel order, String newStatus);
    }

    public AdminManageOrdersAdapter(Context context, List<OrderModel> orderList, OrderStatusListener statusListener) {
        this.context = context;
        this.orderList = orderList != null ? orderList : new ArrayList<>();
        this.statusListener = statusListener;
        Log.d(TAG, "Adapter created with " + this.orderList.size() + " items");
    }

    public void setOnOrderClickListener(OnOrderClickListener listener) {
        this.orderClickListener = listener;
    }

    // ===== CUSTOMER NAME LOADING =====
    public void updateCustomerNames(Map<String, String> customerNameMap) {
        this.customerNameMap = customerNameMap != null ? customerNameMap : new HashMap<>();
        notifyDataSetChanged();
        Log.d(TAG, "Customer names updated with " + customerNameMap.size() + " entries");
    }

    // Helper method: Get customer name
    private String getCustomerName(OrderModel order) {
        if (order == null) return "Customer";

        String customerUid = order.getCustomerUid();
        if (customerUid != null && customerNameMap.containsKey(customerUid)) {
            return customerNameMap.get(customerUid);
        }

        String customerName = order.getCustomerName();
        if (customerName != null && !customerName.trim().isEmpty()) {
            return customerName;
        }

        if (customerUid != null && customerUid.length() >= 6) {
            return "User " + customerUid.substring(0, 6);
        }

        return "Customer";
    }

    @NonNull
    @Override
    public OrderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_manage_order, parent, false);
        return new OrderViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrderViewHolder holder, int position) {
        if (position >= orderList.size()) return;

        OrderModel order = orderList.get(position);
        if (order == null) return;

        // ===== SIMPLE ORDER ID =====
        holder.tvOrderId.setText("Order #" + (position + 1));

        // ===== CUSTOMER NAME =====
        holder.tvCustomer.setText(getCustomerName(order));

        // ===== TOTAL PRICE =====
        String formattedTotal = order.getFormattedTotal();
        holder.tvTotalPrice.setText(formattedTotal != null && !formattedTotal.isEmpty()
                ? formattedTotal : "₱0.00");

        // ===== PAYMENT METHOD =====
        String paymentMethod = order.getPayment_method();
        holder.tvPayment.setText(paymentMethod != null ? paymentMethod.toUpperCase() : "COD");

        // ===== STATUS =====
        String currentStatus = order.getStatus() != null ? order.getStatus() : "Pending";
        holder.tvStatus.setText(currentStatus.toUpperCase());
        setStatusBadgeColor(holder.tvStatus, currentStatus);

        // ===== ORDER TYPE =====
        String orderType = order.getOrderType() != null ? order.getOrderType().toLowerCase() : "delivery";
        String displayOrderType = orderType.equals("pickup") ? "Pick-up" : "Delivery";

        // Set order type color and text
        int orderTypeColor = orderType.equals("pickup") ? COLOR_PICKUP : COLOR_DELIVERY;
        holder.tvOrderType.setText(displayOrderType);
        holder.tvOrderType.setTextColor(orderTypeColor);

        // ===== DELIVERY LOCATION (ONLY FOR DELIVERY ORDERS) =====
        if (orderType.equals("delivery")) {
            // SHOW LOCATION FOR DELIVERY ORDERS
            holder.locationSection.setVisibility(View.VISIBLE);

            String deliveryLocation = order.getDeliveryLocation();
            if (deliveryLocation != null && !deliveryLocation.isEmpty()) {
                // Truncate long addresses
                if (deliveryLocation.length() > 40) {
                    holder.tvLocation.setText(deliveryLocation.substring(0, 37) + "...");
                } else {
                    holder.tvLocation.setText(deliveryLocation);
                }
                holder.tvLocation.setTextColor(COLOR_DELIVERY);
            } else {
                holder.tvLocation.setText("No delivery address");
                holder.tvLocation.setTextColor(Color.parseColor("#757575"));
            }
        } else {
            // HIDE LOCATION FOR PICKUP ORDERS
            holder.locationSection.setVisibility(View.GONE);
        }

        // ===== PROGRESS DOTS =====
        updateProgressDots(holder, order);

        // ===== GCASH PROOF =====
        boolean isGcash = order.isGcash();
        holder.gcashSection.setVisibility(isGcash ? View.VISIBLE : View.GONE);
        if (isGcash) {
            holder.imgGcashProof.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        // ===== UPDATE BUTTON =====
        setupUpdateButton(holder, order, currentStatus);

        // ===== ORDER CLICK LISTENER =====
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (orderClickListener != null) {
                    orderClickListener.onOrderClick(order);
                }
            }
        });
    }

    private void setupUpdateButton(OrderViewHolder holder, OrderModel order, String currentStatus) {
        String nextStatus = getNextStatus(order);

        if (nextStatus != null) {
            holder.btnUpdateStatus.setText("Mark as " + nextStatus);
            holder.btnUpdateStatus.setVisibility(View.VISIBLE);
            setButtonColor(holder.btnUpdateStatus, nextStatus);

            holder.btnUpdateStatus.setOnClickListener(v -> {
                if (statusListener != null) {
                    // Update in Firebase via listener
                    statusListener.onUpdateStatus(order, nextStatus);

                    // Update UI immediately
                    order.setStatus(nextStatus);
                    holder.tvStatus.setText(nextStatus.toUpperCase());
                    setStatusBadgeColor(holder.tvStatus, nextStatus);
                    updateProgressDots(holder, order);

                    // Update button for next status
                    String newNextStatus = getNextStatus(order);
                    if (newNextStatus != null) {
                        holder.btnUpdateStatus.setText("Mark as " + newNextStatus);
                        setButtonColor(holder.btnUpdateStatus, newNextStatus);
                    } else {
                        holder.btnUpdateStatus.setText("Completed");
                        holder.btnUpdateStatus.setEnabled(false);
                        holder.btnUpdateStatus.setBackgroundColor(COLOR_COMPLETED);
                    }

                    Toast.makeText(context, "Updated to: " + nextStatus, Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            holder.btnUpdateStatus.setText("Completed");
            holder.btnUpdateStatus.setEnabled(false);
            holder.btnUpdateStatus.setBackgroundColor(COLOR_COMPLETED);
        }

        // Hide button for completed/cancelled
        if ("completed".equalsIgnoreCase(currentStatus) ||
                "cancelled".equalsIgnoreCase(currentStatus)) {
            holder.btnUpdateStatus.setVisibility(View.GONE);
        }
    }

    private void updateProgressDots(OrderViewHolder holder, OrderModel order) {
        String[] statusFlow = getStatusFlow(order);
        String currentStatus = order.getStatus() != null ? order.getStatus().toLowerCase() : "pending";

        StringBuilder dots = new StringBuilder();
        for (int i = 0; i < statusFlow.length; i++) {
            String status = statusFlow[i];

            if (status.equals(currentStatus)) {
                dots.append("●");
            } else if (isStatusBefore(status, currentStatus, statusFlow)) {
                dots.append("●");
            } else {
                dots.append("○");
            }

            if (i < statusFlow.length - 1) dots.append(" ");
        }

        if (holder.tvProgress != null) {
            holder.tvProgress.setText(dots.toString());
            setProgressDotsColor(holder.tvProgress, currentStatus);
        }
    }

    @Override
    public int getItemCount() {
        return orderList != null ? orderList.size() : 0;
    }

    public void updateList(List<OrderModel> newList) {
        this.orderList = newList != null ? newList : new ArrayList<>();
        notifyDataSetChanged();
        Log.d(TAG, "List updated with " + this.orderList.size() + " items");
    }

    // ===== STATUS FLOW METHODS =====
    private String getNextStatus(OrderModel order) {
        String current = order.getStatus();
        if (current == null) current = "Pending";
        current = current.toLowerCase();

        String orderType = order.getOrderType() != null ? order.getOrderType().toLowerCase() : "delivery";
        String paymentMethod = order.getPayment_method() != null ? order.getPayment_method().toLowerCase() : "cod";

        if ("pickup".equals(orderType)) {
            switch (current) {
                case "pending": return "Accepted";
                case "accepted": return "Preparing";
                case "preparing": return "Ready";
                case "ready": return "Completed";
                default: return null;
            }
        } else {
            if ("gcash".equals(paymentMethod)) {
                switch (current) {
                    case "pending": return "Verifying";
                    case "verifying": return "Preparing";
                    case "preparing": return "Delivering";
                    case "delivering": return "Completed";
                    default: return null;
                }
            } else {
                switch (current) {
                    case "pending": return "Accepted";
                    case "accepted": return "Preparing";
                    case "preparing": return "Delivering";
                    case "delivering": return "Completed";
                    default: return null;
                }
            }
        }
    }

    private String[] getStatusFlow(OrderModel order) {
        String orderType = order.getOrderType() != null ? order.getOrderType().toLowerCase() : "delivery";
        String paymentMethod = order.getPayment_method() != null ? order.getPayment_method().toLowerCase() : "cod";

        if ("pickup".equals(orderType)) {
            return new String[]{"pending", "accepted", "preparing", "ready", "completed"};
        } else {
            if ("gcash".equals(paymentMethod)) {
                return new String[]{"pending", "verifying", "preparing", "delivering", "completed"};
            } else {
                return new String[]{"pending", "accepted", "preparing", "delivering", "completed"};
            }
        }
    }

    private boolean isStatusBefore(String status, String currentStatus, String[] flow) {
        for (int i = 0; i < flow.length; i++) {
            if (flow[i].equals(status)) {
                for (int j = i + 1; j < flow.length; j++) {
                    if (flow[j].equals(currentStatus)) {
                        return true;
                    }
                }
                return false;
            }
        }
        return false;
    }

    // ===== COLOR METHODS =====
    private void setStatusBadgeColor(TextView statusView, String status) {
        int color = COLOR_PENDING; // default
        if (status != null) {
            switch (status.toLowerCase()) {
                case "accepted": color = COLOR_ACCEPTED; break;
                case "verifying": color = COLOR_VERIFYING; break;
                case "preparing": color = COLOR_PREPARING; break;
                case "ready": color = COLOR_READY; break;
                case "delivering": color = COLOR_DELIVERING; break;
                case "completed": color = COLOR_COMPLETED; break;
                case "cancelled": color = COLOR_CANCELLED; break;
            }
        }
        statusView.setBackgroundColor(color);
    }

    private void setButtonColor(Button button, String nextStatus) {
        int color = Color.parseColor("#3F51B5"); // default primary
        if (nextStatus != null) {
            switch (nextStatus.toLowerCase()) {
                case "accepted": color = COLOR_ACCEPTED; break;
                case "verifying": color = COLOR_VERIFYING; break;
                case "preparing": color = COLOR_PREPARING; break;
                case "ready": color = COLOR_READY; break;
                case "delivering": color = COLOR_DELIVERING; break;
                case "completed": color = COLOR_COMPLETED; break;
            }
        }
        button.setBackgroundColor(color);
    }

    private void setProgressDotsColor(TextView progressView, String currentStatus) {
        int color = Color.parseColor("#3F51B5"); // default primary
        if (currentStatus != null) {
            switch (currentStatus.toLowerCase()) {
                case "pending": color = COLOR_PENDING; break;
                case "accepted": color = COLOR_ACCEPTED; break;
                case "verifying": color = COLOR_VERIFYING; break;
                case "preparing": color = COLOR_PREPARING; break;
                case "ready": color = COLOR_READY; break;
                case "delivering": color = COLOR_DELIVERING; break;
                case "completed": color = COLOR_COMPLETED; break;
                case "cancelled": color = COLOR_CANCELLED; break;
            }
        }
        progressView.setTextColor(color);
    }

    // ===== VIEWHOLDER =====
    static class OrderViewHolder extends RecyclerView.ViewHolder {
        TextView tvOrderId, tvCustomer, tvTotalPrice, tvPayment, tvStatus, tvProgress, tvOrderType, tvLocation;
        Button btnUpdateStatus;
        ImageView imgGcashProof;
        View gcashSection, locationSection;

        public OrderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvOrderId = itemView.findViewById(R.id.tvOrderId);
            tvCustomer = itemView.findViewById(R.id.tvCustomer);
            tvTotalPrice = itemView.findViewById(R.id.tvTotalPrice);
            tvPayment = itemView.findViewById(R.id.tvPayment);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvProgress = itemView.findViewById(R.id.tvProgress);
            tvOrderType = itemView.findViewById(R.id.tvOrderType);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            btnUpdateStatus = itemView.findViewById(R.id.btnUpdateStatus);
            imgGcashProof = itemView.findViewById(R.id.imgGcashProof);
            gcashSection = itemView.findViewById(R.id.gcashSection);
            locationSection = itemView.findViewById(R.id.locationSection);
        }
    }
}