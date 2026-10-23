package uy.nico.barehome;

import android.os.SystemClock;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** A callback endpoint retained only for bounded startup; never owns a BroadcastReceiver. */
final class DogModeReply implements DogModeCoordinator.Reply {
    private static final ThreadPoolExecutor SEND = new ThreadPoolExecutor(0, 1, 100,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(4), runnable -> {
                Thread thread = new Thread(runnable, "BareHome-dog-final");
                thread.setDaemon(true);
                return thread;
            });
    private final DogModeWire.Request request;
    private final long deadline;

    DogModeReply(DogModeWire.Request request, long deadline) {
        this.request = request;
        this.deadline = deadline;
    }

    @Override public void complete(String state) {
        try { SEND.execute(() -> send(request, state, deadline)); }
        catch (java.util.concurrent.RejectedExecutionException unavailable) { /* One attempt. */ }
    }

    static boolean allowed(DogModeWire.Request request) throws java.net.SocketException {
        return localAddressFor(request.replyAddress) != null;
    }

    static void send(DogModeWire.Request request, String state, long deadline) {
        try {
            if (SystemClock.elapsedRealtime() >= deadline) return;
            InetAddress local = localAddressFor(request.replyAddress);
            if (local == null || SystemClock.elapsedRealtime() >= deadline) return;
            byte[] data = DogModeWire.reply(request, state);
            try (DatagramChannel channel = DatagramChannel.open()) {
                channel.configureBlocking(false);
                channel.bind(new InetSocketAddress(local, 0));
                if (SystemClock.elapsedRealtime() < deadline) {
                    channel.send(ByteBuffer.wrap(data), new InetSocketAddress(
                            InetAddress.getByAddress(request.replyAddress), request.port));
                }
            }
        } catch (Exception unavailable) {
            // No retries or secret logging. State can be requested explicitly after uncertainty.
        }
    }

    private static InetAddress localAddressFor(byte[] peer) throws java.net.SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces == null) return null;
        InetAddress matching = null;
        while (interfaces.hasMoreElements()) {
            NetworkInterface network = interfaces.nextElement();
            if (!network.isUp() || network.isLoopback()) continue;
            for (InterfaceAddress address : network.getInterfaceAddresses()) {
                InetAddress local = address.getAddress();
                if (!(local instanceof Inet4Address)) continue;
                if (Arrays.equals(peer, local.getAddress())) return null;
                if (matching == null && PictureStateWire.allowedPeer(
                        peer, local.getAddress(), address.getNetworkPrefixLength())) matching = local;
            }
        }
        return matching;
    }
}
