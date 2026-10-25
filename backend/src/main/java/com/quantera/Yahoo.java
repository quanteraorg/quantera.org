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
    static synchronized String crumb(boolean refresh) {
        if (!refresh && CRUMB != null && System.currentTimeMillis() - CRUMB_AT < 6 * 3600_000L) return CRUMB;
        try {
            HTTP.send(HttpRequest.newBuilder(URI.create(cfg("YAHOO_COOKIE_URL", "https://fc.yahoo.com"))).timeout(Duration.ofSeconds(8)).header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {} // fc.yahoo.com answers 404 but still sets the cookie
        try {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(base() + "/v1/test/getcrumb")).timeout(Duration.ofSeconds(8)).header("User-Agent", UA).GET().build(), HttpResponse.BodyHandlers.ofString());
            String c = r.body() == null ? "" : r.body().trim();
            if (r.statusCode() != 200 || c.isEmpty() || c.length() > 40 || c.startsWith("{") || c.startsWith("<")) throw new UpstreamException("Global market data is unavailable right now");
            CRUMB = c; CRUMB_AT = System.currentTimeMillis();
            return c;
        } catch (UpstreamException e) { LAST_ERR = "yahoo crumb: refused"; throw e; }
        catch (Exception e) { LAST_ERR = "yahoo crumb: " + e.getClass().getSimpleName(); throw new UpstreamException("Global market data is unavailable right now"); }
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
                for (String s : part) { Map<String, Object> c = CACHE.peek("yq" + s); if (c != null) out.put(s, c); } // stale beats nothing
                err = e;
            }
        }
        if (out.isEmpty() && err != null) throw err;
        return out;
    }

    static Map<String, Object> quote(String s) {
        Map<String, Object> q = quotes(List.of(s)).get(s);
        if (q == null) throw new UpstreamException("Symbol not found", 404);
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
        Map<String, Object> r = map(list(map(call("/v10/finance/quoteSummary/" + enc(s) + "?modules=price,summaryProfile,summaryDetail,defaultKeyStatistics,financialData", null, true).get("quoteSummary")).get("result")).stream().findFirst().orElse(null));
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
            return Json.obj("regions", regions, "sectors", SECTORS, "source", "Yahoo Finance (delayed)", "updated", Instant.now().toString());
        });
    }

    static final int PER_PAGE = 25;

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
            Map<String, Object> r = map(list(map(call("/v1/finance/screener?lang=en-US&region=US", body, true).get("finance")).get("result")).stream().findFirst().orElse(null));
            List<Object> items = new ArrayList<>();
            for (Object o : list(r.get("quotes"))) { Map<String, Object> q = norm(map(o)); if (q.get("price") != null) { CACHE.put("yq" + q.get("symbol"), q); items.add(q); } }
            Double total = num(r.get("total"));
            return Json.obj("country", c.code(), "name", c.name(), "kind", kind, "sector", sector, "sort", sort, "items", items, "total", total == null ? items.size() : total.intValue(), "page", page, "perPage", PER_PAGE, "source", "Yahoo Finance (delayed)");
        });
    }

    /** Exchange suffixes Yahoo uses for non-US listings (BRK.B and BF.B stay US). */
    static final Set<String> SUFFIXES = Set.of("NS", "BO", "T", "L", "IL", "DE", "F", "PA", "AS", "BR", "LS", "MI", "MC", "SW", "VI", "ST", "OL", "CO", "HE", "IR", "WA", "AT", "PR", "BD",
            "HK", "SS", "SZ", "KS", "KQ", "TW", "TWO", "AX", "NZ", "SI", "JK", "KL", "BK", "PS", "TO", "V", "NE", "CN", "SA", "MX", "BA", "SN", "SR", "TA", "JO", "IS", "CA", "QA", "KW", "AE", "XC");

    static boolean isGlobal(String s) {
        if (s.startsWith("^") || s.contains("=")) return true;
        int d = s.lastIndexOf('.');
        return d > 0 && SUFFIXES.contains(s.substring(d + 1).toUpperCase());
    }
}
