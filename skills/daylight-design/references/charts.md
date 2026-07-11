# Charts on paper — dataviz recipes for the DC-1

Color is how most charting defaults distinguish anything. On this panel you
get **pattern, weight, shape, position, and words**. Used well, these read
*better* than color — this is how print statisticians worked for a century.

## The rules that replace the palette

1. **Direct labels beat legends.** A legend is a color-matching puzzle;
   in grayscale it's unsolvable. Label each series at its line end (or
   inside its bar group). If labels can't fit, the chart is too dense for
   this screen — split it.
2. **Series ≤ 4 per chart.** Beyond that, no pattern system stays legible
   at arm's length. Split into small multiples instead — the 4:3 screen
   fits a 2×2 grid of small charts beautifully.
3. **Ink hierarchy**: data = `#000` at 2–3px; axes = `#000` 2px; gridlines =
   `#ccc` 1px (few of them); annotations = `#555`. The data must be the
   darkest, heaviest thing on the chart.
4. **No gradients, no soft shadows, no 3D.** Flat fills and hard edges only.

## Line charts

Differentiate series by **dash pattern + end-of-line label + shape marker**:

```svg
<path d="…" stroke="#000" stroke-width="3" fill="none"/>                 <!-- series 1: solid -->
<path d="…" stroke="#000" stroke-width="3" fill="none" stroke-dasharray="8 5"/>  <!-- series 2: dashed -->
<path d="…" stroke="#000" stroke-width="3" fill="none" stroke-dasharray="2 4"/>  <!-- series 3: dotted -->
<path d="…" stroke="#555" stroke-width="3" fill="none"/>                 <!-- series 4: mid-gray solid -->
```

Add markers at data points when lines cross often: ● ○ ■ △ (filled vs open
is a free extra channel). Dash patterns must differ in *rhythm*, not just
length — `8 5` vs `2 4` reads; `8 5` vs `6 5` doesn't.

## Bars & areas

Differentiate by **fill pattern**, defined once as SVG patterns:

```svg
<defs>
  <pattern id="hatch" width="7" height="7" patternTransform="rotate(45)" patternUnits="userSpaceOnUse">
    <rect width="7" height="7" fill="#fff"/><line x1="0" y1="0" x2="0" y2="7" stroke="#000" stroke-width="2.5"/>
  </pattern>
  <pattern id="dots" width="8" height="8" patternUnits="userSpaceOnUse">
    <rect width="8" height="8" fill="#fff"/><circle cx="4" cy="4" r="1.8" fill="#000"/>
  </pattern>
</defs>
<rect fill="#000"/>            <!-- series 1: solid ink -->
<rect fill="url(#hatch)" stroke="#000" stroke-width="2"/>  <!-- series 2 -->
<rect fill="url(#dots)"  stroke="#000" stroke-width="2"/>  <!-- series 3 -->
<rect fill="#fff" stroke="#000" stroke-width="2"/>         <!-- series 4: outline -->
```

Always stroke patterned/white bars with 2px ink so shapes hold. Order the
fills darkest→lightest to encode series rank for free.

## The rest of the zoo

- **Pie charts**: don't. Grayscale wedges are unreadable; use a labeled,
  sorted bar chart. (This was true in color too.)
- **Heatmaps**: ≤4 gray steps read reliably; beyond that switch encodings —
  dot size, dot density, or just print the numbers (a table with bold
  extremes is an underrated heatmap).
- **Status / KPI tiles**: shape + word, never a colored dot: ● on track,
  ◐ at risk, ○ blocked — each with its label.
- **Sparklines**: 2px ink, no fill, dot on the last value, current number
  printed beside it.
- **Stacked areas**: solid ink / hatch / white-outline segments with direct
  labels inside each band; ≤3 bands.
- **Scatter**: shape encodes category (● ○ ■ △), size encodes magnitude;
  outline overlapping markers in white (`stroke="#fff" stroke-width="2"`)
  so they separate.

## Checks

Run **daylight-preview** on the chart page: text sizes and effective
contrast get audited; then squint at `dc1-day.png` — can you follow each
series with a finger? If two series are only distinguishable by close
inspection, change one to a pattern from a different family (dash rhythm →
fill texture → marker shape), don't just tweak grays. `daylight-map`'s
palette mode can pick maximally-separated grays if you must have more than
4 solid-gray series — but at that point, small multiples.
