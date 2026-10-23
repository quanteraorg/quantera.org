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

    static boolean marketOn() { return cfg("ALPHAVANTAGE_API_KEY", null) != null; }
    static boolean newsOn() { return cfg("MARKETAUX_API_TOKEN", null) != null; }
    static boolean aiOn() { return cfg("ANTHROPIC_API_KEY", null) != null; }

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

    static Map<String, Object> quote(String s, long ttlMs) {
        return CACHE.get("q" + s, ttlMs, () -> {
            Map<String, Object> j = map(av(q("function", "GLOBAL_QUOTE", "symbol", s)).get("Global Quote"));
            if (num(j.get("05. price")) == null) throw new UpstreamException("Symbol not found", 404);
            return Json.obj("symbol", s, "price", num(j.get("05. price")), "change", num(j.get("09. change")), "changePct", num(j.get("10. change percent")),
                    "volume", num(j.get("06. volume")), "prevClose", num(j.get("08. previous close")), "asOf", str(j.get("07. latest trading day")), "source", "Alpha Vantage");
        });
    }

    static Map<String, Object> history(String s, boolean full) {
        return CACHE.get("h" + s + (full ? 1 : 0), 6 * 3600_000L, () -> {
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

    static Map<String, Object> search(String text) {
        return CACHE.get("s" + text.toLowerCase(), 3600_000L, () -> {
            List<Object> out = new ArrayList<>();
            for (Object o : list(av(q("function", "SYMBOL_SEARCH", "keywords", text)).get("bestMatches"))) {
                if (out.size() >= 10) break;
                Map<String, Object> m = map(o);
                out.add(Json.obj("symbol", str(m.get("1. symbol")), "name", str(m.get("2. name")), "type", str(m.get("3. type")), "region", str(m.get("4. region")), "currency", str(m.get("8. currency"))));
            }
            return Json.obj("results", out);
        });
    }

    static Map<String, Object> overview(String s) {
        return CACHE.get("o" + s, 24 * 3600_000L, () -> {
            Map<String, Object> j = av(q("function", "OVERVIEW", "symbol", s));
            if (str(j.get("Symbol")) == null) throw new UpstreamException("No company data for this symbol", 404);
            return Json.obj("symbol", str(j.get("Symbol")), "name", str(j.get("Name")), "type", str(j.get("AssetType")), "sector", str(j.get("Sector")), "industry", str(j.get("Industry")),
                    "country", str(j.get("Country")), "currency", str(j.get("Currency")), "exchange", str(j.get("Exchange")), "marketCap", num(j.get("MarketCapitalization")),
                    "pe", num(j.get("PERatio")), "beta", num(j.get("Beta")), "dividendYield", num(j.get("DividendYield")), "high52", num(j.get("52WeekHigh")), "low52", num(j.get("52WeekLow")),
                    "revenueGrowthYoY", num(j.get("QuarterlyRevenueGrowthYOY")), "profitMargin", num(j.get("ProfitMargin")), "roe", num(j.get("ReturnOnEquityTTM")), "description", str(j.get("Description")));
        });
    }

    static Map<String, Object> movers() {
        return CACHE.get("mv", 10 * 60_000L, () -> {
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
            Map<String, Object> j = map(av(q("function", "CURRENCY_EXCHANGE_RATE", "from_currency", from, "to_currency", to)).get("Realtime Currency Exchange Rate"));
            if (j.isEmpty()) throw new UpstreamException("No exchange rate available", 404);
            return Json.obj("from", from, "to", to, "name", str(j.get("2. From_Currency Name")), "rate", num(j.get("5. Exchange Rate")), "asOf", str(j.get("6. Last Refreshed")));
        });
    }

    static List<Object> calendar(String kind) {
        return CACHE.get("c" + kind, 12 * 3600_000L, () -> {
            boolean ipo = kind.equals("ipo");
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
            return new ArrayList<Object>(items.subList(0, Math.min(400, items.size())));
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

    static List<Object> news(String symbols, String text, int limit) {
        return CACHE.get("n" + symbols + "|" + text + "|" + limit, 15 * 60_000L, () -> {
            String key = cfg("MARKETAUX_API_TOKEN", null);
            if (key == null) throw new UpstreamException("News is not configured", 503);
            StringBuilder u = new StringBuilder(cfg("MARKETAUX_BASE_URL", "https://api.marketaux.com/v1")).append("/news/all?language=en&filter_entities=true&limit=").append(Math.min(Math.max(limit, 1), 10));
            if (!symbols.isEmpty()) u.append("&symbols=").append(URLEncoder.encode(symbols, StandardCharsets.UTF_8));
            if (!text.isEmpty()) u.append("&search=").append(URLEncoder.encode(text, StandardCharsets.UTF_8));
            u.append("&api_token=").append(URLEncoder.encode(key, StandardCharsets.UTF_8));
            HttpResponse<String> r;
            try { r = HTTP.send(HttpRequest.newBuilder(URI.create(u.toString())).timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
            catch (Exception e) { throw new UpstreamException("News provider error"); }
            if (r.statusCode() == 402 || r.statusCode() == 429) throw new UpstreamException("News rate limit reached. Try again later.", 429);
            if (r.statusCode() != 200) throw new UpstreamException("News provider error");
            List<Object> out = new ArrayList<>();
            try {
                for (Object o : list(map(Json.parse(r.body())).get("data"))) {
                    Map<String, Object> a = map(o);
                    List<Object> syms = new ArrayList<>();
                    for (Object e : list(a.get("entities"))) {
                        Map<String, Object> en = map(e);
                        if (str(en.get("symbol")) == null || syms.size() >= 5) continue;
                        syms.add(Json.obj("symbol", str(en.get("symbol")), "name", str(en.get("name")), "sentiment", num(en.get("sentiment_score"))));
                    }
                    String snippet = str(a.get("snippet")) != null ? str(a.get("snippet")) : str(a.get("description"));
                    out.add(Json.obj("id", str(a.get("uuid")), "title", str(a.get("title")), "summary", snippet == null ? "" : snippet, "url", str(a.get("url")), "source", str(a.get("source")), "publishedAt", str(a.get("published_at")), "symbols", syms));
                }
            } catch (IllegalArgumentException e) { throw new UpstreamException("News provider error"); }
            return out;
        });
    }
}
