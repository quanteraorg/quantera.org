package com.quantera;

import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;

/** Runs the real server against FAKE Alpha Vantage / Marketaux / Anthropic servers. No keys or internet needed. */
public class TestMain {
    static int pass = 0, fail = 0;
    static final List<String> upstreamCalls = Collections.synchronizedList(new ArrayList<>());
    static final AtomicInteger aiStep = new AtomicInteger();
    static volatile String avMode = "ok";
    static final HttpClient C = HttpClient.newHttpClient();

    static void t(String name, boolean ok) { if (ok) pass++; else fail++; System.out.println((ok ? "PASS " : "FAIL ") + name); }

    record R(int code, Object body, String raw) {}

    static R call(int port, String method, String path, String body, String ip) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("X-Forwarded-For", ip);
        if (method.equals("POST")) b.header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body == null ? "{}" : body)); else b.GET();
        HttpResponse<String> r = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Object j = null;
        try { j = Json.parse(r.body()); } catch (Exception ignored) {}
        return new R(r.statusCode(), j, r.body());
    }
    static String ip() { return "10." + new Random().nextInt(250) + "." + new Random().nextInt(250) + ".1"; }
    static R get(int port, String path) throws Exception { return call(port, "GET", path, null, ip()); }
    @SuppressWarnings("unchecked") static Map<String, Object> m(Object o) { return (Map<String, Object>) o; }
    @SuppressWarnings("unchecked") static List<Object> l(Object o) { return (List<Object>) o; }
    static double d(Object o) { return ((Number) o).doubleValue(); }

    static void reply(HttpExchange ex, int code, String ct, String body) throws java.io.IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", ct);
        ex.sendResponseHeaders(code, b.length);
        ex.getResponseBody().write(b); ex.close();
    }

    static String fn(HttpExchange ex) { for (String p : ex.getRequestURI().getRawQuery().split("&")) if (p.startsWith("function=")) return p.substring(9); return ""; }
    static String sym(HttpExchange ex) { for (String p : ex.getRequestURI().getRawQuery().split("&")) if (p.startsWith("symbol=")) return p.substring(7); return ""; }

    public static void main(String[] a) throws Exception {
        HttpServer fake = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fake.createContext("/av", ex -> {
            upstreamCalls.add(ex.getRequestURI().toString());
            String f = fn(ex), J = "application/json";
            if (avMode.equals("limit")) { reply(ex, 200, J, "{\"Information\":\"rate limit\"}"); return; }
            if (avMode.equals("daily")) { reply(ex, 200, J, "{\"Information\":\"We have detected your API key as AV-TEST and our standard API rate limit is 25 requests per day. Please subscribe to any of the premium plans at https://www.alphavantage.co/premium/ to instantly remove all daily rate limits.\"}"); return; }
            if (avMode.equals("premium")) { reply(ex, 200, J, "{\"Information\":\"Thank you for using Alpha Vantage! This is a premium endpoint. You may subscribe to any of the premium plans at https://www.alphavantage.co/premium/ to instantly unlock all premium endpoints\"}"); return; }
            if (avMode.equals("badkey")) { reply(ex, 200, J, "{\"Error Message\":\"the parameter apikey is invalid or missing. Please claim your free API key on (https://www.alphavantage.co/support/#api-key). It should take less than 20 seconds.\"}"); return; }
            switch (f) {
                case "GLOBAL_QUOTE" -> reply(ex, 200, J, sym(ex).equals("ZZZ") ? "{\"Global Quote\":{}}" : "{\"Global Quote\":{\"05. price\":\"226.10\",\"09. change\":\"1.5\",\"10. change percent\":\"0.67%\",\"06. volume\":\"5000\",\"07. latest trading day\":\"2026-10-05\",\"08. previous close\":\"224.6\"}}");
                case "TIME_SERIES_DAILY" -> reply(ex, 200, J, "{\"Time Series (Daily)\":{\"2026-10-02\":{\"1. open\":\"10\",\"2. high\":\"12\",\"3. low\":\"9\",\"4. close\":\"11\",\"5. volume\":\"100\"},\"2026-10-01\":{\"1. open\":\"9\",\"2. high\":\"10\",\"3. low\":\"8\",\"4. close\":\"10\",\"5. volume\":\"90\"}}}");
                case "SYMBOL_SEARCH" -> reply(ex, 200, J, "{\"bestMatches\":[{\"1. symbol\":\"SHOP\",\"2. name\":\"Shopify\",\"3. type\":\"Equity\",\"4. region\":\"United States\",\"8. currency\":\"USD\"}]}");
                case "OVERVIEW" -> reply(ex, 200, J, "{\"Symbol\":\"AAPL\",\"Name\":\"Apple\",\"AssetType\":\"Common Stock\",\"Sector\":\"TECHNOLOGY\",\"MarketCapitalization\":\"3400000000000\",\"PERatio\":\"33\",\"Beta\":\"1.1\",\"DividendYield\":\"None\"}");
                case "TOP_GAINERS_LOSERS" -> reply(ex, 200, J, "{\"last_updated\":\"2026-10-05 16:00\",\"top_gainers\":[{\"ticker\":\"AAA\",\"price\":\"5\",\"change_amount\":\"1\",\"change_percentage\":\"25%\",\"volume\":\"900\"}],\"top_losers\":[{\"ticker\":\"BBB\",\"price\":\"2\",\"change_amount\":\"-1\",\"change_percentage\":\"-33.3%\",\"volume\":\"50\"}],\"most_actively_traded\":[{\"ticker\":\"CCC\",\"price\":\"9\",\"change_amount\":\"0.1\",\"change_percentage\":\"1.1%\",\"volume\":\"9000\"}]}");
                case "CURRENCY_EXCHANGE_RATE" -> reply(ex, 200, J, "{\"Realtime Currency Exchange Rate\":{\"2. From_Currency Name\":\"Bitcoin\",\"5. Exchange Rate\":\"65000.5\",\"6. Last Refreshed\":\"2026-10-05 20:00:00\"}}");
                case "EARNINGS_CALENDAR" -> reply(ex, 200, "text/csv", "symbol,name,reportDate,fiscalDateEnding,estimate,currency\nAAPL,\"Apple, Inc.\",2026-10-30,2026-09-30,1.5,USD\nMSFT,Microsoft,2026-10-28,2026-09-30,,USD\n");
                case "IPO_CALENDAR" -> reply(ex, 200, "text/csv", "symbol,name,ipoDate,priceRangeLow,priceRangeHigh,currency,exchange\nNEW,NewCo,2026-11-01,10,12,USD,NASDAQ\n");
                default -> reply(ex, 200, J, "{\"Error Message\":\"bad\"}");
            }
        });
        fake.createContext("/mx/news/all", ex -> {
            upstreamCalls.add(ex.getRequestURI().toString());
            reply(ex, 200, "application/json", "{\"data\":[{\"uuid\":\"1\",\"title\":\"T\",\"snippet\":\"S\",\"url\":\"https://x.test\",\"source\":\"Src\",\"published_at\":\"2026-10-05T00:00:00Z\",\"entities\":[{\"symbol\":\"AAPL\",\"name\":\"Apple\",\"sentiment_score\":0.3},{\"symbol\":null,\"name\":\"x\"}]}]}");
        });
        fake.createContext("/fh/quote", ex -> {
            String sy = ""; for (String q : ex.getRequestURI().getRawQuery().split("&")) if (q.startsWith("symbol=")) sy = q.substring(7);
            String tok = ""; for (String q : ex.getRequestURI().getRawQuery().split("&")) if (q.startsWith("token=")) tok = q.substring(6);
            upstreamCalls.add("finnhub " + sy + " token=" + tok);
            reply(ex, 200, "application/json", sy.equals("ZZZ") ? "{\"c\":0,\"d\":null,\"dp\":null,\"pc\":0,\"t\":0}" : "{\"c\":226.6,\"d\":0.56,\"dp\":" + (sy.equals("TSLA") ? "-3.1" : sy.equals("NVDA") ? "2.4" : "0.25") + ",\"h\":227,\"l\":224,\"o\":225,\"pc\":226.04,\"t\":1792800000}");
        });
        fake.createContext("/fh/stock/profile2", ex -> reply(ex, 200, "application/json", "{\"name\":\"Apple Inc\",\"country\":\"US\",\"currency\":\"USD\",\"exchange\":\"NASDAQ\",\"finnhubIndustry\":\"Technology\",\"marketCapitalization\":3400000,\"logo\":\"https://x.test/l.png\",\"weburl\":\"https://apple.com\"}"));
        fake.createContext("/fh/stock/metric", ex -> reply(ex, 200, "application/json", "{\"metric\":{\"peTTM\":33.1,\"beta\":1.2,\"52WeekHigh\":350,\"52WeekLow\":200,\"dividendYieldIndicatedAnnual\":0.45,\"revenueGrowthTTMYoy\":6.1,\"netProfitMarginTTM\":24,\"roeTTM\":150}}"));
        fake.createContext("/fh/search", ex -> reply(ex, 200, "application/json", "{\"result\":[{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP\",\"type\":\"Common Stock\"},{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP.TO\",\"type\":\"Common Stock\"}]}"));
        fake.createContext("/fh/calendar/earnings", ex -> reply(ex, 200, "application/json", "{\"earningsCalendar\":[{\"date\":\"2026-10-30\",\"epsEstimate\":1.5,\"symbol\":\"AAPL\"},{\"date\":\"2026-10-28\",\"epsEstimate\":null,\"symbol\":\"MSFT\"},{\"date\":\"2026-10-29\",\"symbol\":\"RELI.NS\"}]}"));
        fake.createContext("/fh/calendar/ipo", ex -> reply(ex, 200, "application/json", "{\"ipoCalendar\":[{\"date\":\"2026-11-01\",\"exchange\":\"NASDAQ\",\"name\":\"NewCo\",\"price\":\"10.00-12.00\",\"symbol\":\"NEW\"}]}"));
        fake.createContext("/td/time_series", ex -> { upstreamCalls.add("twelvedata " + ex.getRequestURI().getRawQuery()); reply(ex, 200, "application/json", ex.getRequestURI().getRawQuery().contains("symbol=ZZZ") ? "{\"code\":400,\"message\":\"symbol not found\",\"status\":\"error\"}" : "{\"values\":[{\"datetime\":\"2026-10-23\",\"open\":\"11\",\"high\":\"12\",\"low\":\"10\",\"close\":\"11.5\",\"volume\":\"100\"},{\"datetime\":\"2026-10-22\",\"open\":\"10\",\"high\":\"11\",\"low\":\"9\",\"close\":\"10.5\",\"volume\":\"90\"}],\"status\":\"ok\"}"); });
        fake.createContext("/fh/news", ex -> { upstreamCalls.add("finnhub news"); reply(ex, 200, "application/json", "[{\"id\":7001,\"headline\":\"Apple and Nvidia rally as Intelligence report lands\",\"summary\":\"Chip stocks rose.\",\"url\":\"https://x.test/fh1\",\"source\":\"Reuters\",\"datetime\":1792900000,\"related\":\"\"},{\"id\":7002,\"headline\":\"Weekend weather outlook\",\"summary\":\"Sunny.\",\"url\":\"https://x.test/fh2\",\"source\":\"Wire\",\"datetime\":1792900100,\"related\":\"\"}]"); });
        fake.createContext("/fh/company-news", ex -> { upstreamCalls.add("finnhub company-news " + ex.getRequestURI().getRawQuery().split("&")[0]); reply(ex, 200, "application/json", "[{\"id\":8001,\"headline\":\"Tesla deliveries beat\",\"summary\":\"S\",\"url\":\"https://x.test/fh3\",\"source\":\"CNBC\",\"datetime\":1792900200,\"related\":\"TSLA\"}]"); });
        fake.createContext("/mx/news/uuid/", ex -> reply(ex, 200, "application/json", "{\"uuid\":\"11111111-2222-3333-4444-555555555555\",\"title\":\"Apple rises\",\"description\":\"Desc &amp; more\",\"snippet\":\"S\",\"url\":\"https://x.test/a\",\"source\":\"Src\",\"published_at\":\"2026-10-05T00:00:00Z\",\"entities\":[{\"symbol\":\"AAPL\",\"name\":\"Apple\",\"type\":\"equity\",\"industry\":\"Technology\",\"sentiment_score\":0.4,\"highlights\":[{\"highlight\":\"iPhone demand is <em>strong</em>\",\"sentiment\":0.6}]}]}"));
        fake.createContext("/mx/news/similar/", ex -> reply(ex, 200, "application/json", "{\"data\":[{\"uuid\":\"9\",\"title\":\"Related\",\"source\":\"Src\",\"published_at\":\"2026-10-04T00:00:00Z\",\"url\":\"https://x.test/b\"}]}"));
        fake.createContext("/an/v1/messages", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            upstreamCalls.add("anthropic key=" + ex.getRequestHeaders().getFirst("x-api-key") + " hasTools=" + body.contains("get_quote"));
            if (body.contains("AUTHFAIL")) { reply(ex, 401, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"authentication_error\",\"message\":\"invalid x-api-key\"}}"); return; }
            if (body.contains("NOCREDIT")) { reply(ex, 400, "application/json", "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}"); return; }
            if (body.contains("Task: ")) { upstreamCalls.add("explain " + (body.contains("Today's price moves: AAPL +0.25% today") ? "with-moves" : "no-moves")); reply(ex, 200, "application/json", "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"- **What**: Apple rose\"}]}"); return; }
            if (aiStep.incrementAndGet() % 2 == 1) reply(ex, 200, "application/json", "{\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"tool_use\",\"id\":\"tu1\",\"name\":\"get_quote\",\"input\":{\"symbol\":\"AAPL\"}}]}");
            else reply(ex, 200, "application/json", "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"**Data** Apple is $226 [[asset:AAPL]]\"}]}");
        });
        Set<String> joined = Collections.synchronizedSet(new HashSet<>());
        fake.createContext("/sb/rest/v1/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), table = ex.getRequestURI().getPath().substring("/sb/rest/v1/".length());
            upstreamCalls.add("supabase " + table + " key=" + ex.getRequestHeaders().getFirst("apikey") + " " + body);
            if (table.equals("waitlist")) { String email = String.valueOf(m(l(Json.parse(body)).get(0)).get("email")); reply(ex, 201, "application/json", joined.add(email) ? body : "[]"); }
            else reply(ex, 201, "application/json", body);
        });
        fake.start();
        String base = "http://127.0.0.1:" + fake.getAddress().getPort();
        System.setProperty("AV_BASE_URL", base + "/av"); System.setProperty("MARKETAUX_BASE_URL", base + "/mx"); System.setProperty("FINNHUB_BASE_URL", base + "/fh"); System.setProperty("TWELVEDATA_BASE_URL", base + "/td"); System.setProperty("ANTHROPIC_BASE_URL", base + "/an");
        System.setProperty("ALPHAVANTAGE_API_KEY", "av-test"); System.setProperty("MARKETAUX_API_TOKEN", "mx-test"); System.setProperty("ANTHROPIC_API_KEY", "an-test");
        HttpServer app = Main.start(0);
        int p = app.getAddress().getPort();

        R r = get(p, "/api/quote?symbol=aapl");
        t("quote normalised", r.code == 200 && d(m(r.body).get("price")) == 226.1 && d(m(r.body).get("changePct")) == 0.67);
        int n1 = upstreamCalls.size(); get(p, "/api/quote?symbol=aapl");
        t("quote cached (no second upstream call)", upstreamCalls.size() == n1);
        t("API key sent upstream only, never returned", !r.raw.contains("av-test") && upstreamCalls.get(0).contains("apikey=av-test"));
        t("unknown symbol -> 404", get(p, "/api/quote?symbol=ZZZ").code == 404);
        t("bad symbol -> 400", get(p, "/api/quote?symbol=%3Cscript%3E").code == 400);
        r = get(p, "/api/history?symbol=AAPL&full=1");
        t("history sorted oldest first", r.code == 200 && "2026-10-01".equals(l(m(r.body).get("bars")).stream().map(x -> m(x).get("t")).findFirst().orElse(null)) && d(m(l(m(r.body).get("bars")).get(1)).get("c")) == 11);
        r = get(p, "/api/search?q=shop");
        t("search normalised", "SHOP".equals(m(l(m(r.body).get("results")).get(0)).get("symbol")));
        t("search long query -> 400", get(p, "/api/search?q=" + "x".repeat(50)).code == 400);
        r = get(p, "/api/overview?symbol=AAPL");
        t("overview turns \"None\" into null", m(r.body).get("dividendYield") == null && d(m(r.body).get("marketCap")) == 3.4e12);
        r = get(p, "/api/news?symbols=aapl");
        t("news normalised, entities without symbol dropped", l(m(l(m(r.body).get("articles")).get(0)).get("symbols")).size() == 1 && d(m(l(m(l(m(r.body).get("articles")).get(0)).get("symbols")).get(0)).get("sentiment")) == 0.3);
        t("bad news symbols -> 400", get(p, "/api/news?symbols=a%3Bb").code == 400);
        get(p, "/api/news?q=interest%20rates");
        t("news keyword search sent as search param", upstreamCalls.stream().anyMatch(u -> u.contains("search=interest") && u.contains("countries=us")));
        get(p, "/api/news?limit=5");
        t("default news feed: US, tagged companies, major outlets", upstreamCalls.stream().anyMatch(u -> u.contains("countries=us") && u.contains("must_have_entities=true") && u.contains("domains=reuters.com")));
        Providers.CACHE.clear(); System.setProperty("FINNHUB_API_KEY", "fh-test");
        r = get(p, "/api/quote?symbol=AAPL");
        t("Finnhub quote normalised with day change, key kept server-side", r.code == 200 && d(m(r.body).get("changePct")) == 0.25 && "Finnhub".equals(m(r.body).get("source")) && !r.raw.contains("fh-test") && upstreamCalls.stream().anyMatch(u -> u.startsWith("finnhub AAPL token=fh-test")));
        t("Finnhub unknown symbol -> 404", get(p, "/api/quote?symbol=ZZZ").code == 404);
        r = get(p, "/api/news?symbols=AAPL&limit=3");
        Map<String, Object> s0 = m(l(m(l(m(r.body).get("articles")).get(0)).get("symbols")).get(0));
        t("news symbols carry today's move (AAPL +0.25%), US tickers first", r.code == 200 && "AAPL".equals(s0.get("symbol")) && d(s0.get("changePct")) == 0.25);
        t("tagger finds companies by name and exchange ticker, not inside other words", Providers.tag("Apple and Nvidia rally; Intelligence report (NYSE: XOM)", "").equals(List.of("XOM", "AAPL", "NVDA")));
        Providers.CACHE.clear();
        r = get(p, "/api/markets");
        Map<String, Object> mvs = m(m(r.body).get("movers"));
        t("movers come from Finnhub quotes of large US companies", r.code == 200 && "NVDA".equals(m(l(mvs.get("gainers")).get(0)).get("symbol")) && "TSLA".equals(m(l(mvs.get("losers")).get(0)).get("symbol")) && "Largest US companies".equals(mvs.get("universe")));
        Providers.CACHE.clear();
        r = get(p, "/api/news?limit=10");
        List<Object> arts = l(m(r.body).get("articles"));
        t("main feed merges Finnhub stories tagged with stocks, drops untagged ones", r.code == 200 && arts.stream().anyMatch(x -> "fh7001".equals(m(x).get("id")) && l(m(x).get("symbols")).size() == 2) && arts.stream().noneMatch(x -> "fh7002".equals(m(x).get("id"))));
        r = get(p, "/api/news?symbols=TSLA&limit=6");
        t("single-stock news adds that company's Finnhub news with its move", r.code == 200 && l(m(r.body).get("articles")).stream().anyMatch(x -> "fh8001".equals(m(x).get("id")) && d(m(l(m(x).get("symbols")).get(0)).get("changePct")) == -3.1));
        r = get(p, "/api/overview?symbol=AAPL");
        t("Finnhub profile + metrics in old units (cap in $, yield/margins as fractions)", r.code == 200 && "Apple Inc".equals(m(r.body).get("name")) && d(m(r.body).get("marketCap")) == 3.4e12 && Math.abs(d(m(r.body).get("dividendYield")) - 0.0045) < 1e-9 && Math.abs(d(m(r.body).get("profitMargin")) - 0.24) < 1e-9 && d(m(r.body).get("pe")) == 33.1);
        r = get(p, "/api/search?q=shop");
        t("Finnhub search keeps US listings only", r.code == 200 && l(m(r.body).get("results")).size() == 1 && "Equity".equals(m(l(m(r.body).get("results")).get(0)).get("type")));
        r = get(p, "/api/calendar?type=earnings");
        t("Finnhub earnings calendar sorted, US tickers only", r.code == 200 && l(m(r.body).get("items")).size() == 2 && "MSFT".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")));
        r = get(p, "/api/calendar?type=ipo");
        t("Finnhub IPO price range parsed", r.code == 200 && d(m(l(m(r.body).get("items")).get(0)).get("high")) == 12);
        t("crypto from Finnhub", d(Providers.fx("BTC", "USD").get("rate")) == 226.6);
        System.setProperty("TWELVEDATA_API_KEY", "td-test");
        r = get(p, "/api/history?symbol=AAPL");
        t("Twelve Data history oldest first, key server-side", r.code == 200 && "2026-10-22".equals(m(l(m(r.body).get("bars")).get(0)).get("t")) && d(m(l(m(r.body).get("bars")).get(1)).get("c")) == 11.5 && "Twelve Data".equals(m(r.body).get("source")) && !r.raw.contains("td-test") && upstreamCalls.stream().anyMatch(u -> u.startsWith("twelvedata") && u.contains("apikey=td-test")));
        t("Twelve Data unknown symbol -> 404", get(p, "/api/history?symbol=ZZZ").code == 404);
        System.clearProperty("TWELVEDATA_API_KEY");
        r = call(p, "POST", "/api/explain", "{\"lens\":\"five_w\",\"title\":\"Apple rises\",\"summary\":\"Shares rose.\",\"symbols\":[\"AAPL\"]}", ip());
        t("explain: lens answer grounded with today's move", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple rose") && upstreamCalls.contains("explain with-moves"));
        int ne = upstreamCalls.size(); call(p, "POST", "/api/explain", "{\"lens\":\"five_w\",\"title\":\"Apple rises\",\"summary\":\"Shares rose.\",\"symbols\":[\"AAPL\"]}", ip());
        t("explain: repeat answer served from cache", upstreamCalls.size() == ne);
        r = call(p, "POST", "/api/explain", "{\"lens\":\"tldr\",\"title\":\"AUTHFAIL\"}", ip());
        t("AI invalid key -> clear 503 message", r.code == 503 && r.raw.contains("key is invalid"));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"tldr\",\"title\":\"NOCREDIT\"}", ip());
        t("AI out of credits -> clear 503 message", r.code == 503 && r.raw.contains("out of credits"));
        t("explain: unknown lens -> 400", call(p, "POST", "/api/explain", "{\"lens\":\"hack\",\"title\":\"x\"}", ip()).code == 400);
        System.clearProperty("FINNHUB_API_KEY"); Providers.CACHE.clear();
        t("news bad search text -> 400", get(p, "/api/news?q=%3Cscript%3E").code == 400);
        r = get(p, "/api/markets");
        t("markets combines movers, index ETFs and crypto", r.code == 200 && d(m(l(m(m(r.body).get("movers")).get("gainers")).get(0)).get("changePct")) == 25 && d(m(l(m(m(r.body).get("movers")).get("losers")).get(0)).get("changePct")) == -33.3 && l(m(r.body).get("indices")).size() == 4 && d(m(l(m(r.body).get("crypto")).get(0)).get("rate")) == 65000.5);
        r = get(p, "/api/quotes?symbols=AAPL,ZZZ");
        t("quotes batch returns found + lists missing", r.code == 200 && l(m(r.body).get("quotes")).size() == 1 && "ZZZ".equals(l(m(r.body).get("missing")).get(0)));
        t("quotes rejects more than 8 symbols", get(p, "/api/quotes?symbols=A,B,C,D,E,F,G,H,I").code == 400);
        r = get(p, "/api/calendar?type=earnings");
        t("calendar parses CSV incl. quoted commas and blank estimates", r.code == 200 && "Microsoft".equals(m(l(m(r.body).get("items")).get(0)).get("name")) && "Apple, Inc.".equals(m(l(m(r.body).get("items")).get(1)).get("name")) && m(l(m(r.body).get("items")).get(0)).get("estimate") == null);
        r = get(p, "/api/calendar?type=ipo");
        t("ipo calendar parsed", "NEW".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && d(m(l(m(r.body).get("items")).get(0)).get("high")) == 12);
        t("calendar bad type -> 400", get(p, "/api/calendar?type=x").code == 400);
        Providers.CACHE.clear(); avMode = "limit";
        r = get(p, "/api/quote?symbol=MSFT");
        t("provider rate limit -> 429 with friendly message", r.code == 429 && r.raw.toLowerCase().contains("check back"));
        int nb = upstreamCalls.size(); r = get(p, "/api/quote?symbol=IBM");
        t("while blocked, no further provider calls are made", r.code == 429 && upstreamCalls.size() == nb);
        Providers.AV_BLOCKED_UNTIL = 0; Providers.CACHE.clear(); avMode = "daily";
        t("daily limit mentioning premium plans -> 429, not 402", get(p, "/api/quote?symbol=MSFT").code == 429);
        Providers.AV_BLOCKED_UNTIL = 0; Providers.CACHE.clear(); avMode = "premium";
        t("premium-only endpoint -> 402", get(p, "/api/quote?symbol=MSFT").code == 402);
        Providers.CACHE.clear(); avMode = "badkey";
        t("invalid API key -> 503, not symbol not found", get(p, "/api/quote?symbol=MSFT").code == 503); avMode = "ok"; Providers.AV_BLOCKED_UNTIL = 0;
        Providers.CACHE.get("stale-test", 0, () -> "good");
        t("rate-limited refresh serves the last good value", "good".equals(Providers.CACHE.get("stale-test", 0, () -> { throw new Support.UpstreamException("limit", 429); })));
        boolean nf = false; try { Providers.CACHE.get("stale-test", 0, () -> { throw new Support.UpstreamException("gone", 404); }); } catch (Support.UpstreamException e) { nf = e.code == 404; }
        t("not-found errors are not hidden by stale data", nf);
        r = get(p, "/api/article?id=11111111-2222-3333-4444-555555555555");
        t("article: entities with plain-text highlights + similar stories", r.code == 200 && "AAPL".equals(m(l(m(r.body).get("entities")).get(0)).get("symbol")) && "iPhone demand is strong".equals(m(l(m(l(m(r.body).get("entities")).get(0)).get("highlights")).get(0)).get("text")) && l(m(r.body).get("similar")).size() == 1);
        t("article rejects bad id", get(p, "/api/article?id=../etc").code == 400);
        HttpResponse<String> head = C.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        t("HEAD probe gets a status without a body", head.statusCode() == 404 && head.body().isEmpty());
        String join = "{\"email\":\"Ada@Example.com\",\"interest\":\"Pro\",\"source\":\"pricing\"}";
        t("waitlist without storage -> 503", call(p, "POST", "/api/waitlist", join, ip()).code == 503);
        System.setProperty("SUPABASE_URL", base + "/sb/"); System.setProperty("SUPABASE_SERVICE_KEY", "sb-test");
        r = call(p, "POST", "/api/waitlist", join, ip());
        t("waitlist stores lower-cased email via Supabase, key server-side only", r.code == 200 && Boolean.FALSE.equals(m(r.body).get("alreadyJoined")) && upstreamCalls.stream().anyMatch(u -> u.startsWith("supabase waitlist key=sb-test") && u.contains("ada@example.com")) && !r.raw.contains("sb-test"));
        r = call(p, "POST", "/api/waitlist", join, ip());
        t("waitlist duplicate -> alreadyJoined", r.code == 200 && Boolean.TRUE.equals(m(r.body).get("alreadyJoined")));
        t("waitlist rejects bad email", call(p, "POST", "/api/waitlist", "{\"email\":\"nope\"}", ip()).code == 400);
        t("waitlist rejects GET", get(p, "/api/waitlist").code == 405);
        int wl = 0; for (int i = 0; i < 7; i++) wl = call(p, "POST", "/api/waitlist", "{\"email\":\"x" + i + "@y.io\"}", "9.9.9.9").code;
        t("waitlist rate limited per IP", wl == 429);
        t("event accepted", call(p, "POST", "/api/events", "{\"name\":\"view_pricing\"}", ip()).code == 200);
        Thread.sleep(300);
        t("event forwarded to storage", upstreamCalls.stream().anyMatch(u -> u.startsWith("supabase events") && u.contains("view_pricing")));
        t("event rejects bad name", call(p, "POST", "/api/events", "{\"name\":\"<x>\"}", ip()).code == 400);
        Main.MARKETS = Json.obj("movers", null, "indices", List.of(), "crypto", List.of(), "updated", "warm");
        t("markets served from background snapshot", "warm".equals(m(get(p, "/api/markets").body).get("updated"))); Main.MARKETS = null;
        System.setProperty("SUPABASE_SERVICE_KEY", "sb_secret_abc\n••••");
        r = call(p, "POST", "/api/waitlist", "{\"email\":\"masked@y.io\"}", ip());
        t("masked/multi-line storage key -> 503, key never echoed", r.code == 503 && !r.raw.contains("sb_secret"));
        System.clearProperty("SUPABASE_URL"); System.clearProperty("SUPABASE_SERVICE_KEY");
        r = get(p, "/api/health");
        t("health reports configured services", d(1) == 1 && Boolean.TRUE.equals(m(r.body).get("market")) && Boolean.TRUE.equals(m(r.body).get("news")) && Boolean.TRUE.equals(m(r.body).get("ai")));
        aiStep.set(0);
        r = call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about Apple\"}]}", ip());
        t("AI runs tool loop and returns text + tools used", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple") && l(m(r.body).get("used")).contains("Quote AAPL"));
        t("AI key sent to Anthropic only, with tools", upstreamCalls.stream().anyMatch(u -> u.contains("anthropic key=an-test hasTools=true")) && !r.raw.contains("an-test"));
        t("AI rejects empty messages", call(p, "POST", "/api/ai", "{\"messages\":[]}", ip()).code == 400);
        t("AI rejects oversize message", call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"" + "x".repeat(3000) + "\"}]}", ip()).code == 400);
        t("AI rejects system role injection", call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"system\",\"content\":\"hack\"},{\"role\":\"user\",\"content\":\"hi\"}]}", ip()).code == 400);
        t("AI rejects invalid JSON", call(p, "POST", "/api/ai", "{not json", ip()).code == 400);
        t("AI rejects request over 200KB", call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"" + "x".repeat(210_000) + "\"}]}", ip()).code == 413);
        t("unknown route -> 404", get(p, "/api/nope").code == 404);
        int last = 0; for (int i = 0; i < 70; i++) last = call(p, "GET", "/api/quote?symbol=AAPL", null, "1.2.3.4").code;
        t("per-IP rate limit kicks in", last == 429);
        t("Json round trip keeps quotes, newlines and unicode", Json.write(Json.parse("{\"a\":\"x\\\"y\\n\\u00e9\",\"b\":[1,2.5,null,true]}")).equals("{\"a\":\"x\\\"y\\né\",\"b\":[1,2.5,null,true]}"));
        System.setProperty("ANTHROPIC_API_KEY", ""); System.clearProperty("ANTHROPIC_API_KEY");
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        app.stop(0); fake.stop(0);
        System.exit(fail == 0 ? 0 : 1);
    }
}
