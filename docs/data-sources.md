# Data sources register

A factual list of every outside service Quantera reads data from or sends data to, taken from the code as of
November 2026. It records what is used and where. It draws **no conclusions about licensing**. "Unverified" means no
plan or licence document is kept in this repo. The owner's open questions about terms are tracked separately.

Server cache times come from each route's TTL in `backend/src/main/java/com/quantera/Main.java` (the CDN may keep a
response for that long). When a provider is rate limited or down, `Support.Cache` can serve the last good value for up
to 24 hours. That value is marked `stale: true` with an `asOf` time, and the site says so.

| Provider | What we use | Where it's shown | Caching | Attribution shown | Plan / licence |
|---|---|---|---|---|---|
| **Yahoo Finance** (unofficial public endpoints `query1.finance.yahoo.com`, `fc.yahoo.com`) | Quotes, daily and intraday charts, company profiles, fundamentals and key ratios, analyst estimates, screener, worldwide search, index levels, 44-country world markets | Asset, markets, world, screener, compare and stock pages; public SEO stock pages (`/stock/…`); AI tool results | Quotes 60 s, history 1 h, overview 24 h, fundamentals 15 min, world 60 s–2 min, screener 5 min | "Data from Yahoo Finance" / "via Yahoo Finance" on several pages; SEO pages show "Source: … as of …"; AI answers cite "(Yahoo Finance, …)" | Unverified (README notes it is unofficial and unlicensed; switch off with `YAHOO_ENABLED=0`) |
| **Forge Global** (via Yahoo Finance) | Private-company estimated valuations, price per share, funding rounds | Private companies list and pages (`/private-companies`, `/private-company/…`), app's private page | 10–30 min | "Data: Forge Global via Yahoo Finance"; method explained on each page | Unverified |
| **Finnhub** (`finnhub.io/api`) | Live US quotes (quote warmer, about 40 calls/min in session) | Prices across the app, tape, alerts checker | 60 s | "From market data providers including Finnhub"; AI cites "(Finnhub, …)" | Unverified (free plan per README) |
| **Twelve Data** (`api.twelvedata.com`) | Daily series for sparklines, intraday bars | Sparklines, intraday charts | History 1 h, intraday 60 s; series refreshed when older than 6 h | Credited in intraday chart notes | Unverified (free plan per README) |
| **Alpha Vantage** (`alphavantage.co/query`) | Fallback quotes and daily history when Finnhub / Twelve Data aren't set | Prices and charts (fallback only) | Quotes 60 s, history 1 h | None found | Unverified |
| **Marketaux** (`api.marketaux.com/v1`) | News articles with tagged companies and sentiment; story pages | News, stock pages' latest news, story explanations | News 20 s, article 1 h | Each story shows its original publisher | Unverified |
| **SEC EDGAR** (`data.sec.gov`, `www.sec.gov`) | Filings lists, XBRL company facts (financial statements), company tickers directory | Filings, financials and statements pages, scorecard inputs, AI `get_financial_statements` tool | Filings 1 h, financials and statements 6 h | "SEC EDGAR" credit; AI cites "(SEC 10-K, …)"; links to the filing | Public data. SEC asks for a contact in the User-Agent (`SEC_USER_AGENT`) |
| **FRED** (`api.stlouisfed.org`) | Economic series and release calendar | Economy dashboard (`#/macro`), calendar | 30 min | "FRED" credit with links to `fred.stlouisfed.org` series | Unverified (free API key) |
| **Anthropic** (`api.anthropic.com`) | Claude for Ask AI chat, story explanations and research reports (when `AI_PROVIDER=anthropic`) | Ask AI, story "explain" lenses, research report | Explanations 6 h, reports 12 h; chat not cached | Labelled as AI; "not investment advice" | Unverified (paid API key) |
| **Google Gemini** (`generativelanguage.googleapis.com`) | Same AI features when Gemini is the provider | Same as Anthropic | Same as Anthropic | Same as Anthropic | Unverified (free tier per README) |
| **Vercel Web Analytics** | Anonymous page-view statistics (opt-out in privacy choices) | Not shown | n/a | Privacy page | Unverified |
| **Sentry** (`*.ingest.us.sentry.io`) | Browser error reports only (no replays or tracing; login tokens scrubbed) | Not shown | n/a | Privacy page | Unverified |
| **Supabase** (`*.supabase.co`) | Sign-in, synced settings (`user_data`), leaderboard, waitlist, feedback, events, price alerts, reaction events | Account, leaderboard, alerts | n/a (database) | Privacy page | Unverified |

## Keeping this current
Add a row whenever the code starts calling a new outside host (search the backend for `https://`), and update the
"Caching" column when a route's TTL changes.
