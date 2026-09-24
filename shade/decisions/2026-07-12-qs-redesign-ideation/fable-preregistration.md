# Fable — sealed ideation (written before seeing Sol's)

Frame I'm designing against: the iPhone-native's fear isn't buttons,
it's *consequences* — "if I touch the wrong Android thing I'll break it
or drown in a menu tree". So: fewer nouns, more sentences, nothing
irreversible, and never dump the user into stock Settings without
warning. Ideas ordered roughly by leverage-per-cost. Cost scale:
**A** = afternoon in PanelView · **B** = weekend, still sideload-able ·
**C** = needs the blessing / platform team.

## 1. The panel speaks one calm sentence (A)
A single italic line under the header narrating non-default state:
"quiet · candlelit · holding portrait". Empty when everything's default.
Teaches our vocabulary passively every time the shade opens; iPhone
people read state instead of decoding six tile colors.
Cost: pure PanelView; strings from existing state functions.
Risk: verbosity — hard rule: only non-defaults, max ~3 clauses.
Maintenance: each new pill adds one phrase. Collisions: none.

## 2. "Just like Control Center" — say the grammar out loud once (A)
Our interaction grammar (tap flips, long-press opens the deeper page)
is ALREADY iOS Control Center's. iPhone people don't know that; one
ghost caption on first-ever pull — "tap flips · hold opens — like
Control Center" — that self-destructs after one open. Plus a tiny
corner dot on pills that have deeper pages (Wi-Fi/BT) as a quiet
"there's more here" affordance.
Cost: a pref + one caption + 4px canvas dot. Risk: cheese; must never
show twice. Collisions: none.

## 3. Presets become the face; sliders become the tuning (B)
The roadmap's Daylight/Overcast/Candle/Moonless row, but the stronger
claim: presets REPLACE the sliders as the default face of light on this
device (sliders one tap deeper, or below a hairline). iPhone people
live in modes (Focus, ring/silent); a named light is a mode. Each
preset = brightness + (post-blessing) warmth pair; the zone-legend
vocabulary and presets share words, so the story is coherent.
Cost: B now for brightness half; warmth half lights up with blessing
automatically. Risk: preset values need Anjan's eyes per room type;
make each preset long-press-editable ("hold to make this yours" —
teaching moment). Collisions: none; pure panel.

## 4. "The essentials" — our own eight-row settings page (B)
The intimidation is Android Settings itself. Add one picker-page (same
chassis as the Wi-Fi/BT pages): text size, screen timeout, volume,
alarm, auto-rotate, dark schedule, device name, storage-left line. Each
row = public API where possible, honest hand-off row where not (same
pattern we proved with the Wi-Fi list). The footer's "all settings"
becomes "everything else…" — reframing stock Settings as the attic, not
the front door.
Cost: B (each row independent, ~30 lines each). Maintenance: each row
is a tiny contract with AOSP that must be re-checked at the 16/17 jump —
keep every row degrade-to-handoff, mirroring the picker pattern.
Collisions: none technically; product-wise it starts absorbing Settings,
so scope discipline matters (eight rows, never eighteen).

## 5. Battery as time, not anxiety (A)
While charging: "full by 9:40" (public API: computeChargeTimeRemaining).
Steady state: just "100%"; consider dropping the % for a word ("full",
"plenty", "low — 15%") — numbers are phone anxiety; words are calm.
Cost: A. Risk: discharge *estimates* are lies on a weeks-long battery —
don't estimate, only state. Collisions: none.

## 6. Undo instead of confirm (A/B)
iPhone-native fear = irreversibility. Any pill tap that cuts you off
(Airplane especially) shows a transient whisper row in the panel:
"airplane on — tap to put it back" for ~8s. No dialogs ever.
Cost: A for one action, B generalized. Risk: timer conflicts with the
no-wakeups budget — implement as a panel-lifetime-only row (dies with
the panel, no alarms). Collisions: none if panel-scoped.

## 7. Warmth scheduling as one sentence, honestly gated (B + C)
"candlelight after sunset" as a single toggle row in shade setup — but
it schedules, which breaks the sacred no-timers budget. Resolution:
exact-time AlarmManager alarms (2/day) are within the spirit (no
polling); document the exception. Dark-at-sunset works today; amber
needs blessing. Sunset needs location (off!) — so v1 is "after 8pm",
user-set time, no location. Cost: B; the alarm plumbing is the real
cost. Collisions: the crash-loop breaker + boot receiver must re-arm
alarms; doze can delay inexact alarms — use setExactAndAllowWhileIdle
sparingly or accept ±15min drift (calmer).

## 8. The RRO re-theme is the highest-leverage platform ask after the
   blessing itself (C)
Every hand-off today ends in blue Material. A grayscale RRO (runtime
resource overlay) over Settings/SystemUI dialogs de-uglifies every
surface we can't replace, cheaply, OS-side. Push it up the platform-ask
priority list — it's small (a resources-only APK) and it makes even the
attic match the house.
Cost: C but genuinely small; platform team owns; survives OS jumps
better than forks (it's data, not code). Collisions: overlay key names
drift across AOSP versions — pin per-release.

## 9. Voice file (A, discipline not code)
shade/VOICE.md: the copy rules that already exist implicitly (plain
sentences, no jargon nouns, honest about the blessing, lowercase calm).
Every future string checked against it — cheap insurance against tone
drift across Claude sessions and contributors.

## 10. What I would NOT do
- No second page of tiles, ever (stock Android's own confusion).
- No haptics-dependent affordances (open question whether DC-1 even
  has a motor).
- No numeric brightness/warmth percentages anywhere user-visible —
  words and positions only. The dial already proved the pattern.
- No settings *search* — search is an admission the structure failed.

## Shared-blind-spot guesses (for the comparison)
Sol will likely converge on presets-as-modes and plain-language copy.
Both of us risk: (a) over-teaching — captions everywhere become clutter;
(b) designing for founder-Anjan's taste rather than actual novices (no
user evidence exists — flagged in the packet); (c) absorbing Settings
scope-creep.
