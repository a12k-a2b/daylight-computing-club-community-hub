# Field test — the wizard and the Clubhouse on real glass

*2026-07-11, ~3am, desktop thread. The DC-1 came in over USB; a Claude
session walked the whole journey the way Melissa will: shelf → wizard →
download → install → one-tap. Every screenshot in this report is from the
real tablet. Nothing here is speculation.*

**Headline: the journey works.** The shelf renders, the wizard's warnings
match Android's real dialogs almost word for word, the Clubhouse installed
and installed *Daylight Keys* by itself — the club's first dish delivered
by the club's own butler. The rough edges below are real, but they're
edges, not cracks.

---

## What the wizard promised vs. what the glass showed

The wizard's step 2 warning — *"File might be harmful — tap Keep /
Download anyway"* — matched Chrome's actual dialog exactly. That kind of
promise-keeping is the whole game. Where the promises drift:

1. **Chrome's own "Install Daylight Computer Club" popup lands on top of
   the site header on first visit.** A friend arriving to install a dish
   sees a system "Install" button for *the website* before they've
   scrolled an inch. Two different "installs" on one screen. Worth either
   suppressing the PWA prompt (drop the manifest's installability) or
   embracing it (a wizard aside: "Chrome may offer to install the club
   itself — that's the 'pin the website' door, fine either way, not this
   step").

2. **Step 3 says "Chrome shows a little bar at the bottom" — on the DC-1
   it's a toast at the top,** showing only "(24.42 KB)
   daylightcomputer.club — Open", no filename. Eyes go to the wrong edge
   of the screen. The mockup should match: top toast, size-not-filename.

3. **The toast auto-hides in a few seconds** — anyone reading at wizard
   pace misses it. That's fine *if* the fallback works, but —

4. **The fallback is a dead end on the DC-1: "the download is in your
   notifications" — it isn't.** Chrome ships with notifications OFF on
   this device (`POST_NOTIFICATIONS granted=false`), so downloads never
   appear in the shade. The reliable recovery is Chrome's **⋮ → Downloads**
   (always works, and the friend can also just tap the wizard's Download
   button again and catch the toast this time). The copy should point
   there instead.

5. **Step 2's "Downloaded it →" and Chrome's "Open" toast appear at the
   same moment and compete.** A friend who taps Chrome's "Open" skips
   ahead to the installer without the wizard's prep for the permission
   screens. One sentence in step 2 defuses it: "Chrome will show a little
   'File downloaded — Open' note; you can ignore it, we'll open it
   together in a moment."

## The Clubhouse, first night in the field

Installed 0.1 (build a2d565e) and launched. The shelf lives inside, it
recognizes itself ("On your tablet ✓"), it recognized Tracing Paper, and
"GETTING ALONG: No collisions — your dishes are getting along ☀" is a
lovely first impression. The butler's 6-hour shelf check registered and
runs clean (forced a run; no crash, correctly quiet with nothing new).
Notification permission flow works.

**The one-tap install is real.** Tapped "Get it — one tap ↓" on Daylight
Keys → "Handing it to Android…" → Android's installer → installed. The
promise of Move 3, working. Six fixes will make it Melissa-ready:

1. **Downgrade offered as update.** Tracing Paper on the tablet is 1.7.5;
   the shelf entry is 1.5. The card says "Update to 1.5 ↑". Two fixes,
   both worth doing: the Companion should compare versions *directionally*
   (installed newer than shelf → "you're ahead of the shelf", not an
   update), and the shelf catalog needs Tracing Paper 1.7.5 shelved (it's
   the build verified on real glass 2026-07-11).

2. **First one-tap dies during the unknown-sources detour.** Android
   blocks the first install ("not allowed to install unknown apps from
   this source"), the friend goes to Settings, flips the toggle, comes
   back — and the pending install has been aborted
   (`INSTALL_FAILED_ABORTED: User rejected permissions`). The button has
   quietly reset; nothing explains that the first attempt died. The butler
   gets the abort callback — it should catch it and say, in club voice:
   "Android now trusts the Clubhouse. Tap once more and it's yours."
   Better still: pre-brief the whole thing before the first one-tap ("the
   first time, Android will ask once to trust the Clubhouse — here's the
   screen you'll see").

3. **After a successful install the card doesn't update.** Daylight Keys
   installed fine and its card still said "Get it — one tap ↓". Installed
   state is read once at launch; it should refresh on resume and on the
   install-success callback (the moment is already instrumented).

4. **Android's installer says "Key Mode Toggle", not "Daylight Keys".**
   The dc1-keys manifest label predates the christening. At the most
   trust-sensitive dialog of the journey, the name doesn't match the card.
   One-line fix in dc1-keys' manifest + rebuild + reshelve (desktop lane;
   queued, not yet done — say the word).

5. **Raw Android errors reach the friend.** The abort toast read
   "Install didn't finish: INSTALL_FAILED_ABORTED: User rejected
   permissions." True, but not potluck language.

6. **The Companion wears Android's default robot icon** — visible on the
   permission screens and installer dialogs. It needs the club's sun mark
   before friends meet those screens.

## A collision the inspector should someday catch

Daylight Shade (the pull-down panel, on glass for its own test) draws a
swipe-catch strip just below the status bar. During this walk it
repeatedly swallowed taps aimed at Chrome's toolbar (menu button, download
toast). That's exactly the class of fight the inspector-at-home exists to
explain — worth adding `top-edge-strip` to the `uses` vocabulary when
Shade heads for the shelf.

## Note for future sessions: one hand on the glass at a time

Tonight *five* desktop sessions had adb access at once (Shade, Lofi QA,
motion, color-to-gray, this one). Mid-walk, other sessions foregrounded
their apps, pulled the notification shade, and once put the screen to
sleep — and this session's taps landed in *their* windows (one stray tap
hit "Manage" in the shade; no harm done, verified). Blind coordinate taps
can't be made safe while another session drives. Suggested convention
until something better exists: **a session that needs the glass checks
`~/code/.dc1-glass` first** — if it exists, wait or coordinate; if not,
write your session name + timestamp there, work, delete it when done.
(This session finished its device work before writing this note and is
off the glass.)

---

*Screenshots from the walk (shelf on real glass, each wizard step, every
system dialog, the Clubhouse home) are in the desktop session's scratchpad
and can be re-taken on request — the walk is fully scripted now.*
