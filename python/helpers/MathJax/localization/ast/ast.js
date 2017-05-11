/* -*- Mode: Javascript; indent-tabs-mode:nil; js-indent-level: 2 -*- */
/* vim: set ts=2 et sw=2 tw=80: */

/*************************************************************
 *
 *  MathJax/localization/ast/ast.js
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

MathJax.Localization.addTranslation("ast",null,{
  menuTitle: "asturianu",
  version: "2.6.0",
  isLoaded: true,
  domains: {
    "_": {
        version: "2.6.0",
        isLoaded: true,
        strings: {
          CookieConfig: "MathJax alcontr\u00F3 una cookie de configuraci\u00F3n d'usuariu qu'incluye c\u00F3digu a executar. \u00BFQuier executar esi c\u00F3digu?\n\n(Tendr\u00EDa de calcar \u00ABEncaboxar\u00BB a menos que creara la cookie vust\u00E9 mesmu.)",
          MathProcessingError: "Error de procesamientu matem\u00E1ticu",
          MathError: "Error matem\u00E1ticu",
          LoadFile: "Cargando %1",
          Loading: "Cargando",
          LoadFailed: "Fall\u00F3 la carga del ficheru: %1",
          ProcessMath: "Procesando matem\u00E1tiques: %1%%",
          Processing: "Procesando",
          TypesetMath: "Escribiendo matem\u00E1tiques: %1%%",
          Typesetting: "Componiendo",
          MathJaxNotSupported: "El so navegador nun tien sofitu pa MathJax"
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
      return n;
    }
});

MathJax.Ajax.loadComplete("[MathJax]/localization/ast/ast.js");
