# Image-gen prompts for the "Why the club exists" page

*(Done! — `why.html` now opens with a real 1975 Homebrew photograph and
closes with an image generated from the prompt below, both at
`site/images/`. The original stick-figure scenes live on, side by side,
in the middle of the page — and the untouched first version of the whole
page is kept for memory at `archive/why-manifesto-og.html`.)*

Keep them grayscale (or warm duotone) so they read well on the Daylight's
monochrome reflective screen.

---

## Top image — the Homebrew Computer Club, 1975

> A warm, grainy black-and-white documentary photograph, 1975, of the
> Homebrew Computer Club: a dozen people crowded around folding tables in a
> garage or lecture hall, passing around hand-built home computers and bare
> circuit boards, leaning in to look at each other's work, clearly excited
> and talking. Boxy early microcomputers with small CRT screens and toggle
> switches on the tables. Period-accurate 1970s clothing, eyeglasses,
> handwritten notes and schematics scattered around. Natural light, candid,
> nobody posing — the feeling of people sharing what they made, not selling
> anything. Film grain, slightly faded, high contrast, monochrome.

Negative / avoid: modern devices, smartphones, logos, glossy studio lighting,
color.

## Bottom image — the Daylight Computer Club, now

> A calm, sunlit black-and-white illustration in a hand-drawn zine style: a
> small group of friends of different ages sitting outdoors in bright natural
> daylight — a porch, a garden, a park bench — passing around warm, paper-like
> matte tablets (e-paper / reflective LCD, no glossy glare). One person hands
> their tablet to another to show something; a parent and child look at a
> screen together; someone sketches. A single small sun motif in the sky.
> The mood is unhurried, communal, generous — the modern echo of a 1970s
> computer club, but calm and outdoors. Grayscale with one warm amber accent,
> real 2px ink linework, no gradients, no glossy screens.

Negative / avoid: glowing blue phone screens, cold office lighting, corporate
stock-photo feel, gradients, heavy color.

---

## The six instincts — an illustrated poster

The crisp, text-accurate version already exists (hand-built:
`poster/club-instincts-poster-source.html` → `site/club-instincts-poster.png`).
This prompt makes the *artistic* companion — six little scenes instead of
six labeled panels. Image models mangle small type, so keep lettering to
the six single-word names and let the vignettes do the talking.

> A vertical 3:4 poster made of six small hand-drawn vignettes arranged in
> a 2×3 grid, one per club instinct, each with a single hand-lettered word
> beneath it: RECIPROCITY — two friends passing a covered dish across a
> table, both smiling; VULNERABILITY — someone shyly holding out a small
> homemade thing, another person receiving it with both hands;
> OPEN RECIPES — a recipe card being copied by hand, two versions side by
> side, one slightly different; HUMAN SCALE — a small circle of five
> friends around one table, nobody outside it; MADE-FOR-ONE — an adult
> giving a child a little tablet with a hand-drawn sunflower on its paper
> screen, a "for you" tag on it; UNHURRIED — a low sun over a calm
> horizon, someone reading on a porch, coffee steaming. A small sun motif
> at the top of the poster and "daylightcomputer.club" hand-lettered at
> the bottom.
>
> Hand-drawn pen-and-ink illustration on warm cream paper, in the style of
> a lovingly made community zine. Dense, patient linework — fine
> crosshatching and stippled shading, real ink lines, no gradients, no
> digital gloss. Almost entirely monochrome (warm black ink on cream) with
> exactly one accent color: warm amber, used sparingly on a handful of
> small meaningful things — the sun, a mug, a flower, one screen. Natural
> daylight, unhurried mood — communal, generous, a little nostalgic;
> 1970s computer-club spirit, present day.

Negative / avoid: glowing screens, blue light, smartphones, logos,
corporate stock-photo feel, neon, gradients, paragraphs of small text —
one amber accent only.

---

**Making more images in this style:** the house aesthetic is bottled as a
Claude skill at `.claude/skills/dcc-poster-design/SKILL.md` — it has the
reusable verbatim style block, the negatives, and the rules (one amber
accent, the squint test, one small sun somewhere). Any future Claude
session in this repo can use it to keep the whole image set matching.

Tip: if your image model mangles small details, generate at a wide aspect
(about 20:9, matching the `viewBox="0 0 800 360"`) and keep the scene simple.
