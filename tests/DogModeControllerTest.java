package uy.nico.barehome;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.HashMap;
import java.util.Map;

public final class DogModeControllerTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    static final class TestContext extends Context {
        int permission = PackageManager.PERMISSION_GRANTED;
        public int checkSelfPermission(String value) {
            check(Manifest.permission.WRITE_SECURE_SETTINGS.equals(value), "Unrelated permission requested");
            return permission;
        }
        public void startActivity(Intent intent) {
            check(intent.target == MainActivity.class, "Exit did not target fixed BareHome");
            homes++;
        }
    }

    private static DogModeController.Channel channel(final boolean available, final boolean open) {
        return new DogModeController.Channel() {
            public boolean available() { return available; }
            public boolean open() { return open; }
        };
    }

    private static TestContext fixture() {
        TestContext context = new TestContext();
        context.resolver.secure.put("sleep_timeout", "86400000");
        context.resolver.secure.put("attentive_timeout", "14100000");
        context.resolver.global.put("no_signal_auto_power_off", "1");
        context.resolver.global.put("development_settings_enabled", "1");
        context.resolver.global.put("adb_enabled", "1");
        context.resolver.global.put("debug_view_attributes", "1");
        context.resolver.secure.put("screensaver_enabled", "1");
        context.resolver.system.put("screen_off_timeout", "7200000");
        DogModeStore.records.clear();
        Build.MODEL = "CSV4K"; Build.DEVICE = "zhongshan";
        Build.PRODUCT = "KTC_MOTOROLA_4K_SA_21V2"; Build.VERSION.SDK_INT = 30;
        return context;
    }

    public static void main(String[] args) {
        TestContext context = fixture();
        Map<String, String> secure = new HashMap<>(context.resolver.secure);
        Map<String, String> global = new HashMap<>(context.resolver.global);
        check("unavailable".equals(new DogModeController(context).start()), "Unverified channel route accepted");
        check(context.resolver.writes == 0 && context.homes == 0, "Unverified route changed TV");
        DogModeController controller = new DogModeController(context, channel(true, true));
        check("off".equals(controller.snapshot()) && context.resolver.writes == 0, "Query changed TV");
        check("active".equals(controller.start()), "Verified channel start failed");
        check("-1".equals(context.resolver.secure.get("sleep_timeout"))
                && "-1".equals(context.resolver.secure.get("attentive_timeout"))
                && "0".equals(context.resolver.global.get("no_signal_auto_power_off"))
                && "0".equals(context.resolver.global.get("development_settings_enabled"))
                && "0".equals(context.resolver.global.get("adb_enabled")), "Wrong disable namespace/value");
        check(context.resolver.writes == 5, "Start wrote outside exact five-key allowlist");
        check(context.resolver.events.indexOf("global:adb_enabled=0")
                < context.resolver.events.indexOf("global:development_settings_enabled=0"), "Developer master disabled before USB debugging");
        check(DogModeStore.records.get(context).startsWith("D2|csv4k-android11-v2|active|")
                && DogModeStore.records.get(context).split("\\|", -1).length == 9,
                "New strategy did not save five distinct originals");
        check("1".equals(context.resolver.secure.get("screensaver_enabled"))
                && "7200000".equals(context.resolver.system.get("screen_off_timeout")), "Screensaver/display timeout changed");
        context.permission = -1;
        check("restore_required".equals(controller.stop()), "Revoked permission lost recovery");
        check(context.homes == 0, "Permission failure hid recovery by opening Home");
        context.permission = 0;
        check("off".equals(new DogModeController(context, channel(false, false)).stop()), "Missing channel route blocked restore");
        check(context.resolver.secure.equals(secure) && context.resolver.global.equals(global), "Exact prior settings not restored");
        check(context.resolver.events.indexOf("global:development_settings_enabled=1")
                < context.resolver.events.indexOf("global:adb_enabled=1"), "USB debugging restored before Developer master");
        check(context.homes == 1, "Successful stop did not open Home");

        debuggingValuesAndFailures();
        legacyRecovery();

        for (int guard = 0; guard < 5; guard++) {
            context = fixture();
            if (guard == 0) Build.MODEL = "Other";
            if (guard == 1) Build.DEVICE = "other";
            if (guard == 2) Build.PRODUCT = "other";
            if (guard == 3) Build.VERSION.SDK_INT = 31;
            if (guard == 4) context.permission = -1;
            check("unavailable".equals(new DogModeController(context, channel(true, true)).start()), "Unsupported hardware/access accepted");
            check(context.resolver.writes == 0 && context.homes == 0, "Hardware/access guard changed TV");
        }
        System.out.println("DogModeController: " + assertions + " behavioral assertions passed");
    }

    private static void debuggingValuesAndFailures() {
        for (String developer : new String[] {null, "0", "1"}) {
            for (String adb : new String[] {null, "0", "1"}) {
                TestContext context = fixture();
                if (developer == null) context.resolver.global.remove("development_settings_enabled");
                else context.resolver.global.put("development_settings_enabled", developer);
                if (adb == null) context.resolver.global.remove("adb_enabled");
                else context.resolver.global.put("adb_enabled", adb);
                Map<String, String> original = new HashMap<>(context.resolver.global);
                DogModeController controller = new DogModeController(context, channel(true, true));
                check("active".equals(controller.start()), "Valid raw boolean/absent debugging state rejected");
                check("off".equals(new DogModeController(context, channel(false, false)).stop()), "Debugging restart recovery failed");
                check(original.equals(context.resolver.global), "Exact debugging state/absence not restored");
            }
        }
        for (String key : new String[] {"development_settings_enabled", "adb_enabled"}) {
            for (String invalid : new String[] {"", "2", "-1", "01", "true", " 1", "1\n"}) {
                TestContext context = fixture();
                context.resolver.global.put(key, invalid);
                check("unavailable".equals(new DogModeController(context, channel(true, true)).start()), "Malformed debugging boolean accepted");
                check(context.resolver.writes == 0 && !DogModeStore.records.containsKey(context),
                        "Invalid baseline changed settings or saved a recoverable fake value");
            }
        }
        TestContext context = fixture();
        Map<String, String> original = new HashMap<>(context.resolver.global);
        context.resolver.failures.add("global:development_settings_enabled");
        check("error".equals(new DogModeController(context, channel(true, true)).start()), "Partial debug disable accepted");
        check(original.equals(context.resolver.global) && DogModeStore.records.get(context).isEmpty(),
                "Partial debug disable did not restore original USB debugging");
        context = fixture();
        DogModeController controller = new DogModeController(context, channel(true, true));
        check("active".equals(controller.start()), "Restore fixture did not start");
        context.resolver.masterRestoreResetsAdb = true;
        check("off".equals(controller.stop()) && "1".equals(context.resolver.global.get("adb_enabled")),
                "Master-before-ADB restoration did not preserve prior debugging state");
        context = fixture();
        controller = new DogModeController(context, channel(true, true));
        controller.start();
        context.resolver.adbRestoreChangesMaster = true;
        check("restore_required".equals(controller.stop()) && !DogModeStore.records.get(context).isEmpty(),
                "Later provider side effect escaped final full-snapshot confirmation");
        context.resolver.adbRestoreChangesMaster = false;
        check("off".equals(controller.stop()), "Provider-side-effect recovery lost saved baseline");
        context = fixture();
        controller = new DogModeController(context, channel(true, true));
        controller.start();
        context.resolver.failures.add("global:development_settings_enabled");
        check("restore_required".equals(controller.stop()) && "1".equals(context.resolver.global.get("adb_enabled"))
                && !DogModeStore.records.get(context).isEmpty(), "Partial debug restore discarded originals or stopped restoring other keys");
        context.resolver.failures.clear();
        check("off".equals(controller.stop()), "Explicit debug restoration recovery failed");
    }

    private static void legacyRecovery() {
        for (String version : new String[] {"D1", "D2"}) {
            for (String phase : new String[] {"active", "starting", "preparing", "restoring"}) {
                TestContext context = fixture();
                context.resolver.secure.put("sleep_timeout", "-1");
                context.resolver.secure.put("attentive_timeout", "-1");
                context.resolver.global.put("no_signal_auto_power_off", "0");
                String generation = "00000000000000000000000000000000";
                String record = version + "|csv4k-android11-v1|" + phase
                        + ("D2".equals(version) ? "|" + generation : "") + "|ODY0MDAwMDA=|~|MDE=";
                DogModeStore.records.put(context, record);
                DogModeController controller = new DogModeController(context, channel(true, true));
                check("restore_required".equals(controller.snapshot()), "Legacy journal looked active under five-key strategy");
                check(!controller.prepareStart().begins && record.equals(DogModeStore.records.get(context))
                        && context.resolver.writes == 0, "Legacy originals were migrated or replaced by Start");
                check(!controller.permitsHelper(generation, true) && !controller.permitsHelper(generation, false),
                        "Legacy journal granted new helper lease");
                check("restore_required".equals(controller.stop(false)) && !DogModeStore.records.get(context).isEmpty(),
                        "Legacy journal cleared without helper removal");
                check(context.resolver.writes == 3 && "1".equals(context.resolver.global.get("development_settings_enabled"))
                        && "1".equals(context.resolver.global.get("adb_enabled")), "Legacy recovery invented debugging originals");
                check("86400000".equals(context.resolver.secure.get("sleep_timeout"))
                        && !context.resolver.secure.containsKey("attentive_timeout")
                        && "01".equals(context.resolver.global.get("no_signal_auto_power_off")), "Legacy recovery lost exact raw/absent energy values");
                check("off".equals(new DogModeController(context, channel(false, false)).stop()),
                        "Legacy restore required new channel capability or consent");
            }
        }
        TestContext context = fixture();
        DogModeStore.records.put(context, "D2|csv4k-android11-v2|active|00000000000000000000000000000000|MA==|MA==|MA==|Mg==|MQ==");
        check("restore_required".equals(new DogModeController(context, channel(true, true)).stop()) && context.resolver.writes == 0,
                "Corrupt journal wrote a nonboolean debugging value");
    }
}
