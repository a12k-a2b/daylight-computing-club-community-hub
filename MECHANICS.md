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

## Move 3 — The Club Companion *(the club's own dish; the collision checker lives here)*

A web page can never see what's actually installed — Android guards that,
rightly. So the club brings a dish of its own: a small native app, signed
with the club key, installed once through the wizard like anything else.
Then it carries everything after:

- **The butler** — checks the shelf in the background; a quiet
  notification when a friend brings a dish, an update lands, or a recall
  happens. One tap installs: the Companion downloads from the shelf and
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
  → cook pastes it into their kitchen → fix → reshelve → the Companion
  offers everyone the update. Every dish effectively comes with its
  cook's kitchen on call. This is the club's answer to "how does this
  scale without becoming a store": problems route to the person who
  cooked, with everything their tools need to fix it.

The Companion never installs silently, never uninstalls, never phones
home. It informs, and the human taps. (One-tap-no-confirmation is
Sol:OS-card territory — policy, not physics, and not ours to shortcut.)

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
- **The desktop thread** (Anjan's Mac): Move 3 — the Companion APK (built
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
- **B. The Companion:** v0 butler (notify + one-tap install + updates),
  then v1 inspector + reporter. Shelved as a dish; the wizard installs it
  once, it installs everything after.
- **C. Sol:OS:** a conversation inside Daylight, not code.

*Where this fits the three rings (ROADMAP): all three moves serve rings
one and two. Nothing here builds ring three — and nothing blocks it.*
