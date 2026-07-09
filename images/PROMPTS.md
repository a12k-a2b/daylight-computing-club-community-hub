# Image-gen prompts for the "Why the club exists" page

The `site/why.html` page ships with two hand-drawn grayscale scenes that
match the club's paper-and-ink look. If you'd rather use richer AI-generated
images, generate them from the prompts below and drop them in as
`site/images/homebrew-1975.jpg` and `site/images/daylight-now.jpg`, then swap
the two inline `<svg class="scene">…</svg>` blocks in `why.html` for
`<img class="scene" src="images/homebrew-1975.jpg" alt="…">`.

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

Tip: if your image model mangles small details, generate at a wide aspect
(about 20:9, matching the `viewBox="0 0 800 360"`) and keep the scene simple.
