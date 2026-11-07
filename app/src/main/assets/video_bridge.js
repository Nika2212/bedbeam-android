// BedBeam video bridge. Injected at document start into every frame (including cross-site player
// iframes). Reports when a <video> appears or plays, and runs remote commands on the main video:
// "toggle", "seek:<seconds>", "state", "exitfs". Replies with {type:"state", t, d, paused}.
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

  N.onmessage = function (e) {
    var m = String(e.data), v = pick();
    if (m === 'exitfs') {
      try { (document.exitFullscreen || document.webkitExitFullscreen).call(document); } catch (x) {}
      return;
    }
    if (!v) { send({ type: 'none' }); return; }
    if (m === 'toggle') {
      if (v.paused) { var p = v.play(); if (p && p.catch) p.catch(function () {}); } else v.pause();
    } else if (m.indexOf('seek:') === 0) {
      var d = parseFloat(m.slice(5)) || 0, max = isFinite(v.duration) ? v.duration - 0.5 : 1e9;
      v.currentTime = Math.max(0, Math.min(max, (v.currentTime || 0) + d));
    }
    state(v);
  };

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
