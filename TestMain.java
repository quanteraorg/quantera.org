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
        fake.createContext("/an/v1/messages", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            upstreamCalls.add("anthropic key=" + ex.getRequestHeaders().getFirst("x-api-key") + " hasTools=" + body.contains("get_quote"));
            if (aiStep.incrementAndGet() % 2 == 1) reply(ex, 200, "application/json", "{\"stop_reason\":\"tool_use\",\"content\":[{\"type\":\"tool_use\",\"id\":\"tu1\",\"name\":\"get_quote\",\"input\":{\"symbol\":\"AAPL\"}}]}");
            else reply(ex, 200, "application/json", "{\"stop_reason\":\"end_turn\",\"content\":[{\"type\":\"text\",\"text\":\"**Data** Apple is $226 [[asset:AAPL]]\"}]}");
        });
        fake.start();
        String base = "http://127.0.0.1:" + fake.getAddress().getPort();
        System.setProperty("AV_BASE_URL", base + "/av"); System.setProperty("MARKETAUX_BASE_URL", base + "/mx"); System.setProperty("ANTHROPIC_BASE_URL", base + "/an");
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
        t("news keyword search sent as search param", upstreamCalls.stream().anyMatch(u -> u.contains("search=interest")));
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
        t("provider rate limit -> 429 with friendly message", r.code == 429 && r.raw.toLowerCase().contains("rate limit")); avMode = "ok";
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
