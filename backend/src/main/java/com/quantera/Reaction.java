package com.quantera;

import java.time.*;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Reaction gap: why the same news moved one company 5% and another 25%.
 *
 * For each company, the event-day move is split into the part the whole market explains (beta × the S&P 500's move,
 * with beta estimated from the 120 trading days before the news) and the company-specific ("abnormal") part. That
 * abnormal move is then measured against the company's own normal day (the spread of its abnormal moves before the
 * news), so a 25% move by a stock that often moves 8% a day can be compared fairly with a 5% move by one that rarely
 * moves 1%. Size, valuation, how directly the news names the company, trading volume and the run-up before the news
 * are reported next to it. Everything observed is kept apart from interpretation, and interpretations are written as
 * typical patterns, never as proof of cause.
 */
final class Reaction {
    private Reaction() {}

    static final String BENCH = "SPY";
    static final int EST = 120, MIN_EST = 40;
    static final ZoneId NY = ZoneId.of("America/New_York");

    /** The first trading session that could react to news published at this moment. */
    static LocalDate sessionFor(String at) {
        String a = at == null ? "" : at.trim();
        if (a.matches("\\d{4}-\\d{2}-\\d{2}")) return LocalDate.parse(a);
        ZonedDateTime t;
        try { t = Instant.parse(a).atZone(NY); }
        catch (DateTimeException e) {
            try { t = OffsetDateTime.parse(a).atZoneSameInstant(NY); } catch (DateTimeException e2) { throw new UpstreamException("Bad date", 400); }
        }
        // news after the 4 p.m. close can only move the next session's price
        return t.toLocalTime().isBefore(LocalTime.of(16, 0)) ? t.toLocalDate() : t.toLocalDate().plusDays(1);
    }

    record Series(List<String> t, double[] c, double[] v) {}

    static Series series(String s) {
        List<Object> bars = list(Providers.history(s, true).get("bars"));
        List<String> t = new ArrayList<>(); List<Double> c = new ArrayList<>(), v = new ArrayList<>();
        for (Object o : bars) {
            Map<String, Object> b = map(o); Double cl = num(b.get("c"));
            if (cl == null || cl <= 0) continue;
            t.add(String.valueOf(b.get("t")).substring(0, 10)); c.add(cl); Double vv = num(b.get("v")); v.add(vv == null ? 0 : vv);
        }
        return new Series(t, c.stream().mapToDouble(x -> x).toArray(), v.stream().mapToDouble(x -> x).toArray());
    }

    static int indexOn(Series s, LocalDate d) {
        String k = d.toString();
        for (int i = 0; i < s.t().size(); i++) if (s.t().get(i).compareTo(k) >= 0) return i;
        return -1;
    }

    static Double r2(double x) { return Double.isFinite(x) ? Math.round(x * 100) / 100.0 : null; }
    static Double pct(double x) { return Double.isFinite(x) ? Math.round(x * 10000) / 100.0 : null; }

    /** One company's reaction, measured against the market and against its own normal day. */
    static Map<String, Object> one(String s, LocalDate day, Series m, String headline) {
        Map<String, Object> ov = Map.of();
        try { ov = Providers.overview(s); } catch (RuntimeException ignored) { /* size and valuation are optional context */ }
        return measure(s, series(s), day, m, headline, ov);
    }

    /** The measurements for one company, from its daily closes and the market's. */
    static Map<String, Object> measure(String s, Series x, LocalDate day, Series m, String headline, Map<String, Object> ov) {
        int i0 = indexOn(x, day);
        if (i0 < 0) throw new UpstreamException("No trading day for " + s + " on or after " + day + " yet", 404);
        if (i0 < MIN_EST + 2) throw new UpstreamException("Not enough price history for " + s + " before " + day, 404);
        Map<String, Integer> mi = new HashMap<>();
        for (int i = 0; i < m.t().size(); i++) mi.put(m.t().get(i), i);

        // estimation window: daily returns ending two sessions before the news, matched to the market by date
        List<double[]> pairs = new ArrayList<>();
        for (int i = Math.max(1, i0 - 1 - EST); i <= i0 - 2; i++) {
            Integer a = mi.get(x.t().get(i)), b = mi.get(x.t().get(i - 1));
            if (a == null || b == null || a != b + 1) continue;
            pairs.add(new double[]{x.c()[i] / x.c()[i - 1] - 1, m.c()[a] / m.c()[b] - 1});
        }
        if (pairs.size() < MIN_EST) throw new UpstreamException("Not enough overlapping history for " + s, 404);
        double mx = 0, my = 0;
        for (double[] p : pairs) { my += p[0]; mx += p[1]; }
        mx /= pairs.size(); my /= pairs.size();
        double sxy = 0, sxx = 0;
        for (double[] p : pairs) { sxy += (p[1] - mx) * (p[0] - my); sxx += (p[1] - mx) * (p[1] - mx); }
        double beta = sxx > 0 ? sxy / sxx : 1, alpha = my - beta * mx, ss = 0, sr = 0;
        for (double[] p : pairs) { double e = p[0] - alpha - beta * p[1]; ss += e * e; sr += p[0] * p[0]; }
        double sigma = Math.sqrt(ss / Math.max(1, pairs.size() - 2)), sigmaRaw = Math.sqrt(sr / pairs.size());

        // the event day itself
        Integer m0 = mi.get(x.t().get(i0)), m1 = mi.get(x.t().get(i0 - 1));
        double r0 = x.c()[i0] / x.c()[i0 - 1] - 1;
        Double rm = m0 != null && m1 != null && m0 == m1 + 1 ? m.c()[m0] / m.c()[m1] - 1 : null;
        double marketPart = rm == null ? 0 : beta * rm, abn = r0 - marketPart, z = sigma > 0 ? abn / sigma : 0;

        // three sessions (day 0 to +2), when they have happened
        Map<String, Object> w3 = null;
        if (i0 + 2 < x.c().length && m0 != null && m1 != null && m0 + 2 < m.c().length) {
            double c3 = x.c()[i0 + 2] / x.c()[i0 - 1] - 1, cm = m.c()[m0 + 2] / m.c()[m1] - 1;
            w3 = Json.obj("move", pct(c3), "market", pct(beta * cm), "abnormal", pct(c3 - beta * cm));
        }
        // did the price get back to where it was before the news (within 20 sessions)?
        Map<String, Object> rec = null;
        if (Math.abs(r0) >= 0.01 && i0 + 1 < x.c().length) {
            double base = x.c()[i0 - 1]; Integer back = null; int last = Math.min(x.c().length - 1, i0 + 20);
            for (int k = i0 + 1; k <= last; k++) if (r0 < 0 ? x.c()[k] >= base : x.c()[k] <= base) { back = k - i0; break; }
            rec = Json.obj("sessionsChecked", last - i0, "recoveredAfter", back, "nowVsBefore", pct(x.c()[last] / base - 1));
        }
        double avgVol = 0; int nv = 0;
        for (int i = Math.max(0, i0 - 21); i <= i0 - 1; i++) if (x.v()[i] > 0) { avgVol += x.v()[i]; nv++; }
        Double volRatio = nv > 0 && x.v()[i0] > 0 ? r2(x.v()[i0] / (avgVol / nv)) : null;
        Double runUp = i0 >= 21 ? pct(x.c()[i0 - 1] / x.c()[i0 - 21] - 1) : null;

        String name = String.valueOf(ov.getOrDefault("name", s));
        return Json.obj("symbol", s, "name", name, "sector", ov.get("sector"), "marketCap", ov.get("marketCap"), "pe", ov.get("pe"),
                "day0", x.t().get(i0), "prevClose", r2(x.c()[i0 - 1]), "close", r2(x.c()[i0]),
                "move", pct(r0), "market", pct(marketPart), "abnormal", pct(abn), "typicalDay", pct(sigma), "typicalDayRaw", pct(sigmaRaw),
                "sigmas", r2(z), "beta", r2(beta), "estimationDays", pairs.size(), "benchmarkMove", rm == null ? null : pct(rm),
                "threeDay", w3, "recovery", rec, "volumeVsAverage", volRatio, "runUp20d", runUp, "namedInHeadline", named(headline, s, name));
    }

    /** Whether the headline names the company (ticker or the first word of its name, e.g. "Nvidia"). */
    static boolean named(String headline, String s, String name) {
        if (headline == null || headline.isBlank()) return false;
        String h = " " + headline.toLowerCase().replaceAll("[^a-z0-9$.]+", " ") + " ";
        if (h.contains(" " + s.toLowerCase() + " ") || h.contains(" $" + s.toLowerCase() + " ")) return true;
        String w = name.toLowerCase().replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+")[0];
        return w.length() >= 3 && !Set.of("the", "inc", "corp", "first", "american", "united", "general", "global", "international").contains(w) && h.contains(" " + w + " ");
    }

    static String fmtCap(Object o) {
        Double c = num(o); if (c == null) return null;
        return c >= 1e12 ? String.format("$%.2fT", c / 1e12) : c >= 1e9 ? String.format("$%.1fB", c / 1e9) : String.format("$%.0fM", c / 1e6);
    }
    static double d(Object o) { Double x = num(o); return x == null ? Double.NaN : x; }
    static String p(double x) { return (x > 0 ? "+" : x < 0 ? "−" : "") + String.format("%.1f%%", Math.abs(x)); }

    /** Plain-English reasons for the gap between the biggest and smallest reaction. Each says whether it is a measurement or a typical pattern. */
    static List<Object> explain(Map<String, Object> big, Map<String, Object> small) {
        List<Object> out = new ArrayList<>();
        String B = String.valueOf(big.get("symbol")), S = String.valueOf(small.get("symbol"));
        double mb = d(big.get("move")), ms = d(small.get("move")), ab = d(big.get("abnormal")), as = d(small.get("abnormal"));
        double tb = d(big.get("typicalDay")), ts = d(small.get("typicalDay")), zb = Math.abs(d(big.get("sigmas"))), zs = Math.abs(d(small.get("sigmas")));
        // 1. the market's share
        double kb = d(big.get("market")), ks = d(small.get("market"));
        out.add(Json.obj("factor", "The whole market", "kind", "measured", "weight", Math.abs(kb - ks) / Math.max(0.01, Math.abs(mb - ms)),
                "text", String.format("The S&P 500's move that day explains %s of %s's %s and %s of %s's %s (each stock's usual sensitivity to the market, its beta, is %s and %s). The rest is company-specific: %s versus %s.",
                        p(kb), B, p(mb), p(ks), S, p(ms), big.get("beta"), small.get("beta"), p(ab), p(as))));
        // 2. normal volatility
        if (tb > 0 && ts > 0) {
            boolean similar = Math.max(zb, zs) / Math.max(0.1, Math.min(zb, zs)) < 1.6;
            out.add(Json.obj("factor", "How much each stock normally moves", "kind", "measured", "weight", similar ? 2.0 : Math.min(1.5, Math.abs(Math.log(tb / ts))),
                    "text", String.format("On a normal day %s's company-specific move is about ±%.1f%% and %s's about ±%.1f%%. Measured that way, %s's reaction was %.1f× its normal day and %s's %.1f× its normal day. %s",
                            B, tb, S, ts, B, zb, S, zs, similar ? "So relative to how each usually trades, the two reactions were similar in size: much of the headline gap is simply that " + (tb > ts ? B : S) + " is a more volatile stock."
                                    : zb > zs ? "Even allowing for that, " + B + " reacted far more strongly than usual: the news mattered more to it." : "Allowing for that, " + S + " actually reacted more strongly relative to its normal day.")));
        }
        // 3. size
        double cb = d(big.get("marketCap")), cs = d(small.get("marketCap"));
        if (cb > 0 && cs > 0 && Math.max(cb, cs) / Math.min(cb, cs) >= 2) {
            boolean smallerMovedMore = cb < cs;
            out.add(Json.obj("factor", "Company size", "kind", "pattern", "weight", smallerMovedMore ? Math.min(1.5, Math.log10(cs / cb)) : 0.3,
                    "text", smallerMovedMore
                            ? String.format("%s (%s) is about %.0f× smaller than %s (%s). Smaller companies usually react more to the same news: one product, contract or rule can be a large share of their business, fewer analysts and funds follow them, and their shares trade less, so prices adjust in bigger steps.", B, fmtCap(cb), cs / cb, S, fmtCap(cs))
                            : String.format("%s (%s) is the larger company yet moved more than %s (%s), so size alone doesn't explain this gap: the news was probably more important to %s's business.", B, fmtCap(cb), S, fmtCap(cs), B)));
        }
        // 4. how directly the news is about the company
        boolean nb = Boolean.TRUE.equals(big.get("namedInHeadline")), ns = Boolean.TRUE.equals(small.get("namedInHeadline"));
        if (nb != ns) out.add(Json.obj("factor", "Who the news is about", "kind", "pattern", "weight", 1.2,
                "text", nb ? String.format("The headline names %s but not %s. News about a company directly usually moves it more than companies that are only connected to it (competitors, suppliers, the same sector).", B, S)
                        : String.format("The headline names %s, yet %s moved more: the market may see %s as more exposed to what the news describes, or other news moved it the same day.", S, B, B)));
        // 5. trading activity
        double vb = d(big.get("volumeVsAverage")), vs = d(small.get("volumeVsAverage"));
        if (vb > 0 && vs > 0) out.add(Json.obj("factor", "Trading activity", "kind", "measured", "weight", Math.min(1.2, Math.abs(Math.log(vb / vs))),
                "text", String.format("%s traded %.1f× its usual volume and %s %.1f× its usual volume. %s", B, vb, S, vs,
                        vb > vs * 1.3 ? "Far more trading in " + B + " suggests investors were actively repricing it." : vs > vb * 1.3 ? "Heavier trading in " + S + " despite its smaller move suggests buyers and sellers were more evenly matched." : "Trading picked up by a similar amount in both.")));
        // 6. valuation
        double pb = d(big.get("pe")), ps = d(small.get("pe"));
        if (pb > 0 && ps > 0 && Math.max(pb, ps) / Math.min(pb, ps) >= 1.5) out.add(Json.obj("factor", "Valuation", "kind", "pattern", "weight", pb > ps ? 0.8 : 0.3,
                "text", String.format("%s trades at %.0f times earnings and %s at %.0f. %s", B, pb, S, ps,
                        pb > ps ? "A higher valuation means more of the price rests on future growth, so news that changes the outlook tends to move it more." : "The more expensive stock moved less, so valuation isn't the main driver here.")));
        // 7. run-up before the news
        double ub = d(big.get("runUp20d")), us = d(small.get("runUp20d"));
        if (Double.isFinite(ub) && Double.isFinite(us) && Math.abs(ub - us) >= 8) out.add(Json.obj("factor", "Run-up before the news", "kind", "pattern", "weight", 0.6,
                "text", String.format("In the month before, %s had moved %s and %s %s. A stock that has already risen a lot can fall harder on disappointing news (expectations were high), and one that has fallen can bounce more on good news.", B, p(ub), S, p(us))));
        out.sort((a, b) -> Double.compare(d(map(b).get("weight")), d(map(a).get("weight"))));
        return out;
    }

    static Map<String, Object> of(String symbols, String at, String headline) {
        List<String> syms = new ArrayList<>();
        for (String s : symbols.toUpperCase().split(",")) { s = s.trim(); if (!s.isEmpty() && SYM.matcher(s).matches() && !syms.contains(s) && syms.size() < 8) syms.add(s); }
        if (syms.size() < 2) throw new UpstreamException("Choose at least two companies", 400);
        LocalDate day = sessionFor(at);
        if (day.isBefore(LocalDate.of(2000, 1, 1)) || day.isAfter(LocalDate.now(NY).plusDays(3))) throw new UpstreamException("Bad date", 400);
        String h = headline == null ? "" : headline.length() > 300 ? headline.substring(0, 300) : headline;
        return Providers.CACHE.get("rx" + syms + day + h.hashCode(), 6 * 3600_000L, () -> {
            Series m = series(BENCH);
            List<Object> rows = new ArrayList<>(), skipped = new ArrayList<>();
            for (String s : syms) {
                try { rows.add(one(s, day, m, h)); }
                catch (UpstreamException e) { skipped.add(Json.obj("symbol", s, "reason", e.getMessage())); }
            }
            if (rows.size() < 2) throw new UpstreamException(skipped.isEmpty() ? "Not enough data" : String.valueOf(map(skipped.get(0)).get("reason")), 404);
            rows.sort((a, b) -> Double.compare(Math.abs(d(map(b).get("move"))), Math.abs(d(map(a).get("move")))));
            Map<String, Object> big = map(rows.get(0)), small = map(rows.get(rows.size() - 1));
            return Json.obj("day0", big.get("day0"), "requested", at, "benchmark", "S&P 500 (SPY)", "companies", rows, "skipped", skipped,
                    "gap", Json.obj("bigger", big.get("symbol"), "smaller", small.get("symbol"), "points", r2(Math.abs(d(big.get("move"))) - Math.abs(d(small.get("move"))))),
                    "reasons", explain(big, small),
                    "method", "Day 0 is the first trading session after the news (news after the 4 p.m. New York close counts from the next day). Market part = the company's beta × the S&P 500's move that day, with beta estimated from up to "
                            + EST + " trading days ending two sessions before the news. Company-specific (abnormal) move = actual move − market part. Normal day = the typical size (standard deviation) of the company-specific moves in that same window. Prices are daily closes from "
                            + "Quantera's data providers and are not adjusted for dividends.",
                    "caveat", "These are measurements of what happened and typical patterns that often explain gaps like this. They do not prove why prices moved: other news, earnings or market events on the same day can also move a stock. Education, not investment advice.");
        });
    }
}
