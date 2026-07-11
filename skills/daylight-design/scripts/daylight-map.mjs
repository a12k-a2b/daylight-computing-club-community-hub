#!/usr/bin/env node
// daylight-map — map a color palette to grays that stay distinguishable on
// the Daylight DC-1's LivePaper panel.
//
// Not naive desaturation: colors are compared in OKLab, saturated colors get
// a Helmholtz–Kohlrausch lightness lift (vivid red *looks* brighter than its
// luminance), and gray assignments are optimized in PANEL-EFFECTIVE space —
// the DC-1 crushes the dark quarter of the ramp, so separations are measured
// after the panel's response curve, not in sRGB numbers.
//
// Usage:
//   node daylight-map.mjs "#6366f1=primary" "#ef4444=danger" "#ffffff=bg" ...
//   node daylight-map.mjs --json palette.json
//   node daylight-map.mjs image in.png out.png [--method apparent|luminance]
//                         [--no-boost]        (image mode needs playwright)
//
// Options:
//   --pairs a:b,c:d   role pairs a user must tell apart AT A GLANCE
//                     (adjacent UI, chart series). Default: all pairs, weakly.
//   --text fg:bg,...  text-on-background pairs — enforced to ≥4.5:1
//                     effective contrast (WCAG ratio after the panel curve)
//   --ramp            snap results to the club 5-tone ramp
//                     (#000 #555 #999 #ccc #fff) and report what collapsed
//   --json <file>     read {"colors":[{"hex":"#..","role":".."}],
//                     "pairs":[["a","b"]], "text":[["fg","bg"]]}
//
// Output: a hex→gray table, the effective-contrast matrix for flagged pairs,
// and — where grayscale alone cannot carry a distinction — a concrete
// suggestion for a second channel (inversion, weight, pattern, icon).

import { readFileSync } from 'node:fs';

// ---------- sRGB ↔ OKLab (Björn Ottosson's constants) ----------

const srgbToLinear = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
const linearToSrgb = c => 255 * (c <= 0.0031308 ? 12.92 * c : 1.055 * c ** (1 / 2.4) - 0.055);

function hexToRgb(hex) {
  const h = hex.replace('#', '');
  const f = h.length === 3 ? h.split('').map(x => x + x).join('') : h;
  return [0, 2, 4].map(i => parseInt(f.slice(i, i + 2), 16));
}

function rgbToOklab([r8, g8, b8]) {
  const r = srgbToLinear(r8), g = srgbToLinear(g8), b = srgbToLinear(b8);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  return {
    L: 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
    a: 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
    b: 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
  };
}

// ---------- Helmholtz–Kohlrausch lift ----------
// Saturated colors look brighter than their luminance; when color collapses
// to gray, honor the APPARENT lightness so a vivid mid-red doesn't land in
// the same gray as a drab mid-gray-blue. K_HK is a pragmatic default —
// tune against the research notes in ../references/.
const K_HK = 0.18;
function apparentL(lab) {
  const chroma = Math.hypot(lab.a, lab.b);
  return Math.min(1, lab.L + K_HK * chroma);
}

// ---------- the panel's response (same model as dc1-preview) ----------
const PANEL = { gamma: 1.35, floor: 48, ceil: 228 };
const panelGray = v => PANEL.floor + (PANEL.ceil - PANEL.floor) * Math.pow(v, PANEL.gamma); // v in 0..1
function effContrast(v1, v2) { // WCAG ratio of two grays AS RENDERED
  const lin = v => {
    const s = panelGray(v) / 255;
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4;
  };
  const [hi, lo] = [lin(v1), lin(v2)].sort((a, b) => b - a);
  return (hi + 0.05) / (lo + 0.05);
}

// ---------- CLI ----------

const argv = process.argv.slice(2);
const flag = n => argv.includes(`--${n}`);
const optVal = n => { const i = argv.indexOf(`--${n}`); return i >= 0 ? argv[i + 1] : undefined; };

// ---------- image mode ----------
// Decolorize a picture for the panel. Two regimes, auto-detected:
//
//  FLAT-COLOR content (UI screenshots, kid-app art, icons, comics — a small
//  palette covers ~all pixels): cluster the actual palette, then spread the
//  clusters across the gray range by their FULL perceptual distance (OKLab
//  ΔE, where red↔green is huge even when luminance ties), preserving
//  apparent-lightness order. Pixels remap to their cluster's gray plus the
//  local lightness residual, so antialiasing and texture survive.
//  This is what saves saturated palettes — global per-pixel formulas cannot
//  separate five vivid hues that share the same lightness.
//
//  CONTINUOUS content (photos, gradient art): per-pixel apparent lightness
//  (H-K lift) + a global chroma boost along the image's dominant chromatic
//  axis (Grundland–Dodgson-style). Same color → same gray everywhere.
if (argv[0] === 'image') {
  const [, inPath, outPath] = argv;
  if (!inPath || !outPath) { console.error('usage: daylight-map.mjs image in.png out.png [--method auto|palette|apparent|luminance]'); process.exit(2); }
  const method = optVal('method') ?? 'auto';
  const { chromium } = await import('playwright');
  const executablePath = process.env.DC1_CHROMIUM || undefined;
  const browser = await chromium.launch(executablePath ? { executablePath } : {});
  const page = await (await browser.newContext()).newPage();
  const b64 = readFileSync(inPath).toString('base64');
  const kind = /\.jpe?g$/i.test(inPath) ? 'jpeg' : 'png';

  // shared in-page helpers, injected as a string so both passes can use them
  const HELPERS = `
    const toLin = c => { c /= 255; return c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4; };
    const oklab = (r8, g8, b8) => {
      const r = toLin(r8), g = toLin(g8), b = toLin(b8);
      const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
      const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
      const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
      return [0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
              1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
              0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s];
    };
    async function loadInto(b64, kind) {
      const img = new Image();
      await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = 'data:image/' + kind + ';base64,' + b64; });
      const cv = document.createElement('canvas');
      cv.width = img.width; cv.height = img.height;
      const cx = cv.getContext('2d');
      cx.drawImage(img, 0, 0);
      return { cv, cx, d: cx.getImageData(0, 0, cv.width, cv.height) };
    }`;

  // pass 1 — palette histogram (16 levels/channel bins, averaged)
  const hist = await page.evaluate(new Function('args', HELPERS + `
    return (async ({ b64, kind }) => {
      const { d } = await loadInto(b64, kind);
      const px = d.data, bins = new Map();
      for (let i = 0; i < px.length; i += 4) {
        const k = (px[i] >> 4) << 8 | (px[i + 1] >> 4) << 4 | (px[i + 2] >> 4);
        let b = bins.get(k);
        if (!b) bins.set(k, b = { n: 0, r: 0, g: 0, b: 0 });
        b.n++; b.r += px[i]; b.g += px[i + 1]; b.b += px[i + 2];
      }
      const total = px.length / 4;
      const top = [...bins.values()].sort((a, b) => b.n - a.n).slice(0, 64)
        .map(b => ({ n: b.n, r: b.r / b.n, g: b.g / b.n, b: b.b / b.n }));
      return { total, top };
    })(args)`), { b64, kind });

  // decide the regime: do ≤24 palette bins cover ≥88% of pixels?
  let clusters = [];
  { // merge near-identical bins (ΔE < 0.04), largest first
    for (const t of hist.top) {
      const lab = rgbToOklab([t.r, t.g, t.b]);
      const near = clusters.find(c => Math.hypot(c.lab.L - lab.L, c.lab.a - lab.a, c.lab.b - lab.b) < 0.04);
      if (near) { near.n += t.n; }
      else clusters.push({ n: t.n, rgb: [t.r, t.g, t.b], lab });
    }
    clusters = clusters.filter(c => c.n / hist.total > 0.001).slice(0, 24);
  }
  const coverage = clusters.reduce((s, c) => s + c.n, 0) / hist.total;
  const usePalette = method === 'palette' || (method === 'auto' && coverage >= 0.88);

  let out;
  if (usePalette) {
    // spread clusters over the gray range by cumulative perceptual distance,
    // in apparent-lightness order (chain 1-D MDS — deterministic, no seams)
    clusters.sort((a, b) => apparentL(a.lab) - apparentL(b.lab));
    const pos = [0];
    for (let i = 1; i < clusters.length; i++) {
      const p = clusters[i - 1], q = clusters[i];
      const dE = Math.hypot(p.lab.L - q.lab.L, p.lab.a - q.lab.a, p.lab.b - q.lab.b);
      // mass-weighted: separations between colors that dominate the image
      // deserve gray range; incidental colors (antialiasing, gradient
      // crumbs) shouldn't eat it
      const mass = Math.sqrt((p.n + q.n) / 2 / hist.total);
      pos.push(pos[i - 1] + Math.max(dE, 0.02) * (0.15 + mass));
    }
    const span = pos[pos.length - 1] || 1;
    // anchor the ends where the content actually lives: pure-white palettes
    // stay white; if nothing is near-black, don't force anything to ink
    const lo = Math.min(0.98, Math.max(0, apparentL(clusters[0].lab) - 0.05));
    const hi = clusters[clusters.length - 1].lab.L > 0.93 ? 1 : Math.min(1, apparentL(clusters[clusters.length - 1].lab) + 0.05);
    clusters.forEach((c, i) => { c.gray = lo + (hi - lo) * pos[i] / span; });

    console.log(`palette regime (coverage ${(coverage * 100).toFixed(1)}%, ${clusters.length} clusters):`);
    for (const c of clusters) {
      const hex = '#' + c.rgb.map(v => Math.round(v).toString(16).padStart(2, '0')).join('');
      console.log(`  ${hex} → ${toHexGray(c.gray)}  (${(100 * c.n / hist.total).toFixed(1)}% of pixels)`);
    }

    out = await page.evaluate(new Function('args', HELPERS + `
      return (async ({ b64, kind, clusters }) => {
        const { cv, cx, d } = await loadInto(b64, kind);
        const px = d.data;
        for (let i = 0; i < px.length; i += 4) {
          const [L, a, b] = oklab(px[i], px[i + 1], px[i + 2]);
          let best = 0, bd = 1e9;
          for (let c = 0; c < clusters.length; c++) {
            const q = clusters[c].lab;
            const dd = (L - q.L) ** 2 + (a - q.a) ** 2 + (b - q.b) ** 2;
            if (dd < bd) { bd = dd; best = c; }
          }
          const c = clusters[best];
          const v = Math.max(0, Math.min(1, c.gray + (L - c.lab.L))); // keep local detail
          px[i] = px[i + 1] = px[i + 2] = Math.round(255 * v);
        }
        cx.putImageData(d, 0, 0);
        return cv.toDataURL('image/png').split(',')[1];
      })(args)`), { b64, kind, clusters: clusters.map(c => ({ lab: c.lab, gray: c.gray })) });
  } else {
    const useLum = method === 'luminance';
    console.log(useLum ? 'luminance regime (forced)' : `continuous regime (palette coverage only ${(coverage * 100).toFixed(1)}%)`);
    out = await page.evaluate(new Function('args', HELPERS + `
      return (async ({ b64, kind, useLum, K_HK }) => {
        const { cv, cx, d } = await loadInto(b64, kind);
        const px = d.data, nPix = px.length / 4;
        const labs = new Float32Array(nPix * 3);
        let saa = 0, sbb = 0, sab = 0;
        for (let i = 0, j = 0; i < px.length; i += 4, j += 3) {
          const [L, a, b] = oklab(px[i], px[i + 1], px[i + 2]);
          labs[j] = L; labs[j + 1] = a; labs[j + 2] = b;
          saa += a * a; sbb += b * b; sab += a * b;
        }
        const theta = 0.5 * Math.atan2(2 * sab, saa - sbb);   // dominant chroma axis
        const ax = Math.cos(theta), ay = Math.sin(theta);
        const LAMBDA = 0.35;
        for (let i = 0, j = 0; i < px.length; i += 4, j += 3) {
          const L = labs[j], a = labs[j + 1], b = labs[j + 2];
          let v = useLum ? L
            : Math.min(1, L + K_HK * Math.hypot(a, b)) + LAMBDA * (a * ax + b * ay);
          px[i] = px[i + 1] = px[i + 2] = Math.round(255 * Math.max(0, Math.min(1, v)));
        }
        cx.putImageData(d, 0, 0);
        return cv.toDataURL('image/png').split(',')[1];
      })(args)`), { b64, kind, useLum, K_HK });
  }

  const { writeFileSync } = await import('node:fs');
  writeFileSync(outPath, Buffer.from(out, 'base64'));
  await browser.close();
  console.log(`→ ${outPath}`);
  process.exit(0);
}

function toHexGray(v) {
  return '#' + Math.round(v * 255).toString(16).padStart(2, '0').repeat(3);
}

let colors = [], mustPairs = [], textPairs = [];
if (optVal('json')) {
  const j = JSON.parse(readFileSync(optVal('json'), 'utf8'));
  colors = j.colors.map(c => ({ hex: c.hex, role: c.role ?? c.hex }));
  mustPairs = j.pairs ?? [];
  textPairs = j.text ?? [];
} else {
  for (const a of argv) {
    if (a.startsWith('--')) continue;
    if (a === optVal('pairs') || a === optVal('text') || a === optVal('json')) continue;
    const [hex, role] = a.split('=');
    if (/^#?[0-9a-fA-F]{3,6}$/.test(hex.replace('#', ''))) colors.push({ hex, role: role ?? hex });
  }
  const parsePairs = s => (s ?? '').split(',').filter(Boolean).map(p => p.split(':'));
  mustPairs = parsePairs(optVal('pairs'));
  textPairs = parsePairs(optVal('text'));
}
if (colors.length < 2) {
  console.error('usage: node daylight-map.mjs "#hex=role" "#hex=role" ... [--pairs a:b,..] [--text fg:bg,..] [--ramp]');
  process.exit(2);
}

const idx = Object.fromEntries(colors.map((c, i) => [c.role, i]));
for (const [a, b] of [...mustPairs, ...textPairs]) {
  if (!(a in idx) || !(b in idx)) { console.error(`unknown role in pair ${a}:${b}`); process.exit(2); }
}

// ---------- set up targets ----------

const labs = colors.map(c => rgbToOklab(hexToRgb(c.hex)));
const appL = labs.map(apparentL);

// pairwise perceptual distance (OKLab ΔE), normalized to the palette's max
const n = colors.length;
const dist = Array.from({ length: n }, () => new Array(n).fill(0));
let maxD = 0;
for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
  const d = Math.hypot(labs[i].L - labs[j].L, labs[i].a - labs[j].a, labs[i].b - labs[j].b);
  dist[i][j] = dist[j][i] = d;
  maxD = Math.max(maxD, d);
}

const mustSet = new Set(mustPairs.map(([a, b]) => [idx[a], idx[b]].sort().join(',')));
const textSet = textPairs.map(([a, b]) => [idx[a], idx[b]]);

// ---------- optimize gray values ----------
// g[i] ∈ [0,1] paper-space gray. Start from apparent lightness, then descend
// on a stress function measured through the panel curve.

const gray0 = appL.map(L => Math.max(0, Math.min(1, (L - 0.1) / 0.85)));
let g = gray0.slice();

// anchors: the lightest low-chroma color is paper, the darkest is ink
const chroma = labs.map(l => Math.hypot(l.a, l.b));
let bgI = -1, inkI = -1;
for (let i = 0; i < n; i++) {
  if (chroma[i] < 0.06 && labs[i].L > 0.9 && (bgI < 0 || labs[i].L > labs[bgI].L)) bgI = i;
  if (labs[i].L < 0.35 && (inkI < 0 || labs[i].L < labs[inkI].L)) inkI = i;
}
if (bgI >= 0) g[bgI] = 1;
if (inkI >= 0) g[inkI] = 0;

const EFF_RANGE = panelGray(1) - panelGray(0);
function stress(g) {
  let e = 0;
  for (let i = 0; i < n; i++) for (let j = i + 1; j < n; j++) {
    const sep = Math.abs(panelGray(g[i]) - panelGray(g[j])) / EFF_RANGE;
    const want = dist[i][j] / maxD;
    const must = mustSet.has([i, j].sort().join(','));
    const w = must ? 6 : 1;
    e += w * (sep - want) ** 2;
    // glance-distinct pairs need real separation, not just proportionality
    if (must && sep < 0.25) e += 12 * (0.25 - sep) ** 2;
  }
  // keep apparent-lightness ORDER (the app should still feel like itself)
  for (let i = 0; i < n; i++) for (let j = 0; j < n; j++) {
    if (appL[i] < appL[j] - 0.02 && g[i] > g[j]) e += 2 * (g[i] - g[j]) ** 2;
  }
  // text pairs must clear 4.5:1 effective
  for (const [f, b] of textSet) {
    const c = effContrast(g[f], g[b]);
    if (c < 4.5) e += 8 * (4.5 - c) ** 2;
  }
  return e;
}

// coordinate descent — tiny problem sizes, no need for anything fancier
let best = stress(g);
for (let round = 0; round < 200; round++) {
  let improved = false;
  for (let i = 0; i < n; i++) {
    if (i === bgI || i === inkI) continue;
    for (const step of [0.08, 0.03, 0.01]) {
      for (const dir of [1, -1]) {
        const old = g[i];
        g[i] = Math.max(0, Math.min(1, g[i] + dir * step));
        const e = stress(g);
        if (e < best - 1e-9) { best = e; improved = true; }
        else g[i] = old;
      }
    }
  }
  if (!improved) break;
}

// ---------- optional snap to the club ramp ----------

const RAMP = [0x00, 0x55, 0x99, 0xcc, 0xff].map(v => v / 255);
let snapped = null;
if (flag('ramp')) {
  snapped = g.map(v => RAMP.reduce((a, b) => Math.abs(b - v) < Math.abs(a - v) ? b : a));
}

// ---------- report ----------

const toHex = v => {
  const b = Math.round(v * 255);
  return '#' + b.toString(16).padStart(2, '0').repeat(3);
};
const secondChannel = (a, b) => {
  const roles = `${colors[a].role}/${colors[b].role}`;
  if (/select|active|primary/i.test(roles)) return 'inversion (black fill, white text) on the active one';
  if (/danger|error|delete|success|ok|warn/i.test(roles)) return 'icon + word (✗/✓/⚠) — never gray alone for meaning';
  if (/series|chart|line|cat/i.test(roles)) return 'patterns: solid vs dashed vs dotted, label the series directly';
  return 'border weight (2px vs none), bold text, or a shape marker';
};

console.log('daylight-map — palette → LivePaper grays (optimized in panel-effective space)\n');
console.log('role'.padEnd(14) + 'color'.padEnd(10) + 'gray'.padEnd(9) + (snapped ? 'ramp'.padEnd(9) : '') + 'apparent-L');
for (let i = 0; i < n; i++) {
  console.log(
    colors[i].role.padEnd(14) + colors[i].hex.padEnd(10) + toHex(g[i]).padEnd(9) +
    (snapped ? toHex(snapped[i]).padEnd(9) : '') + appL[i].toFixed(2)
  );
}

const finalG = snapped ?? g;
let warned = false;
console.log('\nchecks (contrast measured through the panel curve):');
for (const [f, b] of textSet) {
  const c = effContrast(finalG[f], finalG[b]);
  const ok = c >= 4.5;
  if (!ok) warned = true;
  console.log(`  ${ok ? '✓' : '✗'} text ${colors[f].role} on ${colors[b].role}: ${c.toFixed(2)}:1 ${ok ? '' : '(needs 4.5:1)'}`);
}
for (const key of mustSet) {
  const [i, j] = key.split(',').map(Number);
  const sep = Math.abs(panelGray(finalG[i]) - panelGray(finalG[j]));
  const ok = sep >= 0.22 * EFF_RANGE;
  if (!ok) warned = true;
  console.log(`  ${ok ? '✓' : '✗'} distinguish ${colors[i].role} vs ${colors[j].role}: Δ${Math.round(sep)} effective levels` +
    (ok ? '' : ` — gray alone won't carry this; add ${secondChannel(i, j)}`));
}
if (!textSet.length && !mustSet.size) console.log('  (no --text or --pairs given — only the table above)');
console.log(warned
  ? '\nSome distinctions need a second channel — see suggestions above and grayscale-mapping.md.'
  : '\nAll flagged distinctions survive the panel. Verify with daylight-preview.');
