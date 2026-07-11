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

- [ ] First sideload on a real DC-1: walk `shade setup`, grant the four
      accesses, live with it for a day.
- [ ] Tune the gesture: drag threshold, settle velocity, open/close timing.
      Feel is unfalsifiable from an emulator — this is why the APK exists
      before the polish does. *(afternoon after feedback)*
- [ ] Fix list from first touch (there will be one — overlay windows always
      have a surprise or two on real hardware).
- [ ] Then put it on the club shelf so installs go through the friendly
      wizard instead of raw sideloading. One normal club commit.

## v0.5 — full mode *(needs blessing)*

- [ ] Swipe on the status bar itself opens our panel; stock shade silenced.
      The code already does this — it just needs the permissions to exist.
- [ ] Zero-tap setup: pre-granted accesses in the build
      (`config_defaultListenerAccessPackages`, `config_defaultDndAccessPackages`,
      preset app-ops). New tablets come with the shade already alive.
- [ ] Warmth slider drives the real amber backlight (needs the setting key —
      README step 4; one line of code once known).

## v1 — polish that needs nothing

- [x] **Dark-mode variant of the panel itself** — *built:* the panel
      follows the system theme — day is ink on paper, night the same page
      inverted — and an open panel rebuilds itself the moment the theme
      flips (Dark pill or schedule). The mock mirrors it via the viewer's
      color scheme. (Open question 4 below still stands: confirm on real
      glass that inverted is what night wants.)
- [ ] **Landscape pass** — the DC-1 lives in both orientations; check the
      660dp sheet, maybe 6 pills in one row when wide. *(afternoon)*
- [ ] **Richer notification rows** — tap-to-expand long text, inline action
      buttons (reply / archive / mark done), group by app, show ongoing
      progress (downloads!) as a calm percentage line. *(weekend)*
- [ ] **Media seek bar** — a thin ink line under the transport buttons with
      elapsed/total time. *(afternoon)*
- [ ] **Make the heart work everywhere** — today the heart shows only when
      an app supports Android's standard rating API, and Spotify mostly
      doesn't; most players expose like/favorite as *custom actions* on
      their media session instead. Read `PlaybackState.getCustomActions()`
      and render them as extra ink buttons — this is also how "seek 15s"
      arrives for apps that publish it as a custom action (Audible).
      *(afternoon)*
- [ ] **Choose-your-own pills** — a "which six?" editor in shade setup
      (candidates: battery saver, flashlight-if-exists, cast, mute,
      screenshot). The user-facing half of "modifiable, editable,
      iterative". *(afternoon)*
- [ ] **Lock-screen behavior** — currently the strip simply refuses to open
      over the keyguard. Decide what a *calm* locked shade shows (clock +
      media controls only, no settings?) and build that variant.
      *(weekend; design decision first)*
- [ ] **Open/close feel menu** — instant vs 130 ms slide; "reduce motion"
      switch for purists. *(afternoon)*

## v2 — replace the hand-off surfaces *(needs blessing)*

The three places v1 still shows stock Material, in order of annoyance:

- [x] **Native Wi-Fi picker inside the shade** — *built (young):*
      long-press (or unblessed tap on) the Wi-Fi pill → networks
      strongest-first, current on top, tap a saved network to hop
      (`enableNetwork`, lights up with blessing; unknown/password networks
      hand off to the system sheet). Declared `neverForLocation` — the
      shade sees radios, never places. Toggle off in shade setup if flaky.
      A native password sheet can follow someday. *(shipped as labs;
      needs on-glass shakedown)*
- [x] **Native Bluetooth device list** — *built (young):* paired devices
      with connected/paired state and tap to connect/disconnect (profile
      reflection — needs blessing + `BLUETOOTH_PRIVILEGED`; hands off to
      settings until then), plus find-new-devices discovery and pairing,
      which work on any install. The pairing-code confirmation stays a
      system dialog (security). *(shipped as labs; needs on-glass
      shakedown)*
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

1. Which setting does the stock warmth slider write? *(platform team —
   unblocks v0.5 warmth + v3 scheduling)*
2. What should a locked shade show? *(design decision — unblocks v1
   lock-screen)*
3. Does the DC-1 have a vibration motor? (If yes: subtle haptic tick on
   pill toggle; if no: delete this line.)
4. Dark mode on a reflective LCD: is white-on-black actually preferred at
   night with the amber backlight, or is the Dark pill itself vestigial on
   this hardware? Real-device answer wanted before investing in v1's dark
   variant.
