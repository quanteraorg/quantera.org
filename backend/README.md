# Quantera API (Java 21, no dependencies)

Serves live market data, news, calendars and the Claude-powered AI to the Quantera website.
Keys stay on this server. The browser never sees them.

## Run locally
    ./test.sh                                   # 35 checks against fake providers (no keys needed)
    ./build.sh
    ALPHAVANTAGE_API_KEY=... MARKETAUX_API_TOKEN=... ANTHROPIC_API_KEY=... java -cp out com.quantera.Main
    curl localhost:8080/api/health

## Endpoints
GET /api/health · /api/markets · /api/quote?symbol= · /api/quotes?symbols=A,B · /api/history?symbol=&full=1 · /api/overview?symbol= · /api/search?q= · /api/news?symbols= or ?q= · /api/calendar?type=earnings|ipo · POST /api/ai {"messages":[{"role","content"}]}

## Deploy
Any host that runs Docker or Java 21: Render (New Web Service -> Docker), Fly.io, Railway, AWS, etc.
1. Push this folder to GitHub, create a Web Service from it, choose the Dockerfile.
2. Add the environment variables from .env.example.
3. Open https://YOUR-HOST/api/health  -> {"ok":true,"market":true,"news":true,"ai":true}
4. In the frontend project, put that host name into vercel.json (see ../frontend/README.md).
Note: Vercel itself does not run long-lived Java servers, which is why the API is hosted separately.

## Source map
- Main.java       server, routes, validation, rate limiting, CORS, security headers
- Providers.java  Alpha Vantage + Marketaux clients, normalisation, caching, CSV parsing
- Anthropic.java  Claude chat with server-side tools (quote, history stats, company, search, news)
- Support.java    config, cache, rate limiter, helpers
- Json.java       small JSON reader/writer
- src/test/.../TestMain.java  the 35 checks

## Honest limits
- Docker build is not tested in the authoring environment; the Java code compiles and passes the 35 checks on JDK 21.
- Cache and rate limiter live in memory per instance. Use Redis if you run several instances.
- Not built yet: user accounts, database, Academy progress and Market Lab storage (the research report's Phases 2 to 4).
- Free Alpha Vantage / Marketaux plans are tiny; check their terms before showing data publicly.
