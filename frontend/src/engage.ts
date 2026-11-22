// Reasons to come back, and one connected platform:
//   1. Search that covers everything: research studies, lessons, concepts (the glossary) and tools are added to the
//      existing company and private-company results, grouped, with arrow-key navigation.
//   2. "Continue where you left off": recently viewed companies, the last lesson and recent research.
//   3. Saved research and the weekly research brief on both dashboards.
//   4. Preferences (prefs.ts): Student level and topics drive lesson recommendations; Professional industries and the
//      default research view. Stored in this browser and synced to the account, nothing else.
//   5. Company pages link to Quantera research on that company and to the lessons behind their numbers.
//   6. Feedback form: report wrong data, a bug, request a company, suggest a lesson or a feature.
import { library } from './library';
import { prefs, setPrefs, PREFKEY, type Level, type Depth } from './prefs';

declare const GL: [string, string][];
declare function isSimple(): boolean;
declare const QAUTH: { user: unknown } | undefined;

const RVC = 'q_rvc', RVL = 'q_rvl';
const CSS = `
#res .qs-h{display:flex;justify-content:space-between;align-items:center}
#res a.qs-a{display:flex;flex-direction:column;gap:1px}#res a.qs-a small{color:var(--mu);font-size:12px;line-height:1.35}
#res a:focus-visible,#res a.qs-on{outline:2px solid var(--ac);outline-offset:-2px;background:rgba(var(--acc),.1)}
.qx-wb .chart svg{width:100%;height:auto;max-height:260px}.qx-wb .chart text{fill:var(--mu);font-size:11px}
.qx-wb ul{margin:6px 0 10px;padding-left:20px;font-size:14px;line-height:1.6}.qx-wb .q{font-size:15.5px;font-weight:600;line-height:1.5}
.qx-pf label{display:flex;flex-direction:column;gap:4px;font-size:12.5px;color:var(--mu);margin:0 0 10px}
.qx-pf select{font:inherit;font-size:14px;padding:7px 10px!important}
.qx-pf .chips{display:flex;flex-wrap:wrap;gap:6px}.qx-pf .chips button{font:inherit;font-size:12.5px;border:1px solid var(--ln);border-radius:99px;background:none;color:var(--mu);padding:5px 10px;cursor:pointer}
.qx-pf .chips button[aria-pressed=true]{color:var(--tx);border-color:var(--ac);background:rgba(var(--acc),.12)}
.qx-cl .qx-ls a{padding:8px 12px}
@media(max-width:1000px) and (min-width:681px){.qx-g>section:last-child:nth-child(odd){grid-column:span 12}}
.qcr{border:1px solid var(--ln);border-radius:16px;padding:16px 20px;background:var(--s1);margin:18px 0}
.qcr h3{margin:0 0 8px;font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:var(--mu)}.qcr ul{margin:0;padding-left:20px;line-height:1.8;font-size:14.5px}`;

const when = (iso?: string) => { if (!iso) return ''; const d = new Date(iso.length === 10 ? iso + 'T12:00:00Z' : iso); return isNaN(+d) ? '' : d.toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' }); };
const lessons = () => (typeof LES !== 'undefined' ? LES : []).map(l => ({ id: l[0], title: l[1], cat: l[2], level: l[3], desc: l[6] }));

// ---------- 1. universal search ----------
const TOOLS: [string, string, string][] = [
  ['#/insights', 'Insights', 'Compare how companies reacted to the same news'], ['#/screener', 'Screener', 'Filter US stocks by size, valuation and growth'],
  ['#/compare', 'Compare', 'Companies side by side'], ['#/valuation/AAPL', 'Valuation (DCF)', 'Discounted cash flow model'], ['#/ratios', 'Ratio calculator', 'Work out P/E, margins and more'],
  ['#/trade', 'Paper trading', 'Practice with $100,000 of virtual money'], ['#/private', 'Private companies', 'Estimated valuations and funding rounds'],
  ['#/macro', 'Economy', 'Inflation, jobs, rates and GDP'], ['#/calendar', 'Calendar', 'Earnings and economic events'], ['#/news', 'News', 'Market news by company'],
  ['#/statex', 'Statement exercises', 'Analyst questions answered from SEC filings'], ['#/dashboard', 'Dashboard', 'Your starting point'], ['#/glossary', 'Glossary', 'Financial terms in plain English'],
];
const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9%&/ ]+/g, ' ').replace(/\s+/g, ' ').trim();
const hit = (q: string, ...fields: (string | undefined)[]) => { const n = norm(q), compact = n.replace(/[^a-z0-9]/g, ''); if (n.length < 2) return 0;
  let best = 0; for (const f of fields) { if (!f) continue; const t = norm(f); if (t.startsWith(n)) best = Math.max(best, 3); else if (t.includes(' ' + n) || t.includes(n)) best = Math.max(best, 2); else if (compact.length >= 2 && t.replace(/[^a-z0-9]/g, '').includes(compact)) best = Math.max(best, 1); } return best; };
let LIBCACHE: Awaited<ReturnType<typeof library>> | null = null;

function extra(q: string): string {
  const sec = (title: string, items: string[]) => items.length ? `<div class="qs-x"><h6 class="qs-h">${title}</h6>${items.join('')}</div>` : '';
  const rs = (LIBCACHE?.articles || []).map(a => ({ a, s: hit(q, a.title, (a.tickers || []).join(' '), a.category, (a as { industry?: string }).industry, a.dek) })).filter(x => x.s).sort((x, y) => y.s - x.s).slice(0, 3)
    .map(({ a }) => `<a class="qs-a" href="#/research/${esc(a.slug)}"><span>${esc(a.title)}</span><small>${esc((a as { classification?: string }).classification || 'Research')} · ${esc(when(a.date))}</small></a>`);
  const ls = lessons().map(l => ({ l, s: hit(q, l.title, l.id, l.cat) * 2 + (hit(q, l.desc) ? 1 : 0) })).filter(x => x.s).sort((x, y) => y.s - x.s).slice(0, 3)
    .map(({ l }) => `<a class="qs-a" href="#/lesson/${esc(l.id)}"><span>${esc(l.title)}</span><small>Lesson · ${esc(l.level)} · ${esc(l.cat)}</small></a>`);
  const gs = (typeof GL !== 'undefined' ? GL : []).map(g => ({ g, s: hit(q, g[0]) * 2 + (hit(q, g[1]) ? 1 : 0) })).filter(x => x.s >= 2).sort((x, y) => y.s - x.s).slice(0, 2)
    .map(({ g }) => `<a class="qs-a" href="#/glossary"><span>${esc(g[0])}</span><small>${esc(g[1].length > 90 ? g[1].slice(0, 88) + '…' : g[1])}</small></a>`);
  const ts = TOOLS.map(t => ({ t, s: hit(q, t[1], t[2]) })).filter(x => x.s >= 2).slice(0, 2).map(({ t }) => `<a class="qs-a" href="${t[0]}"><span>${esc(t[1])}</span><small>${esc(t[2])}</small></a>`);
  const news = norm(q).length >= 3 ? `<a class="qs-a" href="#/news" data-news="${esc(q)}"><span>Search the news for “${esc(q)}”</span><small>Market events and company news</small></a>` : '';
  return sec('Research', rs) + sec('Lessons', ls) + sec('Concepts', gs) + sec('Tools', ts) + (news ? sec('Market events', [news]) : '');
}

function search(): void {
  const q = document.getElementById('q') as HTMLInputElement | null, res = document.getElementById('res'); if (!q || !res) return;
  q.setAttribute('aria-controls', 'res'); res.setAttribute('aria-label', 'Search results');
  q.placeholder = 'Search companies, research, lessons and concepts…';
  library().then(l => LIBCACHE = l).catch(() => { /* search still works for companies */ });
  let busy = false, last = '';
  const apply = () => {
    if (busy) return; const v = q.value.trim(); if (!v || norm(v).length < 2) { res.querySelectorAll('.qs-x').forEach(x => x.remove()); return; }
    if (res.querySelector('.qs-x') && last === v) return;
    busy = true; last = v;
    res.querySelectorAll('.qs-x').forEach(x => x.remove());
    // the old generic "Education" link ("Lesson: reading a company profile") isn't about the query; lessons replace it
    res.querySelectorAll('h6').forEach(h => { if (/^Education$/.test(h.textContent || '')) { const n = h.nextElementSibling; if (n && n.tagName === 'A' && /reading a company profile/i.test(n.textContent || '')) n.remove(); h.remove(); } });
    const html = extra(v);
    if (html) {
      // When the company results match what was typed (a name or ticker), they stay first; otherwise research,
      // lessons and concepts lead, since the company list is then only loosely related.
      const n = norm(v), strong = [...res.querySelectorAll<HTMLAnchorElement>(':scope > a, :scope > div:not(.qs-x) > a')].some(x => norm(x.textContent || '').includes(n));
      res.insertAdjacentHTML(strong ? 'beforeend' : 'afterbegin', html); if (getComputedStyle(res).display === 'none') res.style.display = '';
    }
    busy = false;
  };
  new MutationObserver(() => { if (!busy) apply(); }).observe(res, { childList: true });
  q.addEventListener('input', () => { last = ''; setTimeout(apply, 260); });
  // keyboard: arrows move through results, Enter opens, Escape goes back to the box
  const links = () => [...res.querySelectorAll<HTMLAnchorElement>('a[href]')].filter(a => a.offsetParent !== null);
  q.addEventListener('keydown', e => { if (e.key === 'ArrowDown') { const l = links(); if (l.length) { e.preventDefault(); l[0].focus(); } } });
  document.addEventListener('keydown', e => {
    if (!(document.activeElement as HTMLElement | null)?.closest('#res')) return;
    const l = links(), i = l.indexOf(document.activeElement as HTMLAnchorElement); if (i < 0) return;
    if (e.key === 'ArrowDown') { e.preventDefault(); (l[i + 1] || l[0]).focus(); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); i === 0 ? q.focus() : l[i - 1].focus(); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); q.focus(); setTimeout(() => q.focus(), 0); }
  }, true);
  res.addEventListener('click', e => { const a = (e.target as HTMLElement).closest<HTMLAnchorElement>('a[data-news]'); if (a) { try { sessionStorage.setItem('q_newsq', a.dataset.news || ''); } catch { /* private mode */ } } });
}

// ---------- 2. recently viewed ----------
function remember(): void {
  const [, r, a] = location.hash.split('/');
  if (r === 'asset' && a && /^[\w.^=-]{1,20}$/.test(decodeURIComponent(a))) {
    const s = decodeURIComponent(a).toUpperCase(), l = LS<{ s: string; t: number }[]>(RVC, []).filter(x => x.s !== s);
    SS(RVC, [{ s, t: Date.now() }, ...l].slice(0, 12));
  }
  if (r === 'lesson' && a) SS(RVL, { id: decodeURIComponent(a), t: Date.now() });
  if (r === 'news') { let nq = ''; try { nq = sessionStorage.getItem('q_newsq') || ''; sessionStorage.removeItem('q_newsq'); } catch { /* private mode */ }
    if (nq) setTimeout(() => { const i = document.querySelector<HTMLInputElement>('#m input[type=search], #m input#nq, #m input[placeholder*="earch"]'); if (i) { i.value = nq; i.dispatchEvent(new Event('input', { bubbles: true })); } }, 600); }
}

// ---------- 3. dashboard additions ----------
function sectionsHtml(pro: boolean): string {
  return `<section class="qx-c qx-cl" aria-labelledby="qe-cl"><h3 id="qe-cl">Continue where you left off</h3><div id="qe-clb"></div></section>
    <section class="qx-c ${pro ? 'w8' : 'w8'} qx-wb" aria-labelledby="qe-wb"><h3 id="qe-wb">Weekly research brief</h3><div id="qe-wbb"><div class="qx-skel"></div><div class="qx-skel" style="width:70%"></div></div></section>
    <section class="qx-c" aria-labelledby="qe-sv"><h3 id="qe-sv">Saved research</h3><div id="qe-svb"></div></section>
    <section class="qx-c ${pro ? 'w8' : 'w8'} qx-pf" aria-labelledby="qe-pf"><h3 id="qe-pf">Personalize</h3><div id="qe-pfb"></div></section>`;
}

async function cont(el: HTMLElement): Promise<void> {
  const rv = LS<{ s: string; t: number }[]>(RVC, []).slice(0, 4), rl = LS<{ id: string; t: number } | null>(RVL, null), read = LS<string[]>('q_rread', []).slice(0, 2);
  const L = rl ? lessons().find(l => l.id === rl.id) : null;
  let lib = LIBCACHE; if (!lib) { try { lib = await library(); } catch { lib = null; } }
  const items: string[] = [];
  if (L) items.push(`<li><a href="#/lesson/${esc(L.id)}"><span><b>${esc(L.title)}</b><small>Lesson you last opened${typeof DONE !== 'undefined' && DONE.has(L.id) ? ' · completed' : ''}</small></span></a></li>`);
  rv.forEach(x => items.push(`<li><a href="#/asset/${encodeURIComponent(x.s)}"><span><b>${esc(x.s)}</b><small>Company viewed ${esc(when(new Date(x.t).toISOString()))}</small></span></a></li>`));
  read.forEach(s => { const a = lib?.articles.find(x => x.slug === s); if (a) items.push(`<li><a href="#/research/${esc(a.slug)}"><span><b>${esc(a.title)}</b><small>Research you read</small></span></a></li>`); });
  el.innerHTML = items.length ? `<ul class="qx-ls">${items.slice(0, 6).join('')}</ul>` : '<p class="mu">Companies, lessons and research you open will appear here, so you can pick up where you stopped.</p>';
}

async function saved(el: HTMLElement): Promise<void> {
  const sv = LS<string[]>('q_rsave', []); let lib = LIBCACHE; if (!lib) { try { lib = await library(); } catch { lib = null; } }
  const arts = sv.map(s => lib?.articles.find(a => a.slug === s)).filter(Boolean) as NonNullable<typeof lib>['articles'];
  el.innerHTML = arts.length ? `<ul class="qx-ls">${arts.slice(0, 4).map(a => `<li><a href="#/research/${esc(a.slug)}"><span><b>${esc(a.title)}</b><small>${esc(when(a.date))}</small></span></a></li>`).join('')}</ul>` : '<p class="mu">Use “☆ Save” on any study to keep it here.</p><a class="lnk" href="#/research">Browse research →</a>';
}

async function brief(el: HTMLElement, pro: boolean): Promise<void> {
  let lib = LIBCACHE; if (!lib) { try { lib = await library(); } catch { lib = null; } }
  if (!lib || !lib.articles.length) { el.innerHTML = '<p class="mu">The research brief couldn’t be loaded just now.</p>'; return; }
  const arts = [...lib.articles].sort((a, b) => a.date.localeCompare(b.date)), week = Math.floor((Date.now() / 864e5 + 3) / 7), a = arts[week % arts.length];
  let q = '', findings: string[] = a.findings || [], chart = '', nsrc = (a as { sources?: number }).sources || 0;
  try {
    const r = await fetch(`/research/${encodeURIComponent(a.slug)}.html`); const doc = new DOMParser().parseFromString(await r.text(), 'text/html');
    q = doc.querySelector('#question')?.parentElement?.querySelector('p')?.textContent || '';
    const c = doc.querySelector('main .chart'); if (c) { c.querySelectorAll('script').forEach(s => s.remove()); chart = c.outerHTML; }
    if (!nsrc) nsrc = doc.querySelectorAll('ol.srcs li').length;
  } catch { /* brief still shows the findings */ }
  const app = `#/research/${encodeURIComponent(a.slug)}`;
  el.innerHTML = `<h4><a href="${app}">${esc(a.title)}</a></h4><p class="mu" style="font-size:13px">${esc((a as { classification?: string }).classification || 'Research')} · Published ${esc(when(a.date))}${(a as { updated?: string }).updated ? ' · Updated ' + esc(when((a as { updated?: string }).updated)) : ''} · ${a.readMinutes || 5} min read</p>
    ${q ? `<p class="q">${esc(q)}</p>` : ''}<ul>${findings.slice(0, pro ? 4 : 3).map(f => `<li>${esc(f)}</li>`).join('')}</ul>${chart && pro ? chart : ''}
    <p class="src">${nsrc ? nsrc + ' sources, each linked from the figures it supports. ' : ''}<a href="/research/${esc(a.slug)}?depth=method">Methodology and data</a> · <a href="${app}">Read the full report →</a></p>`;
}

function personalize(el: HTMLElement, pro: boolean): void {
  const p = prefs(), cats = [...new Set(lessons().map(l => l.cat))].sort();
  const inds = [...new Set((LIBCACHE?.articles || []).map(a => (a as { industry?: string }).industry).filter(Boolean) as string[])].sort();
  const chips = (key: 'topics' | 'industries', list: string[]) => `<div class="chips">${list.map(v => `<button type="button" data-k="${key}" data-v="${esc(v)}" aria-pressed="${(p[key] || []).includes(v)}">${esc(v)}</button>`).join('')}</div>`;
  el.innerHTML = pro
    ? `<label>Default research view<select data-p="depth"><option value="">Detailed analysis (default)</option>${[['overview', 'Overview'], ['method', 'Methodology and data']].map(([v, t]) => `<option value="${v}"${p.depth === v ? ' selected' : ''}>${t}</option>`).join('')}</select></label>
       <label>Preferred industries</label>${inds.length ? chips('industries', inds) : '<p class="mu">Industries appear as research is published.</p>'}
       <p class="src">Followed companies are your <a href="#/trade">watchlist</a>. ${note()}</p>`
    : `<label>Lesson level<select data-p="level"><option value="">Start from the beginning</option>${['Beginner', 'Intermediate', 'Advanced'].map(v => `<option${p.level === v ? ' selected' : ''}>${v}</option>`).join('')}</select></label>
       <label>Favorite topics</label>${chips('topics', cats)}<p class="src">Recommended lessons and the concept challenge follow these. ${note()}</p>`;
  el.querySelectorAll<HTMLSelectElement>('select[data-p]').forEach(s => s.onchange = () => { const q = prefs(); const k = s.dataset.p as 'level' | 'depth'; if (s.value) (q as Record<string, unknown>)[k] = s.value as Level | Depth; else delete q[k]; setPrefs(q); track('pref_' + k); route(); });
  el.querySelectorAll<HTMLButtonElement>('button[data-k]').forEach(b => b.onclick = () => { const q = prefs(), k = b.dataset.k as 'topics' | 'industries', v = b.dataset.v!, l = new Set(q[k] || []); l.has(v) ? l.delete(v) : l.add(v); q[k] = [...l]; setPrefs(q); b.setAttribute('aria-pressed', String(l.has(v))); track('pref_' + k); });
}
const note = () => (typeof QAUTH !== 'undefined' && QAUTH && QAUTH.user ? 'Saved to your account.' : 'Saved in this browser only. With a free account it syncs to your other devices. Nothing else is collected.');

// Row by row, so every row fills the 12-column grid (sections were sized before these extra ones were added).
function layout(g: HTMLElement, pro: boolean): void {
  const sec = (id: string) => g.querySelector<HTMLElement>(`#${id}`)?.closest<HTMLElement>('section') || null;
  const order = pro
    ? [['qe-cl', 4], ['qx-rs', 8], ['qx-in', 4], ['qe-wb', 8], ['qe-sv', 4], ['qx-mi', 8], ['qx-wl', 6], ['qx-mc', 6], ['qx-nw', 8], ['qx-pp', 4], ['qx-tl', 8], ['qe-pf', 4]]
    : [['qe-cl', 4], ['qx-next', 8], ['qx-prog', 4], ['qx-cx', 8], ['qx-dn', 4], ['qe-wb', 8], ['qe-sv', 4], ['qx-mk', 8], ['qx-fc', 8], ['qx-pf', 4], ['qx-rr', 4], ['qx-ns', 4], ['qe-pf', 4]];
  for (const [id, span] of order as [string, number][]) { const el = sec(id); if (!el) continue; el.classList.remove('w6', 'w8', 'w12'); if (span !== 4) el.classList.add('w' + span); g.appendChild(el); }
}

function addToDashboard(): void {
  const m = document.getElementById('m'), g = m?.querySelector('.qx-dash .qx-g'); if (!m || !g || g.querySelector('#qe-cl')) return;
  const pro = !!m.querySelector('.qx-seg a.on[href$="professional"]');
  const anchor = pro ? g.querySelector('#qx-mi')?.closest('section') : g.querySelector('#qx-mk')?.closest('section');
  const tmp = document.createElement('div'); tmp.innerHTML = sectionsHtml(pro);
  const [cl, wb, sv, pf] = [...tmp.children] as HTMLElement[];
  if (anchor) anchor.before(wb, sv); else g.append(wb, sv);
  g.prepend(cl); g.append(pf);
  layout(g as HTMLElement, pro);
  cont(m.querySelector('#qe-clb')!); brief(m.querySelector('#qe-wbb')!, pro); saved(m.querySelector('#qe-svb')!); personalize(m.querySelector('#qe-pfb')!, pro);
}

// ---------- 5. company pages link to research and the concepts behind them ----------
async function companyLinks(): Promise<void> {
  const [, r, a] = location.hash.split('/'); if (r !== 'asset' || !a || a.includes('research')) return;
  const sym = decodeURIComponent(a).toUpperCase(), m = document.getElementById('m'); if (!m || m.querySelector('.qcr')) return;
  let lib = LIBCACHE; if (!lib) { try { lib = await library(); LIBCACHE = lib; } catch { lib = null; } }
  const rs = (lib?.articles || []).filter(x => (x.tickers || []).map(t => t.toUpperCase()).includes(sym));
  const host = m.querySelector('.ws') || m; if (!host.isConnected || m.querySelector('.qcr') || !m.querySelector('.big')) return;
  const L: [string, string][] = [['pe', 'The P/E ratio'], ['income', 'Reading an income statement'], ['cashflow', 'Cash flow and free cash flow'], ['ratios', 'Financial ratios'], ['dcf', 'Build a DCF']];
  m.insertAdjacentHTML('beforeend', `<div class="qcr" role="complementary" aria-label="Research and lessons for ${esc(sym)}"><div class="qx-two" style="display:grid;grid-template-columns:repeat(auto-fit,minmax(260px,1fr));gap:18px">
    <div><h3>Quantera research on ${esc(sym)}</h3>${rs.length ? `<ul>${rs.map(x => `<li><a href="#/research/${esc(x.slug)}">${esc(x.title)}</a> <span class="mu" style="font-size:12.5px">${esc((x as { classification?: string }).classification || 'Research')} · ${esc(when(x.date))}</span></li>`).join('')}</ul>` : `<p class="mu" style="margin:0">No Quantera study covers ${esc(sym)} yet. <a href="#/insights">Compare its reaction to news</a> or <a href="javascript:void 0" onclick="QFB_('company')">request one</a>.</p>`}</div>
    <div><h3>Understand these numbers</h3><ul>${L.map(([id, t]) => `<li><a href="#/lesson/${id}">${t}</a></li>`).join('')}</ul></div></div></div>`);
}

// ---------- 6. feedback categories ----------
function feedbackKinds(): void {
  // The database accepts four kinds (bug, data, idea, other); requests are filed as ideas with a clear label, so no
  // schema change is needed. The label is added to the message when the form is sent.
  const EXTRA: [string, string, string][] = [['company', 'Request a company', '[Company request] '], ['lesson', 'Suggest a lesson', '[Lesson idea] '], ['feature', 'Suggest a feature', '[Feature idea] ']];
  new MutationObserver(() => {
    const sel = document.querySelector<HTMLSelectElement>('#qfb select[name=kind]:not([data-qe])'); if (!sel) return;
    sel.dataset.qe = '1';
    const other = [...sel.options].find(o => o.value === 'other');
    EXTRA.forEach(([v, t]) => { const o = document.createElement('option'); o.value = v; o.textContent = t; sel.insertBefore(o, other || null); });
    const form = sel.form; if (!form) return;
    form.addEventListener('submit', () => {
      const x = EXTRA.find(e => e[0] === sel.value), ta = form.querySelector<HTMLTextAreaElement>('textarea'); if (!x || !ta) return;
      if (!ta.value.startsWith(x[2])) ta.value = x[2] + ta.value; const o = [...sel.options].find(op => op.value === 'idea'); if (o) sel.value = 'idea';
    }, true);
  }).observe(document.body, { childList: true, subtree: true });
}

export function initEngage(): void {
  // open the feedback form on one of the added kinds (the form itself only preselects its own four)
  (window as unknown as { QFB_: (k: string) => void }).QFB_ = (k: string) => {
    const f = (window as unknown as { qFeedback?: (k: string) => void }).qFeedback; if (!f) return; f('idea');
    setTimeout(() => { const s = document.querySelector<HTMLSelectElement>('#qfb select[name=kind]'); if (s && [...s.options].some(o => o.value === k)) s.value = k; }, 80);
  };
  document.head.insertAdjacentHTML('beforeend', `<style id="qe-css">${CSS}</style>`);
  for (const k of [RVC, RVL, PREFKEY]) if (!SYNC.includes(k)) SYNC.push(k);
  search(); feedbackKinds();
  const after = () => {
    remember();
    const r = location.hash.split('/')[1] || '';
    if (r === 'dashboard') { [60, 400, 1200].forEach(t => setTimeout(addToDashboard, t)); }
    if (r === 'asset') { [1500, 4000, 8000].forEach(t => setTimeout(companyLinks, t)); }
  };
  addEventListener('hashchange', after); setTimeout(after, 300);
}
