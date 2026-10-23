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
                && "0".equals(context.resolver.global.get("no_signal_auto_power_off")), "Wrong disable namespace/value");
        check(context.resolver.writes == 3, "Start wrote outside exact three-key allowlist");
        check("1".equals(context.resolver.secure.get("screensaver_enabled"))
                && "7200000".equals(context.resolver.system.get("screen_off_timeout")), "Screensaver/display timeout changed");
        context.permission = -1;
        check("restore_required".equals(controller.stop()), "Revoked permission lost recovery");
        check(context.homes == 0, "Permission failure hid recovery by opening Home");
        context.permission = 0;
        check("off".equals(new DogModeController(context, channel(false, false)).stop()), "Missing channel route blocked restore");
        check(context.resolver.secure.equals(secure) && context.resolver.global.equals(global), "Exact prior settings not restored");
        check(context.homes == 1, "Successful stop did not open Home");

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
}
