package uy.nico.barehome;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/** System-bound only during a durable Dog Mode session. No polling or scheduled keepalive. */
public final class NsNowDogService extends AccessibilityService {
    interface Completion { void complete(boolean verified); }
    private static final long START_TIMEOUT_MS = 30000;
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Session current;
    private static NsNowDogService instance;
    private static long ownershipEpoch;
    private static String failedGeneration = "", failure = "";

    private static final class Session {
        final String generation;
        final long deadline;
        final Completion completion;
        final NsNowDogSelector selector = new NsNowDogSelector();
        final NsNowDogSelector.Progress progress = new NsNowDogSelector.Progress();
        boolean cancelled, launched, verified, active, delivered;
        Runnable timeout;
        Session(String generation, Completion completion) {
            this.generation = generation;
            this.completion = completion;
            deadline = SystemClock.elapsedRealtime() + START_TIMEOUT_MS;
        }
    }

    static boolean available(Context context) { return DogModeAccessibility.available(context); }
    static boolean isEnabled(Context context) {
        try {
            return DogModeAccessibility.contains(android.provider.Settings.Secure.getString(
                    context.getContentResolver(), android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
                    DogModeAccessibility.component(context));
        } catch (RuntimeException unavailable) { return false; }
    }
    static boolean isMonitoring(String generation) {
        synchronized (LOCK) { return instance != null && current != null && !current.cancelled && current.active && current.generation.equals(generation); }
    }
    static String failureReason(String generation) {
        synchronized (LOCK) { return failedGeneration.equals(generation) ? failure : ""; }
    }
    static String startupFailure(Context context, String generation) {
        synchronized (LOCK) {
            if (current != null && current.generation.equals(generation)) return current.progress.failure();
            if (failedGeneration.equals(generation) && knownFailure(failure)) return failure;
            return "nsnow_access";
        }
    }
    private static boolean knownFailure(String value) {
        return DogModeWire.startupFailure(value);
    }

    static boolean begin(Context context, String generation, Completion completion) {
        if (generation == null || !generation.matches("[A-Za-z0-9_-]{1,80}") || completion == null || !available(context)) return false;
        final Session session = new Session(generation, completion);
        synchronized (LOCK) {
            if (current != null && !current.cancelled) return false;
            current = session;
            ownershipEpoch++;
            failedGeneration = ""; failure = "";
        }
        session.timeout = () -> {
            NsNowDogService service;
            synchronized (LOCK) { service = instance; }
            if (service != null) service.finishSelection(session, false, "channel_timeout");
            else failWithoutService(context.getApplicationContext(), session, "service_timeout");
        };
        MAIN.postDelayed(session.timeout, START_TIMEOUT_MS);
        try {
            if (!DogModeAccessibility.enable(context)) {
                cancel(context, generation); disable(context); return false;
            }
        } catch (RuntimeException blocked) {
            cancel(context, generation); disable(context); return false;
        }
        MAIN.post(() -> {
            NsNowDogService service;
            synchronized (LOCK) { service = instance; }
            if (service != null) service.launch(session);
        });
        return true;
    }

    static boolean activate(Context context, String generation) {
        synchronized (LOCK) {
            if (instance == null || current == null || current.cancelled || !current.generation.equals(generation) || !current.verified
                    || !available(context) || !DogModeCoordinator.permitsHelper(context, generation, true)) return false;
            current.active = true;
            MAIN.removeCallbacks(current.timeout);
            return true;
        }
    }

    /** Invalidating the token is synchronous; queued MAIN actions cannot survive Stop. */
    static void cancel(Context context, String generation) {
        synchronized (LOCK) {
            if (current != null && (generation == null || current.generation.equals(generation))) {
                current.cancelled = true;
                if (current.timeout != null) MAIN.removeCallbacks(current.timeout);
                current = null;
                ownershipEpoch++;
            }
        }
    }
    static boolean disable(Context context) {
        cancel(context, null);
        final long epoch;
        synchronized (LOCK) { epoch = ownershipEpoch; }
        MAIN.post(() -> {
            synchronized (LOCK) {
                if (current == null && ownershipEpoch == epoch && instance != null) instance.disableSelf();
            }
        });
        try { return DogModeAccessibility.disable(context); }
        catch (RuntimeException denied) { return false; }
    }

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        AccessibilityServiceInfo info = getServiceInfo();
        info.packageNames = new String[] {NsNowDogSelector.PACKAGE};
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        info.notificationTimeout = 100;
        setServiceInfo(info);
        Session session;
        final long epoch;
        synchronized (LOCK) { instance = this; session = current; epoch = ownershipEpoch; }
        if (session != null) { launch(session); return; }
        String active = DogModeCoordinator.activeHelperGeneration(this);
        if (active == null || !available(this)) {
            synchronized (LOCK) {
                if (current == null && ownershipEpoch == epoch) disableSelf();
            }
            return;
        }
        synchronized (LOCK) {
            if (current != null || ownershipEpoch != epoch || !available(this)
                    || !DogModeCoordinator.permitsHelper(this, active, true)) return;
            Session restored = new Session(active, null);
            restored.active = true; restored.verified = true; restored.delivered = true;
            current = restored;
            ownershipEpoch++;
        }
        // An active lease may answer future exact prompts. It never retunes or replays a click.
    }

    private void launch(Session session) {
        synchronized (LOCK) {
            if (!live(session) || session.launched || session.active || session.verified) return;
            if (!available(this) || !DogModeCoordinator.permitsHelper(this, session.generation, false)) {
                finishSelection(session, false, "helper_unavailable"); return;
            }
            session.launched = true;
            try {
                startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                        .setComponent(new ComponentName(NsNowDogSelector.PACKAGE,
                                "uy.com.nsnow.atv.ui.activities.InitialVideo"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
                // One immediate snapshot also handles an already-visible NS NOW modal/player.
                // Later work runs only in response to NS NOW accessibility events.
                MAIN.post(() -> inspectWindow(session, -1));
            } catch (RuntimeException unavailable) { finishSelection(session, false, "launch_failed"); }
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || !NsNowDogSelector.PACKAGE.contentEquals(event.getPackageName() == null ? "" : event.getPackageName())) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && event.getEventType() != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) return;
        final Session session;
        synchronized (LOCK) { session = current; if (!live(session)) return; }
        if (!available(this)) { abort(session, "helper_unavailable"); return; }
        inspectWindow(session, event.getWindowId());
    }

    private void inspectWindow(Session session, int eventWindow) {
        synchronized (LOCK) { if (!live(session)) return; }
        try (Snapshot snapshot = capture()) {
            if (snapshot == null || eventWindow >= 0 && eventWindow != snapshot.window) return;
            NsNowDogSelector.Decision decision;
            synchronized (LOCK) {
                if (!live(session)) return;
                decision = session.selector.inspect(snapshot.root, snapshot.window, session.active, SystemClock.elapsedRealtime());
                session.progress.observe(snapshot.root, decision);
            }
            switch (decision.kind) {
                case VERIFIED: finishSelection(session, true, ""); break;
                case BLOCKED: abort(session, decision.failure); break;
                case FOCUS_HEADERS:
                case FOCUS_LIVE:
                case CLICK_LIVE:
                case SHOW_CONTROLS:
                case CLICK_PICKER:
                case CLICK_CHANNEL: performFixedAction(session, decision.kind, snapshot.window); break;
                case DISMISS_PROMPT: dismissPrompt(session); break;
                default: break;
            }
        } catch (RuntimeException stale) {
            // Stale/incomplete window trees do not justify an input retry or a broader selector.
        }
    }

    private void performFixedAction(Session session, NsNowDogSelector.Kind kind, int observedWindow) {
        boolean attempted = false;
        try (Snapshot fresh = capture()) {
            if (fresh == null || fresh.window != observedWindow) return;
            NsNowDogSelector.Node target = NsNowDogSelector.fixedTarget(fresh.root, kind);
            if (target == null) return;
            AccessibilityNodeInfo node = fresh.handles.get(target.handle);
            boolean focusAction = kind == NsNowDogSelector.Kind.FOCUS_HEADERS || kind == NsNowDogSelector.Kind.FOCUS_LIVE;
            synchronized (LOCK) {
                if (!live(session) || session.active || !available(this)
                        || !DogModeCoordinator.permitsHelper(this, session.generation, false)
                        || node.getWindowId() != fresh.window || !node.refresh() || !sameWindow(fresh.window)
                        || !NsNowDogSelector.PACKAGE.contentEquals(node.getPackageName() == null ? "" : node.getPackageName())
                        || !node.isVisibleToUser() || !node.isEnabled()
                        || (focusAction ? !node.isFocusable() : !node.isClickable())
                        || kind == NsNowDogSelector.Kind.CLICK_LIVE && !NsNowDogSelector.headerHasFocus(fresh.root)) return;
                // One attempt per exact source-backed step. Each later step needs fresh UI proof.
                attempted = true;
                session.selector.actionAttempted(kind);
                session.progress.action(kind, false);
                boolean accepted = node.performAction(focusAction
                        ? AccessibilityNodeInfo.ACTION_FOCUS : AccessibilityNodeInfo.ACTION_CLICK);
                session.progress.action(kind, accepted);
                if (accepted) MAIN.post(() -> inspectWindow(session, fresh.window));
            }
        } finally {
            if (!attempted) {
                synchronized (LOCK) {
                    if (live(session) && !session.active) session.selector.releaseUnsentReservation(kind);
                }
            }
        }
    }

    private void dismissPrompt(Session session) {
        try (Snapshot fresh = capture()) {
            if (fresh == null || !NsNowDogSelector.promptMatches(fresh.root)) return;
            synchronized (LOCK) {
                if (!live(session) || !session.active || !available(this)
                        || !DogModeCoordinator.permitsHelper(this, session.generation, true)) return;
                // A key/settings event can hide this prompt without changing the player window.
                // Recheck its exact current tree after the consent and durable-generation reads.
                try (Snapshot boundary = capture()) {
                    if (boundary == null || boundary.window != fresh.window
                            || !NsNowDogSelector.promptMatches(boundary.root)) return;
                    // NS NOW handles any real key while this exact prompt is visible; its text has no click handler.
                    // One Back attempt per observed prompt instance. Countdown changes never trigger another.
                    performGlobalAction(GLOBAL_ACTION_BACK);
                }
            }
        }
    }

    private boolean sameWindow(int window) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            return root.getWindowId() == window && root.isVisibleToUser()
                    && NsNowDogSelector.PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName());
        } finally { root.recycle(); }
    }

    private static boolean live(Session session) {
        return session != null && current == session && !session.cancelled
                && (session.active || SystemClock.elapsedRealtime() < session.deadline);
    }
    private void abort(Session session, String reason) {
        synchronized (LOCK) {
            if (session.active) {
                failedGeneration = session.generation; failure = session.progress.failure();
                session.cancelled = true; current = null;
                disableSelf();
                return;
            }
        }
        finishSelection(session, false, reason);
    }
    private void finishSelection(Session session, boolean success, String reason) {
        Completion callback;
        synchronized (LOCK) {
            if (current != session || session.cancelled || session.delivered || session.active) return;
            session.delivered = true;
            session.verified = success;
            if (session.timeout != null) MAIN.removeCallbacks(session.timeout);
            if (!success) {
                failedGeneration = session.generation; failure = session.progress.failure();
                session.cancelled = true; current = null;
                disableSelf();
            }
            callback = session.completion;
        }
        if (callback != null) callback.complete(success);
    }
    private static void failWithoutService(Context context, Session session, String reason) {
        Completion callback;
        synchronized (LOCK) {
            if (current != session || session.cancelled || session.delivered) return;
            session.cancelled = true; session.delivered = true; current = null;
            failedGeneration = session.generation; failure = session.progress.failure();
            callback = session.completion;
        }
        try { DogModeAccessibility.disable(context); } catch (RuntimeException ignored) {}
        if (callback != null) callback.complete(false);
    }
    @Override public void onInterrupt() {
        Session session;
        synchronized (LOCK) { session = current; }
        if (session != null) abort(session, "service_interrupted");
    }
    @Override public boolean onUnbind(Intent intent) {
        Session session;
        synchronized (LOCK) { if (instance == this) instance = null; session = current; }
        if (session != null && !session.active) finishSelection(session, false, "service_unbound");
        return super.onUnbind(intent);
    }

    private Snapshot capture() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;
        if (!NsNowDogSelector.PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName())) {
            root.recycle(); return null;
        }
        Snapshot snapshot = new Snapshot(root.getWindowId());
        try { snapshot.root = snapshot.copy(root, 0); return snapshot; }
        catch (RuntimeException invalid) { snapshot.close(); return null; }
    }
    private static final class Snapshot implements AutoCloseable {
        final int window;
        final List<AccessibilityNodeInfo> handles = new ArrayList<>();
        NsNowDogSelector.Node root;
        Snapshot(int window) { this.window = window; }
        NsNowDogSelector.Node copy(AccessibilityNodeInfo node, int depth) {
            if (depth > 48 || handles.size() >= 2048) { node.recycle(); throw new IllegalStateException("Window too large"); }
            int handle = handles.size(); handles.add(node);
            String pkg = string(node.getPackageName(), 80), id = node.getViewIdResourceName();
            // Retain only exact allowlisted label values, never program/account names or other screen text.
            String text = relevant(id) ? string(node.getText(), 180) : "";
            String description = id != null && (id.equals(NsNowDogSelector.PACKAGE + ":id/btnSinthony")
                    || id.equals(NsNowDogSelector.PACKAGE + ":id/video_view"))
                    ? string(node.getContentDescription(), 80) : "";
            NsNowDogSelector.Node result = new NsNowDogSelector.Node(pkg, id == null ? "" : id,
                    text, node.isVisibleToUser(), node.isClickable(), handle, node.isFocusable(), node.isFocused(), description);
            if (!NsNowDogSelector.PACKAGE.equals(pkg) || !node.isVisibleToUser()) return result;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null) result.add(copy(child, depth + 1));
            }
            return result;
        }
        private static boolean relevant(String id) {
            if (id == null || !id.startsWith(NsNowDogSelector.PACKAGE + ":id/")) return false;
            String name = id.substring((NsNowDogSelector.PACKAGE + ":id/").length());
            return name.equals("header_label") || name.equals("channelName") || name.equals("ch_info_name") || name.equals("exo_info_name")
                    || name.equals("exo_confirmationText") || name.equals("exo_confirmationButton")
                    || name.equals("lblTitle") || name.equals("btnAction") || name.equals("btnCancel");
        }
        private static String string(CharSequence value, int bound) {
            return value != null && value.length() <= bound ? value.toString() : "";
        }
        @Override public void close() { for (AccessibilityNodeInfo node : handles) node.recycle(); handles.clear(); root = null; }
    }
}
