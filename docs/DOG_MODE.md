# Dog Mode TV bridge

Dog Mode is one explicit TV operation with a persistent recovery record. A
supported start saves the TV's existing automatic-off settings, disables the
verified timers, and selects NS NOW's CANAL 10 through a verified channel
capability. Opening NS NOW by itself is not a successful channel selection. If
that capability is unavailable, start returns `unavailable` before changing any
energy setting.

Stop restores the exact saved values, returns to BareHome, then clears the
recovery record. An absent original setting is restored as absent. It does not
replace saved values with guessed defaults. A second remote's Start cannot
overwrite the first Start's originals or relaunch the channel.

The record lives in BareHome's private files and is atomically saved and synced
before the first energy write. It survives process death and a TV restart. There
is no boot receiver or automatic recovery write: use Stop to finish restoration.
Do not clear BareHome's data or uninstall it while Dog Mode is active or recovery
is required.

## Supported energy controls

The controller requires model `CSV4K`, device `zhongshan`, product
`KTC_MOTOROLA_4K_SA_21V2`, Android 11 / API 30, and BareHome's existing optional
`WRITE_SECURE_SETTINGS` permission. The permission is broader than these three
keys. The exact allowlist is:

| Android namespace | Key | Dog Mode value |
| --- | --- | --- |
| Secure | `sleep_timeout` | `-1` (Never) |
| Secure | `attentive_timeout` | `-1` (Never) |
| Global | `no_signal_auto_power_off` | `0` (Off) |

These meanings were established from this TV's OEM Settings code: its
`EnergySaverFragment` writes `-1` for Never, `TVSettingConfig` maps
`attentive_timeout=-1` to Auto Sleep Never, and `PowerFragment` uses `0` for
no-signal automatic power-off. They are not universal Android TV vendor keys.
Other hardware and OS versions remain unsupported.

The bridge leaves Picture settings, `screensaver_enabled`, and
`screen_off_timeout` unchanged. In particular, `screen_off_timeout=-1` is not
treated as a safe Never value. The examined NS NOW `PlayerLIVE` sets
`FLAG_KEEP_SCREEN_ON` while its playback window is active. Disabling the verified automatic-off timers does
not guarantee video continues through a network failure, an NS NOW prompt, or
an external power command.

## Failure and recovery

The journal progresses through preparing, starting, active, and restoring. A
fresh random generation distinguishes each start. Start failure
attempts to restore all originals and return Home. Stop attempts every original
even if one write fails. Readback, navigation, or persistence failure keeps the
journal for an explicit later Stop; it never discards the originals to make the
interface appear off. Losing NS NOW's channel capability does not prevent
restoring energy settings.

State queries only read. They do not initialize, repair, relaunch, or reapply a
setting. `off` means there is no outstanding journal; it does not assert which
app is currently visible. `active` requires the active journal, matching disabled
timer values, and the enabled helper monitoring that generation. `starting`
means a live, bounded selection is in progress. An interrupted startup, unavailable
active helper, or external timer change returns `restore_required`. Unsupported
capability returns `unavailable`, and unreadable
or damaged storage returns `error`.

## Authenticated one-shot reply

The paired remote sends one exact app link over its pinned TV Remote connection:

```text
barehome://dog/state?reply=PRIVATE_IPV4&port=PORT&token=64_LOWERCASE_HEX
barehome://dog/start?reply=PRIVATE_IPV4&port=PORT&token=64_LOWERCASE_HEX
barehome://dog/stop?reply=PRIVATE_IPV4&port=PORT&token=64_LOWERCASE_HEX
```

Order is fixed. Unknown parameters, fragments, URI encodings, noncanonical IPs,
nonprivate addresses and ports outside 1024–65535 are rejected. The callback
must be on a local TV subnet and cannot be the TV itself, a network address, or
a broadcast address. No action is performed until the callback passes these
checks. Other local TV apps can invoke the exported fixed links; the links do
not grant those apps settings access or allow arbitrary settings or targets.

The TV sends one bounded ASCII UDP datagram:

```text
BH2|REQUEST_ID|STATE|MAC
```

`REQUEST_ID` is lowercase SHA-256 of the 32-byte request secret. `MAC` is
lowercase HMAC-SHA256 of `BH2|REQUEST_ID|STATE`, keyed by that secret. `STATE` is
`off`, `starting`, `active`, `restore_required`, `unavailable`, or `error`. The secret is
carried only in the encrypted app-link request and is never logged. The reply
contains only coarse state and is authenticated, not encrypted.

A failed Start may instead return one of seven fixed diagnostic states:
`nsnow_access` (NS NOW could not be opened or inspected), `nsnow_menu` (the live
TV menu was not reached), `nsnow_channel` (legacy/fallback channel-selection failure),
`nsnow_target` (channel rows appeared but no exact unique visible clickable CANAL 10
target was observed), `nsnow_input` (that target appeared but no channel click was
accepted, including known-unsent preflight exits), `nsnow_player` (its player was
not confirmed after an accepted click), or `nsnow_debug` (NS NOW's
debugging restriction). These contain no screen text or arbitrary error detail.
They are returned only after original energy settings, helper removal, and Home
were successfully restored. An incomplete recovery always returns
`restore_required` instead. A later state query returns the normal `off` state;
diagnostics are not saved as a mode or a playback history.

A short nonexported receiver owns a seven-second initial reply lifetime. Start
returns `starting` after the snapshot and timer changes. A separate bounded
startup operation has up to 30 seconds from request receipt to verify CANAL 10
and send a final authenticated reply to the same endpoint and token. It retains
no broadcast pending result or listening socket during that time. Start clients
allow 35 seconds; state and stop clients allow eight seconds.

The coordinator serializes mutations. Before Stop cancels the selector, it
durably marks the generation as restoring. Late callbacks cannot activate that
generation or a later session. The TV listens on no network port and adds no
periodic work or wake lock.
Recent mutation IDs are persisted in a bounded 16-entry replay cache before an
action. Duplicate requests read the current state instead of repeating a
mutation. This is bounded replay protection, not unlimited request history.

A missing reply is uncertain. It must never cause an automatic Start or Stop
retry; request state or explicitly choose the recovery action. The normal remote
continues to work if local UDP callbacks are blocked.

## Optional NS NOW helper and consent

NS NOW version 1.1.266 (409) exports its launcher but no channel link. It also
has its own three-hour "¿Seguís ahí?" prompt, independent of the TV's timers.
The optional Android Accessibility helper is limited in code to this exact
NS NOW version and package, `uy.com.nsnow.atv`.

The TV's visible Dog Mode consent screen explains this access. Opening it grants
nothing. In BareRemote Settings, choose **Set up on TV** to open that page on the
paired TV, then choose **Allow for Dog Mode** using the TV controls. The fixed
`barehome://dog/setup` link accepts no query, fragment, or consent value. It only
opens the page; Allow only saves a private opt-in. Start enables BareHome's own helper
component, selects a verified CANAL 10 target, then switches to event-driven
handling of NS NOW's exact inactivity prompt while the durable active session
remains authorized. The helper does not poll, send periodic keys, take
screenshots, collect program history, or act on other apps. Each action checks
the current package/window and matching durable generation immediately before
dispatch. It never dismisses or bypasses NS NOW's USB-debugging block.

Android Accessibility can expose broader screen contents than this feature
needs. Its package filter and code restrict the helper to NS NOW. The existing
broad `WRITE_SECURE_SETTINGS` permission is additionally used to add or remove
only BareHome's component from `enabled_accessibility_services`. Other entries
are preserved, and the app never writes the global accessibility-enabled switch.

Stop invalidates pending work and removes only BareHome's helper before
finishing energy restoration. Revoking consent also cancels and removes that
helper immediately, then attempts restoration; a failure remains visible and
keeps the journal. A failed removal cannot be reported as a completed exit.
Following process death, a system rebind may adopt a valid durable active lease
to handle future exact prompts. It never replays an interrupted selection or
automatically retunes an app. The helper is disabled when Dog Mode ends.

## Automated checks

The host tests exercise the actual transaction, wire parser, HMAC output, and
Android energy adapter. They cover durable snapshots, exact nullable-value
restoration, restart recovery, two-client idempotence, partial failure rollback,
external edits, permission loss, corrupt journals, hardware guards and the narrow
energy allowlist. They do not establish physical channel playback or persistence
across a real TV reboot.

## Physical validation — 2026-10-23

The signed BareHome 1.4 / code 12 candidate was installed in place on the CSV4K
(Android 11 / API 30) with NS NOW 1.1.266 / code 409. The exact signed universal
BareRemote macOS 0.4.0 / build 14 was launched from Applications on arm64 with its
existing pairing. The TV setup link opened the visible consent screen without
changing energy settings or enabling the helper. Developer options and USB
debugging were then disabled; the debugging listener was confirmed closed before
NS NOW launched.

Start returned authenticated `active`, and the owner confirmed CANAL 10 was
playing. End returned authenticated `off` only after the TV's exact energy
readbacks and helper removal passed; the owner confirmed BareHome was showing.
The private baseline contained sleep 86400000, attentive 14100000 and no-signal 3;
startup-failure checks also confirmed those original values were restored. The
display timeout and picture controls were left unchanged. Debugging remains off.

This was a short start/exit check. The actual three-hour inactivity prompt,
long-duration playback, TV reboot recovery and other TV/NS NOW versions were not
physically tested. Prompt handling has source-backed selector fixtures and a
final exact-prompt check before its one Back action. Android's Dog Mode client
was checked with host/loopback tests; no physical phone was used for this check.
