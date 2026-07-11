---
name: daylight-teach
description: Generate the teaching layer for an app headed to a Daylight DC-1 — a first-run guided tour, a safe practice playground, or a quest-style "play to learn it" mode — so a non-technical friend can go from home screen to first success without help. Use when someone asks for an onboarding, tutorial, walkthrough, "teach people how to use my app", or when shelving a dish whose card would otherwise need a manual.
---

# daylight-teach — every dish comes with a taste

Apps in the club are handed to friends and family with zero context — no
sales page, no support channel, maybe a one-line tagline on the shelf.
Most developers (and most AIs) skip onboarding because it's tedious. This
skill generates it: the app itself teaches its mechanics, its interface,
and its mental model, the way good video games do — by playing, not reading.

**The bar**: picture the actual recipient (Sophie, grade ten, science
homework due). She installs the dish, opens it once, and reaches her first
real success — created a thing, finished a round, saved a note — without
asking anyone. If the app already passes that bar naked, **generate nothing**;
have the courage to skip. Unneeded tutorials are clutter with a progress bar.

## Step 1 — learn the app yourself

- Drive it (Playwright or by hand): every route/screen, every interactive
  element, every state transition. The daylight-preview skill's audit pass
  is a decent element inventory to start from.
- Write down, in one sentence each:
  - **The mental model**: "This app is a ___ where you ___." (If you can't,
    the app has a design problem — flag it, don't paper over it.)
  - **The 3 aha-actions**: the minimal actions after which the app makes
    sense (e.g. "add a task", "check it off", "watch it archive itself").
  - **The traps**: anything you personally mis-tapped or had to think about.
    Those exact spots are what the teaching layer must cover.

## Step 2 — pick the lightest format that clears the bar

| Format | When | Cost |
|---|---|---|
| **Nothing** | the UI is its own tutorial | zero — prefer it |
| **Guided tour** (3–7 steps, first run) | one screen, a few concepts | small |
| **Practice playground** (pre-filled fake data, "try it here, nothing breaks") | destructive or empty-start apps: editors, trackers, anything where a blank first screen is scary | medium |
| **Quest mode** ("play the game of the app": task list, each detected as done) | multi-step workflows, several features, or when delight matters — the gamified option | larger |

Quests are the club's signature move when they fit: 4–8 tasks, phrased as
missions in the app's own voice ("Sophie needs to hand in her lab report —
find where finished notes live"), each with **real completion detection**,
a visible ✓, and a small payoff line that names the concept just learned.

## Step 3 — build it as data + one small runtime

Keep the teaching layer separable: a spec file plus a self-contained script,
so remixing the app doesn't mean untangling the tutorial from the logic.

```
tour.json     — the content (steps/quests), editable by anyone
tour.js       — ~150 lines: renders steps, detects completion, persists
```

Spec shape (adapt freely):

```json
{ "quests": [ {
    "id": "first-note",
    "say": "Make your first note — tap the big + at the bottom.",
    "spotlight": "#new-note",
    "done_when": { "event": "click", "selector": "#new-note" },
    "payoff": "That's the + — everything in this app starts there."
} ] }
```

`done_when` detection, in order of preference: an app event or state check
(`localStorage`/store predicate, element-appears) > a DOM event on a
selector > "user taps Next" (last resort — that's reading, not doing).

## DC-1 rules for the teaching layer itself

- **Paper-legible**: coach marks are solid `#fff` cards with 2px `#000`
  borders and ≥18px text. No translucent dark scrims (they turn to mud on
  the panel) — spotlight by drawing a heavy border around the target
  element instead of dimming the world.
- **Touch-first**: the card never covers its own target; buttons ≥48px;
  "Skip" is always visible, always remembered (`localStorage`), never nags
  again. Back button closes the tour (see daylight-port on back handling).
- **Voice**: instructions are one spoken-style sentence per step — they
  should sound natural read aloud (a parent reading over a kid's shoulder,
  or `speechSynthesis` if the app already talks).
- **On-device language**: "tap", "long-press", "swipe" — never "click";
  name things by what's visibly on them ("the ⚙ in the corner"), not by
  developer names.
- **Offline**: the tour ships with the app. No fetched videos, no CDN.

## Step 4 — prove it

- Playwright-run the quest path: from a fresh profile, following only the
  tour's instructions, the aha-actions all get completed and detected.
- Run **daylight-preview** with the tour open: targets, contrast, text size.
- The killer test, when possible: hand the tablet to an actual person who
  hasn't seen the app. Watch silently. Every place they hesitate is a step
  you rewrite.

---

*v0 note: this skill is younger than its siblings — the spec shape and
runtime pattern are a starting point, not doctrine. Improve it against real
dishes and send what you learn back to the club repo.*
