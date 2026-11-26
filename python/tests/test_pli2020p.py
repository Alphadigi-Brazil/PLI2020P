# -*- coding: utf-8 -*-
"""Testes do modulo pli2020p (rodar com ``python -m unittest discover -s python/tests``).

Os valores esperados vem de quadros capturados de instalacoes reais e conferidos contra a
implementacao Java do mesmo repositorio (mesmos bytes, byte a byte).
"""

import base64
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from pli2020p import Frame, crc16, encode, json_array, parse_color, to_json  # noqa: E402

ACEITACAO_TEXTO = (
    "AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2Eg"
    "OTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o"
)
ACEITACAO_SEMAFORO = "AGT//w8GAAEAAAAFJLc="


class TestAceitacao(unittest.TestCase):
    def test_quatro_linhas_mais_semaforo(self):
        quadros = encode(
            ["`D-`M-20`Y", "`H:`N:`S", "Vaga 999", "Bem-vindo!"],
            ["GREEN", "GREEN", "YELLOW", "GREEN"],
            green_seconds=5,
        )
        self.assertEqual(2, len(quadros))
        self.assertEqual(ACEITACAO_TEXTO, quadros[0].base64)
        self.assertEqual(90, quadros[0].data_len)
        self.assertEqual(ACEITACAO_SEMAFORO, quadros[1].base64)
        self.assertEqual(14, quadros[1].data_len)

    def test_primeiro_quadro_e_o_comando_0x6e(self):
        quadros = encode(["A", "B", "C", "D"])
        self.assertEqual(0x6E, quadros[0].raw[4])

    def test_duas_linhas_parciais(self):
        quadros = encode(
            [None, None, "Vaga 999", "Bem-vindo!"],
            [None, None, "YELLOW", "GREEN"],
        )
        self.assertEqual(2, len(quadros))
        self.assertEqual(0x62, quadros[0].raw[4])
        self.assertEqual(
            "AGT//2IbAgABAAUAAQMA//8AAAAAAAAIAFZhZ2EgOTk5qYQ=", quadros[0].base64
        )
        self.assertEqual(35, quadros[0].data_len)
        self.assertEqual(
            "AGT//2IdAwABAAUAAQMAAP8AAAAAAAAKAEJlbS12aW5kbyGdAQ==", quadros[1].base64
        )
        self.assertEqual(37, quadros[1].data_len)


class TestQuadros(unittest.TestCase):
    def test_linha_unica_0x62(self):
        quadros = encode([None, None, "Bemvindo"], [None, None, "YELLOW"])
        self.assertEqual(1, len(quadros))
        self.assertEqual(
            "00 64 FF FF 62 1B 02 00 01 00 05 00 01 03 00 FF FF 00 00 00 00 00 00 08 00 "
            "42 65 6D 76 69 6E 64 6F 87 30",
            quadros[0].hex(),
        )
        self.assertEqual(35, quadros[0].data_len)

    def test_semaforo_5s(self):
        quadros = encode([None], green_seconds=5)
        self.assertEqual("00 64 FF FF 0F 06 00 01 00 00 00 05 24 B7", quadros[0].hex())

    def test_semaforo_10s(self):
        quadros = encode([None], green_seconds=10)
        self.assertEqual("AGT//w8GAAEAAAAKZLM=", quadros[0].base64)

    def test_apagar_linha(self):
        quadros = encode([None, "", None, None], [None, "OFF", None, None])
        self.assertEqual(1, len(quadros))
        self.assertEqual("AGT//2ITAQABAAUAAQMAAAAAAAAAAAAAAIZ/", quadros[0].base64)

    def test_sem_nada_para_enviar(self):
        with self.assertRaises(ValueError):
            encode([])


class TestTexto(unittest.TestCase):
    def test_acentos_em_utf8(self):
        quadros = encode(["Ação"], ["RED"])
        self.assertEqual(33, quadros[0].data_len)  # 19 de cabecalho + 6 bytes de texto + 8 de quadro
        # "Ação" em UTF-8 = 41 C3 A7 C3 A3 6F (6 bytes)
        self.assertIn(b"\x41\xc3\xa7\xc3\xa3\x6f", quadros[0].raw)

    def test_gbk_para_chines(self):
        quadros = encode(["欢迎光临请入场停车"], ["RED"], encoding="gbk")
        # 9 caracteres chineses = 18 bytes em GBK
        self.assertEqual(45, quadros[0].data_len)
        self.assertIn(bytes.fromhex("BB B6 D3 AD B9 E2 C1 D9"), quadros[0].raw)

    def test_controle_de_linha_vira_espaco(self):
        quadros = encode(["A\nB"], ["RED"])
        self.assertIn(b"A B", quadros[0].raw)

    def test_quatro_linhas_de_33_bytes_caem_para_0x62(self):
        quadros = encode(["x" * 33] * 4)
        self.assertEqual(4, len(quadros))
        for quadro in quadros:
            self.assertEqual(0x62, quadro.raw[4])

    def test_texto_longo_demais_e_rejeitado(self):
        with self.assertRaises(ValueError):
            encode(["x" * 250])

    def test_maximo_de_quatro_linhas(self):
        with self.assertRaises(ValueError):
            encode(["a", "b", "c", "d", "e"])

    def test_string_unica_e_aceita(self):
        quadros = encode("Bem-vindo!")
        self.assertEqual(1, len(quadros))
        self.assertEqual(0x62, quadros[0].raw[4])


class TestCrc(unittest.TestCase):
    def test_vetores_conhecidos(self):
        self.assertEqual(0xB724, crc16(bytes.fromhex("00 64 FF FF 0F 06 00 01 00 00 00 05")))
        self.assertEqual(0x3087, crc16(bytes.fromhex(
            "00 64 FF FF 62 1B 02 00 01 00 05 00 01 03 00 FF FF 00 00 00 00 00 00 08 00 "
            "42 65 6D 76 69 6E 64 6F")))

    def test_crc_do_quadro_gerado_bate(self):
        quadro = encode(["Vaga 999"], ["GREEN"])[0]
        self.assertEqual(crc16(quadro.raw[:-2]), quadro.raw[-2] | (quadro.raw[-1] << 8))


class TestSaida(unittest.TestCase):
    def test_data_len_e_o_tamanho_decodificado(self):
        for quadro in encode(["Vaga 999", "Bem-vindo!"], ["YELLOW", "GREEN"], green_seconds=3):
            self.assertEqual(len(base64.b64decode(quadro.base64)), quadro.data_len)

    def test_json_array(self):
        quadros = encode(["Vaga 999"], ["YELLOW"], green_seconds=5)
        texto = json_array(quadros)
        self.assertTrue(texto.startswith('"serialData":[{"serialChannel":0,"data":"'))
        self.assertIn('"dataLen":35', texto)
        self.assertEqual(2, texto.count('"serialChannel":0'))
        self.assertEqual("{" + texto + "}", to_json(quadros))

    def test_serial_channel_configuravel(self):
        quadros = encode(["A"], ["RED"])
        self.assertIn('"serialChannel":2', quadros[0].json(2))

    def test_igualdade_de_frames(self):
        self.assertEqual(encode(["A"], ["RED"])[0], encode(["A"], ["RED"])[0])


class TestCores(unittest.TestCase):
    def test_nomes(self):
        self.assertEqual((255, 0, 0), parse_color("RED"))
        self.assertEqual((0, 255, 0), parse_color("green"))
        self.assertEqual((255, 255, 0), parse_color(" Yellow "))
        self.assertEqual((0, 0, 0), parse_color("OFF"))
        self.assertEqual((255, 0, 0), parse_color(None))

    def test_hexadecimal(self):
        self.assertEqual((255, 136, 0), parse_color("#FF8800"))
        self.assertEqual((18, 52, 86), parse_color("123456"))

    def test_cor_invalida(self):
        with self.assertRaises(ValueError):
            parse_color("AZUL")


if __name__ == "__main__":
    unittest.main(verbosity=2)
