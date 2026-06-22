import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("PvwaClient contract (against a local HTTP server)")
class PvwaClientContractTest {

    private HttpServer server;
    private final PvwaClient client = new PvwaClient();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    private String start(String path, HttpHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext(path, handler);
        server.start();
        return "http://localhost:" + server.getAddress().getPort();
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static String drain(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private PvwaClient.Credentials creds(String base) {
        return new PvwaClient.Credentials(base, "CyberArk", "user", "secret", false, false);
    }

    // --------------------------------------------------------------------------------------- logon (positive)

    @Test
    @DisplayName("logon posts to /api/Auth/CyberArk/Logon and returns the session token")
    void logonReturnsToken() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        String base = start("/api/Auth/CyberArk/Logon", exchange -> {
            requestBody.set(drain(exchange.getRequestBody()));
            respond(exchange, 200, "\"TOKEN-123\"");
        });

        PvwaClient.Session session = client.logon(creds(base));

        assertEquals(base, session.baseUri());
        assertEquals("TOKEN-123", session.token());
        assertNotNull(requestBody.get());
        assertTrue(requestBody.get().contains("\"username\":\"user\""));
        assertTrue(requestBody.get().contains("\"concurrentSession\":false"));
    }

    // --------------------------------------------------------------------------------------- logon (negative)

    @Test
    @DisplayName("logon surfaces the server ErrorMessage on a non-2xx response")
    void logonFailureSurfacesError() throws Exception {
        String base = start("/api/Auth/CyberArk/Logon",
                exchange -> respond(exchange, 403, "{\"ErrorCode\":\"X\",\"ErrorMessage\":\"bad credentials\"}"));

        IOException error = assertThrows(IOException.class, () -> client.logon(creds(base)));
        assertTrue(error.getMessage().contains("bad credentials"));
        assertTrue(error.getMessage().contains("403"));
    }

    @Test
    @DisplayName("logon fails when a 2xx response carries no token")
    void logonEmptyTokenFails() throws Exception {
        String base = start("/api/Auth/CyberArk/Logon", exchange -> respond(exchange, 200, "\"\""));

        IOException error = assertThrows(IOException.class, () -> client.logon(creds(base)));
        assertTrue(error.getMessage().toLowerCase().contains("token"));
    }

    // --------------------------------------------------------------------------------------- import (positive)

    @Test
    @DisplayName("import posts the package with the token as the Authorization header")
    void importSendsAuthorizationHeader() throws Exception {
        AtomicReference<String> authHeader = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        String base = start("/API/ConnectionComponents/Import", exchange -> {
            authHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            body.set(drain(exchange.getRequestBody()));
            respond(exchange, 200, "{\"Result\":\"ok\"}");
        });

        PvwaClient.Session session = new PvwaClient.Session(base, "TOKEN-123", false);
        String response = client.importConnectionComponent(session, new byte[]{0, 1, 2, 3});

        assertEquals("TOKEN-123", authHeader.get());
        assertTrue(body.get().contains("\"ImportFile\""));
        assertTrue(response.contains("ok"));
    }

    // --------------------------------------------------------------------------------------- import (negative)

    @Test
    @DisplayName("import surfaces the server error on a non-2xx response")
    void importFailureSurfacesError() throws Exception {
        String base = start("/API/ConnectionComponents/Import",
                exchange -> respond(exchange, 500, "{\"ErrorMessage\":\"import exploded\"}"));

        PvwaClient.Session session = new PvwaClient.Session(base, "TOKEN-123", false);
        IOException error = assertThrows(IOException.class,
                () -> client.importConnectionComponent(session, new byte[]{1}));
        assertTrue(error.getMessage().contains("import exploded"));
    }

    // ----------------------------------------------------------------------------------------------- logoff

    @Test
    @DisplayName("logoff posts to /API/Auth/Logoff without throwing")
    void logoffPosts() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        String base = start("/API/Auth/Logoff", exchange -> {
            method.set(exchange.getRequestMethod());
            respond(exchange, 200, "");
        });

        client.logoff(new PvwaClient.Session(base, "TOKEN-123", false));
        assertEquals("POST", method.get());
    }
}

