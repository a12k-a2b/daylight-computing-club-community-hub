/* Shared first-hour helpers. Nothing leaves the tablet except an explicit mailto. */

const Starter = {
  esc(s) {
    return String(s).replace(/[&<>"]/g, c => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;'
    }[c]));
  },

  read(key, fallback) {
    try {
      const raw = localStorage.getItem(key);
      return raw == null ? fallback : JSON.parse(raw);
    } catch (e) {
      return fallback;
    }
  },

  write(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
  },

  unlock() {
    return this.read('dcc-starter-unlock', { name: '', email: '', serial: '', note: '' });
  },

  saveUnlock(part) {
    this.write('dcc-starter-unlock', Object.assign(this.unlock(), part, {
      updated: new Date().toISOString()
    }));
  },

  mailto({ to, subject, body }) {
    const href = 'mailto:' + encodeURIComponent(to) +
      '?subject=' + encodeURIComponent(subject) +
      '&body=' + encodeURIComponent(body);
    location.href = href;
  },

  wizard(steps, { storageKey, homeHref, homeLabel }) {
    const $ = id => document.getElementById(id);
    let idx = Math.min(
      parseInt(localStorage.getItem(storageKey) || '0', 10) || 0,
      Math.max(0, steps.length - 1)
    );

    function save() { localStorage.setItem(storageKey, String(idx)); }

    function render() {
      const step = typeof steps[idx] === 'function' ? steps[idx]() : steps[idx];
      $('progress').textContent = 'STEP ' + (idx + 1) + ' OF ' + steps.length;
      $('step-title').textContent = step.title;
      $('step-body').innerHTML = step.body;
      if (step.after) step.after();

      const nav = $('nav');
      nav.innerHTML = '';
      if (idx > 0) {
        const back = document.createElement('button');
        back.type = 'button';
        back.className = 'btn secondary back';
        back.textContent = '← Back';
        back.onclick = () => { idx--; save(); render(); };
        nav.appendChild(back);
      }
      if (step.next) {
        const next = document.createElement('button');
        next.type = 'button';
        next.className = 'btn';
        next.textContent = step.next;
        next.onclick = () => {
          if (step.beforeNext && step.beforeNext() === false) return;
          idx++;
          save();
          render();
        };
        nav.appendChild(next);
      } else if (idx === steps.length - 1) {
        const home = document.createElement('a');
        home.className = 'btn';
        home.href = homeHref || './';
        home.textContent = homeLabel || 'Back to first hour →';
        home.onclick = () => localStorage.removeItem(storageKey);
        nav.appendChild(home);
      }
      window.scrollTo(0, 0);
    }

    return { render, goto(n) { idx = n; save(); render(); } };
  }
};
