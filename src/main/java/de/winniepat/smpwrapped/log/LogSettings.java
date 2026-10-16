package de.winniepat.smpwrapped.log;

public final class LogSettings {

    private final LogLevel level;
    private final boolean consoleEnabled;
    private final boolean fileEnabled;
    private final long maxFileSizeBytes;
    private final int keepFiles;
    private final int queueCapacity;
    private final boolean stackTraces;
    private final int stackTraceDepth;
    private final boolean dedupeEnabled;
    private final int dedupeThreshold;
    private final long dedupeWindowSeconds;
    private final long dedupeThrottleSeconds;
    private final boolean redactSecrets;
    private final boolean summaryOnShutdown;

    private LogSettings(Builder builder) {
        this.level = builder.level;
        this.consoleEnabled = builder.consoleEnabled;
        this.fileEnabled = builder.fileEnabled;
        this.maxFileSizeBytes = builder.maxFileSizeBytes;
        this.keepFiles = builder.keepFiles;
        this.queueCapacity = builder.queueCapacity;
        this.stackTraces = builder.stackTraces;
        this.stackTraceDepth = builder.stackTraceDepth;
        this.dedupeEnabled = builder.dedupeEnabled;
        this.dedupeThreshold = builder.dedupeThreshold;
        this.dedupeWindowSeconds = builder.dedupeWindowSeconds;
        this.dedupeThrottleSeconds = builder.dedupeThrottleSeconds;
        this.redactSecrets = builder.redactSecrets;
        this.summaryOnShutdown = builder.summaryOnShutdown;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static LogSettings defaults() {
        return builder().build();
    }

    public LogLevel level() {
        return level;
    }

    public boolean consoleEnabled() {
        return consoleEnabled;
    }

    public boolean fileEnabled() {
        return fileEnabled;
    }

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public int keepFiles() {
        return keepFiles;
    }

    public int queueCapacity() {
        return queueCapacity;
    }

    public boolean stackTraces() {
        return stackTraces;
    }

    public int stackTraceDepth() {
        return stackTraceDepth;
    }

    public boolean dedupeEnabled() {
        return dedupeEnabled;
    }

    public int dedupeThreshold() {
        return dedupeThreshold;
    }

    public long dedupeWindowMillis() {
        return dedupeWindowSeconds * 1000L;
    }

    public long dedupeThrottleMillis() {
        return dedupeThrottleSeconds * 1000L;
    }

    public boolean redactSecrets() {
        return redactSecrets;
    }

    public boolean summaryOnShutdown() {
        return summaryOnShutdown;
    }

    public static final class Builder {

        private LogLevel level = LogLevel.INFO;
        private boolean consoleEnabled = true;
        private boolean fileEnabled = true;
        private long maxFileSizeBytes = 5L * 1024L * 1024L;
        private int keepFiles = 5;
        private int queueCapacity = 8192;
        private boolean stackTraces = true;
        private int stackTraceDepth = 30;
        private boolean dedupeEnabled = true;
        private int dedupeThreshold = 5;
        private long dedupeWindowSeconds = 300L;
        private long dedupeThrottleSeconds = 300L;
        private boolean redactSecrets = true;
        private boolean summaryOnShutdown = true;

        public Builder level(LogLevel level) {
            this.level = level == null ? LogLevel.INFO : level;
            return this;
        }

        public Builder consoleEnabled(boolean value) {
            this.consoleEnabled = value;
            return this;
        }

        public Builder fileEnabled(boolean value) {
            this.fileEnabled = value;
            return this;
        }

        public Builder maxFileSizeMb(long megabytes) {
            long clamped = Math.max(0, megabytes);
            this.maxFileSizeBytes = clamped == 0 ? 0L : clamped * 1024L * 1024L;
            return this;
        }

        public Builder keepFiles(int keepFiles) {
            this.keepFiles = Math.max(0, keepFiles);
            return this;
        }

        public Builder queueCapacity(int capacity) {
            this.queueCapacity = Math.max(256, capacity);
            return this;
        }

        public Builder stackTraces(boolean value) {
            this.stackTraces = value;
            return this;
        }

        public Builder stackTraceDepth(int depth) {
            this.stackTraceDepth = Math.max(0, depth);
            return this;
        }

        public Builder dedupeEnabled(boolean value) {
            this.dedupeEnabled = value;
            return this;
        }

        public Builder dedupeThreshold(int threshold) {
            this.dedupeThreshold = Math.max(0, threshold);
            return this;
        }

        public Builder dedupeWindowSeconds(long seconds) {
            this.dedupeWindowSeconds = Math.max(1L, seconds);
            return this;
        }

        public Builder dedupeThrottleSeconds(long seconds) {
            this.dedupeThrottleSeconds = Math.max(0L, seconds);
            return this;
        }

        public Builder redactSecrets(boolean value) {
            this.redactSecrets = value;
            return this;
        }

        public Builder summaryOnShutdown(boolean value) {
            this.summaryOnShutdown = value;
            return this;
        }

        public LogSettings build() {
            return new LogSettings(this);
        }
    }
}