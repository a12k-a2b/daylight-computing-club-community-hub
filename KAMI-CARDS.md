# Kami kami cards — the paper spirit that travels with every dish

*kami (紙) is paper; kami (神) is spirit. A kami kami card is both: the
paper spirit of a dish — everything about it that isn't code.*

A dish's APK is what it **is**. Its kami kami card is what it's **for**,
**why it's this way**, what its cook **tried that didn't work**, and how a
fresh kitchen could **cook it again**. The card is written for two readers
at once: a friend flipping it over in their hands, and that friend's
Claude, one sentence away from warming the dish into something new.

This is the club's answer to a quiet problem: our cooks don't read code —
they talk to kitchens. A source repo is the least legible form of a dish.
The conversation that made it is the most legible — and until now it died
with the cook's chat history. The kami makes it a durable, portable thing
that rides on the shelf next to the APK.

## Where a kami lives

- The file: `site/apps/<id>/kami.md` — next to the dish it belongs to.
- The catalog: a `"kami": "apps/<id>/kami.md"` field on the dish's
  `apps.json` entry.
- The postcard: `kami.html?app=<id>` renders the card as an actual
  postcard — picture side and message side, stamp, postmark, lineage.
- Web dishes carry kamis too (the file sits in `site/apps/<id>/` even
  when no APK does).

## The five sections (fixed headings — machines parse these)

```markdown
# Kami — <Dish Name>

## Dear friend
What this dish is for, in the cook's own voice. Two to four sentences.
Who it was made for and what moment of their day it serves.

## Why it's this way
The design decisions WITH their reasons, as short annotations:
- "the chime is quiet — it's for 6am"
- "single-unit brightness steps at the dim end — the DC-1's backlight
  is cliff-y down there"
A decision without its reason is furniture; the reason is the spirit.

## Paths not taken
What the cook tried that didn't work, and what was rejected on purpose.
This is the most generous section: it saves the next kitchen from
burning the same pan twice.

## To cook it again
One paragraph a fresh Claude could cook from — the regeneration spec.
If the code vanished tonight, this paragraph plus a kitchen should
produce a dish the cook would recognize.

## Lineage
A growing list, newest last:
- cooked by <name> (with Claude), <when> — for <whom>
- warmed by <name>, <when> — <what changed, one line>
```

The catalog stays the source of truth for facts it already holds (name,
author, version, dates, dedications) — the kami never repeats them. The
kami holds only what no robot can reconstruct: intent, reasons, dead
ends, and the recipe.

## The norms that come with it

- **Every new dish brings its kami.** The share form asks for it the way
  a potluck asks whose dish this is. (Your Claude can draft it from your
  cooking session in a minute — you just check it sounds like you.)
- **Warming is the club's word for remixing.** Take a dish, change what
  you need, shelve your variant back. The Remix flow hands your Claude
  the kami, not just a repo link — the kitchen travels with the dish.
- **Lineage is credit.** "Warmed by Melissa, after Anjan's original" on
  the card. Forks are adversarial; warmings are potluck.
- **The honesty rule extends** (same as daylight-ifying, MECHANICS.md):
  a warmed dish's card says what changed, and the inspectors re-run on
  what actually ships. Trust attaches to *this* dish, never inherited
  from its ancestor's reputation.
- **A kami is a gift, not a contract.** Old dishes grow kamis when their
  cooks feel like it. Gaps are marked honestly ("only the cook remembers
  why") rather than invented.

## Who builds what

- **Desktop thread (done in v0):** this format; the first card
  (`site/apps/dc1-keys/kami.md`); the postcard page (`site/kami.html`).
- **Website thread (invited):** a "kami kami card" link on each dish
  card; the Remix button carries the kami text into its copied prompt;
  the share-form robot asks for a kami on new submissions (gently —
  optional at first); wizard step 7 mentions the card ("every dish
  comes with a postcard from its cook").
- **The Clubhouse (later):** the reporter appends trouble reports to the
  dish's traveling history; the butler shows the postcard when a gift
  arrives.
