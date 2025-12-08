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

    EditText name, email, phone, address, password, confirmPassword;
    Button btnRegister;
    TextView loginLink;
    FirebaseAuth auth;
    DatabaseReference usersRef;
    FusedLocationProviderClient fusedLocationClient;

    private LocationCallback locationCallback;
    private ExecutorService executorService;
    private boolean isGettingLocation = false;

    private boolean locationConfirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        auth = FirebaseAuth.getInstance();
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        executorService = Executors.newSingleThreadExecutor();

        name = findViewById(R.id.etName);
        email = findViewById(R.id.etEmail);
        phone = findViewById(R.id.etPhone);
        address = findViewById(R.id.etAddress);
        password = findViewById(R.id.etPassword);
        confirmPassword = findViewById(R.id.etConfirmPassword);
        btnRegister = findViewById(R.id.btnRegister);
        loginLink = findViewById(R.id.loginLink);

        btnRegister.setOnClickListener(v -> registerUser());
        loginLink.setOnClickListener(v -> startActivity(new Intent(RegisterActivity.this, LoginActivity.class)));

        address.setOnClickListener(v -> {
            if (!locationConfirmed) {
                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle("Use Current Location?")
                        .setMessage("Do you want to auto-fill your address using your current location?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            locationConfirmed = true;

                            // STEP 1: Check if Location Services is ON in device settings
                            if (isLocationEnabled()) {
                                // STEP 2: Check app permissions
                                checkLocationPermissions();
                            } else {
                                // Show alert to turn ON Location Services
                                showEnableLocationDialog();
                            }
                        })
                        .setNegativeButton("No", (dialog, which) -> {
                            locationConfirmed = true;
                            address.setFocusableInTouchMode(true);
                            address.requestFocus();
                        })
                        .show();
            } else {
                address.setFocusableInTouchMode(true);
                address.requestFocus();
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

    // -----------------------------
    // CHECK IF LOCATION SERVICES IS ENABLED ON DEVICE
    // -----------------------------
    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    // -----------------------------
    // SHOW DIALOG TO ENABLE LOCATION SERVICES
    // -----------------------------
    private void showEnableLocationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Turn On Location")
                .setMessage("Location Services is required to detect your address.\n\n" +
                        "Please enable Location Services in your device settings.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    // Open device settings to enable location
                    Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivityForResult(intent, LOCATION_SETTINGS_REQUEST);
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                })
                .setOnCancelListener(dialog -> {
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                })
                .show();
    }

    // -----------------------------
    // CHECK APP PERMISSIONS
    // -----------------------------
    private void checkLocationPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {

            // Permissions already granted, get location
            fetchCurrentLocation();
        } else {
            // Request permissions
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST);
        }
    }

    // -----------------------------
    // HANDLE RESULT FROM LOCATION SETTINGS
    // -----------------------------
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == LOCATION_SETTINGS_REQUEST) {
            // User returned from Location Settings
            // Wait a moment for settings to apply
            new Handler().postDelayed(() -> {
                if (isLocationEnabled()) {
                    // Location is now ON, check permissions
                    checkLocationPermissions();
                } else {
                    Toast.makeText(this, "❌ Location Services still disabled", Toast.LENGTH_SHORT).show();
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                }
            }, 1000);
        }
    }

    // -----------------------------
    // PERMISSION RESULT
    // -----------------------------
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
                    // Permissions granted, get location
                    fetchCurrentLocation();
                } else {
                    Toast.makeText(this, "❌ Location permission denied", Toast.LENGTH_SHORT).show();
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                }
            }
        }
    }

    // -----------------------------
    // IMPROVED LOCATION FETCHING
    // -----------------------------
    private void fetchCurrentLocation() {
        // Double-check location is enabled
        if (!isLocationEnabled()) {
            Toast.makeText(this, "❌ Please enable Location Services first", Toast.LENGTH_SHORT).show();
            showEnableLocationDialog();
            return;
        }

        // Double-check permissions
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            checkLocationPermissions();
            return;
        }

        if (isGettingLocation) {
            Toast.makeText(this, "📍 Already getting location...", Toast.LENGTH_SHORT).show();
            return;
        }

        isGettingLocation = true;
        Toast.makeText(this, "📍 Getting your location...", Toast.LENGTH_SHORT).show();

        // METHOD 1: Try getLastLocation first (fastest, uses cached location)
        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            Log.d(TAG, "getLastLocation success: " + location.getLatitude() + ", " + location.getLongitude());

                            // Check if location is fresh enough (less than 2 minutes old)
                            long locationAge = System.currentTimeMillis() - location.getTime();
                            if (locationAge < 2 * 60 * 1000) { // 2 minutes
                                processLocation(location.getLatitude(), location.getLongitude());
                                isGettingLocation = false;
                                return;
                            }
                        }

                        // METHOD 2: If last location is old or null, request new location
                        requestNewLocation();
                    }
                });
    }

    private void requestNewLocation() {
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY); // Uses GPS, WiFi, cell towers
        locationRequest.setInterval(10000);
        locationRequest.setFastestInterval(5000);
        locationRequest.setNumUpdates(1);
        locationRequest.setMaxWaitTime(15000);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.e(TAG, "LocationResult is null");
                    showLocationError();
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    if (location != null) {
                        Log.d(TAG, "New location: " + location.getLatitude() + ", " + location.getLongitude());
                        processLocation(location.getLatitude(), location.getLongitude());
                        stopLocationUpdates();
                        isGettingLocation = false;
                        return;
                    }
                }
                showLocationError();
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            showLocationError();
            return;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);

        // Timeout after 15 seconds
        new android.os.Handler().postDelayed(() -> {
            if (isGettingLocation) {
                Log.e(TAG, "Location request timeout");
                showLocationError();
                stopLocationUpdates();
            }
        }, 15000);
    }

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
                    "❌ Could not get location. Please:\n1. Enable Location Services\n2. Check GPS/WiFi signal\n3. Try again",
                    Toast.LENGTH_LONG).show();

            // Show option to enter manually
            new AlertDialog.Builder(RegisterActivity.this)
                    .setTitle("Location Unavailable")
                    .setMessage("Would you like to enter your address manually?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        address.setFocusableInTouchMode(true);
                        address.requestFocus();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void processLocation(double lat, double lon) {
        runOnUiThread(() -> {
            Toast.makeText(RegisterActivity.this, "📍 Detecting address...", Toast.LENGTH_SHORT).show();
        });

        // RUN BOTH SERVICES IN BACKGROUND THREAD
        executorService.execute(() -> {
            // ANDROID GEOCODER (Primary - faster)
            String geocoderAddress = getAddressFromAndroidGeocoder(lat, lon);

            // OSM (Secondary - better barangay data)
            String osmAddress = getAddressFromOSM(lat, lon);

            // DECIDE WHICH ADDRESS TO USE
            runOnUiThread(() -> {
                String finalAddress = selectBestAddress(geocoderAddress, osmAddress);

                if (!finalAddress.isEmpty()) {
                    address.setText(finalAddress);
                    Toast.makeText(RegisterActivity.this,
                            "📍 Address detected",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(RegisterActivity.this,
                            "⚠️ Could not detect address. Please enter manually.",
                            Toast.LENGTH_LONG).show();
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                }
            });
        });
    }

    // -----------------------------
    // ANDROID GEOCODER
    // -----------------------------
    private String getAddressFromAndroidGeocoder(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                return formatPhilippineAddress(
                        addr.getThoroughfare(),          // street
                        extractBarangayFromGeocoder(addr), // barangay
                        addr.getLocality(),              // city/municipality
                        addr.getAdminArea()              // province
                );
            }
        } catch (Exception e) {
            Log.e(TAG, "Android Geocoder Error: " + e.getMessage());
        }
        return "";
    }

    private String extractBarangayFromGeocoder(Address addr) {
        // Try subLocality first
        if (addr.getSubLocality() != null) {
            String subLocality = addr.getSubLocality();
            if (isBarangayName(subLocality)) {
                return subLocality;
            }
        }

        // Try feature name
        if (addr.getFeatureName() != null) {
            String featureName = addr.getFeatureName();
            if (isBarangayName(featureName)) {
                return featureName;
            }
        }

        // Check address lines
        for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
            String line = addr.getAddressLine(i);
            if (line != null && isBarangayName(line)) {
                return extractBarangayFromString(line);
            }
        }

        return "";
    }

    private boolean isBarangayName(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        return lowerText.contains("barangay") ||
                lowerText.contains("brgy") ||
                lowerText.contains("bgy") ||
                lowerText.contains("village");
    }

    private String extractBarangayFromString(String text) {
        // Extract barangay name from string
        String[] words = text.split("[,\\s]+");
        for (String word : words) {
            if (isBarangayName(word)) {
                return text; // Return the whole string containing barangay
            }
        }
        return "";
    }

    // -----------------------------
    // OPENSTREETMAP
    // -----------------------------
    private String getAddressFromOSM(double lat, double lon) {
        try {
            String urlString = String.format(Locale.US,
                    "%s?format=json&lat=%.6f&lon=%.6f&zoom=18" +
                            "&addressdetails=1&countrycodes=ph&accept-language=en",
                    OSM_NOMINATIM_URL, lat, lon);

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
                return extractAddressFromOSM(json);
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    private String extractAddressFromOSM(JSONObject json) {
        try {
            JSONObject addressObj = json.optJSONObject("address");
            if (addressObj == null) return "";

            String street = addressObj.optString("road", "");
            String barangay = "";
            String city = "";
            String province = "";

            // GET BARANGAY
            if (addressObj.has("village")) {
                barangay = addressObj.optString("village", "");
            } else if (addressObj.has("suburb")) {
                String suburb = addressObj.optString("suburb", "");
                if (isBarangayName(suburb)) {
                    barangay = suburb;
                }
            } else if (addressObj.has("hamlet")) {
                String hamlet = addressObj.optString("hamlet", "");
                if (isBarangayName(hamlet)) {
                    barangay = hamlet;
                }
            }

            // GET CITY/MUNICIPALITY
            if (addressObj.has("city")) {
                city = addressObj.optString("city", "");
            } else if (addressObj.has("town")) {
                city = addressObj.optString("town", "");
            } else if (addressObj.has("municipality")) {
                city = addressObj.optString("municipality", "");
            }

            // GET PROVINCE (filter regions)
            if (addressObj.has("state")) {
                String state = addressObj.optString("state", "");
                if (!isRegionName(state)) {
                    province = state;
                }
            } else if (addressObj.has("county")) {
                province = addressObj.optString("county", "");
            }

            return formatPhilippineAddress(street, barangay, city, province);

        } catch (Exception e) {
            Log.e(TAG, "OSM Parse Error: " + e.getMessage());
            return "";
        }
    }

    // -----------------------------
    // FORMAT PHILIPPINE ADDRESS
    // -----------------------------
    private String formatPhilippineAddress(String street, String barangay, String city, String province) {
        StringBuilder addressBuilder = new StringBuilder();

        // STREET
        if (street != null && !street.trim().isEmpty()) {
            addressBuilder.append(street.trim());
        }

        // BARANGAY (ensure format)
        if (barangay != null && !barangay.trim().isEmpty()) {
            String bar = barangay.trim();
            String lowerBar = bar.toLowerCase();

            // Add "Barangay" prefix if missing
            if (!lowerBar.contains("barangay") &&
                    !lowerBar.contains("brgy") &&
                    !lowerBar.contains("bgy")) {
                bar = "Barangay " + bar;
            }

            if (addressBuilder.length() > 0) {
                addressBuilder.append(", ");
            }
            addressBuilder.append(bar);
        }

        // CITY/MUNICIPALITY
        if (city != null && !city.trim().isEmpty()) {
            if (addressBuilder.length() > 0) {
                addressBuilder.append(", ");
            }
            addressBuilder.append(city.trim());
        }

        // PROVINCE (skip if empty or region name)
        if (province != null && !province.trim().isEmpty() && !isRegionName(province)) {
            if (addressBuilder.length() > 0) {
                addressBuilder.append(", ");
            }
            addressBuilder.append(province.trim());
        }

        return addressBuilder.toString().trim();
    }

    private boolean isRegionName(String area) {
        if (area == null) return false;
        String lowerArea = area.toLowerCase();
        return lowerArea.contains("calabarzon") ||
                lowerArea.contains("region") ||
                lowerArea.equals("ncr") ||
                lowerArea.contains("car") ||
                lowerArea.contains("bangsamoro");
    }

    // -----------------------------
    // SELECT BEST ADDRESS
    // -----------------------------
    private String selectBestAddress(String geocoderAddress, String osmAddress) {
        Log.d(TAG, "Geocoder: " + geocoderAddress);
        Log.d(TAG, "OSM: " + osmAddress);

        // If both empty, return empty
        if (geocoderAddress.isEmpty() && osmAddress.isEmpty()) {
            return "";
        }

        // If one is empty, return the other
        if (geocoderAddress.isEmpty()) return osmAddress;
        if (osmAddress.isEmpty()) return geocoderAddress;

        // Score each address (higher score = better)
        int geocoderScore = scoreAddress(geocoderAddress);
        int osmScore = scoreAddress(osmAddress);

        Log.d(TAG, "Geocoder Score: " + geocoderScore);
        Log.d(TAG, "OSM Score: " + osmScore);

        // Return address with higher score
        if (osmScore > geocoderScore) {
            return osmAddress;
        } else {
            return geocoderAddress;
        }
    }

    private int scoreAddress(String address) {
        int score = 0;
        String lowerAddress = address.toLowerCase();

        // Points for having barangay
        if (lowerAddress.contains("barangay") ||
                lowerAddress.contains("brgy") ||
                lowerAddress.contains("bgy")) {
            score += 10;
        }

        // Points for having street
        if (address.split(",").length > 0) {
            score += 5;
        }

        // Points for having city
        if (hasCityOrMunicipality(lowerAddress)) {
            score += 5;
        }

        // Points for having province
        if (hasProvince(lowerAddress)) {
            score += 3;
        }

        // Penalty for having region names
        if (lowerAddress.contains("calabarzon") ||
                lowerAddress.contains("region")) {
            score -= 5;
        }

        return score;
    }

    private boolean hasCityOrMunicipality(String address) {
        // Common city indicators (you can expand this list)
        String[] cityIndicators = {
                "city", "municipality", "town", "municipal"
        };

        for (String indicator : cityIndicators) {
            if (address.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasProvince(String address) {
        // Common province indicators (you can expand this list)
        String[] provinceIndicators = {
                "province", "prov.", "prov"
        };

        for (String indicator : provinceIndicators) {
            if (address.contains(indicator)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------
    // REGISTER USER
    // -----------------------------
    private void registerUser() {
        String fullName = name.getText().toString().trim();
        String userEmail = email.getText().toString().trim();
        String userPhone = phone.getText().toString().trim();
        String userAddress = address.getText().toString().trim();
        String userPassword = password.getText().toString().trim();
        String confirmPass = confirmPassword.getText().toString().trim();

        // VALIDATIONS
        if (TextUtils.isEmpty(fullName) || TextUtils.isEmpty(userEmail)
                || TextUtils.isEmpty(userPhone) || TextUtils.isEmpty(userAddress)
                || TextUtils.isEmpty(userPassword) || TextUtils.isEmpty(confirmPass)) {
            Toast.makeText(this, "⚠️ All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(userEmail).matches()) {
            Toast.makeText(this, "❌ Invalid email format.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!Patterns.PHONE.matcher(userPhone).matches() || userPhone.length() < 10) {
            Toast.makeText(this, "❌ Invalid phone number.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (userPassword.length() < 6) {
            Toast.makeText(this, "❌ Password must be at least 6 characters.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!userPassword.equals(confirmPass)) {
            Toast.makeText(this, "❌ Passwords do not match.", Toast.LENGTH_SHORT).show();
            return;
        }

        // CREATE USER
        btnRegister.setEnabled(false);
        btnRegister.setText("Registering...");

        auth.createUserWithEmailAndPassword(userEmail, userPassword)
                .addOnCompleteListener(task -> {
                    btnRegister.setEnabled(true);
                    btnRegister.setText("Create Account");

                    if (task.isSuccessful()) {
                        FirebaseUser user = auth.getCurrentUser();
                        if (user != null) {
                            HashMap<String, Object> userData = new HashMap<>();
                            userData.put("name", fullName);
                            userData.put("email", userEmail);
                            userData.put("phone", userPhone);
                            userData.put("address", userAddress);
                            userData.put("role", "customer");

                            usersRef.child(user.getUid()).setValue(userData)
                                    .addOnCompleteListener(dbTask -> {
                                        if (dbTask.isSuccessful()) {
                                            user.sendEmailVerification();
                                            Toast.makeText(RegisterActivity.this,
                                                    "✅ Registration successful! Please verify your email.",
                                                    Toast.LENGTH_LONG).show();
                                            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                                            finish();
                                        } else {
                                            Toast.makeText(RegisterActivity.this,
                                                    "❌ Failed to save user info.",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                    });
                        }
                    } else {
                        Toast.makeText(RegisterActivity.this,
                                "❌ Registration failed: " + task.getException().getMessage(),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}