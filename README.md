# PLI2020P — Codificador de mensagens para painel de LED

[![build](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml/badge.svg)](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8%2B-orange.svg)](pom.xml)

Biblioteca Java **sem dependências em runtime** que monta os pacotes RS485 do controlador do
painel de LED **PLI2020P** (64×64, vermelho/verde, até 4 linhas de texto) e devolve o resultado
no formato que as câmeras LPR esperam no campo `serialData`: **Base64 + `dataLen`**.

Você informa o que quer mostrar:

- até 4 linhas de texto, **cada uma com a sua cor**;
- se o semáforo (placa de relé) deve ficar **verde e por quantos segundos**.

E recebe de volta algo pronto para colocar na resposta HTTP da câmera:

```json
{
  "serialData": [
    {
      "serialChannel": 0,
      "data": "AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2EgOTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o",
      "dataLen": 90
    },
    {
      "serialChannel": 0,
      "data": "AGT//w8GAAEAAAAFJLc=",
      "dataLen": 14
    }
  ]
}
```

---

## 1. Onde ela entra no fluxo

```
 ┌───────────────────────┐   HTTP POST (resultado do OCR)   ┌──────────────┐
 │  Plataforma de        │ ──────────────────────────────►  │  Câmera LPR  │
 │  estacionamento /     │                                  │              │
 │  controle de acesso   │ ◄──────────────────────────────  │              │
 └───────────────────────┘   HTTP 200 + "serialData"        └──────┬───────┘
          │                                                        │ RS485 (HEX)
          │  esta biblioteca monta os pacotes                       │ 9600/115200 bps
          ▼                                                        ▼
   Base64 + dataLen                                       ┌──────────────────┐
                                                          │ Painel PLI2020P  │
                                                          │ 64×64, 4 linhas  │
                                                          └──────────────────┘
```

A câmera decodifica o Base64 em HEX e transmite pelo RS485. O `dataLen` é a quantidade de
**bytes HEX** (não o tamanho da string Base64) — a biblioteca já devolve exatamente isso.

---

## 2. Instalação

### Maven

O artefato ainda não está publicado em repositório central. Gere e instale localmente:

```bash
git clone https://github.com/Alphadigi-Brazil/PLI2020P.git
cd PLI2020P
mvn install
```

```xml
<dependency>
  <groupId>br.com.alphadigi</groupId>
  <artifactId>pli2020p</artifactId>
  <version>1.0.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'br.com.alphadigi:pli2020p:1.0.0'
```

### Sem build system

Copie `src/main/java/br/com/alphadigi/pli2020p/` para o seu projeto. São 9 arquivos, Java 8,
nenhuma dependência externa.

---

## 3. Uso em 30 segundos

```java
import br.com.alphadigi.pli2020p.*;
import java.util.List;

PliDisplayRequest request = PliDisplayRequest.builder()
        .line(0, "`D-`M-20`Y", PliColor.GREEN)    // linha 1: data  dd-mm-yyyy
        .line(1, "`H:`N:`S",   PliColor.GREEN)    // linha 2: hora  hh:nn:ss
        .line(2, "Vaga 999",   PliColor.YELLOW)   // linha 3: vagas livres
        .line(3, "Bem-vindo!", PliColor.GREEN)    // linha 4: mensagem
        .greenLightSeconds(5)                     // semáforo verde por 5 s (0 = não mexer)
        .build();

List<SerialData> frames = Pli2020pEncoder.encode(request);

frames.get(0).getData();     // Base64
frames.get(0).getDataLen();  // 90
frames.get(0).getHex();      // 00 64 FF FF 6E 52 ... (para conferir no analisador serial)

// já no formato da resposta da câmera:
String json = "{" + Pli2020pEncoder.toJsonArray(frames) + "}";
```

Saída exata do exemplo acima:

```text
serialData[0]  CMD=0x6E  dataLen=90
  hex : 00 64 FF FF 6E 52 00 04 00 00 01 05 00 00 FF 00 00 0A 60 44 2D 60 4D 2D 32 30 60 59 0D
        01 00 01 05 00 00 FF 00 00 08 60 48 3A 60 4E 3A 60 53 0D
        02 00 01 05 00 FF FF 00 00 08 56 61 67 61 20 39 39 39 0D
        03 00 01 05 00 00 FF 00 00 0A 42 65 6D 2D 76 69 6E 64 6F 21 00
        5E 68
  data: AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2EgOTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o

serialData[1]  CMD=0x0F  dataLen=14
  hex : 00 64 FF FF 0F 06 00 01 00 00 00 05 24 B7
  data: AGT//w8GAAEAAAAFJLc=
```

Rode você mesmo: `java -cp target/classes br.com.alphadigi.pli2020p.demo.PliDemo`

---

## 4. Cores

O painel é **vermelho/verde**; só os canais R e G acendem. O canal B existe apenas para os
modelos coloridos da mesma família.

| Constante | RGBA | Resultado no painel |
|---|---|---|
| `PliColor.RED` | 255, 0, 0, 0 | vermelho |
| `PliColor.GREEN` | 0, 255, 0, 0 | verde |
| `PliColor.YELLOW` / `PliColor.AMBER` | 255, 255, 0, 0 | vermelho + verde = amarelo/âmbar |
| `PliColor.BLACK` / `PliColor.OFF` | 0, 0, 0, 0 | apagado (também serve para limpar a linha) |

Também aceita string, útil quando a cor vem de configuração/banco:

```java
PliColor.parse("RED");       // vermelho
PliColor.parse("#FF8800");   // laranja (acende R + G)
PliColor.of(0, 255, 0);      // verde
```

---

## 5. Modos de exibição

```java
DisplayLine.of(0, "Vaga 999", PliColor.YELLOW);         // texto fixo (padrão)
DisplayLine.scrolling(3, "Bem-vindo!", PliColor.GREEN); // rolagem contínua p/ esquerda
```

Controle fino, quando precisar:

```java
new DisplayLine(
    2,                                  // linha 0..3 (0 = topo)
    "Vaga 999",
    PliColor.YELLOW,
    DisplayMode.IMMEDIATE,              // ver tabela abaixo
    1,                                  // velocidade 1..32
    5,                                  // tempo de permanência 0..255 s
    0);                                 // repetições (0 = infinito)
```

| `DisplayMode` | Código | Efeito |
|---|---|---|
| `IMMEDIATE` | 0x00 | aparece imediatamente |
| `MOVE_RIGHT_TO_LEFT` | 0x01 | entra da direita |
| `MOVE_LEFT_TO_RIGHT` | 0x02 | entra da esquerda |
| `MOVE_BOTTOM_TO_TOP` | 0x03 | entra de baixo |
| `MOVE_TOP_TO_BOTTOM` | 0x04 | entra de cima |
| `PULL_DOWN` / `PULL_UP` | 0x05 / 0x06 | janela desce / sobe |
| `PULL_LEFT` / `PULL_RIGHT` | 0x07 / 0x08 | janela abre p/ esquerda / direita |
| `TYPEWRITER` | 0x0D | letra por letra |
| `CONTINUOUS_LEFT` | 0x15 | rolagem contínua (o "letreiro") |

---

## 6. Data e hora sem ficar reenviando

O painel tem RTC próprio: escreva os marcadores no texto e ele mesmo atualiza o relógio.
Isso evita um POST por segundo na câmera.

| Marcador | Vira |
|---|---|
| `` `Y `` | ano (4 dígitos) |
| `` `M `` | mês |
| `` `D `` | dia |
| `` `V `` | dia da semana |
| `` `H `` | hora |
| `` `N `` | minuto |
| `` `S `` | segundo |
| `` `C `` | constante 20 |

Exemplo: `` `D-`M-20`Y `` → `26-11-2026`; `` `H:`N:`S `` → `09:43:27`.

---

## 7. Acentuação e idioma (leia antes de reclamar de "ç" virando "?")

O documento do fabricante fala em "ASCII e GBK2312", mas **as câmeras em campo enviam UTF-8**:
`á` chega como `C3 A1`, `í` como `C3 AD`. Por isso o padrão da biblioteca é **UTF-8** — português
com acento funciona.

```java
PliDisplayRequest.builder().textEncoding(TextEncoding.UTF8)  // padrão
PliDisplayRequest.builder().textEncoding(TextEncoding.GBK)   // só para chinês simplificado
```

Atenção: **GBK não representa `ç`, `ã`, `á`**. Se o painel precisa mostrar chinês e português ao
mesmo tempo, não há uma codificação única que sirva — separe os idiomas por linha ou
configure o painel para UTF-8 (firmwares mais recentes aceitam).

---

## 8. Como os pacotes são escolhidos

| Estratégia | Quando usar | Resultado |
|---|---|---|
| `AUTO` (padrão) | sempre | 4 linhas → **1 pacote `0x6E`**; 1–3 linhas → **1 pacote `0x62` por linha**; com voz → `0x6E` |
| `MULTI_LINE_PACKET` | quer forçar atualização atômica | 1 pacote `0x6E` (falha se não couber em 255 bytes) |
| `PER_LINE_PACKETS` | quer compatibilidade máxima | 1 pacote `0x62` por linha |

O `AUTO` reproduz o que as instalações em campo fazem: tela cheia em um único pacote (sem
"meia tela nova, meia tela velha"), atualizações parciais linha a linha (as outras linhas
continuam como estavam).

Se alguma linha passar de 32 bytes (limite recomendado do `0x6E`) ou o pacote passar de 255
bytes, o `AUTO` cai automaticamente para `0x62` por linha. Se nem isso couber, é lançada
`PliProtocolException` dizendo qual limite foi estourado.

---

## 9. Referência rápida da API

| Classe | Para quê |
|---|---|
| `PliDisplayRequest` + `builder()` | descreve o que mostrar (linhas, cores, semáforo, endereço, codificação) |
| `Pli2020pEncoder.encode(request)` | devolve `List<SerialData>` (texto primeiro, semáforo depois) |
| `Pli2020pEncoder.relayFrame(addr, ver, segundos)` | só o pacote do semáforo |
| `Pli2020pEncoder.toJsonArray(frames)` | monta o trecho `"serialData": [...]` |
| `SerialData` | `getData()` (Base64), `getDataLen()`, `getHex()`, `toJson()` |
| `PliColor` | cores e `parse(String)` |
| `DisplayLine` / `DisplayMode` | conteúdo e comportamento de cada linha |
| `PliFrameParser.parse(bytes)` | lê um quadro capturado no RS485 e valida o CRC (diagnóstico) |
| `PliProtocolException` | erro de limite/parâmetro, com mensagem explicativa |

Parâmetros do builder:

| Método | Padrão | Observação |
|---|---|---|
| `deviceAddress(int)` | `0x00` | endereço DA do painel |
| `protocolVersion(int)` | `0x64` | `0xC8` = protocolo v2 (DL de 2 bytes) |
| `textEncoding(TextEncoding)` | `UTF8` | ver seção 7 |
| `saveToFlash(boolean)` | `false` | `true` grava na memória permanente (evite uso frequente) |
| `strategy(...)` | `AUTO` | ver seção 8 |
| `serialChannel(int)` | `0` | ecoado para a câmera |
| `greenLightSeconds(int)` | `0` | 0..255; 0 não envia pacote de semáforo |
| `voice(String)` | `null` | anúncio de voz (só no `0x6E`) |

---

## 10. Integrando na resposta da câmera

A câmera aceita um array com vários canais/pacotes. Basta devolver os elementos na ordem
gerada (texto → semáforo):

```json
{
  "Response_AlarmInfoPlate": {
    "info": "ok",
    "content": "retransfer_stop",
    "serialData": [
      { "serialChannel": 0, "data": "AGT//25SAAQ...AF5o", "dataLen": 90 },
      { "serialChannel": 0, "data": "AGT//w8GAAEAAAAFJLc=", "dataLen": 14 }
    ]
  }
}
```

Só semáforo (sem mexer no texto):

```java
List<SerialData> frames = Pli2020pEncoder.encode(
        PliDisplayRequest.builder().greenLightSeconds(10).build());
// 1 pacote 0x0F, dataLen 14
```

Limpar uma linha:

```java
PliDisplayRequest.builder()
        .line(2, "", PliColor.BLACK)   // texto vazio + preto
        .build();
```

---

## 11. Limites

| Item | Limite |
|---|---|
| Linhas | 4 (0..3) |
| Texto por linha no `0x6E` | 255 bytes (recomendado ≤ 32) |
| Texto por linha no `0x62` | 236 bytes |
| Payload total (`DL`) | 255 bytes (protocolo v1) |
| Velocidade | 1..32 |
| Tempo de permanência | 0..255 s |
| Repetições | 0..255 (0 = infinito) |
| Semáforo verde | 1..255 s |
| Endereço | 0x00..0xFF |

O tempo do semáforo é controlado pelo próprio painel: ele desliga o relé sozinho ao fim do
período e volta ao vermelho.

---

## 12. Build, testes e demo

```bash
mvn verify                                              # compila + 17 testes
java -cp target/classes br.com.alphadigi.pli2020p.demo.PliDemo   # imprime os quadros
```

Ou com o wrapper (não precisa ter Maven instalado):

```bash
./mvnw verify
```

CI: GitHub Actions roda `mvn verify` em Java 8, 11, 17 e 21
([workflow](.github/workflows/build.yml)).

### O que os testes garantem

Os testes não são "achismo": eles comparam a saída da biblioteca, byte a byte, com quadros
capturados de instalações reais (`Pli2020pEncoderTest`):

- CRC-16 de 7 quadros reais confere;
- `0x62` "Bemvindo" (3ª linha, âmbar, rolando) idêntico ao capturado;
- exemplo em chinês do documento do fabricante idêntico (incluindo CRC);
- `0x6E` com 4 linhas + voz idêntico ao capturado (113 bytes);
- `0x0F` semáforo 5 s idêntico ao capturado;
- ida e volta pelo `PliFrameParser`;
- `dataLen` = tamanho decodificado do Base64 = tamanho do quadro;
- limites, fallback e mensagens de erro.

---

## 13. Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| Acentos aparecem como `?` ou estranhos | painel em GBK | use `TextEncoding.GBK` se for chinês; se for português, verifique o firmware (aceita UTF-8) |
| Texto cortado no fim | linha > 32 bytes no `0x6E` | encurte ou use `strategy(PER_LINE_PACKETS)` |
| Nada aparece | endereço errado ou baudrate | confira `deviceAddress` e a configuração RS485 da câmera |
| Semáforo não volta ao vermelho | tempo 0 ou > 255 | use 1..255 s |
| Só a 4ª linha atualiza | pacote `0x62` isolado | é o comportamento esperado em atualização parcial; use 4 linhas para refresh completo |
| `PliProtocolException` | texto/pacote grande demais | veja a mensagem: ela informa o limite |

---

## 14. Licença

[MIT](LICENSE) © Alpha Digi Brazil. Use, modifique e redistribua livremente, inclusive em
produtos comerciais; mantenha o aviso de copyright.

Contribuições são bem-vindas — veja [CONTRIBUTING.md](CONTRIBUTING.md).

> Este projeto é uma implementação independente do protocolo de comunicação do controlador
> PLI2020P. Não é afiliado ao fabricante do painel. O documento de protocolo do fabricante
> **não** é redistribuído aqui; a referência em [docs/PROTOCOL.md](docs/PROTOCOL.md) descreve
> apenas o necessário para integração.
