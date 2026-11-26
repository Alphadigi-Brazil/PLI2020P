"""PLI2020P - mensagens do painel de LED (arquivo unico, Python 3.8+, sem dependencias).

Gera o Base64 e o ``dataLen`` que a camera LPR espera no campo ``serialData`` da resposta
HTTP, a partir das 4 linhas de texto, da cor de cada linha e do tempo de semaforo verde.

Uso basico::

    from pli2020p import encode, json_array

    quadros = encode(
        ["`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"],
        ["GREEN", "GREEN", "YELLOW", "GREEN"],
        green_seconds=5,
    )

    quadros[0].base64      # "AGT//25SAAQ..."
    quadros[0].data_len    # 90
    json_array(quadros)    # '"serialData":[{...},{...}]'

Colunas do painel: 0 = primeira linha (topo) ... 3 = quarta linha.
``None`` em uma posicao mantem a linha como esta; ``""`` apaga a linha.

Cores aceitas: ``"RED"``, ``"GREEN"``, ``"YELLOW"``, ``"OFF"`` (ou ``"BLACK"``),
``"#RRGGBB"`` e tuplas ``(r, g, b)``.

Data/hora: use os marcadores ``Y`` ano, ``M`` mes, ``D`` dia, ``V`` dia da semana,
``H`` hora, ``N`` minuto, ``S`` segundo (sempre com acento grave antes da letra).
O painel atualiza sozinho, nao e preciso reenviar.

Auto-teste::

    python pli2020p.py --selftest
"""

import base64 as _base64

__version__ = "1.1.0"

__all__ = ["Frame", "encode", "json_array", "crc16", "parse_color"]

_CMD_RELAY = 0x0F
_CMD_TEMP_TEXT = 0x62
_CMD_MULTI_LINE = 0x6E

_MAX_MULTI_LINE_TEXT = 32      # limite recomendado do comando 0x6E
_MAX_SINGLE_LINE_TEXT = 236    # 19 bytes de cabecalho + texto, dentro de 255

_COLORS = {
    "RED": (255, 0, 0),
    "GREEN": (0, 255, 0),
    "YELLOW": (255, 255, 0),
    "AMBER": (255, 255, 0),
    "ORANGE": (255, 255, 0),
    "OFF": (0, 0, 0),
    "BLACK": (0, 0, 0),
    "NONE": (0, 0, 0),
}


class Frame:
    """Um quadro RS485 pronto para enviar (texto ou semaforo)."""

    __slots__ = ("_raw",)

    def __init__(self, raw: bytes) -> None:
        self._raw = bytes(raw)

    @property
    def raw(self) -> bytes:
        """Os bytes do quadro."""
        return self._raw

    @property
    def base64(self) -> str:
        """O que vai no campo ``data`` da camera."""
        return _base64.b64encode(self._raw).decode("ascii")

    @property
    def data_len(self) -> int:
        """O que vai no campo ``dataLen`` (quantidade de bytes)."""
        return len(self._raw)

    def hex(self, sep: str = " ") -> str:
        """Bytes em hexadecimal, para conferir no analisador serial."""
        return sep.join(f"{byte:02X}" for byte in self._raw)

    def json(self, serial_channel: int = 0) -> str:
        """Um elemento ``serialData``."""
        return (
            '{"serialChannel":%d,"data":"%s","dataLen":%d}'
            % (serial_channel, self.base64, self.data_len)
        )

    def __len__(self) -> int:
        return len(self._raw)

    def __eq__(self, other: object) -> bool:
        return isinstance(other, Frame) and other._raw == self._raw

    def __hash__(self) -> int:
        return hash(self._raw)

    def __repr__(self) -> str:
        return "Frame(dataLen=%d, data=%r)" % (self.data_len, self.base64)


def parse_color(color) -> tuple:
    """Converte ``"RED"``, ``"#FF8800"`` ou ``(r, g, b)`` em uma tupla RGB."""
    if color is None:
        return _COLORS["RED"]
    if isinstance(color, (tuple, list)):
        if len(color) != 3:
            raise ValueError("cor deve ser (r, g, b), recebi %r" % (color,))
        rgb = tuple(int(c) for c in color)
        if any(c < 0 or c > 255 for c in rgb):
            raise ValueError("componentes de cor devem estar entre 0 e 255: %r" % (color,))
        return rgb
    if not isinstance(color, str):
        raise ValueError("cor invalida: %r" % (color,))

    nome = color.strip().upper()
    if not nome:
        return _COLORS["RED"]
    if nome in _COLORS:
        return _COLORS[nome]
    if nome.startswith("#"):
        nome = nome[1:]
    if len(nome) == 6:
        try:
            valor = int(nome, 16)
        except ValueError:
            pass
        else:
            return ((valor >> 16) & 0xFF, (valor >> 8) & 0xFF, valor & 0xFF)
    raise ValueError(
        "cor desconhecida: %r (use RED, GREEN, YELLOW, OFF ou #RRGGBB)" % (color,)
    )


def crc16(data: bytes, offset: int = 0, length: int = None) -> int:
    """CRC-16/MODBUS (poly 0xA001, init 0xFFFF), usado em todos os quadros."""
    if length is None:
        length = len(data) - offset
    crc = 0xFFFF
    for byte in data[offset:offset + length]:
        crc ^= byte
        for _ in range(8):
            crc = ((crc >> 1) ^ 0xA001) if (crc & 1) else (crc >> 1)
    return crc & 0xFFFF


def encode(texts, colors=None, green_seconds: int = 0, encoding: str = "utf-8"):
    """Gera os quadros: primeiro o texto, depois o semaforo (se ``green_seconds`` > 0).

    :param texts: 1 a 4 linhas (lista, tupla ou uma string). ``None`` mantem a linha.
    :param colors: cor de cada linha; ``None`` = vermelho.
    :param green_seconds: 0 = nao mexe no semaforo; 1..255 = verde por N segundos.
    :param encoding: codificacao do texto; ``"utf-8"`` (padrao) ou ``"gbk"`` para chines.
    :return: lista de :class:`Frame`.
    """
    if isinstance(texts, str):
        texts = [texts]
    if texts is None or len(texts) == 0:
        raise ValueError("informe pelo menos uma linha de texto")
    if len(texts) > 4:
        raise ValueError(
            "o painel tem 4 linhas, recebi %d" % len(texts)
        )
    if not isinstance(green_seconds, int) or not 0 <= green_seconds <= 255:
        raise ValueError("green_seconds deve ser 0..255, recebi %r" % (green_seconds,))

    linhas = []
    for indice, texto in enumerate(texts):
        if texto is None:
            continue
        cor = None
        if colors is not None and indice < len(colors):
            cor = colors[indice]
        linhas.append((indice, _clean_text(texto), parse_color(cor)))

    if not linhas and green_seconds == 0:
        raise ValueError("nada para enviar: nenhum texto e semaforo desligado")

    quadros = []
    if linhas:
        data = None
        if len(linhas) == 4:
            data = _build_multi_line(linhas, encoding)
        if data is not None and len(data) <= 255:
            quadros.append(Frame(_packet(_CMD_MULTI_LINE, data)))
        else:
            for linha in linhas:
                quadros.append(
                    Frame(_packet(_CMD_TEMP_TEXT, _build_single_line(linha, encoding)))
                )
    if green_seconds > 0:
        quadros.append(Frame(_relay_packet(green_seconds)))
    return quadros


def json_array(frames, serial_channel: int = 0) -> str:
    """Monta o trecho ``"serialData":[ ... ]`` da resposta HTTP."""
    itens = ",".join(frame.json(serial_channel) for frame in frames)
    return '"serialData":[' + itens + "]"


def to_json(frames, serial_channel: int = 0) -> str:
    """Resposta completa, pronta para retornar ao POST da camera."""
    return "{" + json_array(frames, serial_channel) + "}"


# --------------------------------------------------------------------- internos


def _clean_text(texto: str) -> str:
    if not isinstance(texto, str):
        texto = str(texto)
    return texto.replace("\r\n", " ").replace("\r", " ").replace("\n", " ").replace("\0", " ")


def _build_multi_line(linhas, encoding):
    """Comando 0x6E: as 4 linhas em um pacote. ``None`` se alguma linha for grande demais."""
    data = bytearray()
    data.append(0x00)              # SAVE_FLAG: area temporaria
    data.append(len(linhas))       # TEXT_CONTEXT_NUMBER
    for posicao, (indice, texto, cor) in enumerate(linhas):
        conteudo = texto.encode(encoding)
        if len(conteudo) > _MAX_MULTI_LINE_TEXT:
            return None            # cai para 0x62 linha a linha
        data.append(indice)        # LID
        data.append(0x00)          # DM: exibe imediatamente
        data.append(0x01)          # DS: velocidade
        data.append(0x05)          # DT: permanencia (s)
        data.append(0x00)          # DR: repeticoes (0 = infinito)
        data.extend(cor)           # TC
        data.append(0x00)          # A
        data.append(len(conteudo))  # TL (1 byte no 0x6E)
        data.extend(conteudo)
        data.append(0x00 if posicao == len(linhas) - 1 else 0x0D)
    return bytes(data)


def _build_single_line(linha, encoding):
    """Comando 0x62: uma linha por pacote."""
    indice, texto, cor = linha
    conteudo = texto.encode(encoding)
    if len(conteudo) > _MAX_SINGLE_LINE_TEXT:
        raise ValueError(
            "linha %d tem %d bytes; o maximo e %d"
            % (indice + 1, len(conteudo), _MAX_SINGLE_LINE_TEXT)
        )
    data = bytearray()
    data.append(indice)            # TWID
    data.append(0x00)              # ETM
    data.append(0x01)              # ETS
    data.append(0x00)              # DM (reservado)
    data.append(0x05)              # DT
    data.append(0x00)              # EXM
    data.append(0x01)              # EXS
    data.append(0x03)              # FINDEX (fixo)
    data.append(0x00)              # DRS
    data.extend(cor)               # TC
    data.append(0x00)              # A (reservado)
    data.extend(b"\x00\x00\x00\x00")  # BC (fundo preto)
    data.append(len(conteudo) & 0xFF)         # TL (2 bytes no 0x62)
    data.append((len(conteudo) >> 8) & 0xFF)
    data.extend(conteudo)
    return bytes(data)


def _relay_packet(segundos: int) -> bytes:
    data = bytes([0x00, 0x01, 0x00, 0x00, 0x00, segundos & 0xFF])
    return _packet(_CMD_RELAY, data)


def _packet(comando: int, data: bytes) -> bytes:
    """DA + VR + PN[2] + CMD + DL + DATA + CRC[2]."""
    if len(data) > 255:
        raise ValueError("pacote com %d bytes; o maximo e 255" % len(data))
    quadro = bytearray()
    quadro.append(0x00)            # DA
    quadro.append(0x64)            # VR (protocolo v1)
    quadro.extend(b"\xFF\xFF")     # PN
    quadro.append(comando)         # CMD
    quadro.append(len(data))       # DL
    quadro.extend(data)
    crc = crc16(quadro)
    quadro.append(crc & 0xFF)
    quadro.append((crc >> 8) & 0xFF)
    return bytes(quadro)


# --------------------------------------------------------------------- demo/teste


def _hex(texto: str) -> bytes:
    return bytes.fromhex(texto)


def _selftest() -> None:  # pragma: no cover - executado pela linha de comando
    def esperar(esperado, obtido, nome):
        if esperado != obtido:
            raise SystemExit(
                "FALHOU %s\n  esperado: %r\n  obtido  : %r" % (nome, esperado, obtido)
            )
        print("   ok " + nome)

    print("-- quadros de texto (4 linhas + semaforo 5 s)")
    quadros = encode(
        ["`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"],
        ["GREEN", "GREEN", "YELLOW", "GREEN"],
        green_seconds=5,
    )
    esperar(2, len(quadros), "quantidade de quadros")
    esperar(
        "AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2Eg"
        "OTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o",
        quadros[0].base64,
        "base64 do texto",
    )
    esperar(90, quadros[0].data_len, "dataLen do texto")
    esperar("AGT//w8GAAEAAAAFJLc=", quadros[1].base64, "base64 do semaforo")
    esperar(14, quadros[1].data_len, "dataLen do semaforo")

    print("-- quadro 0x62 de uma linha (Bemvindo, amarelo)")
    um = encode([None, None, "Bemvindo"], [None, None, "YELLOW"])
    esperar(
        "00 64 FF FF 62 1B 02 00 01 00 05 00 01 03 00 FF FF 00 00 00 00 00 00 08 00 "
        "42 65 6D 76 69 6E 64 6F 87 30",
        um[0].hex(),
        "hex 0x62",
    )
    esperar(35, um[0].data_len, "dataLen 0x62")

    print("-- CRC-16 dos quadros de referencia")
    esperar(0xB724, crc16(_hex("00 64 FF FF 0F 06 00 01 00 00 00 05")), "crc semaforo 5 s")

    print("-- limites")
    esperar(1, len(encode(["A", "B", "C", "D"])), "4 linhas = 1 pacote")
    esperar(2, len(encode([None, None, "A", "B"])), "2 linhas = 2 pacotes")
    esperar(4, len(encode(["x" * 33] * 4)), "linhas longas = 4 pacotes")

    try:
        encode(["x" * 250])
    except ValueError:
        print("   ok texto grande demais e rejeitado")
    else:
        raise SystemExit("FALHOU: texto grande demais deveria ser rejeitado")

    print("\nSELFTEST OK")


def _demo() -> None:  # pragma: no cover
    quadros = encode(
        ["`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"],
        ["GREEN", "GREEN", "YELLOW", "GREEN"],
        green_seconds=5,
    )
    print("PLI2020P - demonstracao")
    for indice, quadro in enumerate(quadros):
        print("serialData[%d]  dataLen=%d" % (indice, quadro.data_len))
        print("  hex : " + quadro.hex())
        print("  data: " + quadro.base64)
    print()
    print(to_json(quadros))


if __name__ == "__main__":
    import sys

    if "--selftest" in sys.argv:
        _selftest()
    else:
        _demo()
