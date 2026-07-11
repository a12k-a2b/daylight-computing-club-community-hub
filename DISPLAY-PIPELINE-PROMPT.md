# Prompt: color→gray across the DC-1's entire display stack

Copy everything below the line into a fresh Claude Code conversation.

---

## Mission

You're working with Anjan (Daylight Computer's founder) to answer one big
question across the DC-1's entire display stack: **how should color become
gray on this device — and at which layer?** Today, color content renders
naively onto a monochrome reflective LCD. We believe there is real quality
on the table at every level: app, OS graphics pipeline, Android framework,
kernel display driver, display controller (DDIC), and panel driving scheme.
Your job over a long back-and-forth conversation: establish ground truth
about the current pipeline, then propose, rank, prototype, and verify
improvements — writing and testing real code where possible, and preparing
precise asks for third parties (display controller vendor) where not.

## How to work with me (the conversational contract)

- This is a multi-session collaboration, not a one-shot answer. Work in
  phases; ask me for ONE piece of ground truth at a time — I can run adb
  commands on a real DC-1, photograph the panel, pull files, and relay
  questions to our display/kernel engineers and the DDIC vendor.
- I'm not a display engineer. Explain findings plainly, define acronyms on
  first use, and tell me exactly what to type/photograph when you need data.
- Be honest about uncertainty. Several "facts" below are my fuzzy
  recollections, marked **[unverified]** — your first job is to
  confirm or kill them, not to build castles on them.
- We are terrified of deep-stack bugs we can't anticipate. Every proposal
  must come with: a revert path, a blast-radius estimate, and a way to A/B
  it against stock behavior. Prefer runtime-toggleable changes (sysprops,
  DRM properties, settings) over baked-in ones.

## The device (verified facts)

- Daylight DC-1: 10.5″ "LivePaper" monochrome **reflective LCD** (RLCD, not
  e-ink), 1600×1200 (4:3), ~190 PPI, 60 Hz, no color filter array — the
  panel shows grayscale only. 256 gray levels driven; in practice eyes
  resolve ~9–16 levels in ambient light, fewest in the dark quarter (the
  dark end compresses badly).
- Reflective: lit by ambient light by day. The backlight (front-light) is
  **amber / blue-free** for night use — with it on, the whole image is
  amber-tinted.
- SoC: MediaTek Helio G99. OS: Sol:OS, Daylight's own Android 13 build —
  **we control SurfaceFlinger, the framework, and the kernel**; a newer
  Android base is planned.
- Contrast behavior we model in software (tuned by eye, awaiting hardware
  measurement): effective range ≈ sRGB 48–228 with a shadow-crushing
  gamma ≈ 1.35 by day; worse under amber night light.

## Current pipeline, as I understand it (**verify all of this first**)

- Android renders normal color frames; color data is fed to the display
  controller. **[unverified]** I believe the DDIC currently uses **only the
  red channel** of the incoming pixel data to drive the mono panel.
- **[unverified]** I don't actually know whether Sol:OS applies a
  grayscale color transform (e.g., a SurfaceFlinger saturation-0 matrix)
  before the framebuffer, or whether the "red channel only" behavior IS our
  de facto color→gray mapping. These have wildly different implications:
  if gray = R with no upstream transform, then pure blue renders near-black
  and pure red renders near-white today.
- **[unverified]** The panel is currently driven by **two DDICs** (driver
  chips). A driving scheme has been proposed to us: if we change how the
  Android framebuffer packs pixels — using multiple channels per DDIC pixel
  instead of just the red channel — **one DDIC could drive the whole panel
  instead of two.** (Presumably: a color DDIC has R/G/B source outputs per
  pixel; a mono panel could map three adjacent mono columns onto them, so
  one chip covers 3× the columns — if the framebuffer/DSI stream delivers
  three logical gray pixels packed into each RGB word. I don't fully
  understand this; help me pin down what was actually proposed.)

## Verified research you should NOT re-derive (adversarially fact-checked, 3-vote verification per claim)

1. **Cadík 2008** — the only large independent perceptual study of
   color-to-gray conversions (7 methods, 24 images, 119 subjects, ~20,328
   2AFC comparisons): Grundland & Dodgson's global "Decolorize" ranked
   best overall (z=0.544), Smith et al.'s H-K-corrected "Apparent
   Greyscale" best on accuracy (z=0.487, statistically tied) — and plain
   CIE Y luminance (0.158) beat every other optimization method including
   Gooch's Color2Gray. Two lessons: good global mapping is the ceiling
   worth chasing, and luminance is a strong baseline.
2. **Global beats local/spatial for UI, unambiguously.** Spatially-varying
   ("contextual") methods cause the same color to map to different grays
   in different places, halos, phantom gradients, temporal flicker. For an
   OS-wide mapping: one deterministic global function. (Image-dependent
   mappings are fine per-image; an OS should be image-INDEPENDENT.)
3. **The Helmholtz–Kohlrausch effect** (saturated colors look brighter
   than their luminance — strongly for blues/magentas, negligibly for
   yellows) has a verified closed form used by the accuracy-winning
   method: Nayatani 1997 VAC chromatic lightness in CIELUV:
   `L*_NVAC = L*·(1 + [−0.1340·q(θ) + 0.0872·K_Br]·s_uv)`,
   `s_uv = 13·√((u′−u′c)²+(v′−v′c)²)`, `θ = atan2(v′−v′c, u′−u′c)`,
   `K_Br = 0.2717·(6.469 + 6.362·L_a^0.4495)/(6.469 + L_a^0.4495)`,
   q(θ) = −0.01585 − 0.03017cosθ − 0.04556cos2θ − 0.02667cos3θ
   − 0.00295cos4θ + 0.14592sinθ + 0.05084sin2θ − 0.01900sin3θ
   − 0.00764sin4θ. Note `L_a` is the **adapting luminance** — it differs
   between a sunlit reflective panel and amber night light, which gives a
   principled basis for *different day and night mappings*.
   Caveat: this is nonlinear and hue-dependent — a 3×3 color matrix can
   only approximate it; a LUT or shader can do it exactly.
4. **Nobody in the verified literature is quantization- or
   device-curve-aware** — optimizing gray assignments through the panel's
   actual response curve (crushed dark end, ~9–16 usable levels) appears
   to be open ground. We already do this at the app level in our community
   tools; doing it at the display level would be novel and defensible.
5. **Daylight's own design system already hand-calibrated brand colors to
   grays on this hardware** — Morning Yellow `#FFC70D`→`#CECECE`, Hardware
   Amber `#FF9D00`→`#9D9D9E`, Sunset Orange `#FC6900`→`#6C6C6D`, and a
   Sol:OS neutral scale (primary ink `#1A1A1A`, secondary `#535353`,
   tertiary `#858585`, disabled `#CCCCCC`). Treat these as ground-truth
   perceptual targets: any OS-level mapping should reproduce or beat them.
   Sol:OS night mode is a dark THEME (black ground, white text, amber
   accents), not a filter.

## The specific ideas to explore (from me — make sense of them)

- **The Ricoh red-filter idea.** A friend with a monochrome Ricoh/B&W
  camera says a red lens filter improved his images, and asked whether the
  DC-1 could "use red as the primary values rather than RGB" in its
  grayscale mapping. I think there's an insight here I don't fully grasp.
  My understanding of your job: formalize it. In B&W photography, choosing
  a filter IS choosing the spectral weighting — red filters darken blue
  skies and lift warm tones, which photographers often prefer to flat
  panchromatic response. For a display mapping, the analog is the RGB→gray
  weight vector: (1,0,0) = "red filter", Rec.709 luma = "panchromatic",
  and the verified H-K formula = "what human vision actually reports."
  Questions: Is our current de facto mapping literally (1,0,0)? Is that
  accidentally *good* for some content (warm photos) and terrible for
  others (blue UI elements vanish dark... or light)? What weight vector —
  or better, what LUT — should the OS default be, per the research above?
  Design the experiment that settles it (see Experiment 1).
- **The amber backlight interplay.** At night everything is amber. Is
  there anything smart to do in the mapping when the backlight is on — a
  different gamma/LUT (the H-K formula's `L_a` parameter changes),
  compensating the perceptual shift, or aligning how red/warm content maps
  so it harmonizes with the amber light instead of muddying? What does the
  physics actually allow on a mono panel + amber front-light, and what's
  placebo?
- **The one-DDIC driving scheme.** Work through the proposal above: what
  exactly would need to change at the framebuffer/composition/DSI level to
  pack three logical mono pixels into one RGB pixel word; whether the
  MediaTek display pipeline can emit such a buffer (a final GPU
  composition pass? DSI pixel-format tricks?); what the DDIC must support;
  what breaks (timing, partial updates, tearing, DPI metadata, screenshots,
  screen recording); and what we'd ask the DDIC vendor. Deliverable: a
  feasibility memo + the sharpest possible list of vendor questions —
  ideally with a software proof-of-concept packing shader they can see.

## Map the stack, layer by layer

For each layer: what runs there today (find out), what could be changed,
what it buys, what it risks, who controls it.

1. **App/web level** — already covered by our community skill pack
   (palette optimization, H-K-aware tools). Treat as the fallback that
   always works; not this conversation's focus.
2. **Android framework / SurfaceFlinger** — color transform matrices
   (accessibility daltonizer, `persist.sys.sf.color_saturation`, night
   display CTM), per-display color modes, RenderEngine. A saturation-0
   matrix with *chosen luma weights* is the cheapest OS-wide lever; a
   custom RenderEngine/RenderEffect shader could do full nonlinear H-K.
   We own this code.
3. **Hardware composer (HWC) / MediaTek display pipeline (MDP/PQ,
   "MiraVision")** — MTK SoCs have color-processing engines with tuning
   parameters; find out what the G99 exposes (LUTs, CCM, gamma) and what
   Sol:OS currently sets.
4. **Kernel DRM/KMS** — DEGAMMA_LUT / CTM / GAMMA_LUT properties per CRTC:
   the natural home for (a) the RGB→gray weighting, (b) **panel-response
   linearization** — counteracting the crushed dark end so the 256 driven
   levels are perceptually even — and (c) a night-mode LUT swap keyed to
   the backlight. We own the kernel.
5. **DSI interface / framebuffer format** — pixel packing (the one-DDIC
   scheme), bit depth actually delivered to the panel.
6. **DDIC** — its own gamma/register init (set via DSI init sequence in
   the panel driver — findable and editable in our kernel), channel usage,
   any mono-specific modes. Changes here range from "edit init sequence"
   (our control) to "vendor firmware/mask change" (their control).
7. **Panel + backlight physics** — measured tone response by day and
   under amber light (we have a calibration page and can photograph
   gray-step patterns), which anchors everything above.

## Phase plan

**Phase 0 — ground truth (start here, one item per message):**
- `adb shell screencap` while colorful content is on screen: is the
  captured buffer color or gray? (Color capture + gray panel ⇒ conversion
  happens at/below HWC or in the DDIC; gray capture ⇒ SurfaceFlinger-level.)
- **Experiment 1 (the decisive one):** fullscreen pure-red, pure-green,
  pure-blue, and white test pages; photograph the panel for each under
  identical daylight. The relative brightness of R/G/B vs white reveals
  the effective end-to-end RGB→gray weights. If red ≈ white and blue ≈
  black, the "red channel only, no upstream transform" belief is confirmed.
- `dumpsys SurfaceFlinger` (color transforms, color modes), `getprop`
  scan for color/saturation/PQ properties, HWC info.
- Kernel: panel driver source in our tree (DSI init sequence, DDIC part
  number), `/sys/class/drm` CRTC properties (does the pipeline expose
  CTM/GAMMA_LUT?), backlight sysfs node (for night-state hooks).
- From me/vendor: DDIC datasheets, the two-DDIC wiring, the original
  one-DDIC proposal in whatever form it exists.
- Panel measurement: I'll photograph our gray-step calibration page
  (daylightcomputer.club/calibrate.html) by day and under amber light so
  you can fit the real tone curve.

**Phase 1 — cheap software wins (build, A/B, measure):**
candidate order — (a) correct luma weights or LUT at the
SurfaceFlinger/DRM level (panchromatic baseline, then H-K-approximating);
(b) panel-response linearization LUT (de-crush the dark end);
(c) backlight-keyed night LUT with H-K `L_a` adjustment;
(d) a developer toggle + screenshot/photo A/B harness for all of it.

**Phase 2 — prototypes needing more surgery:** full nonlinear H-K via
RenderEngine shader or 3D LUT if the pipeline has one; per-theme mapping
hooks for Sol:OS.

**Phase 3 — third-party asks:** DDIC vendor questions (mono gamma, channel
modes, the packing scheme), armed with our measurements and a
proof-of-concept.

## Rank every proposal in a running table

Columns: layer · what it does · perceptual win (vs the verified research)
· effort · risk/blast-radius · revert path · **our control vs vendor
dependency** · what YOU (Claude) can build/test/verify yourself, and how.
Keep it updated as facts land. Bias toward: measurable, revertible, ours.

## What you can and can't verify yourself — be explicit

You can: write test APKs/pages, shaders, kernel patches, DRM property
scripts, adb harnesses, photo-based measurement scripts (I photograph, you
analyze), and simulation against the tone curve we fit. You cannot: run
code on the panel yourself, or know timing-level DSI behavior without the
datasheet — so every hardware-touching step must ship with a "how Anjan
safely tests this in 10 minutes, and how we roll back" section. Nothing
lands without an A/B photo comparison.

## Deliverables

1. A ground-truth document of the actual current pipeline (with evidence).
2. The ranked intervention table.
3. Prototype kit for Phase 1 (code + test/revert instructions).
4. The measured panel tone curve, day and amber-night.
5. A one-DDIC feasibility memo + vendor question list.
6. A plain-English explanation I can share with the team of what "correct"
   color→gray means on this device — the red-filter story, resolved.
