package uy.nico.barehome;

/** A fixed visible setup page, without consent or settings mutations. */
final class DogModeSetup {
    static boolean allowed(String action, String uri, boolean explicitComponent) {
        if ("android.intent.action.VIEW".equals(action)) return "barehome://dog/setup".equals(uri);
        return explicitComponent && uri == null
                && (action == null || "android.intent.action.MAIN".equals(action));
    }

    private DogModeSetup() {}
}
