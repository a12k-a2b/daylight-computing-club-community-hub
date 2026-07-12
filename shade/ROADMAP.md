# Daylight Shade — roadmap

The whole point of the shade is that iterating on quick settings stops
being an OS project and becomes an afternoon in `PanelView.java`. This doc
is the queue. Each idea says what it needs before it can happen and roughly
how big it is — *(afternoon)*, *(weekend)*, or *(project)*. Ideas with no
dependency listed can start any time.

The one recurring dependency is **the blessing**: Sol:OS shipping this app
platform-signed as a priv-app (the exact ask lives in README.md, "The exact
ask for the platform team"). Preview mode needs none of it; everything
marked *needs blessing* lights up in the same APK the day it lands.

---

## v0.2 — prove it on glass *(now; needs a DC-1, not code)*

- [x] First sideload on a real DC-1 — *done 2026-07-11:* full adb-driven
      protocol passed (gestures, sliders, pills, pickers, notifications,
      dark mode, failure drills, reboot). Findings + fixes:
      `shade-test-report.md`. "Live with it for a day" still open.
- [ ] Tune the gesture: drag threshold, settle velocity, open/close timing.
      Feel is unfalsifiable from an emulator — this is why the APK exists
      before the polish does. *(afternoon after feedback)*
- [x] Fix list from first touch — *there was one, and it's fixed:* media
      card package-name label + dead-session ghost card (Android 11+
      package visibility, STATE_NONE filter), panel now closes on screen
      off, strip re-arms after app updates (MY_PACKAGE_REPLACED), warmth
      slider direction matched to stock, honest Wi-Fi-list hand-off,
      nameless BLE finds filtered, own plumbing out of the notification
      list.
- [ ] Then put it on the club shelf so installs go through the friendly
      wizard instead of raw sideloading. One normal club commit.

## v0.5 — full mode *(needs blessing)*

- [ ] Swipe on the status bar itself opens our panel; stock shade silenced.
      The code already does this — it just needs the permissions to exist.
- [ ] Zero-tap setup: pre-granted accesses in the build
      (`config_defaultListenerAccessPackages`, `config_defaultDndAccessPackages`,
      preset app-ops). New tablets come with the shade already alive.
- [ ] Warmth slider drives the real amber backlight — the key is known and
      wired (`screen_brightness_amber_rate` = 256+amber, discovered
      on-glass); Android only accepts the write from a system install, so
      this lights up automatically with the blessing. (The night-light
      stand-in was retired on DC-1s after Anjan's hands-on: a software
      tint over a hardware-amber backlight reads as broken — the slider
      now waits, disabled and honest, instead.)

## v1 — polish that needs nothing

- [x] **Dark-mode variant of the panel itself** — *built:* the panel
      follows the system theme — day is ink on paper, night the same page
      inverted — and an open panel rebuilds itself the moment the theme
      flips (Dark pill or schedule). The mock mirrors it via the viewer's
      color scheme. The live rebuild is verified on real glass
      (2026-07-11, `cmd uimode night` with the panel open — rebuilds in
      place, correctly inverted). Open question 4 below still stands:
      whether inverted is what night *wants* is an eyes question.
- [ ] **Landscape pass** — the DC-1 lives in both orientations; check the
      660dp sheet, maybe 6 pills in one row when wide. *(afternoon)*
- [ ] **Richer notification rows** — tap-to-expand long text, inline action
      buttons (reply / archive / mark done), group by app, show ongoing
      progress (downloads!) as a calm percentage line. *(weekend)*
- [ ] **Media seek bar** — a thin ink line under the transport buttons with
      elapsed/total time. *(afternoon)*
- [x] **Make the heart work everywhere** — *built:* the heart now uses the
      standard rating API where implemented and otherwise the app's
      like/favorite *custom action* (Spotify's route); the ±15s buttons
      prefer a real seek, then the app's own jump action (Audible's ±30s),
      then fast-forward/rewind; and whatever custom actions remain
      (shuffle, repeat, sleep timer…) appear as a second row of buttons,
      the app's own icons re-inked to match the shade, max 4. Filled-heart
      state for custom actions is a label heuristic — verify against real
      Spotify on-glass.
- [ ] **Choose-your-own pills** — a "which six?" editor in shade setup
      (candidates: battery saver, flashlight-if-exists, cast, mute,
      screenshot). The user-facing half of "modifiable, editable,
      iterative". NOTE: the pills now live as three captioned pairs
      (connection / attention / page) — the editor must fill roles
      within pairs, not shuffle an unstructured bag of six. *(afternoon)*
- [x] **The ideation-round batch** — *built 2026-07-12, from the
      two-brain round (see shade/decisions/2026-07-12-qs-redesign-...):*
      Apple vocabulary on the pills (founder ruling: familiar where
      familiar, novel where novel); three human pairs with captions;
      battery in words ("plenty", "full by 9:40" while charging);
      first-pull notes that dismiss by doing and never return (reset row
      in shade setup); the essentials page (text size, screen sleep,
      sound, alarms, storage, wallpaper, date & time, software update,
      shade setup — live rows where public APIs allow, honest hand-offs
      elsewhere) with the footer reframed as essentials · everything
      else…; VOICE.md. Presets-as-the-face was proposed and REJECTED
      (founder): the dials teach first.
- [ ] **Lock-screen behavior** — currently the strip simply refuses to open
      over the keyguard. Decide what a *calm* locked shade shows (clock +
      media controls only, no settings?) and build that variant.
      *(weekend; design decision first)*
- [ ] **Open/close feel menu** — instant vs 130 ms slide; "reduce motion"
      switch for purists. *(afternoon)*
- [x] **The dial that names the light** — *built (v1):* the brightness
      slider is soft-capped ("100%" ≈ 60% duty — slamming it right can't
      burn eyes or battery; true max lives in full Settings) and a quiet
      italic legend above the thumb names the zone: pure reflective /
      paper-like / screen-like. Perceptual (log) dial from dc1-keys
      underneath. Design exploration for richer treatments (zone ticks,
      presets row, vocabulary): `design/BRIGHTNESS-DIAL-BRIEF.md` —
      pasteable straight into Claude Design.

- [x] **"looking for a setting?"** — *built 2026-07-12 (founder idea):*
      plain-language settings search, fully offline — a bundled catalog
      of ~30 destinations with iPhone-vocabulary synonyms ("passcode",
      "make text bigger"), scored matching, tap → the right Settings
      screen; dead entries dropped per-device; no-match hands off to
      full Settings, plainly. No INTERNET, no model — the synonym table
      IS the offline brain. Device-verified end to end (2026-07-12):
      typed "pin code" into the in-shade box, tapped the answer, landed
      on Settings → Security with Screen lock in view. One real bug
      found and fixed on glass along the way: the panel was re-grabbing
      keyboard focus on every layout, which handed the IME to the
      launcher behind it. Post-blessing: READ_SEARCH_INDEXABLES (now
      in the platform ask) swaps the catalog for Settings' own live
      index. Queued: hold-to-speak (SpeechRecognizer, needs an on-glass
      check), row-highlight keys (verified per-version only), and — only
      if offline search demonstrably disappoints — a smarter networked
      tier via a companion app, never in the shade (the no-INTERNET
      promise is the trust story).

## v2 — replace the hand-off surfaces *(needs blessing)*

The three places v1 still shows stock Material, in order of annoyance:

- [x] **Native Wi-Fi picker inside the shade** — *built (young), shaken
      down on glass:* the list itself turns out to be blessing-gated —
      Android keeps scan results location-locked (neverForLocation is
      explicitly excluded, and the DC-1 runs location-off), handing
      sideloads an empty list. So in preview the picker shows the radio
      toggle plus an honest "list arrives with the blessing" hand-off row;
      the full list (strongest-first, current on top, tap-to-hop via
      `enableNetwork`) lights up with NETWORK_SETTINGS — the same
      carve-out the stock picker uses. Declared `neverForLocation` — the
      shade sees radios, never places. A native password sheet can follow
      someday.
- [x] **Native Bluetooth device list** — *built (young), shaken down on
      glass:* discovery genuinely finds nearby devices on a plain sideload
      (nameless BLE randoms filtered out — anything in pairing mode
      broadcasts a name), and tap-to-flip the radio works today with
      BLUETOOTH_CONNECT. Paired devices list with connect/disconnect needs
      blessing + `BLUETOOTH_PRIVILEGED`; hands off to settings until then.
      The pairing-code confirmation stays a system dialog (security).
      End-to-end pairing with real earbuds still wants a human test.
- [ ] **Grayscale re-theme (RRO) of the Settings app** — an OS-side theme
      overlay so every surface we still hand off to (full Settings, the
      Wi-Fi sheet, pairing dialogs) turns calm grayscale. Cheap bridge
      until the native pickers land, and worth keeping even after.
      *(OS-side, small — in the teammate ask)*

## v3 — the shade grows outward

- [ ] **Our own status bar** — clock/battery/quiet-dot drawn our way in a
      thin platform-blessed window; the stock one hidden. Completes the
      "nothing Material visible" story. *(project; needs blessing)*
- [ ] **Volume row / per-app volume** — media, alarm, notifications as ink
      sliders; maybe per-app someday. *(weekend)*
- [ ] **Privacy indicators** — a small "mic in use / camera in use" line in
      the header, drawn calmly instead of Android's green pill. Needs
      privileged app-ops watching. *(weekend; needs blessing)*
- [ ] **Warmth scheduling** — "candlelight after sunset": the warmth slider
      follows the sun, one gentle default instead of ten settings.
      *(afternoon once warmth is hooked up)*
- [ ] **Reading-light presets** — one row: Daylight / Overcast / Candle /
      Moonless; each a brightness+warmth pair. Possibly replaces both
      sliders someday for most people. *(afternoon; design decision)*
- [ ] **Club shelf notice** — when a friend shares a new dish, a quiet row
      in the shade: "Melissa brought sourdough-timer". The club and the OS
      shaking hands. *(weekend; wants a shelf feed endpoint)*
- [ ] **Alarm pill?** — revisit the sixth-pill debate: rotation lock won
      v1 because alarm isn't a toggle; if alarms matter more, the header
      alarm text could become tappable (opens the clock app) and free the
      pill for something else. *(afternoon; Anjan's call)*

## Design lab

`design/mock.html` is the fast loop: it is 1:1 with what the app draws, so
try layout ideas there first (pills per row, header arrangement, slider
weight), screenshot on an actual DC-1 display for the squint test, then
port the winner into `PanelView.java`. Variants worth mocking:

- one-row pills in landscape
- the dark (ink-paper) variant
- presets row replacing sliders
- a denser "power user" layout vs. the current calm one
- the Wi-Fi / Bluetooth picker pages (in the app since v0.2 but not yet in
  the mock — add them so picker design iterates at mock speed too)

## Open questions (answers change the queue)

1. ~~Which setting does the stock warmth slider write?~~ **Answered
   on-glass 2026-07-11:** `Settings.System screen_brightness_amber_rate`
   = 256 + amber (0..255), left-is-amber on the stock slider. Writable
   only by system installs → v0.5 warmth rides the blessing; v3
   scheduling unblocked the same day that lands.
2. What should a locked shade show? *(design decision — unblocks v1
   lock-screen)*
3. Does the DC-1 have a vibration motor? (If yes: subtle haptic tick on
   pill toggle; if no: delete this line.)
4. Dark mode on a reflective LCD: is white-on-black actually preferred at
   night with the amber backlight, or is the Dark pill itself vestigial on
   this hardware? Real-device answer wanted before investing in v1's dark
   variant.
