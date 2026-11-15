// Multiple watchlists. The app's code everywhere reads and writes one watchlist (the WL set, saved as q_wl); this
// keeps that as the *active* list and stores named lists alongside it (q_wls). Switching lists swaps the contents
// of WL, so every page that shows the watchlist (Markets, Trade, Portfolio, alerts) follows without changes.
declare const WL: Set<string>;

type Lists = { active: string; lists: Record<string, string[]> };
const KEY = 'q_wls';
const CSS = `.qwl{display:flex;gap:8px;align-items:center;flex-wrap:wrap;margin:6px 0 10px;font-size:13px}
.qwl select{font:inherit;font-size:13.5px;padding:6px 10px!important;max-width:220px}.qwl .chip{min-height:30px}`;

function load(): Lists {
  const s = LS<Lists | null>(KEY, null);
  const cur = [...WL];
  if (!s || !s.lists || !s.active || !s.lists[s.active]) return { active: 'My watchlist', lists: { 'My watchlist': cur } };
  s.lists[s.active] = cur; // the live set is the truth for the active list
  return s;
}
const save = (s: Lists) => SS(KEY, s);

function switchTo(name: string): void {
  const s = load(); if (!s.lists[name]) return;
  s.active = name; save(s);
  WL.clear(); for (const t of s.lists[name]) WL.add(t);
  SS('q_wl', [...WL]);
  track('watchlist_switch');
  try { route(); } catch { location.reload(); }
}

function bar(): string {
  const s = load(), names = Object.keys(s.lists);
  return `<div class="qwl" data-qwl><label for="qwls" class="mu">List</label><select id="qwls" aria-label="Choose a watchlist">${names.map(n => `<option${n === s.active ? ' selected' : ''}>${esc(n)}</option>`).join('')}</select>
    <button class="chip" data-w="new">+ New list</button>${names.length > 1 ? `<button class="chip" data-w="ren">Rename</button><button class="chip" data-w="del">Delete</button>` : ''}</div>`;
}

function bind(host: HTMLElement): void {
  const sel = host.querySelector<HTMLSelectElement>('#qwls');
  if (sel) { sel.onchange = () => switchTo(sel.value); for (const ev of ['input', 'change', 'click', 'keydown']) sel.addEventListener(ev, e => e.stopPropagation()); }
  host.querySelectorAll<HTMLButtonElement>('[data-w]').forEach(b => b.onclick = e => {
    e.stopPropagation();
    const s = load(), act = b.dataset.w;
    if (act === 'new') {
      const n = (prompt('Name for the new watchlist (for example "Chip stocks" or "Earnings this week"):') || '').trim().slice(0, 40);
      if (!n) return; if (s.lists[n]) { qtoast('A list with that name already exists.'); return; }
      s.lists[n] = []; save(s); switchTo(n); qtoast(`Created “${esc(n)}”. Add stocks with ☆ on any stock page.`);
    } else if (act === 'ren') {
      const n = (prompt('New name for this watchlist:', s.active) || '').trim().slice(0, 40);
      if (!n || n === s.active) return; if (s.lists[n]) { qtoast('A list with that name already exists.'); return; }
      s.lists[n] = s.lists[s.active]; delete s.lists[s.active]; s.active = n; save(s); try { route(); } catch { /* */ }
    } else if (act === 'del') {
      if (!confirm(`Delete the watchlist “${s.active}”? The stocks stay on your other lists.`)) return;
      delete s.lists[s.active]; const next = Object.keys(s.lists)[0]; s.active = next; save(s); switchTo(next);
    }
  });
}

// put the list switcher next to every watchlist heading on the page
function attach(): void {
  const m = document.getElementById('m'); if (!m) return;
  const heads = [...m.querySelectorAll<HTMLElement>('h3, h4')].filter(h => /^(Your watchlist|Watchlist)\b/.test((h.textContent || '').trim()));
  for (const h of heads) {
    if (h.nextElementSibling?.hasAttribute('data-qwl') || m.querySelector('[data-qwl]')) continue; // one switcher per page
    h.insertAdjacentHTML('afterend', bar());
    bind(h.nextElementSibling as HTMLElement);
  }
}

export function initWatchlists(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qwl-css">${CSS}</style>`);
  if (!SYNC.includes(KEY)) SYNC.push(KEY);
  const m = document.getElementById('m');
  if (m) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(attach, 60); }).observe(m, { childList: true, subtree: true }); }
}
