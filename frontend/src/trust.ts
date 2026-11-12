// Data trust for private-company valuations: show where each number comes from and how it is worked out.
// The headline "estimated valuation" is the latest secondary-market price (Forge Price) times an implied share
// count (the latest funding round's post-money valuation divided by that round's price per share). This panel
// shows that sum with the real figures, the other evidence (round price, mutual fund marks with SEC filings),
// the range they imply, and how old each piece is. If the provider's figure doesn't match the sum, it says so
// instead of claiming a method.

interface Round { date: string; round?: string; raised?: number | null; issuePrice?: number | null; valuation?: number | null }
interface Mark { date: string; fund?: string; shareClass?: string; price: number; filing?: string }
interface Co { name?: string; price?: number; summary?: { valuation?: number }; rounds?: Round[]; marks?: Mark[]; history?: { t: string; c: number }[]; source?: string }

const CSS = `
.qtr{margin:14px 0}.qtr h3{display:flex;align-items:center;gap:10px;flex-wrap:wrap}
.qtr-f{display:flex;flex-wrap:wrap;align-items:baseline;gap:6px 10px;font-size:15px;line-height:1.6;padding:12px 14px;border-radius:12px;background:rgba(var(--fgc),.03);border:1px solid var(--ln);font-variant-numeric:tabular-nums}
.qtr-f>*{display:inline!important;background:none!important;border:0!important;padding:0!important;margin:0!important;box-shadow:none!important;min-height:0!important;width:auto!important}.qtr-f b{font-weight:600}.qtr-f .op{color:var(--mu)}
.qtr-t{width:100%;border-collapse:collapse;margin-top:12px;font-size:14px}.qtr-t th{font-size:12px;color:var(--mu);font-weight:500;text-align:right;padding:8px 10px;border-bottom:1px solid var(--ln);white-space:nowrap}
.qtr-t td{padding:10px;border-bottom:1px solid var(--ln);text-align:right;font-variant-numeric:tabular-nums;vertical-align:top}.qtr-t th:first-child,.qtr-t td:first-child{text-align:left}
.qtr-t td small{display:block;color:var(--mu);font-size:12px;white-space:normal}
.qtr-age{display:inline-flex;align-items:center;gap:6px;border-radius:99px;padding:3px 10px;font-size:12.5px;font-weight:500;border:1px solid var(--ln)}
.qtr-age.ok{color:var(--up);border-color:rgba(var(--upc),.4)}.qtr-age.old{color:var(--dn);border-color:rgba(var(--dnc),.4)}.qtr-age.mid{color:var(--ac3)}
.qtr ul{margin:10px 0 0;padding-left:18px;line-height:1.6;color:var(--mu);font-size:13.5px}
@media(max-width:640px){.qtr-t .qtr-x{display:none}}`;

const money = (v: number | null | undefined): string => {
  if (v == null || !isFinite(v)) return '—';
  const a = Math.abs(v), s = v < 0 ? '−' : '';
  return s + '$' + (a >= 1e12 ? fmt(a / 1e12, 2) + 'T' : a >= 1e9 ? fmt(a / 1e9, 1) + 'B' : a >= 1e6 ? fmt(a / 1e6, 0) + 'M' : fmt(a, 2));
};
const day = (d?: string) => d ? new Date(d + 'T12:00:00').toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' }) : '—';
const ageDays = (d?: string) => d ? Math.max(0, Math.round((Date.now() - new Date(d + 'T12:00:00').getTime()) / 864e5)) : Infinity;
const ageText = (n: number) => !isFinite(n) ? 'unknown' : n < 2 ? 'today' : n < 60 ? `${n} days ago` : n < 730 ? `${Math.round(n / 30.4)} months ago` : `${fmt(n / 365, 1)} years ago`;
const pct = (a: number, b: number) => { const p = (a / b - 1) * 100; return Math.abs(p) < 0.5 ? 'Same as headline' : `${p >= 0 ? '+' : '−'}${fmt(Math.abs(p), 0)}% vs headline`; };

export function trustPanel(b: Co): string {
  const head = b.summary?.valuation, price = b.price;
  const rounds = (b.rounds || []).filter(r => r.valuation && r.issuePrice).sort((x, y) => y.date.localeCompare(x.date));
  const last = rounds[0];
  const marks = (b.marks || []).slice().sort((x, y) => y.date.localeCompare(x.date));
  const mark = marks[0];
  const priced = b.history?.length ? b.history[b.history.length - 1].t : undefined;
  if (!head || !last) return '';
  const roundShares = last.valuation! / last.issuePrice!;
  const implied = price != null ? price * roundShares : null;
  const matches = implied != null && Math.abs(implied / head - 1) < 0.02;
  // when the provider's figure doesn't fit the last priced round (often an old one), use the share count its own numbers imply
  const shares = matches || price == null ? roundShares : head / price;

  const dealAge = ageDays(last.date);
  const cls = dealAge <= 180 ? 'ok' : dealAge <= 365 ? 'mid' : 'old';
  const ageLabel = `Last priced funding round: ${day(last.date)} · ${ageText(dealAge)}`;

  const rows: string[] = [];
  if (price != null) rows.push(`<tr><td>Secondary-market price<small>Forge Price: built from trades between existing shareholders and recent rounds${priced ? ` · latest ${day(priced)}` : ''}</small></td><td>$${fmt(price, 2)}</td><td>${money(price * shares)}</td><td class="qtr-x">Used for the headline</td></tr>`);
  rows.push(`<tr><td>Latest funding round<small>${esc(last.round || 'Round')} · ${day(last.date)} · price new investors paid</small></td><td>$${fmt(last.issuePrice!, 2)}</td><td>${money(last.valuation!)}</td><td class="qtr-x">${pct(last.valuation!, head)}</td></tr>`);
  if (mark) {
    const before = mark.date < last.date;
    rows.push(`<tr><td>Mutual fund mark<small>${esc(mark.fund || 'Fund')} · ${esc(mark.shareClass || 'shares')} · ${day(mark.date)}${mark.filing ? ` · <a href="${esc(mark.filing)}" target="_blank" rel="noopener noreferrer">SEC filing ↗</a>` : ''}${before ? ' · <b>reported before the latest round</b>' : ''}</small></td><td>$${fmt(mark.price, 2)}</td><td>${money(mark.price * shares)}</td><td class="qtr-x">${pct(mark.price * shares, head)}</td></tr>`);
  }
  // a fund mark reported before the latest round is out of date, so it is shown but left out of the range
  const markCurrent = !!mark && mark.date >= last.date;
  const vals = [price != null ? price * shares : null, last.valuation!, markCurrent ? mark!.price * shares : null].filter((v): v is number => v != null);
  const lo = Math.min(...vals), hi = Math.max(...vals);

  return `<div class="pan qtr" id="qtrust"><h3>How we got these numbers <span class="qtr-age ${cls}">${ageLabel}</span></h3>
  ${matches ? `<div class="qtr-f"><span>Estimated valuation</span><span class="op">=</span><span>price per share <b>$${fmt(price!, 2)}</b></span><span class="op">×</span><span>about <b>${fmt(shares / 1e6, 0)} million</b> shares</span><span class="op">=</span><b>${money(head)}</b></div>
  <div class="mu" style="font-size:13px;margin-top:8px">The share count is implied by the latest round: ${money(last.valuation!)} post-money ÷ $${fmt(last.issuePrice!, 2)} per share. Companies don’t publish their exact share count, so this is an approximation.</div>`
  : `<div class="qtr-f">The headline valuation is the data provider’s own estimate (${esc(b.source || 'Yahoo Finance')}). It doesn’t match the latest priced round from ${day(last.date)} (price × that round’s share count would give ${money(implied)}), probably because the share count has changed since. Below, the other evidence uses the share count implied by the provider’s own figures (${money(head)} ÷ $${fmt(price ?? 0, 2)} ≈ ${fmt(shares / 1e6, 0)} million shares). Treat these with extra caution.</div>`}
  <div class="ov"><table class="qtr-t"><tr><th>Evidence</th><th>Per share</th><th>Implied valuation</th><th class="qtr-x">Compared</th></tr>${rows.join('')}</table></div>
  <div style="margin-top:10px;font-size:14px">${hi / lo < 1.03 ? `The current evidence agrees closely: about <b>${money(head)}</b>` : `Taken together, the evidence points to roughly <b>${money(lo)} – ${money(hi)}</b> (a ${fmt((hi / lo - 1) * 100, 0)}% spread)`}${mark && !markCurrent ? ', leaving out the fund mark because it predates the latest round' : ''}.</div>
  <ul><li>Round prices are usually for <b>preferred shares</b>, which carry protections that common shares (what employees hold) don’t, so they tend to overstate what common shares are worth.</li>
  <li>Secondary prices come from a small number of private trades and can move a lot on one deal.</li>
  <li>Fund marks are each fund’s own fair-value judgement, reported with a delay of one to two months.</li>
  <li>Source: ${esc(b.source || 'Yahoo Finance (Forge Global)')}; fund marks from SEC filings (Form N-PORT). Not investment advice.</li></ul></div>`;
}

export function initTrust(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qtrust-css">${CSS}</style>`);
  const orig = PAGES.pvt;
  if (!orig) return;
  const add = (sym: string) => {
    jf('/api/private/company?symbol=' + encodeURIComponent(sym)).then(({ ok, b }) => {
      if (!ok) return;
      const tryInsert = (n = 0) => {
        const host = document.getElementById('pvc'), st = host?.querySelector('.pvst');
        if (!host || document.getElementById('qtrust')) return;
        if (!st) { if (n < 40) setTimeout(() => tryInsert(n + 1), 150); return; }
        const html = trustPanel(b as Co);
        if (html) st.insertAdjacentHTML('afterend', html);
      };
      tryInsert();
    }).catch(() => {});
  };
  PAGES.pvt = (s?: string) => { orig(s); add(String(s || '').toUpperCase()); };
  const m = /^#\/pvt\/([^/]+)/.exec(location.hash);
  if (m) add(decodeURIComponent(m[1]).toUpperCase());
}
