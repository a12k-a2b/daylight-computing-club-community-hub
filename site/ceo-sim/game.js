/* The Decider — UI, sound, and time-lapse driver. model.js does the math. */
(function () {
  'use strict';
  const S = window.CEOSIM;
  const $ = id => document.getElementById(id);

  // ------------------------------------------------------------- audio
  // Tiny synth: everything is beeps, thuds, paper, and one sad trombone.
  const FX = {
    ctx: null,
    muted: localStorage.getItem('dcc-ceosim-mute') === '1',
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
    E: { name: 'OPTION B½ — THE CONDUCTOR', motto: '“The big calls are how people grow.”', maxW: 205 }
  };
  const WEEK_MS = 640;

  let game = null, raf = 0, acc = 0, lastT = 0;
  let speed = 1, paused = false, inCard = false;
  let cardIdx = 0, nextCardWeek = 0, currentCard = null;
  let bubbleTimer = 0, papersAlive = 0;

  function progress() {
    try { return JSON.parse(localStorage.getItem('dcc-ceosim-progress') || '{}'); }
    catch (e) { return {}; }
  }
  function saveProgress(p) { localStorage.setItem('dcc-ceosim-progress', JSON.stringify(p)); }

  // -------------------------------------------------------------- title
  function sideDone(p) {
    return { grip: !!(p.A || p.D), trust: !!(p.B || p.E) };
  }

  function renderTitle() {
    const p = progress();
    const mark = (r, m) => !r ? '' :
      r.win ? '★ SURVIVED — 12 QUARTERS' :
      m === 'D' ? '▣ SOLD OFF, YEAR ' + Math.ceil(r.week / 48) :
      m === 'E' ? '▣ WENT SIDEWAYS, YEAR ' + Math.ceil(r.week / 48) :
      '☠ DIED, WEEK ' + r.week;
    ['A', 'B', 'C', 'D', 'E'].forEach(m => { $('played' + m).textContent = mark(p[m], m); });
    const s = sideDone(p);
    const unlocked = s.grip && s.trust;
    $('btnC').disabled = !unlocked;
    $('descC').textContent = unlocked
      ? 'Sort every decision yourself: keep it or delegate it. The doors are labeled. Mostly.'
      : '🔒 Locked. Fail once on each side of the dial first — the balance only means something after the ditches.';
  }

  // ---------------------------------------------------------------- sim
  function startScenario(mode) {
    FX.ensure();
    game = S.makeGame(mode, Date.now() % 100000);
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
    buildMetrics(); buildDepts(); renderAll();
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

  function step() {
    const evs = S.tick(game);
    evs.forEach(handleEvent);
    renderAll();
    if (game.over) { setTimeout(showEnd, 1600); return; }
    if (game.week >= nextCardWeek && cardIdx < S.CARDS.length) {
      openCard(S.CARDS[cardIdx++]);
      nextCardWeek = game.week + S.CARD_WEEKS[game.mode];
    }
  }

  // ------------------------------------------------------------- events
  function handleEvent(e) {
    logLine(e);
    if (e.sfx) FX.play(e.sfx);
    if (e.who && e.who !== 'NARRATOR') showBubble(e.who, e.t);
    if (e.fire) igniteDept(e.fire);
    if (e.t && e.t.indexOf('FIRE:') === 0 && !e.fire) igniteDept(DEPTS[Math.floor(Math.random() * 4)].key);
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
    const board = $('orgboard'), b = $('bubble');
    const home = WHO_HOME[who] || 'CEO';
    const el = home === 'CEO' ? $('ceoBox') : document.querySelector('.dept[data-key="' + home + '"]');
    if (!el) return;
    b.querySelector('.bwho').textContent = who;
    b.querySelector('.btext').textContent = text.length > 140 ? text.slice(0, 137) + '…' : text;
    const br = board.getBoundingClientRect(), er = el.getBoundingClientRect();
    b.style.left = (er.left - br.left + er.width / 2) + 'px';
    b.style.top = (er.top - br.top - 6) + 'px';
    b.hidden = false;
    clearTimeout(bubbleTimer);
    bubbleTimer = setTimeout(() => { b.hidden = true; }, Math.max(1200, 3400 / speed));
  }

  function igniteDept(key) {
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
    const wrap = $('depts'); wrap.innerHTML = '';
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
    E: { id: 'extra', label: 'THE STANDARD', max: 100 }
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
    $('stampStakes').textContent = 'STAKES: ' + card.stakes;
    $('stampDoor').textContent = card.oneWay ? 'ONE-WAY DOOR' : 'REVERSIBLE';
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
      nb.textContent = 'Two questions, every time: how bad if wrong — and can we undo it?';
    }
    $('cardOverlay').hidden = false;
  }

  function choose(keep) {
    const r = S.applyCard(game, currentCard, keep);
    FX.play(r.sfx || 'blip');
    $('cardChoices').hidden = true;
    $('cardVerdict').textContent =
      game.mode !== 'C' ? (keep ? 'SO ORDERED.' : 'SO DELEGATED.')
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
    p[g.mode] = { week: g.week, win: key === 'C_WIN' };
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
    if (unlocked && g.mode !== 'C') addBtn('Find the balance ▸ OPTION C', true, () => startScenario('C'));
    if (g.mode === 'C' && key === 'C_LOSE') addBtn('Try the balance again ▸', true, () => startScenario('C'));
    if (unlocked && nextUnplayed.length) {
      const n = nextUnplayed[0];
      addBtn('A subtler death awaits ▸ ' + NAMES[n], false, () => startScenario(n));
    }
    addBtn('Replay this scenario', false, () => startScenario(g.mode));
    addBtn('Front desk (menu)', false, backToTitle);
    $('endOverlay').hidden = false;
    if (key === 'C_WIN') FX.play('kaching');
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
    localStorage.setItem('dcc-ceosim-mute', FX.muted ? '1' : '0');
    renderMute(); if (!FX.muted) FX.play('blip');
  });
  $('chooseTake').addEventListener('click', () => choose(true));
  $('chooseDel').addEventListener('click', () => choose(false));
  $('cardContinue').addEventListener('click', () => {
    $('cardOverlay').hidden = true; inCard = false; currentCard = null;
  });

  // debug/testing hook (used by the club's Playwright smoke test)
  window.SIM = {
    start: startScenario, state: () => game, setSpeed,
    step: n => { for (let i = 0; i < (n || 1) && game && !game.over && !inCard; i++) step(); },
    choose, model: S
  };

  renderMute();
  renderTitle();
})();
