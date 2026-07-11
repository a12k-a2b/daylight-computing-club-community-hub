# The Daylight / Sol:OS design system — distilled

**Status: real.** Anjan's full design system lives in his claude.ai/design
project **"Design System"** — tokens, four theme modes, a complete React
component library (core, forms, feedback, navigation, media, surfaces,
Sol:OS widgets), a ~1,101-glyph icon set, guidelines pages, brand assets,
and its own `SKILL.md` + `claude-code-prompt.md` for vendoring into an app
repo as `./design-system`. When working with the full system, follow its
own instructions. This file distills what every daylight-ified dish needs,
and **overrides the generic defaults in SKILL.md and the sibling references.**

## The Sol:OS gray scale (use this, not the generic ramp)

Light (Day) mode — 0 = paper, 1000 = ink:

| Token | Value | Role |
|---|---|---|
| `--os-0` | `#FFFFFF` | background / paper |
| `--os-50` | `#F7F7F7` | surface / panels |
| `--os-100` | `rgba(0,0,0,0.08)` | hairline border |
| `--os-150` | `#F5F5F5` | recessed background |
| `--os-200` | `#CCCCCC` | disabled |
| `--os-300` | `#858585` | low emphasis / tertiary text |
| `--os-400` | `#535353` | secondary ink, unselected |
| `--os-900` | `#1A1A1A` | **primary ink (text)** — not pure black |
| `--os-1000` | `#000000` | max ink |

Corrections to my earlier generic defaults: body ink is `#1A1A1A` (900),
secondary is `#535353`, tertiary `#858585`, disabled `#CCCCCC`. Measured
translucency IS part of the system in Day mode — hover `rgba(0,0,0,.05)`,
scrim `rgba(0,0,0,.45)`, shadow color `rgba(0,0,0,.18)` — so soft shadows
and scrims are allowed *at these calibrated values*; don't invent stronger
ones. In High-Contrast modes, elevation switches to solid rings
(`0 0 0 1.5–2.5px #000`) — borders replace shadows entirely.

## Brand color → gray: the calibrated precedent

The brand palette (Solis Iter — the sun's path) and its **hand-calibrated
on-device grays** ("never naively desaturated" — the system's own words):

| Brand | Warm value | On-screen gray |
|---|---|---|
| Moonlight White | `#FAF4F2` | `#FFFFFF` |
| Midnight Black | `#17190F` | `#000000` |
| Morning Yellow | `#FFC70D` | `#CECECE` |
| Hardware Amber | `#FF9D00` | `#9D9D9E` |
| Sunset Orange | `#FC6900` | `#6C6C6D` |

This is the house's own proof of role-based mapping: three warm hues of
similar luminance spread across three well-separated grays. `daylight-map`
should reproduce mappings of this quality; when it disagrees with a
hand-calibrated value here, the hand wins.

## Four theme modes — night is a THEME, not a filter

1. **Day** (default): paper ground, ink text, tokens above.
2. **Night**: **black ground (`#000` base, `#1A1A1A` surfaces), white
   text, hardware-amber accents with a soft glow.** This is the important
   design correction: at night Sol:OS apps flip to a dark theme lit by the
   amber backlight — they don't just show the day UI under amber light.
   A well-ported dish should offer/respect a dark mode
   (`prefers-color-scheme` or Sol:OS's `data-theme="dark"`), mapping its
   surfaces to the Night tokens. `dc1-night.png` in daylight-preview
   remains the check for how a *light* theme survives when the user stays
   in Day mode after dark.
3. **High-Contrast light** (`data-theme="hc"`): pure white/black, solid
   black hairlines, no mid-gray text (secondary/tertiary collapse to ink),
   borders carry all elevation. For full sun and accessibility (AAA).
4. **High-Contrast dark** (`hc-dark`): the same, inverted.

Dishes don't have to ship all four — but their CSS should be one variable
layer away from them (use custom properties for every color; never
hard-code grays in components).

## Typography

| Family | Role |
|---|---|
| ABC Arizona Sans | primary UI text — body, labels, buttons |
| ABC Arizona Flare | titles & display (flared terminals, poetic) |
| ABC ROM | numerals, longer reading copy |
| ABC ROM Mono | metadata, code, tiny uppercase eyebrows |
| PP Editorial New | editorial serif display |

Scale: display 65/56/48/40px → titles 36/28/24 → body 20/16/15 → meta 14 /
label 12. Tight optical tracking (display ≈ −0.05em, body −0.02em),
line-height 1.5 for body. **Sentence case everywhere; ALL-CAPS never for
headings** (only the tiny mono eyebrow, sparingly). Weights 100–700
available; body sits at 400–500.

**Font licensing**: ABC Arizona/ROM (Dinamo) and PP Editorial New are
commercial faces licensed to Daylight — do **not** embed them in community
dishes. The token stacks already carry graceful fallbacks (system sans,
Georgia); community apps use the fallbacks unless they hold a license.
Note the DS body size (16px) trusts these specific faces at 190 PPI —
with fallback fonts keep the pack's ≥18px guidance for reading surfaces.

## Voice

Hopeful, grounded, sentence case, no hype, **no emoji in UI**. Embrace
"epaper". Say less; mean it.

## Components & icons

The full system has built components for nearly everything (buttons, chips,
inputs, dialogs, toasts, tabs, dock, status bar, cards, list items, media
player, Sol:OS widgets). Working rules from the system's own prompt:
treat it as source of truth; recreate components in the app's own framework
rather than pasting prototype HTML; import icons from its set instead of
hand-drawing SVGs; when you build a new reusable component worth keeping,
flag it for promotion back into the system.

## Getting the full system

- claude.ai/design project "Design System" (Anjan's account) — sync with
  the `/design-sync` skill or DesignSync tool, or vendor a copy into the
  app repo as `./design-system` and follow its `claude-code-prompt.md`.
- Key files: `tokens/*.css` (colors, typography, spacing, fonts),
  `guidelines/*.html` (grayscale, night, high-contrast, elevation, type),
  `components/**`, `ui_kits/sol-os/`, `templates/sol-os-screen/`.

## Validated against the pack's simulator (2026-07-11)

The hand-calibrated values above were run through daylight-preview's
LivePaper model, numerically and visually. Everything agrees:

- The three brand grays sit 37–41 effective levels apart — clearly three
  steps in the day simulation.
- Text tiers land exactly on their designed roles: `#1A1A1A` on paper
  9.2:1 (body), `#535353` 5.6:1 (body-capable secondary), `#858585` 3.3:1
  (large/low-emphasis only — the model draws the same line the designers
  drew). Pairs designed as near-equivalents (max ink vs primary ink,
  surface vs paper) are the only ones that collapse.
- The Night theme is legible and warm under the night curve, and turns to
  mush under the day curve — as it should. **Judge each theme under its
  intended light: light themes in `dc1-day.png`, dark themes in
  `dc1-night.png`.**
- The system's 12–14px meta/label sizes trip daylight-preview's tiny-text
  audit. They are house-sanctioned **for metadata only**, tuned on real
  hardware with the licensed faces — treat those flags as advisory for
  meta/label roles, and keep ≥16px (18px with fallback fonts) for anything
  meant to be *read*.

Absolute confirmation still needs eyes on hardware: `site/calibrate.html`.

## Precedence, restated

For club-made and Sol:OS-native dishes: this system, fully. For
daylight-ifying a friend's app: rule zero still holds (preserve the app's
soul) — but use this scale as the target gray vocabulary, these modes as
the theme model, and this voice for any copy the port adds.
