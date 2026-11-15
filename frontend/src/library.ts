// Research Library in the app: #/research (search and filter) and #/research/<slug> (the reader).
// Content lives in frontend/scripts/research.json; build-research.py turns it into static, indexable pages at
// /research/<slug> plus the JSON this module reads. The reader shows the static page's article body, so a study
// looks the same in the app and in search results, and there is one place to edit it.

interface Item { slug: string; title: string; dek: string; date: string; category: string; tickers?: string[]; readMinutes?: number; findings?: string[] }
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
@media (max-width:640px){.qra table{display:block;overflow-x:auto}}`;

const nice = (d: string) => { try { return new Date(d + 'T12:00:00Z').toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' }); } catch { return d; } };
let LIB: Lib | null = null;
export async function library(): Promise<Lib> {
  if (LIB) return LIB;
  const r = await fetch('/research/index.json'); if (!r.ok) throw new Error('library');
  return (LIB = await r.json() as Lib);
}

let query = '', cat = '';
async function libPage(): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  document.title = 'Research library | Quantera Research';
  m.innerHTML = `<h1>Research</h1><p class="qrl-lede">Original, sourced studies of companies, earnings and how markets react to the news. Every figure links to its source, facts are kept apart from interpretation, and every study states its limits.</p><div id="qrlb"><div class="qrx-sk" style="height:140px"></div></div>`;
  const box = m.querySelector('#qrlb') as HTMLElement;
  let lib: Lib;
  try { lib = await library(); } catch { box.innerHTML = '<p class="mu">The library could not be loaded. Please try again.</p>'; return; }
  const draw = () => {
    const q = query.trim().toLowerCase();
    const list = lib.articles.filter(a => (!cat || a.category === cat) && (!q || `${a.title} ${a.dek} ${a.category} ${(a.tickers || []).join(' ')} ${(a.findings || []).join(' ')}`.toLowerCase().includes(q)));
    (box.querySelector('.qrl-list') as HTMLElement).innerHTML = list.map(a => `<a class="qrl-card" href="#/research/${encodeURIComponent(a.slug)}"><small>${esc(a.category)} · ${esc(nice(a.date))} · ${a.readMinutes || 5} min read${a.tickers?.length ? ' · ' + a.tickers.map(esc).join(' ') : ''}</small><b>${esc(a.title)}</b><p>${esc(a.dek)}</p>${a.findings?.length ? `<ul>${a.findings.slice(0, 3).map(f => `<li>${esc(f)}</li>`).join('')}</ul>` : ''}</a>`).join('')
      || `<p class="mu">No study matches${q ? ` “${esc(query)}”` : ''}${cat ? ` in ${esc(cat)}` : ''} yet.</p>`;
    box.querySelectorAll<HTMLButtonElement>('[data-c]').forEach(b => b.classList.toggle('on', b.dataset.c === cat));
  };
  const used = new Set(lib.articles.map(a => a.category));
  box.innerHTML = `<div class="qrl-top"><input type="search" id="qrlq" placeholder="Search studies: a company, ticker or topic" aria-label="Search the research library" value="${esc(query)}"></div>
    <div class="qrl-cats"><button class="chip" data-c="">All</button>${lib.categories.map(c => `<button class="chip" data-c="${esc(c)}"${used.has(c) ? '' : ' title="No studies in this category yet"'}>${esc(c)}</button>`).join('')}</div>
    <div class="qrl-list"></div>
    <div class="pan" style="margin-top:22px"><h3 style="margin-top:0">How we research</h3><ul style="margin:0;padding-left:20px;line-height:1.7"><li>Primary sources first: company filings with the SEC, official releases and exchange data.</li><li>No source, no number: if a figure can’t be verified, we say it wasn’t found.</li><li>Facts and interpretation are labelled separately; a coincidence in time is never presented as a cause.</li><li>Every calculation is re-run independently before publication.</li></ul>
    <p class="mu" style="margin:10px 0 0">Want to test a question yourself? <a href="#/insights">Compare how companies reacted to the same news →</a></p></div>`;
  const inp = box.querySelector('#qrlq') as HTMLInputElement;
  inp.oninput = () => { query = inp.value; draw(); };
  box.querySelectorAll<HTMLButtonElement>('[data-c]').forEach(b => b.onclick = () => { cat = b.dataset.c || ''; draw(); });
  draw();
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
      else if (h.startsWith('#src')) a.addEventListener('click', e => { e.preventDefault(); document.getElementById(h.slice(1))?.scrollIntoView({ block: 'center' }); });
      else if (/^https?:/.test(h)) { a.setAttribute('target', '_blank'); a.setAttribute('rel', 'noopener noreferrer'); }
    });
    main.querySelector('h1')?.classList.add('qpt');
    m.innerHTML = `<article class="qra">${main.innerHTML}<p class="mu" style="font-size:13px">Permanent link: <a href="/research/${encodeURIComponent(slug)}">quanteraorg.vercel.app/research/${esc(slug)}</a></p></article>`;
    document.title = (doc.title || 'Research') ;
    const seen = LS<string[]>('q_rread', []); if (!seen.includes(slug)) SS('q_rread', [slug, ...seen].slice(0, 50));
    track('research_read');
  } catch { m.innerHTML = '<div class="qra"><h1>Study not found</h1><p class="mu">This study may have moved. <a href="#/research">Browse the research library</a>.</p></div>'; }
}

export function initLibrary(): void {
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
