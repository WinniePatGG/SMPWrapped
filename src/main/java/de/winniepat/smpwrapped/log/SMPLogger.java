package de.winniepat.smpwrapped.log;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SMPLogger {

    private final String module;

    SMPLogger(String module) {
        this.module = module;
    }

    public static SMPLogger get(String module) {
        return Logging.getLogger(module);
    }

    public String module() {
        return module;
    }

    public boolean isEnabled(LogLevel level) {
        return Logging.sink().isEnabled(level);
    }

    public void trace(String message, Object... context) {
        log(LogLevel.TRACE, message, null, context);
    }

    public void debug(String message, Object... context) {
        log(LogLevel.DEBUG, message, null, context);
    }

    public void info(String message, Object... context) {
        log(LogLevel.INFO, message, null, context);
    }

    public void warn(String message, Object... context) {
        log(LogLevel.WARN, message, null, context);
    }

    public void error(String message, Throwable throwable, Object... context) {
        log(LogLevel.ERROR, message, throwable, context);
    }

    public void fatal(String message, Throwable throwable, Object... context) {
        log(LogLevel.FATAL, message, throwable, context);
    }

    public void error(String message, Object... context) {
        log(LogLevel.ERROR, message, null, context);
    }

    public void fatal(String message, Object... context) {
        log(LogLevel.FATAL, message, null, context);
    }

    private void log(LogLevel level, String message, Throwable throwable, Object[] context) {
        LogSink sink = Logging.sink();
        if (!sink.isEnabled(level)) {
            return;
        }
        sink.log(level, module, Thread.currentThread().getName(), message, throwable, context);
    }

    static Map<String, String> renderContext(Object[] context, boolean redact) {
        Map<String, String> rendered = new LinkedHashMap<>();
        if (context == null || context.length == 0) {
            return rendered;
        }
        for (int i = 0; i < context.length; i += 2) {
            if (context[i] == null) {
                continue;
            }
            String key = String.valueOf(context[i]);
            Object value = (i + 1 < context.length) ? context[i + 1] : null;
            try {
                rendered.put(key, LogSanitizer.value(key, value, redact));
            } catch (Throwable t) {
                rendered.put(key, "<unrenderable>");
            }
        }
        return rendered;
    }
}