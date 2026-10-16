package de.winniepat.smpwrapped.log;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;

final class LogWriter implements AutoCloseable {

    private final Path baseFile;
    private final long maxFileSizeBytes;
    private final int keepFiles;
    private final BlockingQueue<String> queue;
    private final Thread worker;
    private final Consumer<String> failureSink;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong writtenLines = new AtomicLong();
    private final AtomicLong rotations = new AtomicLong();

    private volatile boolean running = true;
    private volatile BufferedWriter writer;

    LogWriter(Path baseFile, int queueCapacity, long maxFileSizeBytes, int keepFiles,
              Consumer<String> failureSink) {
        this.baseFile = baseFile;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.keepFiles = Math.max(1, keepFiles);
        this.queue = new ArrayBlockingQueue<>(Math.max(256, queueCapacity));
        this.failureSink = failureSink;
        this.worker = new Thread(this::drainLoop, "SMPWrapped-LogWriter");
        this.worker.setDaemon(true);
    }

    void start() {
        open();
        worker.start();
    }

    boolean isRunning() {
        return running;
    }

    boolean write(String line) {
        if (!running) {
            return false;
        }
        if (queue.offer(line)) {
            return true;
        }
        long droppedNow = dropped.incrementAndGet();
        if (droppedNow == 1 || droppedNow % 1000 == 0) {
            report("log queue full, dropped " + droppedNow + " line(s)");
        }
        return false;
    }

    void flush() {
        long deadline = System.currentTimeMillis() + 1500L;
        while (!queue.isEmpty() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        BufferedWriter current = writer;
        if (current == null) {
            return;
        }
        synchronized (this) {
            try {
                current.flush();
            } catch (IOException e) {
                report("flush failed: " + e);
            }
        }
    }

    long droppedCount() {
        return dropped.get();
    }

    long writtenCount() {
        return writtenLines.get();
    }

    long bytesCount() {
        return written.get();
    }

    long rotationCount() {
        return rotations.get();
    }

    private void drainLoop() {
        while (running || !queue.isEmpty()) {
            String line;
            try {
                line = queue.poll(250L, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (line == null) {
                continue;
            }
            try {
                writeLine(line);
            } catch (Throwable t) {
                report("write failed: " + t);
                closeQuietly();
            }
        }
        flushQuietly();
        closeQuietly();
    }

    private void writeLine(String line) throws IOException {
        BufferedWriter current = writer;
        if (current == null) {
            open();
            current = writer;
            if (current == null) {
                return;
            }
        }
        synchronized (this) {
            if (maxFileSizeBytes > 0 && written.get() > 0 && written.get() + line.length() > maxFileSizeBytes) {
                rotate();
                current = writer;
                if (current == null) {
                    return;
                }
            }
            current.write(line);
            current.write(System.lineSeparator());
            written.addAndGet(line.length() + 1L);
            writtenLines.incrementAndGet();
            if (queue.isEmpty()) {
                current.flush();
            }
        }
    }

    private synchronized void open() {
        try {
            Path parent = baseFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            long size = Files.exists(baseFile) ? Files.size(baseFile) : 0L;
            OutputStream out = Files.newOutputStream(baseFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8), 8192);
            written.set(size);
        } catch (Throwable t) {
            writer = null;
            report("cannot open log file " + baseFile + ": " + t);
        }
    }

    private synchronized void rotate() {
        closeQuietly();
        for (int i = keepFiles - 1; i >= 1; i--) {
            Path from = archive(i);
            Path to = archive(i + 1);
            if (!Files.exists(from)) {
                continue;
            }
            try {
                Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            } catch (Throwable t) {
                report("rotation failed: " + t);
            }
        }
        Path firstArchive = archive(1);
        if (Files.exists(baseFile)) {
            try {
                Files.deleteIfExists(firstArchive);
                gzip(baseFile, firstArchive);
                Files.deleteIfExists(baseFile);
            } catch (Throwable t) {
                report("rotation failed: " + t);
            }
        }
        rotations.incrementAndGet();
        open();
    }

    private void gzip(Path source, Path destination) throws IOException {
        try (InputStream in = Files.newInputStream(source);
             OutputStream out = new GZIPOutputStream(Files.newOutputStream(destination))) {
            in.transferTo(out);
        }
    }

    private Path archive(int index) {
        return baseFile.resolveSibling(baseFile.getFileName() + "." + index + ".gz");
    }

    private synchronized void flushQuietly() {
        BufferedWriter current = writer;
        if (current == null) {
            return;
        }
        try {
            current.flush();
        } catch (IOException e) {
            report("flush failed: " + e);
        }
    }

    private synchronized void closeQuietly() {
        BufferedWriter current = writer;
        writer = null;
        if (current == null) {
            return;
        }
        try {
            current.flush();
            current.close();
        } catch (IOException e) {
            report("close failed: " + e);
        }
    }

    private void report(String message) {
        if (failureSink != null) {
            try {
                failureSink.accept(message);
            } catch (Throwable ignored) {
            }
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            worker.join(3000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        closeQuietly();
    }
}