// Motion: price flashes, number count-ups, chart draw-ins, scroll reveals, a cursor spotlight on cards
// and a stronger page transition. Everything is off when the visitor asks for reduced motion.
// Rule for numbers: an animation never leaves a wrong value on screen. Each count-up ends by writing the
// exact original text, and stops at once if anything else changes the element.

const CSS = `
#m.qpg>*{animation:qpage2 .46s cubic-bezier(.22,1,.36,1) both!important}
@keyframes qpage2{from{opacity:0;transform:translateY(16px) scale(.995);filter:blur(3px)}to{opacity:1;transform:none;filter:none}}
.qflash-up{animation:qfu 1.1s ease-out}.qflash-dn{animation:qfd 1.1s ease-out}
@keyframes qfu{0%{background:rgba(var(--upc),.38);border-radius:4px}100%{background:transparent}}
@keyframes qfd{0%{background:rgba(var(--dnc),.38);border-radius:4px}100%{background:transparent}}
.qrv{opacity:0;transform:translateY(18px)}
.qrv.qin{opacity:1;transform:none;transition:opacity .6s cubic-bezier(.22,1,.36,1),transform .6s cubic-bezier(.22,1,.36,1);transition-delay:var(--qd,0s)}
.qspot{position:relative;isolation:isolate}
.qspot::after{content:"";position:absolute;inset:0;border-radius:inherit;pointer-events:none;opacity:0;transition:opacity .3s;z-index:-1;
 background:radial-gradient(260px circle at var(--mx,50%) var(--my,50%),rgba(var(--acc),.13),transparent 70%)}
.qspot:hover::after{opacity:1}
@media(prefers-reduced-motion:reduce){#m.qpg>*{animation:none!important}.qflash-up,.qflash-dn{animation:none}.qrv{opacity:1;transform:none}.qspot::after{display:none}}`;

const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;
const NUM = /^([+\-−]?)(\$?)(\d{1,3}(?:,\d{3})*|\d+)(\.\d+)?([%TBMK]?)$/;

interface Parsed { sign: string; cur: string; value: number; decimals: number; suffix: string; grouped: boolean }
function parse(text: string): Parsed | null {
  const m = NUM.exec(text.trim());
  if (!m) return null;
  const value = parseFloat((m[3] + (m[4] ?? '')).replace(/,/g, ''));
  if (!isFinite(value)) return null;
  return { sign: m[1], cur: m[2], value, decimals: m[4] ? m[4].length - 1 : 0, suffix: m[5], grouped: m[3].includes(',') };
}
function format(p: Parsed, v: number): string {
  const n = v.toLocaleString('en-US', { minimumFractionDigits: p.decimals, maximumFractionDigits: p.decimals, useGrouping: p.grouped });
  return (p.sign === '-' ? '−' : p.sign) + p.cur + n + p.suffix;
}

/** First non-empty text node directly inside an element (so badges and child spans are left alone). */
function ownText(el: Element): Text | null {
  for (const n of Array.from(el.childNodes)) if (n.nodeType === 3 && (n.nodeValue ?? '').trim()) return n as Text;
  return null;
}

const counting = new WeakSet<Node>();
const recentlyCounted = new Map<string, number>(); // pages that redraw every few seconds don't re-animate
function countUp(el: Element): void {
  const node = ownText(el);
  if (!node || counting.has(node)) return;
  const original = node.nodeValue ?? '';
  const p = parse(original);
  if (!p || p.value === 0) return;
  const key = original.trim(), last = recentlyCounted.get(key);
  if (last && performance.now() - last < 60_000) return;
  recentlyCounted.set(key, performance.now());
  counting.add(node);
  const lead = original.match(/^\s*/)?.[0] ?? '', tail = original.match(/\s*$/)?.[0] ?? '';
  // Prices start close to their value (a settle, not a count from zero); small figures count from zero.
  const from = p.value >= 100 ? p.value * 0.94 : 0;
  const t0 = performance.now(), dur = 700;
  let written = lead + format(p, from) + tail;
  node.nodeValue = written;
  const step = (now: number) => {
    if (node.nodeValue !== written) { counting.delete(node); return; } // someone else wrote: theirs wins
    const k = Math.min(1, (now - t0) / dur), e = 1 - Math.pow(1 - k, 3);
    if (k < 1) {
      written = lead + format(p, from + (p.value - from) * e) + tail;
      node.nodeValue = written;
      requestAnimationFrame(step);
    } else {
      node.nodeValue = original; // always end on the exact original text
      counting.delete(node);
    }
  };
  requestAnimationFrame(step);
}

/** Chart lines draw from left to right the first time they appear. */
const drawn = new WeakSet<SVGPathElement>();
function drawIn(path: SVGPathElement): void {
  if (drawn.has(path)) return;
  drawn.add(path);
  const fill = path.getAttribute('fill');
  if (fill && fill !== 'none') return;
  let len = 0;
  try { len = path.getTotalLength(); } catch { return; }
  if (!len || len < 40) return;
  path.style.strokeDasharray = `${len}`;
  path.style.strokeDashoffset = `${len}`;
  path.getBoundingClientRect();
  path.style.transition = 'stroke-dashoffset 1s cubic-bezier(.22,1,.36,1)';
  path.style.strokeDashoffset = '0';
  setTimeout(() => { path.style.strokeDasharray = ''; path.style.strokeDashoffset = ''; path.style.transition = ''; }, 1100);
}

const COUNT_SEL = '.big,.hl .p,.hmv .p,.cpn-h b,.mchr b,.hm-facts b';
const REVEAL_SEL = '.hm section,.body>.pan,.body>.g>.pan,.hm-do>a,.hm-do2>a,.hm-news>a,.faq details,#nl>.pan';
const SPOT_SEL = '.hm-do>a,.hm-do2>a,.hm-news>a,.pan.card,.cpn,.hl,.hmv';

let io: IntersectionObserver | null = null;
function observe(root: ParentNode): void {
  if (reduced()) return;
  const vh = innerHeight;
  // reveal only what starts below the fold, so nothing visible blinks
  root.querySelectorAll<HTMLElement>(REVEAL_SEL).forEach((el, i) => {
    if (el.dataset.qrv) return;
    el.dataset.qrv = '1';
    if (el.getBoundingClientRect().top < vh * 0.92) return;
    el.classList.add('qrv');
    el.style.setProperty('--qd', `${Math.min(i % 4, 3) * 70}ms`);
    io?.observe(el);
  });
  root.querySelectorAll<Element>(COUNT_SEL).forEach(el => {
    const r = el.getBoundingClientRect();
    if (r.top < vh && r.bottom > 0) countUp(el); else io?.observe(el);
  });
  root.querySelectorAll<SVGPathElement>('#m svg path').forEach(p => {
    const r = p.getBoundingClientRect();
    if (r.top < vh && r.bottom > 0) drawIn(p); else io?.observe(p);
  });
  root.querySelectorAll<HTMLElement>(SPOT_SEL).forEach(el => el.classList.add('qspot'));
}

/** Prices that change in place flash green when up, red when down (real updates only). */
function watchPrices(): void {
  const mo = new MutationObserver(list => {
    if (reduced()) return;
    for (const m of list) {
      const el = (m.target.nodeType === 3 ? m.target.parentElement : m.target) as HTMLElement | null;
      if (!el || !el.isConnected) continue;
      const now = ownText(el);
      if (!now || counting.has(now)) continue;
      let before: string | null = null;
      if (m.type === 'characterData') before = m.oldValue;
      else m.removedNodes.forEach(n => { if (n.nodeType === 3) before = n.nodeValue; });
      if (before == null) continue;
      const a = parse(before.replace('−', '-')), b = parse((now.nodeValue ?? '').replace('−', '-'));
      if (!a || !b || a.value === b.value) continue;
      const up = (b.sign === '-' ? -b.value : b.value) > (a.sign === '-' ? -a.value : a.value);
      el.classList.remove('qflash-up', 'qflash-dn');
      void el.offsetWidth;
      el.classList.add(up ? 'qflash-up' : 'qflash-dn');
    }
  });
  const attach = () => { const t = document.getElementById('tape'); if (t && !t.dataset.qfl) { t.dataset.qfl = '1'; mo.observe(t, { subtree: true, childList: true, characterData: true, characterDataOldValue: true }); } };
  attach();
  new MutationObserver(attach).observe(document.querySelector('header') ?? document.body, { childList: true });
}

/** Safety net: anything already on screen (or scrolled past) is shown, even if a layout change skipped its trigger. */
function revealSafetyNet(): void {
  let queued = false;
  const check = () => {
    queued = false;
    document.querySelectorAll<HTMLElement>('.qrv:not(.qin)').forEach(el => {
      const r = el.getBoundingClientRect();
      if (r.height && r.top < innerHeight) el.classList.add('qin');
    });
  };
  const soon = () => { if (!queued) { queued = true; requestAnimationFrame(check); } };
  for (const ev of ['scroll', 'resize', 'click', 'hashchange']) addEventListener(ev, soon, { passive: true });
  setInterval(check, 800);
}

function spotlight(): void {
  document.addEventListener('pointermove', e => {
    const el = (e.target as Element | null)?.closest?.('.qspot') as HTMLElement | null;
    if (!el) return;
    const r = el.getBoundingClientRect();
    el.style.setProperty('--mx', `${e.clientX - r.left}px`);
    el.style.setProperty('--my', `${e.clientY - r.top}px`);
  }, { passive: true });
}

export function initMotion(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="motion">${CSS}</style>`);
  io = 'IntersectionObserver' in window ? new IntersectionObserver(entries => {
    for (const en of entries) {
      if (!en.isIntersecting) continue;
      const t = en.target;
      io?.unobserve(t);
      if (t.classList.contains('qrv')) t.classList.add('qin');
      else if (t instanceof SVGPathElement) drawIn(t);
      else countUp(t);
    }
  }, { rootMargin: '0px 0px -8% 0px' }) : null;
  const main = document.getElementById('m');
  if (main) {
    let queued = false;
    new MutationObserver(() => { if (queued) return; queued = true; requestAnimationFrame(() => { queued = false; observe(main); }); })
      .observe(main, { childList: true, subtree: true });
    observe(main);
  }
  watchPrices();
  spotlight();
  revealSafetyNet();
}
