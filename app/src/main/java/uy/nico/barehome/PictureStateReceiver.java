package uy.nico.barehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.Enumeration;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** One short explicit broadcast, one outgoing authenticated datagram. Never listens. */
public final class PictureStateReceiver extends BroadcastReceiver {
    private static final long LIFETIME_MS = 3500;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    // At most one worker; its thread exits after a brief idle interval. No scheduled work.
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(0, 1, 100,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(4), runnable -> {
                Thread thread = new Thread(runnable, "BareHome-reply");
                thread.setDaemon(true);
                return thread;
            });

    @Override public void onReceive(Context context, Intent intent) {
        final PictureStateWire.Request request = intent == null ? null
                : PictureStateWire.parse(intent.getDataString());
        if (request == null) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final long deadline = SystemClock.elapsedRealtime() + LIFETIME_MS;
        final AtomicBoolean finished = new AtomicBoolean();
        // Bounds goAsync even if a queued worker or network-interface lookup stalls.
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pending.finish();
        };
        MAIN.postDelayed(finish, LIFETIME_MS);
        try {
            WORK.execute(() -> {
                try {
                    if (finished.get()) return;
                    InetAddress local = localAddressFor(request.replyAddress);
                    if (local == null || finished.get() || expired(deadline)) return;
                    final CountDownLatch ready = new CountDownLatch(1);
                    final AtomicReference<String> state = new AtomicReference<>();
                    MAIN.post(() -> {
                        try {
                            if (finished.get() || expired(deadline)) return;
                            PictureController controller = new PictureController(app);
                            if (!request.toggle) {
                                state.set(controller.snapshot());
                            } else {
                                int consumed = consume(app, request.id);
                                // Persistence must succeed before any toggle. Duplicates only read.
                                if (consumed == PictureRequestCache.FAILED || expired(deadline)) {
                                    state.set("error");
                                } else if (consumed == PictureRequestCache.DUPLICATE) {
                                    state.set(controller.snapshot());
                                } else {
                                    state.set(controller.toggleRemote() ? controller.snapshot() : "error");
                                }
                            }
                        } catch (RuntimeException failed) {
                            state.set("error");
                        } finally {
                            ready.countDown();
                        }
                    });
                    long remaining = deadline - SystemClock.elapsedRealtime();
                    if (remaining <= 0 || !ready.await(remaining, TimeUnit.MILLISECONDS)
                            || finished.get() || expired(deadline) || state.get() == null) return;
                    byte[] data = PictureStateWire.reply(request, state.get());
                    // Nonblocking UDP, one send attempt. Bind the subnet-matching TV address.
                    try (DatagramChannel channel = DatagramChannel.open()) {
                        channel.configureBlocking(false);
                        channel.bind(new InetSocketAddress(local, 0));
                        if (!finished.get() && !expired(deadline)) {
                            channel.send(ByteBuffer.wrap(data), new InetSocketAddress(
                                    InetAddress.getByAddress(request.replyAddress), request.port));
                        }
                    }
                } catch (Exception unavailable) {
                    // One-shot delivery has no retry, log of secrets, or persistent network work.
                } finally {
                    finish.run();
                    MAIN.removeCallbacks(finish);
                }
            });
        } catch (RejectedExecutionException busy) {
            finish.run();
            MAIN.removeCallbacks(finish);
        }
    }

    private static boolean expired(long deadline) {
        return SystemClock.elapsedRealtime() >= deadline;
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

    private static int consume(Context context, String id) {
        final SharedPreferences preferences = context.getSharedPreferences("remote_requests", Context.MODE_PRIVATE);
        return PictureRequestCache.consume(new PictureRequestCache.Store() {
            @Override public String read() { return preferences.getString("recent_ids", ""); }
            @Override public boolean save(String ids) {
                return preferences.edit().putString("recent_ids", ids).commit();
            }
        }, id);
    }
}
