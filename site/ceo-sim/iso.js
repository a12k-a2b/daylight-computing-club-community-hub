/* The Decider — isometric edition renderer.
   Defines window.BOARD before game.js loads; game.js delegates the org view
   to it. Everything is ink on paper: white faces, black 2px outlines,
   diagonal hatching for shade — a tiny civilization drawn by hand. */
(function () {
  'use strict';
  const TW = 76, TH = 38;           // tile diamond
  const FLOOR = 13;                 // px per building floor

  // Estate layout in grid units. HQ up top, departments around the green.
  const SITES = {
    CEO:     { gx: 2.6, gy: 0.5, label: 'SUNBEAM HQ' },
    ENG:     { gx: 0.2, gy: 2.4, label: 'ENG' },
    PRODUCT: { gx: 1.7, gy: 4.1, label: 'PRODUCT' },
    SALES:   { gx: 3.9, gy: 3.7, label: 'SALES' },
    OPS:     { gx: 5.1, gy: 1.7, label: 'OPS' }
  };
  const DEPTS = ['ENG', 'PRODUCT', 'SALES', 'OPS'];

  let cv, ctx, W, H, OX, OY;
  let cur = null;                   // latest game state from render()
  let mode = 'A';
  let fires = {};                   // dept -> until-timestamp
  let papers = [];                  // {from,to,t,dur,spin}
  let workers = [];                 // {road, t, dir, speed}
  let doorsDrawn = 0;
  let raf = 0, lastSpawn = 0;

  const iso = (gx, gy) => ({ x: OX + (gx - gy) * TW / 2, y: OY + (gx + gy) * TH / 2 });

  function sizeCanvas() {
    if (!cv) return;
    const w = cv.clientWidth, h = cv.clientHeight;
    const dpr = window.devicePixelRatio || 1;
    if (cv.width !== w * dpr) { cv.width = w * dpr; cv.height = h * dpr; }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    W = w; H = h;
    OX = W / 2 + 30;
    OY = 78;
  }

  // ---- ink primitives ----------------------------------------------------
  function poly(pts, fill, stroke, lw) {
    ctx.beginPath();
    pts.forEach((p, i) => i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y));
    ctx.closePath();
    if (fill) { ctx.fillStyle = fill; ctx.fill(); }
    if (stroke) { ctx.strokeStyle = stroke; ctx.lineWidth = lw || 2; ctx.stroke(); }
  }

  function hatch(pts, gap) {
    // diagonal hatching clipped to a polygon — our stand-in for shade
    ctx.save();
    ctx.beginPath();
    pts.forEach((p, i) => i ? ctx.lineTo(p.x, p.y) : ctx.moveTo(p.x, p.y));
    ctx.closePath();
    ctx.clip();
    const xs = pts.map(p => p.x), ys = pts.map(p => p.y);
    const x0 = Math.min.apply(0, xs) - 40, x1 = Math.max.apply(0, xs);
    const y0 = Math.min.apply(0, ys), y1 = Math.max.apply(0, ys);
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    for (let x = x0; x < x1 + (y1 - y0); x += gap || 5) {
      ctx.beginPath();
      ctx.moveTo(x, y0);
      ctx.lineTo(x + (y1 - y0), y1);
      ctx.stroke();
    }
    ctx.restore();
  }

  function tileDiamond(gx, gy, w, d) {
    const a = iso(gx, gy), b = iso(gx + w, gy), c = iso(gx + w, gy + d), e = iso(gx, gy + d);
    return [a, b, c, e];
  }

  // A building: extruded box with hatched right face, floor lines, windows.
  function building(gx, gy, floors, label, shakeX) {
    const h = floors * FLOOR;
    const sx = shakeX || 0;
    const base = tileDiamond(gx, gy, 1, 1).map(p => ({ x: p.x + sx, y: p.y }));
    const top = base.map(p => ({ x: p.x, y: p.y - h }));
    const [tN, tE, tS, tW] = top;
    const [bN, bE, bS, bW] = base;
    // left (SW) face
    poly([tW, tS, bS, bW], '#fff', '#000');
    // right (SE) face, hatched
    poly([tS, tE, bE, bS], '#fff', '#000');
    hatch([tS, tE, bE, bS], 5);
    // roof
    poly([tN, tE, tS, tW], '#fff', '#000');
    // floor lines + windows on the left face
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    for (let f = 1; f < floors; f++) {
      const y = -f * FLOOR;
      ctx.beginPath();
      ctx.moveTo(bW.x, bW.y + y); ctx.lineTo(bS.x, bS.y + y);
      ctx.stroke();
    }
    for (let f = 0; f < floors; f++) {
      const y = -f * FLOOR - FLOOR * 0.45;
      for (let k = 1; k <= 2; k++) {
        const t = k / 3;
        const x = bW.x + (bS.x - bW.x) * t, yy = bW.y + (bS.y - bW.y) * t + y;
        ctx.beginPath(); ctx.moveTo(x - 3, yy); ctx.lineTo(x + 3, yy); ctx.stroke();
      }
    }
    // label plate under the base
    if (label) {
      ctx.font = '10px ui-monospace, "Courier New", monospace';
      ctx.textAlign = 'center'; ctx.fillStyle = '#000';
      ctx.fillText(label, bS.x, bS.y + 14);
    }
    return { top: { x: (tN.x + tS.x) / 2, y: (tN.y + tS.y) / 2 }, base: bS };
  }

  function flag(pt, morale) {
    // HQ flag: proud at high morale, drooping as it falls
    const droop = (100 - morale) / 100;
    ctx.strokeStyle = '#000'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(pt.x, pt.y); ctx.lineTo(pt.x, pt.y - 22); ctx.stroke();
    const fy = pt.y - 22, len = 16, sag = droop * 14;
    poly([
      { x: pt.x, y: fy },
      { x: pt.x + len, y: fy + sag * 0.6 },
      { x: pt.x + len * 0.9, y: fy + 7 + sag },
      { x: pt.x, y: fy + 7 }
    ], '#fff', '#000', 1.5);
  }

  function pilePaper(queue, t) {
    // the queue as a literal tower of paper beside HQ
    const site = SITES.CEO;
    const slabs = Math.min(26, Math.round(queue / 2.2));
    const lean = queue > 55 ? Math.sin(t / 300) * 2 + 6 : 0;
    const p0 = iso(site.gx - 0.7, site.gy + 0.75);
    for (let i = 0; i < slabs; i++) {
      const y = p0.y - i * 4;
      const dx = lean * (i / 26);
      poly([
        { x: p0.x - 13 + dx, y: y }, { x: p0.x + 13 + dx, y: y },
        { x: p0.x + 13 + dx, y: y - 3 }, { x: p0.x - 13 + dx, y: y - 3 }
      ], '#fff', '#000', 1);
    }
    if (queue >= 1) {
      ctx.font = '10px ui-monospace, "Courier New", monospace';
      ctx.textAlign = 'center'; ctx.fillStyle = '#000';
      ctx.fillText('QUEUE ' + Math.round(queue), p0.x, p0.y + 13);
    }
  }

  function flame(x, y, t, s) {
    const w = 7 * s, h = 13 * s, fl = Math.sin(t / 90 + x) * 2.5;
    poly([
      { x: x, y: y },
      { x: x - w, y: y - h * 0.45 },
      { x: x - w * 0.3, y: y - h * 0.55 },
      { x: x + fl, y: y - h },
      { x: x + w * 0.35, y: y - h * 0.5 },
      { x: x + w, y: y - h * 0.4 }
    ], '#000', null);
  }

  function smoke(x, y, t) {
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    for (let i = 0; i < 3; i++) {
      const ph = ((t / 900) + i / 3) % 1;
      const yy = y - 8 - ph * 34, xx = x + Math.sin(ph * 6 + i) * 5;
      ctx.beginPath(); ctx.arc(xx, yy, 3 + ph * 5, 0, Math.PI * 2); ctx.stroke();
    }
  }

  function monolith(i, label) {
    // a one-way door, standing on the front lawn like a monument to itself
    const gx = 0.3 + i * 0.55, gy = 4.9 - i * 0.18;
    const p = iso(gx, gy);
    poly([
      { x: p.x - 6, y: p.y }, { x: p.x + 6, y: p.y },
      { x: p.x + 6, y: p.y - 24 }, { x: p.x - 6, y: p.y - 24 }
    ], '#000', '#000');
    ctx.font = '8px ui-monospace, "Courier New", monospace';
    ctx.textAlign = 'center'; ctx.fillStyle = '#000';
    if (label) ctx.fillText(label.slice(0, 9), p.x, p.y + 9);
  }

  function tree(gx, gy) {
    const p = iso(gx, gy);
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1.5;
    ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(p.x, p.y - 9); ctx.stroke();
    ctx.beginPath(); ctx.arc(p.x, p.y - 14, 6, 0, Math.PI * 2);
    ctx.fillStyle = '#fff'; ctx.fill(); ctx.stroke();
  }

  function sun(morale, t) {
    // civilization needs a sky: the sun swells with morale
    const r = 8 + morale / 9;
    const x = W - 52, y = 46;
    ctx.strokeStyle = '#000'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fillStyle = '#fff'; ctx.fill(); ctx.stroke();
    if (morale > 55) {
      ctx.lineWidth = 1.5;
      for (let i = 0; i < 8; i++) {
        const a = i * Math.PI / 4 + t / 4000;
        ctx.beginPath();
        ctx.moveTo(x + Math.cos(a) * (r + 4), y + Math.sin(a) * (r + 4));
        ctx.lineTo(x + Math.cos(a) * (r + 10), y + Math.sin(a) * (r + 10));
        ctx.stroke();
      }
    }
  }

  function moonbeam(rival, t) {
    // the competition, on its own islet across the water off the front-left
    // shore — every week your engine underperforms, their tower gains height
    poly(tileDiamond(-1.7, 5.9, 1.9, 1.6), '#fff', '#000');
    // water between the islands
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    for (let i = 0; i < 3; i++) {
      const p = iso(-0.55 + i * 0.12, 5.45 + i * 0.4);
      ctx.beginPath();
      for (let s = 0; s <= 4; s++) {
        const x = p.x + s * 8, y = p.y + Math.sin(s * Math.PI + t / 700 + i) * 2;
        s ? ctx.lineTo(x, y) : ctx.moveTo(x, y);
      }
      ctx.stroke();
    }
    const floors = Math.max(1, Math.min(12, Math.round(1 + rival / 9)));
    const ref = building(-1.25, 6.2, floors, 'MOONBEAM');
    if (rival > 60) building(-0.5, 6.55, Math.max(1, floors - 4), null);
    // their crescent flag, always crisp
    ctx.strokeStyle = '#000'; ctx.lineWidth = 2;
    ctx.beginPath(); ctx.moveTo(ref.top.x, ref.top.y); ctx.lineTo(ref.top.x, ref.top.y - 16); ctx.stroke();
    ctx.beginPath(); ctx.arc(ref.top.x + 5, ref.top.y - 18, 5, 0, Math.PI * 2);
    ctx.fillStyle = '#000'; ctx.fill();
    ctx.beginPath(); ctx.arc(ref.top.x + 7.5, ref.top.y - 19.5, 4.2, 0, Math.PI * 2);
    ctx.fillStyle = '#fff'; ctx.fill();
    ctx.font = '9px ui-monospace, "Courier New", monospace';
    ctx.textAlign = 'center'; ctx.fillStyle = '#000';
    ctx.fillText(rival > 55 ? 'THE COMPETITION (THRIVING)' : 'THE COMPETITION',
      Math.max(ref.base.x, 96), ref.base.y + 26);
  }

  function cracks() {
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1.5;
    [[0.4, 2.2], [2.6, 3.1], [3.8, 1.4]].forEach((c, i) => {
      let p = iso(c[0], c[1]);
      ctx.beginPath(); ctx.moveTo(p.x, p.y);
      for (let s = 0; s < 5; s++) {
        p = { x: p.x + 14 + (i * 7 % 10), y: p.y + (s % 2 ? 7 : -5) };
        ctx.lineTo(p.x, p.y);
      }
      ctx.stroke();
    });
  }

  // ---- the frame ---------------------------------------------------------
  function frame(t) {
    raf = requestAnimationFrame(frame);
    if (!cur || !ctx || document.hidden) return;
    sizeCanvas();
    const g = cur;
    ctx.clearRect(0, 0, W, H);

    // the island the company lives on
    const ground = tileDiamond(-0.8, -0.3, 7.4, 5.8);
    poly(ground, '#fff', '#000');
    // grounds furniture
    tree(0.2, 1.9); tree(5.3, 3.4); tree(2.1, 4.9); tree(4.9, 0.9);
    sun(g.morale, t);
    moonbeam(g.rival || 6, t);

    // roads from each department to HQ
    const hqBase = iso(SITES.CEO.gx + 0.5, SITES.CEO.gy + 0.9);
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    ctx.setLineDash([5, 4]);
    DEPTS.forEach(k => {
      const s = SITES[k], p = iso(s.gx + 0.5, s.gy + 0.15);
      ctx.beginPath(); ctx.moveTo(p.x, p.y); ctx.lineTo(hqBase.x, hqBase.y); ctx.stroke();
    });
    ctx.setLineDash([]);

    if (g.over && g.ended !== 'C_WIN') cracks();

    // paper pile (the queue)
    pilePaper(g.queue, t);

    // buildings, back to front; floors follow staff counts
    const per = Math.max(1, Math.round(g.headcount / 4));
    const tops = {};
    const order = Object.keys(SITES).sort((a, b) => (SITES[a].gx + SITES[a].gy) - (SITES[b].gx + SITES[b].gy));
    order.forEach(k => {
      const s = SITES[k];
      const burning = fires[k] && fires[k] > t;
      const shake = burning ? Math.sin(t / 30) * 2 : 0;
      const floors = k === 'CEO'
        ? 5 + Math.min(3, Math.round(g.queue / 22))
        : Math.max(1, Math.min(7, Math.round(per / 2.2)));
      const ref = building(s.gx, s.gy, floors, s.label, shake);
      tops[k] = ref;
      if (k === 'CEO') flag({ x: ref.top.x, y: ref.top.y }, g.morale);
      if (burning) {
        flame(ref.top.x - 6, ref.top.y + 4, t, 1);
        flame(ref.top.x + 7, ref.top.y + 6, t + 200, 0.8);
        smoke(ref.top.x, ref.top.y - 6, t);
      }
    });

    // one-way doors as monoliths on the front lawn (in front of the city)
    (g.doors || []).forEach((d, i) => monolith(i, d));

    // citizens commuting; speed follows ship speed
    ctx.fillStyle = '#000';
    ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
    workers.forEach(wk => {
      wk.t += wk.dir * wk.speed * (0.3 + g.speed);
      if (wk.t > 1 || wk.t < 0) { wk.dir *= -1; wk.t = Math.max(0, Math.min(1, wk.t)); }
      const s = SITES[wk.road], a = iso(s.gx + 0.5, s.gy + 0.15);
      const x = a.x + (hqBase.x - a.x) * wk.t, y = a.y + (hqBase.y - a.y) * wk.t;
      ctx.beginPath(); ctx.arc(x, y - 3, 2.4, 0, Math.PI * 2); ctx.fill();
      ctx.beginPath(); ctx.moveTo(x, y - 1); ctx.lineTo(x, y + 3); ctx.stroke();
    });

    // memos in flight
    for (let i = papers.length - 1; i >= 0; i--) {
      const p = papers[i];
      p.t += 16 / p.dur;
      if (p.t >= 1) { papers.splice(i, 1); continue; }
      const a = tops[p.from] ? tops[p.from].top : hqBase;
      const b = tops[p.to] ? tops[p.to].top : hqBase;
      const x = a.x + (b.x - a.x) * p.t;
      const y = a.y + (b.y - a.y) * p.t - Math.sin(p.t * Math.PI) * 34;
      ctx.save();
      ctx.translate(x, y); ctx.rotate(p.t * p.spin);
      poly([{ x: -5, y: 0 }, { x: 0, y: -3.4 }, { x: 5, y: 0 }, { x: 0, y: 3.4 }], '#fff', '#000', 1.2);
      ctx.restore();
    }
  }

  // ---- BOARD API (called by game.js) ------------------------------------
  window.BOARD = {
    reset(m) {
      mode = m; fires = {}; papers = []; doorsDrawn = 0;
      cv = document.getElementById('isoCanvas');
      ctx = cv.getContext('2d');
      sizeCanvas();
      workers = [];
      for (let i = 0; i < 14; i++) {
        workers.push({
          road: DEPTS[i % 4],
          t: (i * 0.37) % 1,
          dir: i % 2 ? 1 : -1,
          speed: 0.002 + (i % 5) * 0.0009
        });
      }
      if (!raf) raf = requestAnimationFrame(frame);
    },
    render(g) {
      cur = g;
      // one memo per tick, routed by the philosophy being played
      const now = performance.now();
      if (now - lastSpawn > 220 && papers.length < 9) {
        lastSpawn = now;
        const from = DEPTS[Math.floor(Math.random() * 4)];
        let to = 'CEO';
        if (g.mode === 'B' || g.mode === 'E') to = DEPTS[Math.floor(Math.random() * 4)];
        else if (g.mode === 'D') to = Math.random() < 0.35 ? 'CEO' : from;
        else if (g.mode === 'C') to = Math.random() < 0.25 ? 'CEO' : from;
        if (to !== from) papers.push({ from, to, t: 0, dur: 950, spin: Math.random() * 6 - 3 });
      }
      // citizen count tracks headcount
      const want = Math.max(8, Math.min(26, Math.round(g.headcount / 3.4)));
      while (workers.length < want) {
        workers.push({ road: DEPTS[workers.length % 4], t: Math.random(), dir: 1, speed: 0.002 + Math.random() * 0.004 });
      }
      while (workers.length > want) workers.pop();
    },
    fire(key) {
      fires[key] = performance.now() + 6000;
    },
    anchor(key) {
      // bubble anchor in #orgboard coordinates: above the building
      if (!cv) return null;
      const s = SITES[key] || SITES.CEO;
      const p = iso(s.gx + 0.5, s.gy + 0.5);
      const floors = key === 'CEO' ? 6 : 3;
      return { x: cv.offsetLeft + p.x, y: cv.offsetTop + p.y - floors * FLOOR - 26 };
    }
  };
})();
