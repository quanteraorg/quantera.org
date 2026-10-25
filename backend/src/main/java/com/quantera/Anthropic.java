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

    /** Turns an Anthropic error response into a clear message (logged too); never includes the key. */
    static UpstreamException apiError(HttpResponse<String> r) {
        String type = "", msg = "";
        try { Map<String, Object> e = map(map(Json.parse(r.body())).get("error")); type = String.valueOf(e.getOrDefault("type", "")); msg = String.valueOf(e.getOrDefault("message", "")); } catch (RuntimeException ignored) {}
        System.out.println("Anthropic API error " + r.statusCode() + " " + type + ": " + (msg.length() > 200 ? msg.substring(0, 200) : msg));
        String low = msg.toLowerCase();
        if (r.statusCode() == 429 || r.statusCode() == 529) return new UpstreamException("The AI is busy. Please try again shortly.", 429);
        if (r.statusCode() == 401 || type.equals("authentication_error")) return new UpstreamException("The AI service key is invalid. The site owner needs to update ANTHROPIC_API_KEY.", 503);
        if (low.contains("credit balance") || low.contains("billing")) return new UpstreamException("The AI service is out of credits. The site owner needs to add credits in the Anthropic Console.", 503);
        if (r.statusCode() == 403 || type.equals("permission_error")) return new UpstreamException("The AI service key does not have access to this model.", 503);
        if (r.statusCode() == 404 || type.equals("not_found_error")) return new UpstreamException("The AI model setting is not available. The site owner needs to update ANTHROPIC_MODEL.", 503);
        return new UpstreamException("AI provider error");
    }

    /** Ways to explain a news story (like Particle's lenses). Each answer must stay inside the story's own text. */
    static final Map<String, String> LENSES = Map.of(
            "tldr", "Give a TL;DR in exactly 3 short bullets: what happened, why it happened, what could happen next.",
            "eli5", "Explain it like the reader is 12: plain words, one everyday analogy, at most 110 words.",
            "matters", "Explain why it matters for investors: which listed companies or sectors could be affected, in which direction and why, and what to watch next. 3 to 5 bullets.",
            "sides", "Give two sides: **Bull case** (2 to 3 bullets) and **Bear case** (2 to 3 bullets), then one line on what would settle the debate.",
            "five_w", "Answer the 5 W's, one line each: **Who**, **What**, **When**, **Where**, **Why**. Add **How** only if the story says.",
            "numbers", "List the key numbers in the story and what each one means for a beginner. If the story has no numbers, say so in one line.",
            "questions", "Give 3 questions a smart investor would ask next, each with a one-line hint about where to find the answer.");
    static final String EXPLAIN_SYSTEM = "You explain financial news for Quantera, an education app for beginner investors. Use only the headline, summary, companies and price moves you are given: "
            + "never invent facts, figures, quotes or events, and if the story does not say something, say it is not in the story. Plain English, short sentences. "
            + "Format with '- ' bullets and **bold** labels only. Keep it under 170 words. This is education, not investment advice.";

    static Map<String, Object> explain(Object body) {
        String key = cfg("ANTHROPIC_API_KEY", null);
        if (key == null) throw new UpstreamException("The AI is not configured", 503);
        Map<String, Object> b = map(body);
        String lens = String.valueOf(b.getOrDefault("lens", "")), title = String.valueOf(b.getOrDefault("title", "")).trim(), summary = String.valueOf(b.getOrDefault("summary", "")).trim();
        if (!LENSES.containsKey(lens) || title.isEmpty() || title.length() > 300 || summary.length() > 2000) throw new UpstreamException("Invalid request", 400);
        List<String> syms = new ArrayList<>();
        for (Object o : list(b.get("symbols"))) { String s = String.valueOf(o).toUpperCase(); if (SYM.matcher(s).matches() && syms.size() < 8) syms.add(s); }
        return Providers.CACHE.get("x" + lens + "|" + title.hashCode() + "|" + summary.hashCode() + "|" + syms, 6 * 3600_000L, () -> {
            StringBuilder moves = new StringBuilder();
            for (String s : syms) {
                try { Map<String, Object> q = Providers.quote(s, 5 * 60_000L); Double c = num(q.get("changePct")); if (c != null) moves.append(s).append(' ').append(c >= 0 ? "+" : "").append(String.format("%.2f", c)).append("% today; "); }
                catch (RuntimeException e) { moves.append(s).append("; "); }
            }
            String prompt = "Headline: " + title + "\nSummary: " + (summary.isEmpty() ? "(none)" : summary) + "\nCompanies tagged: " + (syms.isEmpty() ? "none" : String.join(", ", syms))
                    + "\nToday's price moves: " + (moves.length() == 0 ? "not available" : moves) + "\n\nTask: " + LENSES.get(lens);
            String req = Json.write(Json.obj("model", cfg("ANTHROPIC_MODEL", "claude-sonnet-4-6"), "max_tokens", 450, "system", EXPLAIN_SYSTEM, "messages", List.of(Json.obj("role", "user", "content", prompt))));
            HttpResponse<String> r;
            try {
                r = Providers.HTTP.send(HttpRequest.newBuilder(URI.create(cfg("ANTHROPIC_BASE_URL", "https://api.anthropic.com") + "/v1/messages")).timeout(Duration.ofSeconds(40))
                        .header("x-api-key", key).header("anthropic-version", "2023-06-01").header("content-type", "application/json").POST(HttpRequest.BodyPublishers.ofString(req)).build(), HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) { throw new UpstreamException("AI provider error"); }

            if (r.statusCode() != 200) throw apiError(r);
            StringBuilder t = new StringBuilder();
            try { for (Object o : list(map(Json.parse(r.body())).get("content"))) if ("text".equals(map(o).get("type"))) t.append(str(map(o).get("text"))); }
            catch (IllegalArgumentException e) { throw new UpstreamException("AI provider error"); }
            if (t.length() == 0) throw new UpstreamException("AI provider error");
            return Json.obj("lens", lens, "text", t.toString());
        });
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

            if (r.statusCode() != 200) throw apiError(r);
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
