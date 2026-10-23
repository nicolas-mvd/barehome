package uy.nico.barehome;

import android.content.Context;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public final class DogModeAccessibilityTest {
    private static int assertions;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] arguments) throws Exception {
        Context context = new Context(new File(arguments[0]));
        check(!DogModeAccessibility.hasConsent(context), "Missing consent granted access");
        try (FileOutputStream oldGrant = new FileOutputStream(new File(arguments[0], "dog-mode-accessibility-consent"))) {
            oldGrant.write("allow-nsnow409-v1".getBytes(StandardCharsets.US_ASCII));
        }
        check(!DogModeAccessibility.hasConsent(context) && !DogModeAccessibility.available(context),
                "Old helper-only consent authorized new debugging scope");
        AtomicFile.failFinish = true;
        check(!DogModeAccessibility.setConsent(context, true), "Failed grant claimed durable success");
        check(!DogModeAccessibility.hasConsent(context), "Failed grant leaked an allowed in-memory cache");
        check(!DogModeAccessibility.available(context), "Failed grant allowed helper startup");
        AtomicFile.failFinish = false;
        check(DogModeAccessibility.setConsent(context, true), "Explicit consent could not be stored");
        check(DogModeAccessibility.hasConsent(context) && DogModeAccessibility.available(context), "Durable consent not read");
        Context restarted = new Context(new File(arguments[0]));
        check(DogModeAccessibility.hasConsent(restarted), "Consent did not survive new context");
        AtomicFile.failFinish = true;
        check(!DogModeAccessibility.setConsent(context, false), "Failed revoke claimed durable success");
        AtomicFile.failFinish = false;
        check(DogModeAccessibility.setConsent(context, false), "Explicit revoke not stored");
        check(!DogModeAccessibility.hasConsent(restarted), "Revoke left stale allowed cache");

        String helper = "uy.nico.barehome/uy.nico.barehome.NsNowDogService";
        String others = "reader.app/.Reader:other.app/other.app.Other";
        String combined = DogModeAccessibility.edit(others, helper, true);
        check(combined.equals(others + ":" + helper), "Enable changed unrelated components");
        check(DogModeAccessibility.edit(combined, helper, false).equals(others), "Disable changed unrelated components");
        check(DogModeAccessibility.edit(others + ":uy.nico.barehome/.NsNowDogService", helper, false).equals(others),
                "Short component alias not removed");
        check(DogModeAccessibility.edit(null, helper, false) == null, "No-op invented enabled setting");
        check(DogModeAccessibility.edit(helper + ":" + others + ":uy.nico.barehome/.NsNowDogService", helper, false).equals(others),
                "Duplicate own aliases survived cleanup");
        check(!DogModeAccessibility.contains(others, helper), "Other service mistaken for helper");
        check(DogModeAccessibility.contains(combined, helper), "Helper missing from enabled set");
        System.out.println("DogModeAccessibility: " + assertions + " behavioral assertions passed");
    }
}
