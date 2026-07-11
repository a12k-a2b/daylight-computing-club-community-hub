# Tracing Paper

A transparent notepad for the Daylight DC-1 — press the orange top button
and a sheet of glass slides over whatever you're reading; write on it with
the pen; press again and it's gone, your place kept.

It's a from-scratch, open-source homage to [Glassnote]
(https://glassnote.mthompson.io/), Matt Thompson's lovely DC-1 app. None of
Glassnote's code or art is in here — the club wanted the same *idea* on the
shelf as a dish we can read, patch, and sign ourselves, so this replicates
the behavior described on its public pages and adds a few club touches. If
you can run the real thing, go support it.

## What it does

- **Top button** (KEYCODE_F12 on the DC-1) toggles a full-screen glass pad
  over any app. Hold it while the pad is up to **snap a screenshot** of your
  notes over the page (saved to `Pictures/Tracing Paper`).
- **Writes with the pen**, pressure-sensitive, and the pen's eraser end
  erases. Optional pen-only mode ignores your resting palm.
- **HILITE** — a highlighter in light gray (dark enough to read on the
  grayscale panel) with a thin black ring so highlights stand out when you
  scan a page. It sits under your ink, so pen lines stay crisp.
- **SNIP** — drag a box over whatever's on screen; the clipping is pasted
  onto the glass right where it was cut from. Snips are undoable, saved
  with the page, and come along into PDF exports.
- **Notebooks**: as many as you like, each named and each with its own
  paper — blank, lined, dots, or school (ruled with a margin). Pages flip,
  add, wipe, or tear out; everything autosaves to app-private storage.
- **GLASS slider** — the pad's opacity, 0 to 100%, right in the toolbar:
  clear glass at one end, opaque paper at the other.
- **PEEK** makes the glass untouchable so you can scroll the app underneath
  while your notes stay visible; a WRITE pill (or the top button) brings the
  ink back under your pen.
- **PDF export** of every notebook (vector ink, templates and snips
  included) to `Download/Tracing Paper`.
- **Quick-settings tile** as a second way in, and a **re-learn flow** in the
  app in case a SolOS update ever remaps the hardware buttons: tap
  "Re-learn", press any hardware key, done.
- The **volume keys are deliberately untouched** — Daylight Keys owns those.
  Tracing Paper only ever consumes its one toggle button.

## Low-latency ink

The pen path uses the same wet/dry-ink architecture as Android's modern ink
stack (androidx.ink / front-buffered rendering, as used by tldraw and
Google's own note tools), implemented with plain platform APIs so the build
stays Gradle-free:

- **Unbuffered input** — `requestUnbufferedDispatch` on pen-down, so stylus
  events arrive at digitizer rate instead of batched to vsync.
- **Wet layer** — the in-progress stroke is drawn to a hardware
  `SurfaceView` canvas (`lockHardwareCanvas`) the moment each event arrives,
  skipping the UI-thread frame wait.
- **Prediction** — a ~20 ms tail extrapolated from pen velocity (capped so
  flicks don't overshoot), redrawn every event, replaced by truth as it
  arrives.
- **Dry on pen-up** — the finished stroke is committed to the ordinary
  bitmap layer and the wet surface is wiped; eraser strokes stay on the dry
  path since erasing has to reveal what's underneath. If the surface isn't
  available the pad silently falls back to the plain path.

## How the buttons are heard

An `AccessibilityService` with `flagRequestFilterKeyEvents` sees hardware
key presses (the DC-1 top button arrives as `KEYCODE_F12` — the same value
the club's dc1-keys listens for), and the same service hosts the overlay
window (`TYPE_ACCESSIBILITY_OVERLAY`) and takes the screenshots. As a
belt-and-braces fallback the service also listens for SolOS's own button
broadcasts (`com.daylightcomputer.solosserver.ACTION_BUTTON_SINGLE_PRESS`
/ `..._LONG_PRESS`), debounced so a press never lands twice. Because that
receiver must be exported for SolOS to reach it, any local app could spoof
those broadcasts — so the broadcast path is only ever allowed to toggle the
pad; screenshots can only be triggered by the un-spoofable hardware-key
path or the on-glass SNAP button. The service never reads screen content.

## Building

Plain tools, no Gradle: `./build.sh` (wants `ANDROID_SDK` with
build-tools 35 + platform android-33, and the club keystore — see the
club repo's `signing/`). Output: `build/tracing-paper.apk`, signed with
the club key.
