package com.example.a4csofo;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminFamousFragment extends Fragment {

    // UI Components
    private BarChart barChart;
    private PieChart pieChart;
    private TextView tvTotalOrders, tvCompletedOrders, tvCancelledOrders, tvRevenue, tvBestSeller;
    private TextView tvTodayOrders, tvWeekOrders, tvMonthOrders;

    // Firebase
    private DatabaseReference ordersRef;

    // Data storage
    private Map<String, Integer> itemSales = new HashMap<>();

    // Statistics counters
    private int totalOrders = 0;
    private int completedOrders = 0;
    private int cancelledOrders = 0;
    private double totalRevenue = 0;
    private String bestSeller = "N/A";

    // Time period counters
    private int todayCount = 0;
    private int weekCount = 0;
    private int monthCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_famous_analytics, container, false);

        initializeViews(view);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        ordersRef = database.getReference("orders");

        loadOrderData();

        return view;
    }

    private void initializeViews(View view) {
        Log.d("AdminFamous", "=== INITIALIZE VIEWS START ===");

        // Initialize charts and text views
        barChart = view.findViewById(R.id.barChart);
        pieChart = view.findViewById(R.id.pieChart);
        Log.d("AdminFamous", "Charts initialized: " + (barChart != null) + ", " + (pieChart != null));

        tvTotalOrders = view.findViewById(R.id.tvTotalOrders);
        tvCompletedOrders = view.findViewById(R.id.tvCompletedOrders);
        tvCancelledOrders = view.findViewById(R.id.tvCancelledOrders);
        tvRevenue = view.findViewById(R.id.tvRevenue);
        tvBestSeller = view.findViewById(R.id.tvBestSeller);

        tvTodayOrders = view.findViewById(R.id.tvTodayOrders);
        tvWeekOrders = view.findViewById(R.id.tvWeekOrders);
        tvMonthOrders = view.findViewById(R.id.tvMonthOrders);

        // DEBUG: Check if views are found
        Log.d("AdminFamous", "tvTotalOrders found: " + (tvTotalOrders != null));
        Log.d("AdminFamous", "tvCompletedOrders found: " + (tvCompletedOrders != null));
        Log.d("AdminFamous", "tvCancelledOrders found: " + (tvCancelledOrders != null));
        Log.d("AdminFamous", "tvRevenue found: " + (tvRevenue != null));
        Log.d("AdminFamous", "tvBestSeller found: " + (tvBestSeller != null));

        // IMMEDIATELY SET TEST VALUES (FOR VISIBILITY)
        if (tvTotalOrders != null) {
            tvTotalOrders.setText("TEST");
            tvTotalOrders.setTextColor(Color.BLACK);
            Log.d("AdminFamous", "Set TEST on tvTotalOrders");
        }

        if (tvCompletedOrders != null) {
            tvCompletedOrders.setText("TEST");
            tvCompletedOrders.setTextColor(Color.BLACK);
            Log.d("AdminFamous", "Set TEST on tvCompletedOrders");
        }

        if (tvCancelledOrders != null) {
            tvCancelledOrders.setText("TEST");
            tvCancelledOrders.setTextColor(Color.BLACK);
            Log.d("AdminFamous", "Set TEST on tvCancelledOrders");
        }

        if (tvRevenue != null) {
            tvRevenue.setText("TEST");
            tvRevenue.setTextColor(Color.BLACK);
            Log.d("AdminFamous", "Set TEST on tvRevenue");
        }

        setupCharts();
    }

    private void setupCharts() {
        // Bar Chart Setup
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setDrawBarShadow(false);
        barChart.setDrawValueAboveBar(true);
        barChart.setPinchZoom(false);
        barChart.setDrawGridBackground(false);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelCount(5);

        YAxis leftAxis = barChart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGranularity(1f);
        leftAxis.setAxisMinimum(0f);

        barChart.getAxisRight().setEnabled(false);
        barChart.getLegend().setEnabled(false);
        barChart.animateY(1000);

        // Pie Chart Setup
        pieChart.getDescription().setEnabled(false);
        pieChart.setUsePercentValues(true);
        pieChart.setExtraOffsets(5, 10, 5, 5);
        pieChart.setDragDecelerationFrictionCoef(0.95f);
        pieChart.setDrawHoleEnabled(true);
        pieChart.setHoleColor(Color.WHITE);
        pieChart.setTransparentCircleColor(Color.WHITE);
        pieChart.setTransparentCircleAlpha(110);
        pieChart.setHoleRadius(58f);
        pieChart.setTransparentCircleRadius(61f);
        pieChart.setDrawCenterText(true);
        pieChart.setRotationAngle(0);
        pieChart.setRotationEnabled(true);
        pieChart.setHighlightPerTapEnabled(true);
        pieChart.animateY(1000);
    }

    private void loadOrderData() {
        if (ordersRef == null) {
            Log.e("AnalyticsError", "Orders reference is null");
            return;
        }

        ordersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                resetCounters();

                Log.d("AdminFamous", "Loading orders data...");

                if (!snapshot.exists()) {
                    Log.d("AdminFamous", "No orders found");
                    updateEmptyState();
                    return;
                }

                // Setup time ranges
                Calendar calendar = Calendar.getInstance();

                // Today start (midnight)
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long todayStart = calendar.getTimeInMillis();

                // Week start (7 days ago)
                calendar.add(Calendar.DAY_OF_MONTH, -7);
                long weekStart = calendar.getTimeInMillis();

                // Month start (30 days ago)
                calendar = Calendar.getInstance();
                calendar.add(Calendar.DAY_OF_MONTH, -30);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long monthStart = calendar.getTimeInMillis();

                long now = System.currentTimeMillis();

                // Process each order
                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    try {
                        OrderModel order = orderSnapshot.getValue(OrderModel.class);
                        if (order != null) {
                            String status = order.getStatus();

                            if (status == null) continue;

                            String statusLower = status.toLowerCase();

                            // GET TOTAL PRICE FROM ALL ORDERS
                            Double orderTotal = order.getTotal_price();
                            if (orderTotal != null) {
                                totalRevenue += orderTotal;
                            }

                            // Count orders by status
                            if (statusLower.equals("completed")) {
                                completedOrders++;
                                analyzeOrderItems(order);

                            } else if (statusLower.equals("cancelled") || statusLower.equals("rejected")) {
                                cancelledOrders++;
                            }

                            // Count ALL orders regardless of status
                            totalOrders++;

                            // Check time periods
                            Long orderDate = order.getOrderDate();
                            if (orderDate != null) {
                                if (orderDate >= todayStart && orderDate <= now) {
                                    todayCount++;
                                }
                                if (orderDate >= weekStart && orderDate <= now) {
                                    weekCount++;
                                }
                                if (orderDate >= monthStart && orderDate <= now) {
                                    monthCount++;
                                }
                            }
                        }
                    } catch (Exception e) {
                        Log.e("AdminFamous", "Error processing order: " + e.getMessage());
                    }
                }

                Log.d("AdminFamous", "=== FINAL STATISTICS ===");
                Log.d("AdminFamous", "Total Orders: " + totalOrders);
                Log.d("AdminFamous", "Completed: " + completedOrders);
                Log.d("AdminFamous", "Cancelled: " + cancelledOrders);
                Log.d("AdminFamous", "Total Revenue: ₱" + totalRevenue);

                updateUI();

                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "Analytics loaded!\n" +
                                    "Total: " + totalOrders + "\n" +
                                    "Completed: " + completedOrders + "\n" +
                                    "Cancelled: " + cancelledOrders + "\n" +
                                    "Revenue: ₱" + String.format("%.2f", totalRevenue),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AdminFamous", "Failed to load orders: " + error.getMessage());
                Toast.makeText(getContext(), "Failed to load data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void resetCounters() {
        totalOrders = 0;
        completedOrders = 0;
        cancelledOrders = 0;
        totalRevenue = 0;
        todayCount = 0;
        weekCount = 0;
        monthCount = 0;
        itemSales.clear();
        bestSeller = "N/A";
    }

    private void updateEmptyState() {
        if (tvTotalOrders != null) tvTotalOrders.setText("0");
        if (tvCompletedOrders != null) tvCompletedOrders.setText("0");
        if (tvCancelledOrders != null) tvCancelledOrders.setText("0");
        if (tvRevenue != null) tvRevenue.setText("₱0.00");
        if (tvBestSeller != null) tvBestSeller.setText("N/A");

        if (tvTodayOrders != null) tvTodayOrders.setText("0");
        if (tvWeekOrders != null) tvWeekOrders.setText("0");
        if (tvMonthOrders != null) tvMonthOrders.setText("0");

        updateCharts();
    }

    private void analyzeOrderItems(OrderModel order) {
        List<String> items = order.getItems();
        if (items != null && !items.isEmpty()) {
            for (String itemString : items) {
                try {
                    String itemName = itemString;
                    if (itemString.contains(":")) {
                        itemName = itemString.split(":")[0].trim();
                    } else if (itemString.contains("x")) {
                        itemName = itemString.split("x")[0].trim();
                    }

                    if (!itemName.isEmpty()) {
                        int currentCount = itemSales.getOrDefault(itemName, 0);
                        itemSales.put(itemName, currentCount + 1);
                    }
                } catch (Exception e) {
                    Log.e("AdminFamous", "Error parsing item: " + itemString);
                }
            }
        }

        int maxSales = 0;
        for (Map.Entry<String, Integer> entry : itemSales.entrySet()) {
            if (entry.getValue() > maxSales) {
                maxSales = entry.getValue();
                bestSeller = entry.getKey();
            }
        }
    }

    private void updateUI() {
        Log.d("AdminFamous", "=== updateUI() called ===");

        if (getActivity() != null) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    updateStats();
                    updateTimeStats();
                    updateCharts();
                }
            });
        } else {
            Log.e("AdminFamous", "Activity is null, cannot update UI");
        }
    }

    private void updateStats() {
        Log.d("AdminFamous", "=== Updating TextViews ===");
        Log.d("AdminFamous", "Values to set - Total: " + totalOrders +
                ", Completed: " + completedOrders +
                ", Cancelled: " + cancelledOrders +
                ", Revenue: " + totalRevenue);

        try {
            if (tvTotalOrders != null) {
                tvTotalOrders.setText(String.valueOf(totalOrders));
                tvTotalOrders.setTextColor(Color.BLACK);
                Log.d("AdminFamous", "✓ Set tvTotalOrders: " + totalOrders);
            } else {
                Log.e("AdminFamous", "✗ tvTotalOrders is NULL!");
            }

            if (tvCompletedOrders != null) {
                tvCompletedOrders.setText(String.valueOf(completedOrders));
                tvCompletedOrders.setTextColor(Color.BLACK);
                Log.d("AdminFamous", "✓ Set tvCompletedOrders: " + completedOrders);
            } else {
                Log.e("AdminFamous", "✗ tvCompletedOrders is NULL!");
            }

            if (tvCancelledOrders != null) {
                tvCancelledOrders.setText(String.valueOf(cancelledOrders));
                tvCancelledOrders.setTextColor(Color.BLACK);
                Log.d("AdminFamous", "✓ Set tvCancelledOrders: " + cancelledOrders);
            } else {
                Log.e("AdminFamous", "✗ tvCancelledOrders is NULL!");
            }

            if (tvRevenue != null) {
                String revenueText = String.format("₱%,.2f", totalRevenue);
                tvRevenue.setText(revenueText);
                tvRevenue.setTextColor(Color.BLACK);
                Log.d("AdminFamous", "✓ Set tvRevenue: " + revenueText);
            } else {
                Log.e("AdminFamous", "✗ tvRevenue is NULL!");
            }

            if (tvBestSeller != null) {
                tvBestSeller.setText(bestSeller);
                tvBestSeller.setTextColor(Color.BLACK);
                Log.d("AdminFamous", "✓ Set tvBestSeller: " + bestSeller);
            }

        } catch (Exception e) {
            Log.e("AdminFamous", "Error in updateStats: " + e.getMessage());
        }
    }

    private void updateTimeStats() {
        try {
            if (tvTodayOrders != null) {
                tvTodayOrders.setText(String.valueOf(todayCount));
                Log.d("AdminFamous", "Set today orders: " + todayCount);
            }
            if (tvWeekOrders != null) {
                tvWeekOrders.setText(String.valueOf(weekCount));
                Log.d("AdminFamous", "Set week orders: " + weekCount);
            }
            if (tvMonthOrders != null) {
                tvMonthOrders.setText(String.valueOf(monthCount));
                Log.d("AdminFamous", "Set month orders: " + monthCount);
            }
        } catch (Exception e) {
            Log.e("AdminFamous", "Error updating time stats: " + e.getMessage());
        }
    }

    private void updateCharts() {
        updateBarChart();
        updatePieChart();
    }

    private void updateBarChart() {
        try {
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            entries.add(new BarEntry(0, totalOrders));
            labels.add("Total");

            entries.add(new BarEntry(1, completedOrders));
            labels.add("Completed");

            entries.add(new BarEntry(2, cancelledOrders));
            labels.add("Cancelled");

            Log.d("AdminFamous", "Bar Chart Data: Total=" + totalOrders +
                    ", Completed=" + completedOrders + ", Cancelled=" + cancelledOrders);

            BarDataSet dataSet = new BarDataSet(entries, "Order Statistics");
            dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setValueTextSize(12f);
            dataSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.valueOf((int) value);
                }
            });

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.5f);
            barData.setValueTextSize(10f);

            barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
            barChart.setData(barData);
            barChart.invalidate();
            barChart.animateY(1000);
        } catch (Exception e) {
            Log.e("AdminFamous", "Error updating bar chart: " + e.getMessage());
        }
    }

    private void updatePieChart() {
        try {
            if (itemSales.isEmpty()) {
                pieChart.setCenterText("No Sales Data");
                pieChart.setData(null);
                pieChart.invalidate();
                return;
            }

            List<Map.Entry<String, Integer>> sortedItems = new ArrayList<>(itemSales.entrySet());
            sortedItems.sort((a, b) -> b.getValue().compareTo(a.getValue()));

            List<PieEntry> entries = new ArrayList<>();
            List<Integer> colors = new ArrayList<>();

            int count = Math.min(5, sortedItems.size());
            for (int i = 0; i < count; i++) {
                Map.Entry<String, Integer> item = sortedItems.get(i);
                String label = item.getKey();
                if (label.length() > 15) {
                    label = label.substring(0, 12) + "...";
                }
                entries.add(new PieEntry(item.getValue(), label));
                colors.add(ColorTemplate.COLORFUL_COLORS[i % ColorTemplate.COLORFUL_COLORS.length]);
            }

            if (sortedItems.size() > 5) {
                int othersTotal = 0;
                for (int i = 5; i < sortedItems.size(); i++) {
                    othersTotal += sortedItems.get(i).getValue();
                }
                entries.add(new PieEntry(othersTotal, "Others"));
                colors.add(Color.GRAY);
            }

            PieDataSet dataSet = new PieDataSet(entries, "");
            dataSet.setColors(colors);
            dataSet.setSliceSpace(3f);
            dataSet.setSelectionShift(5f);
            dataSet.setValueLinePart1OffsetPercentage(80f);
            dataSet.setValueLinePart1Length(0.5f);
            dataSet.setValueLinePart2Length(0.6f);
            dataSet.setYValuePosition(PieDataSet.ValuePosition.OUTSIDE_SLICE);
            dataSet.setValueTextColor(Color.BLACK);
            dataSet.setValueTextSize(11f);
            dataSet.setValueFormatter(new ValueFormatter() {
                @Override
                public String getFormattedValue(float value) {
                    return String.valueOf((int) value);
                }
            });

            PieData data = new PieData(dataSet);

            pieChart.setCenterText("Top Items");
            pieChart.setCenterTextSize(14f);
            pieChart.setCenterTextColor(Color.BLACK);
            pieChart.setData(data);
            pieChart.invalidate();
            pieChart.animateY(1000);
        } catch (Exception e) {
            Log.e("AdminFamous", "Error updating pie chart: " + e.getMessage());
        }
    }
}