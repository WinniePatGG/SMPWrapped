package de.winniepat.smpwrapped.web;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class RateLimiter {
    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public boolean allowRequest(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        return bucket.tryConsume();
    }

    public void cleanup() {
        buckets.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static class Bucket {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();

        synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            if (now - windowStart > 60_000) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= 60; 
        }

        boolean isExpired() {
            return System.currentTimeMillis() - windowStart > 120_000;
        }
    }
}
