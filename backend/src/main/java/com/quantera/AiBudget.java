package com.quantera;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static com.quantera.Support.*;

/**
 * A server-wide daily ceiling on AI requests (Ask AI, story explanations, deep-research reports), so that no amount of
 * traffic or abuse can run up an unbounded AI bill. Counts each visitor request that reaches the AI provider (cached
 * answers don't count). Resets at midnight UTC. AI_DAILY_LIMIT sets the ceiling (default 400; 0 turns AI off).
 * AI_GLOBAL_PER_MIN (default 30) also caps those requests per rolling minute, whoever sends them, so a burst can't
 * spend the whole day's allowance at once. Neither depends on the visitor's address.
 * Per-instance: with several server instances, each has its own count.
 */
final class AiBudget {
    private AiBudget() {}

    private static volatile LocalDate day = LocalDate.now(ZoneOffset.UTC);
    private static final AtomicInteger used = new AtomicInteger();
    private static final java.util.ArrayDeque<Long> recent = new java.util.ArrayDeque<>();

    static int perMinute() {
        try { return Math.max(0, Integer.parseInt(cfg("AI_GLOBAL_PER_MIN", "30").trim())); } catch (NumberFormatException e) { return 30; }
    }

    static int limit() {
        try { return Math.max(0, Integer.parseInt(cfg("AI_DAILY_LIMIT", "400").trim())); } catch (NumberFormatException e) { return 400; }
    }

    /** Takes one request from today's allowance, or refuses with a clear message once it's used up. */
    static void take() {
        long now = System.currentTimeMillis();
        synchronized (recent) {
            while (!recent.isEmpty() && now - recent.peekFirst() >= 60_000) recent.pollFirst();
            if (recent.size() >= perMinute()) throw new UpstreamException("Quantera’s AI is busy right now. Please try again in a minute.", 429);
            recent.addLast(now);
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(day)) synchronized (AiBudget.class) { if (!today.equals(day)) { day = today; used.set(0); } }
        if (used.incrementAndGet() > limit()) {
            used.decrementAndGet();
            synchronized (recent) { recent.pollLast(); }
            throw new UpstreamException("Quantera’s AI has reached its daily limit. Please try again tomorrow; everything else on the site still works.", 429);
        }
    }

    static int usedToday() { return used.get(); }
    static void reset() { used.set(0); synchronized (recent) { recent.clear(); } }
}
