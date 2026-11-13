// AI deep research: one panel per company with three verdicts side by side (an AI analyst view, Wall Street's consensus
// and a technical rating), a full technical-analysis table, and a written report built only from figures Quantera holds.
// Replaces the story-explainer lenses on news stories, sits on every stock's Research tab, and has its own page at
// #/report/SYM. The technicals and analyst figures load without the AI, so the panel stays useful when the AI is offline.

interface Sig { name: string; value: number | null; signal: 'buy' | 'sell' | 'neutral'; rule: string }
interface Tech {
  symbol: string; asOf: string; price: number; source: string; rating: string; score: number; trend: string;
  counts: { buy: number; neutral: number; sell: number }; signals: Sig[];
  levels: { support20d: number; resistance20d: number; high52w: number; low52w: number; fromHighPct: number; fromLowPct: number };
  averages: Record<string, number | null>; returns: Record<string, number | null>; volatilityPct: number; atr14: number; method: string;
}
interface Analysts { target?: number; low?: number; high?: number; count?: number; rating?: string; strongBuy?: number; buy?: number; hold?: number; sell?: number; strongSell?: number }
interface Fund { name?: string; price?: number; analysts?: Analysts | null; nextEarnings?: string | null }
interface Report {
  symbol: string; name: string; generatedAt: string; provider: string; sources: { what: string; from: string; asOf: string }[]; notAvailable: string[];
  report: { headline: string; summary: string; sections: { title: string; points: string[] }[]; bull: string[]; bear: string[]; risks: string[]; catalysts: string[];
    verdict: { rating: 'Buy' | 'Hold' | 'Sell'; confidence: string; horizon: string; reasons: string[]; changeMind: string } };
}

const CSS = `
.qdr .qdr-h{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-bottom:4px}.qdr .qdr-h h3{margin:0;font-size:18px}.qdr .qdr-h a{margin-left:auto;font-size:13px}
.qdr .qdr-sub{color:var(--mu);font-size:13px;line-height:1.5;margin:0 0 12px}
.qdr-tabs{display:flex;gap:6px;flex-wrap:wrap;margin:0 0 12px}.qdr-tabs button{border:1px solid var(--ln);background:transparent;color:var(--tx);border-radius:99px;padding:5px 12px;font:inherit;font-size:13px;cursor:pointer}
.qdr-tabs button.on{border-color:var(--ac);background:rgba(var(--acc),.12)}
.qdr-cards{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:10px;margin-bottom:14px}@media(max-width:760px){.qdr-cards{grid-template-columns:minmax(0,1fr)}}
.qdr-card{border:1px solid var(--ln);border-radius:14px;padding:12px 14px;min-width:0}.qdr-card .qdr-k{font-size:12px;color:var(--mu);display:block;margin-bottom:4px}
.qdr-card .qdr-v{font-size:24px;font-weight:600;letter-spacing:-.01em;line-height:1.2}.qdr-card .qdr-s{font-size:12.5px;color:var(--mu);margin-top:4px;line-height:1.45}
.qdr-card .qdr-v.buy{color:var(--up)}.qdr-card .qdr-v.sell{color:var(--dn)}.qdr-card .qdr-v.hold{color:var(--ac)}
.qdr-bar{display:flex;height:8px;border-radius:99px;overflow:hidden;margin-top:8px;background:var(--s2)}.qdr-bar i{display:block;height:100%}
.qdr table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums lining-nums}.qdr th,.qdr td{padding:7px 8px;border-bottom:1px solid var(--ln);text-align:left;vertical-align:top}
.qdr th{font-size:12px;color:var(--mu);font-weight:500}.qdr td.n{text-align:right;white-space:nowrap}.qdr td small{color:var(--mu);font-size:12px;line-height:1.4}
.qdr .ov{overflow-x:auto}.qdr .sg{font-weight:600;white-space:nowrap}.qdr .sg.buy{color:var(--up)}.qdr .sg.sell{color:var(--dn)}.qdr .sg.neutral{color:var(--mu)}
.qdr-lv{display:grid;grid-template-columns:repeat(auto-fit,minmax(140px,1fr));gap:8px;margin:12px 0}.qdr-lv>div{border:1px solid var(--ln);border-radius:12px;padding:8px 10px}
.qdr-lv span{display:block;font-size:12px;color:var(--mu)}.qdr-lv b{font-variant-numeric:tabular-nums}
.qdr-rep h4{margin:16px 0 6px;font-size:15px}.qdr-rep ul{margin:0;padding-left:18px;line-height:1.6}.qdr-rep li{margin:3px 0}
.qdr-rep .lead{font-size:15.5px;line-height:1.6;margin:0 0 6px}.qdr-rep .qdr-hl{font-family:Fraunces,serif;font-size:21px;line-height:1.3;margin:0 0 8px}
.qdr-2{display:grid;grid-template-columns:minmax(0,1fr) minmax(0,1fr);gap:12px}@media(max-width:760px){.qdr-2{grid-template-columns:minmax(0,1fr)}}
.qdr-2>div{border:1px solid var(--ln);border-radius:12px;padding:10px 12px}
.qdr-note{font-size:12.5px;color:var(--mu);line-height:1.55;margin-top:12px}
.qdr-sk{height:120px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite}
.qdr-find{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 16px}.qdr-find input{max-width:260px}`;

const enc = encodeURIComponent;
const tcache = new Map<string, Promise<{ ok: boolean; b: any }>>(), fcache = new Map<string, Promise<{ ok: boolean; b: any }>>();
const rcache = new Map<string, Promise<{ ok: boolean; status: number; b: any }>>();
const once = (m: Map<string, Promise<any>>, k: string, f: () => Promise<any>) => { if (!m.has(k)) m.set(k, f().catch(() => ({ ok: false, status: 0, b: {} }))); return m.get(k)!; };
const tech = (s: string) => once(tcache, s, () => jf('/api/technicals?symbol=' + enc(s)));
const fund = (s: string) => once(fcache, s, () => jf('/api/fundamentals?symbol=' + enc(s)));
const report = (s: string) => once(rcache, s, async () => {
  const r = await fetch('/api/research', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ symbol: s }) });
  const b = await r.json().catch(() => ({}));
  if (!r.ok) rcache.delete(s); // let a later visit try again
  return { ok: r.ok, status: r.status, b };
});

const num = (v: number | null | undefined, dp = 2) => v == null || !isFinite(v) ? '—' : fmt(v, dp);
const pc = (v: number | null | undefined) => v == null || !isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${fmt(Math.abs(v), 1)}%`;
const cls = (r: string) => /buy/i.test(r) ? 'buy' : /sell|under/i.test(r) ? 'sell' : 'hold';
const arrow = (r: string) => cls(r) === 'buy' ? '▲ ' : cls(r) === 'sell' ? '▼ ' : '● ';
const day = (iso: string) => { const d = new Date(iso.length === 10 ? iso + 'T00:00:00Z' : iso); return isNaN(+d) ? iso : d.toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric', timeZone: 'UTC' }); };
const li = (xs: string[]) => xs.length ? `<ul>${xs.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : '<p class="mu">Not available.</p>';
const YR: Record<string, string> = { strong_buy: 'Strong buy', buy: 'Buy', hold: 'Hold', underperform: 'Underperform', sell: 'Sell', none: '—' };

function analystCard(f: Fund | null): string {
  const a = f?.analysts;
  if (!a || !a.rating) return `<div class="qdr-card"><span class="qdr-k">Wall Street analysts</span><div class="qdr-v">—</div><div class="qdr-s">No analyst coverage found for this company.</div></div>`;
  const parts = [[a.strongBuy, 'var(--up)'], [a.buy, 'color-mix(in srgb,var(--up) 60%,transparent)'], [a.hold, 'var(--mu)'], [a.sell, 'color-mix(in srgb,var(--dn) 60%,transparent)'], [a.strongSell, 'var(--dn)']] as [number | undefined, string][];
  const tot = parts.reduce((s, [v]) => s + (v || 0), 0);
  const up = a.target && f?.price ? (a.target / f.price - 1) * 100 : null;
  const label = YR[a.rating] || a.rating;
  return `<div class="qdr-card"><span class="qdr-k">Wall Street analysts</span><div class="qdr-v ${cls(label)}">${arrow(label)}${esc(label)}</div>
    <div class="qdr-s">${a.count ? `${fmt(a.count, 0)} analysts. ` : ''}${a.target ? `Average target $${fmt(a.target, 2)}${up != null ? ` (${pc(up)} from today)` : ''}, range $${num(a.low)}–$${num(a.high)}. Targets are forecasts.` : ''}</div>
    ${tot ? `<div class="qdr-bar" role="img" aria-label="${a.strongBuy || 0} strong buy, ${a.buy || 0} buy, ${a.hold || 0} hold, ${a.sell || 0} sell, ${a.strongSell || 0} strong sell">${parts.map(([v, c]) => v ? `<i style="width:${v / tot * 100}%;background:${c}"></i>` : '').join('')}</div>
    <div class="qdr-s">${a.strongBuy || 0} strong buy · ${a.buy || 0} buy · ${a.hold || 0} hold · ${a.sell || 0} sell · ${a.strongSell || 0} strong sell</div>` : ''}
    <div class="qdr-s">Source: Yahoo Finance.</div></div>`;
}

function techCard(t: Tech | null, err?: string): string {
  if (!t) return `<div class="qdr-card"><span class="qdr-k">Technical rating</span><div class="qdr-v">—</div><div class="qdr-s">${esc(err || 'Price history is unavailable.')}</div></div>`;
  return `<div class="qdr-card"><span class="qdr-k">Technical rating</span><div class="qdr-v ${cls(t.rating)}">${arrow(t.rating)}${esc(t.rating)}</div>
    <div class="qdr-s">${t.counts.buy} buy · ${t.counts.neutral} neutral · ${t.counts.sell} sell signals. Trend: ${esc(t.trend.toLowerCase())}.</div>
    <div class="qdr-s">Daily closes to ${esc(day(t.asOf))} (${esc(t.source)}). Describes past prices, not a forecast.</div></div>`;
}

function aiCard(r: Report | null, state: 'loading' | 'off' | 'error' | 'ok', msg = ''): string {
  if (state === 'ok' && r) {
    const v = r.report.verdict;
    return `<div class="qdr-card"><span class="qdr-k">AI analyst view · ${esc(v.horizon)}</span><div class="qdr-v ${cls(v.rating)}">${arrow(v.rating)}${esc(v.rating)}</div>
      <div class="qdr-s">Confidence: <b>${esc(v.confidence)}</b>. ${esc(v.reasons[0] || '')}</div><div class="qdr-s">An AI opinion from the figures below, for learning. Not a recommendation to buy or sell; Quantera is not a registered investment adviser or research analyst.</div></div>`;
  }
  const body = state === 'loading' ? 'Reading the filings, ratios, competitors, technicals and news…' : esc(msg);
  return `<div class="qdr-card"><span class="qdr-k">AI analyst view</span><div class="qdr-v">${state === 'loading' ? '…' : '—'}</div><div class="qdr-s">${body}</div></div>`;
}

function techTable(t: Tech): string {
  const L = t.levels, R = t.returns;
  return `<h4 style="margin:16px 0 6px;font-size:15px">Technical analysis</h4>
    <div class="qdr-lv"><div><span>Last close</span><b>$${num(t.price)}</b></div><div><span>Support (20-day low)</span><b>$${num(L.support20d)}</b></div><div><span>Resistance (20-day high)</span><b>$${num(L.resistance20d)}</b></div>
    <div><span>52-week range</span><b>$${num(L.low52w)}–$${num(L.high52w)}</b></div><div><span>From 52-week high</span><b>${pc(L.fromHighPct)}</b></div>
    <div><span>Return 1m / 3m</span><b>${pc(R['1m'])} / ${pc(R['3m'])}</b></div><div><span>Return 6m / 1y</span><b>${pc(R['6m'])} / ${pc(R['1y'])}</b></div>
    <div><span>Volatility (annualised)</span><b>${num(t.volatilityPct, 1)}%</b></div><div><span>Average daily range (ATR 14)</span><b>$${num(t.atr14)}</b></div></div>
    <div class="ov"><table><tr><th>Indicator</th><th style="text-align:right">Value</th><th>Signal</th><th>How to read it</th></tr>
    ${t.signals.map(s => `<tr><td>${esc(s.name)}</td><td class="n">${num(s.value)}</td><td><span class="sg ${s.signal}">${s.signal === 'buy' ? '▲ Buy' : s.signal === 'sell' ? '▼ Sell' : '● Neutral'}</span></td><td><small>${esc(s.rule)}</small></td></tr>`).join('')}</table></div>
    <div class="qdr-note">${esc(t.method)} <a href="#/lesson/rsi">Lesson: reading RSI</a> · <a href="#/trade/${enc(t.symbol)}">Open the chart</a></div>`;
}

function reportBody(r: Report): string {
  const p = r.report;
  return `<div class="qdr-rep"><h4 style="margin-top:4px">The report</h4>${p.headline ? `<p class="qdr-hl">${esc(p.headline)}</p>` : ''}<p class="lead">${esc(p.summary)}</p>
    ${p.sections.map(s => `<h4>${esc(s.title)}</h4>${li(s.points)}`).join('')}
    <div class="qdr-2" style="margin-top:14px"><div><h4 style="margin-top:0">▲ Bull case</h4>${li(p.bull)}</div><div><h4 style="margin-top:0">▼ Bear case</h4>${li(p.bear)}</div></div>
    <div class="qdr-2" style="margin-top:12px"><div><h4 style="margin-top:0">Key risks</h4>${li(p.risks)}</div><div><h4 style="margin-top:0">What to watch</h4>${li(p.catalysts)}</div></div>
    <h4>Verdict: ${esc(p.verdict.rating)} (${esc(p.verdict.confidence.toLowerCase())} confidence)</h4>${li(p.verdict.reasons)}
    ${p.verdict.changeMind ? `<p><b>What would change this view:</b> ${esc(p.verdict.changeMind)}</p>` : ''}
    <h4>Sources</h4><ul>${r.sources.map(s => `<li>${esc(s.what)}: ${esc(s.from)} · ${esc(day(s.asOf))}</li>`).join('')}</ul>
    ${r.notAvailable.length ? `<p class="qdr-note">Not available for this report: ${esc(r.notAvailable.join(', '))}.</p>` : ''}
    <p class="qdr-note">Written by AI on ${esc(new Date(r.generatedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' }))} from the data listed above, and refreshed at most every 12 hours. AI can misread figures; check the sources before relying on anything. This is general information for learning, not a personal recommendation to buy, sell or hold any security. Quantera is not a registered investment adviser, broker or research analyst. Do your own research and consider speaking to a licensed professional.</p></div>`;
}

/** Fill `host` with the deep research panel for one company. */
async function renderOne(host: HTMLElement, sym: string, full: boolean): Promise<void> {
  const box = document.createElement('div'); box.className = 'qdr-body'; box.dataset.s = sym;
  host.querySelector('.qdr-body')?.remove(); host.appendChild(box);
  box.innerHTML = `<div class="qdr-cards">${aiCard(null, 'loading')}<div class="qdr-card"><div class="qdr-sk" style="height:70px"></div></div><div class="qdr-card"><div class="qdr-sk" style="height:70px"></div></div></div><div id="qdrt"></div><div id="qdrr"></div>`;
  const [t, f] = await Promise.all([tech(sym), fund(sym)]);
  if (!box.isConnected) return;
  const T: Tech | null = t.ok ? t.b : null, F: Fund | null = f.ok ? f.b : null;
  const cards = box.querySelector('.qdr-cards') as HTMLElement;
  const paint = (ai: string) => { cards.innerHTML = ai + analystCard(F) + techCard(T, t.b?.error); };
  paint(aiCard(null, 'loading'));
  (box.querySelector('#qdrt') as HTMLElement).innerHTML = T ? techTable(T) : '';
  const rr = box.querySelector('#qdrr') as HTMLElement;
  rr.innerHTML = `<div class="qdr-sk" style="margin-top:14px;height:${full ? 320 : 160}px"></div>`;
  const r = await report(sym);
  if (!box.isConnected) return;
  if (r.ok) { paint(aiCard(r.b, 'ok')); rr.innerHTML = reportBody(r.b); track('deep_research_ok'); return; }
  const off = r.status === 503 || /key|configured|credit|billing|permission/i.test(r.b?.error || '');
  paint(aiCard(null, off ? 'off' : 'error', off ? 'The AI writer is offline right now, so there’s no written report yet. The analyst consensus and technicals still work.' : (r.b?.error || 'The report could not be written right now. Please try again later.')));
  rr.innerHTML = off ? '' : `<button class="chip" id="qdrretry" style="margin-top:12px">Try the report again</button>`;
  rr.querySelector('#qdrretry')?.addEventListener('click', () => renderOne(host, sym, full));
}

/** Panel for one or more companies (tabs when several). `syms` empty shows a company finder. */
function panel(host: HTMLElement, syms: string[], opts: { title?: string; full?: boolean } = {}): void {
  const key = syms.join(',');
  if (host.dataset.qdr === key && host.querySelector('.qdr')) return; // already showing these companies
  host.dataset.qdr = key;
  host.innerHTML = `<div class="qdr"><div class="qdr-h"><h3>${esc(opts.title || 'AI deep research')}</h3><span class="tag">AI</span>${syms.length && !opts.full ? `<a href="#/report/${enc(syms[0])}" id="qdrfull">Full report →</a>` : ''}</div>
    <p class="qdr-sub">An AI analyst’s Buy / Hold / Sell view, Wall Street’s consensus and the technical picture side by side, then a full report built from SEC filings, ratios, competitors, prices and news. Every input is listed with its source.</p>
    ${syms.length > 1 ? `<div class="qdr-tabs" role="tablist">${syms.map((s, i) => `<button role="tab" aria-selected="${i === 0}" class="${i === 0 ? 'on' : ''}" data-s="${esc(s)}">${esc(s)}</button>`).join('')}</div>` : ''}</div>`;
  const q = host.querySelector('.qdr') as HTMLElement;
  if (!syms.length) {
    q.insertAdjacentHTML('beforeend', `<div class="qdr-find"><input id="qdrq" placeholder="Ticker, e.g. AAPL" aria-label="Ticker" maxlength="12"><button class="btn p" id="qdrgo">Research it</button></div>`);
    const go = () => { const s = (q.querySelector('#qdrq') as HTMLInputElement).value.trim().toUpperCase(); if (/^[A-Z][A-Z0-9.\-^=]{0,11}$/.test(s)) location.hash = '#/report/' + enc(s); };
    (q.querySelector('#qdrgo') as HTMLButtonElement).onclick = go;
    (q.querySelector('#qdrq') as HTMLInputElement).onkeydown = e => { if (e.key === 'Enter') go(); };
    return;
  }
  q.querySelectorAll<HTMLButtonElement>('.qdr-tabs button').forEach(b => b.onclick = () => {
    q.querySelectorAll('.qdr-tabs button').forEach(x => { x.classList.toggle('on', x === b); x.setAttribute('aria-selected', String(x === b)); });
    const a = q.querySelector('#qdrfull') as HTMLAnchorElement | null; if (a) a.href = '#/report/' + enc(b.dataset.s!);
    renderOne(q, b.dataset.s!, !!opts.full);
  });
  renderOne(q, syms[0], !!opts.full);
}

function reportPage(sym?: string): void {
  const m = document.getElementById('m')!;
  const recent = LS<string[]>('q_reps', []).filter(s => s !== sym).slice(0, 8);
  m.innerHTML = `<div class="pan" id="qdrpage"></div>${recent.length || !sym ? `<div class="pan" style="margin-top:12px"><h3 style="margin:0 0 8px;font-size:16px">${recent.length ? 'Your recent reports' : 'Popular companies'}</h3><div class="pre">${(recent.length ? recent : ['AAPL', 'NVDA', 'MSFT', 'AMZN', 'TSLA', 'JPM', 'KO']).map(s => `<a class="chip" href="#/report/${enc(s)}">${esc(s)}</a>`).join('')}</div></div>` : ''}`;
  const host = m.querySelector('#qdrpage') as HTMLElement;
  if (sym) SS('q_reps', [sym, ...LS<string[]>('q_reps', []).filter(s => s !== sym)].slice(0, 12));
  panel(host, sym ? [sym] : [], { title: sym ? `AI deep research: ${sym}` : 'AI deep research', full: true });
}

export function initDeepResearch(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qdr-css">${CSS}</style>`);
  // News stories: the panel replaces the old explainer lenses, for the companies the story names.
  (window as any).qDeep = (host: HTMLElement | null, ents: { symbol?: string }[]) => {
    if (!host) return;
    const syms = [...new Set((ents || []).map(e => String(e.symbol || '').toUpperCase()).filter(s => /^[A-Z][A-Z0-9.\-^=]{0,11}$/.test(s)))].slice(0, 5);
    if (!syms.length) {
      if (host.dataset.qdr === '') return;
      host.dataset.qdr = '';
      host.innerHTML = `<div class="qdr"><div class="qdr-h"><h3>AI deep research</h3><span class="tag">AI</span></div><p class="qdr-sub">This story doesn’t name a listed company. Research any company: an AI analyst view, Wall Street’s consensus, technicals and a full sourced report.</p>
        <div class="qdr-find"><input id="qdrq2" placeholder="Ticker, e.g. AAPL" aria-label="Ticker" maxlength="12"><button class="btn p" id="qdrgo2">Research it</button></div></div>`;
      const go = () => { const s = (host.querySelector('#qdrq2') as HTMLInputElement).value.trim().toUpperCase(); if (/^[A-Z][A-Z0-9.\-^=]{0,11}$/.test(s)) location.hash = '#/report/' + enc(s); };
      (host.querySelector('#qdrgo2') as HTMLButtonElement).onclick = go;
      (host.querySelector('#qdrq2') as HTMLInputElement).onkeydown = e => { if (e.key === 'Enter') go(); };
      return;
    }
    panel(host, syms, { title: 'AI deep research on this story’s companies' });
  };
  PAGES.report = (a?: string) => { track('view_report'); reportPage(a && a !== 'report' ? decodeURIComponent(a).toUpperCase().slice(0, 12) : undefined); };
  HUB.report = 'research';
  TT.report = ['Deep research', 'AI reports with a Buy / Hold / Sell view, analyst consensus and technicals.'];
  if (!SUBT.research?.some(x => x[1] === 'report')) SUBT.research?.unshift(['Deep research', 'report']);
  // Each stock's Research tab gets the same panel.
  const main = document.getElementById('m');
  if (main) new MutationObserver(() => {
    const res = document.getElementById('qres'), mm = /^#\/asset\/([^/]+)/.exec(location.hash);
    if (!res || !mm || document.getElementById('qdrres')) return;
    const host = document.createElement('div'); host.id = 'qdrres'; host.className = 'pan'; host.style.marginBottom = '14px';
    res.insertBefore(host, res.querySelector('.qr-jump')?.nextSibling || res.firstChild);
    // Only start the work when the Research tab is open.
    const start = () => { if ((document.getElementById('m') as HTMLElement).dataset.tab === 'research' && !host.dataset.qdr) panel(host, [decodeURIComponent(mm[1]).toUpperCase()]); };
    start(); new MutationObserver(start).observe(document.getElementById('m')!, { attributes: true, attributeFilter: ['data-tab'] });
  }).observe(main, { childList: true, subtree: true });
  if (/^#\/report/.test(location.hash)) route();
}
