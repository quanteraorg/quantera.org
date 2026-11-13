package com.quantera;

import java.util.*;
import java.util.regex.*;

import static com.quantera.Support.*;

/**
 * Keeps AI answers tied to the data they were given.
 * - links(): a clickable source for each tool the AI used (SEC filing, quote page, news article).
 * - check(): finds each figure in the answer (prices, percentages, dollar amounts) and looks for it in the data the AI was
 *   given, allowing for the rounding shown. Figures it can't find are listed so readers know to check them: they may be
 *   the AI's own arithmetic, a rounded figure, or a mistake.
 */
final class Grounding {
    private Grounding() {}

    // a number with optional sign, currency, thousands separators, decimals and unit
    static final Pattern FIG = Pattern.compile("(?<![\\w.,/])([-−+]?)(\\$|US\\$)?(\\d{1,3}(?:,\\d{3})+|\\d+)(\\.\\d+)?(?:\\s?(%|percent\\b|trillion\\b|billion\\b|million\\b|thousand\\b|tn\\b|bn\\b|[TBMK]\\b))?(?![\\w-])");
    static final Pattern MONTH = Pattern.compile("(?i)(jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec)[a-z]*\\.?\\s*$");
    static final Pattern TOKEN = Pattern.compile("\\[\\[[^\\]]*\\]\\]|\\]\\([^)]*\\)|https?://\\S+");

    /** One figure the answer states. */
    record Fig(String text, double value, double tol, boolean pct) {}

    static List<Fig> figures(String answer) {
        List<Fig> out = new ArrayList<>();
        if (answer == null) return out;
        String t = TOKEN.matcher(answer).replaceAll(" ");
        Matcher m = FIG.matcher(t);
        while (m.find() && out.size() < 60) {
            String whole = m.group(3).replace(",", ""), dec = m.group(4) == null ? "" : m.group(4), unit = m.group(5) == null ? "" : m.group(5).toLowerCase(), cur = m.group(2);
            double v;
            try { v = Double.parseDouble(whole + dec); } catch (NumberFormatException e) { continue; }
            boolean pct = unit.equals("%") || unit.equals("percent");
            double scale = switch (unit) { case "trillion", "tn", "t" -> 1e12; case "billion", "bn", "b" -> 1e9; case "million", "m" -> 1e6; case "thousand", "k" -> 1e3; default -> 1; };
            boolean plain = cur == null && unit.isEmpty();
            if (plain && dec.isEmpty()) {
                if (v >= 1900 && v <= 2100) continue; // a year
                if (v < 10) continue;                  // a count ("3 reasons") rather than a figure
                if (MONTH.matcher(t.substring(Math.max(0, m.start() - 6), m.start())).find()) continue; // a date ("Nov 11")
            }
            if (plain && !dec.isEmpty() && v < 1) continue; // ratios like 0.8 are too easy to match by chance
            // the precision shown: "394.3 billion" is anything that rounds to it; whole numbers allow the last digit to be rounded
            double step = dec.isEmpty() ? 1 : Math.pow(10, -(dec.length() - 1));
            double tol = Math.max(step * 0.5 * scale, Math.abs(v * scale) * 0.005) + 1e-9;
            out.add(new Fig(m.group().trim(), v * scale, tol, pct));
        }
        return out;
    }

    /** Every number in the data, including numbers written inside text (headlines, labels). */
    static void numbers(Object o, List<Double> into) {
        if (into.size() > 200_000) return;
        if (o instanceof Number n) { double d = n.doubleValue(); if (Double.isFinite(d)) into.add(Math.abs(d)); }
        else if (o instanceof Map<?, ?> m) { for (Object v : m.values()) numbers(v, into); }
        else if (o instanceof List<?> l) { for (Object v : l) numbers(v, into); }
        else if (o instanceof String s && s.length() < 4000) for (Fig f : figures(s)) into.add(Math.abs(f.value()));
    }

    static boolean found(Fig f, List<Double> data) {
        double v = Math.abs(f.value());
        for (double x : data) {
            if (Math.abs(x - v) <= f.tol()) return true;
            if (f.pct() && Math.abs(x * 100 - v) <= f.tol()) return true; // fractions such as margins (0.245 = 24.5%)
        }
        return false;
    }

    /** How many of the answer's figures appear in the data, and which don't. */
    static Map<String, Object> check(String answer, Object data) {
        List<Fig> figs = figures(answer);
        List<Double> nums = new ArrayList<>();
        numbers(data, nums);
        List<String> missing = new ArrayList<>();
        int ok = 0;
        Set<String> seen = new HashSet<>();
        for (Fig f : figs) {
            if (!seen.add(f.text())) continue;
            if (found(f, nums)) ok++; else if (missing.size() < 12) missing.add(f.text());
        }
        return Json.obj("figures", seen.size(), "matched", ok, "unmatched", missing);
    }

    private static Map<String, Object> link(String label, String url) { return Json.obj("label", label, "url", url); }
    private static String yahoo(String s) { return "https://finance.yahoo.com/quote/" + s; }

    /** Clickable sources for one tool call. */
    static List<Object> links(String tool, Map<String, Object> in, Object out) {
        List<Object> l = new ArrayList<>();
        String s = String.valueOf(in.getOrDefault("symbol", "")).toUpperCase();
        if (!SYM.matcher(s).matches()) s = "";
        try {
            switch (tool) {
                case "get_quote", "get_price_stats" -> { if (!s.isEmpty()) l.add(link(s + " price and history (Yahoo Finance)", yahoo(s))); }
                case "get_company" -> { if (!s.isEmpty()) l.add(link(s + " company profile (Yahoo Finance)", yahoo(s) + "/profile")); }
                case "get_earnings" -> { if (!s.isEmpty()) l.add(link(s + " earnings and analyst estimates (Yahoo Finance)", yahoo(s) + "/analysis")); }
                case "get_competitors" -> { if (!s.isEmpty()) l.add(link(s + " competitors (Quantera research)", "/#/trade/" + s)); }
                case "get_technicals" -> { if (!s.isEmpty()) l.add(link(s + " chart and technicals (Quantera)", "/#/trade/" + s)); }
                case "get_financial_statements" -> {
                    List<Object> per = list(map(out).get("periods"));
                    for (int i = per.size() - 1; i >= 0 && l.size() < 2; i--) {
                        Map<String, Object> p = map(per.get(i));
                        Object u = p.get("link");
                        if (u != null && String.valueOf(u).startsWith("https://www.sec.gov/")) l.add(link(s + " " + p.get("filing") + " for " + p.get("period") + " (SEC EDGAR)", String.valueOf(u)));
                    }
                    if (l.isEmpty() && !s.isEmpty()) l.add(link(s + " filings (SEC EDGAR)", "https://www.sec.gov/cgi-bin/browse-edgar?action=getcompany&type=10-K&CIK=" + s));
                }
                case "get_news" -> {
                    for (Object o : list(out)) {
                        Map<String, Object> a = map(o);
                        Object u = a.get("url");
                        if (u != null && String.valueOf(u).startsWith("http") && l.size() < 5)
                            l.add(link(String.valueOf(a.getOrDefault("title", "Article")) + (a.get("source") != null ? " (" + a.get("source") + ")" : ""), String.valueOf(u)));
                    }
                }
                default -> { }
            }
        } catch (RuntimeException ignored) { /* a source link is a nicety; never fail the answer for it */ }
        return l;
    }

    /** Adds a link list, keeping each URL once. */
    static void addLinks(List<Object> into, List<Object> more) {
        Set<Object> have = new HashSet<>();
        for (Object o : into) have.add(map(o).get("url"));
        for (Object o : more) if (have.add(map(o).get("url")) && into.size() < 12) into.add(o);
    }
}
