package br.com.alphadigi.pli2020p;

import java.nio.charset.Charset;

/**
 * One physical line of the 64x64 display (one of the 4 rows).
 *
 * <p>{@code lineId} 0 is the top row, 3 the bottom row.</p>
 */
public final class DisplayLine {

    private final int lineId;
    private final String text;
    private final PliColor color;
    private final DisplayMode mode;
    private final int speed;
    private final int dwellSeconds;
    private final int repeatCount;

    /**
     * Static line: immediate display, speed 1, dwell 5 s, infinite repeat.
     */
    public static DisplayLine of(int lineId, String text, PliColor color) {
        return new DisplayLine(lineId, text, color, DisplayMode.IMMEDIATE, 1, 5, 0);
    }

    /**
     * Scrolling line: continuous left scroll (marquee), speed 1, dwell 5 s, infinite repeat.
     */
    public static DisplayLine scrolling(int lineId, String text, PliColor color) {
        return new DisplayLine(lineId, text, color, DisplayMode.CONTINUOUS_LEFT, 1, 5, 0);
    }

    public DisplayLine(int lineId, String text, PliColor color, DisplayMode mode,
                       int speed, int dwellSeconds, int repeatCount) {
        if (lineId < 0 || lineId > 3) {
            throw new IllegalArgumentException("lineId must be 0..3, got " + lineId);
        }
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        if (color == null) {
            throw new IllegalArgumentException("color must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (speed < 1 || speed > 32) {
            throw new IllegalArgumentException("speed must be 1..32, got " + speed);
        }
        if (dwellSeconds < 0 || dwellSeconds > 255) {
            throw new IllegalArgumentException("dwellSeconds must be 0..255, got " + dwellSeconds);
        }
        if (repeatCount < 0 || repeatCount > 255) {
            throw new IllegalArgumentException("repeatCount must be 0..255, got " + repeatCount);
        }
        this.lineId = lineId;
        this.text = sanitize(text);
        this.color = color;
        this.mode = mode;
        this.speed = speed;
        this.dwellSeconds = dwellSeconds;
        this.repeatCount = repeatCount;
    }

    /**
     * The controller treats 0x00 / 0x0D / 0x0A as record separators, so they can never be
     * part of a text value.
     */
    private static String sanitize(String text) {
        if (text.indexOf('\u0000') < 0 && text.indexOf('\r') < 0 && text.indexOf('\n') < 0) {
            return text;
        }
        return text.replace("\r\n", " ").replace('\r', ' ').replace('\n', ' ').replace('\u0000', ' ');
    }

    /** Encoded text bytes (UTF-8 by default, GBK for Chinese-only panels). */
    public byte[] getTextBytes(Charset charset) {
        return text.getBytes(charset);
    }

    public int getLineId() {
        return lineId;
    }

    public String getText() {
        return text;
    }

    public PliColor getColor() {
        return color;
    }

    public DisplayMode getMode() {
        return mode;
    }

    public int getSpeed() {
        return speed;
    }

    public int getDwellSeconds() {
        return dwellSeconds;
    }

    public int getRepeatCount() {
        return repeatCount;
    }

    @Override
    public String toString() {
        return "line " + (lineId + 1) + " [" + mode + "] " + color + " \"" + text + "\"";
    }
}
