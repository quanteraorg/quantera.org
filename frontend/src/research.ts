// Company research as one connected workspace, and visible sources for every figure.
//
// 1. Sources: under a stock's key numbers, a line says where each group of figures comes from and how fresh it is
//    (price provider and time, fundamentals provider and fiscal period, analyst count).
// 2. Methods: every key ratio has an ⓘ that explains how it's calculated, over what period, from which source,
//    with a link to the Academy lesson that teaches it.
// 3. A Research tab on each stock page joins the company's competitors (side by side), a valuation snapshot,
//    a five-year financial snapshot from SEC filings, and the user's own thesis (bull case, bear case, assumptions).

import { dcf, impliedGrowth, startingInputs, type St } from './valuation';

declare const KTB: [string, string][];

interface Peer { symbol: string; name?: string; currency?: string; price?: number | null; marketCap?: number | null; pe?: number | null; forwardPe?: number | null; evEbitda?: number | null; revenueGrowth?: number | null; grossMargin?: number | null; operatingMargin?: number | null; profitMargin?: number | null; roe?: number | null; debtToEquity?: number | null; latestQuarter?: string }

// ---------- methods: how each figure is worked out ----------
const SRC_F = 'Yahoo Finance, trailing 12 months';
const METHODS: Record<string, { f: string; p: string; l?: string }> = {
  'Company value': { f: 'Share price × shares outstanding (market capitalisation).', p: 'Live price × latest share count. ' + 'Yahoo Finance.' },
  'Market cap': { f: 'Share price × shares outstanding.', p: 'Live price × latest share count. Yahoo Finance.' },
  'Price vs profit': { f: 'P/E = share price ÷ earnings per share over the last 12 months. Forward P/E uses analysts’ forecast EPS for the next 12 months. PEG = P/E ÷ expected yearly earnings growth (%).', p: SRC_F + '; forward figures are analyst forecasts, not results.', l: 'pe' },
  'P/E': { f: 'Share price ÷ earnings per share over the last 12 months.', p: SRC_F + '.', l: 'pe' },
  'Forward P/E': { f: 'Share price ÷ analysts’ forecast EPS for the next 12 months.', p: 'Yahoo Finance; a forecast, not a result.', l: 'pe' },
  'PEG': { f: 'P/E ÷ expected yearly earnings growth (%). Around 1 is often read as growth roughly matching the price; it depends on the growth forecast.', p: 'Yahoo Finance; uses analyst growth forecasts.', l: 'peg' },
  'Price / sales': { f: 'Market value ÷ revenue over the last 12 months.', p: SRC_F + '.', l: 'ratios' },
  'Price / book': { f: 'Share price ÷ shareholders’ equity per share (book value). High for companies that buy back a lot of stock.', p: 'Yahoo Finance, latest balance sheet.', l: 'ratios' },
  'EV / EBITDA': { f: 'Enterprise value (market value + debt − cash) ÷ earnings before interest, tax, depreciation and amortisation.', p: SRC_F + '.', l: 'ratios' },
  'Sales growth': { f: 'Revenue in the latest quarter compared with the same quarter a year earlier.', p: 'Yahoo Finance, latest reported quarter.', l: 'income' },
  'Revenue growth': { f: 'Revenue in the latest quarter compared with the same quarter a year earlier.', p: 'Yahoo Finance, latest reported quarter.', l: 'income' },
  'Earnings growth': { f: 'Earnings in the latest quarter compared with the same quarter a year earlier.', p: 'Yahoo Finance, latest reported quarter.', l: 'income' },
  'EPS (past 12 months)': { f: 'Net income available to shareholders ÷ diluted shares, over the last four quarters.', p: SRC_F + '.', l: 'income' },
  'EPS (forecast)': { f: 'Analysts’ average forecast of earnings per share for the next 12 months.', p: 'Yahoo Finance; a forecast, not a result.', l: 'pe' },
  'Profit margin': { f: 'Net income ÷ revenue: how much of each dollar of sales is left as profit after every cost and tax.', p: SRC_F + '.', l: 'income' },
  'Gross margin': { f: '(Revenue − cost of revenue) ÷ revenue.', p: SRC_F + '.', l: 'income' },
  'Operating margin': { f: 'Operating income ÷ revenue: profit from the main business before interest and tax.', p: SRC_F + '.', l: 'income' },
  'Return on equity': { f: 'Net income ÷ shareholders’ equity. Very high values often mean equity is small after buybacks, not unusual profitability.', p: SRC_F + '.', l: 'ratios' },
  'Return on assets': { f: 'Net income ÷ total assets.', p: SRC_F + '.', l: 'ratios' },
  'Net debt': { f: 'Total debt − cash and equivalents. Negative means more cash than debt.', p: 'Yahoo Finance, latest balance sheet.', l: 'ratios' },
  'Cash': { f: 'Cash, cash equivalents and short-term investments.', p: 'Yahoo Finance, latest balance sheet.' },
  'Debt': { f: 'Total debt: short- and long-term borrowings.', p: 'Yahoo Finance, latest balance sheet.' },
  'Debt / equity': { f: 'Total debt ÷ shareholders’ equity.', p: 'Yahoo Finance, latest balance sheet.', l: 'ratios' },
  'Current ratio': { f: 'Current assets ÷ current liabilities: can the company pay what it owes within a year? Not meaningful for banks.', p: 'Yahoo Finance, latest balance sheet.', l: 'ratios' },
  'Free cash flow': { f: 'Cash from operations − capital spending.', p: SRC_F + '.', l: 'cashflow' },
  'Dividend yield': { f: 'Yearly dividends per share ÷ share price.', p: 'Yahoo Finance, indicated yearly dividend.', l: 'dividend' },
  'Payout ratio': { f: 'Dividends ÷ net income: the share of profit paid out.', p: SRC_F + '.', l: 'dividend' },
  'Analyst target': { f: 'The average 12-month price target of the analysts covering the stock. A forecast, not a fact; targets vary widely.', p: 'Yahoo Finance; the number of analysts is shown beside it.' },
};

const CSS = `
.qinfo{display:inline-grid;place-items:center;width:17px;height:17px;margin-left:5px;border-radius:50%;border:1px solid rgba(var(--fgc),.25);background:none;color:var(--mu);font:600 10.5px/1 inherit;cursor:pointer;vertical-align:1px;padding:0}
.qinfo:hover,.qinfo[aria-expanded=true]{border-color:var(--ac);color:var(--ac)}
.qmeth{position:fixed;z-index:130;width:min(320px,calc(100vw - 24px));padding:12px 14px;border-radius:14px;font-size:13px;line-height:1.5;color:var(--tx);
 background:rgba(var(--bgc),.92);-webkit-backdrop-filter:saturate(180%) blur(20px);backdrop-filter:saturate(180%) blur(20px);border:1px solid rgba(var(--fgc),.12);box-shadow:0 18px 44px -14px rgba(var(--shc),.55);animation:qglass .32s cubic-bezier(.3,1.3,.5,1) both}
.qmeth b{display:block;margin-bottom:4px}.qmeth small{display:block;color:var(--mu);margin-top:6px}.qmeth a{color:var(--ac);display:inline-block;margin-top:6px}
@media(prefers-reduced-transparency:reduce){.qmeth{-webkit-backdrop-filter:none;backdrop-filter:none;background:rgb(var(--bgc))}}
.qsrc{display:flex;flex-wrap:wrap;gap:4px 14px;margin:10px 0 2px;font-size:12.5px;color:var(--mu);line-height:1.5}.qsrc b{color:var(--tx);font-weight:500}.qsrc span{white-space:nowrap}
#m.asp:not([data-tab=research]) #qres{display:none}
#m.asp[data-tab=research] :is(.g[style*="2fr 1fr"],#sc,#fna,#fin,#fpic,#fany,#erx,#rnb,#secp,#qst),#m.asp[data-tab=research]>div>.g.g2{display:none!important}
#qres{margin-top:16px}#qres .qr-jump{display:flex;gap:8px;flex-wrap:wrap;margin-bottom:14px}
#qres .qr-g{display:grid;grid-template-columns:minmax(0,1.15fr) minmax(0,1fr);gap:14px;align-items:start}@media(max-width:900px){#qres .qr-g{grid-template-columns:minmax(0,1fr)}}
#qres h3{margin:0 0 4px;font-size:17px}#qres .qr-sub{color:var(--mu);font-size:13px;margin:0 0 12px;line-height:1.5}
#qres label{display:block;font-size:13px;font-weight:500;margin:12px 0 4px}#qres label:first-of-type{margin-top:0}
#qres textarea{width:100%;min-height:62px;resize:vertical;font:inherit;font-size:14.5px;line-height:1.55;padding:10px 12px!important;box-sizing:border-box}
#qres .qr-saved{font-size:12px;color:var(--mu);margin-top:8px}
#qres table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums lining-nums}
#qres th,#qres td{padding:8px 10px;border-bottom:1px solid var(--ln);text-align:right;white-space:nowrap}#qres th{font-size:12px;color:var(--mu);font-weight:500}
#qres th:first-child,#qres td:first-child{text-align:left;position:sticky;left:0;background:var(--s1);z-index:1}
#qres tr.me td{font-weight:600;background:rgba(var(--acc),.07)}#qres tr.me td:first-child{background:color-mix(in srgb,var(--s1) 88%,rgb(var(--acc)))}
#qres tr.med td{color:var(--mu);font-style:italic}
#qres .ov{overflow-x:auto;border:1px solid var(--ln);border-radius:12px}
#qres .qr-big{font-size:30px;font-weight:600;font-variant-numeric:tabular-nums;letter-spacing:-.02em}
#qres .qr-kv{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:8px;margin-top:10px}#qres .qr-kv div{border:1px solid var(--ln);border-radius:10px;padding:8px 10px}
#qres .qr-kv span{display:block;font-size:12px;color:var(--mu)}#qres .qr-kv b{font-variant-numeric:tabular-nums}
#qres .qr-note{font-size:12.5px;color:var(--mu);line-height:1.55;margin-top:10px}
#qres .qr-load{height:120px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite}
html[data-theme=light] .ntag{color:color-mix(in srgb,var(--c) 55%,#000)} /* topic tags: the pale tag colours need darker text on light backgrounds */
@media(prefers-reduced-motion:reduce){#qres .qr-load{animation:none}.qmeth{animation:none}}`;

// ---------- formatting ----------
const money = (v: number | null | undefined) => { if (v == null || !isFinite(v)) return '—'; const a = Math.abs(v), s = v < 0 ? '−' : ''; return a >= 1e12 ? `${s}$${fmt(a / 1e12, 2)}T` : a >= 1e9 ? `${s}$${fmt(a / 1e9, 1)}B` : a >= 1e6 ? `${s}$${fmt(a / 1e6, 0)}M` : `${s}$${fmt(a, 2)}`; };
const pc = (v: number | null | undefined, dp = 1) => v == null || !isFinite(v) ? '—' : `${v < 0 ? '−' : ''}${fmt(Math.abs(v * 100), dp)}%`;
const x1 = (v: number | null | undefined) => v == null || !isFinite(v) || v <= 0 ? '—' : fmt(v, 1);
const when = (iso?: string | null) => { if (!iso) return null; const d = new Date(iso.length === 10 ? iso + 'T12:00:00Z' : iso); return isNaN(+d) ? null : d; };
const dayStr = (d: Date) => d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
const timeStr = (d: Date) => d.toLocaleString('en-US', { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit', timeZoneName: 'short' });
const sym = () => { const m = /^#\/asset\/([^/]+)/.exec(location.hash); return m ? decodeURIComponent(m[1]).toUpperCase() : null; };

const cache = new Map<string, Promise<any>>();
const get = (url: string) => { if (!cache.has(url)) cache.set(url, jf(url).then(r => r.ok ? r.b : null).catch(() => null)); return cache.get(url)!; };

// ---------- 1. sources under the key numbers ----------
function sources(s: string): void {
  const kn = document.querySelector<HTMLElement>('#m .kn');
  if (!kn || document.getElementById('qsrc')) return;
  const host = document.createElement('div'); host.id = 'qsrc'; host.className = 'qsrc'; host.dataset.s = s;
  host.innerHTML = '<span>Sources loading…</span>';
  (document.querySelector('#m .knr') || kn).insertAdjacentElement('afterend', host);
  Promise.all([get('/api/quote?symbol=' + encodeURIComponent(s)), get('/api/fundamentals?symbol=' + encodeURIComponent(s))]).then(([q, f]) => {
    if (!host.isConnected) return;
    const parts: string[] = [];
    if (q?.price != null) {
      const t = when(q.time) || when(q.asOf);
      parts.push(`<span><b>Price:</b> ${esc(q.source || 'market data')}${t ? ' · ' + (q.time ? timeStr(t) : dayStr(t)) : ''} · may be delayed up to 15 min</span>`);
    }
    if (f?.key) {
      const lq = when(f.latestQuarter);
      parts.push(`<span><b>Key figures:</b> ${esc(f.source || 'Yahoo Finance')} · last 12 months${lq ? ' to ' + dayStr(lq) : ''}</span>`);
      if (f.analysts?.count) parts.push(`<span><b>Analyst target:</b> average of ${fmt(f.analysts.count, 0)} analysts · forecast, not fact</span>`);
    }
    parts.push('<span>Tap ⓘ on a figure to see how it’s calculated</span>');
    host.innerHTML = parts.join('');
  });
}

// ---------- 2. how each figure is calculated ----------
let pop: HTMLElement | null = null, popBtn: HTMLButtonElement | null = null;
function closePop() { pop?.remove(); pop = null; if (popBtn) { popBtn.setAttribute('aria-expanded', 'false'); popBtn = null; } }
function openPop(btn: HTMLButtonElement, key: string) {
  const m = METHODS[key]; if (!m) return;
  if (popBtn === btn) { closePop(); return; }
  closePop();
  pop = document.createElement('div'); pop.className = 'qmeth'; pop.id = 'qmeth'; pop.setAttribute('role', 'dialog'); pop.setAttribute('aria-label', 'How ' + key + ' is calculated');
  pop.innerHTML = `<b>${esc(key)}</b>${esc(m.f)}<small>Source and period: ${esc(m.p)}</small>${m.l ? `<a href="#/lesson/${m.l}">Learn it in the Academy →</a>` : ''}`;
  document.body.appendChild(pop);
  const r = btn.getBoundingClientRect(), w = pop.offsetWidth, h = pop.offsetHeight;
  pop.style.left = Math.max(12, Math.min(innerWidth - w - 12, r.left - 20)) + 'px';
  pop.style.top = (r.bottom + 8 + h > innerHeight ? Math.max(12, r.top - h - 8) : r.bottom + 8) + 'px';
  btn.setAttribute('aria-expanded', 'true'); popBtn = btn;
}
function methods(): void {
  document.querySelectorAll<HTMLElement>('#m .kn .kl, #m #fpic .fr > span:first-child').forEach(el => {
    if (el.dataset.qm) return;
    const key = (el.textContent || '').trim();
    if (!METHODS[key]) return;
    el.dataset.qm = '1';
    const b = document.createElement('button'); b.type = 'button'; b.className = 'qinfo'; b.textContent = 'i';
    b.setAttribute('aria-label', 'How ' + key + ' is calculated'); b.setAttribute('aria-expanded', 'false');
    b.onclick = e => { e.stopPropagation(); openPop(b, key); };
    el.appendChild(b);
  });
}

// ---------- 3. the Research tab ----------
interface Thesis { line?: string; bull?: string; bear?: string; assume?: string; change?: string; at?: number }
const TKEY = 'q_thesis';
const loadT = (s: string): Thesis => (LS<Record<string, Thesis>>(TKEY, {}))[s] || {};
const saveT = (s: string, t: Thesis) => { const all = LS<Record<string, Thesis>>(TKEY, {}); all[s] = { ...t, at: Date.now() }; SS(TKEY, all); };

const median = (a: (number | null | undefined)[]) => { const v = a.filter((x): x is number => x != null && isFinite(x)).sort((p, q) => p - q); if (!v.length) return null; const m = v.length >> 1; return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };

function research(s: string): void {
  const tabs = document.querySelector('#m .ktabs');
  if (!tabs) return;
  let host = document.getElementById('qres');
  if (host && host.dataset.s === s) return;
  host?.remove();
  host = document.createElement('div'); host.id = 'qres'; host.dataset.s = s;
  tabs.insertAdjacentElement('afterend', host);
  const t = loadT(s);
  const folders = (LS<any[]>('q_col', [])).filter(f => (f.items || []).some((i: any) => i.t === 's' && i.sym === s));
  host.innerHTML = `<div class="qr-jump">
      <button class="chip" data-go="fin">Financial statements</button>
      <a class="chip" href="#/valuation/${encodeURIComponent(s)}">Valuation model</a>
      <button class="chip" data-go="overview">Chart</button>
      <button class="chip" id="qrcmp">Compare with competitors</button>
      <a class="chip" href="#/trade/${encodeURIComponent(s)}">Practice trade</a>
      <a class="chip" href="#/lesson/research">Learn: how to research a company</a></div>
    <div class="qr-g">
      <div class="pan" id="qrth"><h3>My research</h3><p class="qr-sub">Your own thinking about ${esc(s)}, saved automatically in this browser (and synced when you're signed in). ${folders.length ? 'In folders: ' + folders.map(f => `<a href="#/notes">${esc(f.emoji || '📁')} ${esc(f.name)}</a>`).join(', ') + '.' : '<a href="#/notes">Add it to a research folder →</a>'}</p>
        ${[['line', 'My thesis in one line', 'e.g. Services growth keeps margins rising even if iPhone sales are flat'], ['bull', 'Bull case: what goes right', ''], ['bear', 'Bear case: what goes wrong', ''], ['assume', 'Key assumptions I’m making', 'e.g. revenue grows 6% a year; margins stay above 45%'], ['change', 'What would change my mind', '']].map(([k, l, ph]) => `<label for="qr-${k}">${l}</label><textarea id="qr-${k}" data-k="${k}" placeholder="${esc(ph)}">${esc((t as any)[k] || '')}</textarea>`).join('')}
        <div class="qr-saved" id="qrsv">${t.at ? 'Last saved ' + timeStr(new Date(t.at)) : 'Not written yet'}</div></div>
      <div><div class="pan" id="qrval"><h3>Valuation snapshot</h3><div class="qr-load"></div></div>
        <div class="pan" id="qrfin" style="margin-top:14px"><h3>Five years at a glance</h3><div class="qr-load"></div></div></div>
    </div>
    <div class="pan" id="qrpeer" style="margin-top:14px"><h3>Competitors side by side</h3><div class="qr-load"></div></div>`;
  host.querySelectorAll<HTMLButtonElement>('[data-go]').forEach(b => b.onclick = () => { (window as any).KTGO?.(b.dataset.go, 1); });
  let tm: number | undefined;
  host.querySelectorAll<HTMLTextAreaElement>('textarea[data-k]').forEach(a => a.oninput = () => {
    clearTimeout(tm);
    tm = window.setTimeout(() => { const cur: Thesis = {}; host!.querySelectorAll<HTMLTextAreaElement>('textarea[data-k]').forEach(x => { (cur as any)[x.dataset.k!] = x.value.slice(0, 4000); }); saveT(s, cur); const sv = document.getElementById('qrsv'); if (sv) sv.textContent = 'Saved ' + timeStr(new Date()); track('thesis_save'); }, 500);
  });
  (host.querySelector('#qrcmp') as HTMLButtonElement).onclick = () => { get('/api/peers?symbol=' + encodeURIComponent(s)).then(p => { const list = [s, ...((p?.peers || []) as Peer[]).filter(x => x.symbol !== s).map(x => x.symbol)].slice(0, 5); SS('q_cmp', list); go('#/compare'); }); };
  loadRest(s);
}

function loadRest(s: string): void {
  const st = get('/api/statements?symbol=' + encodeURIComponent(s)), q = get('/api/quote?symbol=' + encodeURIComponent(s));
  Promise.all([st, q]).then(([S, Q]: [St | null, any]) => {
    const val = document.getElementById('qrval'), fin = document.getElementById('qrfin');
    if (!val || !fin) return;
    const link = `<a class="chip" href="#/valuation/${encodeURIComponent(s)}" style="display:inline-block;margin-top:10px">Open the full model →</a>`;
    if (!S) { val.innerHTML = '<h3>Valuation snapshot</h3><p class="qr-note">Needs SEC filings, so it works for US-listed companies that file annual reports (10-K).</p>'; fin.innerHTML = '<h3>Five years at a glance</h3><p class="qr-note">No SEC statements for this company. See the Financials tab for what’s available.</p>'; return; }
    const price = Q?.price != null ? Number(Q.price) : NaN, si = startingInputs(S, price);
    if (S.financial) val.innerHTML = `<h3>Valuation snapshot</h3><p class="qr-note">Banks and insurers aren’t valued from free cash flow; compare their price-to-book and return on equity with the competitors below instead.</p>`;
    else if (!si) val.innerHTML = `<h3>Valuation snapshot</h3><p class="qr-note">The filings don’t include the figures the model needs, so nothing is estimated.</p>${link}`;
    else {
      const r = dcf(si.x), ig = impliedGrowth(si.x), up = r.ps / price - 1;
      val.innerHTML = `<h3>Valuation snapshot</h3><p class="qr-sub">A discounted cash flow model on the company’s own filed figures, with standard starting assumptions you can change in the full model.</p>
        <div class="qr-big">$${fmt(r.ps, 2)} <span style="font-size:14px;font-weight:500" class="${up >= 0 ? 'up' : 'dn'}">${up >= 0 ? '+' : '−'}${fmt(Math.abs(up * 100), 0)}% vs $${fmt(price, 2)}</span></div>
        <div class="qr-kv"><div><span>Free cash flow (latest year)</span><b>${money(si.x.fcf * 1e9)}</b></div><div><span>Growth assumed, years 1–5</span><b>${fmt(si.x.g, 1)}%</b></div><div><span>Discount rate</span><b>${fmt(si.x.d, 1)}%</b></div><div><span>Growth today’s price implies</span><b>${ig == null ? '—' : fmt(ig, 1) + '%'}</b></div></div>
        <p class="qr-note">What these assumptions imply, not a forecast. Growth starts from the company’s ${si.cagr == null ? 'typical' : fmt(si.cagr, 1) + '%'} yearly revenue growth${si.cagr != null && si.cagr > 20 ? ', capped at 20%' : ''}.</p>${link}`;
    }
    // five-year snapshot from the filings
    const P = S.annual.periods.slice(-5), n = S.annual.periods.length;
    const line = (arr: any[], k: string) => { const l = arr.find((x: any) => x.key === k); return l ? l.values.slice(n - P.length) as (number | null)[] : null; };
    const rev = line(S.annual.income, 'revenue'), ni = line(S.annual.income, 'netIncome'), fcf = line(S.annual.cashflow, 'freeCashFlow');
    const rows: [string, (number | null)[] | null, (v: number | null) => string][] = [['Revenue', rev, money], ['Net income', ni, money], ['Net margin', rev && ni ? rev.map((r, i) => r && ni[i] != null ? ni[i]! / r : null) : null, v => pc(v)], ['Free cash flow', fcf, money]];
    fin.innerHTML = `<h3>Five years at a glance</h3><div class="ov" style="margin-top:8px"><table><tr><th>Fiscal year</th>${P.map(p => `<th>${p.filing ? `<a href="${esc(p.filing)}" target="_blank" rel="noopener noreferrer">${esc(p.label)}</a>` : esc(p.label)}</th>`).join('')}</tr>
      ${rows.filter(r => r[1]).map(([l, v, f]) => `<tr><td>${l}</td>${v!.map(x => `<td>${f(x)}</td>`).join('')}</tr>`).join('')}</table></div>
      <p class="qr-note">From SEC filings; each year links to its annual report. <button class="chip" data-go2="fin" style="margin-left:4px">All statements →</button></p>`;
    fin.querySelector<HTMLButtonElement>('[data-go2]')!.onclick = () => (window as any).KTGO?.('fin', 1);
  });
  get('/api/peers?symbol=' + encodeURIComponent(s)).then(p => {
    const box = document.getElementById('qrpeer'); if (!box) return;
    const peers: Peer[] = p?.peers || [];
    if (peers.length < 2) { box.innerHTML = '<h3>Competitors side by side</h3><p class="qr-note">No competitor list is available for this company right now.</p>'; return; }
    const cols: [keyof Peer, string, (v: any) => string][] = [['marketCap', 'Company value', money], ['pe', 'P/E', x1], ['forwardPe', 'Forward P/E', x1], ['evEbitda', 'EV / EBITDA', x1], ['revenueGrowth', 'Revenue growth', v => pc(v)], ['grossMargin', 'Gross margin', v => pc(v)], ['operatingMargin', 'Operating margin', v => pc(v)], ['profitMargin', 'Profit margin', v => pc(v)], ['roe', 'Return on equity', v => pc(v, 0)]];
    const others = peers.filter(x => x.symbol !== s);
    const t = when(p.asOf);
    box.innerHTML = `<h3>Competitors side by side</h3><p class="qr-sub">${esc(s)} against ${others.length} companies in the same industry. The median row is the middle value of the competitors (not counting ${esc(s)}). Click a company to research it.</p>
      <div class="ov"><table><tr><th>Company</th>${cols.map(c => `<th>${c[1]}</th>`).join('')}</tr>
      ${peers.map(r => `<tr class="${r.symbol === s ? 'me' : ''}"><td><a href="#/asset/${encodeURIComponent(r.symbol)}/research">${esc(r.symbol)}</a> <span class="mu" style="font-weight:400">${esc((r.name || '').slice(0, 22))}</span></td>${cols.map(c => `<td>${c[2](r[c[0]] as any)}</td>`).join('')}</tr>`).join('')}
      <tr class="med"><td>Competitor median</td>${cols.map(c => `<td>${c[2](median(others.map(o => o[c[0]] as any)) as any)}</td>`).join('')}</tr></table></div>
      <p class="qr-note">${esc(p.source || '')}${t ? ' Loaded ' + timeStr(t) + '.' : ''} Companies report in different currencies and fiscal years; ratios are the most comparable figures. A competitor whose figures couldn’t be loaded is left out rather than estimated. Tap ⓘ next to a ratio on the Overview tab to see how it’s calculated.</p>`;
  });
}

export function initResearch(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qres-css">${CSS}</style>`);
  if (typeof KTB !== 'undefined' && !KTB.some(t => t[0] === 'research')) KTB.splice(Math.max(0, KTB.findIndex(t => t[0] === 'notes')), 0, ['research', 'Research']);
  if (!SYNC.includes(TKEY)) SYNC.push(TKEY);
  addEventListener('click', e => { if (pop && !(e.target as Element).closest('.qmeth')) closePop(); });
  addEventListener('keydown', e => { if (e.key === 'Escape' && pop) { const b = popBtn; closePop(); b?.focus(); } });
  addEventListener('hashchange', closePop);
  addEventListener('scroll', closePop, { passive: true });
  const main = document.getElementById('m'); if (!main) return;
  let t: number | undefined;
  const run = () => {
    const s = sym(); if (!s) return;
    methods();
    sources(s);
    // the tab bar may have been drawn before this module loaded: add the Research tab if it's missing
    const bar = main.querySelector('.ktabs');
    if (bar && !bar.querySelector('[data-k=research]')) {
      const b = document.createElement('button'); b.setAttribute('role', 'tab'); b.dataset.k = 'research'; b.textContent = 'Research';
      b.onclick = () => (window as any).KTGO?.('research', 1);
      bar.insertBefore(b, bar.querySelector('[data-k=notes]'));
      if (location.hash.split('/')[3] === 'research' && main.dataset.tab !== 'research') (window as any).KTGO?.('research');
    }
    if (main.dataset.tab === 'research') research(s);
  };
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(run, 60); }).observe(main, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-tab'] });
  run();
}
