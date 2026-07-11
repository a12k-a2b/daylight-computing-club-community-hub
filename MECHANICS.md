# The mechanics of sharing — how a dish travels

The table is built: shelf, wizard, robots, trust list, invites. This doc is
about the *carrying* — every leg of a dish's journey from a cook's kitchen
to a friend's tablet, where the friction still is, and the plan to remove
it. The goal, in one line: **sharing an app with a friend should feel as
easy as texting them a photo — and living with that app should be as safe
as eating at a friend's table.**

---

## The journey of a dish (and where it still drags)

| Leg | Today | Friction left |
|---|---|---|
| 1. Cook → club | Share form, Claude prompt, or hand it to a keeper | Needs GitHub or Claude; no "just text it to Anjan" path written down |
| 2. Club → shelf | Robots: sign, scan, review, crash-test, shelve | Solid. VirusTotal gate still unarmed (LOOSE-ENDS §4) |
| 3. Shelf → friend's awareness | Nothing — you find out when someone tells you | No update badges, no "Anjan brought something new" |
| 4. Awareness → installed | The 7-step guided wizard | Good, but Chrome-bound: scare screens, download-bar hunting |
| 5. Installed → living well together | Nothing | **Collisions invisible; recalls can't reach tablets; no path for feedback to the cook** |

Legs 3–5 are the frontier. Three moves cover them.

---

## Move 1 — A catalog that knows what dishes need *(cheap, unlocks everything)*

`inspect.py` already reads each APK's permissions and `shelve.py` writes
them into the catalog in human words ("draw over other apps"). Extend that
into a small `uses` vocabulary per app — the *contended* capabilities:

`accessibility` · `overlay` · `volume-keys` · `notification-listener` ·
`device-admin` · `modify-system-settings` · plus the package name (already
stored — two dishes with the same package silently overwrite each other,
the hardest collision of all).

No new UX. This is metadata the next two moves eat.

## Move 2 — A shelf that remembers *(the club PWA grows a memory — on-device only)*

The invite link already carries who you are (`invite.html?f=melissa`).
Let the club remember it in the tablet's own storage, plus one more thing:
which dishes you completed the wizard for. **Nothing leaves the device —
no accounts, no server, no analytics. The club's memory of you lives in
your hands.** That one act of remembering unlocks:

- **Collision warnings before install** — wizard step 0: "Heads up: you
  already took *Daylight Keys*, and this dish also wants the volume keys.
  They'll fight. Here's what choosing looks like." (From Move 1's `uses`
  × the dishes it remembers you took.)
- **Recall banners** — RECALL.md's weakest step is "tell the friends who
  installed it." A pulled dish leaves a note in the catalog
  (`recalled.json`); next time the club opens, anyone who took it sees:
  "A dish you took was pulled — two taps to remove it," with the exact
  Settings path.
- **Update badges** — "updated since you took it" on cards (already
  roadmapped, now trivial).
- **Dedications** — a `for: ["melissa"]` field on a catalog entry; the
  shelf greets her: "made for you ☀". Broadcast stays; warmth is added.
- **Invite + dish in one link** — `invite.html?f=matt&dish=reading-timer`:
  one texted link that seats Matt *and* walks him straight into that
  dish's wizard.

## Move 3 — The Clubhouse *(né Club Companion — the club's home on the Daylight)*

A web page can never see what's actually installed — Android guards that,
rightly. So the club brings a dish of its own: a small native app, signed
with the club key, installed once through the wizard like anything else.
Then it carries everything after:

- **The butler** — checks the shelf in the background; a quiet
  notification when a friend brings a dish, an update lands, or a recall
  happens. One tap installs: the Clubhouse downloads from the shelf and
  hands the file to Android's installer directly — no Chrome, no
  download-bar hunt, one confirmation screen instead of seven steps.
  Updates install cleanly over the top (same club key).
- **The inspector-at-home** — the real collision checker. It sees true
  installed state (packages, enabled accessibility services, active
  overlays) and compares against the catalog's `uses`: two dishes both
  listening to volume keys, two overlays fighting for the same corner,
  a package-name overwrite about to happen. It explains in plain words
  and deep-links to the exact Settings toggle when choosing is required.
- **The reporter — feedback that reaches the cook's kitchen.** When a
  dish crashes or collides, one tap builds a structured report: device,
  app versions, the colliding pair, the relevant log lines. It leaves
  the tablet only when the friend chooses to send it — via the share
  sheet, as text or email. **The report is written to be pasted into the
  cook's Claude session.** That's the loop: friend hits trouble → one tap
  → cook pastes it into their kitchen → fix → reshelve → the Clubhouse
  offers everyone the update. Every dish effectively comes with its
  cook's kitchen on call. This is the club's answer to "how does this
  scale without becoming a store": problems route to the person who
  cooked, with everything their tools need to fix it.

The Clubhouse never installs silently, never uninstalls, never phones
home. It informs, and the human taps. (One-tap-no-confirmation is
Sol:OS-card territory — policy, not physics, and not ours to shortcut.)

**Naming, settled 2026-07-11:** the app is **the Clubhouse** — on a
Daylight it isn't a companion to the club, it IS where the club lives;
the website is the same club seen through any window (iPhone, Mac, a
friend's laptop). One club, two doors: *the website is the source of
truth; the Clubhouse is the source of presence.* The butler, the
inspector-at-home, and the reporter remain as the Clubhouse's staff —
roles inside the building, not the building. Desktop thread: update the
Android app label to "The Clubhouse" (the package name
club.daylightcomputer.companion stays, so existing installs update in
place).

## The gift path — receiving should feel like AirDrop at a potluck

Settled 2026-07-11. The highest bar for leg 3→4: **a friend should never
need to remember a link.** A dish made for you simply *appears* on your
Daylight, wrapped, with the cook's name on it; you open it and it works.
Three redundant pathways, all kept good (the caretaker checks them weekly):

| Pathway | Needs | Experience |
|---|---|---|
| **Magic** (Clubhouse + PWA) | Clubhouse installed once; your seat claimed once (invite link) | Quiet notification "Anjan brought you a gift ☀" → open the club → a wrapped dish with your name → tap Open → one-tap install |
| **URL** (works for anyone, forever) | Nothing — a browser | `invite.html?f=you&dish=x` or just the club link; wrapped gifts still show once your tablet knows you |
| **Claude** (cooks' path) | A Claude/Codex session | Paste daylightcomputer.club → llms.txt teaches it to take/bring/remix |

**No accounts, on purpose.** Identity = your invite link claiming your
seat in the tablet's own storage (`dcc-friend`). Addressing = the
catalog's `for: ["melissa"]` field (now settable from the share form:
"Who's it for?"). The gift state (`dcc-opened`) lives on-device. Nothing
phones home; there is nothing to log into and nothing to breach. If the
club ever needs stronger identity, that's the Sol:OS conversation, not a
password database.

**The wrapping.** An unopened gift renders as one of eight hand-drawn
wrappers (casserole, pie under cloth, jar with a bow, wrapped loaf,
basket, cloche, cookie tin, parcel with twine) — picked by a stable hash
of the dish id, so every dish keeps its wrapper. Opening moves like
**living paper** — the lid lifts, the card settles into place. (The DC-1
runs 60–120 fps; motion is welcome when it behaves like paper — brief,
physical, then still. Think Harry Potter's newspapers: the paper moves,
the ink never glows.) The words carry the warmth: "From Anjan — Anjan
made something for you, Melissa." No confetti. A potluck, not a party
popper.

**Clubhouse contract (desktop thread):** the Clubhouse reads the same
catalog. A new entry whose `for` includes this tablet's seat →
notification "「author」 brought you a gift ☀", deep-link to the club (or
straight to one-tap install). `recalled.json` entries → recall
notification. No new endpoints — apps.json and recalled.json are the
whole protocol. **And the Clubhouse is the one-stop club app** (Anjan,
2026-07-11): its home tab IS the shelf — a WebView of
daylightcomputer.club carrying the tablet's seat — with the
butler/inspector screens alongside. One icon on the Daylight, the whole
club inside.

## The caretaker — pathways rot; someone sweeps weekly

`.github/workflows/caretaker.yml` (Mondays): walks all three pathways —
every page answers, catalog schema valid, every APK present + signature
verifies + live link downloads, dedications point at real seats,
llms.txt and the share form exist, all robot workflows parse — and posts
a 🟢/🟡/🔴 report on the standing "Caretaker log" issue. Red fails the
run so breakage is loud.

## The container rule — one bar, not one format

Settled 2026-07-09 (Anjan + desktop thread): **every dish leaves the
kitchen Daylight-ready; the container follows what the dish needs.**
We do NOT convert everything to APKs — a web dish's three-tap pin, browser
sandbox (zero collision surface), and instant updates are virtues worth
keeping. Instead, the "daylight-ify skill pack" (ROADMAP's on-ice section
— now thawing) becomes a kitchen robot that runs on submissions:

- **Needs device powers** (keys, overlays, sensors, background) →
  native APK; it already is one.
- **Self-contained applet** (games, learning tools, most vibecoded
  things) → offer APK-ification: a thin shell with assets bundled inside,
  so it works offline, carries a version, updates through the shelf, and
  the Clubhouse can manage it.
- **Live server-backed app** → stays a web dish; the kitchen daylight-ifies
  its screen (grayscale, borders, contrast, living-paper motion), never its
  packaging.

Honesty rule: the kitchen is a sous-chef, not a ghostwriter — a
transformed dish's card says "daylight-ified from <friend>'s original"
(remix lineage), and the inspectors re-run on what actually ships.

## What stays with Sol:OS

Removing the scare screens, blessing the club key as a "friends ring,"
silent updates — ROADMAP's Sol:OS card. Everything above works on stock
Android today and gets strictly better if that conversation lands.

---

## Who builds what

- **The website thread** (cloud): Move 1 (robots) and Move 2 (site JS) —
  catalog `uses`, wizard memory + warnings, recall banner, badges,
  dedications, `&dish=` links, plus an `llms.txt` so a friend can paste
  `daylightcomputer.club` into *their* Claude and it learns how to shelve.
- **The desktop thread** (Anjan's Mac): Move 3 — the Clubhouse APK (built
  like dc1-keys: plain `aapt2`+`javac`+`d8`, no Gradle), tested on the
  real DC-1 over USB; plus the human-delivery mechanics: sending dishes
  and invites by text/email from Anjan's own accounts, shelving dishes
  that friends text or email to Anjan, and owner-only unlocks (VirusTotal
  key).

## The order of work

- **A. This week, no new apps:** catalog `uses` + wizard memory +
  collision warning + recall banner (website thread) · send/receive-by-
  text procedures in CLAUDE.md (desktop thread — done) · arm VirusTotal
  (Anjan, 2 minutes) · dedications + combined invite-dish links.
- **B. The Clubhouse:** v0 butler (notify + one-tap install + updates),
  then v1 inspector + reporter. Shelved as a dish; the wizard installs it
  once, it installs everything after.
- **C. Sol:OS:** a conversation inside Daylight, not code.

*Where this fits the three rings (ROADMAP): all three moves serve rings
one and two. Nothing here builds ring three — and nothing blocks it.*
