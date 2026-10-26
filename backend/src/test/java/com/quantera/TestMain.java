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
        fake.createContext("/fh/stock/symbol", ex -> { String mic = ex.getRequestURI().getRawQuery().replaceAll(".*mic=([A-Z]+).*", "$1"); List<Object> rows = l(Json.parse("[{\"symbol\":\"AAPL\",\"description\":\"APPLE INC\",\"type\":\"Common Stock\",\"mic\":\"XNAS\"},{\"symbol\":\"SPY\",\"description\":\"SPDR S&P 500 ETF\",\"type\":\"ETP\",\"mic\":\"ARCX\"},{\"symbol\":\"ABCD\",\"description\":\"OTC CO\",\"type\":\"Common Stock\",\"mic\":\"OOTC\"},{\"symbol\":\"XYZW\",\"description\":\"WARRANT\",\"type\":\"Warrant\",\"mic\":\"XNAS\"}]")); rows.removeIf(o -> !mic.equals(m(o).get("mic"))); reply(ex, 200, "application/json", Json.write(rows)); });
        fake.createContext("/fh/stock/profile2", ex -> reply(ex, 200, "application/json", "{\"name\":\"Apple Inc\",\"country\":\"US\",\"currency\":\"USD\",\"exchange\":\"NASDAQ\",\"finnhubIndustry\":\"Technology\",\"marketCapitalization\":3400000,\"logo\":\"https://x.test/l.png\",\"weburl\":\"https://apple.com\"}"));
        fake.createContext("/fh/stock/metric", ex -> reply(ex, 200, "application/json", "{\"metric\":{\"peTTM\":33.1,\"beta\":1.2,\"52WeekHigh\":350,\"52WeekLow\":200,\"dividendYieldIndicatedAnnual\":0.45,\"revenueGrowthTTMYoy\":6.1,\"netProfitMarginTTM\":24,\"roeTTM\":150}}"));
        fake.createContext("/fh/search", ex -> { upstreamCalls.add("finnhub search"); reply(ex, 200, "application/json", "{\"result\":[{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP\",\"type\":\"Common Stock\"},{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP.TO\",\"type\":\"Common Stock\"}]}"); });
        fake.createContext("/fh/calendar/earnings", ex -> reply(ex, 200, "application/json", "{\"earningsCalendar\":[{\"date\":\"2026-10-30\",\"epsEstimate\":1.5,\"symbol\":\"AAPL\"},{\"date\":\"2026-10-28\",\"epsEstimate\":null,\"symbol\":\"MSFT\"},{\"date\":\"2026-10-29\",\"symbol\":\"RELI.NS\"}]}"));
        fake.createContext("/fh/calendar/ipo", ex -> reply(ex, 200, "application/json", "{\"ipoCalendar\":[{\"date\":\"2026-11-01\",\"exchange\":\"NASDAQ\",\"name\":\"NewCo\",\"price\":\"10.00-12.00\",\"symbol\":\"NEW\"}]}"));
        fake.createContext("/td/time_series", ex -> { upstreamCalls.add("twelvedata " + ex.getRequestURI().getRawQuery()); reply(ex, 200, "application/json", ex.getRequestURI().getRawQuery().contains("symbol=ZZZ") ? "{\"code\":400,\"message\":\"symbol not found\",\"status\":\"error\"}" : "{\"values\":[{\"datetime\":\"2026-10-23\",\"open\":\"11\",\"high\":\"12\",\"low\":\"10\",\"close\":\"11.5\",\"volume\":\"100\"},{\"datetime\":\"2026-10-22\",\"open\":\"10\",\"high\":\"11\",\"low\":\"9\",\"close\":\"10.5\",\"volume\":\"90\"}],\"status\":\"ok\"}"); });
        fake.createContext("/gm/v1beta/models/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            upstreamCalls.add("gemini " + ex.getRequestURI().getPath() + " key=" + ex.getRequestHeaders().getFirst("x-goog-api-key") + (body.contains("\"OBJECT\"") ? " schema-ok" : ""));
            if (body.contains("BADKEY")) { reply(ex, 400, "application/json", "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\",\"status\":\"INVALID_ARGUMENT\"}}"); return; }
            String txt = body.contains("functionResponse") ? "**Data** Apple is $226 [[asset:AAPL]]" : body.contains("Task: ") ? "- **What**: chips rose" : null;
            reply(ex, 200, "application/json", txt != null ? "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":\"" + txt.replace("\"", "\\\"") + "\"}]}}]}"
                    : "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"functionCall\":{\"name\":\"get_quote\",\"args\":{\"symbol\":\"AAPL\"}}}]}}]}");
        });
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
        // FRED: monthly series 2020-01 .. 2026-09 with value 100 + month index; daily series with "." holes
        fake.createContext("/fred/graph/fredgraph.csv", ex -> { String id = ex.getRequestURI().getRawQuery().replaceAll(".*id=([A-Z0-9]+).*", "$1"); upstreamCalls.add("fred " + id);
            StringBuilder b = new StringBuilder("observation_date," + id + "\n"); java.time.LocalDate d0 = java.time.LocalDate.of(2020, 1, 1);
            for (int i = 0; i < 81; i++) b.append(d0.plusMonths(i)).append(',').append(i == 40 ? "." : String.valueOf(100 + i)).append('\n');
            reply(ex, 200, "text/csv", b.toString()); });
        fake.createContext("/fredapi/fred/series/observations", ex -> { upstreamCalls.add("fredapi " + ex.getRequestURI().getRawQuery()); StringBuilder b = new StringBuilder("{\"observations\":["); java.time.LocalDate d0 = java.time.LocalDate.of(2020, 1, 1);
            for (int i = 0; i < 81; i++) b.append(i == 0 ? "" : ",").append("{\"date\":\"").append(d0.plusMonths(i)).append("\",\"value\":\"").append(i == 40 ? "." : String.valueOf(100 + i)).append("\"}");
            reply(ex, 200, "application/json", b.append("]}").toString()); });
                fake.createContext("/sec/files/company_tickers.json", ex -> { upstreamCalls.add("sec tickers ua=" + ex.getRequestHeaders().getFirst("User-Agent")); reply(ex, 200, "application/json", "{\"0\":{\"cik_str\":320193,\"ticker\":\"AAPL\",\"title\":\"Apple Inc.\"},\"1\":{\"cik_str\":1067983,\"ticker\":\"BRK-B\",\"title\":\"Berkshire\"}}"); });
        fake.createContext("/secdata/submissions/", ex -> { String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
            reply(ex, 200, "application/json", "{\"name\":\"Apple Inc.\",\"sicDescription\":\"Electronic Computers\",\"fiscalYearEnd\":\"0927\",\"filings\":{\"recent\":{\"form\":[\"4\",\"8-K\",\"SD\",\"10-K\"],\"filingDate\":[\"" + today + "\",\"" + today + "\",\"2026-05-01\",\"2025-10-31\"],\"accessionNumber\":[\"0001-26-000001\",\"0001-26-000002\",\"0001-26-000003\",\"0000320193-25-000079\"],\"primaryDocument\":[\"x.xml\",\"e.htm\",\"sd.htm\",\"aapl-20250927.htm\"],\"primaryDocDescription\":[\"\",\"8-K\",\"SD\",\"10-K\"],\"reportDate\":[\"\",\"\",\"\",\"2025-09-27\"]}}}"); });
        fake.createContext("/secdata/api/xbrl/companyfacts/", ex -> reply(ex, 200, "application/json", "{\"facts\":{\"us-gaap\":{"
            + "\"Revenues\":{\"units\":{\"USD\":[{\"end\":\"2023-09-30\",\"val\":383000000000,\"frame\":\"CY2023\"},{\"end\":\"2024-09-28\",\"val\":391000000000,\"frame\":\"CY2024\"},{\"end\":\"2024-12-28\",\"val\":124000000000,\"frame\":\"CY2024Q4\"},{\"end\":\"2024-09-28\",\"val\":1}]}},"
            + "\"NetIncomeLoss\":{\"units\":{\"USD\":[{\"end\":\"2024-09-28\",\"val\":93700000000,\"frame\":\"CY2024\"}]}},"
            + "\"EarningsPerShareDiluted\":{\"units\":{\"USD/shares\":[{\"end\":\"2024-09-28\",\"val\":6.08,\"frame\":\"CY2024\"},{\"start\":\"2022-10-01\",\"end\":\"2023-09-30\",\"val\":12.4,\"frame\":\"CY2023\",\"filed\":\"2023-11-03\"},{\"start\":\"2022-10-01\",\"end\":\"2023-09-30\",\"val\":6.2,\"filed\":\"2024-11-01\"}]}},"
            + "\"NetCashProvidedByUsedInOperatingActivities\":{\"units\":{\"USD\":[{\"end\":\"2024-09-28\",\"val\":118000000000,\"frame\":\"CY2024\"}]}},"
            + "\"PaymentsToAcquirePropertyPlantAndEquipment\":{\"units\":{\"USD\":[{\"end\":\"2024-09-28\",\"val\":9400000000,\"frame\":\"CY2024\"}]}},"
            + "\"Assets\":{\"units\":{\"USD\":[{\"end\":\"2024-12-28\",\"val\":344000000000,\"frame\":\"CY2024Q4I\"}]}}}}}"));
        // Supabase table push_alerts kept in memory; a push service that records what it receives
        List<Map<String, Object>> pushRows = Collections.synchronizedList(new ArrayList<>());
        List<String[]> pushed = Collections.synchronizedList(new ArrayList<>());
        java.util.concurrent.atomic.AtomicInteger pushIds = new java.util.concurrent.atomic.AtomicInteger();
        fake.createContext("/sb/rest/v1/push_alerts", ex -> { String q = ex.getRequestURI().getRawQuery() == null ? "" : java.net.URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8), body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), mth = ex.getRequestMethod();
            java.util.function.Predicate<Map<String, Object>> match = row -> { for (String part : q.split("&")) { String[] kv = part.split("=", 2); if (kv.length < 2 || kv[0].equals("select") || kv[0].equals("order") || kv[0].equals("limit")) continue;
                if (kv[1].equals("is.null") ? row.get(kv[0]) != null : kv[1].startsWith("eq.") && !String.valueOf(row.get(kv[0])).replaceAll("\\.0$", "").equals(kv[1].substring(3))) return false; } return true; };
            synchronized (pushRows) {
                if (mth.equals("POST")) { List<Object> out = new ArrayList<>(); for (Object o : l(Json.parse(body))) { Map<String, Object> r = new LinkedHashMap<>(m(o)); r.put("id", (double) pushIds.incrementAndGet()); r.put("fired_at", null); pushRows.add(r); out.add(r); } reply(ex, 201, "application/json", Json.write(out)); return; }
                if (mth.equals("PATCH")) { for (Map<String, Object> r : pushRows) if (match.test(r)) r.putAll(m(Json.parse(body))); reply(ex, 200, "application/json", "[]"); return; }
                if (mth.equals("DELETE")) { pushRows.removeIf(match); reply(ex, 200, "application/json", "[]"); return; }
                List<Object> out = new ArrayList<>(); for (Map<String, Object> r : pushRows) if (match.test(r)) out.add(r); reply(ex, 200, "application/json", Json.write(out)); }
        });
        fake.createContext("/push/", ex -> { pushed.add(new String[]{ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst("Content-Encoding"), Base64.getEncoder().encodeToString(ex.getRequestBody().readAllBytes())}); reply(ex, 201, "text/plain", ""); });
        fake.createContext("/sb/rest/v1/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), table = ex.getRequestURI().getPath().substring("/sb/rest/v1/".length());
            upstreamCalls.add("supabase " + table + " key=" + ex.getRequestHeaders().getFirst("apikey") + " " + body);
            if (table.equals("waitlist")) { String email = String.valueOf(m(l(Json.parse(body)).get(0)).get("email")); reply(ex, 201, "application/json", joined.add(email) ? body : "[]"); }
            else reply(ex, 201, "application/json", body);
        });
        // Yahoo Finance (global markets): cookie, crumb that must be refreshed once, batch quotes, charts, screener, search
        java.util.concurrent.atomic.AtomicInteger crumbs = new java.util.concurrent.atomic.AtomicInteger();
        fake.createContext("/yc", ex -> { ex.getResponseHeaders().add("Set-Cookie", "A3=yt; Path=/"); reply(ex, 404, "text/plain", "no"); });
        java.util.concurrent.atomic.AtomicBoolean crumbBlocked = new java.util.concurrent.atomic.AtomicBoolean();
        fake.createContext("/yh/v1/test/getcrumb", ex -> { if (crumbBlocked.get()) reply(ex, 429, "text/plain", "Too Many Requests"); else reply(ex, 200, "text/plain", "crumb" + crumbs.incrementAndGet()); });
        fake.createContext("/yp", ex -> reply(ex, 200, "text/html", "<html>no crumb here</html>"));
        fake.createContext("/yh/v8/finance/spark", ex -> { String q = java.net.URLDecoder.decode(ex.getRequestURI().getRawQuery(), StandardCharsets.UTF_8); upstreamCalls.add("yahoo spark " + q);
            Map<String, Object> o = new LinkedHashMap<>();
            for (String sy : q.replaceAll(".*symbols=([^&]*).*", "$1").split(",")) o.put(sy, Json.obj("symbol", sy, "fulldayPrice", 3105.0, "fulldayChange", 21.0, "fulldayChangePercent", 0.681, "chartPreviousClose", 3084.0, "timestamp", List.of(1792713600), "close", List.of(3105.0)));
            reply(ex, 200, "application/json", Json.write(o)); });
        fake.createContext("/td/stocks", ex -> { upstreamCalls.add("twelvedata stocks " + ex.getRequestURI().getRawQuery());
            reply(ex, 200, "application/json", "{\"data\":[{\"symbol\":\"7203\",\"name\":\"Toyota Motor Corporation\",\"currency\":\"JPY\",\"mic_code\":\"XJPX\",\"type\":\"Common Stock\"},{\"symbol\":\"1301\",\"name\":\"Kyokuyo Co., Ltd.\",\"currency\":\"JPY\",\"mic_code\":\"XJPX\",\"type\":\"Common Stock\"},{\"symbol\":\"9999\",\"name\":\"Some Pref\",\"currency\":\"JPY\",\"mic_code\":\"XJPX\",\"type\":\"Preferred Stock\"},{\"symbol\":\"700\",\"name\":\"Tencent\",\"currency\":\"HKD\",\"mic_code\":\"XHKG\",\"type\":\"Common Stock\"}]}"); });
        fake.createContext("/yh/v7/finance/quote", ex -> { String q = ex.getRequestURI().getRawQuery(); upstreamCalls.add("yahoo quote " + q);
            if (q.contains("crumb=crumb1")) { reply(ex, 401, "application/json", "{}"); return; }
            List<Object> res = new ArrayList<>();
            for (String sy : java.net.URLDecoder.decode(q.replaceAll(".*symbols=([^&]*).*", "$1"), StandardCharsets.UTF_8).split(","))
                if (!sy.equals("NOPE.L")) res.add(Json.obj("symbol", sy, "longName", "Name " + sy, "regularMarketPrice", sy.equals("^GSPC") ? 7623.68 : 100.5, "regularMarketChange", -39.75, "regularMarketChangePercent", -0.519, "currency", sy.endsWith(".NS") ? "INR" : "USD", "fullExchangeName", "X", "quoteType", sy.startsWith("^") ? "INDEX" : "EQUITY", "regularMarketTime", 1792787901));
            reply(ex, 200, "application/json", Json.write(Json.obj("quoteResponse", Json.obj("result", res)))); });
        fake.createContext("/yh/v8/finance/chart/", ex -> { String q = ex.getRequestURI().getRawQuery(); upstreamCalls.add("yahoo chart " + ex.getRequestURI().getPath() + "?" + q);
            boolean intra = q.contains("interval=60m");
            long t0 = 1792731600L; // 2026-10-23 03:30 UTC = 09:00 IST
            List<Object> ts = new ArrayList<>(), c = new ArrayList<>();
            for (int i = 0; i < 6; i++) { ts.add(intra ? t0 + i * 3600L : t0 - (5 - i) * 86400L); c.add(1000.0 + i); }
            reply(ex, 200, "application/json", Json.write(Json.obj("chart", Json.obj("result", List.of(Json.obj("meta", Json.obj("currency", "INR", "gmtoffset", 19800, "exchangeTimezoneName", "Asia/Kolkata", "fullExchangeName", "NSE"), "timestamp", ts,
                "indicators", Json.obj("quote", List.of(Json.obj("open", c, "high", c, "low", c, "close", c, "volume", c))))))))); });
        fake.createContext("/yh/v1/finance/screener", ex -> { String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8); upstreamCalls.add("yahoo screener " + body);
            reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"total\":209,\"quotes\":[{\"symbol\":\"INFY.NS\",\"longName\":\"Infosys Limited\",\"regularMarketPrice\":1153.8,\"regularMarketChangePercent\":0.4,\"currency\":\"INR\",\"quoteType\":\"EQUITY\"}]}]}}"); });
        fake.createContext("/yh/v1/finance/search", ex -> { upstreamCalls.add("yahoo search"); reply(ex, 200, "application/json", "{\"quotes\":[{\"symbol\":\"AAPL\",\"quoteType\":\"EQUITY\",\"longname\":\"Apple Inc.\"},{\"symbol\":\"APC.DE\",\"quoteType\":\"EQUITY\",\"longname\":\"Apple Inc.\",\"exchDisp\":\"XETRA\"},{\"symbol\":\"X1\",\"quoteType\":\"OPTION\"}],\"news\":[{\"uuid\":\"abc-1\",\"title\":\"Toyota raises its outlook\",\"publisher\":\"Reuters\",\"link\":\"https://x.test/y1\",\"providerPublishTime\":1792950000,\"type\":\"STORY\",\"relatedTickers\":[\"7203.T\",\"TM\"]},{\"uuid\":\"abc-2\",\"title\":\"A video\",\"link\":\"https://x.test/y2\",\"type\":\"VIDEO\"}]}"); });
        fake.createContext("/yh/v1/finance/screener/predefined/saved", ex -> { upstreamCalls.add("yahoo predefined " + ex.getRequestURI().getRawQuery());
            reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"quotes\":[{\"symbol\":\"RHI\",\"longName\":\"Robert Half Inc.\",\"regularMarketPrice\":45.87,\"regularMarketChangePercent\":21.7,\"currency\":\"USD\"},{\"symbol\":\"BRK-B\",\"longName\":\"Berkshire\",\"regularMarketPrice\":500,\"regularMarketChangePercent\":1.0,\"currency\":\"USD\"}]}]}}"); });
        fake.createContext("/yh/v1/finance/trending/US", ex -> reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"quotes\":[{\"symbol\":\"NVDA\"},{\"symbol\":\"NQ=F\"},{\"symbol\":\"MU\"}]}]}}"));
        fake.createContext("/yh/v1/finance/visualization", ex -> { upstreamCalls.add("yahoo ipo " + new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"documents\":[{\"columns\":[{\"id\":\"ticker\"},{\"id\":\"companyshortname\"},{\"id\":\"exchange_short_name\"},{\"id\":\"startdatetime\"},{\"id\":\"pricefrom\"},{\"id\":\"priceto\"},{\"id\":\"offerprice\"},{\"id\":\"currencyname\"},{\"id\":\"shares\"},{\"id\":\"dealtype\"}],\"rows\":[[\"NEWCO\",\"NewCo Inc\",\"NASDAQ\",\"2026-11-03T00:00:00.000Z\",18,20,null,\"USD\",10000000,\"EXPECTED\"],[\"9999.HK\",\"Asia Co\",\"HKSE\",\"2026-10-01T00:00:00.000Z\",null,null,12.5,\"HKD\",2000000,\"PRICED\"]]}]}]}}"); });
        fake.start();
        String base = "http://127.0.0.1:" + fake.getAddress().getPort();
        System.setProperty("AV_BASE_URL", base + "/av"); System.setProperty("MARKETAUX_BASE_URL", base + "/mx"); System.setProperty("FINNHUB_BASE_URL", base + "/fh"); System.setProperty("TWELVEDATA_BASE_URL", base + "/td"); System.setProperty("GEMINI_BASE_URL", base + "/gm"); System.setProperty("ANTHROPIC_BASE_URL", base + "/an");
        System.setProperty("YAHOO_ENABLED", "0"); System.setProperty("YAHOO_BASE_URL", base + "/yh"); System.setProperty("YAHOO_COOKIE_URL", base + "/yc"); System.setProperty("YAHOO_PAGE_URL", base + "/yp");
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
        for (int k = 0; k < 40; k++) { String s = "S" + (char) ('A' + k / 26) + (char) ('A' + k % 26); Providers.SNAP.put(s, Json.obj("symbol", s, "price", 10.0 + k, "changePct", (double) (k - 20), "change", 0.1)); }
        Providers.SNAP.put("SPY", Json.obj("symbol", "SPY", "price", 759.97, "changePct", 9.9));
        Providers.CACHE.clear();
        r = get(p, "/api/markets");
        Map<String, Object> wm = m(m(r.body).get("movers"));
        t("movers from the warm snapshot: 25 per list, ETFs excluded", r.code == 200 && l(wm.get("gainers")).size() == 19 && l(wm.get("losers")).size() == 20 && l(wm.get("active")).size() == 25 && !r.raw.contains("\"SPY\",\"price\":759.97,\"change\":null,\"changePct\":9.9"));
        t("market cards carry the real index level when available", r.code == 200 && l(m(r.body).get("indices")).stream().anyMatch(x -> "SPY".equals(m(x).get("symbol")) && m(x).get("level") != null) && upstreamCalls.stream().anyMatch(u -> u.startsWith("finnhub %5EGSPC")));
        r = get(p, "/api/snapshot");
        t("snapshot returns every warm quote with its sector", r.code == 200 && d(m(r.body).get("count")) == 1 && "SPY".equals(m(l(m(r.body).get("quotes")).get(0)).get("symbol")) && "ETFs".equals(m(l(m(r.body).get("quotes")).get(0)).get("sector")));
        r = get(p, "/api/directory?q=app");
        t("directory: US major-exchange stocks/ETFs only, searchable", r.code == 200 && d(m(r.body).get("listed")) == 2 && d(m(r.body).get("total")) == 1 && "AAPL".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")));
        r = get(p, "/api/directory?type=etf");
        t("directory: filter by type", r.code == 200 && d(m(r.body).get("total")) == 1 && "SPY".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && d(m(l(m(r.body).get("items")).get(0)).get("price")) == 759.97);
        t("directory: rejects bad search", get(p, "/api/directory?q=%3Cscript%3E").code == 400);
        Providers.SNAP.clear(); Providers.CACHE.clear();
        t("tagger finds companies by name and exchange ticker, not inside other words", Providers.tag("Apple and Nvidia rally; Intelligence report (NYSE: XOM)", "").equals(List.of("XOM", "AAPL", "NVDA")));
        Providers.CACHE.clear();
        r = get(p, "/api/markets");
        Map<String, Object> mvs = m(m(r.body).get("movers"));
        t("movers come from Finnhub quotes of large US companies", r.code == 200 && "NVDA".equals(m(l(mvs.get("gainers")).get(0)).get("symbol")) && "TSLA".equals(m(l(mvs.get("losers")).get(0)).get("symbol")) && "Largest US companies".equals(mvs.get("universe")));
        Providers.CACHE.clear();
        r = get(p, "/api/news?limit=10");
        List<Object> arts = l(m(r.body).get("articles"));
        t("main feed merges Finnhub market stories (tagged ones carry their stocks) and asks for merger news too", r.code == 200 && arts.stream().anyMatch(x -> "fh7001".equals(m(x).get("id")) && l(m(x).get("symbols")).size() == 2) && arts.stream().anyMatch(x -> "fh7002".equals(m(x).get("id"))) && upstreamCalls.stream().filter(u -> u.equals("finnhub news")).count() >= 2);
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
        r = get(p, "/api/intraday?symbol=AAPL&interval=15min");
        t("intraday bars oldest first with a time of day, 20 sessions requested", r.code == 200 && "2026-10-22T09:30:00".equals(m(l(m(r.body).get("bars")).get(0)).get("t")) && d(m(r.body).get("perSession")) == 26 && upstreamCalls.stream().anyMatch(u -> u.contains("interval=15min") && u.contains("outputsize=520")));
        t("intraday rejects unknown interval", get(p, "/api/intraday?symbol=AAPL&interval=2min").code == 400);
        System.clearProperty("TWELVEDATA_API_KEY");
        t("intraday without a Twelve Data key -> 503", get(p, "/api/intraday?symbol=MSFT&interval=5min").code == 503);
        Providers.directoryAll();
        int fhSearches = (int) upstreamCalls.stream().filter(u -> u.contains("search")).count();
        r = get(p, "/api/search?q=apple");
        t("search answers from the in-memory directory, no provider call", r.code == 200 && "AAPL".equals(m(l(m(r.body).get("results")).get(0)).get("symbol")) && "directory".equals(m(r.body).get("source")) && upstreamCalls.stream().filter(u -> u.contains("search")).count() == fhSearches);
        Providers.SNAP.put("WARM", Json.obj("symbol", "WARM", "price", 42.0, "changePct", 1.0));
        Providers.FH_BLOCKED_UNTIL = System.currentTimeMillis() + 10_000;
        r = get(p, "/api/quote?symbol=WARM");
        t("while Finnhub is backing off, quotes come from the warm snapshot", r.code == 200 && d(m(r.body).get("price")) == 42.0);
        get(p, "/api/quote?symbol=COLD");
        t("while Finnhub is backing off, no further Finnhub calls are made", upstreamCalls.stream().noneMatch(u -> u.startsWith("finnhub COLD")));
        Providers.FH_BLOCKED_UNTIL = 0; Providers.SNAP.remove("WARM");
        r = call(p, "POST", "/api/explain", "{\"lens\":\"five_w\",\"title\":\"Apple rises\",\"summary\":\"Shares rose.\",\"symbols\":[\"AAPL\"]}", ip());
        t("explain: lens answer grounded with today's move", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple rose") && upstreamCalls.contains("explain with-moves"));
        int ne = upstreamCalls.size(); call(p, "POST", "/api/explain", "{\"lens\":\"five_w\",\"title\":\"Apple rises\",\"summary\":\"Shares rose.\",\"symbols\":[\"AAPL\"]}", ip());
        t("explain: repeat answer served from cache", upstreamCalls.size() == ne);
        r = call(p, "POST", "/api/explain", "{\"lens\":\"tldr\",\"title\":\"AUTHFAIL\"}", ip());
        t("AI invalid key -> clear 503 message", r.code == 503 && r.raw.contains("key is invalid"));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"tldr\",\"title\":\"NOCREDIT\"}", ip());
        t("AI out of credits -> clear 503 message", r.code == 503 && r.raw.contains("out of credits"));
        System.setProperty("GEMINI_API_KEY", "gm-test");
        r = get(p, "/api/health");
        t("health: Gemini chosen when its key is set", "gemini".equals(m(r.body).get("aiProvider")));
        r = call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about Apple\"}]}", ip());
        t("Gemini chat runs the quote tool and answers, key server-side", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple is $226") && l(m(r.body).get("used")).contains("Quote AAPL") && !r.raw.contains("gm-test")
                && upstreamCalls.stream().anyMatch(u -> u.startsWith("gemini /gm/v1beta/models/gemini-flash-latest:generateContent key=gm-test schema-ok")));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"eli5\",\"title\":\"Chips rally\",\"summary\":\"Shares rose.\",\"symbols\":[\"NVDA\"]}", ip());
        t("Gemini explains a story", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("chips rose"));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"eli5\",\"title\":\"BADKEY\"}", ip());
        t("Gemini invalid key -> clear 503 message", r.code == 503 && r.raw.contains("GEMINI_API_KEY"));
        System.setProperty("AI_PROVIDER", "anthropic");
        t("AI_PROVIDER=anthropic switches back to Claude", "anthropic".equals(m(get(p, "/api/health").body).get("aiProvider")));
        System.clearProperty("AI_PROVIDER"); System.clearProperty("GEMINI_API_KEY");
        t("explain: unknown lens -> 400", call(p, "POST", "/api/explain", "{\"lens\":\"hack\",\"title\":\"x\"}", ip()).code == 400);
        System.clearProperty("FINNHUB_API_KEY"); Providers.CACHE.clear();
        t("news bad search text -> 400", get(p, "/api/news?q=%3Cscript%3E").code == 400);
        r = get(p, "/api/markets");
        t("markets combines movers, index ETFs and crypto", r.code == 200 && d(m(l(m(m(r.body).get("movers")).get("gainers")).get(0)).get("changePct")) == 25 && d(m(l(m(m(r.body).get("movers")).get("losers")).get(0)).get("changePct")) == -33.3 && l(m(r.body).get("indices")).size() == 4 && d(m(l(m(r.body).get("crypto")).get(0)).get("rate")) == 65000.5);
        r = get(p, "/api/quotes?symbols=AAPL,ZZZ");
        t("quotes batch returns found + lists missing", r.code == 200 && l(m(r.body).get("quotes")).size() == 1 && "ZZZ".equals(l(m(r.body).get("missing")).get(0)));
        t("quotes rejects more than 25 symbols", get(p, "/api/quotes?symbols=A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z").code == 400);
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
        // ---- global markets (Yahoo)
        t("global data off -> /api/world 503", get(p, "/api/world").code == 503);
        System.setProperty("YAHOO_ENABLED", "1"); Providers.CACHE.clear();
        r = get(p, "/api/quote?symbol=%5EGSPC");
        t("index level from Yahoo, stale crumb refreshed once", r.code == 200 && d(m(r.body).get("price")) == 7623.68 && upstreamCalls.stream().anyMatch(u -> u.startsWith("yahoo quote") && u.contains("crumb=crumb2")));
        r = get(p, "/api/world");
        t("world: regions, countries and index levels in one quote call", r.code == 200 && l(m(r.body).get("regions")).size() == 4 && Json.write(r.body).contains("\"Nifty 50\"") && d(m(l(m(l(m(l(m(r.body).get("regions")).get(0)).get("countries")).get(0)).get("indexes")).get(0)).get("price")) == 7623.68
            && upstreamCalls.stream().filter(u -> u.startsWith("yahoo quote") && u.contains("NSEI")).count() == 1);
        r = get(p, "/api/world/country?c=in&kind=stocks&sector=Technology");
        t("country stocks: screener by region, main exchange and sector", r.code == 200 && "INFY.NS".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && d(m(r.body).get("total")) == 209
            && upstreamCalls.stream().anyMatch(u -> u.startsWith("yahoo screener") && u.contains("[\"region\",\"in\"]") && u.contains("[\"exchange\",\"NSI\"]") && u.contains("[\"sector\",\"Technology\"]")));
        t("country request validated", get(p, "/api/world/country?c=in&sector=Hacking").code == 400 && get(p, "/api/world/country?c=zz").code == 404);
        r = get(p, "/api/world/country?c=gb&kind=indexes");
        t("country indexes carry names and levels", r.code == 200 && "FTSE 100".equals(m(l(m(r.body).get("items")).get(0)).get("name")) && d(m(l(m(r.body).get("items")).get(0)).get("price")) == 100.5);
        r = get(p, "/api/history?symbol=RELIANCE.NS");
        t("non-US history from Yahoo in exchange-local dates", r.code == 200 && "2026-10-23".equals(m(l(m(r.body).get("bars")).get(5)).get("t")) && "INR".equals(m(r.body).get("currency")) && upstreamCalls.stream().noneMatch(u -> u.contains("twelvedata") && u.contains("RELIANCE")));
        r = get(p, "/api/intraday?symbol=RELIANCE.NS&interval=4h");
        t("4h bars built from hourly bars within a day", r.code == 200 && l(m(r.body).get("bars")).size() == 2 && "2026-10-23T10:30:00".equals(m(l(m(r.body).get("bars")).get(0)).get("t")) && d(m(l(m(r.body).get("bars")).get(0)).get("c")) == 1003.0);
        r = get(p, "/api/quotes?symbols=RELIANCE.NS,NOPE.L");
        t("mixed quotes: global batch, unknown listed as missing", r.code == 200 && l(m(r.body).get("quotes")).size() == 1 && l(m(r.body).get("missing")).contains("NOPE.L"));
        r = get(p, "/api/search?q=apple");
        t("search adds other countries' listings after US matches", r.code == 200 && !Yahoo.isGlobal(String.valueOf(m(l(m(r.body).get("results")).get(0)).get("symbol"))) && Json.write(r.body).contains("APC.DE") && !Json.write(r.body).contains("X1"));
        t("Yahoo suffix rule keeps US class shares local", Yahoo.isGlobal("7203.T") && Yahoo.isGlobal("^N225") && !Yahoo.isGlobal("BRK.B") && !Yahoo.isGlobal("AAPL"));
        // ---- Yahoo refuses the crumb (as it does on some server networks): crumb-free prices and Twelve Data listings
        crumbBlocked.set(true); Yahoo.CRUMB = null; Yahoo.CRUMB_FAIL_UNTIL = 0; Providers.CACHE.clear(); System.setProperty("TWELVEDATA_API_KEY", "td-test");
        r = get(p, "/api/world");
        t("crumb refused: world index levels still served (spark), reason logged", r.code == 200 && Json.write(r.body).contains("3105") && String.valueOf(Yahoo.LAST_ERR).contains("q1=429") && upstreamCalls.stream().anyMatch(u -> u.startsWith("yahoo spark")));
        r = get(p, "/api/world/country?c=jp&kind=stocks");
        t("crumb refused: country list from Twelve Data with Yahoo symbols and prices, A to Z", r.code == 200 && "basic".equals(m(r.body).get("mode")) && d(m(r.body).get("total")) == 3
            && "1301.T".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && d(m(l(m(r.body).get("items")).get(1)).get("price")) == 3105.0 && Json.write(r.body).contains("0700.HK") && !Json.write(r.body).contains("9999"));
        int crumbTries = (int) upstreamCalls.stream().filter(u -> u.startsWith("yahoo quote")).count();
        get(p, "/api/quotes?symbols=SHEL.L,7203.T");
        t("crumb refused: no new crumb attempts for 15 minutes", upstreamCalls.stream().filter(u -> u.startsWith("yahoo quote")).count() == crumbTries);
        crumbBlocked.set(false); Yahoo.CRUMB_FAIL_UNTIL = 0; System.clearProperty("TWELVEDATA_API_KEY");
        r = get(p, "/api/news?symbols=7203.T&limit=30");
        t("news for a non-US company comes from Yahoo, videos skipped", r.code == 200 && Json.write(r.body).contains("Toyota raises its outlook") && Json.write(r.body).contains("yh-abc-1") && !Json.write(r.body).contains("A video"));
        r = get(p, "/api/calendar?type=ipo");
        t("IPO calendar from Yahoo: worldwide, with status and deal size", r.code == 200 && Json.write(r.body).contains("NewCo Inc") && Json.write(r.body).contains("\"status\":\"PRICED\"") && Json.write(r.body).contains("\"dealSize\":25000000"));
        r = get(p, "/api/popular?list=gainers");
        t("whole-market gainers from Yahoo, class shares mapped back (BRK.B)", r.code == 200 && "RHI".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && Json.write(r.body).contains("\"BRK.B\"") && upstreamCalls.stream().anyMatch(u -> u.contains("scrIds=day_gainers")));
        r = get(p, "/api/popular?list=trending");
        t("trending skips futures, keeps order", r.code == 200 && "NVDA".equals(m(l(m(r.body).get("items")).get(0)).get("symbol")) && !Json.write(r.body).contains("NQ=F"));
        r = get(p, "/api/popular?list=popular");
        t("popular: 300+ well-known names with sectors, one batch per 50", r.code == 200 && d(m(r.body).get("count")) > 300 && Json.write(r.body).contains("\"sector\":\"Semiconductors\""));
        t("popular list validated", get(p, "/api/popular?list=memes").code == 400);
        System.setProperty("YAHOO_ENABLED", "0");
        // ---- economy dashboard (FRED)
        System.setProperty("FRED_BASE_URL", base + "/fred");
        r = get(p, "/api/macro");
        Map<String, Object> cpi = l(m(r.body).get("items")).stream().map(x -> m(x)).filter(x -> "CPIAUCSL".equals(x.get("id"))).findFirst().orElse(Map.of());
        t("macro: 16 series, CPI shown year over year from FRED's CSV, missing values skipped", r.code == 200 && l(m(r.body).get("items")).size() == 16 && Math.abs(d(cpi.get("value")) - (180.0 / 168 - 1) * 100) < 0.01 && "2026-09-01".equals(cpi.get("date")) && l(cpi.get("points")).size() == 36);
        Map<String, Object> jobs = l(m(r.body).get("items")).stream().map(x -> m(x)).filter(x -> "PAYEMS".equals(x.get("id"))).findFirst().orElse(Map.of());
        t("macro: jobs added = monthly change", d(jobs.get("value")) == 1.0);
        System.setProperty("FRED_API_KEY", "fred-test"); System.setProperty("FRED_API_URL", base + "/fredapi"); Providers.CACHE.clear();
        r = get(p, "/api/macro");
        t("macro: official FRED API used when a key is set (key sent server-side only)", r.code == 200 && l(m(r.body).get("items")).size() == 16 && !r.raw.contains("fred-test") && upstreamCalls.stream().anyMatch(u -> u.startsWith("fredapi") && u.contains("api_key=fred-test")));
        System.clearProperty("FRED_API_KEY");
        // ---- SEC filings and financial statements
        System.setProperty("SEC_BASE_URL", base + "/sec"); System.setProperty("SEC_DATA_URL", base + "/secdata"); System.setProperty("SEC_USER_AGENT", "Quantera Test test@example.com");
        r = get(p, "/api/filings?symbol=AAPL");
        t("filings: plain-English labels, links, insider count, unknown forms skipped, SEC user agent sent", r.code == 200 && "Insider buy or sell".equals(m(l(m(r.body).get("filings")).get(0)).get("label")) && d(m(r.body).get("insiderFilings90d")) == 1
            && Json.write(r.body).contains("https://www.sec.gov/Archives/edgar/data/320193/000032019325000079/aapl-20250927.htm") && !Json.write(r.body).contains("\"SD\"") && upstreamCalls.stream().anyMatch(u -> u.equals("sec tickers ua=Quantera Test test@example.com")));
        r = get(p, "/api/financials?symbol=AAPL");
        Map<String, Object> fy = m(l(m(r.body).get("annual")).get(1));
        t("financials: yearly revenue, profit, EPS and free cash flow from XBRL frames; latest balance sheet", r.code == 200 && "2024".equals(fy.get("year")) && d(fy.get("revenue")) == 391e9 && d(fy.get("eps")) == 6.08 && d(fy.get("freeCashFlow")) == 118e9 - 9.4e9
            && d(m(m(r.body).get("balance")).get("assets")) == 344e9 && l(m(r.body).get("quarterly")).size() == 1);
        t("financials: per-share figures use the latest restated filing (e.g. after a stock split)", d(m(l(m(r.body).get("annual")).get(0)).get("eps")) == 6.2);
        List<Object> sp = new ArrayList<>(List.of(Json.obj("eps", 3.85, "netIncome", 9.75e9), Json.obj("eps", 0.17, "netIncome", 4.37e9), Json.obj("eps", 1.19, "netIncome", 29.8e9), Json.obj("eps", -0.5, "netIncome", -1e9)));
        Sec.splitAdjust(sp);
        t("financials: EPS before an unrestated 10-for-1 split is adjusted; later years untouched", d(m(sp.get(0)).get("eps")) == 0.39 && d(m(sp.get(1)).get("eps")) == 0.17 && d(m(sp.get(2)).get("eps")) == 1.19);
        t("filings: companies that don't file with the SEC -> 404", get(p, "/api/filings?symbol=7203.T").code == 404);
        // ---- background price alerts (Web Push)
        java.security.KeyPairGenerator kg = java.security.KeyPairGenerator.getInstance("EC"); kg.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
        java.security.KeyPair vk = kg.generateKeyPair(), ua = kg.generateKeyPair();
        System.setProperty("VAPID_PUBLIC_KEY", Push.b64e(Push.raw((java.security.interfaces.ECPublicKey) vk.getPublic())));
        System.setProperty("VAPID_PRIVATE_KEY", Push.b64e(Push.fixed(((java.security.interfaces.ECPrivateKey) vk.getPrivate()).getS())));
        System.setProperty("PUSH_ALLOW_LOCAL", "1"); System.setProperty("SUPABASE_URL", base + "/sb/"); System.setProperty("SUPABASE_SERVICE_KEY", "sb-test");
        byte[] authSecret = new byte[16]; new java.security.SecureRandom().nextBytes(authSecret);
        String subJ = Json.write(Json.obj("endpoint", base + "/push/device-123456", "keys", Json.obj("p256dh", Push.b64e(Push.raw((java.security.interfaces.ECPublicKey) ua.getPublic())), "auth", Push.b64e(authSecret))));
        t("push: public key served", get(p, "/api/push/key").code == 200);
        r = call(p, "POST", "/api/push/alerts", "{\"subscription\":" + subJ + ",\"symbol\":\"aapl\",\"op\":\">\",\"price\":1}", ip());
        t("push: alert saved for this device", r.code == 200 && pushRows.size() == 1 && "AAPL".equals(pushRows.get(0).get("symbol")));
        t("push: bad subscription rejected", call(p, "POST", "/api/push/alerts", "{\"subscription\":{\"endpoint\":\"javascript:x\"},\"symbol\":\"AAPL\",\"op\":\">\",\"price\":1}", ip()).code == 400);
        Push.check();
        boolean decrypted = false, signed = false;
        if (!pushed.isEmpty()) {
            byte[] bodyB = Base64.getDecoder().decode(pushed.get(0)[2]); byte[] salt = Arrays.copyOfRange(bodyB, 0, 16), asPub = Arrays.copyOfRange(bodyB, 21, 86), ct = Arrays.copyOfRange(bodyB, 86, bodyB.length);
            javax.crypto.KeyAgreement ka = javax.crypto.KeyAgreement.getInstance("ECDH"); ka.init(ua.getPrivate()); ka.doPhase(Push.pub(asPub), true);
            byte[] uaRaw = Push.raw((java.security.interfaces.ECPublicKey) ua.getPublic()), prkKey = Push.hmac(authSecret, ka.generateSecret());
            byte[] ikm = Push.expand(prkKey, Push.cat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaRaw, asPub), 32), prk = Push.hmac(salt, ikm);
            javax.crypto.Cipher c = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            c.init(javax.crypto.Cipher.DECRYPT_MODE, new javax.crypto.spec.SecretKeySpec(Push.expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16), "AES"), new javax.crypto.spec.GCMParameterSpec(128, Push.expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12)));
            byte[] plain = c.doFinal(ct); String msg = new String(plain, 0, plain.length - 1, StandardCharsets.UTF_8);
            decrypted = plain[plain.length - 1] == 2 && msg.contains("AAPL is above") && "aes128gcm".equals(pushed.get(0)[1]);
            String jwt = pushed.get(0)[0].replaceAll("^vapid t=([^,]+),.*$", "$1"); String[] parts = jwt.split("\\.");
            java.security.Signature sg = java.security.Signature.getInstance("SHA256withECDSAinP1363Format"); sg.initVerify(vk.getPublic()); sg.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            signed = sg.verify(Push.b64d(parts[2])) && new String(Push.b64d(parts[1]), StandardCharsets.UTF_8).contains("\"aud\":\"" + base + "\"") && pushed.get(0)[0].endsWith("k=" + System.getProperty("VAPID_PUBLIC_KEY"));
        }
        t("push: triggered alert sent encrypted (RFC 8291) and decrypts on the device", decrypted);
        t("push: VAPID token signed with the server key for the push service's origin (RFC 8292)", signed);
        Push.check();
        t("push: fired alert marked and not sent twice", pushRows.get(0).get("fired_at") != null && pushed.size() == 1);
        r = call(p, "POST", "/api/push/delete", "{\"subscription\":" + subJ + ",\"id\":1}", ip());
        t("push: owner can delete the alert", r.code == 200 && pushRows.isEmpty());
        System.clearProperty("VAPID_PUBLIC_KEY"); System.clearProperty("VAPID_PRIVATE_KEY");
        System.out.println("\n" + pass + " passed, " + fail + " failed");
        app.stop(0); fake.stop(0);
        System.exit(fail == 0 ? 0 : 1);
    }
}
