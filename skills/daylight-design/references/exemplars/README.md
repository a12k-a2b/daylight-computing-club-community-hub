# Exemplar gallery — taste, one dish at a time

Skills teach principles; this gallery teaches taste. Every real app that
goes through the daylight-ify pack leaves a before/after pair here, so the
next porter (human or AI) can *see* what good looks like on this screen
instead of re-deriving it.

**Empty is honest**: no staged mockups. Entries are added only from real
dishes, with the friend's name on them (the club's honesty rule).

## Adding an exemplar

One folder per dish: `exemplars/<app-id>/` containing

| File | What |
|---|---|
| `before.png` | the original color UI (screenshot at DC-1 viewport) |
| `after-day.png` | the daylight-ified version through `dc1-preview`'s day simulation |
| `after-night.png` | same, night/amber simulation |
| `on-device.jpg` | *(gold standard, when possible)* a photo of the real panel |
| `why.md` | the paragraph that matters — see below |

`why.md` format — short, specific, honest:

```md
# <App name> — daylight-ified from <Friend>'s original
source: <link> · ported: <date> · container: pwa | shell-apk | native

**What changed and why it works** (one paragraph: the two or three moves
that did the real work — e.g. "inverted the selected card instead of
tinting it; moved the three hover actions into a visible toolbar; mapped
the six category colors to four grays + two patterns because pairs
collided under the panel curve.")

**What we tried that failed** (at least one thing — failures teach more
than wins.)

**Lesson folded back into the skills**: <link to the commit that updated
a SKILL.md/reference, or "none yet">
```

That last line is the point of the gallery: an exemplar that taught the
pack nothing is a screenshot, not a lesson.
