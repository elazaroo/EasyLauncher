package com.elazaroo.easylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class AppSelectorActivity extends AppCompatActivity {
    private ListView listViewApps;
    private Button btnSaveSelection;
    private List<ApplicationInfo> installedApps;
    private ArrayList<String> selectedApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_selector);

        listViewApps = findViewById(R.id.list_apps);
        btnSaveSelection = findViewById(R.id.btn_save_selection);

        PackageManager packageManager = getPackageManager();
        installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA);

        // Filtrar y ordenar aplicaciones
        List<ApplicationInfo> filteredApps = new ArrayList<>();
        for (ApplicationInfo app : installedApps) {
            String appName = app.loadLabel(packageManager).toString();
            if (!appName.startsWith("com.")) {
                filteredApps.add(app);
            }
        }

        // Ordenar aplicaciones por nombre
        filteredApps.sort((app1, app2) -> {
            String name1 = app1.loadLabel(packageManager).toString().toLowerCase();
            String name2 = app2.loadLabel(packageManager).toString().toLowerCase();
            return name1.compareTo(name2);
        });

        // Usar el adaptador con la lista filtrada y ordenada
        AppAdapter adapter = new AppAdapter(this, filteredApps);
        listViewApps.setAdapter(adapter);
        listViewApps.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        // Seleccionar automáticamente las primeras N aplicaciones
        int numberOfAppsToSelect = 5; // Cambia este valor según cuántas aplicaciones quieras seleccionar
        for (int i = 0; i < Math.min(numberOfAppsToSelect, filteredApps.size()); i++) {
            listViewApps.setItemChecked(i, true);
        }

        // Cargar selecciones previas desde SharedPreferences
        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String savedApps = prefs.getString("selected_apps", null);
        if (savedApps != null) {
            String[] appsArray = savedApps.split(",");
            for (int i = 0; i < filteredApps.size(); i++) {
                String appName = filteredApps.get(i).loadLabel(packageManager).toString();
                for (String savedApp : appsArray) {
                    if (appName.equals(savedApp.trim())) {
                        listViewApps.setItemChecked(i, true);
                    }
                }
            }
        }

        // Guardar la selección
        btnSaveSelection.setOnClickListener(v -> saveSelectedApps(filteredApps));
    }

    // Método para guardar las aplicaciones seleccionadas
    private void saveSelectedApps(List<ApplicationInfo> filteredApps) { // ← RECIBIR filteredApps
        selectedApps.clear();
        for (int i = 0; i < filteredApps.size(); i++) {  // ← CORREGIDO: usar filteredApps
            if (listViewApps.isItemChecked(i)) {
                String appName = filteredApps.get(i).loadLabel(getPackageManager()).toString();
                selectedApps.add(appName);
            }
        }

        Intent intent = new Intent();
        intent.putStringArrayListExtra("selected_apps", selectedApps);
        setResult(RESULT_OK, intent);
        finish();
    }


}

class AppAdapter extends ArrayAdapter<ApplicationInfo> {
    private Context context;
    private List<ApplicationInfo> apps;
    private PackageManager packageManager;

    public AppAdapter(Context context, List<ApplicationInfo> apps) {
        super(context, android.R.layout.simple_list_item_multiple_choice, apps);
        this.context = context;
        this.apps = apps;
        this.packageManager = context.getPackageManager();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_multiple_choice, parent, false);
        }

        TextView textView = convertView.findViewById(android.R.id.text1);
        ImageView iconView = convertView.findViewById(android.R.id.icon);

        ApplicationInfo app = apps.get(position);
        textView.setText(app.loadLabel(packageManager));

        if (iconView != null) {
            iconView.setImageDrawable(app.loadIcon(packageManager));
        }

        return convertView;
    }
}