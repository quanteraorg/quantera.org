package com.quantera;

import java.time.Instant;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Deep research on one company: a technical picture computed from real daily prices (no AI needed), and an AI-written
 * report built only from the figures Quantera already has (SEC filings, quote, fundamentals, analysts, peers, technicals,
 * headlines). The report ends with a Buy / Hold / Sell view, its confidence and what would change it. Every input is
 * listed with its source so the reader can check it.
 */
final class Research {
    private Research() {}

    // ---------- technicals ----------

    static double[] sma(double[] c, int n) {
        double[] o = new double[c.length]; Arrays.fill(o, Double.NaN); double s = 0;
        for (int i = 0; i < c.length; i++) { s += c[i]; if (i >= n) s -= c[i - n]; if (i >= n - 1) o[i] = s / n; }
        return o;
    }

    /** Exponential moving average seeded with the simple average of the first n values; NaN until then. */
    static double[] ema(double[] c, int n) {
        double[] o = new double[c.length]; Arrays.fill(o, Double.NaN);
        int start = 0; while (start < c.length && Double.isNaN(c[start])) start++;
        if (c.length - start < n) return o;
        double s = 0; for (int i = start; i < start + n; i++) s += c[i];
        double k = 2.0 / (n + 1), e = s / n; o[start + n - 1] = e;
        for (int i = start + n; i < c.length; i++) { e = c[i] * k + e * (1 - k); o[i] = e; }
        return o;
    }

    /** Wilder's RSI. */
    static double rsi(double[] c, int n) {
        if (c.length <= n) return Double.NaN;
        double g = 0, l = 0;
        for (int i = 1; i <= n; i++) { double d = c[i] - c[i - 1]; if (d > 0) g += d; else l -= d; }
        g /= n; l /= n;
        for (int i = n + 1; i < c.length; i++) { double d = c[i] - c[i - 1]; g = (g * (n - 1) + Math.max(d, 0)) / n; l = (l * (n - 1) + Math.max(-d, 0)) / n; }
        return l == 0 ? 100 : 100 - 100 / (1 + g / l);
    }

    private static double r2(double v) { return Double.isNaN(v) || Double.isInfinite(v) ? Double.NaN : Math.round(v * 100) / 100.0; }
    private static Object nn(double v) { return Double.isNaN(v) ? null : r2(v); }

    private static Map<String, Object> sig(List<Object> out, String name, double value, String verdict, String rule) {
        Map<String, Object> m = Json.obj("name", name, "value", nn(value), "signal", verdict, "rule", rule);
        out.add(m); return m;
    }

    static Map<String, Object> technicals(String s) {
        return Providers.CACHE.get("tech" + s, 3600_000L, () -> {
            Map<String, Object> h = Providers.history(s, true);
            List<Object> bars = list(h.get("bars"));
            if (bars.size() < 60) throw new UpstreamException("Not enough price history for technical analysis", 404);
            int n = bars.size();
            double[] c = new double[n], hi = new double[n], lo = new double[n];
            for (int i = 0; i < n; i++) { Map<String, Object> b = map(bars.get(i)); c[i] = num(b.get("c")); hi[i] = num(b.get("h")); lo[i] = num(b.get("l")); }
            double px = c[n - 1];
            double s20 = sma(c, 20)[n - 1], s50 = sma(c, 50)[n - 1], s200 = n >= 200 ? sma(c, 200)[n - 1] : Double.NaN;
            double[] e12 = ema(c, 12), e26 = ema(c, 26), macd = new double[n];
            for (int i = 0; i < n; i++) macd[i] = e12[i] - e26[i];
            double[] sg = ema(macd, 9);
            double m = macd[n - 1], ms = sg[n - 1], rs = rsi(c, 14);
            double h14 = -1e18, l14 = 1e18; for (int i = n - 14; i < n; i++) { h14 = Math.max(h14, hi[i]); l14 = Math.min(l14, lo[i]); }
            double st = h14 > l14 ? (px - l14) / (h14 - l14) * 100 : 50;
            int y = Math.min(252, n);
            double h52 = -1e18, l52 = 1e18; for (int i = n - y; i < n; i++) { h52 = Math.max(h52, hi[i]); l52 = Math.min(l52, lo[i]); }
            double sup = 1e18, res = -1e18; for (int i = n - 20; i < n; i++) { sup = Math.min(sup, lo[i]); res = Math.max(res, hi[i]); }
            double sum = 0, sq = 0; int k = 0; for (int i = n - 20; i < n; i++) { double r = Math.log(c[i] / c[i - 1]); sum += r; sq += r * r; k++; }
            double vol = Math.sqrt(Math.max(0, sq / k - (sum / k) * (sum / k)) * 252) * 100;
            double tr = 0; for (int i = n - 14; i < n; i++) tr += Math.max(hi[i] - lo[i], Math.max(Math.abs(hi[i] - c[i - 1]), Math.abs(lo[i] - c[i - 1])));
            double atr = tr / 14;
            java.util.function.IntFunction<Object> ret = d -> n > d ? r2((px / c[n - 1 - d] - 1) * 100) : null;

            List<Object> sigs = new ArrayList<>();
            sig(sigs, "Price vs 20-day average", s20, px > s20 ? "buy" : "sell", "Above the average is a short-term uptrend");
            sig(sigs, "Price vs 50-day average", s50, px > s50 ? "buy" : "sell", "Above the average is a medium-term uptrend");
            if (!Double.isNaN(s200)) {
                sig(sigs, "Price vs 200-day average", s200, px > s200 ? "buy" : "sell", "Above the average is a long-term uptrend");
                sig(sigs, "50-day vs 200-day average", s50 - s200, s50 > s200 ? "buy" : "sell", "50-day above 200-day (a \"golden cross\") is bullish; below (a \"death cross\") is bearish");
            }
            sig(sigs, "MACD vs signal line", m - ms, m > ms ? "buy" : "sell", "MACD (12, 26) above its 9-day signal line means momentum is improving");
            sig(sigs, "RSI (14 days)", rs, rs < 30 ? "buy" : rs > 70 ? "sell" : "neutral", "Under 30 is oversold (may bounce), over 70 overbought (may pull back), between is neutral");
            sig(sigs, "Stochastic %K (14 days)", st, st < 20 ? "buy" : st > 80 ? "sell" : "neutral", "Where the price sits in its 14-day range: under 20 oversold, over 80 overbought");
            Object r3 = ret.apply(63);
            if (r3 != null) sig(sigs, "3-month momentum (%)", (Double) r3, (Double) r3 > 0 ? "buy" : "sell", "Positive returns over 3 months tend to persist in the short run");

            int b = 0, se = 0;
            for (Object o : sigs) { String v = (String) map(o).get("signal"); if (v.equals("buy")) b++; else if (v.equals("sell")) se++; }
            double score = (b - se) / (double) sigs.size();
            String rating = score >= .5 ? "Strong buy" : score >= .1 ? "Buy" : score <= -.5 ? "Strong sell" : score <= -.1 ? "Sell" : "Neutral";
            String trend = !Double.isNaN(s200) ? (px > s50 && s50 > s200 ? "Uptrend" : px < s50 && s50 < s200 ? "Downtrend" : "Mixed") : (px > s50 ? "Uptrend" : "Downtrend");

            return Json.obj("symbol", s, "asOf", map(bars.get(n - 1)).get("t"), "price", r2(px), "source", h.get("source"),
                    "rating", rating, "score", r2(score), "counts", Json.obj("buy", b, "neutral", sigs.size() - b - se, "sell", se), "trend", trend,
                    "signals", sigs,
                    "levels", Json.obj("support20d", r2(sup), "resistance20d", r2(res), "high52w", r2(h52), "low52w", r2(l52),
                            "fromHighPct", r2((px / h52 - 1) * 100), "fromLowPct", r2((px / l52 - 1) * 100)),
                    "averages", Json.obj("sma20", nn(s20), "sma50", nn(s50), "sma200", nn(s200), "macd", nn(m), "macdSignal", nn(ms), "rsi14", nn(rs), "stochK", nn(st)),
                    "returns", Json.obj("1m", ret.apply(21), "3m", r3, "6m", ret.apply(126), "1y", ret.apply(252)),
                    "volatilityPct", r2(vol), "atr14", r2(atr),
                    "method", "Daily closes. Each signal is a standard rule (listed with it); the rating is (buy signals − sell signals) ÷ all signals: "
                            + "0.5 or more Strong buy, 0.1 or more Buy, −0.1 or less Sell, −0.5 or less Strong sell, otherwise Neutral. "
                            + "Technical signals describe past price action only; they are not forecasts.");
        });
    }

    // ---------- AI report ----------

    static final Set<String> RATINGS = Set.of("Buy", "Hold", "Sell"), CONF = Set.of("Low", "Medium", "High");

    static final String SYSTEM = "You are a careful equity research analyst writing for Quantera, a finance research and education app. "
            + "Use ONLY the figures and facts in the DATA block. Never invent numbers, events, quotes, products or dates; if something is missing, say it is not available. "
            + "After each figure, put its source and date in brackets, e.g. (SEC 10-K, FY2025), (Yahoo Finance, last 12 months), (technicals, daily closes to 2026-11-11). "
            + "Plain English that a beginner can follow, but analytical: compare against competitors and history, explain why each point matters. "
            + "Give a 12-month view of Buy, Hold or Sell, weighing valuation against peers and history, growth, profitability, financial health, analyst consensus, the technical trend and the risks. "
            + "Choose Hold when the evidence is mixed or the data is thin, and set confidence to match. Do not predict a specific price. "
            + "Reply with ONE JSON object and nothing else, exactly this shape: "
            + "{\"headline\":\"one sentence, at most 20 words\",\"summary\":\"3 to 4 sentences\","
            + "\"sections\":[{\"title\":\"Business\",\"points\":[\"...\"]},{\"title\":\"Growth and profitability\",\"points\":[]},{\"title\":\"Financial health\",\"points\":[]},"
            + "{\"title\":\"Valuation\",\"points\":[]},{\"title\":\"Competition\",\"points\":[]},{\"title\":\"Technical picture\",\"points\":[]},{\"title\":\"Recent news\",\"points\":[]}],"
            + "\"bull\":[\"3 points\"],\"bear\":[\"3 points\"],\"risks\":[\"3 points\"],\"catalysts\":[\"2 to 3 upcoming events or things to watch\"],"
            + "\"verdict\":{\"rating\":\"Buy|Hold|Sell\",\"confidence\":\"Low|Medium|High\",\"reasons\":[\"3 short reasons\"],\"changeMind\":\"one sentence: what would change this view\"}}. "
            + "Each section has 2 to 4 points of one or two sentences. If a section has no data, give one point saying so.";

    private static Object trim(Object o) {
        if (o instanceof String s) return s.length() > 700 ? s.substring(0, 700) + "…" : s;
        if (o instanceof Map<?, ?> m) { Map<String, Object> out = new LinkedHashMap<>(); for (Map.Entry<?, ?> e : m.entrySet()) if (e.getValue() != null) out.put(String.valueOf(e.getKey()), trim(e.getValue())); return out; }
        if (o instanceof List<?> l) { List<Object> out = new ArrayList<>(); for (Object x : l.subList(0, Math.min(l.size(), 12))) out.add(trim(x)); return out; }
        return o;
    }

    /** Everything the report may use, each part labelled with its source. Missing parts are left out and listed. */
    static Map<String, Object> pack(String s, List<Object> sources) {
        Map<String, Object> d = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        String today = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        try { d.put("company", trim(Providers.overview(s))); sources.add(src("Company profile", "Finnhub / Yahoo Finance", today)); } catch (RuntimeException e) { missing.add("company profile"); }
        try {
            Map<String, Object> q = Providers.quote(s, 5 * 60_000L);
            d.put("quote", Json.obj("price", q.get("price"), "changePct", q.get("changePct"), "time", q.get("time"), "note", "may be delayed up to 15 minutes"));
            sources.add(src("Price", "Finnhub / Yahoo Finance", String.valueOf(q.getOrDefault("time", today))));
        } catch (RuntimeException e) { missing.add("live price"); }
        try {
            Map<String, Object> f = Yahoo.fundamentals(s);
            d.put("keyRatios", Json.obj("source", "Yahoo Finance (last 12 months)", "values", f.get("key")));
            if (f.get("analysts") != null) { d.put("analystConsensus", Json.obj("source", "Yahoo Finance (Wall Street analysts; targets are forecasts)", "values", f.get("analysts"))); sources.add(src("Analyst consensus", "Yahoo Finance", today)); }
            d.put("earnings", Json.obj("source", "Yahoo Finance", "lastQuarters", f.get("earnings"), "nextReport", f.get("nextEarnings")));
            sources.add(src("Ratios and earnings", "Yahoo Finance (last 12 months)", today));
        } catch (RuntimeException e) { missing.add("ratios, earnings and analyst consensus"); }
        try { d.put("financialStatements", Anthropic.statementsForAi(s, false)); sources.add(src("Financial statements", "SEC EDGAR 10-K filings (last 5 years)", today)); } catch (RuntimeException e) { missing.add("SEC financial statements"); }
        try { d.put("competitors", trim(Peers.of(s))); sources.add(src("Competitors", "Finnhub peer list, ratios from Yahoo Finance", today)); } catch (RuntimeException e) { missing.add("competitors"); }
        try {
            Map<String, Object> t = new LinkedHashMap<>(technicals(s)); t.remove("method");
            d.put("technicals", t); sources.add(src("Technicals", "Computed by Quantera from daily closes (" + t.get("source") + ")", String.valueOf(t.get("asOf"))));
        } catch (RuntimeException e) { missing.add("technicals"); }
        try {
            List<Object> heads = new ArrayList<>();
            for (Object o : Providers.news(s, "", 6)) { Map<String, Object> a = map(o); heads.add(Json.obj("title", a.get("title"), "source", a.get("source"), "date", a.get("publishedAt"))); }
            if (!heads.isEmpty()) { d.put("recentHeadlines", heads); sources.add(src("Headlines", "News providers via Quantera", today)); }
        } catch (RuntimeException e) { missing.add("news"); }
        if (!missing.isEmpty()) d.put("notAvailable", missing);
        return d;
    }

    private static Map<String, Object> src(String what, String from, String asOf) { return Json.obj("what", what, "from", from, "asOf", asOf); }

    private static List<String> strings(Object o, int max) {
        List<String> out = new ArrayList<>();
        for (Object x : list(o)) { String t = x == null ? "" : String.valueOf(x).trim(); if (!t.isEmpty() && out.size() < max) out.add(t.length() > 600 ? t.substring(0, 600) + "…" : t); }
        return out;
    }

    /** Parse and check the model's JSON; anything malformed is an error rather than a half-shown report. */
    static Map<String, Object> parse(String text) {
        int a = text.indexOf('{'), b = text.lastIndexOf('}');
        if (a < 0 || b <= a) throw new UpstreamException("The AI returned an unreadable report. Please try again.");
        Map<String, Object> j;
        try { j = map(Json.parse(text.substring(a, b + 1))); } catch (RuntimeException e) { throw new UpstreamException("The AI returned an unreadable report. Please try again."); }
        Map<String, Object> v = map(j.get("verdict"));
        String rating = String.valueOf(v.get("rating")).trim(), conf = String.valueOf(v.get("confidence")).trim();
        if (!RATINGS.contains(rating)) throw new UpstreamException("The AI returned an unreadable report. Please try again.");
        List<Object> secs = new ArrayList<>();
        for (Object o : list(j.get("sections"))) { Map<String, Object> x = map(o); String t = str(x.get("title")); List<String> p = strings(x.get("points"), 5); if (t != null && !p.isEmpty()) secs.add(Json.obj("title", t, "points", p)); }
        return Json.obj("headline", Objects.toString(str(j.get("headline")), ""), "summary", Objects.toString(str(j.get("summary")), ""), "sections", secs,
                "bull", strings(j.get("bull"), 5), "bear", strings(j.get("bear"), 5), "risks", strings(j.get("risks"), 5), "catalysts", strings(j.get("catalysts"), 5),
                "verdict", Json.obj("rating", rating, "confidence", CONF.contains(conf) ? conf : "Low", "horizon", "12 months",
                        "reasons", strings(v.get("reasons"), 4), "changeMind", Objects.toString(str(v.get("changeMind")), "")));
    }

    static Map<String, Object> report(Object body) {
        String s = String.valueOf(map(body).getOrDefault("symbol", "")).trim().toUpperCase();
        if (!SYM.matcher(s).matches()) throw new UpstreamException("Bad symbol", 400);
        if (!Anthropic.useGemini() && cfg("ANTHROPIC_API_KEY", null) == null) throw new UpstreamException("The AI is not configured", 503);
        return Providers.CACHE.get("rep" + Anthropic.provider() + s, 12 * 3600_000L, () -> {
            List<Object> sources = new ArrayList<>();
            Map<String, Object> d = pack(s, sources);
            if (!d.containsKey("quote") && !d.containsKey("keyRatios") && !d.containsKey("financialStatements")) throw new UpstreamException("There isn't enough data on " + s + " for a report", 404);
            String prompt = "Company ticker: " + s + "\nToday's date: " + java.time.LocalDate.now(java.time.ZoneOffset.UTC) + "\n\nDATA:\n" + Json.write(d) + "\n\nWrite the report now as the JSON object described.";
            String text = Anthropic.complete(SYSTEM, prompt, 8000);
            Map<String, Object> co = map(d.get("company"));
            return Json.obj("symbol", s, "name", co.getOrDefault("name", s), "generatedAt", Instant.now().toString(), "provider", Anthropic.provider(),
                    "report", parse(text), "sources", sources, "notAvailable", d.getOrDefault("notAvailable", List.of()));
        });
    }
}
