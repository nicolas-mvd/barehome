package uy.nico.barehome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Fixed Dog Mode commands and one authenticated, coarse-state reply. */
final class DogModeWire {
    static final int MAX_REQUEST_BYTES = 192;
    static final int MAX_REPLY_BYTES = 164;
    private static final Pattern REQUEST = Pattern.compile(
            "barehome://dog/(state|start|stop)\\?reply=([0-9.]{7,15})&port=([1-9][0-9]{3,4})&token=([0-9a-f]{64})");

    static final class Request {
        final String action;
        final byte[] replyAddress;
        final int port;
        final byte[] token;
        final String id;

        private Request(String action, byte[] address, int port, byte[] token)
                throws GeneralSecurityException {
            this.action = action;
            this.replyAddress = address;
            this.port = port;
            this.token = token;
            id = hex(MessageDigest.getInstance("SHA-256").digest(token));
        }

        boolean mutates() { return !"state".equals(action); }
    }

    static Request parse(String uri) {
        if (uri == null || uri.length() > MAX_REQUEST_BYTES) return null;
        Matcher match = REQUEST.matcher(uri);
        if (!match.matches()) return null;
        byte[] address = PictureStateWire.ipv4(match.group(2));
        int port = Integer.parseInt(match.group(3));
        if (!PictureStateWire.privateAddress(address) || port < 1024 || port > 65535) return null;
        byte[] token = new byte[32];
        for (int i = 0; i < token.length; i++) {
            token[i] = (byte) Integer.parseInt(match.group(4).substring(i * 2, i * 2 + 2), 16);
        }
        try { return new Request(match.group(1), address, port, token); }
        catch (GeneralSecurityException unavailable) { return null; }
    }

    static byte[] reply(Request request, String state) throws GeneralSecurityException {
        if (!validState(state)) throw new IllegalArgumentException("Unknown Dog Mode state");
        String prefix = "BH2|" + request.id + "|" + state;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(request.token, "HmacSHA256"));
        byte[] packet = (prefix + "|" + hex(mac.doFinal(prefix.getBytes(StandardCharsets.US_ASCII))))
                .getBytes(StandardCharsets.US_ASCII);
        if (packet.length > MAX_REPLY_BYTES) throw new IllegalArgumentException("Reply too long");
        return packet;
    }

    private static boolean validState(String state) {
        return DogModeTransaction.OFF.equals(state) || DogModeTransaction.ACTIVE.equals(state)
                || DogModeTransaction.STARTING.equals(state)
                || DogModeTransaction.RESTORE.equals(state) || DogModeTransaction.UNAVAILABLE.equals(state)
                || DogModeTransaction.ERROR.equals(state) || startupFailure(state);
    }

    static boolean startupFailure(String state) {
        return "nsnow_access".equals(state) || "nsnow_menu".equals(state)
                || "nsnow_channel".equals(state) || "nsnow_player".equals(state)
                || "nsnow_debug".equals(state) || "nsnow_target".equals(state)
                || "nsnow_input".equals(state);
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
        return out.toString();
    }

    private DogModeWire() {}
}
