// Daylight Computer Club service worker.
// Pages (HTML) and apps.json: network-first, so the club you see is always
// the current one — the cache is only the offline fallback.
// Static assets (css, icons): cache-first with background refresh, so the
// club opens instantly but self-heals when styles change.
// APK files: never cached — always a fresh download.

const CACHE = 'dcc-v4';
const SHELL = [
  './',
  'index.html',
  'install.html',
  'invite.html',
  'friends.json',
  'recalled.json',
  'share.html',
  'guestbook.html',
  'wishboard.html',
  'newsletter.html',
  'why.html',
  'instincts.html',
  'style.css',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png'
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
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

  if (url.pathname.endsWith('.apk')) return; // straight to network

  const wantsFresh =
    req.mode === 'navigate' ||
    url.pathname.endsWith('.html') ||
    url.pathname.endsWith('/') ||
    url.pathname.endsWith('apps.json') ||
    url.pathname.endsWith('friends.json') ||
    url.pathname.endsWith('recalled.json');

  if (wantsFresh) {
    // Network-first: live site wins, cache only when offline.
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

  // Static assets: serve from cache, refresh it in the background.
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
