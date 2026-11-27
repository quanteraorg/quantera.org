// Offline test: with no live data, the site must never show made-up numbers for a real company.
// Every /api call fails (fixtures "fail" mode). On the NVDA asset, trade and valuation pages, markets and the portfolio,
// there must be no $ price figure and no "mock", "demo data" or "prototype" text, and no page errors.
// Then, with recorded data, the same NVDA pages must show a real $ price (so the check above isn't passing by accident).
import { chromium } from 'playwright';
import { useFixtures } from './fixtures.mjs';

const BASE = process.env.BASE || 'http://localhost:8124';
const PAGES = ['asset/NVDA', 'trade/NVDA', 'markets', 'portfolio', 'valuation/NVDA'];
const PRICE = /\$\d[\d,]*(\.\d+)?/;
const MOCK = /\bmock\b|demo data|prototype|sample data/i;
const failures = [];

async function open(browser, mode) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('q_mnd', '1'); localStorage.setItem('q_priv', '"all"'); } catch (e) {} });
  const page = await ctx.newPage();
  await useFixtures(page, mode);
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('dialog', d => d.dismiss().catch(() => {}));
  await page.goto(BASE + '/'); await page.waitForTimeout(1500);
  return { ctx, page, errors };
}
// Amounts that are the practice account's own settings, not prices of a company
const ALLOWED = /\$100,000 of virtual|\$100,000 (?:in )?(?:virtual|practice)|(?:portfolio value|cash|buying power)\s*\$[\d,.]+(?:\s*[▲▼]\s*\$[\d,.]+)?|[+−-]?\$0\.00\b/gi; // and empty totals ($0.00)
const text = page => page.evaluate(() => (document.getElementById('m') || document.body).innerText).then(t => t.replace(ALLOWED, '(practice cash)'));
const go = async (page, route) => { await page.evaluate(r => { location.hash = '#/' + r; }, route); await page.waitForTimeout(2500); };

const browser = await chromium.launch();
{
  const { ctx, page, errors } = await open(browser, 'fail');
  for (const route of PAGES) {
    await go(page, route);
    const t = await text(page), where = `offline #/${route}`;
    for (const e of errors.splice(0)) failures.push(`${where}: page error: ${e}`);
    const p = PRICE.exec(t); if (p) { const i = t.indexOf(p[0]); failures.push(`${where}: shows a price with no data: "${t.slice(Math.max(0, i - 50), i + 20).replace(/\s+/g, ' ')}"`); }
    const m = MOCK.exec(t); if (m) { const i = t.indexOf(m[0]); failures.push(`${where}: mentions mock/demo data: "${t.slice(Math.max(0, i - 50), i + 40).replace(/\s+/g, ' ')}"`); }
  }
  // every other page: no mock/demo/prototype wording either (they may show $ in lessons and calculators)
  for (const route of ['', 'world', 'news', 'stocks', 'screener', 'compare', 'ipos', 'plan', 'planner', 'learn', 'ai', 'macro', 'calendar', 'options', 'private', 'leaderboard', 'insights', 'research', 'challenge']) {
    await go(page, route);
    const t = await text(page), where = `offline #/${route}`;
    for (const e of errors.splice(0)) failures.push(`${where}: page error: ${e}`);
    const m = MOCK.exec(t); if (m) { const i = t.indexOf(m[0]); failures.push(`${where}: mentions mock/demo data: "${t.slice(Math.max(0, i - 50), i + 40).replace(/\s+/g, ' ')}"`); }
  }
  await ctx.close();
}
{
  const { ctx, page, errors } = await open(browser, 'ok');
  for (const route of ['asset/NVDA', 'trade/NVDA']) {
    await go(page, route);
    const t = await text(page), where = `recorded #/${route}`;
    for (const e of errors.splice(0)) failures.push(`${where}: page error: ${e}`);
    if (!PRICE.test(t)) failures.push(`${where}: no price shown even with data`);
  }
  await ctx.close();
}
await browser.close();
if (failures.length) { console.log(failures.map(f => 'FAIL ' + f).join('\n')); console.log(`\n${failures.length} failure(s)`); process.exit(1); }
console.log('PASS offline: no invented prices or demo-data text for real companies; recorded data still shows prices');
