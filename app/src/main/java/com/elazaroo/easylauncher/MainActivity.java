package com.elazaroo.easylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.DragEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.GridLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import android.Manifest;

public class MainActivity extends AppCompatActivity {
    private GridLayout layoutButtons;
    private ArrayList<String> selectedApps = new ArrayList<>();
    private HashMap<String, String> appPackageMap = new HashMap<>();
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private BroadcastReceiver appChangeReceiver;
    private boolean lastLocationPermissionState;
    private Handler permissionCheckHandler = new Handler(Looper.getMainLooper());
    private Runnable permissionCheckRunnable = new Runnable() {
        @Override
        public void run() {
            boolean currentPermissionState = checkLocationPermission();
            if (currentPermissionState != lastLocationPermissionState) {
                lastLocationPermissionState = currentPermissionState;
                
                if (currentPermissionState) {
                    Toast.makeText(MainActivity.this, "Ubicación activada", Toast.LENGTH_SHORT).show();
                    initializeWeatherWidget();
                } else {
                    Toast.makeText(MainActivity.this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                    initializeWeatherWidgetWithoutLocation();
                }
            }
            
            permissionCheckHandler.postDelayed(this, 30000); 
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        lastLocationPermissionState = checkLocationPermission();
        if (lastLocationPermissionState) {
            initializeWeatherWidget();
        } else {
            requestLocationPermission();
        }

        layoutButtons = findViewById(R.id.layout_buttons);
        findViewById(R.id.btn_edit_home).setOnClickListener(v -> openAppSelector());

        loadAppPackageMap();
        loadSelectedApps();
        
        registerAppChangeReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        boolean currentPermissionState = checkLocationPermission();
        if (currentPermissionState != lastLocationPermissionState) {
            lastLocationPermissionState = currentPermissionState;
            
            if (currentPermissionState) {
                Toast.makeText(this, "Ubicación activada", Toast.LENGTH_SHORT).show();
                initializeWeatherWidget();
            } else {
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                initializeWeatherWidgetWithoutLocation();
            }
        }
        
        permissionCheckHandler.post(permissionCheckRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        permissionCheckHandler.removeCallbacks(permissionCheckRunnable);
    }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeWeatherWidget();
            } else {
                Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show();
                initializeWeatherWidgetWithoutLocation();
            }
        }
    }

    private void initializeWeatherWidgetWithoutLocation() {
        View weatherWidgetView = findViewById(R.id.weather_widget);
        new WeatherWidget(this, weatherWidgetView, false, new WeatherWidget.LocationPermissionCallback() {
            @Override
            public void requestLocationPermission() {
                ActivityCompat.requestPermissions(MainActivity.this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    LOCATION_PERMISSION_REQUEST_CODE);
            }
        });
    }

    private void initializeWeatherWidget() {
        View weatherWidgetView = findViewById(R.id.weather_widget);
        new WeatherWidget(this, weatherWidgetView, true, new WeatherWidget.LocationPermissionCallback() {
            @Override
            public void requestLocationPermission() {
                ActivityCompat.requestPermissions(MainActivity.this, 
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 
                    LOCATION_PERMISSION_REQUEST_CODE);
            }
        });
    }

    private void openAppSelector() {
        Intent intent = new Intent(this, AppSelectorActivity.class);
        startActivityForResult(intent, 1);
    }

    private ArrayList<String> mergeSelectedApps(ArrayList<String> newSelection) {
        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String savedApps = prefs.getString("selected_apps", null);
        ArrayList<String> oldOrder = new ArrayList<>();
        if (savedApps != null) {
            String[] appsArray = savedApps.split(",");
            for (String app : appsArray) {
                oldOrder.add(app.trim());
            }
        }

        ArrayList<String> mergedOrder = new ArrayList<>();

        for (String app : oldOrder) {
            if (newSelection.contains(app)) {
                mergedOrder.add(app);
            }
        }

        for (String app : newSelection) {
            if (!oldOrder.contains(app)) {
                mergedOrder.add(app);
            }
        }

        return mergedOrder;
    }



    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            ArrayList<String> newSelection = data.getStringArrayListExtra("selected_apps");
            selectedApps = mergeSelectedApps(newSelection);
            saveSelectedAppsToPrefs(selectedApps);
            updateButtons();
        }
    }


    private void loadSelectedApps() {
        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String savedApps = prefs.getString("selected_apps", null);
        if (savedApps != null) {
            String[] appsArray = savedApps.split(",");
            selectedApps.clear();
            
            ArrayList<String> stillInstalledApps = new ArrayList<>();
            
            for (String appName : appsArray) {
                String trimmedAppName = appName.trim();
                String packageName = appPackageMap.get(trimmedAppName);
                
                if (packageName != null && isAppInstalled(packageName)) {
                    stillInstalledApps.add(trimmedAppName);
                }
            }
            
            selectedApps.addAll(stillInstalledApps);
            
            if (stillInstalledApps.size() < appsArray.length) {
                saveSelectedAppsToPrefs(selectedApps);
            }
        }
        updateButtons();
    }

    private boolean isAppInstalled(String packageName) {
        try {
            getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void updateButtons() {
        layoutButtons.removeAllViews();
        for (String appName : selectedApps) {
            View buttonView = createAppButton(appName);
            layoutButtons.addView(buttonView);
        }
        saveSelectedAppsToPrefs(selectedApps);
    }

    private void setupDragAndDrop(View buttonView, final String appName) {
        final int originalPosition = layoutButtons.indexOfChild(buttonView);

        buttonView.setOnLongClickListener(v -> {
            View.DragShadowBuilder shadowBuilder = new View.DragShadowBuilder(v);
            v.startDragAndDrop(null, shadowBuilder, v, 0);
            v.setVisibility(View.INVISIBLE);
            return true;
        });

        buttonView.setOnDragListener((v, event) -> {
            int action = event.getAction();
            switch (action) {
                case DragEvent.ACTION_DRAG_ENTERED:
                    break;

                case DragEvent.ACTION_DROP:
                    View droppedView = (View) event.getLocalState();
                    if(droppedView != null) {
                        droppedView.setVisibility(View.VISIBLE);

                        String draggedAppName = selectedApps.get(layoutButtons.indexOfChild(droppedView));

                        int draggedIndex = selectedApps.indexOf(draggedAppName);
                        int droppedIndex = selectedApps.indexOf(appName);

                        new Handler(Looper.getMainLooper()).post(() -> {
                            selectedApps.set(draggedIndex, appName);
                            selectedApps.set(droppedIndex, draggedAppName);

                            updateButtons();
                        });

                    }
                    break;

                case DragEvent.ACTION_DRAG_ENDED:
                    final Object localState = event.getLocalState();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (localState instanceof View) {
                            ((View) localState).setVisibility(View.VISIBLE);
                        }
                    });
                    break;

                default:
                    break;
            }
            return true;
        });
    }

    private View createAppButton(String appName) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View buttonView = inflater.inflate(R.layout.app_button_layout, null);

        TextView textView = buttonView.findViewById(R.id.app_name);
        ImageView iconView = buttonView.findViewById(R.id.app_icon);
        textView.setText(appName);

        String packageName = appPackageMap.get(appName);
        if (packageName != null) {
            try {
                PackageManager packageManager = getPackageManager();
                iconView.setImageDrawable(packageManager.getApplicationIcon(packageName));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
        layoutParams.width = 300;
        layoutParams.height = 400;
        layoutParams.setMargins(8, 8, 8, 8);
        buttonView.setLayoutParams(layoutParams);

        buttonView.setOnClickListener(v -> openApp(appName));

        setupDragAndDrop(buttonView, appName);

        return buttonView;
    }

    private void openApp(String appName) {
        String packageName = appPackageMap.get(appName);
        if (packageName != null && !packageName.isEmpty()) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                startActivity(intent);
            } else {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + packageName)));
                } catch (Exception e) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName)));
                }
            }
        } else {
            android.widget.Toast.makeText(this, getString(R.string.app_not_found, appName), android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void saveSelectedAppsToPrefs(ArrayList<String> apps) {
        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        StringBuilder appsString = new StringBuilder();
        for (String app : apps) {
            if (appsString.length() > 0) {
                appsString.append(",");
            }
            appsString.append(app);
        }
        editor.putString("selected_apps", appsString.toString());
        editor.apply();
    }

    private void loadAppPackageMap() {
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        appPackageMap.clear();
        for (ApplicationInfo app : installedApps) {
            String appName = app.loadLabel(packageManager).toString();
            String packageName = app.packageName;
            Log.d("AppMap", "App: " + appName + " - Package: " + packageName);
            appPackageMap.put(appName, packageName);
        }
    }

    private void registerAppChangeReceiver() {
        appChangeReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                String packageName = intent.getData().getSchemeSpecificPart();
                
                if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    refreshAppList(packageName);
                } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    loadAppPackageMap();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addDataScheme("package");
        registerReceiver(appChangeReceiver, filter);
    }

    private void refreshAppList(String removedPackageName) {
        boolean needsUpdate = false;
        ArrayList<String> appsToRemove = new ArrayList<>();
        
        for (String appName : selectedApps) {
            String packageName = appPackageMap.get(appName);
            if (packageName != null && packageName.equals(removedPackageName)) {
                appsToRemove.add(appName);
                needsUpdate = true;
            }
        }
        
        if (needsUpdate) {
            selectedApps.removeAll(appsToRemove);
            saveSelectedAppsToPrefs(selectedApps);
            updateButtons();
        }
    }

    @Override
    protected void onDestroy() {
        if (appChangeReceiver != null) {
            unregisterReceiver(appChangeReceiver);
        }
        super.onDestroy();
    }
}