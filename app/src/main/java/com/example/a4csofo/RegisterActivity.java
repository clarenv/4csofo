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

    // Variables to store location data
    private double currentLatitude = 0;
    private double currentLongitude = 0;

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
                            if (isLocationEnabled()) {
                                checkLocationPermissions();
                            } else {
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
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                })
                .setOnCancelListener(dialog -> {
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
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
                    Toast.makeText(this, "❌ Location Services still disabled", Toast.LENGTH_SHORT).show();
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
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
                    Toast.makeText(this, "❌ Location permission denied", Toast.LENGTH_SHORT).show();
                    address.setFocusableInTouchMode(true);
                    address.requestFocus();
                }
            }
        }
    }

    // ==================== LOCATION FETCHING ====================
    private void fetchCurrentLocation() {
        if (!isLocationEnabled()) {
            Toast.makeText(this, "❌ Please enable Location Services first", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(this, "📍 Getting your location...", Toast.LENGTH_SHORT).show();

        fusedLocationClient.getLastLocation()
                .addOnCompleteListener(this, new OnCompleteListener<Location>() {
                    @Override
                    public void onComplete(@NonNull Task<Location> task) {
                        if (task.isSuccessful() && task.getResult() != null) {
                            Location location = task.getResult();
                            long locationAge = System.currentTimeMillis() - location.getTime();

                            if (locationAge < 2 * 60 * 1000) {
                                currentLatitude = location.getLatitude();
                                currentLongitude = location.getLongitude();
                                startSequentialValidation();
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
                        startSequentialValidation();
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

    private void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            locationCallback = null;
        }
    }

    // ==================== SEQUENTIAL VALIDATION ====================
    private void startSequentialValidation() {
        runOnUiThread(() -> {
            Toast.makeText(RegisterActivity.this, "📍 Validating street address...", Toast.LENGTH_SHORT).show();
        });

        executorService.execute(() -> {
            // STEP 1: Get street address
            String street = getStreetAddress();

            if (street.isEmpty()) {
                runOnUiThread(() -> {
                    showRetryDialog("Street", "Could not detect street address.");
                });
                return;
            }

            final String finalStreet = street;

            runOnUiThread(() -> {
                // Ask user to confirm street
                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle("Confirm Street Address")
                        .setMessage("Detected street: " + finalStreet + "\n\nIs this correct?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // STEP 2: Get barangay
                            getBarangayAddress(finalStreet);
                        })
                        .setNegativeButton("No, Try Again", (dialog, which) -> {
                            showRetryDialog("Street", "Please try getting location again.");
                        })
                        .setCancelable(false)
                        .show();
            });
        });
    }

    private void getBarangayAddress(String confirmedStreet) {
        runOnUiThread(() -> {
            Toast.makeText(RegisterActivity.this, "📍 Validating barangay...", Toast.LENGTH_SHORT).show();
        });

        executorService.execute(() -> {
            String barangay = getBarangayName();

            if (barangay.isEmpty()) {
                runOnUiThread(() -> {
                    showRetryDialog("Barangay", "Could not detect barangay.");
                });
                return;
            }

            final String finalBarangay = barangay;

            runOnUiThread(() -> {
                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle("Confirm Barangay")
                        .setMessage("Detected barangay: " + finalBarangay + "\n\nIs this correct?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // STEP 3: Get city
                            getCityAddress(confirmedStreet, finalBarangay);
                        })
                        .setNegativeButton("No, Try Again", (dialog, which) -> {
                            showRetryDialog("Barangay", "Please try getting location again.");
                        })
                        .setCancelable(false)
                        .show();
            });
        });
    }

    private void getCityAddress(String street, String barangay) {
        runOnUiThread(() -> {
            Toast.makeText(RegisterActivity.this, "📍 Validating city/municipality...", Toast.LENGTH_SHORT).show();
        });

        executorService.execute(() -> {
            String city = getCityName();

            if (city.isEmpty()) {
                runOnUiThread(() -> {
                    showRetryDialog("City/Municipality", "Could not detect city/municipality.");
                });
                return;
            }

            final String finalCity = city;

            runOnUiThread(() -> {
                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle("Confirm City/Municipality")
                        .setMessage("Detected city/municipality: " + finalCity + "\n\nIs this correct?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // STEP 4: Get province
                            getProvinceAddress(street, barangay, finalCity);
                        })
                        .setNegativeButton("No, Try Again", (dialog, which) -> {
                            showRetryDialog("City/Municipality", "Please try getting location again.");
                        })
                        .setCancelable(false)
                        .show();
            });
        });
    }

    private void getProvinceAddress(String street, String barangay, String city) {
        runOnUiThread(() -> {
            Toast.makeText(RegisterActivity.this, "📍 Validating province...", Toast.LENGTH_SHORT).show();
        });

        executorService.execute(() -> {
            String province = getProvinceName();

            if (province.isEmpty() || isRegionName(province)) {
                runOnUiThread(() -> {
                    showRetryDialog("Province", "Could not detect province.");
                });
                return;
            }

            final String finalProvince = province;

            runOnUiThread(() -> {
                new AlertDialog.Builder(RegisterActivity.this)
                        .setTitle("Confirm Province")
                        .setMessage("Detected province: " + finalProvince + "\n\nIs this correct?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            // FINAL: Format and display address
                            String finalAddress = formatFinalAddress(street, barangay, city, finalProvince);
                            address.setText(finalAddress);
                            Toast.makeText(RegisterActivity.this, "✅ Address validated successfully!", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("No, Try Again", (dialog, which) -> {
                            showRetryDialog("Province", "Please try getting location again.");
                        })
                        .setCancelable(false)
                        .show();
            });
        });
    }

    // ==================== ADDRESS COMPONENT EXTRACTION ====================
    private String getStreetAddress() {
        // Try Geocoder first
        String street = getStreetFromGeocoder();
        if (!street.isEmpty()) {
            return formatStreet(street);
        }

        // Fallback to OSM
        street = getStreetFromOSM();
        return formatStreet(street);
    }

    private String getBarangayName() {
        // Try Geocoder first
        String barangay = getBarangayFromGeocoder();
        if (!barangay.isEmpty()) {
            return formatBarangay(barangay);
        }

        // Fallback to OSM
        barangay = getBarangayFromOSM();
        return formatBarangay(barangay);
    }

    private String getCityName() {
        // Try Geocoder first
        String city = getCityFromGeocoder();
        if (!city.isEmpty()) {
            return formatCity(city);
        }

        // Fallback to OSM
        city = getCityFromOSM();
        return formatCity(city);
    }

    private String getProvinceName() {
        // Try Geocoder first
        String province = getProvinceFromGeocoder();
        if (!province.isEmpty() && !isRegionName(province)) {
            return formatProvince(province);
        }

        // Fallback to OSM
        province = getProvinceFromOSM();
        if (!province.isEmpty() && !isRegionName(province)) {
            return formatProvince(province);
        }

        return "";
    }

    // ==================== GEOCODER METHODS ====================
    private String getStreetFromGeocoder() {
        try {
            if (!Geocoder.isPresent()) return "";

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                return addr.getThoroughfare() != null ? addr.getThoroughfare() : "";
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder Error: " + e.getMessage());
        }
        return "";
    }

    private String getBarangayFromGeocoder() {
        try {
            if (!Geocoder.isPresent()) return "";

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);

                // Try sub-locality first
                if (addr.getSubLocality() != null) {
                    String subLocality = addr.getSubLocality();
                    if (isBarangayName(subLocality)) {
                        return subLocality;
                    }
                }

                // Try feature name
                if (addr.getFeatureName() != null) {
                    String featureName = addr.getFeatureName();
                    if (isBarangayName(featureName) &&
                            !featureName.equalsIgnoreCase(addr.getLocality())) {
                        return featureName;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder Error: " + e.getMessage());
        }
        return "";
    }

    private String getCityFromGeocoder() {
        try {
            if (!Geocoder.isPresent()) return "";

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                if (addr.getLocality() != null) {
                    return addr.getLocality();
                } else if (addr.getSubAdminArea() != null) {
                    return addr.getSubAdminArea();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder Error: " + e.getMessage());
        }
        return "";
    }

    private String getProvinceFromGeocoder() {
        try {
            if (!Geocoder.isPresent()) return "";

            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            List<Address> addresses = geocoder.getFromLocation(currentLatitude, currentLongitude, 1);

            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                if (addr.getAdminArea() != null) {
                    return addr.getAdminArea();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Geocoder Error: " + e.getMessage());
        }
        return "";
    }

    // ==================== OSM METHODS ====================
    private String getStreetFromOSM() {
        try {
            JSONObject json = getOSMData();
            if (json != null) {
                JSONObject addressObj = json.optJSONObject("address");
                if (addressObj != null) {
                    String street = addressObj.optString("road", "");
                    if (street.isEmpty()) {
                        street = addressObj.optString("pedestrian", "");
                    }
                    if (street.isEmpty()) {
                        street = addressObj.optString("footway", "");
                    }
                    return street;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    private String getBarangayFromOSM() {
        try {
            JSONObject json = getOSMData();
            if (json != null) {
                JSONObject addressObj = json.optJSONObject("address");
                if (addressObj != null) {
                    if (addressObj.has("village")) {
                        return addressObj.optString("village", "");
                    } else if (addressObj.has("suburb")) {
                        String suburb = addressObj.optString("suburb", "");
                        if (isBarangayName(suburb)) {
                            return suburb;
                        }
                    } else if (addressObj.has("hamlet")) {
                        String hamlet = addressObj.optString("hamlet", "");
                        if (isBarangayName(hamlet)) {
                            return hamlet;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    private String getCityFromOSM() {
        try {
            JSONObject json = getOSMData();
            if (json != null) {
                JSONObject addressObj = json.optJSONObject("address");
                if (addressObj != null) {
                    if (addressObj.has("city")) {
                        return addressObj.optString("city", "");
                    } else if (addressObj.has("town")) {
                        return addressObj.optString("town", "");
                    } else if (addressObj.has("municipality")) {
                        return addressObj.optString("municipality", "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    private String getProvinceFromOSM() {
        try {
            JSONObject json = getOSMData();
            if (json != null) {
                JSONObject addressObj = json.optJSONObject("address");
                if (addressObj != null) {
                    if (addressObj.has("state")) {
                        return addressObj.optString("state", "");
                    } else if (addressObj.has("county")) {
                        return addressObj.optString("county", "");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return "";
    }

    private JSONObject getOSMData() {
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

                return new JSONObject(response.toString());
            }
            conn.disconnect();
        } catch (Exception e) {
            Log.e(TAG, "OSM Error: " + e.getMessage());
        }
        return null;
    }

    // ==================== FORMATTING FUNCTIONS ====================
    private String formatStreet(String street) {
        if (street == null || street.trim().isEmpty()) {
            return "";
        }

        street = street.trim();
        String lowerStreet = street.toLowerCase();

        // Add proper suffix if missing
        if (!lowerStreet.contains("street") &&
                !lowerStreet.contains("st.") &&
                !lowerStreet.contains("st ") &&
                !lowerStreet.contains("avenue") &&
                !lowerStreet.contains("ave") &&
                !lowerStreet.contains("road") &&
                !lowerStreet.contains("rd") &&
                !lowerStreet.contains("boulevard") &&
                !lowerStreet.contains("blvd")) {
            street = street + " St.";
        }

        return capitalizeWords(street);
    }

    private String formatBarangay(String barangay) {
        if (barangay == null || barangay.trim().isEmpty()) {
            return "";
        }

        barangay = barangay.trim();
        String lowerBarangay = barangay.toLowerCase();

        // Add "Barangay" prefix if missing
        if (!lowerBarangay.startsWith("barangay ") &&
                !lowerBarangay.startsWith("brgy ") &&
                !lowerBarangay.startsWith("bgy ")) {
            barangay = "Barangay " + barangay;
        }

        return capitalizeWords(barangay);
    }

    private String formatCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            return "";
        }

        city = city.trim();

        // Remove unnecessary prefixes
        city = city.replace("City of ", "")
                .replace("Municipality of ", "")
                .replace("City ", "")
                .replace("Municipality ", "")
                .trim();

        return capitalizeWords(city);
    }

    private String formatProvince(String province) {
        if (province == null || province.trim().isEmpty()) {
            return "";
        }

        province = province.trim();

        // Remove unnecessary prefixes
        province = province.replace("Province of ", "")
                .replace("Province ", "")
                .trim();

        return capitalizeWords(province);
    }

    private String formatFinalAddress(String street, String barangay, String city, String province) {
        // Strict format: [street], [barangay], [city], [province]
        StringBuilder addressBuilder = new StringBuilder();

        if (!street.isEmpty()) {
            addressBuilder.append(street);
        }

        if (!barangay.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(barangay);
        }

        if (!city.isEmpty()) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(city);
        }

        if (!province.isEmpty() && !isRegionName(province)) {
            if (addressBuilder.length() > 0) addressBuilder.append(", ");
            addressBuilder.append(province);
        }

        return addressBuilder.toString();
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isEmpty()) return text;

        String[] words = text.split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0)))
                        .append(word.substring(1).toLowerCase())
                        .append(" ");
            }
        }

        return result.toString().trim();
    }

    private boolean isBarangayName(String text) {
        if (text == null) return false;
        String lowerText = text.toLowerCase();
        return lowerText.contains("barangay") ||
                lowerText.contains("brgy") ||
                lowerText.contains("bgy") ||
                lowerText.contains("village");
    }

    private boolean isRegionName(String area) {
        if (area == null) return false;
        String lowerArea = area.toLowerCase();
        return lowerArea.contains("calabarzon") ||
                lowerArea.contains("region") ||
                lowerArea.equals("ncr") ||
                lowerArea.contains("car") ||
                lowerArea.contains("bangsamoro") ||
                lowerArea.contains("administrative region") ||
                lowerArea.contains("national capital region");
    }

    // ==================== ERROR HANDLING ====================
    private void showLocationError() {
        runOnUiThread(() -> {
            isGettingLocation = false;
            Toast.makeText(RegisterActivity.this,
                    "❌ Could not get location. Please:\n1. Enable Location Services\n2. Check GPS/WiFi signal\n3. Try again",
                    Toast.LENGTH_LONG).show();

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

    private void showRetryDialog(String component, String message) {
        runOnUiThread(() -> {
            new AlertDialog.Builder(RegisterActivity.this)
                    .setTitle("Issue with " + component)
                    .setMessage(message + "\n\nWould you like to:")
                    .setPositiveButton("Try Again", (dialog, which) -> {
                        fetchCurrentLocation();
                    })
                    .setNegativeButton("Enter Manually", (dialog, which) -> {
                        address.setFocusableInTouchMode(true);
                        address.requestFocus();
                    })
                    .setCancelable(false)
                    .show();
        });
    }

    // ==================== REGISTRATION ====================
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