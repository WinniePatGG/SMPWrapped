package de.winniepat.smpwrapped.log;

public final class LogContext {

    private LogContext() {
    }

    public static Object[] of(Object... keyValues) {
        return keyValues == null ? new Object[0] : keyValues;
    }
}