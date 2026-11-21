// Same news. Different world: why one company moves 5% and another 25% on the same news.
// - window.qReaction(host, {symbols, at, headline}) renders the analysis anywhere (news stories use it).
// - #/insights is the Insights page: run the analysis on any companies and date, start from today's multi-company
//   stories, and build up a size-versus-reaction picture from your own analyses.
// The numbers come from /api/reaction (backend Reaction.java). Observations and interpretations are kept apart.

interface Row {
  symbol: string; name: string; sector?: string; marketCap?: number; pe?: number; day0: string; prevClose: number; close: number;
  move: number; market: number; abnormal: number; typicalDay: number; sigmas: number; beta: number; estimationDays: number; benchmarkMove?: number;
  threeDay?: { move: number; market: number; abnormal: number } | null; recovery?: { sessionsChecked: number; recoveredAfter: number | null; nowVsBefore: number } | null;
  volumeVsAverage?: number | null; runUp20d?: number | null; namedInHeadline: boolean;
}
interface Res {
  day0: string; benchmark: string; companies: Row[]; skipped: { symbol: string; reason: string }[];
  gap: { bigger: string; smaller: string; points: number }; reasons: { factor: string; kind: 'measured' | 'pattern'; text: string }[]; method: string; caveat: string;
}
export interface Ask { symbols: string[]; at: string; headline?: string }

const CSS = `
.qrx{margin-top:4px}.qrx .qrx-hd{font-family:Fraunces,Georgia,serif;font-weight:300;font-size:clamp(22px,2.4vw,28px);line-height:1.2;margin:0 0 4px}
.qrx .qrx-sub{color:var(--mu);font-size:13.5px;margin:0 0 16px}
.qrx-bars{display:grid;gap:14px;margin:6px 0 18px}
.qrx-b{display:grid;grid-template-columns:minmax(92px,150px) minmax(0,1fr) 84px;gap:12px;align-items:center}
.qrx-b .nm b{display:block;font-size:15px}.qrx-b .nm small{color:var(--mu);font-size:12px;display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.qrx-trk{position:relative;height:30px;border-radius:8px;background:rgba(var(--fgc),.04);overflow:hidden}
.qrx-zero{position:absolute;top:0;bottom:0;width:1px;background:rgba(var(--fgc),.35)}
.qrx-band{position:absolute;top:0;bottom:0;background:rgba(var(--fgc),.07);border-left:1px dashed rgba(var(--fgc),.25);border-right:1px dashed rgba(var(--fgc),.25)}
.qrx-seg{position:absolute;top:7px;height:16px}.qrx-seg.m{background:rgba(142,151,168,.75)}.qrx-seg.up{background:#4fd1a5}.qrx-seg.dn{background:#e86a6a}
.qrx-b .val{text-align:right;font-variant-numeric:tabular-nums;font-size:17px;font-weight:600}.qrx-b .val small{display:block;font-size:11.5px;color:var(--mu);font-weight:400}
.qrx-key{display:flex;flex-wrap:wrap;gap:14px;font-size:12px;color:var(--mu);margin:-6px 0 16px}.qrx-key i{display:inline-block;width:12px;height:10px;border-radius:2px;margin-right:5px;vertical-align:-1px}
.qrx-why{display:grid;gap:10px;margin:8px 0 18px}.qrx-r{border:1px solid var(--ln);border-radius:12px;padding:12px 14px;background:var(--s1)}
.qrx-r b{font-size:14.5px}.qrx-r p{margin:5px 0 0;font-size:14px;line-height:1.55}
.qrx-tag{display:inline-block;font-size:11px;letter-spacing:.04em;text-transform:uppercase;padding:2px 8px;border-radius:999px;margin-left:8px;vertical-align:1px;border:1px solid var(--ln);color:var(--mu)}
.qrx-tag.measured{color:#4fd1a5;border-color:rgba(79,209,165,.35)}.qrx-tag.pattern{color:#e8c77a;border-color:rgba(232,199,122,.35)}
.qrx-note{font-size:12.5px;color:var(--mu);line-height:1.55;margin:10px 0 0}
.qrx h4{margin:18px 0 8px;font-size:15px}
.qrx .ov{overflow-x:auto}
.qrx-sk{height:120px;border-radius:12px;background:linear-gradient(90deg,rgba(var(--fgc),.04),rgba(var(--fgc),.08),rgba(var(--fgc),.04));background-size:200% 100%;animation:qrxsk 1.4s linear infinite}
@keyframes qrxsk{to{background-position:-200% 0}}@media (prefers-reduced-motion:reduce){.qrx-sk{animation:none}}
@media (max-width:640px){.qrx-b{grid-template-columns:minmax(0,1fr) 76px}.qrx-b .qrx-trk{grid-column:1/-1;grid-row:2}}
.qin-form{display:grid;grid-template-columns:minmax(0,2fr) minmax(0,1fr) auto;gap:10px;align-items:end}
.qin-form label{display:flex;flex-direction:column;gap:5px;font-size:12.5px;color:var(--mu)}
.qin-form input{font:inherit;font-size:14px;padding:10px 12px!important;min-width:0}
@media (max-width:760px){.qin-form{grid-template-columns:minmax(0,1fr)}}
.qin-pre{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0 0}#m .qin-pre .chip{max-width:100%;min-width:0;overflow:hidden;text-align:left}#m .qin-pre .chip span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;min-width:0}
.qin-ev{display:grid;gap:10px;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));margin-top:10px}
.qin-ev button{text-align:left;border:1px solid var(--ln);border-radius:12px;padding:12px 14px;background:var(--s1);color:inherit;font:inherit;cursor:pointer;display:flex;flex-direction:column;gap:5px}
.qin-ev button:hover{border-color:rgba(var(--acc),.5)}.qin-ev b{font-size:14px;line-height:1.35}.qin-ev small{color:var(--mu);font-size:12px}
.qin-lede{font-size:16px;line-height:1.6;max-width:760px;margin:6px 0 18px}
.qnx-opts{grid-template-columns:repeat(auto-fit,minmax(230px,1fr))!important}
.qin-sc svg{width:100%;max-width:720px;height:auto;display:block}.qin-sc text{fill:var(--mu);font-size:11px}`;

const enc = encodeURIComponent;
const sgn = (x: number | null | undefined, d = 1) => x == null || !isFinite(x) ? '—' : (x > 0 ? '+' : x < 0 ? '−' : '') + fmt(Math.abs(x), d) + '%';
const cap = (c?: number) => !c ? '—' : c >= 1e12 ? '$' + fmt(c / 1e12, 2) + 'T' : c >= 1e9 ? '$' + fmt(c / 1e9, 1) + 'B' : '$' + fmt(c / 1e6, 0) + 'M';
const dayLabel = (d: string) => { try { return new Date(d + 'T12:00:00Z').toLocaleDateString('en-US', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' }); } catch { return d; } };

// ---------- saved analyses (this device only), for the size-versus-reaction picture ----------
const HIST = 'q_rxh';
type Pt = { s: string; d: string; cap: number; abn: number; z: number };
function remember(r: Res): void {
  const pts = LS<Pt[]>(HIST, []).filter(p => p.d !== r.day0 || !r.companies.some(c => c.symbol === p.s));
  for (const c of r.companies) if (c.marketCap) pts.push({ s: c.symbol, d: c.day0, cap: c.marketCap, abn: c.abnormal, z: c.sigmas });
  SS(HIST, pts.slice(-120));
}

function bars(r: Res): string {
  const span = Math.max(2, ...r.companies.map(c => Math.max(Math.abs(c.move), Math.abs(c.market) + Math.abs(c.abnormal), c.typicalDay))) * 1.08;
  const X = (v: number) => 50 + (v / span) * 50; // percent across the track, zero in the middle
  return `<div class="qrx-bars">${r.companies.map(c => {
    const segs: string[] = [];
    // market part first from zero, then the company-specific part continues from where it ends
    const m0 = X(0), m1 = X(c.market), a1 = X(c.market + c.abnormal);
    if (Math.abs(c.market) > 0.01) segs.push(`<div class="qrx-seg m" style="left:${Math.min(m0, m1)}%;width:${Math.abs(m1 - m0)}%" title="Market part ${sgn(c.market)}"></div>`);
    segs.push(`<div class="qrx-seg ${c.abnormal >= 0 ? 'up' : 'dn'}" style="left:${Math.min(m1, a1)}%;width:${Math.max(0.4, Math.abs(a1 - m1))}%" title="Company-specific ${sgn(c.abnormal)}"></div>`);
    return `<div class="qrx-b"><div class="nm"><b><a href="#/asset/${enc(c.symbol)}">${esc(c.symbol)}</a></b><small title="${esc(c.name)}">${esc(c.name)} · ${cap(c.marketCap)}</small></div>
      <div class="qrx-trk" role="img" aria-label="${esc(c.symbol)}: moved ${sgn(c.move)}; market part ${sgn(c.market)}, company-specific ${sgn(c.abnormal)}; normal day ±${fmt(c.typicalDay, 1)}%">
        <div class="qrx-band" style="left:${X(-c.typicalDay)}%;width:${X(c.typicalDay) - X(-c.typicalDay)}%"></div><div class="qrx-zero" style="left:50%"></div>${segs.join('')}</div>
      <div class="val ${c.move >= 0 ? 'up' : 'dn'}">${sgn(c.move)}<small>${fmt(Math.abs(c.sigmas), 1)}× normal</small></div></div>`;
  }).join('')}</div>
  <div class="qrx-key"><span><i style="background:rgba(142,151,168,.75)"></i>Whole-market part</span><span><i style="background:#4fd1a5"></i><i style="background:#e86a6a;margin-left:-3px"></i>Company-specific part</span><span><i style="background:rgba(var(--fgc),.12);border:1px dashed rgba(var(--fgc),.35)"></i>A normal day's range</span></div>`;
}

function table(r: Res): string {
  const rec = (c: Row) => !c.recovery ? '—' : c.recovery.recoveredAfter != null ? `Back after ${c.recovery.recoveredAfter} day${c.recovery.recoveredAfter > 1 ? 's' : ''}` : `Not within ${c.recovery.sessionsChecked} days (${sgn(c.recovery.nowVsBefore)})`;
  return `<div class="ov"><table><tr><th>Company</th><th class="r">Move</th><th class="r">Market part</th><th class="r">Company-specific</th><th class="r">Normal day</th><th class="r">× normal</th><th class="r">Beta</th><th class="r">Days 0–2</th><th>Back to pre-news price</th><th class="r">Volume vs usual</th><th class="r">Month before</th><th class="r">Size (today)</th><th class="r">P/E (today)</th><th>In headline</th></tr>
  ${r.companies.map(c => `<tr><td><a href="#/asset/${enc(c.symbol)}"><b>${esc(c.symbol)}</b></a><br><small class="mu">${esc(c.sector || '')}</small></td>
    <td class="r ${c.move >= 0 ? 'up' : 'dn'}">${sgn(c.move)}</td><td class="r">${sgn(c.market)}</td><td class="r"><b>${sgn(c.abnormal)}</b></td><td class="r">±${fmt(c.typicalDay, 1)}%</td><td class="r">${fmt(Math.abs(c.sigmas), 1)}×</td>
    <td class="r">${fmt(c.beta, 2)}</td><td class="r">${c.threeDay ? sgn(c.threeDay.move) : '—'}</td><td>${rec(c)}</td><td class="r">${c.volumeVsAverage != null ? fmt(c.volumeVsAverage, 1) + '×' : '—'}</td>
    <td class="r">${sgn(c.runUp20d)}</td><td class="r">${cap(c.marketCap)}</td><td class="r">${c.pe ? fmt(c.pe, 0) : '—'}</td><td>${c.namedInHeadline ? 'Yes' : 'No'}</td></tr>`).join('')}</table></div>`;
}

function view(r: Res): string {
  const big = r.companies[0], small = r.companies[r.companies.length - 1];
  return `<div class="qrx"><h3 class="qrx-hd">Same news, different reactions: ${esc(big.symbol)} ${sgn(big.move)} vs ${esc(small.symbol)} ${sgn(small.move)}</h3>
    <p class="qrx-sub">First trading session after the news: ${esc(dayLabel(r.day0))}. Compared with the ${esc(r.benchmark)}${big.benchmarkMove != null ? `, which moved ${sgn(big.benchmarkMove, 2)}` : ''}.</p>
    ${bars(r)}
    <h4>Why the gap</h4><div class="qrx-why">${r.reasons.map(x => `<div class="qrx-r"><b>${esc(x.factor)}</b><span class="qrx-tag ${x.kind}">${x.kind === 'measured' ? 'Measured' : 'Typical pattern'}</span><p>${esc(x.text)}</p></div>`).join('')}</div>
    <h4>The numbers</h4>${table(r)}
    ${r.skipped.length ? `<p class="qrx-note">Left out: ${r.skipped.map(s => `${esc(s.symbol)} (${esc(s.reason)})`).join('; ')}.</p>` : ''}
    <p class="qrx-note"><b>How this is measured.</b> ${esc(r.method)}</p><p class="qrx-note"><b>What it can’t tell you.</b> ${esc(r.caveat)}</p></div>`;
}

export async function runReaction(host: HTMLElement, ask: Ask): Promise<void> {
  host.innerHTML = '<div class="qrx-sk" aria-label="Measuring the reactions"></div>';
  const q = `/api/reaction?symbols=${enc(ask.symbols.join(','))}&at=${enc(ask.at)}${ask.headline ? `&headline=${enc(ask.headline.slice(0, 300))}` : ''}`;
  try {
    const r = await fetch(q), j = await r.json().catch(() => ({}));
    if (!r.ok) { host.innerHTML = `<p class="mu">${esc(j.error || 'The reaction analysis is unavailable right now. Please try again.')}</p>`; return; }
    host.innerHTML = view(j as Res); remember(j as Res); track('reaction_run');
  } catch { host.innerHTML = '<p class="mu">Could not reach the data service. Please try again.</p>'; }
}

// ---------- the Insights page ----------
function scatter(): string {
  const pts = LS<Pt[]>(HIST, []).filter(p => p.cap > 0 && isFinite(p.z));
  if (pts.length < 2) return '<p class="mu">Run a few comparisons above and each company you measure appears here, so you can see whether smaller companies reacted more strongly in the events you looked at.</p>';
  const W = 640, H = 300, L = 46, B = 34, lx = pts.map(p => Math.log10(p.cap)), x0 = Math.floor(Math.min(...lx)), x1 = Math.ceil(Math.max(...lx)) + (Math.ceil(Math.max(...lx)) === Math.floor(Math.min(...lx)) ? 1 : 0);
  const zmax = Math.max(3, ...pts.map(p => Math.abs(p.z))) * 1.1;
  const X = (v: number) => L + (v - x0) / (x1 - x0) * (W - L - 40), Y = (z: number) => H - B - Math.abs(z) / zmax * (H - B - 12);
  const ticks: string[] = []; for (let e = x0; e <= x1; e++) ticks.push(`<line x1="${X(e)}" x2="${X(e)}" y1="12" y2="${H - B}" stroke="rgba(142,151,168,.15)"/><text x="${X(e)}" y="${H - B + 16}" text-anchor="middle">${cap(10 ** e)}</text>`);
  for (let z = 0; z <= zmax; z += zmax > 8 ? 2 : 1) ticks.push(`<line x1="${L}" x2="${W - 12}" y1="${Y(z)}" y2="${Y(z)}" stroke="rgba(142,151,168,.12)"/><text x="${L - 6}" y="${Y(z) + 4}" text-anchor="end">${z}×</text>`);
  const dots = pts.map(p => `<circle cx="${X(Math.log10(p.cap)).toFixed(1)}" cy="${Y(p.z).toFixed(1)}" r="5" fill="${p.abn >= 0 ? '#4fd1a5' : '#e86a6a'}" fill-opacity=".8"><title>${esc(p.s)} on ${esc(p.d)}: company-specific ${sgn(p.abn)}, ${fmt(Math.abs(p.z), 1)}× its normal day, size ${cap(p.cap)}</title></circle>`).join('');
  // rank correlation only once there is enough to say anything, and always with its limits
  let corr = '';
  if (pts.length >= 10) {
    const rk = (a: number[]) => { const o = a.map((v, i) => [v, i]).sort((x, y) => x[0] - y[0]); const r = new Array(a.length); o.forEach((v, i) => r[v[1]] = i); return r; };
    const a = rk(lx), b = rk(pts.map(p => Math.abs(p.z))), n = pts.length; let d2 = 0; for (let i = 0; i < n; i++) d2 += (a[i] - b[i]) ** 2;
    const rho = 1 - 6 * d2 / (n * (n * n - 1));
    corr = `<p class="qrx-note">Across these ${n} measurements, the rank correlation between company size and reaction strength is <b>${fmt(rho, 2)}</b> (−1 would mean smaller companies always reacted more, 0 no relationship). This only describes the events you chose; it is not a general finding.</p>`;
  }
  return `<div class="qin-sc"><svg viewBox="0 0 ${W} ${H}" role="img" aria-label="Company size against reaction strength for ${pts.length} measurements">${ticks.join('')}${dots}<text x="${(W + L) / 2}" y="${H - 4}" text-anchor="middle">Company size (market value, log scale)</text><text transform="translate(12 ${(H - B) / 2}) rotate(-90)" text-anchor="middle">Reaction × normal day</text></svg></div>
    <p class="qrx-note">Each dot is one company in one of your comparisons (green: reacted up, red: down). Saved on this device only. <button class="btn" id="qinclr" style="margin-left:6px">Clear</button></p>${corr}`;
}

// ---------- Quantera's own dataset, collected by the server (backend Tracker.java) ----------
interface Bucket { name: string; range: string; n: number; medianSigmas: number | null; medianAbnormal: number | null; shareOver2: number | null }
interface Trk { rows: number; measured: number; events: number; from?: string; to?: string; buckets: Bucket[]; rankCorrelation: number | null; method: string; caveat: string }
async function tracker(box: HTMLElement): Promise<void> {
  const head = '<h3 style="margin-top:0">Reaction tracker: do smaller companies react more?</h3>';
  let t: Trk;
  try { const r = await fetch('/api/reaction/tracker'); if (!r.ok) throw new Error(); t = await r.json(); }
  catch { box.innerHTML = head + '<p class="mu" style="margin:0">The tracker’s data isn’t available right now. Please check back later.</p>'; return; }
  const day = (d?: string) => d ? new Date(d + 'T12:00:00Z').toLocaleDateString('en-US', { day: 'numeric', month: 'short', year: 'numeric' }) : '';
  if (t.measured < 10) {
    box.innerHTML = head + `<p style="margin:0 0 8px">Quantera now measures, automatically, how every company reacts to news stories that name two or more of them, and stores the results as its own research dataset. ${t.measured ? `So far: <b>${t.measured} companies across ${t.events} stories</b>.` : 'The first stories are being measured now.'} Results by company size appear here once there are at least 10 measurements.</p><p class="qrx-note">${esc(t.method)}</p>`;
    return;
  }
  const max = Math.max(1, ...t.buckets.map(b => b.medianSigmas || 0));
  const bars = t.buckets.map(b => `<div class="qrx-b"><div class="nm"><b>${esc(b.name)}</b><small>${esc(b.range)} · ${b.n} measured</small></div>
    <div class="qrx-trk" role="img" aria-label="${esc(b.name)} companies: median reaction ${b.medianSigmas ?? '—'} times their normal day"><div class="qrx-seg up" style="left:0;width:${b.medianSigmas ? (b.medianSigmas / max) * 100 : 0}%;top:7px"></div></div>
    <div class="val">${b.medianSigmas != null ? fmt(b.medianSigmas, 1) + '×' : '—'}<small>median vs normal day</small></div></div>`).join('');
  const rho = t.rankCorrelation;
  const read = rho == null ? '' : `<p style="margin:12px 0 0">Across ${t.measured} measurements, the rank correlation between company size and reaction strength is <b>${fmt(rho, 2)}</b> ${rho <= -0.2 ? '(smaller companies have tended to react more)' : rho >= 0.2 ? '(larger companies have tended to react more)' : '(no clear relationship so far)'}.</p>`;
  box.innerHTML = head + `<p class="mu" style="margin:0 0 12px">Quantera’s own dataset: <b>${t.measured} companies across ${t.events} news stories</b>, ${esc(day(t.from))} to ${esc(day(t.to))}, measured automatically. Bars show how big the company-specific reaction was compared with each company’s normal day.</p>
    <div class="qrx-bars" style="margin-bottom:6px">${bars}</div>
    <div class="ov"><table><tr><th>Size</th><th class="r">Companies</th><th class="r">Median reaction (× normal day)</th><th class="r">Median company-specific move</th><th class="r">Reactions over 2× normal</th></tr>
    ${t.buckets.map(b => `<tr><td><b>${esc(b.name)}</b> <small class="mu">${esc(b.range)}</small></td><td class="r">${b.n}</td><td class="r">${b.medianSigmas != null ? fmt(b.medianSigmas, 2) + '×' : '—'}</td><td class="r">${b.medianAbnormal != null ? fmt(b.medianAbnormal, 2) + '%' : '—'}</td><td class="r">${b.shareOver2 != null ? fmt(b.shareOver2, 0) + '%' : '—'}</td></tr>`).join('')}</table></div>
    ${read}<p class="qrx-note"><b>How it’s collected.</b> ${esc(t.method)}</p><p class="qrx-note"><b>What it can’t tell you.</b> ${esc(t.caveat)}</p>`;
}

// a real, documented event to start from: Nvidia's Q2 FY2027 results, released after the close on 26 Aug 2026
const PRESETS: { label: string; ask: Ask }[] = [
  { label: 'Nvidia results, 26 Aug 2026: NVDA vs AMD, SMCI, AVGO', ask: { symbols: ['NVDA', 'AMD', 'SMCI', 'AVGO'], at: '2026-08-26T21:00:00Z', headline: 'Nvidia reports Q2 FY2027 results' } },
];

async function insights(): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  document.title = 'Insights: why the same news moves stocks differently | Quantera';
  m.innerHTML = `<h1>Insights</h1>
    <p class="qin-lede"><b>Same news. Different world.</b> When a story breaks, one company moves 5% and another 25%. Quantera measures how much of each move was the whole market, how unusual it was for that particular stock, and which differences between the companies usually explain the gap.</p>
    <div class="pan"><h3 style="margin-top:0">Compare reactions to the same news</h3>
      <div class="qin-form"><label>Companies (2 to 8 tickers)<input id="qins" placeholder="e.g. NVDA, AMD, SMCI" autocomplete="off"></label>
      <label>Date of the news<input id="qind" type="date"></label><button class="btn p" id="qing">Compare</button></div>
      <label style="display:flex;flex-direction:column;gap:5px;font-size:12.5px;color:var(--mu);margin-top:10px">Headline (optional: used to see which companies the news names)<input id="qinh" maxlength="300" style="font:inherit;font-size:14px;padding:10px 12px!important"></label>
      <div class="qin-pre">${PRESETS.map((p, i) => `<button class="chip" data-p="${i}" title="${esc(p.label)}"><span>${esc(p.label)}</span></button>`).join('')}</div>
      <div id="qinout" style="margin-top:18px"></div></div>
    <div class="pan"><h3 style="margin-top:0">Today’s stories that moved more than one company</h3><p class="mu" style="margin:0">Pick one to compare how each company reacted.</p><div class="qin-ev" id="qinev"><div class="qrx-sk" style="height:70px"></div></div></div>
    <div class="pan" id="qtrk"><h3 style="margin-top:0">Reaction tracker: do smaller companies react more?</h3><div class="qrx-sk" style="height:120px"></div></div>
    <div class="pan"><h3 style="margin-top:0">Your comparisons</h3><p class="mu" style="margin:0 0 10px">Every comparison you run adds a dot here (saved on this device only).</p><div id="qinsc">${scatter()}</div></div>
    <div class="pan"><h3 style="margin-top:0">Method and limits</h3><p class="qrx-note" style="margin-top:0">Daily closing prices only, so news during the trading day is measured over the whole day. The market part uses each stock’s beta against the S&P 500 from the 120 trading days before the news. “Normal day” is the usual size of the stock’s company-specific moves over the same period. Reasons marked <b>Measured</b> are calculations; reasons marked <b>Typical pattern</b> are well-documented tendencies that may not apply to a particular event. Other news on the same day can move a stock, so none of this proves cause. Education, not investment advice. <a href="#/lesson/capm">Lesson: beta and the market</a></p></div>`;
  const out = m.querySelector('#qinout') as HTMLElement, S = m.querySelector('#qins') as HTMLInputElement, D = m.querySelector('#qind') as HTMLInputElement, Hd = m.querySelector('#qinh') as HTMLInputElement;
  D.max = new Date().toISOString().slice(0, 10);
  const refresh = () => { const sc = m.querySelector('#qinsc'); if (sc) { sc.innerHTML = scatter(); bindClear(); } };
  const bindClear = () => { const c = m.querySelector('#qinclr') as HTMLButtonElement | null; if (c) c.onclick = () => { SS(HIST, []); refresh(); }; };
  bindClear();
  const go = async (ask: Ask) => {
    S.value = ask.symbols.join(', '); D.value = ask.at.slice(0, 10); Hd.value = ask.headline || '';
    await runReaction(out, ask); refresh();
  };
  (m.querySelector('#qing') as HTMLButtonElement).onclick = () => {
    const syms = S.value.toUpperCase().split(/[\s,;]+/).filter(s => /^[A-Z0-9.\-^=]{1,12}$/.test(s)).slice(0, 8);
    if (syms.length < 2) { out.innerHTML = '<p class="mu">Enter at least two tickers, separated by commas.</p>'; return; }
    if (!D.value) { out.innerHTML = '<p class="mu">Choose the date of the news.</p>'; return; }
    go({ symbols: syms, at: D.value, headline: Hd.value.trim() });
  };
  S.onkeydown = e => { if (e.key === 'Enter') (m.querySelector('#qing') as HTMLButtonElement).click(); };
  m.querySelectorAll<HTMLButtonElement>('[data-p]').forEach(b => b.onclick = () => go(PRESETS[+b.dataset.p!].ask));
  const pre = /^#\/insights\/(.+)$/.exec(location.hash);
  if (pre) { const [s, at] = decodeURIComponent(pre[1]).split('@'); if (s && at) { const syms = s.split(','), p = PRESETS.find(x => x.ask.at === at && x.ask.symbols.join(',') === syms.join(',')); go(p ? p.ask : { symbols: syms, at }); } }
  tracker(m.querySelector('#qtrk') as HTMLElement);
  // real stories from the news feed that tag two or more companies
  const ev = m.querySelector('#qinev') as HTMLElement;
  try {
    const { ok, b } = await jf('/api/news');
    const arts = ((ok && b?.articles) || []).map((a: any) => ({ a, syms: [...new Set(((a.symbols || []) as any[]).map(s => String(typeof s === 'string' ? s : s?.symbol || '').toUpperCase()).filter(s => /^[A-Z][A-Z0-9.\-]{0,9}$/.test(s)))] as string[] }))
      .filter((x: any) => x.syms.length >= 2 && (x.a.publishedAt || x.a.published_at)).slice(0, 6);
    ev.innerHTML = arts.length ? arts.map((x: any, i: number) => `<button data-e="${i}"><b>${esc(x.a.title)}</b><small>${esc(x.syms.slice(0, 6).join(' · '))} · ${esc(x.a.source || '')}</small></button>`).join('') : '<p class="mu">No current story tags more than one company. Try the example above or enter your own.</p>';
    ev.querySelectorAll<HTMLButtonElement>('[data-e]').forEach(b => b.onclick = () => { const x = arts[+b.dataset.e!]; go({ symbols: x.syms.slice(0, 8), at: x.a.publishedAt || x.a.published_at, headline: x.a.title }); out.scrollIntoView({ block: 'start', behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' }); });
  } catch { ev.innerHTML = '<p class="mu">News is unavailable right now.</p>'; }
}

export function initReaction(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qrx-css">${CSS}</style>`);
  (window as any).qReaction = (host: HTMLElement, ask: Ask) => runReaction(host, ask);
  PAGES.insights = () => { track('view_insights'); insights(); };
  PAGES.lab = () => location.replace('#/insights'); // the old sample-data "Reaction lab" now leads here
  if (!SUBT.research?.some(x => x[1] === 'insights')) SUBT.research?.splice(1, 0, ['Insights', 'insights']);
  (HUB as Record<string, string>).insights = 'research';
}
