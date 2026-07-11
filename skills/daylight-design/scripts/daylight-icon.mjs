#!/usr/bin/env node
// daylight-icon — launcher icons that read in grayscale, for people who
// won't open a design tool. Give it a glyph (a letter, a word's initial,
// an emoji if your system has fonts for it) and it emits the full Android/
// PWA icon set in club style: bold ink on paper, no color-coded meaning.
//
//   node daylight-icon.mjs "K" --out site/apps/myapp/icons
//   node daylight-icon.mjs "🔑" --serif --out icons
//
// Emits: icon-512.png, icon-192.png, icon-maskable-512.png (safe-zone
// padded), icon-mono-512.png (black-on-transparent, Android 13 themed
// icon layer). Requires: npm i playwright.

import { chromium } from 'playwright';
import { mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const args = process.argv.slice(2);
const glyph = args.find(a => !a.startsWith('--'));
if (!glyph) { console.error('usage: node daylight-icon.mjs <glyph> [--out dir] [--serif]'); process.exit(2); }
const outI = args.indexOf('--out');
const outDir = outI >= 0 ? args[outI + 1] : './icons';
const family = args.includes('--serif') ? 'Georgia, serif' : 'system-ui, sans-serif';

const browser = await chromium.launch(process.env.DC1_CHROMIUM ? { executablePath: process.env.DC1_CHROMIUM } : {});
const page = await (await browser.newContext()).newPage();

const pngs = await page.evaluate(async ({ glyph, family }) => {
  function draw(size, { scale, bg, ink, transparent }) {
    const cv = document.createElement('canvas');
    cv.width = cv.height = size;
    const cx = cv.getContext('2d');
    if (!transparent) { cx.fillStyle = bg; cx.fillRect(0, 0, size, size); }
    cx.fillStyle = ink;
    cx.textAlign = 'center';
    cx.textBaseline = 'alphabetic';
    // fit the glyph: binary-search a font size whose bounding box fits scale%
    let lo = 8, hi = size * 1.4, fs = size * 0.6;
    for (let i = 0; i < 18; i++) {
      fs = (lo + hi) / 2;
      cx.font = `bold ${fs}px ${family}`;
      const m = cx.measureText(glyph);
      const w = m.width;
      const h = (m.actualBoundingBoxAscent || fs * 0.8) + (m.actualBoundingBoxDescent || fs * 0.2);
      if (Math.max(w, h) > size * scale) hi = fs; else lo = fs;
    }
    cx.font = `bold ${lo}px ${family}`;
    const m = cx.measureText(glyph);
    const asc = m.actualBoundingBoxAscent || lo * 0.8, desc = m.actualBoundingBoxDescent || lo * 0.2;
    cx.fillText(glyph, size / 2, size / 2 + (asc - desc) / 2);
    // force grayscale — emoji glyphs render in color; the shelf is paper
    const d = cx.getImageData(0, 0, size, size), px = d.data;
    for (let i = 0; i < px.length; i += 4) {
      const l = 0.2126 * px[i] + 0.7152 * px[i + 1] + 0.0722 * px[i + 2];
      // darken midtones so the mark stays bold on the reflective panel
      const v = l < 200 ? Math.max(0, l * 0.55) : l;
      px[i] = px[i + 1] = px[i + 2] = v;
    }
    cx.putImageData(d, 0, 0);
    return cv.toDataURL('image/png').split(',')[1];
  }
  return {
    'icon-512.png':          draw(512, { scale: 0.68, bg: '#ffffff', ink: '#000000' }),
    'icon-192.png':          draw(192, { scale: 0.68, bg: '#ffffff', ink: '#000000' }),
    'icon-maskable-512.png': draw(512, { scale: 0.50, bg: '#ffffff', ink: '#000000' }), // safe zone: central 80% circle
    'icon-mono-512.png':     draw(512, { scale: 0.68, ink: '#000000', transparent: true }),
  };
}, { glyph, family });

mkdirSync(outDir, { recursive: true });
for (const [name, b64] of Object.entries(pngs)) writeFileSync(join(outDir, name), Buffer.from(b64, 'base64'));
await browser.close();
console.log(`icons for "${glyph}" → ${outDir}/ (512, 192, maskable, mono)`);
console.log('manifest snippet:');
console.log(JSON.stringify({ icons: [
  { src: 'icons/icon-192.png', sizes: '192x192', type: 'image/png' },
  { src: 'icons/icon-512.png', sizes: '512x512', type: 'image/png' },
  { src: 'icons/icon-maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
  { src: 'icons/icon-mono-512.png', sizes: '512x512', type: 'image/png', purpose: 'monochrome' },
] }, null, 2));
