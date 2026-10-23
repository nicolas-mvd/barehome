package uy.nico.barehome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** One explicit request, one short outbound callback. No listener or persistent work. */
public final class DogModeReceiver extends BroadcastReceiver {
    private static final long LIFETIME_MS = 7000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor WORK = new ThreadPoolExecutor(0, 1, 100,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(4), runnable -> {
                Thread thread = new Thread(runnable, "BareHome-dog-reply");
                thread.setDaemon(true);
                return thread;
            });

    @Override public void onReceive(Context context, Intent intent) {
        final DogModeWire.Request request = intent == null ? null
                : DogModeWire.parse(intent.getDataString());
        if (request == null) return;
        final PendingResult pending = goAsync();
        final Context app = context.getApplicationContext();
        final long deadline = SystemClock.elapsedRealtime() + LIFETIME_MS;
        final AtomicBoolean finished = new AtomicBoolean();
        final Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) pending.finish();
        };
        MAIN.postDelayed(finish, LIFETIME_MS);
        try {
            WORK.execute(() -> {
                try {
                    if (finished.get()) return;
                    if (!DogModeReply.allowed(request) || finished.get() || expired(deadline)) return;
                    // The initial reply is short. A bounded startup callback retains no receiver,
                    // socket, or goAsync result while NS NOW opens and CANAL 10 is verified.
                    String state = DogModeCoordinator.request(app, request, deadline,
                            new DogModeReply(request, deadline + 25_000));
                    if (finished.get() || expired(deadline)) return;
                    DogModeReply.send(request, state, deadline);
                } catch (Exception unavailable) {
                    // No automatic mutation retry, secret logging, or retained network work.
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

}
