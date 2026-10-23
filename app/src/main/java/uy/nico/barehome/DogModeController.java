package uy.nico.barehome;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Exact CSV4K energy/debugging controls and a separately verified, fixed channel capability. */
final class DogModeController {
    private static final String LEGACY_STRATEGY = "csv4k-android11-v1";
    private static final String STRATEGY = "csv4k-android11-v2";
    interface Channel {
        boolean available();
        /** Success requires selecting the fixed CANAL 10 target, not merely opening NS NOW. */
        boolean open();
    }

    private final DogModeTransaction transaction;

    DogModeController(Context context) {
        this(context, new Channel() {
            @Override public boolean available() { return NsNowDogService.available(context); }
            // Production startup uses the asynchronous, generation-checked coordinator.
            @Override public boolean open() { return false; }
        });
    }

    DogModeController(Context context, Channel channel) {
        final Context app = context.getApplicationContext();
        transaction = new DogModeTransaction(new DogModeStore(app), new DogModeTransaction.Device() {
            @Override public boolean available() { return canRestore() && channel.available(); }
            @Override public boolean canRestore() {
                return "CSV4K".equals(Build.MODEL) && "zhongshan".equals(Build.DEVICE)
                        && "KTC_MOTOROLA_4K_SA_21V2".equals(Build.PRODUCT)
                        && Build.VERSION.SDK_INT == 30
                        && app.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                            == PackageManager.PERMISSION_GRANTED;
            }
            @Override public String strategy() { return STRATEGY; }
            @Override public int settingCount() { return 5; }
            @Override public boolean supportsRestore(String strategy, int count) {
                return STRATEGY.equals(strategy) && count == 5 || LEGACY_STRATEGY.equals(strategy) && count == 3;
            }
            @Override public boolean validOriginal(int index, String value) {
                return index < 3 || value == null || "0".equals(value) || "1".equals(value);
            }
            @Override public int disableIndex(int position) {
                // Turn USB debugging off before the Developer options master value.
                return position == 3 ? 4 : position == 4 ? 3 : position;
            }
            @Override public String read(int index) {
                switch (index) {
                    case 0: return Settings.Secure.getString(app.getContentResolver(), "sleep_timeout");
                    case 1: return Settings.Secure.getString(app.getContentResolver(), "attentive_timeout");
                    case 2: return Settings.Global.getString(app.getContentResolver(), "no_signal_auto_power_off");
                    case 3: return Settings.Global.getString(app.getContentResolver(), "development_settings_enabled");
                    case 4: return Settings.Global.getString(app.getContentResolver(), "adb_enabled");
                    default: throw new IllegalArgumentException("Unknown Dog Mode setting");
                }
            }
            @Override public boolean write(int index, String value) {
                if (!canRestore()) return false;
                switch (index) {
                    case 0: return Settings.Secure.putString(app.getContentResolver(), "sleep_timeout", value);
                    case 1: return Settings.Secure.putString(app.getContentResolver(), "attentive_timeout", value);
                    case 2: return Settings.Global.putString(app.getContentResolver(), "no_signal_auto_power_off", value);
                    case 3: return validOriginal(index, value)
                            && Settings.Global.putString(app.getContentResolver(), "development_settings_enabled", value);
                    case 4: return validOriginal(index, value)
                            && Settings.Global.putString(app.getContentResolver(), "adb_enabled", value);
                    default: throw new IllegalArgumentException("Unknown Dog Mode setting");
                }
            }
            @Override public String disabled(int index) {
                switch (index) {
                    case 0: case 1: return "-1";
                    case 2: case 3: case 4: return "0";
                    default: throw new IllegalArgumentException("Unknown Dog Mode setting");
                }
            }
            @Override public boolean openChannel() { return channel.open(); }
            @Override public boolean openHome() {
                try {
                    app.startActivity(new Intent(app, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                    return true;
                } catch (RuntimeException unavailable) {
                    return false;
                }
            }
        });
    }

    String snapshot() { return transaction.snapshot(); }
    String start() { return transaction.start(); }
    String stop() { return transaction.stop(); }
    String stop(boolean helperRemoved) { return transaction.stop(helperRemoved); }
    DogModeTransaction.Start prepareStart() { return transaction.prepareStart(); }
    String completeStart(String generation, boolean verified) { return transaction.completeStart(generation, verified); }
    boolean prepareStop() { return transaction.prepareStop(); }
    String generation() { return transaction.generation(); }
    String activeGeneration() { return transaction.activeGeneration(); }
    boolean permitsHelper(String generation, boolean active) { return transaction.permitsHelper(generation, active); }
}
