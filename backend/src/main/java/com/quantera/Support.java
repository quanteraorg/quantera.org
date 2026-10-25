package com.quantera;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Small shared helpers: config, errors, cache, rate limiter, safe type conversion. */
final class Support {
    private Support() {}

    static final Pattern SYM = Pattern.compile("^[A-Za-z0-9.\\-]{1,12}$");

    /** System property wins over environment variable (lets tests inject values). */
    static String cfg(String key, String def) {
        String v = System.getProperty(key);
        if (v == null) v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    static class UpstreamException extends RuntimeException {
        final int code;
        UpstreamException(String msg, int code) { super(msg); this.code = code; }
        UpstreamException(String msg) { this(msg, 502); }
    }

    /** Time-based cache with a size cap. When a refresh fails because a provider is rate limited or down, the last good value (up to a day old) is served instead. */
    static final class Cache {
        private record E(long t, Object v) {}
        static final long STALE_MS = 24 * 3600_000L;
        private final Map<String, E> m = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override protected boolean removeEldestEntry(Map.Entry<String, E> e) { return size() > 400; }
        });
        @SuppressWarnings("unchecked")
        <T> T get(String key, long ttlMs, Supplier<T> fn) {
            E e = m.get(key);
            long now = System.currentTimeMillis();
            if (e != null && now - e.t < ttlMs) return (T) e.v;
            T v;
            try { v = fn.get(); }
            catch (UpstreamException x) {
                if (e != null && now - e.t < STALE_MS && (x.code == 429 || x.code >= 500)) return (T) e.v;
                throw x;
            }
            m.put(key, new E(now, v));
            return v;
        }
        void put(String key, Object v) { m.put(key, new E(System.currentTimeMillis(), v)); }
        void clear() { m.clear(); }
    }

    /** Best-effort per-instance limiter. Use a shared store (Redis) if you run several instances. */
    static final class RateLimiter {
        private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();
        boolean limited(String ip, String route, int max) {
            if (hits.size() > 5000) hits.clear();
            long now = System.currentTimeMillis();
            Deque<Long> d = hits.computeIfAbsent(ip + "|" + route, k -> new ArrayDeque<>());
            synchronized (d) {
                while (!d.isEmpty() && now - d.peekFirst() > 60_000) d.pollFirst();
                d.addLast(now);
                return d.size() > max;
            }
        }
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object o) { return o instanceof Map ? (Map<String, Object>) o : new LinkedHashMap<>(); }

    @SuppressWarnings("unchecked")
    static List<Object> list(Object o) { return o instanceof List ? (List<Object>) o : new ArrayList<>(); }

    static String str(Object o) { return o == null ? null : String.valueOf(o); }

    /** Parses numbers the way vendors send them: "None", "-" and blanks become null. */
    static Double num(Object o) {
        if (o == null) return null;
        String s = String.valueOf(o).trim().replace("%", "");
        if (s.isEmpty() || s.equals("None") || s.equals("-")) return null;
        try { double d = Double.parseDouble(s); return Double.isNaN(d) ? null : d; } catch (NumberFormatException e) { return null; }
    }
}
