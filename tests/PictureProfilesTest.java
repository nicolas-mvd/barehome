package uy.nico.barehome;

import java.util.HashMap;
import java.util.Map;

/** Host-side behavioral checks; no Android classes, TV, or runtime dependencies. */
public final class PictureProfilesTest {
    private static final class MemoryStore implements PictureProfiles.Store {
        final Map<String, Integer> values = new HashMap<>();
        int saves;

        @Override public int get(String key, int fallback) {
            Integer value = values.get(key);
            return value == null ? fallback : value;
        }

        @Override public boolean save(PictureProfiles.State state) {
            saves++;
            values.put(PictureProfiles.VERSION, 1);
            values.put(PictureProfiles.DAY_BACKLIGHT, state.dayBacklight);
            values.put(PictureProfiles.DAY_BRIGHTNESS, state.dayBrightness);
            values.put(PictureProfiles.NIGHT_BACKLIGHT, state.nightBacklight);
            values.put(PictureProfiles.NIGHT_BRIGHTNESS, state.nightBrightness);
            values.put(PictureProfiles.ACTIVE_NIGHT, state.night ? 1 : 0);
            values.put(PictureProfiles.PICTURE_MODE, state.pictureMode);
            return true;
        }

        void legacy(int backlight, int brightness, int mode) {
            values.put("night_backlight", backlight);
            values.put("night_brightness", brightness);
            values.put("night_picture_mode", mode);
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void levels(PictureProfiles.State state, int dayBacklight, int dayBrightness,
            int nightBacklight, int nightBrightness, boolean night) {
        check(state.dayBacklight == dayBacklight && state.dayBrightness == dayBrightness,
                "Day profile changed unexpectedly");
        check(state.nightBacklight == nightBacklight && state.nightBrightness == nightBrightness,
                "Night profile changed unexpectedly");
        check(state.night == night, "Selected mode changed unexpectedly");
        check(state.backlight() == (night ? nightBacklight : dayBacklight)
                && state.brightness() == (night ? nightBrightness : dayBrightness),
                "Selected levels do not match the selected profile");
    }

    private static void firstRunKeepsCurrentDay() {
        MemoryStore store = new MemoryStore();
        levels(PictureProfiles.load(store, 66, 48, 9), 66, 48, 5, 20, false);
        check(store.saves == 0 && store.values.isEmpty(), "Loading must not persist or change settings");
        levels(PictureProfiles.load(store, -1, 48, 9), 5, 50, 5, 20, false);
        levels(PictureProfiles.load(store, 66, 101, 9), 5, 50, 5, 20, false);
        levels(PictureProfiles.load(store, 0, 0, 9), 5, 0, 5, 20, false);
        levels(PictureProfiles.load(store, 100, 100, 9), 100, 100, 5, 20, false);
    }

    private static void migrationKeepsDayAndOriginalNightPreset() {
        MemoryStore store = new MemoryStore();
        store.legacy(5, 50, 9);
        levels(PictureProfiles.load(store, 5, 20, 9), 5, 50, 5, 20, true);
        store.legacy(70, 51, 9);
        PictureProfiles.State migrated = PictureProfiles.load(store, 11, 23, 9);
        levels(migrated, 70, 51, 5, 20, true);
        check(migrated.pictureMode == 9 && store.saves == 0, "Migration must be read-only and retain picture mode");
        store.save(migrated);
        levels(PictureProfiles.load(store, 100, 100, 7), 70, 51, 5, 20, true);
        check(PictureProfiles.load(store, 100, 100, 7).pictureMode == 9,
                "Changing TV picture mode must not silently rebind saved profiles");
    }

    private static void migrationRejectsWrongModeAndBadValues() {
        MemoryStore store = new MemoryStore();
        store.legacy(70, 50, 7);
        levels(PictureProfiles.load(store, 42, 45, 9), 42, 45, 5, 20, false);
        store.legacy(-1, 50, 9);
        levels(PictureProfiles.load(store, 42, 45, 9), 42, 45, 5, 20, false);
        store.legacy(50, 101, 9);
        levels(PictureProfiles.load(store, 42, 45, 9), 42, 45, 5, 20, false);
        store.legacy(0, 0, 9);
        levels(PictureProfiles.load(store, 0, 20, 9), 5, 0, 5, 20, true);
        levels(PictureProfiles.load(store, -1, -1, 9), 5, 0, 5, 20, true);
    }

    private static void editsAndSelectionSurviveRestartIndependently() {
        MemoryStore store = new MemoryStore();
        PictureProfiles.State original = PictureProfiles.load(store, 60, 50, 9);
        PictureProfiles.State edited = original.edit(true, 7, 21).edit(false, 65, 49).select(true);
        levels(original, 60, 50, 5, 20, false);
        levels(edited, 65, 49, 7, 21, true);
        store.save(edited);
        levels(PictureProfiles.load(store, 65, 49, 9), 65, 49, 7, 21, true);
        levels(PictureProfiles.load(store, -1, -1, -1), 65, 49, 7, 21, true);
        store.save(edited.select(false));
        levels(PictureProfiles.load(store, 7, 21, 9), 65, 49, 7, 21, false);
        check(store.saves == 2, "Reading after external changes must not write profiles");
    }

    private static void corruptStorageAndEditsStayInRange() {
        MemoryStore store = new MemoryStore();
        PictureProfiles.State original = PictureProfiles.load(store, 60, 50, 9);
        store.save(original.select(true));
        store.values.put(PictureProfiles.NIGHT_BACKLIGHT, 101);
        store.legacy(99, 99, 9);
        levels(PictureProfiles.load(store, 30, 40, 9), 60, 50, 5, 20, true);
        store.save(original);
        store.values.put(PictureProfiles.ACTIVE_NIGHT, 2);
        levels(PictureProfiles.load(store, 30, 40, 9), 60, 50, 5, 20, false);
        store.values.put(PictureProfiles.DAY_BRIGHTNESS, -1);
        levels(PictureProfiles.load(store, 30, 40, 9), 30, 40, 5, 20, false);
        int[][] invalid = {{4, 20}, {101, 20}, {5, -1}, {5, 101}};
        for (int[] pair : invalid) {
            boolean rejected = false;
            try { original.edit(true, pair[0], pair[1]); }
            catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Invalid profile was accepted");
        }
        levels(original.edit(false, 100, 0).edit(true, 5, 100), 100, 0, 5, 100, false);
    }

    public static void main(String[] args) {
        firstRunKeepsCurrentDay();
        migrationKeepsDayAndOriginalNightPreset();
        migrationRejectsWrongModeAndBadValues();
        editsAndSelectionSurviveRestartIndependently();
        corruptStorageAndEditsStayInRange();
        System.out.println("PictureProfiles: 5 behavioral scenarios passed");
    }
}
