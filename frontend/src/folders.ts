// Notion-style research folders: group stocks (with live prices), saved news stories and your own notes.
// Lives on the existing "My research" page (#/notes) as one section above the per-stock notes,
// plus an "Add to folder" button on every stock page. Saved in the browser and synced with an account.

interface Item { t: 's' | 'n'; sym?: string; k?: string; title?: string; url?: string; source?: string }
interface Folder { id: string; name: string; emoji: string; note: string; items: Item[]; at: number }
const KEY = 'q_col';
const EMOJI = ['📁', '🔬', '💡', '🚀', '🏦', '⚡', '🌍', '🧠'];
const load = (): Folder[] => LS<Folder[]>(KEY, []);
const save = (f: Folder[]) => SS(KEY, f);
const uid = () => Math.random().toString(36).slice(2, 9);
let openId: string | null = null;

const CSS = `
.qf-sec{margin-bottom:22px}.qf-h{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin:0 0 12px}.qf-h h3{margin:0;font-size:18px}.qf-h .btn{margin:0 0 0 auto}
.qf-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:12px}
.qf-card{display:flex;flex-direction:column;gap:6px;padding:16px!important;cursor:pointer;text-align:left;font:inherit;color:var(--tx);transition:transform .15s,border-color .2s}
.qf-card:hover{border-color:rgba(var(--acc),.55)!important;transform:translateY(-2px)}.qf-card.on{border-color:var(--ac)!important;background:rgba(var(--acc),.07)!important}
.qf-card .e{font-size:24px}.qf-card b{font-size:15px}.qf-card small{color:var(--mu)}
.qf-new{border:1px dashed rgba(var(--fgc),.2)!important;background:transparent!important;align-items:center;justify-content:center;color:var(--mu)}
.qf-open{margin-top:14px;padding:20px!important;animation:qfin .3s cubic-bezier(.22,1,.36,1)}@keyframes qfin{from{opacity:0;transform:translateY(8px)}}
.qf-title{display:flex;align-items:center;gap:10px;margin-bottom:12px}.qf-title button.e{font-size:26px;background:none;border:0;cursor:pointer;padding:0}
.qf-title input{flex:1;font-size:22px!important;font-weight:600;background:transparent!important;border:0!important;padding:4px 0!important;box-shadow:none!important;color:var(--tx)}
.qf-note{width:100%;min-height:90px;resize:vertical;font:inherit;font-size:15px;line-height:1.6;padding:12px!important}
.qf-items{margin:16px 0 0}.qf-items h4{margin:16px 0 6px;font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--mu)}
.qf-row{display:flex;align-items:center;gap:12px;padding:10px 4px;border-bottom:1px solid var(--ln)}.qf-row:last-child{border-bottom:0}
.qf-row a.n{flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.qf-row .x{background:none;border:0;color:var(--mu);cursor:pointer;font-size:16px}.qf-row .x:hover{color:var(--dn)}
.qf-add{display:flex;gap:8px;flex-wrap:wrap;margin-top:12px}.qf-add input,.qf-add select{flex:1;min-width:180px}
.qf-foot{display:flex;gap:8px;justify-content:space-between;flex-wrap:wrap;margin-top:16px;color:var(--mu);font-size:12.5px}
.qf-pop{position:fixed;z-index:120;width:260px;padding:8px;border-radius:16px}.qf-pop button{display:flex;gap:10px;align-items:center;width:100%;text-align:left;background:none;border:0;color:var(--tx);padding:9px 10px;border-radius:10px;cursor:pointer;font:inherit}
.qf-pop button:hover{background:rgba(var(--acc),.14)}.qf-pop small{margin-left:auto;color:var(--mu)}`;

function quotes(syms: string[]): Promise<Record<string, { price: number; changePct: number }>> {
  if (!syms.length || !QLIVE.market) return Promise.resolve({});
  return jf('/api/quotes?symbols=' + encodeURIComponent(syms.slice(0, 40).join(','))).then(({ ok, b }) => {
    const out: Record<string, { price: number; changePct: number }> = {};
    if (ok) for (const q of b.quotes || []) out[q.symbol] = q;
    return out;
  }).catch(() => ({}));
}

function renderSection(host: HTMLElement): void {
  let sec = document.getElementById('qfsec');
  if (!sec) { sec = document.createElement('div'); sec.id = 'qfsec'; sec.className = 'qf-sec'; host.prepend(sec); }
  const F = load();
  sec.innerHTML = `<div class="qf-h"><h3>Research folders</h3><span class="mu" style="font-size:13.5px">Group stocks, stories and notes, like pages in Notion.</span><button class="btn p" id="qfnew">+ New folder</button></div>
    <div class="qf-grid">${F.map(f => `<button class="pan qf-card ${f.id === openId ? 'on' : ''}" data-f="${f.id}"><span class="e">${f.emoji}</span><b>${esc(f.name)}</b><small>${f.items.length} item${f.items.length === 1 ? '' : 's'}${f.note ? ' · notes' : ''}</small></button>`).join('')}
    ${F.length ? '' : '<button class="pan qf-card qf-new" id="qfnew2">Create your first folder, e.g. “AI chip makers”</button>'}</div><div id="qfopen"></div>`;
  const create = () => { const f: Folder = { id: uid(), name: 'Untitled folder', emoji: EMOJI[F.length % EMOJI.length], note: '', items: [], at: Date.now() }; save([f, ...load()]); openId = f.id; track('folder_new'); renderSection(host); const t = document.querySelector<HTMLInputElement>('#qfopen .qf-title input'); t?.focus(); t?.select(); };
  (sec.querySelector('#qfnew') as HTMLButtonElement).onclick = create;
  const c2 = sec.querySelector('#qfnew2') as HTMLButtonElement | null; if (c2) c2.onclick = create;
  sec.querySelectorAll<HTMLElement>('[data-f]').forEach(b => b.onclick = () => { openId = openId === b.dataset.f ? null : b.dataset.f!; renderSection(host); });
  if (openId) renderOpen(host);
}

function renderOpen(host: HTMLElement): void {
  const box = document.getElementById('qfopen')!, F = load(), f = F.find(x => x.id === openId);
  if (!f) { box.innerHTML = ''; return; }
  const upd = (fn: (f: Folder) => void, redraw = true) => { const all = load(), g = all.find(x => x.id === f.id); if (!g) return; fn(g); g.at = Date.now(); save(all); if (redraw) renderSection(host); };
  const stocks = f.items.filter(i => i.t === 's'), news = f.items.filter(i => i.t === 'n');
  const saved = LS<any[]>('q_saved', []).filter(a => !news.some(n => n.k === String(a.id || a.url)));
  box.innerHTML = `<div class="pan qf-open"><div class="qf-title"><button class="e" title="Change icon">${f.emoji}</button><input value="${esc(f.name)}" aria-label="Folder name" maxlength="60"></div>
    <textarea class="qf-note" placeholder="Write your thinking here: why these companies, what you're watching for, questions to answer…" aria-label="Folder notes">${esc(f.note)}</textarea>
    <div class="qf-items"><h4>Stocks (${stocks.length})</h4>${stocks.map(i => `<div class="qf-row" data-sym="${esc(i.sym)}"><a class="n" href="#/asset/${encodeURIComponent(i.sym!)}"><b>${esc(i.sym)}</b></a><span class="p mu">…</span><button class="x" title="Remove" data-rm="s:${esc(i.sym)}">×</button></div>`).join('') || '<div class="mu" style="padding:6px 0">No stocks yet.</div>'}
    <div class="qf-add"><input placeholder="Add a stock by ticker, e.g. NVDA" aria-label="Add a stock" id="qfsym"><button class="btn" id="qfaddsym">Add</button></div>
    <h4>Saved stories (${news.length})</h4>${news.map(i => `<div class="qf-row"><a class="n" href="${esc(i.url || '#/news')}" target="_blank" rel="noopener">${esc(i.title)}</a><span class="mu" style="font-size:12.5px">${esc(i.source || '')}</span><button class="x" title="Remove" data-rm="n:${esc(i.k)}">×</button></div>`).join('') || '<div class="mu" style="padding:6px 0">No stories yet. Save stories on the News page, then add them here.</div>'}
    ${saved.length ? `<div class="qf-add"><select id="qfstory" aria-label="Add a saved story"><option value="">Add one of your saved stories…</option>${saved.slice(0, 30).map(a => `<option value="${esc(String(a.id || a.url))}">${esc(String(a.title).slice(0, 90))}</option>`).join('')}</select></div>` : ''}</div>
    <div class="qf-foot"><span>Saved automatically</span><button class="btn" id="qfdel">Delete folder</button></div></div>`;
  const name = box.querySelector('.qf-title input') as HTMLInputElement;
  name.oninput = () => upd(g => { g.name = name.value.trim() || 'Untitled folder'; }, false);
  name.onblur = () => renderSection(host);
  (box.querySelector('.qf-title .e') as HTMLButtonElement).onclick = () => upd(g => { g.emoji = EMOJI[(EMOJI.indexOf(g.emoji) + 1) % EMOJI.length]; });
  const note = box.querySelector('.qf-note') as HTMLTextAreaElement;
  note.oninput = () => upd(g => { g.note = note.value.slice(0, 5000); }, false);
  const addSym = async () => {
    const inp = box.querySelector('#qfsym') as HTMLInputElement, s = inp.value.trim().toUpperCase();
    if (!/^[A-Z0-9.\-^=]{1,15}$/.test(s)) return;
    if (QLIVE.market) { const q = await quotes([s]); if (!q[s]) { inp.value = ''; inp.placeholder = `Couldn’t find “${s}”. Check the ticker.`; return; } }
    upd(g => { if (!g.items.some(i => i.t === 's' && i.sym === s)) g.items.push({ t: 's', sym: s }); });
    track('folder_add_stock');
  };
  (box.querySelector('#qfaddsym') as HTMLButtonElement).onclick = addSym;
  (box.querySelector('#qfsym') as HTMLInputElement).onkeydown = e => { if (e.key === 'Enter') addSym(); };
  const sel = box.querySelector('#qfstory') as HTMLSelectElement | null;
  if (sel) sel.onchange = () => { const a = saved.find(x => String(x.id || x.url) === sel.value); if (a) upd(g => { g.items.push({ t: 'n', k: String(a.id || a.url), title: a.title, url: a.url, source: a.source }); }); };
  box.querySelectorAll<HTMLButtonElement>('[data-rm]').forEach(b => b.onclick = () => { const [t, v] = b.dataset.rm!.split(/:(.*)/); upd(g => { g.items = g.items.filter(i => !(i.t === t && (t === 's' ? i.sym === v : i.k === v))); }); });
  (box.querySelector('#qfdel') as HTMLButtonElement).onclick = () => { if (confirm(`Delete the folder “${f.name}”? This can’t be undone.`)) { save(load().filter(x => x.id !== f.id)); openId = null; renderSection(host); } };
  quotes(stocks.map(i => i.sym!)).then(Q => box.querySelectorAll<HTMLElement>('.qf-row[data-sym]').forEach(r => { const q = Q[r.dataset.sym!], p = r.querySelector('.p') as HTMLElement; if (q) p.innerHTML = `$${fmt(q.price, 2)} <span class="${q.changePct >= 0 ? 'up' : 'dn'}">${q.changePct >= 0 ? '+' : '−'}${fmt(Math.abs(q.changePct), 2)}%</span>`; else p.textContent = ''; }));
}

/** "Add to folder" button on stock pages, with a glass pop-up listing folders. */
function stockButton(): void {
  const m = /^#\/asset\/([^/]+)/.exec(location.hash); if (!m) return;
  const sym = decodeURIComponent(m[1]).toUpperCase();
  const watch = Array.from(document.querySelectorAll<HTMLElement>('#m [onclick^="WT_("]'))[0];
  if (!watch || document.getElementById('qfbtn')) return;
  watch.insertAdjacentHTML('afterend', `<button class="chip" id="qfbtn" style="margin-left:6px">📁 Add to folder</button>`);
  const btn = document.getElementById('qfbtn') as HTMLButtonElement;
  // the title row spreads its children apart; keep the title left and the action buttons together on the right
  const row = btn.parentElement, title = row?.firstElementChild as HTMLElement | null;
  if (row && title && getComputedStyle(row).justifyContent === 'space-between') { row.style.justifyContent = 'flex-start'; row.style.flexWrap = 'wrap'; row.style.gap = '6px'; title.style.marginRight = 'auto'; btn.style.marginLeft = '0'; }
  btn.onclick = e => {
    e.stopPropagation(); document.querySelector('.qf-pop')?.remove();
    const F = load(), r = btn.getBoundingClientRect(), pop = document.createElement('div');
    pop.className = 'qf-pop'; pop.style.left = Math.min(r.left, innerWidth - 270) + 'px'; pop.style.top = (r.bottom + 8) + 'px';
    pop.innerHTML = F.map(f => { const has = f.items.some(i => i.t === 's' && i.sym === sym); return `<button data-f="${f.id}"><span>${f.emoji}</span>${esc(f.name)}<small>${has ? '✓ added' : ''}</small></button>`; }).join('') + `<button data-f="new"><span>＋</span>New folder with ${esc(sym)}</button>`;
    document.body.appendChild(pop);
    pop.onclick = ev => {
      const b = (ev.target as Element).closest<HTMLElement>('[data-f]'); if (!b) return;
      const all = load(); let f = all.find(x => x.id === b.dataset.f);
      if (!f) { f = { id: uid(), name: sym + ' research', emoji: EMOJI[all.length % EMOJI.length], note: '', items: [], at: Date.now() }; all.unshift(f); }
      if (!f.items.some(i => i.t === 's' && i.sym === sym)) f.items.push({ t: 's', sym });
      f.at = Date.now(); save(all); pop.remove(); track('folder_add_from_stock');
      qtoast(`📁 Added <b>${esc(sym)}</b> to “${esc(f.name)}”. <a href="#/notes" style="color:var(--ac)">Open folders →</a>`);
    };
    setTimeout(() => document.addEventListener('click', () => pop.remove(), { once: true }), 0);
  };
}

export function initFolders(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qfolders-css">${CSS}</style>`);
  if (!SYNC.includes(KEY)) SYNC.push(KEY);
  const notes = PAGES.notes;
  PAGES.notes = (a?: string) => {
    notes(a);
    const m = document.getElementById('m')!;
    // the old empty state only talks about per-stock notes; folders sit above it either way
    const host = (m.querySelector('.body') as HTMLElement) || m;
    renderSection(host);
  };
  if (/^#\/notes/.test(location.hash)) { const m = document.getElementById('m')!; renderSection((m.querySelector('.body') as HTMLElement) || m); }
  // stock pages render asynchronously; add the button whenever one appears
  let t: number | undefined;
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(stockButton, 120); }).observe(document.getElementById('m')!, { childList: true, subtree: true });
  stockButton();
}
