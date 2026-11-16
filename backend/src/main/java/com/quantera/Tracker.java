package com.quantera;

import java.time.Instant;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Reaction tracker: Quantera's own dataset of how companies of different sizes react to the same news.
 *
 * Every few hours the server takes recent news stories that tag two or more US-listed companies, waits until the
 * first trading session after the story has closed, measures each company's reaction with Reaction.java (market part,
 * company-specific part, size of the move against its own normal day) and stores one row per company in Supabase
 * (table reaction_events, server-only). /api/reaction/tracker summarises the rows by company size.
 *
 * Kept small on purpose: at most a few new stories per run, so the free market-data plans' limits are respected.
 * What it measures is observation, not cause: other news the same day can move a stock too.
 */
final class Tracker {
    private Tracker() {}

    static final int PER_RUN = 3;
    static volatile String lastRun = null, lastError = null;

    static void start() {
        if (!Store.on()) return;
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory())
                .scheduleWithFixedDelay(() -> { try { run(); } catch (Throwable t) { lastError = t.getClass().getSimpleName() + ": " + t.getMessage(); } },
                        180, 3 * 3600, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** US-listed tickers the story tags (plain 1–5 letter symbols; foreign listings and private companies are left out). */
    static List<String> tickers(Map<String, Object> a) {
        List<String> out = new ArrayList<>();
        for (Object o : list(a.get("symbols"))) {
            String s = String.valueOf(o instanceof Map<?, ?> m ? m.get("symbol") : o).toUpperCase();
            if (s.matches("[A-Z]{1,5}") && !out.contains(s) && out.size() < 6) out.add(s);
        }
        return out;
    }

    static String key(Map<String, Object> a) {
        String k = String.valueOf(a.getOrDefault("id", a.getOrDefault("url", a.get("title"))));
        return k.length() > 120 ? k.substring(0, 120) : k;
    }

    static void run() {
        if (!Store.on()) return;
        int done = 0, seen = 0;
        // the company feed (same query as the daily challenge, so it shares that cache) tags companies far more often than the general feed
        List<Object> stories = new ArrayList<>();
        for (String q : List.of("SPY,QQQ,NVDA,AAPL,MSFT,AMZN,TSLA", "")) try { stories.addAll(Providers.news(q, "", 60)); } catch (RuntimeException ignored) { /* one feed down is fine */ }
        Set<String> keys = new HashSet<>();
        for (Object o : stories) {
            if (!keys.add(key(map(o)))) continue;
            if (done >= PER_RUN) break;
            Map<String, Object> a = map(o);
            List<String> syms = tickers(a);
            String at = str(a.get("publishedAt")), title = str(a.get("title"));
            if (syms.size() < 2 || at == null || title == null) continue;
            if (!sessionClosed(at, java.time.ZonedDateTime.now(Reaction.NY))) continue;
            seen++;
            String k = key(a);
            try { if (!list(Store.rest("GET", "reaction_events?select=id&limit=1&event_key=eq." + java.net.URLEncoder.encode(k, java.nio.charset.StandardCharsets.UTF_8), null)).isEmpty()) continue; }
            catch (UpstreamException e) { lastError = "storage: " + e.getMessage(); return; }
            Map<String, Object> r;
            try { r = Reaction.of(String.join(",", syms), at, title); } catch (UpstreamException e) { continue; }
            List<Object> rows = new ArrayList<>();
            // companies only: funds (SPY, QQQ, sector ETFs) have no company size, and the study compares company sizes
            for (Object c : list(r.get("companies"))) if (num(map(c).get("marketCap")) != null) rows.add(row(k, title, at, map(c)));
            if (rows.size() < 2) continue;
            try { Store.insertIgnore("reaction_events", rows, "event_key,symbol"); done++; }
            catch (UpstreamException e) { lastError = "storage: " + e.getMessage(); return; }
        }
        lastRun = Instant.now().toString(); lastCandidates = seen; lastAdded = done; if (done > 0) lastError = null;
    }

    static volatile int lastCandidates = 0, lastAdded = 0;

    /** Whether the first trading session that could react to news published at `at` has finished (16:30 New York, allowing for late prints). */
    static boolean sessionClosed(String at, java.time.ZonedDateTime nowNy) {
        java.time.LocalDate d;
        try { d = Reaction.sessionFor(at); } catch (RuntimeException e) { return false; }
        while (d.getDayOfWeek().getValue() > 5) d = d.plusDays(1); // a weekend story is first traded on Monday
        java.time.LocalDate today = nowNy.toLocalDate();
        return d.isBefore(today) || (d.equals(today) && nowNy.toLocalTime().isAfter(java.time.LocalTime.of(16, 30)));
    }

    static Map<String, Object> row(String key, String title, String at, Map<String, Object> c) {
        return Json.obj("event_key", key, "headline", title.length() > 300 ? title.substring(0, 300) : title, "published_at", at, "day0", c.get("day0"),
                "symbol", c.get("symbol"), "market_cap", c.get("marketCap"), "move_pct", c.get("move"), "market_pct", c.get("market"),
                "abnormal_pct", c.get("abnormal"), "typical_day", c.get("typicalDay"), "sigmas", c.get("sigmas"), "beta", c.get("beta"),
                "volume_ratio", c.get("volumeVsAverage"), "named", Boolean.TRUE.equals(c.get("namedInHeadline")));
    }

    static final String[][] BUCKETS = { {"Mega", "Over $200B"}, {"Large", "$10B–$200B"}, {"Mid", "$2B–$10B"}, {"Small", "Under $2B"} };
    static int bucket(double cap) { return cap >= 200e9 ? 0 : cap >= 10e9 ? 1 : cap >= 2e9 ? 2 : 3; }
    static double median(List<Double> v) { if (v.isEmpty()) return Double.NaN; List<Double> s = new ArrayList<>(v); Collections.sort(s); int n = s.size(); return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2; }
    static Double r2(double x) { return Double.isFinite(x) ? Math.round(x * 100) / 100.0 : null; }

    /** Size buckets, rank correlation between size and reaction strength, and the newest rows for the chart. Pure: unit-tested. */
    static Map<String, Object> summarise(List<Object> rows) {
        List<List<Double>> z = new ArrayList<>(), ab = new ArrayList<>();
        for (int i = 0; i < 4; i++) { z.add(new ArrayList<>()); ab.add(new ArrayList<>()); }
        List<double[]> pairs = new ArrayList<>();
        Set<String> events = new HashSet<>(), seenMoves = new HashSet<>(); String first = null, last = null;
        for (Object o : rows) {
            Map<String, Object> r = map(o);
            // the same company on the same day is one move, however many stories tagged it
            if (!seenMoves.add(r.get("symbol") + "|" + r.get("day0"))) { events.add(String.valueOf(r.get("event_key"))); continue; }
            Double cap = num(r.get("market_cap")), s = num(r.get("sigmas")), a = num(r.get("abnormal_pct"));
            events.add(String.valueOf(r.get("event_key")));
            String d = str(r.get("day0")); if (d != null) { if (first == null || d.compareTo(first) < 0) first = d; if (last == null || d.compareTo(last) > 0) last = d; }
            if (cap == null || cap <= 0 || s == null || a == null) continue;
            int b = bucket(cap); z.get(b).add(Math.abs(s)); ab.get(b).add(Math.abs(a));
            pairs.add(new double[]{Math.log10(cap), Math.abs(s)});
        }
        List<Object> bs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            List<Double> zi = z.get(i);
            long big = zi.stream().filter(x -> x >= 2).count();
            bs.add(Json.obj("name", BUCKETS[i][0], "range", BUCKETS[i][1], "n", zi.size(), "medianSigmas", r2(median(zi)), "medianAbnormal", r2(median(ab.get(i))),
                    "shareOver2", zi.isEmpty() ? null : r2(100.0 * big / zi.size())));
        }
        return Json.obj("rows", rows.size(), "measured", pairs.size(), "events", events.size(), "from", first, "to", last, "buckets", bs,
                "rankCorrelation", pairs.size() >= 10 ? r2(spearman(pairs)) : null);
    }

    static double spearman(List<double[]> p) {
        int n = p.size(); double[] a = ranks(p, 0), b = ranks(p, 1); double d2 = 0;
        for (int i = 0; i < n; i++) d2 += (a[i] - b[i]) * (a[i] - b[i]);
        return 1 - 6 * d2 / (n * ((double) n * n - 1));
    }
    static double[] ranks(List<double[]> p, int k) {
        Integer[] idx = new Integer[p.size()]; for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, Comparator.comparingDouble(i -> p.get(i)[k]));
        double[] r = new double[p.size()]; for (int i = 0; i < idx.length; i++) r[idx[i]] = i; return r;
    }

    static Map<String, Object> view() {
        if (!Store.on()) throw new UpstreamException("The reaction tracker isn't connected yet", 503);
        List<Object> rows = list(Store.rest("GET", "reaction_events?select=event_key,headline,day0,symbol,market_cap,move_pct,abnormal_pct,typical_day,sigmas,named&market_cap=not.is.null&order=day0.desc&limit=2000", null));
        Map<String, Object> s = summarise(rows);
        s.put("recent", rows.subList(0, Math.min(rows.size(), 400)));
        s.put("lastRun", lastRun); s.put("lastCandidates", lastCandidates); s.put("lastAdded", lastAdded);
        s.put("method", "Every few hours Quantera takes news stories that tag two or more US-listed companies, waits until the first trading session after the story has closed, and measures each company's reaction: the part the whole market explains (beta × the S&P 500's move) and the company-specific part, measured against the company's own normal day. Size is the company's market value when measured.");
        s.put("caveat", "These are observations of what happened after news, not proof that the news caused the move: other news, earnings or market events the same day can also move a stock. The stories come from Quantera's news providers, so the sample reflects what they cover. Education, not investment advice.");
        return s;
    }
}
