# Mapping a color UI to DC-1 grayscale — the algorithm

Naive desaturation fails because hue carries meaning that luminance doesn't:
`filter: grayscale(1)` turns a red delete button and a green save button
into twins (~the same mid-gray). Map by **role**, not by pixel.

**Tooling**: `scripts/daylight-map.mjs` automates steps 3 and 5 — it takes
the palette (hex=role pairs), compares colors in OKLab with a
Helmholtz–Kohlrausch lift (vivid colors *look* brighter than their
luminance), and optimizes gray assignments in panel-effective space, so
"distinguishable" is measured after the DC-1's shadow-crushing curve.
`daylight-map.mjs image in.png out.png` decolorizes pictures: flat-color
art gets palette-aware spreading (red/green/blue stay apart), photos get a
global apparent-lightness mapping. Run it; then do steps 2 and 4 — the
judgment calls — yourself.

## Step 1 — inventory colors by role

Grep the styles (CSS custom properties, Tailwind config, theme files) and
list every color with its job, not its hex:

> primary action · destructive action · success/confirm · warning · info ·
> link · selected/active · focus ring · disabled · text primary/secondary/
> placeholder · background/surface/elevated surface · border/divider ·
> chart series 1..n · brand accents

## Step 2 — sort roles by required *distinctness*, not by hue

Ask of each pair of roles: "must a user tell these apart **at a glance**,
without reading?" Selected vs unselected: yes. Danger vs primary: yes.
Surface vs elevated surface: no (words and borders can carry it).
That yes-list is what the gray ramp must guarantee.

## Step 3 — assign grays from the ramp, preserving value order

Use the five-tone ramp (`#000 / #555 / #999 / #ccc / #fff` — or the design
system's, if present). Keep the app's original light↔dark hierarchy where it
exists (its darkest text stays darkest), so the translated app still "feels
like itself" in value structure. Constraints:

- Glance-distinct pairs: ≥2 ramp steps apart, **or** differ by a second
  channel (step 4).
- Body text: `#000` on `#fff`. Secondary: `#555`. `#999` never carries
  prose. The compressed dark end means `#000`–`#444` count as ONE tone.
- Large fills flip to outlines: a solid indigo header becomes white with a
  heavy `#000` bottom rule, not a solid `#555` slab (big mid-gray areas look
  like dirt on paper; reserve solid black fills for the one or two elements
  that deserve maximum weight).

## Step 4 — re-encode what hue was saying

| Color used to say | Say it now with |
|---|---|
| selected / active / on | **inversion** — black fill, white text. Strongest signal on paper |
| primary action | inversion or 3px border + bold label |
| destructive | word ("Delete") + icon + heavy border; confirm step. Never rely on "it's the red one" |
| success / error state | ✓ / ✗ icons + words; error text stays `#000`, bold, with an icon — never light gray |
| link | underline (the original link affordance) |
| focus | 3px `#000` outline with 2px offset |
| warning banner | border-weight + icon + position, e.g. 3px bordered box |
| chart series | patterns: solid/dashed/dotted lines; hatch/dot/solid fills; direct labels on series instead of a color legend |
| heatmap / intensity | dot density, size, or numbers — not gray ramps beyond ~4 levels |
| status dots (green/yellow/red) | shape: ● filled / ◐ half / ○ empty — plus a word nearby |
| brand color moments | typography, a signature glyph, generous whitespace — brand ≠ hue |

## Step 5 — verify mechanically, then by eye

Run **daylight-preview**: its contrast audit applies the LivePaper curve
(shadow-crushing gamma, compressed range) before checking WCAG thresholds —
grays that pass on a monitor legitimately fail there. Then squint at
`dc1-day.png` and `dc1-night.png`: can you spot the selected item, the
primary action, the error, without reading?

## Worked example — a typical vibe-coded palette

| Original | Role | DC-1 translation |
|---|---|---|
| `#6366f1` indigo fill button | primary action | black fill, white bold label |
| `#ef4444` red button | delete | white fill, 3px black border, "Delete" + ✗, confirm step |
| `#22c55e` green toast | success | "✓ Saved" in bold ink, 2px bordered box |
| `#6366f1` text links | links | ink text, underlined |
| `#e0e7ff` selected row tint | selected | inverted row (black bg, white text) or ▸ marker + bold |
| `#f9fafb` page bg / `#fff` cards + shadow | surfaces | `#fff` everywhere; cards get 2px `#000` borders, shadows deleted |
| `#6b7280` secondary text | secondary text | `#555` |
| `#9ca3af` placeholder | placeholder | `#999`, italic |
| `#d1d5db` borders | dividers | `#ccc` hairlines; `#000` 2px where the border *means* something |
| chart: indigo/emerald/amber lines | series | solid / dashed / dotted ink lines, labeled at line ends |
| focus ring `#6366f1` glow | focus | 3px solid `#000` outline, 2px offset |

Photos and illustrations: the panel renders them in 256 hardware grays —
they survive, but timidly. Boost contrast/levels a touch; prefer line art
and duotones when assets are yours to change; make sure nothing *functional*
(a map pin, a diagram arrow) depends on hue inside an image.
