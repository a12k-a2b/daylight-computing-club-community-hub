#!/usr/bin/env node
// dc1-preview — see any web app the way a Daylight DC-1 will show it.
//
// Renders a URL at the DC-1's screen geometry (10.5" 4:3, 1600×1200 panel),
// saves three screenshots — the original, a daylight LivePaper simulation,
// and a night (amber backlight) simulation — and audits the page for the
// jank that hurts most on this device: small touch targets, low effective
// contrast after grayscale mapping, tiny text, horizontal overflow, and
// console errors.
//
// Usage:
//   node dc1-preview.mjs <url> [options]
//
// Options:
//   --out <dir>        output directory (default: ./dc1-preview)
//   --portrait         960×1280 CSS viewport instead of landscape 1280×960
//   --dpr <n>          devicePixelRatio to emulate (default 1.25; measure the
//                      real device with `window.devicePixelRatio` and pass it)
//   --posterize        also save a 12-level posterized image — roughly how
//                      many gray steps the panel resolves in practice
//   --full             full-page screenshots instead of one viewport
//   --strict           exit 1 if any audit fails (for CI)
//
// Requires: `npm i playwright` (any recent version). If your Chromium lives
// somewhere unusual, set DC1_CHROMIUM=/path/to/chromium.

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync, readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Canonical device constants live in the pack's daylight-facts.json —
// correct THAT file (ideally from a real device via site/calibrate.html)
// and every tool recalibrates. Fallback defaults keep a lone copy of this
// script working.
function loadFacts() {
  for (const start of [dirname(fileURLToPath(import.meta.url)), process.cwd()]) {
    let dir = start;
    for (let i = 0; i < 6; i++) {
      const f = join(dir, 'daylight-facts.json');
      if (existsSync(f)) { try { return JSON.parse(readFileSync(f, 'utf8')); } catch { /* fall through */ } }
      const up = dirname(dir);
      if (up === dir) break;
      dir = up;
    }
  }
  return null;
}
const FACTS = loadFacts();

// ---------- CLI ----------

const args = process.argv.slice(2);
const url = args.find(a => !a.startsWith('--'));
if (!url) {
  console.error('usage: node dc1-preview.mjs <url> [--out dir] [--portrait] [--dpr n] [--posterize] [--full] [--strict]');
  process.exit(2);
}
const opt = name => {
  const i = args.indexOf(`--${name}`);
  return i >= 0 ? (args[i + 1] ?? true) : undefined;
};
const outDir = typeof opt('out') === 'string' ? opt('out') : './dc1-preview';
const portrait = args.includes('--portrait');
const dpr = Number(opt('dpr')) || FACTS?.viewport?.devicePixelRatio?.value || 1.25;
const posterize = args.includes('--posterize');
const fullPage = args.includes('--full');
const strict = args.includes('--strict');

// DC-1 panel: 1600×1200. CSS viewport = panel / dpr.
const [pw, ph] = FACTS?.panel?.resolution ?? [1600, 1200];
const panel = portrait ? { w: ph, h: pw } : { w: pw, h: ph };
const viewport = { width: Math.round(panel.w / dpr), height: Math.round(panel.h / dpr) };

// ---------- LivePaper simulation curve ----------
// A reflective LCD has no light of its own: "white" is paper-bright at best,
// "black" is a dark gray, and the darkest quarter of the ramp squeezes into
// nearly one tone. We model that as: luminance → shadow-crushing gamma →
// compressed output range → paper tint. Numbers are tuned by eye against the
// device, not measured; treat them as a good pessimist, not ground truth.
const CURVES = {
  day:   FACTS?.curve?.day   ?? { gamma: 1.35, floor: 48, ceil: 228, tint: [1.0, 0.975, 0.90] },  // ambient light, cream paper
  night: FACTS?.curve?.night ?? { gamma: 1.45, floor: 40, ceil: 200, tint: [1.0, 0.72, 0.38] },   // amber backlight, blue-free
};
const PRACTICAL_LEVELS = FACTS?.panel?.gray_levels_practical?.value ?? 12;
const MIN_TARGET = FACTS?.input?.min_touch_target_css_px ?? 48;
const MIN_FONT = FACTS?.input?.min_body_font_css_px ?? 16;

function simulateGray(r, g, b, curve) {
  const lum = 0.2126 * r + 0.7152 * g + 0.0722 * b;
  const t = Math.pow(lum / 255, curve.gamma);
  return curve.floor + (curve.ceil - curve.floor) * t;
}

// WCAG-ish contrast between two colors AS RENDERED by the panel.
function dc1Contrast(fg, bg) {
  const c = CURVES.day;
  const lin = v => {
    const s = simulateGray(...v, c) / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const [l1, l2] = [lin(fg), lin(bg)].sort((a, b) => b - a);
  return (l1 + 0.05) / (l2 + 0.05);
}

// ---------- drive the page ----------

const executablePath = process.env.DC1_CHROMIUM || undefined;
const browser = await chromium.launch(executablePath ? { executablePath } : {});
const context = await browser.newContext({
  viewport,
  deviceScaleFactor: dpr,
  isMobile: true,
  hasTouch: true,
  userAgent: 'Mozilla/5.0 (Linux; Android 13; DC-1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
});
const page = await context.newPage();

const consoleErrors = [];
page.on('console', m => { if (m.type() === 'error') consoleErrors.push(m.text()); });
page.on('pageerror', e => consoleErrors.push(String(e)));
const failedRequests = [];
page.on('requestfailed', r => {
  const f = r.failure()?.errorText ?? '';
  if (!f.includes('ERR_ABORTED')) failedRequests.push(`${r.url()} — ${f}`);
});

await page.goto(url, { waitUntil: 'load', timeout: 45000 });
await page.waitForTimeout(1500); // let late JS settle

// ---------- audits ----------

const audit = await page.evaluate(({ minTarget, minFont }) => {
  const describe = el => {
    const id = el.id ? `#${el.id}` : '';
    const cls = el.classList.length ? `.${[...el.classList].slice(0, 2).join('.')}` : '';
    const text = (el.textContent || el.getAttribute('aria-label') || '').trim().slice(0, 40);
    return `<${el.tagName.toLowerCase()}${id}${cls}> ${text ? JSON.stringify(text) : ''}`.trim();
  };
  const visible = el => {
    const r = el.getBoundingClientRect();
    const s = getComputedStyle(el);
    return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none';
  };

  // 1. touch targets
  const interactive = [...document.querySelectorAll(
    'a[href], button, input, select, textarea, summary, [role="button"], [role="link"], [role="tab"], [role="checkbox"], [onclick]'
  )].filter(visible);
  const smallTargets = [];
  const inlineProseLinks = [];
  for (const el of interactive) {
    const r = el.getBoundingClientRect();
    if (r.width >= minTarget && r.height >= minTarget) continue;
    // padding on a parent <label> can rescue a small control; measure the clickable box itself
    if (el.matches('input[type=checkbox], input[type=radio]') && el.closest('label')) continue;
    // WCAG 2.5.8 exempts links inline in a sentence — still fiddly on a
    // tablet, so report them separately rather than as failures
    const s = getComputedStyle(el);
    const inline = s.display.startsWith('inline') && el.matches('a[href]');
    const proseParent = el.parentElement &&
      (el.parentElement.textContent || '').trim().length > (el.textContent || '').trim().length + 3;
    const item = { el: describe(el), w: Math.round(r.width), h: Math.round(r.height) };
    if (inline && proseParent) inlineProseLinks.push(item);
    else smallTargets.push(item);
  }

  // 2. text: size + computed colors (for contrast check back in Node)
  const parseColor = str => {
    const m = str.match(/rgba?\(([\d.]+)[, ]+([\d.]+)[, ]+([\d.]+)(?:[,/ ]+([\d.]+))?\)/);
    return m ? { r: +m[1], g: +m[2], b: +m[3], a: m[4] === undefined ? 1 : +m[4] } : null;
  };
  const opaqueBgOf = el => {
    for (let n = el; n; n = n.parentElement) {
      const c = parseColor(getComputedStyle(n).backgroundColor);
      if (c && c.a >= 0.9) return [c.r, c.g, c.b];
    }
    return [255, 255, 255];
  };
  const tinyText = [];
  const textPairs = [];
  const seen = new Set();
  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const el = node.parentElement;
    if (!el || seen.has(el) || !node.textContent.trim() || !visible(el)) continue;
    seen.add(el);
    const s = getComputedStyle(el);
    const size = parseFloat(s.fontSize);
    if (size < minFont) tinyText.push({ el: describe(el), px: size });
    const fg = parseColor(s.color);
    if (fg) {
      textPairs.push({
        el: describe(el), size, weight: +s.fontWeight || 400,
        fg: [fg.r, fg.g, fg.b], bg: opaqueBgOf(el),
      });
    }
    if (textPairs.length > 400) break; // enough sampling
  }

  // 3. layout
  const overflowX = document.scrollingElement.scrollWidth > window.innerWidth + 1;
  const viewportMeta = !!document.querySelector('meta[name=viewport]');

  return { smallTargets, inlineProseLinks, tinyText, textPairs, overflowX, viewportMeta };
}, { minTarget: MIN_TARGET, minFont: MIN_FONT });

const lowContrast = [];
const seenPair = new Set();
for (const p of audit.textPairs) {
  const ratio = dc1Contrast(p.fg, p.bg);
  const large = p.size >= 24 || (p.size >= 18.5 && p.weight >= 700);
  const need = large ? 3 : 4.5;
  const key = `${p.fg}|${p.bg}`;
  if (ratio < need && !seenPair.has(key)) {
    seenPair.add(key);
    lowContrast.push({ el: p.el, ratio: ratio.toFixed(2), need, fg: p.fg, bg: p.bg });
  }
}

// ---------- screenshots + simulation ----------

mkdirSync(outDir, { recursive: true });
const shot = await page.screenshot({ fullPage, type: 'png' });
writeFileSync(join(outDir, 'original.png'), shot);

async function transform(pngBuffer, curve, levels) {
  const worker = await context.newPage();
  const b64 = pngBuffer.toString('base64');
  const out = await worker.evaluate(async ({ b64, curve, levels }) => {
    const img = new Image();
    await new Promise((res, rej) => { img.onload = res; img.onerror = rej; img.src = `data:image/png;base64,${b64}`; });
    const cv = document.createElement('canvas');
    cv.width = img.width; cv.height = img.height;
    const cx = cv.getContext('2d');
    cx.drawImage(img, 0, 0);
    const d = cx.getImageData(0, 0, cv.width, cv.height);
    const px = d.data;
    for (let i = 0; i < px.length; i += 4) {
      const lum = 0.2126 * px[i] + 0.7152 * px[i + 1] + 0.0722 * px[i + 2];
      let t = Math.pow(lum / 255, curve.gamma);
      if (levels) t = Math.round(t * (levels - 1)) / (levels - 1);
      const v = curve.floor + (curve.ceil - curve.floor) * t;
      px[i] = v * curve.tint[0];
      px[i + 1] = v * curve.tint[1];
      px[i + 2] = v * curve.tint[2];
    }
    cx.putImageData(d, 0, 0);
    return cv.toDataURL('image/png').split(',')[1];
  }, { b64, curve, levels });
  await worker.close();
  return Buffer.from(out, 'base64');
}

writeFileSync(join(outDir, 'dc1-day.png'), await transform(shot, CURVES.day));
writeFileSync(join(outDir, 'dc1-night.png'), await transform(shot, CURVES.night));
if (posterize) writeFileSync(join(outDir, 'dc1-levels.png'), await transform(shot, CURVES.day, PRACTICAL_LEVELS));

await browser.close();

// ---------- report ----------

const problems = [];
const section = (title, items, fmt) => {
  console.log(`\n${title}`);
  if (!items.length) { console.log('  ✓ none'); return; }
  problems.push(title);
  for (const it of items.slice(0, 15)) console.log(`  ✗ ${fmt(it)}`);
  if (items.length > 15) console.log(`  … and ${items.length - 15} more`);
};

console.log(`dc1-preview — ${url}`);
console.log(`viewport ${viewport.width}×${viewport.height} CSS px @ ${dpr}x (panel ${panel.w}×${panel.h})`);
console.log(`screenshots → ${outDir}/original.png, dc1-day.png, dc1-night.png${posterize ? ', dc1-levels.png' : ''}`);

if (!audit.viewportMeta) problems.push('no <meta name=viewport> — page will render desktop-scaled and blurry');
console.log(`\nviewport meta: ${audit.viewportMeta ? '✓ present' : '✗ MISSING — page renders desktop-scaled'}`);
console.log(`horizontal overflow: ${audit.overflowX ? '✗ page scrolls sideways' : '✓ none'}`);
if (audit.overflowX) problems.push('horizontal overflow');

section(`touch targets under 48×48 CSS px (${audit.smallTargets.length})`, audit.smallTargets,
  t => `${t.w}×${t.h}  ${t.el}`);
if (audit.inlineProseLinks.length) {
  console.log(`\nsmall links inline in prose (${audit.inlineProseLinks.length}) — WCAG-exempt, but fiddly under a finger; consider making them standalone rows`);
  for (const t of audit.inlineProseLinks.slice(0, 8)) console.log(`  · ${t.w}×${t.h}  ${t.el}`);
}
section(`text under 16px (${audit.tinyText.length})`, audit.tinyText,
  t => `${t.px}px  ${t.el}`);
section(`low contrast ON THE DC-1 PANEL (${lowContrast.length}) — fine on your monitor ≠ fine on paper`, lowContrast,
  c => `${c.ratio}:1 (needs ${c.need}:1)  rgb(${c.fg}) on rgb(${c.bg})  ${c.el}`);
section(`console errors (${consoleErrors.length})`, consoleErrors, e => e.slice(0, 160));
section(`failed requests (${failedRequests.length})`, failedRequests, e => e.slice(0, 160));

console.log(`\n${problems.length ? `${problems.length} problem group(s) — open dc1-day.png and squint.` : 'clean — now open dc1-day.png and judge with your eyes.'}`);
if (strict && problems.length) process.exit(1);
