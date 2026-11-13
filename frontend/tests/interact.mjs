// Interaction test: does every page work when someone actually uses it?
// Runs against a static server for frontend/public (BASE, default http://localhost:8124) with /api answered from
// recorded live data (fixtures.mjs). Fails on any uncaught page error, sideways scrolling, or broken text
// ("undefined", "NaN", "[object Object]") and on any of these not working:
//   1. Click everything: every visible, safe button and tab on every page, desktop and phone
//   2. Flows: search, command menu, stock tabs, watchlist, a paper trade, chart ranges, types, indicators, layouts and
//      drawing, news story options, settings, valuation inputs, lesson quiz
//   3. Slow API: pages show a loading state, then their content
//   4. Failed API: pages show a message instead of breaking
//   5. Phone: the bottom navigation and key buttons work with taps
import { chromium } from 'playwright';
import { useFixtures, STORIES } from './fixtures.mjs';

const BASE = process.env.BASE || 'http://localhost:8124';
const ONLY = process.env.ONLY || ''; // e.g. ONLY=flows to run one part while debugging
const ROUTES = ['', 'markets', 'world', 'news', 'stocks', 'screener', 'compare', 'ipos', 'asset/AAPL', 'trade/AAPL', 'plan',
  'portfolio', 'planner', 'learn', 'glossary', 'ai', 'macro', 'calendar', 'about', 'pricing', 'settings', 'options', 'valuation/AAPL',
  'asset/AAPL/research', 'lesson/pe', 'report', 'report/AAPL', 'private', 'leaderboard'];
// Buttons that leave the page, ask the browser for something, download, or delete data are not clicked blindly.
const SKIP = /delete|log ?out|sign ?out|reset|clear all|remove all|leave the leaderboard|full ?screen|save chart image|share|notify me|download|export|print|install|continue with google|enable alerts|turn on alerts/i;
const BROKEN = /\b(undefined|NaN)\b|\[object Object\]/;
const failures = [];
const fail = (where, what) => { failures.push(`${where}: ${what}`); };

async function newPage(browser, size, mode = 'ok') {
  const phone = size === 'phone';
  const ctx = await browser.newContext(phone ? { viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true, deviceScaleFactor: 2 } : { viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('q_mnd', '1'); localStorage.setItem('q_priv', '"all"'); } catch (e) {} });
  const page = await ctx.newPage();
  await useFixtures(page, mode);
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('dialog', d => d.dismiss().catch(() => {}));
  return { ctx, page, errors };
}

async function checks(page, errors, where, w) {
  for (const e of errors.splice(0)) fail(where, `page error: ${e}`);
  const r = await page.evaluate(() => ({ sw: document.documentElement.scrollWidth, cw: document.documentElement.clientWidth, text: (document.getElementById('m') || document.body).innerText }));
  if (r.sw > r.cw + 1) fail(where, `page is ${r.sw}px wide on a ${r.cw}px screen`);
  const bad = BROKEN.exec(r.text);
  if (bad) { const i = r.text.indexOf(bad[0]); fail(where, `broken text "${r.text.slice(Math.max(0, i - 40), i + 30).replace(/\s+/g, ' ')}"`); }
  return r;
}
const go = async (page, route) => { await page.evaluate(r => { location.hash = '#/' + r; }, route); await page.waitForTimeout(1400); };

// ---------- 1. click everything ----------
async function clickEverything(browser, size) {
  const { ctx, page, errors } = await newPage(browser, size);
  await page.goto(BASE + '/'); await page.waitForTimeout(2000);
  let clicked = 0;
  for (const route of ROUTES) {
    await go(page, route);
    const where = `${size} #/${route}`;
    await checks(page, errors, where);
    const n = await page.evaluate(sel => {
      const els = [...document.querySelectorAll('#m button, #m [role=tab], #sub a')].filter(e => e.offsetParent && !e.disabled);
      els.forEach((e, i) => e.setAttribute('data-qt', String(i)));
      return els.length;
    });
    for (let i = 0; i < Math.min(n, 45); i++) {
      const el = page.locator(`[data-qt="${i}"]`).first();
      if (!(await el.count())) continue;
      const label = ((await el.textContent().catch(() => '')) || '').trim() + ' ' + ((await el.getAttribute('title').catch(() => '')) || '') + ' ' + ((await el.getAttribute('aria-label').catch(() => '')) || '');
      if (SKIP.test(label) || !(await el.isVisible().catch(() => false))) continue;
      try { await el.click({ timeout: 1500, trial: false }); clicked++; } catch { continue; }
      await page.waitForTimeout(220);
      await checks(page, errors, `${where} after clicking "${label.trim().slice(0, 40)}"`);
      await page.keyboard.press('Escape').catch(() => {});
      if (!(await page.evaluate(r => location.hash === '#/' + r || (r === '' && (location.hash === '' || location.hash === '#/')), route))) await go(page, route);
    }
  }
  await ctx.close();
  return clicked;
}

// ---------- 2. flows ----------
async function flows(browser, size) {
  const { ctx, page, errors } = await newPage(browser, size);
  const W = (s) => `${size} flow: ${s}`;
  page.setDefaultTimeout(6000);
  const step = async (name, fn) => { try { await fn(); } catch (e) { fail(W(name), 'stopped: ' + String(e.message || e).split('\n')[0]); await page.keyboard.press('Escape').catch(() => {}); } };
  await page.goto(BASE + '/'); await page.waitForTimeout(2000);
  const ok = async (cond, where, what) => { if (!(await cond)) fail(where, what); await checks(page, errors, where); };

  // search
  await step('search', async () => {
    await go(page, 'markets');
    const q = page.locator('#q');
    if (await q.isVisible()) {
      await q.click(); await q.fill('apple'); await page.waitForTimeout(1200);
      const first = page.locator('#res a, #res [data-s], #res .it').first();
      if (await first.count()) { await first.click(); await page.waitForTimeout(1500); }
      await ok(page.evaluate(() => /#\/asset\//.test(location.hash)), W('search'), `picking a search result didn't open a stock (at ${await page.evaluate(() => location.hash)})`);
    }
  });
  // command menu (desktop keyboard)
  await step('command menu', async () => {
    if (size === 'desktop') {
      await go(page, 'markets');
      await page.keyboard.press('Control+k'); await page.waitForTimeout(400);
      const open = await page.evaluate(() => !!document.querySelector('.cp input, #cpq'));
      await ok(Promise.resolve(open), W('command menu'), 'Ctrl+K did not open the command menu');
      if (open) { await page.keyboard.type('news'); await page.waitForTimeout(300); await page.keyboard.press('Enter'); await page.waitForTimeout(1000);
        await ok(page.evaluate(() => /#\/news/.test(location.hash)), W('command menu'), 'choosing "news" in the command menu did not open News');
        if (await page.evaluate(() => { const c = document.querySelector('.cp'); return !!c && c.offsetParent !== null && getComputedStyle(c).display !== 'none'; })) fail(W('command menu'), 'the command menu stayed open after choosing a page');
        await page.keyboard.press('Escape'); await page.waitForTimeout(200); }
    }
  });
  // stock tabs and watchlist
  await step('stock tabs', async () => {
    await go(page, 'asset/AAPL'); await page.waitForTimeout(1500);
    for (const tab of ['Financials', 'Analysts & earnings', 'Research', 'My notes', 'Overview']) {
      const t = page.locator('#m button', { hasText: tab }).first();
      if (!(await t.count())) { fail(W('stock tabs'), `no "${tab}" tab`); continue; }
      await t.click(); await page.waitForTimeout(700);
      await ok(page.evaluate(() => (document.getElementById('m').innerText || '').length > 200), W(`stock tab ${tab}`), 'tab content is empty');
    }
    // research depth: earnings, valuation in context and timeline on the Research tab
    await step('research depth', async () => {
      await page.locator('#m button', { hasText: /^Research$/ }).first().click(); await page.waitForTimeout(3500);
      const d = await page.evaluate(() => ({ e: document.querySelectorAll('#qdpe tr').length - 1, v: document.querySelectorAll('#qdpv .vrow').length, t: document.querySelectorAll('#qdpt .ev').length, f: document.querySelectorAll('#qdpt [data-f]').length }));
      if (d.e < 4) fail(W('research depth'), `the earnings table has ${d.e} rows`);
      if (d.v < 1) fail(W('research depth'), 'valuation in context shows no multiples');
      if (d.t < 3) fail(W('research depth'), `the timeline has ${d.t} events`);
      const f = page.locator('#qdpt [data-f="earn"]').first();
      if (await f.count()) { await f.click(); await page.waitForTimeout(200); const hidden = await page.evaluate(() => [...document.querySelectorAll('#qdpt .ev')].filter(e => e.style.display !== 'none').every(e => e.dataset.ty === 'earn')); if (!hidden) fail(W('research depth'), 'the Earnings filter on the timeline shows other events too'); }
      await checks(page, errors, W('research depth'));
      await page.locator('#m button', { hasText: /^Overview$/ }).first().click(); await page.waitForTimeout(500);
    });
    const star = page.locator('#m button', { hasText: /Watch/ }).first();
    if (await star.count()) await step('watchlist', async () => {
      const t0 = (await star.textContent()).trim();
      await star.click(); await page.waitForTimeout(400);
      const t1 = (await star.textContent()).trim();
      const wl = await page.evaluate(() => localStorage.getItem('q_wl'));
      if (t0 === t1 || !wl) fail(W('watchlist'), `the Watch button didn't toggle (${t0} → ${t1})`);
      await star.click(); await page.waitForTimeout(300); // put it back
    });
  });
  // chart: ranges, types, indicators, layouts, drawing
  await step('chart', async () => {
    for (const r of ['1M', '6M', '1Y', '5Y']) { const b = page.locator('#m .tvbtn', { hasText: new RegExp(`^${r}$`) }).first(); if (await b.count()) { await b.click(); await page.waitForTimeout(250); } }
    const typeSel = page.locator('#m select.tvsel').first();
    if (await typeSel.count()) for (const v of await typeSel.evaluate(s => [...s.options].map(o => o.value).slice(0, 10))) { await typeSel.selectOption(v); await page.waitForTimeout(150); }
    await checks(page, errors, W('chart ranges and types'));
    const ind = page.locator('#m .tvbtn', { hasText: 'Indicators' }).first();
    if (await ind.count()) {
      await ind.click(); await page.waitForTimeout(400);
      const chips = page.locator('button[data-g="in"]:visible, button[data-g="pn"]:visible');
      const nb = Math.min(await chips.count(), 30);
      let toggled = 0;
      for (let i = 0; i < nb; i++) {
        const c = chips.nth(i), on0 = /\bon\b/.test((await c.getAttribute('class')) || '');
        await c.click({ timeout: 1500 }).catch(() => {}); await page.waitForTimeout(90);
        if (/\bon\b/.test((await c.getAttribute('class').catch(() => '')) || '') !== on0) toggled++;
      }
      if (!nb) fail(W('indicators'), 'the Indicators menu showed no indicators to switch on');
      else if (toggled < nb * 0.8) fail(W('indicators'), `only ${toggled} of ${nb} indicators switched when clicked`);
      await page.keyboard.press('Escape');
      await checks(page, errors, W('indicators on'));
    }
    if (size === 'desktop') {
      for (const l of ['2 charts', '4 charts', '1 chart']) { const b = page.locator('#m .chip', { hasText: l }).first(); if (await b.count()) { await b.click(); await page.waitForTimeout(500); await checks(page, errors, W(`layout ${l}`)); } }
      const tl = page.locator('#m .tvtool[title^="Trend line"]:visible').first();
      const cvi = await page.evaluate(() => { const cs = [...document.querySelectorAll('#m canvas')]; let best = 0, bi = 0; cs.forEach((c, i) => { const r = c.getBoundingClientRect(); if (r.width * r.height > best) { best = r.width * r.height; bi = i; } }); return bi; });
        const cv = page.locator('#m canvas').nth(cvi);
      if (await tl.count() && await cv.count()) {
        await tl.scrollIntoViewIfNeeded(); await tl.click(); await cv.scrollIntoViewIfNeeded(); const bb = await cv.boundingBox();
        if (bb) {
          const before = await page.evaluate(() => localStorage.getItem('q_dr') || '');
          // draw inside the part of the chart that is on screen (with many indicators the chart is taller than the window)
          const vh = await page.evaluate(() => innerHeight), top = Math.max(bb.y, 0), bot = Math.min(bb.y + bb.height, vh) - 20;
          await page.mouse.move(bb.x + bb.width * 0.3, top + (bot - top) * 0.3); await page.mouse.down();
          await page.mouse.move(bb.x + bb.width * 0.6, top + (bot - top) * 0.6, { steps: 6 }); await page.mouse.up(); await page.waitForTimeout(1000); // drawings save 0.5s after the last change
          const after = await page.evaluate(() => localStorage.getItem('q_dr') || '');
          if (after === before) fail(W('drawing'), 'drawing a trend line on the chart did not save a drawing');
          await checks(page, errors, W('drawing'));
        }
      }
    }
  });
  // chart extras: indicator search, ADX, Fibonacci extension, named templates
  if (size === 'desktop') await step('chart extras', async () => {
    await go(page, 'trade/AAPL'); await page.waitForTimeout(1500);
    await page.locator('#m .tvbtn', { hasText: 'Indicators' }).first().click(); await page.waitForTimeout(300);
    await page.fill('.qcs', 'volatility'); await page.waitForTimeout(150);
    const shown = await page.evaluate(() => [...document.querySelectorAll('#m .tvp button[data-g="in"]')].filter(b => b.style.display !== 'none').map(b => b.dataset.v).sort().join(','));
    if (shown !== 'atr,bb,kc') fail(W('indicator search'), `"volatility" showed ${shown || 'nothing'}`);
    await page.fill('.qcs', 'adx'); await page.locator('#m .tvp button[data-v="adx"]').click(); await page.waitForTimeout(300);
    if (!(await page.evaluate(() => document.querySelector('#m .tvp button[data-v="adx"]')?.classList.contains('on')))) fail(W('ADX'), 'clicking ADX after searching did not switch it on');
    await page.fill('.qcs', ''); await page.keyboard.press('Escape');
    const fe = page.locator('#m .tvtool[title^="Fibonacci extension"]').first();
    if (!(await fe.count())) fail(W('Fibonacci extension'), 'tool missing');
    else {
      await fe.scrollIntoViewIfNeeded(); await fe.click(); const cv = page.locator('#m canvas').first(); await cv.scrollIntoViewIfNeeded(); const bb = await cv.boundingBox();
      for (const [fx, fy] of [[0.3, 0.5], [0.5, 0.25], [0.62, 0.4]]) { await page.mouse.click(bb.x + bb.width * fx, bb.y + Math.min(bb.height, 600) * fy); await page.waitForTimeout(200); }
      await page.waitForTimeout(900);
      if (!(await page.evaluate(() => (JSON.parse(localStorage.getItem('q_dr') || '{}').AAPL || []).some(d => d.t === 'fe' && d.a && d.b && d.c)))) fail(W('Fibonacci extension'), 'three clicks did not save an extension');
    }
    await page.locator('#m .tvbtn[title="Chart settings"]').first().click(); await page.waitForTimeout(300);
    await page.fill('.qtpn', 'Test set-up'); await page.click('.qtps'); await page.waitForTimeout(300);
    const names = await page.evaluate(() => JSON.parse(localStorage.getItem('q_tpls') || '[]').map(t => t.name));
    if (!names.includes('Test set-up')) fail(W('templates'), `saving a template did not store it (${names.join(', ')})`);
    await checks(page, errors, W('chart extras'));
  });
  // paper trade
  await step('paper trade', async () => {
    await go(page, 'trade/AAPL'); await page.waitForTimeout(1500);
    const buy = page.locator('#wqb');
    if (await buy.count()) {
      await page.locator('#wq').fill('3').catch(() => {});
      await buy.click(); await page.waitForTimeout(800);
      const confirm = page.locator('button:visible', { hasText: /^(Buy AAPL|Confirm|Place order)$/ }).last();
      if (await confirm.count() && await confirm.isVisible()) { await confirm.click(); await page.waitForTimeout(800); }
      const held = await page.evaluate(() => { const a = JSON.parse(localStorage.getItem('q_pt') || 'null'); return a && a.pos && a.pos.AAPL ? a.pos.AAPL.q : 0; });
      if (!(held > 0)) fail(W('paper trade'), 'buying 3 AAPL did not add a position');
      await checks(page, errors, W('paper trade'));
      await go(page, 'portfolio'); await page.waitForTimeout(1500);
      if (await page.evaluate(() => !!document.querySelector('body > .qm'))) fail(W('paper trade'), 'the order receipt stayed on screen after moving to another page');
      await ok(page.evaluate(() => /AAPL/.test(document.getElementById('m').innerText)), W('portfolio'), 'the new AAPL position is not shown on Portfolio');
    } else fail(W('paper trade'), 'no Buy button on the trade page');
  });
  // news story options
  await step('news story', async () => {
    if (STORIES[0]) {
      await go(page, 'news'); await page.waitForTimeout(800);
      await go(page, 'story/' + encodeURIComponent(STORIES[0])); await page.waitForTimeout(2500);
      await ok(page.evaluate(() => !!document.querySelector('.qnx .one')), W('story'), 'the story breakdown did not appear');
      for (const o of ['pf', 'dr']) {
        const b = page.locator(`button[data-o=${o}]`).first();
        if (!(await b.count())) { fail(W('story'), `option ${o} missing`); continue; }
        await b.click(); await page.waitForTimeout(2500);
        await ok(page.evaluate(o => !!document.querySelector(o === 'pf' ? '.qpi' : '.qdr'), o), W(`story option ${o}`), 'clicking the option showed nothing');
      }
    }
  });
  // settings: theme, privacy switch
  await step('settings', async () => {
    await go(page, 'settings'); await page.waitForTimeout(800);
    const priv = page.locator('#qpvs');
    if (await priv.count()) { await priv.uncheck(); await page.waitForTimeout(200); if ((await page.evaluate(() => localStorage.getItem('q_priv'))) !== '"essential"') fail(W('settings'), 'the privacy switch did not save'); await priv.check(); }
    else fail(W('settings'), 'no privacy switch in Settings');
  });
  // valuation inputs
  await step('valuation', async () => {
    await go(page, 'valuation/AAPL'); await page.waitForTimeout(2000);
    const big = async () => page.evaluate(() => (document.querySelector('.qv-big') || {}).textContent || '');
    const v0 = await big(); const num = page.locator('.qv-in input[type=number]').first();
    if (await num.count()) { await num.fill('30'); await num.dispatchEvent('input'); await num.dispatchEvent('change'); await page.waitForTimeout(400); if ((await big()) === v0) fail(W('valuation'), 'changing an assumption did not change the value'); }
    await checks(page, errors, W('valuation'));
  });
  // lesson quiz
  await step('lesson quiz', async () => {
    await go(page, 'lesson/pe'); await page.waitForTimeout(1000);
    const quizTab = page.locator('#m button, #m .chip', { hasText: /Quiz/ }).first();
    if (await quizTab.count()) { await quizTab.click(); await page.waitForTimeout(400); const opt = page.locator('#m .op').first(); if (await opt.count()) { await opt.click(); await page.waitForTimeout(400); } }
    await checks(page, errors, W('lesson quiz'));

  });  await ctx.close();
}

// ---------- 3 and 4. slow and failed API ----------
async function apiStates(browser, mode) {
  const { ctx, page, errors } = await newPage(browser, 'desktop', mode);
  await page.goto(BASE + '/'); await page.waitForTimeout(1500);
  for (const route of ['markets', 'news', 'asset/AAPL', 'trade/AAPL', 'portfolio', 'calendar', 'screener', 'valuation/AAPL', 'report/AAPL', 'macro', 'private']) {
    await page.evaluate(r => { location.hash = '#/' + r; }, route);
    const where = `${mode} API #/${route}`;
    if (mode === 'slow') {
      await page.waitForTimeout(500);
      const early = await page.evaluate(() => { const m = document.getElementById('m'); const t = (m.innerText || '').trim(); const sk = m.querySelector('[class*="sk"], [class*="load"], [aria-busy="true"], .qdr-sk, .qnx-sk, .qst-load'); return { len: t.length, loading: !!sk || /loading|fetching|…/i.test(t) }; });
      if (early.len < 20 && !early.loading) fail(where, 'blank page while data loads (no loading state)');
      await page.waitForTimeout(3500);
    } else await page.waitForTimeout(2000);
    const r = await checks(page, errors, where);
    if ((r.text || '').trim().length < 40) fail(where, 'page is blank instead of showing a message');
  }
  await ctx.close();
}

// ---------- 5. phone taps ----------
async function phoneTaps(browser) {
  const { ctx, page, errors } = await newPage(browser, 'phone');
  await page.goto(BASE + '/#/markets'); await page.waitForTimeout(2000);
  const links = await page.evaluate(() => [...document.querySelectorAll('#nav > a[data-r]')].filter(a => a.offsetParent).map(a => a.dataset.r));
  if (!links.length) fail('phone', 'no bottom navigation links');
  for (const r of links) {
    await page.locator(`#nav > a[data-r="${r}"]`).first().tap().catch(() => {});
    await page.waitForTimeout(900);
    const h = await page.evaluate(() => location.hash);
    if (!h.includes(r)) fail('phone tap', `tapping "${r}" in the bottom navigation went to ${h}`);
    await checks(page, errors, `phone tap ${r}`);
  }
  await page.evaluate(() => { location.hash = '#/asset/AAPL'; }); await page.waitForTimeout(2000);
  for (const t of ['Financials', 'Research', 'Overview']) { const b = page.locator('#m button', { hasText: t }).first(); if (await b.count()) { await b.tap().catch(() => {}); await page.waitForTimeout(500); await checks(page, errors, `phone tap tab ${t}`); } }
  const r1 = page.locator('#m .tvbtn', { hasText: /^1Y$/ }).first(); if (await r1.count()) { await r1.tap(); await page.waitForTimeout(300); await checks(page, errors, 'phone tap chart range'); }
  // touch gestures on the chart: one finger sideways pans it, a vertical swipe still scrolls the page
  await page.evaluate(() => { location.hash = '#/trade/AAPL'; }); await page.waitForTimeout(2500);
  const cv = page.locator('#m canvas').first();
  if (await cv.count()) {
    await cv.scrollIntoViewIfNeeded(); await page.waitForTimeout(200);
    const bb = await cv.boundingBox(), cdp = await ctx.newCDPSession(page);
    const T = (type, pts) => cdp.send('Input.dispatchTouchEvent', { type, touchPoints: pts.map(([x, y], id) => ({ x, y, id })) });
    const cx = bb.x + bb.width / 2, cy = bb.y + Math.min(bb.height, 500) * 0.4;
    const img0 = (await cv.screenshot()).toString('base64'), y0 = await page.evaluate(() => scrollY);
    await T('touchStart', [[cx, cy]]); for (let i = 1; i <= 8; i++) await T('touchMove', [[cx + i * 15, cy + i]]); await T('touchEnd', []); await page.waitForTimeout(300);
    if ((await cv.screenshot()).toString('base64') === img0) fail('phone chart', 'a sideways swipe did not pan the chart');
    if ((await page.evaluate(() => scrollY)) !== y0) fail('phone chart', 'a sideways swipe on the chart scrolled the page');
    const y1 = await page.evaluate(() => scrollY);
    await T('touchStart', [[cx, cy]]); for (let i = 1; i <= 8; i++) await T('touchMove', [[cx, cy - i * 25]]); await T('touchEnd', []); await page.waitForTimeout(500);
    if ((await page.evaluate(() => scrollY)) === y1) fail('phone chart', 'a vertical swipe on the chart no longer scrolls the page');
    await checks(page, errors, 'phone chart gestures');
  }
  await ctx.close();
}

const browser = await chromium.launch();
const t0 = Date.now();
let clicks = 0;
try {
  if (!ONLY || ONLY === 'clicks') for (const size of ['desktop', 'phone']) { try { clicks += await clickEverything(browser, size); } catch (e) { fail(`${size} click-everything`, 'stopped: ' + String(e.message || e).split('\n')[0]); } }
  if (!ONLY || ONLY === 'flows') for (const size of ['desktop', 'phone']) { try { await flows(browser, size); } catch (e) { fail(`${size} flows`, 'stopped: ' + String(e.message || e).split('\n')[0]); } }
  if (!ONLY || ONLY === 'api') for (const m of ['slow', 'fail']) { try { await apiStates(browser, m); } catch (e) { fail(`${m} API`, 'stopped: ' + String(e.message || e).split('\n')[0]); } }
  if (!ONLY || ONLY === 'phone') try { await phoneTaps(browser); } catch (e) { fail('phone taps', 'stopped: ' + String(e.message || e).split('\n')[0]); }
} finally { await browser.close(); }
const uniq = [...new Set(failures)];
if (uniq.length) { console.error(`${uniq.length} problem(s):\n` + uniq.map(f => '  - ' + f).join('\n')); process.exit(1); }
console.log(`OK: ${clicks} buttons clicked across ${ROUTES.length} pages on desktop and phone; search, command menu, stock tabs, watchlist, chart, drawing, paper trade, news options, settings, valuation and quiz flows; slow and failed API states; phone taps. ${Math.round((Date.now() - t0) / 1000)}s.`);
