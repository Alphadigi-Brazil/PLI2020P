# Referência do protocolo (o que esta biblioteca gera)

Documento resumido, escrito a partir de capturas de uma instalação em funcionamento e das
observações práticas de campo. Serve para quem precisa depurar no analisador serial ou
portar a lógica para outra linguagem. O documento original do fabricante não é redistribuído.

Todos os campos multibyte são **little-endian** (byte menos significativo primeiro).

---

## 1. Estrutura do quadro

| Offset | Tamanho | Campo | Conteúdo |
|---|---|---|---|
| 0 | 1 | `DA` | endereço do painel (0x00..0xFF) |
| 1 | 1 | `VR` | versão do protocolo: `0x64` (v1) ou `0xC8` (v2) |
| 2 | 2 | `PN` | sequência do pacote; `FF FF` = pacote único/último |
| 4 | 1 | `CMD` | comando (`0x6E`, `0x62`, `0x0F`, ...) |
| 5 | 1 ou 2 | `DL` | tamanho do `DATA` (1 byte na v1, 2 bytes na v2) |
| 5+DL | `DL` | `DATA` | carga do comando |
| fim-2 | 2 | `CRC` | CRC-16 do `DA` até o último byte do `DATA` |

Tamanho total = `6 + DL + 2` (v1) ou `7 + DL + 2` (v2).

---

## 2. CRC-16

CRC-16/MODBUS: polinômio `0xA001` (reflexo de `0x8005`), inicial `0xFFFF`, sem xor final,
enviado com o **byte baixo primeiro**.

```java
static int crc16(byte[] buf, int off, int len) {
    int crc = 0xFFFF;
    for (int i = off; i < off + len; i++) {
        crc ^= buf[i] & 0xFF;
        for (int bit = 0; bit < 8; bit++) {
            crc = ((crc & 1) != 0) ? (crc >>> 1) ^ 0xA001 : crc >>> 1;
        }
    }
    return crc & 0xFFFF;
}
```

Conferência rápida: `00 64 FF FF 0F 06 00 01 00 00 00 05` → CRC `B7 24` (valor `0xB724`).

---

## 3. `0x6E` — várias linhas em um pacote

Usado para atualizar as 4 linhas de uma vez, com cor independente por linha. Máximo de 255
bytes de `DATA` (protocolo v1).

```text
DL | SAVE_FLAG(1) | NUM(1) | LINHA × NUM | [voz] | CRC
```

`SAVE_FLAG`: `0x00` área temporária (padrão), `0x01` grava na memória permanente.
`NUM`: quantidade de linhas (1..4).

Cada linha:

| Campo | Tam. | Valor |
|---|---|---|
| `LID` | 1 | número da linha: 0 = topo … 3 = base |
| `DM` | 1 | modo de exibição (tabela na seção 6) |
| `DS` | 1 | velocidade 1..32 |
| `DT` | 1 | permanência em segundos 0..255 |
| `DR` | 1 | repetições 0..255 (0 = infinito) |
| `TC` | 4 | cor R, G, B, A (A reservado = 0) |
| `TL` | **1** | tamanho do texto em bytes |
| `TEXT` | `TL` | texto (UTF-8) |
| terminador | 1 | `0x0D` nas linhas intermediárias, `0x00` na última |

Bloco de voz (opcional):

| Campo | Tam. | Valor |
|---|---|---|
| `VF` | 1 | fixo `0x0A` |
| `VTL` | 1 | tamanho da frase |
| `VOICE` | `VTL` | texto da voz |
| terminador | 1 | `0x00` |

> ⚠️ **Ponto que o documento do fabricante não deixa claro:** no `0x6E` o `TL` tem **1 byte**.
> No `0x62` ele tem 2 bytes. Errar isso desloca todo o resto do quadro e o CRC não fecha.
> Confirmado em quadros reais: `... 00 00 01 05 00 FF 00 00 00 08 60 48 3A ...`, onde `08` é o
> tamanho e o texto começa imediatamente depois.

---

## 4. `0x62` — uma linha por pacote

Usado para atualização parcial ou quando a linha é maior do que o `0x6E` aceita.

```text
DL | TWID | ETM | ETS | DM | DT | EXM | EXS | FINDEX | DRS | TC[4] | BC[4] | TL[2] | TEXT | CRC
```

| Campo | Tam. | Valor |
|---|---|---|
| `TWID` | 1 | linha 0..3 |
| `ETM` | 1 | modo de entrada |
| `ETS` | 1 | velocidade de entrada 1..32 |
| `DM` | 1 | reservado, `0x00` |
| `DT` | 1 | permanência 0..255 s |
| `EXM` | 1 | modo de saída |
| `EXS` | 1 | velocidade de saída 1..32 |
| `FINDEX` | 1 | fixo `0x03` |
| `DRS` | 1 | repetições (0 = infinito) |
| `TC` | 4 | cor R, G, B, A |
| `BC` | 4 | cor de fundo (preto = `00 00 00 00`) |
| `TL` | 2 | tamanho do texto (little-endian) |
| `TEXT` | `TL` | texto (UTF-8) |

`DL = 19 + TL`. Texto máximo: 236 bytes.

---

## 5. `0x0F` — relé / semáforo

```text
DL = 06 | 00 01 00 00 00 | OT | CRC
```

`OT` = tempo em segundos (1..255). O painel liga o relé e o desliga sozinho ao final — é
assim que o semáforo fica verde por N segundos e volta ao vermelho sem um segundo comando.

> ⚠️ O documento do fabricante diz que os 5 bytes reservados são todos `0x00`, mas todos os
> quadros capturados usam `00 01 00 00 00`. Esta biblioteca envia o valor observado em campo.

---

## 6. Modos de exibição (`DM` / `ETM` / `EXM`)

| Código | Modo |
|---|---|
| `0x00` | imediato (sem movimento) |
| `0x01` | entra da direita para a esquerda |
| `0x02` | entra da esquerda para a direita |
| `0x03` | entra de baixo para cima |
| `0x04` | entra de cima para baixo |
| `0x05` / `0x06` | janela desce / sobe |
| `0x07` / `0x08` | janela abre para a esquerda / direita |
| `0x0D` | letra por letra |
| `0x15` | rolagem contínua para a esquerda |

---

## 7. Cores

`TC` é RGBA. No painel vermelho/verde só R e G acendem:

| Cor | `TC` |
|---|---|
| vermelho | `FF 00 00 00` |
| verde | `00 FF 00 00` |
| amarelo / âmbar | `FF FF 00 00` |
| apagado | `00 00 00 00` |

---

## 8. Exemplo completo (caso de aceitação)

Pedido: 4 linhas (data verde, hora verde, "Vaga 999" amarelo, "Bem-vindo!" verde) + semáforo
verde por 5 s.

Quadro de texto, 90 bytes:

```text
00 64 FF FF 6E 52 00 04
   00 00 01 05 00 00 FF 00 00 0A  60 44 2D 60 4D 2D 32 30 60 59  0D
   01 00 01 05 00 00 FF 00 00 08  60 48 3A 60 4E 3A 60 53        0D
   02 00 01 05 00 FF FF 00 00 08  56 61 67 61 20 39 39 39        0D
   03 00 01 05 00 00 FF 00 00 0A  42 65 6D 2D 76 69 6E 64 6F 21  00
5E 68
```

| Byte(s) | Leitura |
|---|---|
| `00 64 FF FF 6E 52` | DA=0, VR=v1, PN=FFFF, CMD=0x6E, DL=0x52=82 |
| `00 04` | área temporária, 4 linhas |
| `00 00 01 05 00 00 FF 00 00 0A` | linha 1, imediato, vel.1, 5 s, verde, texto de 10 bytes |
| `60 44 2D 60 4D 2D 32 30 60 59` | `` `D-`M-20`Y `` |
| `03 … 00` | linha 4, texto termina com `0x00` |
| `5E 68` | CRC |

Base64: `AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2EgOTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o`

Quadro do semáforo, 14 bytes:

```text
00 64 FF FF 0F 06 00 01 00 00 00 05 24 B7
```

Base64: `AGT//w8GAAEAAAAFJLc=`

---

## 9. Escapes de data/hora

O texto pode conter marcadores que o painel substitui pelo relógio interno:

| Marcador | Valor |
|---|---|
| `` `Y `` | ano |
| `` `M `` | mês |
| `` `D `` | dia |
| `` `V `` | dia da semana |
| `` `H `` | hora |
| `` `N `` | minuto |
| `` `S `` | segundo |
| `` `C `` | constante 20 |

O painel atualiza sozinho; não é necessário reenviar o quadro a cada segundo.

---

## 10. Observações de campo

1. **Acentuação:** os quadros reais usam UTF-8 (`á` = `C3 A1`, `í` = `C3 AD`), apesar de o
   documento citar GBK. Use UTF-8 para português.
2. **`TL` de 1 byte no `0x6E`** (ver seção 3) — a causa mais comum de CRC inválido ao
   implementar o protocolo do zero.
3. **Prefixo do relé** `00 01 00 00 00` (seção 5).
4. **`PN`** fica `FF FF` porque todos os comandos usados aqui cabem em um pacote; a biblioteca
   não faz fragmentação.
5. **`dataLen`** informado à câmera é o tamanho total do quadro em bytes (Base64 decodificado).
