package ai.anomalousvectors.tools.burp.bridge.http;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Parses a single HTTP/1.1 request from a blocking {@link InputStream}.
 *
 * <p>Bounds start-line, headers, and body size to keep the local bridge resilient to abuse.</p>
 */
public final class HttpRequestParser {

    private static final int MAX_START_LINE = 8192;
    private static final int MAX_HEADER_LINE = 8192;
    private static final int MAX_HEADERS = 200;
    private static final int MAX_BODY = 1_000_000;

    private HttpRequestParser() {
    }

    /**
     * Parses one request.
     *
     * @param in request input stream
     * @return parsed request, or {@code null} when the stream is empty/malformed/truncated
     * @throws IOException if reading the stream fails
     */
    public static HttpRequest parse(InputStream in) throws IOException {
        String start = readLine(in, MAX_START_LINE);
        if (start == null || start.isEmpty()) {
            return null;
        }

        String[] parts = splitRequestLine(start);
        if (parts == null) {
            return null;
        }

        String method = parts[0];
        String uri = parts[1];
        String version = parts[2];

        Map<String, String> headers = readHeaders(in);
        if (headers == null) {
            return null;
        }

        String path = uri;
        String rawQuery = null;
        int qIdx = uri.indexOf('?');
        if (qIdx >= 0) {
            path = uri.substring(0, qIdx);
            rawQuery = uri.substring(qIdx + 1);
        }

        int contentLen = 0;
        if ("POST".equals(method) || "PUT".equals(method)) {
            String cl = headers.get("content-length");
            if (cl != null) {
                try {
                    contentLen = Integer.parseInt(cl.trim());
                } catch (NumberFormatException _) {
                    // ignore invalid content-length
                }
            }
            if (contentLen < 0 || contentLen > MAX_BODY) {
                return new HttpRequest(method, path, version, headers, parseQuery(rawQuery), "");
            }
        }

        String body = "";
        if (contentLen > 0) {
            byte[] buf = in.readNBytes(contentLen);
            body = new String(buf, StandardCharsets.UTF_8);
        }

        return new HttpRequest(method, path, version, headers, parseQuery(rawQuery), body);
    }

    /**
     * Splits a request line into method, URI, and version.
     *
     * @param start request line
     * @return three parts, or {@code null} when malformed
     */
    public static String[] splitRequestLine(String start) {
        String[] parts = start.split(" ", 3);
        return (parts.length < 3)
                ? null
                : new String[] {parts[0].toUpperCase(Locale.ROOT), parts[1], parts[2]};
    }

    /**
     * Parses an {@code application/x-www-form-urlencoded} query string.
     *
     * @param raw raw query without leading {@code ?}; may be {@code null}
     * @return immutable-empty or mutable map of decoded parameters
     */
    public static Map<String, String> parseQuery(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> m = new HashMap<>();
        for (String pair : raw.split("&")) {
            int idx = pair.indexOf('=');
            String k = (idx >= 0) ? pair.substring(0, idx) : pair;
            String v = (idx >= 0) ? pair.substring(idx + 1) : "";
            m.put(urlDecode(k), urlDecode(v));
        }
        return m;
    }

    private static Map<String, String> readHeaders(InputStream in) throws IOException {
        Map<String, String> headers = new HashMap<>();
        for (int i = 0; i < MAX_HEADERS; i++) {
            String line = readLine(in, MAX_HEADER_LINE);
            if (line == null) {
                return null;
            }
            if (line.isEmpty()) {
                break;
            }
            int idx = line.indexOf(':');
            if (idx > 0) {
                String k = line.substring(0, idx).trim().toLowerCase(Locale.ROOT);
                String v = line.substring(idx + 1).trim();
                headers.put(k, v);
            }
        }
        return headers;
    }

    private static String readLine(InputStream in, int maxLen) throws IOException {
        StringBuilder sb = new StringBuilder(80);
        int prev = -1;
        for (int i = 0; i < maxLen; i++) {
            int b = in.read();
            if (b == -1) {
                break;
            }
            if (b == '\n') {
                int end = sb.length();
                if (end > 0 && prev == '\r') {
                    sb.setLength(end - 1);
                }
                return sb.toString();
            }
            sb.append((char) b);
            prev = b;
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException _) {
            return s;
        }
    }
}
