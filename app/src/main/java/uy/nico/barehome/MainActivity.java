package uy.nico.barehome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One Activity, one View. No services, workers, network, images, or idle callbacks. */
public final class MainActivity extends Activity implements LauncherView.Listener {
    static final class App {
        final String label;
        final ComponentName component;
        final String category;

        App(String label, ComponentName component, String category) {
            this.label = label;
            this.component = component;
            this.category = category;
        }

        String key() {
            return component.flattenToString();
        }
    }

    private LauncherView home;
    private SharedPreferences preferences;
    private String savedSelection;
    private Intent settingsIntent;
    private boolean catalogDirty = true;

    // Registered only for this Activity's lifetime. A stopped process is never restarted.
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            catalogDirty = true;
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("selection", MODE_PRIVATE);
        savedSelection = preferences.getString("component", "");
        settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        ResolveInfo settings = getPackageManager().resolveActivity(
                settingsIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (settings != null && settings.activityInfo != null) {
            settingsIntent.setComponent(new ComponentName(
                    settings.activityInfo.packageName, settings.activityInfo.name));
        }
        home = new LauncherView(this, this);
        setContentView(home);

        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packages.addDataScheme("package");
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(packagesChanged, packages, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packagesChanged, packages);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        fullscreen();
        if (catalogDirty) refresh();
        else refreshBrightness();
        home.requestFocus();
    }

    @Override protected void onPause() {
        rememberSelection();
        super.onPause();
    }

    @Override protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        super.onDestroy();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // HOME returns to the existing selection, rather than rebuilding the screen.
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) fullscreen();
    }

    @Override public void onBackPressed() {
        // HOME is the end of the navigation stack.
    }

    private void fullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void rememberSelection() {
        String selection = home.selectedComponent();
        if (!selection.isEmpty() && !selection.equals(savedSelection)) {
            savedSelection = selection;
            preferences.edit().putString("component", selection).apply();
        }
    }

    @Override public void refresh() {
        String selection = home.selectedComponent();
        if (selection.isEmpty()) selection = savedSelection;

        Map<String, App> byPackage = new HashMap<>();
        collectApps(Intent.CATEGORY_LEANBACK_LAUNCHER, byPackage);
        collectApps(Intent.CATEGORY_LAUNCHER, byPackage);
        ArrayList<App> apps = new ArrayList<>(byPackage.values());
        Collections.sort(apps, new Comparator<App>() {
            @Override public int compare(App a, App b) {
                int rank = Integer.compare(Config.rank(a.component.getPackageName()),
                        Config.rank(b.component.getPackageName()));
                if (rank != 0) return rank;
                int label = a.label.compareToIgnoreCase(b.label);
                return label != 0 ? label : a.key().compareTo(b.key());
            }
        });
        home.setApps(apps.toArray(new App[0]), selection);
        catalogDirty = false;
        refreshBrightness();
    }

    private int brightnessPercent() {
        int value = Settings.System.getInt(getContentResolver(),
                Settings.System.SCREEN_BRIGHTNESS, -1);
        return value < 0 ? -1 : Math.round(Math.min(255, value) * 100f / 255);
    }

    private void refreshBrightness() {
        home.setBrightness(brightnessPercent());
    }

    @Override public void adjustBrightness(int direction) {
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Allow BareHome to change system settings", Toast.LENGTH_SHORT).show();
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                        Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException | SecurityException unavailable) {
                Toast.makeText(this, "Enable Modify system settings for BareHome in Settings",
                        Toast.LENGTH_LONG).show();
            }
            return;
        }
        int current = brightnessPercent();
        if (current < 0) {
            Toast.makeText(this, "Screen brightness is unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        int next = Math.max(Config.BRIGHTNESS_MIN_PERCENT, Math.min(100,
                current + Integer.signum(direction) * Config.BRIGHTNESS_STEP_PERCENT));
        try {
            boolean manual = Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
            boolean changed = manual && Settings.System.putInt(getContentResolver(),
                    Settings.System.SCREEN_BRIGHTNESS, Math.round(next * 255f / 100));
            if (!changed) Toast.makeText(this, "Could not change brightness", Toast.LENGTH_SHORT).show();
        } catch (SecurityException denied) {
            Toast.makeText(this, "Brightness permission is unavailable", Toast.LENGTH_SHORT).show();
        }
        refreshBrightness();
    }

    private void collectApps(String category, Map<String, App> byPackage) {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> results = pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(category), 0);
        ComponentName settings = settingsIntent.getComponent();
        for (ResolveInfo result : results) {
            ActivityInfo activity = result.activityInfo;
            if (activity == null || !activity.enabled || !activity.exported
                    || !activity.applicationInfo.enabled
                    || getPackageName().equals(activity.packageName)
                    || (settings != null && settings.getPackageName().equals(activity.packageName))) {
                continue;
            }
            ComponentName component = new ComponentName(activity.packageName, activity.name);
            App previous = byPackage.get(activity.packageName);
            // Prefer the TV entry point; choose deterministically if a package advertises several.
            if (previous != null && (!previous.category.equals(category)
                    || previous.component.getClassName().compareTo(activity.name) <= 0)) {
                continue;
            }
            CharSequence loadedLabel = result.loadLabel(pm);
            String label = loadedLabel == null ? activity.packageName : loadedLabel.toString().trim();
            label = label.replace('\n', ' ').replace('\r', ' ');
            if (label.isEmpty()) label = activity.packageName;
            byPackage.put(activity.packageName, new App(label, component, category));
        }
    }

    @Override public void launch(App app) {
        Intent intent = app == null ? new Intent(settingsIntent)
                : new Intent(Intent.ACTION_MAIN).addCategory(app.category).setComponent(app.component);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException unavailable) {
            Toast.makeText(this, "App unavailable", Toast.LENGTH_SHORT).show();
            catalogDirty = true;
            refresh();
        }
    }
}
