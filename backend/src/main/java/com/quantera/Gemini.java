package com.quantera;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

import static com.quantera.Support.*;

/** Google Gemini (free tier via an AI Studio key): the same chat tools and story explanations as Anthropic. The key never leaves the server. */
final class Gemini {
    private Gemini() {}

    static boolean on() { return cfg("GEMINI_API_KEY", null) != null; }

    private static Map<String, Object> call(Map<String, Object> body) {
        String model = cfg("GEMINI_MODEL", "gemini-flash-latest");
        HttpResponse<String> r;
        try {
            r = Providers.HTTP.send(HttpRequest.newBuilder(URI.create(cfg("GEMINI_BASE_URL", "https://generativelanguage.googleapis.com") + "/v1beta/models/" + model + ":generateContent"))
                    .timeout(Duration.ofSeconds(45)).header("x-goog-api-key", cfg("GEMINI_API_KEY", "")).header("content-type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(Json.write(body))).build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) { throw new UpstreamException("AI provider error"); }
        if (r.statusCode() != 200) {
            String status = "", msg = "";
            try { Map<String, Object> e = map(map(Json.parse(r.body())).get("error")); status = String.valueOf(e.getOrDefault("status", "")); msg = String.valueOf(e.getOrDefault("message", "")); } catch (RuntimeException ignored) {}
            System.out.println("Gemini API error " + r.statusCode() + " " + status + ": " + (msg.length() > 200 ? msg.substring(0, 200) : msg));
            if (r.statusCode() == 429 || status.equals("RESOURCE_EXHAUSTED")) throw new UpstreamException("The AI is busy right now (free daily limit). Please try again later.", 429);
            if (msg.contains("API key") || status.equals("UNAUTHENTICATED")) throw new UpstreamException("The AI service key is invalid. The site owner needs to update GEMINI_API_KEY.", 503);
            if (r.statusCode() == 404) throw new UpstreamException("The AI model setting is not available. The site owner needs to update GEMINI_MODEL.", 503);
            if (r.statusCode() == 403) throw new UpstreamException("The AI service key does not have access to this model.", 503);
            throw new UpstreamException("AI provider error");
        }
        try { return map(Json.parse(r.body())); } catch (IllegalArgumentException e) { throw new UpstreamException("AI provider error"); }
    }

    private static List<Object> parts(Map<String, Object> resp) {
        return list(map(map(list(resp.get("candidates")).isEmpty() ? null : list(resp.get("candidates")).get(0)).get("content")).get("parts"));
    }

    private static String text(List<Object> parts) {
        StringBuilder t = new StringBuilder();
        for (Object p : parts) { String s = str(map(p).get("text")); if (s != null && !Boolean.TRUE.equals(map(p).get("thought"))) t.append(s); }
        return t.toString().trim();
    }

    /** One-shot completion (story explanations). */
    static String complete(String system, String prompt, int maxTokens) {
        Map<String, Object> resp = call(Json.obj("systemInstruction", Json.obj("parts", List.of(Json.obj("text", system))),
                "contents", List.of(Json.obj("role", "user", "parts", List.of(Json.obj("text", prompt)))),
                "generationConfig", Json.obj("maxOutputTokens", maxTokens)));
        String t = text(parts(resp));
        if (t.isEmpty()) throw new UpstreamException("AI provider error");
        return t;
    }

    /** Gemini's schema dialect uses upper-case type names. */
    @SuppressWarnings("unchecked")
    static Object schema(Object o) {
        if (o instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : ((Map<String, Object>) m).entrySet()) out.put(e.getKey(), e.getKey().equals("type") ? String.valueOf(e.getValue()).toUpperCase() : schema(e.getValue()));
            return out;
        }
        if (o instanceof List<?> l) { List<Object> out = new ArrayList<>(); for (Object x : l) out.add(schema(x)); return out; }
        return o;
    }

    /** The AI chat with the same server-side tools (quotes, price stats, company, search, news) as the Anthropic version. */
    static Map<String, Object> chat(List<Map<String, Object>> input) {
        List<Object> decls = new ArrayList<>();
        for (Object t : Anthropic.TOOLS) { Map<String, Object> m = map(t); decls.add(Json.obj("name", m.get("name"), "description", m.get("description"), "parameters", schema(m.get("input_schema")))); }
        List<Object> contents = new ArrayList<>();
        for (Map<String, Object> m : input) contents.add(Json.obj("role", "assistant".equals(m.get("role")) ? "model" : "user", "parts", List.of(Json.obj("text", m.get("content")))));
        Set<String> used = new LinkedHashSet<>();
        String text = "";
        for (int turn = 0; turn < 5; turn++) {
            List<Object> ps = parts(call(Json.obj("systemInstruction", Json.obj("parts", List.of(Json.obj("text", Anthropic.SYSTEM))), "contents", contents,
                    "tools", List.of(Json.obj("functionDeclarations", decls)), "generationConfig", Json.obj("maxOutputTokens", 900))));
            text = text(ps);
            List<Object> calls = new ArrayList<>();
            for (Object p : ps) if (map(p).get("functionCall") != null) calls.add(map(map(p).get("functionCall")));
            if (calls.isEmpty()) break;
            contents.add(Json.obj("role", "model", "parts", ps));
            List<Object> results = new ArrayList<>();
            for (Object c : calls) {
                Map<String, Object> fc = map(c);
                Object out = Anthropic.runTool(str(fc.get("name")), map(fc.get("args")), used);
                String js = Json.write(out);
                results.add(Json.obj("functionResponse", Json.obj("name", fc.get("name"), "response", Json.obj("result", js.length() > 6000 ? js.substring(0, 6000) : js))));
            }
            contents.add(Json.obj("role", "user", "parts", results));
        }
        return Json.obj("text", text.isEmpty() ? "Sorry, I could not produce an answer. Please try again." : text, "used", new ArrayList<>(used));
    }
}
