# PLI2020P — mensagens do painel de LED (Java)

[![build](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml/badge.svg)](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8%2B-orange.svg)](pom.xml)

**Português** · [English](README.en.md)

Monte as mensagens do painel de LED **PLI2020P** (64×64, vermelho/verde, 4 linhas) sem precisar
entender o protocolo HEX: você informa o texto de cada linha, a cor e o tempo de semáforo verde;
a biblioteca devolve o **Base64** e o **dataLen** que a câmera LPR espera no campo `serialData`.

---

## Comece por aqui

Três caminhos. Escolha o que combina com o seu caso — todos geram exatamente os mesmos bytes.

| Caminho | Para quem | O que fazer |
|---|---|---|
| **[Calculadora online](https://alphadigi-brazil.github.io/PLI2020P/calculadora.html)** | quer testar agora, sem programar | digite as linhas, copie o JSON e cole na sua plataforma |
| **[Um arquivo Java](standalone/Pli2020p.java)** | projeto Java sem Maven | copie 1 arquivo para o projeto e chame `Pli2020p.encode(...)` |
| **[Maven](pom.xml)** | projeto Java com Maven | `mvn install` + dependência `br.com.alphadigi:pli2020p` |

### 1. Calculadora (sem escrever código)

Abra **<https://alphadigi-brazil.github.io/PLI2020P/calculadora.html>** (funciona offline se você
baixar o arquivo `docs/calculadora.html`).

1. Preencha as 4 linhas e escolha a cor de cada uma;
2. informe os segundos de semáforo verde (0 = não mexe);
3. clique em **Copiar JSON** e cole na resposta HTTP da sua plataforma.

A calculadora mostra também o **HEX**, para conferir no analisador serial.

### 2. Um arquivo Java (recomendado para quem não usa Maven)

Copie [`standalone/Pli2020p.java`](standalone/Pli2020p.java) para uma pasta `pli2020p` dentro do
`src` do seu projeto — o arquivo já declara `package pli2020p;`. Depois:

```java
import pli2020p.Pli2020p;

String[] textos = {"`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"};
String[] cores  = {"GREEN", "GREEN", "YELLOW", "GREEN"};

List<Pli2020p.Frame> quadros = Pli2020p.encode(textos, cores, 5); // 5 s de verde

String json = "{" + Pli2020p.jsonArray(quadros) + "}";
// {"serialData":[{"serialChannel":0,"data":"AGT//25S...","dataLen":90}, ...]}
```

Sem dependências, sem build, Java 8. Para conferir que o arquivo está íntegro:

```bash
java pli2020p.Pli2020p --selftest     # imprime SELFTEST OK
```

### 3. Maven

O artefato não está publicado em repositório central; instale no seu repositório local:

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

```java
import br.com.alphadigi.pli2020p.*;
import java.util.List;

PliDisplayRequest request = PliDisplayRequest.builder()
        .line(0, "`D-`M-20`Y", PliColor.GREEN)    // linha 1: data  dd-mm-yyyy
        .line(1, "`H:`N:`S",   PliColor.GREEN)    // linha 2: hora  hh:nn:ss
        .line(2, "Vaga 999",   PliColor.YELLOW)   // linha 3: vagas livres
        .line(3, "Bem-vindo!", PliColor.GREEN)    // linha 4: mensagem
        .greenLightSeconds(5)                     // semáforo verde por 5 s
        .build();

List<SerialData> frames = Pli2020pEncoder.encode(request);
String json = "{" + Pli2020pEncoder.toJsonArray(frames) + "}";
```

---

## O resultado

Para o exemplo acima (data e hora em verde, vagas em amarelo, saudação em verde, 5 s de verde):

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

- o primeiro elemento é o texto (as 4 linhas vão juntas em um único pacote — nunca fica "meia tela nova");
- o segundo é o semáforo verde;
- `dataLen` é a quantidade de **bytes HEX**, não o tamanho da string Base64.

### Onde isso entra

```
 ┌───────────────────────┐   HTTP POST (resultado do OCR)   ┌──────────────┐
 │  Plataforma de        │ ──────────────────────────────►  │  Câmera LPR  │
 │  estacionamento /     │                                  │              │
 │  controle de acesso   │ ◄──────────────────────────────  │              │
 └───────────────────────┘   HTTP 200 + "serialData"        └──────┬───────┘
          │                                                        │ RS485 (HEX)
          │  esta biblioteca monta os pacotes                       │
          ▼                                                        ▼
   Base64 + dataLen                                       ┌──────────────────┐
                                                          │ Painel PLI2020P  │
                                                          │ 64×64, 4 linhas  │
                                                          └──────────────────┘
```

---

## Como testar no campo (sem a câmera)

Serve para validar o painel antes de mexer no software:

1. Abra a [calculadora](https://alphadigi-brazil.github.io/PLI2020P/calculadora.html), monte a
   mensagem e clique em **Copiar HEX**.
2. Ligue um conversor **USB-RS485** no painel (A com A, B com B — se não funcionar, inverta).
3. Abra um terminal serial (Hercules, RealTerm, `minicom`), use a **mesma velocidade configurada
   no painel** (a mesma que a câmera usa), 8N1, e envie os bytes em hexadecimal.
4. O texto deve aparecer e o semáforo deve ficar verde pelo tempo indicado.

Se não aparecer nada, confira nesta ordem: alimentação 5 V do painel → A/B invertidos →
velocidade (baudrate) → endereço do painel (padrão `0x00`).

---

## Cores

O painel é **vermelho/verde**; só os canais R e G acendem (o azul existe apenas nos modelos
coloridos da família).

| Cor | Nome | RGBA |
|---|---|---|
| vermelho | `"RED"` / `PliColor.RED` | 255, 0, 0, 0 |
| verde | `"GREEN"` / `PliColor.GREEN` | 0, 255, 0, 0 |
| amarelo / âmbar | `"YELLOW"` / `PliColor.YELLOW` | 255, 255, 0, 0 |
| apagado | `"OFF"` / `PliColor.BLACK` | 0, 0, 0, 0 |

Também aceita `#RRGGBB`, útil quando a cor vem de configuração:
`PliColor.parse("#FF8800")` ou `"#FF8800"` na versão de arquivo único.

---

## Data e hora sem ficar reenviando

O painel tem relógio próprio: escreva marcadores no texto e ele atualiza sozinho.

| Marcador | Vira |
|---|---|
| `` `Y `` | ano |
| `` `M `` | mês |
| `` `D `` | dia |
| `` `V `` | dia da semana |
| `` `H `` | hora |
| `` `N `` | minuto |
| `` `S `` | segundo |
| `` `C `` | constante 20 |

`` `D-`M-20`Y `` → `26-11-2026` · `` `H:`N:`S `` → `09:43:27`

---

## Acentuação e idioma

Os painéis em campo usam **UTF-8**: `á` vai como `C3 A1`, `í` como `C3 AD`. Por isso o padrão é
UTF-8 e o português com acento funciona.

Só troque para GBK se o painel for configurado para chinês simplificado:

```java
PliDisplayRequest.builder().textEncoding(TextEncoding.GBK)
```

GBK **não** representa `ç`, `ã`, `á` — não existe uma codificação única para chinês + português
na mesma mensagem.

---

## Modos de exibição

```java
DisplayLine.of(0, "Vaga 999", PliColor.YELLOW);         // texto fixo (padrão)
DisplayLine.scrolling(3, "Bem-vindo!", PliColor.GREEN); // rolagem contínua
```

Controle fino: `new DisplayLine(linha, texto, cor, modo, velocidade, permanência, repetições)`.

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
| `CONTINUOUS_LEFT` | 0x15 | rolagem contínua (letreiro) |

---

## Como os pacotes são escolhidos

| Estratégia | Resultado |
|---|---|
| `AUTO` (padrão) | 4 linhas → **1 pacote `0x6E`**; 1–3 linhas → **1 pacote `0x62` por linha**; com voz → `0x6E` |
| `MULTI_LINE_PACKET` | força 1 pacote `0x6E` (falha se não couber em 255 bytes) |
| `PER_LINE_PACKETS` | força 1 pacote `0x62` por linha |

Tela cheia vai em um pacote só; atualização parcial mexe apenas nas linhas informadas e deixa as
outras como estão. Linha acima de 32 bytes ou pacote acima de 255 bytes caem automaticamente
para `0x62`; se nem isso couber, é lançada `PliProtocolException` dizendo qual limite estourou.

---

## Referência rápida da API

| Classe | Para quê |
|---|---|
| `PliDisplayRequest.builder()` | descreve o que mostrar |
| `Pli2020pEncoder.encode(request)` | devolve `List<SerialData>` (texto primeiro, semáforo depois) |
| `Pli2020pEncoder.relayFrame(addr, ver, s)` | só o pacote do semáforo |
| `Pli2020pEncoder.toJsonArray(frames)` | monta `"serialData": [...]` |
| `SerialData` | `getData()`, `getDataLen()`, `getHex()`, `toJson()` |
| `PliFrameParser.parse(bytes)` | lê um quadro capturado e valida o CRC (diagnóstico) |

Arquivo único: `Pli2020p.encode(...)`, `Pli2020p.jsonArray(...)`,
`Frame.base64()`, `Frame.dataLen()`, `Frame.hex()`, `Frame.json()`.

| Parâmetro (Maven) | Padrão | Observação |
|---|---|---|
| `deviceAddress(int)` | `0x00` | endereço do painel |
| `protocolVersion(int)` | `0x64` | `0xC8` = protocolo v2 |
| `textEncoding(...)` | UTF-8 | ver acentuação |
| `saveToFlash(boolean)` | `false` | grava na memória permanente |
| `strategy(...)` | `AUTO` | ver pacotes |
| `greenLightSeconds(int)` | `0` | 0..255 |
| `voice(String)` | `null` | anúncio de voz (só `0x6E`) |

---

## Limites

| Item | Limite |
|---|---|
| Linhas | 4 (0..3) |
| Texto por linha no `0x6E` | 255 bytes (recomendado ≤ 32) |
| Texto por linha no `0x62` | 236 bytes |
| Payload total | 255 bytes (protocolo v1) |
| Velocidade | 1..32 |
| Permanência | 0..255 s |
| Semáforo verde | 1..255 s |

O painel desliga o relé sozinho ao fim do tempo e volta ao vermelho.

---

## Problemas comuns

| Sintoma | Causa provável | Solução |
|---|---|---|
| Acentos estranhos | painel em GBK | `textEncoding(TextEncoding.GBK)` se for chinês; senão verifique o firmware |
| Texto cortado | linha > 32 bytes no `0x6E` | encurte ou use `PER_LINE_PACKETS` |
| Nada aparece | endereço/baudrate/A-B | veja “Como testar no campo” |
| Semáforo não volta ao vermelho | tempo inválido | use 1..255 s |
| Só a última linha mudou | atualização parcial (`0x62`) | é o esperado; envie as 4 linhas para refresh completo |

---

## Desenvolvimento

```bash
mvn verify                                                        # 18 testes
java -cp target/classes br.com.alphadigi.pli2020p.demo.PliDemo    # imprime os quadros
java -cp target/standalone pli2020p.Pli2020p --selftest           # arquivo único
node tools/check-calculator.js                                    # calculadora == Java
```

CI roda tudo em Java 8, 11, 17 e 21 ([workflow](.github/workflows/build.yml)).

Os testes comparam a saída, byte a byte, com quadros capturados de instalações reais — não são
valores inventados.

---

## Estrutura

```
src/main/java/br/com/alphadigi/pli2020p/   biblioteca (9 classes)
src/test/java/...                          18 testes de regressão
standalone/Pli2020p.java                   versão de arquivo único
docs/calculadora.html                      calculadora offline
docs/PROTOCOL.md                           referência do protocolo
docs/index.html                            página inicial das ferramentas
tools/check-calculator.js                  CI: calculadora == Java
```

---

## Licença

[MIT](LICENSE) © Alpha Digi Brazil. Use, modifique e redistribua livremente, inclusive em
produtos comerciais; mantenha o aviso de copyright.

Contribuições: [CONTRIBUTING.md](CONTRIBUTING.md).

> Implementação independente do protocolo do controlador PLI2020P, sem vínculo com o fabricante
> do painel. O documento de protocolo do fabricante não é redistribuído aqui.
