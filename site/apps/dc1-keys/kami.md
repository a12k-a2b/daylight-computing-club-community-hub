# Kami — Daylight Keys

## Dear friend

This one is for reading in bed without waking anyone. The DC-1's volume
keys become brightness keys, so you can dim the light one notch at a time
without leaving your page — and when even the dimmest notch is too much,
you tap the little sun and the backlight goes out entirely, leaving just
paper. It was made for Melissa first. The snip tool and the dog-ear
corner came along later, the way a pocketknife grows tools.

## Why it's this way

- **Volume Up + Down together toggles the mode** — a chord, not a single
  key, so ordinary volume presses keep doing what your thumbs expect.
  You only enter brightness-land on purpose.
- **Single-unit steps at the dim end** — the DC-1's backlight is 8-bit
  and cliff-y at the very bottom (its lowest rung is a dead one; the
  jump from "off" to "first light" is the harshest step on the ladder).
  Fine steps down there are the whole point of the app; big steps up
  high would just be tedious to walk.
- **Tap-the-sun for backlight off** — software can't fix the bottom of
  the hardware ladder, so the app embraces it: the step below "dimmest"
  is honestly "off", one tap, reflective paper only.
- **The dog-ear lives in the top-left corner** — a folded page corner
  you peel to glimpse the time, your pomodoro, and your next event. A
  dog-ear because that's what it is: a reader's mark, not a dashboard.
- **It's an accessibility service** — that's the only way Android lets
  an app listen to volume keys everywhere. It listens for exactly two
  keys and nothing else; the inspectors read that straight from the file.

## Paths not taken

- **A brightness slider on screen** — rejected: the whole point is not
  leaving your page. Physical keys you can find in the dark beat any
  touch surface.
- **Fixing the dim-end cliff in software** — tried and root-caused: the
  cliff is in the hardware's bottom rung (min-on duty is a dead step),
  so the app works *with* it (fine steps + honest off) instead of
  pretending it isn't there.
- **The dog-ear as v1 shipped quietly** — its feel (fold, peel, what it
  shows) is still settling and waits on the cook's own hands; it was
  kept small on purpose rather than grown into a launcher.
- *(Only the cook remembers: whether other chord gestures were tried
  before Vol Up + Vol Down won.)*

## To cook it again

An Android accessibility service (no Gradle — plain aapt2 + javac + d8;
package `com.anjan.dc1keys`) that filters volume-key events everywhere.
Vol Up + Vol Down together toggles between volume mode and brightness
mode, with a quiet notification showing which mode you're in. In
brightness mode, single presses change screen brightness via
WRITE_SETTINGS — single-unit steps at the dim end, coarser above — and a
tap on the sun icon turns the backlight fully off (brightness restores
on the next key press). Long-press the quick-action to get a
drag-a-rectangle screenshot snip. A small always-on-top dog-ear overlay
in the top-left peels open to show the time, a pomodoro, and the next
calendar event (hence the calendar and notification permissions; the
microphone permission belongs to the snip tool's voice-note option).
Everything grayscale, 2px borders, made for a monochrome reflective
screen.

## Lineage

- cooked by Anjan (with Claude), 2026-07 — for Melissa, so she can read
  at night
- christened *Daylight Keys*, 2026-07-11 — it had worn three names
  ("Key Mode Toggle" to the installer, "Volume/Brightness Key Toggle"
  to Settings, "dc1-keys" to the shelf); the night the Clubhouse first
  one-tapped it onto real glass, every surface learned its one name
- this kami first written 2026-07-11 by the desktop kitchen, from the
  project's own records — Anjan may warm the wording anytime
