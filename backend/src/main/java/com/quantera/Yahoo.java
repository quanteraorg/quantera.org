package com.quantera;

import com.quantera.Support.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Global markets through Yahoo Finance's public JSON endpoints (no key): index levels, every country's stocks and ETFs,
 * quotes, daily and intraday charts, company profiles and worldwide search. Prices are delayed (usually 15 minutes outside
 * the US). Unofficial: no licence for commercial use and Yahoo may throttle or change it, so every caller has a fallback.
 * YAHOO_ENABLED=0 turns it off; YAHOO_BASE_URL / YAHOO_COOKIE_URL point it elsewhere (tests).
 */
final class Yahoo {
    private Yahoo() {}

    static boolean on() { return !"0".equals(cfg("YAHOO_ENABLED", "1")); }
    private static String base() { return cfg("YAHOO_BASE_URL", "https://query1.finance.yahoo.com"); }
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36";
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).followRedirects(HttpClient.Redirect.NORMAL).build();
    static final Cache CACHE = Providers.CACHE;
    static volatile String CRUMB;
    static volatile long CRUMB_AT, BLOCKED_UNTIL;
    static volatile String LAST_ERR;

    /** Yahoo wants a session cookie plus a matching "crumb" for quote, screener and profile calls. Refreshed every 6 hours or on a 401. */
    static volatile long CRUMB_FAIL_UNTIL;

    private static HttpResponse<String> plainGet(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).header("User-Agent", UA)
                .header("Accept", "text/html,application/json,*/*").header("Accept-Language", "en-US,en;q=0.9").GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static boolean goodCrumb(HttpResponse<String> r) {
        String c = r.body() == null ? "" : r.body().trim();
        return r.statusCode() == 200 && !c.isEmpty() && c.length() <= 40 && !c.startsWith("{") && !c.startsWith("<");
    }

    /**
     * Yahoo wants a session cookie plus a matching "crumb" for its quote, screener and profile calls (refreshed every 6 hours or on a 401).
     * Some server networks are refused, so several ways are tried; after a refusal callers use the crumb-free endpoints for 15 minutes.
     */
    static synchronized String crumb(boolean refresh) {
        if (!refresh && CRUMB != null && System.currentTimeMillis() - CRUMB_AT < 6 * 3600_000L) return CRUMB;
        if (System.currentTimeMillis() < CRUMB_FAIL_UNTIL) throw new UpstreamException("Global market data is unavailable right now");
        StringBuilder why = new StringBuilder();
        String q2 = base().replace("query1.", "query2.");
        String[][] steps = {{cfg("YAHOO_COOKIE_URL", "https://fc.yahoo.com"), base() + "/v1/test/getcrumb"}, {null, q2 + "/v1/test/getcrumb"},
                {cfg("YAHOO_PAGE_URL", "https://finance.yahoo.com/quote/%5EGSPC/"), base() + "/v1/test/getcrumb"}};
        for (String[] st : steps) {
            try {
                if (st[0] != null) {
                    HttpResponse<String> p = plainGet(st[0]); // sets the session cookie (fc.yahoo.com answers 404 but still sets it)
                    why.append(st[0].contains("fc.") ? "fc=" : "page=").append(p.statusCode()).append(' ');
                    java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"crumb\":\"([^\"]{4,40})\"").matcher(p.body() == null ? "" : p.body());
                    if (m.find()) { CRUMB = m.group(1).replace("\\u002F", "/"); CRUMB_AT = System.currentTimeMillis(); return CRUMB; }
                }
                HttpResponse<String> r = plainGet(st[1]);
                if (goodCrumb(r)) { CRUMB = r.body().trim(); CRUMB_AT = System.currentTimeMillis(); return CRUMB; }
                String b = r.body() == null ? "" : r.body().replaceAll("\\s+", " ");
                why.append(st[1].contains("query2") ? "q2=" : "q1=").append(r.statusCode()).append(b.isEmpty() ? "" : ":" + b.substring(0, Math.min(40, b.length()))).append(' ');
            } catch (Exception e) { why.append(e.getClass().getSimpleName()).append(' '); }
        }
        CRUMB_FAIL_UNTIL = System.currentTimeMillis() + 15 * 60_000L;
        LAST_ERR = "yahoo crumb refused (" + why.toString().trim() + "); using crumb-free endpoints";
        throw new UpstreamException("Global market data is unavailable right now");
    }

    /** GET (body == null) or POST JSON to Yahoo, adding the crumb when needed and retrying once with a fresh one. */
    static Map<String, Object> call(String path, String body, boolean needCrumb) {
        if (!on()) throw new UpstreamException("Global market data is turned off", 503);
        if (System.currentTimeMillis() < BLOCKED_UNTIL) throw new UpstreamException(Providers.AV_LIMIT, 429);
        for (int attempt = 0; attempt < 2; attempt++) {
            String u = base() + path;
            if (needCrumb) u += (u.contains("?") ? "&" : "?") + "crumb=" + URLEncoder.encode(crumb(attempt > 0), StandardCharsets.UTF_8);
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(10)).header("User-Agent", UA).header("Accept", "application/json");
            if (body != null) b.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)); else b.GET();
            HttpResponse<String> r;
            try { r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString()); }
            catch (Exception e) { LAST_ERR = "yahoo " + path.split("\\?")[0] + ": " + e.getClass().getSimpleName(); throw new UpstreamException("Global market data is unavailable right now"); }
            int sc = r.statusCode();
            if ((sc == 401 || sc == 403) && needCrumb && attempt == 0) continue;
            if (sc == 429) { BLOCKED_UNTIL = System.currentTimeMillis() + 60_000; LAST_ERR = "yahoo: rate limited (backing off 60s)"; throw new UpstreamException(Providers.AV_LIMIT, 429); }
            if (sc == 404) throw new UpstreamException("Symbol not found", 404);
            if (sc != 200) { LAST_ERR = "yahoo " + path.split("\\?")[0] + ": HTTP " + sc; throw new UpstreamException("Global market data is unavailable right now"); }
            try { return map(Json.parse(r.body())); }
            catch (RuntimeException | StackOverflowError e) { LAST_ERR = "yahoo: unreadable response"; throw new UpstreamException("Global market data is unavailable right now"); }
        }
        throw new UpstreamException("Global market data is unavailable right now");
    }

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    /** Yahoo profile values arrive as {"raw":1.2,"fmt":"1.20"}. */
    private static Double raw(Object o) { return o instanceof Map ? num(map(o).get("raw")) : num(o); }

    static Map<String, Object> norm(Map<String, Object> q) {
        Double t = num(q.get("regularMarketTime"));
        String name = str(q.get("longName")) != null ? str(q.get("longName")) : str(q.get("shortName"));
        return Json.obj("symbol", str(q.get("symbol")), "name", name == null ? null : name.trim(), "price", num(q.get("regularMarketPrice")), "change", num(q.get("regularMarketChange")),
                "changePct", num(q.get("regularMarketChangePercent")), "volume", num(q.get("regularMarketVolume")), "prevClose", num(q.get("regularMarketPreviousClose")),
                "currency", str(q.get("currency")), "exchange", str(q.get("fullExchangeName")), "type", str(q.get("quoteType")), "marketState", str(q.get("marketState")),
                "marketCap", num(q.get("marketCap")), "asOf", t == null ? null : Instant.ofEpochSecond(t.longValue()).toString(), "source", "Yahoo Finance (delayed)");
    }

    /** Quotes for any symbols worldwide (one call per 50 symbols, cached 60 s each). Unknown symbols are simply absent. */
    static Map<String, Map<String, Object>> quotes(Collection<String> symbols) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        List<String> need = new ArrayList<>();
        UpstreamException err = null;
        for (String s : symbols) { Map<String, Object> c = CACHE.age("yq" + s) < 60_000 ? CACHE.peek("yq" + s) : null; if (c != null) out.put(s, c); else need.add(s); }
        for (int i = 0; i < need.size(); i += 50) {
            List<String> part = need.subList(i, Math.min(need.size(), i + 50));
            try {
                for (Object o : list(map(call("/v7/finance/quote?symbols=" + enc(String.join(",", part)), null, true).get("quoteResponse")).get("result"))) {
                    Map<String, Object> q = norm(map(o));
                    if (q.get("price") == null) continue;
                    String s = String.valueOf(q.get("symbol")).toUpperCase();
                    CACHE.put("yq" + s, q); out.put(s, q);
                }
            } catch (UpstreamException e) {
                if (e.code != 429) try { out.putAll(sparkQuotes(part)); continue; } catch (UpstreamException e2) { e = e2; } // crumb refused: crumb-free prices
                for (String s : part) { Map<String, Object> c = CACHE.peek("yq" + s); if (c != null) out.put(s, c); } // stale beats nothing
                err = e;
            }
        }
        if (out.isEmpty() && err != null) throw err;
        return out;
    }

    /** Country of a Yahoo symbol from its suffix (for the currency when the crumb-free endpoints leave it out). */
    static final Map<String, String> SUFFIX_CCY = Map.ofEntries(Map.entry("T", "JPY"), Map.entry("NS", "INR"), Map.entry("BO", "INR"), Map.entry("L", "GBp"), Map.entry("DE", "EUR"), Map.entry("F", "EUR"),
            Map.entry("PA", "EUR"), Map.entry("AS", "EUR"), Map.entry("BR", "EUR"), Map.entry("MI", "EUR"), Map.entry("MC", "EUR"), Map.entry("LS", "EUR"), Map.entry("HE", "EUR"), Map.entry("IR", "EUR"),
            Map.entry("VI", "EUR"), Map.entry("SW", "CHF"), Map.entry("ST", "SEK"), Map.entry("OL", "NOK"), Map.entry("CO", "DKK"), Map.entry("WA", "PLN"), Map.entry("HK", "HKD"), Map.entry("SS", "CNY"),
            Map.entry("SZ", "CNY"), Map.entry("KS", "KRW"), Map.entry("KQ", "KRW"), Map.entry("TW", "TWD"), Map.entry("TWO", "TWD"), Map.entry("AX", "AUD"), Map.entry("NZ", "NZD"), Map.entry("SI", "SGD"),
            Map.entry("JK", "IDR"), Map.entry("KL", "MYR"), Map.entry("BK", "THB"), Map.entry("PS", "PHP"), Map.entry("TO", "CAD"), Map.entry("V", "CAD"), Map.entry("NE", "CAD"), Map.entry("SA", "BRL"),
            Map.entry("MX", "MXN"), Map.entry("BA", "ARS"), Map.entry("SN", "CLP"), Map.entry("SR", "SAR"), Map.entry("TA", "ILA"), Map.entry("JO", "ZAc"), Map.entry("IS", "TRY"), Map.entry("CA", "EGP"),
            Map.entry("QA", "QAR"), Map.entry("KW", "KWF"), Map.entry("AE", "AED"));
    static String ccyOf(String s) {
        for (Country c : COUNTRIES) for (String[] ix : c.indexes()) if (ix[0].equals(s)) return c.currency();
        int d = s.lastIndexOf('.');
        return d > 0 ? SUFFIX_CCY.get(s.substring(d + 1)) : "USD";
    }

    /** Prices without the crumb: Yahoo's spark endpoint, up to 20 symbols per call. */
    static Map<String, Map<String, Object>> sparkQuotes(List<String> symbols) {
        Map<String, Map<String, Object>> out = new LinkedHashMap<>();
        UpstreamException err = null;
        for (int i = 0; i < symbols.size(); i += 20) {
            List<String> part = symbols.subList(i, Math.min(symbols.size(), i + 20));
            try {
                Map<String, Object> j = call("/v8/finance/spark?symbols=" + enc(String.join(",", part)) + "&range=1d&interval=1d", null, false);
                for (String s : part) {
                    Map<String, Object> x = map(j.get(s));
                    List<Object> cl = list(x.get("close"));
                    Double p = num(x.get("fulldayPrice")) != null ? num(x.get("fulldayPrice")) : cl.isEmpty() ? null : num(cl.get(cl.size() - 1));
                    if (p == null) continue;
                    Double prev = num(x.get("chartPreviousClose")), ch = num(x.get("fulldayChange")) != null ? num(x.get("fulldayChange")) : prev == null ? null : p - prev;
                    Double pct = num(x.get("fulldayChangePercent")) != null ? num(x.get("fulldayChangePercent")) : prev == null || prev == 0 ? null : (p / prev - 1) * 100;
                    List<Object> ts = list(x.get("timestamp"));
                    Map<String, Object> q = Json.obj("symbol", s, "name", null, "price", p, "change", ch, "changePct", pct, "volume", null, "prevClose", prev, "currency", ccyOf(s),
                            "exchange", null, "type", s.startsWith("^") ? "INDEX" : null, "marketState", null, "marketCap", null,
                            "asOf", ts.isEmpty() ? null : Instant.ofEpochSecond(num(ts.get(ts.size() - 1)).longValue()).toString(), "source", "Yahoo Finance (delayed)");
                    CACHE.put("yq" + s, q); out.put(s, q);
                }
            } catch (UpstreamException e) { err = e; }
        }
        if (out.isEmpty() && err != null) throw err;
        return out;
    }

    static Map<String, Object> quote(String s) {
        Map<String, Object> q;
        try { q = quotes(List.of(s)).get(s); }
        catch (UpstreamException e) { if (e.code == 429) throw e; q = null; }
        if (q == null || q.get("name") == null) try { q = chartQuote(s); } catch (UpstreamException e) { if (q == null) throw e; } // single symbol: the chart's header has name and currency
        if (q == null) throw new UpstreamException("Symbol not found", 404);
        return q;
    }

    /** One symbol's quote from the chart endpoint's header (no crumb needed). */
    static Map<String, Object> chartQuote(String s) {
        Map<String, Object> c = CACHE.age("yc" + s) < 60_000 ? CACHE.peek("yc" + s) : null;
        if (c != null) return c;
        Map<String, Object> meta = map(map(list(map(call("/v8/finance/chart/" + enc(s) + "?range=5d&interval=1d", null, false).get("chart")).get("result")).stream().findFirst().orElse(null)).get("meta"));
        Double p = num(meta.get("regularMarketPrice")), prev = num(meta.get("chartPreviousClose")) == null ? num(meta.get("previousClose")) : num(meta.get("previousClose")) != null ? num(meta.get("previousClose")) : num(meta.get("chartPreviousClose"));
        if (p == null) throw new UpstreamException("Symbol not found", 404);
        Double pct = num(meta.get("regularMarketChangePercent")), ch = num(meta.get("regularMarketChange"));
        if (pct == null && prev != null && prev != 0) pct = (p / prev - 1) * 100;
        if (ch == null && prev != null) ch = p - prev;
        Double t = num(meta.get("regularMarketTime"));
        String name = str(meta.get("longName")) != null ? str(meta.get("longName")) : str(meta.get("shortName"));
        Map<String, Object> q = Json.obj("symbol", s, "name", name, "price", p, "change", ch, "changePct", pct, "volume", num(meta.get("regularMarketVolume")), "prevClose", prev,
                "currency", str(meta.get("currency")), "exchange", str(meta.get("fullExchangeName")), "type", str(meta.get("instrumentType")), "marketState", null, "marketCap", null,
                "high52", num(meta.get("fiftyTwoWeekHigh")), "low52", num(meta.get("fiftyTwoWeekLow")), "asOf", t == null ? null : Instant.ofEpochSecond(t.longValue()).toString(), "source", "Yahoo Finance (delayed)");
        CACHE.put("yc" + s, q); CACHE.put("yq" + s, q);
        return q;
    }

    /** Bars from Yahoo's chart API in exchange-local time, oldest first. */
    private static Map<String, Object> chart(String s, String range, String interval) {
        Map<String, Object> res = map(list(map(call("/v8/finance/chart/" + enc(s) + "?range=" + range + "&interval=" + interval + "&includePrePost=false", null, false).get("chart")).get("result")).stream().findFirst().orElse(null));
        Map<String, Object> meta = map(res.get("meta")), ind = map(list(map(res.get("indicators")).get("quote")).stream().findFirst().orElse(null));
        List<Object> ts = list(res.get("timestamp")), o = list(ind.get("open")), h = list(ind.get("high")), l = list(ind.get("low")), c = list(ind.get("close")), v = list(ind.get("volume"));
        if (ts.isEmpty()) throw new UpstreamException("No price history for this symbol", 404);
        Double off = num(meta.get("gmtoffset"));
        List<Object> bars = new ArrayList<>();
        for (int i = 0; i < ts.size(); i++) {
            Double cl = i < c.size() ? num(c.get(i)) : null;
            if (cl == null) continue; // Yahoo leaves holes for halted minutes
            LocalDateTime t = LocalDateTime.ofEpochSecond(num(ts.get(i)).longValue() + (off == null ? 0 : off.longValue()), 0, ZoneOffset.UTC);
            Double op = num(o.get(i)), hi = num(h.get(i)), lo = num(l.get(i));
            bars.add(Json.obj("t", t.toString().length() == 16 ? t + ":00" : t.toString(), "o", op == null ? cl : op, "h", hi == null ? cl : hi, "l", lo == null ? cl : lo, "c", cl, "v", i < v.size() && num(v.get(i)) != null ? num(v.get(i)) : 0.0));
        }
        if (bars.isEmpty()) throw new UpstreamException("No price history for this symbol", 404);
        return Json.obj("bars", bars, "currency", str(meta.get("currency")), "exchange", str(meta.get("fullExchangeName")), "tz", str(meta.get("exchangeTimezoneName")));
    }

    /** Daily bars (5 years, or 6 months when !full) in the same shape as Twelve Data history. */
    static Map<String, Object> history(String s, boolean full) {
        Map<String, Object> ch = chart(s, full ? "5y" : "6mo", "1d");
        List<Object> bars = new ArrayList<>();
        for (Object b : list(ch.get("bars"))) { Map<String, Object> m = new LinkedHashMap<>(map(b)); m.put("t", String.valueOf(m.get("t")).substring(0, 10)); bars.add(m); }
        return Json.obj("symbol", s, "bars", bars, "currency", ch.get("currency"), "exchange", ch.get("exchange"), "source", "Yahoo Finance", "adjusted", false);
    }

    /** Intraday bars for about 20 sessions. Yahoo has no 4-hour bars, so those are built from hourly ones within each day. */
    static Map<String, Object> intraday(String s, String interval) {
        String yi = switch (interval) { case "5min" -> "5m"; case "15min" -> "15m"; case "1h", "4h" -> "60m"; default -> throw new UpstreamException("interval must be one of 5min, 15min, 1h, 4h", 400); };
        Map<String, Object> ch = chart(s, "1mo", yi);
        List<Object> bars = list(ch.get("bars"));
        if (interval.equals("4h")) {
            List<Object> agg = new ArrayList<>();
            Map<String, Object> cur = null; String day = ""; int n = 0;
            for (Object o : bars) {
                Map<String, Object> b = map(o);
                String d = String.valueOf(b.get("t")).substring(0, 10);
                if (cur == null || !d.equals(day) || n == 4) { cur = new LinkedHashMap<>(b); agg.add(cur); day = d; n = 0; }
                else { cur.put("h", Math.max(num(cur.get("h")), num(b.get("h")))); cur.put("l", Math.min(num(cur.get("l")), num(b.get("l")))); cur.put("c", b.get("c")); cur.put("v", num(cur.get("v")) + num(b.get("v"))); }
                n++;
            }
            bars = agg;
        }
        Set<String> days = new HashSet<>();
        for (Object o : bars) days.add(String.valueOf(map(o).get("t")).substring(0, 10));
        int perSession = (int) Math.max(1, Math.round((double) bars.size() / Math.max(1, days.size())));
        return Json.obj("symbol", s, "interval", interval, "tz", ch.get("tz"), "perSession", perSession, "bars", bars, "currency", ch.get("currency"), "source", "Yahoo Finance", "asOf", Instant.now().toString());
    }

    /** Company or fund profile in the same units as Providers.overview (dollars and fractions). */
    static Map<String, Object> overview(String s) {
        Map<String, Object> r;
        try { r = map(list(map(call("/v10/finance/quoteSummary/" + enc(s) + "?modules=price,summaryProfile,summaryDetail,defaultKeyStatistics,financialData", null, true).get("quoteSummary")).get("result")).stream().findFirst().orElse(null)); }
        catch (UpstreamException e) {
            if (e.code == 404 || e.code == 429) throw e;
            Map<String, Object> q = chartQuote(s); // crumb refused: the basics from the chart header
            return Json.obj("symbol", s, "name", q.get("name"), "type", "EQUITY".equals(q.get("type")) ? "Common Stock" : q.get("type"), "sector", null, "industry", null, "country", null,
                    "currency", q.get("currency"), "exchange", q.get("exchange"), "marketCap", null, "pe", null, "beta", null, "dividendYield", null, "high52", q.get("high52"), "low52", q.get("low52"),
                    "revenueGrowthYoY", null, "profitMargin", null, "roe", null, "description", null, "source", "Yahoo Finance");
        }
        if (r.isEmpty()) throw new UpstreamException("No company data for this symbol", 404);
        Map<String, Object> p = map(r.get("price")), sp = map(r.get("summaryProfile")), sd = map(r.get("summaryDetail")), ks = map(r.get("defaultKeyStatistics")), fd = map(r.get("financialData"));
        String type = str(p.get("quoteType"));
        Double pe = raw(sd.get("trailingPE"));
        return Json.obj("symbol", s, "name", str(p.get("longName")) != null ? str(p.get("longName")) : str(p.get("shortName")), "type", "EQUITY".equals(type) ? "Common Stock" : type,
                "sector", str(sp.get("sector")), "industry", str(sp.get("industry")), "country", str(sp.get("country")), "currency", str(p.get("currency")), "exchange", str(p.get("exchangeName")),
                "marketCap", raw(p.get("marketCap")), "pe", pe, "beta", raw(sd.get("beta")) != null ? raw(sd.get("beta")) : raw(ks.get("beta")),
                "dividendYield", raw(sd.get("dividendYield")) != null ? raw(sd.get("dividendYield")) : raw(sd.get("yield")), "high52", raw(sd.get("fiftyTwoWeekHigh")), "low52", raw(sd.get("fiftyTwoWeekLow")),
                "revenueGrowthYoY", raw(fd.get("revenueGrowth")), "profitMargin", raw(fd.get("profitMargins")), "roe", raw(fd.get("returnOnEquity")),
                "description", str(sp.get("longBusinessSummary")), "website", str(sp.get("website")), "source", "Yahoo Finance");
    }

    /** Worldwide symbol search (stocks, ETFs, indexes, funds). */
    static List<Object> search(String text) {
        return CACHE.get("ys" + text.toLowerCase(), 3600_000L, () -> {
            List<Object> out = new ArrayList<>();
            for (Object o : list(call("/v1/finance/search?q=" + enc(text) + "&quotesCount=12&newsCount=0&listsCount=0", null, false).get("quotes"))) {
                Map<String, Object> m = map(o);
                String sym = str(m.get("symbol")), type = str(m.get("quoteType"));
                if (sym == null || !SYM.matcher(sym).matches() || !Set.of("EQUITY", "ETF", "INDEX", "MUTUALFUND").contains(type)) continue;
                out.add(Json.obj("symbol", sym, "name", str(m.get("longname")) != null ? str(m.get("longname")) : str(m.get("shortname")), "type", "EQUITY".equals(type) ? "Equity" : "INDEX".equals(type) ? "Index" : type,
                        "region", str(m.get("exchDisp")), "exchange", str(m.get("exchDisp")), "currency", null));
            }
            return out;
        });
    }

    // ---------------------------------------------------------------- world catalog

    record Country(String code, String name, String region, String currency, List<String> exchanges, String[][] indexes) {}
    static final List<String> SECTORS = List.of("Technology", "Financial Services", "Healthcare", "Consumer Cyclical", "Consumer Defensive", "Industrials", "Energy", "Basic Materials", "Communication Services", "Real Estate", "Utilities");
    static final List<String> REGIONS = List.of("Americas", "Europe", "Asia-Pacific", "Middle East & Africa");
    static final List<Country> COUNTRIES = List.of(
        new Country("us", "United States", "Americas", "USD", List.of("NMS", "NYQ", "NGM", "NCM", "ASE"), new String[][]{{"^GSPC", "S&P 500"}, {"^DJI", "Dow Jones"}, {"^IXIC", "Nasdaq Composite"}, {"^NDX", "Nasdaq 100"}, {"^RUT", "Russell 2000"}, {"^VIX", "VIX volatility"}}),
        new Country("ca", "Canada", "Americas", "CAD", List.of("TOR"), new String[][]{{"^GSPTSE", "S&P/TSX Composite"}}),
        new Country("br", "Brazil", "Americas", "BRL", List.of(), new String[][]{{"^BVSP", "Ibovespa"}}),
        new Country("mx", "Mexico", "Americas", "MXN", List.of(), new String[][]{{"^MXX", "S&P/BMV IPC"}}),
        new Country("ar", "Argentina", "Americas", "ARS", List.of(), new String[][]{{"^MERV", "Merval"}}),
        new Country("cl", "Chile", "Americas", "CLP", List.of(), new String[][]{}),
        new Country("gb", "United Kingdom", "Europe", "GBP", List.of("LSE"), new String[][]{{"^FTSE", "FTSE 100"}, {"^FTMC", "FTSE 250"}}),
        new Country("de", "Germany", "Europe", "EUR", List.of("GER"), new String[][]{{"^GDAXI", "DAX"}, {"^MDAXI", "MDAX"}}),
        new Country("fr", "France", "Europe", "EUR", List.of(), new String[][]{{"^FCHI", "CAC 40"}}),
        new Country("nl", "Netherlands", "Europe", "EUR", List.of(), new String[][]{{"^AEX", "AEX"}}),
        new Country("ch", "Switzerland", "Europe", "CHF", List.of(), new String[][]{{"^SSMI", "SMI"}}),
        new Country("es", "Spain", "Europe", "EUR", List.of(), new String[][]{{"^IBEX", "IBEX 35"}}),
        new Country("it", "Italy", "Europe", "EUR", List.of("MIL"), new String[][]{{"FTSEMIB.MI", "FTSE MIB"}}),
        new Country("se", "Sweden", "Europe", "SEK", List.of(), new String[][]{{"^OMX", "OMX Stockholm 30"}}),
        new Country("no", "Norway", "Europe", "NOK", List.of(), new String[][]{{"OSEBX.OL", "Oslo Børs Benchmark"}}),
        new Country("dk", "Denmark", "Europe", "DKK", List.of(), new String[][]{{"^OMXC25", "OMX Copenhagen 25"}}),
        new Country("fi", "Finland", "Europe", "EUR", List.of(), new String[][]{{"^OMXH25", "OMX Helsinki 25"}}),
        new Country("be", "Belgium", "Europe", "EUR", List.of(), new String[][]{{"^BFX", "BEL 20"}}),
        new Country("ie", "Ireland", "Europe", "EUR", List.of(), new String[][]{{"^ISEQ", "ISEQ All Share"}}),
        new Country("pl", "Poland", "Europe", "PLN", List.of(), new String[][]{{"WIG20.WA", "WIG20"}}),
        new Country("pt", "Portugal", "Europe", "EUR", List.of(), new String[][]{{"PSI20.LS", "PSI"}}),
        new Country("at", "Austria", "Europe", "EUR", List.of("VIE"), new String[][]{{"^ATX", "ATX"}}),
        new Country("eu", "Eurozone", "Europe", "EUR", null, new String[][]{{"^STOXX50E", "Euro Stoxx 50"}, {"^STOXX", "Stoxx Europe 600"}}),
        new Country("jp", "Japan", "Asia-Pacific", "JPY", List.of(), new String[][]{{"^N225", "Nikkei 225"}}),
        new Country("cn", "China", "Asia-Pacific", "CNY", List.of("SHH", "SHZ"), new String[][]{{"000001.SS", "SSE Composite"}, {"399001.SZ", "Shenzhen Component"}, {"000300.SS", "CSI 300"}}),
        new Country("hk", "Hong Kong", "Asia-Pacific", "HKD", List.of(), new String[][]{{"^HSI", "Hang Seng"}, {"^HSCE", "Hang Seng China Enterprises"}}),
        new Country("in", "India", "Asia-Pacific", "INR", List.of("NSI"), new String[][]{{"^NSEI", "Nifty 50"}, {"^BSESN", "Sensex"}, {"^NSEBANK", "Nifty Bank"}, {"^CNX100", "Nifty 100"}}),
        new Country("kr", "South Korea", "Asia-Pacific", "KRW", List.of(), new String[][]{{"^KS11", "KOSPI"}}),
        new Country("tw", "Taiwan", "Asia-Pacific", "TWD", List.of("TAI"), new String[][]{{"^TWII", "TAIEX"}}),
        new Country("au", "Australia", "Asia-Pacific", "AUD", List.of(), new String[][]{{"^AXJO", "S&P/ASX 200"}, {"^AORD", "All Ordinaries"}}),
        new Country("nz", "New Zealand", "Asia-Pacific", "NZD", List.of(), new String[][]{{"^NZ50", "NZX 50"}}),
        new Country("sg", "Singapore", "Asia-Pacific", "SGD", List.of(), new String[][]{{"^STI", "Straits Times"}}),
        new Country("id", "Indonesia", "Asia-Pacific", "IDR", List.of(), new String[][]{{"^JKSE", "IDX Composite"}}),
        new Country("my", "Malaysia", "Asia-Pacific", "MYR", List.of(), new String[][]{{"^KLSE", "FTSE Bursa Malaysia KLCI"}}),
        new Country("th", "Thailand", "Asia-Pacific", "THB", List.of(), new String[][]{{"^SET.BK", "SET"}}),
        new Country("ph", "Philippines", "Asia-Pacific", "PHP", null, new String[][]{{"PSEI.PS", "PSEi"}}),
        new Country("sa", "Saudi Arabia", "Middle East & Africa", "SAR", List.of(), new String[][]{{"^TASI.SR", "Tadawul All Share"}}),
        new Country("ae", "United Arab Emirates", "Middle East & Africa", "AED", null, new String[][]{{"DFMGI.AE", "DFM General"}}),
        new Country("qa", "Qatar", "Middle East & Africa", "QAR", List.of(), new String[][]{}),
        new Country("kw", "Kuwait", "Middle East & Africa", "KWD", List.of(), new String[][]{}),
        new Country("il", "Israel", "Middle East & Africa", "ILS", List.of(), new String[][]{{"^TA125.TA", "TA-125"}}),
        new Country("tr", "Turkey", "Middle East & Africa", "TRY", List.of(), new String[][]{{"XU100.IS", "BIST 100"}}),
        new Country("za", "South Africa", "Middle East & Africa", "ZAR", List.of(), new String[][]{{"^J203.JO", "JSE All Share"}}),
        new Country("eg", "Egypt", "Middle East & Africa", "EGP", List.of(), new String[][]{{"^CASE30", "EGX 30"}})
    );
    static final Map<String, Country> BY_CODE = new LinkedHashMap<>();
    static { for (Country c : COUNTRIES) BY_CODE.put(c.code(), c); }

    private static List<Object> indexRows(Country c, Map<String, Map<String, Object>> q) {
        List<Object> out = new ArrayList<>();
        for (String[] ix : c.indexes()) {
            Map<String, Object> r = new LinkedHashMap<>(q.getOrDefault(ix[0], Json.obj("symbol", ix[0])));
            r.put("symbol", ix[0]); r.put("name", ix[1]); r.put("type", "INDEX");
            out.add(r);
        }
        return out;
    }

    /** Every country Quantera covers, grouped by region, with live index levels (one quote call for all ~60 indexes). */
    static Map<String, Object> world() {
        if (!on()) throw new UpstreamException("Global market data is turned off", 503);
        return CACHE.get("yworld", 60_000L, () -> {
            List<String> all = new ArrayList<>();
            for (Country c : COUNTRIES) for (String[] ix : c.indexes()) all.add(ix[0]);
            Map<String, Map<String, Object>> q = quotes(all);
            List<Object> regions = new ArrayList<>();
            for (String reg : REGIONS) {
                List<Object> cs = new ArrayList<>();
                for (Country c : COUNTRIES) if (c.region().equals(reg))
                    cs.add(Json.obj("code", c.code(), "name", c.name(), "currency", c.currency(), "hasStocks", c.exchanges() != null, "indexes", indexRows(c, q)));
                regions.add(Json.obj("name", reg, "countries", cs));
            }
            return Json.obj("regions", regions, "assets", assetGroups(), "sectors", SECTORS, "source", "Yahoo Finance (delayed)", "updated", Instant.now().toString());
        });
    }

    static final int PER_PAGE = 25;

    /** About 325 of the most widely held and talked-about US-listed stocks and ETFs, by sector. */
    static final String[][] POPULAR = {{"AAPL", "Technology"}, {"MSFT", "Technology"}, {"ORCL", "Technology"}, {"CRM", "Technology"}, {"ADBE", "Technology"}, {"IBM", "Technology"}, {"CSCO", "Technology"}, {"NOW", "Technology"}, {"INTU", "Technology"}, {"PANW", "Technology"}, {"CRWD", "Technology"}, {"SNOW", "Technology"}, {"PLTR", "Technology"}, {"SHOP", "Technology"}, {"UBER", "Technology"}, {"ABNB", "Technology"}, {"DELL", "Technology"}, {"HPQ", "Technology"}, {"ANET", "Technology"}, {"FTNT", "Technology"}, {"DDOG", "Technology"}, {"NET", "Technology"}, {"ZS", "Technology"}, {"MDB", "Technology"}, {"TEAM", "Technology"}, {"WDAY", "Technology"}, {"SNPS", "Technology"}, {"CDNS", "Technology"}, {"APP", "Technology"}, {"RBLX", "Technology"}, {"COIN", "Technology"}, {"HOOD", "Technology"}, {"SONY", "Technology"}, {"ACN", "Technology"}, {"ADSK", "Technology"}, {"NVDA", "Semiconductors"}, {"AVGO", "Semiconductors"}, {"AMD", "Semiconductors"}, {"INTC", "Semiconductors"}, {"QCOM", "Semiconductors"}, {"TXN", "Semiconductors"}, {"AMAT", "Semiconductors"}, {"LRCX", "Semiconductors"}, {"KLAC", "Semiconductors"}, {"MU", "Semiconductors"}, {"ADI", "Semiconductors"}, {"MRVL", "Semiconductors"}, {"ARM", "Semiconductors"}, {"TSM", "Semiconductors"}, {"ASML", "Semiconductors"}, {"SMCI", "Semiconductors"}, {"ON", "Semiconductors"}, {"MCHP", "Semiconductors"}, {"GOOGL", "Communication"}, {"META", "Communication"}, {"NFLX", "Communication"}, {"DIS", "Communication"}, {"CMCSA", "Communication"}, {"T", "Communication"}, {"VZ", "Communication"}, {"TMUS", "Communication"}, {"SPOT", "Communication"}, {"SNAP", "Communication"}, {"PINS", "Communication"}, {"RDDT", "Communication"}, {"EA", "Communication"}, {"TTWO", "Communication"}, {"WBD", "Communication"}, {"ROKU", "Communication"}, {"CHTR", "Communication"}, {"AMZN", "Consumer"}, {"TSLA", "Consumer"}, {"HD", "Consumer"}, {"LOW", "Consumer"}, {"MCD", "Consumer"}, {"SBUX", "Consumer"}, {"NKE", "Consumer"}, {"TGT", "Consumer"}, {"BKNG", "Consumer"}, {"MAR", "Consumer"}, {"HLT", "Consumer"}, {"CMG", "Consumer"}, {"LULU", "Consumer"}, {"F", "Consumer"}, {"GM", "Consumer"}, {"RIVN", "Consumer"}, {"LCID", "Consumer"}, {"TJX", "Consumer"}, {"ROST", "Consumer"}, {"EBAY", "Consumer"}, {"DPZ", "Consumer"}, {"YUM", "Consumer"}, {"DASH", "Consumer"}, {"CVNA", "Consumer"}, {"RCL", "Consumer"}, {"CCL", "Consumer"}, {"DAL", "Consumer"}, {"UAL", "Consumer"}, {"AAL", "Consumer"}, {"LUV", "Consumer"}, {"WMT", "Consumer"}, {"COST", "Consumer"}, {"PG", "Consumer"}, {"KO", "Consumer"}, {"PEP", "Consumer"}, {"PM", "Consumer"}, {"MO", "Consumer"}, {"MDLZ", "Consumer"}, {"CL", "Consumer"}, {"KMB", "Consumer"}, {"GIS", "Consumer"}, {"KHC", "Consumer"}, {"HSY", "Consumer"}, {"STZ", "Consumer"}, {"KDP", "Consumer"}, {"DG", "Consumer"}, {"DLTR", "Consumer"}, {"KR", "Consumer"}, {"EL", "Consumer"}, {"BRK.B", "Financials"}, {"JPM", "Financials"}, {"BAC", "Financials"}, {"WFC", "Financials"}, {"C", "Financials"}, {"GS", "Financials"}, {"MS", "Financials"}, {"SCHW", "Financials"}, {"AXP", "Financials"}, {"V", "Financials"}, {"MA", "Financials"}, {"PYPL", "Financials"}, {"BLK", "Financials"}, {"SPGI", "Financials"}, {"CME", "Financials"}, {"ICE", "Financials"}, {"COF", "Financials"}, {"USB", "Financials"}, {"PNC", "Financials"}, {"AIG", "Financials"}, {"MET", "Financials"}, {"PGR", "Financials"}, {"TRV", "Financials"}, {"CB", "Financials"}, {"MMC", "Financials"}, {"KKR", "Financials"}, {"BX", "Financials"}, {"SOFI", "Financials"}, {"AFRM", "Financials"}, {"XYZ", "Financials"}, {"LLY", "Healthcare"}, {"UNH", "Healthcare"}, {"JNJ", "Healthcare"}, {"ABBV", "Healthcare"}, {"MRK", "Healthcare"}, {"PFE", "Healthcare"}, {"TMO", "Healthcare"}, {"ABT", "Healthcare"}, {"DHR", "Healthcare"}, {"AMGN", "Healthcare"}, {"GILD", "Healthcare"}, {"BMY", "Healthcare"}, {"CVS", "Healthcare"}, {"CI", "Healthcare"}, {"ELV", "Healthcare"}, {"ISRG", "Healthcare"}, {"VRTX", "Healthcare"}, {"REGN", "Healthcare"}, {"MRNA", "Healthcare"}, {"BIIB", "Healthcare"}, {"ZTS", "Healthcare"}, {"SYK", "Healthcare"}, {"BSX", "Healthcare"}, {"MDT", "Healthcare"}, {"HCA", "Healthcare"}, {"NVO", "Healthcare"}, {"AZN", "Healthcare"}, {"HIMS", "Healthcare"}, {"CAT", "Industrials"}, {"DE", "Industrials"}, {"BA", "Industrials"}, {"GE", "Industrials"}, {"HON", "Industrials"}, {"UPS", "Industrials"}, {"FDX", "Industrials"}, {"LMT", "Industrials"}, {"RTX", "Industrials"}, {"NOC", "Industrials"}, {"GD", "Industrials"}, {"UNP", "Industrials"}, {"CSX", "Industrials"}, {"NSC", "Industrials"}, {"MMM", "Industrials"}, {"EMR", "Industrials"}, {"ETN", "Industrials"}, {"ITW", "Industrials"}, {"WM", "Industrials"}, {"PH", "Industrials"}, {"JCI", "Industrials"}, {"CARR", "Industrials"}, {"URI", "Industrials"}, {"PWR", "Industrials"}, {"GEV", "Industrials"}, {"XOM", "Energy"}, {"CVX", "Energy"}, {"COP", "Energy"}, {"EOG", "Energy"}, {"SLB", "Energy"}, {"OXY", "Energy"}, {"PSX", "Energy"}, {"MPC", "Energy"}, {"VLO", "Energy"}, {"KMI", "Energy"}, {"WMB", "Energy"}, {"OKE", "Energy"}, {"HAL", "Energy"}, {"DVN", "Energy"}, {"BKR", "Energy"}, {"ENPH", "Energy"}, {"FSLR", "Energy"}, {"LIN", "Materials"}, {"APD", "Materials"}, {"SHW", "Materials"}, {"ECL", "Materials"}, {"NEM", "Materials"}, {"FCX", "Materials"}, {"DOW", "Materials"}, {"DD", "Materials"}, {"NUE", "Materials"}, {"ALB", "Materials"}, {"MLM", "Materials"}, {"VMC", "Materials"}, {"NEE", "Utilities"}, {"DUK", "Utilities"}, {"SO", "Utilities"}, {"D", "Utilities"}, {"AEP", "Utilities"}, {"EXC", "Utilities"}, {"SRE", "Utilities"}, {"XEL", "Utilities"}, {"PCG", "Utilities"}, {"CEG", "Utilities"}, {"VST", "Utilities"}, {"PLD", "Real estate"}, {"AMT", "Real estate"}, {"EQIX", "Real estate"}, {"CCI", "Real estate"}, {"PSA", "Real estate"}, {"O", "Real estate"}, {"SPG", "Real estate"}, {"WELL", "Real estate"}, {"DLR", "Real estate"}, {"VICI", "Real estate"}, {"BABA", "International"}, {"JD", "International"}, {"PDD", "International"}, {"BIDU", "International"}, {"NIO", "International"}, {"TM", "International"}, {"HMC", "International"}, {"SAP", "International"}, {"SHEL", "International"}, {"BP", "International"}, {"TTE", "International"}, {"UL", "International"}, {"DEO", "International"}, {"BTI", "International"}, {"GSK", "International"}, {"HSBC", "International"}, {"RY", "International"}, {"TD", "International"}, {"MELI", "International"}, {"NU", "International"}, {"SE", "International"}, {"BHP", "International"}, {"RIO", "International"}, {"VALE", "International"}, {"PBR", "International"}, {"INFY", "International"}, {"HDB", "International"}, {"IBN", "International"}, {"SPY", "ETFs"}, {"VOO", "ETFs"}, {"IVV", "ETFs"}, {"VTI", "ETFs"}, {"QQQ", "ETFs"}, {"DIA", "ETFs"}, {"IWM", "ETFs"}, {"VEA", "ETFs"}, {"VWO", "ETFs"}, {"EFA", "ETFs"}, {"AGG", "ETFs"}, {"BND", "ETFs"}, {"TLT", "ETFs"}, {"IEF", "ETFs"}, {"SHY", "ETFs"}, {"LQD", "ETFs"}, {"HYG", "ETFs"}, {"GLD", "ETFs"}, {"SLV", "ETFs"}, {"USO", "ETFs"}, {"UNG", "ETFs"}, {"XLK", "ETFs"}, {"XLF", "ETFs"}, {"XLE", "ETFs"}, {"XLV", "ETFs"}, {"XLY", "ETFs"}, {"XLP", "ETFs"}, {"XLI", "ETFs"}, {"XLU", "ETFs"}, {"XLB", "ETFs"}, {"XLRE", "ETFs"}, {"XLC", "ETFs"}, {"SMH", "ETFs"}, {"SOXX", "ETFs"}, {"ARKK", "ETFs"}, {"VNQ", "ETFs"}, {"SCHD", "ETFs"}, {"VYM", "ETFs"}, {"JEPI", "ETFs"}, {"IBIT", "ETFs"}, {"ITA", "ETFs"}, {"JETS", "ETFs"}, {"KRE", "ETFs"}, {"XBI", "ETFs"}, {"VGT", "ETFs"}};
    private static String ySym(String s) { return s.matches("[A-Z]+\\.[A-Z]") ? s.replace('.', '-') : s; } // BRK.B is BRK-B on Yahoo
    private static String ourSym(String s) { return s.matches("[A-Z]+-[A-Z]") ? s.replace('-', '.') : s; }

    /** Lists for the Markets page: popular (fixed ~325), and live whole-market lists: active, gainers, losers, trending. */
    static Map<String, Object> popular(String which) {
        return CACHE.get("ypop" + which, 60_000L, () -> {
            Map<String, String> sector = new HashMap<>();
            for (String[] p : POPULAR) sector.put(p[0], p[1]);
            List<Object> items = new ArrayList<>();
            List<String> order = new ArrayList<>();
            if (which.equals("popular")) for (String[] p : POPULAR) order.add(p[0]);
            else if (which.equals("trending")) {
                for (Object o : list(map(list(map(call("/v1/finance/trending/US?count=50", null, false).get("finance")).get("result")).stream().findFirst().orElse(null)).get("quotes"))) {
                    String s = str(map(o).get("symbol"));
                    if (s != null && SYM.matcher(s).matches() && !s.contains("=")) order.add(ourSym(s));
                }
            } else {
                String id = which.equals("active") ? "most_actives" : which.equals("gainers") ? "day_gainers" : "day_losers";
                for (Object o : list(map(list(map(call("/v1/finance/screener/predefined/saved?scrIds=" + id + "&count=100", null, false).get("finance")).get("result")).stream().findFirst().orElse(null)).get("quotes"))) {
                    Map<String, Object> q = new LinkedHashMap<>(norm(map(o)));
                    if (q.get("price") == null) continue;
                    String s = ourSym(String.valueOf(q.get("symbol")));
                    q.put("symbol", s); q.put("sector", sector.getOrDefault(s, Providers.SECTOR.get(s))); q.put("spark", Providers.spark(s));
                    items.add(q);
                }
                return Json.obj("list", which, "items", items, "count", items.size(), "source", "Yahoo Finance (delayed)", "updated", Instant.now().toString());
            }
            List<String> ys = new ArrayList<>();
            for (String s : order) ys.add(ySym(s));
            Map<String, Map<String, Object>> q = quotes(ys);
            for (String s : order) {
                Map<String, Object> x = q.get(ySym(s));
                if (x == null) continue;
                x = new LinkedHashMap<>(x);
                x.put("symbol", s); x.put("sector", sector.getOrDefault(s, Providers.SECTOR.get(s))); x.put("spark", Providers.spark(s));
                items.add(x);
            }
            if (which.equals("popular")) items.sort((a, b) -> Double.compare(num(map(b).get("changePct")) == null ? -999 : num(map(b).get("changePct")), num(map(a).get("changePct")) == null ? -999 : num(map(a).get("changePct"))));
            return Json.obj("list", which, "items", items, "count", items.size(), "source", "Yahoo Finance (delayed)", "updated", Instant.now().toString());
        });
    }

    /** Yahoo Finance news for a company, ticker or topic (no crumb needed), in the same shape as the other news feeds. */
    static List<Object> news(String q) {
        return CACHE.get("yn" + q.toLowerCase(), 3 * 60_000L, () -> {
            List<Object> out = new ArrayList<>();
            List<Object> raw = list(call("/v1/finance/search?q=" + enc(q) + "&quotesCount=0&newsCount=50&enableFuzzyQuery=false", null, false).get("news"));
            if (raw.isEmpty()) raw = list(call("/v1/finance/search?q=" + enc(q) + "&quotesCount=0&newsCount=25&enableFuzzyQuery=false", null, false).get("news")); // larger requests are sometimes refused
            for (Object o : raw) {
                Map<String, Object> a = map(o);
                String title = str(a.get("title")), url = str(a.get("link")), id = str(a.get("uuid"));
                if (title == null || url == null || !"STORY".equals(str(a.get("type")))) continue;
                List<Object> syms = new ArrayList<>();
                for (Object t : list(a.get("relatedTickers"))) if (syms.size() < 5 && t != null && SYM.matcher(String.valueOf(t)).matches()) syms.add(Json.obj("symbol", String.valueOf(t), "name", null, "sentiment", null));
                List<Object> res = list(map(a.get("thumbnail")).get("resolutions"));
                Double t = num(a.get("providerPublishTime"));
                out.add(Json.obj("id", "yh-" + (id == null ? Math.abs(url.hashCode()) : id), "title", title, "summary", "", "url", url, "source", str(a.get("publisher")),
                        "image", res.isEmpty() ? null : str(map(res.get(0)).get("url")), "publishedAt", t == null ? null : Instant.ofEpochSecond(t.longValue()).toString(), "symbols", syms));
            }
            return out;
        });
    }

    /** IPOs worldwide from 60 days ago to 120 days ahead: company, ticker, exchange, date, price range or final price, shares, status. */
    static List<Object> ipos() {
        return CACHE.get("yipo", 6 * 3600_000L, () -> {
            LocalDate d0 = LocalDate.now(ZoneOffset.UTC);
            List<Object> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int off = 0; off < 1000; off += 250) { // Yahoo serves at most 250 rows per call
            String body = Json.write(Json.obj("sortType", "ASC", "entityIdType", "ipo_info", "sortField", "startdatetime",
                    "includeFields", List.of("ticker", "companyshortname", "exchange_short_name", "startdatetime", "pricefrom", "priceto", "offerprice", "currencyname", "shares", "dealtype"),
                    "size", 250, "offset", off, "query", Json.obj("operator", "and", "operands", List.of(Json.obj("operator", "gte", "operands", List.of("startdatetime", d0.minusDays(60).toString())),
                            Json.obj("operator", "lte", "operands", List.of("startdatetime", d0.plusDays(120).toString()))))));
            Map<String, Object> r = map(list(map(call("/v1/finance/visualization?lang=en-US&region=US", body, true).get("finance")).get("result")).stream().findFirst().orElse(null));
            Map<String, Object> doc = map(list(r.get("documents")).stream().findFirst().orElse(null));
            List<String> cols = new ArrayList<>();
            for (Object c : list(doc.get("columns"))) cols.add(str(map(c).get("id")));
            List<Object> rows = list(doc.get("rows"));
            for (Object row : rows) {
                List<Object> v = list(row);
                java.util.function.Function<String, Object> g = k -> { int i = cols.indexOf(k); return i < 0 || i >= v.size() ? null : v.get(i); };
                String sym = str(g.apply("ticker")), date = str(g.apply("startdatetime"));
                if (sym == null || date == null || !seen.add(sym + date.substring(0, Math.min(10, date.length())))) continue;
                Double offer = num(g.apply("offerprice")), shares = num(g.apply("shares"));
                out.add(Json.obj("symbol", sym, "name", str(g.apply("companyshortname")), "date", date.substring(0, Math.min(10, date.length())), "low", num(g.apply("pricefrom")), "high", num(g.apply("priceto")),
                        "price", offer, "currency", str(g.apply("currencyname")), "shares", shares, "status", str(g.apply("dealtype")), "exchange", str(g.apply("exchange_short_name")),
                        "dealSize", offer != null && shares != null ? offer * shares : null));
            }
            Double total = num(r.get("total"));
            if (rows.size() < 250 || total == null || off + 250 >= total) break;
            }
            return out;
        });
    }

    /** Commodities, currencies, government bond yields and crypto shown alongside the countries. */
    static final String[][][] ASSETS = {
        {{"Commodities"}, {"GC=F", "Gold", "USD per troy ounce"}, {"SI=F", "Silver", "USD per troy ounce"}, {"PL=F", "Platinum", "USD per troy ounce"}, {"PA=F", "Palladium", "USD per troy ounce"},
            {"HG=F", "Copper", "USD per pound"}, {"CL=F", "Crude oil (WTI)", "USD per barrel"}, {"BZ=F", "Brent crude oil", "USD per barrel"}, {"NG=F", "Natural gas", "USD per MMBtu"},
            {"RB=F", "Gasoline", "USD per gallon"}, {"HO=F", "Heating oil", "USD per gallon"}, {"ZC=F", "Corn", "US cents per bushel"}, {"ZW=F", "Wheat", "US cents per bushel"},
            {"ZS=F", "Soybeans", "US cents per bushel"}, {"KC=F", "Coffee", "US cents per pound"}, {"CC=F", "Cocoa", "USD per tonne"}, {"SB=F", "Sugar", "US cents per pound"},
            {"CT=F", "Cotton", "US cents per pound"}, {"LE=F", "Live cattle", "US cents per pound"}},
        {{"Currencies"}, {"DX-Y.NYB", "US dollar index", "vs six major currencies"}, {"EURUSD=X", "EUR / USD", "US dollars per euro"}, {"GBPUSD=X", "GBP / USD", "US dollars per pound"},
            {"USDJPY=X", "USD / JPY", "yen per US dollar"}, {"USDCNY=X", "USD / CNY", "yuan per US dollar"}, {"USDINR=X", "USD / INR", "rupees per US dollar"}, {"AUDUSD=X", "AUD / USD", "US dollars per Australian dollar"},
            {"USDCAD=X", "USD / CAD", "Canadian dollars per US dollar"}, {"USDCHF=X", "USD / CHF", "Swiss francs per US dollar"}},
        {{"US Treasury yields"}, {"^IRX", "13-week bill", "yield, %"}, {"^FVX", "5-year note", "yield, %"}, {"^TNX", "10-year note", "yield, %"}, {"^TYX", "30-year bond", "yield, %"}},
        {{"Crypto"}, {"BTC-USD", "Bitcoin", "USD"}, {"ETH-USD", "Ethereum", "USD"}, {"SOL-USD", "Solana", "USD"}, {"XRP-USD", "XRP", "USD"}}};

    static List<Object> assetGroups() {
        List<String> all = new ArrayList<>();
        for (String[][] g : ASSETS) for (int i = 1; i < g.length; i++) all.add(g[i][0]);
        Map<String, Map<String, Object>> q;
        try { q = quotes(all); } catch (UpstreamException e) { q = Map.of(); }
        List<Object> out = new ArrayList<>();
        for (String[][] g : ASSETS) {
            List<Object> items = new ArrayList<>();
            for (int i = 1; i < g.length; i++) {
                Map<String, Object> r = new LinkedHashMap<>(q.getOrDefault(g[i][0], Json.obj("symbol", g[i][0])));
                r.put("symbol", g[i][0]); r.put("name", g[i][1]); r.put("unit", g[i][2]);
                items.add(r);
            }
            out.add(Json.obj("name", g[0][0], "items", items));
        }
        return out;
    }

    /** Twelve Data exchange (MIC) to Yahoo symbol suffix, for the main exchange(s) of each country. */
    static final Map<String, String> MIC_SUFFIX = Map.ofEntries(Map.entry("XNAS", ""), Map.entry("XNGS", ""), Map.entry("XNMS", ""), Map.entry("XNCM", ""), Map.entry("XNYS", ""), Map.entry("ARCX", ""),
            Map.entry("BATS", ""), Map.entry("XASE", ""), Map.entry("XTSE", "TO"), Map.entry("BVMF", "SA"), Map.entry("XMEX", "MX"), Map.entry("XBUE", "BA"), Map.entry("XSGO", "SN"), Map.entry("XLON", "L"),
            Map.entry("XETR", "DE"), Map.entry("XPAR", "PA"), Map.entry("XAMS", "AS"), Map.entry("XSWX", "SW"), Map.entry("XMAD", "MC"), Map.entry("XMIL", "MI"), Map.entry("XSTO", "ST"), Map.entry("XOSL", "OL"),
            Map.entry("XCSE", "CO"), Map.entry("XHEL", "HE"), Map.entry("XBRU", "BR"), Map.entry("XDUB", "IR"), Map.entry("XWAR", "WA"), Map.entry("XLIS", "LS"), Map.entry("XWBO", "VI"), Map.entry("XJPX", "T"),
            Map.entry("XSHG", "SS"), Map.entry("XSHE", "SZ"), Map.entry("XHKG", "HK"), Map.entry("XNSE", "NS"), Map.entry("XKRX", "KS"), Map.entry("XKOS", "KQ"), Map.entry("XTAI", "TW"), Map.entry("XASX", "AX"),
            Map.entry("XNZE", "NZ"), Map.entry("XSES", "SI"), Map.entry("XIDX", "JK"), Map.entry("XKLS", "KL"), Map.entry("XBKK", "BK"), Map.entry("XSAU", "SR"), Map.entry("DSMD", "QA"), Map.entry("XKUW", "KW"),
            Map.entry("XTAE", "TA"), Map.entry("XIST", "IS"), Map.entry("XJSE", "JO"), Map.entry("XCAI", "CA"));

    static String yahooSymbol(String sym, String mic) {
        String sfx = MIC_SUFFIX.get(mic);
        if (sfx == null || sym == null || sym.isBlank()) return null;
        if (sfx.isEmpty()) return sym;
        String b = sym.trim().replace(' ', '-').replace('.', '-');
        if (sfx.equals("HK") && b.matches("[0-9]{1,3}")) b = "0".repeat(4 - b.length()) + b;
        String y = b + "." + sfx;
        return SYM.matcher(y).matches() ? y : null;
    }

    /** Every listed stock or ETF on a country's main exchanges (Twelve Data reference list, refreshed daily), sorted by name. */
    static List<Map<String, Object>> listing(Country c, boolean etf) {
        return CACHE.get("tdl" + c.code() + etf, 24 * 3600_000L, () -> {
            List<Map<String, Object>> out = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (Object o : list(map(Providers.getJson("td", (etf ? "/etfs" : "/stocks") + "?country=" + enc(c.name()), 30)).get("data"))) {
                Map<String, Object> m = map(o);
                if (!etf && !"Common Stock".equals(str(m.get("type")))) continue;
                String y = yahooSymbol(str(m.get("symbol")), str(m.get("mic_code")));
                if (y == null || !seen.add(y)) continue;
                out.add(Json.obj("symbol", y, "name", str(m.get("name")), "currency", str(m.get("currency"))));
            }
            out.sort(Comparator.comparing(m -> String.valueOf(m.get("name")).toLowerCase()));
            if (out.isEmpty()) throw new UpstreamException("No listings for this market yet", 404);
            return out;
        });
    }

    /** A page of a country's listings with crumb-free prices (used when Yahoo's screener is refused). A to Z; no sector or ranking data. */
    static Map<String, Object> basicPage(Country c, String kind, int page) {
        List<Map<String, Object>> all = listing(c, kind.equals("etfs"));
        int from = Math.min(all.size(), (page - 1) * PER_PAGE), to = Math.min(all.size(), from + PER_PAGE);
        List<Map<String, Object>> rows = all.subList(from, to);
        List<String> syms = new ArrayList<>();
        for (Map<String, Object> m : rows) syms.add(String.valueOf(m.get("symbol")));
        Map<String, Map<String, Object>> q;
        try { q = sparkQuotes(syms); } catch (UpstreamException e) { q = Map.of(); }
        List<Object> items = new ArrayList<>();
        for (Map<String, Object> m : rows) {
            Map<String, Object> x = new LinkedHashMap<>(q.getOrDefault(String.valueOf(m.get("symbol")), Json.obj("symbol", m.get("symbol"))));
            x.put("name", m.get("name"));
            if (m.get("currency") != null) x.put("currency", m.get("currency"));
            items.add(x);
        }
        return Json.obj("country", c.code(), "name", c.name(), "kind", kind, "mode", "basic", "items", items, "total", all.size(), "page", page, "perPage", PER_PAGE, "source", "Twelve Data listings, Yahoo Finance prices (delayed)");
    }


    /** One country's indexes, or a page of its stocks or ETFs (largest first, optionally one sector), with prices. */
    static Map<String, Object> country(String code, String kind, String sector, int page, String sort) {
        Country c = BY_CODE.get(code);
        if (c == null) throw new UpstreamException("Unknown country", 404);
        if (kind.equals("indexes")) {
            List<String> syms = new ArrayList<>();
            for (String[] ix : c.indexes()) syms.add(ix[0]);
            return Json.obj("country", c.code(), "name", c.name(), "kind", kind, "items", indexRows(c, syms.isEmpty() ? Map.of() : quotes(syms)), "total", c.indexes().length, "page", 1, "perPage", PER_PAGE);
        }
        if (c.exchanges() == null) return Json.obj("country", c.code(), "name", c.name(), "kind", kind, "items", List.of(), "total", 0, "page", 1, "perPage", PER_PAGE);
        boolean etf = kind.equals("etfs");
        String sortField = switch (sort) { case "gainers", "losers" -> "percentchange"; case "active" -> "dayvolume"; default -> etf ? "fundnetassets" : "intradaymarketcap"; };
        String key = "ycty" + code + kind + sector + page + sort;
        return CACHE.get(key, 5 * 60_000L, () -> {
            List<Object> ops = new ArrayList<>(List.of(Json.obj("operator", "eq", "operands", List.of("region", c.code()))));
            if (!c.exchanges().isEmpty()) {
                List<Object> ex = new ArrayList<>();
                for (String e : c.exchanges()) ex.add(Json.obj("operator", "eq", "operands", List.of("exchange", e)));
                ops.add(Json.obj("operator", "or", "operands", ex));
            }
            if (!etf && !sector.isEmpty()) ops.add(Json.obj("operator", "eq", "operands", List.of("sector", sector)));
            if (sort.equals("gainers")) ops.add(Json.obj("operator", "gt", "operands", List.of("percentchange", 0)));
            if (sort.equals("losers")) ops.add(Json.obj("operator", "lt", "operands", List.of("percentchange", 0)));
            String body = Json.write(Json.obj("size", PER_PAGE, "offset", (page - 1) * PER_PAGE, "sortField", sortField, "sortType", sort.equals("losers") ? "ASC" : "DESC",
                    "quoteType", etf ? "ETF" : "EQUITY", "query", Json.obj("operator", "AND", "operands", ops), "userId", "", "userIdType", "guid"));
            Map<String, Object> r;
            try { r = map(list(map(call("/v1/finance/screener?lang=en-US&region=US", body, true).get("finance")).get("result")).stream().findFirst().orElse(null)); }
            catch (UpstreamException e) { if (e.code == 429 || !Providers.twelveOn()) throw e; return basicPage(c, kind, page); } // crumb refused: full listings, prices without ranking
            List<Object> items = new ArrayList<>();
            for (Object o : list(r.get("quotes"))) { Map<String, Object> q = norm(map(o)); if (q.get("price") != null) { CACHE.put("yq" + q.get("symbol"), q); items.add(q); } }
            Double total = num(r.get("total"));
            return Json.obj("country", c.code(), "name", c.name(), "kind", kind, "sector", sector, "sort", sort, "items", items, "total", total == null ? items.size() : total.intValue(), "page", page, "perPage", PER_PAGE, "source", "Yahoo Finance (delayed)");
        });
    }

    /** Exchange suffixes Yahoo uses for non-US listings (BRK.B and BF.B stay US). */
    static final Set<String> SUFFIXES = Set.of("NS", "BO", "T", "L", "IL", "DE", "F", "PA", "AS", "BR", "LS", "MI", "MC", "SW", "VI", "ST", "OL", "CO", "HE", "IR", "WA", "AT", "PR", "BD",
            "HK", "SS", "SZ", "KS", "KQ", "TW", "TWO", "AX", "NZ", "SI", "JK", "KL", "BK", "PS", "TO", "V", "NE", "CN", "SA", "MX", "BA", "SN", "SR", "TA", "JO", "IS", "CA", "QA", "KW", "AE", "XC", "NYB", "PVT");

    static boolean isGlobal(String s) {
        if (s.startsWith("^") || s.contains("=")) return true;
        int d = s.lastIndexOf('.');
        return d > 0 && SUFFIXES.contains(s.substring(d + 1).toUpperCase());
    }

    // ---------------------------------------------------------------- stock screener on real fundamentals

    /** Stocks in one country matching the filters (null = no filter). Amounts are in the country's own currency. Cached 5 minutes. */
    static Map<String, Object> screen(String code, Double minCap, Double maxPe, Double minGrowth, Double minRoe, Double minDiv, String sector, String sort, int page) {
        Country c = BY_CODE.get(code);
        if (c == null || c.exchanges() == null) throw new UpstreamException("Unknown country", 400);
        String key = "yscr" + code + "|" + minCap + "|" + maxPe + "|" + minGrowth + "|" + minRoe + "|" + minDiv + "|" + sector + "|" + sort + "|" + page;
        return CACHE.get(key, 5 * 60_000L, () -> {
            List<Object> ops = new ArrayList<>(List.of(Json.obj("operator", "eq", "operands", List.of("region", c.code()))));
            if (!c.exchanges().isEmpty()) {
                List<Object> ex = new ArrayList<>();
                for (String e : c.exchanges()) ex.add(Json.obj("operator", "eq", "operands", List.of("exchange", e)));
                ops.add(Json.obj("operator", "or", "operands", ex));
            }
            if (!sector.isEmpty()) ops.add(Json.obj("operator", "eq", "operands", List.of("sector", sector)));
            if (minCap != null) ops.add(Json.obj("operator", "gt", "operands", List.of("intradaymarketcap", minCap)));
            if (maxPe != null) ops.add(Json.obj("operator", "btwn", "operands", List.of("peratio.lasttwelvemonths", 0, maxPe)));
            if (minGrowth != null) ops.add(Json.obj("operator", "gt", "operands", List.of("quarterlyrevenuegrowth.quarterly", minGrowth)));
            if (minRoe != null) ops.add(Json.obj("operator", "gt", "operands", List.of("returnonequity.lasttwelvemonths", minRoe)));
            if (minDiv != null) ops.add(Json.obj("operator", "gt", "operands", List.of("forward_dividend_yield", minDiv)));
            if (sort.equals("gainers")) ops.add(Json.obj("operator", "gt", "operands", List.of("percentchange", 0)));
            if (sort.equals("losers")) ops.add(Json.obj("operator", "lt", "operands", List.of("percentchange", 0)));
            String field = switch (sort) { case "gainers", "losers" -> "percentchange"; case "pe" -> "peratio.lasttwelvemonths"; case "dividend" -> "forward_dividend_yield"; case "active" -> "dayvolume"; default -> "intradaymarketcap"; };
            String body = Json.write(Json.obj("size", PER_PAGE, "offset", (page - 1) * PER_PAGE, "sortField", field, "sortType", sort.equals("losers") || sort.equals("pe") ? "ASC" : "DESC",
                    "quoteType", "EQUITY", "query", Json.obj("operator", "AND", "operands", ops), "userId", "", "userIdType", "guid"));
            Map<String, Object> r = map(list(map(call("/v1/finance/screener?lang=en-US&region=US", body, true).get("finance")).get("result")).stream().findFirst().orElse(null));
            List<Object> items = new ArrayList<>();
            for (Object o : list(r.get("quotes"))) {
                Map<String, Object> q = map(o);
                String sym = str(q.get("symbol"));
                if (sym == null || sym.matches("[A-Z]+-P[A-Z]*")) continue; // preferred shares (JPM-PC) aren't the common stock people mean
                String name = str(q.get("longName")) != null ? str(q.get("longName")) : str(q.get("shortName"));
                items.add(Json.obj("symbol", ourSym(sym), "name", name, "price", num(q.get("regularMarketPrice")), "changePct", num(q.get("regularMarketChangePercent")), "marketCap", num(q.get("marketCap")), "pe", num(q.get("trailingPE")),
                        "change52w", num(q.get("fiftyTwoWeekChangePercent")), "dividendYield", num(q.get("dividendYield")), "currency", str(q.get("currency"))));
            }
            return Json.obj("items", items, "total", num(r.get("total")) == null ? items.size() : num(r.get("total")), "page", page, "perPage", PER_PAGE, "country", c.name(), "source", "Yahoo Finance");
        });
    }

    // ---------------------------------------------------------------- fundamentals for any stock worldwide

    private static final String[][] STATEMENT = {{"revenue", "TotalRevenue"}, {"grossProfit", "GrossProfit"}, {"operatingIncome", "OperatingIncome"}, {"ebitda", "EBITDA"}, {"netIncome", "NetIncome"}, {"eps", "DilutedEPS"},
            {"operatingCashFlow", "OperatingCashFlow"}, {"capex", "CapitalExpenditure"}, {"freeCashFlow", "FreeCashFlow"}, {"cash", "CashAndCashEquivalents"}, {"debt", "TotalDebt"}, {"assets", "TotalAssets"},
            {"liabilities", "TotalLiabilitiesNetMinorityInterest"}, {"equity", "StockholdersEquity"}};

    /** Income statement, balance sheet and cash flow by period, oldest first ("annual" or "quarterly"), in the currency the company reports in. */
    private static List<Object> statements(Map<String, Map<String, Object>> annual, Map<String, Map<String, Object>> quarterly, String period) {
        List<Object> out = new ArrayList<>();
        for (Map<String, Object> row : (period.equals("annual") ? annual : quarterly).values())
            if (row.get("revenue") != null || row.get("netIncome") != null) out.add(row);
        return out.size() > 8 ? out.subList(out.size() - 8, out.size()) : out;
    }

    /** Valuation, growth, profitability, financial health, analyst views and statements for one symbol. Parts Yahoo lacks (funds and indexes have no statements) are left empty. Cached 1 hour. */
    static Map<String, Object> fundamentals(String s) {
        return CACHE.get("fnd" + s, 3600_000L, () -> {
            String y = isGlobal(s) ? s : ySym(s); // SHEL.L stays as it is; BRK.B becomes BRK-B
            Map<String, Object> r = Map.of();
            try { r = map(list(map(call("/v10/finance/quoteSummary/" + enc(y) + "?modules=price,summaryDetail,defaultKeyStatistics,financialData,calendarEvents,earningsHistory,recommendationTrend", null, true).get("quoteSummary")).get("result")).stream().findFirst().orElse(null)); }
            catch (UpstreamException e) { if (e.code == 429) throw e; }
            Map<String, Map<String, Object>> annual = new TreeMap<>(), quarterly = new TreeMap<>();
            String reported = null;
            StringBuilder types = new StringBuilder();
            for (String[] f : STATEMENT) types.append("annual").append(f[1]).append(",quarterly").append(f[1]).append(",");
            long now = Instant.now().getEpochSecond();
            try {
                for (Object o : list(map(call("/ws/fundamentals-timeseries/v1/finance/timeseries/" + enc(y) + "?type=" + types + "&period1=" + (now - 7 * 366 * 86400L) + "&period2=" + now + "&merge=false", null, false).get("timeseries")).get("result"))) {
                    Map<String, Object> x = map(o);
                    String t = str(list(map(x.get("meta")).get("type")).stream().findFirst().orElse(null));
                    if (t == null) continue;
                    boolean isAnnual = t.startsWith("annual");
                    String name = t.substring(isAnnual ? 6 : 9);
                    String field = null;
                    for (String[] f : STATEMENT) if (f[1].equals(name)) field = f[0];
                    if (field == null) continue;
                    for (Object e : list(x.get(t))) {
                        Map<String, Object> m = map(e);
                        String d = str(m.get("asOfDate"));
                        Double v = raw(m.get("reportedValue"));
                        if (d == null || v == null) continue;
                        (isAnnual ? annual : quarterly).computeIfAbsent(d, k -> Json.obj("date", k)).put(field, v);
                        if (str(m.get("currencyCode")) != null) reported = str(m.get("currencyCode"));
                    }
                }
            } catch (UpstreamException e) { if (e.code == 429) throw e; }
            if (r.isEmpty() && annual.isEmpty() && quarterly.isEmpty()) throw new UpstreamException("No fundamentals for this symbol", 404);
            Map<String, Object> p = map(r.get("price")), sd = map(r.get("summaryDetail")), ks = map(r.get("defaultKeyStatistics")), fd = map(r.get("financialData"));
            Map<String, Object> key = new LinkedHashMap<>();
            Object[][] fields = {{"marketCap", p, "marketCap"}, {"pe", sd, "trailingPE"}, {"forwardPe", sd, "forwardPE"}, {"ps", sd, "priceToSalesTrailing12Months"}, {"pb", ks, "priceToBook"}, {"peg", ks, "pegRatio"}, {"evEbitda", ks, "enterpriseToEbitda"},
                    {"eps", ks, "trailingEps"}, {"forwardEps", ks, "forwardEps"}, {"dividendYield", sd, "dividendYield"}, {"payoutRatio", sd, "payoutRatio"}, {"beta", sd, "beta"}, {"high52", sd, "fiftyTwoWeekHigh"}, {"low52", sd, "fiftyTwoWeekLow"},
                    {"dayHigh", sd, "dayHigh"}, {"dayLow", sd, "dayLow"}, {"prevClose", sd, "previousClose"}, {"open", sd, "open"}, {"volume", sd, "volume"}, {"avgVolume", sd, "averageVolume"}, {"shares", ks, "sharesOutstanding"},
                    {"netAssets", ks, "totalAssets"}, {"fundYield", sd, "yield"}, {"grossMargin", fd, "grossMargins"}, {"operatingMargin", fd, "operatingMargins"}, {"profitMargin", fd, "profitMargins"}, {"roe", fd, "returnOnEquity"}, {"roa", fd, "returnOnAssets"},
                    {"revenueGrowth", fd, "revenueGrowth"}, {"earningsGrowth", fd, "earningsGrowth"}, {"cash", fd, "totalCash"}, {"debt", fd, "totalDebt"}, {"debtToEquity", fd, "debtToEquity"}, {"currentRatio", fd, "currentRatio"}, {"freeCashFlow", fd, "freeCashflow"}, {"revenue", fd, "totalRevenue"}};
            for (Object[] f : fields) { @SuppressWarnings("unchecked") Double v = raw(((Map<String, Object>) f[1]).get(f[2])); if (v != null) key.put((String) f[0], v); }
            Map<String, Object> analysts = null;
            if (raw(fd.get("targetMeanPrice")) != null || str(fd.get("recommendationKey")) != null) {
                Map<String, Object> tr = map(list(map(r.get("recommendationTrend")).get("trend")).stream().findFirst().orElse(null));
                analysts = Json.obj("target", raw(fd.get("targetMeanPrice")), "low", raw(fd.get("targetLowPrice")), "high", raw(fd.get("targetHighPrice")), "count", raw(fd.get("numberOfAnalystOpinions")), "rating", str(fd.get("recommendationKey")),
                        "strongBuy", num(tr.get("strongBuy")), "buy", num(tr.get("buy")), "hold", num(tr.get("hold")), "sell", num(tr.get("sell")), "strongSell", num(tr.get("strongSell")));
            }
            List<Object> earnings = new ArrayList<>();
            for (Object o : list(map(r.get("earningsHistory")).get("history"))) {
                Map<String, Object> m = map(o);
                if (raw(m.get("epsActual")) != null) earnings.add(Json.obj("quarter", day(m.get("quarter")), "actual", raw(m.get("epsActual")), "estimate", raw(m.get("epsEstimate")), "surprise", raw(m.get("surprisePercent"))));
            }
            String next = day(list(map(map(r.get("calendarEvents")).get("earnings")).get("earningsDate")).stream().findFirst().orElse(null));
            String fin = str(fd.get("financialCurrency")) != null ? str(fd.get("financialCurrency")) : reported;
            return Json.obj("symbol", s, "name", str(p.get("longName")) != null ? str(p.get("longName")) : str(p.get("shortName")), "type", str(p.get("quoteType")), "currency", str(p.get("currency")), "financialCurrency", fin, "price", raw(p.get("regularMarketPrice")),
                    "key", key, "analysts", analysts, "nextEarnings", next != null && next.compareTo(LocalDate.now(ZoneOffset.UTC).toString()) >= 0 ? next : null, "earnings", earnings,
                    "annual", statements(annual, quarterly, "annual"), "quarterly", statements(annual, quarterly, "quarterly"), "source", "Yahoo Finance");
        });
    }

    // ---------------------------------------------------------------- private companies (Yahoo Finance, data from Forge Global)

    static final java.util.regex.Pattern PVT = java.util.regex.Pattern.compile("^[A-Z0-9]{1,8}\\.PVT$");
    private static String day(Object epochSec) { Double d = epochSec instanceof Map ? raw(epochSec) : num(epochSec); return d == null ? null : LocalDate.ofEpochDay(Math.floorDiv(d.longValue(), 86400)).toString(); }

    /** Every private company Yahoo tracks: estimated valuation, funding, latest round and secondary-market price. Cached 30 minutes. */
    static Map<String, Object> privates() {
        return CACHE.get("pvt", 30 * 60_000L, () -> {
            Map<String, Map<String, Object>> by = new LinkedHashMap<>();
            String f = "ticker,symbol,companyName,regularMarketPrice,fiftyTwoWeekChangePercent,latestImpliedValuation,fundingToDate,latestFundingDate,latestAmountRaised,latestShareClass,sector";
            for (String id : List.of("HIGHEST_VALUATION_PRIVATE_COMPANY", "MOST_FUNDED_PRIVATE_COMPANY", "RECENTLY_FUNDED_PRIVATE_COMPANY")) {
                Map<String, Object> res;
                try { res = map(list(map(call("/v1/finance/screener/predefined/saved?scrIds=" + id + "&count=250&start=0&useRecordsResponse=true&fields=" + f, null, false).get("finance")).get("result")).stream().findFirst().orElse(null)); }
                catch (UpstreamException e) { if (by.isEmpty() && id.startsWith("HIGHEST")) throw e; continue; }
                for (Object o : list(res.get("records"))) {
                    Map<String, Object> m = map(o);
                    String sym = str(m.get("ticker")) != null ? str(m.get("ticker")) : str(m.get("symbol"));
                    if (sym == null || !PVT.matcher(sym).matches() || by.containsKey(sym)) continue;
                    // latestAmountRaised sometimes holds a share count (e.g. 68,209), so tiny amounts are dropped
                    by.put(sym, Json.obj("symbol", sym, "name", str(m.get("companyName")), "sector", str(m.get("sector")), "price", num(m.get("regularMarketPrice")),
                            "change52w", num(m.get("fiftyTwoWeekChangePercent")), "valuation", num(m.get("latestImpliedValuation")), "funding", num(m.get("fundingToDate")),
                            "lastRoundDate", day(m.get("latestFundingDate")), "lastRaised", num(m.get("latestAmountRaised")) != null && num(m.get("latestAmountRaised")) >= 1e5 ? num(m.get("latestAmountRaised")) : null, "lastRound", str(m.get("latestShareClass"))));
                }
            }
            if (by.isEmpty()) throw new UpstreamException("Private company data is unavailable right now");
            List<Object> out = new ArrayList<>(by.values());
            out.sort(Comparator.comparingDouble(o -> -Optional.ofNullable(num(map(o).get("valuation"))).orElse(0.0)));
            return Json.obj("companies", out, "source", "Yahoo Finance (Forge Global)", "updated", Instant.now().toString());
        });
    }

    /** One private company: profile, every funding round with post-money valuation, mutual-fund marks (with SEC filing links) and price history. Cached 6 hours. */
    static Map<String, Object> privateCompany(String s) {
        return CACHE.get("pvc" + s, 6 * 3600_000L, () -> {
            Map<String, Object> r = map(list(map(call("/v10/finance/quoteSummary/" + enc(s) + "?modules=price,assetProfile,fundingRounds,marks,summaryDetail", null, true).get("quoteSummary")).get("result")).stream().findFirst().orElse(null));
            if (r.isEmpty()) throw new UpstreamException("Company not found", 404);
            Map<String, Object> p = map(r.get("price")), ap = map(r.get("assetProfile")), sd = map(r.get("summaryDetail"));
            List<Object> rounds = new ArrayList<>(), marks = new ArrayList<>();
            for (Object o : list(map(r.get("fundingRounds")).get("fundingRoundList"))) {
                Map<String, Object> m = map(o);
                rounds.add(Json.obj("date", day(m.get("date")), "round", str(m.get("shareClassShortLabel")) != null ? str(m.get("shareClassShortLabel")) : str(m.get("shareClass")),
                        "raised", raw(m.get("amountRaised")) != null && raw(m.get("amountRaised")) >= 1e5 ? raw(m.get("amountRaised")) : null, "issuePrice", raw(m.get("issuePrice")), "valuation", raw(m.get("impliedValuation"))));
            }
            rounds.sort(Comparator.comparing(o -> String.valueOf(map(o).get("date")), Comparator.reverseOrder())); // newest first
            for (Object o : list(map(r.get("marks")).get("markList"))) {
                Map<String, Object> m = map(o);
                String link = str(m.get("filingLink"));
                marks.add(Json.obj("date", day(m.get("date")), "fund", str(m.get("fund")), "shareClass", str(m.get("shareClass")), "price", raw(m.get("price")),
                        "filing", link != null && link.startsWith("https://www.sec.gov/") ? link : null));
                if (marks.size() >= 12) break;
            }
            Map<String, Object> summary = null;
            for (Object o : list(privates().get("companies"))) if (s.equals(map(o).get("symbol"))) summary = map(o);
            List<Object> pts = new ArrayList<>();
            try { for (Object b : list(chart(s, "5y", "1wk").get("bars"))) pts.add(Json.obj("t", String.valueOf(map(b).get("t")).substring(0, 10), "c", map(b).get("c"))); } catch (UpstreamException ignored) {}
            String name = str(p.get("longName")) != null ? str(p.get("longName")) : str(p.get("shortName"));
            Double hi = raw(sd.get("fiftyTwoWeekHigh")), lo = raw(sd.get("fiftyTwoWeekLow"));
            if (hi == null && !pts.isEmpty()) { // not reported for private companies: from the last year of weekly prices
                String yearAgo = LocalDate.now(ZoneOffset.UTC).minusYears(1).toString();
                for (Object o : pts) { Map<String, Object> x = map(o); Double c = num(x.get("c")); if (c == null || String.valueOf(x.get("t")).compareTo(yearAgo) < 0) continue; hi = hi == null ? c : Math.max(hi, c); lo = lo == null ? c : Math.min(lo, c); }
            }
            return Json.obj("symbol", s, "name", name, "price", raw(p.get("regularMarketPrice")), "currency", str(p.get("currency")),
                    "high52", hi, "low52", lo,
                    "summary", summary, "sector", str(ap.get("sectorDisp")) != null ? str(ap.get("sectorDisp")) : str(ap.get("sector")), "industry", str(ap.get("industryDisp")),
                    "city", str(ap.get("city")), "state", str(ap.get("state")), "country", str(ap.get("country")), "website", str(ap.get("website")), "employees", num(ap.get("fullTimeEmployees")),
                    "description", str(ap.get("longBusinessSummary")), "rounds", rounds, "marks", marks, "history", pts, "source", "Yahoo Finance (Forge Global)");
        });
    }
}
