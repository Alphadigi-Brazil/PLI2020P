package br.com.alphadigi.pli2020p;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression suite built from frames captured on a working installation and from the
 * examples of the vendor protocol document (v1.05).
 */
class Pli2020pEncoderTest {

    /** Frames known to work in the field, hex encoded. */
    private static final String[] FIELD_FRAMES = {
            // 0x62 "ABC1234" on the third line
            "00 64 FF FF 62 1A 02 00 01 00 05 00 01 03 00 FF 00 00 00 00 00 00 00 07 00 "
                    + "41 42 43 31 32 33 34 10 9F",
            // 0x05 clock synchronisation
            "00 64 FF FF 05 08 E5 07 02 07 01 0F 36 2D DD 20",
            // 0x62 "Bemvindo" on the third line, amber, scrolling
            "00 64 FF FF 62 1B 02 15 01 00 05 15 01 03 00 FF FF 00 00 00 00 00 00 08 00 "
                    + "42 65 6D 76 69 6E 64 6F 8A 3D",
            // 0x62 "Brasil Terminal Portuario" on the fourth line (UTF-8 "á" = C3 A1)
            "00 64 FF FF 62 2D 03 15 01 00 00 15 01 03 00 FF FF 00 00 00 00 00 00 1A 00 "
                    + "42 72 61 73 69 6C 20 54 65 72 6D 69 6E 61 6C 20 50 6F 72 74 75 C3 A1 72 69 6F 16 66",
            // 0x6E four lines plus a voice announcement
            "00 64 FF FF 6E 69 00 04 00 00 01 05 00 FF 00 00 00 08 60 48 3A 60 4E 3A 60 53 0D "
                    + "01 00 01 05 00 FF 00 00 00 08 60 44 2F 60 4D 2F 60 59 0D "
                    + "02 00 01 05 00 FF 00 00 00 08 41 42 43 2D 31 32 33 34 0D "
                    + "03 15 01 05 00 FF 00 00 00 10 53 69 67 61 20 65 6D 20 66 72 65 6E 74 65 21 20 00 "
                    + "0A 10 53 69 67 61 20 65 6D 20 66 72 65 6E 74 65 21 20 00 CC 30",
            // 0x0F green light for 5 s
            "00 64 FF FF 0F 06 00 01 00 00 00 05 24 B7",
            // 0x62 clock on the first line
            "00 64 FF FF 62 1B 00 00 01 00 05 00 01 03 00 FF 00 00 00 00 00 00 00 08 00 "
                    + "60 48 3A 60 4E 3A 60 53 7A E7"
    };

    @Test
    void crcMatchesEveryFrameCapturedInTheField() {
        for (int i = 0; i < FIELD_FRAMES.length; i++) {
            byte[] frame = hex(FIELD_FRAMES[i]);
            int carried = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
            int computed = Pli2020pEncoder.crc16(frame, 0, frame.length - 2);
            assertEquals(carried, computed, "frame #" + (i + 1));
        }
    }

    @Test
    void reproducesCapturedSingleLineFrame() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(DisplayLine.scrolling(2, "Bemvindo", PliColor.YELLOW))
                .strategy(PliDisplayRequest.Strategy.PER_LINE_PACKETS)
                .build());

        assertEquals(1, frames.size());
        assertArrayEquals(hex(FIELD_FRAMES[2]), frames.get(0).getBytes());
    }

    @Test
    void reproducesVendorGbKExample() {
        DisplayLine line = new DisplayLine(0, "欢迎光临请入场停车", PliColor.RED,
                DisplayMode.CONTINUOUS_LEFT, 1, 2, 0);
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(line)
                .textEncoding(TextEncoding.GBK)
                .strategy(PliDisplayRequest.Strategy.PER_LINE_PACKETS)
                .build());

        byte[] expected = hex("00 64 FF FF 62 25 00 15 01 00 02 15 01 03 00 FF 00 00 00 "
                + "00 00 00 00 12 00 BB B6 D3 AD B9 E2 C1 D9 C7 EB C8 EB B3 A1 CD A3 "
                + "B3 B5 F4 F5");
        assertEquals(1, frames.size());
        assertArrayEquals(expected, frames.get(0).getBytes());
    }

    @Test
    void reproducesCapturedTrafficLightFrame() {
        assertArrayEquals(hex(FIELD_FRAMES[5]), Pli2020pEncoder.relayFrame(0x00, 0x64, 5));
    }

    @Test
    void reproducesCapturedFourLinesWithVoiceFrame() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(DisplayLine.of(0, "`H:`N:`S", PliColor.RED))
                .line(DisplayLine.of(1, "`D/`M/`Y", PliColor.RED))
                .line(DisplayLine.of(2, "ABC-1234", PliColor.RED))
                .line(DisplayLine.scrolling(3, "Siga em frente! ", PliColor.RED))
                .voice("Siga em frente! ")
                .build());

        assertEquals(1, frames.size());
        assertArrayEquals(hex(FIELD_FRAMES[4]), frames.get(0).getBytes());
    }

    @Test
    void dataLenIsTheDecodedBase64Length() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, "Vaga 999", PliColor.GREEN)
                .line(1, "Bem-vindo!", PliColor.GREEN)
                .build());

        for (SerialData frame : frames) {
            assertEquals(Base64.getDecoder().decode(frame.getData()).length, frame.getDataLen());
            assertEquals(frame.getBytes().length, frame.getDataLen());
        }
    }

    @Test
    void base64IsAFaithfulEncodingOfTheFrame() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, "Ação", PliColor.RED)
                .line(1, "光临", PliColor.GREEN)
                .greenLightSeconds(3)
                .build());

        for (SerialData frame : frames) {
            assertArrayEquals(frame.getBytes(), Base64.getDecoder().decode(frame.getData()));
        }
    }
    @Test
    void everyFrameCanBeParsedBack() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, "A", PliColor.RED)
                .line(1, "B", PliColor.GREEN)
                .line(2, "C", PliColor.YELLOW)
                .line(3, "D", PliColor.OFF)
                .greenLightSeconds(7)
                .build());

        assertEquals(2, frames.size());
        assertEquals(Pli2020pEncoder.CMD_MULTI_LINE,
                PliFrameParser.parse(frames.get(0).getBytes()).getCommand());
        PliFrameParser.Frame relay = PliFrameParser.parse(frames.get(1).getBytes());
        assertEquals(Pli2020pEncoder.CMD_RELAY, relay.getCommand());
        assertEquals(7, relay.getData()[5] & 0xFF);
    }

    @Test
    void autoUsesOnePacketForAFullFourLineRefresh() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, "1", PliColor.RED)
                .line(1, "2", PliColor.RED)
                .line(2, "3", PliColor.RED)
                .line(3, "4", PliColor.RED)
                .build());

        assertEquals(1, frames.size());
        assertEquals(Pli2020pEncoder.CMD_MULTI_LINE, frames.get(0).getBytes()[4] & 0xFF);
    }

    @Test
    void autoUsesOnePacketPerLineForPartialUpdates() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(2, "Vaga 999", PliColor.YELLOW)
                .line(3, "Bem-vindo!", PliColor.GREEN)
                .build());

        assertEquals(2, frames.size());
        assertEquals(Pli2020pEncoder.CMD_TEMP_TEXT, frames.get(0).getBytes()[4] & 0xFF);
        assertEquals(Pli2020pEncoder.CMD_TEMP_TEXT, frames.get(1).getBytes()[4] & 0xFF);
    }

    @Test
    void longLinesFallBackToPerLinePackets() {
        List<SerialData> frames = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, repeat('A', 33), PliColor.RED)
                .line(1, repeat('B', 33), PliColor.RED)
                .line(2, repeat('C', 33), PliColor.RED)
                .line(3, repeat('D', 33), PliColor.RED)
                .build());

        assertEquals(4, frames.size());
        for (SerialData frame : frames) {
            assertEquals(Pli2020pEncoder.CMD_TEMP_TEXT, frame.getBytes()[4] & 0xFF);
        }
        // 19 header bytes + 33 text bytes of payload, plus 8 bytes of frame overhead
        assertEquals(60, frames.get(0).getDataLen());
    }

    @Test
    void rejectsTextThatCannotFitAnyPacket() {
        PliDisplayRequest request = PliDisplayRequest.builder()
                .line(0, repeat('X', 250), PliColor.RED)
                .build();

        PliProtocolException error = assertThrows(PliProtocolException.class,
                () -> Pli2020pEncoder.encode(request));
        assertTrue(error.getMessage().contains("max"));
    }

    @Test
    void greenLightOnlyProducesASingleFrame() {
        List<SerialData> frames = Pli2020pEncoder.encode(
                PliDisplayRequest.builder().greenLightSeconds(10).build());

        assertEquals(1, frames.size());
        assertArrayEquals(hex("00 64 FF FF 0F 06 00 01 00 00 00 0A 64 B3"), frames.get(0).getBytes());
    }

    @Test
    void utf8IsDefaultAndGbkIsSelectable() {
        assertEquals(6, "光临".getBytes(TextEncoding.UTF8.getCharset()).length);
        byte[] gbk = "光临".getBytes(TextEncoding.GBK.getCharset());
        assertEquals(4, gbk.length);

        byte[] frame = Pli2020pEncoder.encode(PliDisplayRequest.builder()
                .line(0, "光临", PliColor.GREEN)
                .textEncoding(TextEncoding.GBK)
                .strategy(PliDisplayRequest.Strategy.MULTI_LINE_PACKET)
                .build()).get(0).getBytes();

        assertEquals(gbk.length, frame[17] & 0xFF);
        assertArrayEquals(gbk, java.util.Arrays.copyOfRange(frame, 18, 18 + gbk.length));
    }

    /** The exact request from the README / acceptance test. */
    @Test
    void producesTheDocumentedAcceptanceOutput() {
        PliDisplayRequest request = PliDisplayRequest.builder()
                .line(0, "`D-`M-20`Y", PliColor.GREEN)
                .line(1, "`H:`N:`S", PliColor.GREEN)
                .line(2, "Vaga 999", PliColor.YELLOW)
                .line(3, "Bem-vindo!", PliColor.GREEN)
                .greenLightSeconds(5)
                .build();

        List<SerialData> frames = Pli2020pEncoder.encode(request);

        assertEquals(2, frames.size());
        assertEquals("AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2Eg"
                        + "OTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o",
                frames.get(0).getData());
        assertEquals(90, frames.get(0).getDataLen());
        assertEquals("AGT//w8GAAEAAAAFJLc=", frames.get(1).getData());
        assertEquals(14, frames.get(1).getDataLen());
    }

    // ------------------------------------------------------------------ helpers

    static byte[] hex(String text) {
        String[] parts = text.trim().split("\\s+");
        byte[] out = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return out;
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
