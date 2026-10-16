package de.winniepat.smpwrapped.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RateLimiter {
    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = Math.max(1, maxRequestsPerMinute);
    }

    public boolean allowRequest(String key) {
        if (key == null) {
            return true;
        }
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(maxRequestsPerMinute));
        return bucket.tryConsume();
    }

    public int trackedClients() {
        return buckets.size();
    }

    public void cleanup() {
        buckets.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static class Bucket {
        private final int limit;
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        Bucket(int limit) {
            this.limit = limit;
        }

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }

        boolean isExpired() {
            return System.currentTimeMillis() - windowStart > 120_000;
        }
    }
}