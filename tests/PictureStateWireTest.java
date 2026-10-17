package uy.nico.barehome;

import java.nio.charset.StandardCharsets;

public final class PictureStateWireTest {
    private static int assertions;
    private static final String TOKEN = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f";
    private static final String QUERY = "?reply=192.168.1.8&port=12345&token=" + TOKEN;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static void parserAndMac() throws Exception {
        PictureStateWire.Request request = PictureStateWire.parse("barehome://picture/state" + QUERY);
        check(request != null && !request.toggle && request.port == 12345, "Valid state request");
        check(PictureStateWire.parse("barehome://picture/toggle" + QUERY).toggle, "Valid toggle");
        String expected = "BH1|630dcd2966c4336691125448bbb25b4ff412a49c732db2c8abc1b8581bd710dd|night|"
                + "ec25732dbfb5835434bea4e9ac99b545f90763a5a090ec5660aaffcc6917646e";
        check(expected.equals(new String(PictureStateWire.reply(request, "night"), StandardCharsets.US_ASCII)),
                "Known SHA256/HMAC vector disagrees with independent Python implementation");
        for (String state : new String[] {"day", "night", "custom", "unavailable", "error"}) {
            byte[] reply = PictureStateWire.reply(request, state);
            check(reply.length <= PictureStateWire.MAX_REPLY_BYTES, "Unbounded reply");
            check(new String(reply, StandardCharsets.US_ASCII).contains("|" + state + "|"), "Wrong state");
        }
        boolean rejected = false;
        try { PictureStateWire.reply(request, "unknown"); } catch (IllegalArgumentException expectedError) { rejected = true; }
        check(rejected, "Unknown state accepted");
        String good = "barehome://picture/state" + QUERY;
        String[] invalid = {null, "", "barehome://picture/state", good + "&extra=1", good + "#fragment",
                good.replace("state?", "state/?"), good.replace("state?", "%73tate?"),
                good.replace("state", "day"), good.replace("192.168.1.8", "8.8.8.8"),
                good.replace("192.168.1.8", "127.0.0.1"), good.replace("192.168.1.8", "169.254.1.1"),
                good.replace("192.168.1.8", "192.168.001.8"), good.replace("192.168.1.8", "192.168.1.256"),
                good.replace("192.168.1.8", "192.168.1.8%00"), good.replace("192.168.1.8", "example.com"),
                good.replace("port=12345", "port=1023"), good.replace("port=12345", "port=65536"),
                good.replace("port=12345", "port=01234"), good.replace("port=12345", "port=+1234"),
                good.replace(TOKEN, TOKEN.toUpperCase()), good.replace(TOKEN, TOKEN.substring(2)),
                good.replace(TOKEN, TOKEN + "00"), good.replace("reply=", "reply=192.168.1.8&reply="),
                "barehome://picture/state?port=12345&reply=192.168.1.8&token=" + TOKEN,
                good.replace("barehome", "BAREHOME"), good + "\n"};
        for (String value : invalid) check(PictureStateWire.parse(value) == null, "Invalid request accepted: " + value);
        check(PictureStateWire.parse(good.replace("port=12345", "port=1024")) != null, "Min port rejected");
        check(PictureStateWire.parse(good.replace("port=12345", "port=65535")) != null, "Max port rejected");
    }
    private static void subnetBoundaries() {
        byte[] local = PictureStateWire.ipv4("192.168.1.6");
        check(PictureStateWire.allowedPeer(PictureStateWire.ipv4("192.168.1.8"), local, 24), "Local peer rejected");
        for (String peer : new String[] {"192.168.1.6", "192.168.1.0", "192.168.1.255", "192.168.2.8",
                "8.8.8.8", "224.0.0.1", "255.255.255.255", "127.0.0.1"}) {
            check(!PictureStateWire.allowedPeer(PictureStateWire.ipv4(peer), local, 24), "Unsafe callback: " + peer);
        }
        check(PictureStateWire.allowedPeer(PictureStateWire.ipv4("10.1.1.2"), PictureStateWire.ipv4("10.1.1.1"), 30), "Valid /30 peer");
        check(!PictureStateWire.allowedPeer(PictureStateWire.ipv4("10.1.1.3"), PictureStateWire.ipv4("10.1.1.1"), 30), "/30 broadcast");
        check(!PictureStateWire.allowedPeer(PictureStateWire.ipv4("10.1.1.0"), PictureStateWire.ipv4("10.1.1.1"), 30), "/30 network");
        for (int prefix : new int[] {-1, 0, 31, 32, 33}) {
            check(!PictureStateWire.allowedPeer(PictureStateWire.ipv4("192.168.1.8"), local, prefix), "Bad prefix accepted");
        }
        for (String address : new String[] {"10.0.0.8", "172.16.0.8", "172.31.255.8", "192.168.0.8"}) {
            check(PictureStateWire.privateAddress(PictureStateWire.ipv4(address)), "RFC1918 address rejected");
        }
        for (String address : new String[] {"172.15.0.8", "172.32.0.8", "192.169.0.8", "100.64.0.8"}) {
            check(!PictureStateWire.privateAddress(PictureStateWire.ipv4(address)), "Non-private address accepted");
        }
    }
    private static final class Store implements PictureRequestCache.Store {
        String saved = "";
        int saves;
        boolean fail;
        public String read() { return saved; }
        public boolean save(String value) { saves++; if (!fail) saved = value; return !fail; }
    }
    private static String id(int number) { return String.format("%064x", number); }
    private static void recentRequestsAreConsumedBeforeApplying() {
        Store store = new Store();
        check(PictureRequestCache.consume(store, id(1)) == PictureRequestCache.NEW, "First token not saved");
        check(store.saves == 1, "Consume did not persist synchronously");
        check(PictureRequestCache.consume(store, id(1)) == PictureRequestCache.DUPLICATE, "Replay accepted");
        check(store.saves == 1, "Duplicate unexpectedly persisted");
        Store afterRestart = new Store(); afterRestart.saved = store.saved;
        check(PictureRequestCache.consume(afterRestart, id(1)) == PictureRequestCache.DUPLICATE, "Replay after restart accepted");
        store.fail = true;
        check(PictureRequestCache.consume(store, id(2)) == PictureRequestCache.FAILED, "Failed commit accepted");
        store.fail = false;
        for (int i = 2; i <= 17; i++) {
            check(PictureRequestCache.consume(store, id(i)) == PictureRequestCache.NEW, "Fresh request rejected");
        }
        check(store.saved.split(",").length == 16, "Cache is unbounded");
        check(PictureRequestCache.consume(store, id(2)) == PictureRequestCache.DUPLICATE, "Recent token lost");
        check(PictureRequestCache.consume(store, id(1)) == PictureRequestCache.NEW, "Oldest ID was not evicted");
        for (String corrupt : new String[] {null, "broken", id(1) + ",", id(1).toUpperCase().replace('0', 'G')}) {
            store.saved = corrupt;
            check(PictureRequestCache.consume(store, id(9)) == PictureRequestCache.FAILED, "Corrupt cache did not fail closed");
        }
        store.saved = "";
        check(PictureRequestCache.consume(store, "invalid") == PictureRequestCache.FAILED, "Invalid ID accepted");
    }
    public static void main(String[] args) throws Exception {
        parserAndMac(); subnetBoundaries(); recentRequestsAreConsumedBeforeApplying();
        System.out.println("PictureStateWire: " + assertions + " behavioral assertions passed");
    }
}
