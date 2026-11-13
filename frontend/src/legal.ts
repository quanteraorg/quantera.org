// Privacy choices and accessibility basics:
// - a one-time, non-blocking privacy notice (no cookies, no ads; anonymous statistics and error reports can be turned off)
// - the same choice in Settings, with links to every policy
// - a "Skip to content" link, keyboard access for click-only links, and text labels for charts drawn on <canvas>
// The choice itself is read in index.html (window.QPRIV / QSTATS) before analytics or error reporting load.

declare global { interface Window { QPRIV?: () => 'all' | 'essential'; QSTATS?: () => boolean; Sentry?: any; va?: any } }

const CSS = `
.qskip{position:fixed;left:-9999px;top:12px;z-index:200;padding:10px 14px;border-radius:10px;background:var(--ac);color:#111;font-weight:600;text-decoration:none}
.qskip:focus{left:12px}
.qlink{background:none;border:0;padding:0;font:inherit;cursor:pointer;text-decoration:underline;text-underline-offset:2px}
.qagree{display:flex;gap:8px;align-items:flex-start;font-size:13px;line-height:1.45;margin:12px 0 0;color:var(--tx)}
.qagree input{margin:2px 0 0;width:16px;height:16px;flex:none;accent-color:var(--ac)}
.qpn{position:fixed;right:16px;bottom:16px;z-index:60;max-width:380px;padding:14px 16px;border-radius:14px;border:1px solid var(--ln);background:var(--s1);box-shadow:0 12px 40px rgba(0,0,0,.35);font-size:13.5px;line-height:1.5;color:var(--tx)}
.qpn p{margin:0 0 10px}.qpn .row{display:flex;gap:8px;flex-wrap:wrap;align-items:center}.qpn .btn{margin:0}
@media(max-width:800px),(pointer:coarse) and (max-height:500px){.qpn{left:12px;right:12px;bottom:calc(84px + env(safe-area-inset-bottom,0px));max-width:none}}
.qpv .opt{display:flex;gap:10px;align-items:flex-start;margin:10px 0;line-height:1.5}.qpv .opt input{margin-top:4px;accent-color:var(--ac)}
.qpv .links{display:flex;gap:14px;flex-wrap:wrap;margin-top:10px;font-size:13.5px}`;

const get = (): 'all' | 'essential' => (window.QPRIV ? window.QPRIV() : 'all');
const gpc = () => (navigator as any).globalPrivacyControl === true || navigator.doNotTrack === '1';

function choose(v: 'all' | 'essential'): void {
  SS('q_priv', v);
  if (v === 'essential') {
    try { window.Sentry?.close?.(); } catch { /* not loaded */ }
    window.va = () => {}; // Vercel Web Analytics: drop anything queued from now on
  }
}

function notice(): void {
  let saved: unknown = null;
  try { saved = JSON.parse(localStorage.getItem('q_priv') || 'null'); } catch { /* storage blocked */ }
  if (saved || gpc() || document.getElementById('qpn')) return;
  const n = document.createElement('div');
  n.id = 'qpn'; n.className = 'qpn'; n.setAttribute('role', 'region'); n.setAttribute('aria-label', 'Privacy notice');
  n.innerHTML = `<p><b>No cookies, no ads.</b> Quantera counts anonymous page views and receives error reports so we can improve the site. You can turn these off at any time in Settings.</p>
    <div class="row"><button class="btn p" data-v="all">OK</button><button class="btn" data-v="essential">Turn off</button><a href="/privacy" style="margin-left:auto">Privacy policy</a></div>`;
  n.querySelectorAll<HTMLButtonElement>('[data-v]').forEach(b => b.onclick = () => { choose(b.dataset.v as 'all' | 'essential'); n.remove(); });
  document.body.appendChild(n);
}

function settingsPanel(): void {
  if (!/^#\/settings/.test(location.hash) || document.getElementById('qpv')) return;
  const m = document.getElementById('m'); if (!m || !m.firstElementChild) return;
  const p = document.createElement('div'); p.id = 'qpv'; p.className = 'pan qpv'; p.style.marginTop = '14px';
  const v = get();
  p.innerHTML = `<h3 style="margin:0 0 4px">Privacy</h3>
    <p class="mu" style="margin:0 0 6px;font-size:13.5px">Quantera sets no cookies and shows no ads. Your watchlist, paper trades and settings stay in this browser unless you create an account.</p>
    <label class="opt"><input type="checkbox" id="qpvs" ${v === 'all' ? 'checked' : ''}><span><b>Anonymous statistics and error reports</b><br><span class="mu" style="font-size:13px">Page-view counts (Vercel Web Analytics, no cookies), which features are used (no name or email attached), and reports when a page breaks (Sentry). ${gpc() ? 'Your browser asks sites not to track you, so these start off.' : ''}</span></span></label>
    <div class="links"><a href="/privacy">Privacy policy</a><a href="/cookies">Cookies and storage</a><a href="/terms">Terms of use</a><a href="/accessibility">Accessibility</a></div>`;
  m.appendChild(p);
  (p.querySelector('#qpvs') as HTMLInputElement).onchange = e => { choose((e.target as HTMLInputElement).checked ? 'all' : 'essential'); qtoast((e.target as HTMLInputElement).checked ? 'Anonymous statistics and error reports are on.' : 'Anonymous statistics and error reports are off.'); };
}

function a11y(root: ParentNode): void {
  // Links that only have a click handler can't be reached with the keyboard; make them behave like buttons.
  root.querySelectorAll<HTMLAnchorElement>('a[onclick]:not([href]):not([tabindex]), a[id]:not([href]):not([tabindex])').forEach(a => {
    a.tabIndex = 0; if (!a.getAttribute('role')) a.setAttribute('role', 'button');
    a.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); a.click(); } });
  });
  // Charts are drawn on <canvas>; give each a text label naming what it shows.
  root.querySelectorAll<HTMLCanvasElement>('#m canvas:not([aria-label]):not([aria-hidden])').forEach(c => {
    const r = c.getBoundingClientRect();
    if (r.width < 80 || r.height < 40) { c.setAttribute('aria-hidden', 'true'); return; }
    const sym = /^#\/(?:asset|trade|report|valuation)\/([^/]+)/.exec(location.hash);
    c.setAttribute('role', 'img');
    c.setAttribute('aria-label', sym ? `Price chart for ${decodeURIComponent(sym[1]).toUpperCase()}. The figures shown beside the chart give the same information as text.` : 'Chart. The figures shown beside it give the same information as text.');
  });
}

export function initLegal(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qlegal-css">${CSS}</style>`);
  const m = document.getElementById('m');
  if (m) { if (!m.hasAttribute('tabindex')) m.tabIndex = -1; }
  const skip = document.createElement('a'); skip.className = 'qskip'; skip.href = '#m'; skip.textContent = 'Skip to content';
  skip.onclick = e => { e.preventDefault(); const t = document.getElementById('m'); if (t) { t.focus(); t.scrollIntoView(); } };
  document.body.insertBefore(skip, document.body.firstChild);
  let t = 0;
  const run = () => { clearTimeout(t); t = window.setTimeout(() => { settingsPanel(); a11y(document); }, 120); };
  new MutationObserver(run).observe(document.body, { childList: true, subtree: true });
  run();
  setTimeout(notice, 1500);
}
