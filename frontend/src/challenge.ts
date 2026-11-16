// Academy: market-reaction challenge. A real news event and the companies it touched; the learner predicts which
// moved most and which reaction was most unusual for that stock, writes down their reasoning, then sees the measured
// answer and the reasons from the reaction analysis (/api/reaction). Nothing is invented: the questions are built
// from the measured data after it loads, and only events whose first trading session has happened are used.
import { runReaction, type Ask } from './reaction';

interface Row { symbol: string; name: string; move: number; market: number; abnormal: number; typicalDay: number; sigmas: number; marketCap?: number }
interface Res { day0: string; companies: Row[] }
interface Ev { key: string; title: string; context: string; ask: Ask }

const CSS = `
.qch{max-width:820px}.qch .ev{border:1px solid var(--ln);border-radius:16px;padding:18px 20px;background:var(--s1);margin:12px 0 16px}
.qch .ev small{font-size:11.5px;letter-spacing:.12em;text-transform:uppercase;color:var(--ac,#c8a96a);font-weight:600}
.qch .ev b{display:block;font-family:Fraunces,Georgia,serif;font-weight:400;font-size:clamp(20px,2vw,25px);line-height:1.3;margin:6px 0}
.qch .ev p{margin:0;color:var(--mu);line-height:1.6}
.qch .q{margin:18px 0}.qch .q h4{margin:0 0 8px;font-size:16px}.qch .opts{display:flex;flex-wrap:wrap;gap:8px}
.qch .opts .chip.ok{border-color:#4fd1a5;color:#4fd1a5}.qch .opts .chip.no{border-color:#e86a6a;color:#e86a6a}
.qch textarea{width:100%;box-sizing:border-box;min-height:84px;font:inherit;font-size:14.5px;padding:10px 12px!important;resize:vertical}
.qch .fb{margin:8px 0 0;font-size:14.5px;line-height:1.55}.qch .score{font-size:15px;margin:14px 0}
.qch-card{display:flex;gap:16px;align-items:center;border:1px solid var(--ln);border-radius:16px;padding:16px 18px;background:var(--s1);margin:0 0 18px}
.qch-card b{display:block;font-size:16px}.qch-card p{margin:2px 0 0;color:var(--mu);font-size:14px}.qch-card .btn{margin-left:auto;flex:none}
@media (max-width:640px){.qch-card{flex-direction:column;align-items:flex-start}.qch-card .btn{margin-left:0}}`;

// a documented event (see the research library) plus real multi-company stories from the news feed
const FIXED: Ev[] = [{
  key: 'nvda-2026-08-26', title: 'Nvidia reports record results',
  context: 'After the market closed on 26 August 2026, Nvidia reported quarterly revenue of $96.2 billion, up 106% on a year earlier, and guided to about $108 billion for the next quarter. Chip stocks AMD, Super Micro Computer (SMCI) and Broadcom (AVGO) are all tied to the same AI spending.',
  ask: { symbols: ['NVDA', 'AMD', 'SMCI', 'AVGO'], at: '2026-08-26T21:00:00Z', headline: 'Nvidia reports Q2 FY2027 results' },
}];

const KEY = 'q_rxc';
const sgn = (x: number) => (x > 0 ? '+' : x < 0 ? '−' : '') + fmt(Math.abs(x), 1) + '%';

async function events(): Promise<Ev[]> {
  const out = [...FIXED];
  try {
    const { ok, b } = await jf('/api/news');
    const yday = Date.now() - 20 * 3600_000;
    for (const a of (ok && b?.articles) || []) {
      const syms = [...new Set(((a.symbols || []) as any[]).map(s => String(typeof s === 'string' ? s : s?.symbol || '').toUpperCase()).filter(s => /^[A-Z]{1,5}$/.test(s)))] as string[];
      const t = Date.parse(a.publishedAt || '');
      if (syms.length >= 2 && t && t < yday && out.length < 4) out.push({ key: (a.id || a.url || a.title || '').slice(0, 80), title: a.title, context: `${a.source || 'A news provider'} published this story on ${new Date(t).toLocaleDateString(undefined, { day: 'numeric', month: 'long', year: 'numeric' })}. It tags ${syms.slice(0, 5).join(', ')}.`, ask: { symbols: syms.slice(0, 5), at: a.publishedAt, headline: a.title } });
    }
  } catch { /* the documented event is enough */ }
  return out;
}

async function play(m: HTMLElement, ev: Ev): Promise<void> {
  m.innerHTML = `<div class="qch"><p class="crumb" style="font-size:13px"><a href="#/learn">Academy</a> › <a href="#/challenge">Market reaction challenge</a></p><h1 class="qpt">Market reaction challenge</h1>
    <div class="ev"><small>The news</small><b>${esc(ev.title)}</b><p>${esc(ev.context)}</p></div><div id="qchq"><div class="qrx-sk" style="height:120px"></div></div></div>`;
  const box = m.querySelector('#qchq') as HTMLElement;
  let r: Res;
  try {
    const q = `/api/reaction?symbols=${encodeURIComponent(ev.ask.symbols.join(','))}&at=${encodeURIComponent(ev.ask.at)}${ev.ask.headline ? `&headline=${encodeURIComponent(ev.ask.headline)}` : ''}`;
    const res = await fetch(q); r = await res.json();
    if (!res.ok || !r.companies || r.companies.length < 2) throw new Error((r as any)?.error || 'no data');
  } catch (e) { box.innerHTML = `<p class="mu">This event can’t be measured right now (${esc((e as Error).message)}). <a href="#/challenge">Try another event</a>.</p>`; return; }
  const cos = r.companies, syms = cos.map(c => c.symbol).sort(); // alphabetical, so the order gives nothing away
  const most = [...cos].sort((a, b) => Math.abs(b.move) - Math.abs(a.move))[0], unusual = [...cos].sort((a, b) => Math.abs(b.sigmas) - Math.abs(a.sigmas))[0];
  const first = cos.find(c => c.symbol === ev.ask.symbols[0]) || cos[0], share = Math.abs(first.move) > 0.05 ? Math.min(100, Math.abs(first.market / first.move) * 100) : 0;
  const shareBand = share < 25 ? 'Less than a quarter' : share < 50 ? 'A quarter to a half' : share < 75 ? 'Half to three quarters' : 'More than three quarters';
  const Q = [
    { h: `1. On the first trading day after the news (${new Date(r.day0 + 'T12:00:00Z').toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' })}), which stock moved the most, up or down?`, opts: syms, right: most.symbol,
      why: `${most.symbol} moved ${sgn(most.move)}. ${cos.filter(c => c !== most).map(c => `${c.symbol} ${sgn(c.move)}`).join(', ')}.` },
    { h: '2. Measured against each stock’s normal day, which reaction was the most unusual?', opts: syms, right: unusual.symbol,
      why: `${unusual.symbol}’s company-specific move was ${fmt(Math.abs(unusual.sigmas), 1)}× its normal day (±${fmt(unusual.typicalDay, 1)}%). ${unusual.symbol === most.symbol ? 'Here the biggest move was also the most unusual one.' : `The biggest move isn’t always the most unusual: ${most.symbol} normally moves more, so its ${sgn(most.move)} was ${fmt(Math.abs(most.sigmas), 1)}× its normal day.`}` },
    { h: `3. How much of ${first.symbol}’s move was explained by the whole market moving?`, opts: ['Less than a quarter', 'A quarter to a half', 'Half to three quarters', 'More than three quarters'], right: shareBand,
      why: `The market part was ${sgn(first.market)} of ${first.symbol}’s ${sgn(first.move)} (its usual sensitivity to the market, beta, times the S&P 500’s move), about ${fmt(share, 0)}%. The rest, ${sgn(first.abnormal)}, was specific to ${first.symbol}.` },
  ];
  box.innerHTML = Q.map((q, i) => `<div class="q" data-q="${i}"><h4>${esc(q.h)}</h4><div class="opts">${q.opts.map(o => `<button class="chip" data-a="${esc(o)}">${esc(o)}</button>`).join('')}</div><p class="fb" hidden></p></div>`).join('')
    + `<div class="q"><h4>4. In a sentence or two: why do you think these companies reacted differently?</h4><textarea id="qchw" placeholder="For example: size, how directly the news is about each company, how volatile each stock usually is…" aria-label="Your reasoning"></textarea><p class="mu" style="font-size:12.5px;margin:6px 0 0">Your answer stays on this device. You’ll compare it with the measured reasons.</p></div>
    <button class="btn p" id="qchgo">Show the answer</button><div id="qchout"></div>`;
  const picks: (string | null)[] = Q.map(() => null);
  box.querySelectorAll<HTMLElement>('[data-q]').forEach(qe => qe.querySelectorAll<HTMLButtonElement>('[data-a]').forEach(b => b.onclick = () => {
    if (qe.dataset.done) return; picks[+qe.dataset.q!] = b.dataset.a!;
    qe.querySelectorAll('.chip').forEach(c => c.classList.toggle('on', c === b));
  }));
  (box.querySelector('#qchgo') as HTMLButtonElement).onclick = async () => {
    if (picks.some(p => p == null)) { qtoast('Answer questions 1 to 3 first.'); return; }
    let score = 0;
    box.querySelectorAll<HTMLElement>('[data-q]').forEach(qe => {
      const i = +qe.dataset.q!, q = Q[i]; qe.dataset.done = '1'; if (picks[i] === q.right) score++;
      qe.querySelectorAll<HTMLButtonElement>('[data-a]').forEach(c => { c.classList.toggle('ok', c.dataset.a === q.right); c.classList.toggle('no', c.dataset.a === picks[i] && picks[i] !== q.right); c.disabled = true; });
      const fb = qe.querySelector('.fb') as HTMLElement; fb.hidden = false; fb.innerHTML = `${picks[i] === q.right ? '✓ Right.' : `✗ The answer is <b>${esc(q.right)}</b>.`} ${esc(q.why)}`;
    });
    (box.querySelector('#qchgo') as HTMLElement).remove();
    const done = LS<Record<string, number>>(KEY, {}); done[ev.key] = Math.max(done[ev.key] || 0, score); SS(KEY, done);
    track('reaction_challenge');
    (window as any).qKes?.(score === 3 ? 'done' : score > 0 ? 'correct' : 'wrong', `${score} of ${3} right. ${score === 3 ? 'Perfect!' : 'Read the explanations: that’s where the learning is.'}`); try { ACT_('challenge'); } catch { /* streak helper may not exist */ }
    const out = box.querySelector('#qchout') as HTMLElement;
    out.innerHTML = `<p class="score"><b>${score} of 3.</b> ${score === 3 ? 'Excellent reading of the market.' : 'The full measurement below shows where each answer comes from.'} Now compare your reasoning with the measured reasons:</p><div id="qchrx"></div>
      <p style="margin-top:14px"><a class="btn" href="#/challenge">Try another event</a> <a class="btn" href="#/insights">Compare any companies yourself</a></p>`;
    await runReaction(out.querySelector('#qchrx') as HTMLElement, ev.ask);
  };
}

async function page(arg?: string): Promise<void> {
  const m = document.getElementById('m'); if (!m) return;
  document.title = 'Market reaction challenge | Quantera Academy';
  const evs = await events(), done = LS<Record<string, number>>(KEY, {});
  const pick = arg && evs.find(e => encodeURIComponent(e.key) === arg);
  if (pick) return play(m, pick);
  m.innerHTML = `<div class="qch"><p class="crumb" style="font-size:13px"><a href="#/learn">Academy</a> › Market reaction challenge</p><h1 class="qpt">Market reaction challenge</h1>
    <p style="font-size:16px;line-height:1.6;max-width:720px">Real news, real price data. Read what happened, predict which company reacted most and which reaction was most unusual, explain your reasoning, then see what was measured and why companies usually react differently.</p>
    ${evs.map(e => `<a class="ev" style="display:block;color:inherit;text-decoration:none" href="#/challenge/${encodeURIComponent(e.key)}"><small>${done[e.key] != null ? `Done · ${done[e.key]} of 3` : 'Not tried yet'}</small><b>${esc(e.title)}</b><p>${esc(e.ask.symbols.join(' · '))}</p></a>`).join('')}
    <p class="mu" style="font-size:13px">Before you start: <a href="#/lesson/capm">beta and the market</a> · <a href="#/lesson/volume">trading volume</a></p></div>`;
}

export function initChallenge(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qch-css">${CSS}</style>`);
  PAGES.challenge = (a?: string) => { track('view_challenge'); const k = /^#\/challenge\/(.+)$/.exec(location.hash)?.[1]; page(k || (a !== 'challenge' ? a : undefined)); };
  (HUB as Record<string, string>).challenge = 'learn';
  // a card at the top of the Academy
  const main = document.getElementById('m');
  const card = () => {
    if (!/^#\/learn\/?$/.test(location.hash) || !main || main.querySelector('.qch-card')) return;
    // at the top of the page body, below the router's header band (or after the title on pages without one)
    const body = main.querySelector<HTMLElement>(':scope > .body'), h = main.querySelector('h1'); if (!body && !h) return;
    const html = `<div class="qch-card"><span style="font-size:26px" aria-hidden="true">⇄</span><div><b>Market reaction challenge</b><p>A real news event: predict which companies reacted most and why, then see what was measured.</p></div><a class="btn p" href="#/challenge">Start</a></div>`;
    if (body) body.insertAdjacentHTML('afterbegin', html); else h!.insertAdjacentHTML('afterend', html);
  };
  if (main) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(card, 30); }).observe(main, { childList: true }); }
}
