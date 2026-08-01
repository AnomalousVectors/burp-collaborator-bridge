package ai.anomalousvectors.tools.burp.bridge.json;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InteractionJsonTest {

    @Test
    void decodeDnsQname_readsLabels() {
        byte[] msg = new byte[12 + 1 + 2 + 1 + 2 + 1];
        int i = 12;
        msg[i++] = 2;
        msg[i++] = 'a';
        msg[i++] = 'b';
        msg[i++] = 2;
        msg[i++] = 'c';
        msg[i++] = 'd';
        msg[i] = 0;

        assertThat(InteractionJson.decodeDnsQname(msg)).isEqualTo("ab.cd");
    }

    @Test
    void decodeDnsQname_emptyWhenTooShort() {
        assertThat(InteractionJson.decodeDnsQname(new byte[4])).isEmpty();
        assertThat(InteractionJson.decodeDnsQname(null)).isEmpty();
    }

    @Test
    void decodeDnsQname_followsCompressionPointer() {
        byte[] msg = new byte[12 + 2 + 1 + 1 + 1];
        msg[12] = (byte) 0xC0;
        msg[13] = 14;
        msg[14] = 1;
        msg[15] = 'x';
        msg[16] = 0;
        assertThat(InteractionJson.decodeDnsQname(msg)).isEqualTo("x");
    }
}
