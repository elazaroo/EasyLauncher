package com.elazaroo.easylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private GridLayout layoutButtons;
    private ArrayList<String> selectedApps = new ArrayList<>();
    private HashMap<String, String> appPackageMap = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        layoutButtons = findViewById(R.id.layout_buttons);

        findViewById(R.id.btn_edit_home).setOnClickListener(v -> openAppSelector());

        loadAppPackageMap();

        loadSelectedApps();
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
            for (String packageName : appsArray) {
                selectedApps.add(packageName.trim());
            }
        }
        updateButtons();
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
            android.widget.Toast.makeText(this, "No se encontró la aplicación: " + appName, android.widget.Toast.LENGTH_SHORT).show();
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

}