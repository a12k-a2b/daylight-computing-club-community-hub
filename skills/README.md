# The daylight-ify skill pack

Skills that teach *your* AI — Claude Code or any agent that can read
markdown — how to make an app belong on the [Daylight DC-1](https://daylightcomputer.com).
The point: a friend vibe-codes anything, anywhere, the path of least
resistance (a web app on a color monitor). One session with these skills
turns it into something that feels at home on a 10.5″ grayscale reflective
tablet — and nobody in the club has to relearn those lessons alone.

This is the ROADMAP's "daylight-ify skill pack", first pass. Free to use,
copy, remix — like everything in the club.

## The skills

| Skill | What it does | Maturity |
|---|---|---|
| [daylight-port](daylight-port/SKILL.md) | web app → the right container (PWA / thin WebView-shell APK / native) + every web↔Android collision fixed (touch, selection, keyboard, offline, back button, downloads). Includes a complete no-Gradle APK shell template with a built-in voice bridge (`daylight-voice.js` — one speech API for Chrome and WebView). | solid first pass; shell template awaits first real build |
| [daylight-design](daylight-design/SKILL.md) | redesign for the medium: color→grayscale mapping that actually reads on LivePaper, 10.5″ 4:3 tablet layout, touch/stylus/voice-first, calm. Preserves the app's own soul. Ships `scripts/daylight-map.mjs` — optimizes palette grays in panel-effective space and decolorizes images palette-aware. `references/design-system.md` distills the real Daylight/Sol:OS design system (tokens, four theme modes, type, voice). | solid first pass |
| [daylight-preview](daylight-preview/SKILL.md) | the DC-1 on your desk: renders any URL at DC-1 geometry, saves day/night LivePaper simulations, audits touch targets, *effective* contrast (after the panel's curve), tiny text, overflow, console errors. | **working & tested** — the feedback loop for the other skills |
| [daylight-teach](daylight-teach/SKILL.md) | generates the teaching layer: guided tour, practice playground, or quest-mode "play to learn it" — so a dish handed to a friend explains itself. | v0 — pattern established, wants real-dish mileage |

Typical order: **port → design → teach**, running **preview** after every
visual pass.

The tools, at a glance: `daylight-preview/scripts/dc1-preview.mjs` (see it
as the DC-1 will show it + audits), `daylight-port/scripts/collision-tests.mjs`
(the port checklist as an executable harness), `daylight-design/scripts/daylight-map.mjs`
(palette & image color→grayscale mapping), `daylight-design/scripts/daylight-icon.mjs`
(grayscale-legible launcher icon set from a glyph).

## Using them

Any of these works:

- **One command (Claude Code plugin)**:
  `/plugin marketplace add a12k-a2b/daylight-computing-club-community-hub`
  then `/plugin install daylight-ify@daylight-computer-club`.
- **Copy into your project**: `cp -r skills/daylight-* your-app/.claude/skills/`
  — Claude Code picks them up automatically.
- **Copy for all your projects**: into `~/.claude/skills/`.
- **No install at all**: point any capable agent at a skill file —
  "read skills/daylight-port/SKILL.md and apply it to this app."

## The shared facts file

`daylight-facts.json` is the single source of device truth (panel, viewport,
simulation curve) — every tool reads it, with sane fallbacks if a script is
copied out alone. Several values are educated defaults awaiting a reading
from a real DC-1: open **https://daylightcomputer.club/calibrate.html** on
the tablet, tap "copy my readings", and send them back. One edit here
recalibrates the entire pack.

## Remixing

These skills are dishes too — improvements are contributions:

- Facts beat guesses. If you measure something on a real DC-1 (the browser's
  `devicePixelRatio`, how many grays *you* can tell apart, a WebView wart),
  correct the skills and PR it.
- Lessons learned porting a real app belong in the collision checklist or
  the mapping tables — that's how the pack compounds.
- Keep the house honesty rule: a daylight-ified dish credits the friend's
  original, and the skills themselves say what's tested vs. what's young.
