#!/usr/bin/env python3
"""Builds the legal pages (privacy, terms, cookies, accessibility) in frontend/public from one template.

Edit the text here, then run:  python3 frontend/scripts/build-legal.py
The pages are plain HTML so they load fast, work without JavaScript and can be read by search engines.
"""
import html, os

UPDATED = '13 November 2026'
SITE = 'https://quanteraorg.vercel.app'
CONTACT = '<a href="https://instagram.com/quantera.research" rel="noopener">@quantera.research on Instagram</a>'
OUT = os.path.join(os.path.dirname(__file__), '..', 'public')

# Analytics only loads if the visitor allows it (same rule as the app: Settings, or Global Privacy Control / Do Not Track).
HEAD_JS = ("<script>try{var t=JSON.parse(localStorage.getItem(\"q_theme\"));if(t==\"system\")t=matchMedia(\"(prefers-color-scheme: light)\").matches?\"light\":\"dark\";"
           "if(t==\"light\"){document.documentElement.dataset.theme=\"light\";document.querySelector(\"meta[name=theme-color]\").content=\"#f4f5f7\"}}catch(e){}</script>"
           "<script>(function(){var v=null;try{v=JSON.parse(localStorage.getItem('q_priv')||'null')}catch(e){}"
           "var ok=v?v==='all':!(navigator.globalPrivacyControl===true||navigator.doNotTrack==='1');"
           "if(ok){window.va=window.va||function(){(window.vaq=window.vaq||[]).push(arguments)};var s=document.createElement('script');s.defer=true;s.src='/_vercel/insights/script.js';document.head.appendChild(s)}})()</script>")

FOOT = ('<footer>For education, not investment advice. <a href="/#/about">About Quantera</a> · <a href="/privacy">Privacy</a> · '
        '<a href="/cookies">Cookies</a> · <a href="/terms">Terms</a> · <a href="/accessibility">Accessibility</a></footer>')


def page(slug, title, desc, body):
    t = html.escape(title)
    d = html.escape(desc)
    return (f'<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
            f'<title>{t} | Quantera</title><meta name="description" content="{d}"><link rel="canonical" href="{SITE}/{slug}">'
            '<link rel="icon" href="/favicon-64.png"><link rel="stylesheet" href="/fonts/fonts.css"><link rel="stylesheet" href="/seo.css">'
            f'<meta name="theme-color" content="#06080c">{HEAD_JS}<meta property="og:type" content="article"><meta property="og:site_name" content="Quantera">'
            f'<meta property="og:title" content="{t} | Quantera"><meta property="og:description" content="{d}"><meta property="og:url" content="{SITE}/{slug}">'
            f'<meta property="og:image" content="{SITE}/og.png"><meta name="twitter:card" content="summary_large_image">'
            '<style>main table{border-collapse:collapse;width:100%;font-size:14.5px;margin:10px 0 18px}main th,main td{border-bottom:1px solid rgba(127,127,127,.25);padding:8px 10px;text-align:left;vertical-align:top}'
            'main th{font-weight:600}main th,main td{white-space:normal;overflow-wrap:anywhere}@media(max-width:640px){main table,main tbody,main tr,main td,main th{display:block;width:auto}main tr:first-child{display:none}main tr{padding:10px 0;border-bottom:1px solid rgba(127,127,127,.25)}main td{border:0;padding:2px 0}main td:first-child{font-weight:600}}.toc{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:14px;margin:6px 0 18px}.note{padding:12px 14px;border-radius:12px;border:1px solid rgba(127,127,127,.3);margin:12px 0}</style>'
            '</head><body><a href="#main" style="position:absolute;left:-999px" onfocus="this.style.left=\'12px\'" onblur="this.style.left=\'-999px\'">Skip to content</a>'
            '<header><a class="logo" href="/"><img src="/logo-mark.png" alt="" width="26" height="26">QUANTERA</a><nav><a href="/stocks">Stocks</a>'
            '<a href="/private-companies">Private companies</a><a href="/learn">Learn</a><a class="cta" href="/#/markets">Open the app</a></nav></header>'
            f'<main id="main"><h1>{t}</h1><p>Last updated {UPDATED}.</p>\n{body}\n</main>{FOOT}</body></html>\n')


PRIVACY = f"""
<div class="toc"><a href="#who">Who we are</a><a href="#summary">Summary</a><a href="#collect">What we collect</a><a href="#why">Why, and our legal bases</a><a href="#share">Who we share it with</a><a href="#keep">How long we keep it</a><a href="#rights">Your rights and choices</a><a href="#children">Children</a><a href="#contact">Contact</a></div>
<h2 id="who">Who we are</h2>
<p>Quantera is a free financial research and learning website, built and run independently by its founders. It is not a bank, broker, investment adviser or research analyst. “We” and “us” in this policy mean the people who run Quantera. You can reach us at {CONTACT}.</p>
<h2 id="summary">Summary</h2>
<ul><li>You can use Quantera without an account. Most of what you do stays in your own browser.</li>
<li>We set <b>no cookies</b>, show <b>no ads</b> and <b>never sell or share</b> your personal information for advertising.</li>
<li>Anonymous usage statistics and error reports help us improve the site. You can turn them off in <a href="/#/settings">Settings</a>, and they start off if your browser sends Global Privacy Control or Do Not Track.</li>
<li>You can delete your account and everything stored with it yourself, in Settings.</li></ul>
<h2 id="collect">What we collect</h2>
<table><tr><th>What</th><th>When</th><th>Details</th></tr>
<tr><td>Your data in this browser</td><td>Always (needed for the site to work)</td><td>Watchlists, paper portfolio and trades, trade journal, notes, research folders and thesis notes, lesson progress and streaks, recent reports and settings (theme, layout, privacy choice). Stored in your browser’s local storage, not sent to us unless you create an account. See <a href="/cookies">Cookies and storage</a>.</td></tr>
<tr><td>Account</td><td>Only if you create one</td><td>Your email address and a synced copy of the data above, so it follows you between devices. Sign-in is handled by our database provider, Supabase; we never see your password. If you sign in with Google, Google shares your email address and basic profile with us through Supabase.</td></tr>
<tr><td>Leaderboard</td><td>Only if you join</td><td>The nickname you choose, your paper returns and number of trades, shown publicly. Please don’t use your real name. You can leave at any time.</td></tr>
<tr><td>Waitlist</td><td>Only if you join</td><td>Your email address, which plan you’re interested in and which page you joined from.</td></tr>
<tr><td>Price alerts</td><td>Only if you turn on background alerts</td><td>The notification address your browser gives us and your alert settings (which symbol, at what price).</td></tr>
<tr><td>Anonymous statistics</td><td>Unless you turn them off</td><td>Page views counted by Vercel Web Analytics, which uses no cookies and does not identify you across days; and counts of which features are used (just the feature name, such as “viewed the leaderboard”, with no name, email or ID attached).</td></tr>
<tr><td>Error reports</td><td>Unless you turn them off</td><td>If a page breaks, a report goes to Sentry: the error, the page address, your browser and device type, and (once per visit, on large or touch screens) your screen size, so we can fix layout problems. No name, email or account details; sign-in tokens are removed before sending.</td></tr>
<tr><td>Ask AI</td><td>Only when you use it</td><td>The text of your conversation (up to the last 20 messages) is sent from our server to an AI provider, Google (Gemini) or Anthropic (Claude), together with the public market data our server looks up for it. We don’t send your name, email or account details, but anything you type is sent, so please don’t include personal or sensitive information. Each provider handles requests under its own terms and may keep them for a limited time, for example to prevent abuse.</td></tr>
<tr><td>AI deep research</td><td>When you open a report</td><td>Only the company’s ticker and public market data (filings, prices, ratios, headlines) are sent to the AI provider. Nothing about you.</td></tr>
<tr><td>Server and security logs</td><td>Every request</td><td>Like any website, our servers see your IP address and the page or data requested. We use this briefly to deliver the site, limit abuse (rate limits are counted in memory) and fix problems.</td></tr></table>
<p>We don’t knowingly collect sensitive information such as health, religion or financial account details. Quantera never asks for bank or brokerage logins, and paper trading uses virtual money only.</p>
<h2 id="why">Why we use it, and our legal bases</h2>
<p>For visitors in the UK, EU and similar places, these are the legal bases we rely on:</p>
<ul><li><b>To provide the service you ask for</b> (contract): your account, sync, alerts and the leaderboard you join.</li>
<li><b>Consent</b>: the waitlist, the leaderboard and background alerts are opt-in, and you can withdraw at any time.</li>
<li><b>Legitimate interests</b>: anonymous statistics and error reports to improve the site, and server logs to keep it secure. You can object at any time by turning statistics and error reports off in Settings.</li></ul>
<h2 id="share">Who we share it with</h2>
<p>We share personal information only with services that run Quantera for us, and only what each needs. We do not sell personal information, share it for cross-context behavioural advertising, or let anyone use it for their own marketing.</p>
<table><tr><th>Service</th><th>What it does for us</th></tr>
<tr><td>Vercel</td><td>Hosts the website; Web Analytics (cookieless page counts)</td></tr>
<tr><td>Render</td><td>Runs our data server</td></tr>
<tr><td>Supabase</td><td>Accounts, synced data, leaderboard, waitlist and alerts</td></tr>
<tr><td>Sentry</td><td>Error reports</td></tr>
<tr><td>Google (Gemini) and Anthropic (Claude)</td><td>Write Ask AI answers and research reports</td></tr>
<tr><td>Google</td><td>Optional “Continue with Google” sign-in</td></tr></table>
<p>Market data comes from providers such as SEC EDGAR, FRED, Finnhub, Twelve Data and Yahoo Finance. Your requests go through our server, so these providers never receive information about you. Fonts are served from our own website. These services are mostly based in the United States, so your information may be processed there; where the law requires, they protect transfers with standard contractual clauses.</p>
<p>We may disclose information if the law requires it, or to protect the safety of our users or the site.</p>
<h2 id="keep">How long we keep it</h2>
<ul><li><b>Account and synced data</b>: until you delete your account.</li>
<li><b>Leaderboard</b>: until you leave it or delete your account.</li>
<li><b>Waitlist</b>: until we email you that the feature has launched, then deleted; or sooner if you ask.</li>
<li><b>Price alerts</b>: until you turn alerts off, delete your account, or your browser withdraws the notification address.</li>
<li><b>Error reports</b>: kept by Sentry for up to 90 days.</li>
<li><b>Statistics</b>: kept only as totals.</li>
<li><b>Server logs</b>: kept by our hosting providers for a short period, typically days.</li>
<li><b>Data in your browser</b>: until you clear it.</li></ul>
<h2 id="rights">Your rights and choices</h2>
<p>Depending on where you live (for example under the GDPR and UK GDPR, California’s CCPA, or India’s Digital Personal Data Protection Act), you can ask to access, correct, download or delete your personal information, object to or restrict how we use it, and withdraw consent. We will not treat you differently for using these rights.</p>
<ul><li><b>Delete everything</b>: open <a href="/#/settings">Settings</a>, choose Delete account and type DELETE. This removes your login, your synced data and your leaderboard entry.</li>
<li><b>Turn off statistics and error reports</b>: in <a href="/#/settings">Settings</a>. Global Privacy Control and Do Not Track signals are respected automatically.</li>
<li><b>Anything else</b>: message us at {CONTACT}. We reply within 30 days. If you’re not satisfied, you can complain to your local data protection authority.</li></ul>
<h2 id="children">Children</h2>
<p>Anyone can read Quantera without an account. You must be at least 13 to create an account, join the leaderboard, the waitlist or alerts. If you are under 18, or under the age of digital consent where you live, please use these features only with a parent’s or guardian’s permission. If you believe a child under 13 has given us personal information, contact us and we’ll delete it.</p>
<h2>Security</h2>
<p>Connections are encrypted, passwords are handled by Supabase and never seen by us, database tables are protected by row-level security, and our API keys stay on our server. No system is perfectly secure, so please use a unique password.</p>
<h2 id="contact">Contact and changes</h2>
<p>Questions or requests: {CONTACT}. If this policy changes, we’ll update this page and the date at the top, and tell account holders about significant changes.</p>
"""

TERMS = f"""
<div class="toc"><a href="#who">Who we are</a><a href="#advice">Not investment advice</a><a href="#ai">AI content</a><a href="#data">Data</a><a href="#use">Using the site</a><a href="#refunds">Prices and refunds</a><a href="#ip">Intellectual property</a><a href="#liability">Liability</a></div>
<p>By using Quantera you agree to these terms and to our <a href="/privacy">privacy policy</a>. If you don’t agree, please don’t use the site.</p>
<h2 id="who">Who we are</h2>
<p>Quantera is a free financial research and learning website built and run independently by its founders. Contact us at {CONTACT}.</p>
<h2 id="advice">Education, not investment advice</h2>
<p>Quantera is for learning and research. Nothing on it is investment, financial, tax or legal advice, and nothing is a personal recommendation to buy, sell or hold any security. Quantera is not a registered investment adviser, broker-dealer or research analyst, and it does not know your circumstances, goals or risk tolerance.</p>
<p>Ratings such as an AI outlook (positive, neutral or negative), a technical rating or Wall Street analysts’ consensus are general information produced by formulas, by other people or by AI from public data. They can be wrong and they are not tailored to you. Past performance does not predict future results. Make your own decisions and speak to a licensed professional before investing real money.</p>
<h2>Paper money only</h2>
<p>Trading on Quantera uses virtual money. No real money, securities or crypto change hands, and paper results don’t predict real results.</p>
<h2 id="ai">AI-generated content</h2>
<p>Ask AI answers, research reports and other content marked “AI” are written automatically by AI models. They can misread figures, miss information or state things that aren’t true. Check important facts against the original sources, which we list wherever we can.</p>
<h2 id="data">Data may be delayed or wrong</h2>
<p>Prices, financials, valuations and news come from third parties. They are often delayed (US prices by up to 15 minutes or more) and can be incomplete or incorrect. Private company valuations are estimates from funding rounds and reports, not market prices. Market data is provided for your personal, non-commercial use only; please don’t copy, resell or redistribute it.</p>
<h2>Who can use Quantera</h2>
<p>Anyone can read Quantera. You must be at least 13 to create an account. If you’re under 18, or under the age of majority where you live, please use account features with a parent’s or guardian’s permission.</p>
<h2 id="use">Using the site fairly</h2>
<p>Don’t misuse Quantera: no scraping or bulk downloading, no attempts to break, overload or get around its limits, no automated access, and no offensive, misleading or impersonating leaderboard nicknames. We may remove content or suspend accounts that break these rules.</p>
<h2>Your account and content</h2>
<p>Keep your sign-in details private; you’re responsible for activity under your account. You keep ownership of your notes and nickname. You give us permission to store and display them only as needed to run the site (for example, showing your nickname on the leaderboard if you join). You can delete your account at any time in Settings.</p>
<h2 id="refunds">Prices and refunds</h2>
<p>Quantera is free. We don’t take payments, and nothing is charged today; the Pricing page shows planned plans only. If we launch paid plans, we’ll show the price, what you get, how to cancel and the refund terms before you pay, and paid plans will come with refund terms at least as generous as the law where you live requires (for example, the 14-day right to cancel in the UK and EU).</p>
<h2 id="ip">Intellectual property</h2>
<p>Quantera’s design, text, lessons and code belong to Quantera. Company names, logos and trademarks shown on the site belong to their owners and are used only to identify those companies; Quantera is not affiliated with or endorsed by them or by any data provider. News summaries link to the original publisher, who owns the article.</p>
<h2>Links to other sites</h2>
<p>We link to news publishers, filings and other websites. We don’t control them and aren’t responsible for their content or privacy practices.</p>
<h2 id="liability">No warranty and limits on liability</h2>
<p>Quantera is provided free, “as is” and “as available”. We don’t promise it will always be available, accurate or error-free. To the fullest extent the law allows, we are not liable for any loss or damage from using the site or relying on its content, including investment losses. Nothing in these terms limits rights you have under consumer law that can’t be excluded.</p>
<h2>Changes and ending these terms</h2>
<p>We may update these terms; the date at the top shows the latest version, and we’ll tell account holders about significant changes. You can stop using Quantera at any time. We may suspend or end access if these terms are broken.</p>
"""

COOKIES = f"""
<p><b>Quantera sets no cookies.</b> It uses your browser’s local storage, which works like a notebook kept only on your device, to remember your work and settings.</p>
<h2>What’s stored on your device</h2>
<table><tr><th>What</th><th>Why</th><th>Needed?</th></tr>
<tr><td>Watchlists, paper portfolio, trades and trade journal</td><td>So your practice investing is still there next time</td><td>Yes, for those features</td></tr>
<tr><td>Notes, research folders, thesis notes, recent reports</td><td>So your research is saved</td><td>Yes, for those features</td></tr>
<tr><td>Lesson progress, daily-challenge streak, badges</td><td>To track your learning</td><td>Yes, for those features</td></tr>
<tr><td>Settings: theme, Simple or Pro view, layouts, chart settings, dismissed tips</td><td>To remember how you like the site</td><td>Yes</td></tr>
<tr><td>Your privacy choice</td><td>To remember whether statistics and error reports are on</td><td>Yes</td></tr>
<tr><td>Sign-in session (only if you have an account)</td><td>To keep you signed in; issued by Supabase</td><td>Yes, for accounts</td></tr>
<tr><td>Short-lived page memory (session storage)</td><td>Scroll positions and one-time notices during a visit; cleared when you close the tab</td><td>Yes</td></tr></table>
<p>None of this is used for advertising or to track you across other websites.</p>
<h2>Statistics and error reports</h2>
<p>Vercel Web Analytics counts page views without cookies or storage on your device. Sentry error reports don’t use cookies. Both are optional: turn them off in <a href="/#/settings">Settings</a> (or “Privacy choices” at the bottom of any page), and they stay off if your browser sends Global Privacy Control or Do Not Track.</p>
<h2>Other websites</h2>
<p>If you choose “Continue with Google”, you go to Google’s own sign-in page, where Google’s cookie policy applies. Links to news publishers and other sites take you to their websites, which have their own policies. Quantera doesn’t embed third-party ads, social media widgets or tracking pixels.</p>
<h2>How to clear it</h2>
<p>Clear this site’s data in your browser settings, or delete your account in <a href="/#/settings">Settings</a>. Clearing browser data removes anything you haven’t synced to an account. More in our <a href="/privacy">privacy policy</a>.</p>
"""

ACCESS = f"""
<p>We want everyone to be able to use Quantera, including people who use a keyboard, a screen reader, magnification or voice control. We aim to meet the <a href="https://www.w3.org/TR/WCAG22/" rel="noopener">Web Content Accessibility Guidelines (WCAG) 2.2</a> at level AA.</p>
<h2>What we do</h2>
<ul><li>Every change is checked automatically with axe accessibility tests on 27 pages, in light and dark themes, on desktop and phone sizes. A change that fails can’t ship.</li>
<li>Text and controls are checked for colour contrast in both themes. Price moves use ▲ and ▼ as well as colour.</li>
<li>Everything can be used with a keyboard, with a visible focus outline and a “Skip to content” link.</li>
<li>Form fields have labels, buttons have names, and images have text alternatives.</li>
<li>Animations stop when your device asks for reduced motion, and see-through “glass” effects turn off when it asks for reduced transparency.</li>
<li>Pages scale to fit phones, tablets and large screens without sideways scrolling.</li></ul>
<h2>Known limitations</h2>
<ul><li>Charts are drawn as pictures. Each has a text label, and the key figures appear as text beside it, but individual points on a chart can’t yet be read by a screen reader.</li>
<li>Some older parts of the site still use custom controls that we are replacing with standard ones.</li>
<li>We have tested with automated tools and keyboards; full testing with screen readers is still to come.</li></ul>
<h2>Tell us about a problem</h2>
<p>If something is hard to use, please tell us at {CONTACT}: what page, what you were trying to do and what assistive technology you use. We’ll reply within 5 working days and fix problems as quickly as we can.</p>
"""

PAGES = [
    ('privacy', 'Privacy policy', 'What Quantera collects, why, who it is shared with, how long it is kept and how to remove it.', PRIVACY),
    ('terms', 'Terms of use', 'The rules for using Quantera: education not advice, data limits, AI content, prices and refunds.', TERMS),
    ('cookies', 'Cookies and storage', 'Quantera sets no cookies. What it stores in your browser, and why.', COOKIES),
    ('accessibility', 'Accessibility', 'How Quantera works for everyone, its known limitations and how to report a problem.', ACCESS),
]

if __name__ == '__main__':
    for slug, title, desc, body in PAGES:
        with open(os.path.join(OUT, slug + '.html'), 'w') as f:
            f.write(page(slug, title, desc, body.strip()))
        print('wrote', slug + '.html')
