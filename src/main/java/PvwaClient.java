import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class PvwaClient {

    private static volatile SSLSocketFactory trustAllSocketFactory;

    public record Credentials(
            String baseUri,
            String authType,
            String username,
            String password,
            boolean concurrentSession,
            boolean ignoreCertificateErrors) {
    }

    public record Session(String baseUri, String token, boolean ignoreCertificateErrors) {
    }

    private record HttpResponse(int status, String body) {
    }

    // ------------------------------------------------------------------------------------------- Logon

    public Session logon(Credentials credentials) throws Exception {
        if (credentials == null) {
            throw new IllegalArgumentException("PVWA credentials are required.");
        }
        String base = normalizeBase(credentials.baseUri());
        String type = (credentials.authType() == null || credentials.authType().isBlank())
                ? "CyberArk" : credentials.authType().trim();
        String url = base + "/api/Auth/" + type + "/Logon";

        String body = "{"
                + "\"username\":" + jsonString(credentials.username()) + ","
                + "\"password\":" + jsonString(credentials.password()) + ","
                + "\"concurrentSession\":" + (credentials.concurrentSession() ? "true" : "false")
                + "}";

        HttpResponse response = post(url, body.getBytes(StandardCharsets.UTF_8), null, credentials.ignoreCertificateErrors());
        if (response.status() < 200 || response.status() >= 300) {
            throw new IOException("Logon failed (HTTP " + response.status() + "): " + extractError(response.body()));
        }

        String token = unwrapToken(response.body());
        if (token == null || token.isBlank()) {
            throw new IOException("Logon succeeded but no session token was returned.");
        }
        return new Session(base, token, credentials.ignoreCertificateErrors());
    }

    // ------------------------------------------------------------------------------------------ Import

    public String importConnectionComponent(Session session, byte[] zipBytes) throws Exception {
        if (session == null) {
            throw new IllegalStateException("Not connected to PVWA.");
        }
        if (zipBytes == null || zipBytes.length == 0) {
            throw new IllegalArgumentException("Connection component package is empty.");
        }

        String url = session.baseUri() + "/API/ConnectionComponents/Import";

        StringBuilder sb = new StringBuilder(zipBytes.length * 4 + 32);
        sb.append("{\"ImportFile\":[");
        for (int i = 0; i < zipBytes.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(zipBytes[i] & 0xFF);
        }
        sb.append("]}");

        HttpResponse response = post(url, sb.toString().getBytes(StandardCharsets.UTF_8),
                session.token(), session.ignoreCertificateErrors());
        if (response.status() < 200 || response.status() >= 300) {
            throw new IOException("Import failed (HTTP " + response.status() + "): " + extractError(response.body()));
        }
        return response.body();
    }

    // ------------------------------------------------------------------------------------------ Logoff

    public void logoff(Session session) throws Exception {
        if (session == null) {
            return;
        }
        String url = session.baseUri() + "/API/Auth/Logoff";
        post(url, new byte[0], session.token(), session.ignoreCertificateErrors());
    }

    // ------------------------------------------------------------------------------------------ HTTP IO

    private HttpResponse post(String urlString, byte[] body, String authToken, boolean ignoreCertificateErrors) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        try {
            if (ignoreCertificateErrors && connection instanceof HttpsURLConnection https) {
                https.setSSLSocketFactory(trustAllSocketFactory());
                https.setHostnameVerifier((hostname, sslSession) -> true);
            }

            connection.setRequestMethod("POST");
            connection.setConnectTimeout(30_000);
            connection.setReadTimeout(120_000);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            if (authToken != null && !authToken.isBlank()) {
                connection.setRequestProperty("Authorization", authToken);
            }
            connection.setDoOutput(true);

            try (OutputStream out = connection.getOutputStream()) {
                if (body.length > 0) {
                    out.write(body);
                }
            }

            int status = connection.getResponseCode();
            String responseBody = readBody(status < 400 ? connection.getInputStream() : connection.getErrorStream());
            return new HttpResponse(status, responseBody);
        } finally {
            connection.disconnect();
        }
    }

    private static String readBody(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toString(StandardCharsets.UTF_8);
        }
    }

    private static SSLSocketFactory trustAllSocketFactory() throws Exception {
        SSLSocketFactory cached = trustAllSocketFactory;
        if (cached != null) {
            return cached;
        }
        synchronized (PvwaClient.class) {
            if (trustAllSocketFactory == null) {
                TrustManager[] trustAll = {
                        new X509TrustManager() {
                            @Override
                            public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                            }

                            @Override
                            public void checkClientTrusted(X509Certificate[] certs, String authType) {
                                // Trust-all is an explicit, user-selected option for self-signed PVWA certificates.
                            }

                            @Override
                            public void checkServerTrusted(X509Certificate[] certs, String authType) {
                                // Trust-all is an explicit, user-selected option for self-signed PVWA certificates.
                            }
                        }
                };
                SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, trustAll, new SecureRandom());
                trustAllSocketFactory = context.getSocketFactory();
            }
        }
        return trustAllSocketFactory;
    }

    // -------------------------------------------------------------------------------------- JSON helpers

    private static String normalizeBase(String base) {
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("PVWA address is required.");
        }
        String value = base.trim();
        if (!value.matches("(?i)^https?://.*")) {
            value = "https://" + value;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static String unwrapToken(String body) {
        if (body == null) {
            return null;
        }
        String token = body.trim();
        if (token.length() >= 2 && token.startsWith("\"") && token.endsWith("\"")) {
            token = token.substring(1, token.length() - 1);
        }
        return token.replace("\\/", "/");
    }

    private static String extractError(String body) {
        if (body == null || body.isBlank()) {
            return "no response body";
        }
        String marker = "\"ErrorMessage\"";
        int markerIndex = body.indexOf(marker);
        if (markerIndex >= 0) {
            int colon = body.indexOf(':', markerIndex + marker.length());
            if (colon >= 0) {
                int firstQuote = body.indexOf('"', colon + 1);
                int secondQuote = firstQuote < 0 ? -1 : body.indexOf('"', firstQuote + 1);
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    return body.substring(firstQuote + 1, secondQuote);
                }
            }
        }
        return body.length() > 300 ? body.substring(0, 300) : body;
    }

    private static String jsonString(String value) {
        if (value == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}