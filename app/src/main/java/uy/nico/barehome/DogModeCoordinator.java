package uy.nico.barehome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Serial durable operations, with one bounded asynchronous NS NOW startup. */
final class DogModeCoordinator {
    interface Reply { void complete(String state); }
    interface Completion { void complete(boolean restored); }
    private static final long STARTUP_MS = 30_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ThreadPoolExecutor SERIAL = new ThreadPoolExecutor(0, 1, 100,
            TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(8), runnable -> {
                Thread thread = new Thread(runnable, "BareHome-dog-state");
                thread.setDaemon(true);
                return thread;
            });
    private static Pending starting;

    private static final class Pending {
        final Context context;
        final String generation;
        final List<Reply> replies = new ArrayList<>();
        final Runnable timeout;
        Pending(Context context, String generation) {
            this.context = context;
            this.generation = generation;
            timeout = () -> enqueue(() -> finishStart(context, generation, false));
        }
        void add(Reply reply) { if (reply != null && replies.size() < 4) replies.add(reply); }
    }

    /** Called on the receiver worker. It never waits for channel selection. */
    static String request(Context context, DogModeWire.Request request, long deadline, Reply finalReply)
            throws Exception {
        final Context app = context.getApplicationContext();
        Future<String> work = SERIAL.submit(() -> {
            if (SystemClock.elapsedRealtime() >= deadline) return DogModeTransaction.ERROR;
            DogModeController controller = new DogModeController(app);
            if (!request.mutates()) return snapshot(app, controller);
            int consumed = consume(app, request.id);
            if (consumed == PictureRequestCache.FAILED || SystemClock.elapsedRealtime() >= deadline) {
                return DogModeTransaction.ERROR;
            }
            if (consumed == PictureRequestCache.DUPLICATE) return snapshot(app, controller);
            if ("stop".equals(request.action)) return stop(app, controller);
            DogModeTransaction.Start prepared = controller.prepareStart();
            if (!prepared.begins) {
                if (DogModeTransaction.STARTING.equals(prepared.state) && samePending(prepared.generation)) {
                    starting.add(finalReply);
                }
                return (DogModeTransaction.STARTING.equals(prepared.state)
                        || DogModeTransaction.ACTIVE.equals(prepared.state))
                        ? snapshot(app, controller) : prepared.state;
            }
            if (SystemClock.elapsedRealtime() >= deadline) {
                String recovery = stop(app, controller);
                return DogModeTransaction.OFF.equals(recovery) ? DogModeTransaction.ERROR : recovery;
            }
            Pending pending = new Pending(app, prepared.generation);
            pending.add(finalReply);
            starting = pending;
            long startupRemaining = deadline + STARTUP_MS - 7000 - SystemClock.elapsedRealtime();
            MAIN.postDelayed(pending.timeout, Math.max(1, startupRemaining));
            boolean scheduled;
            try {
                scheduled = NsNowDogService.begin(app, prepared.generation,
                        verified -> enqueue(() -> finishStart(app, prepared.generation, verified)));
            } catch (RuntimeException failed) {
                scheduled = false;
            }
            if (!scheduled) {
                // Initial response can report the failure immediately; no delayed second reply.
                pending.replies.clear();
                return finishStart(app, prepared.generation, false);
            }
            return DogModeTransaction.STARTING;
        });
        long remaining = deadline - SystemClock.elapsedRealtime();
        if (remaining <= 0) { work.cancel(false); return DogModeTransaction.ERROR; }
        try { return work.get(remaining, TimeUnit.MILLISECONDS); }
        catch (Exception failed) { work.cancel(false); throw failed; }
    }

    private static String snapshot(Context app, DogModeController controller) {
        String state = controller.snapshot();
        if ((DogModeTransaction.OFF.equals(state) || DogModeTransaction.UNAVAILABLE.equals(state))
                && NsNowDogService.isEnabled(app)) return DogModeTransaction.RESTORE;
        if (DogModeTransaction.ACTIVE.equals(state)
                && (!NsNowDogService.isEnabled(app) || !NsNowDogService.isMonitoring(controller.generation()))) {
            return DogModeTransaction.RESTORE;
        }
        // A process restart does not replay an uncertain selection. Its journal is recoverable.
        return DogModeTransaction.STARTING.equals(state) && !samePending(controller.generation())
                ? DogModeTransaction.RESTORE : state;
    }

    private static boolean samePending(String generation) {
        return starting != null && starting.generation.equals(generation);
    }

    private static String finishStart(Context app, String generation, boolean verified) {
        if (!samePending(generation)) return new DogModeController(app).snapshot();
        Pending pending = starting;
        starting = null;
        MAIN.removeCallbacks(pending.timeout);
        DogModeController controller = new DogModeController(app);
        String failure = null;
        if (!verified) {
            try {
                String reason = NsNowDogService.startupFailure(app, generation);
                if (DogModeWire.startupFailure(reason)) failure = reason;
            } catch (RuntimeException unavailable) { /* Generic error is sufficient. */ }
        }
        String state = controller.completeStart(generation, verified);
        if (DogModeTransaction.ACTIVE.equals(state)) {
            boolean activated;
            try { activated = NsNowDogService.activate(app, generation); }
            catch (RuntimeException failed) { activated = false; }
            if (!activated) {
                String recovery = stop(app, controller);
                state = DogModeTransaction.OFF.equals(recovery) ? DogModeTransaction.ERROR : recovery;
            }
        } else {
            String recovery = stop(app, controller);
            state = DogModeTransaction.OFF.equals(recovery)
                    ? failure == null ? DogModeTransaction.ERROR : failure : recovery;
        }
        for (Reply reply : pending.replies) {
            try { reply.complete(state); } catch (RuntimeException unavailable) { /* One attempt. */ }
        }
        pending.replies.clear();
        return state;
    }

    private static String stop(Context app, DogModeController controller) {
        String generation = controller.generation();
        // Durable phase invalidation precedes cancellation and every restore/navigation action.
        boolean invalidated = controller.prepareStop();
        Pending pending = starting;
        starting = null;
        if (pending != null) MAIN.removeCallbacks(pending.timeout);
        NsNowDogService.cancel(app, generation);
        boolean removed;
        try { removed = NsNowDogService.disable(app) && !NsNowDogService.isEnabled(app); }
        catch (RuntimeException failed) { removed = false; }
        String state = invalidated ? controller.stop(removed) : DogModeTransaction.RESTORE;
        if (pending != null) {
            for (Reply reply : pending.replies) {
                try { reply.complete(state); } catch (RuntimeException unavailable) { /* One attempt. */ }
            }
            pending.replies.clear();
        }
        return state;
    }

    /** Used on service rebind only. Never resumes or launches an interrupted selection. */
    static String activeHelperGeneration(Context context) {
        try {
            if (!DogModeAccessibility.hasConsent(context)) return null;
            if (!NsNowDogService.isEnabled(context)) return null;
            return new DogModeController(context).activeGeneration();
        } catch (RuntimeException unavailable) {
            return null;
        }
    }

    /** Fresh durable phase check immediately before each service input; no coordinator lock. */
    static boolean permitsHelper(Context context, String generation, boolean active) {
        try {
            return DogModeAccessibility.hasConsent(context)
                    && NsNowDogService.isEnabled(context)
                    && new DogModeController(context).permitsHelper(generation, active);
        } catch (RuntimeException unavailable) { return false; }
    }

    static void revokeConsent(Context context, Completion completion) {
        final Context app = context.getApplicationContext();
        // Revocation takes effect immediately even if energy restoration later needs recovery.
        final boolean revoked = DogModeAccessibility.setConsent(app, false);
        NsNowDogService.disable(app);
        enqueue(() -> {
            boolean restored;
            try { restored = DogModeTransaction.OFF.equals(stop(app, new DogModeController(app))) && revoked; }
            catch (RuntimeException failed) { restored = false; }
            final boolean result = restored;
            MAIN.post(() -> completion.complete(result));
        });
    }

    private static void enqueue(Runnable work) {
        try { SERIAL.execute(work); }
        catch (java.util.concurrent.RejectedExecutionException full) {
            // The bounded startup watchdog or an explicit Stop retains the durable recovery path.
        }
    }

    private static int consume(Context context, String id) {
        final SharedPreferences preferences = context.getSharedPreferences("dog_requests", Context.MODE_PRIVATE);
        return PictureRequestCache.consume(new PictureRequestCache.Store() {
            @Override public String read() { return preferences.getString("recent_ids", ""); }
            @Override public boolean save(String ids) {
                return preferences.edit().putString("recent_ids", ids).commit();
            }
        }, id);
    }

    private DogModeCoordinator() {}
}
