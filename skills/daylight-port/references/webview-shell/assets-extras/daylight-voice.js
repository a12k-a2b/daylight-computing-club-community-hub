/* daylight-voice.js — one speech-to-text API for both containers.
 *
 * In Chrome (PWA) it uses the Web Speech API; inside the club's WebView
 * shell it uses the DaylightVoice bridge (the system speech dialog, which
 * owns the mic permission). Drop this file next to index.html, include
 * <script src="daylight-voice.js"></script>, then:
 *
 *   if (daylightVoice.available()) {
 *     const text = await daylightVoice.listen({ lang: 'en-US' });
 *   }
 *
 * Rejections: 'unavailable' (no engine — hide the mic button and rely on
 * the keyboard's dictation key), 'cancelled' (user backed out — not an
 * error, just do nothing), 'busy' (a listen is already in flight).
 */
(function () {
  'use strict';
  var pending = null;

  // Callback target for the shell's Java bridge.
  window.__daylightVoiceResult = function (transcript, error) {
    if (!pending) return;
    var p = pending; pending = null;
    if (transcript != null) p.resolve(transcript);
    else p.reject(new Error(error || 'cancelled'));
  };

  var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  var shell = window.DaylightVoice; // injected by the WebView shell

  window.daylightVoice = {
    available: function () {
      if (shell) { try { return shell.available(); } catch (e) { return false; } }
      return !!SR;
    },

    listen: function (opts) {
      opts = opts || {};
      if (pending) return Promise.reject(new Error('busy'));
      if (shell) {
        return new Promise(function (resolve, reject) {
          pending = { resolve: resolve, reject: reject };
          shell.listen(opts.lang || '');
        });
      }
      if (!SR) return Promise.reject(new Error('unavailable'));
      return new Promise(function (resolve, reject) {
        var rec = new SR();
        rec.lang = opts.lang || navigator.language || 'en-US';
        rec.interimResults = false;
        rec.maxAlternatives = 1;
        var done = false;
        rec.onresult = function (e) {
          done = true;
          resolve(e.results[0][0].transcript);
        };
        rec.onerror = function (e) {
          if (done) return; done = true;
          reject(new Error(e.error === 'aborted' || e.error === 'no-speech'
            ? 'cancelled' : (e.error || 'unavailable')));
        };
        rec.onend = function () {
          if (!done) { done = true; reject(new Error('cancelled')); }
        };
        rec.start();
      });
    }
  };
})();
