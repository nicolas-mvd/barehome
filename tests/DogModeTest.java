package uy.nico.barehome;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class DogModeTest {
    private static int assertions;
    private static final String TOKEN = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String QUERY = "?reply=192.168.1.8&port=12345&token=" + TOKEN;

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static final class Store implements DogModeTransaction.Store {
        String record = "";
        int saves;
        Set<Integer> failures = new HashSet<>();
        List<String> events;
        Store(List<String> events) { this.events = events; }
        public String read() { return record; }
        public boolean save(String value) {
            saves++;
            events.add("save:" + (value.isEmpty() ? "off" : value.split("\\|", -1)[2]));
            if (failures.contains(saves)) return false;
            record = value;
            return true;
        }
    }

    private static final class Device implements DogModeTransaction.Device {
        String[] values = {"86400000", null, "01"};
        String[] disabled = {"-1", "0", "0"};
        boolean available = true, restore = true, channel = true, home = true;
        int writes, launches, homes;
        String strategy = "tested-firmware-v1";
        Set<Integer> failures = new HashSet<>();
        List<String> events;
        Device(List<String> events) { this.events = events; }
        public boolean available() { return available; }
        public boolean canRestore() { return restore; }
        public String strategy() { return strategy; }
        public int settingCount() { return values.length; }
        public String read(int index) { return values[index]; }
        public String disabled(int index) { return disabled[index]; }
        public boolean write(int index, String value) {
            writes++;
            events.add("write:" + index + ":" + value);
            if (failures.contains(writes)) return false;
            values[index] = value;
            return true;
        }
        public boolean openChannel() { launches++; events.add("channel"); return channel; }
        public boolean openHome() { homes++; events.add("home"); return home; }
    }

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final Store store = new Store(events);
        final Device device = new Device(events);
        DogModeTransaction transaction() { return new DogModeTransaction(store, device); }
    }

    private static void lifecycleAndReadOnly() {
        Fixture f = new Fixture();
        String[] originals = f.device.values.clone();
        check("off".equals(f.transaction().snapshot()), "Supported idle TV not off");
        check(f.events.isEmpty(), "State query mutated TV");
        check("active".equals(f.transaction().start()), "Start failed");
        check(f.events.get(0).equals("save:preparing"), "Settings changed before durable snapshot");
        check(Arrays.equals(f.device.values, f.device.disabled), "Not all timers disabled");
        check(f.device.launches == 1 && f.device.homes == 0, "Wrong navigation on start");
        String originalJournal = f.store.record;
        int writes = f.device.writes, saves = f.store.saves;
        check("active".equals(f.transaction().start()), "Second client failed to read active mode");
        check(f.device.writes == writes && f.store.saves == saves && f.device.launches == 1,
                "Duplicate start overwrote originals or relaunched app");
        check(originalJournal.equals(f.store.record), "Journal changed on duplicate start");
        check("active".equals(f.transaction().snapshot()), "Restart did not retain active state");
        check("off".equals(f.transaction().stop()), "Stop failed");
        check(Arrays.equals(f.device.values, originals), "Stop lost exact raw/absent originals");
        check(f.device.homes == 1 && f.store.record.isEmpty(), "Stop did not return Home and clear journal");
        check(f.events.get(f.events.size() - 2).equals("home")
                && f.events.get(f.events.size() - 1).equals("save:off"), "Journal cleared before Home");
        writes = f.device.writes;
        check("off".equals(f.transaction().stop()) && f.device.writes == writes, "Stop off rewrote energy values");
    }

    private static void failuresRetainRecovery() {
        Fixture f = new Fixture();
        f.store.failures.add(1);
        check("error".equals(f.transaction().start()), "Failed initial journal accepted");
        check(f.device.writes == 0 && f.device.launches == 0, "Unsaved snapshot changed TV");
        f = new Fixture();
        f.device.failures.add(2);
        String[] original = f.device.values.clone();
        check("error".equals(f.transaction().start()), "Partial disable did not report error");
        check(Arrays.equals(original, f.device.values), "Partial disable not rolled back");
        check(f.device.launches == 0 && f.store.record.isEmpty(), "Partial disable launched channel or lost cleanup");
        f = new Fixture();
        f.device.channel = false;
        original = f.device.values.clone();
        check("error".equals(f.transaction().start()), "Failed channel launch accepted");
        check(Arrays.equals(original, f.device.values) && f.device.homes == 1, "Launch failure did not recover");
        f = new Fixture();
        f.store.failures.add(2); // Active commit, after settings and launch.
        original = f.device.values.clone();
        check("error".equals(f.transaction().start()), "Failed active commit accepted");
        check(Arrays.equals(original, f.device.values) && f.store.record.isEmpty(), "Failed active commit lost originals");
        f = new Fixture();
        original = f.device.values.clone();
        check("active".equals(f.transaction().start()), "Setup failed");
        f.device.failures.add(f.device.writes + 1);
        check("restore_required".equals(f.transaction().stop()), "Incomplete restore looked off");
        check(f.store.record.contains("|restoring|"), "Failed restore discarded journal");
        check(f.device.homes == 0, "Incomplete restore navigated Home and hid failure");
        check(f.device.values[1] == null && "01".equals(f.device.values[2]), "Restore stopped at first failure");
        check("restore_required".equals(f.transaction().start()), "Pending restore overwritten by start");
        check("off".equals(f.transaction().stop()), "Explicit retry could not finish restoration");
        check(Arrays.equals(original, f.device.values), "Retry changed original values");
        f = new Fixture();
        f.transaction().start();
        f.device.home = false;
        check("restore_required".equals(f.transaction().stop()), "Home failure reported exit complete");
        check(!f.store.record.isEmpty(), "Home failure discarded journal");
        f.device.home = true;
        check("off".equals(f.transaction().stop()), "Home failure not recoverable");
    }

    private static void interruptedAndExternalChanges() {
        Fixture f = new Fixture();
        f.store.record = "D1|tested-firmware-v1|preparing|ODY0MDAwMDA=|~|MDE=";
        f.device.values[0] = "-1";
        check("restore_required".equals(f.transaction().snapshot()), "Process death in prepare looked active");
        check(f.events.isEmpty(), "Read query automatically recovered after restart");
        check("restore_required".equals(f.transaction().start()), "Interrupted start replaced original snapshot");
        check("off".equals(f.transaction().stop()), "Interrupted start could not restore");
        check("86400000".equals(f.device.values[0]) && f.device.values[1] == null
                && "01".equals(f.device.values[2]), "Restart recovery changed originals");
        f = new Fixture();
        f.transaction().start();
        f.device.values[0] = "123";
        int writes = f.device.writes;
        check("restore_required".equals(f.transaction().snapshot()), "External timer change looked active");
        check(f.device.writes == writes, "Query overwrote external timer change");
        check("restore_required".equals(f.transaction().start()), "Start silently re-disabled external timer change");
        check(f.device.writes == writes, "Duplicate start mutated energy values");
        f.device.available = false; // App removed; energy access is still allowed.
        check("off".equals(f.transaction().stop()), "App removal blocked energy restoration");
        f = new Fixture();
        f.device.available = false;
        check("unavailable".equals(f.transaction().snapshot()), "Unsupported query claimed off capability");
        check("unavailable".equals(f.transaction().start()) && f.events.isEmpty(), "Unsupported start mutated TV");
        f = new Fixture();
        f.transaction().start();
        f.device.restore = false;
        check("restore_required".equals(f.transaction().stop()) && !f.store.record.isEmpty(), "Permission loss lost snapshot");
        f.device.restore = true;
        check("off".equals(f.transaction().stop()), "Permission recovery could not restore");
    }

    private static void invalidJournalFailsClosed() {
        for (String record : new String[] {"broken", "D2|tested-firmware-v1|active|MA==",
                "D1|other-firmware|active|MA==", "D1|tested-firmware-v1|active|AA=", "D1|tested-firmware-v1|active|/w==",
                "D1|tested-firmware-v1|active|MA==|MA==|MA==|MA==", null}) {
            Fixture f = new Fixture();
            f.store.record = record;
            String state = f.transaction().start();
            check("error".equals(state) || "restore_required".equals(state), "Invalid journal accepted");
            check(f.device.writes == 0 && f.device.launches == 0, "Invalid journal changed TV");
            check("restore_required".equals(f.transaction().stop()), "Corrupt journal stop did not retain failure");
            check(f.device.writes == 0, "Corrupt journal restored invented values");
        }
    }

    private static void asynchronousCancellationAndGenerations() {
        Fixture f = new Fixture();
        String[] original = f.device.values.clone();
        DogModeTransaction.Start first = f.transaction().prepareStart();
        check(first.begins && "starting".equals(first.state) && first.generation.matches("[0-9a-f]{32}"),
                "Async start has no durable generation");
        check(f.store.record.startsWith("D2|") && f.store.record.contains("|starting|" + first.generation + "|"),
                "Starting state not persisted before channel selection");
        check(f.device.launches == 0, "Preparing async start launched channel itself");
        check("starting".equals(f.transaction().snapshot()), "Pending generation not observable");
        check(f.transaction().activeGeneration() == null, "Unverified pending selection granted helper active lease");
        DogModeTransaction.Start duplicate = f.transaction().prepareStart();
        check(!duplicate.begins && first.generation.equals(duplicate.generation), "Second client restarted pending selection");
        check(f.transaction().prepareStop() && f.store.record.contains("|restoring|"), "Stop failed durable invalidation");
        int writes = f.device.writes;
        check("restore_required".equals(f.transaction().completeStart(first.generation, true)), "Late selector activated after Stop");
        check(f.device.writes == writes && f.transaction().activeGeneration() == null, "Late selector mutated stopped transaction");
        check("off".equals(f.transaction().stop()) && Arrays.equals(original, f.device.values), "Stop pending did not restore");
        DogModeTransaction.Start second = f.transaction().prepareStart();
        check(second.begins && !first.generation.equals(second.generation), "New transaction reused old generation");
        writes = f.device.writes;
        check("starting".equals(f.transaction().completeStart(first.generation, true)), "Old completion changed a new start");
        check(f.device.writes == writes && f.transaction().activeGeneration() == null, "Old generation granted new active lease");
        check("active".equals(f.transaction().completeStart(second.generation, true)), "Verified matching completion failed");
        check(second.generation.equals(f.transaction().activeGeneration()), "Verified active lease not durable");
        check("active".equals(f.transaction().completeStart(second.generation, false)), "Duplicate failure undid verified mode");
        check("off".equals(f.transaction().stop()), "Verified async stop failed");
        f = new Fixture();
        original = f.device.values.clone();
        first = f.transaction().prepareStart();
        check("error".equals(f.transaction().completeStart(first.generation, false)), "Failed selector reported success");
        check(!f.store.record.isEmpty(), "Failed selector discarded journal before helper cleanup");
        check("off".equals(f.transaction().stop()), "Failed selector could not restore after helper cleanup");
        check(Arrays.equals(original, f.device.values) && f.store.record.isEmpty(), "Failed async selector lost recovery");
    }

    private static void wire() throws Exception {
        DogModeWire.Request request = DogModeWire.parse("barehome://dog/state" + QUERY);
        check(request != null && !request.mutates(), "State parse failed");
        check(("BH2|630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd|restore_required|"
                + "e544e2b1e15ee30437defca257ba22385929c1eab2ba91e65839531bc6e8d219")
                .equals(new String(DogModeWire.reply(request, "restore_required"), StandardCharsets.US_ASCII)),
                "Reply disagrees with independent Python HMAC vector");
        for (String action : new String[] {"start", "stop"}) {
            DogModeWire.Request mutation = DogModeWire.parse("barehome://dog/" + action + QUERY);
            check(mutation != null && mutation.mutates() && action.equals(mutation.action), "Mutation parse failed");
        }
        for (String state : new String[] {"off", "starting", "active", "restore_required", "unavailable", "error",
                "nsnow_access", "nsnow_menu", "nsnow_channel", "nsnow_player", "nsnow_debug", "nsnow_target", "nsnow_input"}) {
            String packet = new String(DogModeWire.reply(request, state), StandardCharsets.US_ASCII);
            check(packet.startsWith("BH2|630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd|" + state + "|"),
                    "Wrong reply domain or request ID");
            check(packet.length() <= DogModeWire.MAX_REPLY_BYTES, "Reply exceeds bound");
        }
        String good = "barehome://dog/start" + QUERY;
        for (String bad : new String[] {null, "", good + "#x", good + "&x=1", good + "\n",
                good.replace("start?", "%73tart?"), good.replace("dog", "picture"),
                good.replace("start", "toggle"), good.replace("barehome", "BAREHOME"),
                good.replace("192.168.1.8", "8.8.8.8"), good.replace("192.168.1.8", "127.0.0.1"),
                good.replace("192.168.1.8", "192.168.001.8"), good.replace("192.168.1.8", "192.168.1.256"),
                good.replace("port=12345", "port=1023"), good.replace("port=12345", "port=65536"),
                good.replace("port=12345", "port=01234"), good.replace(TOKEN, TOKEN.toUpperCase()),
                good.replace("reply=192.168.1.8&port=12345", "port=12345&reply=192.168.1.8")}) {
            check(DogModeWire.parse(bad) == null, "Invalid URI accepted");
        }
        boolean rejected = false;
        try { DogModeWire.reply(request, "day"); } catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "Picture state accepted in Dog Mode domain");
        for (String bad : new String[] {"nsnow", "nsnow_other", "nsnow_menu|private", "NSNOW_MENU", "nsnow_menu "}) {
            check(!DogModeWire.startupFailure(bad), "Arbitrary startup diagnostic accepted");
            rejected = false;
            try { DogModeWire.reply(request, bad); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Arbitrary diagnostic entered authenticated reply");
        }
    }

    private static void visibleSetupEntry() {
        String view = "android.intent.action.VIEW";
        String uri = "barehome://dog/setup";
        check(DogModeSetup.allowed(view, uri, false), "Exact setup link rejected");
        check(DogModeSetup.allowed(null, null, true), "Explicit system setup launch rejected");
        check(DogModeSetup.allowed("android.intent.action.MAIN", null, true), "Explicit Main setup launch rejected");
        for (String invalid : new String[] {null, "", uri + "?", uri + "?allow=true", uri + "#", uri + "#x",
                uri + "/", "barehome://dog/%73etup", "barehome://DOG/setup", "BAREHOME://dog/setup",
                "barehome://dog/start", "barehome://dog/setup/extra", "barehome://dog:1/setup"}) {
            check(!DogModeSetup.allowed(view, invalid, true), "Invalid setup URI accepted");
        }
        check(!DogModeSetup.allowed(null, null, false), "Implicit no-action entry accepted");
        check(!DogModeSetup.allowed("android.intent.action.MAIN", uri, true), "Data accepted on non-view explicit launch");
        check(!DogModeSetup.allowed("arbitrary.action", null, true), "Arbitrary explicit action accepted");
    }

    public static void main(String[] args) throws Exception {
        lifecycleAndReadOnly(); failuresRetainRecovery(); interruptedAndExternalChanges();
        invalidJournalFailsClosed(); asynchronousCancellationAndGenerations(); wire(); visibleSetupEntry();
        System.out.println("DogMode: " + assertions + " behavioral assertions passed");
    }
}
