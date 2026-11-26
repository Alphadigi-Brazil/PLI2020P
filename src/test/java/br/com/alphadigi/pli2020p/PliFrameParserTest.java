package br.com.alphadigi.pli2020p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PliFrameParserTest {

    private static final byte[] VALID = Pli2020pEncoder.relayFrame(0x00, 0x64, 5);

    @Test
    void parsesAValidFrame() {
        PliFrameParser.Frame frame = PliFrameParser.parse(VALID);

        assertEquals(0x00, frame.getAddress());
        assertEquals(0x64, frame.getProtocolVersion());
        assertEquals(Pli2020pEncoder.CMD_RELAY, frame.getCommand());
        assertArrayEquals(new byte[] {0x00, 0x01, 0x00, 0x00, 0x00, 0x05}, frame.getData());
    }

    @Test
    void rejectsACorruptedCrc() {
        byte[] corrupted = VALID.clone();
        corrupted[corrupted.length - 1] ^= 0xFF;

        PliProtocolException error = assertThrows(PliProtocolException.class,
                () -> PliFrameParser.parse(corrupted));
        assertTrue(error.getMessage().contains("CRC"));
    }

    @Test
    void rejectsATruncatedFrame() {
        byte[] truncated = new byte[VALID.length - 3];
        System.arraycopy(VALID, 0, truncated, 0, truncated.length);

        assertThrows(PliProtocolException.class, () -> PliFrameParser.parse(truncated));
    }
}
