// Quantera's identity on the pages a first-time visitor sees:
// - Homepage: a research-first hero ("Same news. Different world."), a "From the research desk" section with the
//   latest study and the reaction comparison, research-update sign-up. Everything that was on the homepage stays
//   (markets, movers, tools, stories, FAQ); paper trading is one link away instead of the headline.
// - About: an honest account of who runs Quantera and why, its research rules and what's next.
// - A browser-tab title and description for every page (they were all the same before).
import { library } from './library';

const CSS = `
.qh-eye{font-size:12.5px;letter-spacing:.32em;text-transform:uppercase;color:var(--ac,#c8a96a);margin:0 0 18px;font-weight:600}
.hm .qh-h1{font:300 clamp(46px,8.2vw,112px)/.98 Fraunces,Georgia,serif!important;letter-spacing:-.03em!important;margin:0 0 24px!important;color:var(--tx)}
.hm .qh-h1 em{font-style:italic;color:var(--ac,#c8a96a)}
.qh-alt{display:inline-block;margin-top:14px;font-size:14px;color:var(--mu)}.qh-alt:hover{color:var(--tx)}
.qh-desk{margin:8px 0 8px}.qh-desk>h2{margin:0}.qh-desk>.sub{margin:4px 0 16px}
.qh-grid{display:grid;grid-template-columns:1.35fr 1fr 1fr;gap:14px}
@media (max-width:980px){.qh-grid{grid-template-columns:1fr}}
.qh-card{display:flex;flex-direction:column;gap:8px;border:1px solid var(--ln);border-radius:16px;padding:20px 22px;background:var(--s1);color:inherit;text-decoration:none;transition:border-color .15s}
a.qh-card:hover{border-color:rgba(var(--acc),.55)}
.qh-card small{font-size:11.5px;letter-spacing:.12em;text-transform:uppercase;color:var(--ac,#c8a96a);font-weight:600}
.qh-card b{font-family:Fraunces,Georgia,serif;font-weight:400;font-size:clamp(20px,1.9vw,25px);line-height:1.25}
.qh-card p{margin:0;color:var(--mu);font-size:14.5px;line-height:1.55}.qh-card .go{margin-top:auto;padding-top:6px;font-size:14px;color:var(--tx)}
.qh-card ul{margin:0;padding-left:18px;color:var(--mu);font-size:14px;line-height:1.6}
.qh-nl{display:grid;grid-template-columns:1fr minmax(0,420px);gap:20px;align-items:center;border:1px solid var(--ln);border-radius:16px;padding:22px 24px;background:var(--s1)}
.qh-nl h3{margin:0 0 4px;font-family:Fraunces,Georgia,serif;font-weight:400;font-size:24px}.qh-nl p{margin:0;color:var(--mu);font-size:14.5px}
.qh-nl form{display:flex;gap:8px}.qh-nl input{flex:1;min-width:0;font:inherit;font-size:14px;padding:10px 12px!important}
.qh-nl .msg{grid-column:1/-1;font-size:13.5px;margin:0}
@media (max-width:760px){.qh-nl{grid-template-columns:1fr}}
.qab{max-width:820px}.qab .lede{font-size:clamp(18px,1.7vw,21px);line-height:1.6;color:rgba(var(--fgc),.86);margin:6px 0 26px}
.qab h2{font-family:Fraunces,Georgia,serif;font-weight:400;font-size:clamp(22px,2.2vw,28px)!important;margin:34px 0 10px}
.qab p,.qab li{font-size:16px;line-height:1.7}.qab .rules{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin:12px 0}
.qab .rules div{border:1px solid var(--ln);border-radius:14px;padding:14px 16px;background:var(--s1)}.qab .rules b{display:block;margin-bottom:4px}
.qab .rules p{margin:0;font-size:14.5px;color:var(--mu);line-height:1.55}
@media (max-width:640px){.qab .rules{grid-template-columns:1fr}}`;

// ---------- homepage ----------
function hero(open: HTMLElement): void {
  const inner = open.querySelector(':scope > div'); if (!inner || inner.hasAttribute('data-qh')) return;
  inner.setAttribute('data-qh', '1');
  const facts = inner.querySelector('.hm-facts')?.outerHTML || '';
  inner.innerHTML = `<p class="qh-eye">Quantera Research</p>
    <h1 class="qh-h1">Same news.<br><em>Different world.</em></h1>
    <p class="lead">Student-led, independent research into how companies and markets react to the news, and free tools to check it yourself. Every figure is sourced and every method is shown.</p>
    <div class="cta"><a class="btn p" href="#/research">Read the research</a><a class="btn" href="#/insights">Compare reactions to the news</a></div>
    <a class="qh-alt" href="#/trade">Or practise investing with $100,000 of virtual money →</a>
    <div class="fine" style="margin-top:12px">Free. No sign-up needed. For education, not investment advice.</div>${facts}`;
  const f = inner.querySelector('.hm-facts');
  if (f) f.innerHTML = `<span><b>SEC financials</b> for every US stock</span><span><b>44 countries</b> of market prices</span><span><b>38</b> free lessons</span><a href="#/private"><b>100+</b> private company valuations →</a>`;
}

async function desk(hm: HTMLElement): Promise<void> {
  const open = hm.querySelector('.hm-open'); if (!open || hm.querySelector('.qh-desk')) return;
  const sec = document.createElement('section'); sec.className = 'qh-desk qin'; // qin: shown straight away (the homepage's scroll-reveal hides sections until revealed)
  sec.innerHTML = `<h2>From the research desk</h2><p class="sub">Original studies and the questions behind them. <a href="#/research">All research →</a></p>
    <div class="qh-grid"><a class="qh-card" id="qhst" href="#/research"><small>Latest study</small><b>Loading…</b></a>
    <a class="qh-card" href="#/insights/NVDA,AMD,SMCI,AVGO@2026-08-26T21:00:00Z"><small>Insights</small><b>Same results, different reactions</b><p>Nvidia's results on 26 August moved chip stocks by very different amounts. See how much of each move was the whole market, how unusual it was for each stock, and what usually explains the gap.</p><span class="go">Compare the reactions →</span></a>
    <a class="qh-card" href="#/about"><small>How we work</small><b>No source, no number</b><ul><li>Primary sources first: SEC filings and official releases</li><li>Facts and interpretation labelled separately</li><li>Every calculation re-run before publication</li></ul><span class="go">About Quantera Research →</span></a></div>`;
  open.after(sec);
  try {
    const lib = await library(), a = lib.articles[0], c = sec.querySelector('#qhst') as HTMLAnchorElement;
    if (a && c) { c.href = `#/research/${a.slug}`; c.innerHTML = `<small>Latest study · ${esc(a.category)}</small><b>${esc(a.title)}</b><p>${esc(a.dek)}</p><span class="go">Read the study →</span>`; }
  } catch { const c = sec.querySelector('#qhst'); if (c) c.innerHTML = '<small>Research</small><b>Browse the research library</b><span class="go">Open →</span>'; }
}

function newsletter(hm: HTMLElement): void {
  const faq = hm.querySelector('#faq'); if (!faq || hm.querySelector('.qh-nl')) return;
  const sec = document.createElement('section'); sec.className = 'qin'; sec.style.marginBottom = '8px';
  sec.innerHTML = `<div class="qh-nl"><div><h3>New research, by email</h3><p>One short email when we publish a new study. No spam, no selling your address, unsubscribe any time.</p></div>
    <form novalidate><input type="email" required placeholder="you@example.com" aria-label="Email address" autocomplete="email"><button class="btn p" type="submit">Notify me</button></form><p class="msg" aria-live="polite"></p></div>`;
  faq.before(sec);
  const form = sec.querySelector('form') as HTMLFormElement, inp = form.querySelector('input') as HTMLInputElement, msg = sec.querySelector('.msg') as HTMLElement;
  form.onsubmit = async e => {
    e.preventDefault();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(inp.value.trim())) { msg.textContent = 'Please enter a valid email address.'; return; }
    msg.textContent = 'Signing you up…';
    try {
      const r = await fetch('/api/waitlist', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify({ email: inp.value.trim(), interest: 'research-updates', source: 'home' }) });
      const j = await r.json().catch(() => ({}));
      if (r.ok) { msg.textContent = 'Thanks. We’ll email you when the next study is published.'; inp.value = ''; track('newsletter_join'); }
      else msg.innerHTML = r.status === 503 ? 'Email sign-ups open soon. Meanwhile, follow <a href="https://instagram.com/quantera.research" target="_blank" rel="noopener">@quantera.research</a> for new research.' : esc(j.error || 'That didn’t work. Please try again.');
    } catch { msg.textContent = 'Could not reach the server. Please try again.'; }
  };
}

function home(): void {
  const hm = document.querySelector<HTMLElement>('#m .hm'); if (!hm) return;
  const open = hm.querySelector<HTMLElement>('.hm-open'); if (open) hero(open);
  desk(hm); newsletter(hm);
  const doHead = [...hm.querySelectorAll('section > h2')].find(h => /What you can do here/.test(h.textContent || ''));
  if (doHead) { doHead.textContent = 'Tools for your own research'; const sub = doHead.nextElementSibling; if (sub?.classList.contains('sub')) sub.textContent = 'Free to use, no account needed.'; }
}

// ---------- About ----------
function about(): void {
  const m = document.getElementById('m'); if (!m) return;
  m.innerHTML = `<article class="qab"><h1 class="qpt">About Quantera Research</h1>
    <p class="lede">Quantera Research is a student-led financial research, education and technology initiative. We study how companies and markets react to the news, publish what we find with every source shown, and build free tools so anyone can check the work.</p>
    <h2>Why we started</h2>
    <p>Financial news tells you what happened. It rarely explains why the same story moves one company 5% and another 25%, how unusual a move really was, or what the numbers in a company’s filings actually say. The tools that join all of this up are built for professionals and cost thousands a year, and much of what is free is scattered, full of jargon or unsourced.</p>
    <p>We wanted one place where the research comes first, every number shows where it came from, and learning is built in rather than bolted on.</p>
    <h2>Who we are</h2>
    <p>Quantera was founded by twin brothers <b>Aarav Gupta</b> and <b>Aryan Gupta</b>, who are students. They research, write, design and build Quantera together. It is an independent initiative: we are not a bank, broker, investment adviser or research firm, and we have no outside investors or institutional partners.</p>
    <h2>How we research</h2>
    <div class="rules">
      <div><b>Primary sources first</b><p>Company filings with the SEC, official releases and exchange data come before news coverage or opinion.</p></div>
      <div><b>No source, no number</b><p>If we can’t verify a figure, we say it wasn’t found rather than estimate it.</p></div>
      <div><b>Facts apart from interpretation</b><p>What happened is labelled separately from what it might mean. Things that happen at the same time are never presented as cause and effect.</p></div>
      <div><b>Checked before publishing</b><p>Every calculation is re-run independently, and every study states its limits.</p></div>
    </div>
    <h2>What you’ll find here</h2>
    <ul><li><a href="#/research">Research</a>: our published studies of companies, earnings and market events.</li>
      <li><a href="#/insights">Insights</a>: compare how different companies reacted to the same news, measured against the market and each stock’s normal day.</li>
      <li><a href="#/stocks">Company research</a>: financial statements from SEC filings, valuation, competitors and earnings history for any US stock.</li>
      <li><a href="#/learn">Academy</a>: 38 short lessons and exercises, from reading a chart to valuing a company.</li>
      <li><a href="#/trade">Practice</a>: paper trading with $100,000 of virtual money, always labelled as practice.</li></ul>
    <h2>Our educational mission</h2>
    <p>Learning finance should start from real companies and real events, not abstract definitions. Every lesson links to a company you can open, every ratio explains how it is calculated, and the Academy includes exercises where you interpret what actually happened before seeing the answer.</p>
    <h2>What’s next</h2>
    <p>Our long-term research question is how companies of different sizes react to comparable events: how large the moves are, how long they last and how they recover. We are building that dataset one study at a time, and we hope to open it to other students who want to research with us.</p>
    <h2>Get in touch</h2>
    <p>Follow us or send a message on Instagram at <a href="https://instagram.com/quantera.research" target="_blank" rel="noopener">@quantera.research</a>. If you spot an error in our research, please tell us: we correct it and note the change.</p>
    <p class="mu" style="font-size:13.5px">Everything on Quantera is for education. It is not investment advice or a recommendation to buy or sell any security. <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a> · <a href="/accessibility">Accessibility</a></p></article>`;
}

// ---------- titles for every page ----------
const TITLES: Record<string, [string, string]> = {
  '': ['Quantera Research: same news, different world', 'Student-led research into how companies and markets react to the news, with free tools to check it yourself: SEC financials, reaction analysis, charts and an Academy.'],
  markets: ['Markets today', 'Indexes, movers, sectors and the economy, with the data source and time on every figure.'],
  world: ['World markets', 'Stock markets in 44 countries, with currencies and commodities.'],
  news: ['Market news, explained', 'Headlines with the companies they move and a plain-English explanation of why it matters.'],
  story: ['News story, explained', 'What happened, who it affects and how each company reacted.'],
  stocks: ['All stocks', 'Over 11,000 US stocks and funds, plus major markets worldwide.'],
  screener: ['Stock screener', 'Filter stocks by size, valuation, growth and dividends.'],
  compare: ['Compare stocks', 'Compare companies side by side.'],
  ipos: ['IPO calendar', 'Upcoming and recent stock market listings.'],
  calendar: ['Earnings and economic calendar', 'Earnings dates, IPOs and official US economic releases.'],
  macro: ['Economy', 'Inflation, jobs, growth and interest rates from official sources.'],
  trade: ['Paper trading', 'Practise investing with $100,000 of virtual money on real prices.'],
  portfolio: ['Practice portfolio', 'Your paper portfolio against the S&P 500, with risk and a trade journal.'],
  planner: ['Goal planner', 'How much to invest each month to reach a goal.'],
  learn: ['Academy', 'Short lessons and exercises, from reading a chart to valuing a company.'],
  lesson: ['Lesson', 'A short Quantera Academy lesson.'],
  glossary: ['Glossary', 'Finance terms explained in plain English.'],
  ai: ['Ask Quantera', 'Answers grounded in market data and SEC filings, with sources.'],
  report: ['AI deep research', 'An AI outlook, analysts’ consensus and technicals, side by side, from sourced data.'],
  valuation: ['Valuation', 'A discounted cash flow model on real filed figures, with every assumption visible.'],
  private: ['Private company valuations', 'Valuations of 100+ private companies, with how each was worked out.'],
  about: ['About Quantera Research', 'Who runs Quantera, why it exists and how we research.'],
  settings: ['Settings', 'Theme, layout, privacy choices and your data.'],
  pricing: ['Pricing', 'Quantera is free.'],
  leaderboard: ['Leaderboard', 'Paper trading leaderboard.'],
  options: ['Options basics', 'How options pay off, explained with an interactive chart.'],
};
const OWN = new Set(['insights', 'research']); // these pages set their own titles
function setMeta(): void {
  const parts = location.hash.replace(/^#\/?/, '').split('/'), r = parts[0] || '';
  if (OWN.has(r)) return;
  let t = TITLES[r];
  if (r === 'asset' && parts[1]) { const s = decodeURIComponent(parts[1]).toUpperCase().slice(0, 12); t = [`${s} stock: price, financials and research`, `${s}: price, chart, SEC financial statements, valuation, competitors and earnings history.`]; }
  if (!t) return;
  document.title = r === '' ? t[0] : `${t[0]} | Quantera`;
  const md = document.querySelector('meta[name=description]'); if (md) md.setAttribute('content', t[1]);
}

export function initHome(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qh-css">${CSS}</style>`);
  PAGES.about = () => { track('view_about'); about(); };
  const main = document.getElementById('m');
  if (main) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(home, 20); }).observe(main, { childList: true }); }
  home();
  window.addEventListener('hashchange', () => setTimeout(setMeta, 0));
  setMeta();
}
