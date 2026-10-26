package com.quantera;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;

import static com.quantera.Support.*;

/** Waitlist and usage events, stored in Supabase over its REST API (no database driver needed). The service key never leaves the server. */
final class Store {
    private Store() {}

    static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$"), EVENT = Pattern.compile("^[a-z0-9_]{1,40}$"), TAG = Pattern.compile("^[\\w \\-]{0,40}$");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    static boolean on() { return !cfg("SUPABASE_URL", "").isEmpty() && !cfg("SUPABASE_SERVICE_KEY", "").isEmpty(); }

    /** Inserts rows into a table; duplicate rows (by unique constraint) are skipped. Returns the rows actually inserted. */
    private static List<Object> insert(String table, Map<String, Object> row) {
        if (!on()) throw new UpstreamException("Storage is not configured", 503);
        String key = cfg("SUPABASE_SERVICE_KEY", "").trim();
        // A key pasted from a masked display (dots, line breaks) is rejected here, without ever echoing it into logs
        if (!key.matches("[A-Za-z0-9._\\-]+")) { System.out.println("SUPABASE_SERVICE_KEY is malformed: copy the full key after revealing it"); throw new UpstreamException("Storage is not configured correctly", 503); }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg("SUPABASE_URL", "").replaceAll("/+$", "") + "/rest/v1/" + table))
                .timeout(Duration.ofSeconds(8))
                .header("apikey", key).header("Content-Type", "application/json")
                .header("Prefer", "return=representation,resolution=ignore-duplicates");
        if (key.startsWith("eyJ")) b.header("Authorization", "Bearer " + key); // legacy JWT service_role key; new sb_secret_ keys go in apikey only
        HttpRequest req = b.POST(HttpRequest.BodyPublishers.ofString(Json.write(List.of(row)))).build();
        try {
            HttpResponse<String> r = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new UpstreamException("Storage error");
            return list(Json.parse(r.body()));
        } catch (UpstreamException e) { throw e;
        } catch (Exception e) { throw new UpstreamException("Storage error"); }
    }

    /** Any Supabase REST call with the service key (server-side only): GET / POST / PATCH / DELETE on "table?filters". */
    static Object rest(String method, String pathAndQuery, Object body) {
        if (!on()) throw new UpstreamException("Storage is not configured", 503);
        String key = cfg("SUPABASE_SERVICE_KEY", "").trim();
        if (!key.matches("[A-Za-z0-9._\\-]+")) throw new UpstreamException("Storage is not configured correctly", 503);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(cfg("SUPABASE_URL", "").replaceAll("/+$", "") + "/rest/v1/" + pathAndQuery))
                .timeout(Duration.ofSeconds(8)).header("apikey", key).header("Content-Type", "application/json").header("Prefer", "return=representation");
        if (key.startsWith("eyJ")) b.header("Authorization", "Bearer " + key);
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(Json.write(body)));
        try {
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() / 100 != 2) throw new UpstreamException("Storage error " + r.statusCode());
            return r.body() == null || r.body().isBlank() ? List.of() : Json.parse(r.body());
        } catch (UpstreamException e) { throw e;
        } catch (Exception e) { throw new UpstreamException("Storage error"); }
    }

    static Map<String, Object> joinWaitlist(Object body) {
        Map<String, Object> b = map(body);
        String email = String.valueOf(b.getOrDefault("email", "")).trim().toLowerCase(), interest = String.valueOf(b.getOrDefault("interest", "")).trim(), source = String.valueOf(b.getOrDefault("source", "")).trim();
        if (!EMAIL.matcher(email).matches()) throw new UpstreamException("Enter a valid email address.", 400);
        if (!TAG.matcher(interest).matches() || !TAG.matcher(source).matches()) throw new UpstreamException("Invalid request", 400);
        if (!on()) throw new UpstreamException("The waitlist is not connected yet. Please try again soon.", 503);
        List<Object> added = insert("waitlist", Json.obj("email", email, "interest", interest, "source", source));
        return Json.obj("ok", true, "alreadyJoined", added.isEmpty());
    }

    /** Usage events are best effort: without storage, or if storage fails, they are dropped quietly. */
    static Map<String, Object> event(Object body) {
        String name = String.valueOf(map(body).getOrDefault("name", ""));
        if (!EVENT.matcher(name).matches()) throw new UpstreamException("Invalid event", 400);
        if (on()) Thread.startVirtualThread(() -> { try { insert("events", Json.obj("name", name)); } catch (RuntimeException ignored) {} });
        return Json.obj("ok", true);
    }
}
