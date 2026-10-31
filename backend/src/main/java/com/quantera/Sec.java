package com.quantera;

import com.quantera.Support.*;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Company filings and financial statements from the SEC's free EDGAR APIs (US-listed companies and ADRs).
 * The SEC asks every client to identify itself: SEC_USER_AGENT ("Company contact@email"). SEC_BASE_URL / SEC_DATA_URL point elsewhere (tests).
 */
final class Sec {
    private Sec() {}

    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).followRedirects(HttpClient.Redirect.NORMAL).build();
    static final Cache CACHE = Providers.CACHE;

    private static Object get(String url, int timeoutSec) {
        HttpResponse<String> r;
        try {
            r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(timeoutSec)).header("User-Agent", cfg("SEC_USER_AGENT", "Quantera Research research@quantera.org"))
                    .header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { throw new UpstreamException("SEC data is unavailable right now"); }
        if (r.statusCode() == 404) throw new UpstreamException("No SEC filings for this company", 404);
        if (r.statusCode() != 200) throw new UpstreamException("SEC data is unavailable right now");
        try { return Json.parse(r.body()); } catch (RuntimeException | StackOverflowError e) { throw new UpstreamException("SEC data is unavailable right now"); }
    }

    /** Ticker -> CIK for every SEC registrant (refreshed daily). BRK.B is BRK-B at the SEC. */
    static Map<String, Long> ciks() {
        return CACHE.get("secciks", 24 * 3600_000L, () -> {
            Map<String, Long> m = new HashMap<>();
            for (Object o : map(get(cfg("SEC_BASE_URL", "https://www.sec.gov") + "/files/company_tickers.json", 30)).values()) {
                Map<String, Object> x = map(o);
                Double c = num(x.get("cik_str"));
                if (c != null && x.get("ticker") != null) m.putIfAbsent(String.valueOf(x.get("ticker")).toUpperCase(), c.longValue());
            }
            if (m.isEmpty()) throw new UpstreamException("SEC data is unavailable right now");
            return m;
        });
    }

    static long cik(String symbol) {
        Long c = ciks().get(symbol.toUpperCase().replace('.', '-'));
        if (c == null) throw new UpstreamException("This company does not file with the SEC (it may be listed outside the US, or be a fund or index).", 404);
        return c;
    }

    static String pad(long cik) { return String.format("%010d", cik); }

    static final Map<String, String> FORMS = Map.ofEntries(Map.entry("10-K", "Annual report"), Map.entry("10-K/A", "Annual report (amended)"), Map.entry("10-Q", "Quarterly report"),
            Map.entry("8-K", "Major company event"), Map.entry("4", "Insider buy or sell"), Map.entry("S-1", "Registration to go public"), Map.entry("S-1/A", "Registration to go public (amended)"),
            Map.entry("424B4", "Final IPO prospectus"), Map.entry("DEF 14A", "Shareholder meeting and pay (proxy)"), Map.entry("SC 13D", "Investor owns over 5% (active)"),
            Map.entry("SC 13G", "Investor owns over 5% (passive)"), Map.entry("SCHEDULE 13D", "Investor owns over 5% (active)"), Map.entry("SCHEDULE 13G", "Investor owns over 5% (passive)"),
            Map.entry("20-F", "Annual report (foreign company)"), Map.entry("6-K", "Report from a foreign company"), Map.entry("40-F", "Annual report (Canadian company)"), Map.entry("S-4", "Merger registration"));

    /** Latest important filings with plain-English labels and links, plus insider-filing count over 90 days. */
    static Map<String, Object> filings(String symbol) {
        return CACHE.get("secf" + symbol, 3 * 3600_000L, () -> {
            long c = cik(symbol);
            Map<String, Object> j = map(get(cfg("SEC_DATA_URL", "https://data.sec.gov") + "/submissions/CIK" + pad(c) + ".json", 20));
            Map<String, Object> rec = map(map(j.get("filings")).get("recent"));
            List<Object> form = list(rec.get("form")), date = list(rec.get("filingDate")), acc = list(rec.get("accessionNumber")), doc = list(rec.get("primaryDocument")), desc = list(rec.get("primaryDocDescription")), rep = list(rec.get("reportDate")), items = list(rec.get("items")), accepted = list(rec.get("acceptanceDateTime"));
            List<Object> out = new ArrayList<>(), earnings = new ArrayList<>();
            int insiders = 0;
            String cutoff = LocalDate.now(ZoneOffset.UTC).minusDays(90).toString();
            for (int i = 0; i < form.size(); i++) {
                String f = str(form.get(i)), d = i < date.size() ? str(date.get(i)) : null;
                if (f == null || d == null) continue;
                if (f.equals("4") && d.compareTo(cutoff) >= 0) insiders++;
                // Item 2.02 of an 8-K is the earnings release; its timestamp says whether it came out after the 4 pm New York close
                if (f.equals("8-K") && earnings.size() < 12 && i < items.size() && str(items.get(i)) != null && str(items.get(i)).contains("2.02")) {
                    boolean after = false;
                    try { after = !Instant.parse(str(accepted.get(i))).atZone(ZoneId.of("America/New_York")).toLocalTime().isBefore(LocalTime.of(16, 0)); } catch (RuntimeException ignored) {}
                    earnings.add(Json.obj("date", d, "afterClose", after));
                }
                if (!FORMS.containsKey(f) || out.size() >= 40) continue;
                String a = i < acc.size() ? str(acc.get(i)) : "", pd = i < doc.size() ? str(doc.get(i)) : "";
                String url = "https://www.sec.gov/Archives/edgar/data/" + c + "/" + a.replace("-", "") + "/" + (pd == null ? "" : pd);
                out.add(Json.obj("form", f, "label", FORMS.get(f), "date", d, "period", i < rep.size() ? str(rep.get(i)) : null, "description", i < desc.size() ? str(desc.get(i)) : null, "url", url));
            }
            return Json.obj("symbol", symbol, "cik", c, "name", str(j.get("name")), "industry", str(j.get("sicDescription")), "fiscalYearEnd", str(j.get("fiscalYearEnd")),
                    "insiderFilings90d", insiders, "filings", out, "earnings", earnings, "all", "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&CIK=" + c + "&type=&dateb=&owner=include&count=40", "source", "SEC EDGAR");
        });
    }

    // ---------------------------------------------------------------- financial statements (XBRL company facts)

    static final String[] REVENUE = {"Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax", "SalesRevenueGoodsNet"};

    /**
     * Values of one concept by frame (CY2024 = a calendar-aligned year, CY2024Q1 = a quarter, ...I = a point in time).
     * The SEC tags a frame on the filing that first reported the period, so each frame takes the value from the most recent
     * filing for that same period instead: later reports restate older years, e.g. per-share figures after a stock split.
     */
    private static Map<String, double[]> frames(Map<String, Object> facts, String tag, String unit) {
        List<Object> all = list(map(map(map(facts.get(tag)).get("units"))).get(unit));
        Map<String, Object[]> latest = new HashMap<>(); // period "start|end" -> {filed, value}
        for (Object o : all) {
            Map<String, Object> x = map(o);
            Double v = num(x.get("val"));
            String p = str(x.get("start")) + "|" + str(x.get("end")), filed = Objects.toString(str(x.get("filed")), "");
            Object[] cur = latest.get(p);
            if (v != null && (cur == null || filed.compareTo((String) cur[0]) > 0)) latest.put(p, new Object[]{filed, v});
        }
        Map<String, double[]> out = new TreeMap<>();
        for (Object o : all) {
            Map<String, Object> x = map(o);
            String fr = str(x.get("frame"));
            Double v = num(x.get("val"));
            if (fr == null || v == null) continue;
            Object[] l = latest.get(str(x.get("start")) + "|" + str(x.get("end")));
            String end = str(x.get("end"));
            out.put(fr, new double[]{l == null ? v : (Double) l[1], end == null ? 0 : LocalDate.parse(end).toEpochDay()});
        }
        return out;
    }

    private static Map<String, double[]> best(Map<String, Object> facts, String unit, String... tags) {
        Map<String, double[]> pick = new TreeMap<>();
        String latest = "";
        for (String t : tags) {
            Map<String, double[]> f = frames(facts, t, unit);
            String last = f.keySet().stream().filter(k -> k.matches("CY\\d{4}")).reduce((a, b) -> b).orElse("");
            if (last.compareTo(latest) > 0) { latest = last; pick = f; }
        }
        return pick;
    }

    private static Double at(Map<String, double[]> m, String k) { double[] v = m.get(k); return v == null ? null : v[0]; }

    static Map<String, Object> financials(String symbol) {
        return CACHE.get("secx" + symbol, 12 * 3600_000L, () -> {
            long c = cik(symbol);
            Map<String, Object> facts = map(map(get(cfg("SEC_DATA_URL", "https://data.sec.gov") + "/api/xbrl/companyfacts/CIK" + pad(c) + ".json", 30)).get("facts"));
            Map<String, Object> g = map(facts.get("us-gaap"));
            if (g.isEmpty()) throw new UpstreamException("No US-GAAP financial statements for this company (foreign companies often report under IFRS).", 404);
            Map<String, double[]> rev = best(g, "USD", REVENUE), ni = best(g, "USD", "NetIncomeLoss", "ProfitLoss"), eps = best(g, "USD/shares", "EarningsPerShareDiluted", "EarningsPerShareBasic"),
                    ocf = best(g, "USD", "NetCashProvidedByUsedInOperatingActivities", "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"), capex = best(g, "USD", "PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets"),
                    gross = best(g, "USD", "GrossProfit"), opi = best(g, "USD", "OperatingIncomeLoss");
            List<String> years = new ArrayList<>();
            for (String k : rev.keySet()) if (k.matches("CY\\d{4}")) years.add(k);
            if (years.isEmpty()) for (String k : ni.keySet()) if (k.matches("CY\\d{4}")) years.add(k);
            List<Object> annual = new ArrayList<>();
            for (String y : years.subList(Math.max(0, years.size() - 5), years.size())) {
                double[] r = rev.get(y) != null ? rev.get(y) : ni.get(y);
                Double o = at(ocf, y), cx = at(capex, y);
                annual.add(Json.obj("year", y.substring(2), "end", r == null ? null : LocalDate.ofEpochDay((long) r[1]).toString(), "revenue", at(rev, y), "grossProfit", at(gross, y), "operatingIncome", at(opi, y),
                        "netIncome", at(ni, y), "eps", at(eps, y), "operatingCashFlow", o, "capex", cx, "freeCashFlow", o != null && cx != null ? o - cx : null));
            }
            List<String> qs = new ArrayList<>();
            for (String k : rev.keySet()) if (k.matches("CY\\d{4}Q\\d")) qs.add(k);
            List<Object> quarterly = new ArrayList<>();
            for (String q : qs.subList(Math.max(0, qs.size() - 8), qs.size()))
                quarterly.add(Json.obj("quarter", q.substring(2), "end", LocalDate.ofEpochDay((long) rev.get(q)[1]).toString(), "revenue", at(rev, q), "netIncome", at(ni, q), "eps", at(eps, q)));
            splitAdjust(annual);
            splitAdjust(quarterly);
            // latest balance-sheet snapshot (instant frames end in "I")
            Map<String, double[]> cash = best2(g, "CashAndCashEquivalentsAtCarryingValue", "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents"), assets = best2(g, "Assets"), liab = best2(g, "Liabilities"),
                    eq = best2(g, "StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"), debt = best2(g, "LongTermDebt", "LongTermDebtNoncurrent");
            String li = lastInstant(assets);
            Map<String, Object> bs = li == null ? null : Json.obj("date", LocalDate.ofEpochDay((long) assets.get(li)[1]).toString(), "cash", lastVal(cash), "assets", at(assets, li), "liabilities", lastVal(liab), "equity", lastVal(eq), "longTermDebt", lastVal(debt));
            return Json.obj("symbol", symbol, "name", str(map(get(cfg("SEC_DATA_URL", "https://data.sec.gov") + "/submissions/CIK" + pad(c) + ".json", 20)).get("name")),
                    "annual", annual, "quarterly", quarterly, "balance", bs, "currency", "USD", "source", "SEC EDGAR (XBRL)");
        });
    }

    static final double[] SPLITS = {2, 3, 4, 5, 6, 8, 10, 15, 20, 25, 30, 40, 50};

    /**
     * Restates older EPS for stock splits that no later filing covers (e.g. NVIDIA FY2022 after the 2024 10-for-1 split).
     * A split shows up as the implied share count (net income / EPS) jumping by a common split ratio between periods; rows are oldest first.
     */
    @SuppressWarnings("unchecked")
    static void splitAdjust(List<Object> rows) {
        double factor = 1, newer = 0;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> r = (Map<String, Object>) rows.get(i);
            Double eps = num(r.get("eps")), ni = num(r.get("netIncome"));
            if (eps == null || ni == null || Math.abs(eps) < 1e-9 || ni / eps <= 0) continue;
            double shares = ni / eps * factor;
            if (newer > 0) for (double f : SPLITS) if (Math.abs(newer / shares / f - 1) < 0.15) { factor *= f; shares *= f; break; }
            if (factor != 1) r.put("eps", Math.round(eps / factor * 100) / 100.0);
            newer = shares;
        }
    }

    private static Map<String, double[]> best2(Map<String, Object> g, String... tags) {
        Map<String, double[]> pick = new TreeMap<>();
        String latest = "";
        for (String t : tags) { Map<String, double[]> f = frames(g, t, "USD"); String l = lastInstant(f); if (l != null && l.compareTo(latest) > 0) { latest = l; pick = f; } }
        return pick;
    }
    private static String lastInstant(Map<String, double[]> m) { String l = null; for (String k : m.keySet()) if (k.endsWith("I")) l = k; return l; }
    private static Double lastVal(Map<String, double[]> m) { String l = lastInstant(m); return l == null ? null : m.get(l)[0]; }
}
