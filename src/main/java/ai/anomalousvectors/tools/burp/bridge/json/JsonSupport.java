package ai.anomalousvectors.tools.burp.bridge.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Small JSON string helpers used by the Collaborator bridge API.
 *
 * <p>Intentionally dependency-free so the same style can back a future Montoya RPC bridge.</p>
 */
public final class JsonSupport {

    private JsonSupport() {
    }

    /**
     * Builds {@code {"error":"&lt;code&gt;"}}.
     *
     * @param code error code token
     * @return JSON object string
     */
    public static String errorJson(String code) {
        return "{\"error\":\"" + escape(code) + "\"}";
    }

    /**
     * Appends a quoted string JSON field.
     *
     * @param sb destination
     * @param k field name
     * @param v field value; {@code null} becomes empty
     */
    public static void jsonField(StringBuilder sb, String k, String v) {
        sb.append('"').append(escape(k)).append("\":\"").append(escape(v == null ? "" : v)).append('"');
    }

    /**
     * Appends a boolean JSON field.
     *
     * @param sb destination
     * @param k field name
     * @param v field value
     */
    public static void jsonField(StringBuilder sb, String k, boolean v) {
        sb.append('"').append(escape(k)).append("\":").append(v ? "true" : "false");
    }

    /**
     * Escapes a string for inclusion in a JSON string literal (minimal subset).
     *
     * @param s raw string
     * @return escaped string
     */
    public static String escape(String s) {
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"' -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /**
     * Parses a flat JSON object ({@code { "k": "v", ... }}) with no nesting.
     *
     * @param body raw JSON object text; may be {@code null}
     * @return map of keys to string values (empty when invalid)
     */
    public static Map<String, String> parseJsonObjectFlat(String body) {
        String b = (body == null) ? "" : body.trim();
        if (b.length() < 2 || b.charAt(0) != '{' || b.charAt(b.length() - 1) != '}') {
            return Collections.emptyMap();
        }

        String inner = b.substring(1, b.length() - 1).trim();
        if (inner.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> parts = splitTopLevel(inner);
        Map<String, String> out = new HashMap<>();
        for (String p : parts) {
            int idx = p.indexOf(':');
            if (idx < 0) {
                continue;
            }
            String k = unquote(p.substring(0, idx).trim());
            String v = unquote(p.substring(idx + 1).trim());
            out.put(k, v);
        }
        return out;
    }

    /**
     * Null-safe trim that returns empty string for {@code null}.
     *
     * @param s raw string
     * @return trimmed string or empty
     */
    public static String trimToEmpty(String s) {
        return (s == null) ? "" : s.trim();
    }

    private static List<String> splitTopLevel(String s) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder token = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean quoteToggle = (c == '"' && (i == 0 || s.charAt(i - 1) != '\\'));
            if (quoteToggle) {
                depth ^= 1;
            } else if (c == ',' && depth == 0) {
                parts.add(token.toString());
                token.setLength(0);
            } else {
                token.append(c);
            }
        }
        parts.add(token.toString());
        return parts;
    }

    private static String unquote(String s) {
        if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"') {
            String inner = s.substring(1, s.length() - 1);
            return inner.replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }
}
