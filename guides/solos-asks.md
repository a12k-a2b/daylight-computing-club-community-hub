# Three asks for Sol:OS — from the Daylight Computer Club

*Anjan → Mohan, 2026-07. Context: the club (daylightcomputer.club) is a
potluck of homebrew apps friends share onto their DC-1s. Everything below
already works on stock Android today; each ask removes a rough edge that
policy, not physics, keeps in place. Ordered by impact.*

## 1. Bless the club key as a "friends ring"

Every dish on the shelf is signed with one community key (public by
design, like Android's debug key — trust is enforced by human review
before shelving, not by key secrecy). Today each friend's first install
walks through Chrome's "file might be harmful," the unknown-sources
Settings detour, and an installer that aborts mid-detour. **The ask:** a
Sol:OS setting (opt-in, per-tablet) that treats APKs signed by a
designated key as friend-trusted — install and update without the scare
screens, still with a visible confirmation. This is the single biggest
unlock for non-technical friends; the club's inspectors (source review,
emulator crash test, signing gate, VirusTotal) do the actual vetting
upstream.

## 2. A persistent developer channel

The whole make-on-Mac → see-on-Daylight loop currently rides adb.
Wireless debugging dies at every reboot, so the cable keeps coming back.
**The ask:** a developer-mode toggle that keeps the ADB-over-Wi-Fi
listener alive across reboots (or a small Sol:OS companion service that
re-arms it). With it, a maker's desk loop is: say it → deployed → on the
glass, no cable, ever.

## 3. First-class web dishes (nice-to-have)

Many dishes are just web apps. The club's own app now opens them
full-screen in a WebView, which works — but an OS-level "pin a web app"
(real icon, own window, no browser chrome) and routing
daylightcomputer.club links into the club's app by default would make
web dishes feel native without any packaging at all.

---

*Happy to demo the whole loop on a real DC-1 — the club, the guided
installs, the one-tap butler, and the maker pipeline — whenever useful.*
