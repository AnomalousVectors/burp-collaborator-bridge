package ai.anomalousvectors.tools.burp.bridge.http;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HttpRequestParserTest {

    @Test
    void splitRequestLine_upperCasesMethod() {
        String[] parts = HttpRequestParser.splitRequestLine("get /health HTTP/1.1");
        assertThat(parts).containsExactly("GET", "/health", "HTTP/1.1");
    }

    @Test
    void parseQuery_decodesPairs() {
        Map<String, String> q = HttpRequestParser.parseQuery("a=1&b=hello%20world");
        assertThat(q).containsEntry("a", "1").containsEntry("b", "hello world");
    }

    @Test
    void parse_getWithQuery() throws Exception {
        String raw = "GET /payload?custom=abc&without_server=1 HTTP/1.1\r\nHost: localhost\r\n\r\n";
        HttpRequest req = HttpRequestParser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(req).isNotNull();
        assertThat(req.method()).isEqualTo("GET");
        assertThat(req.path()).isEqualTo("/payload");
        assertThat(req.query()).containsEntry("custom", "abc").containsEntry("without_server", "1");
        assertThat(req.body()).isEmpty();
    }

    @Test
    void parse_postBody() throws Exception {
        String body = "{\"custom\":\"xyz\"}";
        String raw = "POST /payload HTTP/1.1\r\nContent-Length: " + body.length() + "\r\n\r\n" + body;
        HttpRequest req = HttpRequestParser.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        assertThat(req).isNotNull();
        assertThat(req.method()).isEqualTo("POST");
        assertThat(req.body()).isEqualTo(body);
    }

    @Test
    void writeJson_includesContentLengthAndTrailingLf() throws Exception {
        var out = new java.io.ByteArrayOutputStream();
        HttpJsonWriter.writeJson(out, 200, "{\"status\":\"ok\"}");
        String resp = out.toString(StandardCharsets.US_ASCII);
        assertThat(resp).startsWith("HTTP/1.1 200 OK\r\n");
        assertThat(resp).contains("Content-Type: application/json; charset=utf-8\r\n");
        assertThat(resp).contains("Content-Length: 16\r\n");
        assertThat(resp).endsWith("{\"status\":\"ok\"}\n");
    }
}
