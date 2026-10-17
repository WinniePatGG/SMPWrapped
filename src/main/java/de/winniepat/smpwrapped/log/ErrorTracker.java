package de.winniepat.smpwrapped.log;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

final class ErrorTracker {

    private static final Pattern HEX = Pattern.compile("[0-9a-fA-F]{8,}");
    private static final Pattern DIGITS = Pattern.compile("\\d+");
    private static final int MAX_KEY_LENGTH = 400;

    private static final class Bucket {
        private long firstSeen;
        private long lastLogged;
        private int count;
        private int suppressed;
    }

    static final class Decision {
        private final boolean shouldLog;
        private final int suppressedRepeats;

        private Decision(boolean shouldLog, int suppressedRepeats) {
            this.shouldLog = shouldLog;
            this.suppressedRepeats = suppressedRepeats;
        }

        boolean shouldLog() { return shouldLog; }
        int suppressedRepeats() { return suppressedRepeats; }

        static Decision log() {
            return new Decision(true, 0);
        }

        static Decision logWithSuppressed(int suppressed) {
            return new Decision(true, suppressed);
        }

        static Decision suppress() {
            return new Decision(false, 0);
        }
    }

    private final boolean enabled;
    private final int threshold;
    private final long windowMillis;
    private final long throttleMillis;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<LogLevel, AtomicLong> totals = new ConcurrentHashMap<>();
    private final AtomicLong suppressedTotal = new AtomicLong();

    ErrorTracker(boolean enabled, int threshold, long windowMillis, long throttleMillis) {
        this.enabled = enabled;
        this.threshold = Math.max(0, threshold);
        this.windowMillis = Math.max(1000L, windowMillis);
        this.throttleMillis = Math.max(0L, throttleMillis);
    }

    Decision decide(LogLevel level, String message, Throwable throwable) {
        totals.computeIfAbsent(level, key -> new AtomicLong()).incrementAndGet();

        if (!enabled || !level.isError() || throttleMillis == 0L) {
            return Decision.log();
        }

        String key = buildKey(level, message, throwable);
        Bucket bucket = buckets.computeIfAbsent(key, ignored -> {
            Bucket created = new Bucket();
            created.firstSeen = System.currentTimeMillis();
            created.lastLogged = 0L;
            return created;
        });

        long now = System.currentTimeMillis();
        synchronized (bucket) {
            if (now - bucket.firstSeen > windowMillis) {
                bucket.firstSeen = now;
                bucket.lastLogged = 0L;
                bucket.count = 0;
                bucket.suppressed = 0;
            }
            bucket.count++;
            if (bucket.count <= threshold) {
                bucket.lastLogged = now;
                return Decision.log();
            }
            if (now - bucket.lastLogged >= throttleMillis) {
                bucket.lastLogged = now;
                int suppressed = bucket.suppressed;
                bucket.suppressed = 0;
                return Decision.logWithSuppressed(suppressed);
            }
            bucket.suppressed++;
            suppressedTotal.incrementAndGet();
            return Decision.suppress();
        }
    }

    void purge() {
        long cutoff = System.currentTimeMillis() - windowMillis * 2;
        buckets.entrySet().removeIf(entry -> entry.getValue().firstSeen < cutoff);
    }

    int trackedSignatures() {
        return buckets.size();
    }

    long suppressedTotal() {
        return suppressedTotal.get();
    }

    Map<String, Long> snapshot() {
        Map<String, Long> result = new LinkedHashMap<>();
        for (LogLevel level : LogLevel.values()) {
            AtomicLong counter = totals.get(level);
            if (counter != null && counter.get() > 0) {
                result.put(level.name(), counter.get());
            }
        }
        return result;
    }

    String topSignatures(int limit) {
        if (limit <= 0 || buckets.isEmpty()) {
            return "none";
        }
        return buckets.entrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getValue().count, a.getValue().count))
            .limit(limit)
            .map(entry -> entry.getKey() + " (x" + entry.getValue().count + ")")
            .reduce((a, b) -> a + "; " + b)
            .orElse("none");
    }

    private static String buildKey(LogLevel level, String message, Throwable throwable) {
        StringBuilder sb = new StringBuilder(128);
        sb.append(level.name()).append("|").append(normalize(message));
        Throwable current = throwable;
        if (current != null) {
            sb.append('|').append(current.getClass().getName());
            StackTraceElement[] frames = current.getStackTrace();
            for (int i = 0; i < Math.min(3, frames.length); i++) {
                sb.append('@').append(frames[i].getClassName()).append('#').append(frames[i].getMethodName());
            }
        }
        return LogSanitizer.truncate(sb.toString(), MAX_KEY_LENGTH);
    }

    private static String normalize(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }
        String normalized = HEX.matcher(message).replaceAll("*");
        return DIGITS.matcher(normalized).replaceAll("#");
    }
}