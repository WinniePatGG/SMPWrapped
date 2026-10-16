package de.winniepat.smpwrapped.log;

import java.util.Locale;

public enum LogLevel {
    TRACE(10),
    DEBUG(20),
    INFO(30),
    WARN(40),
    ERROR(50),
    FATAL(60);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int severity() {
        return severity;
    }

    public boolean isAtLeast(LogLevel other) {
        return other == null || this.severity >= other.severity;
    }

    public boolean isError() {
        return this.severity >= ERROR.severity;
    }

    public static LogLevel parse(String value, LogLevel fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (LogLevel level : values()) {
            if (level.name().equals(normalized)) {
                return level;
            }
        }
        if (normalized.equals("WARN")) {
            return WARN;
        }
        if (normalized.equals("ERR")) {
            return ERROR;
        }
        if (normalized.equals("SEVERE")) {
            return ERROR;
        }
        if (normalized.equals("WARNING")) {
            return WARN;
        }
        return fallback;
    }
}