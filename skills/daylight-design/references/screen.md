# The DC-1 screen, for people who design for it

## Hardware facts

| Property | Value |
|---|---|
| Panel | 10.5″ "LivePaper" — monochrome **reflective LCD** (not e-ink) |
| Resolution | 1600×1200, 4:3, ~190 PPI |
| Refresh | 60 Hz, no e-ink-style full-screen flashing; slight smear on fast motion |
| Gray depth | 256 levels driven; roughly **9–16 distinguishable in practice**, fewest in the dark end |
| Light | Reflective: lit by the room/sun. Backlight is **amber** (blue-free) for night |
| OS / browser | Android 13 (Sol:OS); Chrome + Android WebView |
| CSS viewport | ≈1280×960 landscape at devicePixelRatio 1.25 (defaults; measure `window.devicePixelRatio` on a real device and correct this file) |

## What the physics means for design

- **White is paper, not light.** Peak "white" is the panel reflecting the
  room. In sunlight it's glorious; in a dim room everything is dimmer and
  flatter. You cannot buy contrast with brightness — only with value
  distance, weight, and size.
- **Black is dark gray.** The panel's darkest tone is well above true black,
  and the darkest quarter of the ramp compresses: `#000`, `#222`, `#333` are
  nearly indistinguishable. Never place dark-on-dark; treat `#000`–`#444`
  as one "ink" tone you use *against light grounds only*.
- **Mid-grays are honest, use few of them.** Between ink and paper you have
  maybe three reliably distinct steps at arm's length: call them `#555`,
  `#999`, `#ccc`. A UI needing six distinguishable grays has already failed.
- **Edges beat areas.** A crisp 2px line reads better than a filled
  low-contrast region. Hence the house instinct: real borders instead of
  shadows, outlines instead of tinted fills.
- **Gradients band.** A smooth ramp across few distinguishable levels turns
  into visible steps of mush. Flat fills, hatching, or dot patterns instead.
- **Amber nights.** With the backlight on, everything multiplies toward
  amber and effective contrast drops again. A palette that only just works
  by day fails at night — check both simulations in daylight-preview.
- **60 Hz but paper-calm.** Unlike e-ink, animation is *possible* — smooth
  scrolling feels native. Fast full-screen transitions smear slightly and
  glowing-screen tricks (pulsing, shimmer) look wrong on paper anyway.
  Motion is a seasoning here, not a language.
- **190 PPI at arm's length.** Text renders like good print. Serifs and
  humanist faces do well; hairline weights and sub-16px text do not. Body
  18–22px, line-height ≈1.5, prose measure 60–75ch.
- **Glare and angle.** It's an LCD under glass outdoors: high-contrast
  layouts survive reflections; low-contrast decoration disappears. Assume
  the worst light you'd read a paperback in.

## Quick reference — the default ramp

```
#000  ink        text, borders, icons, filled (inverted) actions
#555  mid        secondary text, de-emphasized icons
#999  faint      disabled, placeholders, tertiary marks — never body text
#ccc  hairline   dividers on white, skeleton blocks, subtle fills
#fff  paper      background
```

Contrast rules of thumb on this panel: body text = ink on paper only;
`#555` on `#fff` is fine for secondary; `#999` is decoration, not reading;
anything on `#ccc` fills must be `#000`.
