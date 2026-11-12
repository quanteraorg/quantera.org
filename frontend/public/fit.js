/* Fit the screen: on large displays the whole interface scales up so it fills the screen at a comfortable size,
   instead of sitting small in the middle or one corner. A 1920×1080 screen shows the 1600×900 layout at 1.2×,
   2560×1440 at 1.6×. Phones, small tablets and laptops are left alone (zoom 1).

   CSS zoom on <html> changes the units browsers report, and browsers differ: Chrome and Firefox report element boxes
   in screen pixels, Safari reports them unzoomed. So nothing is assumed. Probe elements measure, in this browser,
   how boxes, the window size and the scroll position relate to the page's own pixels, and the first pointer and
   touch events are checked against the element they land on. Every value the page reads is then converted to page
   pixels, so menus, chart crosshairs and drawings line up. If the checks ever disagree, the scaling switches itself
   off and the page carries on at normal size. Add ?fit=0 to the address to turn it off. Loaded before app.js. */
(function () {
  var W = window, D = document, R = D.documentElement;
  if (!('zoom' in R.style) || /[?&]fit=0/.test(location.search)) return;
  var P = Window.prototype, own = function (k) { return Object.getOwnPropertyDescriptor(P, k) || Object.getOwnPropertyDescriptor(W, k); };
  var nat = { iw: own('innerWidth'), ih: own('innerHeight'), dpr: own('devicePixelRatio'), sx: own('scrollX'), sy: own('scrollY') };
  if (!nat.iw || !nat.ih || !nat.sy || !nat.dpr) return;
  var get = function (d) { return d.get.call(W); };
  var gbcr = Element.prototype.getBoundingClientRect, gcr = Element.prototype.getClientRects;

  var z = 1,      // the zoom applied
      rz = 1,     // element boxes come back multiplied by this (z in Chrome/Firefox, 1 in Safari)
      ez = 1,     // pointer coordinates come back multiplied by this
      sz = 0,     // window scroll comes back multiplied by this (0 = not measured yet)
      checked = false, view, top;

  function probes() {
    if (view) return;
    view = D.createElement('div'); top = D.createElement('div');
    view.setAttribute('aria-hidden', 'true'); top.setAttribute('aria-hidden', 'true');
    view.style.cssText = 'position:fixed;left:0;top:0;right:0;bottom:0;visibility:hidden;pointer-events:none;z-index:-1;contain:strict';
    top.style.cssText = 'position:absolute;left:0;top:0;width:10px;height:10px;visibility:hidden;pointer-events:none';
    R.appendChild(view); R.appendChild(top);
  }
  function want() {
    var w = get(nat.iw), h = get(nat.ih), f = Math.min(w / 1600, h / 900);
    if (f < 1.1) return 1;  // phones, small tablets, laptops: unchanged
    return Math.min(2, Math.round(f * 20) / 20);
  }
  function off(why) { // something didn't add up: show the page at normal size rather than risk misplaced controls
    if (z === 1) return;
    try { console.warn('Quantera fit: scaling off (' + why + ')'); } catch (e) {}
    W.__qfitOff = why; set(1); W.dispatchEvent(new Event('resize'));
  }

  var dv = function (r, f) { return f === 1 ? r : new DOMRect(r.x / f, r.y / f, r.width / f, r.height / f); };
  Element.prototype.getBoundingClientRect = function () { return dv(gbcr.call(this), rz); };
  Element.prototype.getClientRects = function () {
    var l = gcr.call(this); if (rz === 1) return l;
    var a = []; for (var i = 0; i < l.length; i++) a.push(dv(l[i], rz));
    a.item = function (i) { return a[i] || null; }; return a;
  };
  if (W.Range) { var rb = Range.prototype.getBoundingClientRect; Range.prototype.getBoundingClientRect = function () { return dv(rb.call(this), rz); }; }

  // the browser's own getters, kept before they are wrapped, for the pointer check
  var natM = Object.getOwnPropertyDescriptor(MouseEvent.prototype, 'clientX'), natMY = Object.getOwnPropertyDescriptor(MouseEvent.prototype, 'clientY');
  var natT = W.Touch && Object.getOwnPropertyDescriptor(Touch.prototype, 'clientX'), natTY = W.Touch && Object.getOwnPropertyDescriptor(Touch.prototype, 'clientY');
  var wrap = function (proto, keys, factor) {
    keys.forEach(function (k) {
      var d = Object.getOwnPropertyDescriptor(proto, k);
      if (d && d.get) Object.defineProperty(proto, k, { configurable: true, enumerable: true, get: function () { var v = d.get.call(this), f = factor(); return f === 1 ? v : v / f; } });
    });
  };
  wrap(MouseEvent.prototype, ['clientX', 'clientY', 'pageX', 'pageY', 'x', 'y', 'movementX', 'movementY'], function () { return ez; });
  wrap(MouseEvent.prototype, ['offsetX', 'offsetY'], function () { return rz; }); // offsets follow the box units
  if (W.Touch) wrap(Touch.prototype, ['clientX', 'clientY', 'pageX', 'pageY'], function () { return ez; });
  ['elementFromPoint', 'elementsFromPoint', 'caretRangeFromPoint', 'caretPositionFromPoint'].forEach(function (k) {
    var f = D[k]; if (f) D[k] = function (x, y) { return f.call(D, x * ez, y * ez); };
  });

  // Window size and scroll, measured from the probes in page pixels.
  var def = function (k, fn) { Object.defineProperty(W, k, { configurable: true, get: fn }); };
  def('innerWidth', function () { return z === 1 || !view ? get(nat.iw) : view.offsetWidth; });
  def('innerHeight', function () { return z === 1 || !view ? get(nat.ih) : view.offsetHeight; });
  def('devicePixelRatio', function () { return get(nat.dpr) * z; }); // charts draw at full sharpness
  var sy = function () { if (z === 1 || !top) return get(nat.sy); return Math.max(0, -gbcr.call(top).top / rz); };
  var sx = function () { if (z === 1 || !top) return get(nat.sx); return Math.max(0, -gbcr.call(top).left / rz); };
  def('scrollY', sy); def('pageYOffset', sy); def('scrollX', sx); def('pageXOffset', sx);
  function scale() { // native scroll units per page pixel
    var n = get(nat.sy), l = sy();
    if (n > 40 && l > 20) sz = n / l;
    return sz || z;
  }
  ['scrollTo', 'scroll', 'scrollBy'].forEach(function (k) {
    var f = W[k]; if (!f) return;
    W[k] = function (a, b) {
      if (z === 1) return f.apply(W, arguments);
      var s = scale();
      if (a && typeof a === 'object') { var o = {}; for (var p in a) o[p] = a[p]; if (o.top != null) o.top *= s; if (o.left != null) o.left *= s; return f.call(W, o); }
      return f.call(W, (a || 0) * s, (b || 0) * s);
    };
  });

  // Check pointer units on the first events that land on a small element: the point must fall inside it.
  function check(e) {
    if (checked || z === 1) return;
    var p = e.touches ? e.touches[0] : e, t = e.target;
    if (!p || !t || !t.getBoundingClientRect) return;
    var r = gbcr.call(t); r = dv(r, rz);
    if (r.width < 4 || r.height < 4 || r.width > 420 || r.height > 260) return;
    var gx = e.touches ? natT : natM, gy = e.touches ? natTY : natMY;
    if (!gx || !gy || !gx.get || !gy.get) return;
    var x = gx.get.call(p), y = gy.get.call(p);
    var inside = function (f) { var a = x / f, b = y / f; return a >= r.left - 3 && a <= r.right + 3 && b >= r.top - 3 && b <= r.bottom + 3; };
    var A = inside(z), B = inside(1);
    W.__qfitCheck = (A ? 'z' : '') + (B ? '1' : '');
    if (A && !B) { ez = z; checked = true; }
    else if (B && !A) { ez = 1; checked = true; }
    else if (!A && !B) off('pointer check');
  }
  ['pointerdown', 'mousemove', 'touchstart'].forEach(function (k) { W.addEventListener(k, check, { capture: true, passive: true }); });

  function set(n) {
    var y = sy();
    z = n; R.style.zoom = z === 1 ? '' : String(z);
    if (z === 1) { rz = 1; ez = 1; sz = 0; }
    else {
      probes();
      var b = gbcr.call(view), w = view.offsetWidth; // boxes: does this browser include the zoom?
      rz = w > 0 && Math.abs(b.width / w - z) < 0.05 ? z : 1;
      if (w <= 0 || (rz === 1 && Math.abs(b.width - w) > 2)) { z = 1; R.style.zoom = ''; rz = 1; W.__qfitOff = 'box check'; }
      ez = z; checked = false; sz = 0; // pointer units are confirmed by check()
    }
    R.dataset.fit = String(z); W.__qzoom = z;
    if (y) W.scrollTo(0, y);
  }
  function apply() { var n = W.__qfitOff ? 1 : want(); if (n !== z) { set(n); W.dispatchEvent(new Event('resize')); } }
  apply();

  // One report per browser session from large or touch screens: screen measurements only (no personal data),
  // so problems on devices we can't test here, like an iPad in Safari, can be seen and fixed.
  setTimeout(function () {
    try {
      var big = get(nat.iw) >= 1500 || (navigator.maxTouchPoints || 0) > 1;
      if (!big || sessionStorage.getItem('q_fitrep')) return;
      sessionStorage.setItem('q_fitrep', '1');
      var vv = W.visualViewport, info = {
        nativeWidth: get(nat.iw), nativeHeight: get(nat.ih), dpr: get(nat.dpr), screen: screen.width + 'x' + screen.height,
        zoom: z, boxUnits: rz, pointerUnits: ez, pointerChecked: W.__qfitCheck || 'none', off: W.__qfitOff || null,
        layoutWidth: view ? view.offsetWidth : null, boxWidth: view ? Math.round(gbcr.call(view).width) : null,
        visualScale: vv ? vv.scale : null, visualWidth: vv ? Math.round(vv.width) : null, touchPoints: navigator.maxTouchPoints || 0,
        coarse: !!(W.matchMedia && W.matchMedia('(pointer: coarse)').matches), ua: navigator.userAgent
      };
      if (W.Sentry && Sentry.captureMessage) Sentry.captureMessage('fit-screen report', { level: 'info', tags: { fit: String(z), fitOff: String(!!W.__qfitOff) }, extra: info });
    } catch (e) {}
  }, 9000);
  var t = 0; W.addEventListener('resize', function () { clearTimeout(t); t = setTimeout(apply, 150); });
})();
