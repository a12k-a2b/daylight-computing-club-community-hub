# Daylight Shade — full design-pass brief

*For Claude Design. Everything described below is real, running on a
DC-1 today; the screenshots in this folder are from that device, and
mock.html is a live, interactive 1:1 of the main sheet. Your job is
design passes and variations, not invention from zero — and not
relitigating the settled decisions listed at the end.*

## Context

Daylight Shade is the pull-down quick-settings panel on the Daylight
DC-1 — a 10.5" tablet with a monochrome *reflective* LCD (Live Paper:
reads like paper in ambient light; a white + amber backlight assists in
dim rooms). Customers are iPhone/iPad/Mac people. The shade's job is to
remove Android's ugliness and intimidation, and to *implicitly teach*
proper use of a reflective device — through structure, copy, and small
affordances.

Design language: pure grayscale ink-on-paper. Serif type. Real 2–3px
borders, no shadows, no gradients, no Material, no icon fonts — every
glyph is simple canvas geometry (rects, circles, lines, arcs). Touch
targets ≥48dp. The sheet is 660dp wide, centered. Day is ink on paper;
night is the same page inverted. Copy rules live in VOICE.md (in this
folder) — captions italic serif 12.5dp mid-gray, section labels
letterspaced small caps, the interpunct (·) joins a thing to its state.

## The panel, top to bottom (see 70-batch-panel.png)

1. **Header** — big serif clock, date line, battery *in words*
   ("plenty", "low · 22%", "full by 9:40" while charging — numbers only
   when they change a decision).
2. **The light dials.** Brightness runs a perceptual dial with a soft
   cap ("100%" ≈ 60% of hardware max — you cannot fat-finger your way to
   glare) and a live italic caption above the thumb naming where you
   are: `pure reflective` → `candlelit` → `paper-like` → `screen-like`
   (see 63-candlelit.png, 51-dial-slammed.png). Zone boundaries are
   user-tunable in shade setup (64-two-knobs.png). The warmth slider
   sits below, currently disabled on sideloads with an honest hint
   ("the amber backlight unlocks with the Sol:OS blessing").
3. **Three human pairs** — the six controls as captioned two-pill rows:
   CONNECTION (Wi-Fi, Bluetooth) / ATTENTION (Do Not Disturb, Airplane
   Mode) / PAGE (Dark Mode, Rotation Lock). Names are Apple's exact
   vocabulary on purpose. Active pill = inverted (ink fill, paper type).
   Tap flips; long-press opens the deeper page (Wi-Fi/Bluetooth pickers
   in-shade — 24-wifi-honest.png, 27-bt-picker.png).
4. **Media card** (only while something plays — see mock.html): app
   name small-caps, title/artist serif, transport row of bordered
   square buttons, heart, ±15s.
5. **Notifications** — count header ("notifications · 3" / "all
   quiet"), rows with a 4px ink bar, ✕ to dismiss, clear all.
6. **"looking for a setting?"** — a full-width row opening the search
   page (73-search-focus.png, 76-searchfix-typed.png): one box ("ask in
   your own words…"), a hold-to-speak mic beside it, result rows that
   read "PIN & screen lock · in Security — tap to open". Works fully
   offline.
7. **Footer** — `essentials` (an in-shade page: text size / screen
   sleep / sound as tap-to-cycle ladders; alarms, wallpaper, date &
   time, software update as plain hand-offs; shade setup) ·
   `everything else…` (stock Settings, reframed as the attic).
8. **First-pull notes** (once, ever): three italic lines — "start with
   the room…", "tap changes it · hold opens it — like Control Center",
   "tap outside or press back to leave" — each vanishing when the
   person does the thing it names.

Night face: 31-dark-mode.png (note: shot before the pairs layout — use
it for palette, not layout). Stock Android shade for contrast:
16-stock-qs.png — this is what we're replacing.

## Design passes wanted, per surface

1. **The dial** — caption treatment (above thumb vs fixed legend line
   vs words at track ends), zone ticks/bracket marking the calm range
   before you touch, the cap's presence (invisible? ghost notch?), and
   vocabulary refinement (candlelit/paper-like/screen-like are placed;
   argue only if you have better words).
2. **The pairs** — caption placement (above each pair vs a hairline
   left rail vs inside the row), pair separation, better words than
   "attention"/"page" if they exist (concept fixed: how the tablet
   reaches things / what reaches you / how reading looks), landscape
   (three pairs across?).
3. **The search page** — the empty state (currently one "try …" row);
   the result rows as *doors* (should a hand-off door look different
   from an in-shade door?); the mic button and its `listening…` state;
   how the page sits above the keyboard; what a *spoken* query looks
   like arriving live in the box.
4. **Essentials** — the tap-to-cycle ladder affordance (how does a row
   say "tapping steps through sizes" without an icon font?); visual
   distinction between live rows, info-only rows (Storage), and
   hand-off rows; whether "· medium — tap for the next size" reads or
   needs rethinking.
5. **Header** — is "fully charged" / "plenty" the right register next
   to a 40dp clock? Should charging get any quiet mark?
6. **Rhythm pass across the whole sheet** — it has grown: dials, pairs,
   media, notifications, search, footer. Audit vertical rhythm, the 2px
   section rules, whitespace hierarchy, where the eye rests. Propose
   one coherent spacing system.
7. **Night variant of everything new** — pairs, search, essentials,
   first-pull notes — checked for legibility at raw brightness 3–10,
   where this display actually lives after dark.
8. **Landscape pass** — 660dp sheet on a 1600×1200 canvas.

## Variation menu (do a few full-sheet versions, not only tweaks)

- **V1 "as built, tuned"** — keep the structure, perfect the rhythm.
- **V2 "the quiet rail"** — pair captions and section labels move to a
  thin left rail; the content column gets simpler.
- **V3 "one glance"** — denser: three pairs in one band, media and
  notifications tighter; for people who pull, glance, leave.
- **V4 "the reading room"** — maximal calm: search and essentials
  collapse into a single ask-row; notifications collapse to "3
  waiting — tap to read"; everything else breathes.
- **V5 "night first"** — design the inverted face as the primary and
  derive day from it (the DC-1 is heavily used at night).

## Settled — do not relitigate

Apple vocabulary on familiar concepts (Do Not Disturb, Rotation Lock,
Airplane Mode, Dark Mode); invented words only for the light. Presets
do NOT replace the dials (founder-decided; a presets row may someday
sit *beneath* them as a garnish). No numeric percentages in user-facing
copy except when action matters. Six controls, three pairs. No second
page of tiles. No timers/automation. The no-INTERNET property is
untouchable.

## Constraints & deliverables

Everything must be drawable with simple canvas geometry + serif text.
Grayscale only (the panel itself is pure ink/paper even though your
monitor isn't). ≥48dp targets. Start from mock.html — it is
self-contained HTML/CSS/JS with working pills, sliders, and captions;
produce variations as edited copies of it (one file per variation), so
they stay interactive and can be ported straight into PanelView.java.
For each variation: one paragraph on what it believes and why. End with
a recommendation: which variation (or hybrid) should become v1.1.
