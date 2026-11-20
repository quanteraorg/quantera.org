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

    /** First listing that matches by name and started trading within the last three years (null when none). */
    static Map<String, Object> of(String company) {
        String key = "listed" + norm(company);
        return Providers.CACHE.get(key, 6 * 3600_000L, () -> {
            for (Object o : list(Providers.search(company).get("results"))) {
                Map<String, Object> r = map(o);
                String sym = str(r.get("symbol")), type = str(r.get("type")), region = str(r.get("region"));
                if (sym == null || !sym.matches("[A-Z]{1,5}") || !"Equity".equalsIgnoreCase(type) || !"United States".equals(region) || !nameMatches(company, str(r.get("name")))) continue;
                Long first = Yahoo.firstTrade(sym);
                if (first != null && Instant.ofEpochSecond(first).isAfter(Instant.now().minus(3 * 365, ChronoUnit.DAYS)))
                    return Json.obj("listed", true, "symbol", sym, "name", str(r.get("name")), "firstTrade", Instant.ofEpochSecond(first).toString().substring(0, 10));
            }
            return Json.obj("listed", false);
        });
    }
}
