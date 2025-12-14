package com.example.a4csofo;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 100;
    private static final int LOCATION_SETTINGS_REQUEST = 101;
    private static final String TAG = "RegisterActivity";
    private static final String OSM_NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse";

    // UI Components
    private EditText etName, etEmail, etPhone, etAddress, etPassword, etConfirmPassword;
    private Button btnRegister;
    private TextView loginLink;

    // Firebase
    private FirebaseAuth auth;
    private DatabaseReference usersRef;

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private ExecutorService executorService;
    private boolean isGettingLocation = false;
    private double currentLatitude = 0;
    private double currentLongitude = 0;

    // Wizard State
    private int currentStep = 1;
    private final int TOTAL_STEPS = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Initialize Firebase
        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newSingleThreadExecutor();

        // Initialize UI
        initializeViews();

        // Setup wizard for Step 1
        setupStep1();

        // Setup button listeners
        setupButtonListeners();
    }

    private void initializeViews() {
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        etAddress = findViewById(R.id.etAddress);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        loginLink = findViewById(R.id.loginLink);
    }

    private void setupStep1() {
        // Show only Step 1 fields
        etName.setVisibility(android.view.View.VISIBLE);
        etEmail.setVisibility(android.view.View.VISIBLE);
        etPhone.setVisibility(android.view.View.VISIBLE);

        // Hide Step 2 & 3 fields
        etAddress.setVisibility(android.view.View.GONE);
        etPassword.setVisibility(android.view.View.GONE);
        etConfirmPassword.setVisibility(android.view.View.GONE);

        // Update UI
        updateStepHeader("Step 1 of 3: Personal Information");
        btnRegister.setText("Next");
        loginLink.setVisibility(android.view.View.GONE);
    }

    private void setupStep2() {
        // Hide Step 1 fields
        etName.setVisibility(android.view.View.GONE);
        etEmail.setVisibility(android.view.View.GONE);
        etPhone.setVisibility(android.view.View.GONE);

        // Show Step 2 field
        etAddress.setVisibility(android.view.View.VISIBLE);

        // Hide Step 3 fields
        etPassword.setVisibility(android.view.View.GONE);
        etConfirmPassword.setVisibility(android.view.View.GONE);

        // Update UI
        updateStepHeader("Step 2 of 3: Location");
        btnRegister.setText("Next");
        loginLink.setVisibility(android.view.View.GONE);

        // Setup address click listener
        setupAddressClickListener();
    }

    private void setupStep3() {
        // Hide Step 1 & 2 fields
        etName.setVisibility(android.view.View.GONE);
        etEmail.setVisibility(android.view.View.GONE);
        etPhone.setVisibility(android.view.View.GONE);
        etAddress.setVisibility(android.view.View.GONE);

        // Show Step 3 fields
        etPassword.setVisibility(android.view.View.VISIBLE);
        etConfirmPassword.setVisibility(android.view.View.VISIBLE);

        // Update UI
        updateStepHeader("Step 3 of 3: Security");
        btnRegister.setText("Create Account");
        loginLink.setVisibility(android.view.View.VISIBLE);
    }

    private void updateStepHeader(String text) {
        TextView tvStepHeader = findViewById(R.id.tvStepHeader);
        if (tvStepHeader != null) {
            tvStepHeader.setText(text);
        }
    }

    private void setupAddressClickListener() {
        etAddress.setOnClickListener(v -> {
            // Show HORIZONTAL BUTTONS dialog
            showAddressOptionsDialog();
        });

        etAddress.setFocusable(false);
        etAddress.setClickable(true);
    }

    // ==================== HORIZONTAL BUTTONS DIALOG ====================

    private void showAddressOptionsDialog() {
        // Create custom dialog with horizontal buttons
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Get Your Address");
        builder.setMessage("Choose how to get your address:");

        // Set custom view with horizontal buttons
        builder.setPositiveButton("Use Current Location", null); // We'll override later
        builder.setNegativeButton("Enter Manually", null); // We'll override later

        AlertDialog dialog = builder.create();

        // Show dialog first
        dialog.show();

        // Get the buttons
        Button btnCurrentLocation = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button btnEnterManually = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);

        // Customize button appearance for horizontal alignment
        android.view.ViewGroup.LayoutParams params = btnCurrentLocation.getLayoutParams();
        params.width = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        btnCurrentLocation.setLayoutParams(params);
        btnEnterManually.setLayoutParams(params);

        // Set click listeners AFTER dialog is shown
        btnCurrentLocation.setOnClickListener(v -> {
            dialog.dismiss();
            if (isLocationEnabled()) {
                checkLocationPermissions();
            } else {
                showEnableLocationDialog();
            }
        });

        btnEnterManually.setOnClickListener(v -> {
            dialog.dismiss();
            etAddress.setFocusable(true);
            etAddress.setFocusableInTouchMode(true);
            etAddress.requestFocus();
        });
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showEnableLocationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Turn On Location")
                .setMessage("Location Services is required to detect your address.\n\n" +
                        "Please enable Location Services in your device settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(intent, LOCATION_SETTINGS_REQUEST);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    etAddress.setFocusable(true);
                    etAddress.requestFocus();
                })
                .show();
    }

    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_SETTINGS_REQUEST) {
            new Handler().postDelayed(() -> {
                if (isLocationEnabled()) {
                    checkLocationPermissions();
                } else {
                    Toast.makeText(this, "Location Services still disabled", Toast.LENGTH_SHORT).show();
                    etAddress.setFocusable(true);
                    etAddress.requestFocus();
                }
            }, 1000);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0) {
                boolean fineLocationGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                boolean coarseLocationGranted = grantResults.length > 1 &&
                        grantResults[1] == PackageManager.PERMISSION_GRANTED;

                if (fineLocationGranted || coarseLocationGranted) {
                    fetchCurrentLocation();
                } else {
                    Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                    etAddress.setFocusable(true);
                    etAddress.requestFocus();
                }
            }
        }
    }

    // ==================== STRICT PH ADDRESS FETCHING ====================

    private void fetchCurrentLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Please enable Location Services first", Toast.LENGTH_SHORT).show();
            showEnableLocationDialog();
            return;
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermissions();
            return;
        }

        if (isGettingLocation) {
            return;
        }

        isGettingLocation = true;
        Toast.makeText(this, "Getting your location...", Toast.LENGTH_SHORT).show();

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            long locationAge = System.currentTimeMillis() - location.getTime();

                            if (locationAge < 2 * 60 * 1000) { // Less than 2 minutes old
                                currentLatitude = location.getLatitude();
                                currentLongitude = location.getLongitude();
                                convertToStrictPHAddress();
                                isGettingLocation = false;
                                return;
                            }
                        }
                        requestNewLocation();
                    }
                });
    }

    private void requestNewLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setNumUpdates(1);
        locationRequest.setMaxWaitTime(15000);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    showLocationError();
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        currentLatitude = location.getLatitude();
                        currentLongitude = location.getLongitude();
                        convertToStrictPHAddress();
                        stopLocationUpdates();
                        isGettingLocation = false;
                        return;
                    }
                }
                showLocationError();
            }
        };

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

        new Handler().postDelayed(() -> {
            if (isGettingLocation) {
                showLocationError();
                stopLocationUpdates();
            }
        }, 15000);
    }

    // ==================== DUAL ADDRESS FETCHING (Geocoder + OSM) ====================

    private void convertToStrictPHAddress() {
        executorService.execute(() -> {
            try {
                // FIRST: Try Google Geocoder
                Geocoder geocoder = new Geocoder(RegisterActivity.this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 3);

                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        String strictAddress = "";

                        for (Address address : addresses) {
                            strictAddress = extractStrictPHAddress(address);
                            if (!strictAddress.isEmpty()) {
                                break;
                            }
                        }

                        if (!strictAddress.isEmpty()) {
                            etAddress.setText(strictAddress);
                            Toast.makeText(RegisterActivity.this, "Complete address detected", Toast.LENGTH_SHORT).show();
                            return; // Success, exit
                        }
                    }

                    // SECOND: If Geocoder fails, try OSM
                    Log.d(TAG, "Geocoder failed, trying OSM...");
                    tryOSMAddressFallback();
                });
            } catch (IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Geocoder error: " + e.getMessage());
                    // Try OSM as fallback
                    tryOSMAddressFallback();
                });
            }
        });
    }

    // ==================== OSM FALLBACK ====================

    private void tryOSMAddressFallback() {
        executorService.execute(() -> {
            String osmAddress = getAddressFromOSM();

            runOnUiThread(() -> {
                if (!osmAddress.isEmpty()) {
                    etAddress.setText(osmAddress);
                    Toast.makeText(RegisterActivity.this, "Address detected via OSM", Toast.LENGTH_SHORT).show();
                } else {
                    // Both Geocoder and OSM failed
                    showLocationError();
                }
            });
        });
    }

    private String getAddressFromOSM() {
        try {
            String urlString = String.format(Locale.US,
                    "%s?format=json&lat=%.6f&lon=%.6f&zoom=18" +
                            "&addressdetails=1&countrycodes=ph&accept-language=en",
                    OSM_NOMINATIM_URL, currentLatitude, currentLongitude);

            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Android-App/1.0");

            if (conn.getResponseCode() == 200) {
                InputStream inputStream = conn.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }

                reader.close();
                inputStream.close();
                conn.disconnect();

                JSONObject json = new JSONObject(response.toString());
                return extractOSMAddress(json);
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    // ==================== ADDRESS EXTRACTION METHODS ====================

    /**
     * EXTRACT STRICT PH ADDRESS - Must have ALL 4 parts:
     * 1. Street
     * 2. Barangay
     * 3. City/Municipality
     * 4. Province (not Region)
     */
    private String extractStrictPHAddress(Address address) {
        String street = extractStreet(address);
        String barangay = extractBarangay(address);
        String city = extractCity(address);
        String province = extractProvince(address);

        Log.d(TAG, "Geocoder Extracted - Street: " + street +
                ", Barangay: " + barangay +
                ", City: " + city +
                ", Province: " + province);

        // ALL 4 PARTS REQUIRED
        if (!street.isEmpty() && !barangay.isEmpty() && !city.isEmpty() && !province.isEmpty()) {
            return street + ", " + barangay + ", " + city + ", " + province;
        }

        return "";
    }

    private String extractOSMAddress(JSONObject json) {
        try {
            JSONObject addressObj = json.getJSONObject("address");

            // 1. STREET
            String street = "";
            String[] streetFields = {"road", "pedestrian", "footway", "residential"};
            for (String field : streetFields) {
                if (addressObj.has(field)) {
                    street = addressObj.getString(field);
                    if (!street.isEmpty()) {
                        street = formatStreet(street);
                        break;
                    }
                }
            }

            // 2. BARANGAY
            String barangay = "";
            String[] barangayFields = {"village", "suburb", "hamlet", "neighbourhood"};
            for (String field : barangayFields) {
                if (addressObj.has(field)) {
                    barangay = addressObj.getString(field);
                    if (!barangay.isEmpty() && isBarangayName(barangay)) {
                        barangay = formatBarangay(barangay);
                        break;
                    }
                }
            }

            // 3. CITY/MUNICIPALITY
            String city = "";
            String[] cityFields = {"city", "town", "municipality"};
            for (String field : cityFields) {
                if (addressObj.has(field)) {
                    city = addressObj.getString(field);
                    if (!city.isEmpty()) {
                        city = formatCity(city);
                        break;
                    }
                }
            }

            // 4. PROVINCE
            String province = "";
            String[] provinceFields = {"state", "county"};
            for (String field : provinceFields) {
                if (addressObj.has(field)) {
                    province = addressObj.getString(field);
                    if (!province.isEmpty() && !isRegionName(province)) {
                        province = formatProvince(province);
                        break;
                    }
                }
            }

            Log.d(TAG, "OSM Extracted - Street: " + street +
                    ", Barangay: " + barangay +
                    ", City: " + city +
                    ", Province: " + province);

            // ALL 4 PARTS REQUIRED
            if (!street.isEmpty() && !barangay.isEmpty() && !city.isEmpty() && !province.isEmpty()) {
                return street + ", " + barangay + ", " + city + ", " + province;
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Parsing Error: " + e.getMessage());
        }
        return "";
    }

    // ==================== ADDRESS COMPONENT EXTRACTION ====================

    private String extractStreet(Address address) {
        String street = address.getThoroughfare();
        if (street != null && !street.trim().isEmpty() && isStreetName(street)) {
            return formatStreet(street.trim());
        }
        return "";
    }

    private boolean isStreetName(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("street") || lower.contains("st.") || lower.contains("st ") ||
                lower.contains("avenue") || lower.contains("ave") || lower.contains("ave.") ||
                lower.contains("road") || lower.contains("rd") || lower.contains("rd.") ||
                lower.contains("boulevard") || lower.contains("blvd") ||
                lower.contains("drive") || lower.contains("dr") ||
                lower.contains("highway") || lower.contains("hwy") ||
                lower.contains("lane") || lower.contains("ln");
    }

    private String formatStreet(String street) {
        if (street == null || street.trim().isEmpty()) return "";

        street = street.trim();
        String lower = street.toLowerCase();

        if (!hasStreetSuffix(lower)) {
            street = street + " St.";
        }

        return capitalizeWords(street);
    }

    private boolean hasStreetSuffix(String street) {
        return street.contains("street") || street.contains("st.") || street.contains("st ") ||
                street.contains("avenue") || street.contains("ave") || street.contains("ave.") ||
                street.contains("road") || street.contains("rd") || street.contains("rd.") ||
                street.contains("boulevard") || street.contains("blvd") ||
                street.contains("drive") || street.contains("dr") ||
                street.contains("highway") || street.contains("hwy") ||
                street.contains("lane") || street.contains("ln");
    }

    private String extractBarangay(Address address) {
        String barangay = address.getSubLocality();
        if (barangay != null && !barangay.trim().isEmpty() && isBarangayName(barangay)) {
            return formatBarangay(barangay.trim());
        }
        return "";
    }

    private boolean isBarangayName(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("barangay") || lower.contains("brgy") || lower.contains("bgy") ||
                lower.contains("village") || lower.contains("vil.") ||
                lower.contains("poblacion") || lower.contains("pob.");
    }

    private String formatBarangay(String barangay) {
        if (barangay == null || barangay.trim().isEmpty()) return "";

        barangay = barangay.trim();
        String lower = barangay.toLowerCase();

        if (!lower.startsWith("barangay ") &&
                !lower.startsWith("brgy ") &&
                !lower.startsWith("bgy ")) {
            barangay = "Barangay " + barangay;
        }

        return capitalizeWords(barangay);
    }

    private String extractCity(Address address) {
        String city = address.getLocality();
        if (city == null || city.trim().isEmpty()) {
            city = address.getSubAdminArea();
        }

        if (city != null && !city.trim().isEmpty() && !isRegionName(city)) {
            city = city.trim()
                    .replace("City of ", "")
                    .replace("Municipality of ", "")
                    .replace("City ", "")
                    .replace("Municipality ", "")
                    .trim();
            return capitalizeWords(city);
        }
        return "";
    }

    private String extractProvince(Address address) {
        String province = address.getAdminArea();
        if (province != null && !province.trim().isEmpty()) {
            province = province.trim()
                    .replace("Province of ", "")
                    .replace("Province ", "")
                    .trim();

            if (!isRegionName(province) && isProvinceName(province)) {
                return capitalizeWords(province);
            }
        }
        return "";
    }

    private String formatProvince(String province) {
        if (province == null || province.trim().isEmpty()) return "";

        province = province.trim()
                .replace("Province of ", "")
                .replace("Province ", "")
                .trim();

        return capitalizeWords(province);
    }

    /**
     * Format city name
     */
    private String formatCity(String city) {
        if (city == null || city.trim().isEmpty()) return "";

        city = city.trim()
                .replace("City of ", "")
                .replace("Municipality of ", "")
                .replace("City ", "")
                .replace("Municipality ", "")
                .trim();

        return capitalizeWords(city);
    }

    private boolean isRegionName(String area) {
        if (area == null) return false;
        String lower = area.toLowerCase();
        return lower.contains("region") ||
                lower.equals("ncr") || lower.equals("national capital region") ||
                lower.contains("calabarzon") || lower.contains("car") ||
                lower.contains("bangsamoro") || lower.contains("metro manila");
    }

    private boolean isProvinceName(String area) {
        if (area == null) return false;
        String lower = area.toLowerCase();

        // Common PH provinces
        String[] phProvinces = {
                "laguna", "cavite", "batangas", "rizal", "quezon", "bulacan",
                "pampanga", "tarlac", "nueva ecija", "bataan", "zambales",
                "pangasinan", "la union", "ilocos sur", "ilocos norte",
                "cebu", "bohol", "leyte", "samar", "negros occidental",
                "negros oriental", "iloilo", "aklan", "capiz", "antique",
                "guimaras", "mindoro", "palawan", "marinduque", "romblon",
                "masbate", "albay", "camarines sur", "camarines norte",
                "sorsogon", "catanduanes", "isabela", "cagayan", "nueva vizcaya",
                "quirino", "aurora", "ifugao", "benguet", "mountain province",
                "kalinga", "apayao", "abRA", "zamboanga", "davao", "cotabato",
                "south cotabato", "sultan kudarat", "sarangani", "agusan",
                "surigao", "misamis", "bukidnon", "lanao", "maguindanao",
                "sulu", "tawi-tawi", "basilan"
        };

        for (String province : phProvinces) {
            if (lower.contains(province)) {
                return true;
            }
        }
        return false;
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                if (word.equalsIgnoreCase("del") || word.equalsIgnoreCase("de") ||
                        word.equalsIgnoreCase("y") || word.equalsIgnoreCase("ng")) {
                    result.append(word.toLowerCase()).append(" ");
                } else {
                    result.append(Character.toUpperCase(word.charAt(0)))
                            .append(word.substring(1).toLowerCase())
                            .append(" ");
                }
            }
        }

        return result.toString().trim();
    }

    // ==================== ERROR HANDLING ====================

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    private void showLocationError() {
        runOnUiThread(() -> {
            isGettingLocation = false;
            Toast.makeText(RegisterActivity.this,
                    "Cannot detect complete address. Please enter manually.",
                    Toast.LENGTH_LONG).show();

            new AlertDialog.Builder(RegisterActivity.this)
                    .setTitle("Address Detection Failed")
                    .setMessage("We couldn't detect a complete address (Street, Barangay, City, Province).\n\nPlease enter your address manually.")
                    .setPositiveButton("Enter Manually", (dialog, which) -> {
                        etAddress.setFocusable(true);
                        etAddress.setFocusableInTouchMode(true);
                        etAddress.requestFocus();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    // ==================== BUTTON LISTENERS ====================

    private void setupButtonListeners() {
        btnRegister.setOnClickListener(v -> {
            if (currentStep == 1) {
                if (validateStep1()) {
                    currentStep = 2;
                    setupStep2();
                }
            } else if (currentStep == 2) {
                if (validateStep2()) {
                    currentStep = 3;
                    setupStep3();
                }
            } else if (currentStep == 3) {
                if (validateStep3()) {
                    registerUser();
                }
            }
        });

        loginLink.setOnClickListener(v -> {
            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });
    }

    // ==================== VALIDATION ====================

    private boolean validateStep1() {
        boolean isValid = true;

        // Name validation
        if (etName.getText().toString().trim().isEmpty()) {
            etName.setError("Full name is required");
            isValid = false;
        } else {
            etName.setError(null);
        }

        // Email validation
        String email = etEmail.getText().toString().trim();
        if (email.isEmpty()) {
            etEmail.setError("Email is required");
            isValid = false;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError("Invalid email format");
            isValid = false;
        } else {
            etEmail.setError(null);
        }

        // PH Phone validation (strict: 09XXXXXXXXX)
        String phone = etPhone.getText().toString().trim();
        if (phone.isEmpty()) {
            etPhone.setError("Phone number is required");
            isValid = false;
        } else if (!isValidPhilippinePhone(phone)) {
            etPhone.setError("Invalid PH mobile (09XXXXXXXXX)");
            isValid = false;
        } else {
            etPhone.setError(null);
        }

        return isValid;
    }

    private boolean isValidPhilippinePhone(String phone) {
        String clean = phone.replaceAll("[\\s\\-\\(\\)]", "");
        return clean.matches("09\\d{9}"); // 09 + 9 digits = 11 total
    }

    private boolean validateStep2() {
        if (etAddress.getText().toString().trim().isEmpty()) {
            etAddress.setError("Address is required");
            return false;
        }
        etAddress.setError(null);
        return true;
    }

    private boolean validateStep3() {
        boolean isValid = true;
        String password = etPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (password.isEmpty()) {
            etPassword.setError("Password required");
            isValid = false;
        } else if (password.length() < 6) {
            etPassword.setError("Min 6 characters");
            isValid = false;
        } else {
            etPassword.setError(null);
        }

        if (confirm.isEmpty()) {
            etConfirmPassword.setError("Confirm password");
            isValid = false;
        } else if (!password.equals(confirm)) {
            etConfirmPassword.setError("Passwords don't match");
            isValid = false;
        } else {
            etConfirmPassword.setError(null);
        }

        return isValid;
    }

    // ==================== BACK BUTTON ====================

    @Override
    public void onBackPressed() {
        if (currentStep > 1) {
            currentStep--;
            if (currentStep == 1) setupStep1();
            else if (currentStep == 2) setupStep2();
        } else {
            super.onBackPressed();
        }
    }

    // ==================== REGISTRATION ====================

    private void registerUser() {
        // Get all data
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        // Final validation
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email)
                || TextUtils.isEmpty(phone) || TextUtils.isEmpty(address)
                || TextUtils.isEmpty(password)) {
            Toast.makeText(this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Disable button during registration
        btnRegister.setEnabled(false);
        btnRegister.setText("Creating Account...");

        // Firebase Registration
        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");

                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            HashMap<String, Object> userData = new HashMap<>();
                            userData.put("name", name);
                            userData.put("email", email);
                            userData.put("phone", phone);
                            userData.put("address", address);
                            userData.put("role", "customer");

                            usersRef.child(user.getUid()).setValue(userData)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            user.sendEmailVerification();
                                            Toast.makeText(RegisterActivity.this,
                                                    "Registration successful! Please verify your email.",
                                                    Toast.LENGTH_LONG).show();
                                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                            finish();
                                        } else {
                                            Toast.makeText(RegisterActivity.this,
                                                    "Failed to save user data",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        stopLocationUpdates();
    }
}