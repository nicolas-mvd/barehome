package uy.nico.barehome;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

/** The launcher and explicit remote requests share the same guards and rollback. */
final class PictureController {
    private final Context context;
    private final SharedPreferences preferences;
    private PictureProfiles.State pictureProfiles;

    PictureController(Context context) {
        this.context = context;
        preferences = context.getSharedPreferences("selection", Context.MODE_PRIVATE);
    }

    // This OEM's picture service observes the same key used by its Backlight settings page.
    private static final String BACKLIGHT_SETTING = "picture_backlight";
    private static final String PICTURE_BRIGHTNESS_SETTING = "picture_brightness";

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

    private int pictureLevel(String key) {
        if (!supportsBacklight()) return -1;
        try {
            int value = Settings.Global.getInt(context.getContentResolver(), key, -1);
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
        switch (Settings.Global.getInt(context.getContentResolver(), "picture_mode", -1)) {
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
        return Settings.Global.getInt(context.getContentResolver(), "picture_als", 0) != 0
                || Settings.Global.getInt(context.getContentResolver(),
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
        int mode = Settings.Global.getInt(context.getContentResolver(), "picture_mode", -1);
        if (mode < 0) return false;
        int legacyMode = pendingLegacyMode();
        if (legacyMode >= 0 && legacyMode != mode) return false;
        PictureProfiles.State loaded = PictureProfiles.load(profileStore, backlight, brightness, mode);
        if (!savedProfileMatches(loaded) && !profileStore.save(loaded)) return false;
        pictureProfiles = loaded;
        return true;
    }

    void refresh() {
        pictureProfiles = null;
        try {
            ensureProfiles();
        } catch (SecurityException denied) {
            // App launching remains available without picture access.
        }
    }

    private void pictureMessage(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }

    boolean canChangePicture() {
        pictureProfiles = null;
        try {
            if (pictureLevel(BACKLIGHT_SETTING) < 0
                    || pictureLevel(PICTURE_BRIGHTNESS_SETTING) < 0) {
                pictureMessage("Picture controls are unavailable on this TV");
                return false;
            }
            if (context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
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
            if (Settings.Global.getInt(context.getContentResolver(), "picture_mode", -1)
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
                    && !Settings.Global.putInt(context.getContentResolver(), BACKLIGHT_SETTING, backlight)) {
                return false;
            }
            if (pictureLevel(PICTURE_BRIGHTNESS_SETTING) != brightness
                    && !Settings.Global.putInt(context.getContentResolver(), PICTURE_BRIGHTNESS_SETTING, brightness)) {
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
        if (!savedProfileMatches(next) && !profileStore.save(next)) {
            boolean rolledBack = setPictureLevels(backlight, brightness);
            // A failed SharedPreferences commit can still update its in-memory values.
            profileStore.save(pictureProfiles);
            pictureMessage(rolledBack ? "Could not save preset; previous levels kept"
                    : "Could not save preset; check Picture settings");
            return false;
        }
        pictureProfiles = next;
        pictureMessage((next.night ? "Night" : "Day") + ": backlight " + next.backlight()
                + "%, picture brightness " + next.brightness());
        return true;
    }

    PictureProfiles.State state() { return pictureProfiles; }

    boolean selectedNight() {
        return pictureProfiles != null ? pictureProfiles.night : pendingLegacyMode() >= 0;
    }

    boolean select(boolean night) {
        return canChangePicture() && applyProfile(pictureProfiles.select(night));
    }

    boolean toggle() {
        return canChangePicture() && applyProfile(pictureProfiles.select(!pictureProfiles.night));
    }

    boolean saveMode(int editorPictureMode, boolean night, int backlight, int brightness) {
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

    private boolean savedProfileMatches(PictureProfiles.State state) {
        return profileStore.get(PictureProfiles.VERSION, 0) == 1
                && profileStore.get(PictureProfiles.DAY_BACKLIGHT, -1) == state.dayBacklight
                && profileStore.get(PictureProfiles.DAY_BRIGHTNESS, -1) == state.dayBrightness
                && profileStore.get(PictureProfiles.NIGHT_BACKLIGHT, -1) == state.nightBacklight
                && profileStore.get(PictureProfiles.NIGHT_BRIGHTNESS, -1) == state.nightBrightness
                && profileStore.get(PictureProfiles.ACTIVE_NIGHT, -1) == (state.night ? 1 : 0)
                && profileStore.get(PictureProfiles.PICTURE_MODE, -1) == state.pictureMode;
    }
}
