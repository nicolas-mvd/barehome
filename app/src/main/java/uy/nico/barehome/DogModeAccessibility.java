package uy.nico.barehome;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Explicit opt-in and edits to only BareHome's own accessibility-service component. */
final class DogModeAccessibility {
    private static final Object SETTINGS_LOCK = new Object();
    private static final byte[] ALLOWED = "allow-nsnow409-v1".getBytes(StandardCharsets.US_ASCII);

    static boolean hasConsent(Context context) {
        synchronized (SETTINGS_LOCK) {
            try (FileInputStream input = consentFile(context).openRead()) {
                for (byte expected : ALLOWED) if (input.read() != (expected & 255)) return false;
                return input.read() == -1;
            } catch (IOException | RuntimeException missingOrInvalid) { return false; }
        }
    }
    static boolean setConsent(Context context, boolean allowed) {
        synchronized (SETTINGS_LOCK) {
            AtomicFile file = consentFile(context);
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                if (allowed) output.write(ALLOWED);
                output.getFD().sync();
                file.finishWrite(output); output = null;
                return hasConsent(context) == allowed;
            } catch (IOException | RuntimeException failed) {
                if (output != null) file.failWrite(output);
                return false;
            }
        }
    }
    private static AtomicFile consentFile(Context context) {
        return new AtomicFile(new File(context.getFilesDir(), "dog-mode-accessibility-consent"));
    }
    static boolean available(Context context) {
        if (!hasConsent(context) || !"CSV4K".equals(Build.MODEL) || !"zhongshan".equals(Build.DEVICE)
                || !"KTC_MOTOROLA_4K_SA_21V2".equals(Build.PRODUCT) || Build.VERSION.SDK_INT != 30
                || context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return false;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(NsNowDogSelector.PACKAGE, 0);
            return info.versionCode == 409 && "1.1.266".equals(info.versionName)
                    && info.applicationInfo != null && info.applicationInfo.enabled;
        } catch (PackageManager.NameNotFoundException | RuntimeException unavailable) {
            return false;
        }
    }
    static boolean enable(Context context) {
        if (!available(context)) return false;
        synchronized (SETTINGS_LOCK) {
            String before = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String updated = edit(before, component(context), true);
            if (!equal(before, updated) && !Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)) return false;
            String after = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return contains(after, component(context)) && preservesOthers(before, after, component(context));
        }
    }
    static boolean disable(Context context) {
        synchronized (SETTINGS_LOCK) {
            String before = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            String updated = edit(before, component(context), false);
            if (!equal(before, updated) && !Settings.Secure.putString(context.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, updated)) return false;
            String after = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return !contains(after, component(context)) && preservesOthers(before, after, component(context));
        }
    }
    static String component(Context context) { return new ComponentName(context, NsNowDogService.class).flattenToString(); }
    private static boolean equal(String a, String b) { return a == null ? b == null : a.equals(b); }
    private static boolean same(String value, String component) {
        ComponentName a = ComponentName.unflattenFromString(value);
        ComponentName b = ComponentName.unflattenFromString(component);
        return a != null && a.equals(b);
    }
    static boolean contains(String value, String component) {
        if (value == null) return false;
        for (String entry : value.split(":", -1)) if (same(entry, component)) return true;
        return false;
    }
    static String edit(String value, String component, boolean enabled) {
        StringBuilder out = new StringBuilder();
        if (value != null && !value.isEmpty()) {
            for (String entry : value.split(":", -1)) {
                if (same(entry, component)) continue;
                if (out.length() > 0) out.append(':');
                out.append(entry);
            }
        }
        if (enabled) {
            if (out.length() > 0) out.append(':');
            out.append(component);
        }
        // Null is preserved if this helper was never present; do not invent a setting.
        return !enabled && value == null ? null : out.toString();
    }
    private static boolean preservesOthers(String before, String after, String component) {
        if (before == null || before.isEmpty()) return true;
        String remaining = edit(after, component, false);
        String original = edit(before, component, false);
        return original.equals(remaining);
    }
}
