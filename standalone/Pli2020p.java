package pli2020p;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Gerador de quadros RS485 do painel de LED PLI2020P - <b>arquivo único</b>.
 *
 * <p>Sem dependências, sem Maven, sem build: copie este arquivo para o seu projeto e pronto.
 * Ele gera o Base64 e o {@code dataLen} que a câmera LPR precisa receber no campo
 * {@code serialData} da resposta HTTP.</p>
 *
 * <h3>Como usar (2 passos)</h3>
 * <ol>
 *   <li>Crie a pasta {@code pli2020p} dentro de {@code src} do seu projeto e coloque este
 *       arquivo dentro dela (o nome do arquivo precisa ser {@code Pli2020p.java}).</li>
 *   <li>No seu código: {@code import pli2020p.Pli2020p;} e chame
 *       {@link #encode(String[], String[], int)}.</li>
 * </ol>
 *
 * <h3>Exemplo mínimo</h3>
 * <pre>
 * String[] textos = {"`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"};
 * String[] cores  = {"GREEN", "GREEN", "YELLOW", "GREEN"};
 *
 * List&lt;Pli2020p.Frame&gt; quadros = Pli2020p.encode(textos, cores, 5); // 5 s de verde
 *
 * String b64 = quadros.get(0).base64();   // "AGT//25SAAQ..."
 * int len    = quadros.get(0).dataLen();  // 90
 * </pre>
 *
 * <p>Ou, para montar o JSON inteiro da resposta:</p>
 * <pre>
 * String json = "{" + Pli2020p.jsonArray(quadros) + "}";
 * // {"serialData":[{"serialChannel":0,"data":"...","dataLen":90}, ...]}
 * </pre>
 *
 * <h3>O que ele entende</h3>
 * <ul>
 *   <li><b>textos</b>: até 4 linhas. {@code null} = não mexe naquela linha; {@code ""} =
 *       apaga a linha.</li>
 *   <li><b>cores</b>: {@code "RED"}, {@code "GREEN"}, {@code "YELLOW"} (vermelho+verde),
 *       {@code "OFF"} ou {@code "#RRGGBB"}. {@code null} = vermelho.</li>
 *   <li><b>greenSeconds</b>: 0 = não mexe no semáforo; 1..255 = verde por N segundos
 *       (o painel volta ao vermelho sozinho).</li>
 *   <li><b>data/hora</b>: use os marcadores {@code `Y} ano, {@code `M} mês, {@code `D} dia,
 *       {@code `V} dia da semana, {@code `H} hora, {@code `N} minuto, {@code `S} segundo.
 *       O painel atualiza sozinho.</li>
 *   <li><b>acentos</b>: UTF-8 (padrão dos painéis em campo).</li>
 * </ul>
 *
 * <p>Este arquivo é gerado a partir da biblioteca completa (pacote
 * {@code br.com.alphadigi.pli2020p}); os bytes produzidos são idênticos. Rode
 * {@code java pli2020p.Pli2020p --selftest} para conferir.</p>
 */
public final class Pli2020p {

    private static final Charset UTF8 = Charset.forName("UTF-8");
    private static final int CMD_RELAY = 0x0F;
    private static final int CMD_TEMP_TEXT = 0x62;
    private static final int CMD_MULTI_LINE = 0x6E;

    private Pli2020p() {
    }

    /**
     * Gera os quadros: primeiro o texto, depois o semáforo (se {@code greenSeconds > 0}).
     *
     * @param textos       1 a 4 linhas; {@code null} mantém a linha como está
     * @param cores        cor de cada linha ({@code RED}, {@code GREEN}, {@code YELLOW},
     *                     {@code OFF} ou {@code #RRGGBB}); {@code null} = vermelho
     * @param greenSeconds 0 = sem pacote de semáforo; 1..255 = verde por N segundos
     */
    public static List<Frame> encode(String[] textos, String[] cores, int greenSeconds) {
        if (textos == null || textos.length == 0) {
            throw new IllegalArgumentException("informe pelo menos uma linha de texto");
        }
        if (textos.length > 4) {
            throw new IllegalArgumentException("o painel tem 4 linhas, recebi " + textos.length);
        }
        List<Line> linhas = new ArrayList<Line>();
        for (int i = 0; i < textos.length; i++) {
            if (textos[i] == null) {
                continue;
            }
            String cor = (cores != null && i < cores.length) ? cores[i] : null;
            linhas.add(new Line(i, textos[i], parseColor(cor)));
        }
        if (linhas.isEmpty() && greenSeconds <= 0) {
            throw new IllegalArgumentException("nada para enviar: nenhum texto e semáforo desligado");
        }
        if (greenSeconds < 0 || greenSeconds > 255) {
            throw new IllegalArgumentException("greenSeconds deve ser 0..255, recebi " + greenSeconds);
        }

        List<Frame> quadros = new ArrayList<Frame>();
        if (!linhas.isEmpty()) {
            byte[] multi = null;
            if (linhas.size() == 4) {
                multi = buildMultiLine(linhas);
            }
            if (multi != null && multi.length <= 255) {
                quadros.add(new Frame(packet(CMD_MULTI_LINE, multi)));
            } else {
                for (Line linha : linhas) {
                    quadros.add(new Frame(packet(CMD_TEMP_TEXT, buildSingleLine(linha))));
                }
            }
        }
        if (greenSeconds > 0) {
            quadros.add(new Frame(relayPacket(greenSeconds)));
        }
        return quadros;
    }

    /** Atalho para 4 linhas com cores. */
    public static List<Frame> encode(String l1, String c1, String l2, String c2,
                                     String l3, String c3, String l4, String c4,
                                     int greenSeconds) {
        return encode(new String[] {l1, l2, l3, l4},
                new String[] {c1, c2, c3, c4}, greenSeconds);
    }

    /** Três formas de usar o resultado. */
    public static final class Frame {
        private final byte[] bytes;

        Frame(byte[] bytes) {
            this.bytes = bytes;
        }

        /** O que vai no campo {@code data} da câmera. */
        public String base64() {
            return Base64.getEncoder().encodeToString(bytes);
        }

        /** O que vai no campo {@code dataLen} (quantidade de bytes HEX). */
        public int dataLen() {
            return bytes.length;
        }

        /** Byte a byte em hexadecimal, para conferir no analisador serial. */
        public String hex() {
            StringBuilder sb = new StringBuilder(bytes.length * 3);
            for (byte b : bytes) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(String.format("%02X", b & 0xFF));
            }
            return sb.toString();
        }

        /** Um elemento {@code serialData}. */
        public String json() {
            return "{\"serialChannel\":0,\"data\":\"" + base64()
                    + "\",\"dataLen\":" + dataLen() + "}";
        }

        public byte[] bytes() {
            return bytes.clone();
        }
    }

    /** Monta o trecho {@code "serialData":[ ... ]} da resposta HTTP. */
    public static String jsonArray(List<Frame> quadros) {
        StringBuilder sb = new StringBuilder("\"serialData\":[");
        for (int i = 0; i < quadros.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(quadros.get(i).json());
        }
        return sb.append(']').toString();
    }

    /** CRC-16/MODBUS (poly 0xA001, init 0xFFFF), usado em todos os quadros. */
    public static int crc16(byte[] dados, int inicio, int tamanho) {
        int crc = 0xFFFF;
        for (int i = inicio; i < inicio + tamanho; i++) {
            crc ^= dados[i] & 0xFF;
            for (int bit = 0; bit < 8; bit++) {
                crc = ((crc & 1) != 0) ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
            }
        }
        return crc & 0xFFFF;
    }

    // ------------------------------------------------------------------ internos

    private static final class Line {
        final int id;
        final String text;
        final int r;
        final int g;
        final int b;

        Line(int id, String text, int[] rgb) {
            this.id = id;
            this.text = text.replace("\r", " ").replace("\n", " ").replace("\u0000", " ");
            this.r = rgb[0];
            this.g = rgb[1];
            this.b = rgb[2];
        }

        byte[] textBytes() {
            return text.getBytes(UTF8);
        }
    }

    private static int[] parseColor(String cor) {
        if (cor == null || cor.trim().isEmpty()) {
            return new int[] {255, 0, 0};
        }
        String v = cor.trim().toUpperCase(java.util.Locale.ROOT);
        if ("RED".equals(v)) {
            return new int[] {255, 0, 0};
        }
        if ("GREEN".equals(v)) {
            return new int[] {0, 255, 0};
        }
        if ("YELLOW".equals(v) || "AMBER".equals(v) || "ORANGE".equals(v)) {
            return new int[] {255, 255, 0};
        }
        if ("OFF".equals(v) || "BLACK".equals(v) || "NONE".equals(v)) {
            return new int[] {0, 0, 0};
        }
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.length() == 6 && v.matches("[0-9A-F]{6}")) {
            int rgb = Integer.parseInt(v, 16);
            return new int[] {(rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF};
        }
        throw new IllegalArgumentException("cor desconhecida: " + cor
                + " (use RED, GREEN, YELLOW, OFF ou #RRGGBB)");
    }

    /** 0x6E: as 4 linhas em um pacote. Retorna null se alguma linha for grande demais. */
    private static byte[] buildMultiLine(List<Line> linhas) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0x00);              // SAVE_FLAG: área temporária
        data.write(linhas.size());     // TEXT_CONTEXT_NUMBER
        for (int i = 0; i < linhas.size(); i++) {
            Line linha = linhas.get(i);
            byte[] texto = linha.textBytes();
            if (texto.length > 32) {   // limite recomendado do 0x6E
                return null;
            }
            data.write(linha.id);      // LID
            data.write(0x00);          // DM: exibe imediatamente
            data.write(0x01);          // DS: velocidade
            data.write(0x05);          // DT: permanência (s)
            data.write(0x00);          // DR: repetições (0 = infinito)
            data.write(linha.r);       // TC
            data.write(linha.g);
            data.write(linha.b);
            data.write(0x00);          // A
            data.write(texto.length);  // TL (1 byte no 0x6E)
            data.write(texto, 0, texto.length);
            data.write(i == linhas.size() - 1 ? 0x00 : 0x0D);
        }
        return data.toByteArray();
    }

    /** 0x62: uma linha por pacote. */
    private static byte[] buildSingleLine(Line linha) {
        byte[] texto = linha.textBytes();
        if (texto.length > 236) {
            throw new IllegalArgumentException("linha " + (linha.id + 1) + " tem " + texto.length
                    + " bytes; o máximo é 236");
        }
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(linha.id);          // TWID
        data.write(0x00);              // ETM
        data.write(0x01);              // ETS
        data.write(0x00);              // DM (reservado)
        data.write(0x05);              // DT
        data.write(0x00);              // EXM
        data.write(0x01);              // EXS
        data.write(0x03);              // FINDEX (fixo)
        data.write(0x00);              // DRS
        data.write(linha.r);           // TC
        data.write(linha.g);
        data.write(linha.b);
        data.write(0x00);
        data.write(0x00);              // BC (fundo preto)
        data.write(0x00);
        data.write(0x00);
        data.write(0x00);
        data.write(texto.length & 0xFF);        // TL (2 bytes no 0x62)
        data.write((texto.length >>> 8) & 0xFF);
        data.write(texto, 0, texto.length);
        return data.toByteArray();
    }

    private static byte[] relayPacket(int segundos) {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        data.write(0x00);              // RESERVED[5]
        data.write(0x01);
        data.write(0x00);
        data.write(0x00);
        data.write(0x00);
        data.write(segundos);          // OT
        return packet(CMD_RELAY, data.toByteArray());
    }

    /** DA + VR + PN[2] + CMD + DL + DATA + CRC[2] */
    private static byte[] packet(int comando, byte[] data) {
        if (data.length > 255) {
            throw new IllegalArgumentException("pacote com " + data.length
                    + " bytes; o máximo é 255");
        }
        byte[] quadro = new byte[6 + data.length + 2];
        quadro[0] = 0x00;              // DA
        quadro[1] = 0x64;              // VR (protocolo v1)
        quadro[2] = (byte) 0xFF;       // PN
        quadro[3] = (byte) 0xFF;
        quadro[4] = (byte) comando;    // CMD
        quadro[5] = (byte) data.length; // DL
        System.arraycopy(data, 0, quadro, 6, data.length);
        int crc = crc16(quadro, 0, 6 + data.length);
        quadro[quadro.length - 2] = (byte) (crc & 0xFF);
        quadro[quadro.length - 1] = (byte) ((crc >>> 8) & 0xFF);
        return quadro;
    }

    // ------------------------------------------------------------------ testes/demo

    public static void main(String[] args) {
        if (args.length > 0 && "--selftest".equals(args[0])) {
            selftest();
            return;
        }
        System.out.println("PLI2020P - demonstracao");
        List<Frame> quadros = encode(
                new String[] {"`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"},
                new String[] {"GREEN", "GREEN", "YELLOW", "GREEN"},
                5);
        for (int i = 0; i < quadros.size(); i++) {
            Frame q = quadros.get(i);
            System.out.println("serialData[" + i + "]  dataLen=" + q.dataLen());
            System.out.println("  hex : " + q.hex());
            System.out.println("  data: " + q.base64());
        }
        System.out.println();
        System.out.println("{" + jsonArray(quadros) + "}");
    }

    private static void selftest() {
        aviso("quadros de texto (4 linhas + semaforo 5 s)");

        List<Frame> quadros = encode(
                new String[] {"`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"},
                new String[] {"GREEN", "GREEN", "YELLOW", "GREEN"},
                5);
        esperar(2, quadros.size(), "quantidade de quadros");
        esperar("AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2Eg"
                        + "OTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o",
                quadros.get(0).base64(), "base64 do texto");
        esperar(90, quadros.get(0).dataLen(), "dataLen do texto");
        esperar("AGT//w8GAAEAAAAFJLc=", quadros.get(1).base64(), "base64 do semaforo");
        esperar(14, quadros.get(1).dataLen(), "dataLen do semaforo");

        aviso("quadro 0x62 de uma linha (Bemvindo, amarelo)");
        List<Frame> um = encode(new String[] {null, null, "Bemvindo"}, new String[] {null, null, "YELLOW"}, 0);
        esperar("00 64 FF FF 62 1B 02 00 01 00 05 00 01 03 00 FF FF 00 00 00 00 00 00 08 00 "
                + "42 65 6D 76 69 6E 64 6F 87 30", um.get(0).hex(), "hex 0x62");
        esperar(35, um.get(0).dataLen(), "dataLen 0x62");

        aviso("CRC-16 dos quadros de referencia");
        esperar(0xB724, crc16(hex("00 64 FF FF 0F 06 00 01 00 00 00 05"), 0, 12), "crc semaforo 5 s");
        esperar(0x30CC, crc16(hex("00 64 FF FF 6E 69 00 04 00 00 01 05 00 FF 00 00 00 08 60 48 3A 60 4E 3A 60 53 0D "
                + "01 00 01 05 00 FF 00 00 00 08 60 44 2F 60 4D 2F 60 59 0D "
                + "02 00 01 05 00 FF 00 00 00 08 41 42 43 2D 31 32 33 34 0D "
                + "03 15 01 05 00 FF 00 00 00 10 53 69 67 61 20 65 6D 20 66 72 65 6E 74 65 21 20 00 "
                + "0A 10 53 69 67 61 20 65 6D 20 66 72 65 6E 74 65 21 20 00"), 0, 111), "crc 4 linhas + voz");

        aviso("limites");
        esperar(1, encode(new String[] {"A", "B", "C", "D"}, null, 0).size(), "4 linhas = 1 pacote");
        esperar(2, encode(new String[] {null, null, "A", "B"}, null, 0).size(), "2 linhas = 2 pacotes");
        System.out.println();
        System.out.println("SELFTEST OK");
    }

    private static void aviso(String titulo) {
        System.out.println("-- " + titulo);
    }

    private static void esperar(int esperado, int obtido, String nome) {
        if (esperado != obtido) {
            throw new IllegalStateException(nome + ": esperado " + esperado + ", obtido " + obtido);
        }
        System.out.println("   ok " + nome);
    }

    private static void esperar(String esperado, String obtido, String nome) {
        if (!esperado.equals(obtido)) {
            throw new IllegalStateException(nome + ":\n  esperado " + esperado + "\n  obtido   " + obtido);
        }
        System.out.println("   ok " + nome);
    }

    private static byte[] hex(String texto) {
        String[] partes = texto.trim().split("\\s+");
        byte[] saida = new byte[partes.length];
        for (int i = 0; i < partes.length; i++) {
            saida[i] = (byte) Integer.parseInt(partes[i], 16);
        }
        return saida;
    }
}
