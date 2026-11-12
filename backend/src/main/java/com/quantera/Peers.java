package com.quantera;

import java.util.*;
import java.util.concurrent.*;

import static com.quantera.Support.*;

/**
 * Competitors side by side. Finnhub names a company's peers (same industry, similar size); each peer's figures come
 * from the same fundamentals source as the stock page (Yahoo Finance, trailing 12 months), fetched in parallel and
 * cached. A peer whose figures can't be loaded is left out rather than shown with gaps filled in.
 */
final class Peers {
    private Peers() {}

    static final java.util.regex.Pattern US = java.util.regex.Pattern.compile("^[A-Z]{1,5}([.-][A-Z])?$");
    static final String[] FIELDS = {"marketCap", "pe", "forwardPe", "ps", "evEbitda", "revenueGrowth", "grossMargin", "operatingMargin", "profitMargin", "roe", "debtToEquity", "dividendYield"};

    static Map<String, Object> of(String symbol) {
        if (!Providers.finnhubOn()) throw new UpstreamException("Competitor lists aren't available right now", 503);
        return Providers.CACHE.get("peers" + symbol, 6 * 3600_000L, () -> {
            List<String> names = new ArrayList<>();
            for (Object o : list(Providers.getJson("fh", "/stock/peers?symbol=" + java.net.URLEncoder.encode(symbol, java.nio.charset.StandardCharsets.UTF_8)))) {
                String p = str(o);
                if (p != null && US.matcher(p).matches() && !p.equals(symbol) && !names.contains(p)) names.add(p);
                if (names.size() >= 6) break;
            }
            if (names.isEmpty()) throw new UpstreamException("No competitors listed for this company", 404);
            List<String> all = new ArrayList<>();
            all.add(symbol);
            all.addAll(names);
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(all.size(), 7));
            try {
                List<Future<Map<String, Object>>> fs = new ArrayList<>();
                for (String s : all) fs.add(pool.submit(() -> row(s)));
                List<Object> rows = new ArrayList<>();
                for (Future<Map<String, Object>> f : fs) {
                    try { Map<String, Object> r = f.get(20, TimeUnit.SECONDS); if (r != null) rows.add(r); }
                    catch (Exception ignored) { /* this peer is left out */ }
                }
                if (rows.isEmpty() || !symbol.equals(str(map(rows.get(0)).get("symbol")))) throw new UpstreamException("Figures for this company couldn't be loaded", 502);
                return Json.obj("symbol", symbol, "peers", rows, "asOf", java.time.Instant.now().toString(),
                        "source", "Peer list: Finnhub. Figures: Yahoo Finance, trailing 12 months unless marked forward.");
            } finally { pool.shutdownNow(); }
        });
    }

    static Map<String, Object> row(String s) {
        Map<String, Object> f = Yahoo.fundamentals(s);
        if (!"EQUITY".equals(str(f.get("type")))) return null;
        Map<String, Object> k = map(f.get("key"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", s);
        out.put("name", str(f.get("name")));
        out.put("currency", str(f.get("currency")));
        out.put("price", num(f.get("price")));
        for (String x : FIELDS) out.put(x, num(k.get(x)));
        out.put("latestQuarter", str(f.get("latestQuarter")));
        return out;
    }
}
