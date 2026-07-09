# Roadmap — Daylight Computer Club

The intent, in one line: **the future of sharing open-source, bespoke,
personalized software we make for each other** — with Claude Code, Codex,
or anything else — starting with our Daylights. The Homebrew Computer Club,
but the computer is a calm tablet and the members are friends, teammates,
and parents making learning applets.

The design principle throughout: *a potluck, not a store.* Trust comes from
knowing each other and from open source, not from gatekeeping. Every stage
below should stay simple enough that one person can understand the whole
thing.

*Open, unsettled questions — including whether to rename back to "Daylight
Computing Club" (the full arguments for both names are preserved there) —
live in [LOOSE-ENDS.md](LOOSE-ENDS.md).*

---

## v0 — Me → Melissa *(built now)*

One static site on GitHub Pages. No backend, no accounts, no build step.

- A shelf of app cards driven by one `apps.json`.
- APKs hosted right in the repo, downloaded straight from the page.
- The **guided installer**: an interactive step-by-step wizard that shows
  each Android screen before you see it — the download warning, the one-time
  "allow from this source" switch, Play Protect — plus per-app setup steps
  (accessibility service, permissions). Nobody needs to know what "sideload"
  means.
- PWAs shelve as catalog entries with a URL; the wizard teaches
  add-to-home-screen instead of download-and-install.
- The club is itself a PWA: pin it once, works offline, new apps appear.
- A shared signing key in the repo so rebuilds update cleanly (see
  `signing/README.md`).
- Sharing = sending one link.

## v0.x — Small compounding improvements

- **Screenshots on cards** — a `screenshot` field in `apps.json`; grayscale
  images so the shelf looks right on the DC-1.
- **Update badges** — the service worker already refreshes `apps.json`
  network-first; show "updated since you last looked" on cards, and an
  "Update" flow (same wizard, minus the one-time steps).
- **Build-in-CI** — a GitHub Action that rebuilds APKs from their source
  repos (dc1-keys already builds with plain `javac` + `aapt2`, no Gradle) and
  signs with the club key, so "push to app repo" → "new version on the
  shelf" with no laptop involved.
- **Nicer address** — rename the repo (`daylight-computer-club-community-hub`), or put a
  real domain in front (`dcc.whatever.com`) via Pages custom domains.

## v1 — Friends sharing with friends

The moment someone *other than me* shelves an app, the bottleneck is the PR.

- **The Claude on-ramp** (already on the share page): a copy-paste prompt
  any member gives their own Claude Code session; it clones, adds the app,
  opens the PR. The maintainer just reviews and merges.
- **GitHub Issue forms** as a no-git path: an issue template with fields
  (name, tagline, APK attached or URL), and an Action that turns an approved
  issue into a PR automatically.
- **Members page** — who's in the club, what they've shared. Homebrew had a
  newsletter; ours can be a page.
- **Multiple curators** — a CODEOWNERS file so Melissa, Matt, and Jess can
  merge too. Trust scales along friendship lines, not admin hierarchies.
- **Trusted members auto-shelve** — the middle path between "Anjan approves
  everything" and "no approval at all": submissions from a small allowlist
  of known GitHub usernames publish automatically; strangers still wait for
  a human. Keeps the potluck's host-glances-at-the-dish property (apps
  install on friends' tablets with real permissions — the approval tap is
  the club's only quality gate) while removing friction for regulars.
  PWAs could auto-shelve for everyone sooner: they're browser-sandboxed,
  so the blast radius of a bad one is far smaller than an APK's.

## v2 — A community exchange (Jess's learning applets, other parents)

When people we *don't* all know arrive, the potluck needs a guest book, not
a bouncer:

- **Remix lineage** — a `remixOf` field in the catalog; cards show "remixed
  from Jess's Letter Tracer." Forking an app is a first-class, celebrated
  act — that's the whole point.
- **Collections/shelves** — "learning applets," "e-reader tools," "for
  parents" — still just JSON.
- **Comments/guestbook** via GitHub Discussions embedded per app (giscus or
  similar keeps us backend-free).
- **A real trust story** — per-app permission summaries auto-extracted from
  the AndroidManifest at CI time and printed on the card ("this app can:
  change brightness, read your calendar"), so trust is informed, not just
  vibes. Club key moves to a CI secret; releases get provenance.
- **Multiple clubs** — the repo becomes a template ("deploy your own club"),
  and clubs can follow each other's `apps.json` feeds. Federation by JSON,
  not protocol committees.

## v3 — Beyond the Daylight

The same shelf works for any Android tablet, e-readers running Android, and
(via PWA entries) even iPads — a PWA shelved here is add-to-home-screen-able
in Safari with no TestFlight. The club format — `apps.json` + guided
installer + potluck rules — is the durable thing, not the device.

---

## The Sol:OS card (unique to us: we control the OS)

Android's install friction — "unknown sources," Play Protect, manual
accessibility toggles — is policy, not physics, and Daylight writes the
policy for Sol:OS. Guiding principle for all of these: **remove friction,
keep consent** — a friend saying "yes, I trust Anjan" should take one tap,
never seven, and the OS should never call a friend's app an "unknown
source." In rising order of ambition:

1. **Bless the club** *(small)* — preinstall the club PWA on DC-1s and/or
   pre-allow the club's origin as an install source, so the
   allow-from-this-source step disappears entirely.
2. **Trust the seal** *(medium)* — a trusted-keys list in Sol:OS that
   recognizes the club signing key: club-signed APKs skip the scare screens,
   and special-power grants (accessibility service, overlays, system
   settings) collapse into one friendly per-app yes/no. Effectively a new
   "friends" trust ring between "store" and "unknown."
3. **A club installer with house keys** *(larger)* — a small privileged
   installer app shipped in the OS image holding INSTALL_PACKAGES (the
   F-Droid privileged-extension pattern): one-tap installs, silent updates
   for apps a human already approved. The club site stays unchanged; it
   hands APKs to the installer instead of the browser.

None of this blocks the club — everything works today via the guided
wizard. This is the upgrade path, and a product story in its own right:
*the tablet that treats software from your friends as first-class.*

## On ice (but part of the vision): the "daylight-ify" skill pack

A set of Claude Code skills that takes an existing web app or PWA — maybe
the simplest thing somebody made — and makes it *belong* on the DC-1:

- **Screen skill**: redesign for the 10.5″ 4:3 monochrome reflective LCD —
  pure grayscale palette, real borders instead of shadows, no gradients or
  banding-prone dithers, high contrast in sunlight, no animation (ghosting).
- **Input skill**: touch targets sized for fingers, stylus affordances
  (hover, pressure where available), voice input hooks.
- **Offline skill**: service worker, local-first storage, works on a walk.
- **Packaging skill**: wrap as a real APK (TWA or thin WebView shell) signed
  with the club key, or ship as a PWA entry — whichever fits, and shelve it
  in the club automatically.
- **Calm skill**: the opinionated one — fewer notifications, paper-like
  typography, sunlight-first design review.

When this lands, the pipeline becomes: *friend makes anything, anywhere →
one Claude session daylight-ifies it → it appears on the shelf → every club
member is two taps from it.* That's the club fully realized.
