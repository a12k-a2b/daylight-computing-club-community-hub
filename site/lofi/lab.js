/* The Lab — experimental sounds, one at a time.
   Each tile is a small scene spec fed to the same engine as the main app.
   Copy stays honest: noise = well-evidenced masking; pulses = entrainment
   research (promising, not settled); solfeggio = folklore, offered warmly. */

(() => {
  const $ = id => document.getElementById(id);

  /* ---------- the lab's sounds ---------- */

  const NOISE = [
    { id: 'white', name: 'White', desc: 'every frequency at once — the classic masker',
      layers: [{ kind: 'noise', color: 'white', gain: 0.05 }] },
    { id: 'pink', name: 'Pink', desc: 'softer up top — the one from the sleep studies',
      layers: [{ kind: 'noise', color: 'pink', gain: 0.08 }] },
    { id: 'brown', name: 'Brown', desc: 'deep and round — the internet’s favorite for focus',
      layers: [{ kind: 'noise', color: 'brown', gain: 0.12 }] }
  ];

  // carrier tones stay low and warm; the rate is the point
  const PULSES = [
    { id: 'delta', name: 'Delta · 2.5 Hz', desc: 'the sleep band', rate: 2.5, carrier: 100 },
    { id: 'theta', name: 'Theta · 5 Hz', desc: 'drowsy, dreamlike', rate: 5, carrier: 120 },
    { id: 'schumann', name: 'Schumann · 7.83 Hz', desc: 'the earth’s own hum, says the folklore', rate: 7.83, carrier: 110 },
    { id: 'alpha', name: 'Alpha · 10 Hz', desc: 'calm and present', rate: 10, carrier: 140 },
    { id: 'gamma', name: 'Gamma · 40 Hz', desc: 'the rate from the memory research', rate: 40, carrier: 220 }
  ].map(p => ({
    id: 'pulse-' + p.id, name: p.name, desc: p.desc,
    layers: [
      { kind: 'iso', freq: p.carrier, rate: p.rate, gain: 0.1 },
      { kind: 'noise', color: 'brown', gain: 0.025, filter: { type: 'lowpass', freq: 400 } }
    ]
  }));

  const SOLFEGGIO = [
    [174, 'ease'], [285, 'mend'], [396, 'release'], [417, 'change'],
    [528, 'the “miracle” one'], [639, 'connect'], [741, 'clear'],
    [852, 'intuition'], [963, 'awaken']
  ].map(([hz, word]) => ({
    id: 'sol-' + hz, name: hz + ' Hz', desc: word,
    layers: [
      { kind: 'tone', freq: hz, gain: 0.1, am: { rate: 0.15, depth: 0.25 } },
      { kind: 'noise', color: 'pink', gain: 0.008, filter: { type: 'lowpass', freq: 700 } }
    ]
  }));

  const TEXTURES = [
    { id: 'heartbeat', name: 'Heartbeat', desc: 'sixty a minute, warm and near — the oldest calm there is',
      layers: [
        { kind: 'heartbeat', bpm: 60, gain: 0.11 },
        { kind: 'noise', color: 'brown', gain: 0.04, filter: { type: 'lowpass', freq: 250 } }
      ] },
    { id: 'bowls', name: 'Singing bowls', desc: 'a bowl rings out every little while — or strike it yourself',
      layers: [
        { kind: 'bowls', notes: [130.81, 196.00, 261.63, 155.56], interval: [18, 34], gain: 0.08, decay: 14 },
        { kind: 'noise', color: 'pink', gain: 0.006, filter: { type: 'lowpass', freq: 500 } }
      ] },
    { id: 'om', name: 'Om drone', desc: '136.1 Hz — the tanpura’s home note, endless',
      layers: [
        { kind: 'drone', notes: [136.1, 136.1 * 1.005, 68.05], type: 'triangle', gain: 0.05, attack: 8, breathe: 0.02 },
        { kind: 'noise', color: 'brown', gain: 0.015, filter: { type: 'lowpass', freq: 200 } }
      ] }
  ];

  /* ---------- board ---------- */

  let current = null;
  let isPlaying = false;

  function tile(scene, grid) {
    const b = document.createElement('button');
    b.className = 'tile';
    b.id = 'tile-' + scene.id;
    b.innerHTML = '<span class="s-name">' + scene.name + '</span>' +
                  '<span class="s-desc">' + scene.desc + '</span>';
    b.addEventListener('click', () => tap(scene));
    grid.appendChild(b);
  }

  NOISE.forEach(s => tile(s, $('grid-noise')));
  PULSES.forEach(s => tile(s, $('grid-pulse')));
  SOLFEGGIO.forEach(s => tile(s, $('grid-solfeggio')));
  TEXTURES.forEach(s => tile(s, $('grid-texture')));

  function tap(scene) {
    if (current && current.id === scene.id && isPlaying) { stop(); return; }
    current = scene;
    Engine.play(scene);
    isPlaying = true;
    refresh();
  }

  function stop() {
    Engine.stop(0.8);
    isPlaying = false;
    refresh();
  }

  function refresh() {
    document.querySelectorAll('.tile').forEach(el => el.classList.remove('active'));
    if (current && isPlaying) {
      const el = $('tile-' + current.id);
      if (el) el.classList.add('active');
    }
    $('playpause').disabled = !isPlaying;
    $('playpause').innerHTML = isPlaying ? '&#9632;' : '&#9654;';
    $('now-name').textContent = current ? current.name : 'Pick a sound';
    $('now-desc').textContent = current ? current.desc
      : 'Everything here is generated live, like the main app.';
    $('strike').hidden = !(current && current.id === 'bowls' && isPlaying);
  }

  $('playpause').addEventListener('click', () => { if (isPlaying) stop(); });
  $('strike').addEventListener('click', () => {
    const notes = [130.81, 196.00, 261.63, 155.56];
    Engine.strikeBowl(notes[Math.floor(Math.random() * notes.length)]);
  });

  $('vol').addEventListener('input', e => Engine.setVolume(e.target.value / 100));
  try {
    const v = JSON.parse(localStorage.getItem('lofi:vol'));
    if (v != null) { $('vol').value = v; Engine.setVolume(v / 100); }
  } catch (e) {}

  // no binaural or vinyl texture here — the lab's sounds stand alone
  Engine.setBinaural(false);
  Engine.setTexture(false);

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  window.__lab = {
    get playing() { return isPlaying; },
    get sound() { return current && current.id; },
    tiles: NOISE.length + PULSES.length + SOLFEGGIO.length + TEXTURES.length
  };
})();
