package uy.nico.barehome;

/** Compile-time appearance and initial app order. No configuration service. */
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

    // Initial order is captured once in app preferences, preserving it across public updates.
    // Add package names here to pin apps first on a new installation; otherwise alphabetical.
    // Existing saved order takes precedence over later changes to this compiled list.
    // Example: { "org.videolan.vlc", "com.netflix.ninja" };
    static final String[] PINNED_PACKAGES = {};

}
