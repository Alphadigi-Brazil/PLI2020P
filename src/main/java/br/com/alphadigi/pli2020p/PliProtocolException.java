package br.com.alphadigi.pli2020p;

/**
 * Raised when a request cannot be expressed as a valid PLI2020P RS485 frame.
 */
public class PliProtocolException extends IllegalArgumentException {

    private static final long serialVersionUID = 1L;

    public PliProtocolException(String message) {
        super(message);
    }
}
