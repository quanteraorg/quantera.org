// Small app-wide behaviour fixes found by the interaction tests (tests/interact.mjs).

/** Pop-ups (.qm: order receipts, sign-in, shortcuts…) belong to the page they were opened on. Leaving the page,
 *  e.g. with the bottom navigation or the back button, closes them instead of leaving them covering the next page. */
function closePopupsOnNavigate(): void {
  addEventListener('hashchange', () => {
    const open = document.querySelectorAll('body > .qm');
    if (!open.length) return;
    open.forEach(m => m.remove());
    if (document.body.style.overflow === 'hidden') document.body.style.overflow = '';
  });
}

export function initUx(): void {
  closePopupsOnNavigate();
}
