package com.example.a4csofo;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.*;
import android.view.View;
import android.app.AlertDialog;

import com.android.volley.*;
import com.android.volley.toolbox.*;

import org.json.*;

import java.util.*;

public class CategoriesActivity extends AppCompatActivity {

    private ListView listView;
    private EditText etCategoryName;
    private Button btnAddCategory;
    private ArrayList<String> categoryList = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    // Change this to your XAMPP server IP (e.g. http://192.168.1.100/yourapp/)
    private static final String BASE_URL = "http://YOUR_IP_ADDRESS/yourapp/";
    private static final String GET_CATEGORIES_URL = BASE_URL + "get_categories.php";
    private static final String ADD_CATEGORY_URL = BASE_URL + "add_category.php";
    private static final String DELETE_CATEGORY_URL = BASE_URL + "delete_category.php";
    private static final String UPDATE_CATEGORY_URL = BASE_URL + "update_category.php";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_categories);

        listView = findViewById(R.id.listViewCategories);
        etCategoryName = findViewById(R.id.etCategoryName);
        btnAddCategory = findViewById(R.id.btnAddCategory);

        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, categoryList);
        listView.setAdapter(adapter);

        loadCategories();

        btnAddCategory.setOnClickListener(v -> addCategory());

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            String selected = categoryList.get(position);
            showActionDialog(selected);
            return true;
        });
    }

    private void loadCategories() {
        categoryList.clear();
        StringRequest request = new StringRequest(Request.Method.GET, GET_CATEGORIES_URL,
                response -> {
                    try {
                        JSONArray arr = new JSONArray(response);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject obj = arr.getJSONObject(i);
                            String name = obj.getString("name");
                            int foodCount = obj.getInt("food_count");
                            categoryList.add(name + " (" + foodCount + " items)");
                        }
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Toast.makeText(this, "JSON Error", Toast.LENGTH_SHORT).show();
                    }
                },
                error -> Toast.makeText(this, "Error loading categories", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    private void addCategory() {
        String name = etCategoryName.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter category name", Toast.LENGTH_SHORT).show();
            return;
        }

        StringRequest request = new StringRequest(Request.Method.POST, ADD_CATEGORY_URL,
                response -> {
                    Toast.makeText(this, "Category added!", Toast.LENGTH_SHORT).show();
                    etCategoryName.setText("");
                    loadCategories();
                },
                error -> Toast.makeText(this, "Error adding category", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("name", name);
                return params;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void showActionDialog(String categoryName) {
        new AlertDialog.Builder(this)
                .setTitle(categoryName)
                .setMessage("Choose an action:")
                .setPositiveButton("Edit", (dialog, which) -> showUpdateDialog(categoryName))
                .setNegativeButton("Delete", (dialog, which) -> deleteCategory(categoryName))
                .setNeutralButton("Cancel", null)
                .show();
    }

    private void showUpdateDialog(String oldName) {
        EditText input = new EditText(this);
        input.setText(oldName);
        new AlertDialog.Builder(this)
                .setTitle("Update Category")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> updateCategory(oldName, input.getText().toString()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCategory(String oldName, String newName) {
        StringRequest request = new StringRequest(Request.Method.POST, UPDATE_CATEGORY_URL,
                response -> {
                    Toast.makeText(this, "Category updated!", Toast.LENGTH_SHORT).show();
                    loadCategories();
                },
                error -> Toast.makeText(this, "Error updating category", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("old_name", oldName);
                params.put("new_name", newName);
                return params;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }

    private void deleteCategory(String name) {
        StringRequest request = new StringRequest(Request.Method.POST, DELETE_CATEGORY_URL,
                response -> {
                    Toast.makeText(this, "Category deleted!", Toast.LENGTH_SHORT).show();
                    loadCategories();
                },
                error -> Toast.makeText(this, "Error deleting category", Toast.LENGTH_SHORT).show()
        ) {
            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<>();
                params.put("name", name);
                return params;
            }
        };
        Volley.newRequestQueue(this).add(request);
    }
}
