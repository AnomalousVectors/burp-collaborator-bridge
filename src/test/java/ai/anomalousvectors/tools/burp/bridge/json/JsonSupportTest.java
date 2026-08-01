package ai.anomalousvectors.tools.burp.bridge.json;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonSupportTest {

    @Test
    void escape_handlesControlAndQuotes() {
        assertThat(JsonSupport.escape("a\"b\\c\n\r\t")).isEqualTo("a\\\"b\\\\c\\n\\r\\t");
    }

    @Test
    void errorJson_wrapsCode() {
        assertThat(JsonSupport.errorJson("not_found")).isEqualTo("{\"error\":\"not_found\"}");
    }

    @Test
    void parseJsonObjectFlat_readsSimpleObject() {
        Map<String, String> m = JsonSupport.parseJsonObjectFlat("{\"custom\":\"abc\",\"without_server\":\"1\"}");
        assertThat(m).containsEntry("custom", "abc").containsEntry("without_server", "1");
    }

    @Test
    void parseJsonObjectFlat_invalidReturnsEmpty() {
        assertThat(JsonSupport.parseJsonObjectFlat("not-json")).isEmpty();
        assertThat(JsonSupport.parseJsonObjectFlat(null)).isEmpty();
    }

    @Test
    void trimToEmpty_nullSafe() {
        assertThat(JsonSupport.trimToEmpty(null)).isEmpty();
        assertThat(JsonSupport.trimToEmpty("  x ")).isEqualTo("x");
    }
}
