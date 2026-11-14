// One design system for every page. The site grew in layers (see the vNN blocks in app.js), and each layer styled
// buttons, headings and tables a little differently: an audit of 29 pages found 59 button sizes, 7 sub-heading sizes
// and 9 table styles. This sheet loads last and sets one scale for each, scoped to the page content (#m) so the
// chart toolbar, menus and dialogs keep their own compact styles. Tokens live in :root so modules can reuse them.
const CSS = `
:root{--q-ctl:36px;--q-ctl-sm:32px;--q-ctl-lg:46px;--q-r-pill:999px;--q-r-card:16px;--q-fs-ctl:13.5px;--q-fs-h3:16.5px;--q-fs-th:12px;--q-fs-td:14px}
/* page titles: the stock and lesson pages used their own sizes */
#m>h1:first-child,#m h2.qpt{font-family:Fraunces,Georgia,serif;font-weight:300;font-size:clamp(26px,2.8vw,36px);line-height:1.12;letter-spacing:-.01em}
#m h2.qpt{font-size:clamp(26px,2.8vw,36px)!important}
/* section headings inside pages and cards */
#m h3:not(.hm-mv):not([class*="tv"]){font-size:var(--q-fs-h3);font-weight:600;line-height:1.3;letter-spacing:0}
#m .body h3,#m .lesson h3{font-size:19px}
#m .hm-mv h3{font-size:12.5px}
/* buttons: one height per size, never wrapping onto two lines */
#m .btn{min-height:var(--q-ctl);padding:0 15px;font-size:var(--q-fs-ctl);line-height:1.2;display:inline-flex;align-items:center;justify-content:center;gap:6px;white-space:nowrap;vertical-align:middle;box-sizing:border-box}
#m .sc.hx .cta .btn,#m .hm .cta .btn,#m .btn.lg{min-height:var(--q-ctl-lg);padding:0 22px;font-size:15px}
#m .chip{min-height:var(--q-ctl-sm);padding:0 13px;font-size:13px;line-height:1.2;display:inline-flex;align-items:center;gap:6px;white-space:nowrap;vertical-align:middle;box-sizing:border-box;align-self:center}
#m .btn:focus-visible,#m .chip:focus-visible{outline:2px solid var(--ac,#c8a96a);outline-offset:2px}
@media (max-width:640px){#m .btn{min-height:40px}#m .chip{min-height:36px}}
/* tables: one header and cell style; numbers line up */
#m table:not(.tvt) th{font-size:var(--q-fs-th);font-weight:600;letter-spacing:.03em;color:var(--mu);padding:8px 10px;text-align:left;white-space:nowrap}
#m table:not(.tvt) td{font-size:var(--q-fs-td);padding:9px 10px;font-variant-numeric:tabular-nums}
#m table:not(.tvt) th.r,#m table:not(.tvt) td.r,#m table:not(.tvt) td.num{text-align:right}
@media (max-width:640px){#m table:not(.tvt) th{padding:7px 8px}#m table:not(.tvt) td{font-size:13.5px;padding:8px}}
`;

export function initUi(): void {
  const put = () => { document.getElementById('qui-css')?.remove(); document.head.insertAdjacentHTML('beforeend', `<style id="qui-css">${CSS}</style>`); };
  put();
  // pages without an h1 (stock, lesson) use their first h2 as the title: give it the shared title style
  const main = document.getElementById('m');
  const title = () => { if (!main || main.querySelector('h1')) return; const h = main.querySelector('h2'); if (h && !h.classList.contains('qpt') && h.getBoundingClientRect().top - main.getBoundingClientRect().top < 320) h.classList.add('qpt'); };
  if (main) { let u = 0; new MutationObserver(() => { clearTimeout(u); u = window.setTimeout(title, 40); }).observe(main, { childList: true, subtree: true }); }
  // legacy code adds style blocks later as features load; keep this sheet last so it stays the final word
  let t = 0;
  new MutationObserver(() => { if (document.head.lastElementChild?.id !== 'qui-css') { clearTimeout(t); t = window.setTimeout(put, 50); } }).observe(document.head, { childList: true });
}
