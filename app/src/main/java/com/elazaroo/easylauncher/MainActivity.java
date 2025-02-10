package com.elazaroo.easylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
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

        // Referencia al contenedor de botones
        layoutButtons = findViewById(R.id.layout_buttons);

        // Botón para editar el inicio
        findViewById(R.id.btn_edit_home).setOnClickListener(v -> openAppSelector());

        // Cargar el mapa de aplicaciones y paquetes
        loadAppPackageMap();

        // Cargar aplicaciones seleccionadas previamente
        loadSelectedApps();
    }

    // Abrir la actividad para seleccionar aplicaciones
    private void openAppSelector() {
        Intent intent = new Intent(this, AppSelectorActivity.class);
        startActivityForResult(intent, 1);
    }

    // Manejar el resultado de la selección de aplicaciones
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && resultCode == RESULT_OK) {
            selectedApps = data.getStringArrayListExtra("selected_apps");
            saveSelectedAppsToPrefs(selectedApps); // Guardar en SharedPreferences
            updateButtons();
        }
    }

    // Cargar aplicaciones seleccionadas desde SharedPreferences
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

    // Actualizar los botones según las aplicaciones seleccionadas
    private void updateButtons() {
        layoutButtons.removeAllViews();
        for (String appName : selectedApps) {
            View buttonView = createAppButton(appName);
            layoutButtons.addView(buttonView);
        }
    }

    // Crear un botón para una aplicación específica con ícono
    private View createAppButton(String appName) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View buttonView = inflater.inflate(R.layout.app_button_layout, null);

        // Configurar el texto y el ícono
        TextView textView = buttonView.findViewById(R.id.app_name);
        ImageView iconView = buttonView.findViewById(R.id.app_icon);
        textView.setText(appName);

        String packageName = appPackageMap.get(appName);
        if (packageName != null) {
            try {
                PackageManager packageManager = getPackageManager();
                iconView.setImageDrawable(packageManager.getApplicationIcon(packageName));
            } catch (Exception e) {
                e.printStackTrace(); // Manejar errores al obtener el ícono
            }
        }

        // Configurar LayoutParams para garantizar tamaño uniforme
        GridLayout.LayoutParams layoutParams = new GridLayout.LayoutParams();
        layoutParams.width = 300; // Ancho fijo
        layoutParams.height = 400; // Alto fijo
        layoutParams.setMargins(8, 8, 8, 8); // Margen uniforme

        buttonView.setLayoutParams(layoutParams);

        // Asignar el listener de clic
        buttonView.setOnClickListener(v -> openApp(appName));

        return buttonView;
    }

    // Método para abrir una aplicación específica
    private void openApp(String appName) {
        String packageName = appPackageMap.get(appName);
        if (packageName != null && !packageName.isEmpty()) {
            Intent intent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (intent != null) {
                startActivity(intent); // Abrir la aplicación
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

    // Método para guardar las aplicaciones seleccionadas en SharedPreferences
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

    // Método para cargar el mapa de aplicaciones y paquetes
    private void loadAppPackageMap() {
        PackageManager packageManager = getPackageManager();
        List<ApplicationInfo> installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        appPackageMap.clear(); // Asegurarse de limpiar antes de llenar
        for (ApplicationInfo app : installedApps) {
            String appName = app.loadLabel(packageManager).toString();
            String packageName = app.packageName;
            Log.d("AppMap", "App: " + appName + " - Package: " + packageName);
            appPackageMap.put(appName, packageName);
        }
    }

}