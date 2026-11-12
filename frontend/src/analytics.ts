// Practice portfolio analytics: the paper-trading account analysed, not just listed. Return against the S&P 500
// over the same period, allocation by holding and by sector, concentration, weighted beta, realised results, and a
// trade journal where each trade can carry the reason it was made. Everything is computed from the user's own
// paper trades and live or delayed prices; nothing is estimated. Shown at the top of the Portfolio page.

interface Pos { q: number; avg: number; cost?: number; ccy?: string }
interface Trade { t: number; side: 'b' | 's'; sym: string; q: number; px: number; real?: number; type?: string }
interface Acct { cash: number; pos: Record<string, Pos>; hist: Trade[]; start?: number; real?: number }

const JKEY = 'q_tj';
const CSS = `
#qpa{margin-bottom:18px}#qpa h3{margin:0 0 4px}#qpa .sub{color:var(--mu);font-size:13px;margin:0 0 14px;line-height:1.5}
#qpa .kp{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin-bottom:14px}#qpa .kp>div{border:1px solid var(--ln);border-radius:12px;padding:10px 12px}
#qpa .kp span{display:block;font-size:12px;color:var(--mu)}#qpa .kp b{font-size:19px;font-variant-numeric:tabular-nums}#qpa .kp small{display:block;font-size:12px;color:var(--mu);margin-top:2px}
#qpa .g2{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:14px}@media(max-width:860px){#qpa .g2{grid-template-columns:minmax(0,1fr)}}
#qpa h4{margin:0 0 8px;font-size:13px;letter-spacing:.06em;text-transform:uppercase;color:var(--mu);font-weight:600}
#qpa .qbar{display:grid;grid-template-columns:minmax(70px,110px) minmax(0,1fr) 54px;gap:10px;align-items:center;font-size:13.5px;margin:6px 0}
#qpa .qbar i{display:block;height:8px;border-radius:4px;background:var(--ac)}#qpa .qbar .t{height:8px;border-radius:4px;background:rgba(var(--fgc),.08)}#qpa .qbar em{font-style:normal;text-align:right;font-variant-numeric:tabular-nums}
#qpa table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums}#qpa th,#qpa td{padding:8px 9px;border-bottom:1px solid var(--ln);text-align:right;white-space:nowrap;vertical-align:top}
#qpa th{font-size:12px;color:var(--mu);font-weight:500}#qpa th:first-child,#qpa td:first-child,#qpa td.n,#qpa th.n{text-align:left}
#qpa td.n{white-space:normal;min-width:180px}#qpa td.n input{width:100%;margin:0;font-size:13px;padding:6px 8px!important;box-sizing:border-box}
#qpa .ov{overflow-x:auto;border:1px solid var(--ln);border-radius:12px}#qpa .warn{margin:10px 0 0;padding:9px 12px;border-radius:10px;border:1px solid rgba(var(--acc),.35);background:rgba(var(--acc),.06);font-size:13px;line-height:1.5}
#qpa .note{font-size:12.5px;color:var(--mu);line-height:1.55;margin-top:10px}`;

const money = (v: number) => (v < 0 ? '−$' : '$') + fmt(Math.abs(v), Math.abs(v) >= 1000 ? 0 : 2);
const pct = (v: number | null) => v == null || !isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${fmt(Math.abs(v * 100), 1)}%`;
const day = (t: number) => new Date(t).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });

async function render(host: HTMLElement): Promise<void> {
  const A = LS<Acct | null>('q_pt', null);
  const hist = (A?.hist || []).slice().sort((x, y) => x.t - y.t);
  if (!A || !hist.length) {
    host.innerHTML = `<div class="pan"><h3>Your practice portfolio, analysed</h3><p class="sub">Make a practice trade with virtual money and this section shows how it's doing against the S&P 500, what you own by sector, how concentrated you are, and a journal of every trade with your reasons.</p><a class="btn p" href="#/trade">Make a practice trade →</a></div>`;
    return;
  }
  host.innerHTML = '<div class="pan"><h3>Your practice portfolio, analysed</h3><p class="sub">Loading live prices…</p></div>';
  const syms = Object.keys(A.pos || {}).filter(s => (A.pos[s]?.q || 0) > 0);
  const qs: Record<string, any> = {};
  if (syms.length) { const r = await jf('/api/quotes?symbols=' + encodeURIComponent(syms.join(','))).catch(() => ({ ok: false, b: {} } as any)); for (const q of (r.ok ? r.b.quotes : []) || []) qs[q.symbol] = q; }
  const start = A.start || 100000, first = hist[0].t;
  const lines = syms.map(s => { const p = A.pos[s], px = qs[s]?.price ?? p.avg; return { s, q: p.q, avg: p.avg, px, live: qs[s]?.price != null, v: p.q * px, pl: (px - p.avg) * p.q }; });
  const invested = lines.reduce((a, l) => a + l.v, 0), total = invested + (A.cash || 0), ret = total / start - 1;
  // S&P 500 over the same period (SPY), from the first trade's date
  let spy: number | null = null, spyFrom = '';
  try {
    const h = await jf('/api/history?symbol=SPY&range=1y');
    const bars = (h.ok ? h.b.bars : []) as { t: string; c: number }[];
    const fd = new Date(first).toISOString().slice(0, 10), b0 = bars.find(b => b.t >= fd) || null, b1 = bars[bars.length - 1];
    if (b0 && b1 && b0 !== b1) { spy = b1.c / b0.c - 1; spyFrom = b0.t; }
  } catch { /* shown as unavailable */ }
  // sectors and betas from each holding's profile
  const meta: Record<string, { sector?: string; beta?: number | null }> = {};
  await Promise.all(syms.map(async s => {
    const [o, f] = await Promise.all([jf('/api/overview?symbol=' + encodeURIComponent(s)).catch(() => null), jf('/api/fundamentals?symbol=' + encodeURIComponent(s)).catch(() => null)]);
    meta[s] = { sector: (o as any)?.ok ? (o as any).b.sector : undefined, beta: (f as any)?.ok ? (f as any).b.key?.beta ?? null : null };
  }));
  const bySector: Record<string, number> = {};
  lines.forEach(l => { const k = meta[l.s]?.sector || (/-USD$/.test(l.s) ? 'Crypto' : 'Other / fund'); bySector[k] = (bySector[k] || 0) + l.v; });
  const betaKnown = lines.filter(l => meta[l.s]?.beta != null), betaW = betaKnown.reduce((a, l) => a + l.v, 0);
  const beta = betaW > 0 ? betaKnown.reduce((a, l) => a + l.v * (meta[l.s].beta as number), 0) / betaW : null;
  const top = lines.slice().sort((x, y) => y.v - x.v)[0];
  const sells = hist.filter(t => t.side === 's' && t.real != null), wins = sells.filter(t => (t.real as number) > 0).length;
  const realised = A.real ?? sells.reduce((a, t) => a + (t.real || 0), 0);
  const J = LS<Record<string, string>>(JKEY, {});
  const bars = (rows: [string, number][], of: number) => rows.sort((x, y) => y[1] - x[1]).map(([k, v]) => `<div class="qbar"><span>${esc(k)}</span><div class="t"><i style="width:${Math.max(2, v / of * 100)}%"></i></div><em>${fmt(v / of * 100, 0)}%</em></div>`).join('');
  host.innerHTML = `<div class="pan"><h3>Your practice portfolio, analysed</h3>
    <p class="sub">Virtual money only: nothing real was bought. Prices are live or delayed up to 15 minutes; sectors and betas come from each company's profile (Finnhub and Yahoo Finance).</p>
    <div class="kp"><div><span>Account value</span><b>${money(total)}</b><small>started at ${money(start)}</small></div>
      <div><span>Your return</span><b class="${ret >= 0 ? 'up' : 'dn'}">${pct(ret)}</b><small>since ${day(first)}</small></div>
      <div><span>S&P 500, same period</span><b class="${(spy ?? 0) >= 0 ? 'up' : 'dn'}">${pct(spy)}</b><small>${spy == null ? 'not available' : 'SPY from ' + esc(spyFrom)}</small></div>
      <div><span>Versus the market</span><b class="${spy != null && ret - spy >= 0 ? 'up' : 'dn'}">${spy == null ? '—' : (ret - spy >= 0 ? '+' : '−') + fmt(Math.abs((ret - spy) * 100), 1) + ' pts'}</b><small>your return minus the S&P 500's</small></div>
      <div><span>Cash</span><b>${money(A.cash || 0)}</b><small>${fmt((A.cash || 0) / total * 100, 0)}% of the account</small></div>
      <div><span>Weighted beta</span><b>${beta == null ? '—' : fmt(beta, 2)}</b><small>${beta == null ? 'no betas available' : beta > 1.2 ? 'swings more than the market' : beta < 0.8 ? 'swings less than the market' : 'moves roughly with the market'}</small></div></div>
    ${lines.length ? `<div class="g2"><div><h4>By holding</h4>${bars(lines.map(l => [l.s, l.v] as [string, number]), invested)}</div><div><h4>By sector</h4>${bars(Object.entries(bySector), invested)}</div></div>
      ${top && top.v / total > 0.4 ? `<div class="warn">${esc(top.s)} is ${fmt(top.v / total * 100, 0)}% of your account. One company that large means its news moves your whole result. <a href="#/lesson/divers">Learn about diversification →</a></div>` : ''}` : '<p class="sub">You hold only cash right now.</p>'}
    <h4 style="margin-top:18px">Trade journal</h4>
    <p class="sub" style="margin-bottom:8px">${hist.length} trade${hist.length === 1 ? '' : 's'}${sells.length ? ` · ${wins} of ${sells.length} sales made money · realised ${money(realised)}` : ''}. Write why you made each trade; looking back at your reasons is how investors learn.</p>
    <div class="ov"><table><tr><th>Date</th><th class="n">Trade</th><th>Shares</th><th>Price</th><th>Result</th><th class="n">Why I made it</th></tr>
      ${hist.slice().reverse().slice(0, 50).map(t => `<tr><td>${day(t.t)}</td><td class="n"><b>${t.side === 'b' ? 'Bought' : 'Sold'}</b> <a href="#/asset/${encodeURIComponent(t.sym)}/research">${esc(t.sym)}</a></td><td>${fmt(t.q, t.q % 1 ? 2 : 0)}</td><td>$${fmt(t.px, 2)}</td><td class="${(t.real || 0) >= 0 ? 'up' : 'dn'}">${t.side === 's' && t.real != null ? money(t.real) : '—'}</td><td class="n"><input data-j="${t.t}" value="${esc(J[String(t.t)] || '')}" placeholder="e.g. earnings beat, valuation looked low" aria-label="Why I made this trade"></td></tr>`).join('')}</table></div>
    <p class="note">Return compares your account value with the starting $${fmt(start, 0)}. The S&P 500 figure is the SPY fund's price change from the first trading day on or after your first trade (dividends not included). Beta is weighted by the value of each holding.</p></div>`;
  let tm: number | undefined;
  host.querySelectorAll<HTMLInputElement>('input[data-j]').forEach(i => i.oninput = () => { clearTimeout(tm); tm = window.setTimeout(() => { const all = LS<Record<string, string>>(JKEY, {}); if (i.value.trim()) all[i.dataset.j!] = i.value.slice(0, 300); else delete all[i.dataset.j!]; SS(JKEY, all); track('journal_note'); }, 500); });
}

export function initAnalytics(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qpa-css">${CSS}</style>`);
  if (!SYNC.includes(JKEY)) SYNC.push(JKEY);
  const main = document.getElementById('m'); if (!main) return;
  let t: number | undefined;
  const run = () => {
    if (!/^#\/portfolio\/?$/.test(location.hash)) return;
    const body = main.querySelector<HTMLElement>('.body') || main;
    if (!body.textContent?.trim() || document.getElementById('qpa')) return;
    const host = document.createElement('div'); host.id = 'qpa';
    body.prepend(host);
    render(host);
  };
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(run, 120); }).observe(main, { childList: true, subtree: true });
  run();
}
