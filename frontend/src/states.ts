// Number typography, loading skeletons, and honest error and freshness states.
// Moved out of public/app.js (layer v51) as the first TypeScript module; behaviour is unchanged.

const CSS = `
body,.big,.k,h1,h2,h3,td,th,.p,.sv,.pill,#tape,.hl,.hmv,.cmpt{font-variant-numeric:tabular-nums lining-nums}.hmk{font-variant-numeric:normal}
.qld{position:relative;color:transparent!important;min-width:160px}
.qld::before{content:"";position:absolute;left:0;top:50%;transform:translateY(-50%);height:12px;width:min(260px,100%);border-radius:7px;background:linear-gradient(90deg,var(--s1),var(--s2),var(--s1));background-size:200% 100%;animation:skl 1.3s ease-in-out infinite}
.qld::after{content:attr(data-l);position:absolute;left:0;top:calc(50% + 12px);font-size:11.5px;color:var(--mu);white-space:nowrap}
@media(prefers-reduced-motion:reduce){.qld::before{animation:none}}
.qfresh{flex:none;display:flex;align-items:center;gap:6px;padding:0 10px;font-size:11.5px;color:var(--mu);white-space:nowrap;border-left:1px solid var(--ln)}
.qfresh i{width:6px;height:6px;border-radius:50%;background:var(--up);display:inline-block}.qfresh.stale i{background:#e8a07a}
@media(max-width:800px){.qfresh{display:none}}
.qbanner{display:flex;gap:10px;align-items:center;justify-content:center;flex-wrap:wrap;padding:8px 16px;font-size:13.5px;background:rgba(232,160,122,.12);color:var(--tx);border-bottom:1px solid rgba(232,160,122,.35);text-align:center}
.qbanner button{margin:0;padding:4px 12px;font-size:13px}`;

const NEG = /(^|[\s(])-(?=\$?\d)/;
const NEG_ALL = /(^|[\s(])-(?=\$?\d)/g;
const LOADING = /^Loading\b.{0,40}…$/;
const DATA_API = /\/api\/(markets|snapshot|quotes?|history|intraday|world|popular|news|overview|fundamentals|screen|directory|macro|calendar)\b/;
const STALE_MS = 10 * 60_000;
const NOTICE_EVERY_MS = 5 * 60_000;

/** Negative values in text get a true minus sign (−). Inputs, code and editable text are never touched. */
export function trueMinus(root: Element): void {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
    acceptNode(n) {
      const p = n.parentElement;
      if (!p || p.closest('input,textarea,select,script,style,code,pre,[contenteditable]')) return NodeFilter.FILTER_REJECT;
      return NEG.test(n.nodeValue ?? '') ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
    },
  });
  const nodes: Text[] = [];
  for (let n = walker.nextNode(); n; n = walker.nextNode()) nodes.push(n as Text);
  for (const t of nodes) t.nodeValue = (t.nodeValue ?? '').replace(NEG_ALL, '$1−');
}

/** "Loading …" lines become skeletons; the words stay available to screen readers. */
export function loadingSkeletons(root: Element): void {
  root.querySelectorAll<HTMLElement>('div,span,p,td').forEach(e => {
    if (e.children.length) return;
    const t = (e.textContent ?? '').trim();
    if (LOADING.test(t)) {
      if (!e.classList.contains('qld')) { e.classList.add('qld'); e.setAttribute('role', 'status'); e.dataset.l = t; }
    } else if (e.classList.contains('qld')) {
      e.classList.remove('qld'); delete e.dataset.l;
    }
  });
}

const fresh = { last: 0, fails: 0, noticeAt: 0 };

function drawFreshness(): void {
  const tape = document.getElementById('tape');
  if (!tape) return;
  let e = tape.querySelector<HTMLElement>('.qfresh');
  if (!e) {
    e = document.createElement('span');
    e.className = 'qfresh';
    e.setAttribute('aria-live', 'polite');
    const pause = tape.querySelector('#tpb');
    pause ? tape.insertBefore(e, pause) : tape.appendChild(e);
  }
  if (!fresh.last) { e.style.display = 'none'; return; }
  e.style.display = '';
  const stale = Date.now() - fresh.last > STALE_MS || fresh.fails >= 3;
  e.classList.toggle('stale', stale);
  const when = new Date(fresh.last).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
  e.innerHTML = `<i></i>${stale ? 'Prices may be out of date' : 'Prices as of ' + when} · some delayed 15 min`;
}

function dataFailed(): void {
  fresh.fails++;
  drawFreshness();
  if (fresh.fails >= 3 && Date.now() - fresh.noticeAt > NOTICE_EVERY_MS) {
    fresh.noticeAt = Date.now();
    try { qtoast('Live data isn’t responding right now. Numbers on screen may be out of date; we’ll keep trying.'); } catch { /* toast is optional */ }
  }
}

/** Watches real data responses to drive the freshness label and the failure notice. */
function watchFetch(): void {
  const original = window.fetch.bind(window);
  window.fetch = async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    const isData = DATA_API.test(url);
    try {
      const r = await original(input, init);
      if (isData) {
        if (r.ok) { fresh.last = Date.now(); fresh.fails = 0; drawFreshness(); }
        else if (r.status >= 500) dataFailed();
      }
      return r;
    } catch (err) {
      if (isData) dataFailed();
      throw err;
    }
  };
}

/** On the deployed site, say plainly when live data never connected and sample prices are showing. */
function sampleDataBanner(): void {
  const deployed = /^https?:/.test(location.protocol) && !/^(localhost|127\.)/.test(location.hostname);
  if (!deployed || QLIVE.ok || document.querySelector('.qbanner')) return;
  document.querySelector('header')?.insertAdjacentHTML('afterend',
    '<div class="qbanner" role="status"><span><b>Live data is unavailable right now.</b> Prices shown are sample data, not real quotes.</span>' +
    '<button class="btn" onclick="location.reload()">Try again</button></div>');
}

export function initStates(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="v51">${CSS}</style>`);
  const main = document.getElementById('m');
  if (main) {
    let t: number | undefined;
    const run = () => { trueMinus(main); loadingSkeletons(main); };
    new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(run, 60); })
      .observe(main, { childList: true, subtree: true, characterData: true });
    run();
  }
  watchFetch();
  setInterval(drawFreshness, 60_000);
  setTimeout(sampleDataBanner, 12_000);
}
