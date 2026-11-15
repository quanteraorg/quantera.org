// Academy: "Read the statements" exercise. A real company's last five years from its SEC filings (/api/statements);
// the learner answers four questions a research analyst would ask, then sees the figures and why each answer is right.
// Questions and answers are computed from the filed numbers after they load; nothing is invented.

interface Line { label: string; key: string; values: (number | null)[] }
interface Part { periods: { label: string }[]; income: Line[]; cashflow: Line[]; ratios: Line[]; balance?: Line[] }
interface St { symbol: string; name: string; financial?: boolean; annual: Part; source?: string }

const POOL = ['AAPL', 'MSFT', 'NVDA', 'KO', 'COST', 'NKE', 'INTC', 'DIS', 'PEP', 'MCD', 'HD', 'TSLA'];
const KEY = 'q_stx';
const CSS = `.qsx .tbl{overflow-x:auto;margin:10px 0}.qsx table{width:100%;border-collapse:collapse}.qsx td,.qsx th{text-align:right}.qsx td:first-child,.qsx th:first-child{text-align:left}`;

const big = (v: number | null | undefined) => v == null ? '—' : (v < 0 ? '−' : '') + '$' + (Math.abs(v) >= 1e9 ? fmt(Math.abs(v) / 1e9, 1) + 'B' : fmt(Math.abs(v) / 1e6, 0) + 'M');
const pct = (v: number | null | undefined) => v == null ? '—' : fmt(v * 100, 1) + '%';

function questions(st: St) {
  const a = st.annual, n = a.periods.length, from = Math.max(0, n - 5), yrs = a.periods.slice(from).map(p => p.label);
  const get = (part: Line[], k: string) => (part.find(l => l.key === k)?.values || []).slice(from);
  const rev = get(a.income, 'revenue'), ni = get(a.income, 'netIncome'), nm = get(a.ratios, 'netMargin'), fcf = get(a.cashflow, 'freeCashFlow');
  const capex = get(a.cashflow, 'capex'), div = get(a.cashflow, 'dividends'), bb = get(a.cashflow, 'buybacks'), ocf = get(a.cashflow, 'operatingCashFlow');
  const L = yrs.length - 1;
  if (L < 3 || rev.some(v => v == null) || ni[L] == null || fcf[L] == null || nm.some(v => v == null)) return null;
  const Q: { h: string; opts: string[]; right: string; why: string }[] = [];
  // 1. revenue path
  const ups = rev.slice(1).map((v, i) => (v as number) > (rev[i] as number));
  const r1 = ups.every(Boolean) ? 'Grew every year' : (rev[L] as number) > (rev[0] as number) ? 'Grew overall, but fell in at least one year' : 'Fell overall';
  Q.push({ h: `1. How did ${st.name}’s revenue (total sales) change from ${yrs[0]} to ${yrs[L]}?`, opts: ['Grew every year', 'Grew overall, but fell in at least one year', 'Fell overall'], right: r1,
    why: `Revenue went ${yrs.map((y, i) => `${esc(y)} ${big(rev[i])}`).join(' → ')}. ${r1 === 'Grew every year' ? 'Steady growth every year is rarer than it looks.' : 'A dip in one year is common: check whether it was a one-off or a trend.'}` });
  // 2. best margin year
  const best = nm.reduce<number>((b, v, i) => (v as number) > (nm[b] as number) ? i : b, 0);
  Q.push({ h: '2. In which year did it keep the largest share of its sales as profit (the highest net margin)?', opts: yrs, right: yrs[best],
    why: `Net margin = net income ÷ revenue. It was ${yrs.map((y, i) => `${esc(y)} ${pct(nm[i])}`).join(', ')}. The highest margin isn’t always the year with the most revenue: costs, taxes and one-off items matter too. <a href="#/lesson/ratios">Lesson: margins and ratios</a>` });
  // 3. cash vs profit
  const ratio = (fcf[L] as number) / (ni[L] as number), r3 = Math.abs(ratio - 1) <= 0.05 ? 'About the same' : ratio > 1 ? 'Higher' : 'Lower';
  Q.push({ h: `3. In ${yrs[L]}, was its free cash flow higher or lower than its net income (profit)?`, opts: ['Higher', 'About the same', 'Lower'], right: r3,
    why: `Free cash flow was ${big(fcf[L])} and net income ${big(ni[L])}. Profit includes non-cash items (like depreciation and stock-based pay) and ignores money spent on equipment; free cash flow is the cash actually left after that spending. ${r3 === 'Lower' ? 'Cash well below profit for several years can be a warning sign worth investigating.' : 'Cash at or above profit suggests the reported profit is backed by real cash.'} <a href="#/lesson/cashflow">Lesson: cash flow</a>` });
  // 4. where the cash went
  const uses: [string, number][] = [['Investment in equipment and facilities', Math.abs(capex[L] || 0)], ['Dividends to shareholders', Math.abs(div[L] || 0)], ['Buying back its own shares', Math.abs(bb[L] || 0)]];
  if (uses.some(u => u[1] > 0)) {
    const top = [...uses].sort((x, y) => y[1] - x[1])[0][0];
    Q.push({ h: `4. In ${yrs[L]}, which of these used the most cash?`, opts: uses.map(u => u[0]), right: top,
      why: `Cash from operations was ${big(ocf[L])}. It spent ${big(-Math.abs(capex[L] || 0))} on equipment and facilities, paid ${big(-Math.abs(div[L] || 0))} in dividends and ${big(-Math.abs(bb[L] || 0))} on buybacks. Where cash goes shows management’s priorities: reinvesting for growth, or returning money to shareholders.` });
  }
  const table = `<div class="tbl"><table><tr><th>${esc(st.symbol)}</th>${yrs.map(y => `<th>${esc(y)}</th>`).join('')}</tr>
    ${([['Revenue', rev, big], ['Net income', ni, big], ['Net margin', nm, pct], ['Cash from operations', ocf, big], ['Free cash flow', fcf, big], ['Capital spending', capex, big], ['Dividends', div, big], ['Buybacks', bb, big]] as [string, (number | null)[], (v: number | null) => string][])
      .map(([l, v, f]) => `<tr><td>${l}</td>${v.map(x => `<td>${f(x)}</td>`).join('')}</tr>`).join('')}</table></div>`;
  return { Q, table, yrs };
}

async function page(sym?: string): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  document.title = 'Read the statements | Quantera Academy';
  const s = (sym && /^[A-Z]{1,6}$/.test(sym) ? sym : POOL[Math.floor(Math.random() * POOL.length)]);
  m.innerHTML = `<div class="qch qsx"><p class="crumb" style="font-size:13px"><a href="#/learn">Academy</a> › Read the statements</p><h1 class="qpt">Read the statements</h1>
    <p style="font-size:16px;line-height:1.6;max-width:720px">Four questions an analyst asks about any company, answered from its real filings with the SEC. Pick your answers, then see the numbers.</p>
    <div class="qch-pick" style="display:flex;gap:8px;flex-wrap:wrap;margin:6px 0 4px">${POOL.map(p => `<a class="chip${p === s ? ' on' : ''}" href="#/statex/${p}">${p}</a>`).join('')}</div><div id="qsxq"><div class="qrx-sk" style="height:140px;margin-top:12px"></div></div></div>`;
  const box = m.querySelector('#qsxq') as HTMLElement;
  let st: St;
  try { const { ok, b } = await jf(`/api/statements?symbol=${s}`); if (!ok || !b?.annual || b.financial) throw new Error(b?.error || 'no statements'); st = b; }
  catch (e) { box.innerHTML = `<p class="mu">${esc(s)}’s statements can’t be loaded right now (${esc((e as Error).message)}). Pick another company above.</p>`; return; }
  // SEC lists names in capitals ("MICROSOFT CORP"); show them the way people write them
  if (st.name && st.name === st.name.toUpperCase()) st.name = st.name.toLowerCase().replace(/\b([a-z])/g, c => c.toUpperCase()).replace(/\b(Inc|Corp|Co|Ltd)\b\.?/g, '$1.').replace(/\.\./g, '.');
  const q = questions(st);
  if (!q) { box.innerHTML = `<p class="mu">${esc(st.name)}’s filings don’t have five complete years of these figures. Pick another company above.</p>`; return; }
  box.innerHTML = `<div class="ev"><small>The company</small><b>${esc(st.name)} (${esc(st.symbol)})</b><p>Annual figures, ${esc(q.yrs[0])} to ${esc(q.yrs[q.yrs.length - 1])}, from its 10-K filings with the SEC.</p></div>`
    + q.Q.map((x, i) => `<div class="q" data-q="${i}"><h4>${esc(x.h)}</h4><div class="opts">${x.opts.map(o => `<button class="chip" data-a="${esc(o)}">${esc(o)}</button>`).join('')}</div><p class="fb" hidden></p></div>`).join('')
    + `<button class="btn p" id="qsxgo">Check my answers</button><div id="qsxout"></div>`;
  const picks: (string | null)[] = q.Q.map(() => null);
  box.querySelectorAll<HTMLElement>('[data-q]').forEach(qe => qe.querySelectorAll<HTMLButtonElement>('[data-a]').forEach(b => b.onclick = () => {
    if (qe.dataset.done) return; picks[+qe.dataset.q!] = b.dataset.a!; qe.querySelectorAll('.chip').forEach(c => c.classList.toggle('on', c === b));
  }));
  (box.querySelector('#qsxgo') as HTMLButtonElement).onclick = () => {
    if (picks.some(p => p == null)) { qtoast('Answer every question first.'); return; }
    let score = 0;
    box.querySelectorAll<HTMLElement>('[data-q]').forEach(qe => {
      const i = +qe.dataset.q!, x = q.Q[i]; qe.dataset.done = '1'; if (picks[i] === x.right) score++;
      qe.querySelectorAll<HTMLButtonElement>('[data-a]').forEach(c => { c.classList.toggle('ok', c.dataset.a === x.right); c.classList.toggle('no', c.dataset.a === picks[i] && picks[i] !== x.right); c.disabled = true; });
      const fb = qe.querySelector('.fb') as HTMLElement; fb.hidden = false; fb.innerHTML = `${picks[i] === x.right ? '✓ Right.' : `✗ The answer is <b>${esc(x.right)}</b>.`} ${x.why}`;
    });
    (box.querySelector('#qsxgo') as HTMLElement).remove();
    const d = LS<Record<string, number>>(KEY, {}); d[st.symbol] = Math.max(d[st.symbol] || 0, score); SS(KEY, d);
    track('statements_exercise'); try { ACT_('statex'); } catch { /* streak helper may not exist */ }
    (box.querySelector('#qsxout') as HTMLElement).innerHTML = `<p class="score"><b>${score} of ${q.Q.length}.</b> The figures behind every answer:</p>${q.table}
      <p class="mu" style="font-size:13px">Source: ${esc(st.name)}’s annual reports (10-K) filed with the SEC. <a href="#/asset/${st.symbol}">See the full statements and every filing →</a></p>
      <p><a class="btn" href="#/statex">Try another company</a> <a class="btn" href="#/challenge">Market reaction challenge</a></p>`;
  };
}

export function initStatex(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qsx-css">${CSS}</style>`);
  PAGES.statex = () => { track('view_statex'); page(/^#\/statex\/([A-Z]{1,6})$/.exec(location.hash)?.[1]); };
  (HUB as Record<string, string>).statex = 'learn';
  // a second card next to the reaction challenge at the top of the Academy
  const main = document.getElementById('m');
  const card = () => {
    const c = main?.querySelector('.qch-card'); if (!c || main!.querySelector('.qsx-card')) return;
    c.insertAdjacentHTML('afterend', `<div class="qch-card qsx-card"><span style="font-size:26px" aria-hidden="true">📑</span><div><b>Read the statements</b><p>Four analyst questions about a real company, answered from its SEC filings.</p></div><a class="btn p" href="#/statex">Start</a></div>`);
  };
  if (main) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(card, 60); }).observe(main, { childList: true }); }
}
