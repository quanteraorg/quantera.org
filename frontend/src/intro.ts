// "Introduction to investing": the first lesson for complete beginners, featured at the top of the Academy and first in
// the "Start here" path. Same shape as every other lesson (LES entry, LX content, WD calculator), so the lesson page,
// quiz, progress and badges all work unchanged. The static /learn/intro page is built from scripts/lessons.json.
declare const LX: Record<string, any>;
declare const WD: Record<string, any>;
declare const PATHS: [string, string[]][];
declare function svg(lines: { d: number[]; c: string; w?: number; dash?: number }[], w?: number, h?: number, log?: boolean, ax?: unknown): string;

const ID = 'intro';

export const INTRO_LES = [ID, 'Introduction to investing', 'Finance basics', 'Beginner', '#c8a96a', '#163a4a',
  'Start here. What investing is, how it differs from saving, the main types of investment, risk and return, and how people begin.',
  '#/trade', ['Investing usually means accepting…', ['Some risk of loss for the chance of more growth', 'No risk at all', 'A guaranteed return'], 0],
  'An index fund that tracks the S&P 500 holds about 500 large US companies in a single investment.'];

export const INTRO_X = {
  i: 'Investing means putting money into something you expect to grow in value, or to pay you income, over time: part of a company (a stock), a loan to a government or company (a bond), or a fund that holds many of these at once. In exchange for the chance of more growth than a savings account, you accept that the value will go up and down, and can fall.',
  h: [
    '<b>Saving vs investing.</b> A savings account is safe and easy to use, but grows slowly, and rising prices (inflation) mean the same dollars buy less each year. Investing aims for more growth over many years, with ups and downs along the way.',
    '<b>The main building blocks.</b> <b>Stocks</b>: small pieces of companies; the highest long-run growth and the biggest swings. <b>Bonds</b>: loans that pay interest; usually steadier. <b>Cash</b>: safe, low return. <b>Funds</b> (index funds and ETFs) bundle hundreds of stocks or bonds into one investment, often at low cost.',
    '<b>Risk and return go together.</b> Investments that can grow more can also fall further. Spreading money across many companies (diversification) means one bad company can’t sink you.',
    '<b>Time is your biggest advantage.</b> Returns earn their own returns (compounding), and a long time horizon gives you room to wait out falls in the market.',
    '<b>How markets work.</b> Shares are bought and sold between investors on exchanges such as the NYSE and Nasdaq; the price is wherever buyers and sellers agree. An index like the S&P 500 tracks a group of stocks to show how “the market” is doing.',
    '<b>How people start.</b> Open an account with a brokerage (under 18s need a parent or guardian to open a custodial account), invest a set amount regularly, keep fees low, and only invest money you won’t need for several years.',
    '<b>Next on Quantera:</b> <a href="#/lesson/stock">What is a stock?</a>, then <a href="#/lesson/compound">Compound interest</a> and <a href="#/lesson/divers">Diversification</a>. Practice with <a href="#/trade">$100,000 of virtual money</a>: no real money is involved.'
  ],
  e: 'Put away $100 a month for 30 years and you contribute $36,000. If it were invested and earned 7% a year (an assumption for illustration, not a promise), it would grow to about $122,000. Kept as cash earning nothing, it stays $36,000, and if prices rise 3% a year, $36,000 in 30 years buys only about what $14,800 buys today.',
  m: [
    'Trying to get rich quickly by putting everything into one “hot” stock instead of spreading money out.',
    'Selling in a panic after a fall, which turns a temporary drop into a permanent loss.',
    'Ignoring fees: 1% a year sounds small but can take a large share of your money over decades.',
    'Investing money you’ll need soon: money for the next few years belongs in savings.',
    'Treating investing like gambling: frequent trading on tips is closer to betting than to investing.'
  ],
  k: [
    'Investing means owning things you expect to grow, and accepting ups and downs.',
    'Higher possible returns come with bigger possible falls.',
    'Diversify, for example with a low-cost index fund.',
    'Start early, invest regularly, keep fees low, and leave it alone.',
    'Practice first: paper trading uses virtual money.'
  ],
  q: [
    ['Why can money in a savings account lose value even when the balance doesn’t fall?', ['Prices rise over time (inflation), so it buys less', 'Banks charge you to hold it', 'Interest rates are always negative'], 0, 'Inflation means the same dollars buy less each year.'],
    ['Which usually swings the most in price?', ['Stocks', 'Government bonds', 'Cash in a savings account'], 0, 'Stocks have offered the highest long-run growth, with the biggest ups and downs.'],
    ['What is an index fund?', ['A fund that holds many stocks to track a market index, often at low cost', 'A single company’s stock', 'A guaranteed savings product'], 0, 'One index fund can hold hundreds of companies.'],
    ['The market falls 20%. For a long-term investor in a diversified fund, the usual advice is to…', ['Stick to the plan rather than panic-sell', 'Sell everything immediately', 'Borrow money to buy one stock'], 0, 'Selling after a fall locks in the loss. Diversified markets have recovered from past falls over time, though sometimes slowly, and the past doesn’t guarantee the future.']
  ]
};

export const INTRO_WD = {
  s: [['m', 'Put aside each month ($)', 10, 1000, 10, 100], ['y', 'Years', 1, 50, 1, 30], ['r', 'Yearly return if invested (%)', 0, 12, 0.5, 7]],
  f: (S: Record<string, number>) => {
    const inv: number[] = [0], cash: number[] = [0];
    let v = 0;
    for (let i = 1; i <= S.y * 12; i++) { v = v * (1 + S.r / 100 / 12) + S.m; if (i % 12 === 0) { inv.push(v); cash.push(S.m * i); } }
    const money = (x: number) => '$' + Math.round(x).toLocaleString('en-US');
    return svg([{ d: inv, c: 'var(--ac)', w: 2.5 }, { d: cash, c: 'var(--mu)', w: 2, dash: 1 }], 700, 200, false,
      { fmt: (x: number) => money(x), x0: 'Today', x1: 'Year ' + S.y, label: `Invested at ${S.r}% a year vs kept as cash, over ${S.y} years` })
      + `<div class="big">${money(inv[S.y])}</div><span class="mu">invested at ${S.r}% a year (gold line), vs ${money(cash[S.y])} kept as cash (dashed). The return is an assumption, not a promise: real returns vary from year to year and can be negative.</span>`;
  }
};

export function initIntro(): void {
  const les = LES as unknown as any[];
  if (!les || les.some(l => l[0] === ID)) return;
  les.unshift(INTRO_LES);
  LX[ID] = INTRO_X;
  WD[ID] = INTRO_WD;
  const start = PATHS.find(p => p[0] === 'Start here');
  if (start && !start[1].includes(ID)) start[1].unshift(ID);
  // feature it at the top of the Academy
  const learn = PAGES.learn;
  PAGES.learn = (a?: string) => {
    learn(a);
    const m = document.getElementById('m');
    if (!m || m.querySelector('.qintro')) return;
    const done = DONE.has(ID);
    m.insertAdjacentHTML('afterbegin', `<a class="pan qintro" href="#/lesson/${ID}"><span class="qi-tag">${done ? 'Completed' : 'New to investing? Start here'}</span><b>Introduction to investing</b><span class="qi-d">The basics in about 10 minutes: what investing is, saving vs investing, stocks, bonds and funds, risk and return, and how people begin. Then a short quiz.</span><span class="qi-go">${done ? 'Review the lesson' : 'Start the lesson'} →</span></a>`);
  };
  document.head.insertAdjacentHTML('beforeend', `<style id="qintro-css">.qintro{display:flex;flex-direction:column;gap:4px;margin:4px 0 18px;padding:18px 20px!important;text-decoration:none;color:var(--tx);border:1px solid rgba(200,169,106,.45)!important;background:linear-gradient(135deg,rgba(200,169,106,.12),transparent 60%)!important}
.qintro:hover{border-color:var(--ac)!important}.qintro .qi-tag{font-size:11.5px;letter-spacing:.08em;text-transform:uppercase;color:var(--ac)}.qintro b{font-size:22px;font-weight:600}
.qintro .qi-d{color:var(--mu);font-size:14px;line-height:1.55;max-width:720px}.qintro .qi-go{margin-top:6px;color:var(--ac);font-weight:600;font-size:14px}</style>`);
}
