package de.winniepat.smpwrapped.log;

interface LogSink {

    boolean isEnabled(LogLevel level);

    void log(LogLevel level, String module, String thread, String message, Throwable throwable, Object[] context);
}