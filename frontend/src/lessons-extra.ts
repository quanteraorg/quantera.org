// Four lessons the Academy was missing: market capitalization and how stock markets work (Beginner), reading an
// earnings report (Intermediate) and portfolio optimization (Advanced). Same shape as the built-in lessons, so the
// Academy, guided paths, search, dashboards, quizzes and progress all pick them up. General principles only: no
// company-specific figures that would go out of date.
declare const LX: Record<string, any>;
declare const WD: Record<string, any>;
declare const PATHS: [string, string[]][];
type Q = [string, string[], number, string];
interface X { i: string; h: string[]; e: string; m: string[]; k: string[]; q: Q[] }
type Slider = [string, string, number, number, number, number];
interface W { s: Slider[]; f: (S: Record<string, number>) => string }

const usd = (v: number) => v >= 1e12 ? `$${fmt(v / 1e12)} trillion` : v >= 1e9 ? `$${fmt(v / 1e9, 1)} billion` : v >= 1e6 ? `$${fmt(v / 1e6, 0)} million` : `$${fmt(v, 2)}`;
const row = (k: string, v: string) => `<div class="stat"><span>${k}</span><b>${v}</b></div>`;

const NEW: { les: [string, string, string, string, string, string, string, string, [string, string[], number], string]; x: X; w: W; after: string; path: string }[] = [
  {
    les: ['mcap', 'Market capitalization', 'Investing', 'Beginner', '#c8a96a', '#1f3a5a', 'What a company is worth on the stock market, and why the share price alone doesn’t tell you.', '#/stocks',
      ['Market capitalization is…', ['Share price × number of shares', 'The share price', 'Yearly revenue'], 0],
      'A company with 1 billion shares at $50 each has a market value of $50 billion. One with 50 million shares at $400 is worth $20 billion, less, despite the higher price.'],
    x: {
      i: 'Market capitalization (market cap) is the total value the stock market puts on a company: its share price multiplied by the number of shares in existence. It is the usual way to say how big a public company is.',
      h: ['<b>Market cap = share price × shares outstanding.</b>', 'A high share price doesn’t mean a big company: it depends on how many shares exist. Companies can split shares to make each one cheaper without changing the total.',
        'Rough size bands: large cap (about $10 billion and up), mid cap ($2–10 billion), small cap (under $2 billion). Smaller companies tend to swing more in price.',
        '<b>Enterprise value</b> adds debt and subtracts cash: what it would cost to buy the whole business, including what it owes.'],
      e: 'A $10 stock with 2 billion shares has a market cap of <b>$20 billion</b>. A $500 stock with 10 million shares: <b>$5 billion</b>. If the first company has $4 billion of debt and $1 billion of cash, its enterprise value is $20B + $4B − $1B = <b>$23 billion</b>.',
      m: ['Judging a company’s size by its share price.', 'Thinking a stock split makes a company more valuable. A 2-for-1 split doubles the shares and halves the price; market cap stays the same.', 'Forgetting that market cap changes every time the share price does.'],
      k: ['Market cap = price × shares.', 'Share price alone says nothing about size.', 'Enterprise value adds debt and subtracts cash.'],
      q: [['A company has 400 million shares at $25. Its market cap is…', ['$10 billion', '$400 million', '$25 billion'], 0, '400 million × $25 = $10 billion.'],
        ['After a 2-for-1 stock split, market cap…', ['Stays about the same', 'Doubles', 'Halves'], 0, 'There are twice as many shares at half the price, so price × shares is unchanged.']],
    },
    w: { s: [['p', 'Share price ($)', 1, 1000, 1, 50], ['n', 'Shares outstanding (millions)', 10, 20000, 10, 1000], ['d', 'Net debt: debt minus cash ($ billions)', -50, 200, 1, 5]],
      f: S => { const cap = S.p * S.n * 1e6, ev = cap + S.d * 1e9, band = cap >= 1e10 ? 'Large cap' : cap >= 2e9 ? 'Mid cap' : 'Small cap';
        return row('Market capitalization', usd(cap)) + row('Size band', band) + row('Enterprise value', usd(Math.max(0, ev))) + `<p class="mu" style="font-size:13px;margin-top:10px">Doubling the shares while halving the price (a 2-for-1 split) leaves the market cap at ${usd(cap)}.</p>`; } },
    after: 'stock', path: 'Start here',
  },
  {
    les: ['markets', 'How stock markets work', 'Markets', 'Beginner', '#7aa6e8', '#22304a', 'Who buys and sells, how a price is set, and what the bid, the ask and an index are.', '#/markets',
      ['A stock’s price changes when…', ['Buyers and sellers agree trades at a different price', 'The company decides a new price', 'The exchange sets it each morning'], 0],
      'US stock exchanges are open from 9:30 a.m. to 4 p.m. New York time on weekdays. News that comes out after 4 p.m. shows up in the next day’s price.'],
    x: {
      i: 'A stock market is where shares in companies are bought and sold. A company first sells shares to raise money (for example in an IPO); after that, investors trade those shares with each other on exchanges such as the NYSE and Nasdaq. The company doesn’t set the price: trades between buyers and sellers do.',
      h: ['The <b>bid</b> is the highest price a buyer is offering right now; the <b>ask</b> is the lowest price a seller will accept. The gap between them is the <b>spread</b>.',
        'A trade happens when a buyer and a seller agree. The last trade price is what you see quoted as “the price”.',
        '<b>Market makers</b> stand ready to buy and sell, which keeps trading smooth; the spread is part of how they are paid.',
        'An <b>index</b> such as the S&P 500 tracks a basket of stocks, so you can see how the market as a whole moved.'],
      e: 'If the bid is $99.90 and the ask is $100.10, the spread is $0.20, or 0.2% of the price. Buying 100 shares at the ask and selling straight back at the bid would lose $20 before any fees.',
      m: ['Thinking the company sets or receives money from each trade. After the IPO, trades are between investors.', 'Ignoring the spread on thinly traded stocks, where it can be large.', 'Expecting to trade at the last price shown: you buy at the ask and sell at the bid.'],
      k: ['Prices come from trades between buyers and sellers.', 'You buy at the ask and sell at the bid.', 'An index shows how a whole market moved.'],
      q: [['The bid is $50.00 and the ask is $50.05. If you buy now with a market order, you’ll usually pay about…', ['$50.05', '$50.00', '$50.025'], 0, 'A buyer pays the lowest price a seller is asking: the ask.'],
        ['After a company’s IPO, when investors trade its shares on an exchange, the money goes…', ['Between the investors trading', 'To the company each time', 'To the exchange in full'], 0, 'Trading after the IPO is between investors. The company raised its money when it first sold the shares.']],
    },
    w: { s: [['b', 'Bid ($)', 1, 500, 0.05, 99.9], ['a', 'Ask ($)', 1, 500, 0.05, 100.1], ['n', 'Shares', 1, 1000, 1, 100]],
      f: S => { if (S.a < S.b) return '<p class="dn">The ask can’t be below the bid: a seller would already be trading with that buyer.</p>'; const sp = S.a - S.b, mid = (S.a + S.b) / 2;
        return row('Spread', `$${fmt(sp)} (${fmt(sp / mid * 100)}% of the price)`) + row('Cost to buy ' + S.n + ' shares', `$${fmt(S.a * S.n)}`) + row('Lost by buying and selling straight away', `$${fmt(sp * S.n)} before fees`); } },
    after: 'stock', path: 'Start here',
  },
  {
    les: ['earnings', 'Reading an earnings report', 'Equity research', 'Intermediate', '#4fd1a5', '#173a33', 'What a quarterly report contains, how to compare it with expectations, and why guidance often matters most.', '#/calendar',
      ['When a company “beats estimates”, it means…', ['Its results came in above analysts’ average forecast', 'Its profit doubled', 'Its share price rose'], 0],
      'US public companies report every quarter. Results usually come in a press release, often filed with the SEC on Form 8-K, followed by a conference call with analysts.'],
    x: {
      i: 'Every quarter, public companies report their results: revenue, profit, earnings per share (EPS) and, often, a forecast for the next period (guidance). Investors compare these with what was expected, because the share price already reflects those expectations.',
      h: ['<b>Revenue</b>: sales for the quarter. <b>EPS</b>: profit per share. Check both against the same quarter a year earlier, not just the previous quarter, because many businesses are seasonal.',
        '<b>Versus expectations</b>: analysts’ average forecast (the “consensus”). Surprise = (actual − expected) ÷ expected.',
        '<b>Guidance</b>: the company’s own forecast. A cut in guidance can outweigh a strong quarter, because investors value future profits.',
        '<b>GAAP vs adjusted</b>: “adjusted” or non-GAAP figures leave some costs out. The report has to show how they get from one to the other.'],
      e: 'Analysts expected EPS of $1.20; the company reported $1.32. The surprise is (1.32 − 1.20) ÷ 1.20 = <b>+10%</b>. If it also lowered its forecast for next quarter, the shares could still fall.',
      m: ['Comparing a quarter with the one before when the business is seasonal (holiday sales, for example).', 'Treating adjusted EPS as the whole picture without checking what was left out.', 'Assuming a beat means the stock will rise: guidance and expectations decide the reaction.'],
      k: ['Compare with the same quarter last year.', 'What matters is results versus expectations.', 'Guidance often moves the stock most.'],
      q: [['Expected EPS was $2.00 and actual EPS was $1.80. The surprise is…', ['−10%', '+10%', '−20%'], 0, '(1.80 − 2.00) ÷ 2.00 = −0.10, a 10% miss.'],
        ['Why compare a quarter with the same quarter a year earlier?', ['Many businesses are seasonal, so it compares like with like', 'Because the SEC requires it', 'Because last quarter is always wrong'], 0, 'A retailer’s holiday quarter, for example, is naturally bigger than the one before it.']],
    },
    w: { s: [['e', 'Expected EPS ($)', 0.1, 10, 0.01, 1.2], ['a', 'Actual EPS ($)', -2, 12, 0.01, 1.32], ['g', 'Change in next-quarter guidance (%)', -20, 20, 1, 0]],
      f: S => { const s = (S.a - S.e) / S.e * 100;
        return row('Earnings surprise', `${s >= 0 ? '+' : '−'}${fmt(Math.abs(s))}% (${s > 0.5 ? 'a beat' : s < -0.5 ? 'a miss' : 'in line'})`) + row('Guidance', S.g > 0 ? `raised ${S.g}%` : S.g < 0 ? `cut ${Math.abs(S.g)}%` : 'unchanged') +
          `<p class="mu" style="font-size:13px;margin-top:10px">${S.g < 0 && s > 0 ? 'A beat with lower guidance: the outlook often matters more, so the shares could still fall.' : S.g > 0 && s < 0 ? 'A miss with higher guidance: investors may look past the quarter.' : 'How the shares react depends on how this compares with what investors already expected.'} This is how to read a report, not a prediction.</p>`; } },
    after: 'research', path: 'Research a company',
  },
  {
    les: ['portopt', 'Portfolio optimization', 'Quant', 'Advanced', '#d46ea0', '#3a1f34', 'Combine two investments to get the most return for the risk you take, and see why correlation matters.', '#/portfolio',
      ['Combining two assets reduces risk most when their correlation is…', ['Low or negative', 'Exactly 1', 'Irrelevant'], 0],
      'Harry Markowitz’s 1952 paper “Portfolio Selection” introduced mean-variance optimization, the basis of modern portfolio theory.'],
    x: {
      i: 'Portfolio optimization chooses how much to hold of each investment to get the best trade-off between expected return and risk (volatility). The key insight of modern portfolio theory is that a portfolio’s risk depends not just on each holding’s risk but on how they move together.',
      h: ['For two assets with weights w and 1 − w: <b>expected return = w·rA + (1 − w)·rB</b>.', '<b>Volatility = √(w²σA² + (1 − w)²σB² + 2w(1 − w)ρσAσB)</b>, where ρ is the correlation between them.',
        'If ρ is below 1, the mix is less volatile than the weighted average of the two volatilities. That is diversification.',
        'The set of best mixes is the <b>efficient frontier</b>. Real inputs are estimates, so optimized weights can swing a lot when the estimates change.'],
      e: 'Stock A: 10% expected return, 20% volatility. Stock B: 6% return, 10% volatility. Correlation 0.2. A 50/50 mix has an expected return of 8% and volatility √(0.25·0.04 + 0.25·0.01 + 2·0.25·0.2·0.2·0.1) = √0.0145 ≈ <b>12.0%</b>, below the 15% weighted average.',
      m: ['Treating estimated returns and correlations as known. Small changes in inputs can change the “optimal” weights a lot.', 'Assuming correlations stay fixed: in market crashes they often rise toward 1.', 'Optimizing return alone and ignoring risk.'],
      k: ['Risk depends on correlation, not just each holding.', 'Low correlation gives more diversification.', 'Optimal weights are only as good as the estimates behind them.'],
      q: [['Two assets each have 20% volatility and a correlation of 1. A 50/50 mix has volatility of about…', ['20%', '10%', '14%'], 0, 'With correlation 1 they move together, so there is no diversification: the mix is as volatile as each one, 20%.'],
        ['Why are optimized portfolios often unstable in practice?', ['They depend on estimates of returns and correlations that change', 'Because volatility can’t be measured', 'Because diversification never works'], 0, 'Small errors in the inputs can produce very different “optimal” weights.']],
    },
    w: { s: [['w', 'Weight in A (%)', 0, 100, 5, 50], ['ra', 'A: expected return (%)', -5, 25, 0.5, 10], ['va', 'A: volatility (%)', 1, 60, 1, 20], ['rb', 'B: expected return (%)', -5, 25, 0.5, 6], ['vb', 'B: volatility (%)', 1, 60, 1, 10], ['c', 'Correlation (×100)', -100, 100, 5, 20]],
      f: S => { const w = S.w / 100, ra = S.ra / 100, rb = S.rb / 100, a = S.va / 100, b = S.vb / 100, r = S.c / 100;
        const er = w * ra + (1 - w) * rb, v = Math.sqrt(w * w * a * a + (1 - w) * (1 - w) * b * b + 2 * w * (1 - w) * r * a * b), avg = w * a + (1 - w) * b;
        return row('Expected return', fmt(er * 100) + '%') + row('Volatility', fmt(v * 100) + '%') + row('Weighted-average volatility', fmt(avg * 100) + '%') + row('Return per unit of risk', v > 0 ? fmt(er / v) : '—') +
          `<p class="mu" style="font-size:13px;margin-top:10px">${v < avg - 1e-9 ? `Diversification removes ${fmt((avg - v) * 100)} points of volatility.` : 'With correlation of 1 there is no diversification benefit.'} Estimates, not a forecast.</p>`; } },
    after: 'sharpe', path: 'Advanced toolkit',
  },
];

export function initLessonsExtra(): void {
  const les = LES as unknown as unknown[][], lx = LX as unknown as Record<string, X>, wd = WD as unknown as Record<string, W>, paths = PATHS as unknown as [string, string[]][];
  for (const n of NEW) {
    if (les.some(l => l[0] === n.les[0])) continue;
    const at = les.findIndex(l => l[0] === n.after);
    les.splice(at >= 0 ? at + 1 : les.length, 0, n.les as unknown as unknown[]);
    lx[n.les[0]] = n.x; wd[n.les[0]] = n.w;
    const p = paths.find(x => x[0] === n.path); if (p && !p[1].includes(n.les[0])) p[1].push(n.les[0]);
  }
  // Learning journeys by goal, alongside the existing paths: understand a company's numbers, then analyze its value.
  const J: [string, string[]][] = [['Understand companies', ['mcap', 'income', 'cashflow', 'earnings', 'ratios']], ['Analyze companies', ['pe', 'peg', 'dcf', 'wacc', 'research']]];
  for (const [name, ids] of J) if (!paths.some(p => p[0] === name)) paths.push([name, ids.filter(id => les.some(l => l[0] === id))]);
}
