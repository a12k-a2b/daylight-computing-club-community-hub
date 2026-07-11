# Daylight Shade — first on-glass test protocol

For a Claude Code session running on a computer with a DC-1 connected over
USB. Goal: install the shade, exercise everything, discover the warmth
setting key, and produce a structured findings report that goes back to the
cloud session iterating on the code (PR #41).

Safety context: the DC-1 belongs to Anjan (the repo owner) and this is his
own app. Everything here is reversible — `adb uninstall
com.daylightcomputer.shade` removes every trace; the app declares no
INTERNET permission and modifies only ordinary OS settings. Don't touch
anything else on the device.

Package: `com.daylightcomputer.shade` · min/target SDK 33 (device runs
AOSP 13) · APK: `shade/dist/daylight-shade.apk` on branch
`claude/custom-quick-settings-panel-g3qdj1`.

## 0. Ground rules for the testing session

- **Screenshot everything and actually look at it** (`adb exec-out
  screencap -p > NN-name.png`, then open the image). Rendering claims
  need eyes, and the human's eyes are needed for backlight/feel questions —
  ask him at the marked ⟨HUMAN⟩ steps.
- **Capture logcat for the whole session** (step 4) and check it after
  every stage for `AndroidRuntime`, `Shade*` tags, and window errors.
- **Number findings as you go**; the report format is at the end.
- If something crashes, keep going where possible — a complete list beats
  a perfect first item.

## 1. Prerequisites

1. `adb` on PATH. If missing: macOS `brew install android-platform-tools`;
   Debian/Ubuntu `sudo apt-get install android-sdk-platform-tools` (or
   download platform-tools from dl.google.com/android/repository/).
2. USB debugging on the DC-1 — ⟨HUMAN⟩ if not already on:
   Settings → About tablet → tap **Build number** 7× → back →
   System → **Developer options** → **USB debugging** ON. Then accept the
   "Allow USB debugging?" RSA prompt when it appears on the tablet.
3. `adb devices` must show the device as `device` (not `unauthorized`).

## 2. Get the APK

```sh
curl -L -o daylight-shade.apk \
  https://github.com/a12k-a2b/daylight-computing-club-community-hub/raw/claude/custom-quick-settings-panel-g3qdj1/shade/dist/daylight-shade.apk
```

(or clone the repo and use `shade/dist/daylight-shade.apk` from that
branch). If the cloud session pushes fixes mid-test, re-run this and
`adb install -r` — same signing key, updates in place.

## 3. Install and grant everything grantable

```sh
adb install -r daylight-shade.apk

P=com.daylightcomputer.shade
# app-ops (the "special access" toggles)
adb shell appops set $P SYSTEM_ALERT_WINDOW allow
adb shell appops set $P WRITE_SETTINGS allow
# runtime permissions
adb shell pm grant $P android.permission.POST_NOTIFICATIONS
adb shell pm grant $P android.permission.BLUETOOTH_CONNECT
adb shell pm grant $P android.permission.BLUETOOTH_SCAN
adb shell pm grant $P android.permission.NEARBY_WIFI_DEVICES
# development-grantable (unlocks the warmth stand-in and more)
adb shell pm grant $P android.permission.WRITE_SECURE_SETTINGS
# notification listener (media card + notification list) and DND
adb shell cmd notification allow_listener $P/$P.ShadeNLService
adb shell cmd notification allow_dnd $P
```

Any command that errors: note it in the findings (exact message) and
continue — the app is built to degrade.

## 4. Start the flight recorder

```sh
adb logcat -c
adb logcat -v time > shade-logcat.txt 2>&1 &   # leave running throughout
```

After each stage: `grep -E 'AndroidRuntime|Shade|FATAL' shade-logcat.txt | tail`.

## 5. Launch and verify the control room

```sh
adb shell am start -n com.daylightcomputer.shade/.MainActivity
```

Screenshot. Verify: title "Daylight Shade", PREVIEW MODE banner, every
grant row from step 3 showing ● (filled). Any ○ that step 3 should have
covered is finding material. Then enable the swipe zone: tap the row
"swipe zone just below the status bar" (compute tap coordinates from your
screenshot; `adb shell input tap X Y`). Screenshot again to confirm the
selection square filled in.

## 6. The checklist

Drive with `adb shell input tap/swipe` guided by screenshots; ask the
human for anything physical. Screenshot before/after each lettered stage.

**A. Panel + gesture.** Open via the button in the control room, then via
the swipe zone. (ShadeService is deliberately not exported, so
`am start-foreground-service …SHOW` from the shell is refused — the
button, the tile, and the strip are the only real surfaces.) The strip is
the band directly below the status bar — on the DC-1 that's y≈35–70, so
`adb shell input swipe 600 50 600 900 250` from the home screen; confirm
the exact band via `adb shell dumpsys window windows` (the
`com.daylightcomputer.shade` window's `frame=`). Starting lower (y=90)
misses the strip and the launcher's own swipe-down opens the stock shade
— that's the launcher, not a bug. Verify: sheet slides down, scrim behind
it, tap-scrim closes, BACK closes. ⟨HUMAN⟩ drag it by finger: does it
track the finger? settle naturally? feel fast enough?

**B. Sliders.** Drag the brightness slider full left/right (input swipe
along the track). ⟨HUMAN⟩ did the backlight actually change smoothly?
Then the **warmth key mission** — the single most valuable data point of
this whole test:

```sh
adb shell settings list system > warm-before.txt
adb shell settings list secure >> warm-before.txt
adb shell settings list global >> warm-before.txt
```
Move the STOCK warmth/amber slider noticeably (adb can do this alone:
`cmd statusbar expand-settings`, then swipe along the second slider —
no fingers needed); then:
```sh
adb shell settings list system > warm-after.txt
adb shell settings list secure >> warm-after.txt
adb shell settings list global >> warm-after.txt
diff warm-before.txt warm-after.txt
```
Record every changed key + its before/after values. If nothing changed,
say so explicitly (means the slider talks to a vendor service/sysfs
instead — also crucial to know). **Answered 2026-07-11 on a real DC-1:**
the key is `Settings.System screen_brightness_amber_rate` = 256 + amber
(0..255); 256 = paper white, 511 = full amber. Sideloads cannot write it
(AOSP rejects unknown system-table keys from non-system apps), so in
preview mode our slider drives night-light as a stand-in — ⟨HUMAN⟩ does
the screen tint change? Re-run this diff after any Sol:OS update to catch
the key moving.

**C. Pills.** Tap each of the six. Expected in preview mode: Quiet and
Rotation flip in place (inverted pill); Wi-Fi/Bluetooth open our picker
pages; Airplane opens the airplane settings screen; Dark opens display
settings. Long-press Quiet/Dark/Rotation/Airplane → settings screens.
Verify pill states match reality (compare with stock QS).

**D. Pickers.** Wi-Fi: does the network list populate with real SSIDs,
signal bars sensible, current network on top? Tap a known network →
expected in preview mode: hands off to the compact system Wi-Fi sheet.
Bluetooth: paired devices listed with correct state? "find new devices" →
does discovery find something (⟨HUMAN⟩ put earbuds in pairing mode if
handy)? Tap to pair → system pairing dialog appears? Back arrow returns to
the main sheet; BACK key from a picker goes back (not close).

**E. Media.** ⟨HUMAN⟩ start playing something (Spotify if installed, else
any player). Open panel: app name/title/artist correct? play/pause, next,
prev work? ±15s does something sensible? Heart: visible? filled state
sane? tap it and ⟨HUMAN⟩ check in the app whether it actually
liked/saved. Extra-actions row (shuffle/repeat/…): present? icons legible
ink-on-paper? do they work?

**F. Notifications.**
```sh
adb shell cmd notification post -S bigtext -t TestNote ShadeTest "hello from the test rig"
```
(keep the title one word — quoting through two shells mangles spaces)
Row appears? Tap ✕ dismisses? "clear all" clears? Tap-to-open on a real
notification (e.g. the media one) opens the right app?

**G. Dark mode, live.** With the panel OPEN:
```sh
adb shell cmd uimode night yes   # then: no
```
Panel should rebuild in place — night = same page inverted. Screenshot
both. ⟨HUMAN⟩ on this reflective screen, is inverted actually pleasant?
(Roadmap open question 4.)

**H. Failure drills.**
1. Panel open → `adb shell am force-stop com.daylightcomputer.shade` →
   panel must vanish, stock shade still works, no system-UI weirdness.
   (Force-stop also stops auto-restart until next launch — expected,
   not a bug.) Relaunch the app after.
2. Airplane mode on (via stock QS or settings) → reopen our panel →
   everything still renders; airplane pill shows on; nothing hangs.
   Airplane off again.
3. `adb reboot` → after boot, panel still opens via swipe zone (service
   came back on boot)? Grants survived?
4. Lock the screen → the swipe zone must do nothing over the lockscreen.
5. Rotate the tablet → open panel in both orientations → layout sane?

**I. Footprint.**
```sh
adb shell dumpsys meminfo com.daylightcomputer.shade | head -30
```
Record TOTAL PSS with panel closed and open.

## 7. The report

Write `shade-test-report.md` and show it to the human to paste back into
the cloud session. Format:

```
## Warmth key discovery
<changed keys + values, or "no settings changed — vendor path">

## Findings (most severe first)
1. [blocker|bug|annoyance|polish] <area>: <one-line summary>
   repro: … · screenshot: NN-name.png · logcat: <excerpt or "clean">
...

## Feel notes from Anjan (verbatim)
- …

## Footprint
PSS closed: … / open: …

## Grant commands that failed (if any)
```

Severity guide: blocker = crash/unusable; bug = wrong behavior; annoyance
= works but grating; polish = cosmetic/feel.

## 8. Cleanup (only if asked)

`adb uninstall com.daylightcomputer.shade` — removes everything. Turning
USB debugging back off afterwards is reasonable hygiene.
