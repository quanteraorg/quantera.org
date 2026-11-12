package com.quantera;

import java.time.LocalDate;
import java.util.*;

import static com.quantera.Support.*;

/**
 * Full financial statements (income statement, balance sheet, cash flow) from a company's SEC XBRL filings,
 * annual (up to 10 years) and quarterly (up to 12 quarters), with key ratios and a link to the filing behind
 * every column.
 *
 * Each line tries a short list of US-GAAP tags in order. Companies switch tags over the years (e.g. "Revenues"
 * to "RevenueFromContractWithCustomer..."), so for each period the first tag with a value wins, starting with the
 * tag that has the most recent data. Values come from the latest filing that reported the period, so restated
 * figures replace the originals. Nothing is estimated: a line a company doesn't report is left blank, and the few
 * derived lines (free cash flow, and gross profit or total liabilities when not reported) say how they were worked out.
 */
final class Statements {
    private Statements() {}

    /** One reported value: the figure, the period end, and the filing it was taken from. */
    record Fact(double v, long end, String accn, String form, long filed) {}

    /** frame (CY2024, CY2024Q3, CY2024Q3I) -> latest-filed value for that period. */
    static Map<String, Fact> facts(Map<String, Object> g, String tag, String unit) {
        List<Object> all = list(map(map(map(g.get(tag)).get("units"))).get(unit));
        Map<String, Object[]> latest = new HashMap<>(); // "start|end" -> {filed, value, accn, form}
        for (Object o : all) {
            Map<String, Object> x = map(o);
            Double v = num(x.get("val"));
            if (v == null) continue;
            String p = str(x.get("start")) + "|" + str(x.get("end")), filed = Objects.toString(str(x.get("filed")), "");
            Object[] cur = latest.get(p);
            if (cur == null || filed.compareTo((String) cur[0]) > 0) latest.put(p, new Object[]{filed, v, str(x.get("accn")), str(x.get("form"))});
        }
        Map<String, Fact> out = new TreeMap<>();
        for (Object o : all) {
            Map<String, Object> x = map(o);
            String fr = str(x.get("frame")), end = str(x.get("end"));
            if (fr == null || end == null || num(x.get("val")) == null) continue;
            Object[] l = latest.get(str(x.get("start")) + "|" + end);
            String fd = (String) l[0];
            out.put(fr, new Fact((Double) l[1], LocalDate.parse(end).toEpochDay(), (String) l[2], (String) l[3], fd.isEmpty() ? Long.MAX_VALUE : LocalDate.parse(fd).toEpochDay()));
        }
        return out;
    }

    /** Tags for one line, the one with the most recent data first; per period the first tag with a value is used. */
    static List<Map<String, Fact>> ordered(Map<String, Object> g, String unit, boolean instant, String... tags) {
        List<Object[]> found = new ArrayList<>();
        for (String t : tags) {
            Map<String, Fact> f = facts(g, t, unit);
            if (f.isEmpty()) continue;
            long last = 0;
            for (Map.Entry<String, Fact> e : f.entrySet()) if (e.getKey().endsWith("I") == instant) last = Math.max(last, e.getValue().end());
            if (last > 0) found.add(new Object[]{last, f});
        }
        found.sort((a, b) -> Long.compare((Long) b[0], (Long) a[0]));
        List<Map<String, Fact>> out = new ArrayList<>();
        for (Object[] x : found) { @SuppressWarnings("unchecked") Map<String, Fact> f = (Map<String, Fact>) x[1]; out.add(f); }
        return out;
    }

    /** A flow (income or cash flow) for a period frame like CY2024 or CY2024Q3. */
    static Fact flow(List<Map<String, Fact>> src, String frame) {
        for (Map<String, Fact> f : src) { Fact x = f.get(frame); if (x != null) return x; }
        return null;
    }

    /** A balance at a period's end date (instant frames are calendar-aligned, so match on the date itself). */
    static Fact at(List<Map<String, Fact>> src, long end) {
        for (Map<String, Fact> f : src) {
            Fact best = null;
            for (Map.Entry<String, Fact> e : f.entrySet())
                if (e.getKey().endsWith("I") && Math.abs(e.getValue().end() - end) <= 4 && (best == null || Math.abs(e.getValue().end() - end) < Math.abs(best.end() - end))) best = e.getValue();
            if (best != null) return best;
        }
        return null;
    }

    // [key, label, unit, kind, emphasis, tags...]; kind: usd, ps (per share), sh (shares)
    static final String[][] INCOME = {
        {"revenue", "Revenue", "USD", "usd", "1", "Revenues", "RevenueFromContractWithCustomerExcludingAssessedTax", "SalesRevenueNet", "RevenueFromContractWithCustomerIncludingAssessedTax", "SalesRevenueGoodsNet"},
        {"costOfRevenue", "Cost of revenue", "USD", "usd", "0", "CostOfRevenue", "CostOfGoodsAndServicesSold", "CostOfGoodsSold", "CostOfServices"},
        {"grossProfit", "Gross profit", "USD", "usd", "1", "GrossProfit"},
        {"rnd", "Research and development", "USD", "usd", "0", "ResearchAndDevelopmentExpense", "ResearchAndDevelopmentExpenseExcludingAcquiredInProcessCost"},
        {"sga", "Selling, general and admin", "USD", "usd", "0", "SellingGeneralAndAdministrativeExpense"},
        {"netInterestIncome", "Net interest income", "USD", "usd", "1", "InterestIncomeExpenseNet", "InterestIncomeExpenseAfterProvisionForLoanLoss"},
        {"noninterestIncome", "Non-interest income", "USD", "usd", "0", "NoninterestIncome"},
        {"provision", "Provision for credit losses", "USD", "usd", "0", "ProvisionForLoanLeaseAndOtherLosses", "ProvisionForLoanAndLeaseLosses", "ProvisionForCreditLosses"},
        {"operatingIncome", "Operating income", "USD", "usd", "1", "OperatingIncomeLoss"},
        {"interestExpense", "Interest expense", "USD", "usd", "0", "InterestExpense", "InterestExpenseNonoperating", "InterestExpenseDebt"},
        {"pretaxIncome", "Profit before tax", "USD", "usd", "0", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesMinorityInterestAndIncomeLossFromEquityMethodInvestments", "IncomeLossFromContinuingOperationsBeforeIncomeTaxesDomestic"},
        {"incomeTax", "Income tax", "USD", "usd", "0", "IncomeTaxExpenseBenefit"},
        {"netIncome", "Net income", "USD", "usd", "1", "NetIncomeLoss", "ProfitLoss", "NetIncomeLossAvailableToCommonStockholdersBasic"},
        {"eps", "Earnings per share (diluted)", "USD/shares", "ps", "0", "EarningsPerShareDiluted", "EarningsPerShareBasicAndDiluted", "EarningsPerShareBasic"},
        {"shares", "Shares used (diluted)", "shares", "sh", "0", "WeightedAverageNumberOfDilutedSharesOutstanding", "WeightedAverageNumberOfShareOutstandingBasicAndDiluted", "WeightedAverageNumberOfSharesOutstandingBasic"},
    };
    static final String[][] BALANCE = {
        {"cash", "Cash and equivalents", "USD", "usd", "0", "CashAndCashEquivalentsAtCarryingValue", "CashCashEquivalentsRestrictedCashAndRestrictedCashEquivalents", "Cash"},
        {"shortTermInvestments", "Short-term investments", "USD", "usd", "0", "ShortTermInvestments", "MarketableSecuritiesCurrent", "AvailableForSaleSecuritiesDebtSecuritiesCurrent"},
        {"receivables", "Receivables", "USD", "usd", "0", "AccountsReceivableNetCurrent", "ReceivablesNetCurrent"},
        {"inventory", "Inventory", "USD", "usd", "0", "InventoryNet"},
        {"currentAssets", "Total current assets", "USD", "usd", "1", "AssetsCurrent"},
        {"loans", "Loans (net)", "USD", "usd", "0", "LoansAndLeasesReceivableNetReportedAmount", "LoansAndLeasesReceivableNetOfDeferredIncome", "FinancingReceivableExcludingAccruedInterestAfterAllowanceForCreditLoss"},
        {"ppe", "Property and equipment", "USD", "usd", "0", "PropertyPlantAndEquipmentNet", "PropertyPlantAndEquipmentAndFinanceLeaseRightOfUseAssetAfterAccumulatedDepreciationAndAmortization"},
        {"goodwill", "Goodwill", "USD", "usd", "0", "Goodwill"},
        {"assets", "Total assets", "USD", "usd", "1", "Assets"},
        {"payables", "Accounts payable", "USD", "usd", "0", "AccountsPayableCurrent", "AccountsPayableAndAccruedLiabilitiesCurrent"},
        {"deposits", "Deposits", "USD", "usd", "0", "Deposits"},
        {"currentLiabilities", "Total current liabilities", "USD", "usd", "1", "LiabilitiesCurrent"},
        {"longTermDebt", "Long-term debt", "USD", "usd", "0", "LongTermDebtNoncurrent", "LongTermDebt", "LongTermDebtAndCapitalLeaseObligations"},
        {"liabilities", "Total liabilities", "USD", "usd", "1", "Liabilities"},
        {"equity", "Shareholders' equity", "USD", "usd", "1", "StockholdersEquity", "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest"},
    };
    static final String[][] CASH = {
        {"operatingCashFlow", "Cash from operations", "USD", "usd", "1", "NetCashProvidedByUsedInOperatingActivities", "NetCashProvidedByUsedInOperatingActivitiesContinuingOperations"},
        {"depreciation", "Depreciation and amortization", "USD", "usd", "0", "DepreciationDepletionAndAmortization", "DepreciationAndAmortization", "DepreciationAmortizationAndAccretionNet", "Depreciation"},
        {"stockComp", "Stock-based pay", "USD", "usd", "0", "ShareBasedCompensation", "AllocatedShareBasedCompensationExpense"},
        {"capex", "Capital spending", "USD", "usd", "0", "PaymentsToAcquirePropertyPlantAndEquipment", "PaymentsToAcquireProductiveAssets"},
        {"freeCashFlow", "Free cash flow", "USD", "usd", "1"},
        {"investingCashFlow", "Cash from investing", "USD", "usd", "0", "NetCashProvidedByUsedInInvestingActivities", "NetCashProvidedByUsedInInvestingActivitiesContinuingOperations"},
        {"dividends", "Dividends paid", "USD", "usd", "0", "PaymentsOfDividends", "PaymentsOfDividendsCommonStock"},
        {"buybacks", "Share buybacks", "USD", "usd", "0", "PaymentsForRepurchaseOfCommonStock"},
        {"financingCashFlow", "Cash from financing", "USD", "usd", "0", "NetCashProvidedByUsedInFinancingActivities", "NetCashProvidedByUsedInFinancingActivitiesContinuingOperations"},
    };
    /** Lines reported as positive payments, shown as money going out. */
    static final Set<String> OUTFLOWS = Set.of("capex", "dividends", "buybacks");

    /** SIC 6000-6799: banks, insurers, brokers, REITs and other finance companies, where some ratios don't apply. */
    static boolean financial(Object sic) { Double s = num(sic); return s != null && s >= 6000 && s < 6800; }

    static Map<String, Object> of(String symbol) {
        return Sec.CACHE.get("secst" + symbol, 12 * 3600_000L, () -> {
            long c = Sec.cik(symbol);
            String data = cfg("SEC_DATA_URL", "https://data.sec.gov");
            Map<String, Object> g = map(map(map(Sec.get(data + "/api/xbrl/companyfacts/CIK" + Sec.pad(c) + ".json", 30)).get("facts")).get("us-gaap"));
            if (g.isEmpty()) throw new UpstreamException("No US-GAAP financial statements for this company (foreign companies often report under IFRS).", 404);
            Map<String, Object> sub = map(Sec.get(data + "/submissions/CIK" + Sec.pad(c) + ".json", 20));
            boolean fin = financial(sub.get("sic"));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", symbol);
            out.put("name", str(sub.get("name")));
            out.put("industry", str(sub.get("sicDescription")));
            out.put("financial", fin);
            Map<String, Object> an = build(g, c, false, fin), qu = build(g, c, true, fin);
            if (list(an.get("periods")).isEmpty() && list(qu.get("periods")).isEmpty())
                throw new UpstreamException("No financial statements are filed under this company's current SEC registration yet (for example, after a reorganisation into a new holding company).", 404);
            out.put("annual", an);
            out.put("quarterly", qu);
            out.put("currency", "USD");
            out.put("source", "SEC EDGAR (XBRL company facts)");
            return out;
        });
    }

    static Map<String, Object> build(Map<String, Object> g, long cik, boolean quarterly, boolean fin) {
        String pattern = quarterly ? "CY\\d{4}Q\\d" : "CY\\d{4}";
        // periods: every frame where the company reported revenue or net income
        List<Map<String, Fact>> base = new ArrayList<>(ordered(g, "USD", false, row(INCOME, "revenue")));
        base.addAll(ordered(g, "USD", false, row(INCOME, "netIncome")));
        TreeMap<String, Fact> periods = new TreeMap<>();
        for (Map<String, Fact> f : base) for (Map.Entry<String, Fact> e : f.entrySet()) if (e.getKey().matches(pattern) && (quarterly || e.getValue().form() == null || !e.getValue().form().startsWith("10-Q"))) periods.putIfAbsent(e.getKey(), e.getValue()); // a 12-month figure only in a 10-Q is a trailing year, not a fiscal year
        Map<String, String[]> derived = new HashMap<>(); // quarter key -> {annual frame, q1, q2, q3}
        if (quarterly) {
            TreeMap<String, Fact> years = new TreeMap<>();
            for (Map<String, Fact> f : base) for (Map.Entry<String, Fact> e : f.entrySet())
                if (e.getKey().matches("CY\\d{4}") && (e.getValue().form() == null || !e.getValue().form().startsWith("10-Q"))) years.putIfAbsent(e.getKey(), e.getValue());
            for (Map.Entry<String, Fact> y : years.entrySet()) {
                long e = y.getValue().end();
                List<String> qs = new ArrayList<>();
                boolean has4 = false;
                for (Map.Entry<String, Fact> q : periods.entrySet()) {
                    long d = e - q.getValue().end();
                    if (Math.abs(d) <= 10) has4 = true;
                    else if (d > 60 && d < 300) qs.add(q.getKey());
                }
                if (has4 || qs.size() != 3) continue;
                LocalDate mid = LocalDate.ofEpochDay(e).minusDays(45); // the calendar quarter the SEC would file it under
                String key = "CY" + mid.getYear() + "Q" + ((mid.getMonthValue() - 1) / 3 + 1);
                if (periods.containsKey(key)) continue;
                periods.put(key, y.getValue());
                derived.put(key, new String[]{y.getKey(), qs.get(0), qs.get(1), qs.get(2)});
            }
        }
        List<String> keys = new ArrayList<>(periods.keySet());
        keys = keys.subList(Math.max(0, keys.size() - (quarterly ? 12 : 10)), keys.size());
        List<Object> cols = new ArrayList<>();
        long[] ends = new long[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            Fact f = periods.get(keys.get(i));
            ends[i] = f.end();
            LocalDate end = LocalDate.ofEpochDay(f.end());
            String accn = f.accn();
            cols.add(Json.obj("key", keys.get(i), "end", end.toString(),
                    "label", quarterly ? end.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.US) + " " + end.getYear() : "FY" + end.getYear(),
                    "form", f.form(), "filing", accn == null ? null : "https://www.sec.gov/Archives/edgar/data/" + cik + "/" + accn.replace("-", "") + "/" + accn + "-index.htm",
                    "derived", derived.containsKey(keys.get(i)) ? "Fourth quarter worked out as the full year minus the first three quarters: companies report it only inside the annual report" : null));
        }
        Map<String, double[]> vals = new HashMap<>();
        Map<String, long[]> filed = new HashMap<>();
        List<Object> income = lines(g, INCOME, keys, ends, false, vals, filed, derived);
        List<Object> balance = lines(g, BALANCE, keys, ends, true, vals, filed, derived);
        List<Object> cash = lines(g, CASH, keys, ends, false, vals, filed, derived);
        // derived lines, each saying how it was worked out
        double[] rev = vals.get("revenue"), cor = vals.get("costOfRevenue"), gp = vals.get("grossProfit");
        if (rev != null && cor != null) {
            boolean filled = false;
            if (gp == null) { gp = new double[keys.size()]; Arrays.fill(gp, Double.NaN); }
            for (int i = 0; i < gp.length; i++) if (Double.isNaN(gp[i]) && !Double.isNaN(rev[i]) && !Double.isNaN(cor[i])) { gp[i] = rev[i] - cor[i]; filled = true; }
            if (filled) { vals.put("grossProfit", gp); replace(income, INCOME, "grossProfit", gp, "Revenue minus cost of revenue where the filing doesn't state it"); }
        }
        double[] ocf = vals.get("operatingCashFlow"), cx = vals.get("capex");
        if (ocf != null && cx != null) {
            double[] fcf = new double[keys.size()];
            for (int i = 0; i < fcf.length; i++) fcf[i] = Double.isNaN(ocf[i]) || Double.isNaN(cx[i]) ? Double.NaN : ocf[i] + cx[i]; // capex is already negative
            vals.put("freeCashFlow", fcf);
            replace(cash, CASH, "freeCashFlow", fcf, fin ? "Cash from operations minus capital spending. Not meaningful for banks and insurers, whose operating cash flow includes lending and deposits" : "Cash from operations minus capital spending");
        }
        double[] liab = vals.get("liabilities");
        if (liab == null || anyNaN(liab)) {
            List<Map<String, Fact>> les = ordered(g, "USD", true, "LiabilitiesAndStockholdersEquity"), eqi = ordered(g, "USD", true, "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest", "StockholdersEquity");
            double[] l2 = liab == null ? nan(keys.size()) : liab.clone();
            boolean filled = false;
            for (int i = 0; i < l2.length; i++) if (Double.isNaN(l2[i])) { Fact a = at(les, ends[i]), e = at(eqi, ends[i]); if (a != null && e != null) { l2[i] = a.v() - e.v(); filled = true; } }
            if (filled) { vals.put("liabilities", l2); replace(balance, BALANCE, "liabilities", l2, "Total liabilities and equity minus equity, where the filing doesn't state total liabilities"); }
        }
        List<double[]> splits = splits(g);
        if (splitAdjust(vals.get("eps"), vals.get("shares"), vals.get("netIncome"), filed.get("eps") != null ? filed.get("eps") : filed.get("shares"), splits)) {
            String note = "Earlier periods adjusted for later stock splits, so every column uses today's share basis";
            if (vals.get("eps") != null) replace(income, INCOME, "eps", vals.get("eps"), note);
            if (vals.get("shares") != null) replace(income, INCOME, "shares", vals.get("shares"), note);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("periods", cols);
        out.put("income", income);
        out.put("balance", balance);
        out.put("cashflow", cash);
        out.put("ratios", ratios(vals, keys.size(), quarterly, fin));
        return out;
    }

    static String[] row(String[][] table, String key) { for (String[] r : table) if (r[0].equals(key)) return Arrays.copyOfRange(r, 5, r.length); return new String[0]; }

    static List<Object> lines(Map<String, Object> g, String[][] table, List<String> keys, long[] ends, boolean instant, Map<String, double[]> vals, Map<String, long[]> filed, Map<String, String[]> derived) {
        List<Object> out = new ArrayList<>();
        for (String[] r : table) {
            double[] v = nan(keys.size());
            if (r.length > 5) {
                List<Map<String, Fact>> src = ordered(g, r[2], instant, Arrays.copyOfRange(r, 5, r.length));
                long[] fl = new long[keys.size()];
                for (int i = 0; i < keys.size(); i++) {
                    Fact f = instant ? at(src, ends[i]) : flow(src, keys.get(i));
                    String[] d = instant ? null : derived.get(keys.get(i));
                    if (f == null && d != null && !"ps".equals(r[3]) && !"sh".equals(r[3])) { // per-share and share counts don't subtract cleanly
                        Fact a = flow(src, d[0]), q1 = flow(src, d[1]), q2 = flow(src, d[2]), q3 = flow(src, d[3]);
                        if (a != null && q1 != null && q2 != null && q3 != null) f = new Fact(a.v() - q1.v() - q2.v() - q3.v(), a.end(), a.accn(), a.form(), a.filed());
                    }
                    if (f != null) { v[i] = OUTFLOWS.contains(r[0]) ? -Math.abs(f.v()) : f.v(); fl[i] = f.filed(); }
                }
                filed.put(r[0], fl);
            }
            if (allNaN(v)) { out.add(null); continue; } // placeholder keeps positions for derived lines; removed at the end
            vals.put(r[0], v);
            out.add(line(r, v, null));
        }
        out.removeIf(Objects::isNull);
        return out;
    }

    static Map<String, Object> line(String[] r, double[] v, String note) {
        List<Object> list = new ArrayList<>();
        for (double x : v) list.add(Double.isNaN(x) ? null : x);
        return Json.obj("key", r[0], "label", r[1], "kind", r[3], "total", "1".equals(r[4]), "values", list, "note", note);
    }

    /** Puts a derived line in its place in the statement (or updates it). */
    static void replace(List<Object> lines, String[][] table, String key, double[] v, String note) {
        String[] r = null; int order = 0;
        for (int i = 0; i < table.length; i++) if (table[i][0].equals(key)) { r = table[i]; order = i; }
        lines.removeIf(o -> key.equals(map(o).get("key")));
        int at = 0;
        for (; at < lines.size(); at++) { String k = str(map(lines.get(at)).get("key")); int idx = 0; for (int i = 0; i < table.length; i++) if (table[i][0].equals(k)) idx = i; if (idx > order) break; }
        lines.add(at, line(r, v, note));
    }

    static List<Object> ratios(Map<String, double[]> v, int n, boolean quarterly, boolean fin) {
        List<Object> out = new ArrayList<>();
        ratio(out, n, "grossMargin", "Gross margin", "pct", v.get("grossProfit"), v.get("revenue"), fin ? "Not meaningful for most banks and insurers" : "Gross profit ÷ revenue", fin);
        ratio(out, n, "operatingMargin", "Operating margin", "pct", v.get("operatingIncome"), v.get("revenue"), "Operating income ÷ revenue", false);
        ratio(out, n, "netMargin", "Net margin", "pct", v.get("netIncome"), v.get("revenue"), "Net income ÷ revenue", false);
        ratio(out, n, "fcfMargin", "Free cash flow margin", "pct", v.get("freeCashFlow"), v.get("revenue"), "Free cash flow ÷ revenue", fin);
        ratio(out, n, "roe", quarterly ? "Return on equity (quarter)" : "Return on equity", "pct", v.get("netIncome"), v.get("equity"), "Net income ÷ shareholders' equity at period end" + (quarterly ? "; one quarter, not annualised" : ""), false);
        ratio(out, n, "taxRate", "Effective tax rate", "pct", v.get("incomeTax"), v.get("pretaxIncome"), "Income tax ÷ profit before tax", false);
        ratio(out, n, "currentRatio", "Current ratio", "x", v.get("currentAssets"), v.get("currentLiabilities"), "Current assets ÷ current liabilities", fin);
        ratio(out, n, "debtToEquity", "Long-term debt to equity", "x", v.get("longTermDebt"), v.get("equity"), "Long-term debt ÷ shareholders' equity", false);
        return out;
    }

    static void ratio(List<Object> out, int n, String key, String label, String kind, double[] a, double[] b, String note, boolean skip) {
        if (skip || a == null || b == null) return;
        double[] r = nan(n);
        for (int i = 0; i < n; i++) if (!Double.isNaN(a[i]) && !Double.isNaN(b[i]) && b[i] > 0) r[i] = a[i] / b[i];
        if (allNaN(r)) return;
        out.add(line(new String[]{key, label, "", kind, "0"}, r, note));
    }

    /**
     * Stock splits the company tagged in its own filings (StockholdersEquityNoteStockSplitConversionRatio1), as
     * {ratio, from, to}: the split took effect somewhere between those dates. Filings tag a split with the date it was
     * announced, the date it took effect, or their own period end, so the window runs from the earliest such date
     * (ignoring old comparative periods) to the latest one within about three months of the first filing mentioning it.
     */
    static List<double[]> splits(Map<String, Object> g) {
        List<Object> all = new ArrayList<>(list(map(map(map(g.get("StockholdersEquityNoteStockSplitConversionRatio1")).get("units"))).get("pure")));
        all.sort(Comparator.comparing(o -> Objects.toString(str(map(o).get("filed")), "")));
        List<double[]> out = new ArrayList<>(); // {ratio, firstFiled, from, to}
        for (Object o : all) {
            Map<String, Object> x = map(o);
            Double r = num(x.get("val"));
            String fd = str(x.get("filed")), en = str(x.get("end"));
            if (r == null || r <= 0 || Math.abs(r - 1) < 1e-9 || fd == null || en == null) continue;
            long filedDay = LocalDate.parse(fd).toEpochDay(), endDay = LocalDate.parse(en).toEpochDay();
            double[] hit = null;
            for (double[] sp : out) if (Math.abs(sp[0] - r) < 1e-9 && filedDay - sp[1] < 3 * 365) hit = sp;
            if (hit == null) { hit = new double[]{r, filedDay, Double.MAX_VALUE, -Double.MAX_VALUE}; out.add(hit); }
            if (endDay >= hit[1] - 180 && endDay <= hit[1] + 100) { hit[2] = Math.min(hit[2], endDay); hit[3] = Math.max(hit[3], endDay); }
        }
        List<double[]> res = new ArrayList<>();
        for (double[] sp : out) res.add(sp[2] == Double.MAX_VALUE ? new double[]{sp[0], sp[1], sp[1]} : new double[]{sp[0], sp[2], sp[3]});
        return res;
    }

    /**
     * A figure keeps the share basis of the filing it was last reported in, and filings made after a split restate older
     * years. So a figure last filed before a split's window needs that split (e.g. NVIDIA's FY2017 EPS, last filed in
     * 2019, before its 4-for-1 and 10-for-1 splits), one filed after it doesn't, and one filed inside the window (between
     * announcement and effect, like Alphabet's FY2021 10-K) needs it only if its share count is lower than the next newer
     * period's by about the split ratio. EPS is divided and share counts multiplied by the combined factor, in place.
     */
    static boolean splitAdjust(double[] eps, double[] shares, double[] ni, long[] filed, List<double[]> splits) {
        if (filed == null || splits.isEmpty()) return false;
        int n = filed.length;
        boolean changed = false;
        double newer = Double.NaN; // share count of the next newer period, on today's basis
        for (int i = n - 1; i >= 0; i--) {
            double raw = shares != null && !Double.isNaN(shares[i]) ? shares[i]
                    : eps != null && ni != null && !Double.isNaN(eps[i]) && !Double.isNaN(ni[i]) && Math.abs(eps[i]) > 1e-9 && ni[i] / eps[i] > 0 ? ni[i] / eps[i] : Double.NaN;
            double f = 1;
            if (filed[i] > 0) for (double[] sp : splits) {
                if (filed[i] < sp[1]) f *= sp[0];
                else if (filed[i] <= sp[2] && !Double.isNaN(raw) && !Double.isNaN(newer) && Math.abs(newer / (raw * f) / sp[0] - 1) < 0.35) f *= sp[0];
            }
            if (f != 1) {
                if (eps != null && !Double.isNaN(eps[i])) eps[i] = Math.round(eps[i] / f * 100) / 100.0;
                if (shares != null && !Double.isNaN(shares[i])) shares[i] = shares[i] * f;
                changed = true;
            }
            if (!Double.isNaN(raw)) newer = raw * f;
        }
        return changed;
    }

    static double[] nan(int n) { double[] d = new double[n]; Arrays.fill(d, Double.NaN); return d; }
    static boolean allNaN(double[] d) { for (double x : d) if (!Double.isNaN(x)) return false; return true; }
    static boolean anyNaN(double[] d) { for (double x : d) if (Double.isNaN(x)) return true; return false; }
}
