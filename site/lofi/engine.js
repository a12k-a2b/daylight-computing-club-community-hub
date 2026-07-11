/* Lofi — generative audio engine.
   Reads a scene spec from scenes.js and builds it live out of Web Audio
   nodes: looped generated-noise buffers, slow oscillator drones, and a
   scheduler that sprinkles in one-off events (plucked notes, bells, bird
   phrases, rain plinks, thunder). No samples, no network, no loops you
   can spot — the tablet is the instrument. */

const Engine = (() => {
  let ctx = null;
  let master, compressor, wobble, wobbleGain;
  let crackleBed = null;          // lo-fi vinyl texture, global
  let live = null;                // currently playing scene state
  let volume = 0.7;
  let binauralOn = true;
  let textureOn = true;
  let onBreath = null;            // cb(label, seconds) for the breath pacer

  const rand = (lo, hi) => lo + Math.random() * (hi - lo);
  const pick = arr => arr[Math.floor(Math.random() * arr.length)];

  /* ---------- context & master chain ---------- */

  function ensureCtx() {
    if (ctx) return;
    ctx = new (window.AudioContext || window.webkitAudioContext)();

    master = ctx.createGain();
    master.gain.value = volume;

    compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = -24;
    compressor.knee.value = 20;
    compressor.ratio.value = 4;

    master.connect(compressor);
    compressor.connect(ctx.destination);

    // one slow "tape wobble" LFO shared by every pitched voice
    wobble = ctx.createOscillator();
    wobble.frequency.value = 0.4;
    wobbleGain = ctx.createGain();
    wobbleGain.gain.value = textureOn ? 5 : 0;   // cents of detune
    wobble.connect(wobbleGain);
    wobble.start();
  }

  /* ---------- generated buffers ---------- */

  function noiseBuffer(color) {
    const len = ctx.sampleRate * 4;
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    if (color === 'white') {
      for (let i = 0; i < len; i++) d[i] = Math.random() * 2 - 1;
    } else if (color === 'pink') {
      let b0 = 0, b1 = 0, b2 = 0;
      for (let i = 0; i < len; i++) {
        const w = Math.random() * 2 - 1;
        b0 = 0.99765 * b0 + w * 0.0990460;
        b1 = 0.96300 * b1 + w * 0.2965164;
        b2 = 0.57000 * b2 + w * 1.0526913;
        d[i] = (b0 + b1 + b2 + w * 0.1848) * 0.18;
      }
    } else { // brown
      let last = 0;
      for (let i = 0; i < len; i++) {
        last = (last + 0.02 * (Math.random() * 2 - 1)) / 1.02;
        d[i] = last * 3.5;
      }
    }
    return buf;
  }

  // sparse random impulses — fire crackle, vinyl dust
  function crackleBuffer(density, decaySamples) {
    const len = ctx.sampleRate * 4;
    const buf = ctx.createBuffer(1, len, ctx.sampleRate);
    const d = buf.getChannelData(0);
    const pops = Math.floor(density * 4);
    for (let p = 0; p < pops; p++) {
      const at = Math.floor(Math.random() * (len - decaySamples));
      const amp = Math.random() * Math.random();
      const dur = Math.floor(decaySamples * (0.3 + Math.random() * 0.7));
      for (let i = 0; i < dur; i++) {
        d[at + i] += (Math.random() * 2 - 1) * amp * (1 - i / dur);
      }
    }
    return buf;
  }

  /* ---------- voices ---------- */

  function connectWobble(osc) {
    wobbleGain.connect(osc.detune);
  }

  function looped(buf, bus, gainVal, filterSpec) {
    const src = ctx.createBufferSource();
    src.buffer = buf;
    src.loop = true;
    src.playbackRate.value = rand(0.97, 1.03); // decorrelate reloads
    const g = ctx.createGain();
    g.gain.value = gainVal;
    let head = src;
    let filter = null;
    if (filterSpec) {
      filter = ctx.createBiquadFilter();
      filter.type = filterSpec.type;
      filter.frequency.value = filterSpec.freq;
      filter.Q.value = filterSpec.q || 1;
      head.connect(filter);
      head = filter;
    }
    head.connect(g);
    g.connect(bus);
    src.start();
    return { src, gain: g, filter };
  }

  function addLFO(state, param, rate, depth, offsetPhase) {
    const lfo = ctx.createOscillator();
    lfo.frequency.value = rate * rand(0.85, 1.15);
    const lg = ctx.createGain();
    lg.gain.value = depth;
    lfo.connect(lg);
    lg.connect(param);
    lfo.start(ctx.currentTime + (offsetPhase || 0));
    state.nodes.push(lfo, lg);
  }

  // one struck note: fundamental + one quieter partial, exponential decay
  function strike(bus, freq, gainVal, decay, type, partial) {
    const t = ctx.currentTime + 0.03;
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gainVal, t + 0.02);
    g.gain.exponentialRampToValueAtTime(0.0001, t + decay);
    const pan = ctx.createStereoPanner();
    pan.pan.value = rand(-0.6, 0.6);
    g.connect(pan);
    pan.connect(bus);

    const voices = [[freq, 1]];
    if (partial) voices.push([freq * partial, 0.25]);
    voices.forEach(([f, amp]) => {
      const o = ctx.createOscillator();
      o.type = type || 'sine';
      o.frequency.value = f;
      connectWobble(o);
      const og = ctx.createGain();
      og.gain.value = amp;
      o.connect(og);
      og.connect(g);
      o.start(t);
      o.stop(t + decay + 0.1);
    });
  }

  // a bell: inharmonic partials, long ring
  function bell(bus, freq, gainVal, decay, when) {
    const t = (when || ctx.currentTime) + 0.03;
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gainVal, t + 0.04);
    g.gain.exponentialRampToValueAtTime(0.0001, t + decay);
    const pan = ctx.createStereoPanner();
    pan.pan.value = rand(-0.4, 0.4);
    g.connect(pan);
    pan.connect(bus);
    [[1, 1], [2.76, 0.3], [5.4, 0.08]].forEach(([ratio, amp]) => {
      const o = ctx.createOscillator();
      o.frequency.value = freq * ratio;
      connectWobble(o);
      const og = ctx.createGain();
      og.gain.value = amp;
      o.connect(og);
      og.connect(g);
      o.start(t);
      o.stop(t + decay + 0.1);
    });
  }

  // a short bird phrase: 2–5 swept chirps
  function birdPhrase(bus, gainVal) {
    const base = rand(2100, 3600);
    const chirps = 2 + Math.floor(Math.random() * 4);
    const pan = ctx.createStereoPanner();
    pan.pan.value = rand(-0.8, 0.8);
    pan.connect(bus);
    let t = ctx.currentTime + 0.05;
    for (let i = 0; i < chirps; i++) {
      const o = ctx.createOscillator();
      const g = ctx.createGain();
      const dur = rand(0.05, 0.14);
      o.frequency.setValueAtTime(base * rand(0.9, 1.1), t);
      o.frequency.exponentialRampToValueAtTime(base * rand(0.75, 1.35), t + dur);
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(gainVal, t + dur * 0.3);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
      o.connect(g);
      g.connect(pan);
      o.start(t);
      o.stop(t + dur + 0.05);
      t += dur + rand(0.04, 0.2);
    }
  }

  // rain plink: a tiny damped ping
  function plink(bus, gainVal) {
    const t = ctx.currentTime + 0.02;
    const o = ctx.createOscillator();
    o.frequency.value = rand(700, 2400);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gainVal * rand(0.3, 1), t + 0.005);
    g.gain.exponentialRampToValueAtTime(0.0001, t + rand(0.08, 0.2));
    const pan = ctx.createStereoPanner();
    pan.pan.value = rand(-0.9, 0.9);
    o.connect(g);
    g.connect(pan);
    pan.connect(bus);
    o.start(t);
    o.stop(t + 0.3);
  }

  // far-off thunder: a slow swell of deep noise
  function rumble(bus, gainVal, brownBuf) {
    const t = ctx.currentTime;
    const src = ctx.createBufferSource();
    src.buffer = brownBuf;
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.value = 110;
    const g = ctx.createGain();
    const dur = rand(4, 8);
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(gainVal, t + dur * 0.35);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    src.connect(f);
    f.connect(g);
    g.connect(bus);
    src.start(t, rand(0, 2));
    src.stop(t + dur + 0.1);
  }

  /* ---------- event scheduler ---------- */

  function every(state, intervalRange, fn) {
    const tick = () => {
      if (state.stopped) return;
      fn();
      const id = setTimeout(tick, rand(intervalRange[0], intervalRange[1]) * 1000);
      state.timers.push(id);
    };
    const id = setTimeout(tick, rand(0.5, intervalRange[1]) * 1000);
    state.timers.push(id);
  }

  /* ---------- breath pacer ---------- */

  function runBreath(state, spec, layerGain, baseGain) {
    const pattern = spec.pattern;
    let i = 0;
    const step = () => {
      if (state.stopped) return;
      const [label, secs] = pattern[i % pattern.length];
      const t = ctx.currentTime;
      const g = layerGain.gain;
      if (/in/i.test(label)) {
        g.cancelScheduledValues(t);
        g.setValueAtTime(g.value, t);
        g.linearRampToValueAtTime(baseGain, t + secs);
      } else if (/out/i.test(label)) {
        g.cancelScheduledValues(t);
        g.setValueAtTime(g.value, t);
        g.linearRampToValueAtTime(baseGain * 0.12, t + secs);
      }
      if (onBreath) onBreath(label, secs);
      i++;
      const id = setTimeout(step, secs * 1000);
      state.timers.push(id);
    };
    step();
  }

  /* ---------- layer builder ---------- */

  function buildLayer(spec, bus, state) {
    switch (spec.kind) {

      case 'noise': {
        const startGain = spec.breathLayer ? spec.gain * 0.12 : spec.gain;
        const v = looped(noiseBuffer(spec.color), bus, startGain, spec.filter);
        state.nodes.push(v.src, v.gain);
        if (spec.lfo) {
          const param = spec.lfo.target === 'freq' && v.filter
            ? v.filter.frequency : v.gain.gain;
          const depth = spec.lfo.target === 'freq'
            ? spec.lfo.depth : spec.gain * spec.lfo.depth;
          addLFO(state, param, spec.lfo.rate, depth);
        }
        if (spec.breathLayer && state.scene.breath) {
          runBreath(state, state.scene.breath, v.gain, spec.gain);
        }
        break;
      }

      case 'drone': {
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, ctx.currentTime);
        g.gain.exponentialRampToValueAtTime(
          spec.gain, ctx.currentTime + (spec.attack || 8));
        g.connect(bus);
        state.nodes.push(g);
        if (spec.breathe) {
          addLFO(state, g.gain, 0.05, spec.gain * (spec.breathe / 0.02) * 0.3);
        }
        spec.notes.forEach(f => {
          const o = ctx.createOscillator();
          o.type = spec.type || 'sine';
          o.frequency.value = f;
          o.detune.value = rand(-4, 4);
          connectWobble(o);
          const og = ctx.createGain();
          og.gain.value = 1 / spec.notes.length;
          o.connect(og);
          og.connect(g);
          o.start();
          state.nodes.push(o, og);
        });
        break;
      }

      case 'plucks':
        every(state, spec.interval, () =>
          strike(bus, pick(spec.notes), spec.gain, spec.decay || 2.5,
                 'sine', spec.partial));
        break;

      case 'bells':
        every(state, spec.interval, () =>
          bell(bus, pick(spec.notes), spec.gain, spec.decay || 6));
        break;

      case 'birds':
        every(state, spec.interval, () => birdPhrase(bus, spec.gain));
        break;

      case 'plinks':
        every(state, spec.interval, () => plink(bus, spec.gain));
        break;

      case 'rumble': {
        const buf = noiseBuffer('brown');
        every(state, spec.interval, () => rumble(bus, spec.gain, buf));
        break;
      }

      case 'crackle': {
        const v = looped(
          crackleBuffer(spec.density, Math.floor(ctx.sampleRate * 0.01)),
          bus, spec.gain, spec.filter);
        state.nodes.push(v.src, v.gain);
        break;
      }

      // a single steady tone (the lab's solfeggio tiles) — softened with a
      // slow amplitude breath so a bare sine doesn't pierce
      case 'tone': {
        const g = ctx.createGain();
        g.gain.setValueAtTime(0.0001, ctx.currentTime);
        g.gain.exponentialRampToValueAtTime(spec.gain, ctx.currentTime + (spec.attack || 4));
        g.connect(bus);
        const o = ctx.createOscillator();
        o.frequency.value = spec.freq;
        connectWobble(o);
        o.connect(g);
        o.start();
        state.nodes.push(o, g);
        if (spec.am) addLFO(state, g.gain, spec.am.rate, spec.gain * spec.am.depth);
        break;
      }

      // isochronic pulse: a carrier tone fully amplitude-modulated at the
      // target rate — unlike binaural beats this works on speakers
      case 'iso': {
        const level = ctx.createGain();
        level.gain.setValueAtTime(0.0001, ctx.currentTime);
        level.gain.exponentialRampToValueAtTime(spec.gain, ctx.currentTime + 3);
        level.connect(bus);
        const am = ctx.createGain();
        am.gain.value = 0;                      // driven to 0..1 below
        am.connect(level);
        const dc = ctx.createConstantSource();
        dc.offset.value = 0.5;
        dc.connect(am.gain);
        dc.start();
        const lfo = ctx.createOscillator();
        lfo.frequency.value = spec.rate;
        const lg = ctx.createGain();
        lg.gain.value = 0.5;
        lfo.connect(lg);
        lg.connect(am.gain);
        lfo.start();
        const o = ctx.createOscillator();
        o.frequency.value = spec.freq;
        o.connect(am);
        o.start();
        state.nodes.push(o, lfo, lg, dc, am, level);
        break;
      }

      // a slow resting heartbeat: lub … dub, all low sine thumps
      case 'heartbeat': {
        const period = 60 / (spec.bpm || 60);
        const thump = (t, f, amp) => {
          const o = ctx.createOscillator();
          o.frequency.setValueAtTime(f, t);
          o.frequency.exponentialRampToValueAtTime(f * 0.6, t + 0.1);
          const g = ctx.createGain();
          g.gain.setValueAtTime(0.0001, t);
          g.gain.exponentialRampToValueAtTime(amp, t + 0.02);
          g.gain.exponentialRampToValueAtTime(0.0001, t + 0.22);
          o.connect(g);
          g.connect(bus);
          o.start(t);
          o.stop(t + 0.3);
        };
        every(state, [period, period], () => {
          const t = ctx.currentTime + 0.05;
          thump(t, 62, spec.gain);
          thump(t + 0.22, 50, spec.gain * 0.7);
        });
        break;
      }

      // singing bowls: long inharmonic strikes, like my bells but slower
      case 'bowls':
        every(state, spec.interval, () =>
          bell(bus, pick(spec.notes), spec.gain, spec.decay || 14));
        break;

      case 'waves': {
        // two overlapping swells at incommensurate periods = a real-ish sea
        [[0.055, 0], [0.082, 6]].forEach(([rate, phase]) => {
          const v = looped(noiseBuffer('white'), bus, spec.gain * 0.5,
                           { type: 'lowpass', freq: 450, q: 0.7 });
          state.nodes.push(v.src, v.gain);
          addLFO(state, v.gain.gain, rate, spec.gain * 0.45, phase);
          addLFO(state, v.filter.frequency, rate, 320, phase);
        });
        break;
      }
    }
  }

  function buildBinaural(spec, bus, state) {
    const g = ctx.createGain();
    g.gain.value = binauralOn ? 0.05 : 0;
    g.connect(bus);
    state.binauralGain = g;
    [[-1, spec.carrier - spec.beat / 2], [1, spec.carrier + spec.beat / 2]]
      .forEach(([side, freq]) => {
        const o = ctx.createOscillator();
        o.frequency.value = freq;
        const pan = ctx.createStereoPanner();
        pan.pan.value = side;
        o.connect(pan);
        pan.connect(g);
        o.start();
        state.nodes.push(o, pan);
      });
    state.nodes.push(g);
  }

  /* ---------- public: play / stop ---------- */

  function play(scene) {
    ensureCtx();
    if (ctx.state === 'suspended') ctx.resume();
    stop(0.4);

    const bus = ctx.createGain();
    bus.gain.setValueAtTime(0.0001, ctx.currentTime);
    bus.gain.exponentialRampToValueAtTime(1, ctx.currentTime + 1.5);
    bus.connect(master);

    const state = { scene, bus, nodes: [], timers: [], stopped: false };
    live = state;

    scene.layers.forEach(l => buildLayer(l, bus, state));
    if (scene.binaural) buildBinaural(scene.binaural, bus, state);
    setTexture(textureOn);
    return state;
  }

  function stop(fadeSecs) {
    if (!live) return;
    const state = live;
    live = null;
    state.stopped = true;
    state.timers.forEach(clearTimeout);
    if (crackleBed) crackleBed.gain.gain.setTargetAtTime(0, ctx.currentTime, 0.3);
    const t = ctx.currentTime;
    const fade = fadeSecs == null ? 1 : fadeSecs;
    state.bus.gain.cancelScheduledValues(t);
    state.bus.gain.setValueAtTime(Math.max(state.bus.gain.value, 0.0001), t);
    state.bus.gain.exponentialRampToValueAtTime(0.0001, t + Math.max(fade, 0.05));
    setTimeout(() => {
      state.nodes.forEach(n => {
        try { if (n.stop) n.stop(); } catch (e) { /* already stopped */ }
        try { n.disconnect(); } catch (e) { /* already gone */ }
      });
      try { state.bus.disconnect(); } catch (e) { /* already gone */ }
    }, (fade + 0.2) * 1000);
  }

  // long goodbye for timers: fade over `secs`, then tear down
  function fadeOut(secs) {
    if (!live) return;
    const state = live;
    const t = ctx.currentTime;
    state.bus.gain.cancelScheduledValues(t);
    state.bus.gain.setValueAtTime(Math.max(state.bus.gain.value, 0.0001), t);
    state.bus.gain.exponentialRampToValueAtTime(0.0001, t + secs);
    const id = setTimeout(() => { if (live === state) stop(0.1); }, secs * 1000);
    state.timers.push(id);
  }

  // the nap alarm: three slow rising rounds of a gentle bell arpeggio
  function wakeChime() {
    ensureCtx();
    const bus = ctx.createGain();
    bus.gain.value = 1;
    bus.connect(master);
    const notes = [523.25, 659.26, 783.99, 1046.5]; // C5 E5 G5 C6
    let t = ctx.currentTime + 0.2;
    for (let round = 0; round < 3; round++) {
      const vol = 0.05 + round * 0.04;
      notes.forEach(f => { bell(bus, f, vol, 5, t); t += 1.6; });
      t += 2.5;
    }
    setTimeout(() => { try { bus.disconnect(); } catch (e) {} }, (t - ctx.currentTime + 6) * 1000);
    return t - ctx.currentTime; // seconds until the chime is done
  }

  /* ---------- public: settings ---------- */

  function setVolume(v) {
    volume = v;
    if (master) master.gain.setTargetAtTime(v, ctx.currentTime, 0.1);
  }

  function setBinaural(on) {
    binauralOn = on;
    if (live && live.binauralGain) {
      live.binauralGain.gain.setTargetAtTime(on ? 0.05 : 0, ctx.currentTime, 0.3);
    }
  }

  function setTexture(on) {
    textureOn = on;
    if (!ctx) return;
    wobbleGain.gain.value = on ? 5 : 0;
    if (on && !crackleBed) {
      crackleBed = looped(
        crackleBuffer(6, Math.floor(ctx.sampleRate * 0.004)),
        master, 0.015, { type: 'highpass', freq: 1800 });
    }
    if (crackleBed) {
      crackleBed.gain.gain.setTargetAtTime(on && live ? 0.015 : 0, ctx.currentTime, 0.3);
    }
  }

  // one bowl strike on demand (the lab's "strike again" button)
  function strikeBowl(freq) {
    ensureCtx();
    if (ctx.state === 'suspended') ctx.resume();
    const b = ctx.createGain();
    b.connect(master);
    bell(b, freq || 196, 0.09, 14);
    setTimeout(() => { try { b.disconnect(); } catch (e) {} }, 16000);
  }

  return {
    play, stop, fadeOut, wakeChime, strikeBowl, setVolume, setBinaural, setTexture,
    set onBreath(fn) { onBreath = fn; },
    get playing() { return live ? live.scene : null; }
  };
})();
