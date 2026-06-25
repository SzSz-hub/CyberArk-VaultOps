import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("DiagnosticsLog (durable diagnostics)")
class DiagnosticsLogTest {

    @Test
    @DisplayName("Writes are read back as parsed entries in order")
    void writesAreReadBack(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("diagnostics.log"));

        log.info("app", "started");
        log.warn("retention", "purged 3");
        log.error("load", "boom");

        List<DiagnosticsLog.Entry> entries = log.readRecent(100);
        assertEquals(3, entries.size());
        assertEquals("INFO", entries.get(0).level());
        assertEquals("app", entries.get(0).category());
        assertEquals("started", entries.get(0).message());
        assertEquals("WARN", entries.get(1).level());
        assertEquals("ERROR", entries.get(2).level());
    }

    @Test
    @DisplayName("readRecent returns only the most recent N entries (bounded)")
    void readRecentIsBounded(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("diagnostics.log"));
        for (int i = 0; i < 50; i++) {
            log.info("loop", "entry-" + i);
        }

        List<DiagnosticsLog.Entry> entries = log.readRecent(10);
        assertEquals(10, entries.size());
        assertEquals("entry-40", entries.get(0).message());
        assertEquals("entry-49", entries.get(9).message());
    }

    @Test
    @DisplayName("Tabs and newlines in a message are sanitized so each entry stays one line")
    void sanitizesControlCharacters(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("diagnostics.log"));
        log.info("ui", "line1\nline2\tcol\rend");

        List<DiagnosticsLog.Entry> entries = log.readRecent(10);
        assertEquals(1, entries.size());
        assertFalse(entries.get(0).message().contains("\n"));
        assertFalse(entries.get(0).message().contains("\t"));
        assertFalse(entries.get(0).message().contains("\r"));
    }

    @Test
    @DisplayName("error(category, message, throwable) records the exception type and message")
    void errorRecordsThrowable(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("diagnostics.log"));
        log.error("load", "could not parse", new IllegalStateException("bad folder"));

        List<DiagnosticsLog.Entry> entries = log.readRecent(10);
        assertEquals(1, entries.size());
        assertEquals("ERROR", entries.get(0).level());
        assertTrue(entries.get(0).message().contains("IllegalStateException"));
        assertTrue(entries.get(0).message().contains("bad folder"));
    }

    @Test
    @DisplayName("clear() truncates the log file")
    void clearTruncates(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("diagnostics.log"));
        log.info("app", "before clear");
        assertFalse(log.readRecent(10).isEmpty());

        assertTrue(log.clear());
        assertTrue(log.readRecent(10).isEmpty());
    }

    @Test
    @DisplayName("readRecent on a missing file returns an empty list, not an error")
    void readMissingFile(@TempDir Path tmp) {
        DiagnosticsLog log = new DiagnosticsLog(tmp.resolve("does-not-exist.log"));
        assertTrue(log.readRecent(10).isEmpty());
    }

    @Test
    @DisplayName("A pre-existing line without tabs is surfaced as a raw message")
    void rawLineParsing(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("diagnostics.log");
        Files.writeString(file, "a legacy unstructured line" + System.lineSeparator(), StandardCharsets.UTF_8);

        DiagnosticsLog log = new DiagnosticsLog(file);
        List<DiagnosticsLog.Entry> entries = log.readRecent(10);
        assertEquals(1, entries.size());
        assertEquals("a legacy unstructured line", entries.get(0).message());
        assertEquals("", entries.get(0).level());
    }
}

