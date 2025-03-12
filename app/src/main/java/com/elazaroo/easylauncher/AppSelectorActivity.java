package com.elazaroo.easylauncher;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.TypedValue;
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
    private ArrayList<String> selectedApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.app_selector);

        listViewApps = findViewById(R.id.list_apps);
        btnSaveSelection = findViewById(R.id.btn_save_selection);

        PackageManager packageManager = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveInfoList = packageManager.queryIntentActivities(mainIntent, 0);

        List<ApplicationInfo> launchableApps = new ArrayList<>();
        for (ResolveInfo resolveInfo : resolveInfoList) {
            ApplicationInfo appInfo = resolveInfo.activityInfo.applicationInfo;
            launchableApps.add(appInfo);
        }

        launchableApps.sort((app1, app2) -> {
            String name1 = app1.loadLabel(packageManager).toString().toLowerCase();
            String name2 = app2.loadLabel(packageManager).toString().toLowerCase();
            return name1.compareTo(name2);
        });

        AppAdapter adapter = new AppAdapter(this, launchableApps);
        listViewApps.setAdapter(adapter);
        listViewApps.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        int numberOfAppsToSelect = 0;
        for (int i = 0; i < Math.min(numberOfAppsToSelect, launchableApps.size()); i++) {
            listViewApps.setItemChecked(i, true);
        }

        SharedPreferences prefs = getSharedPreferences("AppPreferences", MODE_PRIVATE);
        String savedApps = prefs.getString("selected_apps", null);
        if (savedApps != null) {
            String[] appsArray = savedApps.split(",");
            for (int i = 0; i < launchableApps.size(); i++) {
                String appName = launchableApps.get(i).loadLabel(packageManager).toString();
                for (String savedApp : appsArray) {
                    if (appName.equals(savedApp.trim())) {
                        listViewApps.setItemChecked(i, true);
                    }
                }
            }
        }

        btnSaveSelection.setOnClickListener(v -> saveSelectedApps(launchableApps));
    }

    private void saveSelectedApps(List<ApplicationInfo> launchableApps) {
        selectedApps.clear();
        for (int i = 0; i < launchableApps.size(); i++) {
            if (listViewApps.isItemChecked(i)) {
                String appName = launchableApps.get(i).loadLabel(getPackageManager()).toString();
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

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.listItemTextColor, typedValue, true);
        int textColor = typedValue.data;

        textView.setTextColor(textColor);

        if (iconView != null) {
            iconView.setImageDrawable(app.loadIcon(packageManager));
        }

        return convertView;
    }
}
