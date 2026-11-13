// Replays real API responses recorded from the live site (fixtures/api.json) so browser tests run on realistic data
// without depending on the live server. Modes:
//   ok   - recorded responses (exact URL, else the same endpoint's first recording, else a small sensible default)
//   slow - the same, each delayed, to exercise loading states
//   fail - every API call fails with a 500 error, to exercise error states
import { readFileSync } from 'node:fs';

const REC = JSON.parse(readFileSync(new URL('./fixtures/api.json', import.meta.url), 'utf8'));
export const STORIES = REC.stories || [];
const byPath = {};
for (const [k, v] of Object.entries(REC.responses)) { const p = k.split('?')[0]; (byPath[p] ||= []).push(v); }

const json = (status, body) => ({ status, contentType: 'application/json', body: typeof body === 'string' ? body : JSON.stringify(body) });
const DEFAULTS = {
  '/api/search': json(200, { results: [{ symbol: 'AAPL', name: 'Apple Inc.', type: 'Common Stock', exchange: 'NASDAQ' }, { symbol: 'MSFT', name: 'Microsoft Corp', type: 'Common Stock', exchange: 'NASDAQ' }] }),
  '/api/directory': json(200, { total: 1, listed: 11131, page: 1, perPage: 25, items: [{ symbol: 'AAPL', name: 'Apple Inc.', type: 'Common Stock' }] }),
};

function answer(url, method) {
  const u = new URL(url);
  if (method !== 'GET') {
    if (u.pathname === '/api/events') return json(200, { ok: true });
    return json(503, { error: 'The AI is not configured' }); // ai, explain, research, waitlist: as on a site without those keys
  }
  const exact = REC.responses[u.pathname + u.search];
  if (exact) return { status: exact.status, contentType: exact.ct, body: exact.body };
  if (DEFAULTS[u.pathname]) return DEFAULTS[u.pathname];
  const any = byPath[u.pathname]?.find(r => r.status === 200);
  if (any) return { status: any.status, contentType: any.ct, body: any.body };
  return json(404, { error: 'Not in the test data' });
}

/** Route this page's /api calls to the recorded data. */
export async function useFixtures(page, mode = 'ok', delayMs = 2500) {
  await page.route('**/api/**', async route => {
    const req = route.request();
    if (mode === 'fail') return route.fulfill(json(500, { error: 'Upstream provider error' }));
    if (mode === 'slow') await new Promise(r => setTimeout(r, delayMs));
    try { await route.fulfill(answer(req.url(), req.method())); } catch { /* page navigated away */ }
  });
  // third-party scripts (analytics, error reporting) are not part of what's under test
  await page.route(/sentry-cdn|_vercel\/insights/, r => r.fulfill({ status: 200, contentType: 'text/javascript', body: '' }));
}
