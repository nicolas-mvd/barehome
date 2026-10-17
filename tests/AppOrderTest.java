package uy.nico.barehome;

/** Upgrade behavior, independent of Android storage. */
public final class AppOrderTest {
    private static final class MemoryStore implements AppOrder.Store {
        String saved;
        int writes;
        boolean writable = true;
        @Override public String read() { return saved; }
        @Override public boolean save(String value) {
            writes++;
            if (writable) saved = value;
            return writable;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        MemoryStore personal = new MemoryStore();
        AppOrder first = AppOrder.load(personal, new String[] {"app.video", "app.movies", "app.music"});
        require(first.persisted() && personal.writes == 1, "first launch saves personal order");
        require(first.rank("app.video") < first.rank("app.movies"), "personal order precedes alphabetical order");
        require(first.rank("app.music") < first.rank("app.newlyinstalled"), "new apps follow saved pins");

        AppOrder publicUpgrade = AppOrder.load(personal, new String[0]);
        require(publicUpgrade.rank("app.video") == 0 && publicUpgrade.rank("app.movies") == 1
                && publicUpgrade.rank("app.music") == 2, "empty public config must not erase personal pins");
        require(personal.writes == 1, "upgrades must not rewrite captured order");

        AppOrder personalUpgrade = AppOrder.load(personal, new String[] {"app.music", "app.video"});
        require(personalUpgrade.rank("app.video") == 0 && personalUpgrade.rank("app.music") == 2,
                "stored order takes precedence over subsequent compiled changes");

        MemoryStore alphabetical = new MemoryStore();
        AppOrder.load(alphabetical, new String[0]);
        AppOrder laterPersonal = AppOrder.load(alphabetical, new String[] {"app.video"});
        require(laterPersonal.rank("app.video") == laterPersonal.rank("app.movies"),
                "captured empty order remains intentionally alphabetical");
        require(alphabetical.writes == 1, "empty order counts as initialized");

        MemoryStore transientFailure = new MemoryStore();
        transientFailure.writable = false;
        AppOrder failed = AppOrder.load(transientFailure, new String[] {"app.video"});
        require(!failed.persisted() && failed.rank("app.video") == 0, "failed capture preserves visible order and blocks update");
        transientFailure.writable = true;
        require(AppOrder.load(transientFailure, new String[] {"app.video"}).persisted(), "failed capture can be retried");
        require(AppOrder.load(transientFailure, new String[0]).rank("app.video") == 0, "successful retry survives public upgrade");

        MemoryStore normalized = new MemoryStore();
        AppOrder unique = AppOrder.load(normalized, new String[] {"app.video", "app.video", "app.movies", "", null});
        require(unique.rank("app.movies") == 1 && unique.rank("app.other") == 2, "duplicates and empty entries cannot distort order");

        AppOrder unreadable = AppOrder.load(new AppOrder.Store() {
            @Override public String read() { throw new IllegalStateException("damaged preferences"); }
            @Override public boolean save(String value) { throw new AssertionError("must not overwrite unreadable order"); }
        }, new String[] {"app.video"});
        require(!unreadable.persisted(), "unreadable preferences do not pretend migration succeeded");
        System.out.println("7 app order migration scenarios passed");
    }
}
