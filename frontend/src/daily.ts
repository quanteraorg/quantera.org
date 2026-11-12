// Duolingo-style learning loop for the Academy: a daily challenge (one real news story to interpret,
// then two quick quiz questions), XP and levels, a daily goal ring and the learning streak.
// Learning only: nothing here rewards trading. The news step never claims a "right answer";
// it shows what the stock actually did and why one headline rarely explains a move.

interface Daily { day: string; done: boolean; xp: number; total: number; days: number; matched: number; hist: string[] }
interface NewsSym { symbol: string; changePct: number | null }
interface Article { title: string; source?: string; url?: string; publishedAt?: string; symbols?: NewsSym[] }
type Call = 'up' | 'down' | 'mixed';

const KEY = 'q_daily';
const today = () => { const x = new Date(); return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`; };
const load = (): Daily => ({ day: '', done: false, xp: 0, total: 0, days: 0, matched: 0, hist: [], ...LS<Partial<Daily>>(KEY, {}) });
const level = (total: number) => { let lv = 1, need = 50, left = total; while (left >= need) { left -= need; lv++; need += 25; } return { lv, into: left, need }; };
const seed = (s: string) => { let h = 2166136261; for (const c of s) h = Math.imul(h ^ c.charCodeAt(0), 16777619); return () => ((h = Math.imul(h ^ (h >>> 15), 2246822507) >>> 0) / 4294967296); };
const streak = () => { const s = LS<{ c: number; l: string }>('q_streak', { c: 0, l: '' }); const y = new Date(Date.now() - 864e5); const yd = `${y.getFullYear()}-${String(y.getMonth() + 1).padStart(2, '0')}-${String(y.getDate()).padStart(2, '0')}`; return s.l === today() || s.l === yd ? s.c : 0; };

const CSS = `
.qd{padding:0!important;overflow:hidden;margin-bottom:18px}
.qd-h{display:flex;align-items:center;gap:18px;flex-wrap:wrap;padding:18px 20px;border-bottom:1px solid var(--ln)}
.qd-ring{--p:0;width:58px;height:58px;border-radius:50%;flex:none;display:grid;place-items:center;background:conic-gradient(var(--ac) calc(var(--p)*1%),rgba(var(--fgc),.08) 0);transition:--p .8s}
.qd-ring>span{width:46px;height:46px;border-radius:50%;background:var(--s1);display:grid;place-items:center;font-size:22px}
.qd-t{flex:1;min-width:200px}.qd-t b{display:block;font-size:17px}.qd-t span{color:var(--mu);font-size:13.5px}
.qd-stats{display:flex;gap:10px;flex-wrap:wrap}.qd-stat{padding:8px 12px;border-radius:12px;background:rgba(var(--fgc),.05);text-align:center;min-width:76px}
.qd-stat b{display:block;font-size:18px}.qd-stat small{color:var(--mu);font-size:11.5px}
.qd-lv{height:6px;border-radius:99px;background:rgba(var(--fgc),.08);overflow:hidden;margin-top:6px}.qd-lv i{display:block;height:100%;background:var(--ac);border-radius:99px;transition:width .8s cubic-bezier(.22,1,.36,1)}
.qd-b{padding:20px}.qd-step{font-size:12px;letter-spacing:.08em;text-transform:uppercase;color:var(--ac);margin-bottom:8px}
.qd-q{font-size:18px;line-height:1.45;margin:0 0 6px}.qd-src{color:var(--mu);font-size:13px;margin-bottom:14px}
.qd-opts{display:grid;gap:10px;grid-template-columns:repeat(auto-fit,minmax(170px,1fr))}
.qd-o{border:1px solid var(--ln);background:rgba(var(--fgc),.03);color:var(--tx);border-radius:14px;padding:14px;text-align:left;cursor:pointer;font:inherit;font-size:15px;transition:transform .12s,border-color .2s,background .2s}
.qd-o:hover{border-color:rgba(var(--acc),.6)}.qd-o:active{transform:scale(.97)}.qd-o small{display:block;color:var(--mu);font-size:12.5px;margin-top:3px}
.qd-o.pick{border-color:var(--ac);background:rgba(var(--acc),.12)}.qd-o.good{border-color:var(--up);background:rgba(var(--upc),.12)}.qd-o.bad{border-color:var(--dn);background:rgba(var(--dnc),.1)}
.qd-o:disabled{cursor:default}
.qd-fb{margin-top:14px;padding:14px 16px;border-radius:14px;background:rgba(var(--fgc),.04);line-height:1.6;animation:qdin .35s cubic-bezier(.22,1,.36,1)}
.qd-fb b.up{color:var(--up)}.qd-fb b.dn{color:var(--dn)}
.qd-next{margin-top:14px}.qd-dots{display:flex;gap:6px;margin-left:auto}.qd-dots i{width:9px;height:9px;border-radius:50%;background:rgba(var(--fgc),.15)}.qd-dots i.on{background:var(--ac)}
.qd-done{text-align:center;padding:26px 20px}.qd-done .big{font-size:40px;margin:6px 0}
.qd-burst{position:fixed;pointer-events:none;z-index:300;width:8px;height:8px;border-radius:2px;animation:qdb 1s cubic-bezier(.2,.7,.3,1) forwards}
@keyframes qdb{to{transform:translate(var(--dx),var(--dy)) rotate(var(--r));opacity:0}}
@keyframes qdin{from{opacity:0;transform:translateY(6px)}}
.qdh{display:flex;align-items:center;gap:14px;flex-wrap:wrap;padding:14px 18px!important;margin:0 0 18px}.qdh b{font-size:16px}.qdh .mu{font-size:13.5px}.qdh .btn{margin:0 0 0 auto}
@media(prefers-reduced-motion:reduce){.qd-burst{display:none}.qd-fb{animation:none}}`;

function burst(from: Element): void {
  if (matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  const r = from.getBoundingClientRect(), cx = r.left + r.width / 2, cy = r.top + r.height / 2;
  const colors = ['var(--ac)', 'var(--up)', '#7aa6e8', '#f5e6c4'];
  for (let i = 0; i < 26; i++) {
    const p = document.createElement('i');
    p.className = 'qd-burst';
    const a = Math.random() * Math.PI * 2, d = 60 + Math.random() * 110;
    p.style.cssText = `left:${cx}px;top:${cy}px;background:${colors[i % 4]};--dx:${Math.cos(a) * d}px;--dy:${Math.sin(a) * d - 40}px;--r:${Math.random() * 540}deg`;
    document.body.appendChild(p);
    setTimeout(() => p.remove(), 1100);
  }
}

function header(d: Daily): string {
  const lv = level(d.total), st = streak(), pct = d.day === today() && d.done ? 100 : 0;
  return `<div class="qd-h"><div class="qd-ring" style="--p:${pct}" title="Daily goal"><span>${pct === 100 ? '✓' : '🎯'}</span></div>
    <div class="qd-t"><b>${pct === 100 ? 'Daily goal complete' : 'Today’s challenge'}</b><span>${pct === 100 ? 'Come back tomorrow to keep your streak going.' : 'One news story, two quick questions. About two minutes.'}</span>
    <div class="qd-lv" title="${lv.into} of ${lv.need} XP to level ${lv.lv + 1}"><i style="width:${Math.round(lv.into / lv.need * 100)}%"></i></div></div>
    <div class="qd-stats"><div class="qd-stat"><b>🔥 ${st}</b><small>day streak</small></div><div class="qd-stat"><b>⭐ ${d.total}</b><small>XP</small></div><div class="qd-stat"><b>${lv.lv}</b><small>level</small></div></div></div>`;
}

async function pickStory(): Promise<{ a: Article; s: NewsSym; spx: number | null } | null> {
  if (!QLIVE.market) return null;
  try {
    const [n, m] = await Promise.all([jf('/api/news?symbols=SPY,QQQ,NVDA,AAPL,MSFT,AMZN,TSLA'), jf('/api/markets').catch(() => ({ ok: false, b: null }))]);
    const arts: Article[] = (n.ok && n.b?.articles) || [];
    const usable = arts.flatMap(a => (a.symbols || []).filter(s => s.changePct != null && !/\.PVT$|^\^/.test(s.symbol)).slice(0, 1).map(s => ({ a, s })));
    if (!usable.length) return null;
    const pick = usable[Math.floor(seed(today())() * usable.length)];
    const spxRow = (m.ok && m.b?.indices || []).find((i: any) => i.levelSymbol === '^GSPC');
    return { ...pick, spx: spxRow ? spxRow.levelChangePct : null };
  } catch { return null; }
}

function quizPicks(): typeof LES {
  const r = seed('q' + today()), pool = LES.filter(l => Array.isArray(l[8]) && l[8][1]?.length), out: typeof LES = [];
  while (out.length < Math.min(2, pool.length)) { const l = pool[Math.floor(r() * pool.length)]; if (!out.includes(l)) out.push(l); }
  return out;
}

export function renderDaily(host: HTMLElement): void {
  const d = load();
  const box = document.createElement('div');
  box.className = 'pan qd';
  box.id = 'qdaily';
  host.prepend(box);
  if (d.day === today() && d.done) {
    box.innerHTML = header(d) + `<div class="qd-done"><div class="mu">Today you earned</div><div class="big">+${d.xp} XP</div><div class="mu">New challenge tomorrow. Meanwhile, try a lesson or the Chart Gym.</div></div>`;
    return;
  }
  box.innerHTML = header(d) + `<div class="qd-b"><div class="qd-step">Loading today’s story…</div></div>`;
  const body = () => box.querySelector('.qd-b') as HTMLElement;
  let xp = 0, step = 0, matchedToday = false;
  const quiz = quizPicks();
  pickStory().then(story => {
    const steps = (story ? 1 : 0) + quiz.length;
    const dots = (i: number) => `<div class="qd-dots">${Array.from({ length: steps }, (_, k) => `<i class="${k <= i ? 'on' : ''}"></i>`).join('')}</div>`;
    const next = () => { step++; const qi = story ? step - 1 : step; if (qi < quiz.length) showQuiz(qi); else finish(); };
    const showNews = () => {
      const s = story!.s, a = story!.a;
      body().innerHTML = `<div style="display:flex;align-items:center"><div class="qd-step">Step 1 · Read the news</div>${dots(0)}</div>
        <p class="qd-q">“${esc(a.title)}”</p><div class="qd-src">${esc(a.source || '')}${a.url ? ` · <a href="${esc(a.url)}" target="_blank" rel="noopener">read the story</a>` : ''}</div>
        <div style="margin-bottom:10px"><b>How might this affect ${esc(s.symbol)}?</b></div>
        <div class="qd-opts"><button class="qd-o" data-c="up">📈 Good news<small>Could push the price up</small></button><button class="qd-o" data-c="down">📉 Bad news<small>Could push the price down</small></button><button class="qd-o" data-c="mixed">⚖️ Mixed or unclear<small>Hard to say from this alone</small></button></div>`;
      body().querySelectorAll<HTMLButtonElement>('.qd-o').forEach(b => b.onclick = () => {
        const c = b.dataset.c as Call, pc = s.changePct as number, actual: Call = Math.abs(pc) < 1 ? 'mixed' : pc > 0 ? 'up' : 'down';
        matchedToday = c === actual; xp += 5 + (matchedToday ? 5 : 0);
        body().querySelectorAll<HTMLButtonElement>('.qd-o').forEach(x => { x.disabled = true; if (x === b) x.classList.add('pick'); });
        const word = actual === 'mixed' ? 'barely moved' : actual === 'up' ? 'rose' : 'fell';
        const spx = story!.spx;
        body().insertAdjacentHTML('beforeend', `<div class="qd-fb">${matchedToday ? '✅ Your read matched how the market moved today.' : '🤔 The market moved differently today.'}
          <b class="${pc >= 0 ? 'up' : 'dn'}">${esc(s.symbol)} ${word} ${pc >= 0 ? '+' : '−'}${fmt(Math.abs(pc), 2)}%</b> today${spx != null ? `, while the S&amp;P 500 moved ${spx >= 0 ? '+' : '−'}${fmt(Math.abs(spx), 2)}%` : ''}.
          <br><span class="mu">Remember: one headline rarely explains a whole day’s move. Prices also react to what investors already expected, other news the same day and the overall market. That’s why there’s no single right answer here.</span>
          <div class="qd-next"><button class="btn p">Next question →</button></div></div>`);
        (body().querySelector('.qd-next .btn') as HTMLButtonElement).onclick = next;
        track('daily_news_' + (matchedToday ? 'match' : 'differ'));
      });
    };
    const showQuiz = (qi: number) => {
      const l = quiz[qi], [q, opts, ans] = l[8], n = (story ? 2 : 1) + qi;
      body().innerHTML = `<div style="display:flex;align-items:center"><div class="qd-step">Step ${n} · Quick question</div>${dots(n - 1)}</div><p class="qd-q">${esc(q)}</p>
        <div class="qd-src">From the lesson “${esc(l[1])}”</div><div class="qd-opts">${opts.map((o, i) => `<button class="qd-o" data-i="${i}">${esc(o)}</button>`).join('')}</div>`;
      body().querySelectorAll<HTMLButtonElement>('.qd-o').forEach(b => b.onclick = () => {
        const i = Number(b.dataset.i), right = i === ans; if (right) xp += 10;
        body().querySelectorAll<HTMLButtonElement>('.qd-o').forEach(x => { x.disabled = true; const k = Number(x.dataset.i); if (k === ans) x.classList.add('good'); else if (x === b) x.classList.add('bad'); });
        if (right) burst(b);
        body().insertAdjacentHTML('beforeend', `<div class="qd-fb">${right ? '✅ Correct! +10 XP' : `Not quite: the answer is <b>${esc(opts[ans])}</b>.`}<br><span class="mu">${esc(l[9])}</span>
          <div class="qd-next"><button class="btn p">${qi + 1 < quiz.length ? 'Next question →' : 'Finish'}</button> <a class="btn" href="#/lesson/${esc(l[0])}">Open the lesson</a></div></div>`);
        (body().querySelector('.qd-next .btn') as HTMLButtonElement).onclick = next;
      });
    };
    const finish = () => {
      const s = load(), firstToday = s.day !== today();
      s.day = today(); s.done = true; s.xp = xp; s.total += xp; s.days += firstToday ? 1 : 0; s.matched += matchedToday ? 1 : 0; s.hist = [...s.hist, today()].slice(-60);
      SS(KEY, s);
      try { ACT_('daily'); } catch { /* streak is optional */ }
      track('daily_done');
      box.innerHTML = header(s) + `<div class="qd-done"><div class="mu">Challenge complete</div><div class="big">+${xp} XP</div><div class="mu">🔥 ${streak()}-day streak. See you tomorrow!</div></div>`;
      burst(box.querySelector('.big') as Element);
      try { checkBadges(); } catch { /* badges are optional */ }
    };
    story ? showNews() : showQuiz(0);
  });
}

/** Compact "today's challenge" strip for the home page. */
function homeStrip(host: Element): void {
  const d = load(), done = d.day === today() && d.done, st = streak();
  host.insertAdjacentHTML('beforebegin', `<section id="qdhome"><div class="pan qdh"><span style="font-size:26px">${done ? '✅' : '🎯'}</span><div><b>${done ? 'Today’s challenge done' : 'Today’s 2-minute challenge'}</b><div class="mu">${done ? `+${d.xp} XP · come back tomorrow` : 'Read one real news story, make a call, then two quick questions.'}${st ? ` · 🔥 ${st}-day streak` : ''}</div></div><a class="btn ${done ? '' : 'p'}" href="#/learn">${done ? 'See progress' : 'Start'}</a></div></section>`);
}

export function initDaily(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qdaily-css">${CSS}</style>`);
  if (!SYNC.includes(KEY)) SYNC.push(KEY);
  BADGES.push(['daily1', '🎯', 'Challenger', 'Finish a daily challenge', () => load().days >= 1]);
  BADGES.push(['daily7', '🗓️', 'Habit builder', 'Finish 7 daily challenges', () => load().days >= 7]);
  BADGES.push(['reader2', '🧠', 'Market reader', 'Match the market’s move on 5 daily stories', () => load().matched >= 5]);
  const learn = PAGES.learn;
  PAGES.learn = (a?: string) => { learn(a); const host = document.querySelector('#m .body') as HTMLElement || document.getElementById('m'); if (host && !document.getElementById('qdaily')) renderDaily(host); };
  const home = PAGES.home;
  PAGES.home = (a?: string) => { home(a); const fst = document.querySelector('.hm section:last-child'); if (fst && !document.getElementById('qdhome')) homeStrip(fst); };
  // the page on screen was drawn before this module loaded: add to it without redrawing
  const host = document.querySelector('#m .body') as HTMLElement | null;
  if (/^#\/learn\/?$/.test(location.hash) && host && !document.getElementById('qdaily')) renderDaily(host);
  const fst = document.querySelector('.hm section:last-child');
  if (fst && !document.getElementById('qdhome')) homeStrip(fst);
}
