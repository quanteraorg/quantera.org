// Generates the search-friendly lesson pages in public/learn from lessons.json, a snapshot of the Academy's lessons (LES and LX in index.html).
// Run after changing lessons: node frontend/scripts/build-learn.js
const fs = require("fs"), path = require("path");
const SITE = "https://quanteraorg.vercel.app", pub = path.join(__dirname, "..", "public"), out = path.join(pub, "learn");
const lessons = JSON.parse(fs.readFileSync(path.join(__dirname, "lessons.json"), "utf8"));
const esc = t => String(t ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const text = t => String(t ?? "").replace(/<[^>]+>/g, "");

const shell = (title, desc, url, main, ld) => `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title><meta name="description" content="${esc(desc)}"><link rel="canonical" href="${SITE}${url}"><link rel="icon" href="/favicon-64.png"><link rel="stylesheet" href="/fonts/fonts.css"><link rel="stylesheet" href="/seo.css"><meta name="theme-color" content="#06080c"><script>try{var t=JSON.parse(localStorage.getItem("q_theme"));if(t=="system")t=matchMedia("(prefers-color-scheme: light)").matches?"light":"dark";if(t=="light"){document.documentElement.dataset.theme="light";document.querySelector("meta[name=theme-color]").content="#f4f5f7"}}catch(e){}</script><meta property="og:type" content="article"><meta property="og:site_name" content="Quantera"><meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(desc)}"><meta property="og:url" content="${SITE}${url}"><meta property="og:image" content="${SITE}/og.png"><meta name="twitter:card" content="summary_large_image"><script>(function(){var v=null;try{v=JSON.parse(localStorage.getItem('q_priv')||'null')}catch(e){}var ok=v?v==='all':!(navigator.globalPrivacyControl===true||navigator.doNotTrack==='1');if(ok){window.va=window.va||function(){(window.vaq=window.vaq||[]).push(arguments)};var s=document.createElement('script');s.defer=true;s.src='/_vercel/insights/script.js';document.head.appendChild(s)}})()</script>${ld ? `<script type="application/ld+json">${JSON.stringify(ld).replace(/</g, "\\u003c")}</script>` : ""}</head><body><header><a class="logo" href="/"><img src="/logo-mark.png" alt="" width="26" height="26">QUANTERA</a><nav><a href="/stocks">Stocks</a><a href="/private-companies">Private companies</a><a href="/learn">Learn</a><a class="cta" href="/#/markets">Open the app</a></nav></header><main>${main}</main><footer>For education, not investment advice. <a href="/#/about">About Quantera</a> · <a href="/privacy">Privacy</a> · <a href="/cookies">Cookies</a> · <a href="/terms">Terms</a> · <a href="/accessibility">Accessibility</a></footer></body></html>\n`;

fs.rmSync(out, { recursive: true, force: true });
fs.mkdirSync(out, { recursive: true });
const taught = lessons.filter(l => l.x);
for (const l of taught) {
  const url = `/learn/${l.id}`, x = l.x, related = taught.filter(o => o.id != l.id && o.cat == l.cat).slice(0, 4), quiz = [[l.q[0], l.q[1], l.q[2]], ...(x.q || [])];
  const main = `<p class="crumb"><a href="/learn">Learn</a> › ${esc(l.cat)}</p><h1>${esc(l.title)}</h1><p>${esc(l.level)} · ${esc(l.cat)}</p>
<h2>The idea</h2><p>${x.i}</p>
<h2>How it works</h2><ul>${x.h.map(v => `<li>${v}</li>`).join("")}</ul>
<h2>Worked example</h2><p class="ex">${x.e}</p>
<h2>Common mistakes</h2><ul>${x.m.map(v => `<li>${v}</li>`).join("")}</ul>
<h2>Key takeaways</h2><ul>${x.k.map(v => `<li>${v}</li>`).join("")}</ul>
<p class="go"><a class="cta" href="/#/lesson/${esc(l.id)}">Try the interactive lesson and quiz</a></p>
<h2>Test yourself</h2>${quiz.map(q => `<h3>${esc(text(q[0]))}</h3><details><summary>Show the answer</summary><p>${esc(text(q[1][q[2]]))}${q[3] ? ". " + esc(text(q[3])) : ""}</p></details>`).join("")}
${related.length ? `<h2>Keep learning</h2><div class="cards">${related.map(o => `<a href="/learn/${esc(o.id)}"><b>${esc(o.title)}</b><span>${esc(o.desc)}</span></a>`).join("")}</div>` : ""}`;
  fs.writeFileSync(path.join(out, l.id + ".html"), shell(`${l.title}: Explained in Plain English | Quantera`, text(l.desc), url, main, { "@context": "https://schema.org", "@type": "Article", headline: l.title, description: text(l.desc), author: { "@type": "Organization", name: "Quantera" }, publisher: { "@type": "Organization", name: "Quantera" }, mainEntityOfPage: SITE + url }));
}
const cats = [...new Set(taught.map(l => l.cat))];
fs.writeFileSync(path.join(out, "index.html"), shell("Learn Investing: Free Lessons in Plain English | Quantera", "Free investing lessons with worked examples: stocks, moving averages, RSI, risk and reward, financial statements and more.", "/learn", `<h1>Learn investing</h1><p>Free lessons in plain English, each with a worked example and common mistakes. The <a href="/#/learn">Academy</a> adds interactive sliders and quizzes.</p>${cats.map(c => `<h2>${esc(c)}</h2><div class="cards">${taught.filter(l => l.cat == c).map(l => `<a href="/learn/${esc(l.id)}"><b>${esc(l.title)}</b><span>${esc(l.desc)}</span></a>`).join("")}</div>`).join("")}`));
// Privacy and terms, written in plain English like the lessons. Update UPDATED whenever the wording changes.
const UPDATED = "6 November 2026";
const legal = [
  ["privacy", "Privacy", "What Quantera collects, why, and how to remove it.", `<h1>Privacy</h1><p>Last updated ${UPDATED}.</p>
<p>Quantera is a free place to learn investing with paper money. We collect as little as we can, we never sell your data and we show no ads.</p>
<h2>What stays on your device</h2><p>Your watchlists, paper portfolio, trades, notes, lesson progress and settings are saved in your browser’s storage. They never leave your device unless you create an account. Clearing your browser data removes them.</p>
<h2>If you create an account</h2><p>An account is optional. It stores your email address and a copy of your progress so it follows you between devices. Sign-in is handled by our database provider, Supabase; we never see your password.</p>
<h2>The leaderboard</h2><p>If you join the leaderboard, the nickname you choose, your paper returns and your number of trades are shown publicly. Don’t use your real name if you’d rather stay anonymous. You can leave at any time.</p>
<h2>The waitlist</h2><p>If you join a waitlist, we keep your email address only to tell you when that feature launches.</p>
<h2>Price alerts</h2><p>If you turn on background alerts, your browser gives us a notification address and we store it with your alert settings so we can notify you.</p>
<h2>Usage statistics</h2><p>We count which pages and features are used, such as “viewed the leaderboard”, without attaching your name or email. We also use Vercel Web Analytics, which counts page views without cookies.</p>
<h2>Services we rely on</h2><p>Vercel hosts the website, Render runs our data server and Supabase stores accounts. Fonts load from Google Fonts. Market data comes from providers such as Yahoo Finance, Finnhub, Twelve Data, SEC EDGAR and FRED; your requests to them go through our server, not from your browser.</p>
<h2>Children</h2><p>Anyone can use Quantera without an account. You must be 13 or older to create an account.</p>
<h2>Removing your data</h2><p>To delete your account and everything stored with it, message us on Instagram at <a href="https://instagram.com/quantera.research" rel="noopener">@quantera.research</a> and we’ll remove it.</p>
<h2>Changes</h2><p>If this policy changes, we’ll update this page and the date at the top.</p>`],
  ["terms", "Terms of use", "The rules for using Quantera, in plain English.", `<h1>Terms of use</h1><p>Last updated ${UPDATED}.</p>
<p>By using Quantera you agree to these terms. If you don’t agree, please don’t use the site.</p>
<h2>Education, not advice</h2><p>Quantera is for learning and research. Nothing on it is investment, financial, tax or legal advice, and nothing is a recommendation to buy or sell anything. Make your own decisions, and speak to a licensed professional before investing real money.</p>
<h2>Paper money only</h2><p>Trading on Quantera uses virtual money. No real money, securities or crypto change hands, and paper results don’t predict real results.</p>
<h2>Data may be delayed or wrong</h2><p>Prices, financials, valuations and news come from third parties. They are often delayed and can be incomplete or incorrect. Private company valuations are estimates from funding rounds and reports, not market prices. Check important figures with an official source.</p>
<h2>Fair use</h2><p>Don’t misuse the site: no scraping or bulk downloading, no attempts to break or overload it, and no offensive or impersonating leaderboard nicknames. We may remove content or accounts that break these rules.</p>
<h2>Your account</h2><p>Keep your sign-in details private. Questions? Message us on Instagram at <a href="https://instagram.com/quantera.research" rel="noopener">@quantera.research</a>. You’re responsible for what happens under your account. You can stop using Quantera and ask us to delete your account at any time.</p>
<h2>No warranty</h2><p>Quantera is provided free and “as is”. We don’t promise it will always be available, accurate or error-free, and to the extent the law allows we aren’t liable for losses from using it.</p>
<h2>Changes</h2><p>We may update these terms. The date at the top shows the latest version. See also our <a href="/privacy">privacy policy</a>.</p>`],
];
for (const [id, title, desc, main] of legal) fs.writeFileSync(path.join(pub, id + ".html"), shell(`${title} | Quantera`, desc, "/" + id, main));
fs.writeFileSync(path.join(pub, "sitemap-learn.xml"), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n<url><loc>${SITE}/</loc></url>\n<url><loc>${SITE}/learn</loc></url>\n<url><loc>${SITE}/privacy</loc></url>\n<url><loc>${SITE}/terms</loc></url>\n${taught.map(l => `<url><loc>${SITE}/learn/${l.id}</loc></url>`).join("\n")}\n</urlset>\n`);
console.log(`Built ${taught.length} lesson pages; ${lessons.length - taught.length} lessons without teaching text were skipped`);
