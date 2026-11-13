// Shows how an AI answer is grounded: clickable sources for the data it looked up, and a check of its figures
// against that data (done on the server, see backend Grounding.java). Used by the AI chat and the deep research report.

export type Check = { figures: number; matched: number; unmatched: string[] } | null | undefined;
export type Source = { label: string; url: string };

const CSS = `
.qgr{margin-top:8px;font-size:12.5px;line-height:1.5}
.qgr-ck{display:inline-flex;gap:6px;align-items:center;padding:3px 10px;border-radius:999px;border:1px solid var(--ln);color:var(--mu)}
.qgr-ck.ok{color:#4fd1a5;border-color:rgba(79,209,165,.35)}
.qgr-ck.part{color:#e8c77a;border-color:rgba(232,199,122,.35)}
.qgr-um{color:var(--mu);margin:6px 0 0}
.qgr ol{margin:6px 0 0;padding-left:18px;color:var(--mu)}.qgr li{margin:2px 0}.qgr li a{color:var(--tx);text-decoration:underline;text-underline-offset:2px;word-break:break-word}
.qgr summary{cursor:pointer;color:var(--mu);margin-top:6px}`;

/** The figure check as a short line, e.g. "✓ All 6 figures match the source data". */
export function checkLine(k: Check): string {
  if (!k || !k.figures) return '';
  const all = k.matched >= k.figures;
  const head = all ? `✓ All ${k.figures} figure${k.figures > 1 ? 's' : ''} match the source data`
    : `${k.matched} of ${k.figures} figures match the source data`;
  const tip = 'Each price, percentage and amount in the answer is compared with the data the AI was given, allowing for rounding.';
  return `<span class="qgr-ck ${all ? 'ok' : 'part'}" title="${esc(tip)}">${head}</span>`
    + (all ? '' : `<p class="qgr-um">Not found in the data: ${k.unmatched.map(x => `<b>${esc(x)}</b>`).join(', ')}. These may be the AI's own arithmetic or rounding, or a mistake, so check them against the sources.</p>`);
}

const href = (u: string) => /^https:\/\//.test(u) || /^\/#\//.test(u) ? u : '';
export function sourceList(src: Source[]): string {
  const ok = (src || []).filter(s => href(s.url));
  if (!ok.length) return '';
  return `<ol>${ok.map(s => { const ext = /^https:/.test(s.url); return `<li><a href="${esc(href(s.url))}"${ext ? ' target="_blank" rel="noopener noreferrer"' : ''}>${esc(s.label)}</a></li>`; }).join('')}</ol>`;
}

type Msg = { t?: string; u?: string[]; s?: Source[]; k?: Check };
/** Footer for one AI chat reply. */
function chatFooter(m: Msg): string {
  if (!m.t) return '';
  const ck = checkLine(m.k), src = sourceList(m.s || []);
  if (!ck && !src) return (m.u || []).length ? `<div class="mu" style="font-size:12px;margin-top:6px">Used: ${(m.u || []).map(esc).join(' · ')}</div>` : '';
  return `<div class="qgr">${ck}${src ? `<details${ck && !(m.k && m.k.matched >= m.k.figures) ? ' open' : ''}><summary>Sources (${(m.s || []).filter(x => href(x.url)).length})</summary>${src}</details>` : ''}</div>`;
}

export function initGrounding(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qgr-css">${CSS}</style>`);
  (window as unknown as { qGround: (m: Msg) => string }).qGround = chatFooter;
}
