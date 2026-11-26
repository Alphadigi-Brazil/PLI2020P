package br.com.alphadigi.pli2020p.demo;

import br.com.alphadigi.pli2020p.Pli2020pEncoder;
import br.com.alphadigi.pli2020p.PliColor;
import br.com.alphadigi.pli2020p.PliDisplayRequest;
import br.com.alphadigi.pli2020p.SerialData;

import java.util.List;

/**
 * Imprime os quadros do caso de aceitação:
 *
 * <pre>
 * linha 1: `D-`M-20`Y   (data atual, dd-mm-aaaa)
 * linha 2: `H:`N:`S     (hora atual, hh:nn:ss)
 * linha 3: Vaga 999     (vagas livres)
 * linha 4: Bem-vindo!
 * </pre>
 *
 * mais um pulso de semáforo verde de 5 segundos.
 */
public final class PliDemo {

    public static void main(String[] args) {
        PliDisplayRequest request = PliDisplayRequest.builder()
                .line(0, "`D-`M-20`Y", PliColor.GREEN)
                .line(1, "`H:`N:`S", PliColor.GREEN)
                .line(2, "Vaga 999", PliColor.YELLOW)
                .line(3, "Bem-vindo!", PliColor.GREEN)
                .greenLightSeconds(5)
                .build();

        System.out.println("PLI2020P 64x64 (vermelho/verde) - requisicao");
        for (int i = 0; i < request.getLines().size(); i++) {
            System.out.println("  " + request.getLines().get(i));
        }
        System.out.println("  semaforo verde: " + request.getGreenLightSeconds() + " s");
        System.out.println();

        List<SerialData> frames = Pli2020pEncoder.encode(request);
        for (int i = 0; i < frames.size(); i++) {
            SerialData frame = frames.get(i);
            int cmd = frame.getBytes()[4] & 0xFF;
            System.out.println("serialData[" + i + "]  CMD=0x" + String.format("%02X", cmd)
                    + "  dataLen=" + frame.getDataLen());
            System.out.println("  hex : " + frame.getHex());
            System.out.println("  data: " + frame.getData());
            System.out.println();
        }

        System.out.println("Trecho JSON para a resposta da camera:");
        System.out.println("{" + Pli2020pEncoder.toJsonArray(frames) + "}");
    }
}
