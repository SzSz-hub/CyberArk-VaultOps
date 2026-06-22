import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("PvwaClient")
class PvwaClientTest {

    private final PvwaClient client = new PvwaClient();

    private static Object invokeStatic(String name, Object arg) throws Exception {
        Method m = PvwaClient.class.getDeclaredMethod(name, String.class);
        m.setAccessible(true);
        return m.invoke(null, arg);
    }

    // -------------------------------------------------------------------------- argument validation (negative)

    @Test
    @DisplayName("logon rejects null credentials")
    void logonNullCredentials() {
        assertThrows(IllegalArgumentException.class, () -> client.logon(null));
    }

    @Test
    @DisplayName("logon rejects a blank base URI")
    void logonBlankBase() {
        PvwaClient.Credentials creds = new PvwaClient.Credentials("  ", "CyberArk", "u", "p", false, true);
        assertThrows(IllegalArgumentException.class, () -> client.logon(creds));
    }

    @Test
    @DisplayName("import rejects a null session")
    void importNullSession() {
        assertThrows(IllegalStateException.class, () -> client.importConnectionComponent(null, new byte[]{1}));
    }

    @Test
    @DisplayName("import rejects an empty package")
    void importEmptyPackage() {
        PvwaClient.Session session = new PvwaClient.Session("https://pvwa.example.com", "token", false);
        assertThrows(IllegalArgumentException.class, () -> client.importConnectionComponent(session, new byte[0]));
    }

    @Test
    @DisplayName("logoff on a null session is a no-op")
    void logoffNullSession() throws Exception {
        client.logoff(null);
    }

    // ----------------------------------------------------------------------------- base normalization (helper)

    @Test
    @DisplayName("normalizeBase prefixes https and trims trailing slashes")
    void normalizeBase() throws Exception {
        assertEquals("https://host", invokeStatic("normalizeBase", "host"));
        assertEquals("http://host", invokeStatic("normalizeBase", "http://host/"));
        assertEquals("https://host", invokeStatic("normalizeBase", "https://host//"));
        assertEquals("https://host:8443", invokeStatic("normalizeBase", "  host:8443  "));
    }

    @Test
    @DisplayName("normalizeBase rejects a blank address")
    void normalizeBaseBlank() {
        InvocationTargetException ex = assertThrows(InvocationTargetException.class,
                () -> invokeStatic("normalizeBase", "   "));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    // ------------------------------------------------------------------------------ JSON escaping (security)

    @Test
    @DisplayName("jsonString escapes quotes, backslashes and control characters")
    void jsonStringEscaping() throws Exception {
        assertEquals("\"a\\\"b\"", invokeStatic("jsonString", "a\"b"));
        assertEquals("\"a\\\\b\"", invokeStatic("jsonString", "a\\b"));
        assertEquals("\"line\\n\"", invokeStatic("jsonString", "line\n"));
        assertEquals("\"\\u0001\"", invokeStatic("jsonString", "\u0001"));
        assertEquals("\"\"", invokeStatic("jsonString", (Object) null));
    }

    // ------------------------------------------------------------------------------ token / error extraction

    @Test
    @DisplayName("unwrapToken strips surrounding quotes and unescapes slashes")
    void unwrapToken() throws Exception {
        assertEquals("abc", invokeStatic("unwrapToken", "\"abc\""));
        assertEquals("a/b", invokeStatic("unwrapToken", "a\\/b"));
        assertEquals("plain", invokeStatic("unwrapToken", "plain"));
    }

    @Test
    @DisplayName("extractError pulls the ErrorMessage field or falls back")
    void extractError() throws Exception {
        assertEquals("boom", invokeStatic("extractError", "{\"ErrorCode\":\"X\",\"ErrorMessage\":\"boom\"}"));
        assertEquals("no response body", invokeStatic("extractError", ""));
    }
}

