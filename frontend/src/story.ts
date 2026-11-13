// News story page: first a detailed, organised breakdown of the story (built from the story itself and live data, so it
// works without AI), then two choices the reader can open: AI deep research on the companies involved, and an analysis
// of how the story touches their own practice portfolio and watchlist.
//
// Everything shown is either from the story, from market data with its time, or a clearly labelled typical pattern
// ("stories like this usually..."). Nothing predicts prices.

interface Ent { symbol?: string; name?: string; industry?: string; sentiment?: number | null; changePct?: number | null; highlights?: { text: string; sentiment?: number }[] }
interface Story { id?: string; title?: string; summary?: string; description?: string; snippet?: string; source?: string; publishedAt?: string; url?: string }
interface Quote { symbol: string; price?: number; changePct?: number; time?: string; source?: string }
interface Pos { q: number; avg: number }

const CSS = `
.qnx h3{margin:0;font-size:19px}.qnx h4{margin:18px 0 8px;font-size:15px;display:flex;gap:8px;align-items:center}
.qnx h4 .n{display:inline-grid;place-items:center;width:22px;height:22px;border-radius:50%;background:rgba(var(--acc),.14);color:var(--ac);font-size:12px;font-weight:700;flex:none}
.qnx .sub{color:var(--mu);font-size:13px;margin:4px 0 0}.qnx ul{margin:0;padding-left:20px;line-height:1.6}.qnx li{margin:3px 0}
.qnx .one{font-size:16.5px;line-height:1.55;padding:12px 14px;border-radius:12px;border:1px solid rgba(var(--acc),.3);background:rgba(var(--acc),.06);margin-top:12px}
.qnx table{border-collapse:collapse;width:100%;font-size:13.5px;font-variant-numeric:tabular-nums lining-nums}.qnx th,.qnx td{padding:7px 8px;border-bottom:1px solid var(--ln);text-align:left;vertical-align:top}
.qnx th{font-size:12px;color:var(--mu);font-weight:500}.qnx td.r,.qnx th.r{text-align:right;white-space:nowrap}.qnx .ov{overflow-x:auto}
.qnx .tone{font-weight:600;white-space:nowrap}.qnx .tone.pos{color:var(--up)}.qnx .tone.neg{color:var(--dn)}.qnx .tone.neu{color:var(--mu)}
.qnx .chipz{display:flex;flex-wrap:wrap;gap:6px}.qnx .note{font-size:12.5px;color:var(--mu);line-height:1.55;margin-top:10px}
.qnx-opts{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:12px}@media(max-width:760px){.qnx-opts{grid-template-columns:minmax(0,1fr)}}
.qnx-opt{border:1px solid var(--ln);border-radius:14px;padding:14px 16px;display:flex;flex-direction:column;gap:6px;background:var(--s1)}
.qnx-opt.on{border-color:var(--ac);box-shadow:0 0 0 1px rgba(var(--acc),.4) inset}.qnx-opt b{font-size:15.5px}.qnx-opt p{margin:0;color:var(--mu);font-size:13.5px;line-height:1.5}
.qnx-opt .btn{align-self:flex-start;margin:6px 0 0!important;float:none!important;position:static!important;max-width:100%;white-space:normal}
.qpi .sum{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px;margin:10px 0}.qpi .sum>div{border:1px solid var(--ln);border-radius:12px;padding:10px 12px}
.qpi .sum span{display:block;font-size:12px;color:var(--mu)}.qpi .sum b{font-size:18px;font-variant-numeric:tabular-nums}
.qpi .lk{display:inline-block;font-size:12px;font-weight:600;padding:2px 8px;border-radius:99px;border:1px solid var(--ln);white-space:nowrap}
.qpi .lk.direct{border-color:var(--ac);color:var(--ac)}.qpi .lk.comp,.qpi .lk.ind{color:var(--tx)}.qpi .lk.none{color:var(--mu)}
.qnx-sk{height:90px;border-radius:12px;background:linear-gradient(90deg,var(--s1),rgba(var(--fgc),.05),var(--s1));background-size:200% 100%;animation:sktm 1.3s linear infinite}`;

const enc = encodeURIComponent;
const cache = new Map<string, Promise<any>>();
const getj = (u: string) => { if (!cache.has(u)) cache.set(u, jf(u).then(r => (r.ok ? r.b : null)).catch(() => null)); return cache.get(u)!; };
const pc = (v: number | null | undefined, dp = 2) => v == null || !isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${fmt(Math.abs(v), dp)}%`;
const money = (v: number) => `${v < 0 ? '−' : ''}$${fmt(Math.abs(v), Math.abs(v) >= 1000 ? 0 : 2)}`;
const tone = (s: number | null | undefined) => s == null ? '<span class="tone neu">not rated</span>' : s > 0.1 ? '<span class="tone pos">▲ positive</span>' : s < -0.1 ? '<span class="tone neg">▼ negative</span>' : '<span class="tone neu">● neutral</span>';
const clean = (t: string) => String(t || '').replace(/\s+/g, ' ').replace(/\b(watch now|read more|click here)\b\.?/gi, '').replace(/\.\.\.$|…$/, '').trim();
const sentences = (t: string) => (clean(t).match(/[^.!?]+[.!?]+(?=\s|$)|[^.!?]+$/g) || []).map(s => s.trim()).filter(s => s.length > 25);
const day = (iso?: string) => { if (!iso) return ''; const d = new Date(iso.length === 10 ? iso + 'T12:00:00Z' : iso); return isNaN(+d) ? iso : d.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' }); };

/** Kinds of news and the sectors they usually move, with the reason. Matched against the headline and summary. */
const TOPICS: { k: string; re: RegExp; label: string; why: string; sectors: RegExp; releases: RegExp }[] = [
  { k: 'rates', re: /\b(fed|federal reserve|interest rates?|rate (cut|hike)s?|fomc|powell|warsh|central bank|treasury yields?|bond yields?)\b/i, label: 'Interest rates',
    why: 'Interest-rate news changes how much it costs companies and people to borrow, and how much investors will pay today for profits far in the future. Banks often earn more when rates are higher; property, utilities and fast-growing technology companies are usually hurt.',
    sectors: /bank|financ|insur|real estate|reit|utilit|tech|software|semicond/i, releases: /consumer price|employment|personal income|gross domestic/i },
  { k: 'inflation', re: /\b(inflation|cpi|consumer prices|pce|price index)\b/i, label: 'Inflation',
    why: 'Inflation news shapes what central banks do next. Higher-than-expected inflation usually means rates stay high for longer, which weighs on property, utilities and growth stocks; companies that can raise prices hold up better.',
    sectors: /real estate|reit|utilit|retail|consumer|food|beverag|tech|software/i, releases: /consumer price|producer price|personal income/i },
  { k: 'oil', re: /\b(oil|crude|opec|natural gas|gasoline|brent|wti|refiner)/i, label: 'Oil and energy',
    why: 'Oil and gas prices are revenue for energy producers but a cost for airlines, shipping, chemicals and consumers. A jump in oil usually helps energy companies and squeezes heavy fuel users.',
    sectors: /energy|oil|gas|airline|transport|logistic|chemical|road|marine/i, releases: /industrial production|consumer price/i },
  { k: 'trade', re: /\b(tariffs?|trade war|export (ban|controls?)|import dut|sanctions?)\b/i, label: 'Trade and tariffs',
    why: 'Tariffs and export rules raise costs for companies that import parts or sell abroad. Manufacturers, chipmakers, carmakers and retailers with global supply chains are usually most exposed.',
    sectors: /industrial|machinery|semicond|tech|auto|retail|consumer|electrical|textile|apparel/i, releases: /retail|industrial production/i },
  { k: 'geo', re: /\b(war|wars|missiles?|attack\w*|drones?|houthis?|invasion|invaded|conflict|military|ceasefire|geopolitic\w*|troops|airstrikes?)\b/i, label: 'War and geopolitics',
    why: 'Conflict tends to push money towards safe havens such as gold and government bonds, lift defence and energy companies, and hurt airlines and companies with business in the affected region.',
    sectors: /aerospace|defen[cs]e|energy|oil|airline|gold|metals|mining/i, releases: /$^/ },
  { k: 'ai', re: /\b(ai|artificial intelligence|chips?|semiconductors?|gpus?|data cent(er|re)s?|nvidia)\b/i, label: 'AI and chips',
    why: 'AI spending decisions move the whole chip supply chain together: chip designers, manufacturers, equipment makers and the cloud companies buying them.',
    sectors: /semicond|tech|software|electronic|communication/i, releases: /$^/ },
  { k: 'jobs', re: /\b(jobs report|payrolls?|unemployment|hiring|layoffs?|gdp|recession|economy)\b/i, label: 'Economy and jobs',
    why: 'Signs of a stronger or weaker economy move companies that depend on spending and borrowing most: retailers, travel, banks and industrial companies.',
    sectors: /retail|consumer|hotel|restaurant|leisure|bank|financ|industrial|machinery|airline/i, releases: /employment|gross domestic|retail/i },
  { k: 'crypto', re: /\b(bitcoin|crypto|ethereum|stablecoin|blockchain)\b/i, label: 'Crypto',
    why: 'Crypto news mostly moves crypto prices and the companies that hold or trade them, such as exchanges, miners and firms with bitcoin on their balance sheet.',
    sectors: /crypto|software|financ/i, releases: /$^/ },
];
const KIND: { re: RegExp; label: string; why: string }[] = [
  { re: /\b(earnings|revenue|profit|guidance|quarter(ly)?|eps|results|forecast)\b/i, label: 'Company results', why: 'Results and forecasts are the biggest regular driver of a single stock: the price reacts to how they compare with what analysts expected, not just whether they were good.' },
  { re: /\b(acqui|merger|takeover|buyout|deal|bid)\w*/i, label: 'Deal', why: 'In a takeover the buyer usually pays a premium, so the target’s shares often jump while the buyer’s can dip on the cost. Rivals can move too as investors guess who might be next.' },
  { re: /\b(resign\w*|steps? down|stepping down|appoint\w*|names? new|new (ceo|chief executive)|ousted|succeed\w*)\b/i, label: 'Leadership', why: 'Leadership changes add uncertainty about strategy; markets often react on the day and then wait for the new leader’s plans.' },
  { re: /\b(lawsuit|sued|regulator|antitrust|probe|investigation|fine|sec )\b/i, label: 'Legal or regulatory', why: 'Legal and regulatory news can mean fines, forced changes or delays; the size of the risk relative to the company matters more than the headline.' },
  { re: /\b(launch|unveil|product|approval|fda)\b/i, label: 'Product', why: 'New products and approvals matter when they are big enough to change future sales; small launches rarely move large companies.' },
];

function numbers(text: string): string[] {
  const out: string[] = [];
  const re = /[^.;]*?(\$\s?\d[\d,.]*\s?(trillion|billion|million|bn|m|k)?|\d[\d,.]*\s?%|\d[\d,.]*\s?(trillion|billion|million))[^.;]*/gi;
  for (const m of clean(text).matchAll(re)) { const s = m[0].trim(); if (s.length > 12 && !out.some(o => o.includes(s) || s.includes(o))) out.push(s.length > 180 ? s.slice(0, 177) + '…' : s); if (out.length >= 5) break; }
  return out;
}

async function quotes(syms: string[]): Promise<Record<string, Quote>> {
  const out: Record<string, Quote> = {};
  for (let i = 0; i < syms.length; i += 20) {
    const b = await getj('/api/quotes?symbols=' + enc(syms.slice(i, i + 20).join(',')));
    for (const q of (b?.quotes || []) as Quote[]) out[q.symbol] = q;
  }
  return out;
}
const sectorOf = async (s: string): Promise<string> => { const o = await getj('/api/overview?symbol=' + enc(s)); return String(o?.industry || o?.sector || '').trim(); };

// ---------- 1. The breakdown ----------
async function breakdown(box: HTMLElement, a: Story, ents: Ent[]): Promise<void> {
  const text = clean([a.description, a.snippet, a.summary].filter(Boolean).sort((x, y) => String(y).length - String(x).length)[0] || '');
  const all = `${a.title || ''}. ${text}`;
  const sents = sentences(text);
  const topics = TOPICS.filter(t => t.re.test(all));
  const kinds = KIND.filter(k => k.re.test(all)).slice(0, 2);
  const syms = ents.map(e => String(e.symbol || '').toUpperCase()).filter(Boolean).slice(0, 8);
  const nums = numbers(all);
  box.innerHTML = `<div class="qnx-sk"></div>`;
  const [q, secs, funds, cal] = await Promise.all([
    quotes([...syms, 'SPY']),
    Promise.all(syms.map(sectorOf)),
    Promise.all(syms.slice(0, 4).map(s => getj('/api/fundamentals?symbol=' + enc(s)))),
    topics.some(t => t.releases.source !== '$^') ? getj('/api/calendar?type=economic') : Promise.resolve(null),
  ]);
  if (!box.isConnected) return;
  const spy = q.SPY?.changePct;
  let n = 0; const H = (t: string) => `<h4><span class="n">${++n}</span>${t}</h4>`;
  const rows = ents.slice(0, 8).map((e, i) => {
    const s = String(e.symbol || '').toUpperCase(), qq = q[s], ch = qq?.changePct ?? e.changePct ?? null;
    const rel = ch != null && spy != null ? ch - spy : null;
    return `<tr><td><a href="#/asset/${enc(s)}"><b>${esc(s)}</b></a><br><small class="mu">${esc(e.name || '')}</small></td><td>${esc(secs[i] || e.industry || '—')}</td><td>${tone(e.sentiment)}</td>
      <td class="r ${ch == null ? '' : ch >= 0 ? 'up' : 'dn'}">${ch == null ? '—' : (ch >= 0 ? '▲ ' : '▼ ') + pc(ch)}</td><td class="r">${rel == null ? '—' : pc(rel)}</td></tr>`;
  }).join('');
  const watch: string[] = [];
  syms.slice(0, 4).forEach((s, i) => { const d = funds[i]?.nextEarnings; if (d) watch.push(`<b>${esc(s)}</b> reports earnings on ${esc(day(d))}: the next hard numbers on how the business is doing.`); });
  const rel = (cal?.items || []) as { date: string; name: string; what?: string }[];
  for (const t of topics) for (const r of rel.filter(r => t.releases.test(r.name)).slice(0, 2)) if (!watch.some(w => w.includes(r.name))) watch.push(`<b>${esc(r.name)}</b> on ${esc(day(r.date))}${r.what ? `: ${esc(r.what)}` : ''}`);
  if (syms.length) watch.push(`How ${syms.slice(0, 3).map(esc).join(', ')} trade over the next few days: a move that keeps going suggests the news changed expectations; one that reverses suggests it was noise.`);
  const highlights = ents.flatMap(e => (e.highlights || []).slice(0, 1).map(h => ({ s: e.symbol, t: clean(h.text), v: h.sentiment }))).filter(h => h.t.length > 30).slice(0, 4);

  box.innerHTML = `<div class="qnx-h"><h3>The story, explained</h3><p class="sub">${esc(a.source || '')}${a.publishedAt ? ' · ' + esc(new Date(a.publishedAt).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' })) : ''} · built from the story and live market data</p></div>
    <div class="one"><b>In one line:</b> ${esc(a.title || sents[0] || '')}</div>
    ${H('What happened')}${sents.length > 1 ? `<ul>${sents.slice(0, 5).map(s => `<li>${esc(/[.!?”"')]$/.test(s) ? s : s + '…')}</li>`).join('')}</ul>` : `<p>${esc(text || a.title || '')}</p>`}${text && !/[.!?”"')]$/.test(text) ? '<p class="note" style="margin-top:4px">The publisher only shares the start of the article; read the full story for the rest.</p>' : ''}
    ${highlights.length ? `<p class="note" style="margin-top:6px">What the story says about each company:</p><ul>${highlights.map(h => `<li><b>${esc(h.s || '')}</b>: “${esc(h.t.length > 220 ? h.t.slice(0, 217) + '…' : h.t)}”</li>`).join('')}</ul>` : ''}
    ${H('What kind of news this is')}<div class="chipz">${[...kinds.map(k => k.label), ...topics.map(t => t.label)].map(l => `<span class="chip">${esc(l)}</span>`).join('') || '<span class="chip">General market news</span>'}</div>
    ${kinds.length || topics.length ? `<ul style="margin-top:8px">${kinds.map(k => `<li><b>${esc(k.label)}:</b> ${esc(k.why)}</li>`).join('')}${topics.map(t => `<li><b>${esc(t.label)}:</b> ${esc(t.why)}</li>`).join('')}</ul>` : ''}
    ${ents.length ? `${H('Who it affects and how the market reacted')}<div class="ov"><table><tr><th>Company</th><th>Industry</th><th>Story’s tone</th><th class="r">Today</th><th class="r">vs S&amp;P 500</th></tr>${rows}</table></div>
      <p class="note">S&amp;P 500 today: ${spy == null ? 'not available' : pc(spy)}. “vs S&amp;P 500” is the stock’s move minus the market’s, a rough guide to how much is company news rather than the whole market. Prices from ${esc(q[syms[0]]?.source || 'our providers')}${q[syms[0]]?.time ? ' at ' + esc(new Date(q[syms[0]]!.time!).toLocaleTimeString(undefined, { timeStyle: 'short' })) : ''}, may be delayed. Tone is the news provider’s rating of the story, not a forecast.</p>` : `${H('Who it affects')}<p>The story doesn’t name a listed company${topics.length ? `; as ${topics.map(t => t.label.toLowerCase()).join(' and ')} news it mostly matters through the sectors described above` : ''}.</p>`}
    ${nums.length ? `${H('Key numbers')}<ul>${nums.map(x => `<li>${esc(x)}</li>`).join('')}</ul>` : ''}
    ${H('What to watch next')}<ul>${(watch.length ? watch : ['Follow-up reports and any official statements from the companies or agencies involved.']).map(w => `<li>${w}</li>`).join('')}</ul>
    <p class="note">This breakdown uses the article summary${a.url ? ` (<a href="${esc(a.url)}" target="_blank" rel="noopener noreferrer">read the full story</a>)` : ''} and market data. “What kind of news” lines describe typical patterns, not predictions. For learning, not investment advice.</p>`;
}

// ---------- 3. Portfolio impact ----------
async function portfolio(box: HTMLElement, a: Story, ents: Ent[]): Promise<void> {
  const acct = LS<{ cash?: number; pos?: Record<string, Pos> } | null>('q_pt', null);
  const pos = Object.entries(acct?.pos || {}).filter(([, p]) => p && p.q > 0);
  const wl = (LS<unknown[]>('q_wl', []) || []).map(x => String(typeof x === 'string' ? x : (x as any)?.symbol || '').toUpperCase()).filter(Boolean);
  const held = new Set(pos.map(([s]) => s.toUpperCase()));
  const watchOnly = [...new Set(wl)].filter(s => !held.has(s)).slice(0, 15);
  if (!pos.length && !watchOnly.length) {
    box.innerHTML = `<div class="qpi"><h3 style="margin:0 0 6px">How this affects your portfolio</h3><p>You don’t have any practice positions or watchlist stocks yet. <a href="#/trade">Make a practice trade</a> or add stocks to your watchlist (★ on any stock page), then come back to this story.</p></div>`;
    return;
  }
  box.innerHTML = `<div class="qnx-sk"></div>`;
  track('story_portfolio');
  const all = `${a.title || ''}. ${clean([a.description, a.snippet, a.summary].filter(Boolean).join(' '))}`;
  const topics = TOPICS.filter(t => t.re.test(all));
  const storySyms = ents.map(e => String(e.symbol || '').toUpperCase()).filter(Boolean);
  const mine = [...pos.map(([s]) => s.toUpperCase()), ...watchOnly];
  const [q, mySecs, storySecs, peers] = await Promise.all([
    quotes([...mine, ...storySyms]),
    Promise.all(mine.map(sectorOf)),
    Promise.all(storySyms.map(sectorOf)),
    Promise.all(storySyms.slice(0, 4).map(s => getj('/api/peers?symbol=' + enc(s)))),
  ]);
  if (!box.isConnected) return;
  const peerOf = new Map<string, string>();
  peers.forEach((p, i) => (p?.peers || []).forEach((x: { symbol: string }) => { if (x.symbol && x.symbol !== storySyms[i]) peerOf.set(x.symbol.toUpperCase(), storySyms[i]); }));
  const storyInd = new Map<string, string>(); storySyms.forEach((s, i) => { if (storySecs[i]) storyInd.set(storySecs[i].toLowerCase(), s); });

  type Row = { s: string; held: boolean; value: number; w: number; ch: number | null; dollar: number | null; link: 'direct' | 'comp' | 'ind' | 'macro' | 'none'; why: string; sec: string };
  const value = (s: string, p: Pos) => p.q * (q[s]?.price ?? p.avg);
  const invested = pos.reduce((t, [s, p]) => t + value(s.toUpperCase(), p), 0), total = invested + Math.max(0, acct?.cash || 0);
  const rows: Row[] = mine.map((s, i) => {
    const p = pos.find(([x]) => x.toUpperCase() === s)?.[1];
    const v = p ? value(s, p) : 0, ch = q[s]?.changePct ?? null, sec = mySecs[i] || '';
    const e = ents.find(x => String(x.symbol || '').toUpperCase() === s);
    let link: Row['link'] = 'none', why = 'Not named in the story, not a listed competitor and not in an industry this kind of news usually moves.';
    if (e) { link = 'direct'; why = `Named in the story; its tone is ${e.sentiment == null ? 'not rated' : e.sentiment > 0.1 ? 'positive' : e.sentiment < -0.1 ? 'negative' : 'neutral'}.`; }
    else if (peerOf.has(s)) { link = 'comp'; why = `A competitor of ${peerOf.get(s)}. News about one company often moves its rivals too, either together (the whole industry re-rated) or in opposite directions (one gains what the other loses).`; }
    else if (sec && storyInd.has(sec.toLowerCase())) { link = 'ind'; why = `Same industry as ${storyInd.get(sec.toLowerCase())} (${sec}). Investors often move similar companies together on news like this.`; }
    else { const t = topics.find(t => sec && t.sectors.test(sec)); if (t) { link = 'macro'; why = `${t.label} news usually affects ${sec} companies: ${t.why.split('. ')[0].toLowerCase()}.`; } }
    return { s, held: !!p, value: v, w: total > 0 ? v / total * 100 : 0, ch, dollar: p && ch != null ? v * ch / (100 + ch) : null, link, why, sec };
  });
  const order = { direct: 0, comp: 1, ind: 2, macro: 3, none: 4 };
  rows.sort((x, y) => order[x.link] - order[y.link] || y.w - x.w);
  const linked = rows.filter(r => r.held && r.link !== 'none'), direct = rows.filter(r => r.held && r.link === 'direct');
  const wLinked = linked.reduce((t, r) => t + r.w, 0), wDirect = direct.reduce((t, r) => t + r.w, 0);
  const dLinked = linked.reduce((t, r) => t + (r.dollar || 0), 0);
  const LBL = { direct: 'In the story', comp: 'Competitor', ind: 'Same industry', macro: 'Sector link', none: 'Not linked' };
  const points: string[] = [];
  if (direct.length) points.push(`<b>Directly in the story:</b> ${direct.map(r => `${esc(r.s)} (${fmt(r.w, 1)}% of your portfolio${r.ch != null ? `, ${pc(r.ch)} today` : ''})`).join(', ')}. This is the clearest link: the news is about a company you own.`);
  const ind = linked.filter(r => r.link !== 'direct');
  if (ind.length) points.push(`<b>Indirectly linked:</b> ${ind.map(r => `${esc(r.s)} (${LBL[r.link].toLowerCase()})`).join(', ')}. These links are typical patterns, not certainties; check whether the stock actually moved.`);
  if (wLinked >= 25) points.push(`<b>Concentration:</b> about ${fmt(wLinked, 0)}% of your practice portfolio is tied to this one story, so a single piece of news has an outsized effect on you. Spreading across unrelated industries reduces that (<a href="#/lesson/divers">lesson: diversification</a>).`);
  else if (pos.length && !linked.length) points.push('<b>Little exposure:</b> none of your practice positions is directly or indirectly linked to this story.');
  const wlLinked = rows.filter(r => !r.held && r.link !== 'none');
  if (wlLinked.length) points.push(`<b>On your watchlist:</b> ${wlLinked.map(r => `${esc(r.s)} (${LBL[r.link].toLowerCase()})`).join(', ')}. Worth a look if you were thinking of buying.`);

  const holdingsForAi = rows.filter(r => r.held && r.w >= 0.1).slice(0, 15).map(r => `${r.s} ${fmt(r.w, 1).replace(/,/g, '')}%`);
  box.innerHTML = `<div class="qpi"><h3 style="margin:0 0 4px">How this affects your portfolio</h3><p class="sub">Your practice portfolio${watchOnly.length ? ' and watchlist' : ''}, checked against this story. Paper money only.</p>
    ${pos.length ? `<div class="sum"><div><span>Linked to this story</span><b>${fmt(wLinked, 1)}%</b><span>of your portfolio</span></div><div><span>Named in the story</span><b>${fmt(wDirect, 1)}%</b><span>of your portfolio</span></div><div><span>Linked holdings today</span><b class="${dLinked >= 0 ? 'up' : 'dn'}">${linked.length ? money(dLinked) : '—'}</b><span>change in value</span></div></div>` : ''}
    <h4 style="margin:14px 0 6px;font-size:15px">What it means for you</h4><ul style="margin:0;padding-left:20px;line-height:1.6">${points.map(p => `<li>${p}</li>`).join('') || '<li>Nothing you hold or watch is linked to this story.</li>'}</ul>
    <h4 style="margin:16px 0 6px;font-size:15px">Holding by holding</h4>
    <div class="ov"><table><tr><th>Stock</th><th>Link to this story</th><th class="r">Share of portfolio</th><th class="r">Today</th><th class="r">Value change today</th></tr>
    ${rows.filter(r => r.link !== 'none').map(r => `<tr><td><a href="#/asset/${enc(r.s)}"><b>${esc(r.s)}</b></a>${r.held ? '' : ' <small class="mu">watchlist</small>'}<br><small class="mu">${esc(r.sec || '')}</small></td><td><span class="lk ${r.link}">${LBL[r.link]}</span><br><small class="mu">${esc(r.why)}</small></td>
      <td class="r">${r.held ? fmt(r.w, 1) + '%' : '—'}</td><td class="r ${r.ch == null ? '' : r.ch >= 0 ? 'up' : 'dn'}">${r.ch == null ? '—' : (r.ch >= 0 ? '▲ ' : '▼ ') + pc(r.ch)}</td><td class="r">${r.dollar == null ? '—' : money(r.dollar)}</td></tr>`).join('') || '<tr><td colspan="5" class="mu">None of your holdings or watchlist stocks is linked to this story.</td></tr>'}</table></div>
    ${rows.some(r => r.link === 'none') ? `<p class="note" style="margin-top:8px"><b>Not linked to this story:</b> ${rows.filter(r => r.link === 'none').map(r => `<a href="#/asset/${enc(r.s)}">${esc(r.s)}</a>${r.held ? ` (${fmt(r.w, 1)}%${r.ch != null ? ', ' + pc(r.ch) + ' today' : ''})` : ' (watchlist)'}`).join(', ')}. Not named in the story, not a listed competitor and not in an industry this kind of news usually moves.</p>` : ''}
    <div id="qpiai" style="margin-top:12px"></div>
    <p class="note">How links are found: “In the story” means the news provider tagged the company; “Competitor” comes from Finnhub’s peer lists; “Same industry” and “Sector link” compare industries and the kind of news. Today’s change is the whole day’s move, which may have other causes. Prices may be delayed. This shows links, not predictions, and is not investment advice.</p></div>`;
  const ai = box.querySelector('#qpiai') as HTMLElement;
  if ((QLIVE as any).ai && !(window as any).AIOFF_ && holdingsForAi.length) {
    ai.innerHTML = `<button class="btn" id="qpiaib">Explain this for my portfolio with AI</button><span class="mu" style="font-size:12.5px;margin-left:8px">Sends only your tickers and their share of the portfolio.</span>`;
    (ai.querySelector('#qpiaib') as HTMLButtonElement).onclick = async () => {
      ai.innerHTML = '<div class="qnx-sk" style="height:70px"></div>';
      try {
        const r = await fetch('/api/explain', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ lens: 'portfolio', title: a.title, summary: clean([a.description, a.snippet, a.summary].filter(Boolean).join(' ')).slice(0, 1900), symbols: storySyms.slice(0, 8), holdings: holdingsForAi }) });
        const j = await r.json().catch(() => ({}));
        ai.innerHTML = r.ok ? `<div class="pan" style="margin:0"><div style="display:flex;gap:8px;align-items:center;margin-bottom:6px"><b>AI explanation</b><span class="tag">AI</span></div>${aiHtml(j.text || '')}<p class="note">Written by AI from the story and your tickers; it can be wrong.</p></div>`
          : `<p class="mu">${esc(/key|configured|credit|permission/i.test(j.error || '') ? 'The AI writer is offline right now; the analysis above doesn’t need it.' : (j.error || 'The AI explanation is unavailable right now.'))}</p>`;
      } catch { ai.innerHTML = '<p class="mu">The AI explanation is unavailable right now.</p>'; }
    };
  }
}
const aiHtml = (t: string) => esc(t).replace(/\*\*(.+?)\*\*/g, '<b>$1</b>').replace(/^[ \t]*[-*] /gm, '• ').replace(/\n/g, '<br>');

// ---------- the page section ----------
function render(host: HTMLElement, a: Story, ents: Ent[]): void {
  const syms = ents.map(e => String(e.symbol || '').toUpperCase()).filter(s => /^[A-Z][A-Z0-9.\-^=]{0,11}$/.test(s)).slice(0, 5);
  const key = (a.id || a.title || '') + '|' + syms.join(',');
  if (host.dataset.qnx === key) return;
  host.dataset.qnx = key;
  host.innerHTML = `<div class="qnx"><div id="qnxx"></div>
    <h4 style="margin-top:22px"><span class="n" style="background:rgba(var(--acc),.14)">→</span>Go deeper</h4>
    <div class="qnx-opts">
      <div class="qnx-opt" data-o="dr"><b>🔍 AI deep research</b><p>${syms.length ? `On ${syms.map(esc).join(', ')}: an AI outlook (positive, neutral or negative), Wall Street’s consensus, the technical picture and a full sourced report.` : 'Pick any company for an AI outlook, Wall Street’s consensus, technicals and a full sourced report.'}</p><button class="btn p" data-o="dr">Run deep research</button></div>
      <div class="qnx-opt" data-o="pf"><b>💼 How does this affect my portfolio?</b><p>Checks your practice portfolio and watchlist against this story: what’s directly or indirectly exposed, your share at stake and today’s change.</p><button class="btn p" data-o="pf">Analyse my portfolio</button></div>
    </div><div id="qnxrun" style="margin-top:14px"></div></div>`;
  breakdown(host.querySelector('#qnxx') as HTMLElement, a, ents);
  const dup = document.getElementById('shl'); if (dup) dup.style.display = 'none'; // its content is now in the breakdown
  const run = host.querySelector('#qnxrun') as HTMLElement;
  host.querySelectorAll<HTMLButtonElement>('button[data-o]').forEach(b => b.onclick = () => {
    host.querySelectorAll('.qnx-opt').forEach(o => o.classList.toggle('on', (o as HTMLElement).dataset.o === b.dataset.o));
    run.innerHTML = '<div id="qnxinner"></div>';
    const inner = run.querySelector('#qnxinner') as HTMLElement;
    if (b.dataset.o === 'dr') { track('story_deep_research'); (window as any).qDeep?.(inner, ents); }
    else portfolio(inner, a, ents);
    run.scrollIntoView({ behavior: matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth', block: 'start' });
  });
}

export function initStory(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qnx-css">${CSS}</style>`);
  (window as any).qStory = (host: HTMLElement | null, a: Story | null, ents: Ent[]) => { if (host && a) render(host, a, ents || []); };
}
