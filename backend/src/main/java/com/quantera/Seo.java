package com.quantera;

import com.quantera.Support.*;
import java.text.DecimalFormat;
import java.time.*;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Plain HTML pages search engines and link previews can read: one per well-known stock and private company, a directory linking them, and a sitemap.
 * The app itself lives behind #/ URLs, which Google does not treat as separate pages. Vercel rewrites /stock/*, /private-company/* etc. here.
 */
final class Seo {
    private Seo() {}

    record Page(int code, String type, String body) {}

    static String base() { return cfg("SITE_URL", "https://quanteraorg.vercel.app").replaceAll("/+$", ""); }
    static String h(Object o) { return o == null ? "" : String.valueOf(o).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
    static String slug(String name) { return String.valueOf(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", ""); }

    /** $416.2B, ¥50.68T; currency symbols only where they are unambiguous. */
    private static String money(Double v, String ccy) {
        if (v == null) return "—";
        String c = ccy == null ? "USD" : ccy, sym = switch (c) { case "USD" -> "$"; case "EUR" -> "€"; case "GBP" -> "£"; case "JPY" -> "¥"; case "INR" -> "₹"; default -> c + " "; };
        double a = Math.abs(v);
        String n = a >= 1e12 ? new DecimalFormat("0.00").format(a / 1e12) + "T" : a >= 1e9 ? new DecimalFormat("0.0").format(a / 1e9) + "B" : a >= 1e6 ? new DecimalFormat("0").format(a / 1e6) + "M" : new DecimalFormat("#,##0.##").format(a);
        return (v < 0 ? "−" : "") + sym + n;
    }
    private static String pct(Double frac) { return frac == null ? "—" : new DecimalFormat("0.0").format(frac * 100) + "%"; }
    private static String num(Double v, String p) { return v == null ? "—" : new DecimalFormat(p).format(v); }
    private static String signed(Double frac) { return frac == null ? "—" : (frac >= 0 ? "+" : "−") + new DecimalFormat("0.0").format(Math.abs(frac) * 100) + "%"; }
    private static Double d(Map<String, Object> m, String k) { return Support.num(m.get(k)); }
    private static String month(String iso) { try { LocalDate x = LocalDate.parse(iso); return x.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + x.getYear(); } catch (RuntimeException e) { return h(iso); } }

    private static String page(String title, String desc, String path, String main, String jsonLd, boolean index) {
        String url = base() + path;
        return "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + h(title) + "</title><meta name=\"description\" content=\"" + h(desc) + "\">"
                + (index ? "" : "<meta name=\"robots\" content=\"noindex\">") + "<link rel=\"canonical\" href=\"" + h(url) + "\"><link rel=\"icon\" href=\"/favicon-64.png\"><link rel=\"stylesheet\" href=\"/fonts/fonts.css\"><link rel=\"stylesheet\" href=\"/seo.css\"><meta name=\"theme-color\" content=\"#06080c\"><script>try{var t=JSON.parse(localStorage.getItem(\"q_theme\"));if(t==\"system\")t=matchMedia(\"(prefers-color-scheme: light)\").matches?\"light\":\"dark\";if(t==\"light\"){document.documentElement.dataset.theme=\"light\";document.querySelector(\"meta[name=theme-color]\").content=\"#f4f5f7\"}}catch(e){}</script>"
                + "<meta property=\"og:type\" content=\"website\"><meta property=\"og:site_name\" content=\"Quantera\"><meta property=\"og:title\" content=\"" + h(title) + "\"><meta property=\"og:description\" content=\"" + h(desc) + "\"><meta property=\"og:url\" content=\"" + h(url) + "\"><meta property=\"og:image\" content=\"" + h(base()) + "/og.png\"><meta name=\"twitter:card\" content=\"summary_large_image\">"
                + (jsonLd == null ? "" : "<script type=\"application/ld+json\">" + jsonLd.replace("<", "\\u003c") + "</script>") + "<script>(function(){var v=null;try{v=JSON.parse(localStorage.getItem('q_priv')||'null')}catch(e){}var ok=v?v==='all':!(navigator.globalPrivacyControl===true||navigator.doNotTrack==='1');if(ok){window.va=window.va||function(){(window.vaq=window.vaq||[]).push(arguments)};var s=document.createElement('script');s.defer=true;s.src='/_vercel/insights/script.js';document.head.appendChild(s)}})()</script></head><body><header><a class=\"logo\" href=\"/\"><img src=\"/logo-mark.png\" alt=\"\" width=\"26\" height=\"26\">QUANTERA</a><nav><a href=\"/research\">Research</a><a href=\"/stocks\">Stocks</a><a href=\"/private-companies\">Private companies</a><a href=\"/learn\">Learn</a><a class=\"cta\" href=\"/#/markets\">Open the app</a></nav></header><main>"
                + main + "</main><footer>Market data is delayed and may contain errors. Quantera is for education and research and is not investment advice. <a href=\"/#/about\">About Quantera</a> · <a href=\"/privacy\">Privacy</a> · <a href=\"/cookies\">Cookies</a> · <a href=\"/terms\">Terms</a> · <a href=\"/accessibility\">Accessibility</a></footer></body></html>";
    }

    private static String crumbs(String name, String listPath, String listName, String path) {
        return Json.write(Json.obj("@context", "https://schema.org", "@type", "BreadcrumbList", "itemListElement", List.of(
                Json.obj("@type", "ListItem", "position", 1, "name", "Quantera", "item", base() + "/"),
                Json.obj("@type", "ListItem", "position", 2, "name", listName, "item", base() + listPath),
                Json.obj("@type", "ListItem", "position", 3, "name", name, "item", base() + path))));
    }
    private static String tile(String label, String value, String sub) { return "<div><span>" + h(label) + "</span><b>" + h(value) + "</b>" + (sub.isEmpty() ? "" : "<small>" + h(sub) + "</small>") + "</div>"; }
    private static String faq(String q, String a) { return "<h3>" + h(q) + "</h3><p>" + h(a) + "</p>"; }

    static Page notFound(String what) { return new Page(404, "text/html; charset=utf-8", page("Not found | Quantera", "This page was not found on Quantera.", "/", "<h1>" + h(what) + " not found</h1><p><a href=\"/stocks\">Browse stocks</a> or <a href=\"/private-companies\">private companies</a>.</p>", null, false)); }

    static final java.time.ZoneId NY = java.time.ZoneId.of("America/New_York");
    static final java.time.format.DateTimeFormatter WHEN = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    /** "Price as of … New York time; company figures to …; data fetched … UTC". Every date says what it is and its time zone. Pure: unit-tested. */
    static String asOfLine(String priceTime, String fetched, String latestQuarter, String fiscalYearEnd) {
        StringBuilder b = new StringBuilder();
        try { if (priceTime != null) b.append("Price as of ").append(Instant.parse(priceTime).atZone(NY).format(WHEN).replace("AM", "am").replace("PM", "pm")).append(" New York time. "); } catch (RuntimeException ignored) {}
        if (latestQuarter != null || fiscalYearEnd != null) {
            b.append("Company figures: ");
            if (latestQuarter != null) b.append("quarter ended ").append(month(latestQuarter));
            if (latestQuarter != null && fiscalYearEnd != null) b.append("; ");
            if (fiscalYearEnd != null) b.append("full year ended ").append(month(fiscalYearEnd));
            b.append(". ");
        }
        try { if (fetched != null) b.append("Data fetched ").append(Instant.parse(fetched).atZone(ZoneOffset.UTC).format(WHEN).replace("AM", "am").replace("PM", "pm")).append(" UTC."); } catch (RuntimeException ignored) {}
        return b.toString().trim();
    }

    static String fetchedLine(String fetched) {
        try { return fetched == null ? "" : "Data fetched " + Instant.parse(fetched).atZone(ZoneOffset.UTC).format(WHEN).replace("AM", "am").replace("PM", "pm") + " UTC."; } catch (RuntimeException e) { return ""; }
    }

    static String asOfPrice(String priceTime) {
        try { return priceTime == null ? "" : " <small>as of " + h(Instant.parse(priceTime).atZone(NY).format(WHEN).replace("AM", "am").replace("PM", "pm")) + " New York time · delayed</small>"; } catch (RuntimeException e) { return ""; }
    }

    /** Yearly figures straight from the company's 10-K filings with the SEC, each year linked to its filing. Null when the SEC has no
     *  US-GAAP statements for the symbol (foreign companies, funds), so the page falls back to the Yahoo Finance table. */
    static String secTable(String s, String display) {
        if (!s.matches("[A-Z]{1,5}")) return null;
        Map<String, Object> st;
        try { st = Statements.of(s); } catch (RuntimeException e) { return null; }
        Map<String, Object> a = map(st.get("annual"));
        List<Object> per = list(a.get("periods"));
        if (per.isEmpty()) return null;
        int n = per.size(), from = Math.max(0, n - 5);
        Map<String, List<Object>> v = new HashMap<>();
        for (String k : List.of("income", "balance", "cashflow")) for (Object o : list(a.get(k))) v.put(str(map(o).get("key")), list(map(o).get("values")));
        boolean fin = Boolean.TRUE.equals(st.get("financial"));
        String[][] rows = fin
                ? new String[][]{{"Revenue", "revenue"}, {"Net income", "netIncome"}, {"Total assets", "assets"}, {"Shareholders' equity", "equity"}}
                : new String[][]{{"Revenue", "revenue"}, {"Gross profit", "grossProfit"}, {"Operating income", "operatingIncome"}, {"Net income", "netIncome"}, {"Free cash flow", "freeCashFlow"}, {"Cash and equivalents", "cash"}, {"Total debt", "totalDebt"}, {"Shareholders' equity", "equity"}};
        StringBuilder m = new StringBuilder("<h2>").append(h(s)).append(" yearly financials</h2><div class=\"tw\"><table><tr><th>Fiscal year</th>");
        for (int i = from; i < n; i++) {
            Map<String, Object> p = map(per.get(i)); String f = str(p.get("filing"));
            m.append("<th>").append(f != null && f.startsWith("https://www.sec.gov/") ? "<a href=\"" + h(f) + "\" rel=\"nofollow noopener\" target=\"_blank\">" + h(p.get("label")) + "</a>" : h(p.get("label"))).append("</th>");
        }
        m.append("</tr>");
        int shown = 0;
        for (String[] r : rows) {
            List<Object> vals = v.get(r[1]);
            if (vals == null) continue;
            boolean any = false; for (int i = from; i < n && i < vals.size(); i++) if (Support.num(vals.get(i)) != null) any = true;
            if (!any) continue;
            shown++;
            m.append("<tr><td>").append(r[0]).append("</td>");
            for (int i = from; i < n; i++) m.append("<td>").append(h(money(i < vals.size() ? Support.num(vals.get(i)) : null, "USD"))).append("</td>");
            m.append("</tr>");
        }
        if (shown < 2) return null;
        Map<String, Object> last = map(per.get(n - 1));
        m.append("</table></div><p class=\"src\">Yearly figures from ").append(h(display == null ? s : display)).append("'s annual reports (Form 10-K) filed with the SEC; each year links to its filing. Latest: ")
                .append(h(last.get("label"))).append(", year ended ").append(h(last.get("end"))).append(". Free cash flow is calculated (cash from operations minus capital spending)")
                .append(fin ? "" : "; total debt includes debt due within a year").append(". The headline numbers above use the latest quarter or the past 12 months (Yahoo Finance), so they can differ from this table.</p>");
        return m.toString();
    }

    /** One stock: the numbers that matter, a plain-English summary, statements and related stocks. Only companies with real fundamentals get an indexable page. */
    static Page stock(String raw) {
        String s = raw.toUpperCase(Locale.ROOT);
        if (!SYM.matcher(s).matches()) return notFound("Stock");
        Map<String, Object> f;
        try { f = Yahoo.fundamentals(s); } catch (UpstreamException e) { if (e.code == 404) return notFound("Stock"); throw e; }
        List<Object> annual = list(f.get("annual"));
        if (!"EQUITY".equals(f.get("type")) || annual.isEmpty()) return notFound("Stock");
        Map<String, Object> k = map(f.get("key")), a = map(f.get("analysts"));
        String name = str(f.get("name")) == null ? s : str(f.get("name")), q = str(f.get("currency")), c = str(f.get("financialCurrency")) == null ? q : str(f.get("financialCurrency")), path = "/stock/" + s;
        Double px = d(f, "price"), cap = d(k, "marketCap"), pe = d(k, "pe"), rg = d(k, "revenueGrowth"), pm = d(k, "profitMargin"), cash = d(k, "cash"), debt = d(k, "debt"), target = d(a, "target");
        Map<String, Object> last = map(annual.get(annual.size() - 1));
        Double rev = d(last, "revenue"), ni = d(last, "netIncome");
        String lq = str(f.get("latestQuarter")), asOf = lq == null ? "latest quarter" : "latest quarter, " + month(lq), fy = month(str(last.get("date")));
        boolean pence = q != null && !q.equals(q.toUpperCase(Locale.ROOT));
        String capCcy = pence ? q.toUpperCase(Locale.ROOT) : q, price = px == null ? "—" : (pence ? num(px, "#,##0.00") + " " + q : money(px, q));
        String desc = name + " (" + s + ") stock: " + (cap != null ? "market cap " + money(cap, capCcy) + ", " : "") + (pe != null ? "P/E " + num(pe, "0.0") + ", " : "") + (rev != null ? "revenue " + money(rev, c) + " in the latest full year. " : ". ") + "Financials, analyst targets and a plain-English summary.";
        StringBuilder m = new StringBuilder("<p class=\"crumb\"><a href=\"/stocks\">Stocks</a> › " + h(s) + "</p><h1>" + h(name) + " <span>" + h(s) + "</span></h1><p class=\"px\">" + h(price) + asOfPrice(str(f.get("priceTime"))) + "</p><div class=\"kn\">");
        if (cap != null) m.append(tile("Company value", money(cap, capCcy), "Market cap at the latest price"));
        if (pe != null) m.append(tile("Price vs profit", num(pe, "0.0"), "P/E ratio, past 12 months of profit")); else if (d(k, "eps") != null && d(k, "eps") < 0) m.append(tile("Price vs profit", "Loss", "No P/E: earnings are negative"));
        if (rg != null) m.append(tile("Sales growth", signed(rg), "Latest quarter, vs a year ago"));
        if (pm != null) m.append(tile("Profit margin", pct(pm), "Profit ÷ sales, past 12 months"));
        if (cash != null && debt != null) m.append(tile(cash >= debt ? "Net cash" : "Net debt", money(Math.abs(cash - debt), c), "Cash and investments " + money(cash, c) + " · debt " + money(debt, c) + " (" + asOf + ")"));
        if (target != null && px != null) m.append(tile("Analyst target", pence ? num(target, "#,##0.00") + " " + q : money(target, q), "Average 12-month target, " + signed(target / px - 1) + " from this price"));
        m.append("</div><p class=\"go\"><a class=\"cta\" href=\"/#/asset/").append(h(s)).append("\">Open the live chart and full research</a><a href=\"/#/trade/").append(h(s)).append("\">Practice trading ").append(h(s)).append(" with paper money</a></p>");
        m.append("<h2>").append(h(name)).append(" in plain English</h2><p>");
        if (rev != null) m.append("In its latest full year (to ").append(h(fy)).append("), ").append(h(name)).append(" sold ").append(h(money(rev, c))).append(" of products and services").append(ni != null ? (ni >= 0 ? " and kept " + h(money(ni, c)) + " as profit" : " and lost " + h(money(-ni, c))) : "").append(". ");
        if (rg != null) m.append("In the latest quarter, sales were ").append(rg >= 0 ? "up " : "down ").append(h(pct(Math.abs(rg)))).append(" from a year earlier. ");
        if (pe != null) m.append("At this price, investors pay about ").append(h(num(pe, "0"))).append(" times its profit over the past 12 months. ");
        if (cash != null && debt != null) m.append(cash >= debt ? "At the latest quarter, it held more cash and short-term investments than debt." : "At the latest quarter, it owed more in debt than it held in cash and short-term investments.");
        m.append("</p>");
        String secTbl = secTable(s, name);
        if (secTbl != null) m.append(secTbl).append("<h2>Common questions about ").append(h(name)).append("</h2>");
        else {
            m.append("<h2>").append(h(s)).append(" yearly financials</h2><div class=\"tw\"><table><tr><th>Year ending</th>");
            for (Object o : annual) m.append("<th>").append(month(str(map(o).get("date")))).append("</th>");
            m.append("</tr>");
            String[][] rows = {{"Revenue", "revenue"}, {"Gross profit", "grossProfit"}, {"Operating income", "operatingIncome"}, {"Net income", "netIncome"}, {"Free cash flow", "freeCashFlow"}, {"Cash and equivalents", "cash"}, {"Total debt", "debt"}};
            for (String[] r : rows) {
                boolean any = annual.stream().anyMatch(o -> d(map(o), r[1]) != null);
                if (!any) continue;
                m.append("<tr><td>").append(r[0]).append("</td>");
                for (Object o : annual) m.append("<td>").append(h(money(d(map(o), r[1]), c))).append("</td>");
                m.append("</tr>");
            }
            m.append("</table></div><p class=\"src\">Full-year figures. The headline numbers above use the latest quarter or the past 12 months, and include short-term investments in cash, so they can differ from this table.</p><h2>Common questions about ").append(h(name)).append("</h2>");
        }
        if (cap != null) m.append(faq("What is " + name + " worth?", name + "’s market cap, the value of all its shares, is about " + money(cap, capCcy) + "."));
        if (pe != null) m.append(faq("What is " + s + "’s P/E ratio?", "About " + num(pe, "0.0") + ", based on the past 12 months of profit. The P/E ratio compares the share price with profit per share."));
        if (target != null && d(a, "count") != null) m.append(faq("What do analysts expect for " + s + "?", "The average 12-month price target from " + num(d(a, "count"), "0") + " analysts is " + (pence ? num(target, "#,##0.00") + " " + q : money(target, q)) + ". Analyst targets are opinions and are often wrong."));
        if (str(f.get("nextEarnings")) != null) m.append(faq("When does " + name + " report earnings next?", "The next expected report date is " + h(str(f.get("nextEarnings"))) + ". Dates can change."));
        String sector = null;
        for (String[] p : Yahoo.POPULAR) if (p[0].equals(s)) sector = p[1];
        if (sector != null) {
            m.append("<h2>More ").append(h(sector)).append(" stocks</h2><p class=\"links\">");
            int n = 0;
            for (String[] p : Yahoo.POPULAR) if (p[1].equals(sector) && !p[0].equals(s) && n++ < 12) m.append("<a href=\"/stock/").append(h(p[0])).append("\">").append(h(p[0])).append("</a>");
            m.append("</p>");
        }
        m.append("<p class=\"src\">Source: ").append(h(f.get("source"))).append(". ").append(h(asOfLine(str(f.get("priceTime")), str(f.get("fetched")), lq, str(last.get("date"))))).append(" Amounts in ").append(h(c)).append(". Not investment advice.</p>");
        return new Page(200, "text/html; charset=utf-8", page(name + " (" + s + ") Stock: Price, Financials and Analysis | Quantera", desc, path, m.toString(), crumbs(s, "/stocks", "Stocks", path), true));
    }

    private static Map<String, Object> privateBySlug(String sl) {
        for (Object o : list(Yahoo.privates().get("companies"))) if (slug(str(map(o).get("name"))).equals(sl)) return map(o);
        return null;
    }

    /** Sum of the rounds shown when it is under half the reported total (the listing is then clearly incomplete or the total unreliable), otherwise null. */
    static Double listedRaised(Double reported, List<Object> shownRounds) {
        double sum = 0;
        for (Object o : shownRounds) { Double v = d(map(o), "raised"); if (v != null) sum += v; }
        return reported != null && sum > 0 && sum < reported / 2 ? sum : null;
    }

    static final String PVT_METHOD = "How these estimates are made: the estimated valuation is Forge Global's latest price per share multiplied by its estimate of the company's shares. "
            + "That price comes from the latest funding round or, where shares change hands, from secondary-market trades between investors, so it can differ from the valuation the company agreed in its last round. "
            + "Private companies don't publish audited accounts or a daily share price, so treat every figure as an estimate. Funding-round figures are as reported to Forge Global and may be incomplete.";

    /** One row per funding round: closings of the same round on different days, and share classes issued together
     *  (Series H-1, H-2 …), are combined. Rounds arrive newest first. Pure: unit-tested. */
    static List<Map<String, Object>> groupRounds(List<Object> rounds) {
        Map<String, Map<String, Object>> by = new LinkedHashMap<>();
        for (Object o : rounds) {
            Map<String, Object> r = map(o);
            String label = str(r.get("round")) == null ? "Round" : str(r.get("round")), base = label.replaceAll("-[0-9A-Z]{1,3}$", "");
            Double val = d(r, "valuation");
            String key = base + "|" + (val == null ? "?" + r.get("date") : Math.round(val / 1e8));
            Map<String, Object> g = by.computeIfAbsent(key, k -> { Map<String, Object> x = new LinkedHashMap<>(); x.put("round", base); x.put("valuation", val); x.put("dates", new TreeSet<String>()); x.put("labels", new LinkedHashSet<String>()); x.put("prices", new LinkedHashSet<Double>()); x.put("raised", null); return x; });
            if (str(r.get("date")) != null) ((TreeSet<String>) g.get("dates")).add(str(r.get("date")));
            ((LinkedHashSet<String>) g.get("labels")).add(label);
            if (d(r, "issuePrice") != null && d(r, "issuePrice") > 0) ((LinkedHashSet<Double>) g.get("prices")).add(Math.round(d(r, "issuePrice") * 100) / 100.0);
            if (d(r, "raised") != null) g.put("raised", (g.get("raised") == null ? 0 : (Double) g.get("raised")) + d(r, "raised"));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> g : by.values()) {
            TreeSet<String> dates = (TreeSet<String>) g.get("dates"); LinkedHashSet<String> labels = (LinkedHashSet<String>) g.get("labels"); LinkedHashSet<Double> prices = (LinkedHashSet<Double>) g.get("prices");
            String note = dates.size() > 1 ? dates.size() + " closings" : labels.size() > 1 ? labels.size() + " share classes" : null;
            out.add(Json.obj("date", String.join(", ", dates), "round", note == null ? labels.iterator().next() : g.get("round") + " (" + note + ")",
                    "raised", g.get("raised"), "issuePrice", prices.size() == 1 ? prices.iterator().next() : null, "priceVaries", prices.size() > 1, "valuation", g.get("valuation")));
        }
        return out;
    }

    /** How the estimate compares with the last round that set a valuation. Pure: unit-tested. */
    static String pvtBasis(Double estimate, List<Object> rounds) {
        if (estimate == null) return "";
        for (Object o : rounds) { // newest first
            Map<String, Object> r = map(o); Double v = d(r, "valuation");
            if (v == null || v <= 0) continue;
            double gap = estimate / v - 1;
            String when = str(r.get("date")) == null ? "" : " on " + str(r.get("date")), round = str(r.get("round")) == null ? "its last round" : "its " + str(r.get("round")) + " round";
            if (Math.abs(gap) < 0.02) return "This matches the post-money valuation of " + round + when + " (" + money(v, "USD") + ").";
            return "This is " + pct(Math.abs(gap)) + (gap > 0 ? " above" : " below") + " the " + money(v, "USD") + " valuation of " + round + when + ", because it reflects later secondary-market prices.";
        }
        return "No funding round with a published valuation is listed, so this estimate rests on secondary-market prices only.";
    }

    /** A company that has since listed (Anthropic → ANT) gets a clear note and links to its stock page. */
    static String listedNote(String name) {
        Map<String, Object> l;
        try { l = Listed.of(name); } catch (RuntimeException e) { return ""; }
        if (!Boolean.TRUE.equals(l.get("listed"))) return "";
        String sym = str(l.get("symbol"));
        return "<p class=\"basis\"><b>" + h(name) + " is now a public company.</b> It began trading in the US as <a href=\"/stock/" + h(sym) + "\">" + h(sym) + "</a>"
                + (str(l.get("firstTrade")) != null ? " on " + h(l.get("firstTrade")) : "") + ". The figures below are its history before the IPO. <a href=\"/#/trade/" + h(sym) + "\">Paper trade " + h(sym) + "</a>.</p>";
    }

    /** One private company: valuation, funding rounds and what funds report it is worth. */
    static Page privateCompany(String sl) {
        Map<String, Object> co = privateBySlug(sl.toLowerCase(Locale.ROOT));
        if (co == null) return notFound("Company");
        Map<String, Object> f = Yahoo.privateCompany(str(co.get("symbol")));
        String name = str(co.get("name")), path = "/private-company/" + slug(name), sym = str(co.get("symbol"));
        Double val = d(co, "valuation"), fund = d(co, "funding");
        List<Object> rounds = list(f.get("rounds"));
        rounds = rounds.subList(0, Math.min(30, rounds.size()));
        Double listed = listedRaised(fund, rounds);
        if (listed != null) fund = listed;
        String desc = name + " valuation: estimated " + money(val, "USD") + (fund != null ? ", with " + money(fund, "USD") + (listed != null ? " raised in listed rounds" : " raised") : "") + ". Funding rounds, price per share and what mutual funds report it is worth.";
        StringBuilder m = new StringBuilder("<p class=\"crumb\"><a href=\"/private-companies\">Private companies</a> › " + h(name) + "</p><h1>" + h(name) + " valuation</h1><p class=\"px\">" + h(money(val, "USD")) + " <small>estimated</small></p><p class=\"basis\">" + h(pvtBasis(val, rounds)) + "</p>" + listedNote(name) + "<div class=\"kn\">");
        m.append(tile("Past year", d(co, "change52w") == null ? "—" : signed(d(co, "change52w") / 100), "Change in estimated price"));
        if (d(co, "price") != null) m.append(tile("Price per share", money(d(co, "price"), "USD"), "Estimated"));
        if (fund != null) m.append(listed != null ? tile("Total raised (listed rounds)", money(fund, "USD"), "Sum of the rounds below") : tile("Total raised", money(fund, "USD"), "Reported total"));
        if (str(co.get("lastRound")) != null) m.append(tile("Latest round", str(co.get("lastRound")), h(str(co.get("lastRoundDate")))));
        m.append("</div><p class=\"go\"><a class=\"cta\" href=\"/#/pvt/").append(h(sym)).append("\">See the chart and full profile</a></p><h2>What is ").append(h(name)).append("?</h2><p>").append(h(f.get("description"))).append("</p>");
        m.append("<h2>Can you buy ").append(h(name)).append(" stock?</h2><p>").append(h(name)).append(" is a private company, so its shares aren’t sold on a stock exchange or through a normal brokerage. Its value is estimated from funding rounds and secondary-market trades, and it updates only when new deals happen.</p>");
        if (!rounds.isEmpty()) {
            m.append("<h2>").append(h(name)).append(" funding rounds</h2><div class=\"tw\"><table><tr><th>Date</th><th>Round</th><th>Raised</th><th>Price per share</th><th>Post-money valuation</th></tr>");
            for (Map<String, Object> r : groupRounds(rounds)) { m.append("<tr><td>").append(h(r.get("date"))).append("</td><td>").append(h(r.get("round"))).append("</td><td>").append(h(money(d(r, "raised"), "USD"))).append("</td><td>").append(Boolean.TRUE.equals(r.get("priceVaries")) ? "Varies by class" : d(r, "issuePrice") == null ? "—" : "$" + h(num(d(r, "issuePrice"), "#,##0.00"))).append("</td><td>").append(h(money(d(r, "valuation"), "USD"))).append("</td></tr>"); }
            m.append("</table></div><p class=\"src\">Rounds that closed in several parts, or issued several share classes together, are combined into one row. Amounts are as reported to Forge Global and can differ from press reports.</p>");
        }
        m.append("<p class=\"src\">Source: ").append(h(f.get("source"))).append(". ").append(h(PVT_METHOD)).append(" ").append(h(fetchedLine(str(Yahoo.privates().get("updated"))))).append(" Not investment advice.</p>");
        return new Page(200, "text/html; charset=utf-8", page(name + " Valuation, Funding Rounds and Share Price | Quantera", desc, path, m.toString(), crumbs(name, "/private-companies", "Private companies", path), true));
    }

    static Page stocks() {
        StringBuilder m = new StringBuilder("<h1>Stocks</h1><p>Plain-English pages for well-known companies: what they’re worth, how fast they’re growing and whether they make money. Quantera’s <a href=\"/#/stocks\">app</a> covers stocks, ETFs and indexes worldwide.</p>");
        Map<String, List<String>> by = new LinkedHashMap<>();
        for (String[] p : Yahoo.POPULAR) if (!p[1].equals("ETFs")) by.computeIfAbsent(p[1], x -> new ArrayList<>()).add(p[0]); // funds have no company financials, so no page
        by.forEach((sec, syms) -> { m.append("<h2>").append(h(sec)).append("</h2><p class=\"links\">"); for (String s : syms) m.append("<a href=\"/stock/").append(h(s)).append("\">").append(h(s)).append("</a>"); m.append("</p>"); });
        return new Page(200, "text/html; charset=utf-8", page("Stock Directory: Financials and Analysis by Company | Quantera", "Browse well-known stocks by sector, with market cap, financials, analyst targets and plain-English summaries.", "/stocks", m.toString(), null, true));
    }

    static Page privates() {
        StringBuilder m = new StringBuilder("<h1>Private company valuations</h1><p>What OpenAI, Anthropic, Stripe and other private companies are estimated to be worth, how much they have raised, and their latest funding rounds.</p><div class=\"tw\"><table><tr><th>Company</th><th>Sector</th><th>Estimated valuation</th><th>Latest round</th><th>Total raised</th><th>Past year</th></tr>");
        for (Object o : list(Yahoo.privates().get("companies"))) { Map<String, Object> c = map(o); m.append("<tr><td><a href=\"/private-company/").append(h(slug(str(c.get("name"))))).append("\">").append(h(c.get("name"))).append("</a></td><td>").append(h(c.get("sector"))).append("</td><td>").append(h(money(d(c, "valuation"), "USD"))).append("</td><td>").append(str(c.get("lastRound")) == null ? "—" : h(str(c.get("lastRound"))) + (str(c.get("lastRoundDate")) == null ? "" : "<br><small>" + h(str(c.get("lastRoundDate"))) + "</small>")).append("</td><td>").append(h(money(d(c, "funding"), "USD"))).append("</td><td>").append(d(c, "change52w") == null ? "—" : h(signed(d(c, "change52w") / 100))).append("</td></tr>"); }
        m.append("</table></div><p class=\"src\">Source: Yahoo Finance, with valuations and funding data from Forge Global. ").append(h(PVT_METHOD)).append(" Each company's page compares its estimate with its last funding round. ").append(h(fetchedLine(str(Yahoo.privates().get("updated"))))).append(" Not investment advice.</p>");
        return new Page(200, "text/html; charset=utf-8", page("Private Company Valuations: OpenAI, Anthropic, Stripe and More | Quantera", "Estimated valuations, funding totals and latest rounds for the biggest private companies, including OpenAI, Anthropic and Stripe.", "/private-companies", m.toString(), null, true));
    }

    /** Stock and private company URLs; lessons are listed in the static sitemap-learn.xml. */
    static Page sitemap() {
        StringBuilder x = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
        for (String p : List.of("/stocks", "/private-companies")) x.append("<url><loc>").append(h(base() + p)).append("</loc></url>");
        for (String[] p : Yahoo.POPULAR) if (!p[1].equals("ETFs")) x.append("<url><loc>").append(h(base() + "/stock/" + p[0])).append("</loc></url>");
        for (Object o : list(Yahoo.privates().get("companies"))) x.append("<url><loc>").append(h(base() + "/private-company/" + slug(str(map(o).get("name"))))).append("</loc></url>");
        return new Page(200, "application/xml; charset=utf-8", x.append("</urlset>").toString());
    }

    /** Null when the path isn't one of these pages. */
    static Page route(String path) {
        if (path.equals("/seo/stocks")) return stocks();
        if (path.equals("/seo/private")) return privates();
        if (path.equals("/seo/sitemap.xml")) return sitemap();
        if (path.startsWith("/seo/stock/")) return stock(path.substring(11));
        if (path.startsWith("/seo/private/")) return privateCompany(path.substring(13));
        return null;
    }
}
