# Como contribuir

Obrigado pelo interesse! Este projeto é usado por integradores de campo, então
**compatibilidade** e **rastreabilidade dos bytes** valem mais do que elegância.

## Boas-vindas

- Correções de protocolo, suporte a novos comandos do controlador, melhorias de documentação.
- Novos testes com quadros reais capturados de instalações são especialmente valiosos.
- Relatos de incompatibilidade com firmwares diferentes do painel.

## Antes de abrir um PR

1. Abra uma *issue* descrevendo o problema (com o hex dump do quadro, se possível).
2. Rode a suíte localmente:

   ```bash
   ./mvnw verify
   ```

3. Mantenha o estilo do código existente:
   - **Java 8** (sem `var`, `record`, `List.of`, etc.);
   - **zero dependências em runtime** — JUnit só em `test`;
   - nomes de campos do protocolo em maiúsculas (`DA`, `VR`, `DL`, `TC`...), texto explicativo
     em português ou inglês;
   - todo método público precisa de Javadoc curto.

4. Se o comportamento gerado mudar, atualize `docs/PROTOCOL.md`, o `README.md` e o `CHANGELOG.md`.

## Testes

Um teste só é aceito se disser **de onde veio o valor esperado**: captura de campo, exemplo do
fabricante ou cálculo documentado. Evite "golden files" gerados pela própria biblioteca sem
uma segunda fonte independente.

## Commits

Mensagens no imperativo, curtas, em português ou inglês:

```text
corrige TL de 1 byte no comando 0x6E
add fallback to 0x62 for long lines
```

## Licença

Ao contribuir você concorda em licenciar o código sob a [licença MIT](LICENSE) do projeto.
