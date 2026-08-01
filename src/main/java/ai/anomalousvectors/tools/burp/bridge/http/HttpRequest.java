package ai.anomalousvectors.tools.burp.bridge.http;

import java.util.Map;

/**
 * Minimal HTTP/1.1 request parsed from a socket stream.
 *
 * @param method request method (upper-case)
 * @param path path without query string
 * @param version HTTP version token from the request line
 * @param headers lower-case header names to values
 * @param query decoded query parameters
 * @param body request body (empty when absent)
 */
public record HttpRequest(
        String method,
        String path,
        String version,
        Map<String, String> headers,
        Map<String, String> query,
        String body
) {
}
