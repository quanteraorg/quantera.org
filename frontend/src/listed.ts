// Private companies that have since listed (Anthropic became ANT on 19 Nov 2026). The server decides with /api/listed:
// a US stock whose name starts with the company's name and that first traded within the last three years.
// Search entries then open the stock, and the private-company page says it's now public, with links to trade it.
const seen = new Map<string, Promise<{ listed: boolean; symbol?: string; firstTrade?: string } | null>>();
const lookup = (name: string) => {
  const k = name.trim().toLowerCase();
  if (!seen.has(k)) seen.set(k, fetch('/api/listed?name=' + encodeURIComponent(name.trim())).then(r => (r.ok ? r.json() : null)).catch(() => null));
  return seen.get(k)!;
};

function search(): void {
  const res = document.getElementById('res');
  if (!res) return;
  new MutationObserver(() => {
    res.querySelectorAll<HTMLAnchorElement>('.pvres a[href^="#/pvt/"]:not([data-lc])').forEach(a => {
      a.dataset.lc = '1';
      const name = a.querySelector('span')?.textContent || '';
      if (!name) return;
      lookup(name).then(r => {
        if (!r?.listed || !r.symbol || !a.isConnected) return;
        const priv = a.getAttribute('href')!;
        a.setAttribute('href', '#/asset/' + encodeURIComponent(r.symbol));
        const tag = a.querySelectorAll('span')[1];
        if (tag) tag.innerHTML = `Now public · <b>${esc(r.symbol)}</b> · <span class="qls-old" data-h="${esc(priv)}">pre-IPO history</span>`;
        tag?.querySelector<HTMLElement>('.qls-old')?.addEventListener('click', e => { e.preventDefault(); e.stopPropagation(); location.hash = priv; });
      });
    });
  }).observe(res, { childList: true, subtree: true });
}

function banner(): void {
  const m = document.getElementById('m');
  if (!m) return;
  new MutationObserver(() => {
    if (!/^#\/pvt\//.test(location.hash)) return;
    const h1 = m.querySelector<HTMLElement>('.pvhd h1');
    if (!h1 || h1.dataset.lc) return;
    h1.dataset.lc = '1';
    const name = h1.textContent || '';
    lookup(name).then(r => {
      if (!r?.listed || !r.symbol || !h1.isConnected || m.querySelector('.qls-ban')) return;
      const s = esc(r.symbol);
      m.querySelector('.pvhd')?.insertAdjacentHTML('beforebegin', `<div class="pan qls-ban"><b>${esc(name)} is now a public company.</b> It began trading in the US as <b>${s}</b>${r.firstTrade ? ` on ${esc(r.firstTrade)}` : ''}, so its share price now comes from the stock market. The funding rounds below are its history before the IPO.<div class="qls-go"><a class="btn p" href="#/asset/${s}">Open ${s}</a><a class="btn" href="#/trade/${s}">Paper trade ${s}</a></div></div>`);
      m.querySelector('.pvhd .tag')?.replaceChildren(document.createTextNode('Now public · ' + r.symbol));
    });
  }).observe(m, { childList: true, subtree: true });
}

export function initListed(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qls-css">.qls-ban{margin:0 0 14px;border-color:rgba(79,209,165,.5)!important;line-height:1.55;font-size:14.5px}.qls-go{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}.qls-go .btn{margin:0}.qls-old{text-decoration:underline;cursor:pointer}</style>`);
  search();
  banner();
}
