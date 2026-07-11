# Daylight Shade — first on-glass test report

Device: DC-1 (`vext_jagar`), Sol:OS on AOSP 13 (TP1A.220624.014), 1200×1600
@ density 200, no keyguard, location OFF. Driven over USB by a desktop
Claude session, 2026-07-11 ~02:55–03:45 local. This session both found and
fixed — each finding notes its status. Full protocol from ON-GLASS-TEST.md
executed except the ⟨HUMAN⟩ eye/finger checks (batched at the end).

## Warmth key discovery ✅ (the mission's big prize)

`Settings.System screen_brightness_amber_rate` — diffing `settings list
system` around the stock slider showed exactly one changed key.

- Encoding: **value = 256 + amber**, amber ∈ 0..255. 256 = paper white,
  511 = full amber. (Matches dc1-backlight's decompile note: the +256 is
  a sentinel; the "10-bit 511" theory stays dead.)
- Stock slider direction: **left = full amber (511), right = white** —
  "right = more light" on both stock sliders.
- **Write access is the catch**: AOSP's settings provider rejects unknown
  system-table keys from non-system apps
  (`IllegalArgumentException: You cannot keep your settings in the secure
  settings.`) — WRITE_SETTINGS and even WRITE_SECURE_SETTINGS don't help.
  Shell can write it; a sideload cannot; a system-image install can. So
  the real amber drive ships with the blessing, no extra permission
  needed. Warmth.java now knows the key + encoding, probes writability
  once (same-value write), and falls back to the night-light stand-in
  honestly.

## Findings (most severe first)

1. **[bug → fixed] Media card: raw package name + ghost card.** Showed
   "COM.AUDIBLE.APPLICATION" over an empty title with transport buttons
   for a *dormant* session (Audible, STATE_NONE, empty metadata). Two
   causes: Android 11+ package visibility (no `<queries>` → app label and
   custom-action icons unloadable — also why Audible's Bookmark action
   never rendered) and `Media.current()` accepting dead sessions. Fixed:
   QUERY_ALL_PACKAGES (sideloaded/priv app; Play policy moot), skip
   STATE_NONE/STOPPED/ERROR sessions, DISPLAY_TITLE fallback + trim.
   repro: panel open with Audible idle-since-hours · screenshot 04 vs 18.
2. **[bug → fixed] Wi-Fi picker listened forever.** "listening for
   networks…" never resolves: scan results are location-gated (DC-1 runs
   location off; `neverForLocation` is excluded from the gate, and the
   service silently returns an empty list — logcat: "Permission violation
   - getScanResults not allowed"). Fixed: picker now shows the radio
   toggle + an honest "the network list arrives with the Sol:OS blessing /
   tap here to pick from the system list" hand-off row; full list rides
   NETWORK_SETTINGS (the stock picker's own carve-out).
   repro: Wi-Fi pill tap, location off · screenshots 22 / 24 · logcat
   excerpt in report source.
3. **[bug → fixed] Strip dead after every app update.** `adb install -r`
   (or a club-shelf update) kills ShadeService and nothing restarted it —
   swipe silently gone until reboot/manual open. Fixed: BootReceiver now
   also handles ACTION_MY_PACKAGE_REPLACED; verified: strip window back
   ~3 s after install.
4. **[bug → fixed] Panel survives screen off.** Stock shade collapses on
   sleep; ours sat open across power-button off/on (stale clock, and on a
   lock-equipped device it would float notifications over the keyguard).
   Fixed: ACTION_SCREEN_OFF → panel removed instantly.
5. **[parity → fixed] Warmth slider ran backwards vs stock.** Ours was
   left=white/right=amber; stock (and the hardware key) run left=amber.
   Flipped the mapping and moved the filled warm-sun glyph to the left
   end; both sliders now read "right = more light", matching stock muscle
   memory. Flag for Anjan: shout if you preferred the old direction.
6. **[polish → fixed] Notification list noise + dishonest count.** The
   shade listed its own "standing by" plumbing, and the header counted
   notifications it never rendered (said 5, showed 4 — title-less entries
   were counted but skipped). Fixed: own package filtered, count now
   counts what's listable. ("Daylight Shade is displaying over other
   apps" from Android itself still shows — it's the OS talking, and
   filtering `android` would hide USB-debugging etc.; revisit in full
   mode where the nag may not apply.)
7. **[polish → fixed] BT discovery drowned in nameless neighbors.** Found
   devices rendered as bare randomized MACs ("3E:C1:86:7B:5D:36 · found
   nearby") — 3 AM neighborhood BLE noise. Anything actually in pairing
   mode broadcasts a name, so nameless finds are now filtered.
8. **[doc → fixed] ON-GLASS-TEST.md vs reality.** The
   `am start-foreground-service …SHOW` step is refused (service not
   exported — correct posture, wrong doc); the swipe example started at
   y=90, *below* the strip (y≈35–70 here), so the launcher's own
   swipe-down opened the stock shade; `-t "Test note"` loses its quoting
   through two shells. All corrected in the doc.
9. **[note, no code] Bluetooth tap genuinely flips the radio in preview.**
   `BluetoothAdapter.disable()/enable()` still works on API 33 with plain
   BLUETOOTH_CONNECT — a pleasant surprise (README said pills only open
   settings). Goes away in 14+ for non-system callers; we're system by
   then. README updated.
10. **[note, no code] Stock-shade parity captured.** Stock tiles:
    Airplane / Internet / Bluetooth / Auto-rotate / Do Not Disturb /
    Alarm; two sliders, brightness above warmth, icons left, thumbs left
    = low/amber. Ours trades Alarm for Dark — the roadmap's known
    sixth-pill debate, now with the stock lineup on record.

## What passed clean (no findings)

Panel first-render on hardware (layout, borders, serif, grayscale, scrim);
swipe-zone open + finger-track plumbing; scrim-tap and BACK close;
BACK-in-picker returns to sheet; Quiet/Rotation flip in place with correct
inverted-pill states verified against real settings; Airplane/Dark
hand-offs; live notification post/dismiss/clear-all logic; tap-to-open
(USB-debugging row → dev settings); live dark-mode rebuild with the panel
open (day↔night both ways); brightness slider writes real
`screen_brightness` (5→237 verified, restored); force-stop drill (both
windows drop, stock shade unharmed); airplane drill (pills track radio
death honestly); landscape layout; reboot re-arm (service + strip up,
grants intact); crash-free logcat the whole session.

## Footprint

PSS ~20.4 MB panel closed / ~87 MB panel open (HWUI buffers while a
window is up; released on close). APK 60 KB.

## Feel notes from Anjan (verbatim)

*(pending — the ⟨HUMAN⟩ batch below)*

1. Drag the panel by finger: tracks? settles right? fast enough?
2. Our warmth slider in preview (night-light stand-in): does the tint
   visibly change on this backlight at all?
3. Play Spotify, open panel: title/artist/heart correct? heart actually
   likes the song? extras row sane?
4. Pair real earbuds via "find new devices".
5. Dark panel at night on this reflective screen: pleasant or vestigial?
   (roadmap Q4)
6. ~~Confirm it was your fingers on the tablet ~02:56–03:10~~ — solved:
   those were *other Claude sessions* driving the same tablet (five
   collided tonight; `~/code/dc1-glass` + the CLAUDE.md glass rule were
   born at 03:36, mid-test, in response). A touch recorder on the
   touchscreen driver saw **zero** physical touches all session, so the
   glass hardware is clean and every "mystery" event matches adb
   injection. This session predates the rule; the test doc now tells the
   next run to claim the glass first.

## Grant commands that failed

None — all nine granted clean. (The failures that mattered were runtime,
not grants: see findings 1–2.)

## Device state left behind

Everything restored: brightness 5, amber 511, DND on, auto-rotate on,
screen timeout 10 min, night-display keys deleted (stand-in test crumbs
removed), airplane off, radios on. The APK on the device is this branch's
`shade/dist/daylight-shade.apk` (all fixes above), swipe zone enabled,
pickers enabled.

## Round 2 — founder feedback, verified on glass (same night, ~05:00)

Anjan's first hands-on pass surfaced four issues; all fixed and
re-verified on the device:

1. **Warmth slider "did some buggy other thing"** — that was the
   night-light stand-in: a software tint over a hardware-amber backlight
   reads as broken, not warm. On the DC-1 the slider is now disabled with
   the hint "the amber backlight unlocks with the Sol:OS blessing"
   (verified: full-track drag changes nothing — amber stays 511). The
   stand-in survives only on hardware with no amber key (emulators).
2. **Brightness curve** now matches dc1-keys' perceptual dial
   (position = ln(raw)/ln 255). Verified: mid-track → raw 16, 75% → 65,
   full right → 255; far left = raw 1 (backlight off), dead rung 2
   skipped.
3. **Stock-parity pill labels** (cloned from AOSP 13 BluetoothTile
   source): Wi-Fi shows the SSID when Android lets it (blessing — SSIDs
   are redacted for sideloads, so preview says "on"); Bluetooth's title
   becomes the connected device's name (one device) or "N devices"
   (more), repainted live via profile-proxy callbacks + connection-state
   broadcasts. Rendering verified; a name-on-pill check awaits a paired
   device.
4. **The "shady bottom thing" on Wi-Fi** was Android's deprecated
   Settings.Panel sheet (renders as a bare "Wi-Fi" stub on Sol:OS).
   Hand-offs now go straight to the Wi-Fi settings screen. The in-shade
   picker page stays — it IS the blessing stub and lights up with
   NETWORK_SETTINGS.
5. **Dark pill now flips for real in preview** (Anjan: "it just takes you
   to settings — bug or permissions?" — it was permissions:
   MODIFY_DAY_NIGHT_MODE is tier-3). New chain: blessed setNightMode →
   secure-settings path (`ui_night_mode` + a momentary car-mode nudge so
   it applies instantly; needs only the adb grant) → settings hand-off.
   Verified live: tap → system dark + panel rebuilds inverted; tap →
   back to light.

Note: this round ran under the new glass rule — claimed from (and handed
back to) the note-overlay session with Anjan's say-so.
