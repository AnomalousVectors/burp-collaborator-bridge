package ai.anomalousvectors.tools.burp.bridge.collaborator;

import ai.anomalousvectors.tools.burp.bridge.json.InteractionJson;
import ai.anomalousvectors.tools.burp.bridge.json.JsonSupport;
import ai.anomalousvectors.tools.burp.utils.Logger;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.PayloadOption;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Collaborator operations and append-only interaction retention for the HTTP bridge.
 *
 * <p>Transport-agnostic: callers supply a {@link CollaboratorClient} and consume JSON strings.</p>
 */
public final class CollaboratorBridgeService {

    private final Object cacheLock = new Object();
    private final Map<String, Cached> cache = new HashMap<>();
    private long pollSeq;

    /** Retention entry pairing an interaction with the poll sequence that first observed it. */
    private record Cached(Interaction it, long seq) {
    }

    /**
     * Parsed {@code /interactions} query parameters.
     *
     * <p>Filters are currently validated only ({@code invalidSince}); output remains unfiltered.</p>
     */
    public record InteractionQuery(
            String byPayload,
            String byId,
            Set<String> typeWhitelist,
            ZonedDateTime since,
            Integer limit,
            boolean invalidSince
    ) {
    }

    /**
     * Creates a Collaborator payload from optional custom data / without-server flags.
     *
     * @param client live Collaborator client
     * @param custom optional custom data (alnum, length 1..16)
     * @param withoutServer when {@code true}, omit server location from the payload
     * @return generated payload
     * @throws IllegalArgumentException when {@code custom} is non-empty but invalid
     */
    public CollaboratorPayload createPayload(CollaboratorClient client, String custom, boolean withoutServer) {
        final String customValue = JsonSupport.trimToEmpty(custom);
        if (!customValue.isEmpty()) {
            if (!customValue.matches("^[A-Za-z0-9]{1,16}$")) {
                throw new IllegalArgumentException("invalid_custom");
            }
            return withoutServer
                    ? client.generatePayload(customValue, PayloadOption.WITHOUT_SERVER_LOCATION)
                    : client.generatePayload(customValue);
        }
        return withoutServer
                ? client.generatePayload(PayloadOption.WITHOUT_SERVER_LOCATION)
                : client.generatePayload();
    }

    /**
     * Serializes a payload to bridge JSON.
     *
     * @param payload Collaborator payload
     * @return JSON object
     */
    public String toPayloadJson(CollaboratorPayload payload) {
        return InteractionJson.payloadJson(payload);
    }

    /**
     * Parses and validates {@code /interactions} query parameters.
     *
     * @param qRaw query map; may be {@code null}
     * @return parsed query descriptor
     */
    public InteractionQuery parseInteractionQuery(Map<String, String> qRaw) {
        Map<String, String> q = (qRaw == null) ? Collections.emptyMap() : qRaw;

        String byPayload = JsonSupport.trimToEmpty(q.get("payload"));
        String byId = JsonSupport.trimToEmpty(q.get("id"));

        String typesCsv = JsonSupport.trimToEmpty(q.get("types")).toLowerCase(Locale.ROOT);
        Set<String> typeWhitelist = parseTypes(typesCsv);

        String sinceRaw = JsonSupport.trimToEmpty(q.get("since"));
        ZonedDateTime since = null;
        boolean bad = false;
        if (!sinceRaw.isEmpty()) {
            since = parseSince(sinceRaw);
            bad = (since == null);
        }

        Integer limit = parsePositiveInt(JsonSupport.trimToEmpty(q.get("limit")));
        return new InteractionQuery(byPayload, byId, typeWhitelist, since, limit, bad);
    }

    /**
     * Polls Collaborator once and appends any fresh interactions to retention.
     *
     * @param client live Collaborator client
     * @return sequence used to mark {@code "new"} items for this request, or {@code -1} when none arrived
     */
    public long pollAndRetain(CollaboratorClient client) {
        List<Interaction> fresh;
        try {
            fresh = client.getAllInteractions();
        } catch (RuntimeException e) {
            Logger.logError("Collaborator poll failed: " + e.getMessage());
            return -1;
        }
        if (fresh == null || fresh.isEmpty()) {
            return -1;
        }

        long seq;
        synchronized (cacheLock) {
            seq = ++pollSeq;
            for (Interaction it : fresh) {
                cache.putIfAbsent(keyOf(it), new Cached(it, seq));
            }
        }
        return seq;
    }

    /**
     * Builds JSON for all retained interactions, tagging those first seen in {@code currentSeq}.
     *
     * @param currentSeq sequence from {@link #pollAndRetain(CollaboratorClient)}, or {@code -1}
     * @return JSON array string (newest first)
     */
    public String listInteractionsJson(long currentSeq) {
        List<Cached> snapshot;
        synchronized (cacheLock) {
            snapshot = new ArrayList<>(cache.values());
        }

        snapshot.sort(Comparator.comparing((Cached c) -> c.it.timeStamp()).reversed());

        StringJoiner j = new StringJoiner(",", "[", "]");
        for (Cached c : snapshot) {
            boolean isNew = (currentSeq != -1) && (c.seq == currentSeq);
            j.add(InteractionJson.interactionToJson(c.it, isNew));
        }
        return j.toString();
    }

    /**
     * Clears retained interactions (primarily for tests).
     */
    public void clearRetention() {
        synchronized (cacheLock) {
            cache.clear();
            pollSeq = 0L;
        }
    }

    private static String keyOf(Interaction it) {
        return it.id() + "|" + it.type().name() + "|" + it.timeStamp() + "|"
                + it.clientIp().getHostAddress() + ":" + it.clientPort();
    }

    private static Integer parsePositiveInt(String s) {
        if (s.isEmpty()) {
            return null;
        }
        try {
            int v = Integer.parseInt(s);
            return (v > 0) ? v : null;
        } catch (NumberFormatException _) {
            return null;
        }
    }

    private static Set<String> parseTypes(String csv) {
        if (csv.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> out = new HashSet<>();
        for (String t : csv.split(",")) {
            String v = t.trim();
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static ZonedDateTime parseSince(String raw) {
        try {
            if (raw.matches("^\\d{10,}$")) {
                long ms = Long.parseLong(raw);
                if (raw.length() == 10) {
                    ms *= 1000L;
                }
                return ZonedDateTime.ofInstant(Instant.ofEpochMilli(ms), ZonedDateTime.now().getZone());
            }
            return ZonedDateTime.parse(raw);
        } catch (DateTimeParseException | NumberFormatException _) {
            return null;
        }
    }
}
