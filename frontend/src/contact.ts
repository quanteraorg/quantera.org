// Feedback form: "Send feedback" in the footer, and "Spot a wrong number?" links next to financial data.
// Sends to /api/feedback, which validates, rate-limits and stores it server-side only (Supabase table feedback,
// no public access). The page the person was on is attached so a data report points at the right company.
// Any element with data-fb="bug|data|idea|other" opens the form with that choice preselected.

const KINDS: [string, string][] = [['data', 'A number looks wrong'], ['bug', 'Something is broken'], ['idea', 'An idea or request'], ['other', 'Something else']];
const CSS = `
#qfb{border:1px solid var(--ln);border-radius:16px;background:var(--bg);color:var(--tx);padding:0;width:min(520px,calc(100vw - 32px));max-height:calc(100vh - 32px)}
#qfb::backdrop{background:rgba(0,0,0,.55)}
#qfb form{padding:20px 22px;display:flex;flex-direction:column;gap:12px}
#qfb h3{margin:0;font-size:20px}#qfb p{margin:0;color:var(--mu);font-size:13.5px;line-height:1.5}
#qfb label{display:flex;flex-direction:column;gap:5px;font-size:13px;color:var(--mu)}
#qfb select,#qfb textarea,#qfb input{font:inherit;font-size:14.5px;padding:9px 11px!important;color:var(--tx)}
#qfb textarea{min-height:120px;resize:vertical}
#qfb .hp{position:absolute;left:-9999px;width:1px;height:1px;overflow:hidden}
#qfb .row{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap}
#qfb .cnt{font-size:12px;color:var(--mu)}#qfb .msg{font-size:13.5px;min-height:1em}#qfb .msg.err{color:var(--dn)}#qfb .msg.ok{color:var(--up)}
#qfb .btns{display:flex;gap:8px}#qfb .btn{margin:0}
.qfb-link{background:none;border:0;padding:0;font:inherit;color:var(--mu);text-decoration:underline;text-underline-offset:2px;cursor:pointer}
.qfb-link:hover{color:var(--tx)}`;

function open(kind = 'data'): void {
  let d = document.getElementById('qfb') as HTMLDialogElement | null;
  if (!d) {
    document.body.insertAdjacentHTML('beforeend', `<dialog id="qfb" aria-labelledby="qfb-h"><form method="dialog" novalidate>
<h3 id="qfb-h">Send feedback</h3>
<p>Tell us about a wrong number, something that doesn't work, or an idea. The page you're on is attached so we can find it.</p>
<label>What is it about?<select name="kind">${KINDS.map(([v, t]) => `<option value="${v}">${t}</option>`).join('')}</select></label>
<label>Your message<textarea name="message" maxlength="2000" required placeholder="For a wrong number: which figure, what you expected, and where you saw the right one."></textarea></label>
<div class="row"><span class="cnt" aria-live="polite">0 / 2,000</span></div>
<label>Email (optional, only if you'd like a reply)<input name="email" type="email" autocomplete="email" maxlength="254"></label>
<div class="hp" aria-hidden="true"><label>Website<input name="website" tabindex="-1" autocomplete="off"></label></div>
<div class="msg" role="status"></div>
<div class="row"><span class="cnt">We store your message securely and never show it publicly.</span><div class="btns"><button type="button" class="btn" data-x>Cancel</button><button type="submit" class="btn p">Send</button></div></div>
</form></dialog>`);
    d = document.getElementById('qfb') as HTMLDialogElement;
    const f = d.querySelector('form')!, ta = f.message as HTMLTextAreaElement, cnt = f.querySelector('.cnt')!, msg = f.querySelector('.msg') as HTMLElement, send = f.querySelector('[type=submit]') as HTMLButtonElement;
    ta.addEventListener('input', () => { cnt.textContent = `${ta.value.length.toLocaleString()} / 2,000`; });
    f.querySelector('[data-x]')!.addEventListener('click', () => d!.close());
    f.addEventListener('submit', async e => {
      e.preventDefault();
      const body = { kind: (f.kind as HTMLSelectElement).value, message: ta.value.trim(), email: (f.email as HTMLInputElement).value.trim(), website: (f.website as HTMLInputElement).value, page: (location.hash || location.pathname).slice(0, 200) };
      if (body.message.length < 5) { msg.className = 'msg err'; msg.textContent = 'Please write a little more (at least 5 characters).'; ta.focus(); return; }
      send.disabled = true; msg.className = 'msg'; msg.textContent = 'Sending…';
      try {
        const r = await fetch('/api/feedback', { method: 'POST', headers: { 'content-type': 'application/json' }, body: JSON.stringify(body) });
        const j = await r.json().catch(() => ({}));
        if (r.ok) { msg.className = 'msg ok'; msg.textContent = 'Thank you, it has been sent.'; ta.value = ''; cnt.textContent = '0 / 2,000'; try { track('feedback_sent'); } catch { /* optional */ } setTimeout(() => d!.open && d!.close(), 1600); }
        else { msg.className = 'msg err'; msg.textContent = r.status === 429 ? 'Too many messages in a short time. Please wait a minute and try again.' : String((j as any).error || 'It could not be sent. Please try again.'); }
      } catch { msg.className = 'msg err'; msg.textContent = 'You seem to be offline. Please try again.'; }
      send.disabled = false;
    });
  }
  const f = d.querySelector('form')!;
  (f.kind as HTMLSelectElement).value = KINDS.some(k => k[0] === kind) ? kind : 'other';
  (f.querySelector('.msg') as HTMLElement).textContent = '';
  d.showModal();
  (f.message as HTMLTextAreaElement).focus();
}

export function initContact(): void {
  document.head.insertAdjacentHTML('beforeend', `<style id="qfb-css">${CSS}</style>`);
  (window as any).qFeedback = open;
  document.addEventListener('click', e => {
    const t = (e.target as HTMLElement).closest<HTMLElement>('[data-fb]');
    if (t) { e.preventDefault(); open(t.dataset.fb || 'data'); }
  });
  const foot = document.getElementById('sf');
  if (foot && !foot.querySelector('[data-fb]')) foot.insertAdjacentHTML('beforeend', '<a href="#" data-fb="other">Send feedback</a>');
}
