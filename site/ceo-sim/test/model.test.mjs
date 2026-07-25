// The Decider — headless model tests. No dependencies: `node model.test.mjs`
// These assertions ENCODE THE PEDAGOGY. If a tuning change breaks one, the
// lesson broke, not the test.
import { readFileSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

const here = dirname(fileURLToPath(import.meta.url));
(0, eval)(readFileSync(join(here, '..', 'model.js'), 'utf8'));
const S = globalThis.CEOSIM;

let failures = 0;
const ok = (cond, msg) => {
  console.log((cond ? '  ✓ ' : '  ✗ ') + msg);
  if (!cond) failures++;
};

function run(mode, { seed = 42, chooser = null, dialFn = null } = {}) {
  const g = S.makeGame(mode, seed);
  let ci = 0, next = S.CARD_WEEKS[mode] || Infinity;
  while (!g.over && g.week < 300) {
    if (dialFn) g.dial = dialFn(g);
    S.tick(g);
    if (!g.over && g.week >= next && ci < g.deck.length) {
      const c = g.deck[ci++]; next = g.week + (S.CARD_WEEKS[mode] || Infinity);
      const choice = chooser ? chooser(c, g)
        : mode === 'A' || (mode === 'D' && c.stakes === 'HIGH') ? 'take' : 'del';
      S.applyCard(g, c, choice);
    }
  }
  return g;
}

const crit = c => c.oneWay && c.stakes === 'HIGH';

console.log('scenario endings land where the story needs them:');
ok(run('A').ended === 'A' && run('A').week >= 85 && run('A').week <= 100, 'A: the Bottleneck dies ~wk 92 (board coup era)');
ok(run('B').ended === 'B' && run('B').week >= 95 && run('B').week <= 110, 'B: the Ghost dies ~wk 105 (after the board line)');
ok(run('D').ended === 'D' && run('D').week > 195, 'D: the Final Say fades all four years');
ok(run('E').ended === 'E' && run('E').week > 195, 'E: the Conductor fades all four years');

console.log('the Balance grades judgment, not luck:');
ok(run('C', { chooser: c => crit(c) ? 'take' : 'del' }).ended === 'C_WIN', 'perfect sorting wins');
ok(run('C', { chooser: c => crit(c) ? 'memo' : 'del' }).ended === 'C_WIN', 'memo-first on heavy calls also wins');
ok(run('C', { chooser: () => 'del' }).ended === 'C_LOSE', 'delegate-everything loses');
ok(run('C', { chooser: () => 'take' }).ended === 'C_LOSE', 'hoard-everything loses');
{
  // falls for every trap card (reads the surface, not the substance)
  const surface = c => (c.tricky ? !crit(c) : crit(c)) ? 'take' : 'del';
  ok(run('C', { chooser: surface }).ended === 'C_LOSE', 'surface-reading the trap cards loses');
  let slipped = 0;
  const oneSlip = c => { if (crit(c) && c.tricky && !slipped++) return 'del'; return crit(c) ? 'take' : 'del'; };
  ok(run('C', { chooser: oneSlip }).ended === 'C_WIN', 'a single trap slip is survivable (with a scar)');
}

console.log('the capstone rewards attention, not a fixed philosophy:');
ok(run('F', { dialFn: g => S.targetDial(g.week) }).ended === 'F_WIN', 'tracking the drift wins');
ok(run('F', { dialFn: g => Math.round(S.targetDial(g.week) * 4) / 4 }).ended === 'F_WIN', 'coarse-but-attentive wins');
{
  const fixed = run('F', { dialFn: () => 0.5 });
  ok(fixed.ended !== 'F_WIN', 'a fixed dial never wins (got ' + fixed.ended + ')');
  ok(run('F', { dialFn: () => 0.1 }).ended === 'F_LOSE', 'fixed-low runs aground');
  ok(run('F', { dialFn: g => S.targetDial(Math.max(0, g.week - 25)) }).ended === 'F_DRIFT', 'correcting 25 weeks late = survival with scars');
}

console.log('seeds and structure:');
{
  const d1 = S.makeGame('C', 7).deck.map(c => c.id).join();
  ok(d1 === S.makeGame('C', 7).deck.map(c => c.id).join(), 'same seed → same deck');
  ok(d1 !== S.makeGame('C', 8).deck.map(c => c.id).join(), 'different seed → different deck');
  ok(S.CARDS.filter(c => c.tricky).length >= 6, 'at least six trap cards in the deck');
  ok(S.SORT_TEST.length === 6, 'sorting test has six items');
  const g = run('A');
  ok(g.fireWeeks.length >= 3, 'fires are recorded for the chart (' + g.fireWeeks.length + ')');
  ok(g.keyMoments.length >= 8, 'majors collected for the front page');
  const llamas = [...(S.makeGame('A', 1).script)].filter(s => s.tag === 'llama').length;
  ok(llamas === 1, 'events carry semantic tags (llama in A)');
}

console.log(failures ? '\n' + failures + ' FAILURE(S)' : '\nall green');
process.exit(failures ? 1 : 0);
