# Evidence packet — quick-settings redesign ideation (2026-07-12)

Facts only. No preferred direction. Both brains generate ideas from this
plus the shade source tree (`shade/` in this repo).

## The ask (from Anjan, verbatim intent)

Open ideation: how to further redesign Daylight Shade's quick settings.
Balance to strike: most Daylight customers are iPhone/Mac people. Remove
(a) the ugliness of Android and (b) the intimidation of Android-isms.
Make device operation intuitive for iOS-natives, and TEACH proper use of
this device implicitly — through structure, copy, and small affordances.
The brightness-slider vocabulary ("the slider tells a story") is the
model of what he likes. Every idea must carry: implementation difficulty,
who implements (shade app vs Sol:OS platform team), maintenance cost,
and likely collisions / emergent complexity.

## The product

- Daylight DC-1: 10.5" monochrome REFLECTIVE LCD (Live Paper), 1200×1600.
  White + amber backlight (backlight is a supplement; the display reads
  by ambient light). No keyguard configured on test device; location off.
- Sol:OS = AOSP 13 skin; jump to AOSP 16/17 planned; portability matters.
- Daylight Shade = standalone overlay app replacing the stock pull-down
  (NOT a SystemUI fork). Pure Java, zero deps, 64 KB APK, no Gradle,
  no INTERNET permission. Design: pure grayscale ink/paper, serif,
  2–3px borders, canvas-drawn glyphs, ≥48dp targets; night = inverted.
- Two modes, one APK: preview (sideload, user-grantable perms) and full
  (platform-signed priv-app "blessing" — pending platform-team ask).
- Distribution today: club shelf (daylightcomputer.club) sideloads.

## What the panel contains today (all device-verified)

Header (clock/date/alarm/battery) · brightness slider (perceptual dial,
soft-capped at ~60% duty, live zone legend: pure reflective → candlelit
→ paper-like → screen-like, both zone boundaries user-tunable in shade
setup) · warmth slider (disabled on sideloads with honest hint; drives
the real amber key `screen_brightness_amber_rate` = 256+amber once
blessed) · six pills (Wi-Fi, Bluetooth, Airplane, Quiet, Dark, Rotation;
tap flips where Android allows, else opens the right surface;
long-press = deeper; BT and Dark genuinely flip on sideloads today) ·
in-shade Wi-Fi/BT picker pages (network list is blessing-gated; BT
discovery works today) · media card (title/artist/app, transport, ±15s,
heart via rating API or app's like action, extra custom actions
re-inked) · notification list (live post/dismiss/clear-all, own plumbing
filtered) · footer (all settings · shade setup).

## Hard constraints (verified on glass)

- Wi-Fi SSID + scan results: redacted for sideloads (location-gated;
  neverForLocation excluded). Blessed NETWORK_SETTINGS bypasses.
- Warmth key: writable by system installs only.
- Airplane/Wi-Fi radio flips: system-only until blessing (BT + Dark are
  the exceptions that work today; BT's enable/disable dies in AOSP 14+
  for non-system apps — moot post-blessing).
- Performance budget promise: dormant foreground service, NO timers, no
  polling, no wakeups; receivers only while the panel is open.
- Crash story: worst case is always "stock shade comes back".
- shade/design/mock.html must stay 1:1 with PanelView.
- control/SysApi.java is the only file allowed hidden APIs.

## Existing roadmap (do not merely restate; build on or reframe)

Presets row (Daylight/Overcast/Candle/Moonless) · choose-your-own six
pills · lock-screen variant · own status bar (blessing) · volume row ·
privacy indicators (blessing) · warmth scheduling ("candlelight after
sunset") · club-shelf notice row · richer notification rows · media seek
bar · grayscale RRO re-theme of Settings (OS-side) · gesture-feel menu.

## Audience facts

- iPhone-natives: know Control Center (tap = toggle, long-press =
  expand — same interaction grammar as the shade already uses), Focus
  modes, Do Not Disturb moon, screen-time language. Do NOT know:
  Android Settings hierarchy, "SSID", "pairing mode", APK-isms.
- The club's voice (site + shade setup): plain sentences, warm, honest
  about what needs the blessing; "written for a non-technical friend".

## Not collected

- No user interviews/telemetry (the app has no network; the club has no
  analytics). All audience claims are founder intuition + platform docs.
- No battery-life measurements of backlight levels (cap rationale is
  duty-cycle arithmetic, not measured drain).
