package de.winniepat.smpwrapped.log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

final class LogFormatter {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final int MAX_MESSAGE_LENGTH = 8000;
    private static final String INDENT = "    ";
    private static final String NEWLINE = System.lineSeparator();

    private LogFormatter() {
    }

    static String header(String pluginVersion) {
        StringBuilder sb = new StringBuilder(512);
        sb.append(NEWLINE).append(NEWLINE);
        sb.append("=== SMPWrapped ").append(pluginVersion).append(" starting ===").append(NEWLINE);
        sb.append("java=").append(System.getProperty("java.version"))
            .append(" vendor=").append(System.getProperty("java.vendor"))
            .append(" os=").append(System.getProperty("os.name"))
            .append(' ').append(System.getProperty("os.version"))
            .append(" arch=").append(System.getProperty("os.arch")).append(NEWLINE);
        sb.append("processors=").append(Runtime.getRuntime().availableProcessors())
            .append(" maxMemory=").append(Runtime.getRuntime().maxMemory() / (1024L * 1024L)).append("MB")
            .append(" timeZone=").append(java.util.TimeZone.getDefault().getID()).append(NEWLINE);
        sb.append("==============================================").append(NEWLINE);
        return sb.toString();
    }

    static String format(LogLevel level, String module, String thread, String message,
                         Map<String, String> context, Throwable throwable, int stackTraceDepth) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(TIMESTAMP.format(LocalDateTime.now()));
        sb.append(' ');
        sb.append(level.name());
        for (int i = level.name().length(); i < 5; i++) {
            sb.append(' ');
        }
        sb.append(" [").append(module).append(']');
        sb.append(" [").append(thread).append(']');
        sb.append(' ');
        sb.append(LogSanitizer.truncate(message == null ? "null" : message, MAX_MESSAGE_LENGTH));
        if (context != null && !context.isEmpty()) {
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
        if (throwable != null) {
            sb.append(NEWLINE).append(stackTrace(throwable, stackTraceDepth));
        }
        return sb.toString();
    }

    static String stackTrace(Throwable throwable, int maxFrames) {
        StringBuilder sb = new StringBuilder(1024);
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Throwable current = throwable;
        int budget = Math.max(1, maxFrames);
        boolean first = true;
        while (current != null && seen.add(current)) {
            if (!first) {
                sb.append(NEWLINE).append("Caused by: ");
            }
            sb.append(current.getClass().getName());
            if (current.getMessage() != null) {
                sb.append(": ").append(LogSanitizer.truncate(current.getMessage(), 500));
            }
            StackTraceElement[] frames = current.getStackTrace();
            int printed = 0;
            for (StackTraceElement frame : frames) {
                if (printed >= budget) {
                    break;
                }
                sb.append(NEWLINE).append(INDENT).append("at ").append(frame);
                printed++;
            }
            budget -= printed;
            Throwable cause = current.getCause();
            current = (cause == current) ? null : cause;
            if (current != null) {
                if (budget <= 0) {
                    sb.append(NEWLINE).append(INDENT).append("... stack trace truncated");
                    break;
                }
            }
            first = false;
        }
        return sb.toString();
    }
}