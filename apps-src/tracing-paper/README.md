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
- **Pages**: flip back and forth, add pages, wipe or tear out a page.
  Everything autosaves to app-private storage and is still there next time.
- **FROST** cycles the glass: clear → frosted → opaque paper.
- **PEEK** makes the glass untouchable so you can scroll the app underneath
  while your notes stay visible; a WRITE pill (or the top button) brings the
  ink back under your pen.
- **PDF export** of all pages (vector ink) to `Download/Tracing Paper`.
- **Volume-down flips the backlight** while the pad is open (opt-in; asks
  for "Modify system settings"). Off by default so it won't fight
  Daylight Keys.
- **Quick-settings tile** as a second way in, and a **re-learn flow** in the
  app in case a SolOS update ever remaps the hardware buttons: tap
  "Re-learn", press any hardware key, done.

## How the buttons are heard

An `AccessibilityService` with `flagRequestFilterKeyEvents` sees hardware
key presses (the DC-1 top button arrives as `KEYCODE_F12` — the same value
the club's dc1-keys listens for), and the same service hosts the overlay
window (`TYPE_ACCESSIBILITY_OVERLAY`) and takes the screenshots. As a
belt-and-braces fallback the service also listens for SolOS's own button
broadcasts (`com.daylightcomputer.solosserver.ACTION_BUTTON_SINGLE_PRESS`
/ `..._LONG_PRESS`), debounced so a press never lands twice. The service
never reads screen content.

## Building

Plain tools, no Gradle: `./build.sh` (wants `ANDROID_SDK` with
build-tools 34 + platform android-33, and the club keystore — see the
club repo's `signing/`). Output: `build/tracing-paper.apk`, signed with
the club key.
