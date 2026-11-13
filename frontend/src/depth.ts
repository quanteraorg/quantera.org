// Research depth on each stock's Research tab, connecting filings, earnings, valuation and the share price:
//   1. Earnings: every recent report (dated from the company's 8-K earnings releases) with revenue and EPS from its
//      SEC filings, analysts' forecasts against actuals (Yahoo Finance, last 4 quarters) and how the shares moved.
//   2. Valuation in context: today's multiples against the company's own last five years and its competitors.
//   3. Timeline: two years of the share price with earnings, filings, big moves and news marked on it.
// Every figure says where it came from. Nothing is estimated where data is missing: the cell shows "—".

interface Line { key: string; values: (number | null)[] }
interface Period { label: string; end: string; filing?: string; form?: string }
interface Book { periods: Period[]; income: Line[]; balance: Line[]; cashflow: Line[] }
interface St { name?: string; financial: boolean; annual: Book; quarterly: Book }
interface Bar { t: string; c: number }
interface YE { quarter: string; actual: number | null; estimate: number | null; surprise: number | null }
interface Rel { date: string; afterClose: boolean }
interface Filing { form: string; label: string; date: string; url: string }
interface Peer { symbol: string; pe?: number | null; forwardPe?: number | null; ps?: number | null }

const CSS = `
.qdp h3{margin:0 0 4px;font-size:17px}.qdp .sub{color:var(--mu);font-size:13px;line-height:1.5;margin:0 0 12px}
.qdp table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums lining-nums}
.qdp th,.qdp td{padding:7px 8px;border-bottom:1px solid var(--ln);text-align:right;white-space:nowrap}.qdp th{font-size:12px;color:var(--mu);font-weight:500}
.qdp th:first-child,.qdp td:first-child{text-align:left}.qdp .qdp-sc{overflow-x:auto}.qdp .nt{font-size:12.5px;color:var(--mu);line-height:1.55;margin-top:10px}
.qdp .sum{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:10px;margin:4px 0 14px}.qdp .sum>div{border:1px solid var(--ln);border-radius:12px;padding:10px 12px}
.qdp .sum span{display:block;font-size:12px;color:var(--mu)}.qdp .sum b{font-size:18px;font-variant-numeric:tabular-nums}
.qdp .beat{color:var(--up);font-weight:600}.qdp .miss{color:var(--dn);font-weight:600}
.qdp .vrow{display:grid;grid-template-columns:150px minmax(0,1fr) 190px;gap:14px;align-items:center;padding:12px 0;border-bottom:1px solid var(--ln)}
@media(max-width:760px){.qdp .vrow{grid-template-columns:minmax(0,1fr)}}
.qdp .vrow b{font-size:15px}.qdp .vrow small{display:block;color:var(--mu);font-size:12px;line-height:1.45}
.qdp .rg{position:relative;height:34px}.qdp .rg .tr{position:absolute;left:0;right:0;top:15px;height:4px;border-radius:99px;background:var(--s2)}
.qdp .rg .mk{position:absolute;top:8px;width:2px;height:18px;transform:translateX(-1px)}.qdp .rg .mk.me{width:12px;height:12px;top:11px;border-radius:50%;transform:translateX(-6px);background:var(--ac);border:2px solid var(--bg)}
.qdp .rg .mk.md{background:var(--mu)}.qdp .rg .mk.pr{background:#7aa6e8}.qdp .rg .lo,.qdp .rg .hi{position:absolute;top:22px;font-size:11px;color:var(--mu)}.qdp .rg .hi{right:0}
.qdp .lg{display:flex;gap:14px;flex-wrap:wrap;font-size:12px;color:var(--mu);margin-top:6px}.qdp .lg i{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:5px;vertical-align:-1px}
.qdp .tl svg{width:100%;height:auto;display:block}.qdp .tl .f{display:flex;gap:6px;flex-wrap:wrap;margin:10px 0}
.qdp .ev{display:grid;grid-template-columns:96px 22px minmax(0,1fr);gap:8px;padding:8px 0;border-bottom:1px solid var(--ln);font-size:13.5px;line-height:1.45}
.qdp .ev .d{color:var(--mu);font-variant-numeric:tabular-nums}.qdp .ev .i{text-align:center}.qdp .ev small{color:var(--mu)}
.qdp .sk{height:120px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite}`;

const enc = encodeURIComponent;
const cache = new Map<string, Promise<any>>();
const get = (u: string) => { if (!cache.has(u)) cache.set(u, jf(u).then(r => (r.ok ? r.b : null)).catch(() => null)); return cache.get(u)!; };
const DAY = 864e5;
const dt = (s: string) => new Date(s.length === 10 ? s + 'T12:00:00Z' : s).getTime();
const dstr = (t: number | string) => new Date(typeof t === 'string' ? dt(t) : t).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' });
const pct = (v: number | null | undefined, dp = 1) => v == null || !isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${fmt(Math.abs(v * 100), dp)}%`;
const money = (v: number | null | undefined) => { if (v == null || !isFinite(v)) return '—'; const a = Math.abs(v), s = v < 0 ? '−' : ''; return a >= 1e12 ? `${s}$${fmt(a / 1e12, 2)}T` : a >= 1e9 ? `${s}$${fmt(a / 1e9, 1)}B` : a >= 1e6 ? `${s}$${fmt(a / 1e6, 0)}M` : `${s}$${fmt(a, 2)}`; };
const xx = (v: number | null | undefined) => v == null || !isFinite(v) || v <= 0 ? '—' : fmt(v, 1) + '×';
const line = (b: Book, k: string) => [...b.income, ...b.cashflow, ...b.balance].find(l => l.key === k)?.values || [];
const median = (a: number[]) => { const v = a.filter(x => isFinite(x)).sort((p, q) => p - q); if (!v.length) return null; const m = v.length >> 1; return v.length % 2 ? v[m] : (v[m - 1] + v[m]) / 2; };

/** Index of the last bar on or before time t. */
function barAt(bars: Bar[], t: number): number {
  let lo = 0, hi = bars.length - 1, r = -1;
  while (lo <= hi) { const m = (lo + hi) >> 1; if (dt(bars[m].t) <= t) { r = m; lo = m + 1; } else hi = m - 1; }
  return r;
}
/** Split-sized one-day jumps mean the price history isn't adjusted, so multiples from it would be wrong. */
const splitty = (bars: Bar[]) => bars.some((b, i) => i > 0 && Math.abs(b.c / bars[i - 1].c - 1) > 0.45);

// ---------- 1. Earnings ----------
interface Row { rel: Rel; label: string; end: string; rev: number | null; revY: number | null; eps: number | null; epsY: number | null; est: number | null; act: number | null; surp: number | null; d1: number | null; w1: number | null; d1x: number | null }

function earnings(st: St | null, ye: YE[], rels: Rel[], bars: Bar[], spy: Bar[]): Row[] {
  const q = st?.quarterly; if (!q) return [];
  const P = q.periods, rev = line(q, 'revenue'), eps = line(q, 'eps');
  const react = (b: Bar[], r: Rel) => {
    const t = dt(r.date), i = barAt(b, t);
    if (i < 1) return null;
    // last close before the news, then the first close after it (a before-the-open report moves that same day)
    const onDay = b[i].t === r.date;
    const base = !r.afterClose && onDay ? i - 1 : i, hit = base + 1;
    if (base < 0 || hit >= b.length) return null;
    const w = Math.min(base + 5, b.length - 1);
    return { d1: b[hit].c / b[base].c - 1, w1: b[w].c / b[base].c - 1, partial: w < base + 5 };
  };
  // Each report belongs to one quarter: the latest quarter that ended up to 120 days before it and isn't already taken
  // by a later report. Forecasts (Yahoo) are matched the same way, each used once.
  const usedP = new Set<number>(), usedY = new Set<YE>();
  return rels.slice(0, 12).map(r => {
    const t = dt(r.date);
    let k = -1; for (let i = P.length - 1; i >= 0; i--) { const e = dt(P[i].end); if (e <= t && t - e <= 120 * DAY && !usedP.has(i)) { k = i; break; } }
    if (k >= 0) usedP.add(k);
    const y = ye.filter(x => !usedY.has(x) && t - dt(x.quarter) >= -5 * DAY && t - dt(x.quarter) <= 110 * DAY).sort((p1, p2) => dt(p2.quarter) - dt(p1.quarter))[0];
    if (y) usedY.add(y);
    const a = react(bars, r), s = react(spy, r);
    const g = (arr: (number | null)[]) => k >= 4 && arr[k] != null && arr[k - 4] != null && arr[k - 4] !== 0 ? (arr[k]! - arr[k - 4]!) / Math.abs(arr[k - 4]!) : null;
    return { rel: r, label: k >= 0 ? P[k].label : '—', end: k >= 0 ? P[k].end : '', rev: k >= 0 ? rev[k] ?? null : null, revY: g(rev), eps: k >= 0 ? eps[k] ?? null : null, epsY: g(eps),
      est: y?.estimate ?? null, act: y?.actual ?? null, surp: y?.surprise ?? null, d1: a?.d1 ?? null, w1: a?.w1 ?? null, d1x: a && s ? a.d1 - s.d1 : null };
  });
}

function earningsHtml(s: string, rows: Row[], bars: Bar[], next?: string | null): string {
  if (!rows.length) return `<h3>Earnings: results against forecasts</h3><p class="sub">No earnings releases were found in ${esc(s)}’s SEC filings, so there is nothing to compare. This usually means it isn’t a US-listed operating company.</p>`;
  const rets = bars.slice(-504).map((b, i, a) => i ? Math.abs(b.c / a[i - 1].c - 1) : NaN).filter(isFinite);
  const typical = median(rets);
  const withEst = rows.filter(r => r.surp != null), beats = withEst.filter(r => (r.surp as number) > 0).length;
  const moves = rows.map(r => r.d1).filter((v): v is number => v != null);
  const avgAbs = moves.length ? moves.reduce((t, v) => t + Math.abs(v), 0) / moves.length : null;
  const up = moves.filter(v => v > 0).length;
  const max = Math.max(0.02, ...moves.map(Math.abs));
  const bar = (v: number | null) => v == null ? '' : `<span style="display:inline-block;vertical-align:middle;margin-left:6px;width:60px;height:8px;position:relative;background:var(--s2);border-radius:99px"><i style="position:absolute;top:0;bottom:0;${v >= 0 ? 'left:50%' : `right:50%`};width:${Math.min(50, Math.abs(v) / max * 50)}%;background:${v >= 0 ? 'var(--up)' : 'var(--dn)'};border-radius:99px"></i></span>`;
  return `<h3>Earnings: results against forecasts</h3>
    <p class="sub">The last ${rows.length} earnings reports, dated from ${esc(s)}’s 8-K earnings releases. Revenue and EPS are as filed with the SEC; forecasts are analysts’ consensus from Yahoo Finance (last four quarters).</p>
    <div class="sum">
      <div><span>Beat the forecast</span><b>${withEst.length ? `${beats} of ${withEst.length}` : '—'}</b><span>recent quarters</span></div>
      <div><span>Average move the day after</span><b>${avgAbs == null ? '—' : '±' + fmt(avgAbs * 100, 1) + '%'}</b><span>${typical != null && avgAbs != null ? `${fmt(avgAbs / typical, 1)}× a typical day (±${fmt(typical * 100, 1)}%)` : ''}</span></div>
      <div><span>Shares rose after</span><b>${moves.length ? `${up} of ${moves.length}` : '—'}</b><span>reports</span></div>
      <div><span>Next report</span><b>${next ? esc(dstr(next)) : '—'}</b><span>${next ? 'expected (Yahoo Finance)' : 'not announced'}</span></div>
    </div>
    <div class="qdp-sc"><table><tr><th>Reported</th><th>Quarter</th><th>Revenue</th><th>vs year before</th><th>EPS (GAAP)</th><th>vs year before</th><th>Forecast EPS</th><th>Actual EPS</th><th>Surprise</th><th>Next day</th><th>vs S&amp;P 500</th><th>1 week</th></tr>
    ${rows.map(r => `<tr><td>${esc(dstr(r.rel.date))}<br><small class="mu">${r.rel.afterClose ? 'after the close' : 'before the open'}</small></td><td>${esc(r.label)}</td><td>${money(r.rev)}</td><td>${pct(r.revY)}</td><td>${r.eps == null ? '—' : '$' + fmt(r.eps, 2)}</td><td>${pct(r.epsY)}</td>
      <td>${r.est == null ? '—' : '$' + fmt(r.est, 2)}</td><td>${r.act == null ? '—' : '$' + fmt(r.act, 2)}</td><td>${r.surp == null ? '—' : `<span class="${r.surp >= 0 ? 'beat' : 'miss'}">${r.surp >= 0 ? '▲ beat ' : '▼ missed '}${fmt(Math.abs(r.surp * 100), 1)}%</span>`}</td>
      <td class="${r.d1 == null ? '' : r.d1 >= 0 ? 'up' : 'dn'}">${pct(r.d1)}${bar(r.d1)}</td><td>${pct(r.d1x)}</td><td class="${r.w1 == null ? '' : r.w1 >= 0 ? 'up' : 'dn'}">${pct(r.w1)}</td></tr>`).join('')}</table></div>
    <p class="nt">How to read it: “Next day” is the share price change from the last close before the report to the first close after it; “vs S&amp;P 500” subtracts the market’s move on the same days, so it shows the reaction to the company’s own news. Forecasts and the “actual” beside them are adjusted EPS, which companies and analysts use and which often differs from GAAP EPS in the filings. Past reactions don’t predict the next one. <a href="#/lesson/pe">Lesson: earnings and P/E</a></p>`;
}

// ---------- 2. Valuation in context ----------
interface Metric { key: string; name: string; now: number | null; hist: { t: string; v: number }[]; peers: number | null; nowSrc: string; why: string; lesson: string }

function valuation(st: St | null, f: any, bars: Bar[], peers: Peer[]): Metric[] {
  const k = f?.key || {};
  const mc = k.marketCap ?? null, fcfNow = k.freeCashFlow ?? null;
  const out: Metric[] = [
    { key: 'pe', name: 'Price / earnings (P/E)', now: k.pe ?? null, hist: [], peers: median(peers.map(p => p.pe ?? NaN).filter(v => v > 0)), nowSrc: 'Yahoo Finance, last 12 months', why: 'What investors pay for each dollar of yearly profit.', lesson: 'pe' },
    { key: 'ps', name: 'Price / sales (P/S)', now: k.ps ?? null, hist: [], peers: median(peers.map(p => p.ps ?? NaN).filter(v => v > 0)), nowSrc: 'Yahoo Finance, last 12 months', why: 'Useful when profits are small or swinging; compare within an industry.', lesson: 'ratios' },
    { key: 'pfcf', name: 'Price / free cash flow', now: mc && fcfNow && fcfNow > 0 ? mc / fcfNow : null, hist: [], peers: null, nowSrc: 'market value ÷ free cash flow, Yahoo Finance', why: 'What investors pay for each dollar of cash the business actually keeps.', lesson: 'cashflow' },
  ];
  if (st?.financial) out.splice(1, 2); // sales and free cash flow don't describe banks and insurers
  const A = st?.annual;
  if (A && bars.length && !splitty(bars)) {
    const rev = line(A, 'revenue'), eps = line(A, 'eps'), sh = line(A, 'shares'), fcf = line(A, 'freeCashFlow');
    A.periods.forEach((p, i) => {
      const bi = barAt(bars, dt(p.end));
      if (bi < 0 || dt(p.end) - dt(bars[bi].t) > 10 * DAY) return; // no price on that date
      const px = bars[bi].c, n = sh[i];
      const add = (key: string, v: number | null) => { const m = out.find(x => x.key === key); if (m && v != null && isFinite(v) && v > 0) m.hist.push({ t: p.label, v }); };
      add('pe', eps[i] && eps[i]! > 0 ? px / eps[i]! : null);
      if (n) { add('ps', rev[i] ? px * n / rev[i]! : null); add('pfcf', fcf[i] && fcf[i]! > 0 ? px * n / fcf[i]! : null); }
    });
  }
  return out;
}

function valuationHtml(s: string, ms: Metric[], peerCount: number): string {
  const rowsHtml = ms.map(m => {
    const vals = m.hist.map(h => h.v), lo = vals.length ? Math.min(...vals) : null, hi = vals.length ? Math.max(...vals) : null, md = median(vals);
    const all = [lo, hi, m.now, m.peers].filter((v): v is number => v != null && v > 0);
    let verdict = 'Not enough history to compare.';
    if (m.now == null) verdict = m.key === 'pe' ? 'No P/E: the company isn’t profitable over the last 12 months.' : 'Not available right now.';
    else if (vals.length >= 3 && lo != null && hi != null && md != null) {
      const pos = hi > lo ? (m.now - lo) / (hi - lo) : 0.5;
      verdict = m.now > hi ? `Above its whole ${vals.length}-year range` : m.now < lo ? `Below its whole ${vals.length}-year range` : pos > 0.75 ? `Near the top of its ${vals.length}-year range` : pos < 0.25 ? `Near the bottom of its ${vals.length}-year range` : `Middle of its ${vals.length}-year range`;
      verdict += ` (typical ${xx(md)})`;
    }
    if (m.now != null && m.peers != null) verdict += `; ${m.now > m.peers * 1.15 ? 'above' : m.now < m.peers * 0.85 ? 'below' : 'in line with'} competitors (${xx(m.peers)}).`;
    else if (m.now != null) verdict += '.';
    let range = '<small>No history available.</small>';
    if (all.length >= 2) {
      const a = Math.min(...all) * 0.9, b = Math.max(...all) * 1.08, x = (v: number) => `${Math.max(0, Math.min(100, (v - a) / (b - a) * 100))}%`;
      range = `<div class="rg" role="img" aria-label="${esc(`${m.name}: now ${xx(m.now)}, ${vals.length}-year range ${xx(lo)} to ${xx(hi)}, competitors ${xx(m.peers)}`)}"><span class="tr"></span>
        ${lo != null && hi != null ? `<span class="tr" style="left:${x(lo)};right:calc(100% - ${x(hi)});background:rgba(var(--acc),.35)"></span>` : ''}
        ${md != null ? `<span class="mk md" style="left:${x(md)}" title="5-year typical ${xx(md)}"></span>` : ''}${m.peers != null ? `<span class="mk pr" style="left:${x(m.peers)}" title="Competitors ${xx(m.peers)}"></span>` : ''}
        ${m.now != null ? `<span class="mk me" style="left:${x(m.now)}" title="Now ${xx(m.now)}"></span>` : ''}<span class="lo">${xx(a / 0.9)}</span><span class="hi">${xx(b / 1.08)}</span></div>`;
    }
    return `<div class="vrow"><div><b>${esc(m.name)}</b><small>${esc(m.why)} <a href="#/lesson/${m.lesson}">Lesson</a></small></div><div>${range}<small style="margin-top:4px">${esc(verdict)}</small></div>
      <div style="text-align:right"><b style="font-size:20px">${xx(m.now)}</b><small>now · ${esc(m.nowSrc)}</small><small>${m.hist.length ? `${m.hist.map(h => `${esc(h.t)} ${xx(h.v)}`).join(' · ')}` : ''}</small></div></div>`;
  }).join('');
  return `<h3>Valuation in context</h3><p class="sub">Is ${esc(s)} expensive? Today’s multiples next to its own history (price at each fiscal year end ÷ that year’s results from its 10-K) and the median of ${peerCount ? peerCount + ' competitors' : 'its competitors'}.</p>
    ${rowsHtml}<div class="lg"><span><i style="background:var(--ac)"></i>Now</span><span><i style="background:rgba(var(--acc),.35)"></i>Own range</span><span><i style="background:var(--mu)"></i>Own typical (median)</span><span><i style="background:#7aa6e8"></i>Competitors’ median</span></div>
    <p class="nt">A high multiple isn’t “bad”: it usually means investors expect faster growth. Compare with the company’s growth in the earnings table above and its competitors in the table above that. Price history covers about five years, so older years aren’t shown. <a href="#/valuation/${enc(s)}">Build your own valuation (DCF) →</a></p>`;
}

// ---------- 3. Timeline ----------
type EvT = 'earn' | 'file' | 'move' | 'news';
interface Ev { t: number; type: EvT; title: string; detail?: string; url?: string; v?: number }
const EVC: Record<EvT, [string, string, string]> = { earn: ['Earnings', '◆', 'var(--ac)'], file: ['Filings', '▤', '#7aa6e8'], move: ['Big moves', '▲', '#b79ae8'], news: ['News', '●', '#4fd1a5'] };

function timeline(rows: Row[], filings: Filing[], bars: Bar[], news: any[]): { evs: Ev[]; pts: Bar[] } {
  const from = Date.now() - 730 * DAY;
  const pts = bars.filter(b => dt(b.t) >= from);
  const evs: Ev[] = [];
  for (const r of rows) {
    const t = dt(r.rel.date); if (t < from) continue;
    evs.push({ t, type: 'earn', title: `${r.label} results${r.surp != null ? `: EPS ${r.surp >= 0 ? 'beat' : 'missed'} the forecast by ${fmt(Math.abs(r.surp * 100), 1)}%` : ''}`,
      detail: [r.rev != null ? `revenue ${money(r.rev)}${r.revY != null ? ` (${pct(r.revY)} on a year before)` : ''}` : '', r.d1 != null ? `shares ${pct(r.d1)} the next day` : ''].filter(Boolean).join('; ') });
  }
  for (const f of filings) {
    if (!['10-K', '10-Q', '8-K', '20-F', '6-K', 'S-1'].includes(f.form)) continue;
    const t = dt(f.date); if (t < from) continue;
    if (f.form === '8-K' && evs.some(e => e.type === 'earn' && Math.abs(e.t - t) <= DAY)) continue; // already shown as the earnings release
    evs.push({ t, type: 'file', title: `${f.form}: ${f.label}`, url: f.url });
  }
  const rets = pts.map((b, i) => (i ? b.c / pts[i - 1].c - 1 : 0));
  const sd = Math.sqrt(rets.reduce((a, v) => a + v * v, 0) / Math.max(1, rets.length));
  const big = rets.map((v, i) => ({ v, i })).filter(x => x.i > 0 && Math.abs(x.v) >= Math.max(0.04, 2.5 * sd)).sort((a, b) => Math.abs(b.v) - Math.abs(a.v)).slice(0, 8);
  for (const m of big) { const t = dt(pts[m.i].t); if (evs.some(e => e.type === 'earn' && t - e.t >= 0 && t - e.t <= 3 * DAY)) continue; evs.push({ t, type: 'move', v: m.v, title: `Shares ${m.v >= 0 ? 'rose' : 'fell'} ${fmt(Math.abs(m.v * 100), 1)}% in a day`, detail: 'One of the biggest one-day moves in two years. Check the news around this date for the reason.' }); }
  for (const a of (news || []).slice(0, 8)) { const t = dt(a.publishedAt || ''); if (!isFinite(t) || t < from) continue; evs.push({ t, type: 'news', title: String(a.title || ''), detail: String(a.source || ''), url: a.id ? '#/story/' + enc(a.id) : a.url }); }
  evs.sort((a, b) => b.t - a.t);
  return { evs, pts };
}

function timelineHtml(s: string, evs: Ev[], pts: Bar[], src: string): string {
  if (pts.length < 20) return `<h3>Timeline</h3><p class="sub">Not enough price history for a timeline.</p>`;
  const W = 1000, H = 230, P = 8, t0 = dt(pts[0].t), t1 = dt(pts[pts.length - 1].t);
  const lo = Math.min(...pts.map(p => p.c)), hi = Math.max(...pts.map(p => p.c));
  const X = (t: number) => P + (t - t0) / Math.max(1, t1 - t0) * (W - 2 * P), Y = (v: number) => H - 28 - (v - lo) / Math.max(1e-9, hi - lo) * (H - 50);
  const step = Math.max(1, Math.floor(pts.length / 260));
  const path = pts.filter((_, i) => i % step === 0 || i === pts.length - 1).map((p, i) => `${i ? 'L' : 'M'}${X(dt(p.t)).toFixed(1)},${Y(p.c).toFixed(1)}`).join('');
  const priceAt = (t: number) => { const i = barAt(pts, t); return pts[Math.max(0, i)].c; };
  const marks = evs.filter(e => e.t >= t0 && e.t <= t1).map(e => { const c = EVC[e.type]; const x = X(e.t), y = e.type === 'news' || e.type === 'file' ? H - 12 : Y(priceAt(e.t));
    return `<g data-ty="${e.type}"><circle cx="${x.toFixed(1)}" cy="${y.toFixed(1)}" r="${e.type === 'earn' ? 6 : 4.5}" fill="${c[2]}" stroke="var(--bg)" stroke-width="1.5"><title>${esc(dstr(e.t))}: ${esc(e.title)}</title></circle></g>`; }).join('');
  const years = [...new Set(pts.map(p => p.t.slice(0, 4)))].map(y => { const t = dt(y + '-01-01'); return t > t0 ? `<text x="${X(t).toFixed(1)}" y="${H - 2}" font-size="11" fill="var(--mu)">${y}</text><line x1="${X(t).toFixed(1)}" x2="${X(t).toFixed(1)}" y1="8" y2="${H - 22}" stroke="var(--ln)"/>` : ''; }).join('');
  const counts = (Object.keys(EVC) as EvT[]).map(k => [k, evs.filter(e => e.type === k).length] as [EvT, number]);
  return `<h3>Timeline</h3><p class="sub">Two years of ${esc(s)}’s share price with what happened along the way. Hover a dot for details; the list below has everything.</p>
    <div class="tl"><svg viewBox="0 0 ${W} ${H}" role="img" aria-label="${esc(`${s} share price over two years, from $${fmt(pts[0].c, 2)} to $${fmt(pts[pts.length - 1].c, 2)}, with ${evs.length} events marked`)}">${years}<path d="${path}" fill="none" stroke="var(--tx)" stroke-width="1.6" opacity=".8"/>${marks}</svg>
    <div class="f" role="group" aria-label="Filter events"><button class="chip on" data-f="all">All (${evs.length})</button>${counts.filter(([, n]) => n).map(([k, n]) => `<button class="chip" data-f="${k}"><span style="color:${EVC[k][2]}">${EVC[k][1]}</span> ${EVC[k][0]} (${n})</button>`).join('')}</div>
    <div class="evs">${evs.map(e => `<div class="ev" data-ty="${e.type}"><span class="d">${esc(dstr(e.t))}</span><span class="i" style="color:${EVC[e.type][2]}" aria-label="${EVC[e.type][0]}">${EVC[e.type][1]}</span>
      <span>${e.url ? `<a href="${esc(e.url)}"${e.url.startsWith('#') ? '' : ' target="_blank" rel="noopener noreferrer"'}>${esc(e.title)}</a>` : esc(e.title)}${e.detail ? `<br><small>${esc(e.detail)}</small>` : ''}</span></div>`).join('') || '<p class="mu">No events found.</p>'}</div></div>
    <p class="nt">Prices: ${esc(src)}, daily closes. Filings: SEC EDGAR. Big moves are days that moved at least 2.5 times a typical day (and at least 4%). News: the latest headlines tagged with ${esc(s)}.</p>`;
}

// ---------- wiring ----------
async function build(host: HTMLElement, s: string): Promise<void> {
  host.innerHTML = `<div class="pan qdp" id="qdpe"><h3>Earnings: results against forecasts</h3><div class="sk"></div></div>
    <div class="pan qdp" id="qdpv" style="margin-top:14px"><h3>Valuation in context</h3><div class="sk"></div></div>
    <div class="pan qdp" id="qdpt" style="margin-top:14px"><h3>Timeline</h3><div class="sk"></div></div>`;
  const [st, f, fil, h, spy, peers, news] = await Promise.all([
    get('/api/statements?symbol=' + enc(s)), get('/api/fundamentals?symbol=' + enc(s)), get('/api/filings?symbol=' + enc(s)),
    get('/api/history?symbol=' + enc(s) + '&full=1'), get('/api/history?symbol=SPY&full=1'), get('/api/peers?symbol=' + enc(s)), get('/api/news?symbols=' + enc(s)),
  ]);
  if (!host.isConnected) return;
  const bars: Bar[] = (h?.bars || []).filter((b: Bar) => b && isFinite(b.c)), spyBars: Bar[] = (spy?.bars || []).filter((b: Bar) => b && isFinite(b.c));
  const rows = earnings(st, (f?.earnings || []) as YE[], (fil?.earnings || []) as Rel[], bars, spyBars);
  const set = (id: string, html: string) => { const e = host.querySelector('#' + id); if (e) e.innerHTML = html; };
  set('qdpe', earningsHtml(s, rows, bars, f?.nextEarnings));
  const pl = ((peers?.peers || []) as Peer[]).filter(p => p.symbol !== s);
  set('qdpv', f || st ? valuationHtml(s, valuation(st, f, bars, pl), pl.length) : `<h3>Valuation in context</h3><p class="sub">Valuation data for ${esc(s)} isn’t available right now.</p>`);
  const { evs, pts } = timeline(rows, (fil?.filings || []) as Filing[], bars, news?.articles || []);
  set('qdpt', timelineHtml(s, evs, pts, h?.source || 'market data'));
  const t = host.querySelector('#qdpt') as HTMLElement;
  t.querySelectorAll<HTMLButtonElement>('[data-f]').forEach(b => b.onclick = () => {
    t.querySelectorAll('[data-f]').forEach(x => x.classList.toggle('on', x === b));
    const f2 = b.dataset.f;
    t.querySelectorAll<HTMLElement>('[data-ty]').forEach(e => { e.style.display = f2 === 'all' || e.dataset.ty === f2 ? '' : 'none'; });
  });
  track('research_depth');
}

export function initDepth(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qdp-css">${CSS}</style>`);
  const main = document.getElementById('m');
  if (!main) return;
  new MutationObserver(() => {
    const res = document.getElementById('qres'), peer = document.getElementById('qrpeer'), mm = /^#\/asset\/([^/]+)/.exec(location.hash);
    if (!res || !peer || !mm || document.getElementById('qdp')) return;
    const s = decodeURIComponent(mm[1]).toUpperCase();
    const host = document.createElement('div'); host.id = 'qdp'; host.style.marginTop = '14px';
    peer.insertAdjacentElement('afterend', host);
    const jump = res.querySelector('.qr-jump');
    if (jump && !jump.querySelector('[data-qdp]')) jump.insertAdjacentHTML('beforeend', ['qdpe:Earnings', 'qdpv:Valuation vs history', 'qdpt:Timeline'].map(x => { const [id, l] = x.split(':'); return `<button class="chip" data-qdp="${id}">${l}</button>`; }).join(''));
    jump?.querySelectorAll<HTMLButtonElement>('[data-qdp]').forEach(b => b.onclick = () => document.getElementById(b.dataset.qdp!)?.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' }));
    // build when the Research tab is opened
    const start = () => { if (main.dataset.tab === 'research' && !host.dataset.on) { host.dataset.on = '1'; build(host, s); } };
    start(); new MutationObserver(start).observe(main, { attributes: true, attributeFilter: ['data-tab'] });
  }).observe(main, { childList: true, subtree: true });
}
