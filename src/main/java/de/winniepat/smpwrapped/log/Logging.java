package de.winniepat.smpwrapped.log;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class Logging {

    private static final String ROOT_NAME = "SMPWrapped";

    private static final Map<String, SMPLogger> LOGGERS = new ConcurrentHashMap<>();
    private static final FallbackSink FALLBACK = new FallbackSink();

    private static volatile LogSink sink = FALLBACK;
    private static volatile LogWriter writer;
    private static volatile String logFilePath = "<disabled>";
    private static volatile Path writerTarget;
    private static volatile long writerMaxSize = -1L;
    private static volatile int writerKeepFiles = -1;
    private static volatile int writerCapacity = -1;
    private static volatile DefaultSink activeSink;
    private static volatile boolean initialized;

    private Logging() {
    }

    public static synchronized void init(String pluginVersion, File dataFolder, LogSettings settings, Logger console) {
        LogSettings effective = settings == null ? LogSettings.defaults() : settings;
        Path target = null;
        if (effective.fileEnabled() && dataFolder != null) {
            target = new File(new File(dataFolder, "logs"), "smpwrapped.log").toPath();
        }

        boolean writerReusable = writer != null
            && writer.isRunning()
            && target != null
            && target.equals(writerTarget)
            && writerMaxSize == effective.maxFileSizeBytes()
            && writerKeepFiles == effective.keepFiles()
            && writerCapacity == effective.queueCapacity();

        if (!writerReusable) {
            closeWriter();
            if (target != null) {
                LogWriter created = new LogWriter(
                    target,
                    effective.queueCapacity(),
                    effective.maxFileSizeBytes(),
                    effective.keepFiles(),
                    FALLBACK::reportWriterFailure);
                created.start();
                created.write(LogFormatter.header(pluginVersion == null ? "unknown" : pluginVersion));
                writer = created;
                logFilePath = target.toAbsolutePath().toString();
                writerTarget = target;
                writerMaxSize = effective.maxFileSizeBytes();
                writerKeepFiles = effective.keepFiles();
                writerCapacity = effective.queueCapacity();
            }
        }

        DefaultSink created = new DefaultSink(effective, console, writer);
        activeSink = created;
        sink = created;
        initialized = true;

        created.log(LogLevel.INFO, "logging", Thread.currentThread().getName(),
            "Logging initialised", null, new Object[]{
                "level", effective.level().name(),
                "console", effective.consoleEnabled(),
                "file", effective.fileEnabled(),
                "logFile", logFilePath,
                "maxFileSizeBytes", effective.maxFileSizeBytes(),
                "keepFiles", effective.keepFiles(),
                "queueCapacity", effective.queueCapacity(),
                "stackTraces", effective.stackTraces(),
                "stackTraceDepth", effective.stackTraceDepth(),
                "dedupe", effective.dedupeEnabled(),
                "dedupeThreshold", effective.dedupeThreshold(),
                "dedupeWindowSeconds", effective.dedupeWindowMillis() / 1000L,
                "dedupeThrottleSeconds", effective.dedupeThrottleMillis() / 1000L,
                "redactEnabled", effective.redactSecrets(),
                "pluginVersion", pluginVersion
            });
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static SMPLogger getLogger(String module) {
        String key = (module == null || module.isBlank()) ? "core" : module;
        return LOGGERS.computeIfAbsent(key, SMPLogger::new);
    }

    static LogSink sink() {
        return sink;
    }

    public static String logFilePath() {
        return logFilePath;
    }

    public static synchronized void flush() {
        DefaultSink current = activeSink;
        if (current != null) {
            current.flush();
        }
    }

    public static synchronized void shutdown() {
        DefaultSink current = activeSink;
        if (current != null) {
            current.reportSummary();
            current.flush();
        }
        closeWriter();
        activeSink = null;
        sink = FALLBACK;
        initialized = false;
    }

    private static void closeWriter() {
        LogWriter current = writer;
        writer = null;
        writerTarget = null;
        writerMaxSize = -1L;
        writerKeepFiles = -1;
        writerCapacity = -1;
        logFilePath = "<disabled>";
        if (current != null) {
            current.close();
        }
    }

    static Level toJdkLevel(LogLevel level) {
        return switch (level) {
            case TRACE -> Level.FINER;
            case DEBUG -> Level.FINE;
            case INFO -> Level.INFO;
            case WARN -> Level.WARNING;
            case ERROR, FATAL -> Level.SEVERE;
        };
    }

    private static final class DefaultSink implements LogSink {

        private final LogSettings settings;
        private final Logger console;
        private final LogWriter writer;
        private final ErrorTracker tracker;

        DefaultSink(LogSettings settings, Logger console, LogWriter writer) {
            this.settings = settings;
            this.console = console;
            this.writer = writer;
            this.tracker = new ErrorTracker(
                settings.dedupeEnabled(),
                settings.dedupeThreshold(),
                settings.dedupeWindowMillis(),
                settings.dedupeThrottleMillis());
        }

        @Override
        public boolean isEnabled(LogLevel level) {
            return level != null && level.isAtLeast(settings.level());
        }

        @Override
        public void log(LogLevel level, String module, String thread, String message,
                        Throwable throwable, Object[] context) {
            ErrorTracker.Decision decision = tracker.decide(level, message, throwable);
            if (!decision.shouldLog()) {
                return;
            }

            Map<String, String> rendered = SMPLogger.renderContext(context, settings.redactSecrets());
            if (decision.suppressedRepeats() > 0) {
                rendered.put("suppressedRepeats", String.valueOf(decision.suppressedRepeats()));
            }

            if (settings.consoleEnabled() && console != null) {
                logToConsole(level, module, message, rendered, throwable);
            }
            if (writer != null) {
                String fileLine = LogFormatter.format(level, module, thread, message, rendered,
                    settings.stackTraces() ? throwable : null, settings.stackTraceDepth());
                writer.write(fileLine);
            }
        }

        private void logToConsole(LogLevel level, String module, String message,
                                  Map<String, String> context, Throwable throwable) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append(level.name()).append(" [").append(module).append("] ").append(message);
                appendContext(sb, context);
                LogRecord record = new LogRecord(toJdkLevel(level), sb.toString());
                record.setLoggerName(ROOT_NAME);
                record.setThreadID((int) Thread.currentThread().getId());
                if (settings.stackTraces() && throwable != null) {
                    record.setThrown(throwable);
                }
                console.log(record);
            } catch (Throwable ignored) {
            }
        }

        void flush() {
            if (console != null) {
                try {
                    for (java.util.logging.Handler handler : console.getHandlers()) {
                        handler.flush();
                    }
                } catch (Throwable ignored) {
                }
            }
            if (writer != null) {
                writer.flush();
            }
        }

        void reportSummary() {
            if (!settings.summaryOnShutdown()) {
                return;
            }
            Map<String, Long> counts = tracker.snapshot();
            long errorCount = counts.getOrDefault("ERROR", 0L) + counts.getOrDefault("FATAL", 0L);
            Object[] context = new Object[]{
                "levels", formatCounts(counts),
                "errorsAndAbove", errorCount,
                "suppressedRepeats", tracker.suppressedTotal(),
                "trackedSignatures", tracker.trackedSignatures(),
                "topErrors", tracker.topSignatures(3),
                "logLines", writer == null ? 0L : writer.writtenCount(),
                "logBytes", writer == null ? 0L : writer.bytesCount(),
                "logRotations", writer == null ? 0L : writer.rotationCount(),
                "droppedLogLines", writer == null ? 0L : writer.droppedCount()
            };
            log(errorCount > 0 ? LogLevel.WARN : LogLevel.INFO, "logging",
                Thread.currentThread().getName(),
                errorCount > 0
                    ? "Shutdown summary: " + errorCount + " error(s) recorded"
                    : "Shutdown summary: no errors recorded",
                null, context);
        }
    }

    private static String formatCounts(Map<String, Long> counts) {
        if (counts.isEmpty()) {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    static void appendContext(StringBuilder sb, Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            return;
        }
        sb.append(" {");
        boolean first = true;
        for (Map.Entry<String, String> entry : context.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
            first = false;
        }
        sb.append('}');
    }

    private static final class FallbackSink implements LogSink {

        @Override
        public boolean isEnabled(LogLevel level) {
            return level != null && level.isAtLeast(LogLevel.WARN);
        }

        @Override
        public void log(LogLevel level, String module, String thread, String message,
                        Throwable throwable, Object[] context) {
            Logger target = Logger.getLogger(ROOT_NAME);
            StringBuilder sb = new StringBuilder();
            sb.append(level.name()).append(" [").append(module).append("] ").append(message);
            appendContext(sb, SMPLogger.renderContext(context, true));
            LogRecord record = new LogRecord(toJdkLevel(level), sb.toString());
            record.setLoggerName(ROOT_NAME);
            record.setThrown(throwable);
            target.log(record);
        }

        void reportWriterFailure(String message) {
            Logger.getLogger(ROOT_NAME).log(Level.WARNING, "Logging failure: " + message);
        }
    }
}