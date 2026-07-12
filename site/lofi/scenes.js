/* Lofi — the soundtracks.
   Every scene is a recipe, not a recording: layers of generated noise,
   slow drones, sparse plucked notes, and little scheduled events (birds,
   crackle, rain plinks). The engine (engine.js) reads these specs and
   builds the sound live on the tablet — nothing streams, nothing loops
   audibly, and it all works offline.

   Binaural undertones follow the classic brainwave-entrainment bands:
   morning sits in alpha/low-beta (gentle alertness), day in alpha/theta
   (unwinding), night in theta/delta (sleep). They need headphones —
   speakers can't keep the two ears apart. */

// Note frequencies (Hz), equal temperament, for readability below.
const N = {
  C2: 65.41, G2: 98.00, C3: 130.81, Eb3: 155.56, F3: 174.61, G3: 196.00,
  Ab3: 207.65, Bb3: 233.08, C4: 261.63, D4: 293.66, Eb4: 311.13,
  E4: 329.63, F4: 349.23, G4: 392.00, A4: 440.00, Bb4: 466.16,
  C5: 523.25, D5: 587.33, E5: 659.26, G5: 783.99, A5: 880.00,
  C6: 1046.50, D6: 1174.66, E6: 1318.51, G6: 1567.98
};

const DAYPARTS = [
  {
    id: 'morning',
    name: 'Morning',
    glyph: 'sunrise',
    blurb: 'Wake up gently, then get something down on paper.',
    hours: [5, 11]
  },
  {
    id: 'day',
    name: 'Day',
    glyph: 'sun',
    blurb: 'A pocket of calm in the middle of it all.',
    hours: [11, 17]
  },
  {
    id: 'night',
    name: 'Night',
    glyph: 'moon',
    blurb: 'Wind the day down toward sleep.',
    hours: [17, 5]
  }
];

const SCENES = [

  /* ---------------- morning ---------------- */

  {
    id: 'first-light',
    part: 'morning',
    name: 'First Light',
    desc: 'Bright sparse notes over a warming drone — for opening the curtains.',
    binaural: { carrier: 220, beat: 14 },       // low beta: gentle alertness
    layers: [
      { kind: 'drone', notes: [N.C3, N.G3], type: 'sine', gain: 0.045, attack: 10, breathe: 0.02 },
      { kind: 'noise', color: 'pink', gain: 0.012,
        filter: { type: 'lowpass', freq: 1200 } },
      { kind: 'plucks', notes: [N.C5, N.D5, N.E5, N.G5, N.A5, N.C6, N.E6, N.G6],
        interval: [2.5, 7], gain: 0.075, decay: 2.2, partial: 3 },
      { kind: 'plucks', notes: [N.C4, N.E4, N.G4],
        interval: [7, 16], gain: 0.05, decay: 3.5, partial: 2 }
    ]
  },
  {
    id: 'morning-pages',
    part: 'morning',
    name: 'Morning Pages',
    desc: 'A steady writing companion: warm drone, a soft bell now and then.',
    binaural: { carrier: 200, beat: 10 },       // alpha: relaxed focus
    takes: [
      { id: 'stream', name: 'Stream off Geyser Hill',
        desc: 'A small stream with geysers rumbling behind, Yellowstone. Public domain (NPS).',
        src: 'takes/stream-yellowstone.mp3' }
    ],
    layers: [
      { kind: 'drone', notes: [N.F3, N.C4, N.F3 * 2.005], type: 'triangle',
        gain: 0.035, attack: 12, breathe: 0.015 },
      { kind: 'noise', color: 'pink', gain: 0.018,
        filter: { type: 'lowpass', freq: 700 } },
      { kind: 'bells', notes: [N.F4, N.A4, N.C5, N.E5],
        interval: [14, 26], gain: 0.05, decay: 7 }
    ]
  },
  {
    id: 'open-window',
    part: 'morning',
    name: 'Open Window',
    desc: 'Morning air: a light breeze and songbirds somewhere outside.',
    binaural: { carrier: 210, beat: 12 },       // high alpha: awake, easy
    takes: [
      { id: 'dawnchorus', name: 'Dawn chorus, Yellowstone',
        desc: 'Real daybreak birds recorded in Yellowstone. Public domain (NPS).',
        src: 'takes/dawnchorus-yellowstone.mp3' }
    ],
    layers: [
      { kind: 'noise', color: 'white', gain: 0.02,
        filter: { type: 'bandpass', freq: 500, q: 0.6 },
        lfo: { target: 'freq', rate: 0.07, depth: 260 } },
      { kind: 'noise', color: 'pink', gain: 0.01,
        filter: { type: 'lowpass', freq: 400 } },
      { kind: 'birds', interval: [4, 13], gain: 0.05 },
      { kind: 'drone', notes: [N.C4, N.G4], type: 'sine', gain: 0.012,
        attack: 15, breathe: 0.02 }
    ]
  },

  /* ---------------- day ---------------- */

  {
    id: 'take-a-breath',
    part: 'day',
    name: 'Take a Breath',
    desc: 'Box breathing, 4-4-4-4 — the sound swells as you inhale. Follow it.',
    binaural: { carrier: 180, beat: 8 },        // low alpha: let go
    breath: { pattern: [['Breathe in', 4], ['Hold', 4], ['Breathe out', 4], ['Rest', 4]] },
    takes: [
      { id: 'wind', name: 'Wind in the pines',
        desc: 'Wind through lodgepole pines, recorded binaurally in Yellowstone — best on headphones. The pacer still paints, but the wind breathes at its own pace. Public domain (NPS).',
        src: 'takes/wind-yellowstone.mp3' }
    ],
    layers: [
      { kind: 'noise', color: 'pink', gain: 0.05, breathLayer: true,
        filter: { type: 'lowpass', freq: 900 } },
      { kind: 'drone', notes: [N.C3, N.Eb3 * 2, N.G3], type: 'sine',
        gain: 0.03, attack: 8, breathe: 0.01 }
    ]
  },
  {
    id: 'rain-on-the-roof',
    part: 'day',
    name: 'Rain on the Roof',
    desc: 'Steady rain, close plinks, a far-off rumble once in a while.',
    binaural: { carrier: 190, beat: 10 },       // alpha: settle down
    takes: [
      { id: 'amazon', name: 'Rain on a metal roof',
        desc: 'Real rain drumming a metal-sheet roof in the Amazon rainforest. Recorded by Félix Blume (CC0).',
        src: 'takes/rain-amazon.mp3' },
      { id: 'thunder', name: 'A storm rolls through',
        desc: 'A Yellowstone thunderstorm — rain, long rolling thunder, birds after. Public domain (NPS).',
        src: 'takes/thunder-yellowstone.mp3' }
    ],
    layers: [
      { kind: 'noise', color: 'white', gain: 0.028,
        filter: { type: 'bandpass', freq: 2600, q: 0.35 },
        lfo: { target: 'gain', rate: 0.09, depth: 0.3 } },
      { kind: 'noise', color: 'pink', gain: 0.02,
        filter: { type: 'lowpass', freq: 800 } },
      { kind: 'plinks', interval: [0.4, 2.2], gain: 0.035 },
      { kind: 'rumble', interval: [50, 110], gain: 0.06 }
    ]
  },
  {
    id: 'nap',
    part: 'day',
    name: 'Twenty-Minute Nap',
    desc: 'Deep steady hush, then a slow chime wakes you. Timer sets itself.',
    binaural: { carrier: 160, beat: 5 },        // theta: doze
    napMinutes: 20,
    layers: [
      { kind: 'noise', color: 'brown', gain: 0.09,
        filter: { type: 'lowpass', freq: 500 },
        lfo: { target: 'gain', rate: 0.05, depth: 0.15 } },
      { kind: 'drone', notes: [N.C2, N.G2], type: 'sine', gain: 0.03,
        attack: 20, breathe: 0.015 }
    ]
  },

  /* ---------------- night ---------------- */

  {
    id: 'fireside',
    part: 'night',
    name: 'Fireside',
    desc: 'A low fire: crackle, warmth, the occasional deep slow note.',
    binaural: { carrier: 150, beat: 6 },        // theta: drowsy
    takes: [
      { id: 'maple-fire', name: 'A real forest fire, close',
        desc: 'The 2016 Maple Fire burning through lodgepole pine, Yellowstone — deep crackle, no music. Public domain (NPS).',
        src: 'takes/fire-yellowstone.mp3' }
    ],
    layers: [
      { kind: 'crackle', density: 22, gain: 0.05,
        filter: { type: 'bandpass', freq: 900, q: 0.5 } },
      { kind: 'noise', color: 'brown', gain: 0.05,
        filter: { type: 'lowpass', freq: 220 },
        lfo: { target: 'gain', rate: 0.13, depth: 0.25 } },
      { kind: 'drone', notes: [N.C3, N.Eb3], type: 'sine', gain: 0.028,
        attack: 14, breathe: 0.02 },
      { kind: 'plucks', notes: [N.C3, N.Eb3, N.G3, N.Bb3],
        interval: [12, 28], gain: 0.045, decay: 5, partial: 2 }
    ]
  },
  {
    id: 'night-tide',
    part: 'night',
    name: 'Night Tide',
    desc: 'Long, slow waves on a dark shore. Each one a little different.',
    binaural: { carrier: 140, beat: 5 },        // theta: drifting
    takes: [
      { id: 'chile', name: 'Waves on the Chilean shore',
        desc: 'Real ocean waves breaking on the shore of Chile. Recorded by Félix Blume (CC0).',
        src: 'takes/waves-chile.mp3' }
    ],
    layers: [
      { kind: 'waves', gain: 0.09 },
      { kind: 'noise', color: 'brown', gain: 0.035,
        filter: { type: 'lowpass', freq: 150 },
        lfo: { target: 'gain', rate: 0.04, depth: 0.3 } },
      { kind: 'drone', notes: [N.C2 * 2], type: 'sine', gain: 0.015,
        attack: 20, breathe: 0.02 }
    ]
  },
  {
    id: 'sleep',
    part: 'night',
    name: 'Sleep',
    desc: 'Almost nothing at all: deep hush and two tones beating slowly. Set a timer and let it fade.',
    binaural: { carrier: 120, beat: 3 },        // delta: sleep
    takes: [
      { id: 'crickets', name: 'Night crickets',
        desc: 'Crickets on a quiet rural night, nothing else. Recorded by OwlStorm (CC0).',
        src: 'takes/crickets-night.mp3' }
    ],
    layers: [
      { kind: 'noise', color: 'brown', gain: 0.08,
        filter: { type: 'lowpass', freq: 300 },
        lfo: { target: 'gain', rate: 0.03, depth: 0.12 } },
      { kind: 'drone', notes: [N.C2, N.C2 * 1.007], type: 'sine',
        gain: 0.035, attack: 25, breathe: 0.01 },
      { kind: 'drone', notes: [N.G2], type: 'sine', gain: 0.012,
        attack: 30, breathe: 0.015 }
    ]
  }
];
