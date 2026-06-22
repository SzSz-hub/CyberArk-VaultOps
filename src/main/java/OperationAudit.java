import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OperationAudit {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path auditFile;

    public OperationAudit(Path auditFile) {
        this.auditFile = auditFile;
    }

    // -------------------------------------------------------------------------------------- record

    public synchronized void record(String operation, Map<String, String> fields) {
        if (auditFile == null) {
            return;
        }
        try {
            Path parent = auditFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            String line = LocalDateTime.now().format(TIMESTAMP) + "\t" + safe(operation) + "\t" + render(fields)
                    + System.lineSeparator();
            Files.writeString(auditFile, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // Audit logging is best-effort and must never break a user operation.
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

