package ai.anomalousvectors.tools.burp.bridge.collaborator;

import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.CollaboratorPayload;
import burp.api.montoya.collaborator.Interaction;
import burp.api.montoya.collaborator.InteractionType;
import burp.api.montoya.collaborator.PayloadOption;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollaboratorBridgeServiceTest {

    @Mock
    private CollaboratorClient client;

    private CollaboratorBridgeService service;

    @BeforeEach
    void setUp() {
        service = new CollaboratorBridgeService();
    }

    @Test
    void createPayload_rejectsInvalidCustom() {
        assertThatThrownBy(() -> service.createPayload(client, "bad!", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid_custom");
    }

    @Test
    void createPayload_usesWithoutServerOption() {
        CollaboratorPayload payload = mock(CollaboratorPayload.class);
        when(client.generatePayload(eq("abc123"), eq(PayloadOption.WITHOUT_SERVER_LOCATION))).thenReturn(payload);

        assertThat(service.createPayload(client, "abc123", true)).isSameAs(payload);
        verify(client).generatePayload("abc123", PayloadOption.WITHOUT_SERVER_LOCATION);
    }

    @Test
    void createPayload_defaultGenerate() {
        CollaboratorPayload payload = mock(CollaboratorPayload.class);
        when(client.generatePayload()).thenReturn(payload);
        assertThat(service.createPayload(client, "", false)).isSameAs(payload);
    }

    @Test
    void parseInteractionQuery_marksInvalidSince() {
        CollaboratorBridgeService.InteractionQuery q =
                service.parseInteractionQuery(Map.of("since", "not-a-date"));
        assertThat(q.invalidSince()).isTrue();
    }

    @Test
    void pollAndRetain_andList_marksNew() throws Exception {
        Interaction first = mockInteraction("id1", ZonedDateTime.parse("2026-08-01T12:00:00Z"));
        when(client.getAllInteractions()).thenReturn(List.of(first));

        long seq = service.pollAndRetain(client);
        assertThat(seq).isEqualTo(1L);

        String json = service.listInteractionsJson(seq);
        assertThat(json).contains("\"id\":\"id1\"");
        assertThat(json).contains("\"new\":true");

        when(client.getAllInteractions()).thenReturn(List.of());
        long none = service.pollAndRetain(client);
        assertThat(none).isEqualTo(-1L);
        String retained = service.listInteractionsJson(none);
        assertThat(retained).contains("\"new\":false");
    }

    @Test
    void toPayloadJson_includesIdAndPayload() {
        burp.api.montoya.collaborator.InteractionId interactionId = mockId("abc");
        CollaboratorPayload payload = mock(CollaboratorPayload.class);
        when(payload.toString()).thenReturn("abc.oastify.com");
        when(payload.id()).thenReturn(interactionId);
        when(payload.customData()).thenReturn(Optional.empty());
        when(payload.server()).thenReturn(Optional.empty());

        assertThat(service.toPayloadJson(payload))
                .contains("\"payload\":\"abc.oastify.com\"")
                .contains("\"id\":\"abc\"");
    }

    private static Interaction mockInteraction(String id, ZonedDateTime ts) throws Exception {
        burp.api.montoya.collaborator.InteractionId interactionId = mockId(id);
        Interaction it = mock(Interaction.class);
        when(it.id()).thenReturn(interactionId);
        when(it.type()).thenReturn(InteractionType.DNS);
        when(it.timeStamp()).thenReturn(ts);
        when(it.clientIp()).thenReturn(InetAddress.getByName("127.0.0.1"));
        when(it.clientPort()).thenReturn(53);
        when(it.customData()).thenReturn(Optional.empty());
        when(it.dnsDetails()).thenReturn(Optional.empty());
        when(it.httpDetails()).thenReturn(Optional.empty());
        when(it.smtpDetails()).thenReturn(Optional.empty());
        return it;
    }

    private static burp.api.montoya.collaborator.InteractionId mockId(String value) {
        burp.api.montoya.collaborator.InteractionId id =
                mock(burp.api.montoya.collaborator.InteractionId.class);
        when(id.toString()).thenReturn(value);
        return id;
    }
}
