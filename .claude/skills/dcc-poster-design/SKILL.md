---
name: dcc-poster-design
description: Generate image-model prompts (and HTML posters) in the Daylight Computer Club house style — warm cream paper, pen-and-ink zine linework, one amber accent. Use whenever Anjan asks for an image, illustration, diagram, poster, or an image-gen prompt for anything club- or Daylight-related, so every image in the set shares the same aesthetic.
---

# The club's image style

One aesthetic, everywhere: the website, the posters, and every generated
image should look like pages of the same lovingly made community zine.
The canonical exemplar was generated from the "club now" prompt in
`images/PROMPTS.md` — a porch scene of friends passing paper-like tablets,
dense ink crosshatching on cream, a single amber sun.

## The style block — paste this VERBATIM into every image prompt

Consistency comes from reusing these exact words. Write the subject first
(one or two sentences: who, where, doing what), then append this block
unchanged:

> Hand-drawn pen-and-ink illustration on warm cream paper, in the style of
> a lovingly made community zine. Dense, patient linework — fine
> crosshatching and stippled shading, real ink lines, no gradients, no
> digital gloss. Almost entirely monochrome (warm black ink on cream) with
> exactly one accent color: warm amber, used sparingly on a handful of
> small meaningful things — the sun, a mug, a flower, one screen. People
> are warm, ordinary, multigenerational and diverse, drawn mid-interaction
> — showing, passing, leaning in — never posing. Technology appears as
> calm, matte, paper-like tablets (reflective e-paper, no glow, no glossy
> glare) showing simple wholesome content with a hand-drawn diagram or a
> few words of friendly text. Hand-lettered signs and labels with short
> warm mottos are tucked naturally into the scene. Small generous details
> everywhere: potted plants, zines, handwritten notes, coffee, sketches.
> Natural daylight, unhurried mood — communal, generous, a little
> nostalgic; 1970s computer-club spirit, present day.

And always append the negatives:

> Avoid: glowing screens, blue light, smartphones, logos or brand marks,
> corporate stock-photo feel, neon, gradients, chrome or glass surfaces,
> sci-fi, clutter of colors — one amber accent only.

## Knobs you may turn (everything else stays fixed)

- **Subject** — the scene itself. Keep it concrete and communal: people
  showing each other things, making, teaching, fixing, gathering.
- **Lettering content** — the hand-lettered signs are the voice of the
  image. Give the model 1–3 short mottos to letter in, drawn from club
  language: "share · learn · make together", "a potluck, not a store",
  "give to help others", "be kind, share knowledge", the six instincts'
  names, "technology should serve people and planet". Wholesome,
  lowercase-hearted, never salesy.
- **Tablet screens** — whatever a tablet shows should be a tiny gift:
  "the life cycle of sunflowers", a local weather sketch, a child's
  drawing, a recipe.
- **Aspect** — 3:4 portrait for standalone/poster images (the exemplar's
  shape); ~20:9 wide for page-header scenes (matches the `viewBox 800×360`
  slots in the site); square for icons/spots.
- **Era dial** — for "1975 Homebrew" images, switch the medium phrase to
  "warm, grainy black-and-white documentary photograph, 1975" (see the top
  prompt in `images/PROMPTS.md`) but keep everything else in spirit:
  candid, sharing, nobody posing, nothing for sale.

## House rules for the finished image

- **The squint test**: the image must read perfectly in pure grayscale —
  it will be looked at on a monochrome reflective DC-1 screen. Amber may
  only ever be an accent, never load-bearing.
- One sun somewhere is the club's quiet signature (sky, mug, poster on the
  wall — anywhere small).
- Files live in `site/images/`, lowercase-hyphenated names
  (`club-porch.jpg`). Keep originals under ~500 KB if practical (it's a
  static site on tablets).
- If the image replaces an inline `<svg class="scene">` on a page, swap it
  for `<img class="scene" src="images/…" alt="…">` — the CSS already fits.

## Motion — living paper (core design language, settled by Anjan)

The DC-1's reflective screen runs at 60–120 fps: it is **paper that can
move**. The mental model is Harry Potter's newspapers — *the paper moves,
the ink never glows.* For anything on-screen (the site, wizards, apps):

- Motion is welcome when it behaves like paper: a lid **lifts**, a page
  **settles**, a card **slides**, a corner **folds**. Physical verbs only.
- Brief and then still: one movement, ≤ ~600 ms, ease like a real object
  (fast start, soft landing), then complete stillness. Never looping,
  pulsing, bouncing, parallaxing, or attention-seeking.
- No glow, no fades-as-decoration, no motion for motion's sake — movement
  should mean something happened (opened, arrived, granted).
- Always honor `prefers-reduced-motion`.
- Reusable pieces live in `site/style.css` under "living paper"
  (`lid-lift`, `paper-settle`).

Static art (posters, images, PNGs) is unaffected — paper at rest is still
the default state of paper.

## Taste notes — the club's design koans

Small rules that settle arguments, screen and print alike:

- **Motion marks moments, not ambience.** A gift opening, a wish granted
  — the right photo moving at the right moment is magic; every photo
  wiggling is a carnival.
- **Words carry warmth; chrome carries nothing.** Where a store adds UI,
  the club adds a sentence ("made for you, Melissa"). Copy IS the
  interface — write it like a note left on a dish.
- **One big button per screen.** Size = consequence: the thing you came
  to do is black and full-width; everything else whispers at half-size.
- **Italic is a human's voice; roman is the club's.** Dedications, notes,
  captions, invitations get italics — the difference between the
  building speaking and a friend speaking.
- **Names over numbers.** "From Anjan" beats "4.8 ★". No counters, no
  metrics, no badges of scale anywhere — the shelf measures nothing.
- **Every warning states the exact next tap.** "Settings → Apps →
  Uninstall," never bare alarm. Fear without agency is a store's trick.
- **Empty space is shelf space.** Don't fill the table; room left on it
  says "bring something."
- **The paper never asks twice.** Remember one-time steps, greet returning
  friends, keep wizard progress — asking again is how software says it
  wasn't listening.
- **The sun is a signature, not a logo.** One, small, somewhere — never
  big, never repeated, never branded.

## Text-heavy diagrams and posters: don't image-gen, hand-build

Instructional posters (step-by-steps, the six instincts) need crisp
legible type — image models mangle small text. For those, copy an existing
poster source (`poster/club-poster-source.html` or
`poster/club-instincts-poster-source.html`): 1600×2000 cream `.poster`
div, ink dog-ear corner, 4px ink borders, amber-circle step numbers,
hand-drawn SVG spot icons (5px ink strokes + amber details), mono tag
labels, the "a potluck, not a store" ribbon, `daylightcomputer.club`
linkline. Render with Playwright at `deviceScaleFactor: 2` to
`site/<name>.png`. Same palette as the images: paper `#faf6ee`, ink
`#211d18`, mid `#6b655c`, amber `#a06a00`, amber-soft `#e9d9b8`.
