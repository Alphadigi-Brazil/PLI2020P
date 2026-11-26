package br.com.alphadigi.pli2020p;

import java.util.Base64;

/**
 * One element of the {@code serialData} array that the LPR camera expects in the HTTP
 * response: the Base64 payload, its length in bytes ({@code dataLen}) and the raw frame.
 *
 * <p>{@code dataLen} is the number of HEX bytes the camera must push on RS485, i.e. the
 * decoded Base64 length - not the length of the Base64 string itself.</p>
 */
public final class SerialData {

    private final int serialChannel;
    private final byte[] frame;

    public SerialData(int serialChannel, byte[] frame) {
        if (serialChannel < 0 || serialChannel > 255) {
            throw new IllegalArgumentException("serialChannel must be 0..255, got " + serialChannel);
        }
        if (frame == null || frame.length == 0) {
            throw new IllegalArgumentException("frame must not be empty");
        }
        this.serialChannel = serialChannel;
        this.frame = frame.clone();
    }

    public int getSerialChannel() {
        return serialChannel;
    }

    /** Base64 of the RS485 frame. */
    public String getData() {
        return Base64.getEncoder().encodeToString(frame);
    }

    /** Number of bytes the camera must send (= decoded Base64 size). */
    public int getDataLen() {
        return frame.length;
    }

    /** Defensive copy of the raw frame. */
    public byte[] getBytes() {
        return frame.clone();
    }

    /** Uppercase, space separated hex dump, handy for RS485 debugging. */
    public String getHex() {
        StringBuilder sb = new StringBuilder(frame.length * 3);
        for (int i = 0; i < frame.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", frame[i] & 0xFF));
        }
        return sb.toString();
    }

    /** One {@code serialData} element, without the enclosing array. */
    public String toJson() {
        return "{\"serialChannel\":" + serialChannel
                + ",\"data\":\"" + getData()
                + "\",\"dataLen\":" + getDataLen() + "}";
    }

    @Override
    public String toString() {
        return "dataLen=" + getDataLen() + " data=" + getData();
    }
}
