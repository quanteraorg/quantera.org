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
        ROUTES.put("/api/health", new Route(r -> Json.obj("ok", true, "market", Providers.marketOn(), "news", Providers.newsOn(), "ai", Providers.aiOn()), 0, 120));
        ROUTES.put("/api/quote", new Route(r -> Providers.quote(symbol(r), 60_000), 60, 60));
        ROUTES.put("/api/history", new Route(r -> Providers.history(symbol(r), "1".equals(r.query().get("full"))), 3600, 60));
        ROUTES.put("/api/overview", new Route(r -> Providers.overview(symbol(r)), 86400, 60));
        ROUTES.put("/api/search", new Route(r -> {
            String q = r.query().getOrDefault("q", "").trim();
            if (q.isEmpty() || q.length() > 40) throw new UpstreamException("Query must be 1 to 40 characters", 400);
            return Providers.search(q);
        }, 3600, 60));
        ROUTES.put("/api/quotes", new Route(r -> {
            Set<String> list = new LinkedHashSet<>();
            for (String s : r.query().getOrDefault("symbols", "").toUpperCase().split(",")) if (!s.isBlank()) list.add(s.trim());
            if (list.isEmpty() || list.size() > 8 || list.stream().anyMatch(s -> !SYM.matcher(s).matches())) throw new UpstreamException("Provide 1 to 8 valid symbols", 400);
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
            return Json.obj("articles", Providers.news(symbols, text, limit));
        }, 600, 60));
        ROUTES.put("/api/calendar", new Route(r -> {
            String type = r.query().getOrDefault("type", "earnings");
            if (!type.equals("earnings") && !type.equals("ipo")) throw new UpstreamException("type must be earnings or ipo", 400);
            return Json.obj("type", type, "items", Providers.calendar(type));
        }, 3600, 60));
        ROUTES.put("/api/markets", new Route(r -> {
            List<Throwable> errs = new CopyOnWriteArrayList<>();
            var mv = settle(Providers::movers, errs);
            List<CompletableFuture<Optional<Map<String, Object>>>> ix = new ArrayList<>();
            for (String s : INDEX) ix.add(settle(() -> Providers.quote(s, 5 * 60_000), errs));
            var btc = settle(() -> Providers.fx("BTC", "USD"), errs);
            var eth = settle(() -> Providers.fx("ETH", "USD"), errs);
            Object movers = mv.join().orElse(null);
            List<Object> indices = new ArrayList<>(), crypto = new ArrayList<>();
            ix.forEach(f -> f.join().ifPresent(indices::add));
            btc.join().ifPresent(crypto::add); eth.join().ifPresent(crypto::add);
            if (movers == null && indices.isEmpty() && crypto.isEmpty()) throw errs.isEmpty() ? new UpstreamException("Market data provider error") : (RuntimeException) errs.get(0);
            return Json.obj("movers", movers, "indices", indices, "crypto", crypto, "updated", java.time.Instant.now().toString());
        }, 300, 30));
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
            return Anthropic.chat(msgs);
        }, 0, 12));
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
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    static void handle(HttpExchange ex) throws IOException {
        int ttl = 0;
        try {
            String method = ex.getRequestMethod(), path = ex.getRequestURI().getPath();
            if (method.equals("OPTIONS")) { send(ex, 204, Json.obj(), 0); return; }
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
        System.out.println("Quantera API listening on :" + port + " (market=" + Providers.marketOn() + ", news=" + Providers.newsOn() + ", ai=" + Providers.aiOn() + ")");
    }
}
