package br.com.alphadigi.pli2020p;

/**
 * RGBA colour used by the PLI2020P display (TC / BC fields).
 *
 * <p>The PLI2020P is a 64x64 <b>red / green</b> dual-colour panel, so only the R and G
 * channels produce light. Useful values are therefore:</p>
 * <ul>
 *   <li>{@link #RED}   (255, 0, 0)   - red only</li>
 *   <li>{@link #GREEN} (0, 255, 0)   - green only</li>
 *   <li>{@link #YELLOW}(255, 255, 0) - red + green, reads as amber/yellow</li>
 *   <li>{@link #BLACK} (0, 0, 0)     - off (also used to clear a line)</li>
 * </ul>
 *
 * <p>Blue exists only for full-colour panels of the same family; it is ignored by a
 * dual-colour panel.</p>
 */
public final class PliColor {

    /** Red only. */
    public static final PliColor RED = new PliColor(255, 0, 0);
    /** Green only. */
    public static final PliColor GREEN = new PliColor(0, 255, 0);
    /** Red + green (amber / yellow). */
    public static final PliColor YELLOW = new PliColor(255, 255, 0);
    /** Red + green, alias of {@link #YELLOW}. */
    public static final PliColor AMBER = YELLOW;
    /** All channels off. */
    public static final PliColor BLACK = new PliColor(0, 0, 0);
    /** Alias of {@link #BLACK}. */
    public static final PliColor OFF = BLACK;

    private final int red;
    private final int green;
    private final int blue;

    public PliColor(int red, int green, int blue) {
        this.red = checkChannel(red, "red");
        this.green = checkChannel(green, "green");
        this.blue = checkChannel(blue, "blue");
    }

    public static PliColor of(int red, int green, int blue) {
        return new PliColor(red, green, blue);
    }

    private static int checkChannel(int value, String name) {
        if (value < 0 || value > 255) {
            throw new IllegalArgumentException(name + " channel must be 0..255, got " + value);
        }
        return value;
    }

    public int getRed() {
        return red;
    }

    public int getGreen() {
        return green;
    }

    public int getBlue() {
        return blue;
    }

    /** Wire format: R, G, B, A(0). */
    public byte[] toWire() {
        return new byte[] {(byte) red, (byte) green, (byte) blue, (byte) 0x00};
    }

    /**
     * Parses a colour name ({@code RED}, {@code GREEN}, {@code YELLOW}, {@code AMBER},
     * {@code OFF}, {@code BLACK}) or a hex string ({@code #RRGGBB}, {@code RRGGBB},
     * {@code 0xRRGGBB}). Case-insensitive.
     */
    public static PliColor parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("colour must not be null");
        }
        String v = value.trim();
        String upper = v.toUpperCase(java.util.Locale.ROOT);
        if ("RED".equals(upper) || "R".equals(upper)) {
            return RED;
        }
        if ("GREEN".equals(upper) || "G".equals(upper)) {
            return GREEN;
        }
        if ("YELLOW".equals(upper) || "AMBER".equals(upper) || "ORANGE".equals(upper)
                || "Y".equals(upper)) {
            return YELLOW;
        }
        if ("OFF".equals(upper) || "BLACK".equals(upper) || "NONE".equals(upper)
                || "CLEAR".equals(upper) || "K".equals(upper)) {
            return BLACK;
        }
        String hex = upper;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0X")) {
            hex = hex.substring(2);
        }
        if (hex.length() == 6 && hex.matches("[0-9A-F]{6}")) {
            int rgb = Integer.parseInt(hex, 16);
            return new PliColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        }
        throw new IllegalArgumentException("unknown colour: " + value
                + " (use RED/GREEN/YELLOW/OFF or #RRGGBB)");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PliColor)) {
            return false;
        }
        PliColor other = (PliColor) o;
        return red == other.red && green == other.green && blue == other.blue;
    }

    @Override
    public int hashCode() {
        return (red << 16) | (green << 8) | blue;
    }

    @Override
    public String toString() {
        return String.format("RGBA(%d,%d,%d,0)", red, green, blue);
    }
}
