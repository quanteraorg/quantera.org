// Research Library in the app: #/research (search and filter) and #/research/<slug> (the reader).
// Content lives in frontend/scripts/research.json; build-research.py turns it into static, indexable pages at
// /research/<slug> plus the JSON this module reads. The reader shows the static page's article body, so a study
// looks the same in the app and in search results, and there is one place to edit it.

interface Item { slug: string; title: string; dek: string; date: string; updated?: string; category: string; classification?: string; industry?: string; tickers?: string[]; readMinutes?: number; findings?: string[]; sources?: number; primarySources?: number }
declare function isSimple(): boolean;
import { prefs } from './prefs';
interface Lib { categories: string[]; articles: Item[] }

const CSS = `
.qrl-top{display:flex;gap:10px;flex-wrap:wrap;align-items:center;margin:4px 0 14px}.qrl-top input{flex:1;min-width:220px;font:inherit;font-size:14px;padding:10px 12px!important}
.qrl-cats{display:flex;gap:8px;flex-wrap:wrap;margin:0 0 18px}
.qrl-list{display:grid;gap:14px}
.qrl-card{display:block;border:1px solid var(--ln);border-radius:16px;padding:18px 20px;background:var(--s1);color:inherit;text-decoration:none;transition:border-color .15s}
.qrl-card:hover{border-color:rgba(var(--acc),.55)}.qrl-card small{color:var(--mu);font-size:12.5px;letter-spacing:.02em}
.qrl-card b{display:block;font-family:Fraunces,Georgia,serif;font-weight:400;font-size:clamp(19px,2vw,24px);line-height:1.28;margin:6px 0}
.qrl-card p{margin:0;color:var(--mu);font-size:14.5px;line-height:1.55}.qrl-card ul{margin:10px 0 0;padding-left:18px;font-size:13.5px;line-height:1.55}
.qrl-lede{font-size:16px;line-height:1.6;max-width:760px;margin:6px 0 16px}
.qra{max-width:780px}.qra h1{line-height:1.15;margin:6px 0 10px}
.qra h2{font-family:Fraunces,Georgia,serif;font-weight:400;font-size:22px;margin:28px 0 10px}.qra p,.qra li{font-size:16px;line-height:1.7}
.qra .crumb{font-size:13px;color:var(--mu)}.qra .dek{font-size:19px;line-height:1.5;color:var(--mu);margin:4px 0 14px}.qra .meta{font-size:13.5px;color:var(--mu);margin:0 0 22px}.qra .meta b{color:var(--ac,#c8a96a)}
.qra .box{border:1px solid var(--ln);border-radius:14px;padding:14px 18px;margin:16px 0;background:var(--s1)}.qra .box h2{margin-top:0;font-size:19px}
.qra .lbl{display:inline-block;font-size:11px;letter-spacing:.06em;text-transform:uppercase;border:1px solid rgba(232,199,122,.45);color:#e8c77a;border-radius:999px;padding:2px 9px;margin-left:8px;vertical-align:3px;font-family:Manrope,system-ui,sans-serif}
.qra table{width:100%;border-collapse:collapse;margin:10px 0 18px}.qra .chart svg{width:100%;max-width:680px;height:auto}.qra .chart text{fill:var(--mu);font-size:11px}.qra .src{font-size:12.5px;color:var(--mu)}
.qra ol.srcs li{font-size:14px;margin:6px 0}.qra ol.srcs small{color:var(--mu)}.qra .lib a.card{display:block;border:1px solid var(--ln);border-radius:14px;padding:14px 16px;margin:10px 0;color:inherit;text-decoration:none}
.qra .lib .card b{display:block;font-family:Fraunces,Georgia,serif;font-weight:400;font-size:19px}.qra .lib .card small{color:var(--mu)}
@media (max-width:640px){.qra table{display:block;overflow-x:auto}}
.qrl-k{display:inline-block;font-size:10.5px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;border-radius:6px;padding:2px 6px;margin-right:4px;background:rgba(122,166,232,.16);color:#9cbcf0}
.qrl-f{display:flex;gap:10px;flex-wrap:wrap;margin:0 0 16px}.qrl-f label{display:flex;flex-direction:column;gap:4px;font-size:12px;color:var(--mu)}.qrl-f select{font:inherit;font-size:13.5px;padding:7px 10px!important;min-width:150px}
.qrl-kd{margin:16px 0 0;font-size:14px}.qrl-kd summary{cursor:pointer;color:var(--mu)}.qrl-kd dl{display:grid;grid-template-columns:max-content 1fr;gap:6px 14px;margin:10px 0 0}.qrl-kd dt{font-weight:600}.qrl-kd dd{margin:0;color:var(--mu)}
.qra .cls{display:inline-block;font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;border-radius:6px;padding:2px 7px;margin-right:6px;background:rgba(122,166,232,.16);color:#9cbcf0}
.qra .depthbar{display:inline-flex;flex-wrap:wrap;border:1px solid var(--ln);border-radius:99px;overflow:hidden;margin:0 0 12px}.qra .depthbar button{font:inherit;font-size:13.5px;background:none;border:0;color:var(--mu);padding:8px 14px;cursor:pointer}.qra .depthbar button.on{color:var(--tx);background:rgba(var(--acc),.16)}
.qra.depth-overview .dz[data-depth=detail],.qra.depth-overview .dz[data-depth=method],.qra.depth-overview .toc{display:none}.qra.depth-method .dz[data-depth=overview],.qra.depth-method .dz[data-depth=detail],.qra.depth-method .toc{display:none}
.qra .toc{border-left:2px solid rgba(var(--acc),.5);padding:4px 0 4px 14px;margin:18px 0}.qra .toc ol{margin:6px 0 0;padding-left:18px;font-size:14px;line-height:1.75}
.qra .terms dl{display:grid;grid-template-columns:max-content 1fr;gap:6px 14px;margin:6px 0}.qra .terms dt{font-weight:600}.qra .terms dd{margin:0}@media(max-width:560px){.qra .terms dl{grid-template-columns:1fr}}
.qra-dn{font-size:13.5px;color:var(--mu);margin:0 0 14px}
html[data-theme=light] .qrl-k,html[data-theme=light] .qra .cls{color:#1f4f93;background:rgba(122,166,232,.2)}`;

const nice = (d: string) => { try { return new Date(d + 'T12:00:00Z').toLocaleDateString('en-US', { day: 'numeric', month: 'long', year: 'numeric', timeZone: 'UTC' }); } catch { return d; } };
let LIB: Lib | null = null;
export async function library(): Promise<Lib> {
  if (LIB) return LIB;
  const r = await fetch('/research/index.json'); if (!r.ok) throw new Error('library');
  return (LIB = await r.json() as Lib);
}

let query = '', cat = '', kind = '', industry = '', company = '', sort: 'published' | 'updated' = 'published';
// What each research classification means; shown in the library so a reader knows what kind of work they're opening.
const KINDS: [string, string][] = [['Original research', 'A new study by Quantera from primary sources'], ['Historical analysis', 'A study of a past event or period, accurate as of its publication date'], ['Educational', 'An explainer of a concept or method'], ['External reporting', 'A summary of reporting by other publishers']];
async function libPage(): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  document.title = 'Research library | Quantera Research';
  m.innerHTML = `<h1>Research</h1><p class="qrl-lede">Original, sourced studies of companies, earnings and how markets react to the news. Every figure links to its source, facts are kept apart from interpretation, and every study states its limits.</p><div id="qrlb"><div class="qrx-sk" style="height:140px"></div></div>`;
  const box = m.querySelector('#qrlb') as HTMLElement;
  let lib: Lib;
  try { lib = await library(); } catch { box.innerHTML = '<p class="mu">The library could not be loaded. Please try again.</p>'; return; }
  const draw = () => {
    const q = query.trim().toLowerCase();
    const saved = LS<string[]>('q_rsave', []);
    const list = lib.articles.filter(a => (!cat || (cat === '★' ? saved.includes(a.slug) : a.category === cat)) && (!kind || a.classification === kind) && (!industry || a.industry === industry) && (!company || (a.tickers || []).includes(company))
      && (!q || `${a.title} ${a.dek} ${a.category} ${a.classification || ''} ${a.industry || ''} ${(a.tickers || []).join(' ')} ${(a.findings || []).join(' ')}`.toLowerCase().includes(q)))
      .sort((x, y) => sort === 'updated' ? (y.updated || y.date).localeCompare(x.updated || x.date) : y.date.localeCompare(x.date));
    (box.querySelector('.qrl-list') as HTMLElement).innerHTML = list.map(a => `<a class="qrl-card" href="#/research/${encodeURIComponent(a.slug)}"><small><span class="qrl-k">${esc(a.classification || 'Research')}</span> ${esc(a.category)}${a.industry ? ' · ' + esc(a.industry) : ''} · Published ${esc(nice(a.date))}${a.updated ? ' · Updated ' + esc(nice(a.updated)) : ''} · ${a.readMinutes || 5} min read${a.sources ? ` · ${a.sources} sources${a.primarySources ? ` (${a.primarySources} primary)` : ''}` : ''}${a.tickers?.length ? ' · ' + a.tickers.map(esc).join(' ') : ''}</small><b>${esc(a.title)}</b><p>${esc(a.dek)}</p>${a.findings?.length ? `<ul>${a.findings.slice(0, 3).map(f => `<li>${esc(f)}</li>`).join('')}</ul>` : ''}</a>`).join('')
      || (cat === '★' ? '<p class="mu">No saved studies yet. Use “☆ Save” on any study to keep it here (on this device).</p>' : `<p class="mu">No study matches${q ? ` “${esc(query)}”` : ''}${cat ? ` in ${esc(cat)}` : ''} yet.</p>`);
    box.querySelectorAll<HTMLButtonElement>('[data-c]').forEach(b => b.classList.toggle('on', b.dataset.c === cat));
  };
  const used = new Set(lib.articles.map(a => a.category));
  const kinds = KINDS.filter(k => lib.articles.some(a => a.classification === k[0]));
  const inds = [...new Set(lib.articles.map(a => a.industry).filter(Boolean) as string[])].sort();
  const cos = [...new Set(lib.articles.flatMap(a => a.tickers || []))].sort();
  const opt = (v: string, cur: string, label = v) => `<option value="${esc(v)}"${v === cur ? ' selected' : ''}>${esc(label)}</option>`;
  box.innerHTML = `<div class="qrl-top"><input type="search" id="qrlq" placeholder="Search studies: a company, ticker or topic" aria-label="Search the research library" value="${esc(query)}"></div>
    <div class="qrl-cats"><button class="chip" data-c="">All</button><button class="chip" data-c="★">★ Saved</button>${lib.categories.map(c => `<button class="chip" data-c="${esc(c)}"${used.has(c) ? '' : ' title="No studies in this category yet"'}>${esc(c)}</button>`).join('')}</div>
    <div class="qrl-f"><label>Type <select id="qrlk"><option value="">All types</option>${kinds.map(k => opt(k[0], kind)).join('')}</select></label><label>Industry <select id="qrli"><option value="">All industries</option>${inds.map(i => opt(i, industry)).join('')}</select></label><label>Company <select id="qrlco"><option value="">All companies</option>${cos.map(c => opt(c, company)).join('')}</select></label><label>Sort <select id="qrls">${opt('published', sort, 'Newest first')}${opt('updated', sort, 'Recently updated')}</select></label></div>
    <div class="qrl-list"></div>
    <details class="qrl-kd"><summary>What the research types mean</summary><dl>${KINDS.map(k => `<dt>${esc(k[0])}</dt><dd>${esc(k[1])}</dd>`).join('')}</dl></details>
    <div class="pan" style="margin-top:22px"><h3 style="margin-top:0">How we research</h3><ul style="margin:0;padding-left:20px;line-height:1.7"><li>Primary sources first: company filings with the SEC, official releases and exchange data.</li><li>No source, no number: if a figure can’t be verified, we say it wasn’t found.</li><li>Facts and interpretation are labeled separately; a coincidence in time is never presented as a cause.</li><li>Every calculation is re-run independently before publication.</li></ul>
    <p class="mu" style="margin:10px 0 0">Want to test a question yourself? <a href="#/insights">Compare how companies reacted to the same news →</a></p></div>`;
  const inp = box.querySelector('#qrlq') as HTMLInputElement;
  inp.oninput = () => { query = inp.value; draw(); };
  box.querySelectorAll<HTMLButtonElement>('[data-c]').forEach(b => b.onclick = () => { cat = b.dataset.c || ''; draw(); });
  const sel = (id: string, set: (v: string) => void) => { const e = box.querySelector<HTMLSelectElement>(id); if (e) e.onchange = () => { set(e.value); draw(); }; };
  sel('#qrlk', v => kind = v); sel('#qrli', v => industry = v); sel('#qrlco', v => company = v); sel('#qrls', v => sort = v === 'updated' ? 'updated' : 'published');
  draw();
}

// Research depth in the app: the same study at three levels of detail. Student starts with the Overview,
// Professional with the full analysis; either can switch. The static page has the same control.
function depth(m: HTMLElement): void {
  const art = m.querySelector<HTMLElement>('.qra'), bar = m.querySelector<HTMLElement>('.depthbar'); if (!art || !bar) return;
  const set = (d: string) => { art.classList.remove('depth-overview', 'depth-method'); if (d !== 'detail') art.classList.add('depth-' + d);
    bar.querySelectorAll<HTMLButtonElement>('button').forEach(b => { const on = b.dataset.d === d; b.classList.toggle('on', on); b.setAttribute('aria-pressed', String(on)); }); };
  bar.addEventListener('click', e => { const b = (e.target as HTMLElement).closest<HTMLButtonElement>('button[data-d]'); if (b) { set(b.dataset.d!); track('research_depth_' + b.dataset.d); } });
  const student = typeof isSimple === 'function' && isSimple();
  const pd = prefs().depth;
  set(pd || (student ? 'overview' : 'detail'));
  if (student && !pd) bar.insertAdjacentHTML('afterend', '<p class="qra-dn">Student view starts with the overview: the question, the summary, the key findings and the terms you need. Choose <b>Detailed analysis</b> for the full study.</p>');
}

async function reader(slug: string): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  m.innerHTML = '<div class="qra"><div class="qrx-sk" style="height:220px"></div></div>';
  try {
    const r = await fetch(`/research/${encodeURIComponent(slug)}.html`);
    if (!r.ok) throw new Error('missing');
    const doc = new DOMParser().parseFromString(await r.text(), 'text/html'), main = doc.querySelector('main');
    if (!main) throw new Error('bad');
    // links inside the study: keep in-app ones in the app; open sources in a new tab
    main.querySelectorAll('a[href]').forEach(a => {
      const h = a.getAttribute('href') || '';
      if (h.startsWith('/#/')) a.setAttribute('href', h.slice(1));
      else if (h === '/research') a.setAttribute('href', '#/research');
      else if (h.startsWith('/research/')) a.setAttribute('href', '#' + h);
      else if (/^https?:/.test(h)) { a.setAttribute('target', '_blank'); a.setAttribute('rel', 'noopener noreferrer'); }
    });
    main.querySelector('h1')?.classList.add('qpt');
    const isSaved = () => LS<string[]>('q_rsave', []).includes(slug);
    m.innerHTML = `<article class="qra"><button class="chip" id="qrsv" style="float:right;margin:4px 0 0 12px" aria-pressed="${isSaved()}">${isSaved() ? '★ Saved' : '☆ Save'}</button>${main.innerHTML}<p class="mu" style="font-size:13px">Permanent link: <a href="/research/${encodeURIComponent(slug)}">quanteraorg.vercel.app/research/${esc(slug)}</a></p></article>`;
    const sv = m.querySelector('#qrsv') as HTMLButtonElement;
    sv.onclick = () => { const l = LS<string[]>('q_rsave', []), on = l.includes(slug); SS('q_rsave', on ? l.filter(x => x !== slug) : [slug, ...l].slice(0, 100)); sv.textContent = on ? '☆ Save' : '★ Saved'; sv.setAttribute('aria-pressed', String(!on)); if (!on) track('research_save'); };
    depth(m);
    // in-page links (contents, citations): scroll instead of changing the address, which the router would treat as a page
    m.querySelector('.qra')?.addEventListener('click', e => {
      const a = (e.target as HTMLElement).closest<HTMLAnchorElement>('a[href^="#"]'), h = a?.getAttribute('href') || '';
      if (!a || !/^#[a-z][\w-]*$/i.test(h)) return;
      e.preventDefault(); document.getElementById(h.slice(1))?.scrollIntoView({ block: h.startsWith('#src') ? 'center' : 'start', behavior: 'smooth' });
    });
    document.title = doc.title || 'Research';
    const seen = LS<string[]>('q_rread', []); if (!seen.includes(slug)) SS('q_rread', [slug, ...seen].slice(0, 50));
    track('research_read');
  } catch { m.innerHTML = '<div class="qra"><h1>Study not found</h1><p class="mu">This study may have moved. <a href="#/research">Browse the research library</a>.</p></div>'; }
}

export function initLibrary(): void {
  for (const k of ['q_rsave', 'q_rread']) if (!SYNC.includes(k)) SYNC.push(k); // follow the account like watchlists
  document.head.insertAdjacentHTML('beforeend', `<style id="qrl-css">${CSS}</style>`);
  PAGES.research = (a?: string) => {
    const slug = /^#\/research\/([a-z0-9-]+)/.exec(location.hash)?.[1] || (a && a !== 'research' && /^[a-z0-9-]+$/.test(a) ? a : '');
    track('view_research');
    if (slug) reader(slug); else libPage();
  };
  (HUB as Record<string, string>).research = 'research';
  if (SUBT.research && !SUBT.research.some(x => x[1] === 'research')) SUBT.research.unshift(['Library', 'research']);
  // the side menu's "Stocks" item becomes "Research", opening the library (stocks stay one tab away)
  const relabel = () => {
    const a = document.querySelector<HTMLAnchorElement>('[data-r=research]'); if (!a) return;
    a.setAttribute('href', '#/research');
    for (const n of a.childNodes) if (n.nodeType === 3 && /Stocks/.test(n.textContent || '')) n.textContent = (n.textContent || '').replace('Stocks', 'Research');
    const s = a.querySelector('span:not([class])'); if (s && /^Stocks$/.test(s.textContent || '')) s.textContent = 'Research';
  };
  try { (window as any).HL.research = '#/research'; } catch { /* older layer without HL */ }
  relabel(); new MutationObserver(relabel).observe(document.body, { childList: true, subtree: true });
}
