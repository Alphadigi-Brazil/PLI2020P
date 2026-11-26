# PLI2020P — LED panel messages (Java)

[![build](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml/badge.svg)](https://github.com/Alphadigi-Brazil/PLI2020P/actions/workflows/build.yml)
[![license](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![java](https://img.shields.io/badge/java-8%2B-orange.svg)](pom.xml)

[Português](README.md) · **English**

Build messages for the **PLI2020P** LED panel (64×64, red/green, 4 text lines) without dealing
with the HEX protocol: give the text of each line, its colour and how long the traffic light
stays green; the library returns the **Base64** and **dataLen** the LPR camera expects in its
`serialData` field.

---

## Three ways to use it

All three produce exactly the same bytes.

| Option | For | What to do |
|---|---|---|
| **[Web calculator](https://alphadigi-brazil.github.io/PLI2020P/calculadora.html)** | try it without coding | type the lines, copy the JSON, paste it into your platform |
| **[Single Java file](standalone/Pli2020p.java)** | Java project without Maven | copy one file into your project and call `Pli2020p.encode(...)` |
| **[Maven](pom.xml)** | Java project with Maven | `mvn install` + depend on `br.com.alphadigi:pli2020p` |

> The web calculator is in Portuguese because that is the language of the field teams.

### 2. Single file (no Maven)

Copy [`standalone/Pli2020p.java`](standalone/Pli2020p.java) into a `pli2020p` folder inside your
`src` (the file already declares `package pli2020p;`):

```java
import pli2020p.Pli2020p;

String[] texts   = {"`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"};
String[] colours = {"GREEN", "GREEN", "YELLOW", "GREEN"};

List<Pli2020p.Frame> frames = Pli2020p.encode(texts, colours, 5); // 5 s of green

String json = "{" + Pli2020p.jsonArray(frames) + "}";
```

Self-check the file after copying:

```bash
java pli2020p.Pli2020p --selftest     # prints SELFTEST OK
```

### 3. Maven

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
PliDisplayRequest request = PliDisplayRequest.builder()
        .line(0, "`D-`M-20`Y", PliColor.GREEN)
        .line(1, "`H:`N:`S",   PliColor.GREEN)
        .line(2, "Vaga 999",   PliColor.YELLOW)
        .line(3, "Bem-vindo!", PliColor.GREEN)
        .greenLightSeconds(5)
        .build();

List<SerialData> frames = Pli2020pEncoder.encode(request);
String json = "{" + Pli2020pEncoder.toJsonArray(frames) + "}";
```

---

## What you get

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

- first element: the four text lines, sent together in one packet;
- second element: the green light pulse;
- `dataLen` is the number of **HEX bytes**, not the Base64 string length.

```
 ┌───────────────────────┐   HTTP POST (OCR result)        ┌──────────────┐
 │  Parking / access     │ ──────────────────────────────►  │   LPR camera │
 │  control platform     │                                  │              │
 │                       │ ◄──────────────────────────────  │              │
 └───────────────────────┘   HTTP 200 + "serialData"        └──────┬───────┘
          │                                                        │ RS485 (HEX)
          │  this library builds the frames                         │
          ▼                                                        ▼
   Base64 + dataLen                                       ┌──────────────────┐
                                                          │   PLI2020P panel │
                                                          └──────────────────┘
```

---

## Colours

The panel is red/green; only R and G light up.

| Colour | Name | RGBA |
|---|---|---|
| red | `"RED"` / `PliColor.RED` | 255, 0, 0, 0 |
| green | `"GREEN"` / `PliColor.GREEN` | 0, 255, 0, 0 |
| yellow / amber | `"YELLOW"` / `PliColor.YELLOW` | 255, 255, 0, 0 |
| off | `"OFF"` / `PliColor.BLACK` | 0, 0, 0, 0 |

`#RRGGBB` is also accepted (`PliColor.parse("#FF8800")`).

---

## Date and time

The panel has its own RTC; put markers in the text and it keeps them updated:

| Marker | Value |
|---|---|
| `` `Y `` | year |
| `` `M `` | month |
| `` `D `` | day |
| `` `V `` | weekday |
| `` `H `` | hour |
| `` `N `` | minute |
| `` `S `` | second |
| `` `C `` | constant 20 |

`` `D-`M-20`Y `` → `26-11-2026` · `` `H:`N:`S `` → `09:43:27`

---

## Accents and language

Frames captured in the field use **UTF-8** (`á` = `C3 A1`, `í` = `C3 AD`), so UTF-8 is the
default and Portuguese accents work. Use GBK only when the panel is set up for Simplified
Chinese; GBK cannot represent `ç`, `ã`, `á`.

```java
PliDisplayRequest.builder().textEncoding(TextEncoding.GBK)
```

---

## Display modes

`DisplayLine.of(...)` = static text; `DisplayLine.scrolling(...)` = continuous left scroll.
Fine control: `new DisplayLine(line, text, colour, mode, speed, dwell, repeats)`.
Modes: `IMMEDIATE` 0x00, `MOVE_RIGHT_TO_LEFT` 0x01, `MOVE_LEFT_TO_RIGHT` 0x02,
`MOVE_BOTTOM_TO_TOP` 0x03, `MOVE_TOP_TO_BOTTOM` 0x04, `PULL_*` 0x05–0x08,
`TYPEWRITER` 0x0D, `CONTINUOUS_LEFT` 0x15.

---

## How packets are chosen

| Strategy | Result |
|---|---|
| `AUTO` (default) | 4 lines → one `0x6E` packet; 1–3 lines → one `0x62` packet per line |
| `MULTI_LINE_PACKET` | force one `0x6E` packet |
| `PER_LINE_PACKETS` | force one `0x62` packet per line |

A full-screen refresh goes out as a single packet, so the panel never shows half-new, half-old
content. Lines longer than 32 bytes (or packets above 255 bytes) fall back to `0x62`
automatically; if even that does not fit, `PliProtocolException` explains the limit.

---

## Limits

| Item | Limit |
|---|---|
| Lines | 4 (0..3) |
| Text per line in `0x6E` | 255 bytes (recommended ≤ 32) |
| Text per line in `0x62` | 236 bytes |
| Total payload | 255 bytes (protocol v1) |
| Speed | 1..32 |
| Dwell | 0..255 s |
| Green light | 1..255 s |

The panel turns the relay off by itself when the time expires.

---

## Field test without the camera

1. Open the [calculator](https://alphadigi-brazil.github.io/PLI2020P/calculadora.html), build the
   message and click **Copiar HEX**.
2. Connect a **USB-RS485** adapter to the panel (A to A, B to B — swap if it does not work).
3. Use a serial terminal at the **baud rate configured in the panel**, 8N1, and send the hex bytes.
4. The text should appear and the traffic light should turn green for the configured time.

If nothing shows up, check in this order: 5 V power → A/B swapped → baud rate → panel address
(default `0x00`).

---

## Building and testing

```bash
mvn verify                                                        # 18 tests
java -cp target/classes br.com.alphadigi.pli2020p.demo.PliDemo    # prints the frames
java -cp target/standalone pli2020p.Pli2020p --selftest           # single-file version
node tools/check-calculator.js                                    # calculator == Java
```

CI runs everything on Java 8, 11, 17 and 21 ([workflow](.github/workflows/build.yml)).
Tests compare the output byte by byte with frames captured from real installations.

---

## Protocol reference

See [docs/PROTOCOL.md](docs/PROTOCOL.md) (Portuguese) for frame layout, CRC-16/MODBUS, the
`0x6E` / `0x62` / `0x0F` commands and a fully annotated example.

---

## License

[MIT](LICENSE) © Alpha Digi Brazil. Independent implementation of the PLI2020P controller
protocol; not affiliated with the panel manufacturer.
