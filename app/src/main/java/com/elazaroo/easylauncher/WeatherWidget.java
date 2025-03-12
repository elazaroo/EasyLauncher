package com.elazaroo.easylauncher;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Locale;

public class WeatherWidget {
    private static final String TAG = "WeatherWidget";
    private static final String API_KEY = "JGgP5MK6OgRrg58yeGXQDAjRz9ZN5PjI"; // Free API KEY XD
    private static final String BASE_URL = "https://dataservice.accuweather.com/currentconditions/v1/";

    private Context context;
    private View widgetView;
    private ImageView weatherIcon;
    private TextView weatherCity;
    private TextView weatherDescription;
    private OkHttpClient client;
    private FusedLocationProviderClient fusedLocationClient;
    private Handler weatherUpdateHandler;
    private double lastKnownLatitude = 0.0;
    private double lastKnownLongitude = 0.0;
    private boolean isFetchingWeather = false;
    private LocationPermissionCallback permissionCallback;

    public WeatherWidget(Context context, View widgetView) {
        this.context = context;
        this.widgetView = widgetView;
        this.weatherIcon = widgetView.findViewById(R.id.weather_icon);
        this.weatherCity = widgetView.findViewById(R.id.weather_city);
        this.weatherDescription = widgetView.findViewById(R.id.weather_description);
        this.client = OkHttpClientProvider.getOkHttpClient();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.weatherUpdateHandler = new Handler(Looper.getMainLooper());

        Log.d(TAG, "WeatherWidget constructor called");
        requestLocationUpdates();
        scheduleWeatherUpdates();
    }

    public WeatherWidget(Context context, View rootView, boolean hasLocationPermission, LocationPermissionCallback callback) {
        this.context = context;
        this.widgetView = rootView;
        this.weatherIcon = rootView.findViewById(R.id.weather_icon);
        this.weatherCity = rootView.findViewById(R.id.weather_city);
        this.weatherDescription = rootView.findViewById(R.id.weather_description);
        this.client = OkHttpClientProvider.getOkHttpClient();
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        this.weatherUpdateHandler = new Handler(Looper.getMainLooper());
        this.permissionCallback = callback;
    
        Log.d(TAG, "WeatherWidget constructor called");
    
        if (hasLocationPermission) {
            requestLocationUpdates();
            scheduleWeatherUpdates();
        } else {
            displayNoLocationState();
        }
    }

    private void displayNoLocationState() {
        weatherCity.setText(R.string.no_location);
        weatherDescription.setText(R.string.location_permission_denied);
        weatherIcon.setImageResource(R.drawable.ic_location_off);
        
        widgetView.setOnClickListener(v -> {
            if (permissionCallback != null) {
                Toast.makeText(context, R.string.requesting_location_permission, Toast.LENGTH_SHORT).show();
                permissionCallback.requestLocationPermission();
            }
        });
    }

    private void scheduleWeatherUpdates() {
        weatherUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (lastKnownLatitude != 0.0 && lastKnownLongitude != 0.0) {
                    fetchWeatherData(lastKnownLatitude, lastKnownLongitude);
                }

                weatherUpdateHandler.postDelayed(this, 60 * 60 * 1000);
            }
        }, 60 * 60 * 1000);
    }


    @SuppressLint("MissingPermission")
    private void requestLocationUpdates() {
        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                Log.d(TAG, "Last known location: " + location.getLatitude() + ", " + location.getLongitude());
                lastKnownLatitude = location.getLatitude();
                lastKnownLongitude = location.getLongitude();
                fetchWeatherData(lastKnownLatitude, lastKnownLongitude);
            }
        });

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(60 * 60 * 1000)
                .setFastestInterval(15 * 60 * 1000);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            if (locationResult == null) return;
            for (Location location : locationResult.getLocations()) {
                double newLatitude = location.getLatitude();
                double newLongitude = location.getLongitude();

                if (Math.abs(newLatitude - lastKnownLatitude) > 0.001 || Math.abs(newLongitude - lastKnownLongitude) > 0.001) {
                    lastKnownLatitude = newLatitude;
                    lastKnownLongitude = newLongitude;
                    Log.d(TAG, "Location updated: " + lastKnownLatitude + ", " + lastKnownLongitude);
                    fetchWeatherData(lastKnownLatitude, lastKnownLongitude);
                } else {
                    Log.d(TAG, "Location change too small, ignoring update.");
                }
            }
        }
    };


    private void fetchWeatherData(double latitude, double longitude) {
        if (isFetchingWeather) {
            Log.d(TAG, "Weather fetch already in progress, skipping.");
            return;
        }

        if (isWeatherDataRecent(latitude, longitude)) {
            Log.d(TAG, "Weather data is recent, skipping API call.");
            return;
        }

        isFetchingWeather = true;
        String locationUrl = "https://dataservice.accuweather.com/locations/v1/cities/geoposition/search?apikey=" + API_KEY + "&q=" + latitude + "," + longitude;
        Log.d(TAG, "Fetching location key from URL: " + locationUrl);

        Request locationRequest = new Request.Builder().url(locationUrl).build();
        client.newCall(locationRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch location key", e);
                isFetchingWeather = false;
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                isFetchingWeather = false;
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to fetch location key: " + response.message());
                    return;
                }

                try {
                    JSONObject locationJson = new JSONObject(response.body().string());
                    String cityName = locationJson.getString("LocalizedName");
                    String locationKey = locationJson.getString("Key");

                    fetchCurrentConditions(locationKey, cityName, latitude, longitude);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse location key", e);
                }
            }
        });
    }

    private void fetchCurrentConditions(String locationKey, String cityName, double latitude, double longitude) {
        String language = Locale.getDefault().getLanguage();
        String weatherUrl = BASE_URL + locationKey + "?apikey=" + API_KEY + "&language=" + language;
        Log.d(TAG, "Fetching weather data from URL: " + weatherUrl);

        Request weatherRequest = new Request.Builder().url(weatherUrl).build();
        client.newCall(weatherRequest).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Failed to fetch weather data", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "Failed to fetch weather data: " + response.message());
                    return;
                }

                try {
                    org.json.JSONArray weatherArray = new org.json.JSONArray(response.body().string());

                    if (weatherArray.length() > 0) {
                        JSONObject weatherJson = weatherArray.getJSONObject(0);

                        String description = weatherJson.getString("WeatherText");
                        String uri = weatherJson.getString("MobileLink");

                        int iconNumber = weatherJson.getInt("WeatherIcon");
                        String formattedIconNumber = String.format("%02d", iconNumber);
                        String iconUrl = "https://developer.accuweather.com/sites/default/files/" + formattedIconNumber + "-s.png";

                        new Handler(Looper.getMainLooper()).post(() -> {
                            updateWeatherWidget(cityName, description, iconUrl);

                            widgetView.setOnClickListener(v -> {
                                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                                context.startActivity(intent);
                            });

                            saveLastWeatherData(latitude, longitude, System.currentTimeMillis(), cityName, description, iconUrl);
                        });
                    } else {
                        Log.e(TAG, "Weather data array is empty");
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to parse weather data", e);
                }
            }
        });
    }

    private void updateWeatherWidget(String cityName, String description, String iconUrl) {
        Log.d(TAG, "Updating weather widget with city: " + cityName + ", description: " + description + " and icon URL: " + iconUrl);

        new Handler(Looper.getMainLooper()).post(() -> {
            weatherCity.setText(cityName);
            weatherDescription.setText(description);
            Glide.with(context).load(iconUrl).into(weatherIcon);
        });
    }

    private void saveLastWeatherData(double latitude, double longitude, long timestamp, String cityName, String description, String iconUrl) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("WeatherWidgetPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putLong("lastLatitude", Double.doubleToRawLongBits(latitude));
        editor.putLong("lastLongitude", Double.doubleToRawLongBits(longitude));
        editor.putLong("lastUpdateTime", timestamp);
        editor.putString("lastCityName", cityName);
        editor.putString("lastDescription", description);
        editor.putString("lastIconUrl", iconUrl);
        editor.apply();
    }

    private boolean isWeatherDataRecent(double latitude, double longitude) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("WeatherWidgetPrefs", Context.MODE_PRIVATE);
        long lastLatitudeBits = sharedPreferences.getLong("lastLatitude", Double.doubleToRawLongBits(0.0));
        long lastLongitudeBits = sharedPreferences.getLong("lastLongitude", Double.doubleToRawLongBits(0.0));
        long lastUpdateTime = sharedPreferences.getLong("lastUpdateTime", 0);

        double lastLatitude = Double.longBitsToDouble(lastLatitudeBits);
        double lastLongitude = Double.longBitsToDouble(lastLongitudeBits);
        long currentTime = System.currentTimeMillis();

        boolean isRecent = (Math.abs(latitude - lastLatitude) < 0.001 && Math.abs(longitude - lastLongitude) < 0.001 && (currentTime - lastUpdateTime) < 60 * 60 * 1000);

        if (isRecent) {
            String cityName = sharedPreferences.getString("lastCityName", "");
            String description = sharedPreferences.getString("lastDescription", "");
            String iconUrl = sharedPreferences.getString("lastIconUrl", "");
            updateWeatherWidget(cityName, description, iconUrl);
        }

        return isRecent;
    }

    public interface LocationPermissionCallback {
        void requestLocationPermission();
    }
}
