package com.quantera;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Has a private company started trading on a US exchange? When Anthropic listed as ANT the site still sent people to its
 * private-company page. A private company counts as listed when a US equity's name starts with the company's name AND
 * that stock first traded within the last three years, so an older public namesake (Mercury General for Mercury) never
 * matches. Cached for six hours.
 */
final class Listed {
    private Listed() {}

    /** "Anthropic" vs "ANTHROPIC PBC-CL A": the listing name starts with the company's name as a whole word. Pure: unit-tested. */
    static boolean nameMatches(String company, String listing) {
        String a = norm(company), b = norm(listing);
        return !a.isEmpty() && (b.equals(a) || b.startsWith(a + " "));
    }
    static String norm(String s) { return s == null ? "" : s.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", " ").trim(); }

    /** When the company behind a ticker filed its IPO prospectus (Form 424B4 or S-1) with the SEC, or null when none is on file. */
    static String ipoFiling(String sym) {
        try {
            Map<String, Object> r = map(map(Sec.get(cfg("SEC_DATA_URL", "https://data.sec.gov") + "/submissions/CIK" + Sec.pad(Sec.cik(sym)) + ".json", 20)).get("filings"));
            Map<String, Object> rec = map(r.get("recent"));
            List<Object> forms = list(rec.get("form")), dates = list(rec.get("filingDate"));
            for (String want : List.of("424B4", "S-1")) // the final prospectus is dated when trading begins; the S-1 is earlier
                for (int i = 0; i < forms.size() && i < dates.size(); i++) if (want.equals(String.valueOf(forms.get(i)))) return String.valueOf(dates.get(i));
        } catch (RuntimeException ignored) { /* not an SEC filer, or SEC unreachable */ }
        return null;
    }

    /** Listed when a US equity's name starts with the company's name AND it went public within three years (an IPO filing at
     *  the SEC, or failing that its first trading day). Only a definite answer is cached, so a failed lookup is retried. */
    /** The cached answer only (no lookups), for lists that show many companies at once; null when not checked yet. */
    static Map<String, Object> cached(String company) { return Providers.CACHE.peek("listed" + norm(company)); }

    static Map<String, Object> of(String company) {
        String key = "listed" + norm(company);
        Map<String, Object> hit = Providers.CACHE.peek(key);
        // "listed" is final; "not listed" is rechecked every 30 minutes so a new IPO shows up quickly
        if (hit != null && Providers.CACHE.age(key) < (Boolean.TRUE.equals(hit.get("listed")) ? 6 * 3600_000L : 30 * 60_000L)) return hit;
        boolean unsure = false;
        String cutoff = Instant.now().minus(3 * 365, ChronoUnit.DAYS).toString().substring(0, 10);
        for (Object o : list(Providers.search(company).get("results"))) {
            Map<String, Object> r = map(o);
            String sym = str(r.get("symbol")), type = str(r.get("type")), region = str(r.get("region"));
            if (sym == null || !sym.matches("[A-Z]{1,5}") || !"Equity".equalsIgnoreCase(type) || !"United States".equals(region) || !nameMatches(company, str(r.get("name")))) continue;
            String ipo = ipoFiling(sym);
            if (ipo == null) { Long first = Yahoo.firstTrade(sym); if (first != null) ipo = Instant.ofEpochSecond(first).toString().substring(0, 10); else unsure = true; }
            if (ipo != null && ipo.compareTo(cutoff) >= 0) {
                Map<String, Object> out = Json.obj("listed", true, "symbol", sym, "name", str(r.get("name")), "firstTrade", ipo);
                Providers.CACHE.put(key, out); return out;
            }
        }
        Map<String, Object> out = Json.obj("listed", false);
        if (!unsure) Providers.CACHE.put(key, out);
        return out;
    }
}
