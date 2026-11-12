// Economic release calendar on the Calendar page: the next 60 days of major US releases (inflation, jobs, GDP,
// spending, producer prices, retail sales, industrial production) from FRED's official release calendar, each with
// a plain-English line on why markets care. Release names are FRED's own.

const CSS = `
#qecon{margin-bottom:16px}#qecon h3{margin:0 0 4px}#qecon .sub{color:var(--mu);font-size:13px;margin:0 0 12px;line-height:1.5}
#qecon ol{list-style:none;margin:0;padding:0}#qecon li{display:grid;grid-template-columns:110px minmax(0,1fr);gap:4px 14px;padding:10px 0;border-bottom:1px solid var(--ln)}
#qecon li:last-child{border-bottom:0}#qecon time{font-variant-numeric:tabular-nums;color:var(--mu);font-size:13.5px}#qecon time b{display:block;color:var(--tx);font-size:14.5px}
#qecon .n{font-weight:600}#qecon .w{display:block;color:var(--mu);font-size:13px;line-height:1.5;margin-top:2px}
#qecon .soon{display:inline-block;margin-left:6px;font-size:11px;font-weight:600;padding:1px 7px;border-radius:99px;border:1px solid rgba(var(--acc),.45);color:var(--ac)}
#qecon .note{font-size:12.5px;color:var(--mu);margin-top:10px}
@media(max-width:560px){#qecon li{grid-template-columns:minmax(0,1fr)}}`;

function render(host: HTMLElement): void {
  host.innerHTML = '<div class="pan"><h3>Economic releases</h3><p class="sub">Loading the official calendar…</p></div>';
  jf('/api/calendar?type=economic').catch(() => ({ ok: false, b: {} } as any)).then(({ ok, b }) => {
    if (!host.isConnected) return;
    const items = ok ? (b.items || []) as { date: string; name: string; what?: string | null; link: string; source: string }[] : [];
    if (!items.length) { host.innerHTML = `<div class="pan"><h3>Economic releases</h3><p class="sub">${esc(b?.error || 'The release calendar is unavailable right now.')}</p></div>`; return; }
    const today = new Date().toISOString().slice(0, 10);
    const d = (s: string) => new Date(s + 'T12:00:00Z');
    host.innerHTML = `<div class="pan"><h3>Economic releases</h3><p class="sub">The next 60 days of major US economic reports. Markets often move on these, especially inflation and jobs.</p>
      <ol>${items.slice(0, 20).map(it => { const dd = d(it.date), days = Math.round((+dd - +d(today)) / 864e5); return `<li><time datetime="${esc(it.date)}"><b>${dd.toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'UTC' })}</b>${dd.toLocaleDateString('en-US', { weekday: 'short', timeZone: 'UTC' })}</time>
        <div><span class="n">${esc(it.name)}</span>${days <= 7 ? `<span class="soon">${days === 0 ? 'Today' : days === 1 ? 'Tomorrow' : 'In ' + days + ' days'}</span>` : ''}${it.what ? `<span class="w">${esc(it.what)}</span>` : ''}</div></li>`; }).join('')}</ol>
      <p class="note">Source: ${esc(items[0].source)} · <a href="${esc(items[0].link)}" target="_blank" rel="noopener noreferrer">Full calendar ↗</a>. Dates are official schedules and can change. <a href="#/macro">See the latest figures →</a></p></div>`;
  });
}

export function initEcon(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qecon-css">${CSS}</style>`);
  const main = document.getElementById('m'); if (!main) return;
  let t: number | undefined;
  const run = () => {
    if (!/^#\/calendar\/?$/.test(location.hash)) return;
    const body = main.querySelector<HTMLElement>('.body') || main;
    if (!body.textContent?.trim() || document.getElementById('qecon')) return;
    const host = document.createElement('div'); host.id = 'qecon';
    const ctx = document.getElementById('qctx');
    if (ctx && ctx.parentElement === body) ctx.insertAdjacentElement('afterend', host); else body.prepend(host);
    render(host);
  };
  new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(run, 120); }).observe(main, { childList: true, subtree: true });
  run();
}
