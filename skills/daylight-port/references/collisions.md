# Where web and Android collide on the DC-1 — and the fix for each

Work through every section. Each one is a place where a browser-first app
feels janky on an Android tablet. "Shell" = the WebView shell APK;
"PWA" = running in Chrome (pinned standalone or tab). Most fixes apply to both.

## 1. Viewport & scaling

- Always:
  ```html
  <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover, interactive-widget=resizes-content">
  ```
  `interactive-widget=resizes-content` makes the soft keyboard shrink the
  layout instead of covering it (Chrome 108+; Android 13's Chrome has it).
- Don't use `user-scalable=no` — it's an accessibility loss. Kill double-tap
  zoom the right way: `touch-action: manipulation` on interactive elements
  (or `html`), which also removes the tap delay.
- `html { -webkit-text-size-adjust: 100%; }` stops Chrome's font boosting
  from inflating random paragraphs.
- **Shell**: Android's system font-size setting multiplies WebView text via
  `textZoom`. The template pins `textZoom = 100` so layouts don't shatter;
  in exchange, honor the user by making your own text comfortably big
  (daylight-design's job) or wire textZoom to an in-app setting.
- `100vh` lies on Android (URL bar, keyboard). Use `100dvh` / `100svh`.
  Test both orientations — 4:3 means landscape is nearly square; a layout
  that assumes "phone tall" breaks immediately.

## 2. Touch feel

The difference between "web page" and "app" is mostly here.

- `-webkit-tap-highlight-color: transparent;` then add your own visible
  `:active` state (e.g. invert, or 2px inset border — it must survive
  grayscale). There is no hover on a tablet: **anything revealed on hover is
  unreachable**. Move it to tap, long-press, or make it always visible.
- Text selection: on long-press Android pops selection handles + a floating
  toolbar. Wanted in *content* (articles, notes), absurd in *chrome*
  (buttons, labels, nav). Set `user-select: none` on UI; leave content
  selectable. Add `draggable="false"` to images used as UI.
- In Chrome, long-press on links/images opens a context menu (open in new
  tab, download image…). You can't fully suppress it — avoid making large
  images/links the primary touch surface, or use `pointer-events` layering.
  In the shell, WebView shows no context menu by default: one jank gone.
- Buttons respond to `click` — fine (no 300ms delay once viewport +
  `touch-action` are right). Only reach for pointer events for gestures.
- Gestures: keep to tap, long-press, swipe, two-finger where obvious. Edge
  swipes conflict with Android back gesture — don't put horizontal swipe
  actions at the extreme screen edges.
- Stylus (Wacom EMR): arrives as `pointerType: "pen"` with real `pressure`,
  plus hover `pointermove` events *before* touching. Drawing/handwriting
  apps: use pointer events, `touch-action: none` on the canvas only,
  and treat pen differently from finger (palm rejection: while a pen is
  active, ignore `touch` pointers).

## 3. Scroll behavior

- `overscroll-behavior: none` on `html, body` kills the two big tells:
  the glow/stretch at scroll ends, and Chrome's pull-to-refresh (which
  destroys state in SPAs).
- Keep native momentum scrolling; never reimplement scrolling in JS.
- Scrollbars barely show on Android — don't rely on them to signal "there's
  more"; let content visibly continue past the fold (see daylight-design).
- Passive listeners for `touchstart`/`touchmove` (or Chrome punishes you).

## 4. The keyboard (avoid it, then tame it)

- The on-screen keyboard eats half of a 4:3 screen and typing on glass is
  the DC-1's weakest input. First ask: can this input be a choice chip,
  a slider, a date wheel, or voice? (daylight-design covers the redesign.)
- Inputs you keep: `inputmode` (`numeric`, `decimal`, `email`, `url`,
  `search`) picks the right keyboard; `enterkeyhint` (`done`, `go`,
  `search`, `send`) fixes the enter key; `autocomplete` saves retyping.
- With `interactive-widget=resizes-content` (§1) + `windowSoftInputMode=
  "adjustResize"` (shell manifest, already set) the focused field stays
  visible; still call `el.scrollIntoView({block:'center'})` on focus for
  fields low on the page.
- Dismiss: blur the input after submit so the keyboard drops. Android's
  back-to-dismiss-keyboard is free.

## 5. Voice

- Dictation into any focused text field works everywhere via the keyboard's
  mic key. Zero code — this alone makes forms bearable.
- The Web Speech API (`webkitSpeechRecognition`) works in **Chrome only —
  it is absent in WebView**. The shell template closes this gap: it injects
  a `DaylightVoice` Java bridge (system speech dialog via
  `RecognizerIntent`, which owns the mic permission), and
  `assets-extras/daylight-voice.js` wraps both worlds in one API:
  ```js
  if (daylightVoice.available()) {
    const text = await daylightVoice.listen({ lang: 'en-US' });
  }
  ```
  Copy `daylight-voice.js` into `assets/`, include it, and voice features
  work identically as a PWA and as a shell APK. If `available()` is false,
  hide the mic button — the keyboard's dictation key still works.
- `speechSynthesis` (text-to-speech) works in both Chrome and WebView.

## 6. Offline & storage — the DC-1 is Wi-Fi-only and leaves the house

- The app must **launch and be useful with no network**. Airplane-mode
  relaunch is a release gate, not a nice-to-have.
- PWA: service worker, cache-first for the shell, stale-while-revalidate for
  data; version the cache and clean up old ones in `activate`.
- Shell: assets are bundled, so the app itself is offline by construction —
  but hunt down CDN references (`fonts.googleapis.com`, unpkg, analytics…)
  and inline or bundle them. A bundled app that fetches its framework from a
  CDN is offline theater. `grep -r "https://" assets/` before shipping.
- User data: IndexedDB or localStorage, plus `navigator.storage.persist()`.
  Sync when Wi-Fi returns (`navigator.onLine` + `online` event is enough;
  Background Sync works in Chrome, not in WebView).

## 7. Files, downloads, media

- **Shell**: a bare WebView silently ignores `<input type=file>`, downloads,
  and `target="_blank"`. The template wires all three (file chooser →
  system picker; `data:`/`http(s)` downloads → real files in Downloads;
  external links → Chrome). `blob:` downloads need a tiny JS bridge — the
  template README shows the pattern. Test every export/import button.
- PWA: downloads land in the Downloads app — tell the user in-UI ("Saved to
  Downloads"), because Chrome's snackbar is easy to miss.
- Audio/video: no autoplay-with-sound before a user gesture (both
  containers; the shell relaxes it, but keep the gesture for politeness).
  No camera exists — hide/replace any camera or QR affordance.
- Reading/reference apps: request a wake lock
  (`navigator.wakeLock.request('screen')`) — on a reflective screen in
  daylight the backlight is off, so screen-on is nearly free, and nothing is
  jankier than the screen sleeping mid-recipe.

## 8. Back button — the #1 "feels broken" report

Android back must do what the user means: step back inside the app, then
exit from the root. 

- Shell: template overrides back → `webView.goBack()` when possible.
- Both: make in-app navigation real history (`history.pushState` per screen,
  including modals/drawers — push a state when opening, close it on
  `popstate`). A modal that ignores back and an app that exits instead of
  closing the modal are the same bug from two directions.
- Don't trap the user: back from the root should exit (Android norm),
  optionally with one "back again to exit" toast if data could be lost.

## 9. Performance on a Helio G99

- Budget like it's 2019 midrange, because it is: ship small (no 400 KB
  framework for a list app), avoid layout thrash and re-render storms,
  `transform`/`opacity` for any motion (rare anyway — see daylight-design),
  `content-visibility: auto` for long lists, compress images to the size
  actually displayed (grayscale panel: consider shipping grayscale images —
  smaller, and you control the mapping instead of the panel).
- The screen refreshes at 60 Hz but fast full-screen motion smears slightly
  on LivePaper — another reason scrolling long distances beats animated
  transitions.

## 10. Rotation & app identity

- Support both orientations (a 4:3 tablet is used both ways; stylus users
  favor landscape). State must survive rotation: shell template handles
  config changes without recreating the WebView; PWAs keep state in JS but
  verify nothing resets on `resize`.
- Name & icon: short launcher name (≤12 chars); icons must read in
  grayscale on the launcher — bold silhouette, high contrast, no meaning
  carried by color. Provide 192/512 + maskable for PWA; the shell needs one
  `res/drawable/icon.png` (template slot).
- `theme_color`/status bar: grayscale (`#ffffff` or `#000000`), matching the
  app's paper. No colored chrome on this device.
