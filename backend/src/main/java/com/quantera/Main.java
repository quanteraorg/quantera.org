package com.quantera;

import com.quantera.Support.*;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import static com.quantera.Support.*;

/** Quantera API server. Zero dependencies: JDK HttpServer + java.net.http. Run: java -cp out com.quantera.Main */
public final class Main {
    interface Handler { Object handle(Req r) throws Exception; }
    record Req(String method, Map<String, String> query, Object body) {}
    record Route(Handler h, int ttlSec, int maxPerMin) {}

    static final Map<String, Route> ROUTES = new LinkedHashMap<>();
    static final RateLimiter LIMITER = new RateLimiter();
    static final Pattern NEWS_SYMBOLS = Pattern.compile("^[A-Z0-9.,\\-]{1,80}$"), NEWS_TEXT = Pattern.compile("^[\\w\\s.&'\\-]{1,60}$");
    static final List<String> INDEX = List.of("SPY", "QQQ", "DIA", "IWM"); // ETFs tracking the S&P 500, Nasdaq 100, Dow and Russell 2000

    static String symbol(Req r) {
        String s = r.query().getOrDefault("symbol", "").trim().toUpperCase();
        if (!SYM.matcher(s).matches()) throw new UpstreamException("Bad symbol", 400);
        return s;
    }

    /** Runs a task and returns its result, or null if it failed (used so one failing provider call doesn't sink a whole page). */
    static <T> CompletableFuture<Optional<T>> settle(Callable<T> c, List<Throwable> errors) {
        return CompletableFuture.supplyAsync(() -> {
            try { return Optional.ofNullable(c.call()); } catch (Throwable t) { errors.add(t); return Optional.<T>empty(); }
        });
    }

    static void routes() {
        ROUTES.put("/api/health", new Route(r -> Json.obj("ok", true, "market", Providers.marketOn(), "news", Providers.newsOn(), "ai", Providers.aiOn(), "storage", Store.on(), "quotes", Providers.finnhubOn() ? "finnhub" : "alphavantage", "charts", Providers.twelveOn() ? "twelvedata" : "alphavantage", "aiProvider", Anthropic.provider(), "intraday", Providers.twelveOn() || Yahoo.on(), "marketOpen", Providers.usMarketOpen(), "global", Yahoo.on(), "pushAlerts", Push.on(), "economyKey", cfg("FRED_API_KEY", null) != null, "lastPushError", Push.LAST_ERR, "lastProviderError", Providers.LAST_ERR, "lastGlobalError", Yahoo.LAST_ERR), 0, 120));
        ROUTES.put("/api/quote", new Route(r -> Providers.quote(symbol(r), 60_000), 60, 60));
        ROUTES.put("/api/history", new Route(r -> Providers.history(symbol(r), "1".equals(r.query().get("full"))), 3600, 60));
        ROUTES.put("/api/intraday", new Route(r -> Providers.intraday(symbol(r), r.query().getOrDefault("interval", "5min")), 60, 30));
        ROUTES.put("/api/overview", new Route(r -> Providers.overview(symbol(r)), 86400, 60));
        ROUTES.put("/api/search", new Route(r -> {
            String q = r.query().getOrDefault("q", "").trim();
            if (q.isEmpty() || q.length() > 40) throw new UpstreamException("Query must be 1 to 40 characters", 400);
            return Providers.search(q);
        }, 3600, 60));
        ROUTES.put("/api/quotes", new Route(r -> {
            Set<String> list = new LinkedHashSet<>();
            for (String s : r.query().getOrDefault("symbols", "").toUpperCase().split(",")) if (!s.isBlank()) list.add(s.trim());
            if (list.isEmpty() || list.size() > 25 || list.stream().anyMatch(s -> !SYM.matcher(s).matches())) throw new UpstreamException("Provide 1 to 25 valid symbols", 400);
            List<String> global = list.stream().filter(Yahoo::isGlobal).toList();
            if (Yahoo.on() && !global.isEmpty()) try { Yahoo.quotes(global); } catch (UpstreamException ignored) {} // one batch call warms them all
            List<Throwable> errs = new CopyOnWriteArrayList<>();
            Map<String, CompletableFuture<Optional<Map<String, Object>>>> f = new LinkedHashMap<>();
            for (String s : list) f.put(s, settle(() -> Providers.quote(s, 60_000), errs));
            List<Object> quotes = new ArrayList<>(), missing = new ArrayList<>();
            for (var e : f.entrySet()) { Optional<Map<String, Object>> o = e.getValue().join(); if (o.isPresent()) quotes.add(o.get()); else missing.add(e.getKey()); }
            if (quotes.isEmpty()) throw errs.get(0) instanceof RuntimeException re ? re : new UpstreamException("Market data provider error");
            return Json.obj("quotes", quotes, "missing", missing);
        }, 60, 60));
        ROUTES.put("/api/news", new Route(r -> {
            String symbols = r.query().getOrDefault("symbols", "").toUpperCase(), text = r.query().getOrDefault("q", "").trim();
            if (!symbols.isEmpty() && !NEWS_SYMBOLS.matcher(symbols).matches()) throw new UpstreamException("Bad symbols", 400);
            if (!text.isEmpty() && !NEWS_TEXT.matcher(text).matches()) throw new UpstreamException("Bad search text", 400);
            int limit = 6;
            try { limit = Integer.parseInt(r.query().getOrDefault("limit", "6")); } catch (NumberFormatException ignored) {}
            return Json.obj("articles", Providers.news(symbols, text, limit), "updated", java.time.Instant.now().toString());
        }, 20, 60));
        ROUTES.put("/api/article", new Route(r -> {
            String id = r.query().getOrDefault("id", "").trim().toLowerCase();
            if (!id.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new UpstreamException("Bad story id", 400);
            return Providers.article(id);
        }, 3600, 60));
        ROUTES.put("/api/calendar", new Route(r -> {
            String type = r.query().getOrDefault("type", "earnings");
            if (!type.equals("earnings") && !type.equals("ipo")) throw new UpstreamException("type must be earnings or ipo", 400);
            return Json.obj("type", type, "items", Providers.calendar(type));
        }, 3600, 60));
        ROUTES.put("/api/markets", new Route(r -> {
            Map<String, Object> warm = MARKETS;
            return warm != null ? warm : markets();
        }, 60, 30));
        ROUTES.put("/api/world", new Route(r -> Yahoo.world(), 60, 30));
        ROUTES.put("/api/macro", new Route(r -> Macro.dashboard(), 1800, 30));
        ROUTES.put("/api/filings", new Route(r -> Sec.filings(symbol(r)), 3600, 30));
        ROUTES.put("/api/financials", new Route(r -> Sec.financials(symbol(r)), 21600, 30));
        ROUTES.put("/api/push/key", new Route(r -> Push.key(), 3600, 30));
        ROUTES.put("/api/push/alerts", new Route(r -> { if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405); return Push.add(r.body()); }, 0, 30));
        ROUTES.put("/api/push/list", new Route(r -> { if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405); return Push.listFor(r.body()); }, 0, 30));
        ROUTES.put("/api/push/delete", new Route(r -> { if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405); return Push.remove(r.body()); }, 0, 30));
        ROUTES.put("/api/push/test", new Route(r -> { if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405); return Push.test(r.body()); }, 0, 6));
        ROUTES.put("/api/popular", new Route(r -> {
            String l = r.query().getOrDefault("list", "popular");
            if (!l.matches("popular|active|gainers|losers|trending")) throw new UpstreamException("list must be popular, active, gainers, losers or trending", 400);
            return Yahoo.popular(l);
        }, 60, 60));
        ROUTES.put("/api/world/country", new Route(r -> {
            String c = r.query().getOrDefault("c", "").toLowerCase(), kind = r.query().getOrDefault("kind", "stocks"), sector = r.query().getOrDefault("sector", ""), sort = r.query().getOrDefault("sort", "cap");
            if (!c.matches("[a-z]{2}") || !kind.matches("stocks|etfs|indexes") || !(sector.isEmpty() || Yahoo.SECTORS.contains(sector)) || !sort.matches("cap|gainers|losers|active")) throw new UpstreamException("Bad request", 400);
            int page = 1;
            try { page = Math.max(1, Math.min(Integer.parseInt(r.query().getOrDefault("page", "1")), 400)); } catch (NumberFormatException ignored) {}
            return Yahoo.country(c, kind, sector, page, sort);
        }, 120, 60));
        ROUTES.put("/api/snapshot", new Route(r -> Providers.snapshot(), 60, 30));
        ROUTES.put("/api/directory", new Route(r -> {
            String q = r.query().getOrDefault("q", "").trim(), type = r.query().getOrDefault("type", "").trim();
            if (q.length() > 40 || !q.matches("[\\w .&'-]*") || !type.matches("(?i)(|stock|etf|adr)")) throw new UpstreamException("Bad search", 400);
            int page = 1;
            try { page = Math.min(Integer.parseInt(r.query().getOrDefault("page", "1")), 1000); } catch (NumberFormatException ignored) {}
            return Providers.directory(q, type, page);
        }, 300, 60));
        ROUTES.put("/api/explain", new Route(r -> {
            if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405);
            return Anthropic.explain(r.body());
        }, 0, 20));
        ROUTES.put("/api/waitlist", new Route(r -> {
            if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405);
            return Store.joinWaitlist(r.body());
        }, 0, 5));
        ROUTES.put("/api/events", new Route(r -> {
            if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405);
            return Store.event(r.body());
        }, 0, 60));
        ROUTES.put("/api/ai", new Route(r -> {
            if (!r.method().equals("POST")) throw new UpstreamException("Use POST", 405);
            List<Object> m = list(map(r.body()).get("messages"));
            if (m.isEmpty() || m.size() > 20 || !"user".equals(map(m.get(m.size() - 1)).get("role"))) throw new UpstreamException("Invalid messages", 400);
            List<Map<String, Object>> msgs = new ArrayList<>();
            for (Object o : m) {
                Map<String, Object> x = map(o);
                Object role = x.get("role"), content = x.get("content");
                if (!("user".equals(role) || "assistant".equals(role)) || !(content instanceof String c) || c.isBlank() || c.length() > 2000) throw new UpstreamException("Invalid messages", 400);
                msgs.add(Json.obj("role", role, "content", c));
            }
            return Anthropic.useGemini() ? Gemini.chat(msgs) : Anthropic.chat(msgs);
        }, 0, 12));
    }

    /** Latest markets snapshot from the background refresh (null when the refresh is off or has not run yet). */
    static volatile Map<String, Object> MARKETS;

    static Map<String, Object> markets() {
        List<Throwable> errs = new CopyOnWriteArrayList<>();
        var mv = settle(Providers::movers, errs);
        List<CompletableFuture<Optional<Map<String, Object>>>> ix = new ArrayList<>();
        for (String s : INDEX) ix.add(settle(() -> Providers.quote(s, 5 * 60_000), errs));
        var btc = settle(() -> Providers.fx("BTC", "USD"), errs);
        var eth = settle(() -> Providers.fx("ETH", "USD"), errs);
        Object movers = mv.join().orElse(null);
        List<Object> indices = new ArrayList<>(), crypto = new ArrayList<>();
        ix.forEach(f -> f.join().ifPresent(indices::add));
        // Real index levels where the data plan allows it (Finnhub index symbols); otherwise the cards show the tracking fund
        Map<String, String> lvl = Map.of("SPY", "SPX", "QQQ", "NDX", "DIA", "DJI", "IWM", "RUT");
        if (Providers.finnhubOn() || Yahoo.on()) for (int i = 0; i < indices.size(); i++) {
            Map<String, Object> e = new LinkedHashMap<>(map(indices.get(i)));
            String name = lvl.get(String.valueOf(e.get("symbol")));
            if (name == null) continue;
            try { Map<String, Object> iq = Providers.indexQuote(name); e.put("level", iq.get("price")); e.put("levelChange", iq.get("change")); e.put("levelChangePct", iq.get("changePct")); e.put("levelSymbol", Providers.INDEX_SYM.get(name)); indices.set(i, e); }
            catch (RuntimeException ignored) {}
            if (e.get("level") != null && Yahoo.on()) try { // the index's own 30-day line rather than its fund's
                List<Object> bars = list(Providers.history(Providers.INDEX_SYM.get(name), false).get("bars")), sp = new ArrayList<>();
                for (int k = Math.max(0, bars.size() - 30); k < bars.size(); k++) sp.add(map(bars.get(k)).get("c"));
                e.put("levelSpark", sp);
            } catch (RuntimeException ignored) {}
        }
        List<Object> world = new ArrayList<>();
        if (Yahoo.on()) try {
            String[][] w = {{"^FTSE", "FTSE 100"}, {"^GDAXI", "DAX"}, {"^FCHI", "CAC 40"}, {"^STOXX50E", "Euro Stoxx 50"}, {"^N225", "Nikkei 225"}, {"^HSI", "Hang Seng"}, {"000001.SS", "Shanghai"}, {"^NSEI", "Nifty 50"}, {"^KS11", "KOSPI"}, {"^AXJO", "ASX 200"}, {"^GSPTSE", "TSX"}, {"^BVSP", "Ibovespa"}};
            List<String> syms = new ArrayList<>();
            for (String[] x : w) syms.add(x[0]);
            Map<String, Map<String, Object>> q = Yahoo.quotes(syms);
            for (String[] x : w) if (q.containsKey(x[0])) { Map<String, Object> e = new LinkedHashMap<>(q.get(x[0])); e.put("name", x[1]); world.add(e); }
        } catch (RuntimeException ignored) {}
        for (int i = 0; i < indices.size(); i++) { Map<String, Object> e = new LinkedHashMap<>(map(indices.get(i))); e.put("spark", Providers.spark(String.valueOf(e.get("symbol")))); indices.set(i, e); }
        for (var f : List.of(btc, eth)) f.join().ifPresent(c -> { Map<String, Object> e = new LinkedHashMap<>(map(c)); e.put("spark", Providers.spark(e.get("from") + "-USD")); crypto.add(e); });
        if (movers == null && indices.isEmpty() && crypto.isEmpty()) throw errs.isEmpty() ? new UpstreamException("Market data provider error") : (RuntimeException) errs.get(0);
        List<Object> assets = new ArrayList<>();
        if (Yahoo.on()) try {
            String[][] a = {{"GC=F", "Gold"}, {"SI=F", "Silver"}, {"CL=F", "Crude oil (WTI)"}, {"BZ=F", "Brent oil"}, {"NG=F", "Natural gas"}, {"HG=F", "Copper"}, {"^TNX", "US 10-year yield"}, {"DX-Y.NYB", "US dollar index"}, {"EURUSD=X", "EUR/USD"}};
            List<String> syms = new ArrayList<>();
            for (String[] x : a) syms.add(x[0]);
            Map<String, Map<String, Object>> q = Yahoo.quotes(syms);
            for (String[] x : a) if (q.containsKey(x[0])) { Map<String, Object> e = new LinkedHashMap<>(q.get(x[0])); e.put("name", x[1]); assets.add(e); }
        } catch (RuntimeException ignored) {}
        return Json.obj("movers", movers, "indices", indices, "world", world, "assets", assets, "crypto", crypto, "updated", java.time.Instant.now().toString());
    }

    /** Refreshes markets on a fixed schedule so upstream usage stays flat however many visitors arrive. Off unless MARKET_REFRESH_SEC is set (60 or more). */
    static void startRefresh() {
        int sec;
        try { sec = Integer.parseInt(cfg("MARKET_REFRESH_SEC", "0")); } catch (NumberFormatException e) { sec = 0; }
        if (sec < 60 || !Providers.marketOn()) return;
        long ms = sec * 1000L;
        Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory()).scheduleWithFixedDelay(() -> {
            try { MARKETS = markets(); } catch (RuntimeException e) { System.out.println("Markets refresh failed: " + e.getMessage()); }
        }, 0, ms, TimeUnit.MILLISECONDS);
        System.out.println("Markets refresh every " + sec + "s");
    }

    static final Map<String, String> TYPES = Map.of("html", "text/html; charset=utf-8", "js", "text/javascript", "css", "text/css", "json", "application/json", "mp4", "video/mp4", "png", "image/png", "svg", "image/svg+xml", "ico", "image/x-icon");

    /** Local development only: serves the frontend from STATIC_DIR so the site and API share one origin. */
    static boolean serveStatic(HttpExchange ex, String path) throws IOException {
        String dir = cfg("STATIC_DIR", "");
        if (dir.isEmpty() || path.startsWith("/api/") || !ex.getRequestMethod().equals("GET")) return false;
        java.nio.file.Path root = java.nio.file.Path.of(dir).toAbsolutePath().normalize(), f = root.resolve(path.equals("/") ? "index.html" : path.substring(1)).normalize();
        if (!f.startsWith(root) || !java.nio.file.Files.isRegularFile(f)) return false;
        String name = f.getFileName().toString();
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(name.substring(name.lastIndexOf('.') + 1), "application/octet-stream"));
        byte[] b = java.nio.file.Files.readAllBytes(f);
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        return true;
    }

    static Map<String, String> parseQuery(String raw) {
        Map<String, String> m = new HashMap<>();
        if (raw == null || raw.isEmpty()) return m;
        for (String p : raw.split("&")) {
            int i = p.indexOf('=');
            try { m.put(URLDecoder.decode(i < 0 ? p : p.substring(0, i), StandardCharsets.UTF_8), i < 0 ? "" : URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8)); } catch (IllegalArgumentException ignored) {}
        }
        return m;
    }

    static void send(HttpExchange ex, int code, Object body, int ttl) throws IOException {
        byte[] b = Json.write(body).getBytes(StandardCharsets.UTF_8);
        Headers h = ex.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=utf-8");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Cache-Control", ttl > 0 && code == 200 ? "public, s-maxage=" + ttl + ", stale-while-revalidate=" + ttl * 4 : "no-store");
        String origin = ex.getRequestHeaders().getFirst("Origin"), allowed = cfg("ALLOWED_ORIGIN", "");
        if (origin != null && Arrays.asList(allowed.split(",")).contains(origin)) {
            h.set("Access-Control-Allow-Origin", origin); h.set("Vary", "Origin");
            h.set("Access-Control-Allow-Headers", "content-type"); h.set("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
        }
        if (ex.getRequestMethod().equals("HEAD")) { ex.sendResponseHeaders(code, -1); ex.close(); return; } // HEAD responses carry no body
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    static void handle(HttpExchange ex) throws IOException {
        int ttl = 0;
        try {
            String method = ex.getRequestMethod(), path = ex.getRequestURI().getPath();
            if (method.equals("OPTIONS")) { send(ex, 204, Json.obj(), 0); return; }
            if (serveStatic(ex, path)) return;
            Route route = ROUTES.get(path);
            if (route == null) { send(ex, 404, Json.obj("error", "Not found"), 0); return; }
            if (!method.equals("GET") && !method.equals("POST")) { send(ex, 405, Json.obj("error", "Method not allowed"), 0); return; }
            String fwd = ex.getRequestHeaders().getFirst("X-Forwarded-For");
            String ip = fwd != null && !fwd.isBlank() ? fwd.split(",")[0].trim() : ex.getRemoteAddress().getAddress().getHostAddress();
            if (LIMITER.limited(ip, path, route.maxPerMin())) { send(ex, 429, Json.obj("error", "Too many requests"), 0); return; }
            Object body = null;
            if (method.equals("POST")) {
                byte[] raw = ex.getRequestBody().readNBytes(204_801);
                if (raw.length > 204_800) { send(ex, 413, Json.obj("error", "Request too large"), 0); return; }
                try { body = Json.parse(new String(raw, StandardCharsets.UTF_8)); } catch (IllegalArgumentException e) { send(ex, 400, Json.obj("error", "Invalid JSON"), 0); return; }
            }
            ttl = route.ttlSec();
            send(ex, 200, route.h().handle(new Req(method, parseQuery(ex.getRequestURI().getRawQuery()), body)), ttl);
        } catch (UpstreamException e) {
            send(ex, e.code, Json.obj("error", e.getMessage()), 0);
        } catch (Exception e) {
            e.printStackTrace();
            send(ex, 500, Json.obj("error", "Server error"), 0); // never leak internals or keys
        }
    }

    public static HttpServer start(int port) throws IOException {
        if (ROUTES.isEmpty()) routes();
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/", Main::handle);
        s.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        s.start();
        return s;
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(cfg("PORT", "8080"));
        start(port);
        System.out.println("Quantera API listening on :" + port + " (market=" + Providers.marketOn() + ", news=" + Providers.newsOn() + ", ai=" + Providers.aiOn() + ", storage=" + Store.on() + ")");
        startRefresh();
        Providers.startWarmer(); Providers.startPageWarmer();
        Push.start();
    }
}
