// Live API check: calls every read-only endpoint on the real server and checks each answers quickly with the shape the
// website expects. Run daily (and on demand) by .github/workflows/live-check.yml. Doesn't touch AI or account endpoints,
// which cost money or change data.
//   API (default https://quantera-api.onrender.com) - the server to check
const API = (process.env.API || 'https://quantera-api.onrender.com').replace(/\/$/, '');
const SLOW_MS = 8000;

const has = (...keys) => b => keys.every(k => b && b[k] !== undefined && b[k] !== null);
const list = (key, min = 1) => b => Array.isArray(b?.[key]) && b[key].length >= min;
const CHECKS = [
  ['health', b => b.ok === true],
  ['quote?symbol=AAPL', b => typeof b.price === 'number' && b.price > 0],
  ['quotes?symbols=AAPL,MSFT,SPY', list('quotes', 3)],
  ['history?symbol=AAPL&full=1', list('bars', 200)],
  ['intraday?symbol=AAPL', list('bars', 10)],
  ['overview?symbol=AAPL', has('name', 'industry')],
  ['search?q=apple', list('results')],
  ['directory?q=app', list('items')],
  ['news', list('articles', 5)],
  ['news?symbols=NVDA', list('articles')],
  ['calendar?type=earnings', list('items')],
  ['calendar?type=ipo', list('items')],
  ['calendar?type=economic', list('items')],
  ['markets', has('movers')],
  ['world', list('regions')],
  ['world/country?c=JP', b => !!b && !b.error],
  ['macro', b => !!b && !b.error],
  ['snapshot', b => !!b && !b.error],
  ['popular', list('items')],
  ['screen', list('items', 10)],
  ['filings?symbol=AAPL', has('cik')],
  ['financials?symbol=AAPL', list('annual')],
  ['statements?symbol=AAPL', b => !!b?.annual?.periods?.length],
  ['statements?symbol=JPM', b => b?.financial === true],
  ['peers?symbol=NVDA', list('peers', 2)],
  ['technicals?symbol=AAPL', b => ['Strong buy', 'Buy', 'Neutral', 'Sell', 'Strong sell'].includes(b?.rating) && b.signals?.length >= 6],
  ['fundamentals?symbol=AAPL', has('key')],
  ['private', list('companies', 10)],
  ['private/company?symbol=OPAI.PVT', b => !!b && !b.error],
];

const failures = [], slow = [], t0 = Date.now();
await Promise.all(CHECKS.map(async ([path, ok]) => {
  const s = Date.now();
  try {
    const r = await fetch(`${API}/api/${path}`, { signal: AbortSignal.timeout(45000) });
    const ms = Date.now() - s, txt = await r.text();
    let b = null; try { b = JSON.parse(txt); } catch { /* not JSON */ }
    if (r.status !== 200) failures.push(`/api/${path}: HTTP ${r.status} ${txt.slice(0, 160)}`);
    else if (!b) failures.push(`/api/${path}: not JSON`);
    else if (!ok(b)) failures.push(`/api/${path}: unexpected answer ${txt.slice(0, 160)}`);
    if (ms > SLOW_MS) slow.push(`/api/${path} took ${(ms / 1000).toFixed(1)}s`);
  } catch (e) { failures.push(`/api/${path}: ${e.name === 'TimeoutError' ? 'no answer in 45s' : e.message}`); }
}));
// keys and services the site depends on (reported separately: these need a founder, not a code fix)
let status = '';
try { const r = await fetch(`${API}/api/status`); status = r.status === 200 ? 'all services OK' : (await r.json()).error; } catch (e) { status = 'status check failed: ' + e.message; }

console.log(`Checked ${CHECKS.length} endpoints on ${API} in ${((Date.now() - t0) / 1000).toFixed(1)}s. Services: ${status}`);
if (slow.length) console.log('Slow:\n  - ' + slow.join('\n  - '));
if (failures.length) { console.error(`${failures.length} endpoint(s) failing:\n  - ` + failures.join('\n  - ')); process.exit(1); }
console.log('All endpoints answered with the expected data.');
