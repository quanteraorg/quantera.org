// Browser smoke test for the static frontend.
// Serves nothing itself: point BASE at a running static server for frontend/public (default http://localhost:8124).
// For every route, at desktop and phone size, in dark and light theme, it fails on:
//   - uncaught page errors
//   - horizontal overflow (page wider than the screen)
//   - WCAG 2 A/AA violations reported by axe-core (desktop, dark theme)
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
      }
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
console.log(`OK: ${ROUTES.length} routes x 2 themes x 2 sizes, no page errors, no overflow, no accessibility violations.`);
