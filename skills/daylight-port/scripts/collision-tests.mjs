#!/usr/bin/env node
// collision-tests — the daylight-port checklist as an executable harness.
//
// Drives a web app through the behaviors that most often break on the DC-1
// and reports pass / warn / fail per check. It exercises what desktop
// Chromium can honestly prove (rotation reflow, offline relaunch, back
// navigation, overscroll/touch CSS, input hygiene); what needs a real
// device or the WebView shell it says so instead of pretending.
//
// Usage:
//   npm i playwright     # once
//   node collision-tests.mjs <url> [--strict] [--no-offline]
//
// --strict      exit 1 if any FAIL (for CI)
// --no-offline  skip the offline-relaunch check (e.g. dev servers that
//               hold the SW, or apps destined for the shell where assets
//               ship inside the APK)

import { chromium } from 'playwright';
import { readFileSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

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
const [PW, PH] = FACTS?.panel?.resolution ?? [1600, 1200];
const DPR = FACTS?.viewport?.devicePixelRatio?.value ?? 1.25;
const LAND = { width: Math.round(PW / DPR), height: Math.round(PH / DPR) };
const PORT = { width: LAND.height, height: LAND.width };

const args = process.argv.slice(2);
const url = args.find(a => !a.startsWith('--'));
if (!url) { console.error('usage: node collision-tests.mjs <url> [--strict] [--no-offline]'); process.exit(2); }
const strict = args.includes('--strict');
const skipOffline = args.includes('--no-offline');

const results = [];
const add = (level, name, msg) => results.push({ level, name, msg });

const browser = await chromium.launch(process.env.DC1_CHROMIUM ? { executablePath: process.env.DC1_CHROMIUM } : {});
const context = await browser.newContext({
  viewport: LAND, deviceScaleFactor: DPR, isMobile: true, hasTouch: true,
  userAgent: 'Mozilla/5.0 (Linux; Android 13; DC-1) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
});
const page = await context.newPage();
let pageErrors = [];
page.on('pageerror', e => pageErrors.push(String(e)));
page.on('console', m => { if (m.type() === 'error') pageErrors.push(m.text()); });

const overflowX = () => page.evaluate(() =>
  document.scrollingElement.scrollWidth > window.innerWidth + 1);

// ---- 1. load ----
try {
  await page.goto(url, { waitUntil: 'load', timeout: 45000 });
  await page.waitForTimeout(1200);
  if (pageErrors.length) add('fail', 'clean load', `${pageErrors.length} console/page error(s): ${pageErrors[0].slice(0, 120)}`);
  else add('pass', 'clean load', 'no console or page errors');
} catch (e) {
  add('fail', 'clean load', `page did not load: ${String(e).slice(0, 140)}`);
  console.log(render()); process.exit(1);
}

// ---- 2. viewport meta ----
const meta = await page.evaluate(() =>
  document.querySelector('meta[name=viewport]')?.getAttribute('content') ?? null);
if (!meta) add('fail', 'viewport meta', 'missing — page renders desktop-scaled on the tablet');
else {
  add('pass', 'viewport meta', meta);
  if (!/interactive-widget=resizes-content/.test(meta))
    add('warn', 'keyboard resize hint', 'add interactive-widget=resizes-content so the soft keyboard shrinks the layout instead of covering inputs');
  if (/user-scalable\s*=\s*no|maximum-scale\s*=\s*1(\.0+)?\b/.test(meta))
    add('warn', 'zoom lockout', 'user-scalable=no / maximum-scale=1 blocks accessibility zoom — prefer touch-action: manipulation');
}

// ---- 3. rotation reflow ----
const ofL = await overflowX();
await page.setViewportSize(PORT);
await page.waitForTimeout(600);
const ofP = await overflowX();
const errsAfterRotate = pageErrors.length;
await page.setViewportSize(LAND);
await page.waitForTimeout(400);
if (ofL || ofP) add('fail', 'rotation reflow', `horizontal overflow in ${ofL ? 'landscape' : ''}${ofL && ofP ? ' and ' : ''}${ofP ? 'portrait' : ''}`);
else add('pass', 'rotation reflow', 'no overflow in either orientation');
if (pageErrors.length > errsAfterRotate) add('warn', 'rotation errors', 'console errors fired while resizing');

// ---- 4. scroll containment (pull-to-refresh / glow) ----
const overscroll = await page.evaluate(() => {
  const h = getComputedStyle(document.documentElement).overscrollBehaviorY;
  const b = getComputedStyle(document.body).overscrollBehaviorY;
  return h !== 'auto' || b !== 'auto';
});
if (overscroll) add('pass', 'overscroll contained', 'overscroll-behavior set — pull-to-refresh and edge glow tamed');
else add('warn', 'overscroll contained', 'html/body overscroll-behavior is auto — Chrome pull-to-refresh can wipe SPA state');

// ---- 5. touch feel CSS ----
const touch = await page.evaluate(() => {
  const els = [...document.querySelectorAll('button, [role=button], a[href]')].slice(0, 40);
  const selectable = els.filter(el => {
    const s = getComputedStyle(el);
    return s.userSelect !== 'none' && (el.textContent || '').trim();
  }).length;
  const ta = getComputedStyle(document.body).touchAction;
  return { n: els.length, selectable, touchAction: ta };
});
if (touch.n) {
  if (touch.selectable > touch.n / 2)
    add('warn', 'UI text selectable', `${touch.selectable}/${touch.n} buttons/links let long-press start text selection — user-select: none on chrome, keep content selectable`);
  else add('pass', 'UI text selectable', 'most interactive chrome is not selectable');
}
add(touch.touchAction.includes('manipulation') || touch.touchAction.includes('none') ? 'pass' : 'warn',
  'double-tap zoom', touch.touchAction.includes('manipulation') || touch.touchAction.includes('none')
    ? 'touch-action set'
    : 'body touch-action is auto — rapid taps may trigger double-tap zoom; set touch-action: manipulation');

// ---- 6. back navigation ----
const link = await page.evaluate(() => {
  const a = [...document.querySelectorAll('a[href]')].find(a =>
    a.origin === location.origin &&
    a.getAttribute('href') &&
    !a.getAttribute('href').startsWith('#') &&
    a.pathname !== location.pathname);
  return a ? a.getAttribute('href') : null;
});
if (link) {
  const before = page.url();
  try {
    await Promise.all([page.waitForLoadState('load'), page.click(`a[href="${link}"]`)]);
    await page.waitForTimeout(600);
    await page.goBack({ waitUntil: 'load' });
    await page.waitForTimeout(600);
    const backOk = page.url().split('#')[0] === before.split('#')[0];
    const alive = await page.evaluate(() => document.body && document.body.children.length > 0);
    if (backOk && alive) add('pass', 'back navigation', 'navigate in → back returns to a live page');
    else add('fail', 'back navigation', `back landed on ${page.url()} ${alive ? '' : '(empty body)'}`);
  } catch (e) {
    add('warn', 'back navigation', `couldn't drive it automatically (${String(e).slice(0, 80)}) — test by hand: back must step through the app, exit only from the root`);
  }
} else {
  add('warn', 'back navigation', 'no same-origin links found to drive — if the app has modals/screens, verify they push history states so Android back closes them (not the app)');
}

// ---- 7. offline relaunch (the DC-1 leaves the house) ----
if (skipOffline) {
  add('warn', 'offline relaunch', 'skipped (--no-offline) — if this ships as a PWA it still needs a service worker; shell APKs are offline by construction');
} else {
  const hasSW = await page.evaluate(async () =>
    'serviceWorker' in navigator && !!(await navigator.serviceWorker.getRegistration()));
  await page.waitForTimeout(hasSW ? 2500 : 0); // let a fresh SW finish precaching
  await context.setOffline(true);
  try {
    await page.reload({ waitUntil: 'load', timeout: 20000 });
    const alive = await page.evaluate(() =>
      document.body && document.body.innerText.trim().length > 0);
    if (alive) add('pass', 'offline relaunch', `page renders with the network gone${hasSW ? ' (service worker)' : ''}`);
    else add('fail', 'offline relaunch', 'page loads but renders empty offline');
  } catch {
    add(hasSW ? 'fail' : 'warn', 'offline relaunch',
      hasSW ? 'service worker registered but offline reload failed — check its fetch handler/precache'
            : 'no service worker — as a PWA this app dies without Wi-Fi; add one, or ship as a shell APK with assets bundled');
  }
  await context.setOffline(false);
}

// ---- 8. inputs & files (reminders where automation can't reach) ----
const io = await page.evaluate(() => ({
  textInputs: [...document.querySelectorAll('input[type=text], input:not([type]), textarea')].length,
  noInputmode: [...document.querySelectorAll('input[type=text], input:not([type])')]
    .filter(i => !i.getAttribute('inputmode') && !i.getAttribute('autocomplete')).length,
  fileInputs: document.querySelectorAll('input[type=file]').length,
  downloads: document.querySelectorAll('a[download]').length,
}));
if (io.textInputs > 3) add('warn', 'typing load', `${io.textInputs} free-text inputs — typing on glass is the DC-1's worst input; consider chips/voice (daylight-design pass 3)`);
if (io.noInputmode) add('warn', 'input hints', `${io.noInputmode} text input(s) without inputmode/autocomplete`);
if (io.fileInputs || io.downloads) add('warn', 'files on device', `${io.fileInputs} file input(s), ${io.downloads} download link(s) — verify on device; a bare WebView drops both (the shell template wires them). blob: downloads need the JS bridge`);

await browser.close();

// ---- report ----
function render() {
  const icon = { pass: '✓', warn: '·', fail: '✗' };
  let out = `collision-tests — ${url}\nviewport ${LAND.width}×${LAND.height} @ ${DPR}x\n\n`;
  for (const r of results) out += `  ${icon[r.level]} ${r.level.toUpperCase().padEnd(5)} ${r.name}: ${r.msg}\n`;
  const fails = results.filter(r => r.level === 'fail').length;
  const warns = results.filter(r => r.level === 'warn').length;
  out += `\n${fails} fail, ${warns} warn, ${results.filter(r => r.level === 'pass').length} pass`;
  out += '\nNot automatable from a desk — check on a DC-1 or emulator: real keyboard overlap, long-press feel, stylus, Play Protect flow, wake lock.';
  return out;
}
console.log(render());
if (strict && results.some(r => r.level === 'fail')) process.exit(1);
