// Kes, Quantera's mascot: a small gold kestrel (a falcon known for spotting things from far away, which is what good
// research does). Kes lives on the learning pages only (Academy, lessons, challenges, exercises), never near trading,
// in line with the rule "gamify learning, never trading". Kes:
//   - says hello with a tip, blinks and looks around while idle, and offers a tip when tapped;
//   - cheers and flaps on a right answer, droops on a wrong one, celebrates when an exercise is finished;
//   - plays short sounds made on the fly with the Web Audio API (no audio files), only after the visitor has interacted.
// Sounds and Kes can each be switched off from Kes's bubble or in Settings. Reduced-motion users get Kes without the
// bouncing. Other modules call window.qKes('correct' | 'wrong' | 'done' | 'hello', optional text).

type Ev = 'correct' | 'wrong' | 'done' | 'hello' | 'tip';
const PAGES_ON = /^#\/(learn|lesson|challenge|statex|glossary|chartschool|gym)\b|^#\/learn/;
const K_SOUND = 'q_snd', K_KES = 'q_kes';
const soundOn = () => LS<number>(K_SOUND, 1) !== 0, kesOn = () => LS<number>(K_KES, 1) !== 0;
const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------- sounds (synthesised, a few hundred milliseconds each) ----------
let AC: AudioContext | null = null, interacted = false;
addEventListener('pointerdown', () => { interacted = true; }, { once: true, capture: true });
addEventListener('keydown', () => { interacted = true; }, { once: true, capture: true });
function tone(freq: number, start: number, dur: number, type: OscillatorType = 'sine', vol = 0.14, slideTo?: number): void {
  if (!AC) return;
  const o = AC.createOscillator(), g = AC.createGain(), t = AC.currentTime + start;
  o.type = type; o.frequency.setValueAtTime(freq, t); if (slideTo) o.frequency.exponentialRampToValueAtTime(slideTo, t + dur);
  g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(vol, t + 0.015); g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
  o.connect(g).connect(AC.destination); o.start(t); o.stop(t + dur + 0.05);
}
export function sound(kind: 'correct' | 'wrong' | 'done' | 'tap'): void {
  if (!soundOn() || !interacted) return;
  try { AC ||= new (window.AudioContext || (window as any).webkitAudioContext)(); if (AC.state === 'suspended') AC.resume(); } catch { return; }
  if (kind === 'correct') { tone(659.3, 0, 0.12, 'triangle'); tone(987.8, 0.09, 0.22, 'triangle'); tone(1318.5, 0.09, 0.22, 'sine', 0.05); }
  else if (kind === 'wrong') { tone(311.1, 0, 0.16, 'triangle', 0.12, 277.2); tone(233.1, 0.13, 0.26, 'triangle', 0.12, 207.7); }
  else if (kind === 'done') { [523.3, 659.3, 784, 1046.5].forEach((f, i) => tone(f, i * 0.09, 0.22, 'triangle', 0.12)); tone(1568, 0.38, 0.4, 'sine', 0.05); }
  else tone(880, 0, 0.05, 'sine', 0.05);
}

// ---------- the bird ----------
const SVG = `<svg viewBox="0 0 120 120" aria-hidden="true">
  <g class="k-tail"><path d="M40 86 L18 110 L28 113 L24 118 L46 98 Z" fill="#6f8193"/><path d="M22 106 L30 109" stroke="#121820" stroke-width="3" stroke-linecap="round"/></g>
  <g class="k-body">
    <path d="M36 94 C26 72 38 50 62 46 C84 42 98 58 94 78 C90 98 66 106 48 102 Z" fill="#c8a96a"/>
    <path d="M60 64 C76 62 90 70 90 82 C84 96 66 102 52 98 C58 88 60 76 60 64 Z" fill="#f2e6cf"/>
    <g fill="#9c7a3c"><path d="M72 76 l2 4 l-2 2 l-2 -2 Z"/><path d="M80 84 l2 4 l-2 2 l-2 -2 Z"/><path d="M68 90 l2 4 l-2 2 l-2 -2 Z"/></g>
    <g class="k-wing"><path d="M38 62 C54 56 70 64 74 82 C64 94 48 98 34 94 C30 82 32 70 38 62 Z" fill="#9c7a3c"/>
      <g fill="#121820" opacity=".75"><circle cx="48" cy="70" r="2.2"/><circle cx="58" cy="74" r="2.2"/><circle cx="46" cy="82" r="2.2"/><circle cx="57" cy="86" r="2.2"/><circle cx="66" cy="80" r="2"/></g>
      <path d="M36 92 C48 96 62 94 72 86" stroke="#6f8193" stroke-width="3" fill="none" stroke-linecap="round"/></g>
    <path d="M56 102 l-2 9 M62 102 l1 9 M54 111 h-5 M63 111 h5" stroke="#e8a33d" stroke-width="3" stroke-linecap="round"/>
  </g>
  <g class="k-head">
    <circle cx="74" cy="38" r="21" fill="#c8a96a"/>
    <path d="M54 34 C56 20 70 14 82 17 C92 20 96 28 95 34 C84 28 66 28 54 34 Z" fill="#6f8193"/>
    <path d="M70 34 C78 30 90 34 92 44 C88 52 76 54 70 48 Z" fill="#f2e6cf"/>
    <path d="M77 46 C76 52 73 56 69 58" stroke="#121820" stroke-width="3.4" fill="none" stroke-linecap="round"/>
    <g class="k-eyes"><circle cx="80" cy="38" r="6.4" fill="#121820" stroke="#e8a33d" stroke-width="2.2"/><circle cx="82.2" cy="35.8" r="1.9" fill="#fff"/></g>
    <g class="k-lids"><rect x="72" y="30" width="16" height="0" fill="#c8a96a"/></g>
    <path class="k-brows" d="M72 29 q8 -4 15 0" stroke="#3f4b58" stroke-width="2.6" fill="none" stroke-linecap="round"/>
    <path d="M92 36 q4 -2 7 1 l-6 4 Z" fill="#e8a33d"/>
    <path d="M96 37 q9 1 9 9 q-1 2 -3 1 q-1 -4 -7 -5 Z" fill="#2a2f38"/>
  </g>
</svg>`;

const CSS = `
#kes{position:fixed;right:18px;bottom:18px;z-index:60;display:flex;align-items:flex-end;gap:10px;pointer-events:none}
#kes .k-bird{width:78px;height:78px;pointer-events:auto;cursor:pointer;border:0;background:none;padding:0;filter:drop-shadow(0 6px 14px rgba(0,0,0,.35));transform-origin:50% 100%}
#kes .k-bird:focus-visible{outline:2px solid var(--ac,#c8a96a);outline-offset:4px;border-radius:50%}
#kes .k-bird svg{width:100%;height:100%;overflow:visible}
#kes .k-bub{pointer-events:auto;max-width:250px;background:var(--s1,#0d1118);color:var(--tx);border:1px solid rgba(var(--acc),.45);border-radius:16px 16px 4px 16px;padding:10px 12px 8px;font-size:13.5px;line-height:1.45;box-shadow:0 10px 30px rgba(0,0,0,.35);opacity:0;transform:translateY(6px) scale(.97);transition:opacity .2s,transform .2s;visibility:hidden}
#kes.talk .k-bub{opacity:1;transform:none;visibility:visible}
#kes .k-bub b{color:var(--ac,#c8a96a)}
#kes .k-ctl{display:flex;gap:6px;justify-content:flex-end;margin-top:6px}
#kes .k-ctl button{border:1px solid var(--ln);background:transparent;color:var(--mu);border-radius:999px;font:inherit;font-size:11.5px;padding:2px 9px;cursor:pointer}
#kes .k-ctl button:hover{color:var(--tx)}
@media (max-width:800px){#kes{bottom:calc(92px + env(safe-area-inset-bottom,0px));right:10px}#kes .k-bird{width:62px;height:62px}#kes .k-bub{max-width:210px;font-size:13px}}
@media print{#kes{display:none}}
/* motion */
@keyframes k-blink{0%,92%,100%{height:0}95%{height:16px}}
#kes .k-lids rect{animation:k-blink 4.6s infinite}
@keyframes k-hop{0%,100%{transform:translateY(0)}30%{transform:translateY(-16px) scale(1.04,.96)}55%{transform:translateY(0) scale(.96,1.04)}75%{transform:translateY(-6px)}}
@keyframes k-flap{0%,100%{transform:rotate(0)}25%{transform:rotate(-28deg)}50%{transform:rotate(6deg)}75%{transform:rotate(-22deg)}}
@keyframes k-droop{0%{transform:rotate(0)}30%{transform:rotate(-8deg) translateY(4px)}100%{transform:rotate(0)}}
@keyframes k-spin{0%{transform:rotate(0) translateY(0)}40%{transform:rotate(-12deg) translateY(-18px)}70%{transform:rotate(12deg) translateY(-10px)}100%{transform:rotate(0) translateY(0)}}
@keyframes k-tilt{0%,100%{transform:rotate(0)}50%{transform:rotate(-9deg)}}
@keyframes k-look{0%,100%{transform:translateX(0)}30%,60%{transform:translateX(3px)}}
#kes .k-wing,#kes .k-head,#kes .k-eyes{transform-box:fill-box;transform-origin:70% 20%}
#kes .k-head{transform-origin:30% 90%}
#kes.correct .k-bird{animation:k-hop .7s ease}#kes.correct .k-wing{animation:k-flap .6s ease 2}
#kes.wrong .k-bird{animation:k-droop .9s ease}#kes.wrong .k-brows{transform:translateY(3px)}
#kes.done .k-bird{animation:k-spin .9s ease 2}#kes.done .k-wing{animation:k-flap .5s ease 4}
#kes.idle-tilt .k-head{animation:k-tilt 1.6s ease}#kes.idle-look .k-eyes{animation:k-look 1.8s ease}
.k-spark{position:fixed;z-index:61;pointer-events:none;font-size:16px;animation:k-up 1s ease-out forwards}
@keyframes k-up{to{transform:translate(var(--dx),-70px) rotate(var(--r));opacity:0}}
@media (prefers-reduced-motion:reduce){#kes .k-bird,#kes .k-wing,#kes .k-head,#kes .k-eyes{animation:none!important}#kes .k-bub{transition:none}.k-spark{display:none}}`;

const SAY: Record<Ev, string[]> = {
  hello: ['Hi, I’m <b>Kes</b>! I spot what moves markets. Ready to practise?', 'Back again? Let’s sharpen those research instincts.', 'Tip: try the <b>market reaction challenge</b>. Same news, different moves!', 'Small steps add up. One lesson today is a win.'],
  correct: ['Sharp eyes! <b>Correct.</b>', 'Nailed it!', 'That’s how a researcher reads it.', 'Yes! Keep that streak going.', 'Spot on.'],
  wrong: ['Close! Read the explanation below; that’s where the learning is.', 'Not quite. Even pros misread the market sometimes.', 'Hmm, not this time. Check why, then try the next one.'],
  done: ['All done! 🎉 Great session.', 'Finished! Come back tomorrow to keep your streak.', 'That’s a wrap. You read real data like a researcher today.'],
  tip: ['A stock’s <b>beta</b> tells you how much it usually moves with the whole market.', 'Smaller companies often react <b>more</b> to the same news.', '<b>Free cash flow</b> is the cash left after a company invests in itself.', 'A high <b>P/E</b> means investors expect lots of future growth.', 'One headline rarely explains a whole day’s move. Check the market too.', 'Always ask: <b>what did investors already expect?</b>', 'Compare a move to the stock’s <b>normal day</b> before calling it big.', 'SEC filings are the primary source for a company’s numbers.'],
};
const pick = (a: string[]) => a[Math.floor(Math.random() * a.length)];

let host: HTMLElement | null = null, hideT = 0, idleT = 0, stateT = 0;
function build(): HTMLElement {
  const el = document.createElement('div'); el.id = 'kes';
  el.innerHTML = `<div class="k-bub" role="status" aria-live="polite"><div class="k-txt"></div><div class="k-ctl"><button data-k="snd"></button><button data-k="off" title="Hide Kes (bring Kes back in Settings)">Hide</button></div></div>
    <button class="k-bird" aria-label="Kes, Quantera's mascot: tap for a tip">${SVG}</button>`;
  document.body.appendChild(el);
  const snd = el.querySelector<HTMLButtonElement>('[data-k="snd"]')!;
  const label = () => { snd.textContent = soundOn() ? '🔊 Sound on' : '🔈 Sound off'; snd.setAttribute('aria-pressed', String(soundOn())); };
  label();
  snd.onclick = () => { SS(K_SOUND, soundOn() ? 0 : 1); label(); if (soundOn()) sound('tap'); };
  el.querySelector<HTMLButtonElement>('[data-k="off"]')!.onclick = () => { SS(K_KES, 0); el.remove(); host = null; qtoast('Kes is hiding. Bring Kes back any time in Settings.'); };
  el.querySelector<HTMLButtonElement>('.k-bird')!.onclick = () => { sound('tap'); react('tip'); };
  return el;
}

function say(html: string, ms = 4200): void {
  if (!host) return;
  (host.querySelector('.k-txt') as HTMLElement).innerHTML = html;
  host.classList.add('talk'); clearTimeout(hideT); hideT = window.setTimeout(() => host?.classList.remove('talk'), ms);
}

function sparks(): void {
  if (!host || reduced()) return;
  const r = host.querySelector('.k-bird')!.getBoundingClientRect();
  for (let i = 0; i < 7; i++) {
    const s = document.createElement('span'); s.className = 'k-spark'; s.textContent = pick(['✦', '★', '✧', '•']);
    s.style.left = `${r.left + r.width / 2}px`; s.style.top = `${r.top + 10}px`; s.style.color = pick(['#c8a96a', '#e8c77a', '#4fd1a5']);
    s.style.setProperty('--dx', `${Math.round(Math.random() * 120 - 60)}px`); s.style.setProperty('--r', `${Math.round(Math.random() * 180)}deg`);
    document.body.appendChild(s); setTimeout(() => s.remove(), 1100);
  }
}

export function react(ev: Ev, text?: string): void {
  if (ev === 'correct' || ev === 'wrong' || ev === 'done') sound(ev);
  if (!host) return;
  host.classList.remove('correct', 'wrong', 'done', 'idle-tilt', 'idle-look'); void host.offsetWidth;
  if (ev !== 'hello' && ev !== 'tip') { host.classList.add(ev); clearTimeout(stateT); stateT = window.setTimeout(() => host?.classList.remove(ev), 2000); }
  if (ev === 'correct' || ev === 'done') sparks();
  say(text || pick(SAY[ev]), ev === 'tip' || ev === 'hello' ? 5200 : 3600);
}

function idle(): void {
  clearTimeout(idleT);
  idleT = window.setTimeout(() => { if (host && !reduced()) { const c = Math.random() < 0.5 ? 'idle-tilt' : 'idle-look'; host.classList.add(c); setTimeout(() => host?.classList.remove(c), 1900); } idle(); }, 6000 + Math.random() * 6000);
}

let greeted = '';
function place(): void {
  const on = PAGES_ON.test(location.hash) && kesOn();
  if (on && !host) { host = build(); idle(); }
  if (!on && host) { host.remove(); host = null; clearTimeout(idleT); }
  const r = location.hash.split('/')[1] || '';
  if (on && greeted !== r) { greeted = r; setTimeout(() => react('hello'), 700); }
}

// the lesson quizzes (older code) write "Correct." / "Not quite." into #qw<n>: watch for that
function watchLessonQuiz(): void {
  const m = document.getElementById('m'); if (!m) return;
  new MutationObserver(muts => {
    for (const mu of muts) {
      const t = mu.target as HTMLElement;
      const box = t.nodeType === 1 && /^qw\d+$/.test(t.id) ? t : null;
      if (!box) continue;
      const s = box.querySelector('span'); if (!s) continue;
      if (s.classList.contains('up')) react('correct'); else if (s.classList.contains('dn')) react('wrong');
    }
  }).observe(m, { childList: true, subtree: true });
}

// a Settings row so Kes and the sounds can be switched back on
function settingsRow(): void {
  if (!/^#\/settings\b/.test(location.hash)) return;
  const m = document.getElementById('m'); if (!m || m.querySelector('#qkes-set')) return;
  const anchor = m.querySelector('.pan:last-of-type') || m.lastElementChild; if (!anchor) return;
  anchor.insertAdjacentHTML('afterend', `<div class="pan" id="qkes-set"><h3 style="margin-top:0">Kes and sounds</h3><p class="mu" style="margin:0 0 10px">Kes, our mascot, cheers you on in the Academy. Sounds play when you answer a question.</p>
    <label style="display:flex;gap:10px;align-items:center;margin:6px 0"><input type="checkbox" id="qkes-k" ${kesOn() ? 'checked' : ''}> Show Kes on learning pages</label>
    <label style="display:flex;gap:10px;align-items:center;margin:6px 0"><input type="checkbox" id="qkes-s" ${soundOn() ? 'checked' : ''}> Play sounds</label></div>`);
  (m.querySelector('#qkes-k') as HTMLInputElement).onchange = e => { SS(K_KES, (e.target as HTMLInputElement).checked ? 1 : 0); };
  (m.querySelector('#qkes-s') as HTMLInputElement).onchange = e => { SS(K_SOUND, (e.target as HTMLInputElement).checked ? 1 : 0); if ((e.target as HTMLInputElement).checked) sound('correct'); };
}

export function initKes(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="kes-css">${CSS}</style>`);
  for (const k of [K_SOUND, K_KES]) if (!SYNC.includes(k)) SYNC.push(k);
  (window as any).qKes = (ev: Ev, text?: string) => react(ev, text);
  addEventListener('hashchange', () => setTimeout(() => { place(); settingsRow(); }, 50));
  const m = document.getElementById('m');
  if (m) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(settingsRow, 80); }).observe(m, { childList: true }); }
  place(); watchLessonQuiz();
}
