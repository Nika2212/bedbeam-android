// BedBeam page bridge. Injected at document start into every frame (including cross-site player
// iframes).
// Video: reports when a <video> appears or plays, and runs remote commands on the main video:
//   "toggle", "seek:<seconds>", "state", "exitfs". Replies with {type:"state", t, d, paused}.
// Link mode (top frame): "link:start:<fx>,<fy>", "link:up|down|left|right", "link:target", "link:clear".
//   Moves a focus ring between links/buttons; "link:target" replies {type:"target", x, y, vw} (CSS px)
//   so the app can tap the element with a real touch.
(function () {
  if (window.__bedbeamVideo) return;
  window.__bedbeamVideo = true;
  var N = window.bedbeamNative;
  if (!N) return;

  function send(o) { try { N.postMessage(JSON.stringify(o)); } catch (e) {} }
  // Announce the top frame, so commands have a default target before any video has loaded.
  if (window === window.top) send({ type: 'hello' });

  function pick() {
    var fe = document.fullscreenElement || document.webkitFullscreenElement;
    var list = document.getElementsByTagName('video');
    var best = null, score = -1;
    for (var i = 0; i < list.length; i++) {
      var v = list[i], r = v.getBoundingClientRect(), s = r.width * r.height;
      if (fe && (fe === v || fe.contains(v))) s += 1e12;
      if (!v.paused) s += 1e10;
      if (v.readyState > 0) s += 1e8;
      if (s > score) { score = s; best = v; }
    }
    return best;
  }

  function state(v) {
    send({ type: 'state', t: v.currentTime || 0, d: isFinite(v.duration) ? v.duration : 0, paused: v.paused });
  }

  // ---- link mode ------------------------------------------------------------------------------
  var cur = null, startX = null, startY = null;
  var SEL = 'a[href],button,input:not([type=hidden]),select,textarea,summary,[role=button],[role=link],' +
    '[role=tab],[role=menuitem],[role=option],[onclick],[tabindex]:not([tabindex="-1"])';

  function ensureStyle() {
    if (document.getElementById('__bb_link_style')) return;
    var s = document.createElement('style');
    s.id = '__bb_link_style';
    s.textContent = '.__bb_focus{outline:4px solid #5AA9FF!important;outline-offset:3px!important;' +
      'box-shadow:0 0 0 7px rgba(90,169,255,.25)!important;border-radius:6px}';
    (document.head || document.documentElement).appendChild(s);
  }

  function shown(el) {
    var r = el.getBoundingClientRect();
    if (r.width < 4 || r.height < 4) return null;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none' || +cs.opacity === 0) return null;
    return r;
  }

  // Focusable things within one screen above/below the view; nested ones collapse to the outermost.
  function candidates() {
    var out = [], list = document.querySelectorAll(SEL), vh = innerHeight, vw = innerWidth;
    for (var i = 0; i < list.length; i++) {
      var el = list[i];
      if (el.parentElement && el.parentElement.closest(SEL)) continue;
      var r = shown(el);
      if (!r || r.bottom < -vh || r.top > 2 * vh || r.right < 0 || r.left > vw) continue;
      out.push({ el: el, r: r });
    }
    return out;
  }

  function setCur(el) {
    ensureStyle();
    if (cur) cur.classList.remove('__bb_focus');
    cur = el;
    if (!el) return;
    el.classList.add('__bb_focus');
    el.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }

  function move(dir) {
    var list = candidates(), best = null, bs = Infinity;
    if (!cur || !document.contains(cur) || !shown(cur)) {
      // First press: the element nearest the pointer's last position (or the screen centre).
      var sx = startX != null ? startX * innerWidth : innerWidth / 2;
      var sy = startY != null ? startY * innerHeight : innerHeight / 2;
      list.forEach(function (c) {
        if (c.r.bottom < 0 || c.r.top > innerHeight) return;
        var d = Math.hypot(c.r.left + c.r.width / 2 - sx, c.r.top + c.r.height / 2 - sy);
        if (d < bs) { bs = d; best = c; }
      });
    } else {
      var o = cur.getBoundingClientRect(), ox = o.left + o.width / 2, oy = o.top + o.height / 2;
      list.forEach(function (c) {
        if (c.el === cur) return;
        var r = c.r, px = r.left + r.width / 2, py = r.top + r.height / 2, main, cross, overlap;
        if (dir === 'down' || dir === 'up') {
          main = dir === 'down' ? r.top - o.top : o.bottom - r.bottom;
          if (main <= 2 || (dir === 'down' ? py <= oy : py >= oy)) return;
          overlap = Math.min(r.right, o.right) - Math.max(r.left, o.left);
          cross = overlap > 0 ? 0 : Math.abs(px - ox);
        } else {
          main = dir === 'right' ? r.left - o.left : o.right - r.right;
          if (main <= 2 || (dir === 'right' ? px <= ox : px >= ox)) return;
          overlap = Math.min(r.bottom, o.bottom) - Math.max(r.top, o.top);
          cross = overlap > 0 ? 0 : Math.abs(py - oy);
        }
        var s = main + 2.5 * cross;
        if (s < bs) { bs = s; best = c; }
      });
    }
    if (best) { setCur(best.el); return; }
    // Nothing further that way: scroll instead.
    var dx = dir === 'left' ? -0.6 * innerWidth : dir === 'right' ? 0.6 * innerWidth : 0;
    var dy = dir === 'up' ? -0.6 * innerHeight : dir === 'down' ? 0.6 * innerHeight : 0;
    scrollBy(dx, dy);
  }

  function link(cmd) {
    if (cmd.indexOf('start:') === 0) {
      var p = cmd.slice(6).split(',');
      startX = parseFloat(p[0]); startY = parseFloat(p[1]);
      setCur(null);
      move('');
    } else if (cmd === 'clear') {
      setCur(null);
    } else if (cmd === 'target') {
      if (!cur || !document.contains(cur)) { send({ type: 'notarget' }); return; }
      var r = cur.getBoundingClientRect();
      send({ type: 'target', x: r.left + r.width / 2, y: r.top + r.height / 2, vw: innerWidth });
    } else {
      move(cmd);
    }
  }

  N.onmessage = function (e) {
    var m = String(e.data);
    if (m.indexOf('link:') === 0) { link(m.slice(5)); return; }
    var v = pick();
    if (m === 'exitfs') {
      try { (document.exitFullscreen || document.webkitExitFullscreen).call(document); } catch (x) {}
      return;
    }
    if (!v) { send({ type: 'none' }); return; }
    if (m === 'fs') {
      // Auto full screen: only works while the click that started the video still counts as a gesture.
      if (!(document.fullscreenElement || document.webkitFullscreenElement)) {
        try { var r = (v.requestFullscreen || v.webkitRequestFullscreen).call(v); if (r && r.catch) r.catch(function () {}); } catch (x) {}
      }
      return;
    }
    if (m === 'toggle') {
      if (v.paused) { var p = v.play(); if (p && p.catch) p.catch(function () {}); } else v.pause();
    } else if (m.indexOf('seek:') === 0) {
      var d = parseFloat(m.slice(5)) || 0, max = isFinite(v.duration) ? v.duration - 0.5 : 1e9;
      v.currentTime = Math.max(0, Math.min(max, (v.currentTime || 0) + d));
    }
    state(v);
  };

  // ---- text fields: make sure the on-screen keyboard opens ----------------------------------
  function editable(el) {
    if (!el || !el.tagName) return false;
    if (el.isContentEditable) return true;
    var t = el.tagName;
    if (t === 'TEXTAREA') return !el.readOnly && !el.disabled;
    if (t !== 'INPUT') return false;
    var type = (el.type || 'text').toLowerCase();
    return !el.readOnly && !el.disabled &&
      ['text', 'search', 'email', 'url', 'tel', 'password', 'number'].indexOf(type) >= 0;
  }
  document.addEventListener('focusin', function (e) { if (editable(e.target)) send({ type: 'edit' }); }, true);
  // Clicking a field that already has focus fires no focusin, but the keyboard may be closed.
  document.addEventListener('click', function (e) { if (editable(e.target)) send({ type: 'edit' }); }, true);

  document.addEventListener('loadedmetadata', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') send({ type: 'video' });
  }, true);
  document.addEventListener('play', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') send({ type: 'playing' });
  }, true);
  document.addEventListener('pause', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') send({ type: 'paused' });
  }, true);
  document.addEventListener('ended', function (e) {
    if (e.target && e.target.tagName === 'VIDEO') send({ type: 'paused' });
  }, true);
})();
