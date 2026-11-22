// Two experiences on one platform: Quantera Student and Quantera Professional.
//
// The choice is the existing q_mode setting ('simple' = Student, 'pro' = Professional), so every visitor's earlier
// choice carries over and it keeps syncing to their account. Nothing here stores a second copy of any data:
// both dashboards read the same lessons (LES, LX, DONE), paper portfolio (PTL, q_eq), watchlist (WL), research
// library (research/index.json) and market APIs as the rest of the site. The modes change the default view, not
// what anyone may open: every page stays one click away in both.
//
//   - First visit: a "Welcome to Quantera" chooser (Student or Professional, with a preview of each).
//   - #/dashboard: the dashboard for the chosen experience; #/dashboard/student and #/dashboard/professional
//     preview either one without changing the saved choice.
//   - Concept challenges: a short daily scenario with an explanation for every answer (education, not prediction).
//   - Homepage: a short "Two ways to use Quantera" section with a path into each.
import { library } from './library';
import { quizScores } from './learning';
import { prefs } from './prefs';

declare function MODE(): string | null;
declare function setMode_(m: string): void;
declare function PTL(): { cash: number; pos: Record<string, { q: number; cost: number; ccy?: string }>; hist: unknown[]; start: number };
declare function STATS(): { trades: number; holdings: number; lessons: number; streak: number; ret: number };
declare function DNAME(): string;
declare const WL: Set<string>;
declare const LX: Record<string, { k?: string[]; q?: [string, string[], number, string][] }>;
declare const QAUTH: { user: unknown } | undefined;

type Mode = 'student' | 'professional';
const asMode = (m: string | null): Mode | null => (m === 'simple' ? 'student' : m === 'pro' ? 'professional' : null);
const saved = (): Mode | null => asMode(typeof MODE === 'function' ? MODE() : LS<string | null>('q_mode', null));
const choose = (m: Mode) => { setMode_(m === 'student' ? 'simple' : 'pro'); track('experience_' + m); };

const CSS = `
.qx-ov{position:fixed;inset:0;z-index:60;background:rgba(4,6,10,.72);backdrop-filter:blur(6px);-webkit-backdrop-filter:blur(6px);display:grid;place-items:center;padding:16px;animation:qxf .25s ease}
html[data-theme=light] .qx-ov{background:rgba(240,242,246,.8)}
@keyframes qxf{from{opacity:0}to{opacity:1}}@keyframes qxu{from{opacity:0;transform:translateY(10px)}to{opacity:1;transform:none}}
.qx-box{width:min(880px,100%);max-height:calc(100vh - 32px);overflow:auto;background:var(--s1);border:1px solid var(--ln);border-radius:20px;padding:clamp(22px,3.4vw,40px);box-shadow:0 30px 80px rgba(0,0,0,.45);animation:qxu .35s ease}
.qx-box .brand{display:flex;align-items:center;gap:10px;font:500 13px/1 Manrope,sans-serif;letter-spacing:.32em;color:var(--mu)}
.qx-box h2{font:300 clamp(30px,4vw,46px)/1.08 Fraunces,Georgia,serif;letter-spacing:-.02em;margin:18px 0 6px;color:var(--tx)}
.qx-box .sub{color:var(--mu);font-size:16px;margin:0 0 22px}
.qx-two{display:grid;grid-template-columns:1fr 1fr;gap:14px}@media(max-width:700px){.qx-two{grid-template-columns:1fr}}
.qx-card{display:flex;flex-direction:column;gap:8px;text-align:left;border:1px solid var(--ln);border-radius:16px;padding:20px 22px;background:var(--s2,rgba(127,127,127,.04));color:var(--tx)}
.qx-card small{font-size:11.5px;letter-spacing:.16em;text-transform:uppercase;font-weight:700;color:var(--ac,#c8a96a)}
.qx-card b{font:400 clamp(21px,2.2vw,26px)/1.2 Fraunces,Georgia,serif}
.qx-card p{margin:0;color:var(--mu);font-size:14.5px;line-height:1.55}
.qx-card ul{margin:2px 0 6px;padding-left:18px;color:var(--mu);font-size:13.5px;line-height:1.65}
.qx-card .act{display:flex;gap:8px;flex-wrap:wrap;margin-top:auto;padding-top:6px}
.qx-card .act .btn{margin:0}
.qx-card.st{border-color:rgba(var(--acc),.35)}.qx-card.pr{border-color:rgba(122,166,232,.35)}
.qx-foot{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;align-items:center;margin-top:18px;font-size:13px;color:var(--mu)}
.qx-foot button{background:none;border:0;color:var(--mu);text-decoration:underline;cursor:pointer;font:inherit;padding:6px 0}
.qx-dash{max-width:1180px}
.qx-bar{display:flex;align-items:center;justify-content:space-between;gap:12px;flex-wrap:wrap;margin:0 0 18px}
.qx-bar h1{font:300 clamp(28px,3.4vw,42px)/1.1 Fraunces,Georgia,serif;margin:0;letter-spacing:-.02em}
.qx-bar .sub{color:var(--mu);margin:4px 0 0;font-size:15px}
.qx-seg{display:inline-flex;border:1px solid var(--ln);border-radius:99px;overflow:hidden}
.qx-seg a{padding:7px 14px;font-size:13px;color:var(--mu);text-decoration:none}.qx-seg a.on{background:rgba(var(--acc),.14);color:var(--tx)}
.qx-pv{display:flex;gap:10px;align-items:center;flex-wrap:wrap;border:1px dashed rgba(var(--acc),.5);border-radius:12px;padding:10px 14px;margin:0 0 16px;font-size:14px}
.qx-pv .btn{margin:0}
.qx-g{display:grid;grid-template-columns:repeat(12,minmax(0,1fr));gap:14px}
.qx-c{grid-column:span 4;border:1px solid var(--ln);border-radius:16px;padding:18px 20px;background:var(--s1);min-width:0}
.qx-c.w6{grid-column:span 6}.qx-c.w8{grid-column:span 8}.qx-c.w12{grid-column:span 12}
@media(max-width:1000px){.qx-c,.qx-c.w6,.qx-c.w8{grid-column:span 6}}@media(max-width:680px){.qx-c,.qx-c.w6,.qx-c.w8,.qx-c.w12{grid-column:span 12}}
.qx-c h3{margin:0 0 10px;font-size:13px;letter-spacing:.12em;text-transform:uppercase;color:var(--mu);font-weight:700}
.qx-c h4{margin:0 0 4px;font:400 20px/1.25 Fraunces,Georgia,serif}
.qx-c p{margin:0 0 8px;font-size:14.5px;line-height:1.6}.qx-c .mu{color:var(--mu)}
.qx-c .src{font-size:12px;color:var(--mu);margin-top:10px}
.qx-c a.lnk{display:inline-block;margin-top:6px;font-size:14px}
.qx-ls{list-style:none;margin:0;padding:0;display:grid;gap:8px}
.qx-ls a{display:flex;gap:10px;align-items:flex-start;border:1px solid var(--ln);border-radius:12px;padding:10px 12px;color:inherit;text-decoration:none}
.qx-ls a:hover{border-color:rgba(var(--acc),.5)}.qx-ls b{display:block;font-size:14.5px}.qx-ls small{color:var(--mu);font-size:12.5px}
.qx-lv{flex:none;font-size:10.5px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;border-radius:6px;padding:3px 6px;background:rgba(var(--acc),.14);color:var(--ac,#c8a96a)}
.qx-lv.Intermediate{background:rgba(122,166,232,.14);color:#7aa6e8}.qx-lv.Advanced{background:rgba(212,110,160,.14);color:#d46ea0}
.qx-stats{display:flex;gap:10px;flex-wrap:wrap}.qx-stats div{flex:1;min-width:84px;border-radius:12px;background:rgba(var(--fgc),.05);padding:10px 12px}
.qx-stats b{display:block;font-size:20px;font-variant-numeric:tabular-nums}.qx-stats small{color:var(--mu);font-size:12px}
.qx-meter{height:8px;border-radius:99px;background:rgba(var(--fgc),.08);overflow:hidden;margin:10px 0 6px}.qx-meter i{display:block;height:100%;background:var(--ac,#c8a96a);border-radius:99px}
.qx-q p.q{font-size:16px;font-weight:600;line-height:1.5}
.qx-q .tag{display:inline-block;font-size:11px;letter-spacing:.08em;text-transform:uppercase;color:var(--mu);margin-bottom:6px}
.qx-q button.op{display:block;width:100%;text-align:left;margin:8px 0 0;padding:11px 14px;border-radius:12px;border:1px solid var(--ln);background:transparent;color:var(--tx);font:inherit;font-size:14.5px;line-height:1.45;cursor:pointer}
.qx-q button.op:hover:not([disabled]){border-color:rgba(var(--acc),.6)}
.qx-q button.op[disabled]{cursor:default}.qx-q button.op.ok{border-color:var(--up);background:rgba(var(--upc,46,204,113),.08)}.qx-q button.op.no{border-color:var(--dn);background:rgba(var(--dnc,231,76,60),.08)}
.qx-q .why{font-size:13.5px;color:var(--mu);margin:6px 2px 0;line-height:1.5}
.qx-q .res{margin-top:12px;border-top:1px solid var(--ln);padding-top:12px;font-size:14.5px;line-height:1.6}
.qx-t{width:100%;border-collapse:collapse;font-size:13.5px;font-variant-numeric:tabular-nums}
.qx-t th{text-align:left;font-weight:600;color:var(--mu);font-size:12px;letter-spacing:.04em;padding:6px 8px;border-bottom:1px solid var(--ln)}
.qx-t td{padding:7px 8px;border-bottom:1px solid rgba(var(--fgc),.06)}.qx-t td.r,.qx-t th.r{text-align:right}
.qx-t a{color:inherit}.qx-t .up{color:var(--up)}.qx-t .dn{color:var(--dn)}
.qx-tw{overflow-x:auto}
.qx-news{list-style:none;margin:0;padding:0}.qx-news li{padding:9px 0;border-bottom:1px solid rgba(var(--fgc),.06);font-size:14px;line-height:1.45}
.qx-news small{display:block;color:var(--mu);font-size:12px;margin-top:2px}
.qx-skel{height:14px;border-radius:6px;background:rgba(var(--fgc),.07);margin:8px 0;animation:qxp 1.2s ease-in-out infinite}
@keyframes qxp{50%{opacity:.45}}
.qx-err{color:var(--mu);font-size:14px}
.qx-paths{margin:8px 0}.qx-start{margin:14px 0 0;font-size:14.5px;line-height:1.8;color:var(--mu)}.qx-start a{color:var(--tx)}.qx-paths>h2{margin:0}.qx-paths>.sub{margin:4px 0 16px;color:var(--mu)}
@media (prefers-reduced-motion:reduce){.qx-ov,.qx-box,.qx-skel{animation:none}}
`;

// ---------- concept challenges: hypothetical scenarios with an explanation for every answer ----------
interface Opt { t: string; ok?: boolean; why: string }
interface Cx { id: string; type: string; level?: 'Beginner' | 'Intermediate' | 'Advanced'; q: string; opts: Opt[]; concept: string; learn: string[]; explore?: [string, string] }
const CX: Cx[] = [
  { id: 'growth-fall', level: 'Intermediate', type: 'Earnings interpretation', q: 'A software company reports revenue 25% higher than a year earlier, but its shares fall 8% the next day. Which explanation fits best with how markets usually work?',
    opts: [{ t: 'Investors had expected even more, or the company lowered its forecast for the coming quarters', ok: true, why: 'Share prices already reflect what investors expect. A result can be good and still disappoint if expectations, or the outlook the company gives, were higher.' },
      { t: 'Revenue growth always makes a share price fall', why: 'There is no such rule. Prices react to how results compare with expectations, not to growth on its own.' },
      { t: 'Markets only look at revenue, so the fall must be a mistake', why: 'Investors also weigh profit, margins, cash flow and guidance. A fall is not automatically a mistake, and one day’s move rarely has a single cause.' }],
    concept: 'Prices move on results compared with expectations, and on the outlook, not on the headline number alone.', learn: ['research', 'pe'], explore: ['#/insights', 'See how stocks reacted to the same results'] },
  { id: 'pe-compare', level: 'Intermediate', type: 'Valuation', q: 'Company A trades at a P/E of 40. Company B, in the same industry, trades at 12. What can you conclude from this alone?',
    opts: [{ t: 'Investors pay more for each dollar of A’s current profit, usually because they expect it to grow faster', ok: true, why: 'P/E is price ÷ earnings per share. A higher P/E means a higher price per dollar of today’s profit, which usually reflects higher growth expectations.' },
      { t: 'A is overvalued and B is a bargain', why: 'P/E alone can’t tell you that. A may deserve its premium if it grows much faster; B may be cheap because its profits are shrinking.' },
      { t: 'B’s share price will rise', why: 'A low P/E is not a prediction. Stocks can stay cheap for years.' }],
    concept: 'P/E measures expectations, not value on its own. Compare it with growth (the PEG ratio) and with peers.', learn: ['pe', 'peg'], explore: ['#/compare', 'Compare two companies side by side'] },
  { id: 'profit-cash', level: 'Intermediate', type: 'Financial statements', q: 'A company’s net income rose 15% last year, but its free cash flow fell sharply. Which is a likely explanation?',
    opts: [{ t: 'Cash was tied up in unsold inventory or unpaid customer bills, or spent on new equipment', ok: true, why: 'Profit counts sales when they are made and spreads investment over years. Cash flow counts money when it actually moves, so the two can diverge.' },
      { t: 'The accounts must be wrong, because profit and cash are always equal', why: 'They are rarely equal. Depreciation, timing of payments and investment spending all separate them.' },
      { t: 'Free cash flow doesn’t matter if profit is rising', why: 'Analysts watch both. Rising profit with falling cash can be a warning sign worth investigating in the cash flow statement.' }],
    concept: 'Profit and cash are different. The cash flow statement shows where the money actually went.', learn: ['cashflow', 'income'], explore: ['#/asset/AAPL', 'Open a company’s cash flow statement'] },
  { id: 'mcap', level: 'Beginner', type: 'Company comparison', q: 'Company X: $10 a share, 2 billion shares. Company Y: $500 a share, 10 million shares. Which company is worth more on the stock market?',
    opts: [{ t: 'X: its market value is about $20 billion, against $5 billion for Y', ok: true, why: 'Market capitalization = share price × number of shares. $10 × 2 billion = $20 billion; $500 × 10 million = $5 billion.' },
      { t: 'Y, because its shares cost more', why: 'A high share price says nothing about size on its own. What matters is price × shares.' },
      { t: 'They are the same size', why: 'Work it out: $20 billion against $5 billion.' }],
    concept: 'Market capitalization (price × shares) measures size. The share price alone does not.', learn: ['stock'], explore: ['#/stocks', 'Browse companies by market value'] },
  { id: 'yield-jump', level: 'Intermediate', type: 'Risk assessment', q: 'A stock’s dividend yield rises from 3% to 9% in a month, but the company hasn’t changed its dividend. What happened?',
    opts: [{ t: 'The share price fell by about two-thirds', ok: true, why: 'Yield = yearly dividend ÷ share price. With the same dividend, yield triples when the price falls to a third. A very high yield can be a sign investors expect the dividend to be cut.' },
      { t: 'The company became three times more generous', why: 'The dividend didn’t change. Only the price moved.' },
      { t: 'It’s now a safer investment', why: 'Usually the opposite: a sudden jump in yield often reflects higher risk.' }],
    concept: 'Dividend yield moves with the share price. An unusually high yield deserves a closer look, not automatic trust.', learn: ['dividend'] },
  { id: 'divers', level: 'Beginner', type: 'Risk assessment', q: 'Portfolio 1 holds 10 technology stocks. Portfolio 2 holds 10 stocks across different industries. Which is likely to swing less?',
    opts: [{ t: 'Portfolio 2, because stocks in different industries tend to move less together', ok: true, why: 'Diversification works when holdings are not highly correlated. Ten stocks in one industry often fall together on the same news.' },
      { t: 'They swing the same, because both hold 10 stocks', why: 'The number of holdings matters less than how closely they move together.' },
      { t: 'Portfolio 1, because technology companies are bigger', why: 'Size doesn’t remove the shared risk of one industry.' }],
    concept: 'Diversification depends on correlation, not just the number of holdings.', learn: ['divers'], explore: ['#/portfolio', 'Check your paper portfolio’s spread'] },
  { id: 'rates-bonds', level: 'Intermediate', type: 'Market event interpretation', q: 'The central bank unexpectedly raises interest rates. What usually happens to the prices of existing bonds?',
    opts: [{ t: 'They fall, because new bonds now pay more, so older, lower-paying bonds are worth less', ok: true, why: 'Bond prices and yields move in opposite directions. Buyers will only accept an old bond’s lower payments at a lower price.' },
      { t: 'They rise, because bonds are safe', why: 'Bonds carry interest-rate risk. Unexpected rate rises usually push existing bond prices down.' },
      { t: 'Nothing, because bond payments are fixed', why: 'The payments are fixed, but the price investors will pay for them changes with interest rates.' }],
    concept: 'When rates rise, existing bond prices fall. Longer bonds fall more.', learn: ['rates', 'bond'], explore: ['#/macro', 'See today’s interest rates'] },
  { id: 'margins', level: 'Beginner', type: 'Company comparison', q: 'Two companies each have $1 billion in revenue. A earns $200 million in net income, B earns $50 million. What does this tell you?',
    opts: [{ t: 'A keeps 20 cents of each sales dollar as profit; B keeps 5 cents', ok: true, why: 'Net margin = net income ÷ revenue: $200M ÷ $1B = 20%, $50M ÷ $1B = 5%. Margins show how efficiently sales turn into profit.' },
      { t: 'They are equally profitable, because revenue is the same', why: 'Same sales, very different profit. Margins capture the difference.' },
      { t: 'B must be a worse investment', why: 'Not necessarily: margins differ a lot by industry, and B may be investing for growth. Compare with peers first.' }],
    concept: 'Profit margins show how much of each sales dollar becomes profit. Compare them within an industry.', learn: ['ratios', 'income'] },
  { id: 'leverage', level: 'Intermediate', type: 'Risk assessment', q: 'You invest using 2× leverage (half your own money, half borrowed), and the stock falls 30%. Roughly how much of your own money have you lost?',
    opts: [{ t: 'About 60%, before interest and fees', ok: true, why: 'Leverage multiplies the move: a 30% fall on twice your money is a loss equal to 60% of what you put in.' },
      { t: '30%, the same as the stock', why: 'Without leverage, yes. Borrowing doubles your exposure, so it doubles the loss.' },
      { t: '15%, because half the money was borrowed', why: 'Borrowed money still has to be repaid in full. The loss falls on your share.' }],
    concept: 'Leverage magnifies gains and losses in equal measure.', learn: ['leverage', 'sizing'] },
  { id: 'drawdown', level: 'Beginner', type: 'Risk assessment', q: 'An investment falls 50%. What gain does it need to get back to where it started?',
    opts: [{ t: '100%', ok: true, why: 'From $100 to $50 is −50%. Getting from $50 back to $100 means doubling: +100%.' },
      { t: '50%', why: '$50 plus 50% is only $75.' },
      { t: '25%', why: 'Far short: $50 plus 25% is $62.50.' }],
    concept: 'Losses need bigger gains to recover. That is why limiting large losses matters.', learn: ['drawdown', 'riskreward'] },
  { id: 'inflation', level: 'Beginner', type: 'Financial reasoning', q: 'Your savings earn 2% a year while inflation is 4%. What happens to what your money can buy?',
    opts: [{ t: 'It falls by about 2% a year', ok: true, why: 'The real return is roughly the interest rate minus inflation: 2% − 4% ≈ −2%. You have more dollars, but each buys less.' },
      { t: 'It grows 2% a year', why: 'That is the nominal return. Prices rising 4% more than offset it.' },
      { t: 'It stays the same, because savings are protected', why: 'Deposit insurance protects against a bank failing, not against inflation.' }],
    concept: 'The real return is the return after inflation.', learn: ['inflation', 'compound'] },
  { id: 'guidance', level: 'Intermediate', type: 'Earnings interpretation', q: 'A company beats analysts’ earnings forecasts for the quarter, but its shares fall after it cuts its forecast for next quarter. What does this show?',
    opts: [{ t: 'Investors care about expected future profit, so a weaker outlook can outweigh a strong past quarter', ok: true, why: 'A share price reflects expectations about the future. Guidance changes those expectations directly.' },
      { t: 'Beating forecasts is bad for a stock', why: 'Not in general. Here the outlook mattered more than the past quarter.' },
      { t: 'The market reacted to the wrong news', why: 'Markets can overreact, but weighing the future more than the past is normal, not a mistake.' }],
    concept: 'Guidance (the company’s own forecast) often moves a stock more than the quarter just reported.', learn: ['research'], explore: ['#/insights', 'Compare reactions to earnings news'] },
  { id: 'perpetuity', level: 'Advanced', type: 'Valuation exercise', q: 'A business produces $1 billion of free cash flow a year and is not expected to grow. If you require a 10% yearly return, roughly what is it worth today?',
    opts: [{ t: 'About $10 billion', ok: true, why: 'For a steady cash flow that lasts indefinitely, value ≈ cash flow ÷ required return: $1B ÷ 0.10 = $10B.' },
      { t: 'About $1 billion', why: 'That is one year of cash. The business keeps producing it year after year.' },
      { t: 'About $100 billion', why: 'That would mean accepting a 1% return, not 10%.' }],
    concept: 'Valuation turns future cash into today’s value using the return investors require. A DCF builds on the same idea.', learn: ['dcf', 'wacc'], explore: ['#/valuation/AAPL', 'Try a valuation model'] },
  { id: 'volume', level: 'Beginner', type: 'Market event interpretation', q: 'Stock A rises 6% on four times its usual trading volume. Stock B rises 6% on very low volume. What is the most reasonable difference?',
    opts: [{ t: 'A’s move involved many more buyers and sellers, so it reflects broader participation', ok: true, why: 'Volume shows how much trading sits behind a move. It describes participation, not where the price goes next.' },
      { t: 'A will keep rising and B will fall back', why: 'Volume isn’t a forecast. It helps you judge a move, not predict the next one.' },
      { t: 'There is no difference, because both rose 6%', why: 'The price change is the same, but the amount of trading behind it is very different.' }],
    concept: 'Volume measures participation. Compare it with the stock’s own normal volume.', learn: ['volume'] },
];

const dayKey = () => { const x = new Date(); return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`; };
const dayIndex = () => Math.floor(Date.now() / 864e5 - new Date().getTimezoneOffset() / 1440);
// One challenge a day. With a preferred level (Personalize), it comes from that level; Advanced also includes Intermediate.
const todaysCx = (): Cx => { const lv = prefs().level, pool = lv ? CX.filter(c => c.level === lv || (lv === 'Advanced' && c.level === 'Intermediate')) : CX, L = pool.length ? pool : CX; return L[((dayIndex() % L.length) + L.length) % L.length]; };
interface CxLog { [day: string]: { id: string; ok: boolean; i?: number } }
const CXKEY = 'q_cx';

const lessonTitle = (id: string): string => { const l = (typeof LES !== 'undefined' ? LES : []).find(x => x[0] === id); return l ? l[1] : id === 'intro' ? 'Introduction to investing' : id; };

function cxHtml(c: Cx, el: HTMLElement): void {
  const log = LS<CxLog>(CXKEY, {}), done = log[dayKey()] && log[dayKey()].id === c.id ? log[dayKey()] : null;
  el.innerHTML = `<div class="qx-q"><span class="tag">${esc(c.type)}${c.level ? ' · ' + esc(c.level) : ''} · hypothetical scenario</span><p class="q">${esc(c.q)}</p>
    ${c.opts.map((o, i) => `<button type="button" class="op" data-i="${i}">${esc(o.t)}</button>`).join('')}<div class="res" hidden></div>
    <p class="src">For learning: this is a made-up example, not a prediction or advice about any real stock.</p></div>`;
  const show = (pick: number) => {
    el.querySelectorAll<HTMLButtonElement>('button.op').forEach((b, i) => {
      const o = c.opts[i]; b.disabled = true; b.classList.toggle('ok', !!o.ok); b.classList.toggle('no', i === pick && !o.ok);
      b.insertAdjacentHTML('beforeend', `<div class="why">${o.ok ? 'Best answer. ' : ''}${esc(o.why)}</div>`);
    });
    const r = el.querySelector<HTMLElement>('.res')!; r.hidden = false;
    r.innerHTML = `<b>${c.opts[pick].ok ? 'Correct.' : 'Not quite, but now you know why.'}</b> ${esc(c.concept)}<br>Learn more: ${c.learn.map(id => `<a href="#/lesson/${id}">${esc(lessonTitle(id))}</a>`).join(' · ')}${c.explore ? ` · <a href="${c.explore[0]}">${esc(c.explore[1])}</a>` : ''}`;
  };
  if (done) { show(done.i != null && c.opts[done.i] ? done.i : done.ok ? Math.max(0, c.opts.findIndex(o => !!o.ok)) : Math.max(0, c.opts.findIndex(o => !o.ok))); return; }
  el.querySelectorAll<HTMLButtonElement>('button.op').forEach(b => b.onclick = () => {
    const i = +(b.dataset.i || 0), ok = !!c.opts[i].ok, l = LS<CxLog>(CXKEY, {});
    l[dayKey()] = { id: c.id, ok, i }; const keys = Object.keys(l).sort().slice(-120), trimmed: CxLog = {}; keys.forEach(k => trimmed[k] = l[k]);
    SS(CXKEY, trimmed); track('concept_challenge_' + (ok ? 'right' : 'wrong')); show(i);
  });
}

// ---------- shared helpers ----------
const pct = (v: number | null | undefined) => v == null || !isFinite(v) ? '—' : `${v >= 0 ? '+' : '−'}${fmt(Math.abs(v))}%`;
const cls = (v: number | null | undefined) => v == null ? '' : v >= 0 ? 'up' : 'dn';
const money = (v: number | null | undefined) => v == null || !isFinite(v) ? '—' : '$' + fmt(v, v >= 1000 ? 0 : 2);
const big = (v: number | null | undefined) => v == null || !isFinite(v) ? 'Not available' : v >= 1e12 ? `$${fmt(v / 1e12)}T` : v >= 1e9 ? `$${fmt(v / 1e9, 1)}B` : `$${fmt(v / 1e6, 0)}M`;
const when = (iso?: string) => { if (!iso) return ''; const d = new Date(iso.length === 10 ? iso + 'T12:00:00Z' : iso); return isNaN(+d) ? '' : d.toLocaleString('en-US', iso.length === 10 ? { month: 'short', day: 'numeric', year: 'numeric', timeZone: 'UTC' } : { month: 'short', day: 'numeric', hour: 'numeric', minute: '2-digit' }); };
const skel = (n = 3) => Array.from({ length: n }, (_, i) => `<div class="qx-skel" style="width:${92 - i * 14}%"></div>`).join('');
const fail = (what: string) => `<p class="qx-err">${esc(what)} couldn’t be loaded just now. <a href="javascript:void 0" onclick="route()">Try again</a></p>`;
async function getJSON<T>(u: string): Promise<T | null> { try { const r = await jf(u); return r.ok ? r.b as T : null; } catch { return null; } }
interface Quote { symbol: string; price: number; change?: number; changePct?: number | null; asOf?: string; time?: string; source?: string }
const INDICES: [string, string][] = [['^GSPC', 'S&P 500'], ['^NDX', 'Nasdaq 100'], ['^DJI', 'Dow Jones'], ['^RUT', 'Russell 2000']];
const FEATURED = ['AAPL', 'MSFT', 'NVDA', 'AMZN', 'GOOGL', 'JPM', 'KO', 'COST', 'NFLX', 'V', 'DIS', 'NKE', 'WMT', 'MCD'];

function lessonsInOrder(): { id: string; title: string; level: string; cat: string; desc: string }[] {
  const order: Record<string, number> = { Beginner: 0, Intermediate: 1, Advanced: 2 };
  const all = (typeof LES !== 'undefined' ? LES : []).map(l => ({ id: l[0], title: l[1], cat: l[2], level: l[3], desc: l[6] }));
  return all.sort((a, b) => (order[a.level] ?? 3) - (order[b.level] ?? 3));
}

// ---------- the chooser ----------
function chooser(): void {
  if (document.querySelector('.qx-ov')) return;
  const ov = document.createElement('div'); ov.className = 'qx-ov';
  ov.innerHTML = `<div class="qx-box" role="dialog" aria-modal="true" aria-labelledby="qx-h">
    <div class="brand"><img src="/logo-mark.png" alt="" width="26" height="26">QUANTERA</div>
    <h2 id="qx-h">Welcome to Quantera.</h2><p class="sub">Choose how you want to explore financial markets.</p>
    <div class="qx-two">
      <div class="qx-card st"><small>Student</small><b>Learn, explore and practice.</b><p>Explore financial markets through interactive lessons, research explanations, quizzes and simulated investing.</p>
        <ul><li>Lessons from beginner to advanced, with quizzes</li><li>A daily challenge and your learning progress</li><li>Plain-English explanations next to the numbers</li><li>Paper trading with $100,000 of virtual money</li></ul>
        <div class="act"><button class="btn p" data-pick="student">Choose Student</button><button class="btn" data-pv="student">Preview</button></div></div>
      <div class="qx-card pr"><small>Professional</small><b>Research, analyze and investigate.</b><p>Explore detailed financial research, company fundamentals, market intelligence and analytical tools.</p>
        <ul><li>Research studies with methods and sources</li><li>Market, watchlist and economic data tables</li><li>SEC financial statements and valuation tools</li><li>Every chart indicator and order type</li></ul>
        <div class="act"><button class="btn p" data-pick="professional">Choose Professional</button><button class="btn" data-pv="professional">Preview</button></div></div>
    </div>
    <div class="qx-foot"><span>Both use the same data and research. You can switch at any time at the top of the page or in Settings.</span><button type="button" data-x="later">Decide later</button></div></div>`;
  document.body.appendChild(ov);
  const prev = document.activeElement as HTMLElement | null;
  const close = (remember = true) => { ov.remove(); removeEventListener('keydown', key, true); if (remember && !saved()) SS('q_mnd', Math.max(1, LS<number>('q_mnd', 0))); prev?.focus?.(); };
  const key = (e: KeyboardEvent) => {
    if (e.key === 'Escape') { close(); track('experience_later'); }
    if (e.key === 'Tab') { const f = [...ov.querySelectorAll<HTMLElement>('button')]; if (!f.length) return; const i = f.indexOf(document.activeElement as HTMLElement);
      if (e.shiftKey && i <= 0) { e.preventDefault(); f[f.length - 1].focus(); } else if (!e.shiftKey && i === f.length - 1) { e.preventDefault(); f[0].focus(); } }
  };
  addEventListener('keydown', key, true);
  ov.addEventListener('click', e => {
    const t = e.target as HTMLElement, pick = t.closest<HTMLElement>('[data-pick]'), pv = t.closest<HTMLElement>('[data-pv]');
    if (pick) { close(false); choose(pick.dataset.pick as Mode); go('#/dashboard'); }
    else if (pv) { close(false); track('experience_preview_' + pv.dataset.pv); go('#/dashboard/' + pv.dataset.pv); }
    else if (t.closest('[data-x=later]') || t === ov) { close(); track('experience_later'); }
  });
  ov.querySelector<HTMLElement>('[data-pick=student]')?.focus();
}
(window as unknown as { QX_CHOOSE: () => void }).QX_CHOOSE = chooser;

// ---------- Student dashboard ----------
function student(m: HTMLElement): void {
  const S = (() => { try { return STATS(); } catch { return null; } })();
  const done = typeof DONE !== 'undefined' ? DONE : new Set<string>();
  const lessons = lessonsInOrder(), total = lessons.length, nDone = lessons.filter(l => done.has(l.id)).length;
  // recommendations follow the Personalize choices: the chosen level first, favorite topics first within it
  const pf = prefs(), order = ['Beginner', 'Intermediate', 'Advanced'], start = pf.level ? order.indexOf(pf.level) : 0, topics = new Set(pf.topics || []);
  const next = lessons.filter(l => !done.has(l.id)).map(l => ({ l, s: (order.indexOf(l.level) >= start ? 0 : 10) + Math.abs(order.indexOf(l.level) - start) * 2 + (topics.size && !topics.has(l.cat) ? 1 : 0) }))
    .sort((a, b) => a.s - b.s).slice(0, 3).map(x => x.l);
  const daily = LS<{ day?: string; done?: boolean; total?: number; days?: number }>('q_daily', {});
  const cxLog = LS<CxLog>(CXKEY, {}), cxN = Object.keys(cxLog).length, cxOk = Object.values(cxLog).filter(x => x.ok).length;
  const qs = quizScores(), qk = Object.keys(qs), qAvg = qk.length ? Math.round(qk.reduce((t, k) => t + qs[k].best / Math.max(1, qs[k].n), 0) / qk.length * 100) : null;
  const retry = qk.filter(k => qs[k].best < qs[k].n).sort((a, b) => qs[a].best / qs[a].n - qs[b].best / qs[b].n).slice(0, 3);
  const name = (() => { try { const n = DNAME(); return n && !/^(guest|you)$/i.test(n) ? n.split(' ')[0] : ''; } catch { return ''; } })();
  const quizLesson = lessons.find(l => !done.has(l.id) && LX && LX[l.id] && LX[l.id].q && LX[l.id].q!.length) || lessons.find(l => LX && LX[l.id] && LX[l.id].q);
  const steps: [string, string][] = [];
  if (!done.has('intro')) steps.push(['#/lesson/intro', 'Start with the Introduction to investing (about 10 minutes)']);
  if (!S || S.trades < 1) steps.push(['#/trade', 'Place your first paper trade with virtual money']);
  if (!LS<string[]>('q_rread', []).length) steps.push(['#/research', 'Read a research study and see how it uses sources']);
  steps.push(['#/insights', 'Compare how different companies reacted to the same news']);
  if (nDone >= 5) steps.push(['#/statex', 'Answer analyst questions from a real company’s statements']);
  m.innerHTML = `<div class="qx-dash">
   <div class="qx-bar"><div><h1>${name ? 'Welcome back, ' + esc(name) + '.' : 'Welcome to Quantera Student.'}</h1><p class="sub">What should you learn or investigate next? Start here.</p></div>${seg('student')}</div>
   <div class="qx-g">
    <section class="qx-c w8" aria-labelledby="qx-next"><h3 id="qx-next">Recommended lessons</h3>${next.length ? `<ul class="qx-ls">${next.map(l => `<li><a href="#/lesson/${esc(l.id)}"><span class="qx-lv ${esc(l.level)}">${esc(l.level)}</span><span><b>${esc(l.title)}</b><small>${esc(l.desc)}</small></span></a></li>`).join('')}</ul>` : '<p>You’ve completed every lesson. Try the Professional dashboard, or revisit a lesson’s quiz.</p>'}
     <a class="lnk" href="#/learn">See all ${total} lessons by level →</a></section>
    <section class="qx-c" aria-labelledby="qx-prog"><h3 id="qx-prog">Your progress</h3><div class="qx-meter" role="img" aria-label="${nDone} of ${total} lessons completed"><i style="width:${total ? Math.round(nDone / total * 100) : 0}%"></i></div>
     <p><b>${nDone} of ${total}</b> lessons completed</p>
     <div class="qx-stats"><div><b>${S ? S.streak : 0}</b><small>day streak</small></div><div><b>${daily.total || 0}</b><small>challenge XP</small></div><div><b>${cxN ? cxOk + '/' + cxN : '0'}</b><small>concept challenges right</small></div><div><b>${qAvg == null ? '—' : qAvg + '%'}</b><small>quiz average${qk.length ? ` (${qk.length} quiz${qk.length === 1 ? '' : 'zes'})` : ''}</small></div></div>
     <p class="src">${typeof QAUTH !== 'undefined' && QAUTH && QAUTH.user ? 'Saved to your account.' : 'Saved on this device. <a href="#/settings">Create a free account</a> to keep it everywhere.'}</p></section>
    <section class="qx-c w8" aria-labelledby="qx-cx"><h3 id="qx-cx">Today’s concept challenge</h3><div id="qx-cxb"></div></section>
    <section class="qx-c" aria-labelledby="qx-dn"><h3 id="qx-dn">Daily news challenge</h3><p>${daily.day === dayKey() && daily.done ? 'Done for today. Come back tomorrow for a new story.' : 'Read one real news story, decide how it might affect the company, then see what the stock actually did.'}</p><a class="btn p" href="#/learn">${daily.day === dayKey() && daily.done ? 'Review it' : 'Start the news challenge'}</a>
     <h3 style="margin-top:18px">Quick quiz</h3><div id="qx-qz"></div>${retry.length ? `<h3 style="margin-top:18px">Quizzes to retry</h3><ul class="qx-ls">${retry.map(k => `<li><a href="#/lesson/${esc(k)}"><span><b>${esc(lessonTitle(k))}</b><small>Best score ${qs[k].best} of ${qs[k].n}</small></span></a></li>`).join('')}</ul>` : ''}</section>
    <section class="qx-c w6" aria-labelledby="qx-mk"><h3 id="qx-mk">Markets in plain English</h3><div id="qx-mkb">${skel()}</div></section>
    <section class="qx-c w6" aria-labelledby="qx-fc"><h3 id="qx-fc">Featured company</h3><div id="qx-fcb">${skel()}</div></section>
    <section class="qx-c" aria-labelledby="qx-pf"><h3 id="qx-pf">Your paper portfolio</h3><div id="qx-pfb"></div></section>
    <section class="qx-c" aria-labelledby="qx-rr"><h3 id="qx-rr">Research for you</h3><div id="qx-rrb">${skel(2)}</div></section>
    <section class="qx-c" aria-labelledby="qx-ns"><h3 id="qx-ns">Suggested next steps</h3><ul class="qx-ls">${steps.slice(0, 4).map(s => `<li><a href="${s[0]}"><span><b>${esc(s[1])}</b></span></a></li>`).join('')}</ul></section>
   </div></div>`;
  cxHtml(todaysCx(), m.querySelector<HTMLElement>('#qx-cxb')!);
  quiz(m.querySelector<HTMLElement>('#qx-qz')!, quizLesson ? quizLesson.id : null);
  portfolio(m.querySelector<HTMLElement>('#qx-pfb')!, false);
  plainMarkets(m.querySelector<HTMLElement>('#qx-mkb')!);
  featured(m.querySelector<HTMLElement>('#qx-fcb')!);
  research(m.querySelector<HTMLElement>('#qx-rrb')!, 'student');
}

function quiz(el: HTMLElement, id: string | null): void {
  const q = id && LX[id] && LX[id].q ? LX[id].q![0] : null;
  if (!q) { el.innerHTML = '<p class="mu">Every lesson ends with a short quiz.</p>'; return; }
  el.innerHTML = `<div class="qx-q"><span class="tag">From “${esc(lessonTitle(id!))}”</span><p class="q" style="font-size:14.5px">${q[0]}</p>${q[1].map((o, i) => `<button type="button" class="op" data-i="${i}">${o}</button>`).join('')}<div class="res" hidden></div></div>`;
  el.querySelectorAll<HTMLButtonElement>('button.op').forEach(b => b.onclick = () => {
    const i = +(b.dataset.i || 0);
    el.querySelectorAll<HTMLButtonElement>('button.op').forEach((x, j) => { x.disabled = true; x.classList.toggle('ok', j === q[2]); x.classList.toggle('no', j === i && i !== q[2]); });
    const r = el.querySelector<HTMLElement>('.res')!; r.hidden = false;
    r.innerHTML = `<b>${i === q[2] ? 'Correct.' : 'Not quite.'}</b> ${q[3]} <a href="#/lesson/${esc(id!)}">Take the full lesson →</a>`;
    track('dash_quiz_' + (i === q[2] ? 'right' : 'wrong'));
  });
}

function portfolio(el: HTMLElement, pro: boolean): void {
  let A: ReturnType<typeof PTL> | null = null; try { A = PTL(); } catch { /* not ready */ }
  if (!A) { el.innerHTML = '<p class="mu">Your paper portfolio appears here.</p>'; return; }
  const eq = LS<{ t?: number; v: number }[]>('q_eq', []), last = eq.length ? eq[eq.length - 1] : null;
  const value = last ? last.v : A.start, ret = (value / A.start - 1) * 100, n = Object.keys(A.pos).length;
  el.innerHTML = `<div class="qx-stats"><div><b>${money(value)}</b><small>value${last && last.t ? ', ' + when(new Date(last.t).toISOString()) : ''}</small></div><div><b class="${cls(ret)}">${pct(ret)}</b><small>since you started</small></div></div>
    <p style="margin-top:10px">${money(A.cash)} cash · ${n} holding${n === 1 ? '' : 's'} · ${A.hist.length} trade${A.hist.length === 1 ? '' : 's'}</p>
    <p class="src">Paper trading: virtual money only. Nothing real is bought or sold.</p>
    ${pro ? '' : '<p class="mu" style="font-size:13.5px">Your return compares the portfolio with the $' + fmt(A.start, 0) + ' you started with. It includes cash you haven’t invested, which earns nothing here.</p>'}
    <a class="lnk" href="#/portfolio">Open portfolio →</a> · <a class="lnk" href="#/trade">Trade →</a>`;
}

async function plainMarkets(el: HTMLElement): Promise<void> {
  const d = await getJSON<{ quotes: Quote[] }>('/api/quotes?symbols=' + encodeURIComponent(INDICES.map(x => x[0]).join(',')));
  const q = d && d.quotes ? d.quotes : [];
  const sp = q.find(x => x.symbol === '^GSPC'), nd = q.find(x => x.symbol === '^NDX');
  if (!sp) { el.innerHTML = fail('Market data'); return; }
  const dir = (v?: number | null) => v == null ? 'was unchanged' : v >= 0 ? `rose ${fmt(Math.abs(v))}%` : `fell ${fmt(Math.abs(v))}%`;
  el.innerHTML = `<p>The <b>S&amp;P 500</b>, a basket of about 500 of the largest US companies, ${dir(sp.changePct)} in its latest session${nd ? `, and the <b>Nasdaq 100</b>, which leans toward technology companies, ${dir(nd.changePct)}` : ''}.</p>
    <p class="mu" style="font-size:13.5px">A move of 1% or more in a day is large for a whole market. Indexes show the average; individual stocks can move very differently.</p>
    <p class="src">${esc(sp.source || 'Market data')}${sp.time ? ' · ' + when(sp.time) : ''} · may be delayed</p><a class="lnk" href="#/markets">See all markets →</a> · <a class="lnk" href="#/lesson/stock">What is a stock? →</a>`;
}

async function featured(el: HTMLElement): Promise<void> {
  const sym = FEATURED[((dayIndex() % FEATURED.length) + FEATURED.length) % FEATURED.length];
  const [q, o] = await Promise.all([getJSON<Quote>('/api/quote?symbol=' + sym), getJSON<{ name?: string; sector?: string; industry?: string; marketCap?: number | null }>('/api/overview?symbol=' + sym)]);
  if (!q) { el.innerHTML = fail('Today’s featured company'); return; }
  el.innerHTML = `<h4>${esc(o && o.name || sym)} <span class="mu" style="font:500 13px Manrope,sans-serif">${esc(sym)}</span></h4>
    <p class="mu">${esc([o && o.sector, o && o.industry !== o.sector ? o.industry : ''].filter(Boolean).join(' · ') || 'Industry not available')}</p>
    <div class="qx-stats"><div><b>${money(q.price)}</b><small>share price</small></div><div><b class="${cls(q.changePct)}">${pct(q.changePct)}</b><small>latest day</small></div><div><b>${big(o && o.marketCap)}</b><small>market value</small></div></div>
    <p style="margin-top:12px"><b>Three questions to investigate</b></p><ol style="margin:0;padding-left:20px;font-size:14px;line-height:1.7"><li>How does it make money? <a href="#/asset/${sym}">Overview</a></li><li>Is its revenue growing? <a href="#/asset/${sym}">Financials</a> · <a href="#/lesson/income">lesson</a></li><li>What do investors pay for its profit? <a href="#/lesson/pe">The P/E ratio</a></li></ol>
    <p class="src">Price: ${esc(q.source || 'market data')}${q.time ? ' · ' + when(q.time) : ''} · may be delayed. Featured for learning, not as a recommendation.</p>`;
}

async function research(el: HTMLElement, who: Mode): Promise<void> {
  let lib; try { lib = await library(); } catch { lib = null; }
  if (!lib) { el.innerHTML = fail('The research library'); return; }
  const read = LS<string[]>('q_rread', []);
  const arts = [...lib.articles] as (typeof lib.articles[number] & { updated?: string; classification?: string })[];
  if (who === 'student') {
    const recent = read.map(s => arts.find(a => a.slug === s)).filter(Boolean).slice(0, 2) as typeof arts;
    const pick = recent.length ? recent : arts.slice(0, 2);
    el.innerHTML = `<p class="mu" style="font-size:13.5px">${recent.length ? 'Recently viewed' : 'Start with a study'}</p><ul class="qx-ls">${pick.map(a => `<li><a href="#/research/${esc(a.slug)}"><span><b>${esc(a.title)}</b><small>${esc(a.category)} · ${when(a.date)}${a.updated ? ' · updated ' + when(a.updated) : ''} · ${a.readMinutes || 5} min</small></span></a></li>`).join('')}</ul><a class="lnk" href="#/research">All research →</a>`;
    return;
  }
  const sorted = arts.sort((a, b) => (b.updated || b.date).localeCompare(a.updated || a.date));
  el.innerHTML = `<div class="qx-tw"><table class="qx-t"><thead><tr><th>Study</th><th>Type</th><th>Published</th><th>Updated</th><th class="r">Read</th></tr></thead><tbody>${sorted.map(a => `<tr><td><a href="#/research/${esc(a.slug)}">${esc(a.title)}</a>${read.includes(a.slug) ? ' <small class="mu">· read</small>' : ''}</td><td>${esc(a.classification || a.category)}</td><td>${when(a.date)}</td><td>${a.updated ? when(a.updated) : '—'}</td><td class="r">${a.readMinutes || 5} min</td></tr>`).join('')}</tbody></table></div><a class="lnk" href="#/research">Research library →</a>`;
}

// ---------- Professional dashboard ----------
function professional(m: HTMLElement): void {
  const wl = typeof WL !== 'undefined' ? [...WL].slice(0, 12) : [];
  m.innerHTML = `<div class="qx-dash">
   <div class="qx-bar"><div><h1>Research desk</h1><p class="sub">What is happening in the markets, what matters, and where to investigate further.</p></div>${seg('professional')}</div>
   <div class="qx-g">
    <section class="qx-c w8" aria-labelledby="qx-rs"><h3 id="qx-rs">Research and updates</h3><div id="qx-rsb">${skel()}</div></section>
    <section class="qx-c" aria-labelledby="qx-in"><h3 id="qx-in">Insights</h3><p>Measure how differently companies reacted to the same news: each move split into the market’s part and the company-specific part, compared with the stock’s normal day.</p>
     <p class="mu" style="font-size:13.5px">Daily closing prices; beta against the S&P 500 from the 120 trading days before the news; one event per company, no significance test. It shows association, not cause.</p><a class="btn p" href="#/insights">Open Insights</a> <a class="lnk" href="/insights">Methodology →</a></section>
    <section class="qx-c w6" aria-labelledby="qx-mi"><h3 id="qx-mi">Market intelligence</h3><div id="qx-mib">${skel(4)}</div></section>
    <section class="qx-c w6" aria-labelledby="qx-wl"><h3 id="qx-wl">Watchlist</h3><div id="qx-wlb">${wl.length ? skel(4) : '<p class="mu">Add companies to your watchlist from any stock page.</p>'}</div></section>
    <section class="qx-c w6" aria-labelledby="qx-mc"><h3 id="qx-mc">Economic indicators</h3><div id="qx-mcb">${skel(4)}</div></section>
    <section class="qx-c w6" aria-labelledby="qx-nw"><h3 id="qx-nw">Recent developments${wl.length ? ': your watchlist' : ''}</h3><div id="qx-nwb">${skel(4)}</div></section>
    <section class="qx-c w8" aria-labelledby="qx-tl"><h3 id="qx-tl">Analysis tools</h3><div class="qx-tw"><table class="qx-t"><tbody>
     ${[['#/stocks', 'Company research', 'SEC financial statements, valuation in context, earnings history, competitors'], ['#/screener', 'Screener', 'Filter US stocks by size, valuation, growth and profitability'], ['#/compare', 'Compare', 'Two or more companies side by side'], ['#/valuation/AAPL', 'Valuation', 'Discounted cash flow model with editable assumptions'], ['#/statex', 'Statement exercises', 'Analyst questions answered from SEC filings'], ['#/private', 'Private companies', 'Estimated valuations and funding rounds (estimates, clearly labeled)']].map(t => `<tr><td><a href="${t[0]}">${t[1]}</a></td><td class="mu">${t[2]}</td></tr>`).join('')}
     </tbody></table></div></section>
    <section class="qx-c" aria-labelledby="qx-pp"><h3 id="qx-pp">Paper portfolio</h3><div id="qx-ppb"></div></section>
   </div>
   <p class="src" style="margin-top:14px">Prices from Finnhub and Yahoo Finance and may be delayed up to 15 minutes; economic data from the Federal Reserve Bank of St. Louis (FRED); financial statements from SEC EDGAR. For research and education, not investment advice.</p></div>`;
  research(m.querySelector<HTMLElement>('#qx-rsb')!, 'professional');
  intel(m.querySelector<HTMLElement>('#qx-mib')!);
  if (wl.length) watch(m.querySelector<HTMLElement>('#qx-wlb')!, wl);
  macro(m.querySelector<HTMLElement>('#qx-mcb')!);
  news(m.querySelector<HTMLElement>('#qx-nwb')!, wl);
  portfolio(m.querySelector<HTMLElement>('#qx-ppb')!, true);
}

function quoteRows(q: Quote[], names: Record<string, string>): string {
  return `<div class="qx-tw"><table class="qx-t"><thead><tr><th>Name</th><th class="r">Last</th><th class="r">Change</th><th class="r">%</th><th class="r">As of</th></tr></thead><tbody>${q.map(x => `<tr><td><a href="#/asset/${encodeURIComponent(x.symbol)}">${esc(names[x.symbol] || x.symbol)}</a></td><td class="r">${fmt(x.price)}</td><td class="r ${cls(x.change)}">${x.change == null ? '—' : (x.change >= 0 ? '+' : '−') + fmt(Math.abs(x.change))}</td><td class="r ${cls(x.changePct)}">${pct(x.changePct)}</td><td class="r mu">${when(x.time || x.asOf)}</td></tr>`).join('')}</tbody></table></div>`;
}

async function intel(el: HTMLElement): Promise<void> {
  const extra: [string, string][] = [['^TNX', '10-year Treasury yield'], ['GC=F', 'Gold'], ['CL=F', 'Crude oil (WTI)'], ['BTC-USD', 'Bitcoin'], ['^VIX', 'Volatility (VIX)']];
  const all = [...INDICES, ...extra], names = Object.fromEntries(all);
  const d = await getJSON<{ quotes: Quote[]; missing?: string[] }>('/api/quotes?symbols=' + encodeURIComponent(all.map(x => x[0]).join(',')));
  if (!d || !d.quotes || !d.quotes.length) { el.innerHTML = fail('Market data'); return; }
  const order = all.map(x => x[0]), q = d.quotes.filter(x => x.price != null).sort((a, b) => order.indexOf(a.symbol) - order.indexOf(b.symbol));
  el.innerHTML = quoteRows(q, names) + `<p class="src">${esc([...new Set(q.map(x => x.source).filter(Boolean))].join(', '))} · may be delayed${d.missing && d.missing.length ? ' · not available: ' + esc(d.missing.map(s => names[s] || s).join(', ')) : ''}</p><a class="lnk" href="#/markets">Markets →</a> · <a class="lnk" href="#/world">World and commodities →</a>`;
}

async function watch(el: HTMLElement, wl: string[]): Promise<void> {
  const d = await getJSON<{ quotes: Quote[]; missing?: string[] }>('/api/quotes?symbols=' + encodeURIComponent(wl.join(',')));
  if (!d || !d.quotes) { el.innerHTML = fail('Your watchlist'); return; }
  el.innerHTML = quoteRows(d.quotes, {}) + `<p class="src">${esc([...new Set(d.quotes.map(x => x.source).filter(Boolean))].join(', '))} · may be delayed${d.missing && d.missing.length ? ' · not available: ' + esc(d.missing.join(', ')) : ''}</p><a class="lnk" href="#/trade">Edit watchlist →</a>`;
}

async function macro(el: HTMLElement): Promise<void> {
  const d = await getJSON<{ items: { id: string; name: string; unit: string; value: number; prev: number; date: string }[]; source?: string }>('/api/macro');
  if (!d || !d.items) { el.innerHTML = fail('Economic data'); return; }
  const pick = ['CPIAUCSL', 'CPILFESL', 'UNRATE', 'PAYEMS', 'A191RL1Q225SBEA', 'DFF', 'DGS2', 'DGS10', 'T10Y2Y', 'MORTGAGE30US'];
  const rows = pick.map(id => d.items.find(i => i.id === id)).filter(Boolean) as typeof d.items;
  const u = (v: number, unit: string) => unit === '%' ? fmt(v) + '%' : unit === 'k' ? fmt(v, 0) + 'k' : unit === 'pts' ? fmt(v) + ' pts' : fmt(v);
  el.innerHTML = `<div class="qx-tw"><table class="qx-t"><thead><tr><th>Indicator</th><th class="r">Latest</th><th class="r">Previous</th><th class="r">Period</th></tr></thead><tbody>${rows.map(r => `<tr><td>${esc(r.name)}</td><td class="r">${u(r.value, r.unit)}</td><td class="r mu">${r.prev == null ? '—' : u(r.prev, r.unit)}</td><td class="r mu">${when(r.date)}</td></tr>`).join('')}</tbody></table></div><p class="src">${esc(d.source || 'FRED')}. Inflation figures are year-over-year changes.</p><a class="lnk" href="#/macro">All economic data →</a>`;
}

async function news(el: HTMLElement, wl: string[]): Promise<void> {
  const d = await getJSON<{ articles: { id?: string; title: string; source?: string; url?: string; publishedAt?: string; symbols?: { symbol: string }[] }[] }>('/api/news?limit=8' + (wl.length ? '&symbols=' + encodeURIComponent(wl.slice(0, 8).join(',')) : ''));
  if (!d || !d.articles) { el.innerHTML = fail('News'); return; }
  if (!d.articles.length) { el.innerHTML = '<p class="mu">No recent stories for these companies.</p>'; return; }
  el.innerHTML = `<ul class="qx-news">${d.articles.slice(0, 7).map(a => `<li><a href="${a.id ? '#/story/' + encodeURIComponent(a.id) : esc(a.url || '#')}"${a.id ? '' : ' target="_blank" rel="noopener noreferrer"'}>${esc(a.title)}</a><small>${esc(a.source || '')}${a.publishedAt ? ' · ' + when(a.publishedAt) : ''}${a.symbols && a.symbols.length ? ' · ' + esc(a.symbols.slice(0, 3).map(s => s.symbol).join(', ')) : ''}</small></li>`).join('')}</ul><p class="src">External reporting, linked to the original publisher. Headlines are not Quantera research.</p><a class="lnk" href="#/news">All news →</a>`;
}

// ---------- the dashboard route ----------
function seg(active: Mode): string {
  return `<div class="qx-seg" role="navigation" aria-label="Choose a dashboard"><a href="#/dashboard/student" class="${active === 'student' ? 'on' : ''}" aria-current="${active === 'student' ? 'page' : 'false'}">Student</a><a href="#/dashboard/professional" class="${active === 'professional' ? 'on' : ''}" aria-current="${active === 'professional' ? 'page' : 'false'}">Professional</a></div>`;
}

function dashboard(arg?: string): void {
  const m = document.getElementById('m'); if (!m) return;
  const asked = arg === 'student' || arg === 'professional' ? arg as Mode : null;
  const mine = saved(), view: Mode = asked || mine || 'student';
  if (view === 'student') student(m); else professional(m);
  const host = m.querySelector('.qx-dash');
  if (host && view !== mine) {
    host.insertAdjacentHTML('afterbegin', `<div class="qx-pv" role="status"><span>${mine ? `You’re previewing the ${view === 'student' ? 'Student' : 'Professional'} dashboard. Your experience is ${mine === 'student' ? 'Student' : 'Professional'}.` : `You’re previewing the ${view === 'student' ? 'Student' : 'Professional'} experience.`}</span><button class="btn p" data-use="${view}">Use ${view === 'student' ? 'Student' : 'Professional'}</button>${mine ? '' : '<button class="btn" data-ch="1">Compare both</button>'}</div>`);
    host.querySelector<HTMLElement>('[data-use]')?.addEventListener('click', () => { choose(view); go('#/dashboard'); });
    host.querySelector<HTMLElement>('[data-ch]')?.addEventListener('click', chooser);
  }
}

// ---------- Insights in Student mode: the method in plain English first (Professional keeps the full method panel) ----------
function insightsIntro(): void {
  if (saved() !== 'student' || !/^#\/insights/.test(location.hash)) return;
  const m = document.getElementById('m'); if (!m || m.querySelector('.qx-ins')) return;
  const host = m.querySelector('h1, h2'); if (!host) return;
  host.insertAdjacentHTML('afterend', `<div class="qx-c qx-ins" role="note" style="margin:10px 0 18px;max-width:860px"><h3>In plain English</h3>
    <p>When news comes out, a stock moves for two reasons: the whole market moved that day, and something about the company itself. Insights splits each move into those two parts.</p>
    <ul style="margin:0 0 8px;padding-left:20px;line-height:1.7;font-size:14.5px"><li><b>Market part:</b> how much the stock usually moves when the market moves (its <a href="#/lesson/capm">beta</a>), times the market’s move that day.</li><li><b>Company-specific part:</b> the rest of the move.</li><li><b>“Times its normal day”:</b> how big that company-specific move was compared with a typical day for that stock.</li></ul>
    <p class="src">It shows what happened alongside the news, not proof that the news caused it. The full method and limits are further down.</p></div>`);
}

// ---------- homepage: two ways in ----------
function homePaths(): void {
  const m = document.getElementById('m'); if (!m || m.querySelector('.qx-paths')) return;
  const desk = m.querySelector('.qh-desk'); if (!desk) return;
  const mine = saved();
  desk.insertAdjacentHTML('beforebegin', `<section class="qx-paths" aria-labelledby="qx-ph"><h2 id="qx-ph">Two ways to use Quantera</h2><p class="sub">One platform and the same data, presented for how you want to work. Switch at any time.</p>
    <div class="qx-two"><div class="qx-card st"><small>Quantera Student</small><b>Learn, explore and practice.</b><p>Lessons from your first stock to valuation, a daily challenge, plain-English explanations and paper trading.</p><div class="act"><a class="btn${mine === 'student' ? ' p' : ''}" href="#/dashboard/student">${mine === 'student' ? 'Your dashboard' : 'Explore Student'}</a></div></div>
    <div class="qx-card pr"><small>Quantera Professional</small><b>Research, analyze and investigate.</b><p>Original research with methods and sources, SEC financial statements, market and economic data, and analytical tools.</p><div class="act"><a class="btn${mine === 'professional' ? ' p' : ''}" href="#/dashboard/professional">${mine === 'professional' ? 'Your research desk' : 'Explore Professional'}</a></div></div></div>
    <p class="qx-start">Start in one click: <a href="#/lesson/intro">Introduction to investing (10 minutes)</a> · <a href="#/asset/${FEATURED[((dayIndex() % FEATURED.length) + FEATURED.length) % FEATURED.length]}">Investigate today’s featured company</a> · <a href="#/research">Read the latest research</a> · <a href="#/insights">Compare reactions to the news</a>. No account needed.</p>
    <div class="qx-c qx-try" style="margin-top:16px"><h3>Try it now: today’s one-minute challenge</h3><div id="qx-hcx"></div></div></section>`);
  const hc = m.querySelector<HTMLElement>('#qx-hcx'); if (hc) cxHtml(todaysCx(), hc);
}

export function initExperience(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qx-css">${CSS}</style>`);
  SYNC.push(CXKEY);
  PAGES.dashboard = (a?: string) => dashboard(a);
  if (typeof HUB !== 'undefined') HUB.dashboard = 'dashboard';
  // Main menu: Dashboard first.
  const addNav = () => {
    const nav = document.getElementById('nav'); if (!nav || nav.querySelector('a[data-r=dashboard]')) return;
    nav.insertAdjacentHTML('afterbegin', '<a href="#/dashboard" data-r="dashboard"><svg class="ni" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/></svg>Dashboard</a>');
  };
  addNav(); setTimeout(addNav, 400);
  const after = () => {
    const r = location.hash.split('/')[1] || '';
    if (r === 'dashboard') document.querySelectorAll('#nav a').forEach(a => a.classList.toggle('on', (a as HTMLElement).dataset.r === 'dashboard'));
    if (r === '') { homePaths(); setTimeout(homePaths, 600); }
    if (r === 'insights') [300, 1200, 3000].forEach(t => setTimeout(insightsIntro, t));
  };
  addEventListener('hashchange', () => setTimeout(after, 30)); setTimeout(after, 300);
  // First visit: the chooser, once, unless the visitor (or a test) has already dismissed the mode prompt.
  setTimeout(() => { if (!saved() && LS<number>('q_mnd', 0) < 1 && !document.querySelector('.qm,.qx-ov')) chooser(); }, 1200);
}
