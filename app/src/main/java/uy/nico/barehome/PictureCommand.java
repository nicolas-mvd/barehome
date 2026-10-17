package uy.nico.barehome;

/** Exact allowlist: saved picture presets or the TV's own source menu. */
final class PictureCommand {
    static Boolean selectedNight(String uri) {
        if ("barehome://picture/day".equals(uri)) return Boolean.FALSE;
        if ("barehome://picture/night".equals(uri)) return Boolean.TRUE;
        return null;
    }

    static boolean opensInputs(String uri) {
        return "barehome://inputs".equals(uri);
    }

    private PictureCommand() {}
}
