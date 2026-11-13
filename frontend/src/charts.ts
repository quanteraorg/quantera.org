// Chart extras on top of the chart engine in app.js:
// - indicator search: type "RSI", "bands", "volatility" or "trend" to filter the Indicators menu
// - named chart templates: save the current chart set-up (type, indicators and their settings, colours) under a name,
//   switch between saved set-ups, delete them. Synced with the account like other chart settings.
// The engine keeps its own single template slot (q_tpl); named templates are copied in and out of it, so the engine's
// save/load code stays the single place that knows what a template contains.

const KW: Record<string, string> = {
  ma20: 'moving average sma trend', ma50: 'moving average sma trend', ma200: 'moving average sma trend long term',
  ema9: 'exponential moving average ema trend', ema21: 'exponential moving average ema trend', vwap: 'volume weighted average price intraday',
  bb: 'bollinger bands volatility', dc: 'donchian channel breakout high low', psar: 'parabolic sar stop and reverse trend',
  st: 'supertrend trend stop', ichi: 'ichimoku cloud trend', kc: 'keltner channel volatility atr', vp: 'volume profile price levels',
  vol: 'volume', rsi: 'relative strength index momentum overbought oversold', macd: 'moving average convergence divergence momentum trend',
  stoch: 'stochastic oscillator momentum overbought oversold', atr: 'average true range volatility', obv: 'on balance volume accumulation',
  cci: 'commodity channel index momentum', wr: 'williams percent r momentum overbought oversold', roc: 'rate of change momentum',
  mfi: 'money flow index volume overbought oversold', ao: 'awesome oscillator momentum', adx: 'average directional index dmi di trend strength',
};
const CSS = `
.qcs{width:100%;box-sizing:border-box;margin:0 0 10px;padding:9px 12px!important;font:inherit;font-size:14px}
.qcs-none{color:var(--mu);font-size:13px;margin:4px 0 8px}
.qtp{border-top:1px solid var(--ln);margin-top:12px;padding-top:12px}.qtp h4{margin:0 0 4px;font-size:14px}.qtp p{margin:0 0 8px;color:var(--mu);font-size:12.5px}
.qtp .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.qtp input{flex:1;min-width:150px;padding:8px 10px!important;font:inherit;font-size:13.5px}
.qtp ul{list-style:none;margin:10px 0 0;padding:0}.qtp li{display:flex;gap:8px;align-items:center;padding:6px 0;border-bottom:1px solid var(--ln);font-size:13.5px}
.qtp li b{flex:1;font-weight:500;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.qtp li .btn,.qtp .row .btn{margin:0;padding:6px 12px}`;

type Tpl = { name: string; at: number; data: unknown };
const KEY = 'q_tpls';
const all = (): Tpl[] => {
  const t = LS<Tpl[]>(KEY, []);
  if (!t.length) { try { const old = JSON.parse(localStorage.getItem('q_tpl') || 'null'); if (old) return [{ name: 'My template', at: Date.now(), data: old }]; } catch { /* none */ } }
  return Array.isArray(t) ? t : [];
};
const save = (t: Tpl[]) => SS(KEY, t.slice(0, 20));

let query = '';
function search(panel: HTMLElement): void {
  const chips = [...panel.querySelectorAll<HTMLButtonElement>('button[data-g="in"]')];
  if (!chips.length) return;
  let box = panel.querySelector<HTMLInputElement>('.qcs');
  if (!box) {
    panel.insertAdjacentHTML('afterbegin', `<input class="qcs" type="search" placeholder="Search indicators: RSI, bands, volatility, trend…" aria-label="Search indicators"><div class="qcs-none" hidden>No indicator matches. Try “momentum”, “volume” or “trend”.</div>`);
    box = panel.querySelector<HTMLInputElement>('.qcs')!;
    box.value = query;
    // keep these events away from the chart engine, which redraws its panel on input/change and would swallow the next click
    for (const ev of ['input', 'change', 'keydown', 'click']) box.addEventListener(ev, e => e.stopPropagation());
    box.addEventListener('input', () => { query = box!.value; filter(panel); });
  }
  filter(panel);
}
function filter(panel: HTMLElement): void {
  const q = query.trim().toLowerCase();
  let shown = 0;
  panel.querySelectorAll<HTMLButtonElement>('button[data-g="in"]').forEach(b => {
    const hit = !q || `${b.textContent} ${KW[b.dataset.v || ''] || ''}`.toLowerCase().includes(q);
    b.style.display = hit ? '' : 'none';
    if (hit) shown++;
  });
  const none = panel.querySelector<HTMLElement>('.qcs-none');
  if (none) none.hidden = shown > 0;
}

function templates(panel: HTMLElement): void {
  const sv = panel.querySelector<HTMLButtonElement>('[data-g="tsv"]'), ld = panel.querySelector<HTMLButtonElement>('[data-g="tld"]');
  if (!sv || !ld || panel.querySelector('.qtp')) return;
  sv.style.display = 'none'; ld.style.display = 'none';
  const list = all();
  const host = document.createElement('div'); host.className = 'qtp';
  host.innerHTML = `<h4>Chart templates</h4><p>Save this set-up (chart type, indicators and their settings, colours) and switch back to it on any stock.</p>
    <div class="row"><input class="qtpn" maxlength="40" placeholder="Name, e.g. Swing trading" aria-label="Template name"><button class="btn p qtps">Save template</button></div>
    <ul>${list.map((t, i) => `<li><b title="${esc(t.name)}">${esc(t.name)}</b><button class="btn qtpl" data-i="${i}">Apply</button><button class="btn qtpd" data-i="${i}" aria-label="Delete template ${esc(t.name)}">Delete</button></li>`).join('') || '<li class="mu">No templates yet.</li>'}</ul>`;
  panel.appendChild(host);
  const name = host.querySelector<HTMLInputElement>('.qtpn')!;
  for (const ev of ['input', 'change', 'keydown']) name.addEventListener(ev, e => e.stopPropagation());
  host.querySelector<HTMLButtonElement>('.qtps')!.onclick = () => {
    const n = name.value.trim().slice(0, 40) || `Template ${list.length + 1}`;
    const before = all(); // read (and migrate the old single slot) before the engine overwrites it
    sv.click(); // the engine writes the current set-up to q_tpl
    let data: unknown = null; try { data = JSON.parse(localStorage.getItem('q_tpl') || 'null'); } catch { /* none */ }
    if (!data) return qtoast('Could not read the chart set-up.');
    const next = before.filter(t => t.name.toLowerCase() !== n.toLowerCase());
    next.unshift({ name: n, at: Date.now(), data });
    save(next); track('chart_template_save'); qtoast(`Saved “${esc(n)}”.`);
  };
  host.querySelectorAll<HTMLButtonElement>('.qtpl').forEach(b => b.onclick = () => {
    const t = all()[+b.dataset.i!]; if (!t) return;
    try { localStorage.setItem('q_tpl', JSON.stringify(t.data)); } catch { return; }
    ld.click(); track('chart_template_apply'); qtoast(`Applied “${esc(t.name)}”.`);
  });
  host.querySelectorAll<HTMLButtonElement>('.qtpd').forEach(b => b.onclick = () => {
    const l = all(); const t = l[+b.dataset.i!]; if (!t) return;
    l.splice(+b.dataset.i!, 1); save(l); host.remove(); templates(panel); qtoast(`Deleted “${esc(t.name)}”.`);
  });
}

export function initCharts(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qch-css">${CSS}</style>`);
  if (!SYNC.includes(KEY)) SYNC.push(KEY);
  const main = document.getElementById('m');
  if (!main) return;
  let t = 0;
  new MutationObserver(() => {
    clearTimeout(t);
    t = window.setTimeout(() => document.querySelectorAll<HTMLElement>('#m .tvp').forEach(p => { if (p.style.display === 'none') return; search(p); templates(p); }), 30);
  }).observe(main, { childList: true, subtree: true });
}
