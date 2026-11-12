/* Fit the screen: on large displays the whole interface scales up so it fills the screen at a comfortable size,
   instead of sitting small in one corner. A 1920×1080 monitor shows the 1600×900 layout at 1.2×; 2560×1440 at 1.6×.
   Phones, tablets, laptops and touch screens are left alone (zoom 1).

   CSS zoom on <html> makes the browser report mouse positions, element boxes, window size and window scroll in
   real screen pixels, while layout and CSS positions stay in page pixels. The shims below translate those values
   back into page pixels, so existing code that positions menus, chart crosshairs and drawings keeps lining up.
   Loaded before app.js so everything sees the same coordinates from the start. */
(function () {
  var W = window, D = document, R = D.documentElement, z = 1;
  var real = {
    iw: Object.getOwnPropertyDescriptor(Window.prototype, 'innerWidth') || Object.getOwnPropertyDescriptor(W, 'innerWidth'),
    ih: Object.getOwnPropertyDescriptor(Window.prototype, 'innerHeight') || Object.getOwnPropertyDescriptor(W, 'innerHeight'),
    dpr: Object.getOwnPropertyDescriptor(Window.prototype, 'devicePixelRatio') || Object.getOwnPropertyDescriptor(W, 'devicePixelRatio'),
    sx: Object.getOwnPropertyDescriptor(Window.prototype, 'scrollX') || Object.getOwnPropertyDescriptor(W, 'scrollX'),
    sy: Object.getOwnPropertyDescriptor(Window.prototype, 'scrollY') || Object.getOwnPropertyDescriptor(W, 'scrollY')
  };
  if (!real.iw || !real.ih || !('zoom' in R.style)) return;
  var get = function (d) { return d.get.call(W); };

  function want() {
    var w = get(real.iw), h = get(real.ih);
    if (W.matchMedia && W.matchMedia('(pointer: coarse)').matches) return 1;
    if (/[?&]fit=0/.test(location.search)) return 1;
    var f = Math.min(w / 1600, h / 900);
    if (f < 1.1) return 1; // laptops and small monitors: unchanged
    return Math.min(2, Math.round(f * 20) / 20);
  }

  function rect(r) { return z === 1 ? r : new DOMRect(r.x / z, r.y / z, r.width / z, r.height / z); }
  var gbcr = Element.prototype.getBoundingClientRect, gcr = Element.prototype.getClientRects;
  Element.prototype.getBoundingClientRect = function () { return rect(gbcr.call(this)); };
  Element.prototype.getClientRects = function () {
    var l = gcr.call(this); if (z === 1) return l;
    var a = []; for (var i = 0; i < l.length; i++) a.push(rect(l[i]));
    a.item = function (i) { return a[i] || null; }; return a;
  };
  if (W.Range) {
    var rb = Range.prototype.getBoundingClientRect;
    Range.prototype.getBoundingClientRect = function () { return rect(rb.call(this)); };
  }
  ['clientX', 'clientY', 'pageX', 'pageY', 'x', 'y', 'offsetX', 'offsetY', 'movementX', 'movementY'].forEach(function (k) {
    var d = Object.getOwnPropertyDescriptor(MouseEvent.prototype, k);
    if (d && d.get) Object.defineProperty(MouseEvent.prototype, k, { configurable: true, enumerable: true, get: function () { var v = d.get.call(this); return z === 1 ? v : v / z; } });
  });
  ['elementFromPoint', 'elementsFromPoint'].forEach(function (k) {
    var f = D[k]; if (f) D[k] = function (x, y) { return f.call(D, x * z, y * z); };
  });
  var def = function (k, d, fn) { if (d) Object.defineProperty(W, k, { configurable: true, get: fn }); };
  def('innerWidth', real.iw, function () { return get(real.iw) / z; });
  def('innerHeight', real.ih, function () { return get(real.ih) / z; });
  def('devicePixelRatio', real.dpr, function () { return get(real.dpr) * z; }); // charts draw at full sharpness
  def('scrollX', real.sx, function () { return get(real.sx) / z; });
  def('scrollY', real.sy, function () { return get(real.sy) / z; });
  def('pageXOffset', real.sx, function () { return get(real.sx) / z; });
  def('pageYOffset', real.sy, function () { return get(real.sy) / z; });
  ['scrollTo', 'scroll', 'scrollBy'].forEach(function (k) {
    var f = W[k]; if (!f) return;
    W[k] = function (a, b) {
      if (z === 1) return f.apply(W, arguments);
      if (a && typeof a === 'object') { var o = {}; for (var p in a) o[p] = a[p]; if (o.top != null) o.top *= z; if (o.left != null) o.left *= z; return f.call(W, o); }
      return f.call(W, (a || 0) * z, (b || 0) * z);
    };
  });

  var last = 0;
  function apply() {
    var n = want(); if (n === z) return;
    var y = get(real.sy) / z; // keep the reader at the same place
    z = n; R.style.zoom = z === 1 ? '' : String(z); R.dataset.fit = String(z);
    W.__qzoom = z;
    if (y) W.scrollTo(0, y);
    W.dispatchEvent(new Event('resize')); // let charts and layouts re-measure at the new size
  }
  apply();
  W.addEventListener('resize', function () { clearTimeout(last); last = setTimeout(apply, 120); });
})();
