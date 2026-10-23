package uy.nico.barehome;

public final class NsNowDogSelectorTest {
    private static int assertions;
    private static final String PKG = NsNowDogSelector.PACKAGE;
    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
    private static NsNowDogSelector.Node node(String id, String text, boolean clickable, int handle) {
        return new NsNowDogSelector.Node(PKG, PKG + ":id/" + id, text, true, clickable, handle);
    }
    private static NsNowDogSelector.Node root() { return node("root", "", false, 0); }
    private static NsNowDogSelector.Node row(String name, int handle) {
        return node("channelLayout", "", true, handle).add(node("channelName", name, false, handle + 1));
    }
    private static NsNowDogSelector.Node player(String name) {
        return root().add(node("video_view", "", false, 1)).add(node("ch_info_name", name, false, 2));
    }
    private static NsNowDogSelector.Node header(String label, int handle, boolean focused) {
        return new NsNowDogSelector.Node(PKG, "", "", true, true, handle, true, focused, "")
                .add(node("item_services", "", false, handle + 1)
                    .add(node("inner", "", false, handle + 2).add(node("header_label", label, false, handle + 3))));
    }
    private static NsNowDogSelector.Node video(String description, boolean clickable) {
        return new NsNowDogSelector.Node(PKG, PKG + ":id/video_view", "", true, clickable, 1, false, false, description);
    }
    private static NsNowDogSelector.Node picker(String description) {
        return new NsNowDogSelector.Node(PKG, PKG + ":id/btnSinthony", "", true, true, 4, true, false, description);
    }
    private static NsNowDogSelector.Node collapsedMain() {
        NsNowDogSelector.Node grid = new NsNowDogSelector.Node(PKG, PKG + ":id/browse_headers", "", true, false, 14,
                true, false, "");
        // The actual Leanback wrappers become INVISIBLE while the grid remains visible.
        grid.add(new NsNowDogSelector.Node(PKG, "", "", false, false, 15)
                .add(header("TV EN VIVO", 20, false)));
        return root().add(node("main_items_menu", "", false, 10))
                .add(node("browse_frame", "", false, 11)
                    .add(node("browse_headers_dock", "", false, 12)
                        .add(node("browse_headers_root", "", false, 13).add(grid))));
    }
    private static NsNowDogSelector.Node prompt(String title, String body) {
        return player("CANAL 10").add(node("exo_confiramtionDialog", "", false, 4)
                .add(node("exo_confirmationText", title, false, 5))
                .add(node("exo_confirmationButton", body, false, 6)));
    }
    private static String body(int seconds) {
        return "Para no detener la reproducción, presioná cualquier botón del control (" + seconds + ")";
    }
    private static void reservationPolicy(NsNowDogSelector.Kind kind, NsNowDogSelector.Node scene) {
        NsNowDogSelector selector = new NsNowDogSelector();
        check(selector.inspect(scene, 17, false).kind == kind, "Expected step was not reserved: " + kind);
        selector.releaseUnsentReservation(kind);
        check(selector.inspect(scene, 17, false).kind == kind, "Known-unsent preflight could not reconsider: " + kind);
        // Android returning false or throwing is still an attempted, uncertain dispatch.
        selector.actionAttempted(kind);
        selector.releaseUnsentReservation(kind);
        check(selector.inspect(scene, 17, false).kind == NsNowDogSelector.Kind.NONE,
                "Attempted action could be replayed after a false/uncertain dispatch: " + kind);
    }
    public static void main(String[] arguments) {
        NsNowDogSelector selector = new NsNowDogSelector();
        NsNowDogSelector.Node list = root().add(row("CANAL 4", 1)).add(row("CANAL 10", 3));
        NsNowDogSelector.Decision first = selector.inspect(list, 5, false);
        check(first.kind == NsNowDogSelector.Kind.CLICK_CHANNEL && first.handle == 3, "Exact unique channel not selected");
        check(selector.inspect(list, 5, false).kind == NsNowDogSelector.Kind.NONE, "Channel click retried");
        check(selector.inspect(player("CANAL 4"), 6, false).kind == NsNowDogSelector.Kind.NONE, "Different player channel accepted");
        check(selector.inspect(player("CANAL 10"), 6, false).kind == NsNowDogSelector.Kind.VERIFIED, "Separate player confirmation not recognized");
        check(new NsNowDogSelector().inspect(root().add(row("CANAL 10", 1)).add(row("CANAL 10", 3)), 5, false).kind
                == NsNowDogSelector.Kind.BLOCKED, "Ambiguous channel clicked");
        for (String other : new String[] {"CANAL 100", "10", "CANAL 10 HD", "CANAL 10 PLUS"}) {
            check(new NsNowDogSelector().inspect(root().add(row(other, 1)), 5, false).kind == NsNowDogSelector.Kind.NONE,
                    "Unverified channel alias accepted: " + other);
        }
        for (String name : new String[] {"Canal 10", "CANAL10", " CANAL 10 ", "canal\u00a010", "CANAL\t10"}) {
            check(new NsNowDogSelector().inspect(root().add(row(name, 1)), 5, false).kind == NsNowDogSelector.Kind.CLICK_CHANNEL,
                    "Canonical full channel name rejected");
            NsNowDogSelector.Node freshTarget = NsNowDogSelector.fixedTarget(root().add(row(name, 1)), NsNowDogSelector.Kind.CLICK_CHANNEL);
            check(freshTarget != null && freshTarget.handle == 1,
                    "Fresh action-boundary target rejected canonical full channel name");
        }
        check(new NsNowDogSelector().inspect(root().add(row("CANAL 10", 1)).add(row("Canal10", 3)), 5, false).kind
                == NsNowDogSelector.Kind.BLOCKED, "Normalized duplicate channel not rejected");
        check(NsNowDogSelector.fixedTarget(root().add(row("CANAL 10", 1)).add(row("Canal10", 3)),
                NsNowDogSelector.Kind.CLICK_CHANNEL) == null, "Fresh action boundary accepted normalized duplicate channel");
        check(new NsNowDogSelector().inspect(player(" canal10 "), 6, false).kind == NsNowDogSelector.Kind.VERIFIED,
                "Canonical player channel not recognized");
        NsNowDogSelector.Node foreign = new NsNowDogSelector.Node("other.app", PKG + ":id/root", "", true, false, 0)
                .add(row("CANAL 10", 1));
        check(new NsNowDogSelector().inspect(foreign, 5, false).kind == NsNowDogSelector.Kind.NONE, "Foreign root caused action");
        NsNowDogSelector.Node foreignRow = new NsNowDogSelector.Node("other.app", PKG + ":id/channelLayout", "", true, true, 1)
                .add(node("channelName", "CANAL 10", false, 2));
        check(new NsNowDogSelector().inspect(root().add(foreignRow), 5, false).kind == NsNowDogSelector.Kind.NONE, "Foreign row caused action");
        NsNowDogSelector.Node hidden = new NsNowDogSelector.Node(PKG, PKG + ":id/channelLayout", "", false, true, 1)
                .add(node("channelName", "CANAL 10", false, 2));
        check(new NsNowDogSelector().inspect(root().add(hidden), 5, false).kind == NsNowDogSelector.Kind.NONE, "Hidden row selected");
        check(new NsNowDogSelector().inspect(root().add(node("ch_info_name", "CANAL 10", false, 1)), 6, false).kind
                == NsNowDogSelector.Kind.NONE, "Channel label without player accepted");
        check(new NsNowDogSelector().inspect(player("CANAL 10").add(node("exo_info_name", "CANAL 4", false, 3)), 6, false).kind
                == NsNowDogSelector.Kind.NONE, "Contradictory player labels accepted");

        selector = new NsNowDogSelector();
        NsNowDogSelector.Node exact = prompt("¿Seguís ahí?", body(29));
        check(selector.inspect(exact, 9, false).kind == NsNowDogSelector.Kind.NONE, "Prompt dismissed before active mode");
        check(selector.inspect(exact, 9, true).kind == NsNowDogSelector.Kind.DISMISS_PROMPT, "Exact active prompt not handled");
        for (int seconds = 28; seconds > 0; seconds--) {
            check(selector.inspect(prompt("¿Seguís ahí?", body(seconds)), 9, true).kind == NsNowDogSelector.Kind.NONE,
                    "Countdown caused repeated Back");
        }
        selector.inspect(root(), 9, true);
        check(selector.inspect(exact, 9, true).kind == NsNowDogSelector.Kind.NONE, "Partial tree rearmed Back");
        selector.inspect(player("CANAL 10"), 9, true);
        check(selector.inspect(exact, 9, true).kind == NsNowDogSelector.Kind.NONE, "Absence incorrectly rearmed uncertain Back");
        selector = new NsNowDogSelector();
        check(selector.inspect(exact, 9, true, 1000).kind == NsNowDogSelector.Kind.DISMISS_PROMPT, "First timed prompt not handled");
        check(selector.inspect(exact, 9, true, 60000).kind == NsNowDogSelector.Kind.NONE, "Uncertain prompt repeated within same cycle");
        check(selector.inspect(exact, 9, true, 10801000).kind == NsNowDogSelector.Kind.DISMISS_PROMPT,
                "Source-proven next three-hour prompt instance not handled");
        for (String title : new String[] {"¿Sigues ahí?", "Error", "¿Seguís ahí? "}) {
            check(new NsNowDogSelector().inspect(prompt(title, body(29)), 9, true).kind == NsNowDogSelector.Kind.NONE,
                    "Different prompt title handled");
        }
        for (String message : new String[] {body(0), body(31), body(-1), "Para continuar, presioná cualquier botón", body(29) + " ",
                "Para no detener la reproducción, presioná cualquier botón del control (029)"}) {
            check(new NsNowDogSelector().inspect(prompt("¿Seguís ahí?", message), 9, true).kind == NsNowDogSelector.Kind.NONE,
                    "Different prompt body handled");
        }
        NsNowDogSelector.Node blocked = root().add(node("networkDialog", "", false, 1)
                .add(node("lblTitle", NsNowDogSelector.DEBUG_TITLE, false, 2))
                .add(node("btnAction", "PREFERENCIAS DE DISPOSITIVO", true, 3))
                .add(node("btnCancel", "CERRAR APLICACIÓN", true, 4)));
        NsNowDogSelector.Decision denied = new NsNowDogSelector().inspect(blocked, 8, false);
        check(denied.kind == NsNowDogSelector.Kind.BLOCKED && "nsnow_debugging_enabled".equals(denied.failure),
                "NS NOW debugging restriction was not honored");
        check(new NsNowDogSelector().inspect(blocked.add(row("CANAL 10", 5)), 8, false).kind == NsNowDogSelector.Kind.BLOCKED,
                "Channel behind blocking modal selected");
        check(new NsNowDogSelector().inspect(list, 5, true).kind == NsNowDogSelector.Kind.NONE, "Active mode retuned a channel");

        selector = new NsNowDogSelector();
        NsNowDogSelector.Node home = root().add(header("MI NS NOW", 10, true)).add(header("TV EN VIVO", 20, false));
        NsNowDogSelector.Decision focus = selector.inspect(home, 12, false);
        check(focus.kind == NsNowDogSelector.Kind.FOCUS_LIVE && focus.handle == 20, "Exact live header not focused first");
        check(selector.inspect(home, 12, false).kind == NsNowDogSelector.Kind.NONE, "Header focus retried without proof");
        NsNowDogSelector.Node focusedHome = root().add(header("MI NS NOW", 10, false)).add(header("TV EN VIVO", 20, true));
        NsNowDogSelector.Decision click = selector.inspect(focusedHome, 12, false);
        check(click.kind == NsNowDogSelector.Kind.CLICK_LIVE && click.handle == 20, "Focused live header not activated");
        check(selector.inspect(focusedHome, 12, false).kind == NsNowDogSelector.Kind.NONE, "Header click retried");
        check(selector.inspect(root().add(row("CANAL 10", 1)), 12, false).kind == NsNowDogSelector.Kind.CLICK_CHANNEL,
                "Loaded live list did not select fixed channel");
        check(new NsNowDogSelector().inspect(root().add(header("TV EN VIVO", 10, false))
                .add(header("TV EN VIVO", 20, false)), 12, false).kind == NsNowDogSelector.Kind.NONE, "Ambiguous live header acted");
        for (String name : new String[] {"TV", "LIVE TV", "TV EN VIVO HD"}) {
            check(new NsNowDogSelector().inspect(root().add(header(name, 10, false)), 12, false).kind == NsNowDogSelector.Kind.NONE,
                    "Unverified live header alias accepted");
        }
        for (String name : new String[] {" TV EN VIVO ", "Tv en vivo", "tvenvivo", "TV\u00a0EN\tVIVO"}) {
            check(new NsNowDogSelector().inspect(root().add(header(name, 10, false)), 12, false).kind == NsNowDogSelector.Kind.FOCUS_LIVE,
                    "Canonical full live header rejected");
        }
        check(new NsNowDogSelector().inspect(root().add(header("TV EN VIVO", 10, false))
                .add(header(" tvEnVivo ", 20, false)), 12, false).kind == NsNowDogSelector.Kind.NONE,
                "Normalized ambiguous live header acted");
        check(new NsNowDogSelector().inspect(home, 12, true).kind == NsNowDogSelector.Kind.NONE, "Active mode navigated sidebar");
        check(new NsNowDogSelector().inspect(root().add(node("header_label", "TV EN VIVO", true, 1)), 12, false).kind
                == NsNowDogSelector.Kind.NONE, "Unscoped label without service header structure acted");

        selector = new NsNowDogSelector();
        NsNowDogSelector.Decision expand = selector.inspect(collapsedMain(), 16, false);
        check(expand.kind == NsNowDogSelector.Kind.FOCUS_HEADERS && expand.handle == 14,
                "Collapsed Main did not focus its exact visible headers grid");
        check(selector.inspect(collapsedMain(), 16, false).kind == NsNowDogSelector.Kind.NONE,
                "Uncertain grid focus was retried");
        check(selector.inspect(focusedHome, 16, false).kind == NsNowDogSelector.Kind.CLICK_LIVE,
                "Fresh visible live header was not required after expansion");
        check(new NsNowDogSelector().inspect(collapsedMain(), 16, true).kind == NsNowDogSelector.Kind.NONE,
                "Active mode expanded Main headers");
        check(new NsNowDogSelector().inspect(collapsedMain().add(row("CANAL 10", 30)), 16, false).kind
                == NsNowDogSelector.Kind.CLICK_CHANNEL, "Available exact channel lost priority to menu expansion");
        check(new NsNowDogSelector().inspect(collapsedMain().add(node("video_view", "", false, 30)), 16, false).kind
                == NsNowDogSelector.Kind.NONE, "Headers expanded from a player window");
        for (String modal : new String[] {"exo_confiramtionDialog", "networkDialog"}) {
            check(new NsNowDogSelector().inspect(collapsedMain().add(node(modal, "", false, 30)), 16, false).kind
                    == NsNowDogSelector.Kind.NONE, "Headers expanded behind a modal");
        }
        check(new NsNowDogSelector().inspect(collapsedMain().add(node("header_label", "MI NS NOW", false, 30)), 16, false).kind
                == NsNowDogSelector.Kind.NONE, "Visible sidebar was treated as collapsed");
        for (String duplicate : new String[] {"main_items_menu", "browse_frame", "browse_headers_dock", "browse_headers_root", "browse_headers"}) {
            check(new NsNowDogSelector().inspect(collapsedMain().add(node(duplicate, "", false, 30)), 16, false).kind
                    == NsNowDogSelector.Kind.NONE, "Ambiguous Main structure caused focus: " + duplicate);
        }
        NsNowDogSelector.Node detached = root().add(node("main_items_menu", "", false, 10))
                .add(node("browse_frame", "", false, 11)).add(node("browse_headers_dock", "", false, 12))
                .add(node("browse_headers_root", "", false, 13))
                .add(new NsNowDogSelector.Node(PKG, PKG + ":id/browse_headers", "", true, false, 14, true, false, ""));
        check(new NsNowDogSelector().inspect(detached, 16, false).kind == NsNowDogSelector.Kind.NONE,
                "Detached grid with unrelated Main IDs caused focus");
        NsNowDogSelector.Node inaccessible = root().add(node("main_items_menu", "", false, 10))
                .add(node("browse_frame", "", false, 11).add(node("browse_headers_dock", "", false, 12)
                    .add(node("browse_headers_root", "", false, 13)
                        .add(new NsNowDogSelector.Node(PKG, PKG + ":id/browse_headers", "", false, false, 14,
                                true, false, "")))));
        check(new NsNowDogSelector().inspect(inaccessible, 16, false).kind == NsNowDogSelector.Kind.NONE,
                "Invisible headers grid caused focus");
        check(NsNowDogSelector.fixedTarget(collapsedMain(), NsNowDogSelector.Kind.CLICK_LIVE) == null,
                "Invisible text row was made clickable by grid expansion policy");

        selector = new NsNowDogSelector();
        NsNowDogSelector.Node wrongPlayer = root().add(video("Hide player controls", true))
                .add(node("ch_info_name", "CANAL 4", false, 2)).add(picker("Señales"));
        check(selector.inspect(wrongPlayer, 15, false).kind == NsNowDogSelector.Kind.CLICK_PICKER, "Fixed Señales picker not opened");
        check(selector.inspect(wrongPlayer, 15, false).kind == NsNowDogSelector.Kind.NONE, "Picker click retried");
        check(selector.inspect(wrongPlayer.add(row("CANAL 10", 6)), 15, false).kind == NsNowDogSelector.Kind.CLICK_CHANNEL,
                "Picker list blocked by old current-channel label");
        selector = new NsNowDogSelector();
        NsNowDogSelector.Node hiddenControls = root().add(video("Show player controls", true)).add(node("ch_info_name", "CANAL 4", false, 2));
        check(selector.inspect(hiddenControls, 15, false).kind == NsNowDogSelector.Kind.SHOW_CONTROLS, "Exact player controls not revealed");
        check(selector.inspect(hiddenControls, 15, false).kind == NsNowDogSelector.Kind.NONE, "Player control toggle retried");
        check(new NsNowDogSelector().inspect(root().add(video("Hide player controls", true)).add(picker("Signals")), 15, false).kind
                == NsNowDogSelector.Kind.NONE, "Unverified picker label accepted");
        check(new NsNowDogSelector().inspect(root().add(picker("Señales")), 15, false).kind
                == NsNowDogSelector.Kind.NONE, "Picker acted without player context");

        reservationPolicy(NsNowDogSelector.Kind.FOCUS_HEADERS, collapsedMain());
        reservationPolicy(NsNowDogSelector.Kind.FOCUS_LIVE, home);
        reservationPolicy(NsNowDogSelector.Kind.CLICK_LIVE, focusedHome);
        reservationPolicy(NsNowDogSelector.Kind.SHOW_CONTROLS, hiddenControls);
        reservationPolicy(NsNowDogSelector.Kind.CLICK_PICKER, root().add(video("Hide player controls", true)).add(picker("Señales")));
        reservationPolicy(NsNowDogSelector.Kind.CLICK_CHANNEL, list);

        NsNowDogSelector.Progress progress = new NsNowDogSelector.Progress();
        check("nsnow_access".equals(progress.failure()) && !progress.captured(), "Empty progress overclaimed a stage");
        progress.observe(foreign, null);
        check(!progress.captured(), "Foreign screen counted as NS NOW capture");
        progress.observe(root(), null);
        check(progress.captured() && "nsnow_access".equals(progress.failure()), "Unknown controls overclaimed navigation");
        progress.observe(root().add(node("header_label", " TV en vivo ", false, 1)), null);
        check("nsnow_menu".equals(progress.failure()), "Observed fixed label did not identify menu failure");
        progress.action(NsNowDogSelector.Kind.FOCUS_LIVE, false);
        check(progress.attempted(NsNowDogSelector.Kind.FOCUS_LIVE) && !progress.accepted(NsNowDogSelector.Kind.FOCUS_LIVE),
                "Rejected action was recorded as accepted");
        progress.action(NsNowDogSelector.Kind.FOCUS_LIVE, true);
        progress.action(NsNowDogSelector.Kind.CLICK_LIVE, true);
        check("nsnow_menu".equals(progress.failure()), "Accepted menu click claimed a loaded channel list");
        progress.observe(root().add(row("CANAL 4", 1)), null);
        check("nsnow_target".equals(progress.failure()), "Missing exact target not distinguished from menu");
        progress.observe(root().add(row("CANAL 10", 3)), null);
        check("nsnow_input".equals(progress.failure()), "Exact target with unsent preflight not distinguished");
        check(!progress.attempted(NsNowDogSelector.Kind.CLICK_CHANNEL), "Seeing target claimed an input attempt");
        progress.action(NsNowDogSelector.Kind.CLICK_CHANNEL, false);
        check("nsnow_input".equals(progress.failure()), "Rejected channel click claimed player verification stage");
        progress.action(NsNowDogSelector.Kind.CLICK_CHANNEL, true);
        check("nsnow_player".equals(progress.failure()), "Accepted channel action did not identify missing player confirmation");
        progress.observe(blocked, new NsNowDogSelector().inspect(blocked, 8, false));
        check("nsnow_debug".equals(progress.failure()), "Exact debug restriction did not override coarse progress");
        NsNowDogSelector.Progress pickerProgress = new NsNowDogSelector.Progress();
        pickerProgress.action(NsNowDogSelector.Kind.SHOW_CONTROLS, true);
        check("nsnow_access".equals(pickerProgress.failure()), "Controls request falsely claimed a channel list");
        pickerProgress.action(NsNowDogSelector.Kind.CLICK_PICKER, true);
        check("nsnow_channel".equals(pickerProgress.failure()), "Accepted fixed picker action not distinguished");
        for (NsNowDogSelector.Node scene : new NsNowDogSelector.Node[] {
                root().add(row("CANAL 4", 1)), root().add(row("CANAL 100", 1)),
                root().add(row("CANAL 10", 1)).add(row(" canal10 ", 3)),
                root().add(new NsNowDogSelector.Node(PKG, PKG + ":id/channelLayout", "", true, false, 1)
                        .add(node("channelName", "CANAL 10", false, 2)))}) {
            NsNowDogSelector.Progress missing = new NsNowDogSelector.Progress();
            missing.observe(scene, null);
            check("nsnow_target".equals(missing.failure()), "Nonunique/nonclickable/wrong target claimed exact input opportunity");
            check(!missing.attempted(NsNowDogSelector.Kind.CLICK_CHANNEL), "Missing target recorded an action");
        }
        NsNowDogSelector.Progress transientTarget = new NsNowDogSelector.Progress();
        transientTarget.observe(root().add(row(" CANAL\u00a010 ", 1)), null);
        transientTarget.observe(root().add(row("CANAL 4", 1)), null);
        check("nsnow_input".equals(transientTarget.failure()), "Later screen lost fixed target-seen fact");
        transientTarget.action(NsNowDogSelector.Kind.CLICK_CHANNEL, false);
        check("nsnow_input".equals(transientTarget.failure()), "False/throw boundary overclaimed accepted channel input");
        transientTarget.action(NsNowDogSelector.Kind.CLICK_CHANNEL, true);
        check("nsnow_player".equals(transientTarget.failure()), "Accepted input did not move to player confirmation");
        for (java.lang.reflect.Field field : NsNowDogSelector.Progress.class.getDeclaredFields()) {
            check(field.getType() == boolean.class || field.getType() == int.class,
                    "Progress retained data other than coarse flags");
        }
        System.out.println("NsNowDogSelector: " + assertions + " behavioral assertions passed");
    }
}
