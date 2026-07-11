# The Daylight design system — placeholder

**Status: awaiting the real thing.** Anjan has a design system built for the
DC-1 (grayscale scheme, line weights, text sizes, transparency/blur/shadow
usage, examples of designs that work beautifully on the device). When it
lands here, its tokens and rules **override** the defaults in SKILL.md and
the sibling references.

Until then, the operative defaults are:

- the five-tone ramp in `screen.md`
- 2px real borders instead of soft shadows
- serif-friendly, ≥18px body, ≥400 weight
- inversion as the strongest state signal
- flat fills; hatching/patterns instead of gradients

## What belongs in this file when it arrives

1. **Tokens** — the gray ramp (with when-to-use for each step), border
   weights, radii, spacing scale, type scale + families + weights.
2. **Depth language** — the sanctioned way to layer: borders? hard offset
   shadows? measured translucency/blur (where they've been proven to read
   on-device)? With do/don't examples.
3. **Exemplars** — screenshots (ideally photos of the physical screen) of
   designs that worked, annotated with *why*: how they used weight, texture,
   spacing, inversion.
4. **Component recipes** — buttons (primary/secondary/destructive), inputs,
   selected states, dialogs, lists, toolbars, empty states.
5. **The line** between club-house style and app-author freedom — what the
   system mandates (legibility physics) vs. merely suggests (taste).
