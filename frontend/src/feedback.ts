// Learning feedback: Duolingo-style sounds and small on-page effects for the Academy's exercises (no mascot).
//   - right answer: a short chime and sparkles from the button that was clicked;
//   - wrong answer: a soft "not quite" sound and a gentle shake of that button;
//   - finished exercise: a short fanfare and a sparkle burst.
// Sounds are made on the fly with the Web Audio API (no audio files) and only play after the visitor has clicked or
// typed. They can be switched off in Settings ("Sounds"). Reduced-motion users get the sounds without the motion.
// Used on learning pages only, never around trading ("gamify learning, never trading"). Other modules call
// window.qKes('correct' | 'wrong' | 'done'), kept under that name so existing callers keep working.

type Ev = 'correct' | 'wrong' | 'done' | 'hello' | 'tip';
const K_SOUND = 'q_snd';
const soundOn = () => LS<number>(K_SOUND, 1) !== 0;
const reduced = () => matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------- sounds (synthesised, a few hundred milliseconds each) ----------
let AC: AudioContext | null = null, interacted = false, lastEl: Element | null = null;
addEventListener('pointerdown', e => { interacted = true; lastEl = e.target as Element; }, { capture: true });
addEventListener('keydown', () => { interacted = true; lastEl = document.activeElement; }, { capture: true });
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

// ---------- on-page effects ----------
const CSS = `
.q-spark{position:fixed;z-index:61;pointer-events:none;font-size:16px;animation:q-up 1s ease-out forwards}
@keyframes q-up{to{transform:translate(var(--dx),var(--dy)) rotate(var(--r));opacity:0}}
@keyframes q-shake{0%,100%{transform:translateX(0)}20%{transform:translateX(-6px)}40%{transform:translateX(5px)}60%{transform:translateX(-4px)}80%{transform:translateX(2px)}}
.q-shake{animation:q-shake .45s ease}
@media (prefers-reduced-motion:reduce){.q-spark{display:none}.q-shake{animation:none}}`;

function origin(): DOMRect | null {
  const el = lastEl && (lastEl as Element).closest?.('button, a, .qd-o, .chip, label') || lastEl;
  return el && document.contains(el as Node) ? (el as Element).getBoundingClientRect() : null;
}
function sparks(n: number, r: DOMRect | null): void {
  if (reduced()) return;
  const x = r ? r.left + r.width / 2 : innerWidth / 2, y = r ? r.top + r.height / 2 : innerHeight / 2;
  for (let i = 0; i < n; i++) {
    const s = document.createElement('span'); s.className = 'q-spark'; s.textContent = ['✦', '★', '✧', '•'][i % 4];
    s.style.left = `${x}px`; s.style.top = `${y}px`; s.style.color = ['#c8a96a', '#e8c77a', '#4fd1a5'][i % 3];
    const a = Math.random() * Math.PI * 2, d = 40 + Math.random() * (n > 10 ? 120 : 60);
    s.style.setProperty('--dx', `${Math.round(Math.cos(a) * d)}px`); s.style.setProperty('--dy', `${Math.round(Math.sin(a) * d - 30)}px`); s.style.setProperty('--r', `${Math.round(Math.random() * 200)}deg`);
    document.body.appendChild(s); setTimeout(() => s.remove(), 1100);
  }
}

export function react(ev: Ev): void {
  if (ev === 'correct') { sound('correct'); sparks(8, origin()); }
  else if (ev === 'wrong') {
    sound('wrong');
    const el = lastEl && (lastEl as Element).closest?.('button, .qd-o, .chip');
    if (el && !reduced()) { el.classList.remove('q-shake'); void (el as HTMLElement).offsetWidth; el.classList.add('q-shake'); setTimeout(() => el.classList.remove('q-shake'), 500); }
  } else if (ev === 'done') { sound('done'); sparks(18, origin()); }
}

// the lesson quizzes (older code) write "Correct." / "Not quite." into #qw<n>: watch for that
function watchLessonQuiz(): void {
  const m = document.getElementById('m'); if (!m) return;
  new MutationObserver(muts => {
    for (const mu of muts) {
      const t = mu.target as HTMLElement;
      if (t.nodeType !== 1 || !/^qw\d+$/.test(t.id)) continue;
      const s = t.querySelector('span'); if (!s) continue;
      if (s.classList.contains('up')) react('correct'); else if (s.classList.contains('dn')) react('wrong');
    }
  }).observe(m, { childList: true, subtree: true });
}

// a Settings switch for the sounds
function settingsRow(): void {
  if (!/^#\/settings\b/.test(location.hash)) return;
  const m = document.getElementById('m'); if (!m || m.querySelector('#qsnd-set')) return;
  const anchor = m.querySelector('.pan:last-of-type') || m.lastElementChild; if (!anchor) return;
  anchor.insertAdjacentHTML('afterend', `<div class="pan" id="qsnd-set"><h3 style="margin-top:0">Sounds</h3><p class="mu" style="margin:0 0 10px">Short sounds when you answer a question in the Academy.</p>
    <label style="display:flex;gap:10px;align-items:center;margin:6px 0"><input type="checkbox" id="qsnd-s" ${soundOn() ? 'checked' : ''}> Play sounds</label></div>`);
  (m.querySelector('#qsnd-s') as HTMLInputElement).onchange = e => { SS(K_SOUND, (e.target as HTMLInputElement).checked ? 1 : 0); if ((e.target as HTMLInputElement).checked) sound('correct'); };
}

export function initKes(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qfx-css">${CSS}</style>`);
  if (!SYNC.includes(K_SOUND)) SYNC.push(K_SOUND);
  try { localStorage.removeItem('q_kes'); } catch { /* the mascot setting no longer exists */ }
  (window as any).qKes = (ev: Ev) => react(ev);
  addEventListener('hashchange', () => setTimeout(settingsRow, 50));
  const m = document.getElementById('m');
  if (m) { let t = 0; new MutationObserver(() => { clearTimeout(t); t = window.setTimeout(settingsRow, 80); }).observe(m, { childList: true }); }
  watchLessonQuiz();
}
