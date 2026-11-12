// Browser smoke test for the static frontend.
// Serves nothing itself: point BASE at a running static server for frontend/public (default http://localhost:8124).
// For every route, at desktop and phone size, in dark and light theme, it fails on:
//   - uncaught page errors
//   - horizontal overflow (page wider than the screen)
//   - WCAG 2 A/AA violations reported by axe-core (desktop, dark theme)
//   - the navigation moving when hovered, or a click on Markets not landing (desktop)
// /api calls are not served here, so pages run on their built-in fallbacks; live data is checked separately.
import { chromium } from 'playwright';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const AXE = readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
const BASE = process.env.BASE || 'http://localhost:8124';
const ROUTES = ['', 'markets', 'world', 'news', 'stocks', 'screener', 'compare', 'ipos', 'asset/AAPL', 'trade', 'plan',
  'portfolio', 'planner', 'learn', 'glossary', 'ai', 'macro', 'calendar', 'about', 'pricing', 'settings', 'options'];
const SIZES = [[1440, 900, 'desktop'], [390, 844, 'phone']];
const failures = [];

// Hover effects must never move the navigation (a hover light once knocked the fixed side menu into the page).
const navBox = page => page.evaluate(() => {
  const n = document.getElementById('nav'); if (!n) return null;
  const r = n.getBoundingClientRect();
  return `${getComputedStyle(n).position} ${Math.round(r.left)},${Math.round(r.top)} ${Math.round(r.width)}x${Math.round(r.height)}`;
});
async function pointerCheck(page, where) {
  const before = await navBox(page);
  const links = await page.$$('#nav > a');
  for (const a of links.slice(0, 3)) { const b = await a.boundingBox(); if (b) await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 4 }); }
  await page.mouse.move(720, 450, { steps: 4 });
  await page.waitForTimeout(150);
  const after = await navBox(page);
  if (before !== after) failures.push(`${where}: navigation moved on hover (${before} → ${after})`);
}
async function navClickCheck(page, where) {
  await page.evaluate(() => { location.hash = '#/news'; });
  await page.waitForTimeout(1200);
  const before = await navBox(page);
  const link = await page.$('#nav > a[data-r="markets"]');
  if (!link) { failures.push(`${where}: no Markets link in the navigation`); return; }
  await link.hover();
  await page.waitForTimeout(150);
  await link.click();
  await page.waitForTimeout(1200);
  const hash = await page.evaluate(() => location.hash), after = await navBox(page);
  if (!/^#\/markets/.test(hash)) failures.push(`${where}: clicking Markets in the navigation went to ${hash}`);
  if (before !== after) failures.push(`${where}: navigation moved when clicking Markets (${before} → ${after})`);
}

const browser = await chromium.launch();
try {
  for (const theme of ['dark', 'light']) {
    for (const [w, h, size] of SIZES) {
      const page = await browser.newPage({ viewport: { width: w, height: h }, isMobile: size === 'phone', hasTouch: size === 'phone' });
      await page.addInitScript(t => { try { localStorage.setItem('q_theme', JSON.stringify(t)); localStorage.setItem('q_mnd', '1'); } catch (e) {} }, theme);
      const errors = [];
      page.on('pageerror', e => errors.push(e.message));
      await page.goto(BASE + '/');
      await page.waitForTimeout(2500);
      for (const r of ROUTES) {
        errors.length = 0;
        await page.evaluate(r => { location.hash = '#/' + r; }, r);
        await page.waitForTimeout(1800);
        const where = `${theme}/${size} #/${r}`;
        const sw = await page.evaluate(() => document.documentElement.scrollWidth);
        if (sw > w + 1) failures.push(`${where}: page is ${sw}px wide on a ${w}px screen`);
        for (const e of errors) failures.push(`${where}: page error: ${e}`);
        if (theme === 'dark' && size === 'desktop') {
          await page.addScriptTag({ content: AXE });
          const v = await page.evaluate(async () => (await axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa'] }, resultTypes: ['violations'] }))
            .violations.map(x => `${x.id} (${x.impact}, ${x.nodes.length}): ${x.nodes[0].html.slice(0, 120)}`));
          for (const x of v) failures.push(`${where}: accessibility: ${x}`);
        }
        if (size === 'desktop') await pointerCheck(page, where);
      }
      if (size === 'desktop') await navClickCheck(page, `${theme}/${size}`);
      await page.close();
    }
  }
} finally {
  await browser.close();
}

if (failures.length) {
  console.error(`${failures.length} problem(s):\n` + failures.map(f => '  - ' + f).join('\n'));
  process.exit(1);
}
console.log(`OK: ${ROUTES.length} routes x 2 themes x 2 sizes, no page errors, no overflow, no accessibility violations, navigation steady under the pointer.`);
