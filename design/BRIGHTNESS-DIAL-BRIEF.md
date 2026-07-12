# Design brief: the brightness dial that names the light

*For Claude Design. Paste this whole file as the prompt. A v1 of ideas 1–3
is already implemented in the shade (see "what exists today") — this brief
is for pushing the concept further, not starting from zero.*

## Context

You're designing for **Daylight Shade**, the pull-down quick-settings
panel on the Daylight DC-1 — a tablet with a monochrome *reflective* LCD
(a Live Paper display: it reads like paper in ambient light and has a
white + amber backlight for when the room is dim). Design language: pure
grayscale ink-on-paper, serif type, real 2–3px borders, no Material, no
gradients or shadows, glyphs drawn as simple geometry, ≥48dp touch
targets. Day is ink on paper; night is the same page inverted.

## The problem

People slam the brightness slider to 100% the way they do on phones —
but on a reflective screen the backlight is a *supplement*, not the
light source. Full blast means sore eyes ("it's too bright, it hurts"),
wasted battery, and missing the entire point of the display. The plain
Android slider gives zero guidance: it's an unlabeled number line, so
the only legible position on it is "max".

## The two ideas to design around

**1. The soft cap.** The dial's "100%" is deliberately not the panel's
true maximum — it tops out around 60% of hardware duty. You can't
fat-finger your way to glare. (True max stays reachable in Android's
full Settings, a deliberate walk away.) Design question: does the dial
admit this anywhere? A tick? Nothing? Is there an "and beyond" affordance
for the rare person who needs it, or is silence calmer?

**2. The legend — name the light, guide the hand.** As the thumb moves,
a small caption names where you are, so people place themselves by *word*
instead of by *amount*:

- position 0 — **pure reflective** — backlight off; the page is lit by
  your room, like paper.
- the low-middle — **paper-like** — a gentle assist; the page still
  feels ambient-lit. This is the home zone, where we want people to
  settle.
- the top — **screen-like** — the page now glows more than the room;
  named politely but pointedly (it's the "you're doing the phone thing
  again" zone).

The vocabulary is open. Candidates to play with: *moonless / candlelit /
lamplit / overcast / daylight* (these echo a planned presets row:
Daylight / Overcast / Candle / Moonless), *firelight*, *reading lamp*,
*glow*. Warmth will someday want sibling words on its own slider
(candlelight ↔ paper white). Keep it three-to-five words max on a dial —
a legend, not a thesaurus.

## What exists today (v1, on device)

A quiet italic serif caption floats above the thumb and updates live as
you drag: `pure reflective` → `paper-like` → `screen-like` (boundary at
~68% of dial travel). The dial itself is perceptual (logarithmic, like
Android's own) with the soft cap baked in. The mock in `mock.html` next
to this file mirrors it — drag the brightness slider.

## Design explorations wanted

1. **Caption treatment** — above the thumb (current) vs a fixed legend
   line above the track vs words at the track's ends vs inside the thumb.
   Does it show always, or only while dragging (and settle-fade after)?
2. **Zone marks on the track** — tiny ticks or a bracket under the
   "paper-like" span, so the home zone is visible before you touch it.
   Can the track itself hint (e.g. the line thins as light leaves it)?
3. **The cap's presence** — invisible? a notch at the true-max ghost
   position? a hairline gap after 100%?
4. **A presets alternative** — the same intent as one row of four named
   lights (Daylight / Overcast / Candle / Moonless) *replacing* both
   sliders for most people, with the sliders one tap deeper. Mock both
   and argue for one.
5. **Night variant** — all of the above inverted (paper type on ink),
   checked for legibility on a reflective panel at raw brightness 3–10,
   which is where this display actually lives after dark.

Constraints: everything must be drawable with simple canvas geometry
(rects, circles, lines, serif text) — no icon fonts, no images. The
sheet is 660dp wide; the slider row currently 72dp tall; captions are
12.5dp italic serif in the mid-gray ink.

Deliverable: variations of the sliders section of `mock.html` (it's
self-contained HTML/CSS/JS — the pills and sliders already interact), or
sketches in the same idiom, plus a one-paragraph recommendation.
