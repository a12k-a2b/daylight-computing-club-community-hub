// The Decider — browser end-to-end test.
//   1. serve the site:  python3 -m http.server 8931 --directory site
//   2. run:             DC1_CHROMIUM=/path/to/chromium node browser.test.mjs
// Needs playwright installed wherever you run it from (npm i playwright).
import { chromium } from 'playwright';

const BASE = process.env.DECIDER_URL || 'http://localhost:8931/ceo-sim/';
const exe = process.env.DC1_CHROMIUM;

const browser = await chromium.launch(exe ? { executablePath: exe } : {});
const page = await browser.newPage();
const errors = [];
// app errors only — navigation aborts (e.g. the iso.html stub redirect
// cancelling in-flight requests) are network noise, not defects
page.on('console', m => {
  if (m.type() === 'error' && !/net::|Failed to load resource/.test(m.text())) errors.push(m.text());
});
page.on('pageerror', e => errors.push(String(e)));

let failures = 0;
const ok = (cond, msg) => { console.log((cond ? '  ✓ ' : '  ✗ ') + msg); if (!cond) failures++; };

await page.goto(BASE, { waitUntil: 'networkidle' });
await page.evaluate(() => localStorage.clear());
await page.reload({ waitUntil: 'networkidle' });

// ---- sorting test: baseline saves once and locks ----
await page.click('#btnTest');
const answer = (rights) => page.evaluate(n => {
  document.querySelectorAll('#testRows .testrow').forEach((row, i) => {
    const [me, them] = row.querySelectorAll('.tbtns button');
    const truth = CEOSIM.SORT_TEST[i].ceo;
    (i < n ? (truth ? me : them) : (truth ? them : me)).click();
  });
  document.getElementById('testSubmit').click();
}, rights);
await answer(3);
ok(/baseline saved and locked/.test(await page.$eval('#testScoreLine', e => e.textContent)), 'baseline saved and locked');
await page.click('#testClose');
await page.click('#btnTest');
await answer(6);
ok(/practice round/.test(await page.$eval('#testScoreLine', e => e.textContent)), 'retake before winning C is a practice round');
ok((await page.evaluate(() => JSON.parse(localStorage.getItem('dcc-ceosim-test-pre')).score)) === 3, 'baseline still 3/6 after a 6/6 practice');
await page.click('#testClose');

// ---- forced scenarios are single-click per memo ----
const runForced = () => page.evaluate(() => {
  let choiceClicks = 0;
  for (let i = 0; i < 600 && SIM.state() && !SIM.state().over; i++) {
    SIM.step(1);
    const o = document.getElementById('cardOverlay');
    if (!o.hidden) {
      if (!document.getElementById('cardChoices').hidden) choiceClicks++;   // should never happen
      document.getElementById('cardContinue').click();
    }
  }
  return { week: SIM.state().week, ended: SIM.state().ended, choiceClicks };
});
await page.click('#btnA');
const a = await runForced();
ok(a.ended === 'A' && a.choiceClicks === 0, 'A runs to its ending with zero choice clicks (' + JSON.stringify(a) + ')');
await page.waitForSelector('#endOverlay:not([hidden])');
ok(!(await page.$eval('#fpMore', d => d.open)), 'full post-mortem starts collapsed');
ok((await page.$eval('#fpKeyLesson', e => e.textContent.length)) > 20, 'key lesson shown up top');

// Escape dismisses the front page to inspect the wreckage
await page.keyboard.press('Escape');
ok(await page.$eval('#endOverlay', e => e.hidden), 'Escape dismisses the ending');
ok(!(await page.$eval('#reopenEnd', e => e.hidden)), 'floating FRONT PAGE button appears');
await page.click('#reopenEnd');

// ---- B, D, E via the guided buttons ----
await page.click('#fpButtons button.primary');
ok((await runForced()).ended === 'B', 'B ends');
await page.waitForSelector('#endOverlay:not([hidden])');
for (const name of ['FINAL SAY', 'CONDUCTOR']) {
  const i = await page.$$eval('#fpButtons button', (bs, n) => bs.findIndex(b => b.textContent.includes(n)), name);
  await page.evaluate(i => document.querySelectorAll('#fpButtons button')[i].click(), i);
  const r = await runForced();
  ok(r.week > 195, name + ' runs the full fade (wk ' + r.week + ')');
  await page.waitForSelector('#endOverlay:not([hidden])');
}

// ---- C: real choices, hidden stamps, memo, calibration ----
const cBtn = await page.$$eval('#fpButtons button', bs => bs.findIndex(b => /OPTION C/.test(b.textContent)));
await page.evaluate(i => document.querySelectorAll('#fpButtons button')[i].click(), cBtn);
const c = await page.evaluate(() => {
  let hidden = false, memo = 0;
  for (let i = 0; i < 700 && SIM.state() && !SIM.state().over; i++) {
    SIM.step(1);
    const o = document.getElementById('cardOverlay');
    if (!o.hidden && !document.getElementById('cardChoices').hidden) {
      if (document.getElementById('stampStakes').textContent.includes('?')) hidden = true;
      const card = SIM.card(), crit = card.oneWay && card.stakes === 'HIGH';
      const memoBtn = document.getElementById('chooseMemo');
      if (crit && memoBtn && !memoBtn.hidden && memo < 2) { memo++; SIM.choose('memo'); }
      else SIM.choose(crit ? 'take' : 'del');
      document.getElementById('cardContinue').click();
    } else if (!o.hidden) document.getElementById('cardContinue').click();
  }
  return { ended: SIM.state().ended, hidden, memo };
});
ok(c.ended === 'C_WIN' && c.hidden && c.memo === 2, 'C: hidden stamps, memo used, win (' + JSON.stringify(c) + ')');
await page.waitForSelector('#endOverlay:not([hidden])');
ok(/YOUR CALIBRATION/.test(await page.$eval('#fpCalib', e => e.textContent)), 'calibration mirrored in the lead');

// ---- post test unlocks and records the delta ----
const menu = await page.$$eval('#fpButtons button', bs => bs.findIndex(b => /menu/.test(b.textContent)));
await page.evaluate(i => document.querySelectorAll('#fpButtons button')[i].click(), menu);
await page.click('#btnTest');
await answer(6);
ok(/before the game you scored 3\/6/.test(await page.$eval('#testScoreLine', e => e.textContent)), 'post-test reports the honest delta');
await page.click('#testClose');

// ---- F with the real dial buttons; ending reveals the chart ----
await page.click('#btnF');
await page.waitForSelector('#dialRow:not([hidden])');
const f = await page.evaluate(() => {
  for (let i = 0; i < 700 && SIM.state() && !SIM.state().over; i++) {
    const g = SIM.state(), want = CEOSIM.targetDial(g.week);
    if (g.dial < want - 0.03) SIM.dial(0.05); else if (g.dial > want + 0.03) SIM.dial(-0.05);
    SIM.step(1);
    const d = document.getElementById('debriefOverlay');
    if (d && !d.hidden) document.getElementById('debriefResume').click();
  }
  return SIM.state().ended;
});
ok(f === 'F_WIN', 'attentive dial play wins the capstone');
await page.waitForSelector('#endOverlay:not([hidden])');
ok(!(await page.$eval('#fpDialWrap', e => e.hidden)), 'dial-reveal chart shown');

// ---- view toggle mid-game ----
await page.evaluate(() => {
  SIM.start('A'); SIM.step(10);
  const o = document.getElementById('cardOverlay');
  if (!o.hidden) document.getElementById('cardContinue').click();
  document.getElementById('btnPause').click();   // hold the tape while we fiddle with the skin
});
await page.click('#btnView');
ok(!(await page.$eval('#classicBoard', e => e.hidden)) && (await page.$eval('#isoCanvas', e => e.hidden)), 'CHART view swaps in mid-game');
await page.evaluate(() => {
  SIM.step(10);
  const o = document.getElementById('cardOverlay');
  if (!o.hidden) document.getElementById('cardContinue').click();
});
await page.click('#btnView');
ok(await page.$eval('#classicBoard', e => e.hidden), 'ISO view swaps back');

// ---- debrief + custom questions ----
await page.goto(BASE + '?debrief=1&dq=WHAT DO YOU SEE', { waitUntil: 'networkidle' });
await page.click('#btnA');
await page.evaluate(() => { for (let i = 0; i < 30; i++) { SIM.step(1); const o = document.getElementById('cardOverlay'); if (!o.hidden) document.getElementById('cardContinue').click(); if (!document.getElementById('debriefOverlay').hidden) break; } });
ok((await page.$eval('#debriefQ', e => e.textContent)) === 'WHAT DO YOU SEE', 'custom debrief question via &dq=');

// ---- the stub redirect and the field guide ----
await page.goto(BASE + 'iso.html', { waitUntil: 'networkidle' });
ok(/view=iso|\?view=iso/.test(page.url()) || await page.evaluate(() => !!document.getElementById('titleScreen')), 'iso.html forwards to the merged page');
await page.goto(BASE + 'lessons.html', { waitUntil: 'networkidle' });
ok(/Teaching with The Decider/.test(await page.evaluate(() => document.body.textContent)), 'field guide has the teaching section');

console.log('CONSOLE ERRORS:', errors.length ? errors : 'none');
if (errors.length) failures++;
await browser.close();
console.log(failures ? failures + ' FAILURE(S)' : 'all green');
process.exit(failures ? 1 : 0);
