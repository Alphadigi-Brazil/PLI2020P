package br.com.alphadigi.pli2020p;

import java.nio.charset.Charset;

/**
 * Character encoding used for the TEXT field.
 *
 * <p>The protocol document says "ASCII and GBK2312", but the frames captured from real
 * LPR cameras (file {@code Serial message Data2.txt}) encode accented Latin letters as
 * UTF-8 ({@code C3 A1} for "á", {@code C3 AD} for "í"), which is what a Portuguese /
 * Spanish deployment needs. {@link #UTF8} is therefore the default; use {@link #GBK}
 * only for panels that must print simplified Chinese.</p>
 */
public enum TextEncoding {

    UTF8("UTF-8"),
    GBK("GBK");

    private final String charsetName;

    TextEncoding(String charsetName) {
        this.charsetName = charsetName;
    }

    public Charset getCharset() {
        return Charset.forName(charsetName);
    }
}
