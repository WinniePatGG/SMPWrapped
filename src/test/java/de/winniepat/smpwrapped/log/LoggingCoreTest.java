package de.winniepat.smpwrapped.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingCoreTest {

    @Test
    @DisplayName("Log levels are ordered by severity")
    void levelsAreOrdered() {
        assertThat(LogLevel.TRACE.isAtLeast(LogLevel.DEBUG)).isFalse();
        assertThat(LogLevel.ERROR.isAtLeast(LogLevel.WARN)).isTrue();
        assertThat(LogLevel.FATAL.isAtLeast(LogLevel.FATAL)).isTrue();
        assertThat(LogLevel.WARN.isError()).isFalse();
        assertThat(LogLevel.ERROR.isError()).isTrue();
        assertThat(LogLevel.FATAL.isError()).isTrue();
    }

    @Test
    @DisplayName("Log levels parse case insensitively and fall back safely")
    void levelsParse() {
        assertThat(LogLevel.parse("debug", LogLevel.INFO)).isEqualTo(LogLevel.DEBUG);
        assertThat(LogLevel.parse("  Warn ", LogLevel.INFO)).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.parse("WARNING", LogLevel.INFO)).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.parse("SEVERE", LogLevel.INFO)).isEqualTo(LogLevel.ERROR);
        assertThat(LogLevel.parse("nonsense", LogLevel.INFO)).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.parse(null, LogLevel.ERROR)).isEqualTo(LogLevel.ERROR);
        assertThat(LogLevel.parse("  ", LogLevel.WARN)).isEqualTo(LogLevel.WARN);
    }

    @Test
    @DisplayName("Sensitive context keys are detected")
    void sensitiveKeysAreDetected() {
        assertThat(LogSanitizer.isSensitiveKey("token")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("adminToken")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("api-key")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("api_key")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("password")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("Authorization")).isTrue();
        assertThat(LogSanitizer.isSensitiveKey("player")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("season")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("trackedSignatures")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("redactEnabled")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("sessionCount")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey("statKey")).isFalse();
        assertThat(LogSanitizer.isSensitiveKey(null)).isFalse();
    }

    @Test
    @DisplayName("Sensitive values are redacted while normal values survive")
    void sensitiveValuesAreRedacted() {
        assertThat(LogSanitizer.value("token", "abcdef123456", true)).isEqualTo(LogSanitizer.MASK);
        assertThat(LogSanitizer.value("password", "hunter2", true)).isEqualTo(LogSanitizer.MASK);
        assertThat(LogSanitizer.value("player", "Notch", true)).isEqualTo("Notch");
        assertThat(LogSanitizer.value("token", "abcdef123456", false)).isEqualTo("abcdef123456");
    }

    @Test
    @DisplayName("Bearer headers are redacted even under a non sensitive key")
    void bearerHeadersAreRedacted() {
        String rendered = LogSanitizer.value("responseBody", "Authorization: Bearer abcdef123456", true);
        assertThat(rendered).doesNotContain("abcdef123456");
        assertThat(rendered).contains(LogSanitizer.MASK);
    }

    @Test
    @DisplayName("Null and empty sensitive values are not masked with noise")
    void nullValuesAreHandled() {
        assertThat(LogSanitizer.value("token", null, true)).isEqualTo("null");
        assertThat(LogSanitizer.value("token", "", true)).isEmpty();
    }

    @Test
    @DisplayName("Control characters are stripped from values")
    void controlCharactersAreStripped() {
        assertThat(LogSanitizer.value("msg", "a\nb\tc\rd", true)).isEqualTo("a b c d");
    }

    @Test
    @DisplayName("Long values are truncated with a marker")
    void longValuesAreTruncated() {
        String rendered = LogSanitizer.value("msg", "x".repeat(2000), true);
        assertThat(rendered).hasSizeLessThan(700);
        assertThat(rendered).contains("truncated");
    }

    @Test
    @DisplayName("Context pairs are rendered and odd keys are tolerated")
    void contextIsRendered() {
        Map<String, String> rendered = SMPLogger.renderContext(
            new Object[]{"player", "Notch", "season", "season-1", "flag"}, true);
        assertThat(rendered).containsEntry("player", "Notch");
        assertThat(rendered).containsEntry("season", "season-1");
        assertThat(rendered).containsEntry("flag", "null");
    }

    @Test
    @DisplayName("Context rendering skips null keys and never throws")
    void contextRenderingIsSafe() {
        Map<String, String> rendered = SMPLogger.renderContext(new Object[]{null, "x", "k", null}, true);
        assertThat(rendered).containsOnlyKeys("k");
        assertThat(rendered).containsEntry("k", "null");
    }

    @Test
    @DisplayName("Formatted records carry level, module, thread, context and stack trace")
    void formatterProducesStructuredOutput() {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("player", "Notch");

        String line = LogFormatter.format(LogLevel.ERROR, "database", "SMPWrapped-DB",
            "Failed to save player stats", context, new IllegalStateException("boom"), 10);

        assertThat(line).contains("ERROR");
        assertThat(line).contains("[database]");
        assertThat(line).contains("[SMPWrapped-DB]");
        assertThat(line).contains("Failed to save player stats");
        assertThat(line).contains("{player=Notch}");
        assertThat(line).contains("java.lang.IllegalStateException: boom");
        assertThat(line).contains("LoggingCoreTest");
    }

    @Test
    @DisplayName("Formatted records omit the stack trace when no throwable is given")
    void formatterOmitsStackTraceWhenAbsent() {
        String line = LogFormatter.format(LogLevel.INFO, "core", "main", "hello", Map.of(), null, 10);
        assertThat(line).contains("hello");
        assertThat(line).doesNotContain("\tat ");
    }

    @Test
    @DisplayName("Stack traces follow the cause chain and respect the frame budget")
    void stackTraceFollowsCauses() {
        Exception root = new IllegalArgumentException("root cause");
        Exception wrapper = new RuntimeException("wrapper", root);

        String trace = LogFormatter.stackTrace(wrapper, 500);
        assertThat(trace).contains("java.lang.RuntimeException: wrapper");
        assertThat(trace).contains("Caused by: java.lang.IllegalArgumentException: root cause");

        String truncated = LogFormatter.stackTrace(wrapper, 1);
        assertThat(truncated).contains("java.lang.RuntimeException: wrapper");
        assertThat(truncated).contains("truncated");
        assertThat(truncated.lines().filter(line -> line.contains("    at ")).count()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Self referencing causes do not loop forever")
    void stackTraceHandlesSelfCause() {
        Throwable loop = new RuntimeException("loop") {
            @Override
            public synchronized Throwable getCause() {
                return this;
            }
        };
        assertThat(LogFormatter.stackTrace(loop, 50)).contains("loop");
    }

    @Test
    @DisplayName("Startup headers contain version and environment details")
    void headerContainsEnvironment() {
        String header = LogFormatter.header("1.2.3");
        assertThat(header).contains("SMPWrapped 1.2.3");
        assertThat(header).contains("java=");
        assertThat(header).contains("timeZone=");
    }

    @Test
    @DisplayName("Log settings clamp invalid values and expose millisecond windows")
    void settingsClampValues() {
        LogSettings settings = LogSettings.builder()
            .maxFileSizeMb(0)
            .keepFiles(-5)
            .queueCapacity(1)
            .stackTraceDepth(-1)
            .dedupeWindowSeconds(0)
            .dedupeThrottleSeconds(-1)
            .level(null)
            .build();

        assertThat(settings.maxFileSizeBytes()).isZero();
        assertThat(settings.keepFiles()).isZero();
        assertThat(settings.queueCapacity()).isGreaterThanOrEqualTo(256);
        assertThat(settings.stackTraceDepth()).isZero();
        assertThat(settings.dedupeWindowMillis()).isEqualTo(1000L);
        assertThat(settings.dedupeThrottleMillis()).isZero();
        assertThat(settings.level()).isEqualTo(LogLevel.INFO);
    }
}