/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/pt-br/pt-br.js
 *
 *  Copyright (c) 2009-2015 The MathJax Consortium
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

MathJax.Localization.addTranslation("pt-br",null,{
  menuTitle: "portugu\u00EAs do Brasil",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "O MathJax encontrou um cookie com configura\u00E7\u00F5es de usu\u00E1rio que inclui c\u00F3digo a ser executado. Deseja execut\u00E1-lo?\n\n(Voc\u00EA deve pressionar Cancelar a n\u00E3o ser que voc\u00EA mesmo tenha criado o cookie.)",
          MathProcessingError: "Erro no processamento das f\u00F3rmulas",
          MathError: "Erro na f\u00F3rmula matem\u00E1tica",
          LoadFile: "Carregando %1",
          Loading: "Carregando",
          LoadFailed: "O arquivo n\u00E3o pode ser carregado: %1",
          ProcessMath: "Processando f\u00F3rmula: %1%%",
          Processing: "Processando",
          TypesetMath: "Realizando a Diagrama\u00E7\u00E3o das F\u00F3rmulas: %1%%",
          Typesetting: "Realizando a Diagrama\u00E7\u00E3o",
          MathJaxNotSupported: "Seu navegador n\u00E3o suporta MathJax"
        }
    },
    "FontWarnings": {},
    "HTML-CSS": {},
    "HelpDialog": {},
    "MathML": {},
    "MathMenu": {},
    "TeX": {}
  },
  plural: function (n) {
      if (n === 1) {return 1} // one
      return 2; // other
    },
  number: function (n) {
      return String(n).replace(".", ","); // replace dot by comma
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/pt-br/pt-br.js");
