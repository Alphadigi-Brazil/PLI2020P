package br.com.alphadigi.pli2020p;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Everything the parking platform wants the display to show: up to 4 text lines, an
 * optional "green light" pulse for the traffic-light board, and the frame parameters.
 *
 * <pre>
 * PliDisplayRequest request = PliDisplayRequest.builder()
 *         .line(0, "`D-`M-20`Y", PliColor.GREEN)
 *         .line(1, "`H:`N:`S",   PliColor.GREEN)
 *         .line(2, "Vaga 999",   PliColor.YELLOW)
 *         .line(3, "Bem-vindo!", PliColor.GREEN)
 *         .greenLightSeconds(5)
 *         .build();
 * </pre>
 */
public final class PliDisplayRequest {

    /** How the text is packed into RS485 frames. */
    public enum Strategy {
        /**
         * Use one 0x6E multi-line packet when every line fits (<= 32 bytes of text and a
         * total data length <= 255), otherwise fall back to one 0x62 packet per line.
         */
        AUTO,
        /** Always send one 0x6E packet (throws if it cannot fit in a single packet). */
        MULTI_LINE_PACKET,
        /** Always send one 0x62 packet per line. */
        PER_LINE_PACKETS
    }

    private final int address;
    private final int protocolVersion;
    private final int serialChannel;
    private final TextEncoding textEncoding;
    private final boolean saveToFlash;
    private final Strategy strategy;
    private final List<DisplayLine> lines;
    private final int greenLightSeconds;
    private final String voiceText;

    private PliDisplayRequest(Builder b) {
        this.address = b.address;
        this.protocolVersion = b.protocolVersion;
        this.serialChannel = b.serialChannel;
        this.textEncoding = b.textEncoding;
        this.saveToFlash = b.saveToFlash;
        this.strategy = b.strategy;
        this.lines = Collections.unmodifiableList(new ArrayList<DisplayLine>(b.lines.values()));
        this.greenLightSeconds = b.greenLightSeconds;
        this.voiceText = b.voiceText;
        if (lines.isEmpty() && greenLightSeconds == 0) {
            throw new IllegalArgumentException("request has neither text lines nor a green light pulse");
        }
        if (voiceText != null && !voiceText.isEmpty() && lines.isEmpty()) {
            throw new IllegalArgumentException("voice requires at least one text line");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public int getAddress() {
        return address;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public int getSerialChannel() {
        return serialChannel;
    }

    public TextEncoding getTextEncoding() {
        return textEncoding;
    }

    public Charset getCharset() {
        return textEncoding.getCharset();
    }

    public boolean isSaveToFlash() {
        return saveToFlash;
    }

    public Strategy getStrategy() {
        return strategy;
    }

    /** Lines present in the request, ordered by line id (0 = top row). */
    public List<DisplayLine> getLines() {
        return lines;
    }

    /** Seconds the relay (green light) stays on; 0 means "no traffic-light frame". */
    public int getGreenLightSeconds() {
        return greenLightSeconds;
    }

    /** Optional voice announcement, already in the display's voice grammar; may be null. */
    public String getVoiceText() {
        return voiceText;
    }

    @Override
    public String toString() {
        return "PliDisplayRequest{addr=" + address + ", lines=" + lines
                + ", green=" + greenLightSeconds + "s, voice=" + voiceText + "}";
    }

    public static final class Builder {

        private int address = 0x00;
        private int protocolVersion = 0x64;
        private int serialChannel = 0;
        private TextEncoding textEncoding = TextEncoding.UTF8;
        private boolean saveToFlash = false;
        private Strategy strategy = Strategy.AUTO;
        private final Map<Integer, DisplayLine> lines = new TreeMap<Integer, DisplayLine>();
        private int greenLightSeconds = 0;
        private String voiceText;

        private Builder() {
        }

        /** Display address (DA), usually 0x00. */
        public Builder deviceAddress(int address) {
            if (address < 0 || address > 255) {
                throw new IllegalArgumentException("address must be 0..255, got " + address);
            }
            this.address = address;
            return this;
        }

        /** 0x64 = protocol v1.00 (1-byte DL), 0xC8 = v2.00 (2-byte DL). */
        public Builder protocolVersion(int version) {
            if (version != 0x64 && version != 0xC8) {
                throw new IllegalArgumentException("protocolVersion must be 0x64 or 0xC8, got 0x"
                        + Integer.toHexString(version));
            }
            this.protocolVersion = version;
            return this;
        }

        /** serialChannel echoed back to the camera, normally 0. */
        public Builder serialChannel(int channel) {
            if (channel < 0 || channel > 255) {
                throw new IllegalArgumentException("serialChannel must be 0..255, got " + channel);
            }
            this.serialChannel = channel;
            return this;
        }

        public Builder textEncoding(TextEncoding encoding) {
            if (encoding == null) {
                throw new IllegalArgumentException("textEncoding must not be null");
            }
            this.textEncoding = encoding;
            return this;
        }

        /** true = store in flash (SAVE_FLAG=1), false = temporary area (default). */
        public Builder saveToFlash(boolean save) {
            this.saveToFlash = save;
            return this;
        }

        public Builder strategy(Strategy strategy) {
            if (strategy == null) {
                throw new IllegalArgumentException("strategy must not be null");
            }
            this.strategy = strategy;
            return this;
        }

        /** Adds / replaces a line; {@code lineId} 0 = top row .. 3 = bottom row. */
        public Builder line(int lineId, String text, PliColor color) {
            return line(DisplayLine.of(lineId, text, color));
        }

        /** Adds / replaces a scrolling (marquee) line. */
        public Builder scrollingLine(int lineId, String text, PliColor color) {
            return line(DisplayLine.scrolling(lineId, text, color));
        }

        public Builder line(DisplayLine line) {
            if (line == null) {
                throw new IllegalArgumentException("line must not be null");
            }
            lines.put(line.getLineId(), line);
            return this;
        }

        /**
         * Turns the traffic light green for {@code seconds} (1..255) and then back to red.
         * 0 disables the traffic-light frame.
         */
        public Builder greenLightSeconds(int seconds) {
            if (seconds < 0 || seconds > 255) {
                throw new IllegalArgumentException("greenLightSeconds must be 0..255, got " + seconds);
            }
            this.greenLightSeconds = seconds;
            return this;
        }

        /** Optional voice announcement (0x6E only); null or empty disables it. */
        public Builder voice(String voiceText) {
            this.voiceText = voiceText;
            return this;
        }

        public PliDisplayRequest build() {
            return new PliDisplayRequest(this);
        }
    }
}
