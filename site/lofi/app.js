/* Lofi — UI wiring. The board (three dayparts × three soundscapes), the
   player bar, the breath pacer readout, timers, and the PWA plumbing.
   Visual changes are discrete (no animation — reflective LCDs ghost). */

(() => {
  const $ = id => document.getElementById(id);
  const store = {
    get: (k, d) => { try { const v = localStorage.getItem('lofi:' + k); return v == null ? d : JSON.parse(v); } catch (e) { return d; } },
    set: (k, v) => { try { localStorage.setItem('lofi:' + k, JSON.stringify(v)); } catch (e) {} }
  };

  let currentScene = null;      // scene spec the user picked
  let isPlaying = false;
  let timerMins = 0;            // 0 = off
  let timerEndsAt = null;
  let timerTick = null;
  let breathTick = null;

  /* ---------- glyphs ---------- */

  const GLYPHS = {
    sunrise: '<svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true"><path d="M8 28 a12 12 0 0 1 24 0" fill="none" stroke="#000" stroke-width="3"/><line x1="20" y1="6" x2="20" y2="12" stroke="#000" stroke-width="3"/><line x1="6" y1="13" x2="10" y2="17" stroke="#000" stroke-width="3"/><line x1="34" y1="13" x2="30" y2="17" stroke="#000" stroke-width="3"/><line x1="4" y1="28" x2="36" y2="28" stroke="#000" stroke-width="3"/></svg>',
    sun: '<svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true"><circle cx="20" cy="20" r="9" fill="none" stroke="#000" stroke-width="3"/><g stroke="#000" stroke-width="3"><line x1="20" y1="2" x2="20" y2="8"/><line x1="20" y1="32" x2="20" y2="38"/><line x1="2" y1="20" x2="8" y2="20"/><line x1="32" y1="20" x2="38" y2="20"/><line x1="7" y1="7" x2="11" y2="11"/><line x1="29" y1="29" x2="33" y2="33"/><line x1="33" y1="7" x2="29" y2="11"/><line x1="11" y1="29" x2="7" y2="33"/></g></svg>',
    moon: '<svg viewBox="0 0 40 40" width="34" height="34" aria-hidden="true"><path d="M26 4 a16 16 0 1 0 10 26 a13 13 0 0 1 -10 -26 z" fill="none" stroke="#000" stroke-width="3"/></svg>'
  };

  /* ---------- build the board ---------- */

  function currentPartId() {
    const h = new Date().getHours();
    if (h >= 5 && h < 11) return 'morning';
    if (h >= 11 && h < 17) return 'day';
    return 'night';
  }

  function buildBoard() {
    const board = $('board');
    const nowPart = currentPartId();
    DAYPARTS.forEach(part => {
      const col = document.createElement('section');
      col.className = 'part';
      col.innerHTML =
        '<div class="part-head">' + GLYPHS[part.glyph] +
        '<h2>' + part.name + '</h2>' +
        (part.id === nowPart ? '<span class="now-chip">NOW</span>' : '') +
        '</div><p class="part-blurb">' + part.blurb + '</p>';
      SCENES.filter(s => s.part === part.id).forEach(scene => {
        const b = document.createElement('button');
        b.className = 'scene';
        b.id = 'scene-' + scene.id;
        b.innerHTML = '<span class="s-name">' + scene.name + '</span>' +
                      '<span class="s-desc">' + scene.desc + '</span>';
        b.addEventListener('click', () => tapScene(scene));
        col.appendChild(b);
      });
      board.appendChild(col);
    });
  }

  /* ---------- play control ---------- */

  function tapScene(scene) {
    if (currentScene && currentScene.id === scene.id && isPlaying) {
      pause();
      return;
    }
    currentScene = scene;
    start();
  }

  function start() {
    if (!currentScene) return;
    Engine.play(currentScene);
    // a side starts spinning when play begins; switching scenes mid-side
    // keeps it spinning (the side belongs to the session, not the record)
    if (!isPlaying) sideAnchor = Date.now();
    isPlaying = true;

    // the nap arms its own timer unless one is already running
    if (currentScene.napMinutes && !timerEndsAt) {
      setTimer(currentScene.napMinutes);
    }
    keepAlive(true);
    updateMediaSession();
    refresh();
  }

  function pause() {
    Engine.stop(0.8);
    isPlaying = false;
    keepAlive(false);
    refresh();
  }

  function refresh() {
    document.querySelectorAll('.scene').forEach(el => el.classList.remove('active'));
    if (currentScene && isPlaying) {
      const el = $('scene-' + currentScene.id);
      if (el) el.classList.add('active');
    }
    $('playpause').disabled = !currentScene;
    $('playpause').innerHTML = isPlaying ? '&#10073;&#10073;' : '&#9654;';
    $('now-name').textContent = currentScene ? currentScene.name : 'Pick a soundscape';
    $('now-desc').textContent = currentScene ? currentScene.desc
      : 'Morning wakes you, day settles you, night puts you down.';
    const showBreath = !!(currentScene && currentScene.breath && isPlaying);
    $('breath').hidden = !showBreath;
    if (!showBreath && breathTick) { clearInterval(breathTick); breathTick = null; }
  }

  /* ---------- breath pacer readout ---------- */

  Engine.onBreath = (label, secs) => {
    if (!currentScene || !currentScene.breath) return;
    $('breath-cue').textContent = label;
    let left = secs;
    const paint = () => { $('breath-count').textContent = '· '.repeat(left).trim(); };
    paint();
    if (breathTick) clearInterval(breathTick);
    breathTick = setInterval(() => {
      left = Math.max(0, left - 1);
      paint();
      if (left === 0) { clearInterval(breathTick); breathTick = null; }
    }, 1000);
  };

  /* ---------- sleep timer ----------
     Three ways to say "stop later": minutes, record sides (this is a lo-fi
     machine — a side is about 22 minutes), or "finish this side", which
     stops at the current side's edge, counted from when play began. */

  const SIDE_MINS = 22;
  let sideAnchor = null;        // when this play session started

  function setTimer(mins) {
    setTimerEnd(mins ? Date.now() + mins * 60000 : null);
    timerMins = mins;
  }

  function setTimerEnd(endsAt) {
    timerMins = 0;
    if (timerTick) { clearInterval(timerTick); timerTick = null; }
    timerEndsAt = endsAt;
    if (!endsAt) { paintTimer(); return; }
    timerTick = setInterval(() => {
      if (Date.now() >= timerEndsAt) {
        clearInterval(timerTick); timerTick = null;
        timerExpired();
      } else {
        paintTimer();
      }
    }, 5000);
    paintTimer();
  }

  // the edge of the side currently spinning — where "finish this side" stops
  function currentSideEnd() {
    const anchor = sideAnchor || Date.now();
    const side = SIDE_MINS * 60000;
    const spun = Date.now() - anchor;
    return anchor + Math.ceil((spun + 1) / side) * side;
  }

  function timerExpired() {
    timerEndsAt = null; timerMins = 0;
    if (currentScene && currentScene.napMinutes && isPlaying) {
      Engine.fadeOut(6);
      const chimeSecs = Engine.wakeChime();
      setTimeout(() => { isPlaying = false; keepAlive(false); refresh(); paintTimer(); },
                 chimeSecs * 1000);
    } else if (isPlaying) {
      Engine.fadeOut(45);
      setTimeout(() => { isPlaying = false; keepAlive(false); refresh(); }, 45000);
    }
    paintTimer();
  }

  function paintTimer() {
    const b = $('timer');
    if (timerEndsAt) {
      const left = Math.max(1, Math.round((timerEndsAt - Date.now()) / 60000));
      b.textContent = 'timer ' + left + 'm';
      b.classList.add('on');
    } else {
      b.textContent = 'timer off';
      b.classList.remove('on');
    }
  }

  /* ---------- timer panel ---------- */

  function paintPanel() {
    const open = !$('timer-panel').hidden;
    $('timer').classList.toggle('open', open);
    $('tp-side-now').disabled = !isPlaying;
  }

  function closePanel() { $('timer-panel').hidden = true; paintPanel(); }

  $('timer').addEventListener('click', () => {
    $('timer-panel').hidden = !$('timer-panel').hidden;
    paintPanel();
  });

  document.querySelectorAll('#timer-panel [data-mins]').forEach(b => {
    b.addEventListener('click', () => {
      setTimer(Number(b.dataset.mins));
      closePanel();
    });
  });

  $('tp-side-now').addEventListener('click', () => {
    if (!isPlaying) return;
    setTimerEnd(currentSideEnd());
    closePanel();
  });

  $('tp-off').addEventListener('click', () => { setTimer(0); closePanel(); });

  // tapping anywhere outside folds the panel away
  document.addEventListener('click', e => {
    if ($('timer-panel').hidden) return;
    if (!e.target.closest('#timer-panel') && !e.target.closest('#timer')) closePanel();
  });

  /* ---------- knobs ---------- */

  $('playpause').addEventListener('click', () => (isPlaying ? pause() : start()));

  $('vol').addEventListener('input', e => {
    const v = e.target.value / 100;
    Engine.setVolume(v);
    store.set('vol', e.target.value);
  });

  function wireToggle(id, storeKey, apply) {
    const b = $(id);
    let on = store.get(storeKey, true);
    const word = id === 'binaural' ? 'undertone' : 'texture';
    const paint = () => {
      b.classList.toggle('on', on);
      b.innerHTML = word + '&nbsp;' + (on ? 'on' : 'off');
    };
    apply(on); paint();
    b.addEventListener('click', () => {
      on = !on; store.set(storeKey, on); apply(on); paint();
    });
  }

  /* ---------- keep audio alive with the screen off ---------- */
  // A silent looped <audio> element marks the page as "playing media", so
  // Chrome holds its foreground media service and Android spares the app
  // when the screen sleeps or we lose the foreground. If the system pauses
  // the element out from under us (focus loss, interruption), re-arm it.

  let silentEl = null;
  function silentWav() {
    const rate = 8000, secs = 10, data = rate * secs;
    const buf = new ArrayBuffer(44 + data);
    const v = new DataView(buf);
    const w = (off, s) => { for (let i = 0; i < s.length; i++) v.setUint8(off + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, 36 + data, true); w(8, 'WAVEfmt ');
    v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true);
    v.setUint32(24, rate, true); v.setUint32(28, rate, true);
    v.setUint16(32, 1, true); v.setUint16(34, 8, true); w(36, 'data');
    v.setUint32(40, data, true);
    for (let i = 0; i < data; i++) v.setUint8(44 + i, 128);
    return URL.createObjectURL(new Blob([buf], { type: 'audio/wav' }));
  }

  function keepAlive(on) {
    if (on) {
      if (!silentEl) {
        silentEl = new Audio(silentWav());
        silentEl.loop = true;
        silentEl.volume = 0.001;
        // the system paused us (interruption, focus loss) while we should
        // be playing — take the needle back after a beat
        silentEl.addEventListener('pause', () => {
          if (isPlaying) setTimeout(() => {
            if (isPlaying && silentEl.paused) {
              silentEl.play().catch(() => {});
              Engine.resume();
            }
          }, 1000);
        });
      }
      silentEl.play().catch(() => {});
    } else if (silentEl) {
      silentEl.pause();
    }
    if ('mediaSession' in navigator) {
      navigator.mediaSession.playbackState = on ? 'playing' : 'paused';
    }
  }

  // waking the page back up is the moment suspended audio can resume
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible' && isPlaying) {
      Engine.resume();
      keepAlive(true);
    }
  });

  function updateMediaSession() {
    if (!('mediaSession' in navigator) || !currentScene) return;
    navigator.mediaSession.metadata = new MediaMetadata({
      title: currentScene.name,
      artist: 'Lofi — a quiet sound machine',
      album: currentScene.part
    });
    navigator.mediaSession.setActionHandler('play', () => start());
    navigator.mediaSession.setActionHandler('pause', () => pause());
    try { navigator.mediaSession.setActionHandler('stop', () => pause()); } catch (e) {}
  }

  /* ---------- boot ---------- */

  buildBoard();

  const savedVol = store.get('vol', 70);
  $('vol').value = savedVol;
  Engine.setVolume(savedVol / 100);

  wireToggle('binaural', 'binaural', on => Engine.setBinaural(on));
  wireToggle('texture', 'texture', on => Engine.setTexture(on));

  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  // expose a handle for the club's Playwright smoke test
  window.__lofi = {
    get playing() { return isPlaying; },
    get scene() { return currentScene && currentScene.id; },
    get timerEnd() { return timerEndsAt; },
    scenes: SCENES.length
  };
})();
