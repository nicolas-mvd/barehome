#!/bin/sh
# Every operation targets an explicitly selected TV. Never uninstall on failure.
set -eu
set -f
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PACKAGE=uy.nico.barehome
APP=$PACKAGE/.MainActivity

die() { printf '%s\n' "$*" >&2; exit 1; }
usage() {
    echo 'Usage: BAREHOME_TV=SERIAL ./tv.sh status|capture-home|check|install [APK]|run|use|restore|memory|backlight-permission'
    echo 'brightness-permission is a compatibility alias for backlight-permission.'
    echo 'Find SERIAL with adb devices -l. Set BAREHOME_ADB if adb is not on PATH.'
}
case "${1:-}" in
    -h|--help|help) usage; exit 0 ;;
    status|capture-home|check|install|run|use|restore|memory|backlight-permission|brightness-permission) ;;
    *) usage >&2; exit 1 ;;
esac
if test "$1" = install; then
    test "$#" -le 2 || die 'install accepts at most one APK path.'
else
    test "$#" -eq 1 || die 'Unexpected argument.'
fi
TV=${BAREHOME_TV:-}
test -n "$TV" || die 'Set BAREHOME_TV to the exact serial from adb devices -l; no device is selected automatically.'
if test -n "${BAREHOME_ADB:-}"; then
    ADB=$BAREHOME_ADB
elif command -v adb >/dev/null 2>&1; then
    ADB=$(command -v adb)
elif test -x "$SCRIPT_DIR/../.tools/platform-tools/adb"; then
    ADB=$SCRIPT_DIR/../.tools/platform-tools/adb
else
    die 'adb not found. Install Android SDK Platform-Tools and add adb to PATH, or set BAREHOME_ADB.'
fi
command -v "$ADB" >/dev/null 2>&1 || die "Cannot execute adb: $ADB"
state=$("$ADB" -s "$TV" get-state) || die 'Cannot reach the selected TV. Connect it and accept its debugging prompt first.'
test "$(printf '%s' "$state" | tr -d '\r')" = device || die "Selected TV is not ready: $state"
active_user=$("$ADB" -s "$TV" shell am get-current-user) || die 'Cannot verify the active Android user; stopping.'
active_user=$(printf '%s' "$active_user" | tr -d '\r')
test "$active_user" = 0 || die "This helper supports Android user 0 only. Switch to the TV owner profile first (active user: $active_user)."
# Wireless debugging ports and IP addresses can change or be reused. Prefer the
# TV's stable serial so a different endpoint cannot silently reuse its backup.
valid_serial() {
    case "$1" in
        ''|unknown|UNKNOWN|null|NULL|none|None) return 1 ;;
        *[!A-Za-z0-9._:-]*) return 1 ;;
        *) return 0 ;;
    esac
}
device_serial=$("$ADB" -s "$TV" shell getprop ro.serialno) || device_serial=
device_serial=$(printf '%s' "$device_serial" | tr -d '\r')
if ! valid_serial "$device_serial"; then
    device_serial=$("$ADB" -s "$TV" shell getprop ro.boot.serialno) || device_serial=
    device_serial=$(printf '%s' "$device_serial" | tr -d '\r')
fi
if valid_serial "$device_serial"; then
    identity_kind=serial
    identity_value=$device_serial
else
    identity_kind=transport
    identity_value=$TV
    echo 'TV exposes no stable serial: rollback is tied to this ADB connection. Verify it is the same physical TV before reusing saved state.' >&2
fi
# Hex encoding is collision-free and keeps identifiers out of path syntax.
DEVICE_ID=$identity_kind-$(printf '%s' "$identity_value" | od -An -tx1 | tr -d ' \n')
STATE_DIR=$SCRIPT_DIR/.device-state/$DEVICE_ID
HOME_FILE=$STATE_DIR/previous-home

canonical_component() {
    case "$1" in
        *ResolverActivity*|*ChooserActivity*) return 1 ;;
        */*) component_package=${1%%/*}; component_class=${1#*/} ;;
        *) return 1 ;;
    esac
    case "$component_package" in ''|*[!A-Za-z0-9_.]*) return 1 ;; esac
    case "$component_class" in ''|*[!A-Za-z0-9_.$]*) return 1 ;; esac
    case "$component_class" in .*) component_class=$component_package$component_class ;; esac
    printf '%s/%s\n' "$component_package" "$component_class"
}

resolve_home() {
    resolved=$("$ADB" -s "$TV" shell cmd package resolve-activity --brief --user 0 \
        -a android.intent.action.MAIN -c android.intent.category.HOME) || return 1
    printf '%s\n' "$resolved" | tr -d '\r' | tail -n 1
}

validate_previous() {
    candidate_home=$(canonical_component "$1") || die 'Previous HOME is invalid or a chooser. Select a real launcher as the default in TV settings, then capture-home again.'
    case "$candidate_home" in "$PACKAGE"/*) die 'BareHome cannot be its own rollback launcher. Select your previous launcher as default first.' ;; esac
    candidates=$("$ADB" -s "$TV" shell cmd package query-activities --brief --components --user 0 \
        -a android.intent.action.MAIN -c android.intent.category.HOME) || die 'Cannot list enabled HOME activities; stopping to preserve rollback.'
    candidates=$(printf '%s\n' "$candidates" | tr -d '\r')
    for candidate in $candidates; do
        normalized=$(canonical_component "$candidate") || continue
        test "$normalized" != "$candidate_home" || return 0
    done
    die 'Previous HOME is not an enabled HOME activity. Re-enable your previous launcher and select it as default before continuing.'
}

read_previous() {
    test -s "$HOME_FILE" || die 'No rollback launcher saved for this connection. Run capture-home before install, while your previous launcher is still the default.'
    previous=$(cat "$HOME_FILE") || die 'Cannot read the saved rollback launcher.'
    validate_previous "$previous"
}

capture_home() {
    if test -e "$HOME_FILE"; then
        read_previous
        printf 'Keeping saved rollback launcher: %s\n' "$previous"
        return
    fi
    previous=$(resolve_home) || die 'Cannot determine current HOME; nothing was changed.'
    validate_previous "$previous"
    umask 077
    mkdir -p "$STATE_DIR"
    printf '%s\n' "$previous" > "$HOME_FILE"
    printf 'Saved rollback launcher: %s\n' "$previous"
}

set_home() {
    requested=$(canonical_component "$1") || die 'Invalid requested HOME component.'
    # ADB joins shell arguments on the device. Preserve remote quoting for valid
    # nested Java class names containing $, which the remote shell would expand.
    "$ADB" -s "$TV" shell cmd package set-home-activity --user 0 "'$1'" || die 'Setting HOME failed; the rollback file was preserved.'
    actual=$(resolve_home) || die 'Cannot verify HOME after setting it; the rollback file was preserved.'
    actual_normalized=$(canonical_component "$actual") || die "HOME is still ambiguous after setting it: $actual. The rollback file was preserved."
    test "$actual_normalized" = "$requested" || die "HOME verification failed: requested $1, got $actual. The rollback file was preserved."
    "$ADB" -s "$TV" shell input keyevent KEYCODE_HOME || die 'HOME was set, but sending the Home key failed.'
    printf 'Verified default HOME: %s\n' "$actual"
}

case "$1" in
    status)
        printf 'Selected device: %s\n' "$TV"
        printf 'Rollback identity: %s (%s)\n' "$identity_value" "$identity_kind"
        current=$(resolve_home) || die 'Cannot resolve current HOME.'
        printf 'Current HOME: %s\n' "$current"
        if test -s "$HOME_FILE"; then
            printf 'Saved rollback HOME: %s\n' "$(cat "$HOME_FILE")"
        else
            echo 'Saved rollback HOME: none for this connection'
        fi
        ;;
    capture-home) capture_home ;;
    check)
        read_previous
        printf 'Validated enabled rollback HOME: %s\n' "$previous"
        ;;
    install)
        apk=${2:-$SCRIPT_DIR/build/BareHome.apk}
        test -f "$apk" || die "APK not found: $apk"
        # Installing a new HOME handler can reset the default to a chooser.
        capture_home
        "$ADB" -s "$TV" install --no-incremental --user 0 -r "$apk" || die 'Install failed. Existing apps and the saved rollback launcher were preserved.'
        ;;
    run)
        result=$("$ADB" -s "$TV" shell am start -W --user 0 -n "$APP") || die 'Could not launch BareHome.'
        printf '%s\n' "$result"
        case "$result" in *Error:*|*Exception*) die 'Android reported an error launching BareHome.' ;; esac
        ;;
    use)
        read_previous
        set_home "$APP"
        ;;
    restore)
        read_previous
        set_home "$previous"
        ;;
    memory) "$ADB" -s "$TV" shell dumpsys meminfo "$PACKAGE" ;;
    backlight-permission|brightness-permission)
        # These match Build.MODEL, Build.DEVICE, and Build.PRODUCT in the app.
        # The presence of a key alone is not proof of a compatible panel driver.
        model=$("$ADB" -s "$TV" shell getprop ro.product.model) || die 'Cannot read the TV model; nothing was granted.'
        device=$("$ADB" -s "$TV" shell getprop ro.product.device) || die 'Cannot read the TV device; nothing was granted.'
        product=$("$ADB" -s "$TV" shell getprop ro.product.name) || die 'Cannot read the TV product; nothing was granted.'
        model=$(printf '%s' "$model" | tr -d '\r')
        device=$(printf '%s' "$device" | tr -d '\r')
        product=$(printf '%s' "$product" | tr -d '\r')
        test "$model" = CSV4K && test "$device" = zhongshan && test "$product" = KTC_MOTOROLA_4K_SA_21V2 \
            || die 'Backlight control is supported only for the tested CSV4K / zhongshan / KTC_MOTOROLA_4K_SA_21V2 configuration. Nothing was granted; see docs/BRIGHTNESS.md.'
        backlight=$("$ADB" -s "$TV" shell settings get global picture_backlight) || die 'Cannot read picture_backlight; nothing was granted.'
        backlight=$(printf '%s' "$backlight" | tr -d '\r')
        case "$backlight" in
            [0-9]|[0-9][0-9]|[0-9][0-9][0-9])
                test "$backlight" -le 100 || die 'picture_backlight is outside 0–100; nothing was granted.' ;;
            *) die 'No valid existing picture_backlight value in 0–100; nothing was granted.' ;;
        esac
        echo 'Enabling optional backlight controls. WRITE_SECURE_SETTINGS is broader than backlight access; read docs/BRIGHTNESS.md for scope and revocation.'
        "$ADB" -s "$TV" shell pm grant --user 0 "$PACKAGE" android.permission.WRITE_SECURE_SETTINGS \
            || die 'Could not grant the backlight permission. Install BareHome v0.3.1 or newer first; see docs/BRIGHTNESS.md.'
        # Match a granted permission entry, never its requested-permissions name.
        permissions=$("$ADB" -s "$TV" shell dumpsys package "$PACKAGE") || die 'Could not verify the backlight permission; see docs/BRIGHTNESS.md.'
        printf '%s\n' "$permissions" | tr -d '\r' | grep -Eq '^[[:space:]]*android\.permission\.WRITE_SECURE_SETTINGS: granted=true([,[:space:]]|$)' \
            || die 'WRITE_SECURE_SETTINGS was not verified as granted; backlight controls are not ready. See docs/BRIGHTNESS.md.'
        printf 'Verified WRITE_SECURE_SETTINGS. Current backlight: %s%%; its value was not changed. Reopen BareHome to use the controls.\n' "$backlight"
        ;;
esac
