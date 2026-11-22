// Lessons that teach rather than reward guessing.
//   - "What you'll learn" at the top of every lesson (from the lesson's own key takeaways), with its level,
//     length and number of quiz questions.
//   - Quizzes: each question locks after the first answer. A wrong answer shows the right one and explains it,
//     so clicking until something turns green no longer completes a lesson. The score is saved (q_qz, synced
//     with the account like lesson progress), can be retried, and feeds the Student dashboard.
declare function saveDone_(): void;
declare function SAVENOTE(): string;
declare const LX: Record<string, { i?: string; k?: string[]; q?: [string, string[], number, string][] }>;

export interface QuizScore { last: number; best: number; n: number; at: string }
export const QZKEY = 'q_qz';
export const quizScores = (): Record<string, QuizScore> => LS<Record<string, QuizScore>>(QZKEY, {});

const CSS = `
.qlo{border:1px solid var(--ln);border-radius:14px;padding:14px 18px;margin:14px 0 4px;background:var(--s1);max-width:760px}
.qlo h3{margin:0 0 6px;font-size:13px;letter-spacing:.1em;text-transform:uppercase;color:var(--mu)}
.qlo ul{margin:0;padding-left:20px;line-height:1.7;font-size:15px}.qlo .meta{margin:8px 0 0;font-size:13px;color:var(--mu)}
#lx3 .op[disabled]{cursor:default;opacity:1}#lx3 .op.ok{border-color:var(--up)!important}#lx3 .op.no{border-color:var(--dn)!important}
#lx3 .qsum{margin-top:14px;border-top:1px solid var(--ln);padding-top:12px;display:flex;gap:10px;align-items:center;flex-wrap:wrap}
#lx3 .qsum b.sc{font-size:20px}
.qlv{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;margin:6px 0 20px}@media(max-width:820px){.qlv{grid-template-columns:1fr}}
.qlv a{display:flex;flex-direction:column;gap:6px;border:1px solid var(--ln);border-radius:14px;padding:16px 18px;background:var(--s1);color:var(--tx);text-decoration:none}
.qlv a:hover{border-color:rgba(var(--acc),.55)}.qlv small{font-size:11.5px;letter-spacing:.12em;text-transform:uppercase;font-weight:700;color:var(--ac,#c8a96a)}
.qlv b{font-size:16px}.qlv span{color:var(--mu);font-size:13.5px;line-height:1.5}.qlv i{display:block;height:6px;border-radius:99px;background:rgba(var(--fgc),.08);overflow:hidden}.qlv i em{display:block;height:100%;background:var(--ac,#c8a96a)}
.qlvh{margin:22px 0 4px}`;

// Explanations for each lesson card's own question (the first quiz question), which app.js stores without one.
const CARD_WHY: Record<string, string> = {
 "mcap": "Market capitalization = share price × number of shares outstanding. It measures the company’s total value on the stock market, which the share price alone doesn’t show.",
 "markets": "After the IPO, investors trade shares with each other. Each trade happens at a price a buyer and a seller agree on, and those trades move the quoted price.",
 "earnings": "“Beating estimates” means the reported figure, usually EPS or revenue, came in above analysts’ average forecast (the consensus). It says nothing on its own about how the shares will react.",
 "portopt": "Diversification works when assets don’t move in step. The lower (or more negative) the correlation, the more a mix cuts volatility below the weighted average of the two.",

 "intro": "Investing puts money into things whose value can rise or fall. You accept some risk of loss in exchange for the chance of more growth than a savings account offers. Nothing about it is guaranteed.",
 "compound": "Compounding means returns earn their own returns, so growth speeds up the longer money stays invested. Time does the heavy lifting; fees work against it.",
 "rates": "When rates rise, new bonds pay more, so older bonds with lower payments are only worth buying at a lower price.",
 "divers": "Diversification only helps when holdings don’t all move together. Stocks that are highly correlated fall together, which sets a floor on how much risk you can remove.",
 "bond": "A 4% bond is less attractive than new 6% bonds, so its price falls below face value (a discount) until its yield matches the market’s.",
 "mc": "Volatility measures how widely returns swing. More of it spreads the possible outcomes further apart, both good and bad.",
 "dcf": "A DCF divides future cash by (1 + discount rate) for each year. A higher rate shrinks every future amount, so the value today is lower.",
 "stock": "A share is a fixed slice of the company. If the whole company is worth more, each slice usually is too, unless many new shares are issued.",
 "rule72": "The Rule of 72 estimates doubling time as 72 ÷ the yearly rate: 72 ÷ 8 = 9 years.",
 "dividend": "Dividend yield = yearly dividend per share ÷ share price. A $2 dividend on a $50 stock is a 4% yield.",
 "loan": "Spreading a loan over more years lowers each payment, but interest is charged for longer, so you pay more in total.",
 "pe": "P/E is the price paid per dollar of yearly profit. Investors usually pay more for each dollar of profit when they expect profits to grow fast.",
 "fees": "A fee is taken from your whole balance every year, so its effect compounds. Over decades it can take a large share of the final amount.",
 "drawdown": "After falling from $100 to $50, you need to double from $50 to get back to $100: a 100% gain.",
 "inflation": "Inflation raises prices over time. Cash that earns nothing keeps the same number of dollars, but each dollar buys less.",
 "income": "Net income is the bottom line of the income statement: revenue minus every cost, including interest and taxes. It isn’t the same as sales or cash.",
 "orders": "A limit order sets the most you’ll pay (or the least you’ll accept). It may not fill, but it never fills at a worse price.",
 "leverage": "Leverage multiplies your exposure. With 3× leverage, a 10% fall in the investment is roughly a 30% loss of your own money, before costs.",
 "sizing": "Position sizing caps what a single losing trade can cost: with 1% risk per trade, a loss at your stop costs 1% of the whole account.",
 "ratios": "Debt-to-equity = total debt ÷ shareholders’ equity. A value of 2 means the company owes twice what its shareholders own on the balance sheet.",
 "cashflow": "Free cash flow is cash from operations minus capital spending: the cash left after keeping and growing the business.",
 "peg": "PEG = P/E ÷ expected earnings growth (in %). Near 1, the price paid for profit is roughly in line with how fast profit is expected to grow. It is a rough guide, not a verdict.",
 "capm": "Beta measures how much a stock moves with the market. Above 1, it tends to move more than the market in both directions.",
 "rsi": "RSI above 70 is conventionally called overbought: prices rose quickly. It describes the recent past and doesn’t say the price must fall.",
 "options101": "At expiry a call is worth the stock price minus the strike. You only come out ahead once that value is more than the premium you paid.",
 "bs": "Delta is how much the option’s price changes for a $1 move in the stock. A delta of 0.5 means about 50 cents.",
 "sharpe": "Sharpe = (return − risk-free rate) ÷ volatility. A higher value means more return for each unit of risk taken, not simply more return.",
 "kelly": "Betting more than the Kelly size adds risk faster than it adds return. Far above it, long-run growth falls, and the chance of a ruinous loss rises.",
 "var": "A 95% one-day VaR of $1,000 means losses should be smaller than $1,000 on about 95% of days, so bigger on about 1 day in 20. It doesn’t say how much bigger.",
 "wacc": "Lenders are paid before shareholders, so they accept a lower return, and interest is usually tax-deductible. Both make debt cheaper than equity.",
 "duration": "Duration measures how sensitive a bond’s price is to interest rates. Higher duration means a bigger price change for the same change in yields.",
 "movingavg": "A 50-day moving average is the average of the last 50 closing prices, updated each day.",
 "macd": "MACD is a fast moving average (usually 12 days) minus a slow one (usually 26 days). It shows whether recent momentum is stronger than the longer trend.",
 "volume": "Volume counts shares traded. A move on unusually high volume involved many more buyers and sellers. It describes participation, not the next move.",
 "bollinger": "The bands sit a set number of standard deviations around an average. When prices swing more, the standard deviation rises and the bands widen.",
 "levels": "Resistance is a price a stock has repeatedly failed to rise above. It is a pattern in past prices, not a rule the price must follow.",
 "fib": "Fibonacci retracements are reference lines some traders watch. Nothing forces prices to react at them.",
 "riskreward": "Risk is $100 − $95 = $5; reward is $115 − $100 = $15. $15 ÷ $5 = 3, so the reward-to-risk ratio is 3 to 1.",
 "research": "Writing why you expect a company to do well, and what would prove you wrong, turns a hunch into a thesis you can test later."
};
// The quiz on the page is the lesson card's question followed by the lesson's own questions (same order as app.js).
const quizOf = (id: string): [string, string[], number, string][] => {
  const L = (typeof LES !== 'undefined' ? LES : []).find(l => l[0] === id), X = LX[id];
  if (!L) return []; return [[L[8][0], L[8][1], L[8][2], CARD_WHY[id] || ''], ...((X && X.q) || [])];
};
const STATE: Record<string, (number | null)[]> = {};

function enhance(id: string): void {
  const m = document.getElementById('m'), X = LX[id]; if (!m || !X) return;
  const L = (typeof LES !== 'undefined' ? LES : []).find(l => l[0] === id), Q = quizOf(id);
  if (!m.querySelector('.qlo') && X.k && X.k.length) {
    const anchor = m.querySelector('.lsteps') || m.querySelector('#lx1'); if (!anchor) return;
    const mins = Math.max(4, Math.min(15, Math.round(JSON.stringify(X).length / 700) + 3)), sc = quizScores()[id];
    anchor.insertAdjacentHTML('beforebegin', `<div class="qlo" role="note"><h3>What you’ll learn</h3><ul>${X.k.map(k => `<li>${k}</li>`).join('')}</ul><p class="meta">${L ? esc(L[3]) + ' · ' : ''}about ${mins} minutes · ${Q.length} quiz question${Q.length === 1 ? '' : 's'}, each explained${sc ? ` · your best score: ${sc.best} of ${sc.n}` : ''}</p></div>`);
  }
  const qz = m.querySelector<HTMLElement>('#lx3'); if (qz && qz.dataset.lesson !== id) { qz.dataset.lesson = id; STATE[id] = Q.map(() => null); }
}

function finish(id: string, qz: HTMLElement): void {
  const Q = quizOf(id), picked = STATE[id];
  const score = picked.filter((p, n) => p === Q[n][2]).length, all = quizScores(), prev = all[id];
  all[id] = { last: score, best: Math.max(score, prev ? prev.best : 0), n: Q.length, at: new Date().toISOString().slice(0, 10) };
  SS(QZKEY, all);
  if (typeof DONE !== 'undefined') { DONE.add(id); try { saveDone_(); } catch { /* older layer */ } }
  try { ACT_('lesson'); } catch { /* streak helper missing */ }
  const k = LES.findIndex(x => x[0] === id), nx = LES[k + 1];
  const msg = score === Q.length ? 'Every answer right.' : score >= Q.length - 1 ? 'Nearly there. Read the explanation for the one you missed.' : 'Read the explanations above, then try again.';
  const f = qz.querySelector<HTMLElement>('#qf');
  if (f) f.innerHTML = `<div class="qsum"><b class="sc">${score} of ${Q.length}</b><span>${msg}</span><span class="mu" style="font-size:12px">Lesson complete. ${(() => { try { return SAVENOTE(); } catch { return ''; } })()}</span><button type="button" class="btn" data-retry="1">Retry quiz</button>${nx ? `<a class="btn p" href="#/lesson/${nx[0]}">Next: ${esc(nx[1])} →</a>` : ''}</div>`;
  track('quiz_done_' + score + '_of_' + Q.length);
}

// One capturing listener: it runs before the buttons' own (older) handlers and stops them, whatever order the
// lesson page's layers attach them in.
function onClick(e: MouseEvent): void {
  const t = e.target as HTMLElement, qz = t.closest<HTMLElement>('#lx3'); if (!qz) return;
  const id = qz.dataset.lesson || decodeURIComponent(location.hash.split('/')[2] || ''); const Q = quizOf(id); if (!Q.length) return;
  if (!STATE[id] || STATE[id].length !== Q.length) STATE[id] = Q.map(() => null);
  if (t.closest('[data-retry]')) {
    e.stopPropagation(); STATE[id] = Q.map(() => null);
    qz.querySelectorAll<HTMLButtonElement>('.op').forEach(b => { b.disabled = false; b.classList.remove('ok', 'no'); });
    qz.querySelectorAll<HTMLElement>('[id^=qw]').forEach(w => { w.style.display = 'none'; w.innerHTML = ''; });
    const f = qz.querySelector<HTMLElement>('#qf'); if (f) f.innerHTML = ''; track('quiz_retry'); return;
  }
  const b = t.closest<HTMLButtonElement>('.op'); if (!b) return;
  e.stopPropagation(); e.preventDefault();
  const n = +(b.dataset.q || 0), i = +(b.dataset.i || 0), q = Q[n], picked = STATE[id]; if (!q || picked[n] != null) return;
  picked[n] = i; const ok = i === q[2];
  qz.querySelectorAll<HTMLButtonElement>(`.op[data-q="${n}"]`).forEach(x => { x.disabled = true; const j = +(x.dataset.i || 0); x.classList.toggle('ok', j === q[2]); x.classList.toggle('no', j === i && !ok); });
  const w = qz.querySelector<HTMLElement>('#qw' + n);
  if (w) { w.style.display = 'block'; w.innerHTML = ok ? `<span class="up">Correct.</span> ${q[3] || ''}` : `<span class="dn">Not quite.</span> The answer is “${q[1][q[2]]}”. ${q[3] || 'See “How it works” above for why.'}`; }
  const done = picked.filter(p => p != null).length, f = qz.querySelector<HTMLElement>('#qf');
  if (done === Q.length) finish(id, qz); else if (f) f.innerHTML = `<span class="mu">${done} of ${Q.length} answered</span>`;
}


const LEVELS: [string, string][] = [['Beginner', 'Stocks, markets, saving vs investing, dividends, reading an income statement'], ['Intermediate', 'Ratios, cash flow, valuation multiples, risk, indicators and options basics'], ['Advanced', 'DCF and WACC, Black-Scholes, Value at Risk, risk-adjusted returns, quantitative tools']];
function levelsBlock(): void {
  const m = document.getElementById('m'); if (!m || m.querySelector('.qlv')) return;
  const anchor = [...m.querySelectorAll('h3')].find(h => /Guided paths/.test(h.textContent || '')); if (!anchor) return;
  const all = (typeof LES !== 'undefined' ? LES : []), done = typeof DONE !== 'undefined' ? DONE : new Set<string>();
  anchor.insertAdjacentHTML('beforebegin', `<h3 class="qlvh">Learning levels</h3><div class="qlv">${LEVELS.map(([lv, what]) => {
    const ls = all.filter(l => l[3] === lv), d = ls.filter(l => done.has(l[0])).length, nx = ls.find(l => !done.has(l[0])) || ls[0];
    return nx ? `<a href="#/lesson/${nx[0]}"><small>${lv}</small><b>${d === ls.length ? 'All ' + ls.length + ' done. Review: ' + esc(nx[1]) : 'Next: ' + esc(nx[1])}</b><span>${what}</span><i role="img" aria-label="${d} of ${ls.length} ${lv.toLowerCase()} lessons done"><em style="width:${ls.length ? Math.round(d / ls.length * 100) : 0}%"></em></i><span>${d} of ${ls.length} done</span></a>` : '';
  }).join('')}</div>`);
}

export function initLearning(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qlo-css">${CSS}</style>`);
  if (!SYNC.includes(QZKEY)) SYNC.push(QZKEY);
  document.addEventListener('click', onClick, true);
  const learn = PAGES.learn;
  PAGES.learn = (a?: string) => { learn(a); levelsBlock(); setTimeout(levelsBlock, 80); };
  const base = PAGES.lesson;
  PAGES.lesson = (a?: string) => {
    base(a);
    const id = a || decodeURIComponent(location.hash.split('/')[2] || '');
    // the lesson page builds in layers; enhance once they've run
    enhance(id); setTimeout(() => enhance(id), 60); setTimeout(() => enhance(id), 400);
  };
}
