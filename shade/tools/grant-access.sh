#!/usr/bin/env bash
# Daylight Shade — hand the app every access it can hold today.
#
# ROADMAP v0.5 wants "zero-tap setup": a tablet where the shade is already
# alive, no permission tour. The real thing arrives with the blessing, as
# build config (config_defaultListenerAccessPackages and friends). This is
# the version available now — every grant in Caps.java's tier 1 and tier 2,
# handed over from a shell instead of from nine trips through Settings.
#
# Plain adb is enough for all of it. Root is NOT required: these are the
# user-grantable and development-tier permissions, not the privileged ones.
# So this works the same on a stock DC-1 as on rooted 2 — it is a setup
# convenience, not a root feature.
#
# What it deliberately does NOT do: the privileged/signature permissions
# (STATUS_BAR, INTERNAL_SYSTEM_WINDOW, NETWORK_SETTINGS, …). Those need a
# system-partition install or Daylight's platform key — see
# decisions/2026-08-05-amber-needs-a-brightness-poke.md for which is which.
#
# Usage:  tools/grant-access.sh [-s <serial>]
set -uo pipefail

PKG=com.daylightcomputer.shade
NL="$PKG/$PKG.ShadeNLService"
ADB=(adb)
[ "${1:-}" = "-s" ] && { ADB=(adb -s "$2"); shift 2; }

# With more than one device visible, never guess (LESSONS.md).
if [ "${#ADB[@]}" -eq 1 ] && [ "$("${ADB[@]}" devices | grep -c 'device$')" -gt 1 ]; then
    echo "refused: more than one device visible — pass -s <serial>" >&2
    "${ADB[@]}" devices | grep 'device$' >&2
    exit 2
fi

"${ADB[@]}" shell pm path "$PKG" >/dev/null 2>&1 || {
    echo "refused: $PKG is not installed on this device" >&2; exit 2; }

ok=0; failed=0
try() {  # try <label> <shell command…>
    local label="$1"; shift
    if out=$("${ADB[@]}" shell "$@" 2>&1); then
        case "$out" in
            *Exception*|*Error*|*error*|*Failure*|*Unknown*)
                printf '  ✗ %-34s %s\n' "$label" "$(echo "$out" | head -1)"; failed=$((failed+1));;
            *) printf '  ✓ %s\n' "$label"; ok=$((ok+1));;
        esac
    else
        printf '  ✗ %-34s %s\n' "$label" "$(echo "$out" | head -1)"; failed=$((failed+1))
    fi
}

echo "Daylight Shade — granting what this device allows"

echo "app-ops (the two the panel cannot open without):"
try "draw over other apps"      appops set "$PKG" SYSTEM_ALERT_WINDOW allow
try "modify settings"           appops set "$PKG" WRITE_SETTINGS allow

echo "runtime permissions:"
for p in BLUETOOTH_CONNECT BLUETOOTH_SCAN NEARBY_WIFI_DEVICES POST_NOTIFICATIONS RECORD_AUDIO; do
    try "$p" pm grant "$PKG" "android.permission.$p"
done

echo "development tier (adb-grantable, no root):"
try "WRITE_SECURE_SETTINGS"     pm grant "$PKG" android.permission.WRITE_SECURE_SETTINGS

echo "special access:"
try "notification listener"     cmd notification allow_listener "$NL"
try "do-not-disturb access"     cmd notification allow_dnd "$PKG"

echo
echo "granted $ok · failed $failed"
[ "$failed" -eq 0 ] || {
    echo "(a failure here is usually a permission this build spells differently —"
    echo " the panel degrades honestly for anything missing, so nothing is broken.)"; }
echo "Open the shade to confirm: the setup screen lists what the device allows."
