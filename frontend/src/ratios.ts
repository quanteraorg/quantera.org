// Ratio calculator (#/ratios, #/ratios/AAPL). Everything is worked out in the browser.
// Load a US-listed company and the inputs fill from its latest annual report filed with the SEC (/api/statements),
// plus its current market value (/api/fundamentals) for the valuation ratios. Every input says where it came from,
// and changes to "You entered" when edited. Any field can also be typed by hand, so it works for any company.

type Src = { label: string; kind: 'sec' | 'mkt' | 'yh' | 'you' };
const F: [string, string, string][] = [
  // key, label, hint
  ['revenue', 'Revenue', 'Total sales for the year'],
  ['grossProfit', 'Gross profit', 'Revenue minus the direct cost of what was sold'],
  ['operatingIncome', 'Operating income', 'Profit from the business before interest and tax'],
  ['netIncome', 'Net income', 'Profit after every cost and tax'],
  ['assets', 'Total assets', 'Everything the company owns'],
  ['equity', "Shareholders' equity", 'Assets minus everything owed'],
  ['currentAssets', 'Current assets', 'Cash and things turned into cash within a year'],
  ['currentLiabilities', 'Current liabilities', 'Bills due within a year'],
  ['cash', 'Cash', 'Cash and cash equivalents'],
  ['totalDebt', 'Total debt', 'Borrowings, including what is due within a year'],
  ['operatingCashFlow', 'Operating cash flow', 'Cash the business brought in'],
  ['capex', 'Capital expenditure', 'Cash spent on buildings and equipment'],
  ['marketCap', 'Market value', 'Share price × shares (what the market says the company is worth)'],
];
type R = { name: string; f: string; v: (x: Record<string, number | null>) => number | null; unit: 'pct' | 'x' | 'usd'; why: string };
const q = (a: number | null, b: number | null) => a == null || b == null || b === 0 ? null : a / b;
export const RATIOS: [string, R[]][] = [
  ['Profitability', [
    { name: 'Gross margin', f: 'Gross profit ÷ revenue', v: x => q(x.grossProfit, x.revenue), unit: 'pct', why: 'How much of each dollar of sales is left after the direct cost of the product. High margins usually mean pricing power.' },
    { name: 'Operating margin', f: 'Operating income ÷ revenue', v: x => q(x.operatingIncome, x.revenue), unit: 'pct', why: 'What is left after running the business (staff, research, marketing), before interest and tax.' },
    { name: 'Net margin', f: 'Net income ÷ revenue', v: x => q(x.netIncome, x.revenue), unit: 'pct', why: 'The share of sales that ends up as profit for shareholders.' },
    { name: 'Return on equity', f: 'Net income ÷ equity', v: x => x.equity != null && x.equity <= 0 ? null : q(x.netIncome, x.equity), unit: 'pct', why: "Profit made on the shareholders' money. Heavy borrowing or buybacks can push it up, so read it alongside debt." },
    { name: 'Return on assets', f: 'Net income ÷ total assets', v: x => q(x.netIncome, x.assets), unit: 'pct', why: 'Profit made on everything the company owns, however it was paid for.' },
  ]],
  ['Cash', [
    { name: 'Free cash flow', f: 'Operating cash flow − capital expenditure', v: x => x.operatingCashFlow == null || x.capex == null ? null : x.operatingCashFlow - Math.abs(x.capex), unit: 'usd', why: 'Cash left after keeping the business running and growing. It pays dividends, buybacks and debt.' },
    { name: 'Free cash flow margin', f: 'Free cash flow ÷ revenue', v: x => x.operatingCashFlow == null || x.capex == null ? null : q(x.operatingCashFlow - Math.abs(x.capex), x.revenue), unit: 'pct', why: 'How much of each dollar of sales turns into spare cash.' },
  ]],
  ['Safety', [
    { name: 'Current ratio', f: 'Current assets ÷ current liabilities', v: x => q(x.currentAssets, x.currentLiabilities), unit: 'x', why: 'Can the company pay the bills due this year? Below 1 is not always a problem for strong businesses, but it is worth a look.' },
    { name: 'Debt to equity', f: 'Total debt ÷ equity', v: x => x.equity != null && x.equity <= 0 ? null : q(x.totalDebt, x.equity), unit: 'x', why: 'How much the company borrows compared with what shareholders have put in and kept.' },
    { name: 'Net debt', f: 'Total debt − cash', v: x => x.totalDebt == null || x.cash == null ? null : x.totalDebt - x.cash, unit: 'usd', why: 'Debt left if all the cash paid it down. Negative means more cash than debt (net cash).' },
  ]],
  ['Valuation', [
    { name: 'Price to earnings (P/E)', f: 'Market value ÷ net income', v: x => x.netIncome != null && x.netIncome <= 0 ? null : q(x.marketCap, x.netIncome), unit: 'x', why: 'How many years of current profit the market price represents. Not meaningful when the company makes a loss.' },
    { name: 'Price to sales (P/S)', f: 'Market value ÷ revenue', v: x => q(x.marketCap, x.revenue), unit: 'x', why: 'Useful for companies without profits yet, but ignores how profitable the sales are.' },
    { name: 'Free cash flow yield', f: 'Free cash flow ÷ market value', v: x => x.operatingCashFlow == null || x.capex == null ? null : q(x.operatingCashFlow - Math.abs(x.capex), x.marketCap), unit: 'pct', why: 'Spare cash per dollar of market value, a bit like an interest rate on owning the whole company.' },
  ]],
];

const CSS = `
#qra .lead{color:var(--mu);max-width:720px;line-height:1.55;margin:0 0 16px}
#qra .ld{display:flex;gap:8px;flex-wrap:wrap;align-items:center;margin-bottom:8px}#qra .ld input{font:inherit;font-size:15px;padding:9px 12px!important;width:160px;text-transform:uppercase}#qra .ld .btn{margin:0}
#qra .st{font-size:13px;color:var(--mu);min-height:1.2em;margin-bottom:14px}#qra .st.err{color:var(--dn)}
#qra .cols{display:grid;grid-template-columns:minmax(280px,380px) 1fr;gap:22px;align-items:start}
@media (max-width:860px){#qra .cols{grid-template-columns:1fr}}
#qra .qri{display:flex;flex-direction:column;gap:10px}
#qra .qri label{display:grid;grid-template-columns:1fr 150px;gap:2px 10px;align-items:center;font-size:13.5px}
#qra .qri label small{grid-column:1/-1;font-size:11.5px;color:var(--mu)}#qra .qri label small i{font-style:normal}
#qra .qri label small i.yh{color:#c9a7ff}#qra .qri label small i.sec{color:#4fd1a5}#qra .qri label small i.mkt{color:#8fb4ff}#qra .qri label small i.you{color:#e8c77a}
#qra .qri input{font:inherit;font-size:14px;padding:7px 9px!important;text-align:right;font-variant-numeric:tabular-nums;width:100%;box-sizing:border-box}
#qra .unit{font-size:12px;color:var(--mu);margin:-4px 0 4px}
#qra .grp h3{margin:4px 0 8px;font-size:16px}
#qra .rs{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:10px;margin-bottom:16px}
#qra .r{border:1px solid var(--ln);border-radius:12px;padding:12px 14px}
#qra .r .n{font-size:13px;color:var(--mu)}#qra .r .v{font-size:22px;font-weight:600;font-variant-numeric:tabular-nums;margin:2px 0}
#qra .r .f{font-size:12px;color:var(--mu)}#qra .r p{margin:6px 0 0;font-size:12.5px;line-height:1.5;color:var(--mu)}
#qra .note{font-size:12.5px;color:var(--mu);line-height:1.55;max-width:760px}`;

let vals: Record<string, number | null> = {}, src: Record<string, Src> = {}, period = '', tok = 0, ccy = 'USD', origin: '' | 'sec' | 'yahoo' = '';
const dirty = new Set<string>();
const money = (v: number) => (v < 0 ? '−' : '') + (ccy === 'USD' ? '$' : ccy + ' ') + (Math.abs(v) >= 1e12 ? fmt(Math.abs(v) / 1e12, 2) + 'T' : Math.abs(v) >= 1e9 ? fmt(Math.abs(v) / 1e9, 2) + 'B' : Math.abs(v) >= 1e6 ? fmt(Math.abs(v) / 1e6, 1) + 'M' : fmt(Math.abs(v), 0));
const show = (r: R, v: number | null) => v == null || !isFinite(v) ? '—' : r.unit === 'pct' ? fmt(v * 100, 1) + '%' : r.unit === 'x' ? fmt(v, 2) + '×' : money(v);
const toM = (v: number | null) => v == null ? '' : String(Math.round(v / 1e4) / 100); // inputs are in millions of USD

function paint(host: HTMLElement): void {
  const inp = host.querySelector('.qri') as HTMLElement;
  inp.innerHTML = `<div class="unit">All amounts in millions of ${ccy === 'USD' ? 'US dollars' : esc(ccy)}.</div>` + F.map(([k, l, h]) => `<label>${esc(l)}<input inputmode="decimal" data-k="${k}" value="${toM(vals[k] ?? null)}" aria-describedby="qra-${k}"><small id="qra-${k}">${esc(h)}${src[k] ? ` · <i class="${src[k].kind}">${esc(src[k].label)}</i>` : ''}</small></label>`).join('');
  inp.querySelectorAll<HTMLInputElement>('input').forEach(i => i.addEventListener('input', () => {
    const k = i.dataset.k!, t = i.value.replace(/[, $]/g, '');
    vals[k] = t === '' || isNaN(+t) ? null : +t * 1e6;
    src[k] = { label: 'You entered', kind: 'you' }; dirty.add(k);
    const s = i.parentElement!.querySelector('small')!; s.innerHTML = `${esc(F.find(f => f[0] === k)![2])} · <i class="you">You entered</i>`;
    results(host);
  }));
  results(host);
}

function results(host: HTMLElement): void {
  const out = host.querySelector('.qro') as HTMLElement;
  out.innerHTML = RATIOS.map(([g, rs]) => `<div class="grp"><h3>${g}</h3><div class="rs">${rs.map(r => { const v = r.v(vals); return `<div class="r"><div class="n">${esc(r.name)}</div><div class="v">${show(r, v)}</div><div class="f">${esc(r.f)}</div><p>${esc(r.why)}</p></div>`; }).join('')}</div></div>`).join('')
    + `<p class="note">${origin === 'sec' ? `Company figures are from the ${esc(period)} annual report filed with the SEC; market value` : ''}${origin === 'yahoo' ? `Company figures are Yahoo Finance's summary of the annual report for the ${esc(period)}, in ${esc(ccy)}. They haven't been checked against the filing itself, so confirm important numbers in the company's annual report. Market value` : ''}${period ? ` is the latest available and changes every trading day, so valuation ratios mix a past year's results with today's price. ` : ''}All ratios here are <b>calculated</b> from the inputs on the left. A dash means an input is missing or the ratio isn't meaningful (for example P/E when the company made a loss). Education, not investment advice. <button type="button" class="qfb-link" data-fb="data">Spot a wrong number?</button></p>`;
}

async function load(host: HTMLElement, sym: string): Promise<void> {
  const st = host.querySelector('.st') as HTMLElement;
  sym = sym.trim().toUpperCase();
  if (!/^[A-Z0-9]{1,6}([.\-][A-Z]{1,3})?$/.test(sym)) { st.className = 'st err'; st.textContent = 'Enter a stock ticker such as AAPL, SAP.DE or 7203.T, or type the numbers in yourself.'; return; }
  st.className = 'st'; st.textContent = `Loading ${sym}'s latest annual report…`;
  const t = ++tok; dirty.clear();
  if (location.hash !== '#/ratios/' + sym) history.replaceState(null, '', '#/ratios/' + sym);
  const [s, f] = await Promise.all([jf('/api/statements?symbol=' + encodeURIComponent(sym)), jf('/api/fundamentals?symbol=' + encodeURIComponent(sym))]);
  if (t !== tok || !host.isConnected) return;
  const keep = Object.fromEntries([...dirty].map(k => [k, vals[k]]));
  vals = {}; src = {}; period = ''; ccy = 'USD'; origin = '';
  if (s.ok && s.b?.annual?.periods?.length) {
    const A = s.b.annual, L = A.periods.length - 1, p = A.periods[L], lines = [...A.income, ...A.balance, ...A.cashflow];
    period = `${p.label} (${p.form || '10-K'}, year to ${p.end})`; origin = 'sec';
    for (const [k] of F) {
      if (k === 'marketCap') continue;
      let l = lines.find((x: any) => x.key === k); if (k === 'totalDebt' && (!l || l.values[L] == null)) l = lines.find((x: any) => x.key === 'longTermDebt');
      const v = l?.values?.[L]; if (v != null) { vals[k] = v; src[k] = { label: `SEC ${p.form || '10-K'} ${p.label}${k === 'totalDebt' && l.key === 'longTermDebt' ? ' (long-term only)' : ''}`, kind: 'sec' }; }
    }
    if (vals.grossProfit == null && vals.revenue != null) { const c = lines.find((x: any) => x.key === 'costOfRevenue')?.values?.[L]; if (c != null) { vals.grossProfit = vals.revenue - c; src.grossProfit = { label: `Calculated from SEC ${p.label}`, kind: 'sec' }; } }
  }
  // no SEC filing (most companies listed outside the US): Yahoo Finance's annual summary, in the company's reporting currency
  const ya = !origin && f.ok && Array.isArray(f.b?.annual) ? [...f.b.annual].filter((y: any) => y && y.date).sort((a: any, b: any) => String(a.date).localeCompare(String(b.date))).pop() : null;
  if (ya) {
    ccy = String(f.b.financialCurrency || f.b.currency || 'USD'); origin = 'yahoo'; period = `year to ${ya.date}`;
    const Y: Record<string, string> = { revenue: 'revenue', grossProfit: 'grossProfit', operatingIncome: 'operatingIncome', netIncome: 'netIncome', assets: 'assets', equity: 'equity', cash: 'cash', totalDebt: 'debt', operatingCashFlow: 'operatingCashFlow', capex: 'capex' };
    for (const [k, y] of Object.entries(Y)) { const v = ya[y]; if (typeof v === 'number' && isFinite(v)) { vals[k] = v; src[k] = { label: `Yahoo Finance, year to ${ya.date}`, kind: 'yh' }; } }
  }
  const cur = f.ok ? (f.b?.currency || 'USD') : '';
  if (f.ok && f.b?.key?.marketCap && cur === ccy) { vals.marketCap = f.b.key.marketCap; src.marketCap = { label: 'Latest, Yahoo Finance', kind: 'mkt' }; }
  for (const k of Object.keys(keep)) { vals[k] = keep[k]; src[k] = { label: 'You entered', kind: 'you' }; }
  const name = (s.ok && s.b?.name) || (f.ok && f.b?.name) || sym;
  if (!period) { st.className = 'st err'; st.textContent = `No annual figures found for ${sym} (funds and some banks aren't covered). You can still type the numbers in yourself.`; }
  else if (origin === 'yahoo') st.textContent = `${name}: no SEC filing, so figures come from Yahoo Finance (${period}, in ${ccy}). Current assets and liabilities aren't in this source; type them in for the current ratio.${vals.marketCap == null ? ' Market value is in a different currency or unavailable, so valuation ratios are blank.' : ''}`;
  else st.textContent = `${name}: figures from the ${period}.${s.b.financial ? ' This is a bank or insurer, so margins, debt and cash-flow ratios work differently and some are left out.' : ''}${vals.marketCap == null ? ' Market value unavailable, so valuation ratios are blank.' : ''}`;
  try { track('ratios_load'); } catch { /* optional */ }
  paint(host);
}

function page(arg?: string): void {
  const m = document.getElementById('m')!;
  m.innerHTML = `<div id="qra"><h2 hidden>Ratio calculator</h2><p class="lead">Work out a company's main financial ratios yourself. Load a US-listed company to fill the inputs from its latest annual report filed with the SEC, or type in your own numbers. Each result shows its formula and what it tells you.</p>
<form class="ld"><input aria-label="Ticker" placeholder="Ticker, e.g. AAPL" maxlength="7" autocomplete="off" spellcheck="false"><button class="btn p" type="submit">Load from SEC filings</button><button class="btn" type="button" data-clear>Clear</button></form>
<div class="st" role="status"></div><div class="cols"><div class="qri"></div><div class="qro"></div></div></div>`;
  const host = m.querySelector('#qra') as HTMLElement, box = host.querySelector('.ld input') as HTMLInputElement;
  host.querySelector('.ld')!.addEventListener('submit', e => { e.preventDefault(); load(host, box.value); });
  host.querySelector('[data-clear]')!.addEventListener('click', () => { vals = {}; src = {}; period = ''; ccy = 'USD'; origin = ''; box.value = ''; (host.querySelector('.st') as HTMLElement).textContent = ''; history.replaceState(null, '', '#/ratios'); paint(host); });
  vals = {}; src = {}; period = ''; ccy = 'USD'; origin = '';
  paint(host);
  const sym = (arg || '').split('/')[0];
  if (sym) { box.value = sym.toUpperCase(); load(host, sym); }
}

export function initRatios(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qra-css">${CSS}</style>`);
  PAGES.ratios = (a?: string) => { try { track('view_ratios'); } catch { /* optional */ } page(a); };
  TT.ratios = ['Ratio calculator', 'Work out the ratios yourself.'];
  if (SUBT.research && !SUBT.research.some(x => x[1] === 'ratios')) { const i = SUBT.research.findIndex(x => x[1] === 'compare'); SUBT.research.splice(i < 0 ? SUBT.research.length : i + 1, 0, ['Ratio calculator', 'ratios']); }
  (HUB as Record<string, string>).ratios = 'research';
}
