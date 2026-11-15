import { chromium, devices } from 'playwright';
import { useFixtures } from './fixtures.mjs';
const b = await chromium.launch();
for (const [n, o] of [['d', { viewport: { width: 1440, height: 900 } }], ['p', devices['iPhone 13']]]) {
const page = await (await b.newContext(o)).newPage(); await useFixtures(page, 'ok');
await page.goto('http://127.0.0.1:8124/'); await page.waitForTimeout(2500);
await page.screenshot({ path: `/tmp/claude-0/-home-user-quantera-org/51255b97-6aae-5ef8-87d2-15c61321ac03/scratchpad/hp-${n}.png` });
console.log(n, await page.evaluate(() => document.documentElement.scrollWidth > innerWidth + 1));
}
await b.close();
