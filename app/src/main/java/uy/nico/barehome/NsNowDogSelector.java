package uy.nico.barehome;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Fixed NS NOW 1.1.266 UI policy. It contains no Android calls or arbitrary selectors. */
final class NsNowDogSelector {
    static final String PACKAGE = "uy.com.nsnow.atv";
    static final String CHANNEL = "CANAL 10";
    static final String LIVE_LABEL = "TV EN VIVO";
    static final String PROMPT_TITLE = "¿Seguís ahí?";
    static final String DEBUG_TITLE = "Debes desactivar el modo de depuración USB para iniciar la aplicación";
    private static final Pattern PROMPT_BODY = Pattern.compile(
            "Para no detener la reproducción, presioná cualquier botón del control \\(([1-9]|[12][0-9]|30)\\)");

    static final class Node {
        final String packageName, id, text, description;
        final boolean visible, clickable, focusable, focused;
        final int handle;
        final List<Node> children = new ArrayList<>();
        Node(String packageName, String id, String text, boolean visible, boolean clickable, int handle) {
            this(packageName, id, text, visible, clickable, handle, false, false, "");
        }
        Node(String packageName, String id, String text, boolean visible, boolean clickable, int handle,
                boolean focusable, boolean focused, String description) {
            this.packageName = packageName;
            this.id = id;
            this.text = text;
            this.visible = visible;
            this.clickable = clickable;
            this.focusable = focusable;
            this.focused = focused;
            this.description = description;
            this.handle = handle;
        }
        Node add(Node child) { children.add(child); return this; }
    }

    enum Kind { NONE, FOCUS_HEADERS, FOCUS_LIVE, CLICK_LIVE, SHOW_CONTROLS, CLICK_PICKER, CLICK_CHANNEL, VERIFIED, DISMISS_PROMPT, BLOCKED }
    static final class Decision {
        final Kind kind;
        final int handle;
        final String failure;
        Decision(Kind kind, int handle, String failure) {
            this.kind = kind; this.handle = handle; this.failure = failure;
        }
    }
    /** Coarse startup facts only. No labels, window contents, screenshots or request secrets are retained. */
    static final class Progress {
        private boolean nsNowCaptured, menuSeen, channelsSeen, targetSeen, debuggingBlock;
        private int attemptedActions, acceptedActions;
        void observe(Node root, Decision decision) {
            if (!own(root) || !root.visible) return;
            nsNowCaptured = true;
            List<Node> headers = new ArrayList<>(); collect(root, "header_label", headers);
            for (Node header : headers) if (sameName(LIVE_LABEL, header.text)) menuSeen = true;
            List<Node> rows = new ArrayList<>(); collect(root, "channelLayout", rows);
            for (Node row : rows) if (unique(row, "channelName") != null) channelsSeen = true;
            if (channelTarget(root) != null) targetSeen = true;
            if (decision != null && decision.kind == Kind.BLOCKED
                    && "nsnow_debugging_enabled".equals(decision.failure)) debuggingBlock = true;
        }
        void action(Kind kind, boolean accepted) {
            attemptedActions |= 1 << kind.ordinal();
            if (accepted) acceptedActions |= 1 << kind.ordinal();
        }
        boolean attempted(Kind kind) { return (attemptedActions & (1 << kind.ordinal())) != 0; }
        boolean accepted(Kind kind) { return (acceptedActions & (1 << kind.ordinal())) != 0; }
        String failure() {
            if (debuggingBlock) return "nsnow_debug";
            if (accepted(Kind.CLICK_CHANNEL)) return "nsnow_player";
            if (targetSeen) return "nsnow_input";
            if (channelsSeen) return "nsnow_target";
            if (accepted(Kind.CLICK_PICKER)) return "nsnow_channel";
            if (menuSeen) return "nsnow_menu";
            // A captured but unrecognized NS NOW screen is also an access/expected-controls failure.
            return "nsnow_access";
        }
        boolean captured() { return nsNowCaptured; }
    }
    private boolean clicked;
    private boolean focusedHeaders, focusedLive, clickedLive, openedPicker, showedControls;
    private int attemptedInputs;
    private boolean promptObserved;
    private int promptWindow;
    private long promptExpiry;

    Decision inspect(Node root, int window, boolean active) {
        return inspect(root, window, active, System.nanoTime() / 1000000L);
    }
    Decision inspect(Node root, int window, boolean active, long now) {
        if (!own(root)) return none();
        if (debugBlock(root)) return blocked("nsnow_debugging_enabled");
        Node prompt = unique(root, "exo_confiramtionDialog");
        boolean exactPrompt = promptMatches(root);
        if (active) {
            if (!exactPrompt) return none();
            Node body = unique(prompt, "exo_confirmationButton");
            int opening = body.text.lastIndexOf('(');
            long expiry = now + Integer.parseInt(body.text.substring(opening + 1, body.text.length() - 1)) * 1000L;
            // The verified app starts a new 30-second prompt only after its next three-hour timer.
            // Missing/partial nodes never rearm an uncertain Back; countdown updates stay in this instance.
            if (promptObserved && promptWindow == window && expiry - promptExpiry < 10740000L) return none();
            promptObserved = true;
            promptWindow = window;
            promptExpiry = expiry;
            return new Decision(Kind.DISMISS_PROMPT, prompt.handle, null);
        }
        if (exactPrompt) return none();
        List<Node> channelLabels = new ArrayList<>();
        collect(root, "ch_info_name", channelLabels);
        collect(root, "exo_info_name", channelLabels);
        String playerChannel = null;
        for (Node label : channelLabels) {
            if (!label.visible || !own(label) || label.text.isEmpty()) continue;
            if (playerChannel != null && !sameName(playerChannel, label.text)) return none();
            playerChannel = label.text;
        }
        if (sameName(CHANNEL, playerChannel) && visible(root, "video_view")) return new Decision(Kind.VERIFIED, -1, null);
        if (clicked) return none();
        List<Node> rows = new ArrayList<>();
        collect(root, "channelLayout", rows);
        Node target = null;
        for (Node row : rows) {
            if (!own(row) || !row.visible || !row.clickable || !exactName(row, "channelName", CHANNEL)) continue;
            if (target != null) return blocked("ambiguous_channel");
            target = row;
        }
        if (target != null) {
            // Reserve before calling Android. False or uncertain delivery never retries this action.
            clicked = true;
            return new Decision(Kind.CLICK_CHANNEL, target.handle, null);
        }
        if (visible(root, "video_view")) {
            Node picker = fixedTarget(root, Kind.CLICK_PICKER);
            if (picker != null && !openedPicker) {
                openedPicker = true;
                return new Decision(Kind.CLICK_PICKER, picker.handle, null);
            }
            Node controls = fixedTarget(root, Kind.SHOW_CONTROLS);
            if (controls != null && !showedControls && !openedPicker) {
                showedControls = true;
                return new Decision(Kind.SHOW_CONTROLS, controls.handle, null);
            }
            return none();
        }
        Node focus = fixedTarget(root, Kind.FOCUS_LIVE);
        Node header = fixedTarget(root, Kind.CLICK_LIVE);
        if (header != null && !clickedLive && (focus != null && hasFocus(focus) || hasFocus(header))) {
            clickedLive = true;
            return new Decision(Kind.CLICK_LIVE, header.handle, null);
        }
        if (focus != null && !focusedLive && !clickedLive) {
            focusedLive = true;
            return new Decision(Kind.FOCUS_LIVE, focus.handle, null);
        }
        Node headers = fixedTarget(root, Kind.FOCUS_HEADERS);
        if (headers != null && !focusedHeaders && !focusedLive && !clickedLive) {
            focusedHeaders = true;
            return new Decision(Kind.FOCUS_HEADERS, headers.handle, null);
        }
        return none();
    }

    /** Called at the dispatch boundary, before Android can accept, reject, or throw. */
    void actionAttempted(Kind kind) { attemptedInputs |= 1 << kind.ordinal(); }

    /** A stale preflight sent no input. Only a later fresh event may reserve that step again. */
    void releaseUnsentReservation(Kind kind) {
        if ((attemptedInputs & (1 << kind.ordinal())) != 0) return;
        switch (kind) {
            case FOCUS_HEADERS: focusedHeaders = false; break;
            case FOCUS_LIVE: focusedLive = false; break;
            case CLICK_LIVE: clickedLive = false; break;
            case SHOW_CONTROLS: showedControls = false; break;
            case CLICK_PICKER: openedPicker = false; break;
            case CLICK_CHANNEL: clicked = false; break;
            default: break;
        }
    }

    static Node fixedTarget(Node root, Kind kind) {
        if (!own(root) || debugBlock(root) || promptMatches(root)) return null;
        if (kind == Kind.CLICK_CHANNEL) return channelTarget(root);
        if (kind == Kind.FOCUS_HEADERS) return collapsedHeadersTarget(root);
        if (kind == Kind.SHOW_CONTROLS) {
            Node video = unique(root, "video_view");
            return video != null && video.clickable && "Show player controls".equals(video.description) ? video : null;
        }
        if (kind == Kind.CLICK_PICKER) {
            Node picker = unique(root, "btnSinthony");
            return visible(root, "video_view") && picker != null && picker.clickable
                    && "Señales".equals(picker.description) ? picker : null;
        }
        if (kind != Kind.FOCUS_LIVE && kind != Kind.CLICK_LIVE) return null;
        Node label;
        // Other sidebar labels normally exist too: locate the sole exact TV EN VIVO label instead.
        List<Node> labels = new ArrayList<>(); collect(root, "header_label", labels);
        label = null;
        for (Node candidate : labels) {
            if (!sameName(LIVE_LABEL, candidate.text)) continue;
            if (label != null) return null;
            label = candidate;
        }
        if (label == null) return null;
        List<Node> path = new ArrayList<>();
        if (!path(root, label, path)) return null;
        boolean services = false;
        for (Node ancestor : path) if ((PACKAGE + ":id/item_services").equals(ancestor.id)) services = true;
        if (!services) return null;
        for (int i = path.size() - 1; i >= 0 && path.size() - i <= 5; i--) {
            Node candidate = path.get(i);
            List<Node> contained = new ArrayList<>(); collect(candidate, "header_label", contained);
            if (contained.size() != 1) return null;
            if (kind == Kind.FOCUS_LIVE ? candidate.focusable : candidate.clickable) return candidate;
        }
        return null;
    }

    private static Node collapsedHeadersTarget(Node root) {
        // NS NOW's collapsed text rows are INVISIBLE. Focus only the visible grid:
        // Leanback's requestChildFocus(browse_headers_dock, ...) opens its headers.
        // A later event must expose the exact TV EN VIVO label before selecting it.
        if (!visible(root, "main_items_menu") || visible(root, "video_view")
                || visible(root, "ch_info_name") || visible(root, "exo_info_name")
                || visible(root, "exo_confiramtionDialog") || visible(root, "networkDialog")) return null;
        Node frame = unique(root, "browse_frame"), dock = unique(root, "browse_headers_dock");
        Node headersRoot = unique(root, "browse_headers_root"), grid = unique(root, "browse_headers");
        if (frame == null || dock == null || headersRoot == null || grid == null
                || !grid.focusable || grid.focused) return null;
        List<Node> path = new ArrayList<>();
        if (!path(frame, grid, path) || !path.contains(dock) || !path.contains(headersRoot)
                || path.indexOf(dock) >= path.indexOf(headersRoot)) return null;
        List<Node> labels = new ArrayList<>(); collect(root, "header_label", labels);
        return labels.isEmpty() ? grid : null;
    }

    static boolean headerHasFocus(Node root) {
        Node focus = fixedTarget(root, Kind.FOCUS_LIVE), click = fixedTarget(root, Kind.CLICK_LIVE);
        return focus != null && hasFocus(focus) || click != null && hasFocus(click);
    }
    private static boolean hasFocus(Node node) {
        if (!own(node) || !node.visible) return false;
        if (node.focused) return true;
        for (Node child : node.children) if (hasFocus(child)) return true;
        return false;
    }
    private static boolean path(Node node, Node target, List<Node> path) {
        if (!own(node) || !node.visible) return false;
        path.add(node);
        if (node == target) return true;
        for (Node child : node.children) if (path(child, target, path)) return true;
        path.remove(path.size() - 1);
        return false;
    }

    static boolean promptMatches(Node root) {
        Node prompt = unique(root, "exo_confiramtionDialog");
        return prompt != null && prompt.visible
                && exactText(prompt, "exo_confirmationText", PROMPT_TITLE) && bodyMatches(prompt);
    }

    static Node channelTarget(Node root) {
        List<Node> rows = new ArrayList<>();
        collect(root, "channelLayout", rows);
        Node target = null;
        for (Node row : rows) {
            if (!own(row) || !row.visible || !row.clickable || !exactName(row, "channelName", CHANNEL)) continue;
            if (target != null) return null;
            target = row;
        }
        return target;
    }

    private static boolean debugBlock(Node root) {
        Node dialog = unique(root, "networkDialog");
        return dialog != null && dialog.visible && exactText(dialog, "lblTitle", DEBUG_TITLE)
                && exactText(dialog, "btnAction", "PREFERENCIAS DE DISPOSITIVO")
                && exactText(dialog, "btnCancel", "CERRAR APLICACIÓN");
    }
    private static boolean bodyMatches(Node parent) {
        Node node = unique(parent, "exo_confirmationButton");
        return node != null && node.visible && own(node) && PROMPT_BODY.matcher(node.text).matches();
    }
    private static boolean exactText(Node parent, String id, String text) {
        Node node = unique(parent, id);
        return node != null && node.visible && own(node) && text.equals(node.text);
    }
    private static boolean exactName(Node parent, String id, String text) {
        Node node = unique(parent, id);
        return node != null && node.visible && own(node) && sameName(text, node.text);
    }
    private static boolean sameName(String a, String b) {
        return a != null && b != null && canonical(a).equals(canonical(b));
    }
    private static String canonical(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isWhitespace(c) && !Character.isSpaceChar(c)) out.append(c);
        }
        return out.toString().toUpperCase(Locale.ROOT);
    }
    private static boolean visible(Node parent, String id) {
        Node node = unique(parent, id);
        return node != null && node.visible && own(node);
    }
    private static Node unique(Node root, String id) {
        List<Node> found = new ArrayList<>();
        collect(root, id, found);
        return found.size() == 1 ? found.get(0) : null;
    }
    private static void collect(Node node, String id, List<Node> found) {
        if (!own(node) || !node.visible) return;
        if (node.visible && (PACKAGE + ":id/" + id).equals(node.id)) found.add(node);
        for (Node child : node.children) collect(child, id, found);
    }
    private static boolean own(Node node) { return node != null && PACKAGE.equals(node.packageName); }
    private static Decision none() { return new Decision(Kind.NONE, -1, null); }
    private static Decision blocked(String reason) { return new Decision(Kind.BLOCKED, -1, reason); }
}
