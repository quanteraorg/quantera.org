// Apple-style glass motion: a liquid pill that slides between the active nav item, sub-page link and tab;
// menus, the command palette and dialogs that settle in on a spring; a soft light that follows the pointer
// across glass; a header that condenses as you scroll; and back/forward that returns you to where you were.
// Everything holds still for reduced motion, and the glass turns solid for reduced transparency.

const CSS = `
.qpill{position:absolute;left:0;top:0;pointer-events:none;z-index:0;border-radius:20px;opacity:0;
 background:linear-gradient(180deg,rgba(var(--acc),.22),rgba(var(--acc),.12));box-shadow:inset 0 1px 0 rgba(255,255,255,.12),0 4px 14px -6px rgba(var(--acc),.5);
 transition:transform .5s cubic-bezier(.3,1.35,.5,1),width .5s cubic-bezier(.3,1.35,.5,1),height .5s cubic-bezier(.3,1.35,.5,1),opacity .25s}
.qpill.on{opacity:1}.qpill-host>a,.qpill-host>button{position:relative;z-index:1}
.qpill-host.qpill-ready>a.on,.qpill-host.qpill-ready>button.on{background:transparent!important}
#sub.qpill-host .qpill{border-radius:2px;height:2px!important;background:var(--ac);box-shadow:0 0 10px rgba(var(--acc),.6)}
.qglint{position:relative}.qglint::before{content:"";position:absolute;inset:0;border-radius:inherit;pointer-events:none;z-index:2;opacity:0;transition:opacity .35s;
 background:radial-gradient(180px circle at var(--gx,50%) var(--gy,0%),rgba(255,255,255,.10),transparent 65%)}
.qglint:hover::before{opacity:1}
.mm.on,.qf-pop{animation:qglass .42s cubic-bezier(.3,1.3,.5,1) both!important;transform-origin:top center}
.qcp-box,.qm .box{animation:qglass .46s cubic-bezier(.3,1.3,.5,1) both!important}
@keyframes qglass{from{opacity:0;transform:scale(.94) translateY(-6px);filter:blur(6px)}to{opacity:1;transform:none;filter:none}}
.qf-pop{-webkit-backdrop-filter:var(--glass-blur,saturate(180%) blur(20px));backdrop-filter:var(--glass-blur,saturate(180%) blur(20px));background:rgba(var(--bgc),.78);border:1px solid rgba(var(--fgc),.1);box-shadow:0 20px 50px -12px rgba(var(--shc),.45)}
header{transition:padding .3s cubic-bezier(.22,1,.36,1),box-shadow .3s}
html.qcondensed header{box-shadow:0 8px 30px -18px rgba(var(--shc),.55)}
html.qcondensed header::before{background:rgba(var(--bgc),.86)!important}
@media(prefers-reduced-motion:reduce){.qpill{transition:opacity .2s}.mm.on,.qf-pop,.qcp-box,.qm .box{animation:none!important}.qglint::before{display:none}}
@media(prefers-reduced-transparency:reduce){.qf-pop{-webkit-backdrop-filter:none;backdrop-filter:none;background:rgb(var(--bgc))}.qglint::before{display:none}}`;

/** One sliding pill per container; it follows whichever child has class "on". */
const pills = new Map<HTMLElement, HTMLElement>();
function placePill(host: HTMLElement): void {
  let pill = pills.get(host);
  if (!pill || !pill.isConnected) {
    pill = document.createElement('span'); pill.className = 'qpill'; pill.setAttribute('aria-hidden', 'true');
    host.classList.add('qpill-host'); if (getComputedStyle(host).position === 'static') host.style.position = 'relative'; host.prepend(pill); pills.set(host, pill);
  }
  const on = host.querySelector<HTMLElement>(':scope>a.on,:scope>button.on');
  if (!on || !on.offsetWidth) { pill.classList.remove('on'); host.classList.remove('qpill-ready'); return; }
  const x = on.offsetLeft, y = host.id === 'sub' ? on.offsetTop + on.offsetHeight - 2 : on.offsetTop;
  const first = !pill.classList.contains('on');
  if (first) pill.style.transition = 'none';
  pill.style.width = on.offsetWidth + 'px';
  pill.style.height = on.offsetHeight + 'px';
  pill.style.transform = `translate(${x}px,${y}px)`;
  if (first) { pill.getBoundingClientRect(); pill.style.transition = ''; }
  pill.classList.add('on'); host.classList.add('qpill-ready');
}
function pillHosts(): HTMLElement[] {
  return [document.getElementById('nav'), document.getElementById('sub'), ...Array.from(document.querySelectorAll<HTMLElement>('.qtabs'))]
    .filter((h): h is HTMLElement => !!h);
}
function pillsLoop(): void {
  let queued = false;
  const all = () => { queued = false; pillHosts().forEach(placePill); };
  const soon = () => { if (!queued) { queued = true; requestAnimationFrame(all); } };
  // class changes ("on" moves), new tab bars and layout changes all re-place the pills
  new MutationObserver(list => { if (list.some(m => !(m.target as Element).classList?.contains('qpill'))) soon(); })
    .observe(document.body, { subtree: true, attributes: true, attributeFilter: ['class'], childList: true });
  addEventListener('resize', soon, { passive: true });
  document.fonts?.ready.then(soon);
  soon();
}

/** A soft highlight that follows the pointer over glass surfaces. */
function glint(): void {
  const SEL = 'nav#nav,.mm,.qcp-box,.qm .box,.qf-pop,.qtabs button,.qd';
  document.addEventListener('pointermove', e => {
    const el = (e.target as Element | null)?.closest?.(SEL) as HTMLElement | null;
    if (!el) return;
    el.classList.add('qglint');
    const r = el.getBoundingClientRect();
    el.style.setProperty('--gx', `${e.clientX - r.left}px`);
    el.style.setProperty('--gy', `${e.clientY - r.top}px`);
  }, { passive: true });
}

/** The header gets a touch more solid with a soft shadow once the page scrolls. */
function condense(): void {
  let on = false;
  const check = () => { const now = scrollY > 24; if (now !== on) { on = now; document.documentElement.classList.toggle('qcondensed', on); } };
  addEventListener('scroll', check, { passive: true }); check();
}

/** Back and forward return to the scroll position you left, as in a native app. New links still start at the top. */
function scrollMemory(): void {
  const pos = new Map<string, number>();
  let intent = 0;
  for (const ev of ['pointerdown', 'keydown']) addEventListener(ev, () => { intent = performance.now(); }, { capture: true, passive: true });
  let last = location.hash;
  addEventListener('scroll', () => { pos.set(last, scrollY); }, { passive: true });
  addEventListener('hashchange', () => {
    const want = pos.get(location.hash), user = performance.now() - intent < 600;
    last = location.hash;
    if (user || !want) return;
    // pages fill in over a moment (live data), so keep trying until the page is tall enough
    const t0 = performance.now();
    const tryIt = () => {
      if (location.hash !== last) return;
      if (document.documentElement.scrollHeight - innerHeight >= want - 4) { scrollTo(0, want); return; }
      if (performance.now() - t0 < 2500) setTimeout(tryIt, 100);
    };
    requestAnimationFrame(tryIt);
  });
}

export function initGlass(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qglass-css">${CSS}</style>`);
  if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
  pillsLoop();
  glint();
  condense();
  scrollMemory();
}
