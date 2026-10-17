package uy.nico.barehome;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.widget.Toast;
import java.util.HashMap;
import java.util.Map;

/** Runs the real controller against provider failures; no TV is contacted. */
public final class PictureControllerTest {
    private static int assertions;
    private static final class Preferences implements SharedPreferences {
        final Map<String, Integer> values = new HashMap<>();
        int commits;
        int failCommit = -1;
        public int getInt(String key, int fallback) {
            Integer value = values.get(key);
            return value == null ? fallback : value;
        }
        public Editor edit() {
            return new Editor() {
                final Map<String, Integer> edits = new HashMap<>();
                public Editor putInt(String key, int value) { edits.put(key, value); return this; }
                public Editor remove(String key) { edits.put(key, null); return this; }
                public boolean commit() {
                    for (Map.Entry<String, Integer> e : edits.entrySet()) {
                        if (e.getValue() == null) values.remove(e.getKey());
                        else values.put(e.getKey(), e.getValue());
                    }
                    // SharedPreferences can update memory despite failed disk persistence.
                    return ++commits != failCommit;
                }
            };
        }
    }
    private static final class Device extends Context {
        final Preferences prefs = new Preferences();
        final ContentResolver provider = new ContentResolver();
        boolean permission = true;
        Device() {
            provider.values.put("picture_backlight", 60);
            provider.values.put("picture_brightness", 50);
            provider.values.put("picture_mode", 9);
        }
        public SharedPreferences getSharedPreferences(String name, int mode) { return prefs; }
        public ContentResolver getContentResolver() { return provider; }
        public int checkSelfPermission(String name) { return permission ? 0 : -1; }
        int level(String key) { return provider.values.get(key); }
        void pair(int backlight, int brightness) {
            check(level("picture_backlight") == backlight && level("picture_brightness") == brightness,
                    "Wrong actual picture levels");
        }
    }
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void noWrites(Device d) {
        check(d.provider.writes.isEmpty(), "A rejected or read-only operation wrote TV settings");
    }
    private static void commandsAreExact() {
        check(Boolean.FALSE.equals(PictureCommand.selectedNight("barehome://picture/day")), "Day URI");
        check(Boolean.TRUE.equals(PictureCommand.selectedNight("barehome://picture/night")), "Night URI");
        String[] rejected = {null, "", "barehome://picture", "barehome://picture/night/",
                "barehome://picture/night?brightness=100", "barehome://picture/day#x",
                "barehome://picture/%6eight", "barehome://picture:80/day",
                "BAREHOME://picture/day", "barehome://picture/../day", "https://picture/night",
                "barehome://user@picture/day", "barehome://picture/toggle"};
        for (String uri : rejected) check(PictureCommand.selectedNight(uri) == null, "Unsafe URI: " + uri);
        check(PictureCommand.opensInputs("barehome://inputs"), "Input URI");
        for (String uri : new String[] {null, "", "barehome://inputs/", "barehome://inputs?source=3",
                "barehome://inputs#3", "barehome://inputs/hdmi3", "barehome://inputs:80",
                "barehome://picture/night", "barehome://user@inputs", "BAREHOME://inputs"}) {
            check(!PictureCommand.opensInputs(uri), "Unsafe input URI: " + uri);
        }
        check(PictureCommand.selectedNight("barehome://inputs") == null,
                "Opening sources must not select a picture preset");
    }
    private static void startupAndExplicitSelection() {
        Device d = new Device();
        PictureController controller = new PictureController(d);
        controller.refresh();
        noWrites(d);
        d.pair(60, 50);
        check(!controller.selectedNight(), "New Day should be selected");
        int commits = d.prefs.commits;
        controller.refresh();
        check(d.prefs.commits == commits, "Resume should not rewrite unchanged preferences");
        check(controller.select(true), "Night select failed");
        d.pair(5, 20);
        check(controller.selectedNight(), "Night selection was not saved");
        int writes = d.provider.writes.size();
        commits = d.prefs.commits;
        check(controller.select(true), "Repeated Night select failed");
        check(d.provider.writes.size() == writes, "Repeated Night changed TV values again");
        check(d.prefs.commits == commits, "Repeated Night rewrote unchanged preferences");
        check(controller.selectedNight(), "Repeated Night toggled unexpectedly");
        d.provider.values.put("picture_brightness", 40);
        check(controller.select(true), "Explicit same-mode selection must repair an external level change");
        d.pair(5, 20);
        check(controller.select(false), "Day select failed");
        d.pair(60, 50);
        check(!controller.selectedNight(), "Day selection was not saved");
        for (String write : d.provider.writes) check(write.startsWith("picture_backlight=")
                || write.startsWith("picture_brightness="), "Wrote an unrelated setting");
    }
    private static void allGatesPreventWrites() {
        Device d = new Device();
        PictureController c = new PictureController(d);
        Build.MODEL = "other"; check(!c.select(true), "Model gate bypassed");
        Build.MODEL = "CSV4K"; Build.DEVICE = "other"; check(!c.select(true), "Device gate bypassed");
        Build.DEVICE = "zhongshan"; Build.PRODUCT = "other"; check(!c.select(true), "Product gate bypassed");
        Build.PRODUCT = "KTC_MOTOROLA_4K_SA_21V2";
        d.permission = false; check(!c.select(true), "Permission gate bypassed"); d.permission = true;
        d.provider.values.put("picture_als", 1); check(!c.select(true), "Ambient control gate bypassed");
        d.provider.values.put("picture_als", 0);
        d.provider.values.put("key_picture_mode_movie_auto_backlight", 1);
        check(!c.select(true), "Automatic backlight gate bypassed");
        d.provider.values.put("key_picture_mode_movie_auto_backlight", 0);
        c.refresh();
        d.provider.values.put("picture_mode", 7); check(!c.select(true), "Picture mode gate bypassed");
        d.provider.values.put("picture_mode", 9);
        d.provider.values.put("picture_brightness", -1); check(!c.select(true), "Missing level gate bypassed");
        d.provider.values.put("picture_brightness", 101); check(!c.select(true), "Invalid level gate bypassed");
        d.provider.values.put("picture_brightness", 50);
        d.provider.denied = true; check(!c.select(true), "SecurityException escaped");
        d.provider.denied = false;
        noWrites(d); d.pair(60, 50);
        Device legacy = new Device();
        legacy.prefs.values.put("night_backlight", 70);
        legacy.prefs.values.put("night_brightness", 50);
        legacy.prefs.values.put("night_picture_mode", 7);
        check(!new PictureController(legacy).select(true), "Wrong-mode legacy migration was applied");
        noWrites(legacy);
    }
    private static void failedWritesAndSavesRollBack() {
        for (int failingWrite : new int[] {1, 2}) {
            Device d = new Device();
            PictureController c = new PictureController(d); c.refresh();
            d.provider.failingWrites.add(failingWrite);
            check(!c.select(true), "Failed write reported success"); d.pair(60, 50);
            check(!c.selectedNight(), "Failed write persisted Night");
            check(Toast.lastMessage.contains("previous levels kept"), "Rollback result missing");
        }
        Device d = new Device(); PictureController c = new PictureController(d); c.refresh();
        d.prefs.failCommit = d.prefs.commits + 1;
        check(!c.select(true), "Failed preference save reported success"); d.pair(60, 50);
        PictureController restarted = new PictureController(d); restarted.refresh();
        check(!restarted.selectedNight(), "Failed commit's in-memory selection was not restored");
        Device partial = new Device(); PictureController pc = new PictureController(partial); pc.refresh();
        partial.provider.failingWrites.add(2); partial.provider.failingWrites.add(3);
        check(!pc.select(true), "Failed rollback reported success");
        check(Toast.lastMessage.contains("incomplete"), "Failed rollback was hidden");
    }
    private static void remoteAndHomeShareLatestProfiles() {
        Device d = new Device();
        PictureController home = new PictureController(d); home.refresh();
        check(home.saveMode(9, false, 70, 45), "Day editor save failed");
        PictureController remote = new PictureController(d);
        check(remote.select(true), "Remote Night failed");
        home.refresh(); check(home.selectedNight(), "Home retained stale active mode");
        check(home.saveMode(9, true, 8, 22), "Night editor save failed");
        check(remote.select(false), "Remote Day failed"); d.pair(70, 45);
        check(remote.select(true), "Remote reused stale Night profile"); d.pair(8, 22);
        int writes = d.provider.writes.size();
        check(!home.saveMode(9, false, 90, 90), "Stale editor overwrote remotely selected mode");
        check(!home.saveMode(7, true, 8, 22), "Wrong-mode editor save accepted");
        check(!home.saveMode(9, true, 4, 22), "Out-of-range editor save accepted");
        check(d.provider.writes.size() == writes, "Rejected editor save wrote settings");
    }

    private static void snapshotsReadActualStateWithoutWrites() {
        Device d = new Device();
        PictureController c = new PictureController(d);
        check("unavailable".equals(c.snapshot()), "Missing profiles invented a mode");
        check(d.prefs.commits == 0, "Snapshot initialized profiles");
        check(!c.toggleRemote(), "Remote toggle invented a saved choice");
        noWrites(d);
        c.refresh();
        int commits = d.prefs.commits;
        check("day".equals(c.snapshot()), "Actual Day not reported");
        check(d.prefs.commits == commits, "Snapshot rewrote preferences");
        d.provider.values.put("picture_brightness", 49);
        check("custom".equals(c.snapshot()), "Externally changed brightness reported Day");
        d.provider.values.put("picture_brightness", 50);
        d.provider.values.put("picture_backlight", 59);
        check("custom".equals(c.snapshot()), "Externally changed backlight reported Day");
        d.provider.values.put("picture_backlight", 60);
        d.provider.values.put("picture_mode", 7);
        check("custom".equals(c.snapshot()), "Wrong TV picture mode reported Day");
        check(!c.toggleRemote(), "Wrong-mode remote toggle accepted");
        d.provider.values.put("picture_mode", 9);
        d.permission = false;
        check("unavailable".equals(c.snapshot()), "Missing permission reported usable mode");
        d.permission = true;
        d.provider.values.put("picture_als", 1);
        check("unavailable".equals(c.snapshot()), "Ambient control reported usable mode");
        d.provider.values.put("picture_als", 0);
        d.provider.values.put("key_picture_mode_movie_auto_backlight", 1);
        check("unavailable".equals(c.snapshot()), "Automatic backlight reported usable mode");
        d.provider.values.put("key_picture_mode_movie_auto_backlight", 0);
        d.prefs.values.put(PictureProfiles.NIGHT_BACKLIGHT, 101);
        check("unavailable".equals(c.snapshot()), "Corrupted profile silently repaired by snapshot");
        check(d.prefs.values.get(PictureProfiles.NIGHT_BACKLIGHT) == 101,
                "Snapshot persisted repaired profile");
        d.prefs.values.put(PictureProfiles.NIGHT_BACKLIGHT, 5);
        d.prefs.values.put(PictureProfiles.ACTIVE_NIGHT, 2);
        check("unavailable".equals(c.snapshot()), "Corrupt selected mode accepted");
        d.prefs.values.put(PictureProfiles.ACTIVE_NIGHT, 0);
        Build.MODEL = "Other";
        check("unavailable".equals(c.snapshot()), "Unsupported model reported a mode");
        Build.MODEL = "CSV4K";
        d.provider.denied = true;
        check("unavailable".equals(c.snapshot()), "Snapshot read error escaped");
        d.provider.denied = false;
        check(d.prefs.commits == commits, "Read-only snapshots committed preferences");
        noWrites(d);
        check(c.toggleRemote(), "Remote toggle from saved Day failed");
        check("night".equals(c.snapshot()), "Remote toggle did not report actual Night");
        PictureController secondPhone = new PictureController(d);
        check("night".equals(secondPhone.snapshot()), "Other phone did not read current TV mode");
        check(secondPhone.toggleRemote(), "Second phone could not toggle current TV mode");
        check("day".equals(c.snapshot()), "First phone retained stale mode");
        d.prefs.values.put(PictureProfiles.ACTIVE_NIGHT, 1);
        check("custom".equals(c.snapshot()), "Selected Night with actual Day levels reported Night");
    }
    public static void main(String[] args) {
        commandsAreExact(); startupAndExplicitSelection(); allGatesPreventWrites();
        failedWritesAndSavesRollBack(); remoteAndHomeShareLatestProfiles();
        snapshotsReadActualStateWithoutWrites();
        System.out.println("PictureController: " + assertions + " behavioral assertions passed");
    }
}
