---
name: daylight-preview
description: See and audit any web app the way a Daylight DC-1 tablet will actually show it — grayscale LivePaper simulation (day + amber night), touch-target/contrast/tiny-text/overflow/console audits at the DC-1's 10.5″ 4:3 viewport. Use after every visual change while daylight-ifying an app, before shelving a dish in the Daylight Computer Club, or whenever someone asks "how will this look on the Daylight / DC-1 / e-paper tablet?"
---

# daylight-preview — the DC-1 on your desk

You cannot judge a design for a monochrome reflective screen on a glowing
color monitor. This skill gives you a fast, repeatable stand-in: one command
renders any URL at the DC-1's geometry, saves what *you* see next to what
*the tablet owner* will see, and audits the failures that are invisible on
your monitor but glaring on paper.

It is the feedback loop for the other daylight-ify skills: **daylight-design**
and **daylight-port** tell you what to change; this tells you whether it
worked. Run it after every visual pass, not once at the end.

## Run it

```sh
npm i playwright          # once, any recent version
node scripts/dc1-preview.mjs http://localhost:8000/ --out ./dc1-preview
```

Useful flags: `--portrait` (tablet held upright), `--posterize` (quantize to
~12 grays — roughly what the panel resolves in practice), `--full` (full-page
shot), `--dpr <n>` (see "ground truth" below), `--strict` (exit 1 on failures,
for CI). If Chromium lives somewhere unusual: `DC1_CHROMIUM=/path/to/chromium`.

## What comes back

Three images in the output directory:

- `original.png` — the app as your monitor shows it (the lie).
- `dc1-day.png` — LivePaper simulation: luminance-mapped grayscale,
  shadow-crushing curve, compressed range (black ≈ dark gray, white ≈ cream
  paper). **This is the image to judge.** Open it and squint: if hierarchy,
  affordances, and state are still obvious, the design works.
- `dc1-night.png` — the amber backlight at night: everything washes warm and
  contrast drops further. Anything relying on subtle gray distinctions dies
  here first. If the app has a dark theme, judge it HERE (dark themes are
  designed for the backlight and legitimately look muddy in the day
  simulation) — and judge light themes in `dc1-day.png`. Each theme under
  its intended light.

And a report:

| Audit | Why it matters on the DC-1 |
|---|---|
| touch targets < 48×48 CSS px | fingers on a 10.5″ tablet, no mouse precision (inline prose links reported separately — WCAG-exempt but still fiddly) |
| effective contrast **after** the LivePaper curve | grays that pass WCAG on a monitor can fail on paper — e.g. `#767676` on white measures 4.5:1 lit, ~3.9:1 here |
| text < 16px | ~190 PPI reflective panel at arm's length |
| horizontal overflow / missing viewport meta | desktop-scaled pages are unreadable and un-tappable |
| console errors + failed requests | the smoke test — vibe-coded apps break quietly |

## The iteration loop

1. Serve the app locally, run the script.
2. Open `dc1-day.png`. Ask: can I tell what's tappable? Can I tell selected
   from unselected, enabled from disabled, without color? Does anything
   important live in the bottom quarter of the gray ramp (it's mud)?
3. Fix the worst thing (the report ranks the mechanical ones; your eyes rank
   the rest). Re-run. Compare images side by side.
4. Stop when `dc1-day.png` *and* `dc1-night.png` both read clean and the
   report is quiet. Then, if at all possible, look once on a real DC-1 —
   ambient light does things no simulation captures.

## Ground truth beats simulation

The curve is a tuned pessimist, not a measurement. Two facts to calibrate
against a real device when you can:

- **devicePixelRatio**: the script defaults to 1.25 (panel 1600×1200 ÷ 1.25 =
  1280×960 CSS px landscape). Open the club's calibration page
  (`https://daylightcomputer.club/calibrate.html`) once on a real DC-1 —
  it reads out the true value and shows gray-step patterns to count.
- **The gray floor**: the panel shows ~256 levels but eyes resolve roughly
  9–16 in ambient light, fewest in the dark end. `--posterize` approximates
  this; the real panel (and the calibration page) is the judge.
- Both land in `skills/daylight-facts.json` — the shared facts file every
  tool reads. Correct it there once; the whole pack recalibrates.

Simulation limits worth remembering: it can't show glare, paper texture,
ambient-light contrast swings, or touch latency; the contrast audit assumes
solid backgrounds (it ignores background images and gradients — but you
shouldn't be shipping gradients to this screen anyway; see daylight-design).
