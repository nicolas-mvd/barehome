package uy.nico.barehome;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/** A durable, explicit transaction. No timer, service, or automatic recovery writes. */
final class DogModeTransaction {
    static final String OFF = "off";
    static final String ACTIVE = "active";
    static final String STARTING = "starting";
    static final String RESTORE = "restore_required";
    static final String UNAVAILABLE = "unavailable";
    static final String ERROR = "error";
    private static final int MAX_VALUES = 8;
    private static final int MAX_VALUE_BYTES = 128;
    static final int MAX_JOURNAL_BYTES = 2048;

    interface Store {
        /** Empty means no transaction; a failed read must throw, never look empty. */
        String read();
        /** Synchronous durable save. A failed save must not be hidden by an in-memory cache. */
        boolean save(String record);
    }

    interface Device {
        boolean available();
        /** Restoration must remain possible if NS NOW is later removed or its route changes. */
        boolean canRestore();
        /** Immutable version of the verified key allowlist and its meanings. */
        String strategy();
        int settingCount();
        String read(int index);
        /** Null restores an originally absent value, rather than inventing its default. */
        boolean write(int index, String value);
        String disabled(int index);
        /** Only the fixed, verified NS NOW/CANAL 10 route; no caller-supplied target. */
        boolean openChannel();
        boolean openHome();
    }

    private final Store store;
    private final Device device;

    static final class Start {
        final String state;
        final String generation;
        final boolean begins;

        Start(String state, String generation, boolean begins) {
            this.state = state;
            this.generation = generation;
            this.begins = begins;
        }
    }

    DogModeTransaction(Store store, Device device) {
        this.store = store;
        this.device = device;
    }

    /** Does not save, repair, launch, or write any TV setting. */
    String snapshot() {
        try {
            Journal saved = load();
            if (saved == null) return device.available() ? OFF : UNAVAILABLE;
            if (!matchesDevice(saved) || !device.available()) return RESTORE;
            if (!disabledValuesMatch()) return RESTORE;
            if ("active".equals(saved.phase)) return ACTIVE;
            return "starting".equals(saved.phase) ? STARTING : RESTORE;
        } catch (RuntimeException invalid) {
            return ERROR;
        }
    }

    Start prepareStart() {
        Journal pending = null;
        try {
            Journal saved = load();
            // A second phone must never replace the first phone's original values.
            if (saved != null) return new Start(snapshot(), saved.generation, false);
            if (!validDevice() || !device.available()) return new Start(UNAVAILABLE, null, false);
            String[] original = new String[device.settingCount()];
            for (int i = 0; i < original.length; i++) {
                original[i] = device.read(i);
                if (!validValue(original[i]) || !validValue(device.disabled(i))) return new Start(UNAVAILABLE, null, false);
            }
            pending = new Journal(device.strategy(), "preparing", newGeneration(), original);
            // The original values must reach disk before even the first setting changes.
            if (!store.save(pending.encode())) return new Start(ERROR, null, false);
            boolean changed = true;
            for (int i = 0; i < original.length; i++) {
                if (!writeAndConfirm(i, device.disabled(i))) { changed = false; break; }
            }
            if (!changed) {
                return new Start(recover(pending) ? ERROR : RESTORE, null, false);
            }
            if (!store.save(pending.phase("starting").encode())) {
                return new Start(recover(pending) ? ERROR : RESTORE, null, false);
            }
            return new Start(STARTING, pending.generation, true);
        } catch (RuntimeException failed) {
            if (pending != null) {
                try { return new Start(recover(pending) ? ERROR : RESTORE, null, false); }
                catch (RuntimeException recoveryFailed) { return new Start(RESTORE, null, false); }
            }
            // A durable journal remains available to an explicit Stop after failed recovery.
            return new Start(ERROR, null, false);
        }
    }

    /** Only the exact pending generation can become active. Late completions are read-only. */
    String completeStart(String generation, boolean verified) {
        Journal saved = null;
        try {
            saved = load();
            if (saved == null || !saved.generation.equals(generation)
                    || !"starting".equals(saved.phase)) return snapshot();
            if (!verified || !matchesDevice(saved) || !device.available() || !disabledValuesMatch()) {
                return ERROR;
            }
            return store.save(saved.phase("active").encode()) ? ACTIVE : ERROR;
        } catch (RuntimeException failed) {
            return ERROR;
        }
    }

    /** Synchronous adapter retained for deterministic host controllers with an immediate channel. */
    String start() {
        Start prepared = prepareStart();
        if (!prepared.begins) return prepared.state;
        boolean verified;
        try { verified = device.openChannel(); } catch (RuntimeException failed) { verified = false; }
        String state = completeStart(prepared.generation, verified);
        if (ACTIVE.equals(state)) return state;
        return OFF.equals(stop()) ? ERROR : RESTORE;
    }

    /** Durable invalidation happens before the coordinator cancels a selector and restores energy. */
    boolean prepareStop() {
        try {
            Journal saved = load();
            return saved == null || store.save(saved.phase("restoring").encode());
        } catch (RuntimeException failed) {
            return false;
        }
    }

    String generation() {
        try { Journal saved = load(); return saved == null ? null : saved.generation; }
        catch (RuntimeException invalid) { return null; }
    }

    String activeGeneration() { return ACTIVE.equals(snapshot()) ? generation() : null; }

    boolean permitsHelper(String generation, boolean active) {
        try {
            Journal saved = load();
            return saved != null && saved.generation.equals(generation)
                    && (active ? "active" : "starting").equals(saved.phase)
                    && matchesDevice(saved) && device.available() && disabledValuesMatch();
        } catch (RuntimeException invalid) { return false; }
    }

    String stop() { return stop(true); }

    String stop(boolean helperRemoved) {
        try {
            Journal saved = load();
            if (saved == null) return device.openHome() && helperRemoved ? OFF : RESTORE;
            if (!matchesDevice(saved) || !device.canRestore()) return RESTORE;
            return recover(saved, helperRemoved) ? OFF : RESTORE;
        } catch (RuntimeException failed) {
            return RESTORE;
        }
    }

    private boolean recover(Journal saved) { return recover(saved, true); }

    private boolean recover(Journal saved, boolean helperRemoved) {
        if (!store.save(saved.phase("restoring").encode())) return false;
        boolean restored = true;
        // Continue through all keys even when one restore fails; never discard the originals.
        for (int i = 0; i < saved.original.length; i++) {
            try {
                if (!writeAndConfirm(i, saved.original[i])) restored = false;
            } catch (RuntimeException failed) {
                restored = false;
            }
        }
        if (!restored || !device.openHome()) return false;
        return helperRemoved && store.save("");
    }

    private boolean writeAndConfirm(int index, String value) {
        if (!equal(device.read(index), value) && !device.write(index, value)) return false;
        return equal(device.read(index), value);
    }

    private boolean disabledValuesMatch() {
        for (int i = 0; i < device.settingCount(); i++) {
            if (!equal(device.read(i), device.disabled(i))) return false;
        }
        return true;
    }

    private boolean validDevice() {
        return device.strategy() != null && device.strategy().matches("[a-z0-9_-]{1,40}")
                && device.settingCount() > 0 && device.settingCount() <= MAX_VALUES;
    }

    private boolean matchesDevice(Journal saved) {
        return validDevice() && saved.strategy.equals(device.strategy())
                && saved.original.length == device.settingCount();
    }

    private Journal load() {
        String record = store.read();
        if (record == null) throw new IllegalStateException("Unreadable Dog Mode journal");
        if (record.isEmpty()) return null;
        return Journal.parse(record);
    }

    private static boolean validValue(String value) {
        if (value == null) return true;
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return bytes.length <= MAX_VALUE_BYTES && value.equals(new String(bytes, StandardCharsets.UTF_8));
    }

    private static boolean equal(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private static String newGeneration() { return UUID.randomUUID().toString().replace("-", ""); }

    private static final class Journal {
        final String strategy;
        final String phase;
        final String generation;
        final String[] original;

        Journal(String strategy, String phase, String generation, String[] original) {
            this.strategy = strategy;
            this.phase = phase;
            this.generation = generation;
            this.original = original.clone();
        }

        Journal phase(String next) { return new Journal(strategy, next, generation, original); }

        String encode() {
            StringBuilder out = new StringBuilder("D2|").append(strategy).append('|').append(phase)
                    .append('|').append(generation);
            for (String value : original) {
                out.append('|').append(value == null ? "~" : Base64.getEncoder()
                        .encodeToString(value.getBytes(StandardCharsets.UTF_8)));
            }
            return out.toString();
        }

        static Journal parse(String record) {
            if (record.length() > MAX_JOURNAL_BYTES) throw new IllegalArgumentException("Journal too long");
            String[] parts = record.split("\\|", -1);
            boolean legacy = "D1".equals(parts[0]);
            int offset = legacy ? 3 : 4;
            if (parts.length < offset + 1 || parts.length > MAX_VALUES + offset
                    || !(legacy || "D2".equals(parts[0]))
                    || !parts[1].matches("[a-z0-9_-]{1,40}")
                    || !("preparing".equals(parts[2]) || "active".equals(parts[2])
                        || "starting".equals(parts[2]) || "restoring".equals(parts[2]))
                    || (!legacy && !parts[3].matches("[0-9a-f]{32}"))) throw new IllegalArgumentException("Invalid journal");
            String generation = legacy ? "00000000000000000000000000000000" : parts[3];
            String[] values = new String[parts.length - offset];
            for (int i = 0; i < values.length; i++) {
                if ("~".equals(parts[i + offset])) continue;
                byte[] bytes = Base64.getDecoder().decode(parts[i + offset]);
                if (bytes.length > MAX_VALUE_BYTES) throw new IllegalArgumentException("Value too long");
                String value = new String(bytes, StandardCharsets.UTF_8);
                if (!Arrays.equals(bytes, value.getBytes(StandardCharsets.UTF_8))
                        || !Base64.getEncoder().encodeToString(bytes).equals(parts[i + offset])) {
                    throw new IllegalArgumentException("Invalid value encoding");
                }
                values[i] = value;
            }
            return new Journal(parts[1], parts[2], generation, values);
        }
    }
}
