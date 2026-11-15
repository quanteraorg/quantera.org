// Quinn, Quantera's guide: a friendly young research analyst (navy blazer, gold tie, gold-rimmed glasses). He gives a
// thumbs-up with a green up-arrow on a right answer, frowns with a red down-arrow on a wrong one, and celebrates when
// an exercise is finished. Quinn lives on the learning pages only (Academy, lessons, challenges, exercises), never near
// trading, in line with the rule "gamify learning, never trading". Quinn:
//   - says hello with a tip, blinks and looks around while idle, and offers a tip when tapped;
//   - plays short sounds made on the fly with the Web Audio API (no audio files), only after the visitor has interacted.
// Sounds and Quinn can each be switched off from Quinn's bubble or in Settings. Reduced-motion users get Quinn without the
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
  <g class="k-body">
    <path d="M14 120 C16 94 34 84 60 84 C86 84 104 94 106 120 Z" fill="#1c2a44"/>
    <path d="M48 85 L60 106 L72 85 Z" fill="#f2eee6"/>
    <path d="M57 90 L63 90 L65 108 L60 115 L55 108 Z" fill="#c8a96a"/><path d="M57 90 L63 90 L62 94 L58 94 Z" fill="#9c7a3c"/>
    <path d="M48 85 L56 104 L46 98 Z M72 85 L64 104 L74 98 Z" fill="#27375a"/>
    <rect x="53" y="70" width="14" height="16" rx="5" fill="#c99271"/>
    <g class="k-head">
      <circle cx="37" cy="53" r="5.5" fill="#c99271"/><circle cx="83" cy="53" r="5.5" fill="#c99271"/>
      <ellipse cx="60" cy="50" rx="23" ry="26" fill="#d9a67e"/>
      <path d="M36 46 C35 26 50 18 64 20 C79 22 87 32 84 46 C80 38 70 33 60 35 C51 36 42 40 36 46 Z" fill="#2a211c"/>
      <path d="M58 22 C66 18 76 22 78 30 C72 26 64 25 58 28 Z" fill="#3a2e26"/>
      <g class="k-brows"><path d="M44 41 q6 -3 12 0" stroke="#2a211c" stroke-width="2.6" fill="none" stroke-linecap="round"/><path d="M64 41 q6 -3 12 0" stroke="#2a211c" stroke-width="2.6" fill="none" stroke-linecap="round"/></g>
      <g class="k-eyes"><circle cx="50" cy="51" r="2.9" fill="#121820"/><circle cx="70" cy="51" r="2.9" fill="#121820"/><circle cx="51" cy="50" r="1" fill="#fff"/><circle cx="71" cy="50" r="1" fill="#fff"/></g>
      <g class="k-lids"><rect x="45" y="46" width="10" height="0" class="k-lidfill" fill="#d9a67e"/><rect x="65" y="46" width="10" height="0" class="k-lidfill" fill="#d9a67e"/></g>
      <g fill="none" stroke="#c8a96a" stroke-width="2.2"><circle cx="50" cy="51" r="8"/><circle cx="70" cy="51" r="8"/><path d="M58 51 h4 M42 50 l-5 -1 M78 50 l5 -1"/></g>
      <path d="M59 56 q1 5 -1 7" stroke="#b07c5c" stroke-width="1.8" fill="none" stroke-linecap="round"/>
      <path class="k-smile" d="M51 66 q9 7 18 0" stroke="#7a3b2e" stroke-width="2.6" fill="none" stroke-linecap="round"/>
      <path class="k-frown" d="M52 69 q8 -5 16 0" stroke="#7a3b2e" stroke-width="2.6" fill="none" stroke-linecap="round"/>
    </g>
    <g class="k-thumb"><path d="M86 96 c0 -8 3 -12 6 -18 c2 -4 7 -3 6 2 l-2 8 h7 c4 0 5 4 3 6 c2 2 1 5 -1 6 c1 3 -1 5 -3 5 h-12 c-3 0 -4 -2 -4 -4 Z" fill="#d9a67e" stroke="#b07c5c" stroke-width="1.4"/><rect x="80" y="94" width="8" height="14" rx="2" fill="#1c2a44"/></g>
    <g class="k-badge"><circle cx="100" cy="22" r="13" class="k-badgebg" fill="#4fd1a5"/><path class="k-up" d="M100 14 l7 9 h-4.5 v7 h-5 v-7 h-4.5 Z" fill="#06080c"/><path class="k-down" d="M100 30 l7 -9 h-4.5 v-7 h-5 v7 h-4.5 Z" fill="#06080c"/></g>
  </g>
</svg>`;

const CSS = `
#kes{position:fixed;right:18px;bottom:18px;z-index:60;display:flex;align-items:flex-end;gap:10px;pointer-events:none}
#kes .k-bird{width:78px;height:78px;pointer-events:auto;cursor:pointer;border:0;background:none;padding:0;filter:drop-shadow(0 6px 14px rgba(0,0,0,.35));transform-origin:50% 100%}
#kes .k-bird:focus-visible{outline:2px solid var(--ac,#c8a96a);outline-offset:4px;border-radius:50%}
#kes .k-bird svg{width:100%;height:100%;overflow:visible}
#kes .k-frown{display:none}#kes .k-thumb,#kes .k-badge{opacity:0;transform:scale(.6);transform-box:fill-box;transform-origin:50% 100%;transition:opacity .2s,transform .25s}#kes.correct .k-thumb,#kes.done .k-thumb{opacity:1;transform:none}#kes.correct .k-badge,#kes.done .k-badge,#kes.wrong .k-badge{opacity:1;transform:none}#kes .k-down{display:none}#kes.wrong .k-up{display:none}#kes.wrong .k-down{display:inline}#kes.wrong .k-badgebg{fill:#e86a6a}#kes.wrong .k-smile{display:none}#kes.wrong .k-frown{display:inline}#kes.wrong .k-brows{transform:translateY(2px) rotate(0)}
#kes .k-bub{pointer-events:auto;max-width:250px;background:var(--s1,#0d1118);color:var(--tx);border:1px solid rgba(var(--acc),.45);border-radius:16px 16px 4px 16px;padding:10px 12px 8px;font-size:13.5px;line-height:1.45;box-shadow:0 10px 30px rgba(0,0,0,.35);opacity:0;transform:translateY(6px) scale(.97);transition:opacity .2s,transform .2s;visibility:hidden}
#kes.talk .k-bub{opacity:1;transform:none;visibility:visible}
#kes .k-bub b{color:var(--ac,#c8a96a)}
#kes .k-ctl{display:flex;gap:6px;justify-content:flex-end;margin-top:6px}
#kes .k-ctl button{border:1px solid var(--ln);background:transparent;color:var(--mu);border-radius:999px;font:inherit;font-size:11.5px;padding:2px 9px;cursor:pointer}
#kes .k-ctl button:hover{color:var(--tx)}
@media (max-width:800px){#kes{bottom:calc(92px + env(safe-area-inset-bottom,0px));right:10px}#kes .k-bird{width:62px;height:62px}#kes .k-bub{max-width:210px;font-size:13px}}
@media print{#kes{display:none}}
/* motion */
@keyframes k-blink{0%,92%,100%{height:0}95%{height:10px}}
#kes .k-lids rect{animation:k-blink 4.6s infinite}
@keyframes k-hop{0%,100%{transform:translateY(0)}30%{transform:translateY(-16px) scale(1.04,.96)}55%{transform:translateY(0) scale(.96,1.04)}75%{transform:translateY(-6px)}}
@keyframes k-flap{0%,100%{transform:translateY(0)}25%{transform:translateY(-7px)}50%{transform:translateY(1px)}75%{transform:translateY(-5px)}}
@keyframes k-droop{0%{transform:rotate(0)}30%{transform:rotate(-8deg) translateY(4px)}100%{transform:rotate(0)}}
@keyframes k-spin{0%{transform:rotate(0) translateY(0)}40%{transform:rotate(-12deg) translateY(-18px)}70%{transform:rotate(12deg) translateY(-10px)}100%{transform:rotate(0) translateY(0)}}
@keyframes k-tilt{0%,100%{transform:rotate(0)}50%{transform:rotate(-9deg)}}
@keyframes k-look{0%,100%{transform:translateX(0)}30%,60%{transform:translateX(3px)}}
#kes .k-head,#kes .k-eyes{transform-box:fill-box;transform-origin:50% 100%}
#kes .k-head{transform-origin:50% 100%}
#kes.correct .k-bird{animation:k-hop .7s ease}
#kes.wrong .k-bird{animation:k-droop .9s ease}#kes.wrong .k-brows{transform:translateY(3px)}
#kes.done .k-bird{animation:k-spin .9s ease 2}
#kes.idle-tilt .k-head{animation:k-tilt 1.6s ease}#kes.idle-look .k-eyes{animation:k-look 1.8s ease}
.k-spark{position:fixed;z-index:61;pointer-events:none;font-size:16px;animation:k-up 1s ease-out forwards}
@keyframes k-up{to{transform:translate(var(--dx),-70px) rotate(var(--r));opacity:0}}
@media (prefers-reduced-motion:reduce){#kes .k-bird,#kes .k-head,#kes .k-eyes{animation:none!important}#kes .k-bub{transition:none}.k-spark{display:none}}`;

const SAY: Record<Ev, string[]> = {
  hello: ['Hi, I’m <b>Quinn</b>, Quantera’s research analyst. Ready to practise?', 'Back again? Let’s sharpen those research instincts.', 'Tip: try the <b>market reaction challenge</b>. Same news, different moves!', 'Small steps add up. One lesson today is a win.'],
  correct: ['Thumbs up! <b>Correct.</b>', 'Nailed it!', 'That’s how a researcher reads it.', 'Yes! Keep that streak going.', 'Spot on.'],
  wrong: ['Close! Read the explanation below; that’s where the learning is.', 'Not quite. Even pros misread the market sometimes.', 'Hmm, not this time. Check why, then try the next one.'],
  done: ['All done! 🎉 Great session.', 'Finished! Come back tomorrow to keep your streak.', 'That’s a wrap. You read real data like a researcher today.'],
  tip: ['A stock’s <b>beta</b> tells you how much it usually moves with the whole market.', 'Smaller companies often react <b>more</b> to the same news.', '<b>Free cash flow</b> is the cash left after a company invests in itself.', 'A high <b>P/E</b> means investors expect lots of future growth.', 'One headline rarely explains a whole day’s move. Check the market too.', 'Always ask: <b>what did investors already expect?</b>', 'Compare a move to the stock’s <b>normal day</b> before calling it big.', 'SEC filings are the primary source for a company’s numbers.'],
};
const pick = (a: string[]) => a[Math.floor(Math.random() * a.length)];

let host: HTMLElement | null = null, hideT = 0, idleT = 0, stateT = 0;
function build(): HTMLElement {
  const el = document.createElement('div'); el.id = 'kes';
  el.innerHTML = `<div class="k-bub" role="status" aria-live="polite"><div class="k-txt"></div><div class="k-ctl"><button data-k="snd"></button><button data-k="off" title="Hide Quinn (bring Quinn back in Settings)">Hide</button></div></div>
    <button class="k-bird" aria-label="Quinn, Quantera's guide: tap for a tip">${SVG}</button>`;
  document.body.appendChild(el);
  const snd = el.querySelector<HTMLButtonElement>('[data-k="snd"]')!;
  const label = () => { snd.textContent = soundOn() ? '🔊 Sound on' : '🔈 Sound off'; snd.setAttribute('aria-pressed', String(soundOn())); };
  label();
  snd.onclick = () => { SS(K_SOUND, soundOn() ? 0 : 1); label(); if (soundOn()) sound('tap'); };
  el.querySelector<HTMLButtonElement>('[data-k="off"]')!.onclick = () => { SS(K_KES, 0); el.remove(); host = null; qtoast('Quinn is hiding. Bring Quinn back any time in Settings.'); };
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

// a Settings row so Quinn and the sounds can be switched back on
function settingsRow(): void {
  if (!/^#\/settings\b/.test(location.hash)) return;
  const m = document.getElementById('m'); if (!m || m.querySelector('#qkes-set')) return;
  const anchor = m.querySelector('.pan:last-of-type') || m.lastElementChild; if (!anchor) return;
  anchor.insertAdjacentHTML('afterend', `<div class="pan" id="qkes-set"><h3 style="margin-top:0">Quinn and sounds</h3><p class="mu" style="margin:0 0 10px">Quinn, our guide, cheers you on in the Academy. Sounds play when you answer a question.</p>
    <label style="display:flex;gap:10px;align-items:center;margin:6px 0"><input type="checkbox" id="qkes-k" ${kesOn() ? 'checked' : ''}> Show Quinn on learning pages</label>
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
