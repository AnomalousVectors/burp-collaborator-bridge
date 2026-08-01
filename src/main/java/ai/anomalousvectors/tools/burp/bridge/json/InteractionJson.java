package ai.anomalousvectors.tools.burp.bridge.json;

import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.DnsDetails;
import burp.api.montoya.collaborator.HttpDetails;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.SmtpDetails;
import burp.api.montoya.http.message.HttpHeader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Serializes Collaborator payloads and interactions to the bridge JSON shape.
 */
public final class InteractionJson {

    private InteractionJson() {
    }

    /**
     * Serializes a generated payload.
     *
     * @param p Collaborator payload
     * @return JSON object string
     */
    public static String payloadJson(CollaboratorPayload p) {
        StringBuilder sb = new StringBuilder(128);
        sb.append('{');
        JsonSupport.jsonField(sb, "payload", p.toString());
        sb.append(',');
        JsonSupport.jsonField(sb, "id", p.id().toString());
        p.customData().ifPresent(cd -> {
            sb.append(',');
            JsonSupport.jsonField(sb, "customData", cd);
        });
        p.server().ifPresent(loc -> {
            sb.append(',');
            JsonSupport.jsonField(sb, "serverLocation", loc.toString());
        });
        sb.append('}');
        return sb.toString();
    }

    /**
     * Serializes one interaction, including optional DNS/HTTP/SMTP detail blocks.
     *
     * @param i interaction
     * @param isNew whether this interaction arrived in the current poll
     * @return JSON object string
     */
    public static String interactionToJson(Interaction i, boolean isNew) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        JsonSupport.jsonField(sb, "id", i.id().toString());
        sb.append(',');
        JsonSupport.jsonField(sb, "type", i.type().name().toLowerCase(Locale.ROOT));
        sb.append(',');
        JsonSupport.jsonField(sb, "timestamp", i.timeStamp().toString());
        sb.append(',');
        JsonSupport.jsonField(sb, "clientIp", i.clientIp().getHostAddress());
        sb.append(',');
        sb.append("\"clientPort\":").append(i.clientPort());
        sb.append(',');
        JsonSupport.jsonField(sb, "new", isNew);

        i.customData().ifPresent(cd -> {
            sb.append(',');
            JsonSupport.jsonField(sb, "customData", cd);
        });

        sb.append(',');
        JsonSupport.jsonField(sb, "hasDns", i.dnsDetails().isPresent());
        sb.append(',');
        JsonSupport.jsonField(sb, "hasHttp", i.httpDetails().isPresent());
        sb.append(',');
        JsonSupport.jsonField(sb, "hasSmtp", i.smtpDetails().isPresent());

        i.dnsDetails().ifPresent(dd -> appendDns(sb, dd));
        i.httpDetails().ifPresent(hd -> appendHttp(sb, hd));
        i.smtpDetails().ifPresent(sd -> appendSmtp(sb, sd));

        sb.append('}');
        return sb.toString();
    }

    /**
     * Decodes a DNS QNAME from a raw DNS message (supports compression pointers).
     *
     * @param msg raw DNS message bytes
     * @return dotted name, or empty when undecodable
     */
    public static String decodeDnsQname(byte[] msg) {
        if (msg == null || msg.length < 12) {
            return "";
        }
        return readDnsName(msg, 12, new HashSet<>());
    }

    private static void appendDns(StringBuilder sb, DnsDetails dd) {
        sb.append(',');
        sb.append("\"dns\":{");
        JsonSupport.jsonField(sb, "queryType", dd.queryType().name());
        sb.append(',');
        byte[] raw = dd.query().getBytes();
        JsonSupport.jsonField(sb, "qname", decodeDnsQname(raw));
        sb.append(',');
        JsonSupport.jsonField(sb, "rawQueryBase64", base64(raw));
        sb.append('}');
    }

    private static void appendHttp(StringBuilder sb, HttpDetails hd) {
        sb.append(',');
        sb.append("\"http\":{");
        JsonSupport.jsonField(sb, "protocol", hd.protocol().name());

        burp.api.montoya.http.message.HttpRequestResponse rr = hd.requestResponse();
        burp.api.montoya.http.message.requests.HttpRequest req = rr.request();

        sb.append(',');
        appendServiceBlock(sb, req);
        sb.append(',');
        appendRequestBlock(sb, req);

        if (rr.hasResponse()) {
            sb.append(',');
            appendResponseBlock(sb, rr.response());
        }

        appendTimingBlock(sb, rr);

        sb.append('}');
    }

    private static void appendServiceBlock(StringBuilder sb, burp.api.montoya.http.message.requests.HttpRequest req) {
        sb.append("\"service\":{");
        burp.api.montoya.http.HttpService svc = req.httpService();
        JsonSupport.jsonField(sb, "host", svc.host());
        sb.append(',');
        sb.append("\"port\":").append(svc.port());
        sb.append(',');
        JsonSupport.jsonField(sb, "secure", svc.secure());
        sb.append('}');
    }

    private static void appendRequestBlock(StringBuilder sb, burp.api.montoya.http.message.requests.HttpRequest req) {
        sb.append("\"request\":{");
        JsonSupport.jsonField(sb, "method", req.method());
        sb.append(',');
        JsonSupport.jsonField(sb, "httpVersion", req.httpVersion());
        sb.append(',');
        JsonSupport.jsonField(sb, "path", req.path());
        sb.append(',');
        JsonSupport.jsonField(sb, "pathWithoutQuery", req.pathWithoutQuery());
        sb.append(',');
        JsonSupport.jsonField(sb, "query", req.query());
        sb.append(',');
        JsonSupport.jsonField(sb, "url", req.url());
        sb.append(',');
        appendHeadersBodyRaw(sb, req.headers(), req.body().getBytes(), req.toByteArray().getBytes());
        sb.append('}');
    }

    private static void appendResponseBlock(StringBuilder sb, burp.api.montoya.http.message.responses.HttpResponse resp) {
        sb.append("\"response\":{");
        sb.append("\"statusCode\":").append(resp.statusCode());
        sb.append(',');
        JsonSupport.jsonField(sb, "reasonPhrase", resp.reasonPhrase());
        sb.append(',');
        appendHeadersBodyRaw(sb, resp.headers(), resp.body().getBytes(), resp.toByteArray().getBytes());
        sb.append('}');
    }

    private static void appendTimingBlock(StringBuilder sb, burp.api.montoya.http.message.HttpRequestResponse rr) {
        try {
            rr.timingData().ifPresent(td -> {
                sb.append(',');
                sb.append("\"timing\":{");
                JsonSupport.jsonField(sb, "timeRequestSent", td.timeRequestSent().toString());
                sb.append(',');
                JsonSupport.jsonField(sb, "timeToFirstByte",
                        td.timeBetweenRequestSentAndStartOfResponse() == null ? ""
                                : td.timeBetweenRequestSentAndStartOfResponse().toString());
                sb.append(',');
                JsonSupport.jsonField(sb, "timeToLastByte",
                        td.timeBetweenRequestSentAndEndOfResponse() == null ? ""
                                : td.timeBetweenRequestSentAndEndOfResponse().toString());
                sb.append('}');
            });
        } catch (RuntimeException _) {
            // Some RR implementations may not expose timing data; ignore safely.
        }
    }

    private static void appendSmtp(StringBuilder sb, SmtpDetails sd) {
        sb.append(',');
        sb.append("\"smtp\":{");
        JsonSupport.jsonField(sb, "protocol", sd.protocol().name());
        sb.append(',');
        JsonSupport.jsonField(sb, "conversation", sd.conversation());
        sb.append('}');
    }

    private static void appendHeadersBodyRaw(StringBuilder sb, List<HttpHeader> headers, byte[] body, byte[] raw) {
        sb.append("\"headers\":").append(headersArray(headers));
        sb.append(',');
        JsonSupport.jsonField(sb, "bodyBase64", base64(body));
        sb.append(',');
        JsonSupport.jsonField(sb, "rawBase64", base64(raw));
    }

    private static String headersArray(List<HttpHeader> headers) {
        StringJoiner j = new StringJoiner(",", "[", "]");
        for (HttpHeader h : headers) {
            StringBuilder sb = new StringBuilder(64);
            sb.append('{');
            JsonSupport.jsonField(sb, "name", h.name());
            sb.append(',');
            JsonSupport.jsonField(sb, "value", h.value());
            sb.append('}');
            j.add(sb.toString());
        }
        return j.toString();
    }

    private static String base64(byte[] data) {
        return Base64.getEncoder().encodeToString(data == null ? new byte[0] : data);
    }

    private static String readDnsName(byte[] msg, int pos, Set<Integer> visited) {
        StringBuilder name = new StringBuilder();
        int current = pos;
        int safety = 0;

        while (current < msg.length && safety++ < 512) {
            int len = msg[current] & 0xFF;

            if (len == 0) {
                return name.toString();
            }

            if ((len & 0xC0) == 0xC0) {
                if (current + 1 >= msg.length) {
                    return name.toString();
                }
                int ptr = ((len & 0x3F) << 8) | (msg[current + 1] & 0xFF);
                if (ptr >= msg.length || !visited.add(ptr)) {
                    return name.toString();
                }
                String pointed = readDnsName(msg, ptr, visited);
                if (!pointed.isEmpty()) {
                    if (!name.isEmpty()) {
                        name.append('.');
                    }
                    name.append(pointed);
                }
                return name.toString();
            }

            int end = current + 1 + len;
            if (end > msg.length) {
                return name.toString();
            }
            if (!name.isEmpty()) {
                name.append('.');
            }
            name.append(new String(msg, current + 1, len, StandardCharsets.UTF_8));
            current = end;
        }
        return name.toString();
    }
}
