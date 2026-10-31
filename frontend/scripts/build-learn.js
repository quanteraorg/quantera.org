// Generates the search-friendly lesson pages in public/learn from lessons.json, a snapshot of the Academy's lessons (LES and LX in index.html).
// Run after changing lessons: node frontend/scripts/build-learn.js
const fs = require("fs"), path = require("path");
const SITE = "https://quanteraorg.vercel.app", pub = path.join(__dirname, "..", "public"), out = path.join(pub, "learn");
const lessons = JSON.parse(fs.readFileSync(path.join(__dirname, "lessons.json"), "utf8"));
const esc = t => String(t ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const text = t => String(t ?? "").replace(/<[^>]+>/g, "");

const shell = (title, desc, url, main, ld) => `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title><meta name="description" content="${esc(desc)}"><link rel="canonical" href="${SITE}${url}"><link rel="icon" href="/favicon-64.png"><link rel="stylesheet" href="/seo.css"><meta name="theme-color" content="#0a0c10"><meta property="og:type" content="article"><meta property="og:site_name" content="Quantera"><meta property="og:title" content="${esc(title)}"><meta property="og:description" content="${esc(desc)}"><meta property="og:url" content="${SITE}${url}"><meta property="og:image" content="${SITE}/og.png"><meta name="twitter:card" content="summary_large_image">${ld ? `<script type="application/ld+json">${JSON.stringify(ld).replace(/</g, "\\u003c")}</script>` : ""}</head><body><header><a class="logo" href="/"><img src="/logo-mark.png" alt="" width="26" height="26">QUANTERA</a><nav><a href="/stocks">Stocks</a><a href="/private-companies">Private companies</a><a href="/learn">Learn</a><a class="cta" href="/#/markets">Open the app</a></nav></header><main>${main}</main><footer>For education, not investment advice. <a href="/#/about">About Quantera</a></footer></body></html>\n`;

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
fs.writeFileSync(path.join(pub, "sitemap-learn.xml"), `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n<url><loc>${SITE}/</loc></url>\n<url><loc>${SITE}/learn</loc></url>\n${taught.map(l => `<url><loc>${SITE}/learn/${l.id}</loc></url>`).join("\n")}\n</urlset>\n`);
console.log(`Built ${taught.length} lesson pages; ${lessons.length - taught.length} lessons without teaching text were skipped`);
