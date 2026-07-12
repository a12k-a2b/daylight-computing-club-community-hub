// Lofi service worker. The whole app is a handful of static files and the
// sound is generated, so once cached it works fully offline.
// Navigations: network-first (so updates land), everything else:
// cache-first with background refresh.

const CACHE = 'lofi-v4';
const SHELL = [
  './',
  'index.html',
  'lab.html',
  'lofi.css',
  'app.js',
  'lab.js',
  'engine.js',
  'scenes.js',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png'
];

self.addEventListener('install', e => {
  // cache:'reload' skips the browser's HTTP cache, so a new cache version
  // can never be seeded with stale copies of the shell
  e.waitUntil(
    caches.open(CACHE)
      .then(c => c.addAll(SHELL.map(u => new Request(u, { cache: 'reload' }))))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', e => {
  e.waitUntil(
    caches.keys()
      .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', e => {
  const req = e.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== location.origin) return;

  if (req.mode === 'navigate' || url.pathname.endsWith('.html') || url.pathname.endsWith('/')) {
    e.respondWith(
      fetch(req)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(req, copy));
          return res;
        })
        .catch(() => caches.match(req))
    );
    return;
  }

  e.respondWith(
    caches.match(req).then(hit => {
      const refresh = fetch(req)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(req, copy));
          return res;
        })
        .catch(() => hit);
      return hit || refresh;
    })
  );
});
