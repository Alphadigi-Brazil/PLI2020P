package br.com.alphadigi.pli2020p;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Encoder for the PLI2020P LED display controller (64x64 red/green dual-colour panel,
 * up to 4 text lines, RS485 input).
 *
 * <p>It produces exactly what the LPR camera puts on the wire, already Base64 encoded and
 * with the {@code dataLen} the camera reports:</p>
 *
 * <pre>
 * List&lt;SerialData&gt; frames = Pli2020pEncoder.encode(request);
 * frames.get(0).getData();    // "AGT//25pAAQ..."
 * frames.get(0).getDataLen(); // 94
 * </pre>
 *
 * <p>Frame layout (protocol v1.00, all multi-byte fields little-endian):</p>
 * <pre>
 * DA | VR | PN[2] | CMD | DL | DATA... | CRC[2]
 * </pre>
 * <p>CRC is CRC-16/MODBUS (poly 0xA001, init 0xFFFF) over DA..last DATA byte, low byte
 * first.</p>
 *
 * <p>Two text commands are emitted:</p>
 * <ul>
 *   <li>{@code 0x6E} - one packet carrying all 4 lines, each with its own colour
 *       (LID, mode, speed, dwell, repeats, RGBA, length, text). This is what the
 *       reference "4 lines" examples from the parking platform use.</li>
 *   <li>{@code 0x62} - one packet per line, used as a fallback when a line is too long for
 *       the single-packet form.</li>
 * </ul>
 * <p>{@code 0x0F} switches the traffic light to green for N seconds (the controller closes
 * the relay by itself when the time expires), which is the second {@code serialData}
 * element in the platform examples.</p>
 */
public final class Pli2020pEncoder {

    /** Download temporary text (one window / line). */
    public static final int CMD_TEMP_TEXT = 0x62;
    /** Single packet, multi line text (with optional voice). */
    public static final int CMD_MULTI_LINE = 0x6E;
    /** Relay / traffic-light board, on for N seconds. */
    public static final int CMD_RELAY = 0x0F;

    /** PN value meaning "single, last packet". */
    private static final int PN_SINGLE = 0xFFFF;

    /** 0x6E: maximum text bytes per line documented by the vendor. */
    private static final int MULTI_LINE_MAX_TEXT = 32;

    /** 0x62: 19 fixed bytes (TWID..TL) plus the text. */
    private static final int TEMP_TEXT_HEADER = 19;

    /**
     * 0x0F payload prefix observed in every working platform frame
     * ({@code 00 01 00 00 00} instead of the five zero bytes the document mentions).
     */
    private static final byte[] RELAY_PREFIX = {0x00, 0x01, 0x00, 0x00, 0x00};

    private Pli2020pEncoder() {
    }

    /**
     * Encodes the request into the {@code serialData} elements the camera must send, in
     * order: first the text packet(s), then, if requested, the green-light pulse.
     */
    public static List<SerialData> encode(PliDisplayRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        List<SerialData> frames = new ArrayList<SerialData>();
        List<DisplayLine> lines = request.getLines();

        if (!lines.isEmpty()) {
            byte[] multiLine = null;
            if (useMultiLinePacket(request)) {
                multiLine = buildMultiLineData(request);
            }
            if (multiLine != null
                    && multiLine.length <= maxDataLength(request.getProtocolVersion())) {
                frames.add(new SerialData(request.getSerialChannel(),
                        packet(request.getAddress(), request.getProtocolVersion(),
                                CMD_MULTI_LINE, multiLine)));
            } else {
                if (request.getStrategy() == PliDisplayRequest.Strategy.MULTI_LINE_PACKET) {
                    throw new PliProtocolException(
                            "the lines do not fit into one 0x6E packet (max "
                                    + maxDataLength(request.getProtocolVersion()) + " data bytes, "
                                    + MULTI_LINE_MAX_TEXT + " text bytes per line)");
                }
                if (request.getVoiceText() != null && !request.getVoiceText().isEmpty()) {
                    throw new PliProtocolException(
                            "voice announcements are only supported by the 0x6E packet; "
                                    + "shorten the lines or drop the voice text");
                }
                frames.addAll(encodePerLine(request));
            }
        }

        if (request.getGreenLightSeconds() > 0) {
            frames.add(new SerialData(request.getSerialChannel(),
                    relayFrame(request.getAddress(), request.getProtocolVersion(),
                            request.getGreenLightSeconds())));
        }
        return frames;
    }

    /**
     * AUTO sends the whole 4-line block as one 0x6E packet (that is what the reference
     * platform does for full-screen updates) and uses one 0x62 packet per line for partial
     * updates, again matching the captured traffic. A voice announcement forces 0x6E.
     */
    private static boolean useMultiLinePacket(PliDisplayRequest request) {
        switch (request.getStrategy()) {
            case MULTI_LINE_PACKET:
                return true;
            case PER_LINE_PACKETS:
                return false;
            default:
                String voice = request.getVoiceText();
                boolean hasVoice = voice != null && !voice.isEmpty();
                return request.getLines().size() == 4 || hasVoice;
        }
    }

    /** Builds the 0x0F frame: green light on for {@code seconds} (1..255). */
    public static byte[] relayFrame(int address, int protocolVersion, int seconds) {
        if (seconds < 1 || seconds > 255) {
            throw new IllegalArgumentException("seconds must be 1..255, got " + seconds);
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(RELAY_PREFIX, 0, RELAY_PREFIX.length);
        data.write(seconds & 0xFF);
        return packet(address, protocolVersion, CMD_RELAY, data.toByteArray());
    }

    /**
     * Builds the 0x6E data field, or {@code null} when the lines cannot be expressed in a
     * single packet (too long) - the caller then falls back to 0x62.
     */
    private static byte[] buildMultiLineData(PliDisplayRequest request) {
        List<DisplayLine> lines = request.getLines();
        if (lines.size() > 4) {
            return null;
        }
        boolean strict = request.getStrategy() == PliDisplayRequest.Strategy.AUTO;
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(request.isSaveToFlash() ? 0x01 : 0x00); // SAVE_FLAG
        data.write(lines.size());                          // TEXT_CONTEXT_NUMBER

        for (int i = 0; i < lines.size(); i++) {
            DisplayLine line = lines.get(i);
            byte[] text = line.getTextBytes(request.getCharset());
            if (strict && text.length > MULTI_LINE_MAX_TEXT) {
                return null;
            }
            if (text.length > 0xFF) {
                // TL is a single byte in 0x6E; longer lines have to go through 0x62.
                return null;
            }
            data.write(line.getLineId());                  // LID
            data.write(line.getMode().getCode());          // DM
            data.write(line.getSpeed());                   // DS
            data.write(line.getDwellSeconds());            // DT
            data.write(line.getRepeatCount());             // DR
            data.write(line.getColor().toWire(), 0, 4);    // TC
            data.write(text.length);                       // TL (1 byte in 0x6E)
            data.write(text, 0, text.length);              // TEXT
            data.write(i == lines.size() - 1 ? 0x00 : 0x0D);
        }

        String voice = request.getVoiceText();
        if (voice != null && !voice.isEmpty()) {
            byte[] v = voice.getBytes(request.getCharset());
            if (v.length > 255) {
                throw new PliProtocolException("voice text too long: " + v.length + " bytes (max 255)");
            }
            data.write(0x0A);                              // VF
            data.write(v.length);                          // VTL
            data.write(v, 0, v.length);                    // VT
            data.write(0x00);                              // voice terminator
        }
        return data.toByteArray();
    }

    /** One 0x62 frame per line (fallback, also the command used by single-line examples). */
    private static List<SerialData> encodePerLine(PliDisplayRequest request) {
        List<SerialData> frames = new ArrayList<SerialData>();
        for (DisplayLine line : request.getLines()) {
            frames.add(new SerialData(request.getSerialChannel(), tempTextFrame(request, line)));
        }
        return frames;
    }

    private static byte[] tempTextFrame(PliDisplayRequest request, DisplayLine line) {
        byte[] text = line.getTextBytes(request.getCharset());
        if (text.length > 0xFFFF) {
            throw new PliProtocolException("text too long: " + text.length + " bytes");
        }
        int dataLength = TEMP_TEXT_HEADER + text.length;
        if (dataLength > maxDataLength(request.getProtocolVersion())) {
            throw new PliProtocolException("line " + (line.getLineId() + 1) + " text is too long: "
                    + text.length + " bytes (max "
                    + (maxDataLength(request.getProtocolVersion()) - TEMP_TEXT_HEADER) + ")");
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(line.getLineId());                      // TWID
        data.write(line.getMode().getCode());              // ETM
        data.write(line.getSpeed());                       // ETS
        data.write(0x00);                                  // DM (reserved)
        data.write(line.getDwellSeconds());                // DT
        data.write(line.getMode().getCode());              // EXM
        data.write(line.getSpeed());                       // EXS
        data.write(0x03);                                  // FINDEX (fixed)
        data.write(line.getRepeatCount());                 // DRS
        data.write(line.getColor().toWire(), 0, 4);        // TC
        data.write(0x00);                                  // BC (black)
        data.write(0x00);
        data.write(0x00);
        data.write(0x00);
        data.write(text.length & 0xFF);                    // TL low
        data.write((text.length >>> 8) & 0xFF);            // TL high
        data.write(text, 0, text.length);                  // TEXT
        return packet(request.getAddress(), request.getProtocolVersion(), CMD_TEMP_TEXT,
                data.toByteArray());
    }

    /**
     * Wraps a DATA field into a full frame: DA + VR + PN[2] + CMD + DL + DATA + CRC[2].
     */
    static byte[] packet(int address, int protocolVersion, int command, byte[] data) {
        if (address < 0 || address > 255) {
            throw new IllegalArgumentException("address must be 0..255, got " + address);
        }
        int dlSize = protocolVersion == 0xC8 ? 2 : 1;
        int maxData = maxDataLength(protocolVersion);
        if (data.length > maxData) {
            throw new PliProtocolException("data length " + data.length
                    + " exceeds the maximum of " + maxData + " bytes");
        }
        byte[] frame = new byte[5 + dlSize + data.length + 2];
        int p = 0;
        frame[p++] = (byte) address;             // DA
        frame[p++] = (byte) protocolVersion;     // VR
        frame[p++] = (byte) (PN_SINGLE & 0xFF);  // PN low
        frame[p++] = (byte) ((PN_SINGLE >>> 8) & 0xFF); // PN high
        frame[p++] = (byte) command;             // CMD
        frame[p++] = (byte) (data.length & 0xFF);// DL
        if (dlSize == 2) {
            frame[p++] = (byte) ((data.length >>> 8) & 0xFF);
        }
        System.arraycopy(data, 0, frame, p, data.length);
        p += data.length;

        int crc = crc16(frame, 0, p);
        frame[p++] = (byte) (crc & 0xFF);        // CRC low first
        frame[p++] = (byte) ((crc >>> 8) & 0xFF);
        return frame;
    }

    private static int maxDataLength(int protocolVersion) {
        return protocolVersion == 0xC8 ? 0xFFFF : 0xFF;
    }

    /**
     * CRC-16/MODBUS: poly 0xA001 (reversed 0x8005), init 0xFFFF, no final xor.
     */
    public static int crc16(byte[] buffer, int offset, int length) {
        int crc = 0xFFFF;
        for (int i = offset; i < offset + length; i++) {
            crc ^= buffer[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc >>>= 1;
                }
            }
        }
        return crc & 0xFFFF;
    }

    /** Renders the frames as the {@code "serialData": [...]} fragment of the HTTP response. */
    public static String toJsonArray(List<SerialData> frames) {
        StringBuilder sb = new StringBuilder();
        sb.append("\"serialData\":[");
        for (int i = 0; i < frames.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(frames.get(i).toJson());
        }
        sb.append(']');
        return sb.toString();
    }
}
