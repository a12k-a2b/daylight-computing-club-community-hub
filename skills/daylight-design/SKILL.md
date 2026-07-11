---
name: daylight-design
description: Redesign any app's UI for the Daylight DC-1 — map its colors to a grayscale that actually reads on the LivePaper reflective screen, re-lay it out for a 10.5″ 4:3 tablet, make it touch/stylus/voice-first, and keep it calm — while preserving the app's own aesthetic and ethos. Use when someone says "make this look right on the Daylight / DC-1 / e-paper screen", "adapt this for grayscale", "redesign for the tablet", or "daylight-ify this" (design half).
---

# daylight-design — translate an app to paper, don't repaint it

The DC-1's screen is not a worse color screen; it's a different medium —
closer to print than to glass. Designs that assume glowing color fail here
in specific, predictable ways. This skill is four passes over an existing
app, each fixing one way the medium differs. Its sibling **daylight-preview**
is the feedback loop: run it after every pass, judge `dc1-day.png` and
`dc1-night.png` with your eyes.

**Rule zero — preserve the dish's soul.** You are translating to a new
medium, not applying club branding. Keep the app's typographic personality,
its layout rhythm, its voice, its playfulness or austerity. If it was warm,
it stays warm in grayscale. Every choice below bends to this.

**Rule one — check `references/design-system.md` first.** If it contains a
real design system (not the placeholder), its tokens and rules override the
defaults below.

Screen facts that justify everything here: [references/screen.md](references/screen.md).

## Pass 1 — color to grayscale (the mapping, not the desaturation)

Naive desaturation is the classic failure: red and green have similar
luminance, so "stop" and "go" become the same gray. Do the mapping
deliberately — full algorithm and worked example in
[references/grayscale-mapping.md](references/grayscale-mapping.md). The short version:

1. Inventory every color in the app **by role** (primary action, danger,
   success, selected, disabled, surface, borders, text tiers…). Then let
   `scripts/daylight-map.mjs` compute optimized gray assignments — it
   measures distinguishability *after* the panel's response curve and
   flags pairs that need a second channel. For pictures and sprites,
   `daylight-map.mjs image` decolorizes with the palette-aware algorithm.
2. Map roles onto a small gray ramp — default five tones:
   `#000` ink · `#555` mid · `#999` faint · `#ccc` hairline/fill · `#fff` paper.
   Two states a user must tell apart at a glance get grays ≥2 ramp steps
   apart — or a second channel.
3. Re-encode what hue used to say with channels the panel renders honestly:
   **inversion** (black↔white swap — the strongest signal on paper) for
   selected/active, **weight** (borders 2px+, bold text), **icons + words**
   for danger/success, **patterns** (hatching, dots, dashes) for chart
   series, **position and grouping** for everything else.
4. Bans, because the panel muddies them: gradients (band into mush), soft
   drop shadows (gray smears — depth comes from 2px borders, spacing, or
   hard-edged offset shadows), color-only links (underline them), dark-on-dark
   (the bottom quarter of the ramp reads as one tone), stacked translucency
   producing in-between grays, thin light-gray text on white.

## Pass 2 — layout for 10.5″ at 4:3

The tablet trap: a stretched phone layout looks like a kindergarten poster;
a crammed desktop layout is untappable. The DC-1's CSS viewport is roughly
1280×960 landscape / 960×1280 portrait — nearly square. Design for
"comfortable book", between phone and desktop density.

- **Spend the extra space on visibility, not size.** What a phone hides
  behind hamburgers, tabs, and modals, the tablet can just show: filters as
  a visible rail, actions as labeled buttons, state inline. Fewer
  navigational hops is the single biggest tablet win.
- Landscape: two panes (list + detail, canvas + tools, document + notes).
  Portrait: one column, generous margins — cap prose at 60–75 characters
  per line, don't let text run the full 960px.
- Support **both** orientations; reflow between the two layouts above.
- Reachability: held two-handed, thumbs live at the left and right edges;
  resting on a table, everything is equal. Put frequent actions near the
  vertical edges or bottom, never only in the top corners.
- Scroll vertically only. Let content visibly run past the fold (a cut-off
  card invites scrolling; a perfectly framed screen looks finished). No
  horizontal carousels — they fight the back gesture and hide inventory.
- Information density: closer to print than to mobile — tables, lists, and
  toolbars can be richer than a phone would dare, as long as every
  interactive element keeps its 48px (Pass 3).

## Pass 3 — fingers, pen, and voice

- Touch targets ≥48×48 CSS px with ≥8px gaps; whole rows/cards tappable,
  not just the tiny icon inside them. daylight-preview measures this.
- No hover anywhere: tooltips become visible captions or long-press help;
  hover-reveals become always-visible or behind an explicit "⋯".
- Every state change confirms itself visibly at arm's length: pressed =
  inverted, selected = inverted or check-marked, disabled = `#999` + no
  border. (Remember: no cursor changes, no hover glow to lean on.)
- **Typing on glass is the DC-1's worst input; voice is its best.** Rank
  inputs: tap a choice > slider/stepper > dictation > typing. Replace
  free-text where a set of chips would do. Keep text inputs big, few, and
  keyboard-smart (`inputmode`, `enterkeyhint`). For voice-driven features
  use `daylightVoice.listen()` — daylight-port's shim + shell bridge make
  one API work in both Chrome and the WebView shell (collisions.md §5).
- Stylus (Wacom EMR, hover + pressure): a bonus channel, never a
  requirement. If the app draws or annotates: pointer events, pressure →
  stroke width, hover → precise cursor, palm rejection by ignoring touch
  while a pen is active.

## Pass 4 — calm, paper-feel

- Motion: functional only — ≤200ms, opacity/transform, for orientation
  ("this panel came from the right"). No ambient animation, no bouncing
  mascots, no shimmer loaders. On paper, stillness reads as quality; also,
  fast full-screen motion smears slightly on LivePaper.
- Loading: text and structure ("Loading 34 notes…", skeleton = plain `#ccc`
  blocks), never an infinite spinner.
- Typography carries the design now that color is gone: a serif for reading
  surfaces renders beautifully at 190 PPI on paper (if the app's personality
  allows); body ≥18px; weights ≥400 (hairline fonts vanish); use size/weight
  contrast where the app used color contrast.
- Night is amber: after dark the backlight tints everything warm and drops
  contrast further. Check `dc1-night.png`; anything that survives on subtle
  grays by day dies at night.
- Respect attention: this device is bought by people escaping noise.
  Batch/digest any notifications, no badges shouting counts, no upsell
  toasts. Quiet by default.

## Done means

1. `daylight-preview` report clean (targets, effective contrast, text size,
   overflow, console).
2. Squint test on `dc1-day.png`: hierarchy, affordances, and current state
   are obvious; nothing important lives in the mud between `#000` and `#555`.
3. `dc1-night.png` still reads.
4. Both orientations lay out intentionally.
5. The original author would say "that's my app, at home on paper" — not
   "who repainted my app?".
