package uy.nico.barehome;

/** Edit, compile, install. No configuration service or settings database. */
final class Config {
    private Config() {}

    static final int BACKGROUND = 0xff080808;
    static final int FOREGROUND = 0xffeeeeea;
    static final int MUTED = 0xff92928e;
    static final int COLUMNS = 3;
    static final float MARGIN_DP = 40;
    static final float ROW_HEIGHT_DP = 64;
    static final float COLUMN_GAP_DP = 20;
    static final float TEXT_SIZE_SP = 22;
    static final int BRIGHTNESS_STEP_PERCENT = 5;
    static final int BRIGHTNESS_MIN_PERCENT = 5;

    // Add package names here to pin apps first. Otherwise apps are alphabetical.
    // Example: { "org.videolan.vlc", "com.netflix.ninja" };
    static final String[] PINNED_PACKAGES = {};

    static int rank(String packageName) {
        for (int i = 0; i < PINNED_PACKAGES.length; i++) {
            if (PINNED_PACKAGES[i].equals(packageName)) return i;
        }
        return PINNED_PACKAGES.length;
    }
}
