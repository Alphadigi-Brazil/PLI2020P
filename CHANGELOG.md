# Changelog

Formato baseado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).
Versionamento [SemVer](https://semver.org/lang/pt-BR/).

## [1.0.0] - 2026-11-26

### Adicionado

- Codificação do comando `0x6E` (várias linhas em um pacote, cor por linha, voz opcional).
- Codificação do comando `0x62` (uma linha por pacote), usado para atualização parcial e
  como fallback de linhas longas.
- Codificação do comando `0x0F` (semáforo verde por N segundos).
- CRC-16/MODBUS e montagem completa do quadro `DA VR PN CMD DL DATA CRC`.
- Estratégias de empacotamento `AUTO`, `MULTI_LINE_PACKET` e `PER_LINE_PACKETS`.
- Cores `RED`, `GREEN`, `YELLOW`, `BLACK` e parser `#RRGGBB`.
- Codificação de texto UTF-8 (padrão) ou GBK.
- `SerialData` com Base64, `dataLen`, hex e JSON.
- `PliFrameParser` para validar quadros capturados no RS485.
- Suíte de regressão comparando byte a byte com quadros capturados em campo.
- Build Maven, wrapper e GitHub Actions em Java 8/11/17/21.
