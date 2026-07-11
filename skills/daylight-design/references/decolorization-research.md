# Color→grayscale: what the literature actually says

Distilled from a deep research pass (2026-07-11): 26 sources fetched, 129
claims extracted, 25 adversarially verified 3-vote each — 23 confirmed, 2
refuted. This file records what's *known* so future remixers of
`daylight-map` don't re-derive or un-learn it. Primary sources at the end.

## The headline results (all 3-0 verified)

1. **The only large independent perceptual study** (Cadík 2008: 7 methods,
   24 images, 119 subjects, ~20,328 two-alternative comparisons) ranked:
   **Decolorize** (Grundland & Dodgson 2007) best overall (z=0.544),
   **Apparent Greyscale** (Smith et al. 2008) best on *accuracy* (z=0.487,
   statistically tied), then plain **CIE Y luminance** (0.158) — which beat
   Gooch's Color2Gray (0.149), Rasche05 (−0.203), Neumann07 (−0.317), and
   Bala04 (−0.819). Humbling: most published optimization machinery loses
   to plain luminance; only two methods reliably beat it.
2. **No method wins universally** — and the split is exactly our two
   regimes: Decolorize-style image-dependent global mapping excels on
   *narrow-gamut, few-color images* (the profile of app UIs); Smith's
   H-K-corrected mapping wins on *colorful wide-gamut images* (photos).
3. **Global beats local for UI, unambiguously.** Spatially-varying methods
   buy per-image salience at documented costs: the same color mapping to
   different grays in different places, halo artifacts, phantom gradients
   (Color2Gray's own Fig. 7), temporal flicker. For app theming, use a
   global deterministic mapping; image-dependence (choosing the mapping
   per palette) is acceptable, spatial dependence is not. Safest for
   app-wide consistency is an image-INDEPENDENT global step (Smith's).
4. **The Helmholtz–Kohlrausch correction has a closed form.** Smith et al.
   use Nayatani's 1997 VAC chromatic lightness in CIELUV:
   `L*_NVAC = L*·(1 + [−0.1340·q(θ) + 0.0872·K_Br]·s_uv)` with
   `s_uv = 13·√((u′−u′c)² + (v′−v′c)²)`, `θ = atan2(v′−v′c, u′−u′c)`,
   `K_Br = 0.2717·(6.469 + 6.362·L_a^0.4495)/(6.469 + L_a^0.4495)`,
   default `L_a = 20 cd/m²`; q(θ) is a 9-term Fourier series (coefficients
   in `daylight-map.mjs`, verified against the colour-science reference
   implementation). The effect is hue-dependent — strong for blues and
   red-magentas, negligible for yellows — so a constant "chroma lift" is
   wrong. The ~2×-stronger VCC variant and Fairchild's L** were both
   *rejected* in print for compressing the output range.
5. **Color2Gray's real value for us is the palette case.** Its exact
   formulation (CIELAB; signed pair targets `δ_ij` via
   `crunch(x) = α·tanh(x/α)`, convex least-squares) is O(S⁴) per-pixel —
   impractical — but collapses to a tiny solve when the constraint set is
   just palette pairs. That's the verified-in-print ancestry of
   daylight-map's palette optimizer.
6. **Lu–Xu–Jia 2012** (the basis of OpenCV `decolor()`): a global degree-2
   polynomial over 9 monomials {r,g,b,rg,rb,gb,r²,g²,b²} with a *bimodal*
   objective — gray differences may take either polarity as long as the
   magnitude matches the color contrast. Useful idea we reuse: strict
   lightness-order preservation is *not* sacred when hues tie (that
   freedom is how five same-lightness hues get spread).
7. **Decolorize's published practical default is λ = 0.3** (the 0.5 used
   in the evaluation exaggerates contrast) — `daylight-map`'s
   chroma-axis boost uses 0.3 accordingly.

## How daylight-map maps onto this

| Literature | In the tool |
|---|---|
| Smith's global H-K step (accuracy winner) | `hkLightness()` — Nayatani VAC, exact formula |
| Decolorize's dominant-chromatic-axis idea, λ=0.3 | continuous mode's chroma-axis boost |
| Color2Gray restricted to palette pairs | palette mode's pairwise-stress optimizer |
| Lu–Xu–Jia's polarity relaxation | order penalty waived for equal-apparent-lightness colors |
| Cadík's narrow-gamut/wide-gamut split | the auto palette/continuous regime switch |

**Where we're ahead of the verified literature** (the research flags this
as an open question, i.e. plausibly novel): none of the published methods
is *quantization- or device-curve-aware*. daylight-map optimizes
separations measured **through the DC-1's response curve** (crushed dark
end, ~9–16 usable levels) rather than in L*. If a real dish proves this
matters, it's worth a little write-up for the community.

## Refuted while researching (don't re-import these errors)

- The 2022 survey's performance details for Lu et al.'s "30 ms O(1)
  real-time variant" did not verify (1-2), and its characterization of
  Color2Gray/Decolorize as "global linear" methods is flat wrong (0-3).
  Treat that survey's per-method summaries with care; its taxonomy is fine.

## Verified-empty zones (honest gaps, good remix targets)

- **OKLab vs CAM16 vs CIELUV for this task**: unaddressed in print — the
  classic papers predate OKLab (2020). The tool uses OKLab for *distances*
  and CIELUV+Nayatani for *apparent lightness*; nothing verified says
  that's optimal, only that each piece is standard in its role.
- **E-ink industry practice** (Boox App Optimization's bleach/enhance
  modes, Kindle pipelines, dithering at 16 levels): sources were found but
  no claims survived verification. Worth a hands-on session with a Boox.
- **Accessibility/CVD and grayscale-safe dataviz palette literature**
  (Palettailor et al. were located but unverified) — the palette
  optimizer's closest cousins; a future pass should verify and borrow.
- **All perceptual rankings were measured on emissive displays.** A
  reflective panel with a compressed dark end may reorder them; Nayatani's
  object-color vs luminous-color variant choice and the right `L_a` for
  ambient-lit paper are open — `site/calibrate.html` readings will help.

## Primary sources

- Cadík 2008, *Perceptual Evaluation of Color-to-Grayscale Image
  Conversions* — cadik.posvete.cz/color_to_gray_evaluation/
- Grundland & Dodgson 2007, *Decolorize* — Pattern Recognition 40(11);
  tech report UCAM-CL-TR-649; eyemaginary.com/Portfolio/TurnColorsGray.html
- Smith, Landes, Thollot, Myszkowski 2008, *Apparent Greyscale* —
  Eurographics; inria.hal.science/inria-00255958
- Gooch et al. 2005, *Color2Gray* — SIGGRAPH; users.cs.northwestern.edu/~ago820/color2gray/
- Lu, Xu, Jia 2012/2014, *Contrast Preserving Decolorization* — ICCP/IJCV;
  OpenCV `decolor()`; MIT reference impl: github.com/atilimcetin/rtcprgb2gray
- Nayatani 1997, *Simple estimation methods for the H-K effect* — Color
  Research & Application 22(6); reference impl: colour-science `hke.py`
