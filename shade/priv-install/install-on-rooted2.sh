#!/bin/zsh
# Daylight Shade → priv-app on rooted 2 (JP4R01422) — full mode without
# waiting for the platform build. SAFETY DESIGN: this device runs
# ro.control_privapp_permissions=enforce, where a priv-app requesting a
# privileged permission missing from the allowlist CRASHES BOOT. So the
# allowlist is GENERATED from the built APK's actual requests intersected
# with the device's actual privileged-permission set — the same check the
# boot enforcement performs, run before we ever reboot.
#
# Preconditions (script verifies): bootloader unlocked, adb root works,
# dc1-glass claimed by the operator, Anjan's explicit go.
set -euo pipefail
SERIAL=JP4R01422
ADB=(~/Library/Android/sdk/platform-tools/adb -s $SERIAL)
AAPT=~/Library/Android/sdk/build-tools/34.0.0/aapt2
APK=/Users/anjan/code/shade-onglass-worktree/shade/dist/daylight-shade.apk
XML=/tmp/privapp-permissions-daylightshade.xml

echo "== preflight =="
[ "$(~/code/dc1-glass status | grep -c 'shade priv-install')" = 1 ] || { echo "ABORT: claim the glass as 'shade priv-install' first"; exit 1; }
[ "$($ADB shell getprop ro.boot.flash.locked | tr -d '\r')" = "0" ] || { echo "ABORT: bootloader not unlocked"; exit 1; }
$ADB root >/dev/null 2>&1 || true; sleep 2
[ "$($ADB shell whoami | tr -d '\r')" = "root" ] || { echo "ABORT: adb root unavailable — recovery path would be weak"; exit 1; }

echo "== generate allowlist from the APK's own requests =="
REQUESTED=$($AAPT dump permissions $APK | sed -n "s/.*uses-permission: name='\(android.permission[^']*\)'.*/\1/p")
> $XML echo '<?xml version="1.0" encoding="utf-8"?>'
>> $XML echo '<permissions>'
>> $XML echo '  <privapp-permissions package="com.daylightcomputer.shade">'
COUNT=0
for p in ${(f)REQUESTED}; do
  PROT=$($ADB shell "dumpsys package | grep -A3 'Permission \[$p\]'" | grep 'prot=' | head -1 | sed 's/.*prot=//' | tr -d '\r')
  if [[ "$PROT" == *privileged* ]]; then
    echo "  allowlisting: $p ($PROT)"
    >> $XML echo "    <permission name=\"$p\"/>"
    COUNT=$((COUNT+1))
  fi
done
>> $XML echo '  </privapp-permissions>'
>> $XML echo '</permissions>'
xmllint --noout $XML || { echo "ABORT: generated XML invalid"; exit 1; }
[ $COUNT -ge 5 ] || { echo "ABORT: expected >=5 privileged perms, got $COUNT"; exit 1; }
echo "allowlist covers $COUNT privileged permissions; XML valid"

echo "== remove the /data sideload (would shadow the system copy) =="
$ADB uninstall com.daylightcomputer.shade 2>/dev/null || echo "(no data copy present)"

echo "== push =="
$ADB remount
$ADB shell mkdir -p /system_ext/priv-app/DaylightShade
$ADB push $APK /system_ext/priv-app/DaylightShade/DaylightShade.apk
$ADB push $XML /system_ext/etc/permissions/privapp-permissions-daylightshade.xml
$ADB shell "chmod 644 /system_ext/priv-app/DaylightShade/DaylightShade.apk /system_ext/etc/permissions/privapp-permissions-daylightshade.xml"
$ADB shell "restorecon -R /system_ext/priv-app/DaylightShade /system_ext/etc/permissions/privapp-permissions-daylightshade.xml"
$ADB shell ls -lZ /system_ext/priv-app/DaylightShade/ /system_ext/etc/permissions/privapp-permissions-daylightshade.xml

echo "== reboot (the moment of truth; adb-root survives a system_server crash loop, so rollback stays reachable) =="
read "?type YES to reboot $SERIAL now: " OK
[ "$OK" = "YES" ] || { echo "stopped before reboot — nothing active yet; rollback.sh removes the files"; exit 1; }
$ADB reboot
$ADB wait-for-device
for i in $(seq 1 60); do
  [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && { echo "BOOTED after ~${i}x3s"; break; }
  sleep 3
done
echo "== verify =="
$ADB shell pm path com.daylightcomputer.shade
$ADB shell dumpsys package com.daylightcomputer.shade | grep -E 'flags=|privateFlags=' | head -2
for p in STATUS_BAR WRITE_SECURE_SETTINGS MODIFY_DAY_NIGHT_MODE BLUETOOTH_PRIVILEGED READ_SEARCH_INDEXABLES; do
  echo "android.permission.$p: $($ADB shell dumpsys package com.daylightcomputer.shade | grep "android.permission.$p" | head -1 | tr -d '\r')"
done
echo "DONE — walk shade setup on the glass next (capability rows + the amber moment)"
