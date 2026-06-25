import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("RetentionManager (auto-purge)")
class RetentionManagerTest {

    private final RetentionManager manager = new RetentionManager();

    private Path agedDir(Path root, String name, int ageDays) throws IOException {
        Path dir = Files.createDirectories(root.resolve(name));
        Files.writeString(dir.resolve("file.txt"), "x");
        Files.writeString(dir.resolve(RetentionManager.ARTIFACT_MARKER), "marker");
        FileTime stamp = FileTime.from(Instant.now().minus(ageDays, ChronoUnit.DAYS));
        // Stamp the nested files first, then the directory itself (directory mtime drives the decision).
        Files.setLastModifiedTime(dir.resolve("file.txt"), stamp);
        Files.setLastModifiedTime(dir.resolve(RetentionManager.ARTIFACT_MARKER), stamp);
        Files.setLastModifiedTime(dir, stamp);
        return dir;
    }

    @Test
    @DisplayName("Entries older than the cutoff are deleted; newer ones are kept")
    void deletesOnlyOldEntries(@TempDir Path tmp) throws Exception {
        Path old = agedDir(tmp, "old", 40);
        Path fresh = agedDir(tmp, "fresh", 2);

        RetentionManager.PurgeResult result = manager.purge(tmp, 30, Instant.now());

        assertEquals(1, result.deleted());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(fresh));
    }

    @Test
    @DisplayName("Old directories are deleted recursively, including nested content")
    void deletesRecursively(@TempDir Path tmp) throws Exception {
        Path old = agedDir(tmp, "old", 90);
        Path nested = Files.createDirectories(old.resolve("a/b/c"));
        Files.writeString(nested.resolve("deep.txt"), "deep");
        FileTime stamp = FileTime.from(Instant.now().minus(90, ChronoUnit.DAYS));
        Files.setLastModifiedTime(old, stamp);

        RetentionManager.PurgeResult result = manager.purge(tmp, 30, Instant.now());

        assertEquals(1, result.deleted());
        assertFalse(Files.exists(old));
    }

    @Test
    @DisplayName("Retention of 0 days disables purging entirely")
    void zeroDaysDisablesPurge(@TempDir Path tmp) throws Exception {
        Path old = agedDir(tmp, "old", 365);

        RetentionManager.PurgeResult result = manager.purge(tmp, 0, Instant.now());

        assertEquals(0, result.deleted());
        assertTrue(Files.exists(old));
    }

    @Test
    @DisplayName("A missing root is a no-op, not an error")
    void missingRootIsNoOp(@TempDir Path tmp) {
        RetentionManager.PurgeResult result = manager.purge(tmp.resolve("nope"), 30, Instant.now());

        assertEquals(0, result.deleted());
        assertEquals(0, result.failed());
    }

    @Test
    @DisplayName("Null arguments are handled defensively")
    void nullArgumentsSafe(@TempDir Path tmp) {
        assertEquals(0, manager.purge(null, 30, Instant.now()).deleted());
        assertEquals(0, manager.purge(tmp, 30, null).deleted());
    }

    @Test
    @DisplayName("Unmarked entries (not created by VaultOps) are never purged")
    void preservesUnmarkedEntries(@TempDir Path tmp) throws Exception {
        Path looseFile = tmp.resolve("PSM-old.zip");
        Files.writeString(looseFile, "zip");
        Files.setLastModifiedTime(looseFile, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

        Path unmarkedDir = Files.createDirectories(tmp.resolve("unrelated"));
        Files.writeString(unmarkedDir.resolve("data.txt"), "keep");
        Files.setLastModifiedTime(unmarkedDir, FileTime.from(Instant.now().minus(60, ChronoUnit.DAYS)));

        RetentionManager.PurgeResult result = manager.purge(tmp, 30, Instant.now());

        assertEquals(0, result.deleted());
        assertTrue(Files.exists(looseFile));
        assertTrue(Files.exists(unmarkedDir));
    }
}

