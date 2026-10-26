package com.quantera;

import com.quantera.Support.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static com.quantera.Support.*;

/**
 * US economy dashboard from the Federal Reserve Bank of St. Louis (FRED): inflation, jobs, growth, rates, consumers and housing.
 * Uses FRED's public CSV download (no key). FRED_BASE_URL points it elsewhere (tests). Cached 6 hours; series update monthly or weekly.
 */
final class Macro {
    private Macro() {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    static final Cache CACHE = Providers.CACHE;

    /** id, group, name, how to show it (level / yoy / mom / momPct), unit, plain-English meaning, which direction is "good" (up/down/none) */
    record S(String id, String group, String name, String how, String unit, String what, String good) {}
    static final List<S> SERIES = List.of(
        new S("CPIAUCSL", "Inflation", "Inflation (CPI)", "yoy", "%", "How much prices rose over the past year. The Fed aims for about 2%. Higher inflation usually means higher interest rates, which weighs on stocks and bonds.", "down"),
        new S("CPILFESL", "Inflation", "Core inflation", "yoy", "%", "Inflation without food and energy, which jump around. Central banks watch it to see the underlying trend.", "down"),
        new S("PCEPILFE", "Inflation", "Fed’s favourite inflation (core PCE)", "yoy", "%", "The inflation measure the Federal Reserve targets at 2%.", "down"),
        new S("UNRATE", "Jobs", "Unemployment rate", "level", "%", "Share of people who want a job but don’t have one. Rising unemployment can signal a slowdown.", "down"),
        new S("PAYEMS", "Jobs", "Jobs added last month", "mom", "k", "New jobs created in the month (thousands). Strong hiring supports spending, but very strong numbers can keep rates high.", "up"),
        new S("ICSA", "Jobs", "Weekly jobless claims", "level", "k", "People filing for unemployment benefits each week (thousands). A quick early warning for layoffs.", "down"),
        new S("A191RL1Q225SBEA", "Growth", "GDP growth (annualized)", "level", "%", "How fast the whole US economy grew last quarter, at a yearly pace. Two negative quarters in a row is a common recession rule of thumb.", "up"),
        new S("INDPRO", "Growth", "Industrial production", "yoy", "%", "Output of factories, mines and utilities compared with a year ago.", "up"),
        new S("RSAFS", "Growth", "Retail sales (monthly change)", "momPct", "%", "How much shoppers spent compared with the month before. Consumer spending is about two-thirds of the US economy.", "up"),
        new S("DFF", "Rates", "Fed funds rate", "level", "%", "The Federal Reserve’s key interest rate. It sets the cost of borrowing across the economy.", "none"),
        new S("DGS2", "Rates", "2-year Treasury yield", "level", "%", "What the US government pays to borrow for 2 years. It moves with expectations for the Fed.", "none"),
        new S("DGS10", "Rates", "10-year Treasury yield", "level", "%", "The benchmark long-term rate. Mortgages and stock valuations take their cue from it.", "none"),
        new S("T10Y2Y", "Rates", "Yield curve (10-year minus 2-year)", "level", "pts", "Below zero (\"inverted\") has often come before recessions.", "up"),
        new S("MORTGAGE30US", "Consumers & housing", "30-year mortgage rate", "level", "%", "The average rate on a new 30-year home loan.", "down"),
        new S("HOUST", "Consumers & housing", "Housing starts", "level", "k", "New homes started, at a yearly pace (thousands).", "up"),
        new S("UMCSENT", "Consumers & housing", "Consumer sentiment", "level", "", "How confident households feel (University of Michigan survey). Gloomy consumers tend to spend less.", "up"));

    /** Raw (date, value) observations from FRED's CSV, oldest first; "." (missing) rows skipped. */
    static List<double[]> fetch(String id) {
        return CACHE.get("fred" + id, 6 * 3600_000L, () -> {
            String from = LocalDate.now(ZoneOffset.UTC).minusYears(6).toString();
            String key = cfg("FRED_API_KEY", null);
            if (key != null) { // official API (free key from fred.stlouisfed.org); the CSV download below blocks most servers
                HttpResponse<String> r;
                try { r = HTTP.send(HttpRequest.newBuilder(URI.create(cfg("FRED_API_URL", "https://api.stlouisfed.org") + "/fred/series/observations?series_id=" + id + "&observation_start=" + from + "&file_type=json&api_key=" + java.net.URLEncoder.encode(key, java.nio.charset.StandardCharsets.UTF_8)))
                        .timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
                catch (Exception e) { throw new UpstreamException("Economic data is unavailable right now"); }
                if (r.statusCode() == 400 || r.statusCode() == 403) throw new UpstreamException("The FRED API key is not valid", 503);
                if (r.statusCode() != 200) throw new UpstreamException("Economic data is unavailable right now");
                List<double[]> out = new ArrayList<>();
                for (Object o : list(map(Json.parse(r.body())).get("observations"))) {
                    Map<String, Object> x = map(o);
                    Double v = num(x.get("value"));
                    String d = str(x.get("date"));
                    if (v != null && d != null && d.matches("\\d{4}-\\d{2}-\\d{2}")) out.add(new double[]{LocalDate.parse(d).toEpochDay(), v});
                }
                if (out.isEmpty()) throw new UpstreamException("Economic data is unavailable right now");
                return out;
            }
            String u = cfg("FRED_BASE_URL", "https://fred.stlouisfed.org") + "/graph/fredgraph.csv?id=" + id + "&cosd=" + from;
            HttpResponse<String> r;
            try { r = HTTP.send(HttpRequest.newBuilder(URI.create(u)).timeout(Duration.ofSeconds(15)).header("User-Agent", "Quantera").GET().build(), HttpResponse.BodyHandlers.ofString()); }
            catch (Exception e) { throw new UpstreamException("Economic data is unavailable right now"); }
            if (r.statusCode() != 200) throw new UpstreamException("Economic data is unavailable right now");
            List<double[]> out = new ArrayList<>();
            for (String line : r.body().split("\n")) {
                String[] p = line.trim().split(",");
                if (p.length < 2 || !p[0].matches("\\d{4}-\\d{2}-\\d{2}")) continue;
                Double v = num(p[1]);
                if (v == null) continue;
                out.add(new double[]{LocalDate.parse(p[0]).toEpochDay(), v});
            }
            if (out.isEmpty()) throw new UpstreamException("Economic data is unavailable right now");
            return out;
        });
    }

    /** Turns raw observations into what the dashboard shows (level, year-over-year %, monthly change). */
    static List<double[]> transform(List<double[]> raw, String how) {
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < raw.size(); i++) {
            double[] o = raw.get(i);
            switch (how) {
                case "yoy" -> { if (i >= 12) out.add(new double[]{o[0], (o[1] / raw.get(i - 12)[1] - 1) * 100}); }
                case "mom" -> { if (i >= 1) out.add(new double[]{o[0], o[1] - raw.get(i - 1)[1]}); }
                case "momPct" -> { if (i >= 1) out.add(new double[]{o[0], (o[1] / raw.get(i - 1)[1] - 1) * 100}); }
                default -> out.add(o);
            }
        }
        return out;
    }

    static Map<String, Object> one(S s) {
        List<double[]> raw = fetch(s.id()), t = transform(raw, s.how());
        if (s.id().equals("ICSA")) { List<double[]> k = new ArrayList<>(); for (double[] o : t) k.add(new double[]{o[0], o[1] / 1000}); t = k; } // claims in thousands
        if (t.isEmpty()) throw new UpstreamException("Economic data is unavailable right now");
        // Daily and weekly series: one point per week for the sparkline
        boolean dense = t.size() > 400;
        List<Object> pts = new ArrayList<>();
        int step = dense ? 5 : 1, n = dense ? 60 : 36;
        for (int i = Math.max(0, t.size() - n * step); i < t.size(); i += step) pts.add(Math.round(t.get(i)[1] * 1000) / 1000.0);
        double[] last = t.get(t.size() - 1), prev = t.size() > (dense ? 5 : 1) ? t.get(t.size() - 1 - (dense ? 5 : 1)) : last;
        return Json.obj("id", s.id(), "group", s.group(), "name", s.name(), "unit", s.unit(), "what", s.what(), "good", s.good(), "value", Math.round(last[1] * 1000) / 1000.0,
                "prev", Math.round(prev[1] * 1000) / 1000.0, "change", Math.round((last[1] - prev[1]) * 1000) / 1000.0, "date", LocalDate.ofEpochDay((long) last[0]).toString(),
                "points", pts, "source", "https://fred.stlouisfed.org/series/" + s.id());
    }

    /** The whole dashboard; series that fail are left out rather than failing the page. */
    static Map<String, Object> dashboard() {
        return CACHE.get("macro", 30 * 60_000L, () -> {
            Map<String, Future<Map<String, Object>>> f = new LinkedHashMap<>();
            try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {
                for (S s : SERIES) f.put(s.id(), ex.submit(() -> one(s)));
                List<Object> items = new ArrayList<>();
                for (var e : f.entrySet()) try { items.add(e.getValue().get(20, TimeUnit.SECONDS)); } catch (Exception ignored) {}
                if (items.isEmpty()) throw new UpstreamException(cfg("FRED_API_KEY", null) == null ? "The economy dashboard is being set up. Check back soon." : "Economic data is unavailable right now", 503);
                return Json.obj("items", items, "source", "Federal Reserve Bank of St. Louis (FRED)", "updated", Instant.now().toString());
            }
        });
    }
}
