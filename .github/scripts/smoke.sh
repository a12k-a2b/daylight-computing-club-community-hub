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
# The monkey must not stop for anyone: by default it aborts on a crash or ANR
# in ANY process — a loaded CI runner can stall com.android.systemui and sink
# an innocent dish's verdict. With the ignore flags every event lands, faults
# are still all in logcat, and the counters below bill them to the right app.
if adb shell monkey -p "$PKG" --throttle 100 --ignore-security-exceptions \
    --ignore-crashes --ignore-timeouts --ignore-native-crashes -v 1500 > monkey.log 2>&1; then
  echo "MONKEY=survived" >> report.env
else
  echo "MONKEY=aborted" >> report.env
fi
MEM=$(adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -m1 "TOTAL PSS:" | grep -o '[0-9]*' | head -1)
if [ -n "$MEM" ]; then echo "MEM_MB=$((MEM / 1024))" >> report.env; else echo "MEM_MB=" >> report.env; fi
# bill fatals to the dish only — a crash header names its process on the next
# line, and other apps falling over on the emulator are not this dish's fault
adb logcat -d > logcat.txt 2>/dev/null
FATALS=$(grep -A1 "FATAL EXCEPTION" logcat.txt | grep -c "Process: $PKG")
ALL_FATALS=$(grep -c "FATAL EXCEPTION" logcat.txt)
echo "FATALS=$FATALS" >> report.env
echo "OTHER_FATALS=$((ALL_FATALS - FATALS))" >> report.env
# same attribution for ANRs (count the ActivityManager line only — the monkey
# echoes each one a second time)
ANRS=$(grep -c "ActivityManager: ANR in $PKG" logcat.txt)
ALL_ANRS=$(grep -c "ActivityManager: ANR in " logcat.txt)
echo "ANRS=$ANRS" >> report.env
echo "OTHER_ANRS=$((ALL_ANRS - ANRS))" >> report.env
exit 0
