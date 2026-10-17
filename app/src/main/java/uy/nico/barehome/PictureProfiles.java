package uy.nico.barehome;

/** Saved presets only: reading or editing them never writes TV settings. */
final class PictureProfiles {
    static final String VERSION = "profile_version";
    static final String DAY_BACKLIGHT = "profile_day_backlight";
    static final String DAY_BRIGHTNESS = "profile_day_brightness";
    static final String NIGHT_BACKLIGHT = "profile_night_backlight";
    static final String NIGHT_BRIGHTNESS = "profile_night_brightness";
    static final String ACTIVE_NIGHT = "profile_active_night";
    static final String PICTURE_MODE = "profile_picture_mode";

    interface Store {
        int get(String key, int fallback);
        boolean save(State state);
    }

    static final class State {
        final int dayBacklight;
        final int dayBrightness;
        final int nightBacklight;
        final int nightBrightness;
        final boolean night;
        final int pictureMode;

        private State(int dayBacklight, int dayBrightness, int nightBacklight,
                int nightBrightness, boolean night, int pictureMode) {
            this.dayBacklight = dayBacklight;
            this.dayBrightness = dayBrightness;
            this.nightBacklight = nightBacklight;
            this.nightBrightness = nightBrightness;
            this.night = night;
            this.pictureMode = pictureMode;
        }

        int backlight() { return night ? nightBacklight : dayBacklight; }
        int brightness() { return night ? nightBrightness : dayBrightness; }

        State select(boolean selectedNight) {
            return new State(dayBacklight, dayBrightness, nightBacklight,
                    nightBrightness, selectedNight, pictureMode);
        }

        State edit(boolean selectedNight, int backlight, int brightness) {
            if (!validProfile(backlight, brightness)) {
                throw new IllegalArgumentException("Backlight must be 5–100; brightness must be 0–100");
            }
            return selectedNight
                    ? new State(dayBacklight, dayBrightness, backlight, brightness, night, pictureMode)
                    : new State(backlight, brightness, nightBacklight, nightBrightness, night, pictureMode);
        }
    }

    static State load(Store store, int currentBacklight, int currentBrightness, int currentPictureMode) {
        int dayBacklight = store.get(DAY_BACKLIGHT, -1);
        int dayBrightness = store.get(DAY_BRIGHTNESS, -1);
        int nightBacklight = store.get(NIGHT_BACKLIGHT, -1);
        int nightBrightness = store.get(NIGHT_BRIGHTNESS, -1);
        int activeNight = store.get(ACTIVE_NIGHT, -1);
        int mode = store.get(PICTURE_MODE, -1);
        boolean currentValid = validLevel(currentBacklight) && validLevel(currentBrightness);
        if (store.get(VERSION, 0) == 1) {
            // Do not resurrect old Restore values if one new profile is damaged.
            if (!validProfile(dayBacklight, dayBrightness)) {
                dayBacklight = currentValid ? Math.max(5, currentBacklight) : 5;
                dayBrightness = currentValid ? currentBrightness : 50;
            }
            if (!validProfile(nightBacklight, nightBrightness)) {
                nightBacklight = 5;
                nightBrightness = 20;
            }
            return new State(dayBacklight, dayBrightness, nightBacklight,
                    nightBrightness, activeNight == 1, mode >= 0 ? mode : currentPictureMode);
        }

        // The old Night feature saved the previous pair under these names.
        int previousBacklight = store.get("night_backlight", -1);
        int previousBrightness = store.get("night_brightness", -1);
        boolean legacyNight = validLevel(previousBacklight) && validLevel(previousBrightness)
                && currentPictureMode >= 0
                && store.get("night_picture_mode", -1) == currentPictureMode;
        dayBacklight = legacyNight ? Math.max(5, previousBacklight)
                : currentValid ? Math.max(5, currentBacklight) : 5;
        dayBrightness = legacyNight ? previousBrightness : currentValid ? currentBrightness : 50;
        // Night had fixed levels; external picture changes are not preset edits.
        return new State(dayBacklight, dayBrightness, 5, 20, legacyNight, currentPictureMode);
    }

    private static boolean validProfile(int backlight, int brightness) {
        return backlight >= 5 && backlight <= 100 && validLevel(brightness);
    }

    private static boolean validLevel(int value) { return value >= 0 && value <= 100; }

    private PictureProfiles() {}
}
