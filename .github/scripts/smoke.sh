#!/usr/bin/env bash
# Poke a submitted APK on the booted emulator and leave findings in report.env.
# Runs as ONE script because the emulator-runner action executes each line of
# its `script:` input in a separate shell — variables don't survive lines.
set +e
APK="$1"
AAPT=$(ls "$ANDROID_HOME"/build-tools/*/aapt | tail -1)
PKG=$("$AAPT" dump badging "$APK" | grep -o "package: name='[^']*'" | head -1 | cut -d"'" -f2)
echo "PKG=$PKG" >> report.env
if adb install -g "$APK"; then
  echo "INSTALL=ok" >> report.env
else
  echo "INSTALL=failed" >> report.env
  exit 0
fi
adb logcat -c
if adb shell monkey -p "$PKG" --throttle 100 --ignore-security-exceptions -v 1500 > monkey.log 2>&1; then
  echo "MONKEY=survived" >> report.env
else
  echo "MONKEY=crashed" >> report.env
fi
MEM=$(adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -m1 "TOTAL PSS:" | grep -o '[0-9]*' | head -1)
if [ -n "$MEM" ]; then echo "MEM_MB=$((MEM / 1024))" >> report.env; else echo "MEM_MB=" >> report.env; fi
FATALS=$(adb logcat -d | grep -c "FATAL EXCEPTION")
echo "FATALS=$FATALS" >> report.env
exit 0
