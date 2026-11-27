// Unit tests for the money maths in the TypeScript modules, with hand-checked answers. Node's built-in test runner,
// no extra dependencies; Node loads the .ts files directly (type stripping, Node 22.18+).
// Calculators that live inside public/app.js (Black-Scholes, Kelly) are checked through the page in interact.mjs.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { dcf, impliedGrowth } from '../src/valuation.ts';
import { RATIOS } from '../src/ratios.ts';

const close = (a, b, tol = 1e-6) => assert.ok(Math.abs(a - b) <= tol, `${a} is not within ${tol} of ${b}`);
const base = { fcf: 10, g: 0, d: 10, t: 0, shares: 1, net: 0, price: 50 };

test('DCF: a flat $10 a year forever at 10% is worth $100 (a perpetuity), per share with 1 share', () => {
  const r = dcf(base);
  close(r.ev, 100);
  close(r.ps, 100);
});

test('DCF: years 1-10 are discounted at (1 + d)^year', () => {
  const r = dcf(base);
  close(r.rows[0].pv, 10 / 1.1);
  close(r.rows[9].df, 1 / Math.pow(1.1, 10));
  close(r.pv, 10 * (1 - Math.pow(1.1, -10)) / 0.1); // annuity of 10 for 10 years
});

test('DCF: Gordon terminal value is FCF(10) × (1 + t) / (d − t), discounted 10 years', () => {
  const x = { ...base, t: 2 };
  const r = dcf(x);
  const c10 = r.rows[9].fcf;
  close(r.tv, c10 * 1.02 / 0.08);
  close(r.tvpv, r.tv / Math.pow(1.1, 10));
});

test('DCF: growth runs at g for years 1-5, then fades in a straight line to t by year 10', () => {
  const r = dcf({ ...base, g: 10, t: 0 });
  assert.deepEqual(r.rows.map(x => +x.growth.toFixed(6)), [10, 10, 10, 10, 10, 8, 6, 4, 2, 0]);
  close(r.rows[4].fcf, 10 * Math.pow(1.1, 5));
});

test('DCF: net cash is added and value is split across shares', () => {
  const r = dcf({ ...base, net: 20, shares: 4 });
  close(r.eq, 120);
  close(r.ps, 30);
});

test('DCF: no terminal value when the discount rate is not above terminal growth', () => {
  assert.ok(Number.isNaN(dcf({ ...base, d: 2, t: 2 }).tv));
});

test('Reverse DCF: the implied growth reproduces the price', () => {
  const x = { ...base, t: 2.5, d: 9 };
  const target = dcf(x, 7).ps;
  const g = impliedGrowth({ ...x, price: target });
  close(g, 7, 1e-6);
});

const ratio = name => RATIOS.flatMap(([, rs]) => rs).find(r => r.name === name);
test('Ratios: margins, returns, cash and safety on simple numbers', () => {
  const x = { revenue: 200, grossProfit: 80, operatingIncome: 40, netIncome: 20, assets: 400, equity: 100, currentAssets: 150, currentLiabilities: 100, cash: 30, totalDebt: 50, operatingCashFlow: 35, capex: -15, marketCap: 400 };
  close(ratio('Gross margin').v(x), 0.4);
  close(ratio('Operating margin').v(x), 0.2);
  close(ratio('Net margin').v(x), 0.1);
  close(ratio('Return on equity').v(x), 0.2);
  close(ratio('Return on assets').v(x), 0.05);
  close(ratio('Free cash flow').v(x), 20); // capex counts as spending whatever its sign
  close(ratio('Free cash flow margin').v(x), 0.1);
  close(ratio('Current ratio').v(x), 1.5);
  close(ratio('Debt to equity').v(x), 0.5);
  close(ratio('Net debt').v(x), 20);
  close(ratio('Price to earnings (P/E)').v(x), 20);
  close(ratio('Price to sales (P/S)').v(x), 2);
  close(ratio('Free cash flow yield').v(x), 0.05);
});

test('Ratios: no number where it would mislead (losses, negative equity, missing inputs, divide by zero)', () => {
  assert.equal(ratio('Price to earnings (P/E)').v({ marketCap: 100, netIncome: -5 }), null);
  assert.equal(ratio('Return on equity').v({ netIncome: 5, equity: -10 }), null);
  assert.equal(ratio('Debt to equity').v({ totalDebt: 5, equity: 0 }), null);
  assert.equal(ratio('Gross margin').v({ grossProfit: 5, revenue: 0 }), null);
  assert.equal(ratio('Gross margin').v({ grossProfit: null, revenue: 10 }), null);
});
