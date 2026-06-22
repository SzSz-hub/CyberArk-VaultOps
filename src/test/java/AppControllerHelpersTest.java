import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("AppController pure helpers (staleness / folder canonicalization)")
class AppControllerHelpersTest {

    // The constructor only stores references; it never touches the JavaFX toolkit, so a
    // null UI is safe for exercising the pure helper methods reflectively.
    private final AppController controller = new AppController(null, new AppSettings(), null);

    private Object isStale(LocalDateTime loadedAt, FileTime modifiedAtLoad, FileTime current) throws Exception {
        Method m = AppController.class.getDeclaredMethod("isStale", LocalDateTime.class, FileTime.class, FileTime.class);
        m.setAccessible(true);
        return m.invoke(controller, loadedAt, modifiedAtLoad, current);
    }

    private Object canonicalize(String raw) throws Exception {
        Method m = AppController.class.getDeclaredMethod("canonicalizeFolder", String.class);
        m.setAccessible(true);
        return m.invoke(controller, raw);
    }

    // ------------------------------------------------------------------------------------ isStale (positive)

    @Test
    @DisplayName("A newer file on disk is detected as stale")
    void newerFileIsStale() throws Exception {
        FileTime loaded = FileTime.fromMillis(1_000_000);
        FileTime newer = FileTime.fromMillis(2_000_000);
        assertEquals(true, isStale(LocalDateTime.now(), loaded, newer));
    }

    // ------------------------------------------------------------------------------------ isStale (negative)

    @Test
    @DisplayName("Equal or older modification times are not stale")
    void sameOrOlderNotStale() throws Exception {
        FileTime loaded = FileTime.fromMillis(2_000_000);
        assertEquals(false, isStale(LocalDateTime.now(), loaded, FileTime.fromMillis(2_000_000)));
        assertEquals(false, isStale(LocalDateTime.now(), loaded, FileTime.fromMillis(1_000_000)));
    }

    @Test
    @DisplayName("Missing load metadata or current time is never stale")
    void nullsAreNotStale() throws Exception {
        assertEquals(false, isStale(null, FileTime.fromMillis(1), FileTime.fromMillis(2)));
        assertEquals(false, isStale(LocalDateTime.now(), null, FileTime.fromMillis(2)));
        assertEquals(false, isStale(LocalDateTime.now(), FileTime.fromMillis(1), null));
    }

    // ----------------------------------------------------------------------------- canonicalizeFolder (positive)

    @Test
    @DisplayName("An existing directory canonicalizes to an absolute, normalized path")
    void canonicalizeExistingDir(@TempDir Path tmp) throws Exception {
        Path nested = tmp.resolve("a").resolve("..").resolve("a");
        Files.createDirectories(tmp.resolve("a"));
        Path result = (Path) canonicalize(nested.toString());
        assertTrue(result.isAbsolute());
        assertEquals(tmp.resolve("a").toAbsolutePath().normalize(), result);
    }

    // ----------------------------------------------------------------------------- canonicalizeFolder (negative)

    @Test
    @DisplayName("A NUL character in the path is rejected")
    void canonicalizeRejectsNul() {
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> canonicalize("C:/bad\u0000path"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    @DisplayName("A path that exists but is a file (not a folder) is rejected")
    void canonicalizeRejectsFile(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("config.xml");
        Files.writeString(file, "x");
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> canonicalize(file.toString()));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
        assertFalse(Files.isDirectory(file));
    }
}

