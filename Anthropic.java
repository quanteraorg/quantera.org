package com.quantera;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import static com.quantera.Support.*;

/** Claude chat with server-side tools. The API key never leaves the server. */
final class Anthropic {
    private Anthropic() {}

    static final String SYSTEM = "You are Quantera AI inside a finance education and research app. Explain in plain English for beginners. "
            + "Use the tools for every price, figure or headline and never invent data; if a tool fails or finds nothing, say so. Prices are end-of-day or delayed. "
            + "Keep answers under 180 words. When analysing, use three labelled parts: **Data** (what the tools returned), **Analysis** (what it shows), "
            + "**Interpretation** (what it might mean, noting this is not investment advice). End with helpful links using exactly these tokens: [[asset:SYMBOL]] or [[lesson:ID]] "
            + "with ids ratios, cashflow, peg, capm, rsi, options101, bs, sharpe, kelly, var, wacc, duration, compound, pe, fees, divers, bond, dcf.";

    private static Map<String, Object> tool(String name, String desc, Map<String, Object> props, String... required) {
        return Json.obj("name", name, "description", desc, "input_schema", Json.obj("type", "object", "properties", props, "required", List.of(required)));
    }

    static final List<Object> TOOLS = List.of(
            tool("get_quote", "Latest end-of-day quote for a ticker.", Json.obj("symbol", Json.obj("type", "string")), "symbol"),
            tool("get_price_stats", "Return, high, low and worst drop for a ticker over N trading days (max 1000).", Json.obj("symbol", Json.obj("type", "string"), "days", Json.obj("type", "number")), "symbol"),
            tool("get_company", "Company profile and key fundamentals for a ticker.", Json.obj("symbol", Json.obj("type", "string")), "symbol"),
            tool("search_symbols", "Find tickers by company name or keyword.", Json.obj("query", Json.obj("type", "string")), "query"),
            tool("get_news", "Recent headlines, optionally for tickers (comma-separated).", Json.obj("symbols", Json.obj("type", "string"))));

    private static String sym(Object x) {
        String s = String.valueOf(x == null ? "" : x).toUpperCase();
        if (!SYM.matcher(s).matches()) throw new UpstreamException("Bad symbol", 400);
        return s;
    }

    static Object runTool(String name, Map<String, Object> in, Set<String> used) {
        try {
            switch (name) {
                case "get_quote": { String s = sym(in.get("symbol")); used.add("Quote " + s); return Providers.quote(s, 60_000); }
                case "get_price_stats": {
                    String s = sym(in.get("symbol")); used.add("Price history " + s);
                    Double dd = num(in.get("days"));
                    int days = (int) Math.min(dd == null || dd < 1 ? 252 : dd, 1000);
                    List<Object> all = list(Providers.history(s, true).get("bars"));
                    List<Object> bars = all.subList(Math.max(0, all.size() - days), all.size());
                    double first = num(map(bars.get(0)).get("c")), last = num(map(bars.get(bars.size() - 1)).get("c")), hi = -1e18, lo = 1e18, pk = 0, dd2 = 0;
                    for (Object b : bars) {
                        double c = num(map(b).get("c"));
                        hi = Math.max(hi, num(map(b).get("h"))); lo = Math.min(lo, num(map(b).get("l")));
                        pk = Math.max(pk, c); dd2 = Math.min(dd2, c / pk - 1);
                    }
                    return Json.obj("symbol", s, "days", bars.size(), "returnPct", Math.round((last / first - 1) * 1000) / 10.0, "high", hi, "low", lo, "worstDropPct", Math.round(dd2 * 1000) / 10.0);
                }
                case "get_company": { String s = sym(in.get("symbol")); used.add("Company " + s); return Providers.overview(s); }
                case "search_symbols": {
                    String t = String.valueOf(in.getOrDefault("query", "")); t = t.length() > 40 ? t.substring(0, 40) : t;
                    used.add("Search “" + t + "”"); return Providers.search(t);
                }
                case "get_news": {
                    String s = String.valueOf(in.getOrDefault("symbols", "")).toUpperCase().replaceAll("[^A-Z0-9.,\\-]", "");
                    used.add("News"); return Providers.news(s.length() > 80 ? s.substring(0, 80) : s, "", 5);
                }
                default: return Json.obj("error", "Unknown tool");
            }
        } catch (RuntimeException e) { return Json.obj("error", e.getMessage()); }
    }

    static Map<String, Object> chat(List<Map<String, Object>> input) {
        String key = cfg("ANTHROPIC_API_KEY", null);
        if (key == null) throw new UpstreamException("The AI is not configured", 503);
        List<Object> msgs = new ArrayList<>(input);
        Set<String> used = new LinkedHashSet<>();
        String text = "";
        for (int turn = 0; turn < 5; turn++) {
            String body = Json.write(Json.obj("model", cfg("ANTHROPIC_MODEL", "claude-sonnet-4-6"), "max_tokens", 900, "system", SYSTEM, "tools", TOOLS, "messages", msgs));
            HttpResponse<String> r;
            try {
                r = Providers.HTTP.send(HttpRequest.newBuilder(URI.create(cfg("ANTHROPIC_BASE_URL", "https://api.anthropic.com") + "/v1/messages")).timeout(Duration.ofSeconds(45))
                        .header("x-api-key", key).header("anthropic-version", "2023-06-01").header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) { throw new UpstreamException("AI provider error"); }
            if (r.statusCode() == 429) throw new UpstreamException("The AI is busy. Please try again shortly.", 429);
            if (r.statusCode() != 200) throw new UpstreamException("AI provider error");
            Map<String, Object> j;
            try { j = map(Json.parse(r.body())); } catch (IllegalArgumentException e) { throw new UpstreamException("AI provider error"); }
            List<Object> content = list(j.get("content"));
            StringBuilder t = new StringBuilder();
            for (Object b : content) if ("text".equals(map(b).get("type"))) { if (t.length() > 0) t.append('\n'); t.append(str(map(b).get("text"))); }
            text = t.toString();
            if (!"tool_use".equals(j.get("stop_reason"))) break;
            msgs.add(Json.obj("role", "assistant", "content", content));
            List<Object> results = new ArrayList<>();
            for (Object b : content) {
                Map<String, Object> blk = map(b);
                if (!"tool_use".equals(blk.get("type"))) continue;
                String out = Json.write(runTool(str(blk.get("name")), map(blk.get("input")), used));
                results.add(Json.obj("type", "tool_result", "tool_use_id", str(blk.get("id")), "content", out.length() > 6000 ? out.substring(0, 6000) : out));
            }
            msgs.add(Json.obj("role", "user", "content", results));
        }
        return Json.obj("text", text.isEmpty() ? "Sorry, I could not produce an answer. Please try again." : text, "used", new ArrayList<>(used));
    }
}
