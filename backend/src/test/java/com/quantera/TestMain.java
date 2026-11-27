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
    static R adminGet(int port, String path, String token) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("X-Forwarded-For", ip()).GET();
        if (token != null) b.header("X-Admin-Token", token);
        HttpResponse<String> r = C.send(b.build(), HttpResponse.BodyHandlers.ofString());
        Object j = null; try { j = Json.parse(r.body()); } catch (Exception ignored) {}
        return new R(r.statusCode(), j, r.body());
    }
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

    static String http(int port, String path) throws Exception { return java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:" + port + path)).GET().build(), java.net.http.HttpResponse.BodyHandlers.ofString()).body(); }

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
                case "TIME_SERIES_DAILY" -> { if (sym(ex).equals("TECH")) { StringBuilder tb = new StringBuilder("{\"Time Series (Daily)\":{"); java.time.LocalDate d0 = java.time.LocalDate.of(2026, 11, 11);
                    for (int i = 0; i < 300; i++) { double c = 400 - i + (i % 3) * 0.5; tb.append(i == 0 ? "" : ",").append("\"").append(d0.minusDays(i)).append("\":{\"1. open\":\"").append(c).append("\",\"2. high\":\"").append(c + 1).append("\",\"3. low\":\"").append(c - 1).append("\",\"4. close\":\"").append(c).append("\",\"5. volume\":\"100\"}"); }
                    reply(ex, 200, J, tb.append("}}").toString()); } else reply(ex, 200, J, "{\"Time Series (Daily)\":{\"2026-10-02\":{\"1. open\":\"10\",\"2. high\":\"12\",\"3. low\":\"9\",\"4. close\":\"11\",\"5. volume\":\"100\"},\"2026-10-01\":{\"1. open\":\"9\",\"2. high\":\"10\",\"3. low\":\"8\",\"4. close\":\"10\",\"5. volume\":\"90\"}}}"); }
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
            reply(ex, 200, "application/json", sy.equals("ZZZ") ? "{\"c\":0,\"d\":null,\"dp\":null,\"pc\":0,\"t\":0}" : sy.startsWith("IPO") ? "{\"c\":97.17,\"d\":null,\"dp\":null,\"pc\":0,\"t\":1792800000}" : "{\"c\":226.6,\"d\":0.56,\"dp\":" + (sy.equals("TSLA") ? "-3.1" : sy.equals("NVDA") ? "2.4" : "0.25") + ",\"h\":227,\"l\":224,\"o\":225,\"pc\":226.04,\"t\":1792800000}");
        });
        fake.createContext("/fh/stock/symbol", ex -> { String mic = ex.getRequestURI().getRawQuery().replaceAll(".*mic=([A-Z]+).*", "$1"); List<Object> rows = l(Json.parse("[{\"symbol\":\"AAPL\",\"description\":\"APPLE INC\",\"type\":\"Common Stock\",\"mic\":\"XNAS\"},{\"symbol\":\"SPY\",\"description\":\"SPDR S&P 500 ETF\",\"type\":\"ETP\",\"mic\":\"ARCX\"},{\"symbol\":\"ABCD\",\"description\":\"OTC CO\",\"type\":\"Common Stock\",\"mic\":\"OOTC\"},{\"symbol\":\"XYZW\",\"description\":\"WARRANT\",\"type\":\"Warrant\",\"mic\":\"XNAS\"}]")); rows.removeIf(o -> !mic.equals(m(o).get("mic"))); reply(ex, 200, "application/json", Json.write(rows)); });
        fake.createContext("/fh/stock/peers", ex -> { String q = ex.getRequestURI().getRawQuery(); reply(ex, 200, "application/json", q.contains("symbol=FNDX") ? "[\"FNDX\",\"FNDY\",\"7203.T\",\"FNDY\"]" : "[]"); });
        fake.createContext("/fh/stock/profile2", ex -> reply(ex, 200, "application/json", "{\"name\":\"Apple Inc\",\"country\":\"US\",\"currency\":\"USD\",\"exchange\":\"NASDAQ\",\"finnhubIndustry\":\"Technology\",\"marketCapitalization\":3400000,\"logo\":\"https://x.test/l.png\",\"weburl\":\"https://apple.com\"}"));
        fake.createContext("/fh/stock/metric", ex -> reply(ex, 200, "application/json", "{\"metric\":{\"peTTM\":33.1,\"beta\":1.2,\"52WeekHigh\":350,\"52WeekLow\":200,\"dividendYieldIndicatedAnnual\":0.45,\"revenueGrowthTTMYoy\":6.1,\"netProfitMarginTTM\":24,\"roeTTM\":150}}"));
        fake.createContext("/fh/search", ex -> { upstreamCalls.add("finnhub search"); reply(ex, 200, "application/json", "{\"result\":[{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP\",\"type\":\"Common Stock\"},{\"description\":\"SHOPIFY INC\",\"symbol\":\"SHOP.TO\",\"type\":\"Common Stock\"}]}"); });
        fake.createContext("/fh/calendar/earnings", ex -> reply(ex, 200, "application/json", "{\"earningsCalendar\":[{\"date\":\"2026-10-30\",\"epsEstimate\":1.5,\"symbol\":\"AAPL\"},{\"date\":\"2026-10-28\",\"epsEstimate\":null,\"symbol\":\"MSFT\"},{\"date\":\"2026-10-29\",\"symbol\":\"RELI.NS\"}]}"));
        fake.createContext("/fh/calendar/ipo", ex -> reply(ex, 200, "application/json", "{\"ipoCalendar\":[{\"date\":\"2026-11-01\",\"exchange\":\"NASDAQ\",\"name\":\"NewCo\",\"price\":\"10.00-12.00\",\"symbol\":\"NEW\"}]}"));
        fake.createContext("/td/time_series", ex -> { upstreamCalls.add("twelvedata " + ex.getRequestURI().getRawQuery());
            reply(ex, 200, "application/json", ex.getRequestURI().getRawQuery().contains("symbol=ZZZ") ? "{\"code\":400,\"message\":\"symbol not found\",\"status\":\"error\"}" : "{\"values\":[{\"datetime\":\"2026-10-23\",\"open\":\"11\",\"high\":\"12\",\"low\":\"10\",\"close\":\"11.5\",\"volume\":\"100\"},{\"datetime\":\"2026-10-22\",\"open\":\"10\",\"high\":\"11\",\"low\":\"9\",\"close\":\"10.5\",\"volume\":\"90\"}],\"status\":\"ok\"}"); });
        fake.createContext("/gm/v1beta/models/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            upstreamCalls.add("gemini " + ex.getRequestURI().getPath() + " key=" + ex.getRequestHeaders().getFirst("x-goog-api-key") + (body.contains("\"OBJECT\"") ? " schema-ok" : "") + (body.contains("Never tell the user to buy, sell or hold") ? " no-advice" : "") + (body.contains("functionDeclarations") ? " chat" : ""));
            if (body.contains("DENIEDKEY")) { reply(ex, 403, "application/json", "{\"error\":{\"code\":403,\"message\":\"Permission denied on the service.\",\"status\":\"PERMISSION_DENIED\"}}"); return; }
            if (body.contains("BADKEY")) { reply(ex, 400, "application/json", "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\",\"status\":\"INVALID_ARGUMENT\"}}"); return; }
            if (body.contains("DATA:")) { String rep = "Here you go: {\"headline\":\"Steady grower\",\"summary\":\"Apple earns a lot (SEC 10-K, FY2025).\",\"sections\":[{\"title\":\"Business\",\"points\":[\"Sells phones.\"]},{\"title\":\"Empty\",\"points\":[]}],\"bull\":[\"Brand\"],\"bear\":[\"Price\"],\"risks\":[\"Rules\"],\"catalysts\":[\"Earnings\"],\"verdict\":{\"rating\":\"Hold\",\"confidence\":\"Sure\",\"reasons\":[\"Fair price\"],\"changeMind\":\"A cheaper price\"}}";
                upstreamCalls.add("gemini research " + (body.contains("financialStatements") || body.contains("quote") ? "with-data" : "no-data"));
                reply(ex, 200, "application/json", "{\"candidates\":[{\"content\":{\"role\":\"model\",\"parts\":[{\"text\":" + Json.write(rep) + "}]}}]}"); return; }
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
            upstreamCalls.add("anthropic key=" + ex.getRequestHeaders().getFirst("x-api-key") + " hasTools=" + body.contains("get_quote") + (body.contains("Never tell the user to buy, sell or hold") ? " no-advice" : ""));
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
        fake.createContext("/fredapi/fred/release/dates", ex -> { String q = ex.getRequestURI().getRawQuery(); java.time.LocalDate td = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
            // Shaped like the real API: ids and dates only, and ascending order starts decades back.
            String body = !q.contains("sort_order=desc") ? "{\"release_dates\":[{\"release_id\":10,\"date\":\"1997-02-10\"}]}"
                : q.contains("release_id=10&") ? "{\"release_dates\":[{\"release_id\":10,\"date\":\"" + td.plusDays(9) + "\"},{\"release_id\":10,\"date\":\"" + td.minusDays(3) + "\"}]}"
                : q.contains("release_id=9&") ? "{\"release_dates\":[{\"release_id\":9,\"date\":\"" + td.plusDays(2) + "\"}]}" : "{\"release_dates\":[]}";
            reply(ex, 200, "application/json", body); });
        fake.createContext("/fredapi/fred/release", ex -> { String q = ex.getRequestURI().getRawQuery();
            String name = q.contains("release_id=10&") ? "Consumer Price Index" : q.contains("release_id=9&") ? "Renamed Release" : "Other";
            reply(ex, 200, "application/json", "{\"releases\":[{\"id\":1,\"name\":\"" + name + "\"}]}"); });
        fake.createContext("/fredapi/fred/series/observations", ex -> { upstreamCalls.add("fredapi " + ex.getRequestURI().getRawQuery()); StringBuilder b = new StringBuilder("{\"observations\":["); java.time.LocalDate d0 = java.time.LocalDate.of(2020, 1, 1);
            for (int i = 0; i < 81; i++) b.append(i == 0 ? "" : ",").append("{\"date\":\"").append(d0.plusMonths(i)).append("\",\"value\":\"").append(i == 40 ? "." : String.valueOf(100 + i)).append("\"}");
            reply(ex, 200, "application/json", b.append("]}").toString()); });
                fake.createContext("/sec/files/company_tickers.json", ex -> { upstreamCalls.add("sec tickers ua=" + ex.getRequestHeaders().getFirst("User-Agent")); reply(ex, 200, "application/json", "{\"0\":{\"cik_str\":320193,\"ticker\":\"AAPL\",\"title\":\"Apple Inc.\"},\"1\":{\"cik_str\":1067983,\"ticker\":\"BRK-B\",\"title\":\"Berkshire\"}}"); });
        fake.createContext("/secdata/submissions/", ex -> { String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
            reply(ex, 200, "application/json", "{\"name\":\"Apple Inc.\",\"sicDescription\":\"Electronic Computers\",\"fiscalYearEnd\":\"0927\",\"filings\":{\"recent\":{\"form\":[\"4\",\"8-K\",\"SD\",\"10-K\"],\"filingDate\":[\"" + today + "\",\"" + today + "\",\"2026-05-01\",\"2025-10-31\"],\"accessionNumber\":[\"0001-26-000001\",\"0001-26-000002\",\"0001-26-000003\",\"0000320193-25-000079\"],\"primaryDocument\":[\"x.xml\",\"e.htm\",\"sd.htm\",\"aapl-20250927.htm\"],\"primaryDocDescription\":[\"\",\"8-K\",\"SD\",\"10-K\"],\"reportDate\":[\"\",\"\",\"\",\"2025-09-27\"],\"items\":[\"\",\"2.02,9.01\",\"\",\"\"],\"acceptanceDateTime\":[\"\",\"2026-07-30T20:30:28.000Z\",\"\",\"\"]}}}"); });
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
                if (mth.equals("DELETE")) { upstreamCalls.add("supabase-delete push_alerts?" + ex.getRequestURI().getRawQuery()); pushRows.removeIf(match); reply(ex, 200, "application/json", "[]"); return; }
                List<Object> out = new ArrayList<>(); for (Map<String, Object> r : pushRows) if (match.test(r)) out.add(r); reply(ex, 200, "application/json", Json.write(out)); }
        });
        fake.createContext("/push/", ex -> { pushed.add(new String[]{ex.getRequestHeaders().getFirst("Authorization"), ex.getRequestHeaders().getFirst("Content-Encoding"), Base64.getEncoder().encodeToString(ex.getRequestBody().readAllBytes())}); reply(ex, 201, "text/plain", ""); });
        java.util.concurrent.atomic.AtomicInteger heartbeats = new java.util.concurrent.atomic.AtomicInteger();
        fake.createContext("/hb", ex -> { heartbeats.incrementAndGet(); reply(ex, 200, "text/plain", "OK"); });
        fake.createContext("/sb/auth/v1/health", ex -> reply(ex, 200, "application/json", "{\"name\":\"GoTrue\"}"));
        fake.createContext("/sb/auth/v1/user", ex -> { String authH = ex.getRequestHeaders().getFirst("Authorization"); upstreamCalls.add("supabase-auth user key=" + ex.getRequestHeaders().getFirst("apikey"));
            if ("Bearer good-user-token-1234567890".equals(authH)) reply(ex, 200, "application/json", "{\"id\":\"0a1b2c3d-1111-2222-3333-444455556666\",\"email\":\"ada@example.com\"}"); else reply(ex, 401, "application/json", "{}"); });
        fake.createContext("/sb/auth/v1/admin/users/", ex -> { upstreamCalls.add("supabase-admin " + ex.getRequestMethod() + " " + ex.getRequestURI().getPath().substring("/sb/auth/v1/admin/users/".length()) + " key=" + ex.getRequestHeaders().getFirst("apikey")); reply(ex, 200, "application/json", "{}"); });
        fake.createContext("/sb/rest/v1/", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), table = ex.getRequestURI().getPath().substring("/sb/rest/v1/".length());
            upstreamCalls.add("supabase " + table + " key=" + ex.getRequestHeaders().getFirst("apikey") + " " + body);
            if (ex.getRequestMethod().equals("DELETE")) { upstreamCalls.add("supabase-delete " + table + "?" + ex.getRequestURI().getRawQuery()); reply(ex, 200, "application/json", "[]"); return; }
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
                if (!sy.equals("NOPE.L")) res.add(Json.obj("symbol", sy, "longName", "Name " + sy, "regularMarketPrice", sy.equals("^GSPC") ? 7623.68 : sy.equals("IPOD") ? 97.17 : sy.equals("IPOX") ? 150.0 : 100.5, "regularMarketChange", sy.equals("IPOD") ? 14.17 : -39.75, "regularMarketChangePercent", sy.equals("IPOD") ? 17.072289 : -0.519, "regularMarketPreviousClose", sy.equals("IPOD") ? 83.0 : null, "currency", sy.endsWith(".NS") ? "INR" : "USD", "fullExchangeName", "X", "quoteType", sy.startsWith("^") ? "INDEX" : "EQUITY", "regularMarketTime", 1792787901));
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
            if (ex.getRequestURI().getRawQuery().contains("PRIVATE_COMPANY")) { boolean hv = ex.getRequestURI().getRawQuery().contains("HIGHEST");
                reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"total\":2,\"records\":[" + (hv ? "{\"ticker\":\"OPAI.PVT\",\"companyName\":\"OpenAI\",\"sector\":\"Artificial Intelligence\",\"regularMarketPrice\":954.7,\"fiftyTwoWeekChangePercent\":91.2,\"latestImpliedValuation\":1.183e12,\"fundingToDate\":1.78e11,\"latestFundingDate\":1774828800,\"latestAmountRaised\":6.8e10,\"latestShareClass\":\"Series C\"}," : "")
                    + "{\"ticker\":\"ANTH.PVT\",\"companyName\":\"Anthropic\",\"sector\":\"Artificial Intelligence\",\"regularMarketPrice\":589.01,\"fiftyTwoWeekChangePercent\":263.25,\"latestImpliedValuation\":9.65e11,\"fundingToDate\":6.8e10,\"latestFundingDate\":1779926400,\"latestAmountRaised\":4.1e10,\"latestShareClass\":\"Series H-1\"},{\"ticker\":\"BAD\"}]}]}}"); return; }
            reply(ex, 200, "application/json", "{\"finance\":{\"result\":[{\"quotes\":[{\"symbol\":\"RHI\",\"longName\":\"Robert Half Inc.\",\"regularMarketPrice\":45.87,\"regularMarketChangePercent\":21.7,\"currency\":\"USD\"},{\"symbol\":\"BRK-B\",\"longName\":\"Berkshire\",\"regularMarketPrice\":500,\"regularMarketChangePercent\":1.0,\"currency\":\"USD\"}]}]}}"); });
        fake.createContext("/yh/v10/finance/quoteSummary/", ex -> { upstreamCalls.add("yahoo summary " + ex.getRequestURI().getPath());
            if (ex.getRequestURI().getPath().endsWith("/FNDX")) { reply(ex, 200, "application/json", "{\"quoteSummary\":{\"result\":[{\"price\":{\"longName\":\"Fund X\",\"quoteType\":\"EQUITY\",\"currency\":\"JPY\",\"regularMarketPrice\":{\"raw\":3000},\"marketCap\":{\"raw\":5e12}},\"summaryDetail\":{\"trailingPE\":{\"raw\":9.5},\"dayHigh\":{\"raw\":3050}},\"financialData\":{\"financialCurrency\":\"JPY\",\"revenueGrowth\":{\"raw\":0.1},\"targetMeanPrice\":{\"raw\":3600},\"numberOfAnalystOpinions\":{\"raw\":19},\"recommendationKey\":\"buy\"},\"recommendationTrend\":{\"trend\":[{\"buy\":10,\"hold\":5}]},\"earningsHistory\":{\"history\":[{\"epsActual\":{\"raw\":71.5},\"epsEstimate\":{\"raw\":50.1},\"surprisePercent\":{\"raw\":0.428},\"quarter\":{\"raw\":1759190400}}]},\"calendarEvents\":{\"earnings\":{\"earningsDate\":[{\"raw\":4102444800}]}}}]}}"); return; }
            if (ex.getRequestURI().getPath().endsWith("/NOFUND")) { reply(ex, 404, "application/json", "{\"quoteSummary\":{\"result\":null,\"error\":{\"code\":\"Not Found\"}}}"); return; }
            if (ex.getRequestURI().getPath().endsWith("/FAILFND")) { reply(ex, 500, "text/plain", "no"); return; }
            if (!ex.getRequestURI().getPath().endsWith(".PVT")) { reply(ex, 500, "text/plain", "no"); return; }
            reply(ex, 200, "application/json", "{\"quoteSummary\":{\"result\":[{\"price\":{\"longName\":\"Anthropic\",\"currency\":\"USD\",\"regularMarketPrice\":{\"raw\":589.01}},\"assetProfile\":{\"sectorDisp\":\"Artificial Intelligence\",\"city\":\"San Francisco\",\"website\":\"https://www.anthropic.com\",\"longBusinessSummary\":\"AI safety company.\"},"
                + "\"fundingRounds\":{\"fundingRoundList\":[{\"date\":{\"raw\":1779926400},\"shareClassShortLabel\":\"Series H-1\",\"amountRaised\":{\"raw\":41287237632},\"issuePrice\":{\"raw\":589.0095},\"impliedValuation\":{\"raw\":964999970816}}]},"
                + "\"marks\":{\"markList\":[{\"date\":{\"raw\":1785456000},\"fund\":\"Growth Fund\",\"shareClass\":\"Series F-1\",\"price\":{\"raw\":589.01},\"filingLink\":\"https://www.sec.gov/Archives/x\"},{\"date\":{\"raw\":1785456000},\"fund\":\"Odd\",\"filingLink\":\"javascript:alert(1)\"}]}}]}}"); });
        fake.createContext("/yh/ws/fundamentals-timeseries/", ex -> { upstreamCalls.add("yahoo timeseries " + ex.getRequestURI().getPath());
            if (!ex.getRequestURI().getPath().endsWith("/FNDX")) { reply(ex, 200, "application/json", "{\"timeseries\":{\"result\":[]}}"); return; }
            reply(ex, 200, "application/json", "{\"timeseries\":{\"result\":[{\"meta\":{\"type\":[\"annualTotalRevenue\"]},\"annualTotalRevenue\":[{\"asOfDate\":\"2024-03-31\",\"currencyCode\":\"JPY\",\"reportedValue\":{\"raw\":4e13}},null,{\"asOfDate\":\"2025-03-31\",\"currencyCode\":\"JPY\",\"reportedValue\":{\"raw\":5e13}}]},{\"meta\":{\"type\":[\"annualNetIncome\"]},\"annualNetIncome\":[{\"asOfDate\":\"2025-03-31\",\"reportedValue\":{\"raw\":3e12}}]},{\"meta\":{\"type\":[\"quarterlyTotalRevenue\"]},\"quarterlyTotalRevenue\":[{\"asOfDate\":\"2025-06-30\",\"reportedValue\":{\"raw\":1.3e13}}]},{\"meta\":{\"type\":[\"annualUnknownThing\"]},\"annualUnknownThing\":[]},{\"meta\":{\"type\":[\"annualTotalAssets\"]},\"annualTotalAssets\":[{\"asOfDate\":\"2023-03-31\",\"reportedValue\":{\"raw\":9e13}}]}]}}"); });
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
        r = get(p, "/api/quote?symbol=IPOA");
        t("first trading day: no previous close means no day change (not +0.00%)", r.code == 200 && m(r.body).get("prevClose") == null && m(r.body).get("changePct") == null && Boolean.TRUE.equals(m(r.body).get("firstDay")));
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
        double[] sm = Research.sma(new double[]{1, 2, 3, 4, 5}, 2);
        t("technicals: moving average and RSI maths", sm[4] == 4.5 && Double.isNaN(sm[0]) && Research.rsi(new double[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, 14) == 100
                && Math.abs(Research.ema(new double[]{2, 4, 6}, 2)[2] - 5) < 1e-9);
        r = get(p, "/api/technicals?symbol=TECH");
        Map<String, Object> tc = m(r.body), cn = m(tc.get("counts"));
        String rsiSig = l(tc.get("signals")).stream().map(x -> m(x)).filter(x -> "RSI (14 days)".equals(x.get("name"))).map(x -> String.valueOf(x.get("signal"))).findFirst().orElse("");
        t("technicals: a steady uptrend rates Buy or Strong buy, shows overbought RSI, and lists every signal with its rule", r.code == 200
                && List.of("Buy", "Strong buy").contains(tc.get("rating")) && "Uptrend".equals(tc.get("trend")) && "sell".equals(rsiSig)
                && ((Number) cn.get("buy")).intValue() + ((Number) cn.get("neutral")).intValue() + ((Number) cn.get("sell")).intValue() == l(tc.get("signals")).size()
                && l(tc.get("signals")).stream().allMatch(x -> m(x).get("rule") != null) && "2026-11-11".equals(tc.get("asOf")));
        t("technicals: too little history is a clear 404", get(p, "/api/technicals?symbol=AAPL").code == 404);
        boolean bad = false; try { Research.parse("{\"verdict\":{\"rating\":\"Moon\"}}"); } catch (RuntimeException e) { bad = true; }
        t("research: a reply without a valid Positive/Neutral/Negative outlook is rejected, not shown", bad);
        System.setProperty("GEMINI_API_KEY", "gm-test");
        System.setProperty("ADMIN_TOKEN", "adm-test");
        r = adminGet(p, "/api/health/detail", "adm-test");
        t("health detail: Gemini chosen when its key is set", "gemini".equals(m(r.body).get("aiProvider")));
        r = call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about Apple\"}]}", ip());
        t("Gemini chat runs the quote tool and answers, key server-side", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple is $226") && l(m(r.body).get("used")).contains("Quote AAPL") && !r.raw.contains("gm-test")
                && upstreamCalls.stream().anyMatch(u -> u.startsWith("gemini /gm/v1beta/models/gemini-flash-latest:generateContent key=gm-test schema-ok")));
        r = call(p, "POST", "/api/research", "{\"symbol\":\"AAPL\"}", ip());
        Map<String, Object> rv = m(m(m(r.body).get("report")).get("verdict"));
        t("research: AI report parsed from the reply, outlook checked (old \"Hold\" wording maps to Neutral), unknown confidence falls back to Low, empty sections dropped, sources listed",
                r.code == 200 && "Neutral".equals(rv.get("rating")) && "Low".equals(rv.get("confidence")) && l(m(m(r.body).get("report")).get("sections")).size() == 1
                && !l(m(r.body).get("sources")).isEmpty() && upstreamCalls.contains("gemini research with-data") && !r.raw.contains("gm-test"));
        t("research: GET is refused", get(p, "/api/research?symbol=AAPL").code == 405);
        r = call(p, "POST", "/api/explain", "{\"lens\":\"eli5\",\"title\":\"Chips rally\",\"summary\":\"Shares rose.\",\"symbols\":[\"NVDA\"]}", ip());
        t("Gemini explains a story", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("chips rose"));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"portfolio\",\"title\":\"Chips rally\",\"summary\":\"Shares rose.\",\"symbols\":[\"NVDA\"],\"holdings\":[\"NVDA 40%\",\"KO 10.5%\",\"<script> 5%\",\"jane@x.com 50%\"]}", ip());
        t("portfolio lens: explains with holdings; only ticker-and-weight entries are sent", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("chips rose"));
        r = call(p, "POST", "/api/explain", "{\"lens\":\"portfolio\",\"title\":\"Chips rally\",\"summary\":\"Shares rose.\",\"holdings\":[\"bad\"]}", ip());
        t("portfolio lens: no valid holdings is a 400", r.code == 400);
        r = call(p, "POST", "/api/explain", "{\"lens\":\"eli5\",\"title\":\"BADKEY\"}", ip());
        t("Gemini invalid key -> clear 503 message", r.code == 503 && r.raw.contains("GEMINI_API_KEY"));
        Anthropic.AI_PROBLEM = null;
        r = call(p, "POST", "/api/explain", "{\"lens\":\"eli5\",\"title\":\"DENIEDKEY\"}", ip());
        t("Gemini permission denied -> says how to fix it and shows on /api/status", r.code == 503 && r.raw.contains("aistudio.google.com") && Anthropic.AI_PROBLEM != null && Anthropic.AI_PROBLEM.contains("permission denied"));
        Anthropic.AI_PROBLEM = null;
        System.setProperty("AI_PROVIDER", "anthropic");
        t("AI_PROVIDER=anthropic switches back to Claude", "anthropic".equals(m(adminGet(p, "/api/health/detail", "adm-test").body).get("aiProvider")));
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
        {
            Map<String, Object> fresh = Providers.CACHE.get("stale-map", 0, () -> Json.obj("price", 10.0));
            Map<String, Object> old = Providers.CACHE.get("stale-map", 0, () -> { throw new Support.UpstreamException("limit", 429); });
            Map<String, Object> again = Providers.CACHE.peek("stale-map");
            t("stale data is marked: stale=true with the time it was fetched; fresh data and the cached copy carry no flag", !fresh.containsKey("stale")
                    && Boolean.TRUE.equals(old.get("stale")) && String.valueOf(old.get("asOf")).matches("\\d{4}-\\d{2}-\\d{2}T.*Z") && d(old.get("price")) == 10 && !again.containsKey("stale"));
        }
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
        Anthropic.AI_PROBLEM = null; Store.resetPing();
        r = get(p, "/api/status");
        t("status: everything configured and answering -> 200", r.code == 200 && Boolean.TRUE.equals(m(r.body).get("ok")));
        System.setProperty("SUPABASE_URL", base + "/nosb/"); Store.resetPing();
        r = get(p, "/api/status");
        t("status: paused / unreachable Supabase project -> 503 naming it", r.code == 503 && r.raw.contains("Supabase project"));
        System.setProperty("SUPABASE_URL", base + "/sb/"); Store.resetPing();
        Anthropic.AI_PROBLEM = "The AI service key is invalid."; Anthropic.AI_PROBLEM_AT = System.currentTimeMillis();
        r = get(p, "/api/status");
        t("status: rejected AI key -> 503 naming it", r.code == 503 && r.raw.contains("AI: The AI service key is invalid"));
        Anthropic.AI_PROBLEM = null;
        t("delete account: missing token -> 401", call(p, "POST", "/api/account/delete", "{}", ip()).code == 401);
        t("delete account: token Supabase rejects -> 401, nothing deleted", call(p, "POST", "/api/account/delete", "{\"token\":\"bad-user-token-1234567890\"}", ip()).code == 401 && upstreamCalls.stream().noneMatch(u -> u.startsWith("supabase-admin")));
        java.io.PrintStream realOut = System.out; java.io.ByteArrayOutputStream logBuf = new java.io.ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(new java.io.OutputStream() { public void write(int b) { logBuf.write(b); realOut.write(b); } }, true));
        r = call(p, "POST", "/api/account/delete", "{\"token\":\"good-user-token-1234567890\",\"subscription\":{\"endpoint\":\"https://fcm.googleapis.com/fcm/send/device-abcdefgh\",\"keys\":{\"p256dh\":\"BPk0aXdlbnR5Y2hhcnNfX19fX18\",\"auth\":\"YXV0aHNlY3JldDEyMzQ1Ng\"}}}", ip());
        System.setOut(realOut);
        t("delete account: verifies the token, removes saved data + leaderboard, then the login", r.code == 200 && upstreamCalls.stream().anyMatch(u -> u.startsWith("supabase user_data")) && upstreamCalls.stream().anyMatch(u -> u.startsWith("supabase leaderboard")) && upstreamCalls.stream().anyMatch(u -> u.equals("supabase-admin DELETE 0a1b2c3d-1111-2222-3333-444455556666 key=sb-test")) && !r.raw.contains("sb-test"));
        t("delete account: also removes the waitlist and feedback rows for the verified email, and this browser's price alerts",
                upstreamCalls.contains("supabase-delete waitlist?email=eq.ada%40example.com") && upstreamCalls.contains("supabase-delete feedback?email=eq.ada%40example.com")
                && upstreamCalls.contains("supabase-delete push_alerts?endpoint=eq.https%3A%2F%2Ffcm.googleapis.com%2Ffcm%2Fsend%2Fdevice-abcdefgh")
                && l(m(r.body).get("removed")).containsAll(List.of("user_data", "leaderboard", "waitlist", "feedback", "push_alerts")));
        t("delete account: the email never appears in the server log", !logBuf.toString(StandardCharsets.UTF_8).toLowerCase().contains("ada@example.com") && !logBuf.toString(StandardCharsets.UTF_8).contains("ada%40example.com"));
        t("delete account rejects GET", get(p, "/api/account/delete").code == 405);
        System.setProperty("SUPABASE_SERVICE_KEY", "sb_secret_abc\n••••");
        r = call(p, "POST", "/api/waitlist", "{\"email\":\"masked@y.io\"}", ip());
        HttpResponse<String> st = C.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/api/status")).build(), HttpResponse.BodyHandlers.ofString());
        t("status: malformed storage key -> 503 that says so, key never echoed", st.statusCode() == 503 && st.body().contains("SUPABASE_SERVICE_KEY is malformed") && !st.body().contains("sb_secret"));
        t("masked/multi-line storage key -> 503, key never echoed", r.code == 503 && !r.raw.contains("sb_secret"));
        System.clearProperty("SUPABASE_URL"); System.clearProperty("SUPABASE_SERVICE_KEY");
        r = get(p, "/api/health");
        t("health reports configured services", d(1) == 1 && Boolean.TRUE.equals(m(r.body).get("market")) && Boolean.TRUE.equals(m(r.body).get("news")) && Boolean.TRUE.equals(m(r.body).get("ai")));
        aiStep.set(0);
        r = call(p, "POST", "/api/ai", "{\"messages\":[{\"role\":\"user\",\"content\":\"Tell me about Apple\"}]}", ip());
        t("AI runs tool loop and returns text + tools used", r.code == 200 && String.valueOf(m(r.body).get("text")).contains("Apple") && l(m(r.body).get("used")).contains("Quote AAPL"));
        t("AI reply has clickable sources and a figure check", l(m(r.body).get("sources")).toString().contains("https://finance.yahoo.com/quote/AAPL") && m(r.body).get("check") instanceof Map);
        t("AI: Ask AI and story explanations tell the model never to give personal buy/sell/hold or allocation advice",
                upstreamCalls.stream().anyMatch(u -> u.startsWith("anthropic") && u.contains("hasTools=true") && u.endsWith(" no-advice"))
                && upstreamCalls.stream().anyMatch(u -> u.startsWith("anthropic") && u.contains("hasTools=false") && u.endsWith(" no-advice"))
                && upstreamCalls.stream().filter(u -> u.startsWith("anthropic") && u.contains("hasTools=true")).allMatch(u -> u.endsWith(" no-advice")));
        t("AI: the same rule goes to Gemini for chat", upstreamCalls.stream().anyMatch(u -> u.startsWith("gemini") && u.contains(" chat") && u.contains(" no-advice")));
        {
            Map<String, Object> data = Json.obj("price", 226.31, "revenue", 394_328_000_000L, "grossMargin", 0.4621, "head", "Shares rose 3.2% on Tuesday");
            Map<String, Object> ck = Grounding.check("Apple trades at $226.31 (Yahoo Finance). Revenue was $394.3 billion in 2025, gross margin 46.2%, up 3.2% Nov 11. "
                    + "It has 3 product lines and a 10-K filing; the 52-week range is wide. Profit was $512 billion. [[asset:AAPL]] 14-day RSI.", data);
            t("grounding: matches rounded price, billions, fractions as % and numbers inside text", d(ck.get("matched")) == 4);
            t("grounding: flags a figure not in the data, skips years, counts, dates, form names and parameters", d(ck.get("figures")) == 5 && l(ck.get("unmatched")).equals(List.of("$512 billion")));
            t("grounding: no figures -> nothing to check", d(Grounding.check("Apple makes phones.", data).get("figures")) == 0);
            t("grounding: SEC filing links come from the statements' periods", Grounding.links("get_financial_statements", Json.obj("symbol", "AAPL"),
                    Json.obj("periods", List.of(Json.obj("period", "FY2025", "filing", "10-K", "link", "https://www.sec.gov/Archives/x.htm")))).toString().contains("https://www.sec.gov/Archives/x.htm"));
            t("grounding: news links skip items without a web address", Grounding.links("get_news", Json.obj(), List.of(Json.obj("title", "A", "url", "https://n.test/1"), Json.obj("title", "B"))).size() == 1);
        }
        {
            // Reaction gap, on built prices: market moves +1% on day 0; BIG (beta 2, noisy) jumps 25%, SMALL (beta 0.5, calm) 5%
            int n = 160; List<String> days = new ArrayList<>(); java.time.LocalDate d0 = java.time.LocalDate.of(2026, 3, 2);
            for (int i = 0, k = 0; days.size() < n; k++) { java.time.LocalDate d = d0.plusDays(k); if (d.getDayOfWeek().getValue() <= 5) days.add(d.toString()); }
            double[] mc = new double[n], bc = new double[n], sc = new double[n], bv = new double[n], sv = new double[n]; java.util.Random rnd = new java.util.Random(7);
            mc[0] = 100; bc[0] = 50; sc[0] = 200; int ev = 140;
            for (int i = 1; i < n; i++) {
                double rm = i == ev ? 0.01 : (rnd.nextGaussian() * 0.01), eb = i == ev ? 0.23 : rnd.nextGaussian() * 0.04, es = i == ev ? 0.045 : rnd.nextGaussian() * 0.005;
                mc[i] = mc[i - 1] * (1 + rm); bc[i] = bc[i - 1] * (1 + 2 * rm + eb); sc[i] = sc[i - 1] * (1 + 0.5 * rm + es);
                bv[i] = i == ev ? 5000 : 1000; sv[i] = i == ev ? 1500 : 1000;
            }
            Reaction.Series M = new Reaction.Series(days, mc, mc), Bs = new Reaction.Series(days, bc, bv), Ss = new Reaction.Series(days, sc, sv);
            java.time.LocalDate evd = java.time.LocalDate.parse(days.get(ev));
            Map<String, Object> big = Reaction.measure("BIG", Bs, evd, M, "BigCo wins huge contract", Json.obj("name", "BigCo Inc", "marketCap", 2e9, "pe", 60.0));
            Map<String, Object> small = Reaction.measure("SMALL", Ss, evd, M, "BigCo wins huge contract", Json.obj("name", "Smallish Corp", "marketCap", 4e11, "pe", 20.0));
            t("reaction: day-0 move, beta and market part measured", Math.abs(d(big.get("move")) - 25.0) < 0.6 && Math.abs(d(big.get("beta")) - 2) < 0.35 && Math.abs(d(big.get("market")) - 2 * d(big.get("benchmarkMove"))) < 0.6);
            t("reaction: abnormal = move − market part; measured against its own normal day", Math.abs(d(big.get("abnormal")) - (d(big.get("move")) - d(big.get("market")))) < 0.02 && d(big.get("typicalDay")) > 2.5 && d(big.get("typicalDay")) < 6 && d(small.get("typicalDay")) < 1);
            t("reaction: volume vs average, named in headline", d(big.get("volumeVsAverage")) == 5.0 && Boolean.TRUE.equals(big.get("namedInHeadline")) && Boolean.FALSE.equals(small.get("namedInHeadline")));
            List<Object> why = Reaction.explain(big, small);
            String all = why.toString();
            t("reaction: reasons cover market, normal volatility, size, who the news names, volume and valuation", all.contains("The whole market") && all.contains("How much each stock normally moves") && all.contains("200× smaller") && all.contains("headline names BIG") && all.contains("5.0× its usual volume") && all.contains("60 times earnings"));
            t("reaction: patterns are labelled as patterns, measurements as measurements", why.stream().allMatch(o -> List.of("measured", "pattern").contains(((Map<?, ?>) o).get("kind"))));
            t("reaction: news after the close counts from the next session", Reaction.sessionFor("2026-11-05T21:30:00Z").toString().equals("2026-11-06") && Reaction.sessionFor("2026-11-05T15:00:00Z").toString().equals("2026-11-05") && Reaction.sessionFor("2026-11-05").toString().equals("2026-11-05"));
            boolean tooEarly; try { Reaction.measure("BIG", Bs, java.time.LocalDate.parse(days.get(10)), M, "", Map.of()); tooEarly = false; } catch (Support.UpstreamException e) { tooEarly = e.code == 404; }
            t("reaction: refuses when there isn't enough history before the news", tooEarly);
        }
        {
            System.setProperty("AI_DAILY_LIMIT", "2"); AiBudget.reset();
            AiBudget.take(); AiBudget.take();
            int code = 0; try { AiBudget.take(); } catch (Support.UpstreamException e) { code = e.code; }
            t("AI daily cap: refuses with 429 once the day's allowance is used", code == 429 && AiBudget.usedToday() == 2);
            System.setProperty("AI_DAILY_LIMIT", "0"); AiBudget.reset(); code = 0; try { AiBudget.take(); } catch (Support.UpstreamException e) { code = e.code; }
            t("AI daily cap: 0 turns AI off", code == 429);
            System.clearProperty("AI_DAILY_LIMIT"); AiBudget.reset();
        }
        {
            System.setProperty("AI_GLOBAL_PER_MIN", "2"); AiBudget.reset();
            AiBudget.take(); AiBudget.take();
            String msg = ""; int code = 0; try { AiBudget.take(); } catch (Support.UpstreamException e) { code = e.code; msg = e.getMessage(); }
            t("AI per-minute cap: a third request in the same minute gets 429 'try again in a minute', whoever sends it", code == 429 && msg.contains("minute") && AiBudget.usedToday() == 2);
            System.clearProperty("AI_GLOBAL_PER_MIN"); AiBudget.reset();
            System.setProperty("AI_DAILY_LIMIT", "1"); Providers.CACHE.clear();
            String ex1 = "{\"lens\":\"tldr\",\"title\":\"Cap check one\",\"summary\":\"Shares rose.\"}";
            R a1 = call(p, "POST", "/api/explain", ex1, ip()), a2 = call(p, "POST", "/api/explain", ex1, ip());
            R a3 = call(p, "POST", "/api/explain", "{\"lens\":\"tldr\",\"title\":\"Cap check two\",\"summary\":\"Shares rose.\"}", ip());
            t("AI daily cap: a cached explanation doesn't use the allowance; a new one over the cap is 429", a1.code == 200 && a2.code == 200 && AiBudget.usedToday() == 1 && a3.code == 429 && a3.raw.contains("daily limit"));
            System.clearProperty("AI_DAILY_LIMIT"); AiBudget.reset();
        }
        {
            Support.RateLimiter rl = new Support.RateLimiter();
            for (int i = 0; i < 4; i++) rl.limited("203.0.113.50", "/api/ai", 3);
            for (int i = 0; i < Support.RateLimiter.MAX_KEYS + 1000; i++) rl.limited("198.51." + (i / 250) + "." + (i % 250), "/api/ai", 3);
            t("rate limit: a flood of new addresses doesn't reset a client that's already over its limit", rl.limited("203.0.113.50", "/api/ai", 3) && rl.size() <= Support.RateLimiter.MAX_KEYS + 1);
        }
        {
            Map<String, Object> h = m(get(p, "/api/health").body);
            t("health: public status is on/off flags only (no provider names or error text)", h.values().stream().allMatch(v -> v instanceof Boolean)
                    && h.keySet().equals(Set.of("ok", "market", "news", "ai", "storage", "intraday", "marketOpen", "global", "pushAlerts", "economyKey")));
            System.setProperty("ADMIN_TOKEN", "adm-test");
            R noTok = adminGet(p, "/api/health/detail", null), badTok = adminGet(p, "/api/health/detail", "adm-tesx"), ok = adminGet(p, "/api/health/detail", "adm-test");
            System.clearProperty("ADMIN_TOKEN");
            R unset = adminGet(p, "/api/health/detail", "adm-test");
            t("health detail: 404 without the admin token, with a wrong one, or when ADMIN_TOKEN isn't set", noTok.code == 404 && badTok.code == 404 && unset.code == 404 && !noTok.raw.contains("aiProvider"));
            t("health detail: the right token shows providers and last errors, never cached", ok.code == 200 && m(ok.body).containsKey("aiProvider") && m(ok.body).containsKey("lastPushError") && m(ok.body).containsKey("aiUsedToday"));
        }
        {
            // a client calling the API directly can fake X-Forwarded-For, but not the connecting address Render's edge reports
            int last = 0;
            for (int i = 0; i < 302; i++) {
                HttpResponse<String> rr = C.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/api/waitlist")).header("X-Forwarded-For", "10.9." + (i / 250) + "." + (i % 250))
                        .header("CF-Connecting-IP", "203.0.113.7").header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
                last = rr.statusCode();
            }
            t("rate limit: faking a new X-Forwarded-For per request doesn't escape the per-connection backstop", last == 429);
            HttpResponse<String> other = C.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/api/waitlist")).header("X-Forwarded-For", "10.9.9.9")
                    .header("CF-Connecting-IP", "203.0.113.8").header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(), HttpResponse.BodyHandlers.ofString());
            t("rate limit: other connections are unaffected", other.statusCode() != 429);
        }
        {
            List<Object> rows = new ArrayList<>();
            double[][] d = {{3e12, 1.2}, {5e11, 1.5}, {9e10, 2.1}, {4e10, 2.4}, {8e9, 2.9}, {5e9, 3.1}, {3e9, 2.6}, {1.5e9, 4.2}, {9e8, 3.8}, {6e8, 5.0}};
            for (int i = 0; i < d.length; i++) rows.add(Json.obj("event_key", "e" + (i % 3), "day0", "2026-11-0" + (1 + i % 3), "symbol", "S" + i, "market_cap", d[i][0], "sigmas", (i % 2 == 0 ? 1 : -1) * d[i][1], "abnormal_pct", d[i][1] * 1.5));
            rows.add(Json.obj("event_key", "e9", "day0", "2026-11-05", "symbol", "X", "market_cap", null, "sigmas", 1.0, "abnormal_pct", 1.0));
            Map<String, Object> s = Tracker.summarise(rows);
            List<Object> b = l(s.get("buckets"));
            t("tracker: rows grouped by size (mega, large, mid, small), unknown size left out of the groups", d(m(b.get(0)).get("n")) == 2 && d(m(b.get(1)).get("n")) == 2 && d(m(b.get(2)).get("n")) == 3 && d(m(b.get(3)).get("n")) == 3 && d(s.get("measured")) == 10 && d(s.get("rows")) == 11);
            t("tracker: medians use the size of the reaction, up or down", d(m(b.get(0)).get("medianSigmas")) == 1.35 && d(m(b.get(3)).get("medianSigmas")) == 4.2);
            t("tracker: smaller companies reacting more shows as a negative rank correlation; events and dates counted", d(s.get("rankCorrelation")) < -0.8 && d(s.get("events")) == 4 && "2026-11-01".equals(s.get("from")) && "2026-11-05".equals(s.get("to")));
            t("tracker: correlation only from 10 measurements", Tracker.summarise(rows.subList(0, 5)).get("rankCorrelation") == null);
            java.time.ZonedDateTime fri5pm = java.time.ZonedDateTime.of(2026, 11, 13, 19, 0, 0, 0, Reaction.NY), fri3pm = fri5pm.withHour(15), mon5pm = fri5pm.plusDays(3);
            t("tracker: waits until the first session after the story has closed (after-close and weekend stories count from Monday)",
                    Tracker.sessionClosed("2026-11-13T15:00:00Z", fri5pm) && !Tracker.sessionClosed("2026-11-13T15:00:00Z", fri3pm)
                    && !Tracker.sessionClosed("2026-11-13T22:00:00Z", fri5pm.plusDays(1)) && Tracker.sessionClosed("2026-11-13T22:00:00Z", mon5pm) && !Tracker.sessionClosed("not a date", mon5pm));
            t("tracker: waits for final closing prices (not measured at 5 pm New York time)", !Tracker.sessionClosed("2026-11-13T15:00:00Z", fri5pm.withHour(17)));
            t("tracker: only plain US tickers, at most six", Tracker.tickers(Json.obj("symbols", List.of(Json.obj("symbol", "NVDA"), Json.obj("symbol", "7203.T"), Json.obj("symbol", "OPAI.PVT"), "amd"))).equals(List.of("NVDA", "AMD")));
        }
        t("reaction API: needs two companies and a real date", call(p, "GET", "/api/reaction?symbols=AAPL&at=2026-10-22", null, ip()).code == 400 && call(p, "GET", "/api/reaction?symbols=AAPL,MSFT&at=yesterday", null, ip()).code == 400);
        t("AI key sent to Anthropic only, with tools", upstreamCalls.stream().anyMatch(u -> u.contains("anthropic key=an-test hasTools=true")) && !r.raw.contains("an-test"));
        {
            Map<String, Object> ok = Store.feedbackRow(Json.obj("kind", "data", "message", "  NVDA free cash flow\u0007 looks wrong  ", "page", "#/asset/NVDA", "email", ""));
            t("feedback: cleans the message, keeps the page, no email stored when blank", "NVDA free cash flow looks wrong".equals(ok.get("message")) && "#/asset/NVDA".equals(ok.get("page")) && ok.get("email") == null);
            t("feedback: bots that fill the hidden field are dropped quietly", Store.feedbackRow(Json.obj("kind", "bug", "message", "hello there", "website", "x")) == null);
            boolean fbBad = true;
            for (Map<String, Object> m : List.of(Json.obj("kind", "spam", "message", "hello there"), Json.obj("kind", "bug", "message", "hey"), Json.obj("kind", "bug", "message", "x".repeat(2001)), Json.obj("kind", "bug", "message", "hello there", "email", "not-an-email")))
                try { Store.feedbackRow(m); fbBad = false; } catch (Support.UpstreamException e) { fbBad &= e.code == 400; }
            t("feedback: rejects unknown kinds, too short or long messages and bad emails", fbBad);
            t("feedback: odd page values are dropped, not stored", "".equals(Store.feedbackRow(Json.obj("kind", "idea", "message", "hello there", "page", "<script>")).get("page")));
            t("feedback API: POST only", call(p, "GET", "/api/feedback", null, ip()).code == 405);
        }
        {
            String line = Seo.asOfLine("2026-11-14T21:00:00Z", "2026-11-16T01:20:00Z", "2026-09-30", "2025-12-31");
            t("stock page dates: price in New York time, company periods, fetch time in UTC (not the page-build date)",
                    line.contains("Price as of 14 Nov 2026, 4:00 pm New York time") && line.contains("quarter ended Sep 2026") && line.contains("full year ended Dec 2025") && line.contains("Data fetched 16 Nov 2026, 1:20 am UTC"));
            List<Object> rounds = List.of(Json.obj("date", "2026-02-24", "round", "Tender 3", "valuation", 159e9), Json.obj("date", "2025-02-27", "round", "Tender 2", "valuation", 91.5e9));
            t("private valuation: compared with the last priced round, with its date", Seo.pvtBasis(199.1e9, rounds).contains("25.2% above the $159.0B valuation of its Tender 3 round on 2026-02-24")
                    && Seo.pvtBasis(1.2536e12, List.of(Json.obj("date", "2026-11-06", "round", "Series D", "valuation", 1.25e12))).startsWith("This matches the post-money valuation of its Series D round on 2026-11-06")
                    && Seo.pvtBasis(5e9, List.of()).contains("secondary-market prices only"));
        }
        {
            List<Map<String, Object>> g = Seo.groupRounds(List.of(
                    Json.obj("date", "2026-11-06", "round", "Series D", "raised", 11e9, "issuePrice", 992.0623, "valuation", 1.25e12),
                    Json.obj("date", "2026-11-04", "round", "Series D", "raised", 20e9, "issuePrice", 992.0623, "valuation", 1.25e12),
                    Json.obj("date", "2026-03-31", "round", "Series C", "raised", 122e9, "issuePrice", 687.6869, "valuation", 852e9)));
            t("private rounds: two closings of one round become one row with both dates", g.size() == 2 && "Series D (2 closings)".equals(g.get(0).get("round"))
                    && "2026-11-04, 2026-11-06".equals(g.get(0).get("date")) && Math.abs((Double) g.get(0).get("raised") - 31e9) < 1 && "Series C".equals(g.get(1).get("round")));
            List<Map<String, Object>> h = Seo.groupRounds(List.of(
                    Json.obj("date", "2026-05-28", "round", "Series H-2", "raised", 18.2e9, "issuePrice", 589.0095, "valuation", 965e9),
                    Json.obj("date", "2026-05-28", "round", "Series H-3", "raised", 1.9e9, "issuePrice", 20.928, "valuation", 965e9)));
            t("private rounds: share classes issued together become one row; differing prices are flagged", h.size() == 1 && "Series H (2 share classes)".equals(h.get(0).get("round")) && Boolean.TRUE.equals(h.get(0).get("priceVaries")));
        }
        {
            List<Object> dup = List.of(
                    Json.obj("event_key", "a", "symbol", "NVDA", "day0", "2026-11-13", "market_cap", 4e12, "sigmas", -1.0, "abnormal_pct", -1.2),
                    Json.obj("event_key", "b", "symbol", "NVDA", "day0", "2026-11-13", "market_cap", 4e12, "sigmas", -1.0, "abnormal_pct", -1.2),
                    Json.obj("event_key", "b", "symbol", "AAPL", "day0", "2026-11-13", "market_cap", 3.5e12, "sigmas", 0.3, "abnormal_pct", 0.4));
            Map<String, Object> dsm = Tracker.summarise(dup);
            t("tracker: one company's move on one day counts once, however many stories tagged it", Integer.valueOf(2).equals(dsm.get("measured")) && Integer.valueOf(2).equals(dsm.get("events")));
        }
        t("listed: a private company matches its new listing by whole-word name, not a look-alike",
                Listed.nameMatches("Anthropic", "ANTHROPIC PBC-CL A") && Listed.nameMatches("Anthropic", "Anthropic, PBC Class A Common Stock")
                && !Listed.nameMatches("Ramp", "LiveRamp Holdings") && !Listed.nameMatches("Anthropic", "ANTHROPICX CORP") && !Listed.nameMatches("", "ANY"));
        t("company profile: a US ticker described by its home listing (TSM in TWD, SHEL in London, TTE in Paris) is detected; US listings are not",
                Providers.foreignProfile("TSM", "TWD", "TAIWAN STOCK EXCHANGE") && Providers.foreignProfile("SHEL", "USD", "LONDON STOCK EXCHANGE")
                && Providers.foreignProfile("TTE", "USD", "NYSE EURONEXT - EURONEXT PARIS") && Providers.foreignProfile("BABA", "CNY", "NEW YORK STOCK EXCHANGE, INC.")
                && !Providers.foreignProfile("AAPL", "USD", "NASDAQ NMS - GLOBAL MARKET") && !Providers.foreignProfile("JPM", "USD", "NEW YORK STOCK EXCHANGE, INC.")
                && !Providers.foreignProfile("SHEL.L", "GBp", "LONDON STOCK EXCHANGE"));
        {
            Map<String, Object> btc = Yahoo.consistent(Json.obj("price", 61891.97, "change", -1777.082, "changePct", -2.7911239, "prevClose", 61904.76));
            t("quotes: change, % change and previous price always agree (crypto 24h change)", Math.abs((Double) btc.get("prevClose") - 63669.052) < 0.05 && Math.abs((Double) btc.get("changePct") + 2.7911239) < 0.0001);
            Map<String, Object> five = Yahoo.consistent(Json.obj("price", 61653.74, "change", -3202.104, "changePct", -3.165, "prevClose", 64855.844));
            t("quotes: a previous price from another day (5-day chart) is replaced using the day's % change",
                    Math.abs((Double) five.get("prevClose") - 61653.74 / (1 - 0.03165)) < 0.01 && Math.abs((Double) five.get("changePct") + 3.165) < 0.0001 && Math.abs((Double) five.get("change") - (61653.74 - 61653.74 / (1 - 0.03165))) < 0.01);
            Map<String, Object> ok = Json.obj("price", 156.14, "change", -6.71, "changePct", -4.1204, "prevClose", 162.85);
            t("quotes: consistent quotes are left as they are", Yahoo.consistent(ok) == ok);
        }
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
        System.setProperty("FINNHUB_API_KEY", "fh-test");
        r = get(p, "/api/quote?symbol=IPOD");
        t("first trading day: the change is measured from the IPO price Yahoo reports", r.code == 200 && d(m(r.body).get("prevClose")) == 83.0 && Math.abs(d(m(r.body).get("changePct")) - 17.0723) < 0.001 && d(m(r.body).get("price")) == 97.17);
        r = get(p, "/api/quote?symbol=IPOX");
        t("first trading day: if Yahoo's price disagrees, the change stays empty", r.code == 200 && m(r.body).get("changePct") == null);
        System.clearProperty("FINNHUB_API_KEY"); Providers.CACHE.clear();
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
        r = get(p, "/api/screen?c=in&cap=1000000000&pe=20&growth=10&roe=12&div=2&sort=dividend");
        Map<String, Object> scr0 = m(l(m(r.body).get("items")).get(0));
        t("screener: filters reach Yahoo and rows come back normalized", r.code == 200 && "INFY.NS".equals(scr0.get("symbol")) && d(m(r.body).get("total")) == 209
            && upstreamCalls.stream().anyMatch(u -> u.startsWith("yahoo screener") && u.contains("quarterlyrevenuegrowth.quarterly") && u.contains("returnonequity.lasttwelvemonths") && u.contains("forward_dividend_yield") && u.contains("peratio.lasttwelvemonths") && u.contains("intradaymarketcap")));
        t("screener: bad input rejected", get(p, "/api/screen?sort=memes").code == 400 && get(p, "/api/screen?pe=abc").code == 400 && get(p, "/api/screen?c=zz").code == 400 && get(p, "/api/screen?sector=Nope").code == 400);
        System.setProperty("TWELVEDATA_API_KEY", "td-test"); Providers.TD_BLOCKED_UNTIL = System.currentTimeMillis() + 60_000;
        t("stock history falls back to Yahoo when Twelve Data's limit is reached", get(p, "/api/history?symbol=NEWUS").code == 200 && "Yahoo Finance".equals(m(get(p, "/api/history?symbol=NEWUS").body).get("source")));
        t("intraday falls back to Yahoo too", "Yahoo Finance".equals(m(get(p, "/api/intraday?symbol=NEWUS&interval=1h").body).get("source")));
        upstreamCalls.clear(); get(p, "/api/history?symbol=NEWB.B");
        t("fallback asks Yahoo for class shares by their Yahoo symbol", upstreamCalls.stream().anyMatch(u -> u.contains("/chart/NEWB-B")));
        Providers.TD_BLOCKED_UNTIL = 0; System.clearProperty("TWELVEDATA_API_KEY");
        System.setProperty("FINNHUB_API_KEY", "fh-test"); Providers.FH_BLOCKED_UNTIL = System.currentTimeMillis() + 60_000;
        t("quotes and company profiles fall back to Yahoo when Finnhub's limit is reached", get(p, "/api/quote?symbol=NEWUSQ").code == 200 && get(p, "/api/overview?symbol=FNDX").code == 200);
        Providers.FH_BLOCKED_UNTIL = 0; System.clearProperty("FINNHUB_API_KEY");
        r = get(p, "/api/fundamentals?symbol=FNDX");
        Map<String, Object> fk = m(m(r.body).get("key")), fa = m(m(r.body).get("analysts"));
        List<Object> fan = l(m(r.body).get("annual"));
        t("fundamentals: key stats, analysts, earnings and statements in the reporting currency, oldest first", r.code == 200 && d(fk.get("pe")) == 9.5 && d(fk.get("marketCap")) == 5e12 && d(fk.get("revenueGrowth")) == 0.1 && d(fa.get("target")) == 3600 && d(fa.get("buy")) == 10
            && fan.size() == 2 && d(m(fan.get(1)).get("revenue")) == 5e13 && d(m(fan.get(1)).get("netIncome")) == 3e12 && l(m(r.body).get("quarterly")).size() == 1 && "JPY".equals(m(r.body).get("financialCurrency")) && "2100-01-01".equals(m(r.body).get("nextEarnings")) && d(m(l(m(r.body).get("earnings")).get(0)).get("surprise")) == 0.428);
        t("fundamentals: a symbol with no data is a 404, not an error page", get(p, "/api/fundamentals?symbol=NOFUND").code == 404);
        t("fundamentals: when Yahoo's profile request fails, the answer is an error (so the last good profile is kept), not an empty profile", get(p, "/api/fundamentals?symbol=FAILFND").code >= 500);
        System.setProperty("FINNHUB_API_KEY", "fh-test");
        r = get(p, "/api/peers?symbol=FNDX");
        List<Object> prs = r.code == 200 ? l(m(r.body).get("peers")) : List.of();
        t("peers: the company's own figures come first; a peer whose figures fail is left out, not filled in; non-US peers skipped; source stated",
            r.code == 200 && prs.size() == 1 && "FNDX".equals(m(prs.get(0)).get("symbol")) && d(m(prs.get(0)).get("pe")) == 9.5 && String.valueOf(m(r.body).get("source")).contains("Yahoo Finance"));
        t("peers: a company with no listed competitors -> 404", get(p, "/api/peers?symbol=AAPL").code == 404);
        System.clearProperty("FINNHUB_API_KEY");
        String seoHtml = http(p, "/seo/stock/fndx");
        t("seo: stock page is real HTML with title, canonical, numbers and app link", seoHtml.contains("<title>Fund X (FNDX) Stock") && seoHtml.contains("rel=\"canonical\" href=\"https://quanteraorg.vercel.app/stock/FNDX\"") && seoHtml.contains("¥5.00T") && seoHtml.contains("/#/asset/FNDX") && seoHtml.contains("application/ld+json"));
        t("seo: headline numbers state their period", seoHtml.contains("Latest quarter, vs a year ago") && seoHtml.contains("Full-year figures"));
        t("seo: listed-rounds total only when the table sums to under half the reported total", Seo.listedRaised(100.0, List.of(Json.obj("raised", 30.0), Json.obj("raised", null))) == 30.0 && Seo.listedRaised(100.0, List.of(Json.obj("raised", 50.0))) == null && Math.abs(Seo.listedRaised(178.1, List.of(Json.obj("raised", 178.1), Json.obj("raised", 11.0))) - 189.1) < 1e-9 && Seo.listedRaised(100.0, List.of(Json.obj("raised", null))) == null && Seo.listedRaised(null, List.of(Json.obj("raised", 5.0))) == null);
        t("seo: unknown stock gets a noindex 404", http(p, "/seo/stock/NOFUND").contains("noindex") && !seoHtml.contains("noindex"));
        t("seo: page text is escaped", Seo.h("<script>'\"&").equals("&lt;script&gt;&#39;&quot;&amp;"));
        t("seo: readable private company slug", Seo.slug("Figure AI, Inc.").equals("figure-ai-inc"));
        String seoPvt = http(p, "/seo/private/anthropic"), seoMap = http(p, "/seo/sitemap.xml");
        t("seo: private company page has valuation and funding rounds", seoPvt.contains("Anthropic Valuation") && seoPvt.contains("$965.0B") && seoPvt.contains("Series H-1") && http(p, "/seo/private/no-such-co").contains("noindex"));
        t("seo: sitemap and directories list stock and private company URLs", seoMap.contains("/stock/AAPL</loc>") && !seoMap.contains("/stock/SPY</loc>") && seoMap.contains("/private-company/anthropic</loc>") && http(p, "/seo/stocks").contains("href=\"/stock/NVDA\"") && http(p, "/seo/private").contains("href=\"/private-company/openai\""));
        r = get(p, "/api/private");
        List<Object> pv = l(m(r.body).get("companies"));
        t("private companies: merged from Yahoo screeners, deduped, biggest valuation first, bad tickers dropped", r.code == 200 && pv.size() == 2 && "OPAI.PVT".equals(m(pv.get(0)).get("symbol"))
            && d(m(pv.get(1)).get("valuation")) == 9.65e11 && "2026-05-28".equals(m(pv.get(1)).get("lastRoundDate")) && "Series H-1".equals(m(pv.get(1)).get("lastRound")));
        r = get(p, "/api/private/company?symbol=anth.pvt");
        t("private company: funding rounds with post-money valuation, fund marks with SEC links only, profile", r.code == 200 && "Anthropic".equals(m(r.body).get("name"))
            && d(m(l(m(r.body).get("rounds")).get(0)).get("valuation")) == 964999970816.0 && "https://www.sec.gov/Archives/x".equals(m(l(m(r.body).get("marks")).get(0)).get("filing"))
            && m(l(m(r.body).get("marks")).get(1)).get("filing") == null && d(m(m(r.body).get("summary")).get("change52w")) == 263.25 && !l(m(r.body).get("history")).isEmpty());
        t("private company symbol validated", get(p, "/api/private/company?symbol=AAPL").code == 400);
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
        r = get(p, "/api/calendar?type=economic");
        List<Object> econ = r.code == 200 ? l(m(r.body).get("items")) : List.of();
        t("economic calendar: upcoming official release dates from FRED, sorted; past dates dropped; a release whose name doesn't match gets no explanation rather than a wrong one",
            econ.size() == 2 && "Renamed Release".equals(m(econ.get(0)).get("name")) && m(econ.get(0)).get("what") == null
            && "Consumer Price Index".equals(m(econ.get(1)).get("name")) && String.valueOf(m(econ.get(1)).get("what")).startsWith("Inflation") && String.valueOf(m(econ.get(1)).get("source")).contains("FRED"));
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
        r = get(p, "/api/filings?symbol=AAPL");
        t("filings: earnings dates from 8-K item 2.02, flagged after the New York close", l(m(r.body).get("earnings")).size() == 1 && Boolean.TRUE.equals(m(l(m(r.body).get("earnings")).get(0)).get("afterClose")));
        t("filings: companies that don't file with the SEC -> 404", get(p, "/api/filings?symbol=7203.T").code == 404);
        // ---- full financial statements
        r = get(p, "/api/statements?symbol=AAPL");
        Map<String, Object> sa = m(m(r.body).get("annual"));
        List<Object> sper = l(sa.get("periods"));
        Map<String, Object> srev = m(l(sa.get("income")).stream().filter(o -> "revenue".equals(m(o).get("key"))).findFirst().orElseThrow());
        Map<String, Object> sfcf = m(l(sa.get("cashflow")).stream().filter(o -> "freeCashFlow".equals(m(o).get("key"))).findFirst().orElseThrow());
        Map<String, Object> scap = m(l(sa.get("cashflow")).stream().filter(o -> "capex".equals(m(o).get("key"))).findFirst().orElseThrow());
        t("statements: fiscal-year columns, revenue per year, capital spending shown as money out, free cash flow derived with a note",
            r.code == 200 && sper.size() == 2 && "FY2024".equals(m(sper.get(1)).get("label")) && "2024-09-28".equals(m(sper.get(1)).get("end"))
            && d(l(srev.get("values")).get(0)) == 383e9 && d(l(srev.get("values")).get(1)) == 391e9 && d(l(scap.get("values")).get(1)) == -9.4e9
            && d(l(sfcf.get("values")).get(1)) == 118e9 - 9.4e9 && String.valueOf(sfcf.get("note")).contains("Cash from operations minus capital spending")
            && Boolean.FALSE.equals(m(r.body).get("financial")) && "SEC EDGAR (XBRL company facts)".equals(m(r.body).get("source")));
        Set<String> aiUsed = new LinkedHashSet<>();
        Map<String, Object> aiSt = m(Anthropic.runTool("get_financial_statements", Json.obj("symbol", "AAPL"), aiUsed));
        t("ai: the statements tool gives SEC figures labelled with their period and filing, and records the source for the answer",
            "SEC EDGAR filings".equals(aiSt.get("source")) && l(aiSt.get("periods")).size() == 2 && "FY2024".equals(m(l(aiSt.get("periods")).get(1)).get("period"))
            && d(l(m(aiSt.get("lines")).get("Revenue")).get(1)) == 391e9 && aiUsed.contains("SEC filings AAPL (annual)"));
        t("statements: lines a company doesn't report are left out, not shown as zero", l(sa.get("income")).stream().noneMatch(o -> "rnd".equals(m(o).get("key"))));
        t("statements: companies that don't file with the SEC -> 404", get(p, "/api/statements?symbol=7203.T").code == 404);
        Map<String, Object> stg = m(Json.parse("{\"Revenues\":{\"units\":{\"USD\":["
            + "{\"start\":\"2023-01-01\",\"end\":\"2023-12-31\",\"val\":100,\"frame\":\"CY2023\",\"form\":\"10-K\",\"filed\":\"2024-02-01\",\"accn\":\"0000000001-24-000001\"},"
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-12-31\",\"val\":120,\"frame\":\"CY2024\",\"form\":\"10-K\",\"filed\":\"2025-02-01\",\"accn\":\"0000000001-25-000001\"},"
            + "{\"start\":\"2024-10-01\",\"end\":\"2025-09-30\",\"val\":130,\"frame\":\"CY2025\",\"form\":\"10-Q\",\"filed\":\"2025-10-30\"}]}},"
            + "\"Liabilities\":{\"units\":{\"USD\":[{\"end\":\"2024-12-31\",\"val\":40,\"frame\":\"CY2024Q4I\",\"filed\":\"2025-02-01\"}]}},"
            + "\"LiabilitiesAndStockholdersEquity\":{\"units\":{\"USD\":[{\"end\":\"2023-12-31\",\"val\":90,\"frame\":\"CY2023Q4I\",\"filed\":\"2024-02-01\"}]}},"
            + "\"StockholdersEquity\":{\"units\":{\"USD\":[{\"end\":\"2023-12-31\",\"val\":60,\"frame\":\"CY2023Q4I\",\"filed\":\"2024-02-01\"},{\"end\":\"2024-12-30\",\"val\":70,\"frame\":\"CY2024Q4I\",\"filed\":\"2025-02-01\"}]}}}"));
        Map<String, Object> sb = Statements.build(stg, 1, false, false);
        Map<String, Object> sliab = m(l(sb.get("balance")).stream().filter(o -> "liabilities".equals(m(o).get("key"))).findFirst().orElseThrow());
        t("statements: a 12-month figure found only in a 10-Q (a trailing year) is not shown as a fiscal year", l(sb.get("periods")).size() == 2);
        t("statements: each column links to the filing it came from", "https://www.sec.gov/Archives/edgar/data/1/000000000125000001/0000000001-25-000001-index.htm".equals(m(l(sb.get("periods")).get(1)).get("filing")));
        Map<String, Object> rg = m(Json.parse("{\"Revenues\":{\"units\":{\"USD\":["
            + "{\"start\":\"2023-01-01\",\"end\":\"2023-12-31\",\"val\":100,\"frame\":\"CY2023\",\"form\":\"10-K\",\"filed\":\"2024-02-01\",\"accn\":\"0000000001-24-000001\"},"
            + "{\"start\":\"2023-01-01\",\"end\":\"2023-12-31\",\"val\":105,\"form\":\"10-K\",\"filed\":\"2025-02-01\",\"accn\":\"0000000001-25-000001\"},"
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-12-31\",\"val\":120,\"frame\":\"CY2024\",\"form\":\"10-K\",\"filed\":\"2025-02-01\",\"accn\":\"0000000001-25-000001\"}]}}}"));
        Map<String, Object> rb = Statements.build(rg, 1, false, false), rrev = m(l(rb.get("income")).stream().filter(o -> "revenue".equals(m(o).get("key"))).findFirst().orElseThrow());
        t("statements: each year links to its own annual report, not the newest one that repeats it; a restated figure still wins",
            String.valueOf(m(l(rb.get("periods")).get(0)).get("filing")).contains("0000000001-24-000001") && String.valueOf(m(l(rb.get("periods")).get(1)).get("filing")).contains("0000000001-25-000001") && d(l(rrev.get("values")).get(0)) == 105);
        t("statements: balances match the period end (within days); missing total liabilities derived from liabilities+equity minus equity, with a note",
            d(l(sliab.get("values")).get(0)) == 30 && d(l(sliab.get("values")).get(1)) == 40 && String.valueOf(sliab.get("note")).contains("minus equity"));
        long d19 = java.time.LocalDate.parse("2019-03-01").toEpochDay(), d22 = java.time.LocalDate.parse("2022-02-02").toEpochDay(), d23 = java.time.LocalDate.parse("2023-02-01").toEpochDay();
        List<double[]> splits = List.of(new double[]{20, java.time.LocalDate.parse("2022-02-01").toEpochDay(), java.time.LocalDate.parse("2022-07-15").toEpochDay()});
        double[] seps = {43.70, 112.20, 4.56}, sni = {30.7e9, 76.0e9, 60.0e9};
        Statements.splitAdjust(seps, null, sni, new long[]{d19, d22, d23}, splits);
        t("statements: EPS last filed before a split is adjusted; one filed between announcement and effect is adjusted only if its share count jumps by the ratio; restated ones untouched",
            seps[0] == 2.19 && seps[1] == 5.61 && seps[2] == 4.56);
        double[] keep = {5.0, 5.2};
        Statements.splitAdjust(keep, null, new double[]{5e9, 5.2e9}, new long[]{d22, d23}, splits);
        t("statements: a filing inside the split window without a matching share jump is left alone", keep[0] == 5.0);
        Map<String, Object> splitTags = m(Json.parse("{\"StockholdersEquityNoteStockSplitConversionRatio1\":{\"units\":{\"pure\":["
            + "{\"end\":\"2019-09-30\",\"val\":5,\"filed\":\"2020-10-26\"},{\"end\":\"2020-08-10\",\"val\":5,\"filed\":\"2020-10-26\"},{\"end\":\"2020-08-31\",\"val\":5,\"filed\":\"2021-02-08\"},{\"end\":\"2022-08-24\",\"val\":3,\"filed\":\"2022-10-24\"}]}}}"));
        List<double[]> ps = Statements.splits(splitTags);
        t("statements: split ratios read from the company's own tags; old comparative dates ignored",
            ps.size() == 2 && ps.get(0)[0] == 5 && ps.get(0)[1] == java.time.LocalDate.parse("2020-08-10").toEpochDay() && ps.get(1)[0] == 3);
        Map<String, Object> qg = m(Json.parse("{\"Revenues\":{\"units\":{\"USD\":["
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-12-31\",\"val\":100,\"frame\":\"CY2024\",\"form\":\"10-K\",\"filed\":\"2025-02-01\"},"
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-03-31\",\"val\":20,\"frame\":\"CY2024Q1\",\"form\":\"10-Q\",\"filed\":\"2024-05-01\"},"
            + "{\"start\":\"2024-04-01\",\"end\":\"2024-06-30\",\"val\":25,\"frame\":\"CY2024Q2\",\"form\":\"10-Q\",\"filed\":\"2024-08-01\"},"
            + "{\"start\":\"2024-07-01\",\"end\":\"2024-09-30\",\"val\":24,\"frame\":\"CY2024Q3\",\"form\":\"10-Q\",\"filed\":\"2024-11-01\"}]}},"
            + "\"EarningsPerShareDiluted\":{\"units\":{\"USD/shares\":[{\"start\":\"2024-01-01\",\"end\":\"2024-12-31\",\"val\":4,\"frame\":\"CY2024\",\"filed\":\"2025-02-01\"},"
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-03-31\",\"val\":1,\"frame\":\"CY2024Q1\",\"filed\":\"2024-05-01\"},{\"start\":\"2024-04-01\",\"end\":\"2024-06-30\",\"val\":1,\"frame\":\"CY2024Q2\",\"filed\":\"2024-08-01\"},{\"start\":\"2024-07-01\",\"end\":\"2024-09-30\",\"val\":1,\"frame\":\"CY2024Q3\",\"filed\":\"2024-11-01\"}]}}}"));
        Map<String, Object> qb = Statements.build(qg, 1, true, false);
        List<Object> qper = l(qb.get("periods"));
        Map<String, Object> qrev = m(l(qb.get("income")).stream().filter(o -> "revenue".equals(m(o).get("key"))).findFirst().orElseThrow());
        Map<String, Object> qeps = m(l(qb.get("income")).stream().filter(o -> "eps".equals(m(o).get("key"))).findFirst().orElseThrow());
        t("statements: the fourth quarter (only in the annual report) is the year minus three quarters, marked as worked out; per-share figures left blank",
            qper.size() == 4 && "Dec 2024".equals(m(qper.get(3)).get("label")) && m(qper.get(3)).get("derived") != null && m(qper.get(0)).get("derived") == null
            && d(l(qrev.get("values")).get(3)) == 31 && l(qeps.get("values")).get(3) == null);
        Map<String, Object> yg = m(Json.parse("{\"Revenues\":{\"units\":{\"USD\":["
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-03-31\",\"val\":50,\"frame\":\"CY2024Q1\",\"form\":\"10-Q\",\"filed\":\"2024-05-01\"},"
            + "{\"start\":\"2024-04-01\",\"end\":\"2024-06-30\",\"val\":55,\"frame\":\"CY2024Q2\",\"form\":\"10-Q\",\"filed\":\"2024-08-01\"}]}},"
            + "\"NetCashProvidedByUsedInOperatingActivities\":{\"units\":{\"USD\":["
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-03-31\",\"val\":10,\"form\":\"10-Q\",\"filed\":\"2024-05-01\"},"
            + "{\"start\":\"2024-01-01\",\"end\":\"2024-06-30\",\"val\":25,\"form\":\"10-Q\",\"filed\":\"2024-08-01\"}]}}}"));
        Map<String, Object> yb = Statements.build(yg, 1, true, false);
        Map<String, Object> yocf = m(l(yb.get("cashflow")).stream().filter(o -> "operatingCashFlow".equals(m(o).get("key"))).findFirst().orElseThrow());
        t("statements: quarterly cash flow from year-to-date totals (Q2 = first half minus Q1), with a note saying so",
            d(l(yocf.get("values")).get(0)) == 10 && d(l(yocf.get("values")).get(1)) == 15 && String.valueOf(yocf.get("note")).contains("year-to-date"));
        t("statements: SIC 6000-6799 (banks, insurers) flagged so misleading ratios are left out", Statements.financial(6021.0) && !Statements.financial(3571.0) && !Statements.financial(null));
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
        {
            String keysJ = Json.write(m(Json.parse(subJ)).get("keys"));
            boolean allBad = true;
            for (String ep : List.of("https://10.0.0.1/xxxxxxxxxx", "https://evil.example.com/xxxxxxxxxx", "https://fcm.googleapis.com.evil.com/xxxxxxxxxx", "https://127.0.0.1:8443/xxxxxxxxxx"))
                for (String path : List.of("/api/push/alerts", "/api/push/test"))
                    allBad &= call(p, "POST", path, "{\"subscription\":{\"endpoint\":\"" + ep + "\",\"keys\":" + keysJ + "},\"symbol\":\"AAPL\",\"op\":\">\",\"price\":1}", ip()).code == 400;
            t("push: endpoints that aren't a real push service (IP addresses, other sites, look-alikes) are rejected", allBad);
            t("push: the browsers' push services are accepted", Push.pushHost("https://fcm.googleapis.com/fcm/send/abcdefghijk") && Push.pushHost("https://updates.push.services.mozilla.com/wpush/v2/abcdefghijk")
                    && Push.pushHost("https://wns2-par02p.notify.windows.com/w/?token=abcdefghijk") && Push.pushHost("https://web.push.apple.com/abcdefghijk") && !Push.pushHost("https://notify.windows.com.evil.io/abcdefghijk"));
            t("push: the sender itself refuses a non-push-service address (drops it as gone)", Push.send("https://169.254.169.254/latest/meta-data", "x", "x", Json.obj()) == 410);
        }
        t("push: bad subscription rejected", call(p, "POST", "/api/push/alerts", "{\"subscription\":{\"endpoint\":\"javascript:x\"},\"symbol\":\"AAPL\",\"op\":\">\",\"price\":1}", ip()).code == 400);
        System.setProperty("ALERTS_HEARTBEAT_URL", base + "/hb");
        Push.check();
        Thread.sleep(400);
        t("alerts checker pings the heartbeat after a good run", heartbeats.get() >= 1 && Push.LAST_OK > 0);
        System.clearProperty("ALERTS_HEARTBEAT_URL");
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
