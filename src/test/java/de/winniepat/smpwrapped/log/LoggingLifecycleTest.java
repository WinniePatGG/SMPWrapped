package de.winniepat.smpwrapped.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingLifecycleTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void tearDown() {
        Logging.shutdown();
    }

    @Test
    @DisplayName("Loggers are cached per module and fall back to a core module")
    void loggersAreCached() {
        Logging.shutdown();

        SMPLogger first = SMPLogger.get("database");
        SMPLogger second = SMPLogger.get("database");
        SMPLogger core = SMPLogger.get(null);
        SMPLogger blank = SMPLogger.get("   ");

        assertThat(first).isSameAs(second);
        assertThat(first.module()).isEqualTo("database");
        assertThat(core).isSameAs(blank);
        assertThat(core.module()).isEqualTo("core");
    }

    @Test
    @DisplayName("Initialising logging writes a startup header to the log file")
    void initialiseWritesHeader() throws IOException {
        File dataFolder = tempDir.toFile();
        Logging.init("9.9.9", dataFolder, LogSettings.defaults(), null);

        Path logFile = tempDir.resolve("logs").resolve("smpwrapped.log");
        assertThat(Files.exists(logFile)).isTrue();
        assertThat(Files.readString(logFile, StandardCharsets.UTF_8)).contains("SMPWrapped 9.9.9");
        assertThat(Logging.isInitialized()).isTrue();
        assertThat(Logging.logFilePath()).contains("smpwrapped.log");
    }

    @Test
    @DisplayName("Log records reach the log file with level, module and context")
    void recordsReachTheLogFile() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(), LogSettings.defaults(), null);
        SMPLogger log = SMPLogger.get("database");

        log.info("something happened", "player", "Notch", "season", "season-1");
        log.error("save failed", new IllegalStateException("db down"), "player", "Notch");
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).contains("[database]");
        assertThat(content).contains("INFO ");
        assertThat(content).contains("something happened");
        assertThat(content).contains("{player=Notch, season=season-1}");
        assertThat(content).contains("[database] [Test worker] save failed");
        assertThat(content).contains("java.lang.IllegalStateException: db down");
    }

    @Test
    @DisplayName("Levels below the configured threshold are dropped")
    void levelThresholdIsRespected() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(),
            LogSettings.builder().level(LogLevel.WARN).build(), null);
        SMPLogger log = SMPLogger.get("filtered");

        assertThat(log.isEnabled(LogLevel.DEBUG)).isFalse();
        assertThat(log.isEnabled(LogLevel.WARN)).isTrue();

        log.debug("invisible");
        log.warn("visible");
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("invisible");
        assertThat(content).contains("visible");
    }

    @Test
    @DisplayName("Secrets are redacted in the log file")
    void secretsAreRedacted() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(),
            LogSettings.builder().redactSecrets(true).build(), null);

        SMPLogger.get("config").info("token exchange", "adminToken", "sup3rs3cr3t", "season", "s1");
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("sup3rs3cr3t");
        assertThat(content).contains(LogSanitizer.MASK);
        assertThat(content).contains("season=s1");
    }

    @Test
    @DisplayName("Stack traces can be switched off")
    void stackTracesCanBeDisabled() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(),
            LogSettings.builder().stackTraces(false).build(), null);

        SMPLogger.get("db").error("no trace please", new IllegalStateException("secret detail"));
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).contains("no trace please");
        assertThat(content).doesNotContain("secret detail");
    }

    @Test
    @DisplayName("Repeated errors are collapsed but reported with a suppressed counter")
    void repeatedErrorsAreCollapsed() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(), LogSettings.builder()
            .dedupeEnabled(true)
            .dedupeThreshold(1)
            .dedupeWindowSeconds(300)
            .dedupeThrottleSeconds(300)
            .build(), null);

        SMPLogger log = SMPLogger.get("db");
        for (int i = 0; i < 20; i++) {
            log.error("same failure");
        }
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        long occurrences = content.lines().filter(line -> line.contains("same failure")).count();
        assertThat(occurrences).isLessThan(20L);
    }

    @Test
    @DisplayName("Re-initialising with the same file settings keeps one writer")
    void reinitReusesWriter() throws IOException {
        File dataFolder = tempDir.toFile();
        LogSettings settings = LogSettings.defaults();

        Logging.init("1.0.0", dataFolder, settings, null);
        String firstPath = Logging.logFilePath();
        Logging.init("1.0.0", dataFolder, settings, null);

        assertThat(Logging.logFilePath()).isEqualTo(firstPath);

        Logging.shutdown();
        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content.split("=== SMPWrapped", -1).length - 1).isEqualTo(1);
    }

    @Test
    @DisplayName("File logging can be disabled entirely")
    void fileLoggingCanBeDisabled() {
        Logging.init("1.0.0", tempDir.toFile(),
            LogSettings.builder().fileEnabled(false).build(), null);

        SMPLogger.get("core").error("console only");
        assertThat(Logging.logFilePath()).isEqualTo("<disabled>");
        Logging.shutdown();

        assertThat(tempDir.resolve("logs")).doesNotExist();
    }

    @Test
    @DisplayName("Records are mirrored to the console logger with the throwable attached")
    void consoleReceivesRecords() {
        List<LogRecord> captured = new CopyOnWriteArrayList<>();
        Logger console = Logger.getLogger("SMPWrappedTestConsole-" + System.nanoTime());
        console.setUseParentHandlers(false);
        console.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                captured.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        Logging.init("1.0.0", tempDir.toFile(), LogSettings.defaults(), console);
        IllegalStateException failure = new IllegalStateException("boom");
        SMPLogger.get("web").error("request failed", failure, "path", "/api/v1/health");
        Logging.shutdown();

        LogRecord record = captured.stream()
            .filter(r -> r.getMessage().contains("request failed"))
            .findFirst()
            .orElse(null);

        assertThat(record).isNotNull();
        assertThat(record.getMessage()).contains("ERROR [web] request failed");
        assertThat(record.getMessage()).contains("path=/api/v1/health");
        assertThat(record.getThrown()).isSameAs(failure);
    }

    @Test
    @DisplayName("Shutdown reports a summary of the session")
    void shutdownReportsSummary() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(), LogSettings.defaults(), null);
        SMPLogger log = SMPLogger.get("db");
        log.error("first failure", new IllegalStateException("x"));
        log.warn("a warning");
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).contains("Shutdown summary");
        assertThat(content).contains("errorsAndAbove=1");
        assertThat(content).contains("WARN=1");
    }

    @Test
    @DisplayName("Summary logging can be switched off")
    void summaryCanBeDisabled() throws IOException {
        Logging.init("1.0.0", tempDir.toFile(),
            LogSettings.builder().summaryOnShutdown(false).build(), null);
        SMPLogger.get("db").error("boom");
        Logging.shutdown();

        String content = Files.readString(tempDir.resolve("logs").resolve("smpwrapped.log"),
            StandardCharsets.UTF_8);
        assertThat(content).doesNotContain("Shutdown summary");
    }

    @Test
    @DisplayName("Logging works before initialisation without throwing")
    void worksBeforeInitialisation() {
        Logging.shutdown();
        SMPLogger log = SMPLogger.get("early");

        assertThat(log.isEnabled(LogLevel.ERROR)).isTrue();
        assertThat(log.isEnabled(LogLevel.DEBUG)).isFalse();
        log.info("quiet");
        log.error("before init", new IllegalStateException("early"));
    }

    @Test
    @DisplayName("A null data folder disables file logging instead of failing")
    void nullDataFolderIsSafe() {
        Logging.init("1.0.0", null, LogSettings.defaults(), null);
        SMPLogger.get("core").error("no folder available");
        Logging.shutdown();

        assertThat(Logging.logFilePath()).isEqualTo("<disabled>");
    }

    @Test
    @DisplayName("Context rendering is exposed for reuse")
    void contextRenderingIsAvailable() {
        Map<String, String> rendered =
            SMPLogger.renderContext(new Object[]{"season", "season-1"}, true);
        assertThat(rendered).containsExactly(Map.entry("season", "season-1"));
    }
}