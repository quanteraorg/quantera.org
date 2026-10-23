# Quantera API (Java 21, no dependencies)

Serves live market data, news, calendars, the waitlist and the Claude-powered AI to the Quantera website.
Keys stay on this server. The browser never sees them.

## Run locally
    ./test.sh                                   # 45 checks against fake providers (no keys needed)
    ./build.sh
    ALPHAVANTAGE_API_KEY=... MARKETAUX_API_TOKEN=... ANTHROPIC_API_KEY=... java -cp out com.quantera.Main
    curl localhost:8080/api/health
To run the website and API together on one origin (mock data when no keys are set):

    STATIC_DIR=../frontend/public java -cp out com.quantera.Main   # then open http://localhost:8080

## Endpoints
GET /api/health · /api/markets · /api/quote?symbol= · /api/quotes?symbols=A,B · /api/history?symbol=&full=1 · /api/overview?symbol= · /api/search?q= · /api/news?symbols= or ?q= · /api/calendar?type=earnings|ipo · POST /api/ai {"messages":[{"role","content"}]} · POST /api/waitlist {"email","interest","source"} · POST /api/events {"name"}

## Waitlist and usage events (Supabase)
1. Create a free project at supabase.com and run this in its SQL editor:

        create table waitlist (id bigint generated always as identity primary key, email text not null unique, interest text, source text, created_at timestamptz not null default now());
        create table events (id bigint generated always as identity primary key, name text not null, created_at timestamptz not null default now());
        alter table waitlist enable row level security;
        alter table events enable row level security;

   Row level security with no policies keeps both tables closed to the public; the server uses the service key.
2. Set SUPABASE_URL (Project Settings -> API -> Project URL) and SUPABASE_SERVICE_KEY (the service_role key) on the server.
3. /api/health then reports "storage":true. Without them, /api/waitlist answers 503 and events are dropped.

## Scheduled market refresh
Set MARKET_REFRESH_SEC (60 or more, e.g. 300) to rebuild /api/markets in the background on a fixed schedule.
Upstream calls then stay flat however many visitors arrive. Leave it unset on a free Alpha Vantage key:
each refresh makes 7 calls, and the free key allows about 25 a day.

## Deploy
Any host that runs Docker or Java 21: Render (New Web Service -> Docker), Fly.io, Railway, AWS, etc.
1. Push this folder to GitHub, create a Web Service from it, choose the Dockerfile.
2. Add the environment variables from .env.example.
3. Open https://YOUR-HOST/api/health  -> {"ok":true,"market":true,"news":true,"ai":true,"storage":true}
4. In the frontend project, put that host name into vercel.json (see ../frontend/README.md).
Note: Vercel itself does not run long-lived Java servers, which is why the API is hosted separately.

## Source map
- Main.java       server, routes, validation, rate limiting, CORS, security headers, markets refresh, local static files
- Providers.java  Alpha Vantage + Marketaux clients, normalisation, caching, CSV parsing
- Anthropic.java  Claude chat with server-side tools (quote, history stats, company, search, news)
- Store.java      waitlist and usage events in Supabase (REST, service key server-side only)
- Support.java    config, cache, rate limiter, helpers
- Json.java       small JSON reader/writer
- src/test/.../TestMain.java  the 45 checks

## Honest limits
- Cache and rate limiter live in memory per instance. Use Redis if you run several instances.
- Not built yet: user accounts, Academy progress and Market Lab storage.
- Free Alpha Vantage / Marketaux plans are tiny; check their terms before showing data publicly.
