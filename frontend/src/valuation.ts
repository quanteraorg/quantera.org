// Discounted cash flow (DCF) valuation on real figures: the company's own free cash flow, share count, cash and debt
// from its SEC filings, and the live share price. Replaces the old page, which loaded made-up inputs from sample data
// and then told people a stock "may be undervalued".
//
// Every assumption is shown with where its starting value came from, and can be changed. The result is presented as
// "what these assumptions imply", next to a sensitivity table and the growth the current price implies (a reverse DCF),
// never as a verdict. Banks and insurers are refused: free cash flow doesn't describe them.

interface Line { key: string; values: (number | null)[] }
interface Period { label: string; end: string; filing?: string; form?: string }
interface St { symbol: string; name?: string; financial: boolean; annual: { periods: Period[]; income: Line[]; balance: Line[]; cashflow: Line[] } }
interface Inputs { fcf: number; g: number; d: number; t: number; shares: number; net: number; price: number }

const CSS = `
.qv h1{font-size:34px;margin:0 0 6px}.qv .lead{color:var(--mu);max-width:760px;line-height:1.6;margin:0 0 18px}
.qv-find{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:18px}.qv-find input{max-width:260px}
.qv-g{display:grid;grid-template-columns:minmax(0,360px) minmax(0,1fr);gap:16px;align-items:start}@media(max-width:900px){.qv-g{grid-template-columns:minmax(0,1fr)}#qvout{order:-1}}
.qv-in label{display:block;font-size:13.5px;margin:14px 0 4px;font-weight:500}.qv-in label:first-of-type{margin-top:0}
.qv-in .row{display:flex;gap:10px;align-items:center}.qv-in input[type=range]{flex:1;margin:0}.qv-in input[type=number]{width:110px;margin:0;font-variant-numeric:tabular-nums}
.qv-in small{display:block;color:var(--mu);font-size:12px;line-height:1.45;margin-top:4px}.qv-in small a{color:var(--mu)}
.qv-big{font-size:40px;font-weight:600;font-variant-numeric:tabular-nums;letter-spacing:-.02em;line-height:1.15}
.qv-sum{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:14px 0}.qv-sum>div{border:1px solid var(--ln);border-radius:12px;padding:10px 12px}
.qv-sum span{display:block;font-size:12px;color:var(--mu)}.qv-sum b{font-size:17px;font-variant-numeric:tabular-nums}
.qv table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums}.qv th,.qv td{padding:7px 9px;border-bottom:1px solid var(--ln);text-align:right;white-space:nowrap}
.qv th{font-size:12px;color:var(--mu);font-weight:500}.qv th:first-child,.qv td:first-child{text-align:left}
.qv td.me{outline:2px solid var(--ac);outline-offset:-2px;font-weight:600}
.qv .ov{overflow-x:auto}.qv-note{font-size:13px;color:var(--mu);line-height:1.6}.qv-warn{padding:12px 14px;border-radius:12px;border:1px solid rgba(var(--acc),.35);background:rgba(var(--acc),.06);line-height:1.55}`;

const last = (l: Line | undefined): [number, number] | null => {
  if (!l) return null;
  for (let i = l.values.length - 1; i >= 0; i--) { const v = l.values[i]; if (v != null) return [v, i]; }
  return null;
};
const find = (ls: Line[], k: string) => ls.find(l => l.key === k);
const money = (v: number) => { const a = Math.abs(v), s = v < 0 ? '−' : ''; return a >= 1e12 ? `${s}$${fmt(a / 1e12, 2)}T` : a >= 1e9 ? `${s}$${fmt(a / 1e9, 1)}B` : a >= 1e6 ? `${s}$${fmt(a / 1e6, 0)}M` : `${s}$${fmt(a, 2)}`; };
const pct = (v: number, dp = 1) => `${v < 0 ? '−' : ''}${fmt(Math.abs(v), dp)}%`;

/** Two-stage DCF: growth g for years 1-5, fading in a straight line to terminal growth t by year 10, then a perpetuity. */
export function dcf(x: Inputs, g = x.g, d = x.d) {
  const rows: { y: number; growth: number; fcf: number; df: number; pv: number }[] = [];
  let c = x.fcf, pv = 0;
  for (let y = 1; y <= 10; y++) {
    const gr = y <= 5 ? g : g + (x.t - g) * (y - 5) / 5;
    c *= 1 + gr / 100;
    const df = 1 / Math.pow(1 + d / 100, y);
    pv += c * df;
    rows.push({ y, growth: gr, fcf: c, df, pv: c * df });
  }
  const tv = d > x.t ? c * (1 + x.t / 100) / ((d - x.t) / 100) : NaN, tvpv = tv / Math.pow(1 + d / 100, 10);
  const ev = pv + tvpv, eq = ev + x.net;
  return { rows, pv, tv, tvpv, ev, eq, ps: eq / x.shares };
}
/** The year 1-5 growth rate at which the model's value equals today's price (bisection). */
export function impliedGrowth(x: Inputs): number | null {
  const f = (g: number) => dcf(x, g).ps - x.price;
  let lo = -20, hi = 60;
  if (f(lo) > 0 || f(hi) < 0) return null;
  for (let i = 0; i < 60; i++) { const m = (lo + hi) / 2; if (f(m) > 0) hi = m; else lo = m; }
  return (lo + hi) / 2;
}

let state: { sym: string; st: St; x: Inputs; base: Inputs; src: Record<string, string> } | null = null;

function page(sym?: string): void {
  const m = document.getElementById('m')!;
  m.innerHTML = `<div class="qv"><p class="lead">A discounted cash flow model: it projects the cash a business generates, then works out what that is worth today. Starting figures come from the company’s SEC filings and the live share price. Change any assumption to see how much the answer moves.</p>
    <div class="qv-find"><input id="qvq" placeholder="US stock ticker, e.g. AAPL" aria-label="Ticker" value="${esc(sym || '')}" maxlength="12"><button class="btn p" id="qvgo">Load</button>
    ${['AAPL', 'MSFT', 'NVDA', 'KO', 'COST'].map(s => `<button class="chip" data-s="${s}">${s}</button>`).join('')}</div><div id="qvb"></div></div>`;
  const go = (s: string) => { s = s.trim().toUpperCase(); if (/^[A-Z][A-Z0-9.\-]{0,9}$/.test(s)) location.hash = '#/valuation/' + encodeURIComponent(s); };
  (m.querySelector('#qvgo') as HTMLButtonElement).onclick = () => go((m.querySelector('#qvq') as HTMLInputElement).value);
  (m.querySelector('#qvq') as HTMLInputElement).onkeydown = e => { if (e.key === 'Enter') go((e.target as HTMLInputElement).value); };
  m.querySelectorAll<HTMLButtonElement>('[data-s]').forEach(b => b.onclick = () => go(b.dataset.s!));
  const box = m.querySelector('#qvb') as HTMLElement;
  if (!sym) { box.innerHTML = '<div class="pan qv-note">Pick a company to start. The model needs at least one year of positive free cash flow from the company’s annual reports.</div>'; return; }
  box.innerHTML = `<div class="pan"><div class="qst-load" style="height:260px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite"></div></div>`;
  track('valuation_load');
  Promise.all([jf('/api/statements?symbol=' + encodeURIComponent(sym)), jf('/api/quote?symbol=' + encodeURIComponent(sym))]).then(([s, q]) => {
    if (!box.isConnected) return;
    if (!s.ok) { box.innerHTML = `<div class="pan qv-warn">${esc(s.b?.error || 'Financial statements for ' + sym + ' could not be loaded.')} The model needs a company’s SEC filings, so it works for US-listed companies that file 10-Ks.</div>`; return; }
    const st = s.b as St;
    if (st.financial) { box.innerHTML = `<div class="pan qv-warn">🏦 <b>${esc(st.name || sym)} is a bank, insurer or other financial company.</b> This model values the free cash flow a business generates, but for a lender, cash moving through loans and deposits is the business itself, so free cash flow doesn’t describe it. Analysts value these companies with other tools, such as price to book value and return on equity (see the Financials tab on its stock page).</div>`; return; }
    const A = st.annual, P = A.periods, cf = A.cashflow, inc = A.income, bal = A.balance;
    const f = last(find(cf, 'freeCashFlow')), sh = last(find(inc, 'shares')), rev = find(inc, 'revenue');
    const price = q.ok && q.b?.price ? Number(q.b.price) : NaN;
    if (!f || !sh || !isFinite(price)) { box.innerHTML = `<div class="pan qv-warn">${esc(st.name || sym)}’s filings don’t include ${!f ? 'free cash flow (cash from operations and capital spending)' : !sh ? 'a diluted share count' : 'a current price we could load'}, so the model can’t start. Nothing is filled in with guesses.</div>`; return; }
    // starting growth: the company's own revenue growth (compound annual, up to 5 years), kept between 0% and 20%
    let cagr: number | null = null, yrs = 0;
    if (rev) { const v = rev.values, iL = v.length - 1; for (let k = Math.min(5, iL); k >= 1; k--) { const a = v[iL], b = v[iL - k]; if (a != null && b != null && a > 0 && b > 0) { cagr = (Math.pow(a / b, 1 / k) - 1) * 100; yrs = k; break; } } }
    const g0 = cagr == null ? 5 : Math.max(0, Math.min(20, cagr));
    const cash = last(find(bal, 'cash')), sti = last(find(bal, 'shortTermInvestments')), debt = last(find(bal, 'longTermDebt'));
    const net = (cash?.[0] || 0) + (sti?.[0] || 0) - (debt?.[0] || 0);
    const fy = (i: number) => P[i]?.label || '', link = (i: number) => P[i]?.filing ? ` (<a href="${esc(P[i].filing!)}" target="_blank" rel="noopener noreferrer">${esc(P[i].form || 'filing')} ↗</a>)` : '';
    // one unusual year (a one-off payment, a big tax bill) can swing a DCF, so compare it with the 3-year average
    const fl = find(cf, 'freeCashFlow')!.values, last3 = fl.slice(-3).filter((v): v is number => v != null);
    const avg3 = last3.length === 3 ? last3.reduce((a, b) => a + b, 0) / 3 : null;
    const differs = avg3 != null && avg3 > 0 && Math.abs(f[0] / avg3 - 1) > 0.25;
    const useAvg = false; // the reported latest year is the standard start; growth companies sit well above their average for good reason
    const start = useAvg ? avg3! : f[0];
    const base: Inputs = { fcf: start / 1e9, g: Math.round(g0 * 2) / 2, d: 9, t: 2.5, shares: sh[0] / 1e9, net: net / 1e9, price };
    state = {
      sym, st, x: { ...base }, base,
      src: {
        fcf: (useAvg
          ? `Average of the last 3 years (${fy(P.length - 3)}–${fy(P.length - 1)}): ${money(avg3!)}. The latest year alone, ${money(f[0])}${link(f[1])}, is ${f[0] > avg3! ? 'well above' : 'well below'} that average, so it may include one-off items. Type ${fmt(f[0] / 1e9, 1)} to use it instead.`
          : `${fy(f[1])} free cash flow: cash from operations minus capital spending${link(f[1])}.${avg3 != null ? ` The 3-year average is ${money(avg3)}.` : ''}${differs ? ` The latest year is ${f[0] > avg3! ? 'well above' : 'well below'} the average: that can be growth${f[0] > avg3! ? '' : ' slowing'}, or one-off items such as a large payment. If it’s one-offs, the average is a steadier start.` : ''}`)
          + (avg3 != null ? `<span style="display:flex;gap:6px;flex-wrap:wrap;margin-top:6px"><button class="chip" data-fcf="${f[0] / 1e9}">Latest year (${money(f[0])})</button><button class="chip" data-fcf="${avg3 / 1e9}">3-year average (${money(avg3)})</button></span>` : ''),
        g: cagr == null ? 'Revenue history wasn’t available, so this starts at 5%.' : `${esc(st.name || sym)}’s revenue grew ${pct(cagr)} a year over the last ${yrs} year${yrs > 1 ? 's' : ''}${cagr > 20 ? ', capped at 20% because very few companies keep that up for five years' : cagr < 0 ? ', floored at 0%' : ''}. Years 6–10 fade to the terminal rate.`,
        d: 'The return investors expect for the risk. 8–10% is typical for a large US company; riskier businesses use more.',
        t: 'Growth forever after year 10. Kept near long-run inflation and economic growth; it must be below the discount rate.',
        shares: `${fy(sh[1])} diluted shares${link(sh[1])}.`,
        net: `Cash and short-term investments minus long-term debt at the end of ${fy((cash || debt || [0, P.length - 1])[1])}. Added to the value of the business to get the value for shareholders.`,
        price: 'Live or delayed up to 15 minutes.',
      },
    };
    render(box);
  }).catch(e => { console.error('valuation', e); if (box.isConnected) box.innerHTML = '<div class="pan qv-warn">The data couldn’t be loaded just now. Please try again.</div>'; });
}

const FIELDS: [keyof Inputs, string, number, number, number, string][] = [
  ['fcf', 'Starting free cash flow ($B)', -50, 400, 0.1, ''], ['g', 'Growth, years 1–5 (%)', -10, 40, 0.5, '%'], ['d', 'Discount rate (%)', 5, 16, 0.5, '%'],
  ['t', 'Terminal growth (%)', 0, 4, 0.25, '%'], ['shares', 'Diluted shares (billions)', 0.01, 30, 0.01, ''], ['net', 'Net cash ($B; negative = net debt)', -500, 500, 0.1, ''],
];

function render(box: HTMLElement): void {
  if (!state) return;
  const { x, src, st, sym } = state;
  box.innerHTML = `<div class="qv-g"><div class="pan qv-in"><h3 style="margin:0 0 12px">${esc(st.name || sym)} · assumptions</h3>
    ${FIELDS.map(([k, label, mn, mx, step]) => `<label for="qv-${k}">${label}</label><div class="row"><input type="range" min="${mn}" max="${mx}" step="${step}" value="${x[k]}" data-k="${k}" aria-label="${label}"><input type="number" id="qv-${k}" step="${step}" value="${+x[k].toFixed(3)}" data-k="${k}"></div><small>${src[k]}</small>`).join('')}
    <label>Share price</label><div class="row"><b style="font-size:18px">$${fmt(x.price, 2)}</b></div><small>${src.price}</small>
    <button class="chip" id="qvreset" style="margin-top:14px" hidden>Reset to the filed figures</button></div>
    <div id="qvout"></div></div>`;
  const sync = (k: keyof Inputs, from: HTMLInputElement) => {
    box.querySelectorAll<HTMLInputElement>(`input[data-k=${k}]`).forEach(i => { if (i !== from) i.value = String(i.type === 'number' ? +state!.x[k].toFixed(3) : state!.x[k]); });
  };
  box.querySelectorAll<HTMLInputElement>('input[data-k]').forEach(i => {
    const ev = i.type === 'range' ? 'oninput' : 'onchange';
    (i as any)[ev] = () => { const v = +i.value; if (!state || !isFinite(v)) return; const k = i.dataset.k as keyof Inputs; state.x[k] = v; sync(k, i); out(box); };
  });
  box.querySelectorAll<HTMLButtonElement>('[data-fcf]').forEach(b => b.onclick = () => { if (!state) return; state.x.fcf = +b.dataset.fcf!; const i = box.querySelector<HTMLInputElement>('input[type=number][data-k=fcf]')!; sync('fcf', i); i.value = String(+state.x.fcf.toFixed(3)); out(box); track('valuation_fcf_basis'); });
  (box.querySelector('#qvreset') as HTMLButtonElement).onclick = () => { if (state) { state.x = { ...state.base }; render(box); } };
  out(box);
}

function out(box: HTMLElement): void {
  if (!state) return;
  const { x, base, sym } = state;
  const r = dcf(x), up = r.ps / x.price - 1, ig = impliedGrowth(x), share = r.tvpv / r.ev;
  const ds = [-2, -1, 0, 1, 2].map(k => x.d + k), gs = [-4, -2, 0, 2, 4].map(k => x.g + k);
  const changed = (Object.keys(base) as (keyof Inputs)[]).some(k => Math.abs(base[k] - x[k]) > 1e-9);
  (box.querySelector('#qvreset') as HTMLButtonElement).hidden = !changed;
  const ok = x.d > x.t && x.shares > 0;
  (box.querySelector('#qvout') as HTMLElement).innerHTML = `<div class="pan"><div class="mu" style="font-size:13px">With these assumptions, the model gives</div>
    <div class="qv-big" aria-live="polite">${ok && isFinite(r.ps) ? '$' + fmt(r.ps, 2) : '—'} <span style="font-size:16px;font-weight:500" class="${up >= 0 ? 'up' : 'dn'}">${ok && isFinite(up) ? (up >= 0 ? '+' : '−') + fmt(Math.abs(up * 100), 0) + '% vs $' + fmt(x.price, 2) : ''}</span></div>
    <div class="qv-note" style="margin-top:6px">per share. ${ig == null || !ok ? '' : `Put the other way: today’s price assumes about <b>${pct(ig)}</b> a year of free cash flow growth for five years (with the other assumptions unchanged).`} This is what the assumptions imply, not a forecast. Small changes move it a lot; see the table below.</div>
    ${!ok ? '<div class="qv-warn" style="margin-top:8px">The discount rate must be higher than terminal growth (otherwise the value is infinite), and shares must be above zero.</div>' : `
    <div class="qv-sum"><div><span>Value of years 1–10</span><b>${money(r.pv * 1e9)}</b></div><div><span>Value after year 10</span><b>${money(r.tvpv * 1e9)}</b></div><div><span>Business value</span><b>${money(r.ev * 1e9)}</b></div><div><span>+ net cash</span><b>${money(x.net * 1e9)}</b></div><div><span>Value for shareholders</span><b>${money(r.eq * 1e9)}</b></div></div>
    ${share > 0.75 ? `<div class="qv-warn" style="font-size:13.5px">${fmt(share * 100, 0)}% of the value comes from after year 10, so the result depends mostly on terminal growth and the discount rate, the least certain inputs.</div>` : `<div class="qv-note">${fmt(share * 100, 0)}% of the value comes from after year 10.</div>`}
    ${x.fcf <= 0 ? '<div class="qv-warn" style="margin-top:8px">Free cash flow is negative, so growing it makes the value fall. A DCF on negative cash flow says little; analysts usually start from a forecast of when it turns positive.</div>' : ''}`}</div>
    ${ok ? `<div class="pan" style="margin-top:14px"><h3 style="margin:0 0 6px">Sensitivity: value per share</h3><div class="qv-note" style="margin-bottom:8px">Rows change the discount rate, columns the growth in years 1–5. The outlined cell is your current set-up.</div>
    <div class="ov"><table><tr><th>Discount \\ growth</th>${gs.map(g => `<th>${pct(g)}</th>`).join('')}</tr>${ds.map(d => `<tr><td>${pct(d)}</td>${gs.map(g => { const v = dcf(x, g, d).ps; return `<td class="${d === x.d && g === x.g ? 'me' : ''}">${isFinite(v) && d > x.t ? '$' + fmt(v, 0) : '—'}</td>`; }).join('')}</tr>`).join('')}</table></div></div>
    <div class="pan" style="margin-top:14px"><h3 style="margin:0 0 8px">Year by year</h3><div class="ov"><table><tr><th>Year</th><th>Growth</th><th>Free cash flow</th><th>Discount factor</th><th>Value today</th></tr>
    ${r.rows.map(w => `<tr><td>${w.y}</td><td>${pct(w.growth)}</td><td>${money(w.fcf * 1e9)}</td><td>${fmt(w.df, 3)}</td><td>${money(w.pv * 1e9)}</td></tr>`).join('')}
    <tr><td>After 10</td><td>${pct(x.t)}</td><td>${money(r.tv * 1e9)} <span class="mu">(value then)</span></td><td>${fmt(1 / Math.pow(1 + x.d / 100, 10), 3)}</td><td>${money(r.tvpv * 1e9)}</td></tr></table></div>
    <p class="qv-note" style="margin:10px 0 0">Method: each year’s free cash flow is divided by (1 + discount rate)<sup>year</sup>. The value after year 10 is year-10 cash flow × (1 + terminal growth) ÷ (discount rate − terminal growth), discounted back. Business value plus net cash, divided by diluted shares, gives the value per share. Sources: SEC filings (each starting figure links to its filing) and the live quote. Not investment advice.</p></div>` : ''}
    <a class="chip" href="#/asset/${encodeURIComponent(sym)}/fin" style="margin-top:12px;display:inline-block">See ${esc(sym)}’s financial statements →</a>`;
}

export function initValuation(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qv-css">${CSS}</style>`);
  if (!SUBT.research?.some(x => x[1] === 'valuation')) SUBT.research?.push(['Valuation', 'valuation']);
  PAGES.valuation = (a?: string) => { track('view_valuation'); page(a && a !== 'valuation' ? decodeURIComponent(a).toUpperCase() : undefined); };
  // link from the statements on each stock page
  const main = document.getElementById('m');
  if (main) new MutationObserver(() => {
    const q = document.querySelector('#qst .wsn'); const mm = /^#\/asset\/([^/]+)/.exec(location.hash);
    if (q && mm && !document.getElementById('qvlink')) q.insertAdjacentHTML('beforebegin', `<a class="chip" id="qvlink" href="#/valuation/${mm[1]}" style="display:inline-block;margin-top:10px">Value this company (DCF) →</a>`);
  }).observe(main, { childList: true, subtree: true });
  if (/^#\/valuation/.test(location.hash)) route();
}
