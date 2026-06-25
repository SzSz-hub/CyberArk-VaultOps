import java.io.BufferedReader;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class DiagnosticsLog {

    public enum Level { INFO, WARN, ERROR }

    public record Entry(String timestamp, String level, String category, String message) {}

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;
    private static final int ROTATED_LOGS = 3;
    private static final int MAX_FIELD_LENGTH = 4000;

    private final Path logFile;

    public DiagnosticsLog(Path logFile) {
        this.logFile = logFile;
    }

    public Path file() {
        return logFile;
    }

    // -------------------------------------------------------------------------------------- write

    public void info(String category, String message) {
        log(Level.INFO, category, message);
    }

    public void warn(String category, String message) {
        log(Level.WARN, category, message);
    }

    public void error(String category, String message) {
        log(Level.ERROR, category, message);
    }

    public void error(String category, String message, Throwable error) {
        log(Level.ERROR, category, error == null ? message : message + " | " + describe(error));
    }

    public synchronized boolean log(Level level, String category, String message) {
        if (logFile == null) {
            return false;
        }
        try {
            Path parent = logFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = LocalDateTime.now().format(TIMESTAMP) + "\t"
                    + (level == null ? Level.INFO : level) + "\t"
                    + safe(category) + "\t" + safe(message) + System.lineSeparator();
            appendLocked(line);
            return true;
        } catch (IOException failure) {
            // Diagnostics logging must never disrupt the application; swallow the failure.
            return false;
        }
    }

    private void appendLocked(String line) throws IOException {
        Path lockPath = logFile.resolveSibling(logFile.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            rotateIfNeeded();
            Files.writeString(logFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    // --------------------------------------------------------------------------------------- read

    public synchronized List<Entry> readRecent(int maxEntries) {
        List<Entry> entries = new ArrayList<>();
        if (logFile == null || maxEntries <= 0 || !Files.exists(logFile)) {
            return entries;
        }
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                tail.addLast(line);
                if (tail.size() > maxEntries) {
                    tail.removeFirst();
                }
            }
        } catch (IOException ignored) {
            // Best-effort read; return whatever was collected before the failure.
        }
        for (String raw : tail) {
            entries.add(parse(raw));
        }
        return entries;
    }

    public synchronized boolean clear() {
        if (logFile == null) {
            return false;
        }
        try {
            if (Files.exists(logFile)) {
                Files.writeString(logFile, "", StandardCharsets.UTF_8,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            }
            return true;
        } catch (IOException failure) {
            return false;
        }
    }

    private static Entry parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Entry("", "", "", "");
        }
        String[] parts = raw.split("\t", 4);
        if (parts.length == 4) {
            return new Entry(parts[0], parts[1], parts[2], parts[3]);
        }
        return new Entry("", "", "", raw);
    }

    // ----------------------------------------------------------------------------------- rotation

    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(logFile) || Files.size(logFile) < MAX_LOG_BYTES) {
            return;
        }
        for (int index = ROTATED_LOGS; index >= 1; index--) {
            Path source = index == 1 ? logFile : logFile.resolveSibling(logFile.getFileName() + "." + (index - 1));
            Path target = logFile.resolveSibling(logFile.getFileName() + "." + index);
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    // ------------------------------------------------------------------------------------- helpers

    private static String describe(Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append(error.getClass().getSimpleName());
        if (error.getMessage() != null && !error.getMessage().isBlank()) {
            sb.append(": ").append(error.getMessage());
        }
        StackTraceElement[] trace = error.getStackTrace();
        if (trace != null && trace.length > 0) {
            sb.append(" @ ").append(trace[0]);
        }
        Throwable cause = error.getCause();
        if (cause != null && cause != error) {
            sb.append(" <- ").append(cause.getClass().getSimpleName());
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                sb.append(": ").append(cause.getMessage());
            }
        }
        return sb.toString();
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
        if (cleaned.length() > MAX_FIELD_LENGTH) {
            cleaned = cleaned.substring(0, MAX_FIELD_LENGTH) + "\u2026";
        }
        return cleaned;
    }
}

