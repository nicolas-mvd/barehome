package uy.nico.barehome;

import android.Manifest;
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
    // This OEM's picture service observes the same key used by its Backlight settings page.
    private static final String BACKLIGHT_SETTING = "picture_backlight";
    private static final String PICTURE_BRIGHTNESS_SETTING = "picture_brightness";

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
    private PictureProfiles.State pictureProfiles;
    private int editorPictureMode = -1;
    private final PictureProfiles.Store profileStore = new PictureProfiles.Store() {
        @Override public int get(String key, int fallback) {
            return preferences.getInt(key, fallback);
        }

        @Override public boolean save(PictureProfiles.State state) {
            return preferences.edit().putInt(PictureProfiles.VERSION, 1)
                    .putInt(PictureProfiles.DAY_BACKLIGHT, state.dayBacklight)
                    .putInt(PictureProfiles.DAY_BRIGHTNESS, state.dayBrightness)
                    .putInt(PictureProfiles.NIGHT_BACKLIGHT, state.nightBacklight)
                    .putInt(PictureProfiles.NIGHT_BRIGHTNESS, state.nightBrightness)
                    .putInt(PictureProfiles.ACTIVE_NIGHT, state.night ? 1 : 0)
                    .putInt(PictureProfiles.PICTURE_MODE, state.pictureMode)
                    .remove("night_backlight").remove("night_brightness")
                    .remove("night_picture_mode").commit();
        }
    };
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
        else refreshMode();
        home.requestFocus();
    }

    @Override protected void onPause() {
        home.closeModeEditor();
        editorPictureMode = -1;
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
        home.closeModeEditor();
        editorPictureMode = -1;
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
        refreshMode();
    }

    private int pictureLevel(String key) {
        if (!supportsBacklight()) return -1;
        try {
            int value = Settings.Global.getInt(getContentResolver(), key, -1);
            return value >= 0 && value <= 100 ? value : -1;
        } catch (SecurityException denied) {
            return -1;
        }
    }

    private static boolean supportsBacklight() {
        return "CSV4K".equals(Build.MODEL)
                && "zhongshan".equals(Build.DEVICE)
                && "KTC_MOTOROLA_4K_SA_21V2".equals(Build.PRODUCT);
    }

    private boolean automaticBacklightEnabled() {
        String mode;
        switch (Settings.Global.getInt(getContentResolver(), "picture_mode", -1)) {
            case 0: mode = "user"; break;
            case 7: mode = "standard"; break;
            case 3: mode = "vivid"; break;
            case 2: mode = "sport"; break;
            case 9: mode = "movie"; break;
            case 10: mode = "game"; break;
            case 11: mode = "energy_saving"; break;
            default: mode = "others"; break;
        }
        // These missing-key defaults match this device's OEM picture service.
        return Settings.Global.getInt(getContentResolver(), "picture_als", 0) != 0
                || Settings.Global.getInt(getContentResolver(),
                        "key_picture_mode_" + mode + "_auto_backlight", 0) != 0;
    }

    private int pendingLegacyMode() {
        if (profileStore.get(PictureProfiles.VERSION, 0) == 1) return -1;
        int backlight = profileStore.get("night_backlight", -1);
        int brightness = profileStore.get("night_brightness", -1);
        return backlight >= 0 && backlight <= 100 && brightness >= 0 && brightness <= 100
                ? profileStore.get("night_picture_mode", -1) : -1;
    }

    private boolean ensureProfiles() {
        if (pictureProfiles != null) return true;
        int backlight = pictureLevel(BACKLIGHT_SETTING);
        int brightness = pictureLevel(PICTURE_BRIGHTNESS_SETTING);
        if (backlight < 0 || brightness < 0) return false;
        int mode = Settings.Global.getInt(getContentResolver(), "picture_mode", -1);
        if (mode < 0) return false;
        int legacyMode = pendingLegacyMode();
        if (legacyMode >= 0 && legacyMode != mode) return false;
        PictureProfiles.State loaded = PictureProfiles.load(profileStore, backlight, brightness, mode);
        if (!profileStore.save(loaded)) return false;
        pictureProfiles = loaded;
        return true;
    }

    private void refreshMode() {
        try {
            ensureProfiles();
        } catch (SecurityException denied) {
            // App launching remains available without picture access.
        }
        home.setMode(pictureProfiles != null ? pictureProfiles.night : pendingLegacyMode() >= 0);
    }

    private void pictureMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private boolean canChangePicture() {
        try {
            if (pictureLevel(BACKLIGHT_SETTING) < 0
                    || pictureLevel(PICTURE_BRIGHTNESS_SETTING) < 0) {
                pictureMessage("Picture controls are unavailable on this TV");
                return false;
            }
            if (checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    != PackageManager.PERMISSION_GRANTED) {
                pictureMessage("Picture controls need the one-time ADB permission");
                return false;
            }
            if (automaticBacklightEnabled()) {
                pictureMessage("Disable automatic backlight in Picture settings first");
                return false;
            }
            if (!ensureProfiles()) {
                pictureMessage(pendingLegacyMode() >= 0
                        ? "Return to the original Picture mode to finish updating presets"
                        : "Could not load picture presets");
                return false;
            }
            if (Settings.Global.getInt(getContentResolver(), "picture_mode", -1)
                    != pictureProfiles.pictureMode) {
                pictureMessage("Return to the original Picture mode to use these presets");
                return false;
            }
            return true;
        } catch (SecurityException denied) {
            pictureMessage("Picture permission is unavailable");
            return false;
        }
    }

    // The provider has no two-setting transaction. Roll back the pair if either write fails.
    private boolean setPictureLevels(int backlight, int brightness) {
        try {
            if (pictureLevel(BACKLIGHT_SETTING) != backlight
                    && !Settings.Global.putInt(getContentResolver(), BACKLIGHT_SETTING, backlight)) {
                return false;
            }
            if (pictureLevel(PICTURE_BRIGHTNESS_SETTING) != brightness
                    && !Settings.Global.putInt(getContentResolver(), PICTURE_BRIGHTNESS_SETTING, brightness)) {
                return false;
            }
            return pictureLevel(BACKLIGHT_SETTING) == backlight
                    && pictureLevel(PICTURE_BRIGHTNESS_SETTING) == brightness;
        } catch (SecurityException denied) {
            return false;
        }
    }

    private boolean applyProfile(PictureProfiles.State next) {
        int backlight = pictureLevel(BACKLIGHT_SETTING);
        int brightness = pictureLevel(PICTURE_BRIGHTNESS_SETTING);
        if (backlight < 0 || brightness < 0) {
            pictureMessage("Picture levels are unavailable");
            return false;
        }
        if (!setPictureLevels(next.backlight(), next.brightness())) {
            boolean rolledBack = setPictureLevels(backlight, brightness);
            pictureMessage(rolledBack ? "Could not apply both levels; previous levels kept"
                    : "Picture change incomplete; check Picture settings");
            return false;
        }
        if (!profileStore.save(next)) {
            boolean rolledBack = setPictureLevels(backlight, brightness);
            // A failed SharedPreferences commit can still update its in-memory values.
            profileStore.save(pictureProfiles);
            pictureMessage(rolledBack ? "Could not save preset; previous levels kept"
                    : "Could not save preset; check Picture settings");
            return false;
        }
        pictureProfiles = next;
        home.setMode(next.night);
        pictureMessage((next.night ? "Night" : "Day") + ": backlight " + next.backlight()
                + "%, picture brightness " + next.brightness());
        return true;
    }

    @Override public void toggleMode() {
        if (canChangePicture()) applyProfile(pictureProfiles.select(!pictureProfiles.night));
    }

    @Override public void editMode() {
        if (!canChangePicture()) return;
        editorPictureMode = pictureProfiles.pictureMode;
        home.openModeEditor(pictureProfiles.night,
                pictureProfiles.backlight(), pictureProfiles.brightness());
    }

    @Override public boolean saveMode(boolean night, int backlight, int brightness) {
        if (!canChangePicture()) return false;
        if (editorPictureMode != pictureProfiles.pictureMode || night != pictureProfiles.night) {
            pictureMessage("Mode changed; reopen its settings");
            return false;
        }
        if (backlight < 5 || backlight > 100 || brightness < 0 || brightness > 100) {
            pictureMessage("Backlight must be 5–100; picture brightness must be 0–100");
            return false;
        }
        return applyProfile(pictureProfiles.edit(night, backlight, brightness));
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
