package uy.nico.barehome;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Tiny strict wire format. No DNS, Android classes, mutable TV values, or arbitrary commands. */
final class PictureStateWire {
    static final int MAX_REQUEST_BYTES = 192;
    static final int MAX_REPLY_BYTES = 160;
    private static final Pattern REQUEST = Pattern.compile(
            "barehome://picture/(state|toggle)\\?reply=([0-9.]{7,15})&port=([1-9][0-9]{3,4})&token=([0-9a-f]{64})");

    static final class Request {
        final boolean toggle;
        final byte[] replyAddress;
        final int port;
        final byte[] token;
        final String id;

        private Request(boolean toggle, byte[] address, int port, byte[] token)
                throws GeneralSecurityException {
            this.toggle = toggle;
            this.replyAddress = address;
            this.port = port;
            this.token = token;
            this.id = hex(MessageDigest.getInstance("SHA-256").digest(token));
        }
    }

    static Request parse(String uri) {
        if (uri == null || uri.length() > MAX_REQUEST_BYTES) return null;
        Matcher match = REQUEST.matcher(uri);
        if (!match.matches()) return null;
        byte[] address = ipv4(match.group(2));
        int port = Integer.parseInt(match.group(3));
        if (address == null || !privateAddress(address) || port < 1024 || port > 65535) return null;
        byte[] token = new byte[32];
        for (int i = 0; i < token.length; i++) {
            token[i] = (byte) Integer.parseInt(match.group(4).substring(i * 2, i * 2 + 2), 16);
        }
        try { return new Request("toggle".equals(match.group(1)), address, port, token); }
        catch (GeneralSecurityException unavailable) { return null; }
    }

    static byte[] ipv4(String value) {
        if (value == null) return null;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return null;
        byte[] result = new byte[4];
        for (int i = 0; i < 4; i++) {
            if (parts[i].isEmpty() || parts[i].length() > 3
                    || (parts[i].length() > 1 && parts[i].charAt(0) == '0')) return null;
            int number = 0;
            for (int j = 0; j < parts[i].length(); j++) {
                char digit = parts[i].charAt(j);
                if (digit < '0' || digit > '9') return null;
                number = number * 10 + digit - '0';
            }
            if (number > 255) return null;
            result[i] = (byte) number;
        }
        return result;
    }

    static boolean privateAddress(byte[] address) {
        if (address == null || address.length != 4) return false;
        int a = address[0] & 255, b = address[1] & 255;
        return a == 10 || (a == 172 && b >= 16 && b <= 31) || (a == 192 && b == 168);
    }

    static boolean allowedPeer(byte[] peer, byte[] local, int prefix) {
        if (!privateAddress(peer) || !privateAddress(local) || prefix < 1 || prefix > 30) return false;
        int target = addressNumber(peer), own = addressNumber(local);
        int mask = -1 << (32 - prefix);
        int network = own & mask;
        return target != own && (target & mask) == network
                && target != network && target != (network | ~mask);
    }

    static byte[] reply(Request request, String state) throws GeneralSecurityException {
        if (!validState(state)) throw new IllegalArgumentException("Unknown picture state");
        String prefix = "BH1|" + request.id + "|" + state;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(request.token, "HmacSHA256"));
        byte[] packet = (prefix + "|" + hex(mac.doFinal(prefix.getBytes(StandardCharsets.US_ASCII))))
                .getBytes(StandardCharsets.US_ASCII);
        if (packet.length > MAX_REPLY_BYTES) throw new IllegalArgumentException("Reply too long");
        return packet;
    }

    private static boolean validState(String state) {
        return "day".equals(state) || "night".equals(state) || "custom".equals(state)
                || "unavailable".equals(state) || "error".equals(state);
    }

    private static int addressNumber(byte[] address) {
        return (address[0] & 255) << 24 | (address[1] & 255) << 16
                | (address[2] & 255) << 8 | address[3] & 255;
    }

    private static String hex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        final char[] digits = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            out[i * 2] = digits[(bytes[i] & 255) >>> 4];
            out[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(out);
    }

    private PictureStateWire() {}
}
