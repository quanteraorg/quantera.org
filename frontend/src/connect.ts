// One ecosystem instead of separate tools. The company you were last researching follows you: Markets, News,
// Learn, Practice and Portfolio show a slim "continue researching" bar, and every Academy lesson ends with a
// "try it on a real company" link into the part of the product that uses it (a lesson on P/E opens the competitor
// table, a lesson on DCF opens the valuation model, a lesson on RSI opens a chart, and so on).

interface Ctx { s: string; name?: string; at: number }
const KEY = 'q_ctx';
const FRESH = 7 * 864e5;

const CSS = `
.qctx{display:flex;align-items:center;gap:8px 12px;flex-wrap:wrap;margin:0 0 16px;padding:10px 14px;border-radius:14px;border:1px solid rgba(var(--acc),.28);background:rgba(var(--acc),.05);font-size:13.5px}
.qctx b{font-weight:600}.qctx .lead{color:var(--mu)}.qctx a{white-space:nowrap}.qctx .x{margin-left:auto;background:none;border:0;color:var(--mu);font-size:18px;line-height:1;cursor:pointer;padding:2px 6px}
.qtry{margin-top:18px;padding:16px 18px!important}.qtry h3{margin:0 0 6px;font-size:16px}.qtry p{margin:0 0 10px;color:var(--mu);font-size:13.5px;line-height:1.55}`;

// lesson id -> [what to open, label]. {s} is the company you were researching (or Apple if none).
const TRY: Record<string, [string, string]> = {
  pe: ['#/asset/{s}/research', 'Compare {s}’s P/E with its competitors'],
  peg: ['#/asset/{s}/research', 'See {s}’s forward P/E next to its competitors'],
  ratios: ['#/asset/{s}/research', 'Compare {s}’s margins and returns with its competitors'],
  research: ['#/asset/{s}/research', 'Open {s}’s research workspace'],
  income: ['#/asset/{s}/fin', 'Read {s}’s income statement from its SEC filings'],
  cashflow: ['#/asset/{s}/fin', 'See {s}’s cash flow and free cash flow'],
  dcf: ['#/valuation/{s}', 'Run a DCF on {s} with its real filed figures'],
  wacc: ['#/valuation/{s}', 'Change the discount rate in {s}’s valuation'],
  dividend: ['#/asset/{s}', 'Check {s}’s dividend yield and payout'],
  stock: ['#/asset/{s}', 'Look at a real stock page: {s}'],
  movingavg: ['#/asset/{s}', 'Add moving averages to {s}’s chart'],
  macd: ['#/asset/{s}', 'Add MACD to {s}’s chart'],
  rsi: ['#/asset/{s}', 'Add RSI to {s}’s chart'],
  bollinger: ['#/asset/{s}', 'Add Bollinger Bands to {s}’s chart'],
  volume: ['#/asset/{s}', 'Look at {s}’s volume and VWAP'],
  levels: ['#/asset/{s}', 'Draw support and resistance on {s}’s chart'],
  fib: ['#/asset/{s}', 'Draw a Fibonacci retracement on {s}’s chart'],
  orders: ['#/trade/{s}', 'Place a practice limit order on {s}'],
  sizing: ['#/trade/{s}', 'Size a practice position in {s}'],
  riskreward: ['#/trade/{s}', 'Set a stop loss and take profit on {s} (practice money)'],
  leverage: ['#/trade/{s}', 'Practise a trade on {s} with virtual money'],
  divers: ['#/portfolio', 'Test how a mix of investments would have done'],
  drawdown: ['#/portfolio', 'See the biggest drop of a mix of investments'],
  sharpe: ['#/portfolio', 'Compare risk and return of a mix of investments'],
  var: ['#/portfolio', 'See the ups and downs of a mix of investments'],
  capm: ['#/asset/{s}', 'Find {s}’s beta on its stock page'],
  rates: ['#/macro', 'See today’s interest rates and inflation'],
  inflation: ['#/macro', 'See the latest inflation figures'],
  bond: ['#/world', 'See today’s bond yields'],
  duration: ['#/world', 'See today’s bond yields'],
  options101: ['#/options', 'Build an options payoff'],
  bs: ['#/options', 'Build an options payoff'],
  compound: ['#/planner', 'Plan a monthly investment goal'],
  rule72: ['#/planner', 'Plan a monthly investment goal'],
  fees: ['#/planner', 'See what fees do to a long-term goal'],
};
const CTX_PAGES = /^#\/(markets|news|learn|trade|portfolio|screener|compare|calendar|brief|world|macro|me)?\/?$/;

const ctx = (): Ctx | null => { const c = LS<Ctx | null>(KEY, null); return c && c.s && Date.now() - c.at < FRESH ? c : null; };

function remember(): void {
  const m = /^#\/asset\/([^/]+)/.exec(location.hash); if (!m) return;
  const s = decodeURIComponent(m[1]).toUpperCase();
  if (/[=^]|\.PVT$/.test(s)) return; // indexes, futures and private companies have no research workspace
  const kn = document.querySelector('#m .kn'); if (!kn) return; // only once the page loaded as a real company
  const h = (document.querySelector('#m h1, #m h2')?.firstChild?.textContent || '').trim();
  const cur = LS<Ctx | null>(KEY, null);
  if (cur?.s === s && Date.now() - cur.at < 60_000) return;
  if (cur?.s !== s) hidden = false; // a new company brings the bar back
  SS(KEY, { s, name: h && h !== s ? h.slice(0, 40) : undefined, at: Date.now() });
}

let hidden = false; // "×" hides the bar until you open a different company
function bar(): void {
  if (hidden || !CTX_PAGES.test(location.hash || '#/') || location.hash === '' || location.hash === '#/') return;
  const m = document.getElementById('m'); const c = ctx();
  if (!m || !c || document.getElementById('qctx')) return;
  if (location.hash.startsWith('#/trade') && new RegExp('#/trade/' + c.s, 'i').test(location.hash)) return;
  const e = encodeURIComponent(c.s);
  const el = document.createElement('div'); el.id = 'qctx'; el.className = 'qctx'; el.setAttribute('role', 'region'); el.setAttribute('aria-label', 'Continue researching');
  el.innerHTML = `<span class="lead">Continue with</span><b>${esc(c.name || c.s)}${c.name ? ` <span class="mu" style="font-weight:400">${esc(c.s)}</span>` : ''}</b>
    <a href="#/asset/${e}/research">Research</a><a href="#/asset/${e}/fin">Statements</a><a href="#/valuation/${e}">Valuation</a><a href="#/asset/${e}">Chart</a><a href="#/trade/${e}">Practice trade</a>
    <button class="x" aria-label="Hide for now" title="Hide for now">×</button>`;
  (el.querySelector('.x') as HTMLButtonElement).onclick = () => { hidden = true; el.remove(); };
  // inside the page's content column, so it lines up with everything else
  const body = m.querySelector<HTMLElement>(':scope > .body') || m.querySelector<HTMLElement>('.body');
  if (body) body.prepend(el);
  else { const hero = m.querySelector(':scope > .phero'); if (hero) { el.style.margin = '16px 28px'; hero.insertAdjacentElement('afterend', el); } else m.prepend(el); }
}

function tryIt(): void {
  const mm = /^#\/lesson\/([^/]+)/.exec(location.hash); if (!mm) return;
  const id = decodeURIComponent(mm[1]), t = TRY[id];
  const m = document.getElementById('m');
  if (!t || !m || document.getElementById('qtry') || !m.textContent?.trim()) return;
  const s = ctx()?.s || 'AAPL';
  const href = t[0].replace('{s}', encodeURIComponent(s)), label = t[1].replace(/\{s\}/g, s);
  const el = document.createElement('div'); el.id = 'qtry'; el.className = 'pan qtry';
  el.innerHTML = `<h3>Try it on a real company</h3><p>Put this lesson to work with live data${ctx() ? ` on ${esc(s)}, the company you were last researching` : ''}.</p><a class="btn p" href="${href}">${esc(label)} →</a>`;
  const body = m.querySelector('.body') || m; body.appendChild(el);
}

export function initConnect(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qctx-css">${CSS}</style>`);
  if (!SYNC.includes(KEY)) SYNC.push(KEY);
  const main = document.getElementById('m'); if (!main) return;
  let t: number | undefined;
  const run = () => { remember(); bar(); tryIt(); };
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(run, 120); }).observe(main, { childList: true, subtree: true });
  run();
}
