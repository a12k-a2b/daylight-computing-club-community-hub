/* The Decider — UI, sound, and time-lapse driver. model.js does the math. */
(function () {
  'use strict';
  const S = window.CEOSIM;
  const $ = id => document.getElementById(id);
  // Alternate skins (iso.html) define window.BOARD before this file loads;
  // it takes over drawing the org view: {reset, render, fire, anchor}.
  const board = () => window.BOARD;
  // Safari private mode can throw on localStorage — never let a save break play.
  const store = (k, v) => { try { localStorage.setItem(k, v); } catch (e) { /* private mode */ } };
  const readStore = k => { try { return localStorage.getItem(k); } catch (e) { return null; } };
  const QS = new URLSearchParams(location.search);

  // Motion mode. LIVE animates continuously — and the DC-1's LivePaper is a
  // fast 60–120fps RLCD, so motion genuinely looks good there. PAPER redraws
  // once per simulated week: a calm time-lapse of stills, kinder to the
  // battery, and the respectful default under prefers-reduced-motion.
  let paperMotion = QS.get('motion') ? QS.get('motion') === 'paper'
    : readStore('dcc-ceosim-motion') ? readStore('dcc-ceosim-motion') === 'paper'
    : !!(window.matchMedia && matchMedia('(prefers-reduced-motion: reduce)').matches);
  function applyMotion() {
    window.PAPER_MOTION = paperMotion;
    document.body.classList.toggle('papermode', paperMotion);
    const b = $('btnMotion');
    if (b) {
      b.textContent = paperMotion ? 'PAPER' : 'LIVE';
      b.setAttribute('aria-pressed', String(paperMotion));
    }
    if (board() && window.SIM && SIM.state()) board().render(SIM.state());
  }

  // ------------------------------------------------------------- audio
  // Tiny synth: everything is beeps, thuds, paper, and one sad trombone.
  const FX = {
    ctx: null,
    muted: readStore('dcc-ceosim-mute') === '1',
    ensure() {
      if (!this.ctx) {
        const AC = window.AudioContext || window.webkitAudioContext;
        if (AC) this.ctx = new AC();
      }
      if (this.ctx && this.ctx.state === 'suspended') this.ctx.resume();
      return this.ctx;
    },
    tone(freq, t0, dur, type, vol, glideTo) {
      const c = this.ctx, o = c.createOscillator(), g = c.createGain();
      o.type = type || 'sine';
      o.frequency.setValueAtTime(freq, t0);
      if (glideTo) o.frequency.linearRampToValueAtTime(glideTo, t0 + dur);
      g.gain.setValueAtTime(0.0001, t0);
      g.gain.linearRampToValueAtTime(vol || 0.08, t0 + 0.015);
      g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);
      o.connect(g).connect(c.destination);
      o.start(t0); o.stop(t0 + dur + 0.05);
    },
    noise(t0, dur, vol, freq) {
      const c = this.ctx, n = Math.floor(c.sampleRate * dur),
        buf = c.createBuffer(1, n, c.sampleRate), d = buf.getChannelData(0);
      for (let i = 0; i < n; i++) d[i] = (Math.random() * 2 - 1) * (1 - i / n);
      const src = c.createBufferSource(); src.buffer = buf;
      const f = c.createBiquadFilter(); f.type = 'bandpass'; f.frequency.value = freq || 2000;
      const g = c.createGain(); g.gain.value = vol || 0.1;
      src.connect(f).connect(g).connect(c.destination);
      src.start(t0);
    },
    play(name) {
      if (this.muted || !this.ensure()) return;
      const t = this.ctx.currentTime + 0.01;
      try {
        switch (name) {
          case 'blip': this.tone(660, t, 0.09, 'square', 0.04); break;
          case 'ding': this.tone(1318, t, 0.35, 'sine', 0.07); this.tone(1976, t, 0.3, 'sine', 0.03); break;
          case 'kaching':
            this.tone(988, t, 0.09, 'square', 0.05);
            this.tone(1319, t + 0.09, 0.22, 'square', 0.05);
            this.tone(2637, t + 0.09, 0.22, 'sine', 0.03); break;
          case 'thud': this.tone(110, t, 0.25, 'sine', 0.14, 55); break;
          case 'paper': this.noise(t, 0.12, 0.06, 2600); break;
          case 'whoosh': this.noise(t, 0.3, 0.07, 900); break;
          case 'alarm':
            for (let i = 0; i < 3; i++) {
              this.tone(830, t + i * 0.16, 0.08, 'square', 0.05);
              this.tone(620, t + i * 0.16 + 0.08, 0.08, 'square', 0.05);
            } break;
          case 'trombone': {
            const notes = [233, 220, 208, 196];
            notes.forEach((f, i) =>
              this.tone(f, t + i * 0.32, i === 3 ? 0.9 : 0.28, 'sawtooth', 0.07, f * (i === 3 ? 0.84 : 0.97)));
            break;
          }
          case 'page': this.noise(t, 0.22, 0.05, 1400); break;
          case 'fanfare': {
            // I – IV – V – I, small bells; the only tune the company knows
            const chords = [[523, 659, 784], [698, 880, 1047], [784, 988, 1175], [1047, 1319, 1568]];
            chords.forEach((ch, i) =>
              ch.forEach(f => this.tone(f, t + i * 0.22, i === 3 ? 0.7 : 0.2, 'sine', 0.045)));
            break;
          }
        }
      } catch (e) { /* sound is garnish, never the meal */ }
    }
  };

  // -------------------------------------------------------------- state
  const DEPTS = [
    { key: 'ENG', lead: 'PRIYA' },
    { key: 'PRODUCT', lead: 'MARGARET' },
    { key: 'SALES', lead: 'CHAD' },
    { key: 'OPS', lead: 'DOUG' }
  ];
  const WHO_HOME = { PRIYA: 'ENG', MARGARET: 'PRODUCT', CHAD: 'SALES', DOUG: 'OPS', KEVIN: 'ENG', YOU: 'CEO' };
  const MODE_META = {
    A: { name: 'OPTION A — THE BOTTLENECK', motto: '“Every decision is my job.”', maxW: 110 },
    B: { name: 'OPTION B — THE GHOST', motto: '“You guys figure it out.”', maxW: 110 },
    C: { name: 'OPTION C — THE BALANCE', motto: '“I take the one-way doors. You take the rest.”', maxW: 150 },
    D: { name: 'OPTION A½ — THE FINAL SAY', motto: '“I delegate everything! …except what matters.”', maxW: 205 },
    E: { name: 'OPTION B½ — THE CONDUCTOR', motto: '“The big calls are how people grow.”', maxW: 205 },
    F: { name: 'THE CAPSTONE — YOUR COMPANY', motto: '“No philosophy. A dial, six instruments, and my attention.”', maxW: 195 }
  };
  const WEEK_MS = 640;

  let game = null, raf = 0, acc = 0, lastT = 0;
  let speed = 1, paused = false, inCard = false;
  let cardIdx = 0, nextCardWeek = 0, currentCard = null;
  let bubbleTimer = 0, papersAlive = 0, successorFlip = false;
  let debriefDone = {};
  const DEBRIEF = QS.get('debrief') === '1';

  function progress() {
    try { return JSON.parse(readStore('dcc-ceosim-progress') || '{}'); }
    catch (e) { return {}; }
  }
  function readJSON(k) {
    try { return JSON.parse(readStore(k) || 'null'); } catch (e) { return null; }
  }
  function saveProgress(p) { store('dcc-ceosim-progress', JSON.stringify(p)); }

  // -------------------------------------------------------------- title
  function sideDone(p) {
    return { grip: !!(p.A || p.D), trust: !!(p.B || p.E) };
  }

  // ------------------------------------------------- shareable front page
  // Draws the ending as a tall newspaper card (PNG) for texting around.
  function wrapText(c, text, x, y, maxW, lineH) {
    const words = String(text).split(' ');
    let line = '';
    for (const w of words) {
      const probe = line ? line + ' ' + w : w;
      if (c.measureText(probe).width > maxW && line) {
        c.fillText(line, x, y); y += lineH; line = w;
      } else line = probe;
    }
    if (line) { c.fillText(line, x, y); y += lineH; }
    return y;
  }

  function buildEndCard() {
    const g = game, end = S.ENDINGS[g.ended];
    const cv = document.createElement('canvas');
    cv.width = 1080; cv.height = 1350;
    const c = cv.getContext('2d');
    c.fillStyle = '#fff'; c.fillRect(0, 0, 1080, 1350);
    c.fillStyle = '#000'; c.textAlign = 'center';
    c.font = '64px Georgia, serif';
    c.fillText('THE DAILY SLAB', 540, 96);
    c.fillRect(60, 122, 960, 4); c.fillRect(60, 132, 960, 2);
    c.font = '22px ui-monospace, monospace';
    c.fillText('WEEK ' + g.week + ' · YEAR ' + Math.max(1, Math.ceil(g.week / 48)) +
      ' · SUNBEAM SYSTEMS, INC. · PRICE: ONE DECISION', 540, 168);
    c.textAlign = 'left';
    c.font = 'bold 52px Georgia, serif';
    let y = wrapText(c, end.headline, 70, 250, 940, 60);
    c.font = 'italic 30px Georgia, serif';
    y = wrapText(c, end.sub, 70, y + 24, 940, 40);
    c.fillRect(70, y + 12, 940, 2);
    c.font = 'bold 22px ui-monospace, monospace';
    c.fillText('HOW IT HAPPENED', 70, y + 52);
    y += 84;
    c.font = '26px Georgia, serif';
    for (const m of g.keyMoments.slice(-6)) {
      c.font = 'bold 20px ui-monospace, monospace';
      c.fillText('WK ' + m.w, 70, y);
      c.font = '25px Georgia, serif';
      y = wrapText(c, m.t, 160, y, 850, 32) + 14;
      if (y > 1090) break;
    }
    c.strokeStyle = '#000'; c.lineWidth = 3;
    c.strokeRect(60, 1120, 960, 150);
    c.font = 'bold 22px ui-monospace, monospace';
    c.fillText(end.lessonTitle.toUpperCase(), 84, 1156);
    c.font = 'italic 25px Georgia, serif';
    wrapText(c, end.lessons[0], 84, 1192, 912, 32);
    c.font = '20px ui-monospace, monospace';
    c.textAlign = 'center';
    c.fillText('PLAY IT: daylightcomputer.club/ceo-sim · SCENARIO ' + g.mode + ' · SEED ' + g.seed, 540, 1316);
    return cv;
  }

  function shareEndCard() {
    const cv = buildEndCard();
    cv.toBlob(blob => {
      if (!blob) return;
      const file = new File([blob], 'the-daily-slab.png', { type: 'image/png' });
      if (navigator.canShare && navigator.canShare({ files: [file] })) {
        navigator.share({ files: [file], title: 'The Daily Slab' }).catch(() => {});
      } else {
        const a = document.createElement('a');
        a.href = URL.createObjectURL(blob);
        a.download = 'the-daily-slab.png';
        a.click();
        setTimeout(() => URL.revokeObjectURL(a.href), 5000);
      }
    }, 'image/png');
  }

  function renderTitle() {
    const p = progress();
    const mark = (r, m) => !r ? '' :
      m === 'F' ? (r.win ? '★ STEERED — 16 QUARTERS' : r.drift ? '≈ SURVIVED, DRIFTING' : '☠ RAN AGROUND, WEEK ' + r.week) :
      r.win ? '★ SURVIVED — 12 QUARTERS' :
      m === 'D' ? '▣ SOLD OFF, YEAR ' + Math.ceil(r.week / 48) :
      m === 'E' ? '▣ WENT SIDEWAYS, YEAR ' + Math.ceil(r.week / 48) :
      '☠ DIED, WEEK ' + r.week;
    ['A', 'B', 'C', 'D', 'E', 'F'].forEach(m => { const el = $('played' + m); if (el) el.textContent = mark(p[m], m); });
    // the capstone opens once you've walked the narrow path at least once
    const bf = $('btnF');
    if (bf) {
      bf.disabled = !p.C;
      $('descF').textContent = p.C
        ? 'A dial you can turn any week: how much of the company reaches your desk. The right setting moves. Watch the gauges; notice the drift.'
        : '🔒 Locked. Play The Balance first — the dial only means something once you\'ve sorted by hand.';
    }
    // sorting test scoreboard
    const pre = readJSON('dcc-ceosim-test-pre'), post = readJSON('dcc-ceosim-test-post');
    const td = $('testDelta');
    if (td) {
      td.textContent = pre && post ? 'BEFORE: ' + pre.score + '/6 · AFTER: ' + post.score + '/6'
        : pre ? 'BASELINE: ' + pre.score + '/6 — beat The Balance to unlock the re-test'
        : '';
    }
    const bt = $('btnTest');
    if (bt) bt.textContent = !pre ? '🧪 The 60-second sorting test (take it before you play)'
      : (p.C && p.C.win && !post) ? '🧪 Re-take the sorting test — see what the game did'
      : '🧪 The sorting test';
    const s = sideDone(p);
    const unlocked = s.grip && s.trust;
    $('btnC').disabled = !unlocked;
    $('descC').textContent = unlocked
      ? 'Sort every decision yourself: keep it or delegate it. The doors are labeled. Mostly.'
      : '🔒 Locked. Fail once on each side of the dial first — the balance only means something after the ditches.';
    // cold-link guidance: a fresh visitor gets pointed at door number one
    const fresh = !p.A && !p.B && !p.D && !p.E;
    $('btnA').classList.toggle('suggested', fresh);
    if (fresh) $('playedA').textContent = '☞ NEW HERE? START WITH THIS ONE.';
  }

  // ------------------------------------------------- the 60-second test
  // Six decisions, two buttons each. Taken cold it's the baseline; after
  // beating The Balance it's the report card.
  let testAnswers = [];
  function openTest() {
    FX.ensure();
    testAnswers = new Array(S.SORT_TEST.length).fill(null);
    const wrap = $('testRows'); wrap.innerHTML = '';
    S.SORT_TEST.forEach((q, i) => {
      const row = document.createElement('div');
      row.className = 'testrow';
      const label = document.createElement('div');
      label.className = 'tq'; label.textContent = (i + 1) + '. ' + q.t;
      row.appendChild(label);
      const btns = document.createElement('div'); btns.className = 'tbtns';
      [['🖋 ME', true], ['📤 THEM', false]].forEach(([txt, val]) => {
        const b = document.createElement('button');
        b.textContent = txt;
        b.addEventListener('click', () => {
          testAnswers[i] = val;
          btns.querySelectorAll('button').forEach(x => x.setAttribute('aria-pressed', 'false'));
          b.setAttribute('aria-pressed', 'true');
          $('testSubmit').disabled = testAnswers.some(a => a === null);
        });
        btns.appendChild(b);
      });
      row.appendChild(btns);
      const why = document.createElement('p');
      why.className = 'twhy'; why.hidden = true;
      row.appendChild(why);
      wrap.appendChild(row);
    });
    $('testSubmit').disabled = true;
    $('testResult').hidden = true;
    $('testSubmit').hidden = false;
    $('testOverlay').hidden = false;
  }

  function gradeTest() {
    let score = 0;
    const rows = $('testRows').children;
    S.SORT_TEST.forEach((q, i) => {
      const right = testAnswers[i] === q.ceo;
      if (right) score++;
      const why = rows[i].querySelector('.twhy');
      why.hidden = false;
      why.textContent = (right ? '✓ ' : '✗ ') + (q.ceo ? 'The CEO\'s. ' : 'Theirs. ') + q.why;
      rows[i].classList.toggle('wrong', !right);
    });
    const p = progress();
    const key = readJSON('dcc-ceosim-test-pre') && p.C && p.C.win ? 'dcc-ceosim-test-post' : 'dcc-ceosim-test-pre';
    store(key, JSON.stringify({ score, total: S.SORT_TEST.length, at: Date.now() }));
    const pre = readJSON('dcc-ceosim-test-pre'), post = readJSON('dcc-ceosim-test-post');
    $('testScoreLine').textContent = 'SCORE: ' + score + '/' + S.SORT_TEST.length +
      (post && pre && key === 'dcc-ceosim-test-post'
        ? ' — before the game you scored ' + pre.score + '/' + pre.total + '. That delta is the point.'
        : key === 'dcc-ceosim-test-pre'
          ? ' — baseline saved. Play through to The Balance, then re-take it.'
          : '');
    $('testSubmit').hidden = true;
    $('testScoreLine').hidden = false;
    $('testResult').hidden = false;
    FX.play(score >= 5 ? 'fanfare' : 'ding');
    renderTitle();
  }

  // -------------------------------------------------------- visit counter
  // "N chief executives have walked in." Lives on anjan.app; fails silent
  // (offline, adblock, private mode — the game never depends on it).
  function visitCounter() {
    const el = $('visitLine');
    if (!el || !window.fetch) return;
    if (!/daylightcomputer\.club|github\.io/.test(location.hostname)) return;   // no dev noise
    const today = new Date().toISOString().slice(0, 10);
    const bump = readStore('dcc-ceosim-visited') !== today;
    fetch('https://anjan.app/api/decider-visits' + (bump ? '?bump=1' : ''))
      .then(r => r.json())
      .then(d => {
        if (!d || !d.n) return;
        store('dcc-ceosim-visited', today);
        el.textContent = '☠ ' + d.n.toLocaleString() +
          ' chief executive' + (d.n === 1 ? '' : 's') + ' have run Sunbeam. The company dies almost every time.';
        el.hidden = false;
      })
      .catch(() => { /* the sign stays dark */ });
  }

  // ---------------------------------------------------------------- sim
  function startScenario(mode, seed) {
    FX.ensure();
    game = S.makeGame(mode, seed || parseInt(QS.get('seed'), 10) || (Date.now() % 100000));
    cardIdx = 0; nextCardWeek = S.CARD_WEEKS[mode]; currentCard = null;
    acc = 0; lastT = 0; paused = false; inCard = false; papersAlive = 0;
    setSpeed(1);
    $('titleScreen').hidden = true;
    $('endOverlay').hidden = true;
    $('simScreen').hidden = false;
    $('slName').textContent = MODE_META[mode].name;
    $('slMotto').textContent = MODE_META[mode].motto;
    $('ticker').innerHTML = '';
    $('doorsRow').innerHTML = '<span id="doorsHint">ONE-WAY DOORS WALKED THROUGH APPEAR HERE ▸</span>';
    $('bubble').hidden = true;
    successorFlip = false; debriefDone = {};
    const dr = $('dialRow');
    if (dr) { dr.hidden = mode !== 'F'; renderDial(); }
    buildMetrics(); buildDepts();
    if (board()) board().reset(mode);
    renderAll();
    FX.play('page');
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(loop);
  }

  function loop(t) {
    raf = requestAnimationFrame(loop);
    if (!lastT) lastT = t;
    const dt = Math.min(200, t - lastT); lastT = t;
    if (paused || inCard || !game || game.over) return;
    acc += dt * speed;
    let steps = 0;
    while (acc >= WEEK_MS && steps < 10) { acc -= WEEK_MS; steps++; step(); if (game.over || inCard) break; }
  }

  // ------------------------------------------------------- the dial (F)
  function renderDial() {
    if (!game || !$('dialValue')) return;
    $('dialValue').textContent = Math.round(game.dial * 100) + '%';
  }
  function turnDial(delta) {
    if (!game || game.mode !== 'F' || game.over) return;
    const before = game.dial;
    game.dial = Math.min(0.95, Math.max(0.05, Math.round((game.dial + delta) * 100) / 100));
    if (game.dial === before) return;
    renderDial();
    FX.play('blip');
    logLine({ t: 'You turn the dial: ' + Math.round(game.dial * 100) + '% of decisions now reach your desk.' });
  }

  // -------------------------------------------------- debrief mode (?debrief=1)
  const DEBRIEF_QS = {
    A: { 24: 'Nothing has gone visibly wrong yet. What single number would you watch to catch this failure a year early?',
         48: 'Who in this company has learned to decide anything this year? What is the queue teaching them instead?',
         96: 'When did the QUALITY of the CEO\'s decisions start falling — and what caused it?' },
    B: { 24: 'The speed is real. What invisible quantity is being spent to buy it?',
         48: 'List the decisions made so far that cannot be unmade. Who examined them before they happened?',
         96: 'Whose job was coherence? What happens to a question that is everyone\'s?' },
    D: { 24: 'The delegation dashboard reads 91%. What would a LEVERAGE dashboard read?',
         48: 'Each review adds two weeks and 4%. Price that trade for one big decision, then for all of them.',
         96: 'No fires, no growth. Why does this failure produce no feedback — and what replaces the missing alarm?' },
    E: { 24: 'Morale is outstanding. Under what conditions is that the alarm, not the good news?',
         48: 'A big call was just handed over as a "growth opportunity." Estimate the discount. Who says it out loud?',
         96: 'Which hard call has consensus quietly declined to make? What is it costing per quarter?' },
    C: { 24: 'Which memo so far was hardest to classify, and what made it hard?',
         48: 'What information would change one of your keep/delegate calls? Who already has it?',
         96: 'The org is starting to pre-sort decisions your way. What did you do that caused that?' },
    F: { 24: 'Which way are you drifting right now — and what told you?',
         48: 'The crisis is coming (they always are). What will you centralize, and what is your trigger to give it back?',
         96: 'Your company halved its need for you in a year. Did your dial keep up? What is your re-check cadence?' }
  };
  function maybeDebrief() {
    if (!DEBRIEF || !game || game.over) return false;
    const brk = [24, 48, 96].find(w => game.week >= w && !debriefDone[w]);
    if (!brk) return false;
    debriefDone[brk] = true;
    const q = (DEBRIEF_QS[game.mode] || DEBRIEF_QS.C)[brk];
    $('debriefQ').textContent = q;
    $('debriefWk').textContent = 'DISCUSSION BREAK · WK ' + game.week + ' · Q' + Math.ceil(game.week / 12);
    $('debriefOverlay').hidden = false;
    inCard = true;   // pauses the loop the same way a memo does
    FX.play('page');
    return true;
  }

  function step() {
    const evs = S.tick(game);
    evs.forEach(handleEvent);
    renderAll();
    if (game.mode === 'F') renderDial();
    if (game.over) { setTimeout(showEnd, 1600); return; }
    if (maybeDebrief()) return;
    if (S.CARD_WEEKS[game.mode] && game.week >= nextCardWeek && cardIdx < game.deck.length) {
      const card = game.deck[cardIdx++];
      nextCardWeek = game.week + S.CARD_WEEKS[game.mode];
      const critical = card.oneWay && card.stakes === 'HIGH';
      if (game.mode === 'C' && critical && game.learning >= 60 && (successorFlip = !successorFlip)) {
        // the successor arc: the org brings it to you already sorted
        S.applyCard(game, card, 'auto');
        handleEvent({ who: 'PRIYA', t: 'brings you "' + card.t + '" already sorted: two pages, a recommendation, a dissent attached. You add one sentence. The org decides like you now.', sfx: 'ding', major: false });
      } else {
        openCard(card);
      }
    }
  }

  // ------------------------------------------------------------- events
  function handleEvent(e) {
    logLine(e);
    if (e.sfx) FX.play(e.sfx);
    if (e.who && e.who !== 'NARRATOR') showBubble(e.who, e.t);
    if (e.fire) igniteDept(e.fire);
    if (e.t && e.t.indexOf('FIRE:') === 0 && !e.fire) igniteDept(DEPTS[Math.floor(Math.random() * 4)].key);
    if (board() && board().onEvent) board().onEvent(e);
    renderDoors();
  }

  function logLine(e) {
    const div = document.createElement('div');
    div.className = 'tick' + (e.major ? ' major' : '') + (e.sfx === 'alarm' ? ' fire' : '');
    const w = document.createElement('span'); w.className = 'tw';
    w.textContent = 'Wk ' + game.week + (e.who && e.who !== 'NARRATOR' ? ' · ' + e.who : '') + ' — ';
    div.appendChild(w);
    div.appendChild(document.createTextNode(e.t));
    const tk = $('ticker');
    tk.insertBefore(div, tk.firstChild);
    while (tk.children.length > 90) tk.removeChild(tk.lastChild);
  }

  function showBubble(who, text) {
    const wrap = $('orgboard'), b = $('bubble');
    const home = WHO_HOME[who] || 'CEO';
    let left, top;
    if (board()) {
      const pt = board().anchor(home);
      if (!pt) return;
      left = pt.x; top = pt.y;
    } else {
      const el = home === 'CEO' ? $('ceoBox') : document.querySelector('.dept[data-key="' + home + '"]');
      if (!el) return;
      const br = wrap.getBoundingClientRect(), er = el.getBoundingClientRect();
      left = er.left - br.left + er.width / 2;
      top = er.top - br.top - 6;
    }
    b.querySelector('.bwho').textContent = who;
    b.querySelector('.btext').textContent = text.length > 140 ? text.slice(0, 137) + '…' : text;
    b.style.left = left + 'px';
    b.style.top = top + 'px';
    b.hidden = false;
    clearTimeout(bubbleTimer);
    bubbleTimer = setTimeout(() => { b.hidden = true; }, Math.max(1200, 3400 / speed));
  }

  function igniteDept(key) {
    if (board()) { board().fire(key); return; }
    const el = document.querySelector('.dept[data-key="' + key + '"]');
    if (!el) return;
    el.classList.add('onfire');
    if (!el.querySelector('.firebadge')) {
      const badge = document.createElement('span');
      badge.className = 'firebadge'; badge.textContent = '▲ FIRE';
      el.appendChild(badge);
    }
    setTimeout(() => {
      el.classList.remove('onfire');
      const bg = el.querySelector('.firebadge'); if (bg) bg.remove();
    }, Math.max(2500, 6000 / speed));
  }

  function spawnPaper(fromEl, toEl) {
    if (paperMotion) return;
    if (papersAlive > 8 || speed >= 8 || document.hidden || !fromEl || !toEl) return;
    const f = fromEl.getBoundingClientRect(), to = toEl.getBoundingClientRect();
    const p = document.createElement('div');
    p.className = 'flypaper';
    p.style.left = (f.left + f.width / 2) + 'px';
    p.style.top = (f.top + f.height / 2) + 'px';
    document.body.appendChild(p); papersAlive++;
    requestAnimationFrame(() => {
      p.style.transform = 'translate(' + (to.left + to.width / 2 - f.left - f.width / 2) + 'px,' +
        (to.top + to.height / 2 - f.top - f.height / 2) + 'px) rotate(' + (Math.random() * 180 - 90) + 'deg)';
      p.style.opacity = '0.15';
    });
    setTimeout(() => { p.remove(); papersAlive--; }, 950);
  }

  // ------------------------------------------------------------- render
  function buildDepts() {
    const wrap = $('depts'); if (!wrap) return;
    wrap.innerHTML = '';
    DEPTS.forEach(d => {
      const el = document.createElement('div');
      el.className = 'dept'; el.dataset.key = d.key;
      el.innerHTML = '<div class="dname">' + d.key + '</div><div class="dots"></div>';
      wrap.appendChild(el);
    });
  }

  const METRIC_DEFS = {
    common: [
      { id: 'cash', label: 'CASH', max: 3200 },
      { id: 'rev', label: 'REVENUE /QTR', max: 1200 },
      { id: 'speed', label: 'SHIP SPEED', max: 135 },
      { id: 'morale', label: 'MORALE', max: 100 },
      { id: 'queue', label: 'YOUR QUEUE', max: 80 }
    ],
    A: { id: 'extra', label: 'YOUR SANITY', max: 100 },
    B: { id: 'extra', label: 'COHERENCE', max: 100 },
    C: { id: 'extra', label: 'ORG JUDGMENT', max: 100 },
    D: { id: 'extra', label: 'MARKET EDGE', max: 100 },
    E: { id: 'extra', label: 'THE STANDARD', max: 100 },
    F: { id: 'extra', label: 'ORG JUDGMENT', max: 100 }
  };

  function buildMetrics() {
    const defs = METRIC_DEFS.common.concat([METRIC_DEFS[game.mode]]);
    const m = $('metrics'); m.innerHTML = '';
    defs.forEach(d => {
      const el = document.createElement('div');
      el.className = 'metric'; el.id = 'metric-' + d.id;
      el.innerHTML = '<div class="label">' + d.label + '</div><div class="value">—</div><div class="bar"><i></i></div>';
      m.appendChild(el);
    });
  }

  function setMetric(id, text, frac, hot) {
    const el = $('metric-' + id); if (!el) return;
    el.querySelector('.value').textContent = text;
    el.querySelector('.bar i').style.width = Math.round(Math.max(0, Math.min(1, frac)) * 100) + '%';
    el.classList.toggle('hot', !!hot);
  }

  function renderAll() {
    const g = game;
    $('clock').textContent = 'Wk ' + g.week + ' · Q' + Math.max(1, Math.ceil(g.week / 12)) + ' · Y' + Math.max(1, Math.ceil(g.week / 48));
    setMetric('cash', '$' + S.fmtK(Math.max(0, Math.round(g.cash))), g.cash / 3200, g.cash < 500);
    setMetric('rev', '$' + S.fmtK(Math.round(g.revW * 12)), (g.revW * 12) / 1200);
    setMetric('speed', Math.round(g.speed * 100) + '%', g.speed / 1.35, g.speed < 0.5);
    setMetric('morale', Math.round(g.morale), g.morale / 100, g.morale < 35);
    setMetric('queue', Math.round(g.queue), g.queue / 80, g.queue > 25);
    const extra = g.mode === 'A' ? g.sanity
      : g.mode === 'B' || g.mode === 'D' ? g.coherence
      : g.mode === 'E' ? g.quality * 100
      : g.learning;
    setMetric('extra', Math.round(extra), extra / 100, g.mode !== 'C' && extra < 40);

    if (board()) {
      board().render(g);
      drawChart();
      return;
    }

    // paper pile
    const pile = $('pile');
    const sheets = Math.min(34, Math.round(g.queue / 2.2));
    while (pile.children.length < sheets) { const s = document.createElement('div'); s.className = 'sheet'; pile.appendChild(s); }
    while (pile.children.length > sheets) pile.removeChild(pile.lastChild);
    pile.classList.toggle('toppling', g.queue > 55);
    $('pileLabel').textContent = 'queue: ' + Math.round(g.queue);

    // staff dots
    const per = Math.max(1, Math.round(g.headcount / 4));
    document.querySelectorAll('.dept .dots').forEach(d => {
      d.textContent = '●'.repeat(Math.min(14, per));
    });

    // ambient flying paper
    if (!document.hidden && Math.random() < 0.5) {
      const dept = document.querySelectorAll('.dept')[Math.floor(Math.random() * 4)];
      if (g.mode === 'A') spawnPaper(dept, $('ceoBox'));
      else if (g.mode === 'B' || g.mode === 'E') spawnPaper(dept, document.querySelectorAll('.dept')[Math.floor(Math.random() * 4)]);
      else if (g.mode === 'D') spawnPaper(dept, Math.random() < 0.35 ? $('ceoBox') : dept);
      else if (Math.random() < 0.25) spawnPaper(dept, $('ceoBox'));
      else spawnPaper(dept, dept);
    }
    drawChart();
  }

  function renderDoors() {
    const row = $('doorsRow');
    const have = Array.prototype.map.call(row.querySelectorAll('.doorchip'), n => n.textContent);
    game.doors.forEach(d => {
      if (have.indexOf(d) >= 0) return;
      const hint = $('doorsHint'); if (hint) hint.remove();
      const chip = document.createElement('span');
      chip.className = 'doorchip'; chip.textContent = d;
      row.appendChild(chip);
      FX.play('thud');
    });
  }

  function drawChart() {
    const cv = $('chart'), g = game;
    const dpr = window.devicePixelRatio || 1;
    const w = cv.clientWidth, h = cv.clientHeight;
    if (!w) return;
    if (cv.width !== w * dpr) { cv.width = w * dpr; cv.height = h * dpr; }
    const ctx = cv.getContext('2d');
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.clearRect(0, 0, w, h);
    const maxW = MODE_META[g.mode].maxW;
    const X = i => 4 + (w - 8) * (i / maxW);
    // quarter marks
    ctx.strokeStyle = '#ddd'; ctx.lineWidth = 1;
    for (let q = 12; q < maxW; q += 12) {
      ctx.beginPath(); ctx.moveTo(X(q), 0); ctx.lineTo(X(q), h); ctx.stroke();
    }
    const line = (arr, max, dash) => {
      if (arr.length < 2) return;
      ctx.beginPath();
      ctx.setLineDash(dash || []);
      ctx.strokeStyle = dash ? '#777' : '#000';
      ctx.lineWidth = dash ? 1.5 : 2;
      arr.forEach((v, i) => {
        const x = X(i + 1), y = h - 4 - (h - 10) * Math.min(1, v / max);
        i ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      });
      ctx.stroke(); ctx.setLineDash([]);
    };
    line(g.history.cash, 3200);
    line(g.history.morale, 100, [4, 4]);
  }

  // -------------------------------------------------------------- cards
  function openCard(card) {
    inCard = true; currentCard = card;
    FX.play('paper');
    $('cardNo').textContent = 'DECISION #' + (cardIdx) + ' · WK ' + game.week;
    $('cardTitle').textContent = card.t;
    $('cardDesc').textContent = card.d;
    if (game.mode === 'C') {
      // no training wheels in the corner office: classify it yourself
      $('stampStakes').textContent = 'STAKES: ?';
      $('stampDoor').textContent = 'DOOR: ?';
    } else {
      $('stampStakes').textContent = 'STAKES: ' + card.stakes;
      $('stampDoor').textContent = card.oneWay ? 'ONE-WAY DOOR' : 'REVERSIBLE';
    }
    const memo = $('chooseMemo');
    if (memo) memo.hidden = !(game.mode === 'C' && game.learning >= 25);
    $('cardOutcome').hidden = true;
    $('cardChoices').hidden = false;
    const take = $('chooseTake'), del = $('chooseDel'), nb = $('cardNB');
    take.disabled = false; del.disabled = false;
    const high = card.stakes === 'HIGH';
    if (game.mode === 'A') {
      del.disabled = true;
      nb.textContent = '(Delegate? And risk someone deciding... differently? Be serious.)';
    } else if (game.mode === 'B') {
      take.disabled = true;
      nb.textContent = '(You are on a silent retreat. The memo auto-delegates in the spirit of empowerment.)';
    } else if (game.mode === 'D') {
      if (high) {
        del.disabled = true;
        nb.textContent = '(Delegate THIS? No no — this one\'s critical. They\'re all critical, somehow.)';
      } else {
        take.disabled = true;
        nb.textContent = '(Delegated before you finished reading! See? You delegate constantly. You\'re famous for it.)';
      }
    } else if (game.mode === 'E') {
      take.disabled = true;
      nb.textContent = high
        ? '(Overruling them would stunt their growth. You schedule some encouraging feedback instead.)'
        : '(Obviously them — you\'re a conductor, not a micromanager.)';
    } else {
      nb.textContent = 'No stamps on your desk. Two questions, every time: how bad if wrong — and can we undo it?';
    }
    $('cardOverlay').hidden = false;
  }

  function choose(choice) {
    if (typeof choice === 'boolean') choice = choice ? 'take' : 'del';
    const card = currentCard;
    const r = S.applyCard(game, card, choice);
    FX.play(r.sfx || 'blip');
    $('cardChoices').hidden = true;
    if (game.mode === 'C' && r.reveal) {
      // the reveal: what the stamps would have said
      $('stampStakes').textContent = 'STAKES: ' + r.reveal.stakes;
      $('stampDoor').textContent = r.reveal.oneWay ? 'ONE-WAY DOOR' : 'REVERSIBLE';
    }
    $('cardVerdict').textContent =
      game.mode !== 'C' ? (choice === 'take' ? 'SO ORDERED.' : 'SO DELEGATED.')
        : r.good ? '✓ FILED CORRECTLY.' : '✗ FILED... CREATIVELY.';
    $('cardResult').textContent = r.text;
    $('cardOutcome').hidden = false;
  }

  // ------------------------------------------------------------ endings
  function showEnd() {
    if (!game || !game.over || !$('endOverlay').hidden) return;
    const g = game;
    const key = g.ended, end = S.ENDINGS[key];
    const p = progress();
    const sBefore = sideDone(p);
    p[g.mode] = { week: g.week, win: key === 'C_WIN' || key === 'F_WIN', drift: key === 'F_DRIFT' };
    saveProgress(p);
    const sAfter = sideDone(p);
    const unlocked = sAfter.grip && sAfter.trust;
    const unlockedNow = unlocked && !(sBefore.grip && sBefore.trust);

    $('fpPaper').textContent = end.paper;
    $('fpDate').textContent = 'WEEK ' + g.week + ' · YEAR ' + Math.max(1, Math.ceil(g.week / 48)) + ' · SUNBEAM SYSTEMS, INC.';
    $('fpHeadline').textContent = end.headline;
    $('fpSub').textContent = end.sub;
    const ms = $('fpMoments'); ms.innerHTML = '';
    g.keyMoments.slice(-9).forEach(m => {
      const li = document.createElement('li');
      li.innerHTML = '<span class="tw">WK ' + m.w + '</span> ';
      li.appendChild(document.createTextNode(m.t));
      ms.appendChild(li);
    });
    $('fpDoors').innerHTML = g.doors.length
      ? '<h4>ONE-WAY DOORS WALKED THROUGH, UNSUPERVISED</h4><p>' +
        g.doors.map(d => '◼ ' + d).join(' &nbsp; ') + '</p>'
      : '';
    $('fpLessonTitle').textContent = end.lessonTitle;
    $('fpEssay').innerHTML = '';
    // Balance mode: your calibration, mirrored back
    if (g.mode === 'C' && (g.cHoard || g.cGhost || g.cMemoTax || g.cGood)) {
      const h = document.createElement('h4');
      h.textContent = 'YOUR CALIBRATION';
      $('fpEssay').appendChild(h);
      const par = document.createElement('p');
      const bits = [];
      bits.push('You sorted ' + g.cGood + ' correctly.');
      if (g.cHoard) bits.push('You kept ' + g.cHoard + ' reversible call' + (g.cHoard > 1 ? 's' : '') + ' that never needed you — that\'s your inner Bottleneck, and everything queued behind it.');
      if (g.cGhost) bits.push('You waved through ' + g.cGhost + ' one-way door' + (g.cGhost > 1 ? 's' : '') + ' — your inner Ghost; note how long the bill took to arrive.');
      if (g.cMemoTax) bits.push('You asked for ' + g.cMemoTax + ' memo' + (g.cMemoTax > 1 ? 's' : '') + ' on things a shrug could have settled — process is also a tax.');
      if (!g.cHoard && !g.cGhost) bits.push('Neither ditch pulled you in. The stamps were hidden; your judgment supplied them. That is the skill.');
      else bits.push(g.cHoard > g.cGhost
        ? 'Your lean is grip. When you drift, you\'ll drift toward the queue — watch it like a vital sign.'
        : g.cGhost > g.cHoard
          ? 'Your lean is trust. When you drift, you\'ll drift toward the quiet detonations — watch your surprise rate.'
          : 'You miss in both directions equally — rare, and honestly harder to instrument. Watch queue AND surprises.');
      par.textContent = bits.join(' ');
      $('fpEssay').appendChild(par);
    }
    // Capstone: the reveal — the dial you set vs the dial it needed
    const dialWrap = $('fpDialWrap');
    if (dialWrap) {
      dialWrap.hidden = g.mode !== 'F';
      if (g.mode === 'F' && g.history.dial.length > 1) {
        const cv = $('fpDialChart'), dpr = window.devicePixelRatio || 1;
        const w = Math.min(660, dialWrap.clientWidth || 660), h = 150;
        cv.style.width = w + 'px'; cv.style.height = h + 'px';
        cv.width = w * dpr; cv.height = h * dpr;
        const c = cv.getContext('2d');
        c.setTransform(dpr, 0, 0, dpr, 0, 0);
        c.clearRect(0, 0, w, h);
        const N = g.history.dial.length;
        const X = i => 4 + (w - 8) * i / (N - 1), Y = v => h - 8 - (h - 20) * v;
        const line = (arr, dash) => {
          c.beginPath(); c.setLineDash(dash || []);
          c.strokeStyle = dash ? '#777' : '#000'; c.lineWidth = 2;
          arr.forEach((v, i) => i ? c.lineTo(X(i), Y(v)) : c.moveTo(X(i), Y(v)));
          c.stroke(); c.setLineDash([]);
        };
        line(g.history.tstar, [5, 4]);
        line(g.history.dial);
      }
    }
    if (end.essay) {
      const h = document.createElement('h4');
      h.textContent = 'THE POST-MORTEM, IN PROSE';
      $('fpEssay').appendChild(h);
      end.essay.forEach(t => {
        const par = document.createElement('p'); par.textContent = t;
        $('fpEssay').appendChild(par);
      });
    }
    const ls = $('fpLessons'); ls.innerHTML = '';
    end.lessons.forEach(t => {
      const li = document.createElement('li'); li.textContent = t; ls.appendChild(li);
    });
    $('fpUnlock').innerHTML = unlockedNow
      ? '<span class="unlockstamp">🔓 OPTION C UNLOCKED — YOU HAVE NOW FAILED IN BOTH DITCHES</span>' : '';

    const btns = $('fpButtons'); btns.innerHTML = '';
    const addBtn = (label, primary, fn) => {
      const b = document.createElement('button');
      b.textContent = label; if (primary) b.className = 'primary';
      b.addEventListener('click', fn); btns.appendChild(b);
    };
    const NAMES = { A: 'THE BOTTLENECK', B: 'THE GHOST', D: 'THE FINAL SAY', E: 'THE CONDUCTOR' };
    const nextUnplayed = ['A', 'B', 'D', 'E'].filter(m => !p[m]);
    if (!unlocked && nextUnplayed.length) {
      const n = nextUnplayed[0];
      const label = (n === 'A' || n === 'B')
        ? 'Now die the other way ▸ ' + NAMES[n]
        : 'Now the version you\'d actually fall for ▸ ' + NAMES[n];
      addBtn(label, true, () => startScenario(n));
    }
    if (unlocked && g.mode !== 'C' && !p.C) addBtn('Find the balance ▸ OPTION C', true, () => startScenario('C'));
    if (g.mode === 'C' && key === 'C_LOSE') addBtn('Try the balance again ▸', true, () => startScenario('C'));
    if (g.mode === 'C' && key === 'C_WIN' && !p.F) addBtn('Now the real thing ▸ YOUR COMPANY', true, () => startScenario('F'));
    if (g.mode === 'F' && key !== 'F_WIN') addBtn('Steer it again ▸', true, () => startScenario('F'));
    addBtn('📸 Keep this front page', false, shareEndCard);
    addBtn('📖 The field guide', false, () => { location.href = 'lessons.html'; });
    if (unlocked && nextUnplayed.length) {
      const n = nextUnplayed[0];
      addBtn('A subtler death awaits ▸ ' + NAMES[n], false, () => startScenario(n));
    }
    addBtn('Replay this scenario', false, () => startScenario(g.mode));
    addBtn('Front desk (menu)', false, backToTitle);
    $('endOverlay').hidden = false;
    if (key === 'C_WIN' || key === 'F_WIN') FX.play('fanfare');
    renderTitle();   // refresh unlocks behind the overlay
  }

  function backToTitle() {
    cancelAnimationFrame(raf); raf = 0; game = null;
    $('endOverlay').hidden = true;
    $('cardOverlay').hidden = true;
    $('simScreen').hidden = true;
    $('titleScreen').hidden = false;
    renderTitle();
  }

  // ------------------------------------------------------------- wiring
  function setSpeed(s) {
    speed = s; paused = false;
    document.querySelectorAll('.speedbtn[data-speed]').forEach(b =>
      b.setAttribute('aria-pressed', String(Number(b.dataset.speed) === s)));
    $('btnPause').setAttribute('aria-pressed', 'false');
    $('btnPause').textContent = '⏸';
  }

  $('btnA').addEventListener('click', () => startScenario('A'));
  $('btnB').addEventListener('click', () => startScenario('B'));
  $('btnC').addEventListener('click', () => startScenario('C'));
  $('btnD').addEventListener('click', () => startScenario('D'));
  $('btnE').addEventListener('click', () => startScenario('E'));
  if ($('btnF')) $('btnF').addEventListener('click', () => startScenario('F'));
  if ($('dialDown')) $('dialDown').addEventListener('click', () => turnDial(-0.05));
  if ($('dialUp')) $('dialUp').addEventListener('click', () => turnDial(0.05));
  if ($('btnTest')) $('btnTest').addEventListener('click', openTest);
  if ($('testSubmit')) $('testSubmit').addEventListener('click', gradeTest);
  if ($('testClose')) $('testClose').addEventListener('click', () => { $('testOverlay').hidden = true; });
  if ($('debriefResume')) $('debriefResume').addEventListener('click', () => {
    $('debriefOverlay').hidden = true; inCard = false;
  });
  $('btnQuit').addEventListener('click', backToTitle);
  $('btnPause').addEventListener('click', () => {
    paused = !paused;
    $('btnPause').textContent = paused ? '▶' : '⏸';
    $('btnPause').setAttribute('aria-pressed', String(paused));
  });
  document.querySelectorAll('.speedbtn[data-speed]').forEach(b =>
    b.addEventListener('click', () => setSpeed(Number(b.dataset.speed))));
  function renderMute() { $('btnMute').textContent = FX.muted ? '🔇' : '🔉'; }
  $('btnMute').addEventListener('click', () => {
    FX.muted = !FX.muted;
    store('dcc-ceosim-mute', FX.muted ? '1' : '0');
    renderMute(); if (!FX.muted) FX.play('blip');
  });
  const bm = $('btnMotion');
  if (bm) bm.addEventListener('click', () => {
    paperMotion = !paperMotion;
    store('dcc-ceosim-motion', paperMotion ? 'paper' : 'live');
    applyMotion();
  });
  $('chooseTake').addEventListener('click', () => choose('take'));
  $('chooseDel').addEventListener('click', () => choose('del'));
  if ($('chooseMemo')) $('chooseMemo').addEventListener('click', () => choose('memo'));
  $('cardContinue').addEventListener('click', () => {
    $('cardOverlay').hidden = true; inCard = false; currentCard = null;
  });

  // debug/testing hook (used by the club's Playwright smoke test)
  window.SIM = {
    start: startScenario, state: () => game, setSpeed,
    card: () => currentCard, dial: turnDial,
    step: n => { for (let i = 0; i < (n || 1) && game && !game.over && !inCard; i++) step(); },
    choose, model: S
  };

  renderMute();
  renderTitle();
  applyMotion();
  visitCounter();
  // offline: the club's service worker caches the whole game after one visit
  try {
    if ('serviceWorker' in navigator) navigator.serviceWorker.register('../sw.js');
  } catch (e) { /* not fatal anywhere */ }

  // Deep link: ?scenario=A[&week=N] jumps straight into a run mid-flight —
  // used by the DC-1 preview audit, tests, and shareable moments.
  const dlMode = (QS.get('scenario') || '').toUpperCase();
  if (MODE_META[dlMode] && (dlMode !== 'C' || progress().A)) {
    startScenario(dlMode);
    const targetWk = parseInt(QS.get('week'), 10) || 0;
    while (game && !game.over && game.week < targetWk) {
      if (game.mode === 'F') game.dial = S.targetDial(game.week);
      step();
      if (inCard && $('debriefOverlay') && !$('debriefOverlay').hidden) {
        $('debriefOverlay').hidden = true; inCard = false; continue;
      }
      if (inCard && currentCard) {
        const keep = game.mode === 'A' ? true
          : game.mode === 'D' ? currentCard.stakes === 'HIGH'
          : game.mode === 'C' ? (currentCard.oneWay && currentCard.stakes === 'HIGH')
          : false;
        choose(keep);
        $('cardOverlay').hidden = true; inCard = false; currentCard = null;
      }
    }
  }
})();
