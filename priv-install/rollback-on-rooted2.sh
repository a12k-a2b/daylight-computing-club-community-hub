#!/bin/zsh
# Undo the priv-app install on JP4R01422. Works even mid-crash-loop as
# long as root adbd answers (userdebug does).
set -u
ADB=(~/Library/Android/sdk/platform-tools/adb -s JP4R01422)
$ADB root >/dev/null 2>&1; sleep 2
$ADB remount
$ADB shell rm -rf /system_ext/priv-app/DaylightShade
$ADB shell rm -f /system_ext/etc/permissions/privapp-permissions-daylightshade.xml
$ADB reboot
$ADB wait-for-device
echo "system copy removed; device rebooting. Re-sideload with adb install if wanted."
