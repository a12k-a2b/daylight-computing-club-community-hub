# Prompt: refresh rate, pixel response, and motion quality on the DC-1

Copy everything below the line into a fresh Claude Code conversation.
Companion investigation to DISPLAY-PIPELINE-PROMPT.md (color→gray mapping);
same device, same working style, different question. I'll upload the panel
datasheet into this thread — read it thoroughly, it's the anchor document.

---

## Mission

You're working with Anjan (Daylight Computer's founder) to settle motion on
the DC-1: **what actually limits perceived motion quality on this panel —
refresh rate or liquid-crystal pixel response — and what should the OS,
apps, and panel settings do about it?** The panel is a variable-refresh
30–120 Hz IGZO design, but in practice it may only be worth driving to
~90 Hz, and at 120 Hz there's noticeable motion blur — we suspect refresh
rate and pixel response time are muddying each other and we want ground
truth, then policy: the right default refresh, the right VRR behavior per
content type, honest specs, and design guidance for app makers.

## The conversational contract

- Multi-session back-and-forth. Ask me for ONE thing at a time: I can run
  adb commands on a real DC-1 (USB debugging works), shoot photos and
  **slow-motion video** (iPhone, 240 fps) of the panel, upload the panel
  datasheet PDF, and relay questions to our display/kernel engineers and
  the panel/DDIC vendors.
- I'm not a display engineer — define every term on first use (GtG, MPRT,
  sample-and-hold, overdrive…) and explain what each experiment will tell
  us before I run it.
- Deep-stack fear is real: every change you propose needs a revert path
  and a blast-radius note. Prefer runtime-toggleable experiments
  (settings, sysprops) over persistent changes.

## The device (verified facts)

- Daylight DC-1 "LivePaper": **transflective TN LCD**, **IGZO oxide TFT**
  backplane, no color filter array (monochrome, 256 gray levels), 8-bit,
  1600×1200, 10.5″, ~190 PPI. E-paper-class but a real LCD — no e-ink
  refresh flashing.
- **Variable refresh rate 30–120 Hz** (IGZO's low leakage enables low
  rates for power). In practice: possibly only useful to ~90 Hz; motion
  blur reported at 120 Hz. This is THE question.
- TN mode (fast for an LCD, but gray-to-gray response varies a lot by
  transition; viewing-angle gamma shifts). Transflective: read by ambient
  light by day, amber blue-free backlight at night.
- Reflective viewing means **backlight strobing / black-frame insertion is
  impossible by day** — there is no light source to strobe. Whatever
  motion clarity we get must come from real pixel settling and refresh —
  keep this constraint front and center; it kills a whole family of
  standard motion-blur fixes.
- SoC: MediaTek Helio G99. OS: Sol:OS, Daylight's own Android 13 — we
  control the framework, SurfaceFlinger, and kernel. Android's VRR
  machinery (display modes, `Surface.setFrameRate`, `peak_refresh_rate`
  settings) is available to us.
- Motion culture: the club's design language is "living paper" — brief,
  physical motion (a page settles, a lid lifts), never looping. Scrolling
  and pen input are the motion cases that matter most, not video/games.

## The physics to untangle (teach me as we go)

Perceived blur on a sample-and-hold LCD has two independent sources:

1. **Pixel response (GtG)** — how long the liquid crystal takes to settle
   between two gray levels. TN GtG is famously non-uniform: mid-gray ↔
   mid-gray transitions can be several times slower than black↔white. If
   typical GtG ≳ one frame time, raising refresh doesn't sharpen motion —
   frames overlap in the crystal and can *add* smear (my 120 Hz
   observation may be exactly this).
2. **Hold-type blur (MPRT)** — the eye tracks moving content while each
   frame is held; blur ∝ hold time, so higher refresh helps ONLY if
   pixels actually settle within the frame.

So the crossover question: **at what refresh does GtG stop keeping up?**
That's the "practical ceiling." Overdrive (voltage boosting transitions,
usually a DDIC LUT) can push GtG down — find out if our DDIC supports it,
how it's tuned, and whether it's tuned per refresh rate (mistuned
overdrive at 120 Hz would also explain artifacts). Also mind: response
time may differ between reflective-day and backlit-night operation only
via temperature (LC slows dramatically when cold — worth documenting for
an outdoor device), and 8-bit gray on a panel where we practically
distinguish ~9–16 levels means overshoot artifacts are visible fast.

## What I can give you

1. **The panel datasheet PDF** (uploading into this thread). Extract and
   tabulate: GtG response-time matrix (which transitions, at what
   temperature), rise/fall definitions, supported refresh range and VRR
   granularity, overdrive support, DDIC part number(s), interface timing,
   any monochrome-specific driving notes. Flag every spec that contradicts
   what we observe.
2. **adb access** to a real DC-1 (read-only first; settings experiments
   after we agree on revert paths).
3. **240 fps iPhone slow-motion video** of the panel running test
   patterns you give me, plus photos.
4. Relay to engineers/vendors.

## Phase plan

**Phase 0 — inventory (start here):**
- Datasheet extraction (above).
- What the OS exposes today, read-only:
  `adb shell dumpsys display | grep -iE "mode|fps|refresh|render"` (the
  supported-mode list + active mode), `adb shell settings get system
  peak_refresh_rate`, `min_refresh_rate`, `dumpsys SurfaceFlinger --list`
  scan for frame-rate flexibility, `getprop | grep -iE "fps|refresh|vrr"`.
- Establish which refresh rates Sol:OS actually uses when: idle, reading,
  scrolling, pen writing, video.

**Phase 1 — measure the crossover (cheap, decisive):**
- You give me self-contained HTML test patterns (TestUFO-style: a moving
  bar/checker at fixed px/frame, plus a gray-transition toggler that
  flips regions between chosen gray pairs at frame rate — remember mid-gray
  pairs, not just black/white). I run them fullscreen in Chrome at forced
  30/60/90/120 Hz (give me the settings commands + revert commands).
- I film each at 240 fps and photograph the moving bar; you analyze ghost
  trail length in frames → effective GtG per transition, per refresh rate,
  and whether 120 Hz frames overlap in the crystal.
- Deliverable: a measured "motion budget" table — at each refresh rate,
  which gray transitions settle in-frame — and the evidence-backed answer
  to "is 120 Hz real on this panel, and where's the true ceiling?"

**Phase 2 — policy and fixes:**
- OS refresh policy: recommended default, VRR ladder per content
  (reading/idle low for battery, pen at max-that-settles, scrolling at the
  measured sweet spot), implemented via Sol:OS display-mode config +
  `setFrameRate` guidance — with A/B toggles.
- Overdrive: if the DDIC supports it, what re-tuning to request per rate;
  if mistuned OD explains the 120 Hz blur, the vendor ask writes itself.
- Design guidance to feed the club's design skill: max comfortable scroll
  speeds, which gray pairs to avoid animating between, "living paper"
  motion durations that fit measured settling.
- Honest spec language for marketing ("30–120 Hz VRR; motion-clear to
  X Hz" — whatever the data says).

**Phase 3 — vendor conversation**, armed with our measurements: overdrive
tuning per refresh rate, GtG at realistic temperatures, and whatever the
datasheet promised that the panel doesn't deliver.

## Rank every proposal

Keep a running table: what it does · perceptual win · effort · risk/revert
· our control vs vendor · what YOU can build/verify yourself (test
patterns, analysis scripts, settings harnesses — yes; anything touching
kernel/DDIC ships with a 10-minute safe-test-and-rollback recipe — always).

## Deliverables

1. Datasheet digest (specs that matter, in plain English).
2. The measured motion-budget table + the practical-ceiling answer.
3. Recommended Sol:OS refresh/VRR policy with toggleable implementation.
4. Vendor question list (overdrive, GtG, VRR granularity).
5. A short "motion on paper" section for the club's design system:
   what animation the panel genuinely rewards, and what to avoid.
