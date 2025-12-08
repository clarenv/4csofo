package com.example.a4csofo;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
    private int totalOrders = 0;
    private int completedOrders = 0;
    private int cancelledOrders = 0;
    private double totalRevenue = 0;
    private String bestSeller = "N/A";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_famous_analytics, container, false);

        initializeViews(view);

        ordersRef = FirebaseDatabase.getInstance().getReference("orders");

        loadOrderData();

        return view;
    }

    private void initializeViews(View view) {
        barChart = view.findViewById(R.id.barChart);
        pieChart = view.findViewById(R.id.pieChart);

        tvTotalOrders = view.findViewById(R.id.tvTotalOrders);
        tvCompletedOrders = view.findViewById(R.id.tvCompletedOrders);
        tvCancelledOrders = view.findViewById(R.id.tvCancelledOrders);
        tvRevenue = view.findViewById(R.id.tvRevenue);
        tvBestSeller = view.findViewById(R.id.tvBestSeller);

        tvTodayOrders = view.findViewById(R.id.tvTodayOrders);
        tvWeekOrders = view.findViewById(R.id.tvWeekOrders);
        tvMonthOrders = view.findViewById(R.id.tvMonthOrders);

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
        ordersRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                resetCounters();

                // Set calendar instances for today, week, month
                Calendar calendar = Calendar.getInstance();

                // Today start
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long todayStart = calendar.getTimeInMillis();

                // Tomorrow start
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                long tomorrowStart = calendar.getTimeInMillis();

                // Week start (Sunday)
                calendar = Calendar.getInstance();
                calendar.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long weekStart = calendar.getTimeInMillis();

                // Month start
                calendar = Calendar.getInstance();
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                long monthStart = calendar.getTimeInMillis();

                int todayCount = 0;
                int weekCount = 0;
                int monthCount = 0;

                for (DataSnapshot orderSnapshot : snapshot.getChildren()) {
                    OrderModel order = orderSnapshot.getValue(OrderModel.class);
                    if (order != null) {
                        totalOrders++;

                        long orderDate = order.getOrderDate();

                        // Today
                        if (orderDate >= todayStart && orderDate < tomorrowStart) {
                            todayCount++;
                        }

                        // Week
                        if (orderDate >= weekStart) {
                            weekCount++;
                        }

                        // Month
                        if (orderDate >= monthStart) {
                            monthCount++;
                        }

                        String status = order.getStatus();
                        if (status != null) {
                            if (status.equalsIgnoreCase("Completed")) {
                                completedOrders++;

                                try {
                                    String totalStr = order.getTotal();
                                    if (totalStr != null) {
                                        totalStr = totalStr.replace("₱", "").replace(",", "").trim();
                                        double orderTotal = Double.parseDouble(totalStr);
                                        totalRevenue += orderTotal;
                                    }
                                } catch (NumberFormatException e) {
                                    Log.e("RevenueError", "Failed to parse total: " + order.getTotal());
                                }

                                analyzeOrderItems(order);

                            } else if (status.equalsIgnoreCase("Cancelled") || status.equalsIgnoreCase("Rejected")) {
                                cancelledOrders++;
                            }
                        }
                    }
                }

                updateStats();
                updateTimeStats(todayCount, weekCount, monthCount);
                updateCharts();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e("AnalyticsError", "Failed to load orders: " + error.getMessage());
            }
        });
    }

    private void resetCounters() {
        totalOrders = 0;
        completedOrders = 0;
        cancelledOrders = 0;
        totalRevenue = 0;
        itemSales.clear();
        bestSeller = "N/A";
    }

    private void analyzeOrderItems(OrderModel order) {
        if (order.getItems() != null && !order.getItems().isEmpty()) {
            for (String itemName : order.getItems()) {
                itemSales.put(itemName, itemSales.getOrDefault(itemName, 0) + 1);
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

    private void updateStats() {
        tvTotalOrders.setText(String.valueOf(totalOrders));
        tvCompletedOrders.setText(String.valueOf(completedOrders));
        tvCancelledOrders.setText(String.valueOf(cancelledOrders));
        tvRevenue.setText(String.format("₱%.2f", totalRevenue));
        tvBestSeller.setText(bestSeller);
    }

    private void updateTimeStats(int today, int week, int month) {
        tvTodayOrders.setText(String.valueOf(today));
        tvWeekOrders.setText(String.valueOf(week));
        tvMonthOrders.setText(String.valueOf(month));
    }

    private void updateCharts() {
        updateBarChart();
        updatePieChart();
    }

    private void updateBarChart() {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        entries.add(new BarEntry(0, totalOrders));
        labels.add("Total");

        entries.add(new BarEntry(1, completedOrders));
        labels.add("Completed");

        entries.add(new BarEntry(2, cancelledOrders));
        labels.add("Cancelled");

        BarDataSet dataSet = new BarDataSet(entries, "Order Statistics");
        dataSet.setColors(ColorTemplate.MATERIAL_COLORS);
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(12f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.5f);

        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.setData(barData);
        barChart.invalidate();
    }

    private void updatePieChart() {
        if (itemSales.isEmpty()) {
            pieChart.setCenterText("No Data");
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
            entries.add(new PieEntry(item.getValue(), item.getKey()));
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

        PieData data = new PieData(dataSet);
        data.setValueFormatter(new ValueFormatter() {
            @Override
            public String getFormattedValue(float value) {
                return String.valueOf((int) value);
            }
        });
        data.setValueTextSize(11f);
        data.setValueTextColor(Color.BLACK);

        pieChart.setCenterText("Top Items");
        pieChart.setCenterTextSize(14f);
        pieChart.setData(data);
        pieChart.invalidate();
    }

    public static class AnalyticsModel {
        private int totalOrders;
        private int completedOrders;
        private int cancelledOrders;
        private double totalRevenue;
        private String bestSeller;
        private Map<String, Integer> itemSales;

        public AnalyticsModel() {
            itemSales = new HashMap<>();
        }

        public int getTotalOrders() { return totalOrders; }
        public void setTotalOrders(int totalOrders) { this.totalOrders = totalOrders; }
        public int getCompletedOrders() { return completedOrders; }
        public void setCompletedOrders(int completedOrders) { this.completedOrders = completedOrders; }
        public int getCancelledOrders() { return cancelledOrders; }
        public void setCancelledOrders(int cancelledOrders) { this.cancelledOrders = cancelledOrders; }
        public double getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(double totalRevenue) { this.totalRevenue = totalRevenue; }
        public String getBestSeller() { return bestSeller; }
        public void setBestSeller(String bestSeller) { this.bestSeller = bestSeller; }
        public Map<String, Integer> getItemSales() { return itemSales; }
        public void setItemSales(Map<String, Integer> itemSales) { this.itemSales = itemSales; }
    }
}
