package br.com.alphadigi.pli2020p;

/**
 * Minimal frame parser, mostly useful for diagnostics: validates the CRC of a frame
 * captured on the RS485 bus and exposes its command and data field.
 */
public final class PliFrameParser {

    private PliFrameParser() {
    }

    /** A validated frame. */
    public static final class Frame {
        private final int address;
        private final int protocolVersion;
        private final int command;
        private final byte[] data;

        Frame(int address, int protocolVersion, int command, byte[] data) {
            this.address = address;
            this.protocolVersion = protocolVersion;
            this.command = command;
            this.data = data;
        }

        public int getAddress() {
            return address;
        }

        public int getProtocolVersion() {
            return protocolVersion;
        }

        public int getCommand() {
            return command;
        }

        public byte[] getData() {
            return data.clone();
        }
    }

    /**
     * Parses and validates a frame.
     *
     * @throws PliProtocolException when the frame is truncated or the CRC does not match
     */
    public static Frame parse(byte[] frame) {
        if (frame == null || frame.length < 8) {
            throw new PliProtocolException("frame too short: "
                    + (frame == null ? "null" : frame.length + " bytes"));
        }
        int version = frame[1] & 0xFF;
        int dlSize = version == 0xC8 ? 2 : 1;
        int header = 5 + dlSize;
        if (frame.length < header + 2) {
            throw new PliProtocolException("frame too short for DL field");
        }
        int dataLength = frame[5] & 0xFF;
        if (dlSize == 2) {
            dataLength |= (frame[6] & 0xFF) << 8;
        }
        if (frame.length != header + dataLength + 2) {
            throw new PliProtocolException("length mismatch: frame has " + frame.length
                    + " bytes, DL says " + dataLength);
        }
        int expected = Pli2020pEncoder.crc16(frame, 0, frame.length - 2);
        int actual = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
        if (expected != actual) {
            throw new PliProtocolException(String.format(
                    "CRC mismatch: computed %04X, frame carries %04X", expected, actual));
        }
        byte[] data = new byte[dataLength];
        System.arraycopy(frame, header, data, 0, dataLength);
        return new Frame(frame[0] & 0xFF, version, frame[4] & 0xFF, data);
    }
}
