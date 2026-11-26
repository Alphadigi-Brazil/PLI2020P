#!/usr/bin/env node
/*
 * Verifica que a calculadora (docs/calculadora.html) gera exatamente os mesmos bytes que o
 * codificador Java. Roda o <script> da página fora do navegador, com um DOM mínimo.
 *
 *   node tools/check-calculator.js [caminho/para/calculadora.html]
 */
"use strict";

const fs = require("fs");
const path = require("path");
const vm = require("vm");

const arquivo = process.argv[2] || path.join(__dirname, "..", "docs", "calculadora.html");
const html = fs.readFileSync(arquivo, "utf8");
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];

// Valores iniciais iguais aos do HTML (linhas 1-4 verdes/amarelas e semáforo 5 s).
const seeds = {
  t0: "`D-`M-20`Y", c0: "GREEN",
  t1: "`H:`N:`S", c1: "GREEN",
  t2: "Vaga 999", c2: "YELLOW",
  t3: "Bem-vindo!", c3: "GREEN",
  green: "5"
};

const els = {};
function el(id) {
  if (!els[id]) {
    els[id] = {
      id,
      value: Object.prototype.hasOwnProperty.call(seeds, id) ? seeds[id] : "",
      textContent: "",
      style: {},
      dataset: {}
    };
  }
  return els[id];
}

const ctx = {
  document: {
    getElementById: el,
    addEventListener() {},
    createElement: () => el("tmp" + Object.keys(els).length),
    body: { appendChild() {}, removeChild() {} }
  },
  navigator: {},
  TextEncoder,
  btoa,
  setTimeout,
  console
};

vm.createContext(ctx);
vm.runInContext(script, ctx);

const esperadoTexto = "AGT//25SAAQAAAEFAAD/AAAKYEQtYE0tMjBgWQ0BAAEFAAD/AAAIYEg6YE46YFMNAgABBQD//wAACFZhZ2Eg"
  + "OTk5DQMAAQUAAP8AAApCZW0tdmluZG8hAF5o";
const esperadoSemaforo = "AGT//w8GAAEAAAAFJLc=";

let ok = true;
function conferir(nome, esperado, obtido) {
  if (esperado !== obtido) {
    console.log("FALHA " + nome + "\n  esperado: " + esperado + "\n  obtido  : " + obtido);
    ok = false;
  } else {
    console.log("ok   " + nome);
  }
}

conferir("4 linhas + semaforo", esperadoTexto, els["b64Texto"].textContent);
conferir("semaforo 5 s", esperadoSemaforo, els["b64Semaforo"].textContent);
conferir("dataLen do texto", "dataLen = 90", els["metaTexto"].textContent);
conferir("dataLen do semaforo", "dataLen = 14", els["metaSemaforo"].textContent);

// Cenario 2: uma unica linha (deve cair no comando 0x62).
el("t0").value = "";
el("t1").value = "";
el("t2").value = "Bemvindo";
el("c2").value = "YELLOW";
el("t3").value = "";
el("green").value = "0";
vm.runInContext("atualizar();", ctx);

const hexUma = Buffer.from(els["b64Texto"].textContent, "base64")
  .toString("hex").toUpperCase().match(/../g).join(" ");
conferir("uma linha (0x62)",
  "00 64 FF FF 62 1B 02 00 01 00 05 00 01 03 00 FF FF 00 00 00 00 00 00 08 00 42 65 6D 76 69 6E 64 6F 87 30",
  hexUma);

console.log(ok ? "\nCALCULADORA OK" : "\nCALCULADORA COM DIVERGENCIA");
process.exit(ok ? 0 : 1);
