package com.quantera;

import com.quantera.Support.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

import static com.quantera.Support.*;

/** Data providers: Alpha Vantage (prices, fundamentals, calendars) and Marketaux (news). Output is normalised. */
final class Providers {
    private Providers() {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    static final Cache CACHE = new Cache();

    static boolean marketOn() { return cfg("ALPHAVANTAGE_API_KEY", null) != null || cfg("FINNHUB_API_KEY", null) != null; }
    static boolean newsOn() { return cfg("MARKETAUX_API_TOKEN", null) != null; }
    static boolean aiOn() { return cfg("ANTHROPIC_API_KEY", null) != null || cfg("GEMINI_API_KEY", null) != null; }

    /** After Alpha Vantage reports a rate limit, skip calling it until this time (calls made while blocked can still count against the quota). */
    static volatile long AV_BLOCKED_UNTIL = 0;
    static final String AV_LIMIT = "Live prices are refreshing. Please check back in a few minutes.";

    private static String avRaw(Map<String, String> p, int timeoutSec) {
        String key = cfg("ALPHAVANTAGE_API_KEY", null);
        if (key == null) throw new UpstreamException("Market data is not configured", 503);
        if (System.currentTimeMillis() < AV_BLOCKED_UNTIL) throw new UpstreamException(AV_LIMIT, 429);
        StringBuilder u = new StringBuilder(cfg("AV_BASE_URL", "https://www.alphavantage.co/query")).append("?");
        Map<String, String> all = new LinkedHashMap<>(p);
        all.put("apikey", key);
        for (var e : all.entrySet()) u.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)).append('=').append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8)).append('&');
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(u.toString())).timeout(Duration.ofSeconds(timeoutSec)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) throw new UpstreamException("Market data provider error");
            return r.body();
        } catch (UpstreamException e) { throw e;
        } catch (Exception e) { throw new UpstreamException("Market data provider error"); }
    }

    private static void checkAv(Map<String, Object> j) {
        if (j.containsKey("Note") || j.containsKey("Information")) {
            String info = String.valueOf(j.containsKey("Information") ? j.get("Information") : j.get("Note")).toLowerCase();
            // Free-tier limit messages also advertise "premium plans", so check for a rate limit first
            boolean limit = info.contains("rate limit") || info.contains("per day") || info.contains("per minute") || info.contains("call frequency");
            if (!limit && info.contains("premium")) throw new UpstreamException("This data needs a premium Alpha Vantage plan", 402);
            AV_BLOCKED_UNTIL = System.currentTimeMillis() + (info.contains("per day") ? 30 * 60_000L : 60_000L);
            throw new UpstreamException(AV_LIMIT, 429);
        }
        if (j.containsKey("Error Message")) {
            if (String.valueOf(j.get("Error Message")).toLowerCase().contains("apikey")) throw new UpstreamException("Market data is not configured correctly", 503);
            throw new UpstreamException("Symbol not found", 404);
        }
    }

    private static Map<String, Object> av(Map<String, String> p) {
        String body = avRaw(p, 10);
        Map<String, Object> j;
        try { j = map(Json.parse(body)); } catch (IllegalArgumentException e) { throw new UpstreamException("Market data provider error"); }
        checkAv(j);
        return j;
    }

    private static Map<String, String> q(String... kv) {
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put(kv[i], kv[i + 1]);
        return m;
    }

    static boolean finnhubOn() { return cfg("FINNHUB_API_KEY", null) != null; }

    /** Finnhub quote: 60 calls/minute on the free plan, no daily cap; returns the day's change directly. */
    /** After Finnhub / Twelve Data answer 429, everyone (warmers and visitors) backs off until these times instead of making the limit worse. */
    static volatile long FH_BLOCKED_UNTIL = 0, TD_BLOCKED_UNTIL = 0;
    static void fhLimited() { FH_BLOCKED_UNTIL = System.currentTimeMillis() + 30_000; LAST_ERR = java.time.Instant.now().toString().substring(0, 19) + " fh: rate limited (backing off 30s)"; }
    static void tdLimited() { TD_BLOCKED_UNTIL = System.currentTimeMillis() + 61_000; LAST_ERR = java.time.Instant.now().toString().substring(0, 19) + " td: rate limited (backing off 60s)"; }

    /** True during the US regular session (Mon-Fri 9:25-16:05 New York time). Outside it, quotes barely move, so warmers slow down. */
    static boolean usMarketOpen() {
        java.time.ZonedDateTime n = java.time.ZonedDateTime.now(java.time.ZoneId.of("America/New_York"));
        int d = n.getDayOfWeek().getValue(), m = n.getHour() * 60 + n.getMinute();
        return d <= 5 && m >= 9 * 60 + 25 && m <= 16 * 60 + 5;
    }

    private static Map<String, Object> finnhubQuote(String s) {
        if (System.currentTimeMillis() < FH_BLOCKED_UNTIL) {
            Map<String, Object> w = SNAP.get(s);
            if (w != null) return w; // serve the warm copy rather than another 429
            throw new UpstreamException(AV_LIMIT, 429);
        }
        String u = cfg("FINNHUB_BASE_URL", "https://finnhub.io/api/v1") + "/quote?symbol=" + URLEncoder.encode(s, StandardCharsets.UTF_8) + "&token=" + URLEncoder.encode(cfg("FINNHUB_API_KEY", ""), StandardCharsets.UTF_8);
        HttpResponse<String> r;
        try { r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        catch (Exception e) { throw new UpstreamException("Market data provider error"); }
        if (r.statusCode() == 429) { fhLimited(); throw new UpstreamException(AV_LIMIT, 429); }
        if (r.statusCode() == 401 || r.statusCode() == 403) throw new UpstreamException("Market data is not configured correctly", 503);
        if (r.statusCode() != 200) throw new UpstreamException("Market data provider error");
        Map<String, Object> j;
        try { j = map(Json.parse(r.body())); } catch (IllegalArgumentException e) { throw new UpstreamException("Market data provider error"); }
        Double c = num(j.get("c"));
        if (c == null || c == 0) throw new UpstreamException("Symbol not found", 404);
        Double t = num(j.get("t"));
        return Json.obj("symbol", s, "price", c, "change", num(j.get("d")), "changePct", num(j.get("dp")), "volume", null, "prevClose", num(j.get("pc")),
                "asOf", t == null ? null : java.time.Instant.ofEpochSecond(t.longValue()).toString().substring(0, 10), "source", "Finnhub");
    }

    static Map<String, Object> quote(String s, long ttlMs) {
        if (Yahoo.on() && Yahoo.isGlobal(s)) return Yahoo.quote(s); // indexes and non-US listings
        return CACHE.get("q" + s, ttlMs, () -> {
            if (finnhubOn()) {
                try { return finnhubQuote(s); }
                catch (UpstreamException e) {
                    if (Yahoo.on() && e.code != 429) try { return Yahoo.quote(s); } catch (UpstreamException ignored) {}
                    if (e.code == 404 || cfg("ALPHAVANTAGE_API_KEY", null) == null) throw e;
                } // fall back to Yahoo, then Alpha Vantage, on provider trouble
            }
            Map<String, Object> j = map(av(q("function", "GLOBAL_QUOTE", "symbol", s)).get("Global Quote"));
            if (num(j.get("05. price")) == null) throw new UpstreamException("Symbol not found", 404);
            return Json.obj("symbol", s, "price", num(j.get("05. price")), "change", num(j.get("09. change")), "changePct", num(j.get("10. change percent")),
                    "volume", num(j.get("06. volume")), "prevClose", num(j.get("08. previous close")), "asOf", str(j.get("07. latest trading day")), "source", "Alpha Vantage");
        });
    }

    static boolean twelveOn() { return cfg("TWELVEDATA_API_KEY", null) != null; }

    /** GET a JSON document from Finnhub ("fh") or Twelve Data ("td"); maps provider errors to friendly ones. */
    /** Last data-provider failure, shown in /api/health for diagnosis (never contains keys: URLs are not included). */
    static volatile String LAST_ERR = null;

    private static Object fail(String why) {
        why = why.replaceAll("(token|apikey|api_token)=[^&\s]+", "$1=***");
        LAST_ERR = java.time.Instant.now().toString().substring(0, 19) + " " + why;
        System.out.println("Provider error: " + why);
        throw new UpstreamException("Market data provider error");
    }

    static Object getJson(String who, String pathAndQuery) { return getJson(who, pathAndQuery, 10); }

    static Object getJson(String who, String pathAndQuery, int timeoutSec) {
        boolean fh = who.equals("fh");
        String base = fh ? cfg("FINNHUB_BASE_URL", "https://finnhub.io/api/v1") : cfg("TWELVEDATA_BASE_URL", "https://api.twelvedata.com");
        String key = URLEncoder.encode(cfg(fh ? "FINNHUB_API_KEY" : "TWELVEDATA_API_KEY", ""), StandardCharsets.UTF_8);
        String u = base + pathAndQuery + (pathAndQuery.contains("?") ? "&" : "?") + (fh ? "token=" : "apikey=") + key;
        String path = pathAndQuery.split("\\?")[0];
        if (System.currentTimeMillis() < (fh ? FH_BLOCKED_UNTIL : TD_BLOCKED_UNTIL)) throw new UpstreamException(AV_LIMIT, 429);
        HttpResponse<byte[]> r;
        String body;
        try {
            r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(timeoutSec)).header("Accept-Encoding", "gzip").GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] b = r.body();
            if (r.headers().firstValue("Content-Encoding").orElse("").contains("gzip")) b = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(b)).readAllBytes();
            body = new String(b, StandardCharsets.UTF_8);
        } catch (Exception e) { return fail(who + " " + path + ": " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " " + e.getMessage())); }
        if (r.statusCode() == 429) { if (fh) fhLimited(); else tdLimited(); throw new UpstreamException(AV_LIMIT, 429); }
        if (r.statusCode() == 401 || r.statusCode() == 403) { LAST_ERR = who + " " + path + ": HTTP " + r.statusCode() + " (not in plan)"; throw new UpstreamException("This data is not included in the current data plan", 402); }
        if (r.statusCode() != 200) return fail(who + " " + path + ": HTTP " + r.statusCode() + " " + body.substring(0, Math.min(120, body.length())));
        Object j;
        try { j = Json.parse(body); } catch (RuntimeException | StackOverflowError e) { return fail(who + " " + path + ": unreadable response (" + body.length() + " bytes)"); }
        if (!fh && "error".equals(map(j).get("status"))) { // Twelve Data reports errors in the body with HTTP 200
            Double code = num(map(j).get("code"));
            if (code != null && code == 429) tdLimited();
            throw code != null && code == 429 ? new UpstreamException(AV_LIMIT, 429) : code != null && code == 403 ? new UpstreamException("This data is not included in the current data plan", 402) : new UpstreamException("No price history for this symbol", 404);
        }
        return j;
    }

    /** Daily bars from Twelve Data (free: 800 calls/day), oldest first, in the same shape as before. */
    private static Map<String, Object> twelveHistory(String s, boolean full) {
        String sym = s.equals("BTC-USD") ? "BTC/USD" : s.equals("ETH-USD") ? "ETH/USD" : s;
        Map<String, Object> j = map(getJson("td", "/time_series?interval=1day&outputsize=" + (full ? 1300 : 130) + "&symbol=" + URLEncoder.encode(sym, StandardCharsets.UTF_8)));
        List<Object> vals = list(j.get("values"));
        if (vals.isEmpty()) throw new UpstreamException("No price history for this symbol", 404);
        List<Object> bars = new ArrayList<>();
        for (int i = vals.size() - 1; i >= 0; i--) {
            Map<String, Object> v = map(vals.get(i));
            bars.add(Json.obj("t", String.valueOf(v.get("datetime")).substring(0, 10), "o", num(v.get("open")), "h", num(v.get("high")), "l", num(v.get("low")), "c", num(v.get("close")), "v", num(v.get("volume"))));
        }
        return Json.obj("symbol", s, "bars", bars, "source", "Twelve Data", "adjusted", false);
    }

    static Map<String, Object> history(String s, boolean full) {
        return CACHE.get("h" + s + (full ? 1 : 0), 6 * 3600_000L, () -> {
            if (Yahoo.on() && (Yahoo.isGlobal(s) || (!twelveOn() && cfg("ALPHAVANTAGE_API_KEY", null) == null))) return Yahoo.history(s, full);
            if (twelveOn()) {
                try { return twelveHistory(s, full); }
                catch (UpstreamException e) {
                    if (Yahoo.on() && e.code != 429) try { return Yahoo.history(s, full); } catch (UpstreamException ignored) {} // symbols outside the Twelve Data plan
                    if (e.code == 404 || cfg("ALPHAVANTAGE_API_KEY", null) == null) throw e;
                }
            }
            Map<String, Object> j;
            try { j = av(q("function", "TIME_SERIES_DAILY", "symbol", s, "outputsize", full ? "full" : "compact")); }
            catch (UpstreamException e) {
                if (full && e.code == 402) j = av(q("function", "TIME_SERIES_DAILY", "symbol", s, "outputsize", "compact")); else throw e;
            }
            Map<String, Object> t = map(j.get("Time Series (Daily)"));
            if (t.isEmpty()) throw new UpstreamException("No price history for this symbol", 404);
            List<String> days = new ArrayList<>(t.keySet());
            Collections.sort(days);
            if (days.size() > 1300) days = days.subList(days.size() - 1300, days.size());
            List<Object> bars = new ArrayList<>();
            for (String d : days) {
                Map<String, Object> v = map(t.get(d));
                bars.add(Json.obj("t", d, "o", num(v.get("1. open")), "h", num(v.get("2. high")), "l", num(v.get("3. low")), "c", num(v.get("4. close")), "v", num(v.get("5. volume"))));
            }
            return Json.obj("symbol", s, "bars", bars, "source", "Alpha Vantage", "adjusted", false);
        });
    }

    /** Intraday intervals offered by the chart, with bars per regular session (390 minutes) for 20 sessions of history. */
    static final Map<String, Integer> INTRADAY = Map.of("5min", 78, "15min", 26, "1h", 7, "4h", 2);

    /** Real intraday bars from Twelve Data (regular hours, New York time), oldest first. Short cache: 1 min for 5/15 min bars, 5 min otherwise. */
    static Map<String, Object> intraday(String s, String interval) {
        Integer perDay = INTRADAY.get(interval);
        if (perDay == null) throw new UpstreamException("interval must be one of 5min, 15min, 1h, 4h", 400);
        if (Yahoo.on() && (Yahoo.isGlobal(s) || !twelveOn())) return CACHE.get("yi" + s + interval, 60_000L, () -> Yahoo.intraday(s, interval));
        if (!twelveOn()) throw new UpstreamException("Intraday charts need a Twelve Data key", 503);
        long ttl = interval.endsWith("min") ? (usMarketOpen() ? 60_000L : 30 * 60_000L) : (usMarketOpen() ? 5 * 60_000L : 60 * 60_000L);
        return CACHE.get("i" + s + interval, ttl, () -> {
            String sym = s.equals("BTC-USD") ? "BTC/USD" : s.equals("ETH-USD") ? "ETH/USD" : s;
            Map<String, Object> j = map(getJson("td", "/time_series?interval=" + interval + "&outputsize=" + (perDay * 20) + "&timezone=America/New_York&symbol=" + URLEncoder.encode(sym, StandardCharsets.UTF_8)));
            List<Object> vals = list(j.get("values"));
            if (vals.isEmpty()) throw new UpstreamException("No intraday data for this symbol", 404);
            List<Object> bars = new ArrayList<>();
            for (int i = vals.size() - 1; i >= 0; i--) {
                Map<String, Object> v = map(vals.get(i));
                String t = String.valueOf(v.get("datetime")).replace(' ', 'T');
                if (t.length() == 10) t += "T09:30:00";
                bars.add(Json.obj("t", t.length() > 19 ? t.substring(0, 19) : t, "o", num(v.get("open")), "h", num(v.get("high")), "l", num(v.get("low")), "c", num(v.get("close")), "v", num(v.get("volume"))));
            }
            return Json.obj("symbol", s, "interval", interval, "tz", "America/New_York", "perSession", perDay, "bars", bars, "source", "Twelve Data", "asOf", java.time.Instant.now().toString());
        });
    }

    /** Ranks directory rows for a query: exact ticker, ticker prefix, name word prefix, then name contains. */
    private static List<Object> searchDirectory(List<Map<String, Object>> dir, String text) {
        String q = text.trim().toLowerCase(), qu = text.trim().toUpperCase();
        List<Map<String, Object>> hits = new ArrayList<>();
        Map<Map<String, Object>, Integer> score = new IdentityHashMap<>();
        for (Map<String, Object> m : dir) {
            String sym = String.valueOf(m.get("symbol")), name = String.valueOf(m.get("name")).toLowerCase();
            int sc = sym.equals(qu) ? 0 : sym.startsWith(qu) ? 1 + sym.length() : (" " + name).contains(" " + q) ? 20 : name.contains(q) ? 40 : -1;
            if (sc < 0) continue;
            if (SECTOR.containsKey(sym)) sc -= 5; // well-known names first
            score.put(m, sc); hits.add(m);
        }
        hits.sort(Comparator.comparingInt((Map<String, Object> m) -> score.get(m)).thenComparing(m -> String.valueOf(m.get("symbol"))));
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> m : hits) {
            if (out.size() >= 10) break;
            String type = String.valueOf(m.get("type"));
            out.add(Json.obj("symbol", m.get("symbol"), "name", m.get("name"), "type", type.equals("Stock") ? "Equity" : type, "region", "United States", "currency", "USD", "exchange", m.get("exchange")));
        }
        return out;
    }

    static Map<String, Object> search(String text) {
        Map<String, Object> us;
        try { us = searchUs(text); }
        catch (UpstreamException e) { if (!Yahoo.on()) throw e; us = Json.obj("results", List.of(), "source", null); }
        if (!Yahoo.on()) return us;
        try { // add listings from every other country after the US matches
            List<Object> out = new ArrayList<>(list(us.get("results")));
            Set<String> seen = new HashSet<>();
            for (Object o : out) seen.add(String.valueOf(map(o).get("symbol")));
            List<Object> world = new ArrayList<>();
            for (Object o : Yahoo.search(text)) if (seen.add(String.valueOf(map(o).get("symbol"))) && Yahoo.isGlobal(String.valueOf(map(o).get("symbol")))) world.add(o);
            while (out.size() > 6 && out.size() + world.size() > 14) out.remove(out.size() - 1);
            out.addAll(world.subList(0, Math.min(world.size(), 14 - out.size())));
            return Json.obj("results", out, "source", (us.get("source") == null ? "provider" : us.get("source")) + "+yahoo");
        } catch (UpstreamException e) { return us; }
    }

    static Map<String, Object> searchUs(String text) {
        // The full US directory is already in memory once loaded: answer from it and spend no provider quota
        List<Map<String, Object>> dir = CACHE.peek("dir");
        if (dir != null && !dir.isEmpty()) return Json.obj("results", searchDirectory(dir, text), "source", "directory");
        return CACHE.get("s" + text.toLowerCase(), 3600_000L, () -> {
            List<Object> out = new ArrayList<>();
            if (finnhubOn()) {
                for (Object o : list(map(getJson("fh", "/search?exchange=US&q=" + URLEncoder.encode(text, StandardCharsets.UTF_8))).get("result"))) {
                    Map<String, Object> m = map(o);
                    String sym = str(m.get("symbol")), type = str(m.get("type"));
                    if (sym == null || !US_TICKER.matcher(sym).matches() || out.size() >= 10) continue;
                    out.add(Json.obj("symbol", sym, "name", str(m.get("description")), "type", "Common Stock".equals(type) ? "Equity" : "ETP".equals(type) ? "ETF" : type, "region", "United States", "currency", "USD"));
                }
                return Json.obj("results", out);
            }
            for (Object o : list(av(q("function", "SYMBOL_SEARCH", "keywords", text)).get("bestMatches"))) {
                if (out.size() >= 10) break;
                Map<String, Object> m = map(o);
                out.add(Json.obj("symbol", str(m.get("1. symbol")), "name", str(m.get("2. name")), "type", str(m.get("3. type")), "region", str(m.get("4. region")), "currency", str(m.get("8. currency"))));
            }
            return Json.obj("results", out);
        });
    }

    /** Company profile + key metrics from Finnhub, converted to the same units as before (dollars and fractions, not millions and percent). */
    private static Map<String, Object> finnhubOverview(String s) {
        String q = "?symbol=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
        Map<String, Object> p = map(getJson("fh", "/stock/profile2" + q)), mt = map(map(getJson("fh", "/stock/metric" + q + "&metric=all")).get("metric"));
        if (p.isEmpty() && mt.isEmpty()) throw new UpstreamException("No company data for this symbol", 404);
        java.util.function.Function<String, Double> pct = k -> { Double v = num(mt.get(k)); return v == null ? null : v / 100; };
        Double cap = num(p.get("marketCapitalization"));
        if (cap == null) cap = num(mt.get("marketCapitalization"));
        Double pe = num(mt.get("peTTM"));
        if (pe == null) pe = num(mt.get("peBasicExclExtraTTM"));
        return Json.obj("symbol", s, "name", str(p.get("name")) == null ? s : str(p.get("name")), "type", p.isEmpty() ? "ETF" : "Common Stock", "sector", str(p.get("finnhubIndustry")), "industry", str(p.get("finnhubIndustry")),
                "country", str(p.get("country")), "currency", str(p.get("currency")), "exchange", str(p.get("exchange")), "marketCap", cap == null ? null : cap * 1e6,
                "pe", pe, "beta", num(mt.get("beta")), "dividendYield", pct.apply("dividendYieldIndicatedAnnual"), "high52", num(mt.get("52WeekHigh")), "low52", num(mt.get("52WeekLow")),
                "revenueGrowthYoY", pct.apply("revenueGrowthTTMYoy"), "profitMargin", pct.apply("netProfitMarginTTM"), "roe", pct.apply("roeTTM"), "description", null,
                "logo", str(p.get("logo")), "website", str(p.get("weburl")), "source", "Finnhub");
    }

    static Map<String, Object> overview(String s) {
        return CACHE.get("o" + s, 24 * 3600_000L, () -> {
            if (Yahoo.on() && (Yahoo.isGlobal(s) || (!finnhubOn() && cfg("ALPHAVANTAGE_API_KEY", null) == null))) return Yahoo.overview(s);
            if (finnhubOn()) {
                try { return finnhubOverview(s); }
                catch (UpstreamException e) { if (Yahoo.on() && e.code == 404) return Yahoo.overview(s); throw e; }
            }
            Map<String, Object> j = av(q("function", "OVERVIEW", "symbol", s));
            if (str(j.get("Symbol")) == null) throw new UpstreamException("No company data for this symbol", 404);
            return Json.obj("symbol", str(j.get("Symbol")), "name", str(j.get("Name")), "type", str(j.get("AssetType")), "sector", str(j.get("Sector")), "industry", str(j.get("Industry")),
                    "country", str(j.get("Country")), "currency", str(j.get("Currency")), "exchange", str(j.get("Exchange")), "marketCap", num(j.get("MarketCapitalization")),
                    "pe", num(j.get("PERatio")), "beta", num(j.get("Beta")), "dividendYield", num(j.get("DividendYield")), "high52", num(j.get("52WeekHigh")), "low52", num(j.get("52WeekLow")),
                    "revenueGrowthYoY", num(j.get("QuarterlyRevenueGrowthYOY")), "profitMargin", num(j.get("ProfitMargin")), "roe", num(j.get("ReturnOnEquityTTM")), "description", str(j.get("Description")));
        });
    }

    /** Large US companies used for gainers/losers when quotes come from Finnhub (which has no movers endpoint on the free plan). */
    static final List<String> LARGE_CAPS = List.of("AAPL", "MSFT", "NVDA", "AMZN", "GOOGL", "META", "TSLA", "AVGO", "BRK.B", "JPM", "LLY", "V", "UNH", "XOM", "MA", "JNJ",
            "PG", "HD", "COST", "ABBV", "MRK", "ORCL", "CVX", "KO", "PEP", "BAC", "NFLX", "AMD", "CRM", "ADBE", "WMT", "MCD", "CSCO", "INTC", "DIS", "QCOM",
            "IBM", "GS", "CAT", "BA", "UBER", "PLTR", "NKE", "PFE", "MU", "WFC");

    /** Stocks and ETFs kept warm in the background (about 120), so the whole site can show live prices without spending quota per visitor. */
    static final List<String> UNIVERSE;
    static {
        Set<String> u = new LinkedHashSet<>(LARGE_CAPS);
        u.addAll(List.of("SPY", "QQQ", "DIA", "IWM", "VOO", "VTI", "SMH", "XLK", "XLF", "XLE", "XLV", "GLD", "TLT", "ARKK",
                "ASML", "TSM", "BABA", "ADP", "AMAT", "AMGN", "ANET", "APP", "ARM", "AXP", "BKNG", "BLK", "BMY", "BX", "C", "CMCSA", "COIN", "COP", "CRWD",
                "DE", "DELL", "ETN", "F", "GE", "GILD", "GM", "HON", "INTU", "ISRG", "KLAC", "LIN", "LMT", "LOW", "LRCX", "MDT", "MRVL", "MS", "NEE", "NOW",
                "PANW", "PYPL", "RTX", "SBUX", "SCHW", "SHOP", "SMCI", "SNOW", "SPGI", "T", "TGT", "TMO", "TXN", "UNP", "UPS", "VZ", "ABNB", "HOOD", "RIVN",
                "SOFI", "DDOG", "NET", "MSTR", "SPOT", "LSCC", "ONTO"));
        UNIVERSE = List.copyOf(u);
    }
    /** Sector for every warm symbol, so the site can group and filter the market by sector. */
    static final Map<String, String> SECTOR = new HashMap<>();
    static {
        String[][] g = {
            {"Technology", "AAPL MSFT ORCL CRM ADBE CSCO IBM PLTR INTU NOW PANW CRWD SNOW DDOG NET SHOP APP ADP DELL ANET"},
            {"Semiconductors", "NVDA AVGO AMD INTC QCOM MU TSM ASML AMAT KLAC LRCX MRVL TXN ARM SMCI LSCC ONTO"},
            {"Communication", "GOOGL META NFLX DIS CMCSA T VZ SPOT"},
            {"Consumer", "AMZN TSLA HD COST WMT MCD KO PEP PG NKE SBUX LOW TGT BKNG ABNB BABA F GM RIVN UBER"},
            {"Financials", "BRK.B JPM V MA BAC GS WFC MS C AXP BLK BX SCHW SPGI PYPL COIN HOOD SOFI MSTR"},
            {"Healthcare", "LLY UNH JNJ ABBV MRK PFE AMGN BMY GILD ISRG MDT TMO"},
            {"Energy", "XOM CVX COP NEE"},
            {"Industrials", "CAT BA GE HON LMT RTX DE ETN UNP UPS LIN"},
            {"ETFs", "SPY QQQ DIA IWM VOO VTI SMH XLK XLF XLE XLV GLD TLT ARKK"}};
        for (String[] r : g) for (String s : r[1].split(" ")) SECTOR.put(s, r[0]);
    }

    /** Index levels, tried through Finnhub's index symbols (may need a paid plan; callers fall back to the tracking ETF). */
    static final Map<String, String> INDEX_SYM = Map.of("SPX", "^GSPC", "NDX", "^NDX", "DJI", "^DJI", "RUT", "^RUT", "VIX", "^VIX");

    static Map<String, Object> indexQuote(String name) {
        return CACHE.get("ix" + name, 60_000L, () -> {
            Map<String, Object> q;
            if (Yahoo.on()) try { q = new LinkedHashMap<>(Yahoo.quote(INDEX_SYM.get(name))); q.put("symbol", name); return q; } catch (UpstreamException ignored) {}
            if (!finnhubOn() || CACHE.age("ixno" + name) < 6 * 3600_000L) throw new UpstreamException("Index levels are unavailable right now");
            try { q = new LinkedHashMap<>(finnhubQuote(INDEX_SYM.get(name))); }
            catch (UpstreamException e) { if (e.code == 404 || e.code == 402 || e.code == 503) CACHE.put("ixno" + name, true); throw e; } // remember "not in plan" so we stop spending 4 calls per refresh
            q.put("symbol", name);
            return q;
        });
    }

    /** Every US-listed stock and ETF on the major exchanges (Finnhub symbol list, refreshed daily), searchable and paged. */
    static List<Map<String, Object>> directoryAll() {
        return CACHE.get("dir", 24 * 3600_000L, () -> {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int ok = 0;
            // One exchange at a time keeps each download small; a failed exchange doesn't sink the rest
            for (String mic : List.of("XNAS", "XNYS", "ARCX", "BATS", "XASE")) {
                List<Object> rows;
                try { rows = list(getJson("fh", "/stock/symbol?exchange=US&mic=" + mic, 60)); ok++; } catch (UpstreamException e) { continue; }
                for (Object o : rows) {
                Map<String, Object> m = map(o);
                if (!seen.add(String.valueOf(m.get("symbol")))) continue;
                String sym = str(m.get("symbol")), type = str(m.get("type"));
                if (sym == null || !US_TICKER.matcher(sym).matches() || !("Common Stock".equals(type) || "ETP".equals(type) || "ADR".equals(type))) continue;
                out.add(Json.obj("symbol", sym, "name", str(m.get("description")), "type", "ETP".equals(type) ? "ETF" : "ADR".equals(type) ? "ADR" : "Stock", "sector", SECTOR.get(sym), "exchange", mic));
                }
            }
            if (ok == 0) throw new UpstreamException("The stock directory is unavailable right now");
            out.sort(Comparator.comparing(m -> (String) m.get("symbol")));
            return out;
        });
    }

    static Map<String, Object> directory(String q, String type, int page) {
        String ql = q.toLowerCase();
        List<Map<String, Object>> all = directoryAll(), hits = new ArrayList<>();
        for (Map<String, Object> m : all) {
            if (!type.isEmpty() && !type.equalsIgnoreCase(String.valueOf(m.get("type")))) continue;
            if (!ql.isEmpty() && !String.valueOf(m.get("symbol")).toLowerCase().startsWith(ql) && !String.valueOf(m.get("name")).toLowerCase().contains(ql)) continue;
            hits.add(m);
        }
        if (!ql.isEmpty()) hits.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("symbol")).equalsIgnoreCase(q) ? 0 : String.valueOf(m.get("symbol")).toLowerCase().startsWith(ql) ? 1 : 2).thenComparing(m -> (String) m.get("symbol")));
        int per = 25, from = Math.max(0, page - 1) * per;
        List<Object> items = new ArrayList<>();
        for (Map<String, Object> m : hits.subList(Math.min(from, hits.size()), Math.min(from + per, hits.size()))) {
            Map<String, Object> x = new LinkedHashMap<>(m), w = SNAP.get(String.valueOf(m.get("symbol")));
            if (w != null) { x.put("price", w.get("price")); x.put("changePct", w.get("changePct")); }
            items.add(x);
        }
        return Json.obj("total", hits.size(), "listed", all.size(), "page", Math.max(1, page), "perPage", per, "items", items);
    }

    static final Map<String, Map<String, Object>> SNAP = new java.util.concurrent.ConcurrentHashMap<>();
    static final Map<String, Long> SNAP_AT = new java.util.concurrent.ConcurrentHashMap<>();

    /** Symbols whose recent closes are kept warm for sparklines (indices, crypto and the large caps that feed the movers board). */
    static final List<String> SPARK_SET;
    static {
        List<String> l = new ArrayList<>(List.of("SPY", "QQQ", "DIA", "IWM", "BTC-USD", "ETH-USD"));
        l.addAll(LARGE_CAPS);
        SPARK_SET = List.copyOf(l);
    }

    /** Last ~30 daily closes for a symbol, from the history cache only (never calls upstream). */
    static List<Object> spark(String s) {
        Map<String, Object> h = CACHE.peek("h" + s + "0");
        if (h == null) h = CACHE.peek("h" + s + "1");
        if (h == null) return null;
        List<Object> bars = list(h.get("bars")), out = new ArrayList<>();
        for (int i = Math.max(0, bars.size() - 30); i < bars.size(); i++) out.add(map(bars.get(i)).get("c"));
        return out.isEmpty() ? null : out;
    }

    /**
     * Round-robin refreshers that keep upstream usage flat however many people visit.
     * Quotes: one Finnhub call every 1.5 s in session (~40/min of the 60/min free limit), every 6 s outside it (~10/min), none while backing off from a 429.
     * Sparklines: one Twelve Data daily series every 20 s (~3/min of the 8/min free limit), only for series older than 6 hours.
     */
    static void startWarmer() {
        if (!finnhubOn()) return;
        java.util.concurrent.atomic.AtomicInteger i = new java.util.concurrent.atomic.AtomicInteger(), tick = new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).scheduleWithFixedDelay(() -> {
            if (System.currentTimeMillis() < FH_BLOCKED_UNTIL) return;
            if (!usMarketOpen() && Math.floorMod(tick.getAndIncrement(), 4) != 0 && SNAP.size() >= UNIVERSE.size()) return;
            String s = UNIVERSE.get(Math.floorMod(i.getAndIncrement(), UNIVERSE.size()));
            try { Map<String, Object> q = finnhubQuote(s); SNAP.put(s, q); SNAP_AT.put(s, System.currentTimeMillis()); CACHE.put("q" + s, q); }
            catch (RuntimeException ignored) {}
        }, 0, 1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        System.out.println("Quote warmer on: " + UNIVERSE.size() + " symbols");
        if (twelveOn()) { // never on Alpha Vantage alone: its 25 calls/day would be gone in an hour
            java.util.concurrent.atomic.AtomicInteger k = new java.util.concurrent.atomic.AtomicInteger();
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).scheduleWithFixedDelay(() -> {
                if (System.currentTimeMillis() < TD_BLOCKED_UNTIL) return;
                for (int n = 0; n < SPARK_SET.size(); n++) { // next symbol that is missing or stale
                    String s = SPARK_SET.get(Math.floorMod(k.getAndIncrement(), SPARK_SET.size()));
                    if (CACHE.age("h" + s + "0") < 6 * 3600_000L) continue;
                    try { history(s, false); } catch (RuntimeException ignored) {}
                    return;
                }
            }, 3, Math.max(1, Integer.parseInt(cfg("SPARK_WARM_SEC", "20"))), java.util.concurrent.TimeUnit.SECONDS);
            System.out.println("Sparkline warmer on: " + SPARK_SET.size() + " symbols");
        }
        Thread.startVirtualThread(() -> { try { System.out.println("Directory loaded: " + directoryAll().size() + " US listings"); } catch (RuntimeException e) { System.out.println("Directory load failed: " + e.getMessage()); } });
    }

    /** Company name from the in-memory directory (null until it has loaded); no upstream calls. */
    static String nameOf(String sym) {
        List<Map<String, Object>> dir = CACHE.peek("dir");
        if (dir == null) return null;
        int lo = 0, hi = dir.size() - 1; // the directory is sorted by symbol
        while (lo <= hi) { int mid = (lo + hi) >>> 1; int c = String.valueOf(dir.get(mid).get("symbol")).compareTo(sym); if (c == 0) return str(dir.get(mid).get("name")); if (c < 0) lo = mid + 1; else hi = mid - 1; }
        return null;
    }

    /** Every warm quote, newest data first; no upstream calls. */
    static Map<String, Object> snapshot() {
        List<Object> qs = new ArrayList<>();
        for (String s : UNIVERSE) { Map<String, Object> q = SNAP.get(s); if (q != null) { Map<String, Object> x = new LinkedHashMap<>(q); x.put("sector", SECTOR.getOrDefault(s, "Other")); List<Object> sp = spark(s); if (sp != null) x.put("spark", sp); qs.add(x); } }
        return Json.obj("quotes", qs, "count", qs.size(), "universe", UNIVERSE.size(), "updated", java.time.Instant.now().toString());
    }

    private static Map<String, Object> finnhubMovers() {
        if (SNAP.size() >= 30) { // the warm snapshot covers far more names at no extra cost
            List<Map<String, Object>> qs = new ArrayList<>();
            for (Map<String, Object> q : SNAP.values()) if (num(q.get("changePct")) != null && !Set.of("SPY", "QQQ", "DIA", "IWM", "VOO", "VTI", "SMH", "XLK", "XLF", "XLE", "XLV", "GLD", "TLT", "ARKK").contains(String.valueOf(q.get("symbol")))) qs.add(q);
            return rank(qs, 25, "Largest US companies");
        }
        List<Map<String, Object>> qs = Collections.synchronizedList(new ArrayList<>());
        LARGE_CAPS.parallelStream().forEach(s -> { try { Map<String, Object> q = quote(s, 5 * 60_000L); if (num(q.get("changePct")) != null) qs.add(q); } catch (RuntimeException ignored) {} });
        return rank(qs, 10, "Largest US companies");
    }

    /** Gainers, losers and biggest moves (either direction) from a set of quotes. */
    static Map<String, Object> rank(List<Map<String, Object>> qs, int n, String label) {
        if (qs.isEmpty()) throw new UpstreamException("No market movers available", 404);
        List<Map<String, Object>> by = new ArrayList<>(qs);
        by.sort((a, b) -> Double.compare(num(b.get("changePct")), num(a.get("changePct"))));
        List<Map<String, Object>> abs = new ArrayList<>(qs);
        abs.sort((a, b) -> Double.compare(Math.abs(num(b.get("changePct"))), Math.abs(num(a.get("changePct")))));
        List<Map<String, Object>> losers = new ArrayList<>(by);
        Collections.reverse(losers);
        java.util.function.Function<List<Map<String, Object>>, List<Object>> top = l -> { List<Object> o = new ArrayList<>(); for (Map<String, Object> q : l) { if (o.size() >= n) break; String sy = String.valueOf(q.get("symbol")); o.add(Json.obj("symbol", sy, "name", nameOf(sy), "sector", SECTOR.get(sy), "price", q.get("price"), "change", q.get("change"), "changePct", q.get("changePct"), "volume", null, "spark", spark(sy))); } return o; };
        return Json.obj("updated", java.time.Instant.now().toString(), "universe", label, "count", qs.size(), "gainers", top.apply(by.stream().filter(q -> num(q.get("changePct")) > 0).toList()),
                "losers", top.apply(losers.stream().filter(q -> num(q.get("changePct")) < 0).toList()), "active", top.apply(abs));
    }

    static Map<String, Object> movers() {
        return CACHE.get("mv", SNAP.size() >= 30 ? 120_000L : 10 * 60_000L, () -> {
            if (finnhubOn()) return finnhubMovers();
            Map<String, Object> j = av(q("function", "TOP_GAINERS_LOSERS"));
            if (!j.containsKey("top_gainers")) throw new UpstreamException("No market movers available", 404);
            return Json.obj("updated", str(j.get("last_updated")), "gainers", moverList(j.get("top_gainers")), "losers", moverList(j.get("top_losers")), "active", moverList(j.get("most_actively_traded")));
        });
    }

    private static List<Object> moverList(Object o) {
        List<Object> out = new ArrayList<>();
        for (Object x : list(o)) {
            if (out.size() >= 15) break;
            Map<String, Object> m = map(x);
            out.add(Json.obj("symbol", str(m.get("ticker")), "price", num(m.get("price")), "change", num(m.get("change_amount")), "changePct", num(m.get("change_percentage")), "volume", num(m.get("volume"))));
        }
        return out;
    }

    static Map<String, Object> fx(String from, String to) {
        return CACHE.get("fx" + from + to, 5 * 60_000L, () -> {
            if (finnhubOn() && to.equals("USD")) {
                Map<String, Object> j = map(getJson("fh", "/quote?symbol=" + URLEncoder.encode("BINANCE:" + from + "USDT", StandardCharsets.UTF_8)));
                Double c = num(j.get("c")), t = num(j.get("t"));
                if (c != null && c > 0) return Json.obj("from", from, "to", to, "name", from.equals("BTC") ? "Bitcoin" : from.equals("ETH") ? "Ethereum" : from, "rate", c, "changePct", num(j.get("dp")),
                        "asOf", t == null ? null : java.time.Instant.ofEpochSecond(t.longValue()).toString());
            }
            Map<String, Object> j = map(av(q("function", "CURRENCY_EXCHANGE_RATE", "from_currency", from, "to_currency", to)).get("Realtime Currency Exchange Rate"));
            if (j.isEmpty()) throw new UpstreamException("No exchange rate available", 404);
            return Json.obj("from", from, "to", to, "name", str(j.get("2. From_Currency Name")), "rate", num(j.get("5. Exchange Rate")), "asOf", str(j.get("6. Last Refreshed")));
        });
    }

    static List<Object> calendar(String kind) {
        return CACHE.get("c" + kind, 12 * 3600_000L, () -> {
            boolean ipo = kind.equals("ipo");
            if (ipo && Yahoo.on()) try { List<Object> y = Yahoo.ipos(); if (!y.isEmpty()) return y; } catch (RuntimeException ignored) {}
            if (finnhubOn()) {
                java.time.LocalDate d0 = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
                List<Object> raw = new ArrayList<>();
                if (ipo) raw.addAll(list(map(getJson("fh", "/calendar/ipo?from=" + d0.minusDays(60) + "&to=" + d0.plusDays(90))).get("ipoCalendar")));
                else for (int w = 0; w < 2; w++) // week by week: one long range can be cut off before the near dates
                    raw.addAll(list(map(getJson("fh", "/calendar/earnings?from=" + d0.plusDays(w * 7L) + "&to=" + d0.plusDays(w * 7L + 6))).get("earningsCalendar")));
                Map<String, String> names = new HashMap<>();
                for (String[] n : NAMES) names.putIfAbsent(n[1], n[0]);
                List<Map<String, Object>> items = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                for (Object o : raw) {
                    Map<String, Object> m = map(o);
                    String sym = str(m.get("symbol")), date = str(m.get("date"));
                    if (sym == null || sym.isBlank() || date == null || (!ipo && !US_TICKER.matcher(sym).matches()) || !seen.add(sym + date)) continue;
                    if (ipo) {
                        String[] pr = String.valueOf(m.getOrDefault("price", "")).split("-");
                        items.add(Json.obj("symbol", sym, "name", str(m.get("name")), "date", date, "low", num(pr[0]), "high", num(pr[pr.length - 1]), "exchange", str(m.get("exchange"))));
                    } else items.add(Json.obj("symbol", sym, "name", names.get(sym), "date", date, "estimate", num(m.get("epsEstimate")), "currency", "USD"));
                }
                // By date; within a day, well-known large companies first
                items.sort(Comparator.comparing((Map<String, Object> m) -> (String) m.get("date")).thenComparing(m -> m.get("name") == null ? 1 : 0).thenComparing(m -> (String) m.get("symbol")));
                return new ArrayList<Object>(items.subList(0, Math.min(1500, items.size())));
            }
            String text = avRaw(ipo ? q("function", "IPO_CALENDAR") : q("function", "EARNINGS_CALENDAR", "horizon", "3month"), 15).trim();
            if (text.startsWith("{")) {
                try { checkAv(map(Json.parse(text))); } catch (IllegalArgumentException ignored) {}
                throw new UpstreamException("Market data provider error");
            }
            List<List<String>> rows = parseCsv(text);
            if (rows.isEmpty()) return new ArrayList<>();
            List<String> h = rows.get(0);
            List<Map<String, Object>> items = new ArrayList<>();
            for (List<String> r : rows.subList(1, rows.size())) {
                String sym = cell(r, h, "symbol"), date = cell(r, h, ipo ? "ipoDate" : "reportDate");
                if (sym == null || date == null) continue;
                items.add(ipo ? Json.obj("symbol", sym, "name", cell(r, h, "name"), "date", date, "low", num(cell(r, h, "priceRangeLow")), "high", num(cell(r, h, "priceRangeHigh")), "exchange", cell(r, h, "exchange"))
                        : Json.obj("symbol", sym, "name", cell(r, h, "name"), "date", date, "estimate", num(cell(r, h, "estimate")), "currency", cell(r, h, "currency")));
            }
            items.sort(Comparator.comparing(m -> (String) m.get("date")));
            return new ArrayList<Object>(items.subList(0, Math.min(1500, items.size())));
        });
    }

    private static String cell(List<String> r, List<String> h, String name) {
        int i = h.indexOf(name);
        if (i < 0 || i >= r.size()) return null;
        String v = r.get(i);
        return v == null || v.isEmpty() ? null : v;
    }

    /** CSV parser that handles quoted fields, escaped quotes and commas inside quotes. */
    static List<List<String>> parseCsv(String t) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder f = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (quoted) {
                if (c == '"') { if (i + 1 < t.length() && t.charAt(i + 1) == '"') { f.append('"'); i++; } else quoted = false; }
                else f.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(f.toString()); f.setLength(0); }
            else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < t.length() && t.charAt(i + 1) == '\n') i++;
                row.add(f.toString()); f.setLength(0);
                if (row.size() > 1 || !row.get(0).isEmpty()) rows.add(row);
                row = new ArrayList<>();
            } else f.append(c);
        }
        if (f.length() > 0 || !row.isEmpty()) { row.add(f.toString()); rows.add(row); }
        return rows;
    }

    private static HttpResponse<String> mx(String path) {
        String key = cfg("MARKETAUX_API_TOKEN", null);
        if (key == null) throw new UpstreamException("News is not configured", 503);
        String u = cfg("MARKETAUX_BASE_URL", "https://api.marketaux.com/v1") + path + (path.contains("?") ? "&" : "?") + "api_token=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        HttpResponse<String> r;
        try { r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
        catch (Exception e) { throw new UpstreamException("News provider error"); }
        if (r.statusCode() == 402 || r.statusCode() == 429) throw new UpstreamException("News rate limit reached. Try again later.", 429);
        if (r.statusCode() == 404) throw new UpstreamException("Story not found", 404);
        if (r.statusCode() != 200) throw new UpstreamException("News provider error");
        return r;
    }

    static String plain(Object html) { return html == null ? null : String.valueOf(html).replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").trim(); }

    /** One story with the passages that mention each company, plus related stories. */
    static Map<String, Object> article(String id) {
        return CACHE.get("a" + id, 3600_000L, () -> {
            Map<String, Object> a;
            try { a = map(Json.parse(mx("/news/uuid/" + id).body())); } catch (IllegalArgumentException e) { throw new UpstreamException("News provider error"); }
            if (str(a.get("uuid")) == null) throw new UpstreamException("Story not found", 404);
            List<Object> ents = new ArrayList<>();
            for (Object e : list(a.get("entities"))) {
                Map<String, Object> en = map(e);
                if (str(en.get("symbol")) == null || ents.size() >= 8) continue;
                List<Object> hl = new ArrayList<>();
                for (Object h : list(en.get("highlights"))) { Map<String, Object> x = map(h); if (hl.size() < 3) hl.add(Json.obj("text", plain(x.get("highlight")), "sentiment", num(x.get("sentiment")))); }
                ents.add(Json.obj("symbol", str(en.get("symbol")), "name", str(en.get("name")), "type", str(en.get("type")), "industry", str(en.get("industry")), "sentiment", num(en.get("sentiment_score")), "highlights", hl));
            }
            List<Object> similar = new ArrayList<>();
            try {
                for (Object o : list(map(Json.parse(mx("/news/similar/" + id + "?language=en&limit=3").body())).get("data"))) {
                    Map<String, Object> s = map(o);
                    similar.add(Json.obj("id", str(s.get("uuid")), "title", str(s.get("title")), "source", str(s.get("source")), "publishedAt", str(s.get("published_at")), "url", str(s.get("url"))));
                }
            } catch (RuntimeException ignored) {} // related stories are optional
            return Json.obj("id", str(a.get("uuid")), "title", str(a.get("title")), "description", plain(a.get("description")), "snippet", plain(a.get("snippet")), "url", str(a.get("url")), "image", str(a.get("image_url")), "source", str(a.get("source")), "publishedAt", str(a.get("published_at")), "keywords", str(a.get("keywords")), "entities", ents, "similar", similar);
        });
    }

    /** Major US financial outlets, preferred for the default (no search) feed. */
    static final String TOP_US_SOURCES = "reuters.com,bloomberg.com,cnbc.com,wsj.com,marketwatch.com,finance.yahoo.com,barrons.com,ft.com,apnews.com,investors.com,businessinsider.com,forbes.com,nasdaq.com,fool.com";
    static final java.util.regex.Pattern US_TICKER = java.util.regex.Pattern.compile("^[A-Z]{1,5}(\\.[A-Z])?$");

    private static List<Object> newsFetch(String query) {
        HttpResponse<String> r = mx("/news/all?" + query);
        List<Object> out = new ArrayList<>();
        try {
            for (Object o : list(map(Json.parse(r.body())).get("data"))) {
                Map<String, Object> a = map(o);
                List<Object> us = new ArrayList<>(), other = new ArrayList<>();
                for (Object e : list(a.get("entities"))) {
                    Map<String, Object> en = map(e);
                    String sym = str(en.get("symbol"));
                    if (sym == null) continue;
                    Map<String, Object> x = Json.obj("symbol", sym, "name", str(en.get("name")), "sentiment", num(en.get("sentiment_score")));
                    // US listings first (plain tickers like AAPL or BRK.B); foreign listings (DIA.AS, RELIANCE.NS) after
                    (US_TICKER.matcher(sym).matches() && (en.get("country") == null || "us".equalsIgnoreCase(str(en.get("country")))) ? us : other).add(x);
                }
                us.addAll(other);
                String snippet = str(a.get("snippet")) != null ? str(a.get("snippet")) : str(a.get("description"));
                out.add(Json.obj("id", str(a.get("uuid")), "title", str(a.get("title")), "summary", snippet == null ? "" : snippet, "url", str(a.get("url")), "source", str(a.get("source")), "publishedAt", str(a.get("published_at")), "symbols", us.subList(0, Math.min(us.size(), 6))));
            }
        } catch (IllegalArgumentException e) { throw new UpstreamException("News provider error"); }
        return out;
    }

    /** Company names (case-sensitive, whole words) used to tag Finnhub headlines with the stocks they mention. */
    static final String[][] NAMES = {{"Apple", "AAPL"}, {"Microsoft", "MSFT"}, {"Nvidia", "NVDA"}, {"NVIDIA", "NVDA"}, {"Amazon", "AMZN"}, {"Alphabet", "GOOGL"}, {"Google", "GOOGL"}, {"Meta", "META"},
            {"Facebook", "META"}, {"Tesla", "TSLA"}, {"Broadcom", "AVGO"}, {"Berkshire", "BRK.B"}, {"JPMorgan", "JPM"}, {"Eli Lilly", "LLY"}, {"UnitedHealth", "UNH"}, {"Exxon", "XOM"},
            {"Mastercard", "MA"}, {"Johnson & Johnson", "JNJ"}, {"Procter", "PG"}, {"Home Depot", "HD"}, {"Costco", "COST"}, {"AbbVie", "ABBV"}, {"Merck", "MRK"}, {"Oracle", "ORCL"},
            {"Chevron", "CVX"}, {"Coca-Cola", "KO"}, {"PepsiCo", "PEP"}, {"Bank of America", "BAC"}, {"Netflix", "NFLX"}, {"AMD", "AMD"}, {"Salesforce", "CRM"}, {"Adobe", "ADBE"},
            {"Walmart", "WMT"}, {"McDonald's", "MCD"}, {"Cisco", "CSCO"}, {"Intel", "INTC"}, {"Disney", "DIS"}, {"Qualcomm", "QCOM"}, {"IBM", "IBM"}, {"Goldman", "GS"},
            {"Caterpillar", "CAT"}, {"Boeing", "BA"}, {"Uber", "UBER"}, {"Palantir", "PLTR"}, {"Nike", "NKE"}, {"Pfizer", "PFE"}, {"Starbucks", "SBUX"}, {"Micron", "MU"},
            {"Wells Fargo", "WFC"}, {"Morgan Stanley", "MS"}, {"Citigroup", "C"}, {"Verizon", "VZ"}, {"Coinbase", "COIN"}, {"Shopify", "SHOP"}, {"Super Micro", "SMCI"},
            {"TSMC", "TSM"}, {"Taiwan Semiconductor", "TSM"}, {"Alibaba", "BABA"}, {"Spotify", "SPOT"}, {"Airbnb", "ABNB"}, {"PayPal", "PYPL"}, {"Robinhood", "HOOD"},
            {"Rivian", "RIVN"}, {"Dell", "DELL"}, {"Applied Materials", "AMAT"}, {"Lockheed", "LMT"}, {"FedEx", "FDX"}, {"Ford", "F"}, {"General Motors", "GM"},
            {"S&P 500", "SPY"}, {"Nasdaq", "QQQ"}, {"Dow Jones", "DIA"}};
    static final java.util.regex.Pattern EXCH = java.util.regex.Pattern.compile("\\((?:NASDAQ|NYSE|Nasdaq|NYSE American)\\s*:\\s*([A-Z]{1,5}(?:\\.[A-Z])?)\\)");

    static List<String> tag(String text, String related) {
        Set<String> out = new LinkedHashSet<>();
        if (related != null) for (String r : related.split(",")) { r = r.trim().toUpperCase(); if (US_TICKER.matcher(r).matches()) out.add(r); }
        java.util.regex.Matcher m = EXCH.matcher(text);
        while (m.find()) out.add(m.group(1));
        for (String[] n : NAMES) if (java.util.regex.Pattern.compile("(?<![A-Za-z])" + java.util.regex.Pattern.quote(n[0]) + "(?![a-z])").matcher(text).find()) out.add(n[1]);
        return new ArrayList<>(out).subList(0, Math.min(out.size(), 5));
    }

    /** Finnhub news: general market headlines, or one company's news (free for US listings). */
    private static List<Object> finnhubNews(String symbol) { return finnhubNews(symbol, "general"); }

    private static List<Object> finnhubNews(String symbol, String category) {
        String base = cfg("FINNHUB_BASE_URL", "https://finnhub.io/api/v1"), tok = URLEncoder.encode(cfg("FINNHUB_API_KEY", ""), StandardCharsets.UTF_8);
        java.time.LocalDate today = java.time.LocalDate.now(java.time.ZoneOffset.UTC);
        String u = symbol == null ? base + "/news?category=" + category + "&token=" + tok : base + "/company-news?symbol=" + URLEncoder.encode(symbol, StandardCharsets.UTF_8) + "&from=" + today.minusDays(7) + "&to=" + today + "&token=" + tok;
        List<Object> out = new ArrayList<>();
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(8)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) return out;
            for (Object o : list(Json.parse(r.body()))) {
                Map<String, Object> a = map(o);
                String title = str(a.get("headline")), url = str(a.get("url"));
                if (title == null || title.isBlank() || url == null || out.size() >= 100) continue;
                String sum = str(a.get("summary")) == null ? "" : str(a.get("summary"));
                List<String> tags = tag(title + " " + sum, str(a.get("related")));
                if (symbol != null && !tags.contains(symbol)) { tags = new ArrayList<>(tags); tags.add(0, symbol); }
                List<Object> syms = new ArrayList<>();
                for (String t : tags.subList(0, Math.min(tags.size(), 5))) syms.add(Json.obj("symbol", t, "name", null, "sentiment", null));
                Double t = num(a.get("datetime"));
                Double nid = num(a.get("id"));
                out.add(Json.obj("id", "fh" + (nid == null ? Math.abs(url.hashCode()) : nid.longValue()), "title", title, "summary", sum.length() > 400 ? sum.substring(0, 400) + "…" : sum, "url", url,
                        "source", str(a.get("source")), "image", str(a.get("image")), "publishedAt", t == null ? null : java.time.Instant.ofEpochSecond(t.longValue()).toString(), "symbols", syms));
            }
        } catch (Exception ignored) {} // extra coverage only; never fail the feed because of it
        return out;
    }

    /** Merge feeds newest first, dropping duplicate headlines. */
    static List<Object> merge(List<Object> a, List<Object> b, int limit) {
        Map<String, Object> seen = new LinkedHashMap<>();
        List<Object> all = new ArrayList<>(a);
        all.addAll(b);
        all.sort((x, y) -> String.valueOf(map(y).get("publishedAt")).compareTo(String.valueOf(map(x).get("publishedAt"))));
        for (Object o : all) { String k = String.valueOf(map(o).get("title")).toLowerCase().replaceAll("[^a-z0-9]", ""); if (!seen.containsKey(k)) seen.put(k, o); }
        List<Object> out = new ArrayList<>(seen.values());
        return out.subList(0, Math.min(out.size(), limit));
    }

    static List<Object> news(String symbols, String text, int limit) {
        int lim = Math.min(Math.max(limit, 1), 300);
        List<Object> base = CACHE.get("n" + symbols + "|" + text + "|" + lim, 60_000L, () -> {
            String q = "language=en&filter_entities=true&limit=" + lim;
            List<Object> mx = new ArrayList<>();
            RuntimeException err = null;
            final String q0 = q;
            try {
                mx = CACHE.get("nmx" + symbols + "|" + text, 20 * 60_000L, () -> { // Marketaux's free plan allows 100 calls a day
                    String qq = q0;
                    if (!symbols.isEmpty()) return newsFetch(qq + "&symbols=" + URLEncoder.encode(symbols, StandardCharsets.UTF_8));
                    qq += "&countries=us&must_have_entities=true";
                    if (!text.isEmpty()) return newsFetch(qq + "&search=" + URLEncoder.encode(text, StandardCharsets.UTF_8));
                    List<Object> top = newsFetch(qq + "&domains=" + TOP_US_SOURCES); // important US news from major outlets first
                    return top.isEmpty() ? newsFetch(qq) : top;
                });
            } catch (RuntimeException e) { err = e; }
            // More volume: Finnhub's market and company feeds, and Yahoo Finance news (also covers non-US companies and topics)
            List<Object> more = new ArrayList<>();
            String first = symbols.isEmpty() ? null : symbols.split(",")[0];
            if (finnhubOn() && text.isEmpty() && (first == null || (!symbols.contains(",") && !Yahoo.isGlobal(first)))) {
                more.addAll(finnhubNews(first));
                if (first == null) more.addAll(finnhubNews(null, "merger"));
            }
            if (Yahoo.on()) {
                List<String> qs = !text.isEmpty() ? List.of(text) : first != null ? Arrays.asList(symbols.split(",")).subList(0, Math.min(3, symbols.split(",").length))
                        : List.of("stock market", "Wall Street", "economy", "earnings", "Federal Reserve", "inflation", "oil prices", "gold price", "technology stocks", "AI stocks", "bank stocks", "IPO", "mergers acquisitions", "crypto", "European stocks", "Asian markets", "China economy", "trade tariffs");
                for (String q2 : qs) try { more.addAll(Yahoo.news(q2)); } catch (RuntimeException ignored) {}
            }
            if (mx.isEmpty() && more.isEmpty() && err != null) throw err;
            return merge(mx, more, lim);
        });
        return withMoves(base);
    }

    /** Adds today's price move to each tagged US stock ("AAPL +0.25%"). Only with Finnhub, so news never spends the small Alpha Vantage quota. */
    static List<Object> withMoves(List<Object> articles) {
        if (!finnhubOn()) return articles;
        Set<String> want = new LinkedHashSet<>();
        for (Object a : articles) for (Object s : list(map(a).get("symbols"))) { String sym = str(map(s).get("symbol")); if (sym != null && US_TICKER.matcher(sym).matches() && want.size() < 20) want.add(sym); }
        Map<String, Map<String, Object>> q = new java.util.concurrent.ConcurrentHashMap<>();
        want.parallelStream().forEach(s -> { try { q.put(s, quote(s, 5 * 60_000L)); } catch (RuntimeException ignored) {} });
        List<Object> out = new ArrayList<>();
        for (Object a : articles) {
            Map<String, Object> copy = new LinkedHashMap<>(map(a));
            List<Object> syms = new ArrayList<>();
            for (Object s : list(copy.get("symbols"))) {
                Map<String, Object> x = new LinkedHashMap<>(map(s)), qq = q.get(str(x.get("symbol")));
                if (qq != null) { x.put("price", qq.get("price")); x.put("changePct", qq.get("changePct")); }
                syms.add(x);
            }
            copy.put("symbols", syms);
            out.add(copy);
        }
        return out;
    }
}
