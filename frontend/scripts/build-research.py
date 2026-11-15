#!/usr/bin/env python3
"""Builds the Research Library from scripts/research.json.

Writes, in frontend/public:
  research/index.html          the library page (static, indexable)
  research/<slug>.html         one article page each, with Article structured data
  research/index.json          the list the app's library page (#/research) reads
  research/<slug>.json         each article for the app's reader (#/research/<slug>)
  sitemap-research.xml         and adds it to sitemap.xml

Run after editing research.json:  python3 frontend/scripts/build-research.py
"""
import html, json, os, re

SITE = 'https://quanteraorg.vercel.app'
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', 'public')
E = html.escape

HEAD_JS = ("<script>try{var t=JSON.parse(localStorage.getItem(\"q_theme\"));if(t==\"system\")t=matchMedia(\"(prefers-color-scheme: light)\").matches?\"light\":\"dark\";"
           "if(t==\"light\"){document.documentElement.dataset.theme=\"light\";document.querySelector(\"meta[name=theme-color]\").content=\"#f4f5f7\"}}catch(e){}</script>"
           "<script>(function(){var v=null;try{v=JSON.parse(localStorage.getItem('q_priv')||'null')}catch(e){}"
           "var ok=v?v==='all':!(navigator.globalPrivacyControl===true||navigator.doNotTrack==='1');"
           "if(ok){window.va=window.va||function(){(window.vaq=window.vaq||[]).push(arguments)};var s=document.createElement('script');s.defer=true;s.src='/_vercel/insights/script.js';document.head.appendChild(s)}})()</script>")
FOOT = ('<footer>Quantera Research is a student-led research and education initiative. Research is for education, not investment advice. '
        '<a href="/#/about">About</a> · <a href="/research">Research</a> · <a href="/privacy">Privacy</a> · <a href="/terms">Terms</a> · <a href="/accessibility">Accessibility</a></footer>')
STYLE = ('<style>main table{border-collapse:collapse;width:100%;font-size:14.5px;margin:10px 0 18px;font-variant-numeric:tabular-nums}'
         'main th,main td{border-bottom:1px solid rgba(127,127,127,.25);padding:8px 10px;text-align:left;vertical-align:top}main th{font-weight:600;font-size:13px;color:var(--mu)}'
         '.dek{font-size:19px;line-height:1.5;color:var(--mu);margin:4px 0 14px}.meta{font-size:13.5px;color:var(--mu);margin:0 0 22px}.meta b{color:var(--ac)}'
         '.box{border:1px solid var(--ln);border-radius:14px;padding:14px 18px;margin:16px 0;background:var(--s1)}.box h2{margin-top:0;font-size:20px}'
         '.lbl{display:inline-block;font-size:11px;letter-spacing:.06em;text-transform:uppercase;border:1px solid rgba(232,199,122,.45);color:#e8c77a;border-radius:999px;padding:2px 9px;margin-left:8px;vertical-align:3px}'
         '.chart{margin:8px 0 4px}.chart svg{width:100%;max-width:680px;height:auto}.chart text{fill:var(--mu);font-size:11px}.src{font-size:12.5px;color:var(--mu)}'
         'ol.srcs li{margin:6px 0;font-size:14px}ol.srcs small{color:var(--mu)}.lib a.card{display:block;border:1px solid var(--ln);border-radius:14px;padding:16px 18px;margin:12px 0;color:inherit;text-decoration:none;background:var(--s1)}'
         '.lib a.card:hover{border-color:var(--ac)}.lib .card b{display:block;font-family:Fraunces,Georgia,serif;font-weight:400;font-size:21px;line-height:1.3;margin:4px 0}.lib .card small{color:var(--mu)}'
         '@media(max-width:640px){main table{display:block;overflow-x:auto}}</style>')


def head(path, title, desc, kind='article', ld=None):
    t, d = E(title), E(desc)
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<title>{t} | Quantera Research</title><meta name="description" content="{d}"><link rel="canonical" href="{SITE}/{path}">'
            '<link rel="icon" href="/favicon-64.png"><link rel="stylesheet" href="/fonts/fonts.css"><link rel="stylesheet" href="/seo.css">'
            f'<meta name="theme-color" content="#06080c">{HEAD_JS}<meta property="og:type" content="{kind}"><meta property="og:site_name" content="Quantera Research">'
            f'<meta property="og:title" content="{t}"><meta property="og:description" content="{d}"><meta property="og:url" content="{SITE}/{path}">'
            f'<meta property="og:image" content="{SITE}/og.png"><meta name="twitter:card" content="summary_large_image">'
            + (f'<script type="application/ld+json">{json.dumps(ld, ensure_ascii=False)}</script>' if ld else '') + STYLE +
            '</head><body><a href="#main" style="position:absolute;left:-999px" onfocus="this.style.left=\'12px\'" onblur="this.style.left=\'-999px\'">Skip to content</a>'
            '<header><a class="logo" href="/"><img src="/logo-mark.png" alt="" width="26" height="26">QUANTERA</a><nav><a href="/research">Research</a><a href="/stocks">Stocks</a>'
            '<a href="/learn">Learn</a><a class="cta" href="/#/insights">Compare reactions</a></nav></header>')


def nice_date(d):
    y, m, dd = d.split('-')
    return f"{int(dd)} {['January','February','March','April','May','June','July','August','September','October','November','December'][int(m)-1]} {y}"


def chart_svg(c):
    """Grouped bars, one group per label; zero line in the middle. Static and accessible (role=img with a description)."""
    W, H, L, B, T = 640, 240, 40, 30, 14
    vals = [v for s in c['series'] for v in s['values']]
    top = max(1.0, max(abs(v) for v in vals)) * 1.15
    n, k = len(c['labels']), len(c['series'])
    gw = (W - L - 10) / n
    bw = min(26, gw / (k + 1))
    Y = lambda v: T + (H - B - T) / 2 * (1 - v / top)
    cols = ['#c8a96a', '#8e97a8', '#4fd1a5']
    out = [f'<line x1="{L}" x2="{W-10}" y1="{Y(0):.1f}" y2="{Y(0):.1f}" stroke="rgba(142,151,168,.45)"/>']
    step = 2 if top <= 10 else 5
    v = -int(top // step) * step
    while v <= top:
        out.append(f'<text x="{L-6}" y="{Y(v)+4:.1f}" text-anchor="end">{v:+g}{E(c.get("unit",""))}</text>' if v else f'<text x="{L-6}" y="{Y(0)+4:.1f}" text-anchor="end">0</text>')
        v += step
    for i, lab in enumerate(c['labels']):
        x0 = L + gw * i + (gw - bw * k) / 2
        for j, s in enumerate(c['series']):
            val = s['values'][i]
            y, h = (Y(val), Y(0) - Y(val)) if val >= 0 else (Y(0), Y(val) - Y(0))
            out.append(f'<rect x="{x0 + j*bw:.1f}" y="{y:.1f}" width="{bw-3:.1f}" height="{max(1,h):.1f}" fill="{cols[j % 3]}" rx="2"><title>{E(s["name"])} {E(lab)}: {val:+.2f}{E(c.get("unit",""))}</title></rect>')
        out.append(f'<text x="{L + gw*i + gw/2:.1f}" y="{H-8}" text-anchor="middle">{E(lab)}</text>')
    legend = ' · '.join(f'<span style="color:{cols[j % 3]}">■</span> {E(s["name"])}' for j, s in enumerate(c['series']))
    desc = '; '.join(f'{s["name"]}: ' + ', '.join(f'{l} {v:+.2f}{c.get("unit","")}' for l, v in zip(c['labels'], s['values'])) for s in c['series'])
    return (f'<figure class="chart"><figcaption><b>{E(c["title"])}</b> <span class="src">{legend}</span></figcaption>'
            f'<svg viewBox="0 0 {W} {H}" role="img" aria-label="{E(c["title"])}. {E(desc)}">{"".join(out)}</svg><p class="src">{E(c.get("source",""))}</p></figure>')


def cite(s):
    """[1] style references become links to the sources list."""
    return re.sub(r'\[(\d+)\]', lambda m: f'<a href="#src{m.group(1)}">[{m.group(1)}]</a>', E(s))


def article_html(a, allarts):
    url = f'research/{a["slug"]}'
    ld = {"@context": "https://schema.org", "@type": "Article", "headline": a['title'], "description": a['dek'], "datePublished": a['date'], "dateModified": a.get('updated', a['date']),
          "articleSection": a['category'], "author": {"@type": "Organization", "name": "Quantera Research", "url": SITE + "/#/about"},
          "publisher": {"@type": "Organization", "name": "Quantera Research", "logo": {"@type": "ImageObject", "url": SITE + "/icon-512.png"}},
          "mainEntityOfPage": f"{SITE}/{url}", "image": SITE + "/og.png", "about": [{"@type": "Corporation", "tickerSymbol": t} for t in a.get('tickers', [])]}
    b = [f'<main id="main"><p class="crumb"><a href="/research">Research</a> › {E(a["category"])}</p><h1>{E(a["title"])}</h1><p class="dek">{E(a["dek"])}</p>',
         f'<p class="meta"><b>{E(a["category"])}</b> · Published {nice_date(a["date"])} · {a.get("readMinutes", 5)} min read · By Quantera Research'
         + (f' · {" ".join(E(t) for t in a["tickers"])}' if a.get('tickers') else '') + '</p>',
         f'<div class="box"><h2>Research question</h2><p>{E(a["question"])}</p></div>',
         '<h2>Summary</h2>' + ''.join(f'<p>{cite(p)}</p>' for p in a['summary']),
         '<div class="box"><h2>Key findings</h2><ul>' + ''.join(f'<li>{cite(f)}</li>' for f in a['findings']) + '</ul></div>']
    for s in a['sections']:
        b.append(f'<h2>{E(s["h"])}' + (f'<span class="lbl">{E(s["label"])}</span>' if s.get('label') else '') + '</h2>')
        b += [f'<p>{cite(p)}</p>' for p in s.get('p', [])]
        if s.get('list'): b.append('<ol>' + ''.join(f'<li>{cite(x)}</li>' for x in s['list']) + '</ol>')
        if s.get('table'):
            t = s['table']
            b.append('<table><tr>' + ''.join(f'<th>{E(h)}</th>' for h in t['head']) + '</tr>' + ''.join('<tr>' + ''.join(f'<td>{E(c)}</td>' for c in r) + '</tr>' for r in t['rows']) + '</table>')
        if s.get('chart'): b.append(chart_svg(s['chart']))
    b.append('<h2>Limitations</h2><ul>' + ''.join(f'<li>{cite(x)}</li>' for x in a['limitations']) + '</ul>')
    b.append('<h2>Sources</h2><ol class="srcs">' + ''.join(
        f'<li id="src{i+1}">' + (f'<a href="{E(s["url"])}" rel="noopener">{E(s["label"])}</a>' if s.get('url') else E(s['label'])) + f' <small>{E(s.get("kind",""))}</small></li>'
        for i, s in enumerate(a['sources'])) + '</ol>')
    if a.get('explore'): b.append('<h2>Explore further</h2><ul>' + ''.join(f'<li><a href="{E(x["href"])}">{E(x["label"])}</a></li>' for x in a['explore']) + '</ul>')
    rel = [x for x in allarts if x['slug'] != a['slug'] and (x['category'] == a['category'] or set(x.get('tickers', [])) & set(a.get('tickers', [])))][:3]
    if rel: b.append('<h2>Related research</h2><div class="lib">' + ''.join(f'<a class="card" href="/research/{x["slug"]}"><small>{E(x["category"])} · {nice_date(x["date"])}</small><b>{E(x["title"])}</b></a>' for x in rel) + '</div>')
    b.append('<div class="box"><p style="margin:0"><b>Education, not investment advice.</b> This study describes what happened and is not a recommendation to buy, sell or hold any security. '
             'Quantera Research is a student-led initiative, not a registered investment adviser, broker or research analyst. Figures were correct to the sources listed on the publication date. '
             'Spotted an error? Tell us on Instagram at <a href="https://instagram.com/quantera.research" rel="noopener">@quantera.research</a>.</p></div></main>')
    return head(url, a['title'], a['dek'], 'article', ld) + ''.join(b) + FOOT + '</body></html>\n'


def index_html(data):
    arts = data['articles']
    ld = {"@context": "https://schema.org", "@type": "CollectionPage", "name": "Quantera Research library", "url": SITE + "/research",
          "hasPart": [{"@type": "Article", "headline": a['title'], "url": f"{SITE}/research/{a['slug']}", "datePublished": a['date']} for a in arts]}
    body = ('<main id="main"><h1>Research</h1><p class="dek">Original, sourced studies of companies, earnings and how markets react to the news. '
            'Every figure links to its source, facts are kept apart from interpretation, and every study states its limits.</p>'
            '<p><a href="/#/research">Search and filter the library in the app</a> · <a href="/#/insights">Compare how companies reacted to the same news</a></p><div class="lib">'
            + ''.join(f'<a class="card" href="/research/{a["slug"]}"><small>{E(a["category"])} · {nice_date(a["date"])}</small><b>{E(a["title"])}</b><span>{E(a["dek"])}</span></a>' for a in arts)
            + '</div><div class="box"><h2>How we research</h2><ul><li>Primary sources first: company filings with the SEC, official releases and exchange data.</li>'
            '<li>No source, no number: if we can\'t verify a figure, we say it wasn\'t found.</li><li>Facts and interpretation are labelled separately; a coincidence in time is never presented as a cause.</li>'
            '<li>Every calculation is re-run independently before publication.</li></ul></div></main>')
    return head('research', 'Research library', 'Original, sourced research from Quantera: company investigations, earnings analysis and how markets react to the news.', 'website', ld) + body + FOOT + '</body></html>\n'


def main():
    data = json.load(open(os.path.join(HERE, 'research.json'), encoding='utf-8'))
    arts = sorted(data['articles'], key=lambda a: a['date'], reverse=True)
    for a in arts:
        assert re.fullmatch(r'[a-z0-9-]+', a['slug']) and a['category'] in data['categories'], a['slug']
        assert all(int(n) <= len(a['sources']) for n in re.findall(r'\[(\d+)\]', json.dumps(a))), 'citation without a source in ' + a['slug']
    d = os.path.join(OUT, 'research'); os.makedirs(d, exist_ok=True)
    for a in arts:
        open(os.path.join(d, a['slug'] + '.html'), 'w', encoding='utf-8').write(article_html(a, arts))
        json.dump(a, open(os.path.join(d, a['slug'] + '.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    open(os.path.join(d, 'index.html'), 'w', encoding='utf-8').write(index_html({**data, 'articles': arts}))
    json.dump({'categories': data['categories'], 'articles': [{k: a[k] for k in ('slug', 'title', 'dek', 'date', 'category', 'tickers', 'readMinutes', 'findings') if k in a} for a in arts]},
              open(os.path.join(d, 'index.json'), 'w', encoding='utf-8'), ensure_ascii=False)
    urls = [f'{SITE}/research'] + [f'{SITE}/research/{a["slug"]}' for a in arts]
    open(os.path.join(OUT, 'sitemap-research.xml'), 'w').write('<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n'
        + ''.join(f'<url><loc>{u}</loc>' + (f'<lastmod>{a["date"]}</lastmod>' if (a := next((x for x in arts if u.endswith(x["slug"])), None)) else '') + '</url>\n' for u in urls) + '</urlset>\n')
    sm = os.path.join(OUT, 'sitemap.xml'); s = open(sm).read()
    if 'sitemap-research.xml' not in s:
        open(sm, 'w').write(s.replace('</sitemapindex>', f'<sitemap><loc>{SITE}/sitemap-research.xml</loc></sitemap>\n</sitemapindex>'))
    print(f'Built {len(arts)} research article(s).')


if __name__ == '__main__':
    main()
