import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class RetentionManager {

    public static final String ARTIFACT_MARKER = ".vaultops-artifact";

    public record PurgeResult(int deleted, int failed, String firstError) {
        static PurgeResult empty() {
            return new PurgeResult(0, 0, null);
        }
    }

    public PurgeResult purge(Path root, int maxAgeDays, Instant now) {
        if (root == null || maxAgeDays <= 0 || now == null) {
            return PurgeResult.empty();
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            return PurgeResult.empty();
        }

        Instant cutoff = now.minus(Duration.ofDays(maxAgeDays));

        List<Path> children = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.forEach(children::add);
        } catch (IOException e) {
            return new PurgeResult(0, 1, describe(root, e));
        }

        int deleted = 0;
        int failed = 0;
        String firstError = null;
        for (Path child : children) {
            try {
                if (Files.isSymbolicLink(child)) {
                    // Never follow or delete symlinked entries; they may point outside the root.
                    continue;
                }
                if (!isVaultOpsArtifact(child)) {
                    // Only purge folders this tool created; never touch unrelated content sharing the root.
                    continue;
                }
                Instant modified = Files.getLastModifiedTime(child, LinkOption.NOFOLLOW_LINKS).toInstant();
                if (!modified.isBefore(cutoff)) {
                    continue;
                }
                deleteRecursively(child);
                deleted++;
            } catch (IOException e) {
                failed++;
                if (firstError == null) {
                    firstError = describe(child, e);
                }
            }
        }
        return new PurgeResult(deleted, failed, firstError);
    }

    private boolean isVaultOpsArtifact(Path child) {
        return Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                && Files.isRegularFile(child.resolve(ARTIFACT_MARKER), LinkOption.NOFOLLOW_LINKS);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        // Files.walk does not follow symlinks unless FOLLOW_LINKS is set, so traversal stays inside the entry.
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> ordered = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path entry : ordered) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private static String describe(Path path, IOException e) {
        String name = path.getFileName() == null ? path.toString() : path.getFileName().toString();
        String message = (e.getMessage() == null || e.getMessage().isBlank()) ? e.getClass().getSimpleName() : e.getMessage();
        return name + ": " + message;
    }
}

