# The Decider — a playable lesson

An interactive time-lapse sim about the one governing question of running a
company: *of all the decisions this company makes, which ones are mine?*
Four guided failure scenarios, one judgment mode, one free-play capstone,
a field guide, and a 60-second before/after test.

Live: https://daylightcomputer.club/ceo-sim/ · Lessons: [lessons.html](lessons.html)

This directory is meant to be **copied**. It is one of the Daylight Computer
Club's reference "playable lessons" — a genre note lives at the bottom of the
field guide. Point your AI at this folder and say "make one of these about
compound interest." Everything below is what it needs to know.

## Architecture

Three files, no build step, no dependencies, pure grayscale:

```
model.js    the simulation + ALL content. Pure logic — zero DOM access.
            Runs headlessly under node (that's how it's tuned and tested).
            Exposes window.CEOSIM: makeGame(mode, seed) → state,
            tick(state) → events[], applyCard(state, card, choice),
            plus the decision deck, scripts, endings, and sorting test.

game.js     the driver: time-lapse loop, decision-memo overlays, endings,
            audio synth (WebAudio, no assets), sorting test, debrief mode,
            share cards, progress in localStorage. Talks to the model only
            through the functions above.

iso.js      a VIEW. Exposes window.ISO_BOARD {reset, render, fire, anchor,
            onEvent} — the hand-drawn isometric city. game.js activates it
            when the ISO view is selected and falls back to the org-chart
            DOM view (in index.html) otherwise. To add a skin, implement
            those five functions and register a toggle.
```

Design invariants (the club's rules of the road): pure grayscale, real 2px
borders, no gradients, ≥48px touch targets, relative URLs, works offline
after one visit (club service worker), and everything renders on a Daylight
DC-1's LivePaper screen — including a PAPER motion mode that redraws once
per simulated week.

## The playable-lesson pattern

The sequencing is the design. If you copy one thing, copy this:

1. **Guided failure first ("I do")** — scenarios A/B/D/E are lectures you
   inhabit; the philosophy makes the choices, pre-stamped, and you live
   with them. Losing is the syllabus.
2. **Judgment with feedback ("we do")** — The Balance hides the stamps;
   you classify each memo from prose (some are traps), and the truth is
   revealed the moment you commit.
3. **The keys ("you do")** — Your Company hands you a dial while the
   correct setting drifts. The ending reveals the chart of the dial you
   set vs. the dial it needed.
4. **Transfer** — the sorting test measures the change (baseline locked
   before play, re-test after); the field guide's memo template carries
   it to Monday.

## Determinism

The model is deterministic per seed: same seed → same deck order, same
beats, same ending given the same choices. `Math.random()` appears only in
view-layer cosmetics (worker positions, paper spin). This is what makes the
test suite and shareable `?seed=` challenges possible. Keep it that way.

## URL parameters

| param | effect |
|---|---|
| `?scenario=A..F` | jump straight into a run (`&week=N` fast-forwards; C needs A played, F needs C) |
| `?seed=12345` | pin the decision-deck order (printed on the ending share card) |
| `?view=iso\|classic` | pick the board skin (persisted) |
| `?motion=paper\|live` | stills-per-week vs continuous animation (persisted) |
| `?debrief=1` | facilitator mode: discussion breaks at weeks 24/48/96 |
| `&dq=Q1\|Q2\|Q3` | replace the debrief questions with your own |

## Testing

```sh
node test/model.test.mjs          # headless: every mode's ending, the
                                  # F strategy matrix, trap cards, seeds
# browser end-to-end (needs playwright + a static server on :8931):
#   cd ../.. && python3 -m http.server 8931 --directory site &
#   DC1_CHROMIUM=/path/to/chromium node test/browser.test.mjs
```

The model test has no dependencies and must stay green — it encodes the
pedagogy (fixed dials must drift or die; surface-reading the trap cards
must lose; a single slip must be survivable). If a tuning change breaks an
assertion, the lesson broke, not the test.

## Reskinning for a new lesson

1. Rewrite `model.js`'s content blocks: the scripts (scenario beats), the
   deck (decisions with stakes/reversibility), the endings (headline, sub,
   lessons, essay), the sort test. Keep the shape: honeymoon → strain →
   fires → collapse, with second-order consequences arriving on a delay.
2. Re-tune the difference equations in `tick()` until the trajectories
   land where the story needs them (write a quick node loop; see the test).
3. Optionally replace `iso.js` with a view of your own world — anything
   that implements the five ISO_BOARD functions.
4. Update `lessons.html` — the artifact isn't done until the lesson
   survives outside the game.
