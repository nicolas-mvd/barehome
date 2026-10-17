package uy.nico.barehome;

import java.util.LinkedHashSet;

/** Saves the initial compiled order once, including an intentionally empty alphabetical order. */
final class AppOrder {
    interface Store {
        String read();
        boolean save(String packages);
    }

    private final String[] packages;
    private final boolean persisted;

    private AppOrder(String[] packages, boolean persisted) {
        this.packages = packages;
        this.persisted = persisted;
    }

    static AppOrder load(Store store, String[] initialPackages) {
        String saved;
        try {
            saved = store.read();
        } catch (RuntimeException damaged) {
            // Do not overwrite damaged preferences with a public build's empty default.
            return new AppOrder(normalize(initialPackages), false);
        }
        if (saved != null) return new AppOrder(normalize(saved.split("\n", -1)), true);
        String[] initial = normalize(initialPackages);
        StringBuilder encoded = new StringBuilder();
        for (String item : initial) {
            if (encoded.length() > 0) encoded.append('\n');
            encoded.append(item);
        }
        boolean committed;
        try {
            committed = store.save(encoded.toString());
        } catch (RuntimeException failed) {
            committed = false;
        }
        return new AppOrder(initial, committed);
    }

    private static String[] normalize(String[] values) {
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && value.matches("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)+")) unique.add(value);
            }
        }
        return unique.toArray(new String[0]);
    }

    int rank(String packageName) {
        for (int i = 0; i < packages.length; i++) {
            if (packages[i].equals(packageName)) return i;
        }
        return packages.length;
    }

    boolean persisted() { return persisted; }
}
