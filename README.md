# Quantera (Vercel project)

Static site in `public/` plus serverless JavaScript routes in `api/` that call Alpha Vantage (prices, fundamentals, search), Marketaux (news) and the Anthropic API (AI chat). Keys live only in Vercel environment variables, never in the browser.

## Deploy (GitHub -> Vercel)
1. Create a GitHub repo and push this folder (the `.gitignore` already excludes `.env`).
2. In Vercel: Add New Project -> import the repo. No build command, no framework. Output directory is detected automatically (`public/`).
3. Project -> Settings -> Environment Variables, add:
   - `ALPHAVANTAGE_API_KEY`
   - `MARKETAUX_API_TOKEN`
   - `ANTHROPIC_API_KEY` (and optionally `ANTHROPIC_MODEL`)
4. Redeploy. Open `/api/health` on your domain: it should show `{"ok":true,"market":true,"news":true,"ai":true}`.
5. Open the site. Stock pages now say "Live end-of-day data via Alpha Vantage", the News tab shows Marketaux headlines, and the AI uses the Claude API.

If a service is not configured or hits its limit, the site quietly falls back to its built-in sample data and tells the user.

## Routes
GET `/api/health` · `/api/quote?symbol=` · `/api/history?symbol=&full=1` · `/api/overview?symbol=` · `/api/search?q=` · `/api/news?symbols=` · POST `/api/ai` with `{messages:[{role,content}]}`

## Test
`npm test` runs 19 checks against fake provider responses (no keys needed).

## Know before you launch
- **Free tiers are small.** Alpha Vantage's free key allows only a handful of requests per day and Marketaux's free plan limits requests and articles per call (confirm current limits on their sites). Responses are cached (60 s to 24 h) but a real launch needs paid plans.
- **Licensing.** Check each vendor's terms for commercial display and redistribution before charging users or advertising. Prices here are end-of-day, not real-time.
- **Intraday charts** (5m to 4h) are hidden for live symbols because only daily data is fetched.
- **AI costs money** per request. The route limits input size and requests per minute; add a stronger shared limiter (e.g. Upstash Redis) and usage caps before going viral.
- Markets overview, Screener, Compare and Portfolio still use the built-in sample universe. Wire them to the routes next.
- Rotate any key that was ever pasted into a chat or committed to git.
