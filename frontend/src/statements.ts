// Full financial statements on the stock page's Financials tab, from the company's SEC filings (/api/statements):
// income statement, balance sheet, cash flow and ratios, 10 years or 12 quarters, each column linked to its filing.
// For US filers it replaces the short Yahoo-based table (the cards and chart above it stay). For companies without
// SEC statements (most non-US listings), nothing changes and the existing table remains.

interface Period { key: string; end: string; label: string; form?: string; filing?: string; derived?: string | null }
interface Line { key: string; label: string; kind: 'usd' | 'ps' | 'sh' | 'pct' | 'x'; total: boolean; values: (number | null)[]; note?: string | null }
interface Block { periods: Period[]; income: Line[]; balance: Line[]; cashflow: Line[]; ratios: Line[] }
interface St { symbol: string; name?: string; industry?: string; financial: boolean; annual: Block; quarterly: Block; source: string }

declare function isSimple(): boolean;

const TABS: [keyof Block, string][] = [['income', 'Income'], ['balance', 'Balance sheet'], ['cashflow', 'Cash flow'], ['ratios', 'Ratios']];
const KEY_LINES = new Set(['revenue', 'grossProfit', 'operatingIncome', 'netIncome', 'eps', 'netInterestIncome', 'cash', 'assets', 'liabilities', 'equity', 'longTermDebt', 'deposits', 'loans',
  'operatingCashFlow', 'capex', 'freeCashFlow', 'dividends', 'buybacks', 'grossMargin', 'operatingMargin', 'netMargin', 'roe', 'debtToEquity', 'fcfMargin']);
const PLAIN: Record<string, string> = {
  revenue: 'Total sales', grossProfit: 'Sales minus the direct cost of what was sold', operatingIncome: 'Profit from the main business, before interest and tax',
  netIncome: 'Profit after every cost and tax', eps: 'Net income per share', freeCashFlow: 'Cash left after running and investing in the business',
  equity: 'What would be left for shareholders if every debt were paid', roe: 'Profit earned on shareholders’ money', currentRatio: 'Can it pay bills due within a year? Above 1 means yes',
};

const CSS = `
#m.asp:not([data-tab=fin]) #qst{display:none}#fin.qsec>.pre,#fin.qsec>.ov{display:none}
#qst{margin-top:16px}#qst .qst-h{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}#qst h3{margin:0;display:flex;align-items:center;gap:10px;flex-wrap:wrap}
#qst .qst-src{font-size:12px;font-weight:500;padding:3px 10px;border-radius:99px;border:1px solid rgba(var(--upc),.4);color:var(--up)}
#qst .qst-bar{display:flex;justify-content:space-between;gap:10px;flex-wrap:wrap;margin:14px 0 10px}#qst .qst-bar .pre{margin:0}
#qst .qst-wrap{overflow-x:auto;-webkit-overflow-scrolling:touch;border:1px solid var(--ln);border-radius:12px}
#qst table{border-collapse:separate;border-spacing:0;width:100%;font-size:14px;font-variant-numeric:tabular-nums lining-nums}
#qst th,#qst td{padding:9px 12px;text-align:right;white-space:nowrap;border-bottom:1px solid var(--ln)}
#qst tr:last-child td{border-bottom:0}
#qst th{font-size:12px;font-weight:500;color:var(--mu);vertical-align:bottom;background:var(--s1)}
#qst th a{display:block;font-size:11px;color:var(--mu);text-decoration:none;margin-top:2px}#qst th a:hover{color:var(--ac)}
#qst th:first-child,#qst td:first-child{position:sticky;left:0;z-index:1;text-align:left;background:var(--s1);min-width:190px;white-space:normal}
#qst td:first-child small{display:block;font-size:11.5px;color:var(--mu);font-weight:400;line-height:1.35;margin-top:2px}
#qst tr.t td{font-weight:600}#qst tr.t td:first-child{color:var(--tx)}
#qst td.neg{color:var(--dn)}#qst td.na{color:var(--mu)}
#qst td{vertical-align:middle;background:transparent}#qst td.g{font-size:13px}#qst td.sp{min-width:96px}#qst td.sp svg{display:block;width:84px!important;height:24px!important;max-width:none!important;margin-left:auto}
#qst sup{color:var(--ac);font-size:10px;margin-left:2px}
#qst .qst-notes{margin:10px 0 0;padding-left:18px;font-size:12.5px;color:var(--mu);line-height:1.55}
#qst .qst-more{margin-top:10px}#qst .qst-warn{margin:0 0 10px;padding:10px 12px;border-radius:10px;border:1px solid rgba(var(--acc),.35);background:rgba(var(--acc),.06);font-size:13.5px;line-height:1.5}
#qst .qst-load{height:220px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite}
@media(max-width:640px){#qst th:first-child,#qst td:first-child{min-width:124px;max-width:140px;font-size:13px}#qst th,#qst td{padding:8px 9px}#qst td.sp{min-width:70px}#qst td.sp svg{width:64px!important}}
@media(prefers-reduced-motion:reduce){#qst .qst-load{animation:none}}`;

const cache = new Map<string, Promise<St | null>>();
const load = (s: string) => {
  if (!cache.has(s)) cache.set(s, jf('/api/statements?symbol=' + encodeURIComponent(s)).then(({ ok, b }) => ok && b && b.annual ? b as St : null).catch(() => null));
  return cache.get(s)!;
};

const big = (v: number): string => {
  const a = Math.abs(v), s = v < 0 ? '−' : '';
  return a >= 1e12 ? `${s}$${fmt(a / 1e12, 2)}T` : a >= 1e9 ? `${s}$${fmt(a / 1e9, a >= 1e11 ? 1 : 2)}B` : a >= 1e6 ? `${s}$${fmt(a / 1e6, 0)}M` : `${s}$${fmt(a, 0)}`;
};
function show(kind: Line['kind'], v: number | null): string {
  if (v == null) return '—';
  if (kind === 'usd') return big(v);
  if (kind === 'ps') return (v < 0 ? '−$' : '$') + fmt(Math.abs(v), 2);
  if (kind === 'sh') { const a = Math.abs(v); return a >= 1e9 ? fmt(a / 1e9, 2) + 'B' : fmt(a / 1e6, 0) + 'M'; }
  if (kind === 'pct') return (v < 0 ? '−' : '') + fmt(Math.abs(v * 100), 1) + '%';
  return fmt(v, 2) + '×';
}
/** Change from the comparable earlier period: the year before, or the same quarter a year earlier. */
function growth(l: Line, quarterly: boolean): string {
  const v = l.values, n = v.length, back = quarterly ? 4 : 1;
  const a = v[n - 1], b = n - 1 - back >= 0 ? v[n - 1 - back] : null;
  if (a == null || b == null) return '<td class="g na">—</td>';
  if (l.kind === 'pct') { const d = (a - b) * 100; return `<td class="g ${d >= 0 ? 'up' : 'dn'}">${d >= 0 ? '+' : '−'}${fmt(Math.abs(d), 1)} pts</td>`; }
  if (l.kind === 'x' || b <= 0 || a < 0) return '<td class="g na">—</td>'; // growth from a loss or to a loss isn't a meaningful %
  const g = (a / b - 1) * 100, good = l.key === 'shares' || OUTFLOW_NEG(l) ? g <= 0 : g >= 0;
  return `<td class="g ${good ? 'up' : 'dn'}">${g >= 0 ? '+' : '−'}${fmt(Math.abs(g), 1)}%</td>`;
}
const OUTFLOW_NEG = (l: Line) => ['costOfRevenue', 'rnd', 'sga', 'interestExpense', 'incomeTax', 'provision', 'liabilities', 'currentLiabilities', 'longTermDebt', 'payables'].includes(l.key);
function spark(vals: (number | null)[]): string {
  const pts = vals.map((v, i) => [i, v] as [number, number | null]).filter((p): p is [number, number] => p[1] != null);
  if (pts.length < 3) return '<td class="sp"></td>';
  const ys = pts.map(p => p[1]), lo = Math.min(...ys, 0), hi = Math.max(...ys), span = hi - lo || 1, n = vals.length - 1 || 1;
  const d = pts.map((p, i) => `${i ? 'L' : 'M'}${(p[0] / n * 80 + 2).toFixed(1)},${(22 - (p[1] - lo) / span * 20).toFixed(1)}`).join('');
  return `<td class="sp"><svg viewBox="0 0 84 24" width="84" height="24" preserveAspectRatio="none" aria-hidden="true"><path d="${d}" fill="none" stroke="var(--ac)" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"/></svg></td>`;
}

interface View { tab: keyof Block; q: boolean; all: boolean }
const view: View = { tab: 'income', q: false, all: false };

function render(host: HTMLElement, st: St): void {
  const B = view.q ? st.quarterly : st.annual;
  const simple = typeof isSimple === 'function' && isSimple() && !view.all;
  let lines = (B[view.tab] as Line[]) || [];
  // quarterly filings often give some lines only as year-to-date totals; a row that's mostly blank is noise
  const sparse = view.q ? lines.filter(l => l.values.filter(v => v != null).length < B.periods.length * 0.4) : [];
  if (sparse.length) lines = lines.filter(l => !sparse.includes(l));
  const hidden = simple ? lines.filter(l => !KEY_LINES.has(l.key)).length : 0;
  if (simple) lines = lines.filter(l => KEY_LINES.has(l.key));
  const P = B.periods;
  const notes: string[] = [];
  const mark = (l: Line) => { if (!l.note) return ''; let i = notes.indexOf(l.note); if (i < 0) { notes.push(l.note); i = notes.length - 1; } return `<sup>${i + 1}</sup>`; };
  const dMark = (p: Period) => { if (!p.derived) return ''; let i = notes.indexOf(p.derived); if (i < 0) { notes.push(p.derived); i = notes.length - 1; } return `<sup>${i + 1}</sup>`; };
  const head = `<tr><th>${view.q ? 'Quarter ending' : 'Fiscal year'}</th>${P.map(p => `<th>${esc(p.label)}${dMark(p)}${p.filing ? `<a href="${esc(p.filing)}" target="_blank" rel="noopener noreferrer" title="Open the ${esc(p.form || 'filing')} at sec.gov">${esc(p.form || 'Filing')} ↗</a>` : ''}</th>`).join('')}<th>${view.q ? 'vs year ago' : 'vs prior year'}</th><th>Trend</th></tr>`;
  const body = lines.map(l => `<tr class="${l.total ? 't' : ''}"><td>${esc(l.label)}${mark(l)}${simple && PLAIN[l.key] ? `<small>${esc(PLAIN[l.key])}</small>` : ''}</td>${l.values.map(v => `<td class="${v == null ? 'na' : v < 0 && l.kind !== 'usd' ? 'neg' : ''}">${show(l.kind, v)}</td>`).join('')}${growth(l, view.q)}${spark(l.values)}</tr>`).join('');
  const empty = !lines.length ? `<tr><td colspan="${P.length + 3}" style="text-align:left;padding:18px 14px" class="mu">${view.q && view.tab === 'cashflow' ? 'Most companies report cash flow only as year-to-date totals in their quarterly filings, so there are no separate quarterly figures to show. Switch to Annual.' : 'This company doesn’t report these lines in a form we can read.'}</td></tr>` : '';
  host.innerHTML = `<div class="qst-h"><h3>Financial statements <span class="qst-src" title="Straight from the company's filings with the US Securities and Exchange Commission">From SEC filings</span></h3>
    <span class="mu" style="font-size:12.5px">${P.length} ${view.q ? 'quarters' : 'years'} · USD · each column links to its filing</span></div>
    ${st.financial ? `<div class="qst-warn" style="margin-top:12px">🏦 <b>${esc(st.name || st.symbol)} files as a financial company</b>${st.industry ? ` (SEC industry: ${esc(st.industry)})` : ''}. Banks and insurers report differently: there’s no gross profit or current assets, and free cash flow and the current ratio are left out because lending and deposits make them misleading.</div>` : ''}
    <div class="qst-bar"><div class="pre" role="tablist">${TABS.map(([k, n]) => `<button class="chip ${view.tab === k ? 'on' : ''}" data-t="${k}" role="tab" aria-selected="${view.tab === k}">${n}</button>`).join('')}</div>
    <div class="pre"><button class="chip ${view.q ? '' : 'on'}" data-q="0">Annual</button><button class="chip ${view.q ? 'on' : ''}" data-q="1">Quarterly</button></div></div>
    ${P.length ? `<div class="qst-wrap"><table>${head}${body}${empty}</table></div>` : '<div class="mu" style="padding:12px 0">No quarterly statements found.</div>'}
    ${sparse.length ? `<div class="mu" style="font-size:12.5px;margin-top:8px">${sparse.length} line${sparse.length > 1 ? 's' : ''} hidden in the quarterly view (${sparse.map(l => esc(l.label.toLowerCase())).join(', ')}): companies mostly report ${sparse.length > 1 ? 'them' : 'it'} only as year-to-date totals. See Annual.</div>` : ''}
    ${hidden ? `<button class="chip qst-more" data-all="1">Show all ${hidden + lines.length} lines</button>` : ''}
    ${notes.length ? `<ol class="qst-notes">${notes.map(n => `<li>${esc(n)}</li>`).join('')}</ol>` : ''}
    <div class="wsn" style="margin-top:10px">Source: ${esc(st.source)}, as reported. Where a company later revised a figure, the latest filing’s number is used. Blank means not reported in a form we can read. Not investment advice.</div>`;
  host.querySelectorAll<HTMLButtonElement>('[data-t]').forEach(b => b.onclick = () => { view.tab = b.dataset.t as keyof Block; render(host, st); track('st_tab_' + view.tab); });
  host.querySelectorAll<HTMLButtonElement>('[data-q]').forEach(b => b.onclick = () => { view.q = b.dataset.q === '1'; render(host, st); track('st_q'); });
  const more = host.querySelector<HTMLButtonElement>('[data-all]'); if (more) more.onclick = () => { view.all = true; render(host, st); };
  // newest period on the right, scrolled into view next to the labels (on phones the table is wider than the screen)
  const w = host.querySelector<HTMLElement>('.qst-wrap'), ths = host.querySelectorAll<HTMLElement>('th');
  if (w && ths.length > 3) { const last = ths[ths.length - 3]; w.scrollLeft = w.clientWidth >= 700 ? w.scrollWidth : Math.max(0, last.offsetLeft + last.offsetWidth - w.clientWidth); }
}

let busy = false;
function attach(): void {
  const m = /^#\/asset\/([^/]+)/.exec(location.hash);
  const fin = document.getElementById('fin');
  if (!m || !fin || busy) return;
  const sym = decodeURIComponent(m[1]).toUpperCase();
  const have = document.getElementById('qst');
  if (have && have.dataset.s === sym) return;
  have?.remove();
  const host = document.createElement('div');
  host.id = 'qst'; host.className = 'pan'; host.dataset.s = sym;
  host.innerHTML = '<h3 style="margin:0 0 12px">Financial statements</h3><div class="qst-load"></div>';
  fin.insertAdjacentElement('afterend', host);
  busy = true;
  load(sym).then(st => {
    busy = false;
    if (!host.isConnected) return;
    if (!st || (!st.annual.periods.length && !st.quarterly.periods.length)) { host.remove(); document.getElementById('fin')?.classList.remove('qsec'); return; }
    if (!st.annual.periods.length) view.q = true;
    document.getElementById('fin')?.classList.add('qsec');
    render(host, st);
  });
}

export function initStatements(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qst-css">${CSS}</style>`);
  const main = document.getElementById('m');
  if (!main) return;
  let t: number | undefined;
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(() => { attach(); const f = document.getElementById('fin'); if (f && document.getElementById('qst')?.querySelector('table')) f.classList.add('qsec'); }, 80); })
    .observe(main, { childList: true, subtree: true });
  attach();
}
