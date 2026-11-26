package br.com.alphadigi.pli2020p;

/**
 * Text entry / exit mode codes (ETM / EXM of 0x62, DM of 0x6E).
 */
public enum DisplayMode {

    /** Show immediately, no movement. */
    IMMEDIATE(0x00),
    /** Move in from the right edge to the left. */
    MOVE_RIGHT_TO_LEFT(0x01),
    /** Move in from the left edge to the right. */
    MOVE_LEFT_TO_RIGHT(0x02),
    /** Move in from bottom to top. */
    MOVE_BOTTOM_TO_TOP(0x03),
    /** Move in from top to bottom. */
    MOVE_TOP_TO_BOTTOM(0x04),
    /** Pull window down. */
    PULL_DOWN(0x05),
    /** Pull window up. */
    PULL_UP(0x06),
    /** Pull window left. */
    PULL_LEFT(0x07),
    /** Pull window right. */
    PULL_RIGHT(0x08),
    /** Reveal character by character. */
    TYPEWRITER(0x0D),
    /** Continuous scroll to the left (the usual "marquee" mode). */
    CONTINUOUS_LEFT(0x15);

    private final int code;

    DisplayMode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DisplayMode fromCode(int code) {
        for (DisplayMode mode : values()) {
            if (mode.code == code) {
                return mode;
            }
        }
        throw new IllegalArgumentException("unknown display mode code: 0x"
                + Integer.toHexString(code));
    }
}
