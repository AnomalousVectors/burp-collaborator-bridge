package ai.anomalousvectors.tools.burp.bridge.http;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes compact HTTP/1.1 JSON responses with a trailing LF and correct {@code Content-Length}.
 */
public final class HttpJsonWriter {

    private static final String CONTENT_TYPE_JSON = "application/json; charset=utf-8";

    private HttpJsonWriter() {
    }

    /**
     * Writes a JSON body response and closes the logical exchange ({@code Connection: close}).
     *
     * @param out response stream
     * @param code HTTP status code
     * @param body JSON body; a trailing LF is added when missing
     * @throws IOException if the stream write fails
     */
    public static void writeJson(OutputStream out, int code, String body) throws IOException {
        String payloadBody = body.endsWith("\n") ? body : body + "\n";
        byte[] payload = payloadBody.getBytes(StandardCharsets.UTF_8);
        String headers =
                "HTTP/1.1 " + code + " " + reasonPhrase(code) + "\r\n"
                        + "Content-Type: " + CONTENT_TYPE_JSON + "\r\n"
                        + "Content-Length: " + payload.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    /**
     * Returns a short reason phrase for common bridge status codes.
     *
     * @param code HTTP status code
     * @return reason phrase
     */
    public static String reasonPhrase(int code) {
        return switch (code) {
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 500 -> "Internal Server Error";
            case 503 -> "Service Unavailable";
            default -> "OK";
        };
    }
}
