package de.winniepat.smpwrapped.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogWriterTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Written lines land in the log file and the queue drains on close")
    void writesLinesToDisk() throws IOException {
        Path file = tempDir.resolve("smpwrapped.log");
        List<String> failures = new CopyOnWriteArrayList<>();

        LogWriter writer = new LogWriter(file, 1024, 5L * 1024L * 1024L, 3, failures::add);
        writer.start();

        writer.write("first line");
        writer.write("second line");
        writer.close();

        assertThat(Files.exists(file)).isTrue();
        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).contains("first line").contains("second line");
        assertThat(writer.writtenCount()).isEqualTo(2L);
        assertThat(writer.droppedCount()).isZero();
        assertThat(failures).isEmpty();
    }

    @Test
    @DisplayName("Writing after close is rejected")
    void rejectsWritesAfterClose() {
        Path file = tempDir.resolve("smpwrapped.log");
        LogWriter writer = new LogWriter(file, 1024, 1024L, 3, message -> { });
        writer.start();
        writer.close();

        assertThat(writer.isRunning()).isFalse();
        assertThat(writer.write("late")).isFalse();
    }

    @Test
    @DisplayName("Oversized files are rotated into gzipped archives")
    void rotatesOversizedFiles() throws IOException {
        Path file = tempDir.resolve("smpwrapped.log");
        LogWriter writer = new LogWriter(file, 1024, 400L, 2, message -> { });
        writer.start();

        for (int i = 0; i < 40; i++) {
            writer.write("line-" + i + " padding padding padding padding padding");
        }
        writer.close();

        assertThat(writer.rotationCount()).isGreaterThan(0L);
        assertThat(Files.exists(file)).isTrue();

        List<Path> archives;
        try (var stream = Files.list(tempDir)) {
            archives = stream.filter(p -> p.getFileName().toString().endsWith(".gz"))
                .collect(java.util.stream.Collectors.toList());
        }
        assertThat(archives).isNotEmpty();
        assertThat(archives).allSatisfy(p ->
            assertThat(p.getFileName().toString()).startsWith("smpwrapped.log."));

        Path newest = tempDir.resolve("smpwrapped.log.1.gz");
        assertThat(Files.exists(newest)).isTrue();
        try (GZIPInputStream in = new GZIPInputStream(Files.newInputStream(newest))) {
            assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8)).contains("line-");
        }
    }

    @Test
    @DisplayName("Only the configured number of archives is kept")
    void keepsOnlyConfiguredArchives() throws IOException {
        Path file = tempDir.resolve("smpwrapped.log");
        LogWriter writer = new LogWriter(file, 1024, 300L, 1, message -> { });
        writer.start();

        for (int i = 0; i < 60; i++) {
            writer.write("entry-" + i + " with enough text to force several rotations");
        }
        writer.close();

        try (var stream = Files.list(tempDir)) {
            assertThat(stream.filter(p -> p.getFileName().toString().endsWith(".gz")).count())
                .isLessThanOrEqualTo(1L);
        }
    }

    @Test
    @DisplayName("Rotation failures are reported without killing the writer")
    void reportsRotationFailures() {
        List<String> failures = new CopyOnWriteArrayList<>();
        Path file = tempDir.resolve("smpwrapped.log");

        LogWriter writer = new LogWriter(file, 1024, 50L, 2, failures::add);
        writer.start();
        for (int i = 0; i < 20; i++) {
            writer.write("x".repeat(80));
        }
        writer.close();

        assertThat(failures).isEmpty();
        assertThat(Files.exists(file)).isTrue();
    }

    @Test
    @DisplayName("A missing parent directory is created automatically")
    void createsParentDirectories() throws IOException {
        Path nested = tempDir.resolve("a").resolve("b").resolve("smpwrapped.log");
        LogWriter writer = new LogWriter(nested, 1024, 1024L, 3, message -> { });
        writer.start();
        writer.write("nested");
        writer.close();

        assertThat(Files.exists(nested)).isTrue();
    }

    @Test
    @DisplayName("Appending to an existing log keeps the previous content")
    void appendsToExistingFile() throws IOException {
        Path file = tempDir.resolve("smpwrapped.log");
        Files.writeString(file, "previous session\n", StandardCharsets.UTF_8);

        LogWriter writer = new LogWriter(file, 1024, 1024L, 3, message -> { });
        writer.start();
        writer.write("new session");
        writer.close();

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertThat(content).contains("previous session").contains("new session");
    }

    @Test
    @DisplayName("An unwritable directory is reported and does not throw")
    void unwritablePathIsReported() throws IOException {
        List<String> failures = new CopyOnWriteArrayList<>();
        Path blocker = tempDir.resolve("blocker");
        Files.writeString(blocker, "not a directory", StandardCharsets.UTF_8);
        Path bad = blocker.resolve("smpwrapped.log");

        LogWriter writer = new LogWriter(bad, 1024, 1024L, 3, failures::add);
        writer.start();
        writer.write("anything");
        writer.close();

        assertThat(failures).isNotEmpty();
        assertThat(writer.writtenCount()).isZero();
    }
}