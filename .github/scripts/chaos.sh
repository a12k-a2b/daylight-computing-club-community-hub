#!/usr/bin/env bash
# Chaos monkey for Tracing Paper: seed a giant library into a debuggable
# build, open the glass with the hardware key, hammer it with unthrottled
# random input, then check nothing crashed and the notes file still parses.
# Leaves findings in chaos-report.env and logs beside it.
set +e
EVENTS="${1:-20000}"
APK="apps-src/tracing-paper/build/tracing-paper.apk"
PKG="club.daylightcomputer.tracingpaper"

report() { echo "$1" >> chaos-report.env; }

if ! adb install -g "$APK"; then report "INSTALL=failed"; exit 0; fi
report "INSTALL=ok"

# --- seed a monstrous library (debuggable build => run-as works)
python3 .github/scripts/chaos_seed.py > seed.json 2> seed-stats.txt
cat seed-stats.txt
adb push seed.json /data/local/tmp/seed.json
adb shell screencap -p /data/local/tmp/snip.png
adb shell "run-as $PKG mkdir -p files/snips"
adb shell "run-as $PKG sh -c 'cp /data/local/tmp/seed.json files/notes.json'"
for i in 1 2 3 4 5 6 7 8; do
  adb shell "run-as $PKG sh -c 'cp /data/local/tmp/snip.png files/snips/seed-$i.png'"
done
report "SEED=$(wc -c < seed.json)"

# --- enable the service, open the pad with the top button's keycode
adb shell settings put secure enabled_accessibility_services "$PKG/.PadService"
adb shell settings put secure accessibility_enabled 1
sleep 4
adb shell am start -n "$PKG/.MainActivity"
sleep 3
adb shell input keyevent 142
sleep 3
adb logcat -c

# --- scripted heavy strokes first (long multi-segment swipes = dense ink)
for i in $(seq 1 30); do
  adb shell input swipe $((100 + i * 5)) 300 $((600 + i * 3)) $((900 + i * 7)) 120
done
# flip pages hard
for i in $(seq 1 15); do
  adb shell input swipe 400 900 400 200 80
done

# --- then the monkey, unthrottled
adb shell monkey -p "$PKG" --throttle 0 --ignore-security-exceptions --ignore-timeouts \
  --pct-syskeys 0 -v "$EVENTS" > monkey.log 2>&1
if [ $? -eq 0 ]; then report "MONKEY=survived"; else report "MONKEY=crashed"; fi

# --- close the pad (flush), then read the aftermath
adb shell input keyevent 142
sleep 4
adb logcat -d > logcat.txt
FATALS=$(grep -A1 "FATAL EXCEPTION" logcat.txt | grep -c "Process: $PKG")
report "FATALS=$FATALS"
if [ "$FATALS" -gt 0 ]; then
  echo "=== crash stack (logcat) ==="
  grep -B2 -A40 "FATAL EXCEPTION" logcat.txt | head -90
  echo "=== crash block (monkey) ==="
  grep -B2 -A40 "// CRASH" monkey.log | head -60
fi
MEM=$(adb shell dumpsys meminfo "$PKG" 2>/dev/null | grep -m1 "TOTAL PSS:" | grep -o '[0-9]*' | head -1)
[ -n "$MEM" ] && report "MEM_MB=$((MEM / 1024))" || report "MEM_MB="

adb shell "run-as $PKG cat files/notes.json" > after.json 2>/dev/null
CORRUPT=$(adb shell "run-as $PKG ls files/" | grep -c corrupt)
report "QUARANTINED=$CORRUPT"
python3 - <<'EOF' >> chaos-report.env
import json
try:
    d = json.load(open("after.json"))
    books = d.get("books", [])
    pages = sum(len(b.get("pages", [])) for b in books)
    strokes = sum(len(p.get("s", [])) for b in books for p in b.get("pages", []))
    print(f"INTEGRITY=ok")
    print(f"AFTER_BOOKS={len(books)}")
    print(f"AFTER_PAGES={pages}")
    print(f"AFTER_STROKES={strokes}")
except Exception as e:
    print("INTEGRITY=broken")
    print(f"INTEGRITY_ERROR={type(e).__name__}")
EOF
cat chaos-report.env
exit 0
