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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class OperationAudit {

    public record Entry(String timestamp, String operation, String details) {}

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_LOG_BYTES = 5L * 1024 * 1024;
    private static final int ROTATED_LOGS = 5;

    private final Path auditFile;
    private volatile Consumer<String> warningHandler = message -> {};

    public OperationAudit(Path auditFile) {
        this.auditFile = auditFile;
    }

    public Path file() {
        return auditFile;
    }

    public void setWarningHandler(Consumer<String> warningHandler) {
        this.warningHandler = warningHandler == null ? message -> {} : warningHandler;
    }

    // -------------------------------------------------------------------------------------- record

    public synchronized boolean record(String operation, Map<String, String> fields) {
        if (auditFile == null) {
            return false;
        }
        try {
            Path parent = auditFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = LocalDateTime.now().format(TIMESTAMP) + "\t" + safe(operation) + "\t" + render(fields)
                    + System.lineSeparator();
            appendLocked(line);
            return true;
        } catch (IOException failure) {
            warningHandler.accept("Audit logging failed for '" + safe(operation) + "': " + failure.getMessage());
            return false;
        }
    }

    private void appendLocked(String line) throws IOException {
        Path lockPath = auditFile.resolveSibling(auditFile.getFileName() + ".lock");
        try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            rotateIfNeeded();
            Files.writeString(auditFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
    }

    // ---------------------------------------------------------------------------------------- read

    public synchronized List<Entry> readRecent(int maxEntries) {
        List<Entry> entries = new ArrayList<>();
        if (auditFile == null || maxEntries <= 0 || !Files.exists(auditFile)) {
            return entries;
        }
        Deque<String> tail = new ArrayDeque<>();
        try (BufferedReader reader = Files.newBufferedReader(auditFile, StandardCharsets.UTF_8)) {
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

    private static Entry parse(String raw) {
        if (raw == null || raw.isEmpty()) {
            return new Entry("", "", "");
        }
        String[] parts = raw.split("\t", 3);
        if (parts.length == 3) {
            return new Entry(parts[0], parts[1], parts[2]);
        }
        if (parts.length == 2) {
            return new Entry(parts[0], parts[1], "");
        }
        return new Entry("", "", raw);
    }

    private void rotateIfNeeded() throws IOException {
        if (!Files.exists(auditFile) || Files.size(auditFile) < MAX_LOG_BYTES) {
            return;
        }
        for (int index = ROTATED_LOGS; index >= 1; index--) {
            Path source = index == 1 ? auditFile : auditFile.resolveSibling(auditFile.getFileName() + "." + (index - 1));
            Path target = auditFile.resolveSibling(auditFile.getFileName() + "." + index);
            if (Files.exists(source)) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static String render(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        Map<String, String> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            if ("password".equalsIgnoreCase(key) || "token".equalsIgnoreCase(key)) {
                continue;
            }
            filtered.put(key, safe(entry.getValue()));
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : filtered.entrySet()) {
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return sb.toString();
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
