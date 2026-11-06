// Tells Bing and the other IndexNow search engines about every page in the sitemaps.
// Run after adding pages: node frontend/scripts/indexnow.js
const SITE = "https://quanteraorg.vercel.app", KEY = "9307d2dcd3859a2fa1918c1b5638652d";
(async () => {
  const urls = [];
  for (const s of ["sitemap-learn.xml", "sitemap-markets.xml"]) {
    const xml = await (await fetch(SITE + "/" + s)).text();
    for (const m of xml.matchAll(/<loc>([^<]+)<\/loc>/g)) urls.push(m[1]);
  }
  const r = await fetch("https://api.indexnow.org/indexnow", {
    method: "POST",
    headers: { "content-type": "application/json; charset=utf-8" },
    body: JSON.stringify({ host: new URL(SITE).host, key: KEY, keyLocation: SITE + "/" + KEY + ".txt", urlList: urls }),
  });
  console.log("Sent " + urls.length + " URLs to IndexNow: HTTP " + r.status);
})();
