# Harmony — how independently-cooked dishes share one tablet

The multiplicity problem: friends vibe-code apps independently, everyone
downloads everything, and the apps must play nice — same volume keys, same
corners, same brightness variable, same attention. This doc is the club's
answer. It came from first-principles reasoning plus a five-domain precedent
study (OS input arbitration; game modding; package managers; commons
governance & ecology; plugin ecosystems) — the appendix credits what we
stole from where.

## The five layers of collision

1. **Identity** — same Android package name; one app silently replaces the
   other. Dumbest, most destructive.
2. **Exclusive grabs** — volume keys, accessibility event consumption, hot
   corners, overlays. One winner, today chosen arbitrarily (last installed).
3. **Commons erosion** — notifications, battery, background polling,
   home-screen space, attention. All can have some; too many ruin it.
4. **Semantic fights** — two apps writing one variable with different
   opinions (night-dimmer vs reading-brightener, both own "brightness").
5. **Cohesion loss** — everything works, nothing matches; junk-drawer feel.

## The core insight

Operating systems solved coexistence by **isolation** — every app dreams it
owns the machine. That works for resources you can divide or fake (memory,
CPU). Layers 2–5 are scarce **by meaning**: there is one pair of volume keys
because the human has one mental model. You cannot virtualize the user. The
residue the OS never solved is a **norms problem, not a physics problem** —
and norms problems are solved by communities, not kernels.

## The club's unfair advantage

Every historical ecosystem that tried "just publish conventions" failed for
one reason: **humans don't read**. Every cook in this club is a Claude or
Codex — an author that reliably reads the docs before writing a line. Put
the covenant where the AIs already look (llms.txt, the shelving robot) and
it gets *executed*, not published. Conventions are cheap AND strong here for
the first time; therefore the club spends almost everything at build time,
the cheapest layer, and builds runtime machinery only when norms fail.

## The ledger: claims, not vibes

The catalog is the club's land registry. Every dish declares its claims in
`uses` — from a **closed, curated vocabulary** (names, not rival-app lists,
so strangers conflict correctly with zero coordination):

    volume-keys · accessibility · overlay:corner-tl/tr/bl/br ·
    settings:brightness · settings:dnd · notification-listener ·
    device-admin · installs-apps · background-service

Rules of the ledger:
- **Default-none** (Greasemonkey `@grant`): declaring nothing = the dish is
  inert — no background work, no overlays, no settings writes. The absence
  of claims is itself a promise.
- **Modes** (Android audio-focus vocabulary): a claim is `exclusive`,
  `transient` (borrows and returns), or `shares` (coexists if it yields).
  Most "collisions" are really duckable overlaps; only exclusive×exclusive
  on one name is a true fight.
- **Verified, not trusted** (Plug-and-Play's lesson: arbiters fail when
  self-reports lie): `inspect.py` diffs declared claims against the APK's
  actual manifest; drift blocks the shelf. A contract without a verifier
  decays (SemVer's lesson).
- **First declarer keeps; the newcomer adapts** (the bourgeois convention —
  cheap signals replace fights). Claims expire when a dish is recalled or
  abandoned.
- **The scent-mark board** (stigmergy): the robot regenerates a CONTENTION
  table from all claims — who holds which key, which corners are free — in
  the exact docs every cook's AI reads before building.

## The ladder: five rungs, use the lowest that works

1. **Partition** — design the conflict away at build time: unique package
   names, a *free* corner, temporal partitioning (the night app and the day
   app share hardware like owls and hawks share a forest).
2. **Convention** — norms in the docs the AIs read: reserved gestures
   (some belong to the user, claimable by no app — Emacs's C-c rule), a
   club leader-gesture as namespace (Vim's `<Leader>` insight).
3. **Disclosure + human choice** — ledger + plain-words install warnings
   (built). The floor, never removed.
4. **Negotiation** — a new dish's claims clash → the shelving robot opens a
   negotiation with the cook's AI: names the incumbent, lists free
   alternatives, requests adaptation. Compatibility patches are ~free when
   every cook has the source and an AI (RimWorld ships them conditionally);
   "harmonized with Daylight Keys ☀" is a celebrated badge, not an apology.
5. **Mediation** — the heavy rung, per-resource and only after rungs 1–4
   keep failing for that resource (airspace rule: a tower per busy airport,
   never one tower for all the sky). The Clubhouse becomes the ONE holder
   of a genuinely singleton resource and dispatches by **named actions**:
   apps never touch raw volume keys — they export `reader.page-turn`,
   `torch.toggle` in the catalog, and the Clubhouse's single listener
   routes per the user's assignment (Vim `<Plug>`, MediaSession,
   update-alternatives: *everyone stays installed, exactly one is active,
   the human can switch anytime, the loser stands down visibly*).

## Who decides what — subsidiarity by knowledge

| Decision | Decider | When |
|---|---|---|
| What the norms are | The humans together (demo night; keeper tiebreak) | Ongoing |
| What exists & what's claimed | The club's ledger — *library, never police* | Always |
| How a dish adapts to fit | The cook's own AI | Build time |
| What runs; who wins a fight | The friend, plainly informed | Install time |
| Routing within set preferences | The Clubhouse, revocably | Runtime |

Ostrom's audit of the club: boundaries ✓ (invites + members.json), local
rules ✓, monitoring ✓ (inspectors), conflict forum ✓ (demo night, issues),
autonomy ✓, nesting ✓ (federated clubs). The one missing principle is
**graduated sanctions**: between "warning" and "recall" the club needs a
**quarantine** rung — a dish stays shelved but flagged off the default view
while its cook's AI fixes it. (Added to RECALL thinking.)

## Build order

- **v0 (now):** this doc; claims vocabulary + modes in the catalog; wire
  the covenant into llms.txt so every cook plays nice by default.
- **v1:** the plays-well robot — shelve-time diff of new claims vs ledger;
  on clash it *negotiates* (comment with incumbent + free alternatives)
  instead of merely warning; declared-vs-manifest verification blocks
  drift; auto-regenerated contention table.
- **v2:** named actions + the Clubhouse dispatcher for whichever resource
  proves ungovernable by norms (volume keys are the likely first tower);
  the club leader-gesture; quarantine rung.
- **Always:** layer 5 (cohesion) is solved by the daylight-ify kitchen —
  a design language, not a police force.

## What the club refuses to build

- Central allocation of resources to "deserving" apps — store thinking.
- Silent runtime auto-resolution — the human always sees the fight and
  picks the winner.
- The mediation broker before real collisions justify it — with three
  dishes on the shelf, a tower is cathedral-first thinking.

## Appendix: stolen honestly

- **MediaSession / audio focus** (Android): single system router with a
  predictable rule beats priority arms races; claim modes (exclusive /
  transient / duck).
- **Accessibility filter chain** (Android): the anti-pattern we're fixing —
  hidden enablement-order arbitration, silent losers.
- **i3/AutoHotkey**: all bindings in one human-auditable file; duplicates
  rejected at declaration time. apps.json is our one file.
- **Raycast/Alfred**: consent-based takeover flows; pick unclaimed defaults.
- **Forge/Fabric, SMAPI**: convert overwrites into declarative, mergeable
  registrations on one substrate; namespaced registries.
- **LOOT/Skyrim**: machine-readable conflict metadata + community
  compatibility patches.
- **Factorio**: `requires` / `worksWith` / `incompatible` vocabulary,
  enforced at load.
- **RimWorld**: conditional compatibility shipped inside the mod — free
  when authors read the catalog first.
- **dpkg/apt**: virtual capabilities + `update-alternatives` — all
  installed, one active, user-switchable.
- **Nix**: make collisions impossible by construction wherever the
  resource is namespaceable; reserve arbitration for true singletons.
- **SemVer**: a contract without a verifier decays.
- **Intents/VS Code contribution points**: closed vocabulary of declared
  slots; a chooser, not just a warning.
- **Object capabilities**: apps accept scoped grants from the holder
  instead of grabbing singletons — the Clubhouse's endgame.
- **Ostrom**: who decides = the users of the commons, at the smallest
  scale; graduated sanctions; cheap conflict forums.
- **Unlicensed spectrum (WiFi)**: listen-before-talk etiquette works when
  every device implements it — and every cook here reliably does.
- **Airspace classes**: towers per busy airport, self-announcement
  everywhere else.
- **Niche partitioning / territorial signaling / stigmergy**: diverge along
  offered axes; cheap public claims replace fights; the repo is the mound.
- **Emacs/Vim conventions**: reserved-for-user keys; `<Leader>` as
  namespace; `<Plug>` named actions — the single strongest mechanism here.
- **WordPress**: the cautionary tale — shared globals, no declarations, no
  referee.
- **Greasemonkey `@grant`**: default-none; undeclared = inert.
- **Home Assistant**: CI-validated manifests; unique-resource claiming.
